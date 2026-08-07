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


public class ToggleSwitch extends JComponent {

    private static final int W = 46;
    private static final int H = 26;
    /** Padding trong hop W x H de anti-alias khong cham bien component. */
    private static final int PAD = 2;

    private boolean selected;
    private Consumer<Boolean> onChange;

    public ToggleSwitch(boolean initialValue) {
        this.selected = initialValue;
        setCursor(new Cursor(Cursor.HAND_CURSOR));
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
    public Dimension getPreferredSize() {
        return new Dimension(W, H);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(W, H);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(W, H);
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int availW = getWidth();
        int availH = getHeight();

        // Component chua duoc layout (0x0) -> khong ve gi, tranh chia cho 0.
        if (availW <= 0 || availH <= 0) {
            g2.dispose();
            return;
        }

        // CHOT CHAN QUAN TRONG: gioi han vung ve theo kich thuoc THAT cua
        // component, khong bao gio vuot qua availW/availH -> khong the bi
        // Swing clip mat mot phan hinh nua, du component bi bop nho hon
        // thiet ke ban dau (W x H) vi bat ky ly do gi.
        int boxW = Math.min(W, availW);
        int boxH = Math.min(H, availH);
        // Giu ti le pill hop ly khi bi bop qua nho (tranh track bi det/mop).
        boxH = Math.min(boxH, boxW);

        int ox = Math.max(0, (availW - boxW) / 2);
        int oy = Math.max(0, (availH - boxH) / 2);

        int pad = Math.min(PAD, Math.max(0, (Math.min(boxW, boxH) - 4) / 2));

        int trackX = ox + pad;
        int trackY = oy + pad;
        int trackW = Math.max(4, boxW - pad * 2);
        int trackH = Math.max(4, boxH - pad * 2);

        g2.setColor(selected ? AppColor.ACCENT : AppColor.BORDER);
        g2.fillRoundRect(trackX, trackY, trackW, trackH, trackH, trackH);

        int knobD = Math.max(2, trackH - 4);
        int knobY = trackY + (trackH - knobD) / 2;
        int knobX = selected
            ? trackX + trackW - knobD - 2
            : trackX + 2;
        // Bao dam knob khong bao gio ve ra ngoai track du bi bop nho toi da.
        knobX = Math.max(trackX, Math.min(knobX, trackX + trackW - knobD));

        g2.setColor(Color.WHITE);
        g2.fillOval(knobX, knobY, knobD, knobD);

        g2.dispose();
    }
}