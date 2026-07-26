package com.components;

import com.theme.AppColor;
import com.theme.AppFont;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Arc2D;

public class LoadingOverlay extends JComponent {

    private static final int SPINNER_SIZE = 48;
    private static final int SPINNER_STROKE = 5;

    // Mau sac hien dai
    private static final Color SPINNER_GRADIENT_END = new Color(139, 92, 246);
    private static final Color SHADOW_COLOR = new Color(0, 0, 0, 30);
    
    private static final int MIN_VISIBLE_MS = 300; // thời gian hiện 300ms

    private final Timer animationTimer;
    private double angle = 0;
    private String message;
    private boolean useModernStyle = true;

    private long shownAt = 0;
    /** Tang moi lan start() de phat hien stop() "cu" goi tre sau khi da co start() moi hon. */
    private long generation = 0;

    public LoadingOverlay() {
        this(null);
    }

    public LoadingOverlay(String message) {
        this.message = message;
        setOpaque(false);
        setVisible(false);
        animationTimer = new Timer(16, e -> {
            angle = (angle + 6) % 360;
            repaint();
        });
    }

    /** Hien overlay va bat dau xoay, giu nguyen message hien tai (co the null). */
    public void start() {
        start(this.message);
    }

    /** Hien overlay voi message moi va bat dau xoay. */
    public void start(String message) {
        this.message = message;
        generation++;
        shownAt = System.currentTimeMillis();
        setVisible(true);
        repaint(); // ep yeu cau ve ngay, khong doi vong lap Timer dau tien (16ms)
        if (!animationTimer.isRunning()) animationTimer.start();
    }

 
    public void stop() {
        long myGeneration = generation;
        long elapsed = System.currentTimeMillis() - shownAt;
        long remaining = MIN_VISIBLE_MS - elapsed;

        Runnable finish = () -> {
            if (myGeneration == generation) {
                setVisible(false);
                animationTimer.stop();
            }
        };

        if (remaining > 0) {
            Timer delay = new Timer((int) remaining, e -> finish.run());
            delay.setRepeats(false);
            delay.start();
        } else {
            finish.run();
        }
    }

    public boolean isLoading() {
        return isVisible();
    }

    public void setMessage(String message) {
        this.message = message;
        repaint();
    }

    public void setModernStyle(boolean modern) {
        this.useModernStyle = modern;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!isVisible()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        g2.setColor(AppColor.OVERLAY_BACKDROP);
        g2.fillRect(0, 0, getWidth(), getHeight());

        boolean hasMessage = message != null && !message.isBlank();
        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2 - (hasMessage ? 20 : 0);
        int spinnerX = centerX - SPINNER_SIZE / 2;
        int spinnerY = centerY - SPINNER_SIZE / 2;

        if (useModernStyle) {
            drawModernSpinner(g2, spinnerX, spinnerY, hasMessage, centerX, centerY);
        } else {
            drawClassicSpinner(g2, spinnerX, spinnerY, hasMessage);
        }

        g2.dispose();
    }

    private void drawModernSpinner(Graphics2D g2, int x, int y, boolean hasMessage, int centerX, int centerY) {
        g2.setColor(SHADOW_COLOR);
        g2.fillOval(x + 2, y + 3, SPINNER_SIZE, SPINNER_SIZE);

        RadialGradientPaint bgGradient = new RadialGradientPaint(
            centerX, centerY, SPINNER_SIZE / 2,
            new float[]{0.2f, 0.8f, 1.0f},
            new Color[]{AppColor.BG_LIGHTER, AppColor.BORDER, AppColor.FIELD_BORDER}
        );
        g2.setPaint(bgGradient);
        g2.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawOval(x, y, SPINNER_SIZE, SPINNER_SIZE);

        float[] fractions = {0.0f, 0.5f, 1.0f};
        Color[] colors = {AppColor.ACCENT, SPINNER_GRADIENT_END, AppColor.ACCENT};
        LinearGradientPaint gradient = new LinearGradientPaint(
            x, y, x + SPINNER_SIZE, y + SPINNER_SIZE, fractions, colors
        );
        g2.setPaint(gradient);
        g2.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Arc2D.Double(x, y, SPINNER_SIZE, SPINNER_SIZE, angle, 270, Arc2D.OPEN));

        int dotX = (int) (x + SPINNER_SIZE / 2 + (SPINNER_SIZE / 2 - SPINNER_STROKE) *
                         Math.cos(Math.toRadians(angle)));
        int dotY = (int) (y + SPINNER_SIZE / 2 + (SPINNER_SIZE / 2 - SPINNER_STROKE) *
                         Math.sin(Math.toRadians(angle)));
        g2.setColor(SPINNER_GRADIENT_END);
        g2.fillOval(dotX - 4, dotY - 4, 8, 8);

        g2.setColor(new Color(139, 92, 246, 50));
        g2.fillOval(dotX - 8, dotY - 8, 16, 16);

        if (hasMessage) {
            g2.setColor(new Color(0, 0, 0, 20));
            g2.setFont(AppFont.BODY.deriveFont(Font.BOLD, 15));
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(message);
            g2.drawString(message, (getWidth() - textWidth) / 2 + 1, y + SPINNER_SIZE + 26);

            g2.setColor(AppColor.TEXT_MUTED);
            g2.drawString(message, (getWidth() - textWidth) / 2, y + SPINNER_SIZE + 25);

            int dotCount = ((int) (angle / 30) % 3) + 1;
            String dots = ".".repeat(dotCount);
            g2.setFont(AppFont.BODY.deriveFont(Font.BOLD, 14));
            g2.setColor(AppColor.ACCENT);
            g2.drawString(dots, (getWidth() + textWidth) / 2 + 4, y + SPINNER_SIZE + 25);
        }
    }

    private void drawClassicSpinner(Graphics2D g2, int x, int y, boolean hasMessage) {
        g2.setStroke(new BasicStroke(SPINNER_STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(AppColor.BORDER);
        g2.drawOval(x, y, SPINNER_SIZE, SPINNER_SIZE);

        g2.setColor(AppColor.ACCENT);
        g2.draw(new Arc2D.Double(x, y, SPINNER_SIZE, SPINNER_SIZE, angle, 90, Arc2D.OPEN));

        if (hasMessage) {
            g2.setFont(AppFont.BODY);
            g2.setColor(AppColor.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            int textWidth = fm.stringWidth(message);
            g2.drawString(message, (getWidth() - textWidth) / 2, y + SPINNER_SIZE + 24);
        }
    }

   
    public static JPanel attach(JComponent content, LoadingOverlay overlay) {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new OverlayLayout(wrapper));

        overlay.setAlignmentX(0.5f);
        overlay.setAlignmentY(0.5f);
        content.setAlignmentX(0.5f);
        content.setAlignmentY(0.5f);

        wrapper.add(overlay);
        wrapper.add(content);
        return wrapper;
    }
}