package com.components;

import com.theme.AppColor;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Badge hiển thị số lượng (ví dụ: số tin nhắn chưa đọc / cảnh báo tồn kho)
 * Hình tròn, màu đỏ.
 */
public class SidebarBadge extends JLabel {

    private static final Font BADGE_FONT = new Font("Segoe UI", Font.BOLD, 10);
    /** Kích thước tối thiểu để badge luôn tròn và dễ nhìn */
    private static final int MIN_SIZE = 18;

    public SidebarBadge() {
        setOpaque(false);
        setForeground(Color.WHITE);
        setFont(BADGE_FONT);
        setHorizontalAlignment(SwingConstants.CENTER);
        setVerticalAlignment(SwingConstants.CENTER);
        // Padding đều để giữ tỷ lệ gần vuông → dễ thành hình tròn
        setBorder(new EmptyBorder(AppSpacing.XS, AppSpacing.XS, AppSpacing.XS, AppSpacing.XS));
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
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        // Ép thành hình vuông → paintComponent sẽ vẽ thành hình tròn
        int size = Math.max(d.width, d.height);
        size = Math.max(size, MIN_SIZE);
        return new Dimension(size, size);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AppColor.ERROR);
        // Vẽ hình tròn (fillOval) thay vì fillRoundRect
        g2.fillOval(0, 0, getWidth(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }
}