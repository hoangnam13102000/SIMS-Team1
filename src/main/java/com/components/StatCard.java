package com.components;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Stat Card responsive.
 * - Icon cố định bên phải, không đè title.
 * - Title / footer dùng HTML wrap (tối đa 2 dòng) để hiện đủ nội dung khi card hẹp.
 */
public class StatCard extends JPanel {

    private static final int TOP_BORDER_THICKNESS = 2;
    public static final int MIN_CARD_WIDTH = 150;
    /** Chiều cao đủ cho title 2 dòng + value + footer. */
    public static final int PREFERRED_HEIGHT = 118;

    private final Color accentColor;
    private final JLabel valueLabel;
    private final JLabel footerLabel;
    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final boolean compact;
    private final String originalTitle;
    private String originalFooter = " ";

    public StatCard(String label, String value, FontAwesomeSolid icon, Color accentColor) {
        this(label, value, icon, accentColor, false);
    }

    public StatCard(String label, String value, FontAwesomeSolid icon, Color accentColor, boolean compact) {
        this.accentColor = accentColor;
        this.compact = compact;
        this.originalTitle = label != null ? label : "";

        int iconBoxSize = compact ? 28 : 32;
        int iconGlyphSize = compact ? 14 : 16;
        int outerTopPad = compact ? 10 : TOP_BORDER_THICKNESS + 10;
        int outerBottomPad = compact ? 8 : 10;
        int outerSidePad = compact ? 10 : 12;

        setOpaque(false);
        setLayout(new BorderLayout(0, 0));
        setBorder(new EmptyBorder(outerTopPad, outerSidePad, outerBottomPad, outerSidePad));

        JPanel headerRow = new JPanel(new BorderLayout(8, 0));
        headerRow.setOpaque(false);

        titleLabel = new JLabel();
        titleLabel.setFont(AppFont.SMALL_BOLD);
        titleLabel.setForeground(AppColor.TEXT_MUTED);
        titleLabel.setVerticalAlignment(SwingConstants.TOP);
        titleLabel.setToolTipText(originalTitle);
        setWrappedTitle(originalTitle, 120);

        FontIcon fontIcon = FontIcon.of(icon, iconGlyphSize);
        fontIcon.setIconColor(accentColor);

        iconLabel = new JLabel(fontIcon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        Dimension iconDim = new Dimension(iconBoxSize, iconBoxSize);
        iconLabel.setPreferredSize(iconDim);
        iconLabel.setMinimumSize(iconDim);
        iconLabel.setMaximumSize(iconDim);
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 20));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        headerRow.add(titleLabel, BorderLayout.CENTER);
        headerRow.add(iconLabel, BorderLayout.EAST);

        valueLabel = new JLabel(value);
        valueLabel.setFont(AppFont.getXL_Bold());
        valueLabel.setForeground(AppColor.TEXT_TITLE);
        valueLabel.setBorder(new EmptyBorder(compact ? 4 : 6, 0, compact ? 2 : 2, 0));

        footerLabel = new JLabel(" ");
        footerLabel.setFont(AppFont.FOOTER);
        footerLabel.setForeground(AppColor.TEXT_MUTED);
        footerLabel.setVerticalAlignment(SwingConstants.TOP);

        add(headerRow, BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
        add(footerLabel, BorderLayout.SOUTH);

        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                adjustForWidth(getWidth());
            }
        });
    }

    private void setWrappedTitle(String text, int pixelWidth) {
        if (text == null) text = "";
        String safe = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        titleLabel.setText("<html><body style='width:" + Math.max(60, pixelWidth) + "px; margin:0; padding:0;'>"
                + "<div style='font-weight:bold; line-height:1.25;'>" + safe + "</div></body></html>");
    }

    private void setWrappedFooter(String text, int pixelWidth) {
        if (text == null || text.isBlank()) {
            footerLabel.setText(" ");
            return;
        }
        String safe = text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        footerLabel.setText("<html><body style='width:" + Math.max(60, pixelWidth) + "px; margin:0; padding:0;'>"
                + "<div style='line-height:1.2;'>" + safe + "</div></body></html>");
        footerLabel.setToolTipText(text);
    }

    private void adjustForWidth(int width) {
        if (width <= 0) return;

        int sidePad = getInsets().left + getInsets().right;
        int iconW = iconLabel.getPreferredSize().width;
        int textW = Math.max(60, width - sidePad - iconW - 10);

        int newValueSize;
        int newIconSize;
        int newIconBoxSize;

        if (width < 160) {
            newValueSize = compact ? 15 : 17;
            newIconSize = compact ? 12 : 13;
            newIconBoxSize = compact ? 22 : 26;
        } else if (width < 200) {
            newValueSize = compact ? 17 : 20;
            newIconSize = compact ? 13 : 15;
            newIconBoxSize = compact ? 26 : 30;
        } else {
            newValueSize = compact ? 19 : 22;
            newIconSize = compact ? 14 : 16;
            newIconBoxSize = compact ? 28 : 32;
        }

        Font currentValue = valueLabel.getFont();
        if (Math.abs(currentValue.getSize() - newValueSize) > 1) {
            valueLabel.setFont(AppFont.resize(AppFont.getXL_Bold(), newValueSize));
        }

        FontIcon currentIcon = (FontIcon) iconLabel.getIcon();
        if (currentIcon != null && Math.abs(currentIcon.getIconSize() - newIconSize) > 1) {
            FontIcon newIcon = FontIcon.of(currentIcon.getIkon(), newIconSize);
            newIcon.setIconColor(accentColor);
            iconLabel.setIcon(newIcon);
            Dimension box = new Dimension(newIconBoxSize, newIconBoxSize);
            iconLabel.setPreferredSize(box);
            iconLabel.setMinimumSize(box);
            iconLabel.setMaximumSize(box);
        }

        setWrappedTitle(originalTitle, textW);
        if (originalFooter != null && !originalFooter.isBlank()) {
            int footerW = Math.max(60, width - sidePad);
            setWrappedFooter(originalFooter, footerW);
        }
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        int h = Math.max(d.height, compact ? 96 : PREFERRED_HEIGHT);
        int w = Math.max(d.width, MIN_CARD_WIDTH);
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(MIN_CARD_WIDTH - 20, compact ? 80 : 100);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setTrend(String text, boolean positive) {
        originalFooter = text != null ? text : " ";
        footerLabel.setForeground(positive ? AppColor.SUCCESS : AppColor.ERROR);
        int w = getWidth();
        if (w > 0) {
            setWrappedFooter(originalFooter, Math.max(60, w - getInsets().left - getInsets().right));
        } else {
            footerLabel.setText(originalFooter);
            footerLabel.setToolTipText(originalFooter);
        }
    }

    public void setSubtitle(String text) {
        originalFooter = text != null ? text : " ";
        footerLabel.setForeground(AppColor.TEXT_MUTED);
        int w = getWidth();
        if (w > 0) {
            setWrappedFooter(originalFooter, Math.max(60, w - getInsets().left - getInsets().right));
        } else {
            footerLabel.setText(originalFooter);
            footerLabel.setToolTipText(originalFooter);
        }
    }

    public void setIcon(FontAwesomeSolid icon, Color color) {
        FontIcon fontIcon = FontIcon.of(icon, 16);
        fontIcon.setIconColor(color);
        iconLabel.setIcon(fontIcon);
        iconLabel.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 20));
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);

        g2.setClip(shape);
        g2.setColor(AppColor.WHITE);
        g2.fillRect(0, 0, w, h);

        g2.setColor(accentColor);
        g2.fillRect(0, 0, w, TOP_BORDER_THICKNESS);
        g2.setClip(null);

        g2.setColor(AppColor.BORDER);
        g2.draw(shape);

        g2.dispose();
        super.paintComponent(g);
    }
}