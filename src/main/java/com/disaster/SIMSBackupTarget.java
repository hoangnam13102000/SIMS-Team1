package com.disaster;

import com.backup.DatabaseConnectionProvider;
import com.security.AppConfig;
import com.utils.DBConnection;

import java.sql.Connection;

/**
 * Cung cap Connection cho subsystem sao luu/khoi phuc cua SIMS - doc DB_URL /
 * DB_USER / DB_PASSWORD tu AppConfig (secure-config.enc), giong DBConnection
 * thuong dung. MySQL backup/restore su dung cung connection va dialect MySQL.
 */
public final class SIMSBackupTarget {

    private SIMSBackupTarget() {}

    public static DatabaseConnectionProvider appConnectionProvider() {
        return DBConnection::getConnection;
    }

    public static String currentDatabaseName() {
        String url = AppConfig.getInstance().get("DB_URL");
        int query = url.indexOf('?');
        String withoutQuery = query >= 0 ? url.substring(0, query) : url;
        int slash = withoutQuery.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < withoutQuery.length()) {
            return withoutQuery.substring(slash + 1);
        }
        throw new IllegalStateException("Khong tim thay ten database trong DB_URL MySQL.");
    }

    public static Connection tryConnect() {
        try { return DBConnection.getConnection(); } catch (Exception e) { return null; }
    }
}
