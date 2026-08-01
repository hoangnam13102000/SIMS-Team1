package com;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.incident.IncidentLogger;
import com.incident.IncidentType;
import com.security.AppConfig;
import com.theme.ThemeManager;
import com.view.LoginFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;


public class Main {

    public static void main(String[] args) {

        System.setProperty("sun.java2d.uiScale.enabled", "true");
        System.setProperty("flatlaf.uiScale", "100%");

        // Ap dung theme Light/Dark da luu tu lan truoc (mac dinh Light neu
        // chua tung doi).
        ThemeManager.getInstance().applyStartupLookAndFeel();

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

        SwingUtilities.invokeLater(LoginFrame::new);
    }
}