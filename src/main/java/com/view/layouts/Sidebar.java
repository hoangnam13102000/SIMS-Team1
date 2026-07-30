package com.view.layouts;

import com.components.SidebarItem;
import com.i18n.Lang;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
 * Section mặc định đóng; menu dài có scroll dọc.
 */
public class Sidebar extends JPanel {

    private static final Color BG_COLOR = new Color(24, 33, 48);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color SECTION_HOVER = new Color(40, 51, 71);
    private static final Color COLLAPSE_BTN_HOVER = new Color(220, 50, 50);

    private static final int SIDEBAR_WIDTH = 220;
    private static final int COLLAPSED_WIDTH = 60;
    private static final int ROW_HEIGHT = 44;

    private final JPanel itemsContainer;
    private final Map<String, SidebarItem> items = new LinkedHashMap<>();
    private final Map<String, SectionGroup> itemToSection = new LinkedHashMap<>();
    private final List<SectionGroup> sections = new ArrayList<>();
    private SectionGroup currentSection;

    private SidebarItem logoutItem;
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

        // Scroll dọc khi menu dài
        JScrollPane scroll = new JScrollPane(itemsContainer);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));

        JPanel headerWrap = new JPanel(new BorderLayout());
        headerWrap.setOpaque(false);
        headerWrap.setBorder(new EmptyBorder(4, 0, 0, 0));
        headerWrap.add(headerPanel, BorderLayout.NORTH);

        add(headerWrap, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildLogoutSection(), BorderLayout.SOUTH);
    }

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

        logoutItem = new SidebarItem("__logout__", Lang.get("sidebar.logout"), FontAwesomeSolid.SIGN_OUT_ALT);
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

        menuLabel = new JLabel(Lang.get("sidebar.menu"));
        menuLabel.setForeground(TEXT_MUTED);
        menuLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));

        FontIcon collapseIcon = FontIcon.of(FontAwesomeSolid.CHEVRON_LEFT, 16);
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

        FontAwesomeSolid iconType = isCollapsed ? FontAwesomeSolid.CHEVRON_RIGHT : FontAwesomeSolid.CHEVRON_LEFT;
        FontIcon newIcon = FontIcon.of(iconType, 16);
        newIcon.setIconColor(TEXT_MUTED);
        collapseButton.setIcon(newIcon);

        menuLabel.setVisible(!isCollapsed);

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