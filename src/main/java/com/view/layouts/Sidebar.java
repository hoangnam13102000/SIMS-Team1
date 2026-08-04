package com.view.layouts;

import com.components.SidebarItem;
import com.i18n.Lang;
import com.model.Role;
import com.model.User;
import com.service.AuthService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Sidebar admin — nhóm menu dropdown.
 * Section header cùng cấp / cùng căn trái với mục độc lập (Tổng quan).
 * Item con thụt nhẹ, thẳng hàng dọc dưới section.
 * Section mặc định đóng; menu dài có scroll dọc (thanh cuộn tối, mỏng).
 */
public class Sidebar extends JPanel {

    private static final Color BG_COLOR = new Color(24, 33, 48);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color SECTION_HOVER = new Color(40, 51, 71);
    private static final Color COLLAPSE_BTN_HOVER = new Color(220, 50, 50);
    private static final Color SCROLL_THUMB = new Color(70, 85, 110);
    private static final Color SCROLL_THUMB_HOVER = new Color(100, 116, 139);

    private static final int SIDEBAR_WIDTH = 220;
    private static final int COLLAPSED_WIDTH = 60;
    private static final int ROW_HEIGHT = 44;

    private final JPanel itemsContainer;
    private final Map<String, SidebarItem> items = new LinkedHashMap<>();
    private final Map<String, SectionGroup> itemToSection = new LinkedHashMap<>();
    private final List<SectionGroup> sections = new ArrayList<>();
    private SectionGroup currentSection;

    private SidebarItem logoutItem;
    private JPanel userInfoPanel;
    private JLabel userRoleLabel;
    private Consumer<String> navigateListener;
    private Consumer<Void> toggleListener;
    private Runnable logoutListener;
    private String activeKey;
    private boolean isCollapsed = false;

    private JLabel menuLabel;
    private JLabel collapseButton;
    private JPanel headerPanel;

    public Sidebar() {
        setLayout(new BorderLayout());
        setBackground(BG_COLOR);
        setPreferredSize(new Dimension(SIDEBAR_WIDTH, 0));
        setBorder(new EmptyBorder(16, 0, 16, 0));

        itemsContainer = new JPanel();
        itemsContainer.setOpaque(false);
        itemsContainer.setLayout(new BoxLayout(itemsContainer, BoxLayout.Y_AXIS));

        initHeader();

        // Scroll dọc khi menu dài — thanh cuộn mỏng, màu tối khớp sidebar
        JScrollPane scroll = new JScrollPane(itemsContainer);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        styleSidebarScrollBar(scroll.getVerticalScrollBar());

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.setBorder(new EmptyBorder(4, 0, 0, 0));
        headerWrap.add(headerPanel, BorderLayout.NORTH);

        add(headerWrap, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildLogoutSection(), BorderLayout.SOUTH);
    }

    /** Thanh cuộn mỏng, nền trong suốt, thumb tối — không lộ scrollbar hệ thống xấu. */
    private static void styleSidebarScrollBar(JScrollBar bar) {
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(6, 0));
        bar.setUnitIncrement(16);
        bar.setUI(new BasicScrollBarUI() {
            @Override
            protected void configureScrollBarColors() {
                this.thumbColor = SCROLL_THUMB;
                this.trackColor = new Color(0, 0, 0, 0);
            }

            @Override
            protected JButton createDecreaseButton(int orientation) {
                return createZeroButton();
            }

            @Override
            protected JButton createIncreaseButton(int orientation) {
                return createZeroButton();
            }

            private JButton createZeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                b.setMinimumSize(new Dimension(0, 0));
                b.setMaximumSize(new Dimension(0, 0));
                b.setOpaque(false);
                b.setContentAreaFilled(false);
                b.setBorder(null);
                return b;
            }

            @Override
            protected void paintTrack(Graphics g, JComponent c, Rectangle trackBounds) {
                // trong suốt
            }

            @Override
            protected void paintThumb(Graphics g, JComponent c, Rectangle thumbBounds) {
                if (thumbBounds.isEmpty() || !scrollbar.isEnabled()) return;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isDragging || isThumbRollover() ? SCROLL_THUMB_HOVER : SCROLL_THUMB);
                g2.fillRoundRect(thumbBounds.x + 1, thumbBounds.y + 2,
                        Math.max(4, thumbBounds.width - 2), thumbBounds.height - 4, 6, 6);
                g2.dispose();
            }
        });
    }

    private JPanel buildLogoutSection() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel separatorWrap = new JPanel(new BorderLayout());
        separatorWrap.setOpaque(false);
        separatorWrap.setBorder(new EmptyBorder(0, 20, 8, 20));
        separatorWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
        separatorWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
        JPanel separatorLine = new JPanel();
        separatorLine.setBackground(new Color(255, 255, 255, 20));
        separatorLine.setPreferredSize(new Dimension(10, 1));
        separatorWrap.add(separatorLine, BorderLayout.CENTER);

        userInfoPanel = buildUserInfoPanel();
        refreshUserInfo();

        logoutItem = new SidebarItem("__logout__", Lang.get("sidebar.logout"), FontAwesomeSolid.SIGN_OUT_ALT);
        logoutItem.setOnClick(() -> {
            if (logoutListener != null) logoutListener.run();
        });
        logoutItem.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.add(separatorWrap);
        wrapper.add(userInfoPanel);
        wrapper.add(logoutItem);
        return wrapper;
    }

    /**
     * Vai trò phía trên nút Đăng xuất (không hiện tên).
     * Padding trái 20px giống SidebarItem; HTML 2 dòng để không bị "...".
     */
    private JPanel buildUserInfoPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(2, 20, 8, 12));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        userRoleLabel = new JLabel(" ");
        userRoleLabel.setForeground(TEXT_MUTED);
        userRoleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        userRoleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        userRoleLabel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        panel.add(userRoleLabel);
        return panel;
    }

    /** Cập nhật vai trò từ AuthService (không hiện tên). */
    public void refreshUserInfo() {
        if (userRoleLabel == null) return;
        User user = AuthService.getInstance().getCurrentUser();
        if (user == null) {
            userRoleLabel.setText("");
            if (userInfoPanel != null) userInfoPanel.setVisible(false);
            return;
        }
        String role = roleLabel(user.getRole());
        String prefix = Lang.get("sidebar.loggedInAs");
        userRoleLabel.setText("<html>" + prefix + "<br>" + role + "</html>");
        if (userInfoPanel != null) userInfoPanel.setVisible(!isCollapsed);
    }

    private static String roleLabel(Role role) {
        if (role == null) return "-";
        switch (role) {
            case ADMIN: return Lang.get("sidebar.role.admin");
            case SALES_MANAGER: return Lang.get("sidebar.role.salesManager");
            case INVENTORY_MANAGER: return Lang.get("sidebar.role.inventoryManager");
            case SALES_STAFF: return Lang.get("sidebar.role.salesStaff");
            case CUSTOMER: return Lang.get("sidebar.role.customer");
            default: return role.name();
        }
    }

    private void initHeader() {
        headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(new EmptyBorder(0, 20, 12, 12));

        menuLabel = new JLabel(Lang.get("sidebar.menu"));
        menuLabel.setForeground(TEXT_MUTED);
        menuLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));

        // Icon hamburger (ba gạch)
        FontIcon collapseIcon = FontIcon.of(FontAwesomeSolid.BARS, 16);
        collapseIcon.setIconColor(TEXT_MUTED);
        collapseButton = new JLabel(collapseIcon);
        collapseButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        collapseButton.setHorizontalAlignment(SwingConstants.RIGHT);
        collapseButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { toggleCollapse(); }
            @Override public void mouseEntered(MouseEvent e) {
                ((FontIcon) collapseButton.getIcon()).setIconColor(COLLAPSE_BTN_HOVER);
                collapseButton.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                ((FontIcon) collapseButton.getIcon()).setIconColor(TEXT_MUTED);
                collapseButton.repaint();
            }
        });

        headerPanel.add(menuLabel, BorderLayout.WEST);
        headerPanel.add(collapseButton, BorderLayout.EAST);
    }

    // ===== PUBLIC API =====

    public void addSection(String label) {
        currentSection = new SectionGroup(label);
        sections.add(currentSection);
        itemsContainer.add(currentSection.root);
    }

    public void addItem(String key, String label, FontAwesomeSolid iconType) {
        SidebarItem item = new SidebarItem(key, label, iconType);
        item.setOnClick(() -> {
            Sidebar.this.setActive(key);
            if (navigateListener != null) navigateListener.accept(key);
        });
        items.put(key, item);

        if (currentSection != null) {
            currentSection.addItem(item);
            itemToSection.put(key, currentSection);
        } else {
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            itemsContainer.add(item);
        }
    }

    public void onNavigate(Consumer<String> listener) { this.navigateListener = listener; }
    public void onToggle(Consumer<Void> listener) { this.toggleListener = listener; }
    public void onLogout(Runnable listener) { this.logoutListener = listener; }

    public void setActive(String key) {
        this.activeKey = key;
        for (Map.Entry<String, SidebarItem> entry : items.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
        SectionGroup section = itemToSection.get(key);
        if (section != null && !section.expanded) {
            section.setExpanded(true);
        }
    }

    public void setBadge(String key, int count) {
        SidebarItem item = items.get(key);
        if (item != null) item.setBadgeCount(count);
    }

    public void toggleCollapse() {
        isCollapsed = !isCollapsed;

        // Mở: hamburger (BARS). Thu: chevron phải để gợi mở lại.
        FontAwesomeSolid iconType = isCollapsed ? FontAwesomeSolid.CHEVRON_RIGHT : FontAwesomeSolid.BARS;
        FontIcon newIcon = FontIcon.of(iconType, 16);
        newIcon.setIconColor(TEXT_MUTED);
        collapseButton.setIcon(newIcon);

        menuLabel.setVisible(!isCollapsed);
        if (userInfoPanel != null) userInfoPanel.setVisible(!isCollapsed);

        for (SectionGroup s : sections) {
            s.header.setVisible(!isCollapsed);
            s.children.setVisible(isCollapsed || s.expanded);
        }

        headerPanel.setBorder(new EmptyBorder(0, isCollapsed ? 12 : 20, 12, isCollapsed ? 12 : 12));
        setPreferredSize(new Dimension(isCollapsed ? COLLAPSED_WIDTH : SIDEBAR_WIDTH, 0));
        revalidate();
        repaint();

        if (toggleListener != null) toggleListener.accept(null);
    }

    public void expand() { if (isCollapsed) toggleCollapse(); }
    public void collapse() { if (!isCollapsed) toggleCollapse(); }
    public boolean isCollapsed() { return isCollapsed; }

    // ===== SectionGroup =====

    private final class SectionGroup {
        final JPanel root;
        final SectionHeader header;
        final JPanel children;
        boolean expanded = false; // mặc định đóng

        SectionGroup(String label) {
            root = new JPanel() {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
                }
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    d.width = SIDEBAR_WIDTH;
                    return d;
                }
            };
            root.setOpaque(false);
            root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
            root.setAlignmentX(Component.LEFT_ALIGNMENT);

            header = new SectionHeader(label);
            header.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    if (!isCollapsed) setExpanded(!expanded);
                }
            });

            children = new JPanel() {
                @Override
                public Dimension getMaximumSize() {
                    return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
                }
                @Override
                public Dimension getPreferredSize() {
                    Dimension d = super.getPreferredSize();
                    d.width = SIDEBAR_WIDTH;
                    return d;
                }
            };
            children.setOpaque(false);
            children.setLayout(new BoxLayout(children, BoxLayout.Y_AXIS));
            children.setAlignmentX(Component.LEFT_ALIGNMENT);

            root.add(header);
            root.add(children);
            // Đóng section lúc khởi tạo
            children.setVisible(false);
            header.setChevronExpanded(false);
        }

        void addItem(SidebarItem item) {
            item.setAlignmentX(Component.LEFT_ALIGNMENT);
            children.add(item);
        }

        void setExpanded(boolean value) {
            expanded = value;
            children.setVisible(isCollapsed || expanded);
            header.setChevronExpanded(expanded);
            root.revalidate();
            root.repaint();
            Sidebar.this.revalidate();
            Sidebar.this.repaint();
        }
    }

    /**
     * Section header — cùng cấp với SidebarItem / Tổng quan.
     */
    private static final class SectionHeader extends JPanel {
        private final JLabel titleLabel;
        private final JLabel chevronLabel;
        private boolean hover;

        SectionHeader(String label) {
            setLayout(new BorderLayout());
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(0, 20, 0, 12));
            setAlignmentX(Component.LEFT_ALIGNMENT);

            titleLabel = new JLabel(label.toUpperCase());
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
            titleLabel.setForeground(TEXT_MUTED);

            FontIcon chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 11);
            chevron.setIconColor(TEXT_MUTED);
            chevronLabel = new JLabel(chevron);

            add(titleLabel, BorderLayout.WEST);
            add(chevronLabel, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(SIDEBAR_WIDTH, ROW_HEIGHT);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(SIDEBAR_WIDTH, ROW_HEIGHT);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, ROW_HEIGHT);
        }

        void setChevronExpanded(boolean expanded) {
            FontAwesomeSolid type = expanded ? FontAwesomeSolid.CHEVRON_DOWN : FontAwesomeSolid.CHEVRON_RIGHT;
            FontIcon icon = FontIcon.of(type, 11);
            icon.setIconColor(TEXT_MUTED);
            chevronLabel.setIcon(icon);
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(SECTION_HOVER);
                g2.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, 10, 10);
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}