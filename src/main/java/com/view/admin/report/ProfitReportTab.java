package com.view.admin.report;

import com.components.dashboard.DashboardCard;
import com.components.report.RevenueChartPanel;
import com.components.StatCard;
import com.dao.RevenueReportDAO.CategoryProfit;
import com.dao.RevenueReportDAO.DailyPoint;
import com.dao.RevenueReportDAO.ProductProfit;
import com.dao.RevenueReportDAO.ProfitSummary;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Noi dung tab "Loi nhuan" trong trang gop {@link RevenueReportPanel}: 4 the
 * thong ke (doanh thu/gia von/loi nhuan/bien loi nhuan - tat ca tinh tren co
 * so CHUA VAT, xem ghi chu trong {@link com.dao.RevenueReportDAO}) + bieu do
 * loi nhuan theo ngay + loi nhuan theo danh muc + top san pham theo loi nhuan.
 */
class ProfitReportTab extends JPanel {

    private StatCard revenueCard;
    private StatCard costCard;
    private StatCard profitCard;
    private StatCard marginCard;

    private RevenueChartPanel chartPanel;
    private JPanel categoryListPanel;
    private JPanel topProductsListPanel;

    private List<DailyPoint> lastDaily = new ArrayList<>();

    ProfitReportTab() {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel content = new RevenueReportTab.ScrollableColumn();
        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildChartCard());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildBottomRow());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(scroll, BorderLayout.CENTER);
    }

    List<DailyPoint> getLastDaily() {
        return lastDaily;
    }

    // ---------------------------------------------------------------
    // Xay giao dien
    // ---------------------------------------------------------------

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        revenueCard = new StatCard("Doanh thu (chưa VAT)", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        costCard = new StatCard("Tổng giá vốn", "0 đ", FontAwesomeSolid.TRUCK, AppColor.WARNING);
        profitCard = new StatCard("Lợi nhuận gộp", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.SUCCESS);
        marginCard = new StatCard("Biên lợi nhuận", "0%", FontAwesomeSolid.PERCENTAGE, AppColor.INFO);

        row.add(revenueCard);
        row.add(costCard);
        row.add(profitCard);
        row.add(marginCard);
        return row;
    }

    private DashboardCard buildChartCard() {
        DashboardCard card = new DashboardCard("Lợi nhuận theo ngày",
                "Doanh thu (chưa VAT) trừ giá vốn, chỉ tính hóa đơn hợp lệ",
                FontAwesomeSolid.CHART_BAR, AppColor.SUCCESS);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 300));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        chartPanel = new RevenueChartPanel();
        card.getContentPanel().add(chartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        row.setPreferredSize(new Dimension(10, 320));

        row.add(buildCategoryCard());
        row.add(buildTopProductsCard());
        return row;
    }

    private DashboardCard buildCategoryCard() {
        DashboardCard card = new DashboardCard("Lợi nhuận theo danh mục",
                "Sắp xếp giảm dần theo lợi nhuận",
                FontAwesomeSolid.TAGS, AppColor.INFO);
        categoryListPanel = new RevenueReportTab.ScrollableColumn();
        card.getContentPanel().add(plainScroll(categoryListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTopProductsCard() {
        DashboardCard card = new DashboardCard("Top sản phẩm theo lợi nhuận",
                FontAwesomeSolid.TROPHY, AppColor.WARNING);
        topProductsListPanel = new RevenueReportTab.ScrollableColumn();
        card.getContentPanel().add(plainScroll(topProductsListPanel), BorderLayout.CENTER);
        return card;
    }

    private static JScrollPane plainScroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ---------------------------------------------------------------
    // Ap du lieu
    // ---------------------------------------------------------------

    void applyData(ProfitSummary summary, List<DailyPoint> daily, List<CategoryProfit> categories, List<ProductProfit> topProducts) {
        lastDaily = daily;

        revenueCard.setValue(NumberUtil.formatThousands(summary.totalRevenue.longValue()) + " đ");
        revenueCard.setSubtitle("Chưa gồm VAT - xem tab \"Doanh thu\" để có số đã gồm VAT");

        costCard.setValue(NumberUtil.formatThousands(summary.totalCost.longValue()) + " đ");
        costCard.setSubtitle("Theo giá nhập hiện tại của sản phẩm");

        boolean profitPositive = summary.totalProfit.signum() >= 0;
        profitCard.setValue(NumberUtil.formatThousands(summary.totalProfit.longValue()) + " đ");
        profitCard.setTrend(profitPositive ? "Lãi" : "Lỗ", profitPositive);

        Double margin = summary.marginPercent();
        if (margin == null) {
            marginCard.setValue("0%");
            marginCard.setSubtitle("Không có doanh thu để tính");
        } else {
            marginCard.setValue(NumberUtil.formatDecimal(margin, 1) + "%");
            marginCard.setSubtitle("Lợi nhuận ÷ doanh thu");
        }

        chartPanel.setData(daily);

        renderCategories(categories);
        renderTopProducts(topProducts);
    }

    // ---------------------------------------------------------------
    // Danh sach: loi nhuan theo danh muc
    // ---------------------------------------------------------------

    private void renderCategories(List<CategoryProfit> categories) {
        categoryListPanel.removeAll();
        if (categories.isEmpty()) {
            categoryListPanel.add(emptyRow("Không có dữ liệu trong khoảng thời gian này"));
        } else {
            for (CategoryProfit c : categories) {
                categoryListPanel.add(buildCategoryRow(c));
                categoryListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        categoryListPanel.revalidate();
        categoryListPanel.repaint();
    }

    private JPanel buildCategoryRow(CategoryProfit c) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setBorder(new EmptyBorder(6, 8, 6, 8));

        JLabel nameLabel = new JLabel(c.categoryName);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        boolean positive = c.profit.signum() >= 0;
        JLabel profitLabel = new JLabel(NumberUtil.formatThousands(c.profit.longValue()) + " đ");
        profitLabel.setFont(AppFont.SMALL_BOLD);
        profitLabel.setForeground(positive ? AppColor.SUCCESS : AppColor.ERROR);
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profitLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel revenueLabel = new JLabel("DT: " + NumberUtil.formatThousands(c.revenue.longValue()) + " đ");
        revenueLabel.setFont(AppFont.FOOTER);
        revenueLabel.setForeground(AppColor.TEXT_MUTED);
        revenueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        revenueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(profitLabel);
        right.add(revenueLabel);

        row.add(nameLabel, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    // ---------------------------------------------------------------
    // Danh sach: top san pham theo loi nhuan
    // ---------------------------------------------------------------

    private void renderTopProducts(List<ProductProfit> products) {
        topProductsListPanel.removeAll();
        if (products.isEmpty()) {
            topProductsListPanel.add(emptyRow("Không có sản phẩm nào được bán trong khoảng thời gian này"));
        } else {
            int rank = 1;
            for (ProductProfit p : products) {
                topProductsListPanel.add(buildTopProductRow(rank++, p));
                topProductsListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        topProductsListPanel.revalidate();
        topProductsListPanel.repaint();
    }

    private JPanel buildTopProductRow(int rank, ProductProfit product) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel rankLabel = new JLabel(String.valueOf(rank), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(rankColor(rank));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rankLabel.setForeground(Color.WHITE);
        rankLabel.setFont(AppFont.SMALL_BOLD);
        rankLabel.setPreferredSize(new Dimension(22, 22));

        JLabel nameLabel = new JLabel(product.productName);
        nameLabel.setFont(AppFont.BODY);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        boolean positive = product.profit.signum() >= 0;
        JLabel profitLabel = new JLabel(NumberUtil.formatThousands(product.profit.longValue()) + " đ");
        profitLabel.setFont(AppFont.SMALL_BOLD);
        profitLabel.setForeground(positive ? AppColor.SUCCESS : AppColor.ERROR);
        profitLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        profitLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel qtyLabel = new JLabel("Đã bán: " + NumberUtil.formatThousands(product.quantity));
        qtyLabel.setFont(AppFont.FOOTER);
        qtyLabel.setForeground(AppColor.TEXT_MUTED);
        qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        qtyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(profitLabel);
        right.add(qtyLabel);

        JPanel left = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        left.setOpaque(false);
        left.add(rankLabel, BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static Color rankColor(int rank) {
        switch (rank) {
            case 1: return new Color(234, 179, 8);
            case 2: return new Color(148, 163, 184);
            case 3: return new Color(180, 83, 9);
            default: return AppColor.ACCENT;
        }
    }

    private JComponent emptyRow(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.MD, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
}