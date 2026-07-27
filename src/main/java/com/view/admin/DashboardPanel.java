package com.view.admin;

import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.i18n.Lang;
import com.theme.AppColor;
import com.theme.AppSpacing;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class DashboardPanel extends JPanel {

    public DashboardPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildSampleCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        row.add(new StatCard(Lang.get("dashboard.stat.users"), "0", FontAwesomeSolid.USERS, AppColor.ACCENT));
        row.add(new StatCard(Lang.get("dashboard.stat.activityToday"), "0", FontAwesomeSolid.BOLT, AppColor.SUCCESS));
        row.add(new StatCard(Lang.get("dashboard.stat.alerts"), "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING));

        return row;
    }

    private DashboardCard buildSampleCard() {
        DashboardCard card = new DashboardCard(
                Lang.get("dashboard.sample.title"),
                Lang.get("dashboard.sample.subtitle"),
                FontAwesomeSolid.CHART_BAR,
                AppColor.ACCENT
        );
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 220));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 220));

        JLabel placeholder = new JLabel(
                Lang.get("dashboard.sample.placeholder"),
                SwingConstants.CENTER);
        placeholder.setForeground(AppColor.TEXT_MUTED);
        card.getContentPanel().add(placeholder, BorderLayout.CENTER);

        return card;
    }
}