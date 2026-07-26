package com.components;

import com.theme.AppColor;

import javax.swing.JComponent;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Cong tac bat/tat kieu iOS (pill + nut tron truot). Dung cho cac man hinh
 * Cai dat (vd SettingsButton: bat/tat am thanh, an thong bao don hang).
 */
public class ToggleSwitch extends JComponent {

    private static final int W = 40;
    private static final int H = 22;

    private boolean selected;
    private Consumer<Boolean> onChange;

    public ToggleSwitch(boolean initialValue) {
        this.selected = initialValue;
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setPreferredSize(new Dimension(W, H));
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                setSelected(!selected);
            }
        });
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected == selected) return;
        this.selected = selected;
        repaint();
        if (onChange != null) onChange.accept(selected);
    }

    public void onChange(Consumer<Boolean> listener) {
        this.onChange = listener;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color track = selected ? AppColor.ACCENT : AppColor.BORDER;
        g2.setColor(track);
        g2.fillRoundRect(0, 0, W, H, H, H);

        int knobD = H - 4;
        int knobX = selected ? W - knobD - 2 : 2;
        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, 2, knobD, knobD);

        g2.dispose();
    }
}