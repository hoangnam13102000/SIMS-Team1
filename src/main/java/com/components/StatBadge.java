package com.components;

import com.theme.AppFont;

import javax.swing.*;
import java.awt.*;

/**
 * Badge dang "pill" nho, bo tron 2 dau: nen la 1 mau chu dao pha loang
 * (alpha thap), chu dam cung tong mau. Dung de hien thi 1 trang thai ngan
 * gon (don hang, ton kho, vai tro user...) - khong gan cung logic domain
 * nao, chi nhan vao (text, mau) nen tai su dung duoc o bat ky man hinh nao,
 * bao gom lam renderer cho cot trong BaseTable (xem BaseTable#setBadgeColumn).
 */
public class StatBadge extends JLabel {

    /** Do trong suot cua nen (0-255). Nho de chu van noi bat, nen chi la "vet mau" nhe phia sau. */
    private static final int BG_ALPHA = 32;

    private Color baseColor;

    public StatBadge(String text, Color baseColor) {
        super(text);
        setFont(AppFont.SMALL_BOLD);
        setHorizontalAlignment(SwingConstants.CENTER);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        setBadgeColor(baseColor);
    }

    /** Doi text + mau cung luc, tien dung khi tai su dung 1 instance lam renderer cho nhieu dong. */
    public void setBadge(String text, Color baseColor) {
        setText(text);
        setBadgeColor(baseColor);
    }

    public void setBadgeColor(Color baseColor) {
        this.baseColor = baseColor != null ? baseColor : Color.GRAY;
        setForeground(this.baseColor);
        repaint();
    }

    public Color getBaseColor() {
        return baseColor;
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), BG_ALPHA));
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
        g2.dispose();
        super.paintComponent(g);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(d.width, Math.max(d.height, 24));
    }
}