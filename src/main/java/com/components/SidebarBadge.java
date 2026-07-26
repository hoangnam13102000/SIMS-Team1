package com.components;


import com.theme.AppColor;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Badge hiển thị số lượng (ví dụ: số tin nhắn chưa đọc)
 */
public class SidebarBadge extends JLabel {

    private static final Font BADGE_FONT = new Font("Segoe UI", Font.BOLD, 10);

    public SidebarBadge() {
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(BADGE_FONT);
        setHorizontalAlignment(SwingConstants.CENTER);
        setBorder(new EmptyBorder(AppSpacing.XS, AppSpacing.SM, AppSpacing.XS, AppSpacing.SM));
        setVisible(false);
    }

    public void setCount(int count) {
        if (count <= 0) {
            setVisible(false);
            return;
        }
        setText(count > 9 ? "9+" : String.valueOf(count));
        setVisible(true);
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppColor.ERROR);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}