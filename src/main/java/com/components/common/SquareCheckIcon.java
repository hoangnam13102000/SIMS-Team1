package com.components.common;

import com.theme.AppColor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;

/**
 * Icon checkbox vuông (tự vẽ, không phụ thuộc Look and Feel)
 */
public class SquareCheckIcon implements Icon {
    
    private static final int SIZE = 18;

    @Override
    public int getIconWidth() {
        return SIZE;
    }

    @Override
    public int getIconHeight() {
        return SIZE;
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(x, y);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        boolean selected = (c instanceof AbstractButton) && ((AbstractButton) c).isSelected();

        if (selected) {
            // Đã chọn - màu tím với tick
            g2.setColor(AppColor.ACCENT);
            g2.fillRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.draw(new Line2D.Double(4, 9, 7.5, 12.5));
            g2.draw(new Line2D.Double(7.5, 12.5, 14, 5));
        } else {
            // Chưa chọn - viền trắng
            g2.setColor(AppColor.WHITE);
            g2.fillRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
            g2.setColor(AppColor.BORDER);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(0, 0, SIZE - 1, SIZE - 1, 5, 5);
        }
        g2.dispose();
    }
}