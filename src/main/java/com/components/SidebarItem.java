package com.components;

import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Một item trong Sidebar, bao gồm icon, label và badge.
 * <p>
 * Mau chu/icon dung rieng cho Sidebar (KHONG dung AppColor theo theme sang/toi
 * chung cua app nua) - vi nen Sidebar luon co dinh 1 mau toi (BG_COLOR ben
 * Sidebar.java), neu dung mau theo theme sang thi luc app o che do sang, chu
 * muc khong active se qua sang (gan nhu trang), khong con "mo" di duoc nhu
 * mong muon. Dung mau trang voi alpha (do trong suot) khac nhau theo trang
 * thai de dam bao luon mo/dam dung y bat ke theme toan app dang la gi:
 *  - Active: trang 100% + nen accent + chu dam.
 *  - Hover (khong active): trang ~65% - sang len chut de goi y bam duoc.
 *  - Mac dinh (khong active, khong hover): trang ~35% - MO ro rang so voi active.
 */
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

        setLayout(new FlowLayout(FlowLayout.LEFT, 12, 10));
        setOpaque(false);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, ITEM_HEIGHT));
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(0, 20, 0, 12));

        // Icon
        icon = FontIcon.of(iconType, ICON_SIZE);
        icon.setIconColor(INACTIVE_TEXT);
        iconLabel = new JLabel(icon);

        // Label
        label = new JLabel(text);
        label.setFont(AppFont.BODY);
        label.setForeground(INACTIVE_TEXT);

        // Badge
        badge = new SidebarBadge();

        // Add components
        add(iconLabel);
        add(label);
        add(badge);

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