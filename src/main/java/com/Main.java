package com;

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

        // Kiem tra config bao mat NGAY LUC KHOI DONG (fail-fast). Neu thieu
        // bien moi truong MYSHOP_CONFIG_KEY (doi ten qua -Dsecureconfig.envKeyName
        // neu can) hoac file secure-config.enc bi hong/thieu, dung lai va bao
        // loi ro rang thay vi de app chay mo ho roi crash sau.
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

        SwingUtilities.invokeLater(LoginFrame::new);
    }
}
