package com.components.auth;


import com.theme.AppColor;
import com.theme.AppRadius;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Password Strength Meter hiện đại với animation và hiển thị chi tiết
 */
public class PasswordStrengthMeter extends JPanel {
    
    private int strength = 0; // 0-4
    private String password = "";
    
    // Màu sắc hiện đại
    private static final Color[] COLORS = {
        AppColor.RED_ALT,     // Yếu - Đỏ đậm
        AppColor.YELLOW,     // Trung bình - Vàng
        AppColor.BLUE,    // Khá - Xanh dương
        AppColor.GREEN,     // Mạnh - Xanh lá
        AppColor.TEAL     // Rất mạnh - Xanh ngọc
    };
    
    private static final Color[] BG_COLORS = {
        new Color(239, 68, 68, 30),
        new Color(234, 179, 8, 30),
        new Color(59, 130, 246, 30),
        new Color(34, 197, 94, 30),
        new Color(16, 185, 129, 30)
    };
    
    private static final String[] TEXTS = {
        "Rất yếu", "Yếu", "Trung bình", "Mạnh", "Rất mạnh"
    };
    
    private static final String[] DESCRIPTIONS = {
        "Thêm ít nhất 6 ký tự",
        "Thêm chữ hoa và số",
        "Thêm ký tự đặc biệt",
        "Mật khẩu tốt!",
        "Mật khẩu tuyệt vời!"
    };
    
    private static final Color[] DESC_COLORS = {
        AppColor.RED_ALT,
        AppColor.YELLOW,
        AppColor.BLUE,
        AppColor.GREEN,
        AppColor.TEAL
    };
    
    private JLabel strengthLabel;
    private JLabel descriptionLabel;
    private JPanel barPanel;
    private JPanel infoPanel;
    private Timer animationTimer;
    private int animatedStrength = 0;
    private float animationProgress = 0f;
    
    public PasswordStrengthMeter() {
        setLayout(new BorderLayout(0, 8));
        setOpaque(false);
        setPreferredSize(new Dimension(320, 70));
        setMaximumSize(new Dimension(320, 70));
        
        initComponents();
    }
    
    private void initComponents() {
        // ===== BAR PANEL =====
        barPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                int width = getWidth();
                int height = getHeight();
                int radius = height / 2;
                
                // Background
                g2.setColor(new Color(229, 231, 235));
                g2.fillRoundRect(0, 0, width, height, radius, radius);
                
                // Animated progress
                if (animatedStrength > 0) {
                    int targetWidth = (width * animatedStrength) / 4;
                    float progress = Math.min(1f, animationProgress);
                    int currentWidth = (int) (targetWidth * progress);
                    
                    if (currentWidth > 0) {
                        Color color = COLORS[animatedStrength];
                        g2.setColor(color);
                        g2.fillRoundRect(0, 0, currentWidth, height, radius, radius);
                        
                        // Glow effect
                        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 40));
                        g2.fillRoundRect(0, -2, currentWidth, height + 4, radius + 2, radius + 2);
                    }
                }
                
                g2.dispose();
            }
        };
        barPanel.setPreferredSize(new Dimension(320, 8));
        barPanel.setMaximumSize(new Dimension(320, 8));
        barPanel.setOpaque(false);
        
        // ===== INFO PANEL =====
        infoPanel = new JPanel(new BorderLayout(0, 2));
        infoPanel.setOpaque(false);
        
        strengthLabel = new JLabel("Nhập mật khẩu để kiểm tra");
        strengthLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        strengthLabel.setForeground(AppColor.ICON_MUTED);
        
        descriptionLabel = new JLabel("");
        descriptionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        descriptionLabel.setForeground(AppColor.ICON_MUTED);
        
        infoPanel.add(strengthLabel, BorderLayout.WEST);
        infoPanel.add(descriptionLabel, BorderLayout.EAST);
        
        // ===== ADD =====
        add(barPanel, BorderLayout.CENTER);
        add(infoPanel, BorderLayout.SOUTH);
    }
    
    public void updateStrength(String password) {
        this.password = password;
        int newStrength = calculateStrength(password);
        
        if (newStrength != strength) {
            strength = newStrength;
            animatedStrength = strength;
            animationProgress = 0f;
            
            // Animate
            if (animationTimer != null && animationTimer.isRunning()) {
                animationTimer.stop();
            }
            
            animationTimer = new Timer(16, e -> {
                animationProgress += 0.05f;
                if (animationProgress >= 1f) {
                    animationProgress = 1f;
                    animationTimer.stop();
                }
                barPanel.repaint();
                updateLabels();
            });
            animationTimer.start();
        }
        
        updateLabels();
        barPanel.repaint();
    }
    
    private void updateLabels() {
        if (password.isEmpty()) {
            strengthLabel.setText("Nhập mật khẩu để kiểm tra");
            strengthLabel.setForeground(AppColor.ICON_MUTED);
            descriptionLabel.setText("");
            return;
        }
        
        strengthLabel.setText(TEXTS[strength]);
        strengthLabel.setForeground(COLORS[strength]);
        
        descriptionLabel.setText(DESCRIPTIONS[strength]);
        descriptionLabel.setForeground(DESC_COLORS[strength]);
    }
    
    private int calculateStrength(String password) {
        if (password.isEmpty()) return 0;
        
        int score = 0;
        
        // 1. Độ dài
        if (password.length() >= 6) score++;
        if (password.length() >= 10) score++;
        
        // 2. Có chữ hoa
        if (password.matches(".*[A-Z].*")) score++;
        
        // 3. Có chữ thường
        if (password.matches(".*[a-z].*")) score++;
        
        // 4. Có số
        if (password.matches(".*\\d.*")) score++;
        
        // 5. Có ký tự đặc biệt
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*")) score++;
        
        // Tính điểm: 0-8 -> scale về 0-4
        if (score <= 2) return 0;
        if (score <= 4) return 1;
        if (score <= 5) return 2;
        if (score <= 6) return 3;
        return 4;
    }
    
    public int getStrength() {
        return strength;
    }
    
    public String getStrengthText() {
        return TEXTS[strength];
    }
    
    public Color getStrengthColor() {
        return COLORS[strength];
    }
}