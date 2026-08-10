package com.components.common;

import com.theme.AppColor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Icon sóng âm thanh (equalizer bars) chuyển động lên/xuống theo mức RMS
 * khi người dùng đang nói. Dùng cho chat realtime + AI chatbot.
 */
public class SoundWaveIcon extends JComponent {

    private static final int BAR_COUNT = 5;
    private static final int BAR_WIDTH = 3;
    private static final int BAR_GAP = 3;
    private static final int MIN_H = 4;
    private static final int MAX_H = 22;

    private final double[] heights = new double[BAR_COUNT];
    private final double[] targets = new double[BAR_COUNT];
    private final double[] phases = new double[BAR_COUNT];
    private double level;          // 0..1 smoothed
    private boolean active;
    private Timer animTimer;
    private Color barColor = AppColor.ACCENT_HOVER != null ? AppColor.ACCENT_HOVER : new Color(59, 130, 246);
    private long tick;

    public SoundWaveIcon() {
        setPreferredSize(new Dimension(28, 28));
        setMinimumSize(new Dimension(24, 24));
        setOpaque(false);
        for (int i = 0; i < BAR_COUNT; i++) {
            heights[i] = MIN_H;
            targets[i] = MIN_H;
            phases[i] = i * 0.7;
        }
    }

    public void setBarColor(Color c) {
        if (c != null) barColor = c;
    }

    /** Gọi định kỳ (~50–100ms) với giá trị RMS từ AudioRecorder.getLastRms(). */
    public void setLevel(double rms) {
        // RMS thường ~0.01–0.3 khi nói; chuẩn hóa mềm
        double n = Math.min(1.0, Math.max(0.0, rms * 8.0));
        level = level * 0.55 + n * 0.45;
    }

    public void start() {
        if (active) return;
        active = true;
        level = 0.15;
        if (animTimer == null) {
            animTimer = new Timer(40, e -> {
                tick++;
                updateBars();
                repaint();
            });
        }
        animTimer.start();
        setVisible(true);
        repaint();
    }

    public void stop() {
        active = false;
        level = 0;
        if (animTimer != null) animTimer.stop();
        for (int i = 0; i < BAR_COUNT; i++) {
            targets[i] = MIN_H;
            heights[i] = MIN_H;
        }
        repaint();
    }

    public boolean isActive() {
        return active;
    }

    private void updateBars() {
        double base = active ? Math.max(0.08, level) : 0;
        for (int i = 0; i < BAR_COUNT; i++) {
            // Trung tâm cao hơn 2 bên + dao động sin để sống động
            double centerBoost = 1.0 - Math.abs(i - (BAR_COUNT - 1) / 2.0) * 0.22;
            double wave = 0.55 + 0.45 * Math.sin(tick * 0.22 + phases[i]);
            double noise = 0.85 + 0.15 * Math.sin(tick * 0.37 + i * 1.3);
            targets[i] = MIN_H + (MAX_H - MIN_H) * base * centerBoost * wave * noise;
            // Lerp mượt
            heights[i] += (targets[i] - heights[i]) * 0.35;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int totalW = BAR_COUNT * BAR_WIDTH + (BAR_COUNT - 1) * BAR_GAP;
        int startX = (getWidth() - totalW) / 2;
        int midY = getHeight() / 2;

        for (int i = 0; i < BAR_COUNT; i++) {
            int h = (int) Math.round(heights[i]);
            int x = startX + i * (BAR_WIDTH + BAR_GAP);
            int y = midY - h / 2;
            g2.setColor(active ? barColor : new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 90));
            g2.fill(new RoundRectangle2D.Float(x, y, BAR_WIDTH, h, 2, 2));
        }
        g2.dispose();
    }
}
