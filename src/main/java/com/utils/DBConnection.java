package com.utils;

import com.security.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * DB_URL / DB_USER / DB_PASSWORD KHONG con hardcode trong source code nua.
 * Cac gia tri nay duoc doc tu AppConfig, la lop giai ma file
 * secure-config.enc (AES-256-GCM) luc khoi dong app.
 * Xem package com.security.
 */
public class DBConnection {

    /**
     * Tao connection toi MySQL database.
     *
     * DB_URL, DB_USER va DB_PASSWORD duoc doc tu AppConfig.
     *
     * Moi connection se duoc cau hinh timezone UTC+7
     * de dam bao CURRENT_TIMESTAMP / NOW() su dung gio Viet Nam.
     */
    public static Connection getConnection() throws SQLException {

        AppConfig config = AppConfig.getInstance();

        String url = config.get("DB_URL");
        String user = config.get("DB_USER");
        String pass = config.get("DB_PASSWORD");

        Connection connection = null;

        try {
            /*
             * Gioi han thoi gian cho ket noi.
             * Tranh app bi treo vo thoi han khi:
             * - MySQL khong chay
             * - DB_URL sai
             * - Firewall chan
             * - Server khong phan hoi
             */
            DriverManager.setLoginTimeout(8);

            connection = DriverManager.getConnection(
                    url,
                    user,
                    pass
            );

            /*
             * Moi connection cua ung dung deu su dung
             * gio Viet Nam UTC+7.
             *
             * Viet Nam khong co daylight saving time,
             * nen +07:00 la on dinh.
             */
            configureSessionTimeZone(connection);

            return connection;

        } catch (SQLException e) {

            /*
             * Neu da mo connection nhung cau lenh
             * SET time_zone bi loi thi dong connection
             * de tranh connection bi ro ri.
             */
            if (connection != null) {
                try {
                    connection.close();

                } catch (SQLException closeError) {
                    e.addSuppressed(closeError);
                }
            }

            throw new SQLException(
                    "Không kết nối được cơ sở dữ liệu ("
                    + e.getMessage()
                    + ")",
                    e
            );
        }
    }

    /**
     * Dat timezone cho tung session MySQL.
     *
     * CURRENT_TIMESTAMP, NOW() va cac cot co
     * DEFAULT CURRENT_TIMESTAMP se dung UTC+7.
     */
    private static void configureSessionTimeZone(
            Connection connection
    ) throws SQLException {

        String sql = "SET SESSION time_zone = '+07:00'";

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}