package com.view.layouts;

import com.components.SidebarItem;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends JPanel {

    // ===== MÀU SẮC =====
    private static final Color BG_COLOR = new Color(24, 33, 48);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color COLLAPSE_BTN_HOVER = new Color(220, 50, 50);
    
    // ===== KÍCH THƯỚC =====
    private static final int SIDEBAR_WIDTH = 220;
    private static final int COLLAPSED_WIDTH = 60;

    private final JPanel itemsContainer;  // ← final
    private final Map<String, SidebarItem> items = new LinkedHashMap<>();
    private SidebarItem logoutItem;

    private Consumer<String> navigateListener;
    private Consumer<Void> toggleListener;
    private Runnable logoutListener;
    private String activeKey;
    private boolean isCollapsed = false;
    
    // Components
    private JLabel sectionLabel;
    private JLabel collapseButton;
    private JPanel headerPanel;

    public Sidebar() {
        setLayout(new BorderLayout());
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBorder(new EmptyBorder(16, 0, 16, 0));

        // ===== KHỞI TẠO ITEMSCONTAINER Ở ĐÂY =====
        itemsContainer = new JPanel();
        itemsContainer.setOpaque(false);
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));

        initHeader();

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(4, 0, 0, 0));
        top.add(headerPanel, BorderLayout.NORTH);
        top.add(itemsContainer, BorderLayout.CENTER);

        add(top, BorderLayout.NORTH);  // ← SỬA: BorderLayout.NORTH, không phải MIDDLE
        add(buildLogoutSection(), BorderLayout.SOUTH);
    }

    /**
     * Muc "Dang xuat" ghim o DAY sidebar (BorderLayout.SOUTH, tach rieng khoi
     * itemsContainer o tren) - day la 1 HANH DONG, khong phai 1 trang de dieu
     * huong (khong tham gia addItem()/setActive(), khong bao gio sang mau
     * "active" nhu cac muc menu khac). Co 1 duong ke mo phia tren de tach
     * biet ro rang voi danh sach menu.
     */
    private JPanel buildLogoutSection() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JPanel separatorWrap = new JPanel(new BorderLayout());
        separatorWrap.setOpaque(false);
        separatorWrap.setBorder(new EmptyBorder(0, 20, 8, 20));
        JPanel separatorLine = new JPanel();
        separatorLine.setBackground(new Color(255, 255, 255, 20));
        separatorLine.setPreferredSize(new Dimension(10, 1));
        separatorWrap.add(separatorLine, BorderLayout.CENTER);

        logoutItem = new SidebarItem("__logout__", "Đăng xuất", FontAwesomeSolid.SIGN_OUT_ALT);
        logoutItem.setOnClick(() -> {
            if (logoutListener != null) logoutListener.run();
        });

        wrapper.add(separatorWrap);
        wrapper.add(logoutItem);
        return wrapper;
    }

    private void initHeader() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 20, 12, 12));

        sectionLabel = new JLabel("Menu");
        sectionLabel.setForeground(TEXT_MUTED);
        sectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));

        FontIcon collapseIcon = FontIcon.of(FontAwesomeSolid.CHEVRON_LEFT, 16);
        collapseIcon.setIconColor(TEXT_MUTED);
        collapseButton = new JLabel(collapseIcon);
        collapseButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        collapseButton.setHorizontalAlignment(SwingConstants.RIGHT);
        collapseButton.setBorder(new EmptyBorder(0, 0, 0, 0));
        collapseButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                toggleCollapse();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                ((FontIcon) collapseButton.getIcon()).setIconColor(COLLAPSE_BTN_HOVER);
                collapseButton.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                ((FontIcon) collapseButton.getIcon()).setIconColor(TEXT_MUTED);
                collapseButton.repaint();
            }
        });

        headerPanel.add(sectionLabel, BorderLayout.WEST);
        headerPanel.add(collapseButton, BorderLayout.EAST);
    }

    // ===== PUBLIC METHODS =====

    public void addItem(String key, String label, FontAwesomeSolid iconType) {
        SidebarItem item = new SidebarItem(key, label, iconType);
        item.setOnClick(() -> {
            Sidebar.this.setActive(key);
            if (navigateListener != null) {
                navigateListener.accept(key);
            }
        });
        items.put(key, item);
        itemsContainer.add(item);
    }

    public void onNavigate(Consumer<String> listener) {
        this.navigateListener = listener;
    }

    public void onToggle(Consumer<Void> listener) {
        this.toggleListener = listener;
    }

    public void onLogout(Runnable listener) {
        this.logoutListener = listener;
    }

    public void setActive(String key) {
        this.activeKey = key;
        for (Map.Entry<String, SidebarItem> entry : items.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
    }

    public void setBadge(String key, int count) {
        SidebarItem item = items.get(key);
        if (item != null) item.setBadgeCount(count);
    }

    public void toggleCollapse() {
        isCollapsed = !isCollapsed;
        
        // Cập nhật icon
        FontAwesomeSolid iconType = isCollapsed ? FontAwesomeSolid.CHEVRON_RIGHT : FontAwesomeSolid.CHEVRON_LEFT;
        FontIcon newIcon = FontIcon.of(iconType, 16);
        newIcon.setIconColor(TEXT_MUTED);
        collapseButton.setIcon(newIcon);
        
        // Ẩn/hiện label
        sectionLabel.setVisible(!isCollapsed);
        
        // Cập nhật padding
        headerPanel.setBorder(new EmptyBorder(0, isCollapsed ? 12 : 20, 12, isCollapsed ? 12 : 12));
        
        // Cập nhật kích thước
        int width = isCollapsed ? COLLAPSED_WIDTH : SIDEBAR_WIDTH;
        setPreferredSize(new Dimension(width, 0));
        revalidate();
        repaint();
        
        if (toggleListener != null) {
            toggleListener.accept(null);
        }
    }

    public void expand() {
        if (isCollapsed) toggleCollapse();
    }

    public void collapse() {
        if (!isCollapsed) toggleCollapse();
    }

    public boolean isCollapsed() {
        return isCollapsed;
    }
}