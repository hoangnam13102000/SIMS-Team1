package com.components.common;

import com.theme.AppColor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Line2D;

/**
 * Icon tick trong vòng tròn, dùng cho danh sách tính năng
 */
public class CheckIcon implements Icon {
    
    private static final int SIZE = 20;

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
        g2.setColor(AppColor.ACCENT);
        g2.fillOval(0, 0, SIZE - 1, SIZE - 1);
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Double(5, 10, 8, 13));
        g2.draw(new Line2D.Double(8, 13, 15, 6));
        g2.dispose();
    }
}