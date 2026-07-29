package com.view.layouts;

import com.theme.AppColor;
import com.permission.Permission;
import com.permission.PermissionManager;

import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.function.Consumer;

public class MainLayout extends JPanel {

    private final Sidebar sidebar;
    private final Header header;
    private final JPanel contentPanel;
    private final CardLayout cardLayout;
    private Consumer<String> pageChangeListener;

    /**
     * Section đang chờ — chỉ ghi thật vào sidebar khi có ít nhất 1 page
     * được add thành công (tránh hiện tiêu đề nhóm trống vì filter quyền).
     */
    private String pendingSection;

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

    public Header getHeader() {
        return header;
    }

    /**
     * Đánh dấu tiêu đề nhóm menu sẽ được thêm trước page kế tiếp
     * (nếu page đó vượt qua kiểm tra quyền).
     */
    public void addSection(String label) {
        this.pendingSection = label;
    }

    public void addPage(String key, String label,
                         org.kordamp.ikonli.fontawesome5.FontAwesomeSolid icon, JPanel panel) {
        addPage(key, label, icon, panel, (Permission[]) null);
    }

    public void addPage(String key, String label,
            org.kordamp.ikonli.fontawesome5.FontAwesomeSolid icon, JPanel panel,
            Permission permission) {
        addPage(key, label, icon, panel,
                permission == null ? null : new Permission[]{permission});
    }

    public void addPage(String key, String label,
            org.kordamp.ikonli.fontawesome5.FontAwesomeSolid icon, JPanel panel,
            Permission... permissions) {
        if (permissions != null && permissions.length > 0
                && !PermissionManager.getInstance().canAny(permissions)) {
            return;
        }
        flushPendingSection();
        sidebar.addItem(key, label, icon);
        contentPanel.add(panel, key);
    }

    private void flushPendingSection() {
        if (pendingSection != null) {
            sidebar.addSection(pendingSection);
            pendingSection = null;
        }
    }

    public void addHiddenPage(String key, JPanel panel) {
        contentPanel.add(panel, key);
    }

    public void showPage(String key) {
        cardLayout.show(contentPanel, key);
        sidebar.setActive(key);
        if (pageChangeListener != null) pageChangeListener.accept(key);
    }

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