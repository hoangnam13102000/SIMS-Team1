package com.components.dashboard;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Khung card trang, bo goc, dung chung cho moi khoi noi dung tren trang
 * Dashboard (bieu do doanh thu, ty le trang thai don, san pham ban chay,
 * don hang gan day...). Header gom 1 icon badge mau nhe + tieu de + mo ta
 * phu (tuy chon) + 1 khu vuc hanh dong ben phai (tuy chon, vd nut lam moi).
 *
 * Cac component con chi can goi getContentPanel() va them noi dung vao do,
 * khong can tu ve lai border/bo goc/tieu de.
 */
public class DashboardCard extends JPanel {

    private final JPanel contentPanel;
    private final JPanel headerRow;

    public DashboardCard(String title, FontAwesomeSolid icon, Color accentColor) {
        this(title, null, icon, accentColor);
    }

    public DashboardCard(String title, String subtitle, FontAwesomeSolid icon, Color accentColor) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);
        headerRow.setBorder(new EmptyBorder(0, 0, AppSpacing.MD, 0));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.add(buildIconBadge(icon, accentColor));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLine.add(titleLabel);

        titleBlock.add(titleLine);

        if (subtitle != null && !subtitle.isBlank()) {
            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(AppFont.SMALL);
            subtitleLabel.setForeground(AppColor.TEXT_MUTED);
            subtitleLabel.setBorder(new EmptyBorder(4, 44, 0, 0));
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleBlock.add(subtitleLabel);
        }

        headerRow.add(titleBlock, BorderLayout.WEST);

        contentPanel = new JPanel(new BorderLayout());
        contentPanel.setOpaque(false);

        add(headerRow, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
    }

    private JPanel buildIconBadge(FontAwesomeSolid icon, Color accentColor) {
        JPanel badge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue(), 30));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.dispose();
            }
        };
        badge.setOpaque(false);
        badge.setPreferredSize(new Dimension(32, 32));
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(accentColor);
        badge.add(new JLabel(fontIcon));
        return badge;
    }

    /** Them 1 thanh phan (vd nut lam moi) vao goc phai cua header. */
    public void setHeaderAction(JComponent action) {
        headerRow.add(action, BorderLayout.EAST);
    }

    public JPanel getContentPanel() {
        return contentPanel;
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
        g2.setColor(AppColor.WHITE);
        g2.fill(shape);
        g2.setColor(AppColor.BORDER);
        g2.draw(shape);
        g2.dispose();
        super.paintComponent(g);
    }
}