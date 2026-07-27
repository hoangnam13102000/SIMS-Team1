package com.view.layouts;


import com.theme.AppColor;
import com.permission.Permission;
import com.permission.PermissionManager;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.util.function.Consumer;

public class MainLayout extends JPanel {

    private final Sidebar sidebar;
    private final Header header;
    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private Consumer<String> pageChangeListener;

    public MainLayout() {
        this("Cửa hàng điện thoại trực tuyến");
    }

    public MainLayout(String headerSubtitle) {
        setLayout(new BorderLayout());

        sidebar = new Sidebar();
        header = new Header(headerSubtitle);
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(AppColor.PAGE_BG);

        sidebar.onNavigate(this::showPage);

        add(header, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        add(new Footer(), BorderLayout.SOUTH);
    }

    /** Cho phep man hinh chu (vd AdminMainFrame) gan them hanh vi rieng cho Header (avatar, chuong...). */
    public Header getHeader() {
        return header;
    }

    public void addPage(String key, String label,
                         org.kordamp.ikonli.fontawesome5.FontAwesomeSolid icon, JPanel panel) {
        addPage(key, label, icon, panel, null);
    }

    /** Them 1 trang, chi hien ra neu user hien tai co quyen tuong ung (permission = null -> luon hien). */
    public void addPage(String key, String label,
                         org.kordamp.ikonli.fontawesome5.FontAwesomeSolid icon, JPanel panel,
                         Permission permission) {
        if (permission != null && !PermissionManager.getInstance().can(permission)) return;
        sidebar.addItem(key, label, icon);
        contentPanel.add(panel, key);
    }

    public void showPage(String key) {
        cardLayout.show(contentPanel, key);
        sidebar.setActive(key);
        if (pageChangeListener != null) pageChangeListener.accept(key);
    }

    /** Duoc goi moi khi trang hien tai doi (ca do bam sidebar lan do goi showPage() thu cong). */
    public void onPageChange(Consumer<String> listener) {
        this.pageChangeListener = listener;
    }

    public void setBadge(String key, int count) {
        sidebar.setBadge(key, count);
    }

    public void onLogout(Runnable listener) {
        sidebar.onLogout(listener);
        header.onLogout(listener);
    }
}