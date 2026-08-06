package com.view.admin.report;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.dao.RevenueReportDAO;
import com.dao.RevenueReportDAO.CategoryProfit;
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

/**
 * Trang "Bao cao doanh thu & loi nhuan": chon khoang thoi gian (co san preset
 * nhanh, dung chung cho ca 2 tab) roi chuyen doi giua 2 goc nhin:
 * <ul>
 *   <li><b>Doanh thu</b> - {@link RevenueReportTab}: tong quan + bieu do doanh
 *       thu theo ngay + doanh thu theo phuong thuc thanh toan + top san pham
 *       ban chay (doanh thu tinh theo Invoices.TotalAmount, DA GOM VAT).</li>
 *   <li><b>Loi nhuan</b> - {@link ProfitReportTab}: so sanh gia nhap/gia ban,
 *       loi nhuan gop theo ngay/danh muc/san pham (tinh tren co so CHUA VAT -
 *       xem ghi chu trong {@link RevenueReportDAO}).</li>
 * </ul>
 * Truoc day day la 2 trang rieng ("revenueReport" va "profitReport"), gop lai
 * thanh 1 de dung chung bo loc ngay va tranh nham lan vi ca 2 deu co the
 * "Tong doanh thu" nhung tinh tren 2 co so khac nhau (gom VAT hay khong).
 */
public class RevenueReportPanel extends JPanel {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String CARD_REVENUE = "revenue";
    private static final String CARD_PROFIT = "profit";
    private static final String CARD_TREND = "trend";

    private final RevenueReportDAO dao = new RevenueReportDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải báo cáo...");

    private DatePickerField fromField;
    private DatePickerField toField;
    private JComponent filterBarDateSlot;

    private final CardLayout cardLayout = new CardLayout();
    private JPanel cardContainer;
    private RevenueReportTab revenueTab;
    private ProfitReportTab profitTab;
    private SalesTrendTab salesTrendTab;
    private JButton revenueTabButton;
    private JButton profitTabButton;
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
        profitTab = new ProfitReportTab();
        salesTrendTab = new SalesTrendTab();

        cardContainer = new JPanel(cardLayout);
        cardContainer.setOpaque(false);
        cardContainer.add(revenueTab, CARD_REVENUE);
        cardContainer.add(profitTab, CARD_PROFIT);
        cardContainer.add(salesTrendTab, CARD_TREND);

        add(topSection, BorderLayout.NORTH);
        add(LoadingOverlay.attach(cardContainer, loadingOverlay), BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        fromField = new DatePickerField(today.minusDays(29));
        toField = new DatePickerField(today);
        fromField.onChange(v -> loadData());
        toField.onChange(v -> loadData());
        // fromField/toField duoc them vao filter bar o buildFilterBar() ben tren
        // (goi truoc khi field duoc khoi tao) - nen gan lai o day roi revalidate.
        wireDatePickersIntoFilterBar();

        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    // ---------------------------------------------------------------
    // Header + tab toggle + filter bar
    // ---------------------------------------------------------------

    private SectionHeader buildHeader() {
        SectionHeader header = new SectionHeader(FontAwesomeSolid.CHART_LINE, AppColor.ACCENT,
                "Báo cáo doanh thu & lợi nhuận",
                "Thống kê doanh thu, phương thức thanh toán, sản phẩm bán chạy và so sánh giá nhập/giá bán để tính lợi nhuận");
        header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportReport("csv"));
        header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportReport("xlsx"));
        return header;
    }

    /**
     * Hang RIENG cho 2 nut chuyen tab "Doanh thu" / "Loi nhuan" - co truoc day
     * nhet chung vao cuoi hang loc ngay (buildFilterBar), nhung hang do da rat
     * chat (5 nut preset + 2 o chon ngay) va bi ep cung chieu cao 56px, nen
     * khi FlowLayout xuong dong o man hinh hep, dong 2 (chua dung 2 nut nay)
     * bi cat mat - nguoi dung chi thay tab Doanh thu ma khong thay duoc nut
     * de bam sang Loi nhuan. Tach rieng ra 1 hang luon hien, khong phu thuoc
     * be rong cua so, de dam bao 2 nut nay LUON nhin thay va bam duoc.
     */
    private JPanel buildTabRow() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        revenueTabButton = tabButton("Doanh thu", () -> switchTab(CARD_REVENUE));
        profitTabButton = tabButton("Lợi nhuận", () -> switchTab(CARD_PROFIT));
        trendTabButton = tabButton("Xu hướng bán hàng", () -> switchTab(CARD_TREND));
        row.add(revenueTabButton);
        row.add(profitTabButton);
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

        filterBarDateSlot = bar; // placeholder ref de them fromField/toField sau khi khoi tao
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

    /** DatePickerField duoc tao SAU buildFilterBar() (can LocalDate.now() 1 lan duy nhat o constructor) nen gan vao day. */
    private void wireDatePickersIntoFilterBar() {
        JPanel bar = (JPanel) filterBarDateSlot;
        bar.add(fromField);
        JLabel toLabel = (JLabel) bar.getClientProperty("toLabel");
        bar.add(toLabel);
        bar.add(toField);
        // Không cần nút "Làm mới": AutoRefresher đã bind DataChangedEvent → loadData()
        // (debounce 400ms). Mọi thay đổi đi qua DAO / OrderNotifyPoller / restore
        // backup đều publish event và panel tự reload khi đang hiển thị.

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
        styleTabButton(profitTabButton, CARD_PROFIT.equals(activeCard));
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

    // ---------------------------------------------------------------
    // Tai du lieu (goi ca 2 bo DAO 1 luot de chuyen tab khong bi giat/reload)
    // ---------------------------------------------------------------

    private static class ReportData {
        Summary summary;
        Summary previousSummary;
        List<DailyPoint> daily;
        List<PaymentSlice> payments;
        List<TopProduct> topProducts;

        ProfitSummary profitSummary;
        List<DailyPoint> profitDaily;
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
                data.profitDaily = dao.getDailyProfit(from, to);
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
                    revenueTab.applyData(data.summary, data.previousSummary, data.daily, data.payments, data.topProducts);
                    profitTab.applyData(data.profitSummary, data.profitDaily, data.categories, data.topProductsProfit);
                    salesTrendTab.applyData(data.monthlyCategoryTrend);
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Không thể tải báo cáo: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------------
    // Xuat CSV / Excel - xuat theo tab dang xem (Doanh thu hoac Loi nhuan)
    // ---------------------------------------------------------------

    private void exportReport(String format) {
        if (CARD_TREND.equals(activeCard)) {
            exportSalesTrend(format);
            return;
        }

        boolean showingProfit = CARD_PROFIT.equals(activeCard);
        List<DailyPoint> daily = showingProfit ? profitTab.getLastDaily() : revenueTab.getLastDaily();
        if (daily.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu để xuất trong khoảng thời gian đang chọn.");
            return;
        }

        String metric = showingProfit ? "loi_nhuan" : "doanh_thu";
        String sheetName = showingProfit ? "Lợi nhuận" : "Doanh thu";
        String valueColumn = showingProfit ? "Lợi nhuận" : "Doanh thu";

        String defaultName = "bao_cao_" + metric + "_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        String[] headers = {"Ngày", "Số hóa đơn", valueColumn};
        List<Object[]> rows = new ArrayList<>();
        for (DailyPoint p : daily) {
            rows.add(new Object[]{p.date.format(FILE_DATE_FORMAT), p.invoiceCount, p.revenue.longValue()});
        }

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, sheetName, headers, rows);
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

    /** Xuat bang "Thang x Danh muc" (moi cot 1 danh muc, gia tri = so luong ban ra) cua tab Xu huong ban hang. */
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

    // ---------------------------------------------------------------
    // Component phu tro rieng cho trang nay
    // ---------------------------------------------------------------

    /** Nut "chip" bo tron cho cac preset khoang thoi gian (Hom nay/7 ngay qua...). */
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

    /** Nut "chip" bo tron dung lam tab chuyen doi Doanh thu / Loi nhuan (mau active/inactive do updateTabButtonStyles() dieu khien). */
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

    /** Panel nen trang, bo goc - dung lam khung cho filter bar. */
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