package com.utils;

import com.security.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * DB_URL / DB_USER / DB_PASSWORD KHONG con hardcode trong source code nua.
 * Cac gia tri nay duoc doc tu AppConfig, la lop giai ma file
 * secure-config.enc (AES-256-GCM) luc khoi dong app. Xem package com.security.
 */
public class DBConnection {


    public static Connection getConnection() throws SQLException {
        AppConfig config = AppConfig.getInstance();
        String url = config.get("DB_URL");
        String user = config.get("DB_USER");
        String pass = config.get("DB_PASSWORD");
        try {
            // Gioi han thoi gian cho ket noi - tranh app "dung im" vo thoi han khi
            // MySQL khong chay / sai DB_URL / firewall chan.
            DriverManager.setLoginTimeout(8);
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw new SQLException("Không kết nối được cơ sở dữ liệu (" + e.getMessage() + ")", e);
        }
    }
}
