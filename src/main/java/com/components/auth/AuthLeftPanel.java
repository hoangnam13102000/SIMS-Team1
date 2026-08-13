package com.components.auth;

import com.components.common.CheckIcon;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public class AuthLeftPanel extends JPanel {

    // Anh nen va logo duoc dat trong resources/logo, nap 1 lan (static) va
    // dung lai cho moi instance cua man hinh dang nhap/dang ky.
    private static final BufferedImage BACKGROUND_IMAGE = loadImage("/logo/background.png");
    private static final BufferedImage LOGO_ICON_IMAGE = loadImage("/logo/logo_icon.png");

    public AuthLeftPanel(String title, String description, String[] features) {
        setLayout(new BorderLayout());
        setOpaque(true);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // Logo
        JComponent logo = buildLogoBadge();
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Brand
        JLabel brand = new JLabel("Connect Smart");
        brand.setFont(AppFont.BRAND);
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        brand.setBorder(new EmptyBorder(22, 0, 14, 0));

        // Tagline
        JLabel tagline = new JLabel("<html><div style='width:300px;line-height:150%;'>" 
            + description + "</div></html>");
        tagline.setFont(AppFont.BODY);
        tagline.setForeground(AppColor.DARK_TEXT_MUTED);
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);
        tagline.setBorder(new EmptyBorder(0, 0, 36, 0));

        content.add(logo);
        content.add(brand);
        content.add(tagline);
        
        // Features
        for (String feature : features) {
            content.add(featureRow(feature));
        }

        JPanel centerWrap = new JPanel(new GridBagLayout());
        centerWrap.setOpaque(false);
        centerWrap.setBorder(new EmptyBorder(0, 56, 0, 40));
        GridBagConstraints cgc = new GridBagConstraints();
        cgc.anchor = GridBagConstraints.WEST;
        centerWrap.add(content, cgc);

        JLabel footer = new JLabel(AppConstant.COPYRIGHT);
        footer.setFont(AppFont.FOOTER);
        footer.setForeground(AppColor.DARK_FOOTER);
        footer.setBorder(new EmptyBorder(0, 56, 26, 0));

        add(centerWrap, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }
    
    /**
     * Badge logo o dau panel. Uu tien dung icon that (logo_icon.png - da duoc
     * cat rieng phan bieu tuong va xoa nen trang de dat len nen toi/anh nen).
     * Neu vi ly do nao do khong nap duoc anh, fallback ve badge chu "CS" nhu cu
     * de UI khong bao gio vo hinh.
     */
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
    
    private JPanel featureRow(String text) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(320, Integer.MAX_VALUE));
        row.setBorder(new EmptyBorder(0, 0, 14, 0));

        JLabel badge = new JLabel(new CheckIcon());
        badge.setVerticalAlignment(SwingConstants.CENTER);
        badge.setAlignmentY(Component.CENTER_ALIGNMENT);

        JLabel label = new JLabel(text);
        label.setFont(AppFont.BODY);
        label.setForeground(AppColor.DARK_FEATURE_TEXT);
        label.setAlignmentY(Component.CENTER_ALIGNMENT);

        row.add(badge);
        row.add(Box.createHorizontalStrut(10));
        row.add(label);
        row.add(Box.createHorizontalGlue());
        return row;
    }

    private static BufferedImage loadImage(String classpathLocation) {
        try (InputStream in = AuthLeftPanel.class.getResourceAsStream(classpathLocation)) {
            if (in == null) return null;
            return ImageIO.read(in);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Ve anh phu kin toan bo vung dich theo kieu "cover" (giu nguyen ti le,
     * scale sao cho canh nho nhat khop voi vung dich, phan du duoc can giua
     * va cat bot) - tranh bi meo hinh hoac chua het khung nhu "stretch" thong
     * thuong.
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
            // Phu 1 lop toi nhe (dam hon o tren, nhat hon o duoi) de dam bao
            // chu trang/nhan luon du tuong phan tren moi vung cua anh nen,
            // du anh sang mau hay toi mau o tung khu vuc khac nhau.
            GradientPaint scrim = new GradientPaint(
                    0, 0, new Color(4, 16, 34, 150),
                    getWidth() * 0.4f, getHeight(), new Color(4, 16, 34, 70));
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