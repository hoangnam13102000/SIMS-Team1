package com.components.auth;

import com.theme.AppColor;
import java.awt.geom.Rectangle2D;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

public class PasswordStrengthMeter extends JPanel {

    // Chỉ dùng 3 nhóm màu tiêu chuẩn: Đỏ, Vàng, Xanh lá (5 cấp độ)
    private static final Color[] COLORS = {
            new Color(239, 68, 68),   // Rất yếu - Đỏ
            new Color(245, 158, 11),  // Yếu - Cam/Vàng nhạt
            new Color(234, 179, 8),   // Trung bình - Vàng
            new Color(34, 197, 94),   // Mạnh - Xanh lá
            new Color(16, 185, 129)   // Rất mạnh - Xanh lá đậm
    };

    private static final String[] TITLES = {
            "Rất yếu",
            "Yếu",
            "Trung bình",
            "Mạnh",
            "Rất mạnh"
    };

    private static final String[] DESCRIPTIONS = {
            "Mật khẩu quá ngắn",
            "Thêm chữ hoa hoặc số",
            "Thêm ký tự đặc biệt",
            "Mật khẩu tốt",
            "Mật khẩu rất an toàn"
    };

    private int strength = 0;
    private String password = "";

    private final JPanel bar;
    private final JLabel titleLabel;
    private final JLabel descriptionLabel;

    // Các biến phục vụ Lerp Animation (Trượt độ rộng & Chuyển màu)
    private float currentWidthRatio = 0f;
    private float targetWidthRatio = 0f;
    
    private Color currentColor = COLORS[0];
    private Color targetColor = COLORS[0];

    private Timer animationTimer;

    public PasswordStrengthMeter() {
        setOpaque(false);
        setLayout(new BorderLayout(0, 8));
        setPreferredSize(new Dimension(320, 65));
        setMaximumSize(new Dimension(320, 65));

bar = new JPanel() {
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Bo tròn hai đầu hoàn toàn (Capsule/Pill shape)
        float radius = h/2; 

        // 1. Tạo hình dáng khuôn nền
        Shape trackShape = new RoundRectangle2D.Float(0, 0, w, h, radius, radius);

        // 2. Vẽ Background track
        g2.setColor(new Color(229, 231, 235));
        g2.fill(trackShape);

        // 3. Tiến hành cắt (Clip) theo đúng khuôn track nền
        if (currentWidthRatio > 0.001f) {
            float activeWidth = w * currentWidthRatio;

            // Lưu lại State của Graphics trước khi Clip
            Graphics2D g2Clip = (Graphics2D) g2.create();
            g2Clip.clip(trackShape); // Tất cả những gì vẽ sau đây sẽ bị giới hạn trong khuôn này

            // Gradient màu mượt
            GradientPaint gp = new GradientPaint(
                    0, 0, currentColor.brighter(),
                    activeWidth, 0, currentColor
            );

            g2Clip.setPaint(gp);
            // Vẽ hình chữ nhật phẳng trượt ngang (Khuôn clip sẽ tự bo tròn đầu bên phải/trái)
            g2Clip.fill(new Rectangle2D.Float(0, 0, activeWidth, h));

            // Soft overlay/glow nhẹ
            g2Clip.setColor(new Color(currentColor.getRed(), currentColor.getGreen(), currentColor.getBlue(), 40));
            g2Clip.fill(new Rectangle2D.Float(0, 0, activeWidth, h));

            g2Clip.dispose();
        }

        g2.dispose();
    }
};

        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(320, 8));

        titleLabel = new JLabel("Nhập mật khẩu để kiểm tra");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLabel.setForeground(AppColor.ICON_MUTED);

        descriptionLabel = new JLabel("");
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descriptionLabel.setForeground(AppColor.ICON_MUTED);

        JPanel info = new JPanel(new BorderLayout());
        info.setOpaque(false);
        info.add(titleLabel, BorderLayout.WEST);
        info.add(descriptionLabel, BorderLayout.EAST);

        add(bar, BorderLayout.CENTER);
        add(info, BorderLayout.SOUTH);
        
        initAnimationTimer();
    }

    private void initAnimationTimer() {
        // Chạy ở ~60 FPS (16ms)
        animationTimer = new Timer(16, e -> {
            boolean needRepaint = false;

            // Nội suy chiều rộng (Linear Interpolation - Lerp)
            if (Math.abs(currentWidthRatio - targetWidthRatio) > 0.001f) {
                currentWidthRatio += (targetWidthRatio - currentWidthRatio) * 0.15f;
                needRepaint = true;
            } else {
                currentWidthRatio = targetWidthRatio;
            }

            // Nội suy màu sắc
            if (!currentColor.equals(targetColor)) {
                currentColor = interpolateColor(currentColor, targetColor, 0.15f);
                needRepaint = true;
            }

            if (needRepaint) {
                bar.repaint();
            } else {
                animationTimer.stop();
            }
        });
    }

    public void updateStrength(String password) {
        this.password = password;

        if (password.isEmpty()) {
            targetWidthRatio = 0f;
            targetColor = COLORS[0];
            titleLabel.setText("Nhập mật khẩu để kiểm tra");
            titleLabel.setForeground(AppColor.ICON_MUTED);
            descriptionLabel.setText("");
        } else {
            strength = calculateStrength(password);
            
            targetWidthRatio = (strength + 1) / 5.0f;
            targetColor = COLORS[strength];

            titleLabel.setText(TITLES[strength]);
            titleLabel.setForeground(targetColor);
            descriptionLabel.setText(DESCRIPTIONS[strength]);
            descriptionLabel.setForeground(targetColor);
        }

        if (!animationTimer.isRunning()) {
            animationTimer.start();
        }
    }

    private int calculateStrength(String password) {
        if (password.isEmpty()) return 0;

        int score = 0;
        if (password.length() >= 8) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[^a-zA-Z0-9].*")) score++;

        switch (score) {
            case 0:
            case 1:  return 0;
            case 2:  return 1;
            case 3:  return 2;
            case 4:  return 3;
            default: return 4;
        }
    }

    private Color interpolateColor(Color c1, Color c2, float ratio) {
        int r = (int) (c1.getRed() + ratio * (c2.getRed() - c1.getRed()));
        int g = (int) (c1.getGreen() + ratio * (c2.getGreen() - c1.getGreen()));
        int b = (int) (c1.getBlue() + ratio * (c2.getBlue() - c1.getBlue()));
        return new Color(
                Math.min(255, Math.max(0, r)),
                Math.min(255, Math.max(0, g)),
                Math.min(255, Math.max(0, b))
        );
    }

    public int getStrength() {
        return strength;
    }

    public String getStrengthText() {
        return TITLES[strength];
    }

    public Color getStrengthColor() {
        return COLORS[strength];
    }
}