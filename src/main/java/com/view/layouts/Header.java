package com.view.layouts;

import com.service.AuthService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class Header extends JPanel {

    private static final Color BG_COLOR = new Color(30, 41, 59);
    private static final Color ACCENT_COLOR = new Color(99, 102, 241);
    private static final Color SUBTITLE_COLOR = new Color(148, 163, 184);
    private static final Color BADGE_BG = new Color(51, 65, 85);

    public Header() {
        this("Cửa hàng điện thoại trực tuyến", null);
    }

    public Header(String subtitle) {
        this(subtitle, null, null);
    }

    /** Tuong thich nguoc: chi co dieu huong don hang tren chuong. */
    public Header(String subtitle, Runnable onBellViewOrders) {
        this(subtitle, onBellViewOrders, null);
    }

    /**
     * onBellViewOrders: chay khi bam dong don hang / "Xem tat ca don hang" tren chuong.
     * onBellViewSecurity: chay khi bam dong canh bao bao mat / "Xem tat ca bao mat" tren chuong.
     * Ca 2 co the null.
     */
    public Header(String subtitle, Runnable onBellViewOrders, Runnable onBellViewSecurity) {
        setLayout(new BorderLayout());
        setBackground(BG_COLOR);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 3, 0, ACCENT_COLOR),
            new EmptyBorder(14, 28, 14, 24)
        ));
        add(buildBrandSection(subtitle), BorderLayout.WEST);
        add(buildRightSection(onBellViewOrders, onBellViewSecurity), BorderLayout.EAST);
    }

    private JPanel buildBrandSection(String subtitle) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        panel.setOpaque(false);

        JPanel iconBadge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT_COLOR);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
            }
        };
        iconBadge.setOpaque(false);
        iconBadge.setPreferredSize(new Dimension(42, 42));
        FontIcon icon = FontIcon.of(FontAwesomeSolid.MOBILE_ALT, 20);
        icon.setIconColor(Color.WHITE);
        iconBadge.add(new JLabel(icon));

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("SIMS");
        title.setForeground(Color.WHITE);
        title.setFont(new Font("Segoe UI", Font.BOLD, 19));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setForeground(SUBTITLE_COLOR);
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textPanel.add(title);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(subtitleLabel);

        panel.add(iconBadge);
        panel.add(textPanel);
        return panel;
    }

    private JPanel buildRightSection(Runnable onBellViewOrders, Runnable onBellViewSecurity) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        wrapper.setOpaque(false);
        // Da bo NotificationBell (gan voi don hang/su co - khong thuoc framework co ban).
        // 2 tham so onBellViewOrders/onBellViewSecurity gio khong con dung, giu lai
        // trong chu ky ham de khong phai sua noi goi Header(...) o AdminMainFrame/MainLayout.
        wrapper.add(buildUserBadge());
        return wrapper;
    }

    private JPanel buildUserBadge() {
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BADGE_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setOpaque(false);
        badge.setBorder(new EmptyBorder(6, 14, 6, 16));

        FontIcon userIcon = FontIcon.of(FontAwesomeSolid.USER_CIRCLE, 18);
        userIcon.setIconColor(new Color(199, 210, 254));

        String displayName = AuthService.getInstance().isLoggedIn()
                ? AuthService.getInstance().getCurrentUser().getFullName()
                : "Khách";

        JLabel userLabel = new JLabel(displayName);
        userLabel.setForeground(Color.WHITE);
        userLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        badge.add(new JLabel(userIcon));
        badge.add(userLabel);

        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.add(badge);
        return wrapper;
    }
}