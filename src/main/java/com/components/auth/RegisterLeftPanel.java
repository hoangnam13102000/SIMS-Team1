package com.components.auth;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class RegisterLeftPanel extends JPanel {
    
    private final PasswordStrengthMeter strengthMeter;
    
    public RegisterLeftPanel() {
        setLayout(new BorderLayout());
        setOpaque(true);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Logo
        JComponent logo = buildLogoBadge();
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel brand = new JLabel("Connect Smart");
        brand.setFont(AppFont.BRAND);
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(new EmptyBorder(22, 0, 14, 0));

        // ===== Hướng dẫn đăng ký với Icon =====
        JPanel guideTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        guideTitlePanel.setOpaque(false);
        guideTitlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        guideTitlePanel.setMaximumSize(new Dimension(320, 30));
        
        // Icon cho tiêu đề hướng dẫn
        FontIcon guideIcon = FontIcon.of(FontAwesomeSolid.CLIPBOARD_LIST, 16);
        guideIcon.setIconColor(AppColor.ACCENT);
        JLabel guideIconLabel = new JLabel(guideIcon);
        
        JLabel guideTitle = new JLabel(" Hướng dẫn đăng ký");
        guideTitle.setFont(AppFont.HEADING_MD);
        guideTitle.setForeground(Color.WHITE);
        
        guideTitlePanel.add(guideIconLabel);
        guideTitlePanel.add(guideTitle);

        String[] steps = {
            "Nhập Gmail hợp lệ (định dạng @gmail.com)",
            "Tạo tên đăng nhập (không chứa ký tự đặc biệt)",
            "Đặt mật khẩu mạnh (≥ 6 ký tự, có số và chữ hoa)",
            "Nhập lại mật khẩu để xác nhận",
            "Xác nhận mã OTP được gửi qua email",
            "Hoàn tất đăng ký và đăng nhập ngay"
        };

        JPanel guidePanel = new JPanel();
        guidePanel.setOpaque(false);
        guidePanel.setLayout(new BoxLayout(guidePanel, BoxLayout.Y_AXIS));
        guidePanel.setBorder(new EmptyBorder(8, 0, 24, 0));
        
        for (int i = 0; i < steps.length; i++) {
            JPanel stepRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            stepRow.setOpaque(false);
            stepRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            stepRow.setMaximumSize(new Dimension(320, 26));
            
            // Số thứ tự với icon
            JLabel numberLabel = new JLabel((i + 1) + ".");
            numberLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
            numberLabel.setForeground(AppColor.ACCENT);
            numberLabel.setPreferredSize(new Dimension(20, 20));
            
            JLabel stepLabel = new JLabel(" " + steps[i]);
            stepLabel.setFont(AppFont.SMALL);
            stepLabel.setForeground(AppColor.DARK_FEATURE_TEXT);
            
            stepRow.add(numberLabel);
            stepRow.add(stepLabel);
            guidePanel.add(stepRow);
            guidePanel.add(Box.createVerticalStrut(4));
        }

        // ===== PASSWORD STRENGTH METER =====
        JPanel strengthTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        strengthTitlePanel.setOpaque(false);
        strengthTitlePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        strengthTitlePanel.setMaximumSize(new Dimension(320, 30));
        
        // Icon cho độ mạnh mật khẩu
        FontIcon strengthIcon = FontIcon.of(FontAwesomeSolid.SHIELD_ALT, 16);
        strengthIcon.setIconColor(AppColor.ACCENT);
        JLabel strengthIconLabel = new JLabel(strengthIcon);
        
        JLabel strengthTitle = new JLabel(" Độ mạnh mật khẩu");
        strengthTitle.setFont(AppFont.HEADING_MD);
        strengthTitle.setForeground(Color.WHITE);
        
        strengthTitlePanel.add(strengthIconLabel);
        strengthTitlePanel.add(strengthTitle);

        strengthMeter = new PasswordStrengthMeter();
        strengthMeter.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(logo);
        content.add(brand);
        content.add(Box.createVerticalStrut(12));
        content.add(guideTitlePanel);
        content.add(guidePanel);
        content.add(strengthTitlePanel);
        content.add(Box.createVerticalStrut(8));
        content.add(strengthMeter);

        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(0, 40, 0, 40));
        GridBagConstraints cgc = new GridBagConstraints();
        cgc.anchor = GridBagConstraints.WEST;
        cgc.fill = GridBagConstraints.HORIZONTAL;
        centerWrap.add(content, cgc);

        JLabel footer = new JLabel(AppConstant.COPYRIGHT);
        footer.setFont(AppFont.FOOTER);
        footer.setForeground(AppColor.DARK_FOOTER);
        footer.setBorder(new EmptyBorder(0, 40, 26, 0));

        add(centerWrap, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }
    
    public void updatePasswordStrength(String password) {
        strengthMeter.updateStrength(password);
    }
    
    private JComponent buildLogoBadge() {
        JComponent badge = new JComponent() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT);
                g2.fillRoundRect(0, 0, 44, 44, 12, 12);
                g2.setColor(Color.WHITE);
                g2.setFont(AppFont.HEADING_LG);
                FontMetrics fm = g2.getFontMetrics();
                String s = "CS";
                int x = (44 - fm.stringWidth(s)) / 2;
                int y = (44 - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(s, x, y);
                g2.dispose();
            }
        };
        badge.setPreferredSize(new Dimension(44, 44));
        badge.setMaximumSize(new Dimension(44, 44));
        badge.setOpaque(false);
        return badge;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, AppColor.DARK_TOP, getWidth(), getHeight(), AppColor.DARK_BOTTOM);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(255, 255, 255, 30));
        g2.fillOval(getWidth() - 260, -140, 420, 420);
        g2.setColor(new Color(255, 255, 255, 16));
        g2.fillOval(-180, getHeight() - 220, 380, 380);
        g2.dispose();
    }
}