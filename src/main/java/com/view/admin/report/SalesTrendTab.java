package com.view.admin.report;

import com.components.dashboard.DashboardCard;
import com.components.report.MonthlyCategoryTrendPanel;
import com.dao.RevenueReportDAO.CategorySeries;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Noi dung tab "Xu hướng bán hàng" trong trang gop {@link RevenueReportPanel}:
 * bieu do duong the hien SO LUONG san pham ban ra MOI THANG, gop nhom theo
 * DANH MUC, trong khoang thoi gian dang loc o filter bar chung. Hang chu
 * thich (legend) duoi bieu do la cac chip co the BAM DE AN/HIEN tung duong -
 * huu ich khi co nhieu danh muc chong cheo, kho nhin rieng 1 duong.
 */
class SalesTrendTab extends JPanel {

    private MonthlyCategoryTrendPanel chartPanel;
    private JPanel legendPanel;
    private final List<JToggleButton> legendChips = new ArrayList<>();

    private MonthlyCategoryTrend lastTrend;

    SalesTrendTab() {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel content = new RevenueReportTab.ScrollableColumn();
        content.add(buildChartCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(scroll, BorderLayout.CENTER);
    }

    MonthlyCategoryTrend getLastTrend() {
        return lastTrend;
    }

    // ---------------------------------------------------------------
    // Xay giao dien
    // ---------------------------------------------------------------

    private DashboardCard buildChartCard() {
        DashboardCard card = new DashboardCard("Xu hướng bán hàng theo tháng & danh mục",
                "Số lượng sản phẩm bán ra mỗi tháng, gộp nhóm theo danh mục (chỉ tính hóa đơn hợp lệ)",
                FontAwesomeSolid.CHART_LINE, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 420));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));

        chartPanel = new MonthlyCategoryTrendPanel();

        legendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        legendPanel.setOpaque(false);
        legendPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.SM));
        body.setOpaque(false);
        body.add(chartPanel, BorderLayout.CENTER);
        body.add(legendPanel, BorderLayout.SOUTH);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    // ---------------------------------------------------------------
    // Ap du lieu
    // ---------------------------------------------------------------

    void applyData(MonthlyCategoryTrend trend) {
        this.lastTrend = trend;
        chartPanel.setData(trend);
        renderLegend(trend);
    }

    private void renderLegend(MonthlyCategoryTrend trend) {
        legendPanel.removeAll();
        legendChips.clear();

        List<CategorySeries> series = trend != null && trend.series != null ? trend.series : Collections.emptyList();
        if (series.isEmpty()) {
            JLabel empty = new JLabel("Không có dữ liệu trong khoảng thời gian này");
            empty.setFont(AppFont.SMALL);
            empty.setForeground(AppColor.TEXT_MUTED);
            legendPanel.add(empty);
        } else {
            for (int i = 0; i < series.size(); i++) {
                CategorySeries s = series.get(i);
                Color color = chartPanel.colorFor(i);
                String text = s.categoryName + "  ·  " + NumberUtil.formatThousands(s.totalQuantity);

                JToggleButton chip = buildLegendChip(text, color);
                int index = i;
                chip.addItemListener(e -> chartPanel.setSeriesVisible(index, chip.isSelected()));
                legendChips.add(chip);
                legendPanel.add(chip);
            }
        }
        legendPanel.revalidate();
        legendPanel.repaint();
    }

    /** Chip bo tron, mau theo duong tuong ung - bam de an/hien (giu trang thai bang JToggleButton, khong can bien rieng). */
    private JToggleButton buildLegendChip(String label, Color color) {
        JToggleButton chip = new JToggleButton("\u25CF  " + label, true);
        chip.setFont(AppFont.SMALL_BOLD);
        chip.setForeground(color);
        chip.setFocusPainted(false);
        chip.setContentAreaFilled(false);
        chip.setOpaque(false);
        chip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1, true),
                new EmptyBorder(4, 10, 4, 10)));
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setToolTipText("Bấm để ẩn/hiện danh mục này trên biểu đồ");
        chip.addItemListener(e -> {
            boolean on = chip.isSelected();
            chip.setForeground(on ? color : AppColor.TEXT_DISABLED);
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(on ? color : AppColor.BORDER, 1, true),
                    new EmptyBorder(4, 10, 4, 10)));
        });
        return chip;
    }
}
