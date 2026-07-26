package com.view.admin;

import com.components.BaseDialog;
import com.components.SettingsButton;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.view.LoginFrame;
import com.view.layouts.MainLayout;
import com.view.admin.account.UserAccountPanel;
import com.view.admin.customer.CustomerPanel;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Frame quan tri MAU cua framework - chi con lai 1 trang "Tong quan"
 * (DashboardPanel rong) de minh hoa bo khung Sidebar/Header/MainLayout.
 * Khi dung cho app that, them cac trang nghiep vu cua ban bang
 * layout.addPage(key, label, icon, panel, permission) trong buildContent().
 */
public class AdminMainFrame extends JFrame {

    private MainLayout layout;
    private String currentPageKey = "dashboard";
    private final Runnable onThemeChanged = this::rebuildContent;

    public AdminMainFrame() {
        setTitle("SIMS - Quản trị");
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        buildContent();

        // Nut cai dat (Sang/Toi) noi goc phai duoi man hinh.
        SettingsButton.attach(this);

        // Moi khi ThemeManager doi theme (Light/Dark), xay lai toan bo noi
        // dung de tat ca component doc lai dung mau + FlatLaf UI moi nhat.
        ThemeManager.getInstance().addRebuildListener(onThemeChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ThemeManager.getInstance().removeRebuildListener(onThemeChanged);
                AuthService.getInstance().logout();
                new LoginFrame();
            }
        });

        setVisible(true);
    }

    /** Xay (hoac xay lai) toan bo noi dung ben trong frame - MainLayout + cac trang. */
    private void buildContent() {
        if (layout != null) {
            remove(layout);
        }
        getContentPane().setBackground(AppColor.PAGE_BG);

        layout = new MainLayout("Khu vực quản trị");
        layout.addPage("dashboard", "Tổng quan", FontAwesomeSolid.TACHOMETER_ALT, new DashboardPanel(), AppPermission.DASHBOARD_VIEW);
        layout.addPage("users", "Quản lý tài khoản", FontAwesomeSolid.USERS_COG, new UserAccountPanel(), AppPermission.USER_MANAGE);
        layout.addPage("customers", "Quản lý khách hàng", FontAwesomeSolid.ID_CARD, new CustomerPanel(), AppPermission.CUSTOMER_MANAGE);

        // ---- Vi du them 1 trang moi khi ban ghep tinh nang that ----
        // layout.addPage("products", "San pham", FontAwesomeSolid.BOX, new ProductPanel(), AppPermission.PRODUCT_VIEW);

        layout.onPageChange(key -> currentPageKey = key);
        layout.showPage(currentPageKey);
        layout.onLogout(this::doLogout);
        add(layout, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private void rebuildContent() {
        buildContent();
        // Cac component ngoai MainLayout (vd SettingsButton tren layered pane)
        // doc mau truc tiep tu AppColor luc paintComponent nen chi can repaint.
        getLayeredPane().repaint();
    }

    private void doLogout() {
        boolean confirmed = BaseDialog.confirm(
            this,
            "Đăng xuất",
            "Bạn có chắc muốn đăng xuất khỏi tài khoản?",
            "Đăng xuất",
            AppColor.ERROR,
            AppColor.ERROR_HOVER,
            FontAwesomeSolid.SIGN_OUT_ALT
        );

        if (confirmed) {
            // Chi can dispose() - windowClosed() ben duoi se lo logout() va
            // mo lai LoginFrame, tranh bi goi 2 lan.
            dispose();
        }
    }
}