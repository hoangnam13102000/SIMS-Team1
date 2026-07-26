package com.components.common;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Nút chính với hiệu ứng hover, bo tròn
 */
public class PrimaryButton extends JButton {
    
    private boolean hover = false;

    public PrimaryButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setForeground(Color.WHITE);
        setFont(AppFont.BUTTON);
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.BUTTON_HEIGHT));
        setMaximumSize(new Dimension(AppConstant.FIELD_WIDTH, AppConstant.BUTTON_HEIGHT));
        
        addMouseListener(new MouseAdapter() {
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

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(isEnabled() ? (hover ? AppColor.ACCENT_HOVER : AppColor.ACCENT) : AppColor.DISABLED_BTN);
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppConstant.RADIUS_MD, AppConstant.RADIUS_MD);
        g2.dispose();
        super.paintComponent(g);
    }
}