package com.backup.strategy;

import com.backup.BackupException;
import com.backup.BackupStrategy;
import com.backup.DatabaseConnectionProvider;
import com.backup.RestoreStrategy;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * LUU Y: BACKUP DATABASE ghi file tren o dia cua MAY CHAY SQL SERVER, khong
 * phai may chay app Java. Chi dung khi SQL Server + app cung 1 may (rat pho
 * bien voi setup SQL Server Express local nhu myShop). Neu that bai,
 * BackupManager tu dong fallback sang JdbcSqlDumpBackupStrategy.
 *
 * LUU Y VE COMPRESSION: tuy chon "WITH COMPRESSION" cua BACKUP DATABASE CHI
 * duoc SQL Server Standard/Enterprise ho tro - Express (rat pho bien cho
 * setup dev/hoc tap nhu myShop) KHONG ho tro va se lam ca cau lenh that bai
 * ngay lap tuc, du database hoan toan backup duoc binh thuong neu bo tuy chon
 * nay di. Nen truoc khi build cau lenh, ta kiem tra edition qua
 * SERVERPROPERTY('EngineEdition') va chi them COMPRESSION khi server ho tro;
 * kem 1 lop fallback phan ung (retry khong nen) phong truong hop khong xac
 * dinh duoc edition hoac edition bao ho tro nhung thuc te van tu choi.
 */
public class SqlServerNativeBackupStrategy implements BackupStrategy, RestoreStrategy {

    private static final String NAME = "sqlserver-native";

    /** SERVERPROPERTY('EngineEdition') = 4 nghia la SQL Server Express - khong ho tro COMPRESSION. */
    private static final int ENGINE_EDITION_EXPRESS = 4;

    private final DatabaseConnectionProvider connectionProvider;
    private final DatabaseConnectionProvider adminConnectionProvider; // phai tro toi database "master"
    private final String databaseName;

    public SqlServerNativeBackupStrategy(DatabaseConnectionProvider connectionProvider,
                                          DatabaseConnectionProvider adminConnectionProvider,
                                          String databaseName) {
        this.connectionProvider = connectionProvider;
        this.adminConnectionProvider = adminConnectionProvider;
        this.databaseName = databaseName;
    }

    @Override public String getName() { return NAME; }
    @Override public String getFileExtension() { return "bak"; }

    @Override
    public void backupTo(File destinationFile) throws BackupException {
        String path = destinationFile.getAbsolutePath().replace("'", "''");

        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) throw new BackupException("Khong lay duoc Connection (tra ve null).");

            boolean useCompression = supportsCompression(conn);
            try {
                runBackup(conn, path, useCompression);
            } catch (SQLException e) {
                // Fallback phan ung: neu van that bai vi ly do COMPRESSION du da kiem tra
                // edition truoc do (vd khong doc duoc SERVERPROPERTY, hoac edition bao ho
                // tro nhung server thuc te tu choi), thu lai 1 lan khong nen truoc khi bo cuoc.
                if (useCompression && isCompressionNotSupportedError(e)) {
                    runBackup(conn, path, false);
                } else {
                    throw e;
                }
            }

            if (!destinationFile.exists() || destinationFile.length() == 0) {
                throw new BackupException("BACKUP DATABASE chay xong nhung khong thay file tai " + path
                        + " - co the SQL Server dang chay tren may khac.");
            }
        } catch (SQLException e) {
            throw new BackupException("BACKUP DATABASE that bai (" + e.getMessage()
                    + "). Neu SQL Server khong chay cung may voi ung dung, dung strategy jdbc-sql-dump thay the.", e);
        }
    }

    private void runBackup(Connection conn, String path, boolean useCompression) throws SQLException {
        String sql = "BACKUP DATABASE " + quoteDbName() + " TO DISK = N'" + path + "' "
                + "WITH INIT" + (useCompression ? ", COMPRESSION" : "") + ", STATS = 10";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    /**
     * Kiem tra SQL Server dang ket noi co ho tro BACKUP DATABASE WITH COMPRESSION
     * khong (Standard/Enterprise co, Express (EngineEdition = 4) khong).
     * Neu khong xac dinh duoc (loi truy van, driver cu,...) thi tra ve false de
     * an toan - BACKUP van chay duoc du khong nen, con co gang dung COMPRESSION
     * sai edition thi that bai hoan toan.
     */
    private boolean supportsCompression(Connection conn) {
        String sql = "SELECT SERVERPROPERTY('EngineEdition') AS EngineEdition";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt("EngineEdition") != ENGINE_EDITION_EXPRESS;
            }
        } catch (SQLException e) {
            // Bo qua - coi nhu khong xac dinh duoc, mac dinh khong dung COMPRESSION.
        }
        return false;
    }

    private boolean isCompressionNotSupportedError(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase().contains("COMPRESSION")
                && msg.toUpperCase().contains("NOT SUPPORTED");
    }

    @Override
    public void restoreFrom(File backupFile) throws BackupException {
        String path = backupFile.getAbsolutePath().replace("'", "''");
        if (!backupFile.exists()) {
            throw new BackupException("File .bak khong ton tai tren may chay ung dung: " + path);
        }
        try (Connection conn = adminConnectionProvider.getConnection()) {
            if (conn == null) throw new BackupException("Khong lay duoc Connection admin (master).");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER DATABASE " + quoteDbName() + " SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                stmt.execute("RESTORE DATABASE " + quoteDbName() + " FROM DISK = N'" + path + "' WITH REPLACE, STATS = 10");
                stmt.execute("ALTER DATABASE " + quoteDbName() + " SET MULTI_USER");
            }
        } catch (SQLException e) {
            throw new BackupException("RESTORE DATABASE that bai: " + e.getMessage(), e);
        }
    }

    private String quoteDbName() { return "[" + databaseName.replace("]", "]]") + "]"; }
}