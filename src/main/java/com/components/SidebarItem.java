package com.components;

import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SidebarItem extends JPanel {

    // ===== MÀU SẮC =====
    private static final Color ACTIVE_TEXT = Color.WHITE;
    private static final Color INACTIVE_TEXT = new Color(255, 255, 255, 90);
    private static final Color HOVER_TEXT = new Color(255, 255, 255, 170);
    private static final Color ACTIVE_BG = com.theme.AppColor.ACCENT;
    private static final Color HOVER_BG = new Color(40, 51, 71);
    private static final int ITEM_HEIGHT = 44;
    private static final int ICON_SIZE = 15;

    private final String key;
    private boolean active = false;
    private boolean hover = false;

    private final FontIcon icon;
    private final JLabel iconLabel;
    private final JLabel label;
    private final SidebarBadge badge;

    private Runnable onClickListener;

    public SidebarItem(String key, String text, FontAwesomeSolid iconType) {
        this.key = key;

        // BorderLayout: icon trai, label giua (co the rut ngan), badge phai.
        // Tranh FlowLayout xuong dong khi nhan dai -> bi cat vi chieu cao co dinh.
        setLayout(new BorderLayout(10, 0));
        setOpaque(false);
        setPreferredSize(new Dimension(10, ITEM_HEIGHT));
        setMinimumSize(new Dimension(10, ITEM_HEIGHT));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(0, 20, 0, 12));

        // Icon
        icon = FontIcon.of(iconType, ICON_SIZE);
        icon.setIconColor(INACTIVE_TEXT);
        iconLabel = new JLabel(icon);
        iconLabel.setPreferredSize(new Dimension(22, ICON_SIZE + 4));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);

        // Label — 1 dong, khong wrap
        label = new JLabel(text);
        label.setFont(AppFont.BODY);
        label.setForeground(INACTIVE_TEXT);

        // Badge
        badge = new SidebarBadge();

        add(iconLabel, BorderLayout.WEST);
        add(label, BorderLayout.CENTER);
        add(badge, BorderLayout.EAST);

        // Mouse events
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (onClickListener != null) {
                    onClickListener.run();
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                hover = true;
                applyForeground();
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = false;
                applyForeground();
                repaint();
            }
        });
    }

    // ===== PUBLIC METHODS =====

    public String getKey() {
        return key;
    }

    public void setActive(boolean active) {
        this.active = active;
        applyForeground();
        label.setFont(label.getFont().deriveFont(active ? Font.BOLD : Font.PLAIN));
        repaint();
    }

    /** Ap dung mau chu/icon dung theo dung 1 trong 3 trang thai: active > hover > mac dinh (mo). */
    private void applyForeground() {
        Color fg = active ? ACTIVE_TEXT : (hover ? HOVER_TEXT : INACTIVE_TEXT);
        icon.setIconColor(fg);
        label.setForeground(fg);
    }

    public void setBadgeCount(int count) {
        badge.setCount(count);
    }

    public void setOnClick(Runnable listener) {
        this.onClickListener = listener;
    }

    public void setText(String text) {
        label.setText(text);
    }

    public void setIcon(FontAwesomeSolid iconType) {
        FontIcon newIcon = FontIcon.of(iconType, ICON_SIZE);
        newIcon.setIconColor(active ? ACTIVE_TEXT : (hover ? HOVER_TEXT : INACTIVE_TEXT));
        iconLabel.setIcon(newIcon);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (active) {
            g2.setColor(ACTIVE_BG);
            g2.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, 10, 10);
        } else if (hover) {
            g2.setColor(HOVER_BG);
            g2.fillRoundRect(8, 2, getWidth() - 16, getHeight() - 4, 10, 10);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}