package com;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.incident.IncidentLogger;
import com.incident.IncidentType;
import com.security.AppConfig;
import com.theme.ThemeManager;
import com.utils.AppIcon;
import com.view.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.util.TimeZone;


public class Main {

    public static void main(String[] args) {
    	
    	TimeZone.setDefault(
    	        TimeZone.getTimeZone(
    	                "Asia/Ho_Chi_Minh"
    	        )
    	);

        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("flatlaf.uiScale", "100%");
        // Tăng giới hạn payload WebSocket (tin thoại base64 có thể lớn)
        System.setProperty("org.java_websocket.bin.message.size", String.valueOf(16 * 1024 * 1024));
        System.setProperty("org.java_websocket.txt.message.size", String.valueOf(16 * 1024 * 1024));

        // Ap dung theme Light/Dark da luu tu lan truoc (mac dinh Light neu
        // chua tung doi).
        ThemeManager.getInstance().applyStartupLookAndFeel();

        // Icon ung dung tren Dock (macOS) / mot so taskbar Linux. Tren
        // Windows, AppIcon.apply(frame) trong tung JFrame (Login/Admin/
        // Client/Register) la du de doi icon taskbar + title bar.
        AppIcon.applyToTaskbar();

        try {
            AppConfig.getInstance();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null,
                e.getMessage(),
                "Loi cau hinh he thong",
                JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }

        // Noi AppLogger voi bang AuditLogs that su - TRUOC DAY setSink() CHUA
        // TUNG duoc goi o dau ca nen moi log audit (CREATE/UPDATE/DELETE/
        // LOGIN...) bi am tham "roi mat", khong luu vao DB. Phai goi SAU khi
        // AppConfig san sang (can DB_URL/DB_USER/DB_PASSWORD) va TRUOC
        // LoginFrame de log LOGIN/LOGIN_FAILED dau tien cung duoc ghi.
        AppLogger.getInstance().setSink(new com.core.log.DbAuditLogSink());

        // Khoi dong subsystem sao luu/khoi phuc (xem com.disaster.DisasterRecoveryBootstrap).
        // Loi o day KHONG duoc phep chan toan bo app - chi ghi log + luu lai
        // ly do de BackupRecoveryPanel hien thi ro rang khi nguoi dung mo trang.
        try {
            com.disaster.DisasterRecoveryBootstrap.init();
        } catch (Exception e) {
            System.err.println("Khong khoi dong duoc DisasterRecoveryBootstrap: " + e.getMessage());
            AppLogger.getInstance().error(ErrorCode.SYSTEM_UNCAUGHT, "Main - khoi dong DisasterRecoveryBootstrap", e);
            IncidentLogger.getInstance().critical(IncidentType.CONFIG_ERROR,
                    "Main.main", "Khong khoi dong duoc DisasterRecoveryBootstrap: " + e.getMessage(), e);
            com.disaster.DisasterRecoveryBootstrap.recordInitFailure(e.getMessage());
        }

        // Chat WebSocket server: khoi dong SOM (truoc LoginFrame) de khach hang
        // van chat duoc du chua mo AdminMainFrame. Neu cong da bi process
        // khac giu (may admin khac trong LAN), bind se fail nhe - client
        // van ket noi qua WS_HOST trong ws.properties.
        try {
            com.ws.ChatServer.getInstance().start();
        } catch (Exception e) {
            System.err.println("Khong khoi dong duoc ChatServer: " + e.getMessage());
            AppLogger.getInstance().error(ErrorCode.SYSTEM_UNCAUGHT, "Main - ChatServer.start", e);
        }

        SwingUtilities.invokeLater(LoginFrame::new);
    }
}