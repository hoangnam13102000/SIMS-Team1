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
 * Stat Card với icon Ikonli FontAwesome
 */
public class StatCard extends JPanel {

    private static final int TOP_BORDER_THICKNESS = 2;

    private final Color accentColor;
    private final JLabel valueLabel;
    private final JLabel footerLabel;
    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final boolean compact;

    public StatCard(String label, String value, FontAwesomeSolid icon, Color accentColor) {
        this(label, value, icon, accentColor, false);
    }

    public StatCard(String label, String value, FontAwesomeSolid icon, Color accentColor, boolean compact) {
        this.accentColor = accentColor;
        this.compact = compact;

        int iconBoxSize = compact ? 28 : 34;
        int iconGlyphSize = compact ? 15 : 18;
        int outerTopPad = compact ? 10 : TOP_BORDER_THICKNESS + 14;
        int outerBottomPad = compact ? 8 : 14;
        int outerSidePad = compact ? 14 : 16;

        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(outerTopPad, outerSidePad, outerBottomPad, outerSidePad));

        // Header: Label + Icon
        JPanel headerRow = new JPanel(new BorderLayout(12, 0));
        headerRow.setOpaque(false);

        titleLabel = new JLabel(label.toUpperCase());
        titleLabel.setFont(AppFont.SMALL_BOLD);
        titleLabel.setForeground(AppColor.TEXT_MUTED);

        // Tạo icon
        FontIcon fontIcon = FontIcon.of(icon, iconGlyphSize);
        fontIcon.setIconColor(accentColor);

        iconLabel = new JLabel(fontIcon);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(iconBoxSize, iconBoxSize));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 20));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(compact ? 6 : 8, compact ? 6 : 8, compact ? 6 : 8, compact ? 6 : 8));

        headerRow.add(titleLabel, BorderLayout.WEST);
        headerRow.add(iconLabel, BorderLayout.EAST);

        // Value
        valueLabel = new JLabel(value);
        valueLabel.setFont(AppFont.getXL_Bold());
        valueLabel.setForeground(AppColor.TEXT_TITLE);
        valueLabel.setBorder(new EmptyBorder(compact ? 6 : 10, 0, compact ? 2 : 4, 0));

        // Footer
        footerLabel = new JLabel(" ");
        footerLabel.setFont(AppFont.SMALL_BOLD);
        footerLabel.setForeground(AppColor.TEXT_MUTED);

        add(headerRow, BorderLayout.NORTH);
        add(valueLabel, BorderLayout.CENTER);
        add(footerLabel, BorderLayout.SOUTH);

        // Responsive
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                adjustForWidth(getWidth());
            }
        });
    }

    private void adjustForWidth(int width) {
        if (width <= 0) return;

        int newValueSize;
        int newTitleSize;
        int newIconSize;
        int newIconBoxSize;
        int newPadding;
        
        if (width < 170) {
            newValueSize = compact ? 16 : 18;
            newTitleSize = 9;
            newIconSize = compact ? 12 : 14;
            newIconBoxSize = compact ? 22 : 26;
            newPadding = compact ? 4 : 6;
        } else if (width < 220) {
            newValueSize = compact ? 18 : 21;
            newTitleSize = 10;
            newIconSize = compact ? 13 : 16;
            newIconBoxSize = compact ? 25 : 30;
            newPadding = compact ? 5 : 7;
        } else {
            newValueSize = compact ? 19 : 24;
            newTitleSize = 12;
            newIconSize = compact ? 15 : 18;
            newIconBoxSize = compact ? 28 : 34;
            newPadding = compact ? 6 : 8;
        }

        // Resize value label
        Font currentValue = valueLabel.getFont();
        if (Math.abs(currentValue.getSize() - newValueSize) > 1) {
            valueLabel.setFont(AppFont.resize(AppFont.getXL_Bold(), newValueSize));
        }

        // Resize title label
        Font currentTitle = titleLabel.getFont();
        if (Math.abs(currentTitle.getSize() - newTitleSize) > 1) {
            titleLabel.setFont(AppFont.resize(AppFont.SMALL_BOLD, newTitleSize));
        }

        // Resize icon
        FontIcon currentIcon = (FontIcon) iconLabel.getIcon();
        if (currentIcon != null && Math.abs(currentIcon.getIconSize() - newIconSize) > 1) {
            FontIcon newIcon = FontIcon.of(currentIcon.getIkon(), newIconSize);
            newIcon.setIconColor(accentColor);
            iconLabel.setIcon(newIcon);
            iconLabel.setPreferredSize(new Dimension(newIconBoxSize, newIconBoxSize));
            iconLabel.setBorder(BorderFactory.createEmptyBorder(newPadding, newPadding, newPadding, newPadding));
        }

        // Resize footer
        Font currentFooter = footerLabel.getFont();
        int newFooterSize = Math.max(9, newTitleSize - 1);
        if (Math.abs(currentFooter.getSize() - newFooterSize) > 1) {
            footerLabel.setFont(AppFont.resize(AppFont.SMALL_BOLD, newFooterSize));
        }
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }

    public void setTrend(String text, boolean positive) {
        footerLabel.setText(text);
        footerLabel.setForeground(positive ? AppColor.SUCCESS : AppColor.ERROR);
    }

    public void setSubtitle(String text) {
        footerLabel.setText(text);
        footerLabel.setForeground(AppColor.TEXT_MUTED);
    }

    public void setIcon(FontAwesomeSolid icon, Color color) {
        FontIcon fontIcon = FontIcon.of(icon, 18);
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