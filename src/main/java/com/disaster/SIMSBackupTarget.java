package com.disaster;

import com.backup.DatabaseConnectionProvider;
import com.security.AppConfig;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cung cap Connection cho subsystem sao luu/khoi phuc cua SIMS - doc DB_URL /
 * DB_USER / DB_PASSWORD tu AppConfig (secure-config.enc), giong DBConnection
 * thuong dung, nhung them 1 nhanh rieng ket noi toi database "master" (bat
 * buoc de chay RESTORE DATABASE, vi khong the RESTORE 1 database dang chinh
 * no ket noi toi).
 */
public final class SIMSBackupTarget {

    private static final Pattern DB_NAME_PATTERN = Pattern.compile("databaseName=([^;]+)", Pattern.CASE_INSENSITIVE);

    private SIMSBackupTarget() {}

    public static DatabaseConnectionProvider appConnectionProvider() {
        return DBConnection::getConnection;
    }

    /** Ket noi toi database "master" - BAT BUOC dung rieng cho RESTORE DATABASE. */
    public static DatabaseConnectionProvider masterConnectionProvider() {
        return () -> {
            AppConfig config = AppConfig.getInstance();
            String url = config.get("DB_URL");
            String user = config.get("DB_USER");
            String pass = config.get("DB_PASSWORD");
            String masterUrl = replaceDatabaseName(url, "master");
            return DriverManager.getConnection(masterUrl, user, pass);
        };
    }

    public static String currentDatabaseName() {
        String url = AppConfig.getInstance().get("DB_URL");
        Matcher m = DB_NAME_PATTERN.matcher(url);
        if (m.find()) return m.group(1);
        throw new IllegalStateException("Khong tim thay databaseName= trong DB_URL.");
    }

    private static String replaceDatabaseName(String url, String newDbName) {
        Matcher m = DB_NAME_PATTERN.matcher(url);
        if (m.find()) return m.replaceFirst("databaseName=" + newDbName);
        String separator = url.contains("?") ? "&" : (url.endsWith(";") ? "" : ";");
        return url + separator + "databaseName=" + newDbName;
    }

    public static Connection tryConnect() {
        try { return DBConnection.getConnection(); } catch (Exception e) { return null; }
    }
}