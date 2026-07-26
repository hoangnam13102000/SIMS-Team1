package com.view.client;


import com.theme.AppColor;
import com.service.AuthService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ClientHeader extends JPanel {


    private final Map<String, NavItem> navItems = new LinkedHashMap<>();
    private JPanel navPanel;
    private JPanel accountBadge;
    private JTextField searchField;

    private Consumer<String> navigateListener;
    private Consumer<String> searchListener;
    private Runnable profileListener;
    private Runnable logoutListener;

    public ClientHeader() {
        setLayout(new BorderLayout());
        setBackground(AppColor.WHITE);

        add(buildTopBar(), BorderLayout.NORTH);
        add(buildNavBar(), BorderLayout.SOUTH);
    }

    // ---------- Top bar: logo + search + cart + account ----------

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout(20, 0));
        topBar.setOpaque(true);
        topBar.setBackground(AppColor.WHITE);
        topBar.setBorder(new EmptyBorder(14, 24, 14, 20));

        topBar.add(buildLogoSection(), BorderLayout.WEST);
        topBar.add(buildSearchWrapper(), BorderLayout.CENTER);
        topBar.add(buildActionsSection(), BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildLogoSection() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        panel.setOpaque(false);

        JPanel logoBadge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        logoBadge.setOpaque(false);
        logoBadge.setPreferredSize(new Dimension(38, 38));
        FontIcon logoIcon = FontIcon.of(FontAwesomeSolid.MOBILE_ALT, 18);
        logoIcon.setIconColor(Color.WHITE);
        logoBadge.add(new JLabel(logoIcon));

        JLabel brand = new JLabel("SIMS");
        brand.setForeground(AppColor.TEXT_PRIMARY);
        brand.setFont(new Font("Segoe UI", Font.BOLD, 18));

        panel.add(logoBadge);
        panel.add(brand);
        return panel;
    }

    // ---------- Search bar (giua header, giong website ban hang) ----------

    private JPanel buildSearchWrapper() {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        wrapper.setOpaque(false);

        JPanel searchBar = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        searchBar.setOpaque(false);
        searchBar.setPreferredSize(new Dimension(460, 40));
        searchBar.setBorder(new EmptyBorder(0, 16, 0, 6));

        FontIcon searchIconLeft = FontIcon.of(FontAwesomeSolid.SEARCH, 13);
        searchIconLeft.setIconColor(AppColor.TEXT_MUTED);
        JLabel searchIconLabel = new JLabel(searchIconLeft);
        searchIconLabel.setBorder(new EmptyBorder(0, 0, 0, 8));

        searchField = new JTextField();
        searchField.putClientProperty("JTextField.placeholderText", "Tìm kiếm...");
        searchField.setOpaque(false);
        searchField.setBorder(null);
        searchField.setForeground(AppColor.TEXT_PRIMARY);
        searchField.setCaretColor(AppColor.TEXT_PRIMARY);
        searchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        searchField.addActionListener(e -> triggerSearch());
        searchField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) triggerSearch();
            }
        });

        JPanel fieldWrapper = new JPanel(new BorderLayout());
        fieldWrapper.setOpaque(false);
        fieldWrapper.add(searchIconLabel, BorderLayout.WEST);
        fieldWrapper.add(searchField, BorderLayout.CENTER);

        JButton searchButton = new JButton("Tìm");
        searchButton.setFocusPainted(false);
        searchButton.setBorderPainted(false);
        searchButton.setBackground(AppColor.ACCENT_HOVER);
        searchButton.setForeground(Color.WHITE);
        searchButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        searchButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        searchButton.setBorder(new EmptyBorder(6, 16, 6, 16));
        searchButton.addActionListener(e -> triggerSearch());

        JPanel searchButtonWrapper = new JPanel(new GridBagLayout());
        searchButtonWrapper.setOpaque(false);
        searchButtonWrapper.add(roundedButton(searchButton));

        searchBar.add(fieldWrapper, BorderLayout.CENTER);
        searchBar.add(searchButtonWrapper, BorderLayout.EAST);

        wrapper.add(searchBar);
        return wrapper;
    }

    /** Boc button trong panel tu ve nen bo tron, vi JButton mac dinh kho bo tron dep tren moi L&F. */
    private JPanel roundedButton(JButton button) {
        JPanel rounded = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rounded.setOpaque(false);
        button.setContentAreaFilled(false);
        rounded.add(button, BorderLayout.CENTER);
        return rounded;
    }

    private void triggerSearch() {
        String keyword = searchField.getText() == null ? "" : searchField.getText().trim();
        if (searchListener != null) searchListener.accept(keyword);
    }

    public void onSearch(Consumer<String> listener) {
        this.searchListener = listener;
    }

    // ---------- Cart icon + account (ben phai) ----------

    private JPanel buildActionsSection() {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        actions.add(buildAccountBadge());
        return actions;
    }

    // ---------- Nav bar (Cua hang, Don hang cua toi, ...) dang tab gach chan ----------

    private JPanel buildNavBar() {
        JPanel navBarWrapper = new JPanel(new BorderLayout());
        navBarWrapper.setOpaque(true);
        navBarWrapper.setBackground(AppColor.BG_LIGHTER);
        navBarWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColor.BORDER));

        navPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        navPanel.setOpaque(false);
        navPanel.setBorder(new EmptyBorder(0, 20, 0, 0));

        navBarWrapper.add(navPanel, BorderLayout.WEST);
        return navBarWrapper;
    }

    public void addPage(String key, String label, FontAwesomeSolid iconType) {
        NavItem item = new NavItem(key, label, iconType);
        navItems.put(key, item);
        navPanel.add(item);
    }

    public void setActive(String key) {
        for (Map.Entry<String, NavItem> entry : navItems.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(key));
        }
    }

    public void onNavigate(Consumer<String> listener) {
        this.navigateListener = listener;
    }

    private class NavItem extends JPanel {
        private final String key;
        private boolean active = false;
        private boolean hover = false;
        private final FontIcon icon;
        private final JLabel label;

        NavItem(String key, String text, FontAwesomeSolid iconType) {
            this.key = key;
            setOpaque(false);
            setLayout(new FlowLayout(FlowLayout.CENTER, 8, 0));
            setBorder(new EmptyBorder(11, 14, 8, 14));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            icon = FontIcon.of(iconType, 13);
            icon.setIconColor(AppColor.TEXT_MUTED);
            label = new JLabel(text);
            label.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            label.setForeground(AppColor.TEXT_MUTED);

            add(new JLabel(icon));
            add(label);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    ClientHeader.this.setActive(key);
                    if (navigateListener != null) navigateListener.accept(key);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        void setActive(boolean active) {
            this.active = active;
            Color fg = active ? AppColor.ACCENT_HOVER : AppColor.TEXT_MUTED;
            icon.setIconColor(fg);
            label.setForeground(fg);
            label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (hover && !active) {
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight() - 3, 8, 8);
            }
            if (active) {
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRect(0, getHeight() - 3, getWidth(), 3);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------- Account dropdown ----------

    private JPanel buildAccountBadge() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);

        accountBadge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getMousePosition() != null ? AppColor.BG_LIGHTER : AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        accountBadge.setOpaque(false);
        accountBadge.setBorder(new EmptyBorder(7, 14, 7, 14));
        accountBadge.setCursor(new Cursor(Cursor.HAND_CURSOR));

        FontIcon userIcon = FontIcon.of(FontAwesomeSolid.USER_CIRCLE, 17);
        userIcon.setIconColor(AppColor.ACCENT_HOVER);

        JLabel nameLabel = new JLabel(currentDisplayName());
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        nameLabel.setName("accountNameLabel");

        FontIcon chevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 10);
        chevron.setIconColor(AppColor.TEXT_MUTED);

        accountBadge.add(new JLabel(userIcon));
        accountBadge.add(nameLabel);
        accountBadge.add(new JLabel(chevron));

        JPopupMenu menu = buildAccountPopup();
        accountBadge.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                menu.show(accountBadge, 0, accountBadge.getHeight() + 6);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                accountBadge.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                accountBadge.repaint();
            }
        });

        wrapper.add(accountBadge);
        return wrapper;
    }

    private JPopupMenu buildAccountPopup() {
        JPopupMenu menu = new JPopupMenu();

        JMenuItem profileItem = new JMenuItem("Trang cá nhân", FontIcon.of(FontAwesomeSolid.ID_CARD, 13));
        profileItem.addActionListener(e -> {
            if (profileListener != null) profileListener.run();
        });

        JMenuItem logoutItem = new JMenuItem("Đăng xuất", FontIcon.of(FontAwesomeSolid.SIGN_OUT_ALT, 13));
        logoutItem.addActionListener(e -> {
            if (logoutListener != null) logoutListener.run();
        });

        menu.add(profileItem);
        menu.addSeparator();
        menu.add(logoutItem);
        return menu;
    }

    private String currentDisplayName() {
        return AuthService.getInstance().isLoggedIn()
                ? AuthService.getInstance().getCurrentUser().getFullName()
                : "Khách";
    }

    /** Goi lai sau khi doi ho ten o Trang ca nhan de cap nhat lai chu tren badge. */
    public void refreshAccountLabel() {
        for (Component c : accountBadge.getComponents()) {
            if (c instanceof JLabel && "accountNameLabel".equals(c.getName())) {
                ((JLabel) c).setText(currentDisplayName());
            }
        }
    }

    public void onProfile(Runnable listener) {
        this.profileListener = listener;
    }

    public void onLogout(Runnable listener) {
        this.logoutListener = listener;
    }
}