package com.view.admin.report;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.dao.RevenueReportDAO;
import com.dao.RevenueReportDAO.CategoryProfit;
import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.dao.RevenueReportDAO.DailyPoint;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
import com.dao.RevenueReportDAO.PaymentSlice;
import com.dao.RevenueReportDAO.ProductProfit;
import com.dao.RevenueReportDAO.ProfitSummary;
import com.dao.RevenueReportDAO.Summary;
import com.dao.RevenueReportDAO.TopProduct;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import com.utils.FileUtil;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


public class RevenueReportPanel extends JPanel {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CARD_REVENUE = "revenue";
    private static final String CARD_TREND = "trend";

    private final RevenueReportDAO dao = new RevenueReportDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải báo cáo...");

    private DatePickerField fromField;
    private DatePickerField toField;
    private JComponent filterBarDateSlot;

    private final CardLayout cardLayout = new CardLayout();
    private JPanel cardContainer;
    private RevenueReportTab revenueTab;
    private SalesTrendTab salesTrendTab;
    private JButton revenueTabButton;
    private JButton trendTabButton;
    private String activeCard = CARD_REVENUE;

    public RevenueReportPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel topSection = new JPanel(new GridBagLayout());
        topSection.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, AppSpacing.MD, 0);
        topSection.add(buildHeader(), gbc);

        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, AppSpacing.SM, 0);
        topSection.add(buildTabRow(), gbc);

        gbc.gridy = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        topSection.add(buildFilterBar(), gbc);

        revenueTab = new RevenueReportTab();
        salesTrendTab = new SalesTrendTab();

        cardContainer = new JPanel(cardLayout);
        cardContainer.setOpaque(false);
        cardContainer.add(revenueTab, CARD_REVENUE);
        cardContainer.add(salesTrendTab, CARD_TREND);

        add(topSection, BorderLayout.NORTH);
        add(LoadingOverlay.attach(cardContainer, loadingOverlay), BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        fromField = new DatePickerField(today.minusDays(29));
        toField = new DatePickerField(today);
        fromField.onChange(v -> loadData());
        toField.onChange(v -> loadData());
        wireDatePickersIntoFilterBar();

        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    private SectionHeader buildHeader() {
        SectionHeader header = new SectionHeader(FontAwesomeSolid.CHART_LINE, AppColor.ACCENT,
                "Báo cáo doanh thu & lợi nhuận",
                "Thống kê doanh thu, phương thức thanh toán, sản phẩm bán chạy và so sánh giá nhập/giá bán để tính lợi nhuận");
        header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportReport("csv"));
        header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportReport("xlsx"));
        return header;
    }

    private JPanel buildTabRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        revenueTabButton = tabButton("Doanh thu & Lợi nhuận", () -> switchTab(CARD_REVENUE));
        trendTabButton = tabButton("Xu hướng bán hàng", () -> switchTab(CARD_TREND));
        row.add(revenueTabButton);
        row.add(trendTabButton);
        updateTabButtonStyles();

        return row;
    }

    private JPanel buildFilterBar() {
        RoundedPanel bar = new RoundedPanel();
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.SM));
        bar.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        bar.add(presetButton("Hôm nay", () -> applyRange(LocalDate.now(), LocalDate.now())));
        bar.add(presetButton("7 ngày qua", () -> applyRange(LocalDate.now().minusDays(6), LocalDate.now())));
        bar.add(presetButton("Tháng này", () -> applyRange(LocalDate.now().withDayOfMonth(1), LocalDate.now())));
        bar.add(presetButton("Quý này", () -> applyRange(startOfQuarter(LocalDate.now()), LocalDate.now())));
        bar.add(presetButton("Năm nay", () -> applyRange(LocalDate.now().withDayOfYear(1), LocalDate.now())));

        bar.add(verticalSeparator());

        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(AppFont.SMALL);
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        bar.add(fromLabel);

        filterBarDateSlot = bar;
        JLabel toLabel = new JLabel("Đến ngày");
        toLabel.setFont(AppFont.SMALL);
        toLabel.setForeground(AppColor.TEXT_MUTED);
        bar.putClientProperty("toLabel", toLabel);

        return bar;
    }

    private static JSeparator verticalSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 26));
        sep.setForeground(AppColor.BORDER);
        return sep;
    }

    private void wireDatePickersIntoFilterBar() {
        JPanel bar = (JPanel) filterBarDateSlot;
        bar.add(fromField);
        JLabel toLabel = (JLabel) bar.getClientProperty("toLabel");
        bar.add(toLabel);
        bar.add(toField);
        bar.revalidate();
        bar.repaint();
    }

    private void switchTab(String card) {
        if (activeCard.equals(card)) return;
        activeCard = card;
        cardLayout.show(cardContainer, card);
        updateTabButtonStyles();
    }

    private void updateTabButtonStyles() {
        styleTabButton(revenueTabButton, CARD_REVENUE.equals(activeCard));
        styleTabButton(trendTabButton, CARD_TREND.equals(activeCard));
    }

    private static void styleTabButton(JButton button, boolean active) {
        button.setBackground(active ? AppColor.ACCENT : AppColor.BG_LIGHTER);
        button.setForeground(active ? Color.WHITE : AppColor.TEXT_SECONDARY);
        button.repaint();
    }

    private void applyRange(LocalDate from, LocalDate to) {
        fromField.setValue(from);
        toField.setValue(to);
        loadData();
    }

    private static LocalDate startOfQuarter(LocalDate date) {
        int quarterMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), quarterMonth, 1);
    }

    private static class ReportData {
        Summary summary;
        Summary previousSummary;
        List<DailyPoint> daily;
        List<PaymentSlice> payments;
        List<TopProduct> topProducts;

        ProfitSummary profitSummary;
        List<DailyFinancePoint> financeDaily;
        List<CategoryProfit> categories;
        List<ProductProfit> topProductsProfit;

        MonthlyCategoryTrend monthlyCategoryTrend;
    }

    private void loadData() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) return;
        if (from.isAfter(to)) {
            BaseDialog.error(this, "Khoảng thời gian không hợp lệ", "\"Từ ngày\" phải trước hoặc bằng \"Đến ngày\".");
            return;
        }

        loadingOverlay.start("Đang tải báo cáo...");
        SwingWorker<ReportData, Void> worker = new SwingWorker<ReportData, Void>() {
            @Override
            protected ReportData doInBackground() {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                LocalDate prevTo = from.minusDays(1);
                LocalDate prevFrom = prevTo.minusDays(days - 1);

                ReportData data = new ReportData();
                data.summary = dao.getSummary(from, to);
                data.previousSummary = dao.getSummary(prevFrom, prevTo);
                data.daily = dao.getDailyRevenue(from, to);
                data.payments = dao.getRevenueByPaymentMethod(from, to);
                data.topProducts = dao.getTopProducts(from, to, 10);

                data.profitSummary = dao.getProfitSummary(from, to);
                data.financeDaily = dao.getDailyFinance(from, to);
                data.categories = dao.getProfitByCategory(from, to);
                data.topProductsProfit = dao.getTopProductsByProfit(from, to, 10);

                data.monthlyCategoryTrend = dao.getMonthlyCategoryTrend(from, to);
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    ReportData data = get();
                    revenueTab.applyData(
                            data.summary, data.previousSummary, data.daily,
                            data.financeDaily, data.payments, data.topProducts,
                            data.profitSummary, data.categories, data.topProductsProfit);
                    salesTrendTab.applyData(data.monthlyCategoryTrend);
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Không thể tải báo cáo: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void exportReport(String format) {
        if (CARD_TREND.equals(activeCard)) {
            exportSalesTrend(format);
            return;
        }

        List<DailyFinancePoint> daily = revenueTab.getLastFinanceDaily();
        if (daily.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu để xuất trong khoảng thời gian đang chọn.");
            return;
        }
        String[] headers = new String[]{"Ngày", "Số hóa đơn", "Thu (doanh thu)", "Chi - giá vốn",
                "Chi - thiệt hại", "Tổng chi", "Lợi nhuận ròng"};
        List<Object[]> rows = new ArrayList<>();
        for (DailyFinancePoint p : daily) {
            rows.add(new Object[]{p.date.format(FILE_DATE_FORMAT), p.invoiceCount,
                    p.revenue.longValue(), p.cost.longValue(), p.disposalLoss.longValue(),
                    p.totalExpense().longValue(), p.netProfit().longValue()});
        }

        String defaultName = "bao_cao_doanh_thu_loi_nhuan_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, "Doanh thu & Lợi nhuận", headers, rows);
                }
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(RevenueReportPanel.this, "Thành công",
                            "Đã xuất báo cáo vào file \"" + file.getName() + "\"");
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void exportSalesTrend(String format) {
        MonthlyCategoryTrend trend = salesTrendTab.getLastTrend();
        if (trend == null || trend.months.isEmpty() || trend.series.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu để xuất trong khoảng thời gian đang chọn.");
            return;
        }

        String defaultName = "xu_huong_ban_hang_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        String[] headers = new String[trend.series.size() + 1];
        headers[0] = "Tháng";
        for (int i = 0; i < trend.series.size(); i++) {
            headers[i + 1] = trend.series.get(i).categoryName;
        }

        List<Object[]> rows = new ArrayList<>();
        for (int m = 0; m < trend.months.size(); m++) {
            Object[] row = new Object[trend.series.size() + 1];
            row[0] = trend.months.get(m).getMonthValue() + "/" + trend.months.get(m).getYear();
            for (int i = 0; i < trend.series.size(); i++) {
                row[i + 1] = trend.series.get(i).quantityByMonth.get(m);
            }
            rows.add(row);
        }

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, "Xu hướng bán hàng", headers, rows);
                }
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(RevenueReportPanel.this, "Thành công",
                            "Đã xuất báo cáo vào file \"" + file.getName() + "\"");
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private static File ensureExtension(File file, String ext) {
        String name = file.getName();
        if (name.toLowerCase().endsWith("." + ext)) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(file.getParentFile(), base + "." + ext);
    }

    private JButton presetButton(String text, Runnable onClick) {
        JButton button = roundedChipButton(text);
        button.setForeground(AppColor.TEXT_SECONDARY);
        button.setBackground(AppColor.BG_LIGHTER);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(AppColor.ACCENT_SOFT); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(AppColor.BG_LIGHTER); }
        });
        button.addActionListener(e -> onClick.run());
        return button;
    }

    private JButton tabButton(String text, Runnable onClick) {
        JButton button = roundedChipButton(text);
        button.addActionListener(e -> onClick.run());
        return button;
    }

    private static JButton roundedChipButton(String text) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.LARGE, AppRadius.LARGE);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(AppFont.SMALL_BOLD);
        button.setBorder(new EmptyBorder(6, 14, 6, 14));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static class RoundedPanel extends JPanel {
        RoundedPanel() {
            setOpaque(false);
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
}