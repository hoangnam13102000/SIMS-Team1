package com.components.auth;

import com.components.common.CheckIcon;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AuthLeftPanel extends JPanel {
    
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
        JLabel brand = new JLabel("Phone Store");
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
                String s = "P";
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
    
    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        GradientPaint gp = new GradientPaint(0, 0, AppColor.DARK_TOP, getWidth(), getHeight(), AppColor.DARK_BOTTOM);
        g2.setPaint(gp);
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.setColor(new Color(99, 102, 241, 35));
        g2.fillOval(getWidth() - 260, -140, 420, 420);
        g2.setColor(new Color(99, 102, 241, 18));
        g2.fillOval(-180, getHeight() - 220, 380, 380);
        g2.dispose();
    }
}