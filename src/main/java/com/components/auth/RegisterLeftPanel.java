package com.components.auth;

import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class RegisterLeftPanel extends JPanel {

    // Anh nen va logo dung chung voi trang dang nhap (resources/logo), nap 1
    // lan (static) va dung lai cho moi instance cua man hinh dang ky.
    private static final BufferedImage BACKGROUND_IMAGE = loadImage("/logo/background.png");
    private static final BufferedImage LOGO_ICON_IMAGE = loadImage("/logo/logo_icon.png");

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

        JLabel brand = new JLabel("Connect Mart");
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
        if (LOGO_ICON_IMAGE != null) {
            int size = 54;
            Image scaled = LOGO_ICON_IMAGE.getScaledInstance(size, size, Image.SCALE_SMOOTH);
            JLabel logoLabel = new JLabel(new ImageIcon(scaled));
            logoLabel.setOpaque(false);
            Dimension dim = new Dimension(size, size);
            logoLabel.setPreferredSize(dim);
            logoLabel.setMinimumSize(dim);
            logoLabel.setMaximumSize(dim);
            return logoLabel;
        }

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

    private static BufferedImage loadImage(String classpathLocation) {
        try (InputStream in = RegisterLeftPanel.class.getResourceAsStream(classpathLocation)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Ve anh phu kin toan bo vung dich theo kieu "cover" (giu nguyen ti le,
     * scale sao cho canh nho nhat khop voi vung dich, phan du duoc can giua
     * va cat bot).
     */
    private static void drawCoverImage(Graphics2D g2, BufferedImage img, int targetW, int targetH) {
        if (targetW <= 0 || targetH <= 0) return;
        double scale = Math.max((double) targetW / img.getWidth(), (double) targetH / img.getHeight());
        int scaledW = (int) Math.ceil(img.getWidth() * scale);
        int scaledH = (int) Math.ceil(img.getHeight() * scale);
        int x = (targetW - scaledW) / 2;
        int y = (targetH - scaledH) / 2;
        g2.drawImage(img, x, y, scaledW, scaledH, null);
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (BACKGROUND_IMAGE != null) {
            drawCoverImage(g2, BACKGROUND_IMAGE, getWidth(), getHeight());
            // Phu 1 lop toi nhe de dam bao chu trang/nhan luon du tuong phan
            // tren moi vung cua anh nen (trang dang ky co nhieu chu hon trang
            // dang nhap nen can do tuong phan on dinh tren toan bo chieu cao).
            GradientPaint scrim = new GradientPaint(
                    0, 0, new Color(4, 16, 34, 150),
                    getWidth() * 0.4f, getHeight(), new Color(4, 16, 34, 90));
            g2.setPaint(scrim);
            g2.fillRect(0, 0, getWidth(), getHeight());
        } else {
            GradientPaint gp = new GradientPaint(0, 0, AppColor.DARK_TOP, getWidth(), getHeight(), AppColor.DARK_BOTTOM);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
        }

        g2.setColor(new Color(255, 255, 255, 22));
        g2.fillOval(getWidth() - 260, -140, 420, 420);
        g2.setColor(new Color(255, 255, 255, 14));
        g2.fillOval(-180, getHeight() - 220, 380, 380);
        g2.dispose();
    }
}