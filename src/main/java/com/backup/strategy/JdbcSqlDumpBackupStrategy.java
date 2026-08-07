package com.backup.strategy;

import com.backup.BackupException;
import com.backup.BackupStrategy;
import com.backup.DatabaseConnectionProvider;
import com.backup.RestoreStrategy;
import com.backup.dialect.SqlDialect;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Backup/Restore hoan toan qua JDBC chuan - chay duoc voi bat ky DB nao co
 * JDBC driver, ke ca khi khong co quyen BACKUP DATABASE (DB cloud/managed).
 * Dinh dang file: MOI dong la 1 lenh SQL hoan chinh (khong xuong dong giua
 * chung 1 lenh) - nho vay restore chi can doc tung dong va thuc thi, khong
 * can bo phan tich cu phap SQL de vo.
 *
 * Han che da biet: khong dump index phu / FOREIGN KEY / trigger / view /
 * stored procedure. Dung lam phuong an du phong cho strategy native.
 */
public class JdbcSqlDumpBackupStrategy implements BackupStrategy, RestoreStrategy {

    private static final String NAME = "jdbc-sql-dump";
    private static final int FETCH_SIZE = 500;

    private final DatabaseConnectionProvider connectionProvider;
    private final SqlDialect dialect;
    private final String schemaPattern;
    private final Set<String> excludedTables;

    public JdbcSqlDumpBackupStrategy(DatabaseConnectionProvider connectionProvider,
                                      SqlDialect dialect,
                                      String schemaPattern,
                                      Set<String> excludedTables) {
        this.connectionProvider = connectionProvider;
        this.dialect = dialect;
        this.schemaPattern = schemaPattern;
        this.excludedTables = excludedTables == null ? Set.of() : excludedTables;
    }

    @Override public String getName() { return NAME; }

    @Override
    public void backupTo(File destinationFile) throws BackupException {
        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) throw new BackupException("Khong lay duoc Connection (tra ve null).");
            conn.setReadOnly(true);

            List<String> tables = listTables(conn);
            if (tables.isEmpty()) {
                throw new BackupException("Khong tim thay bang nao de backup (kiem tra schemaPattern/quyen truy cap).");
            }

            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(Files.newOutputStream(destinationFile.toPath()), StandardCharsets.UTF_8))) {

                writer.write("-- Generic JDBC SQL dump - tao luc " + Instant.now()); writer.newLine();
                writer.write("-- Strategy: " + NAME + " | So bang: " + tables.size()); writer.newLine();

                for (String table : tables) {
                    writer.write(dialect.dropTableIfExistsStatement(table)); writer.newLine();
                    writer.write(buildCreateTableStatement(conn, table)); writer.newLine();
                }
                for (String table : tables) {
                    dumpTableData(conn, table, writer);
                }
                writer.flush();
            }
        } catch (SQLException e) {
            throw new BackupException("Loi SQL khi backup qua JDBC: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new BackupException("Loi ghi file backup: " + e.getMessage(), e);
        }
    }

    private List<String> listTables(Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), schemaPattern, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String name = rs.getString("TABLE_NAME");
                if (!excludedTables.contains(name)) result.add(name);
            }
        }
        return result;
    }

    private String buildCreateTableStatement(Connection conn, String table) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        StringBuilder sb = new StringBuilder("CREATE TABLE ").append(dialect.quoteIdentifier(table)).append(" (");

        List<String> pkColumns = new ArrayList<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(conn.getCatalog(), schemaPattern, table)) {
            while (pkRs.next()) pkColumns.add(pkRs.getString("COLUMN_NAME"));
        }

        boolean first = true;
        try (ResultSet cols = meta.getColumns(conn.getCatalog(), schemaPattern, table, "%")) {
            while (cols.next()) {
                if (!first) sb.append(", ");
                first = false;
                String colName = cols.getString("COLUMN_NAME");
                int jdbcType = cols.getInt("DATA_TYPE");
                int size = cols.getInt("COLUMN_SIZE");
                int digits = cols.getInt("DECIMAL_DIGITS");
                String nullable = cols.getString("IS_NULLABLE");

                sb.append(dialect.quoteIdentifier(colName)).append(' ')
                        .append(dialect.mapJdbcTypeToColumnDefinition(jdbcType, size, digits));
                if ("NO".equalsIgnoreCase(nullable) && !pkColumns.contains(colName)) sb.append(" NOT NULL");
            }
        }

        if (!pkColumns.isEmpty()) {
            sb.append(", PRIMARY KEY (");
            for (int i = 0; i < pkColumns.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(dialect.quoteIdentifier(pkColumns.get(i)));
            }
            sb.append(')');
        }
        sb.append(");");
        return sb.toString();
    }

    private void dumpTableData(Connection conn, String table, BufferedWriter writer) throws SQLException, IOException {
        String sql = "SELECT * FROM " + dialect.quoteIdentifier(table);
        try (Statement stmt = conn.createStatement()) {
            stmt.setFetchSize(FETCH_SIZE);
            try (ResultSet rs = stmt.executeQuery(sql)) {
                ResultSetMetaData rsMeta = rs.getMetaData();
                int colCount = rsMeta.getColumnCount();

                StringBuilder colList = new StringBuilder();
                for (int i = 1; i <= colCount; i++) {
                    if (i > 1) colList.append(", ");
                    colList.append(dialect.quoteIdentifier(rsMeta.getColumnName(i)));
                }
                String insertPrefix = "INSERT INTO " + dialect.quoteIdentifier(table) + " (" + colList + ") VALUES (";

                int rowCount = 0;
                while (rs.next()) {
                    StringBuilder row = new StringBuilder(insertPrefix);
                    for (int i = 1; i <= colCount; i++) {
                        if (i > 1) row.append(", ");
                        row.append(formatValue(rs, i, rsMeta.getColumnType(i)));
                    }
                    row.append(");");
                    writer.write(row.toString()); writer.newLine();
                    rowCount++;
                    if (rowCount % FETCH_SIZE == 0) writer.flush();
                }
            }
        }
    }

    private String formatValue(ResultSet rs, int index, int jdbcType) throws SQLException {
        Object value;
        switch (jdbcType) {
            case Types.BLOB: case Types.BINARY: case Types.VARBINARY: case Types.LONGVARBINARY: {
                byte[] bytes = rs.getBytes(index);
                if (rs.wasNull() || bytes == null) return "NULL";
                return "0x" + toHex(bytes);
            }
            case Types.BIT: case Types.BOOLEAN: {
                boolean b = rs.getBoolean(index);
                if (rs.wasNull()) return "NULL";
                return b ? "1" : "0";
            }
            case Types.TINYINT: case Types.SMALLINT: case Types.INTEGER: case Types.BIGINT:
            case Types.DECIMAL: case Types.NUMERIC: case Types.REAL: case Types.FLOAT: case Types.DOUBLE: {
                value = rs.getObject(index);
                if (value == null) return "NULL";
                return value.toString();
            }
            default: {
                String str = rs.getString(index);
                if (rs.wasNull() || str == null) return "NULL";
                return "'" + dialect.escapeStringLiteral(str) + "'";
            }
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static final java.util.regex.Pattern INSERT_TABLE_PATTERN =
            java.util.regex.Pattern.compile("^INSERT INTO\\s+(\\S+)\\s*\\(", java.util.regex.Pattern.CASE_INSENSITIVE);

    @Override
    public void restoreFrom(File backupFile) throws BackupException {
        if (!backupFile.exists()) throw new BackupException("File backup khong ton tai: " + backupFile.getAbsolutePath());
        int executed = 0, failed = 0;
        List<String> firstErrors = new ArrayList<>();

        try (Connection conn = connectionProvider.getConnection()) {
            if (conn == null) throw new BackupException("Khong lay duoc Connection (tra ve null).");
            conn.setAutoCommit(true);

            // Xoa han rang buoc FK truoc (khong the chi NOCHECK vi SQL Server van chan
            // DROP TABLE khi con bi FK tham chieu toi). Strategy nay khong dump lai FK
            // nen chap nhan mat FK sau khi restore (da ghi ro trong javadoc class nay).
            String dropFks = dialect.dropAllForeignKeysStatement();
            if (dropFks != null) safeExecute(conn, dropFks);
            String disableFk = dialect.disableForeignKeyChecksStatement();
            if (disableFk != null) safeExecute(conn, disableFk);

            String identityInsertOnTable = null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(Files.newInputStream(backupFile.toPath()), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("--")) continue;

                    String insertTable = extractInsertTableName(trimmed);
                    if (!java.util.Objects.equals(insertTable, identityInsertOnTable)) {
                        if (identityInsertOnTable != null) {
                            String off = dialect.setIdentityInsertStatement(identityInsertOnTable, false);
                            if (off != null) safeExecute(conn, off);
                            identityInsertOnTable = null;
                        }
                        if (insertTable != null && hasIdentityColumn(conn, insertTable)) {
                            String on = dialect.setIdentityInsertStatement(insertTable, true);
                            if (on != null) {
                                safeExecute(conn, on);
                                identityInsertOnTable = insertTable;
                            }
                        }
                    }

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(trimmed);
                        executed++;
                    } catch (SQLException rowError) {
                        failed++;
                        if (firstErrors.size() < 10) firstErrors.add(rowError.getMessage());
                    }
                }
            }

            if (identityInsertOnTable != null) {
                String off = dialect.setIdentityInsertStatement(identityInsertOnTable, false);
                if (off != null) safeExecute(conn, off);
            }

            String enableFk = dialect.enableForeignKeyChecksStatement();
            if (enableFk != null) safeExecute(conn, enableFk);

        } catch (SQLException e) {
            throw new BackupException("Loi SQL khi restore qua JDBC: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new BackupException("Loi doc file backup: " + e.getMessage(), e);
        }

        if (failed > 0) {
            throw new BackupException("Restore hoan tat nhung co loi: " + executed + " lenh thanh cong, "
                    + failed + " lenh loi. Vi du: " + firstErrors);
        }
    }

    private String extractInsertTableName(String sql) {
        java.util.regex.Matcher m = INSERT_TABLE_PATTERN.matcher(sql);
        if (!m.find()) return null;
        String raw = m.group(1);
        // Bo dau ngoac vuong/nhay kep de dung lam ten bang tho khi truy van sys.identity_columns.
        if (raw.startsWith("[") && raw.endsWith("]")) raw = raw.substring(1, raw.length() - 1).replace("]]", "]");
        else if (raw.startsWith("\"") && raw.endsWith("\"")) raw = raw.substring(1, raw.length() - 1).replace("\"\"", "\"");
        return raw;
    }

    private final java.util.Map<String, Boolean> identityColumnCache = new java.util.HashMap<>();

    private boolean hasIdentityColumn(Connection conn, String tableName) {
        return identityColumnCache.computeIfAbsent(tableName, t -> {
            String query = dialect.identityColumnCheckQuery(t);
            if (query == null) return false;
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
                return rs.next();
            } catch (SQLException e) {
                return false;
            }
        });
    }

    private void safeExecute(Connection conn, String sql) {
        try (Statement stmt = conn.createStatement()) { stmt.execute(sql); }
        catch (SQLException ignored) {}
    }

    public static Set<String> noExclusions() { return new LinkedHashSet<>(); }
}