package com.view.admin.report;

import com.components.dashboard.DashboardCard;
import com.components.report.FinanceChartPanel;
import com.components.report.RevenuePieChartPanel;
import com.components.StatCard;
import com.dao.RevenueReportDAO.CategoryProfit;
import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.dao.RevenueReportDAO.DailyPoint;
import com.dao.RevenueReportDAO.PaymentSlice;
import com.dao.RevenueReportDAO.ProductProfit;
import com.dao.RevenueReportDAO.ProfitSummary;
import com.dao.RevenueReportDAO.Summary;
import com.dao.RevenueReportDAO.TopProduct;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Nội dung tab "Doanh thu & Lợi nhuận" (đã gộp 2 tab cũ): thống kê doanh thu
 * + lợi nhuận, biểu đồ Thu/Chi/Lợi nhuận ròng theo ngày, phương thức thanh
 * toán, lợi nhuận theo danh mục, top sản phẩm bán chạy và theo lợi nhuận.
 */
class RevenueReportTab extends JPanel {

    // Doanh thu stats
    private StatCard revenueCard;
    private StatCard invoiceCard;
    private StatCard avgCard;
    private StatCard itemsCard;

    // Lợi nhuận stats
    private StatCard costCard;
    private StatCard lossCard;
    private StatCard profitCard;
    private StatCard netProfitCard;
    private StatCard marginCard;

    private FinanceChartPanel chartPanel;
    private RevenuePieChartPanel pieChartPanel;
    private JPanel paymentListPanel;
    private JPanel categoryListPanel;
    private JPanel topProductsListPanel;
    private JPanel topProfitProductsListPanel;
    private final List<JPanel> paymentLegendRows = new ArrayList<>();

    private List<DailyPoint> lastDaily = new ArrayList<>();
    private List<DailyFinancePoint> lastFinanceDaily = new ArrayList<>();

    RevenueReportTab() {
        setLayout(new BorderLayout());
        setOpaque(false);

        JPanel content = new ScrollableColumn();
        content.add(buildRevenueStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.MD));
        content.add(buildProfitStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildChartCard());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildMiddleRow());
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

    List<DailyFinancePoint> getLastFinanceDaily() {
        return lastFinanceDaily;
    }

    // ---------------------------------------------------------------
    // Xây giao diện
    // ---------------------------------------------------------------

    private JPanel buildRevenueStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        revenueCard = new StatCard("Tổng doanh thu", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        invoiceCard = new StatCard("Số hóa đơn", "0", FontAwesomeSolid.RECEIPT, AppColor.INFO);
        avgCard = new StatCard("Giá trị TB/hóa đơn", "0 đ", FontAwesomeSolid.PERCENTAGE, AppColor.WARNING);
        itemsCard = new StatCard("Sản phẩm đã bán", "0", FontAwesomeSolid.BOXES, AppColor.TEAL);

        row.add(revenueCard);
        row.add(invoiceCard);
        row.add(avgCard);
        row.add(itemsCard);
        return row;
    }

    private JPanel buildProfitStatsRow() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 230));

        JPanel row1 = new JPanel(new GridLayout(1, 3, AppSpacing.MD, 0));
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        costCard = new StatCard("Chi - giá vốn", "0 đ", FontAwesomeSolid.TRUCK, AppColor.WARNING);
        lossCard = new StatCard("Chi - thiệt hại hàng hủy", "0 đ", FontAwesomeSolid.TRASH, AppColor.ERROR);
        profitCard = new StatCard("Lợi nhuận gộp", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.SUCCESS);
        row1.add(costCard);
        row1.add(lossCard);
        row1.add(profitCard);

        JPanel row2 = new JPanel(new GridLayout(1, 2, AppSpacing.MD, 0));
        row2.setOpaque(false);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        row2.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        netProfitCard = new StatCard("Lợi nhuận ròng (đã trừ thiệt hại)", "0 đ", FontAwesomeSolid.DOLLAR_SIGN, AppColor.SUCCESS);
        marginCard = new StatCard("Biên lợi nhuận ròng", "0%", FontAwesomeSolid.PERCENTAGE, AppColor.INFO);
        row2.add(netProfitCard);
        row2.add(marginCard);

        wrapper.add(row1);
        wrapper.add(Box.createVerticalStrut(AppSpacing.MD));
        wrapper.add(row2);
        return wrapper;
    }

    private DashboardCard buildChartCard() {
        DashboardCard card = new DashboardCard("Thu / Chi / Lợi nhuận ròng theo ngày",
                "Thu = doanh thu (chưa VAT) · Chi = giá vốn + thiệt hại hàng hủy · Lợi nhuận ròng = Thu − Chi",
                FontAwesomeSolid.CHART_BAR, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 320));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));

        chartPanel = new FinanceChartPanel();
        card.getContentPanel().add(chartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildMiddleRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        row.setPreferredSize(new Dimension(10, 320));

        row.add(buildPaymentMethodCard());
        row.add(buildCategoryCard());
        return row;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        row.setPreferredSize(new Dimension(10, 320));

        row.add(buildTopProductsCard());
        row.add(buildTopProfitProductsCard());
        return row;
    }

    private DashboardCard buildPaymentMethodCard() {
        DashboardCard card = new DashboardCard("Doanh thu theo phương thức thanh toán",
                "Di chuột vào từng phần để xem chi tiết",
                FontAwesomeSolid.CHART_PIE, AppColor.INFO);

        pieChartPanel = new RevenuePieChartPanel();
        pieChartPanel.setPreferredSize(new Dimension(168, 168));
        pieChartPanel.setOnHoverChange(this::highlightPaymentLegendRow);

        JPanel chartWrap = new JPanel(new GridBagLayout());
        chartWrap.setOpaque(false);
        chartWrap.add(pieChartPanel);

        paymentListPanel = new ScrollableColumn();
        JScrollPane scroll = plainScroll(paymentListPanel);

        JPanel body = new JPanel(new BorderLayout(AppSpacing.LG, 0));
        body.setOpaque(false);
        body.add(chartWrap, BorderLayout.WEST);
        body.add(scroll, BorderLayout.CENTER);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildCategoryCard() {
        DashboardCard card = new DashboardCard("Lợi nhuận theo danh mục",
                "Sắp xếp giảm dần theo lợi nhuận",
                FontAwesomeSolid.TAGS, AppColor.INFO);
        categoryListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(categoryListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTopProductsCard() {
        DashboardCard card = new DashboardCard("Top sản phẩm bán chạy",
                FontAwesomeSolid.TROPHY, AppColor.WARNING);

        topProductsListPanel = new ScrollableColumn();
        JScrollPane scroll = plainScroll(topProductsListPanel);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTopProfitProductsCard() {
        DashboardCard card = new DashboardCard("Top sản phẩm theo lợi nhuận",
                FontAwesomeSolid.TROPHY, AppColor.SUCCESS);
        topProfitProductsListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(topProfitProductsListPanel), BorderLayout.CENTER);
        return card;
    }

    /** Sửa lỗi "bóng ma" chữ đè lên nhau khi hover. */
    private void highlightPaymentLegendRow(int index) {
        for (int i = 0; i < paymentLegendRows.size(); i++) {
            JPanel row = paymentLegendRows.get(i);
            boolean active = i == index;
            row.setOpaque(active);
            if (active) {
                row.setBackground(AppColor.ACCENT_SOFT);
            }
        }
        paymentListPanel.repaint();
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

    static class ScrollableColumn extends JPanel implements Scrollable {
        ScrollableColumn() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ---------------------------------------------------------------
    // Áp dữ liệu
    // ---------------------------------------------------------------

    void applyData(Summary summary, Summary previousSummary, List<DailyPoint> daily,
                   List<DailyFinancePoint> financeDaily,
                   List<PaymentSlice> payments, List<TopProduct> topProducts,
                   ProfitSummary profitSummary, List<CategoryProfit> categories,
                   List<ProductProfit> topProductsProfit) {
        lastDaily = daily;
        lastFinanceDaily = financeDaily != null ? financeDaily : new ArrayList<>();

        // --- Doanh thu ---
        revenueCard.setValue(NumberUtil.formatThousands(summary.totalRevenue.longValue()) + " đ");
        Double growth = summary.growthPercent(previousSummary);
        if (growth == null) {
            revenueCard.setSubtitle("Không có dữ liệu kỳ trước để so sánh");
        } else {
            String sign = growth >= 0 ? "+" : "";
            revenueCard.setTrend(sign + NumberUtil.formatDecimal(growth, 1) + "% so với kỳ trước", growth >= 0);
        }

        invoiceCard.setValue(NumberUtil.formatThousands(summary.invoiceCount));
        invoiceCard.setSubtitle("Hóa đơn hợp lệ (không tính hóa đơn đã hủy)");

        avgCard.setValue(NumberUtil.formatThousands(summary.avgOrderValue().longValue()) + " đ");
        avgCard.setSubtitle("Doanh thu ÷ số hóa đơn");

        itemsCard.setValue(NumberUtil.formatThousands(summary.itemsSold));
        itemsCard.setSubtitle("Tổng số lượng sản phẩm đã bán");

        // --- Lợi nhuận ---
        if (profitSummary != null) {
            costCard.setValue(NumberUtil.formatThousands(profitSummary.totalCost.longValue()) + " đ");
            costCard.setSubtitle("Theo giá nhập hiện tại của sản phẩm");

            lossCard.setValue(NumberUtil.formatThousands(profitSummary.totalLoss.longValue()) + " đ");
            lossCard.setSubtitle("Hàng hết hạn/hỏng đã lập phiếu tiêu hủy");

            boolean profitPositive = profitSummary.totalProfit.signum() >= 0;
            profitCard.setValue(NumberUtil.formatThousands(profitSummary.totalProfit.longValue()) + " đ");
            profitCard.setTrend(profitPositive ? "Lãi (chưa trừ thiệt hại)" : "Lỗ (chưa trừ thiệt hại)", profitPositive);

            boolean netPositive = profitSummary.netProfit.signum() >= 0;
            netProfitCard.setValue(NumberUtil.formatThousands(profitSummary.netProfit.longValue()) + " đ");
            netProfitCard.setTrend(netPositive ? "Lãi ròng" : "Lỗ ròng", netPositive);

            Double margin = profitSummary.netMarginPercent();
            if (margin == null) {
                marginCard.setValue("0%");
                marginCard.setSubtitle("Không có doanh thu để tính");
            } else {
                marginCard.setValue(NumberUtil.formatDecimal(margin, 1) + "%");
                marginCard.setSubtitle("Lợi nhuận ròng ÷ doanh thu");
            }
        }

        chartPanel.setData(financeDaily);

        renderPaymentMethods(payments);
        renderCategories(categories);
        renderTopProducts(topProducts);
        renderTopProfitProducts(topProductsProfit);
    }

    private static Color paymentColor(int index) {
        Color[] palette = {
                AppColor.ACCENT, AppColor.INFO, AppColor.WARNING, AppColor.TEAL,
                AppColor.RED_ALT, AppColor.BLUE, AppColor.ORANGE, AppColor.YELLOW
        };
        return palette[index % palette.length];
    }

    private void renderPaymentMethods(List<PaymentSlice> slices) {
        paymentListPanel.removeAll();
        paymentLegendRows.clear();

        if (slices == null || slices.isEmpty()) {
            paymentListPanel.add(emptyRow("Không có dữ liệu trong khoảng thời gian này"));
            pieChartPanel.setData(new ArrayList<>(), "Tổng", "0 đ");
        } else {
            BigDecimal total = BigDecimal.ZERO;
            for (PaymentSlice s : slices) total = total.add(s.revenue);

            List<RevenuePieChartPanel.Slice> pieSlices = new ArrayList<>();

            for (int i = 0; i < slices.size(); i++) {
                PaymentSlice s = slices.get(i);
                double ratio = total.signum() == 0 ? 0 : s.revenue.doubleValue() / total.doubleValue();
                Color color = paymentColor(i);
                String label = paymentMethodLabel(s.method);
                String valueText = NumberUtil.formatThousands(s.revenue.longValue()) + " đ";
                String countText = s.invoiceCount + " hóa đơn";

                pieSlices.add(new RevenuePieChartPanel.Slice(label, s.revenue.doubleValue(), valueText, countText, color));

                JPanel row = buildPaymentLegendRow(label, valueText, countText, ratio, color);
                int index = i;
                row.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { pieChartPanel.setHoverIndex(index); }
                    @Override public void mouseExited(MouseEvent e) { pieChartPanel.setHoverIndex(-1); }
                });
                paymentLegendRows.add(row);

                paymentListPanel.add(row);
                paymentListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }

            pieChartPanel.setData(pieSlices, "Tổng", NumberUtil.formatThousands(total.longValue()) + " đ");
        }
        paymentListPanel.revalidate();
        paymentListPanel.repaint();
    }

    private void renderCategories(List<CategoryProfit> categories) {
        categoryListPanel.removeAll();
        if (categories == null || categories.isEmpty()) {
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

    private void renderTopProducts(List<TopProduct> products) {
        topProductsListPanel.removeAll();
        if (products == null || products.isEmpty()) {
            topProductsListPanel.add(emptyRow("Không có sản phẩm nào được bán trong khoảng thời gian này"));
        } else {
            int rank = 1;
            for (TopProduct p : products) {
                topProductsListPanel.add(buildTopProductRow(rank++, p));
                topProductsListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        topProductsListPanel.revalidate();
        topProductsListPanel.repaint();
    }

    private void renderTopProfitProducts(List<ProductProfit> products) {
        topProfitProductsListPanel.removeAll();
        if (products == null || products.isEmpty()) {
            topProfitProductsListPanel.add(emptyRow("Không có sản phẩm nào được bán trong khoảng thời gian này"));
        } else {
            int rank = 1;
            for (ProductProfit p : products) {
                topProfitProductsListPanel.add(buildTopProfitProductRow(rank++, p));
                topProfitProductsListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        topProfitProductsListPanel.revalidate();
        topProfitProductsListPanel.repaint();
    }

    private JComponent emptyRow(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.MD, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel buildPaymentLegendRow(String label, String valueText, String countText, double ratio, Color color) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        row.setBorder(new EmptyBorder(6, 8, 6, 8));
        row.setCursor(new Cursor(Cursor.HAND_CURSOR));

        JLabel dot = new JLabel("\u25CF");
        dot.setForeground(color);
        dot.setFont(AppFont.BODY_BOLD);

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JPanel left = new JPanel(new BorderLayout(8, 0));
        left.setOpaque(false);
        left.add(dot, BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel valueLabel = new JLabel(valueText);
        valueLabel.setFont(AppFont.SMALL_BOLD);
        valueLabel.setForeground(AppColor.TEXT_PRIMARY);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel countLabel = new JLabel(countText + "  ·  " + NumberUtil.formatDecimal(ratio * 100, 1) + "%");
        countLabel.setFont(AppFont.FOOTER);
        countLabel.setForeground(AppColor.TEXT_MUTED);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        countLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(valueLabel);
        right.add(countLabel);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private JPanel buildTopProductRow(int rank, TopProduct product) {
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

        JLabel revenueLabel = new JLabel(NumberUtil.formatThousands(product.revenue.longValue()) + " đ");
        revenueLabel.setFont(AppFont.SMALL_BOLD);
        revenueLabel.setForeground(AppColor.TEXT_PRIMARY);
        revenueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        revenueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel qtyLabel = new JLabel("Đã bán: " + NumberUtil.formatThousands(product.quantity));
        qtyLabel.setFont(AppFont.FOOTER);
        qtyLabel.setForeground(AppColor.TEXT_MUTED);
        qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        qtyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(revenueLabel);
        right.add(qtyLabel);

        JPanel left = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        left.setOpaque(false);
        left.add(rankLabel, BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private JPanel buildTopProfitProductRow(int rank, ProductProfit product) {
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

    private static String paymentMethodLabel(String method) {
        if (method == null) return "Khác";
        switch (method) {
            case "CASH": return "Tiền mặt";
            case "BANK_TRANSFER": return "Chuyển khoản";
            case "PAYPAL": return "PayPal";
            case "CARD": return "Thẻ";
            default: return method;
        }
    }
}