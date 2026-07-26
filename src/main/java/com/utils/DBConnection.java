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

    /**
     * Mo 1 ket noi DB moi, hoac NEM SQLException ro rang neu that bai.
     * <p>
     * TRUOC DAY ham nay bat het loi va tra ve {@code null} khi khong ket noi
     * duoc. Da doi lai vi hau het noi goi (UserDAO, PhoneDAO, CategoryDAO,
     * OrderDAO, DashboardDAO, ActivityLogDAO.write()...) dung ngay ket qua
     * trong try-with-resources ma KHONG kiem tra null truoc
     * ({@code con.prepareStatement(...)}), nen khi DB mat ket noi se nem ra
     * NullPointerException thay vi loi that su - an di nguyen nhan goc (vd
     * "mat ket noi DB luc vua khoi dong app") va khien nguoi dung/log chi
     * thay mot NPE kho hieu (vi du dien hinh: bao "ghi audit log that bai"
     * ngay luc login ma khong ro vi sao).
     * <p>
     * Ném SQLException khien cac catch (Exception e)/(SQLException e) da co
     * san khap noi tu dong bat duoc NGUYEN NHAN THAT, khong can sua tung DAO.
     * 3 noi da chu dong kiem tra {@code if (con == null)} truoc day
     * (DbChangeWatcher, BackupRecoveryPanel, AuditLogIntegrityCheck) van
     * hoat dong dung vi ca 3 deu da co catch (SQLException|Exception) bao
     * quanh - doan kiem tra null cua chung tro thanh code "khong bao gio
     * chay toi" nhung khong gay loi gi (van co the don dep sau).
     *
     * @throws SQLException kem nguyen nhan goc (driver loi, sai URL, DB chua
     *                       chay, het thoi gian cho...) - xem {@code getCause()}.
     */
    public static Connection getConnection() throws SQLException {
        AppConfig config = AppConfig.getInstance();
        String url = config.get("DB_URL");
        String user = config.get("DB_USER");
        String pass = config.get("DB_PASSWORD");
        try {
            // Gioi han thoi gian cho ket noi - tranh app "dung im" vo thoi han khi
            // SQL Server khong chay / sai DB_URL / firewall chan (mac dinh driver
            // co the cho rat lau moi bao loi).
            DriverManager.setLoginTimeout(8);
            return DriverManager.getConnection(url, user, pass);
        } catch (SQLException e) {
            throw new SQLException("Không kết nối được cơ sở dữ liệu (" + e.getMessage() + ")", e);
        }
    }
}