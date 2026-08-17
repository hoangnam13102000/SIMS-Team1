package com.view.admin.inventoryreport;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.report.MonthlyCategoryTrendPanel;
import com.components.report.ProductStockChartPanel;
import com.dao.InventoryReportDAO;
import com.dao.InventoryReportDAO.CategoryStock;
import com.dao.InventoryReportDAO.OverallSummary;
import com.dao.InventoryReportDAO.PriceRangeStock;
import com.dao.InventoryReportDAO.ProductStock;
import com.dao.RevenueReportDAO.CategorySeries;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import com.utils.FileUtil;
import com.utils.NumberUtil;
import com.utils.TableExportUtil;
import com.utils.pdf.InventoryReportPdfExporter;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Trang "Báo cáo hàng tồn kho" (Nhân viên kho / Quản lý kho - quyền
 * STOCK_VIEW): thống kê tồn kho HIỆN TẠI theo danh mục sản phẩm và theo
 * khoảng giá bán, kèm biểu đồ xu hướng tồn kho (số lượng) thay đổi theo
 * TỪNG THÁNG cho từng danh mục (vd lượng cà phê bột tăng/giảm qua các
 * tháng), tái sử dụng {@link MonthlyCategoryTrendPanel} - cùng component
 * đang dùng ở tab "Xu hướng bán hàng" của trang Báo cáo doanh thu.
 */
public class InventoryReportPanel extends JPanel {

    private final InventoryReportDAO dao = new InventoryReportDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải báo cáo...");

    private StatCard quantityCard;
    private StatCard valueCard;
    private StatCard lowStockCard;
    private StatCard outOfStockCard;

    private JPanel categoryListPanel;
    private JPanel priceRangeListPanel;

    private DatePickerField fromField;
    private DatePickerField toField;
    private JComponent filterBarDateSlot;
    private MonthlyCategoryTrendPanel trendChartPanel;
    private JPanel trendLegendPanel;
    private ProductStockChartPanel stockChartPanel;

    private List<CategoryStock> lastCategoryStocks = new ArrayList<>();
    private List<PriceRangeStock> lastPriceRangeStocks = new ArrayList<>();
    private List<ProductStock> lastProductStocks = new ArrayList<>();
    private OverallSummary lastSummary;

    public InventoryReportPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        add(buildHeader(), BorderLayout.NORTH);

        ScrollableColumn content = new ScrollableColumn();
        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildStockChartCard());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildBreakdownRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildTrendCard());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        fromField = new DatePickerField(today.minusMonths(5).withDayOfMonth(1));
        toField = new DatePickerField(today);
        fromField.onChange(v -> loadTrend());
        toField.onChange(v -> loadTrend());
        wireDatePickersIntoFilterBar();

        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadAll);
        loadAll();
    }

    // ---------------------------------------------------------------
    // Xay giao dien
    // ---------------------------------------------------------------

    private SectionHeader buildHeader() {
        SectionHeader header = new SectionHeader(FontAwesomeSolid.WAREHOUSE, AppColor.ACCENT,
                "Báo cáo hàng tồn kho",
                "Thống kê số lượng và giá trị tồn kho theo danh mục sản phẩm, theo khoảng giá bán, cùng xu hướng thay đổi theo tháng");
        header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportCategoryBreakdown("csv"));
        header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportCategoryBreakdown("xlsx"));
        header.addOverflowAction("Tạo báo cáo PDF", FontAwesomeSolid.FILE_PDF, this::exportPdfReport);
        return header;
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        quantityCard = new StatCard("Tổng số lượng tồn", "0", FontAwesomeSolid.BOXES, AppColor.ACCENT);
        valueCard = new StatCard("Giá trị tồn kho (theo giá bán)", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.TEAL);
        lowStockCard = new StatCard("Sản phẩm sắp hết hàng", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);
        outOfStockCard = new StatCard("Sản phẩm hết hàng", "0", FontAwesomeSolid.TIMES_CIRCLE, AppColor.RED_ALT);

        row.add(quantityCard);
        row.add(valueCard);
        row.add(lowStockCard);
        row.add(outOfStockCard);
        return row;
    }

    private JPanel buildBreakdownRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));
        row.setPreferredSize(new Dimension(10, 340));

        row.add(buildCategoryCard());
        row.add(buildPriceRangeCard());
        return row;
    }

    private DashboardCard buildCategoryCard() {
        DashboardCard card = new DashboardCard("Tồn kho theo danh mục",
                "Số lượng và giá trị tồn kho (tính theo giá bán) của từng danh mục sản phẩm",
                FontAwesomeSolid.TAGS, AppColor.ACCENT);

        categoryListPanel = new ScrollableColumn();
        JScrollPane scroll = plainScroll(categoryListPanel);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildPriceRangeCard() {
        DashboardCard card = new DashboardCard("Tồn kho theo khoảng giá bán",
                "Vốn tồn kho đang tập trung ở phân khúc giá nào",
                FontAwesomeSolid.CHART_BAR, AppColor.INFO);

        priceRangeListPanel = new ScrollableColumn();
        JScrollPane scroll = plainScroll(priceRangeListPanel);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    /**
     * Card bieu do cot "Ton kho theo san pham" - moi cot la 1 san pham, mau
     * cot canh bao truc tiep neu san pham dang het hang / co lo sap het han /
     * co lo da het han con ton (xem {@link ProductStockChartPanel}).
     */
    /**
     * Card biểu đồ cột "Tồn kho theo sản phẩm" - mỗi cột là 1 sản phẩm (top 20),
     * màu cột cảnh báo trực tiếp nếu sản phẩm đang hết hàng / có lô sắp hết hạn /
     * có lô đã hết hạn còn tồn (xem {@link ProductStockChartPanel}).
     */
    private DashboardCard buildStockChartCard() {
        DashboardCard card = new DashboardCard("Tồn kho theo sản phẩm",
                "Số lượng tồn hiện tại của từng sản phẩm (top 20) - cột tô màu cảnh báo nếu có lô sắp/đã hết hạn hoặc đã hết hàng",
                FontAwesomeSolid.CHART_BAR, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 400));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
        card.setMinimumSize(new Dimension(200, 320));

        stockChartPanel = new ProductStockChartPanel();

        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        legend.setOpaque(false);
        legend.setAlignmentX(Component.LEFT_ALIGNMENT);
        legend.add(legendDot("Còn hàng, bình thường", AppColor.ACCENT));
        legend.add(legendDot("Sắp hết hạn / dưới tồn tối thiểu", AppColor.WARNING));
        legend.add(legendDot("Có lô đã hết hạn còn tồn", AppColor.ERROR));
        legend.add(legendDot("Đã hết hàng", AppColor.TEXT_MUTED));

        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.SM));
        body.setOpaque(false);
        body.add(stockChartPanel, BorderLayout.CENTER);
        body.add(legend, BorderLayout.SOUTH);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTrendCard() {
        DashboardCard card = new DashboardCard("Xu hướng tồn kho theo tháng & danh mục",
                "Số lượng tồn kho cuối mỗi tháng của từng danh mục (vd: lượng cà phê bột thay đổi theo tháng)",
                FontAwesomeSolid.CHART_LINE, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 520));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        card.setMinimumSize(new Dimension(200, 400));

        trendChartPanel = new MonthlyCategoryTrendPanel();

        trendLegendPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.XS));
        trendLegendPanel.setOpaque(false);
        trendLegendPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.SM));
        body.setOpaque(false);
        body.add(buildTrendFilterBar(), BorderLayout.NORTH);
        body.add(trendChartPanel, BorderLayout.CENTER);
        body.add(trendLegendPanel, BorderLayout.SOUTH);

        card.getContentPanel().add(body, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildTrendFilterBar() {
        RoundedPanel bar = new RoundedPanel();
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.SM));
        bar.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        bar.add(presetButton("3 tháng gần đây", () -> applyRange(LocalDate.now().minusMonths(2).withDayOfMonth(1), LocalDate.now())));
        bar.add(presetButton("6 tháng gần đây", () -> applyRange(LocalDate.now().minusMonths(5).withDayOfMonth(1), LocalDate.now())));
        bar.add(presetButton("12 tháng gần đây", () -> applyRange(LocalDate.now().minusMonths(11).withDayOfMonth(1), LocalDate.now())));
        bar.add(presetButton("Năm nay", () -> applyRange(LocalDate.now().withDayOfYear(1), LocalDate.now())));

        bar.add(verticalSeparator());

        JLabel fromLabel = new JLabel("Từ tháng");
        fromLabel.setFont(AppFont.SMALL);
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        bar.add(fromLabel);

        filterBarDateSlot = bar; // placeholder ref de them fromField/toField sau khi khoi tao
        JLabel toLabel = new JLabel("Đến tháng");
        toLabel.setFont(AppFont.SMALL);
        toLabel.setForeground(AppColor.TEXT_MUTED);
        bar.putClientProperty("toLabel", toLabel);

        return bar;
    }

    /** DatePickerField duoc tao SAU buildTrendFilterBar() (can LocalDate.now() 1 lan duy nhat o constructor) nen gan vao day. */
    private void wireDatePickersIntoFilterBar() {
        JPanel bar = (JPanel) filterBarDateSlot;
        bar.add(fromField);
        JLabel toLabel = (JLabel) bar.getClientProperty("toLabel");
        bar.add(toLabel);
        bar.add(toField);
        bar.revalidate();
        bar.repaint();
    }

    private void applyRange(LocalDate from, LocalDate to) {
        fromField.setValue(from);
        toField.setValue(to);
        loadTrend();
    }

    // ---------------------------------------------------------------
    // Tai du lieu
    // ---------------------------------------------------------------

    private void loadAll() {
        loadSnapshot();
        loadTrend();
    }

    private static class SnapshotData {
        OverallSummary summary;
        List<CategoryStock> categories;
        List<PriceRangeStock> priceRanges;
        List<ProductStock> productStocks;
    }

    private void loadSnapshot() {
        loadingOverlay.start("Đang tải báo cáo...");
        SwingWorker<SnapshotData, Void> worker = new SwingWorker<SnapshotData, Void>() {
            @Override
            protected SnapshotData doInBackground() {
                SnapshotData data = new SnapshotData();
                data.summary = dao.getOverallSummary();
                data.categories = dao.getStockByCategory();
                data.priceRanges = dao.getStockByPriceRange();
                data.productStocks = dao.getProductStockOverview();
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    SnapshotData data = get();
                    applySummary(data.summary);
                    lastSummary = data.summary;
                    lastCategoryStocks = data.categories;
                    lastPriceRangeStocks = data.priceRanges;
                    lastProductStocks = data.productStocks;
                    renderCategoryList(data.categories);
                    renderPriceRangeList(data.priceRanges);
                    stockChartPanel.setData(data.productStocks);
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi", "Không thể tải báo cáo: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void loadTrend() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) return;
        if (from.isAfter(to)) {
            BaseDialog.error(this, "Khoảng thời gian không hợp lệ", "\"Từ tháng\" phải trước hoặc bằng \"Đến tháng\".");
            return;
        }

        SwingWorker<MonthlyCategoryTrend, Void> worker = new SwingWorker<MonthlyCategoryTrend, Void>() {
            @Override
            protected MonthlyCategoryTrend doInBackground() {
                return dao.getMonthlyCategoryStockTrend(from, to);
            }

            @Override
            protected void done() {
                try {
                    applyTrend(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi", "Không thể tải xu hướng tồn kho: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------------
    // Ap du lieu vao giao dien
    // ---------------------------------------------------------------

    private void applySummary(OverallSummary summary) {
        quantityCard.setValue(NumberUtil.formatThousands(summary.totalQuantity));
        valueCard.setValue(NumberUtil.formatThousands(summary.valueAtSellPrice.longValue()) + " đ");
        lowStockCard.setValue(NumberUtil.formatThousands(summary.lowStockCount));
        outOfStockCard.setValue(NumberUtil.formatThousands(summary.outOfStockCount));
    }

    private void renderCategoryList(List<CategoryStock> categories) {
        categoryListPanel.removeAll();
        if (categories.isEmpty()) {
            categoryListPanel.add(emptyRow("Chưa có dữ liệu sản phẩm/danh mục"));
        } else {
            BigDecimal maxValue = BigDecimal.ZERO;
            for (CategoryStock c : categories) {
                if (c.valueAtSellPrice.compareTo(maxValue) > 0) maxValue = c.valueAtSellPrice;
            }
            int i = 0;
            for (CategoryStock c : categories) {
                double ratio = maxValue.signum() == 0 ? 0
                        : c.valueAtSellPrice.doubleValue() / maxValue.doubleValue();
                categoryListPanel.add(buildBreakdownRow(c.categoryName, c.productCount,
                        c.quantity, c.valueAtSellPrice, ratio, paletteColor(i++)));
                categoryListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        categoryListPanel.revalidate();
        categoryListPanel.repaint();
    }

    private void renderPriceRangeList(List<PriceRangeStock> priceRanges) {
        priceRangeListPanel.removeAll();
        if (priceRanges.isEmpty()) {
            priceRangeListPanel.add(emptyRow("Chưa có dữ liệu sản phẩm"));
        } else {
            BigDecimal maxValue = BigDecimal.ZERO;
            for (PriceRangeStock p : priceRanges) {
                if (p.valueAtSellPrice.compareTo(maxValue) > 0) maxValue = p.valueAtSellPrice;
            }
            int i = 0;
            for (PriceRangeStock p : priceRanges) {
                double ratio = maxValue.signum() == 0 ? 0
                        : p.valueAtSellPrice.doubleValue() / maxValue.doubleValue();
                priceRangeListPanel.add(buildBreakdownRow(p.label, p.productCount,
                        p.quantity, p.valueAtSellPrice, ratio, paletteColor(i++)));
                priceRangeListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        priceRangeListPanel.revalidate();
        priceRangeListPanel.repaint();
    }

    private void applyTrend(MonthlyCategoryTrend trend) {
        trendChartPanel.setData(trend);
        renderTrendLegend(trend);
    }

    private void renderTrendLegend(MonthlyCategoryTrend trend) {
        trendLegendPanel.removeAll();

        List<CategorySeries> series = trend != null && trend.series != null ? trend.series : Collections.emptyList();
        if (series.isEmpty()) {
            JLabel empty = new JLabel("Không có dữ liệu trong khoảng thời gian này");
            empty.setFont(AppFont.SMALL);
            empty.setForeground(AppColor.TEXT_MUTED);
            trendLegendPanel.add(empty);
        } else {
            for (int i = 0; i < series.size(); i++) {
                CategorySeries s = series.get(i);
                Color color = trendChartPanel.colorFor(i);
                String text = s.categoryName + "  ·  " + NumberUtil.formatThousands(s.totalQuantity) + " (hiện tại)";

                JToggleButton chip = buildLegendChip(text, color);
                int index = i;
                chip.addItemListener(e -> trendChartPanel.setSeriesVisible(index, chip.isSelected()));
                trendLegendPanel.add(chip);
            }
        }
        trendLegendPanel.revalidate();
        trendLegendPanel.repaint();
    }

    // ---------------------------------------------------------------
    // Xuat CSV / Excel (bang "Ton kho theo danh muc" dang hien thi)
    // ---------------------------------------------------------------

    private void exportCategoryBreakdown(String format) {
        if (lastCategoryStocks.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu tồn kho để xuất.");
            return;
        }

        String defaultName = "bao_cao_ton_kho_theo_danh_muc_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        String[] headers = {"Danh mục", "Số sản phẩm", "Số lượng tồn", "Giá trị tồn (giá bán)"};
        List<Object[]> rows = new ArrayList<>();
        for (CategoryStock c : lastCategoryStocks) {
            rows.add(new Object[]{c.categoryName, c.productCount, c.quantity, c.valueAtSellPrice.longValue()});
        }

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, "Tồn kho theo danh mục", headers, rows);
                }
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(InventoryReportPanel.this, "Thành công",
                            "Đã xuất báo cáo vào file \"" + file.getName() + "\"");
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi", "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------------
    // Xuat PDF (bao cao ton kho day du, giong trang bao cao doanh thu)
    // ---------------------------------------------------------------

    private void exportPdfReport() {
        if (lastSummary == null) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu tồn kho để tạo báo cáo.");
            return;
        }

        String defaultName = "bao_cao_ton_kho_" + timestamp() + ".pdf";
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, "pdf");

        InventoryReportPdfExporter.ReportContext ctx = new InventoryReportPdfExporter.ReportContext();
        ctx.from = fromField.getValue();
        ctx.to = toField.getValue();
        ctx.summary = lastSummary;
        ctx.categories = lastCategoryStocks;
        ctx.priceRanges = lastPriceRangeStocks;
        ctx.productStocks = lastProductStocks;
        var currentUser = AuthService.getInstance().getCurrentUser();
        ctx.preparedByName = currentUser != null ? currentUser.getFullName() : null;

        loadingOverlay.start("Đang tạo báo cáo PDF...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                InventoryReportPdfExporter.export(ctx, file);
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(InventoryReportPanel.this, "Thành công",
                            "Đã tạo báo cáo PDF tại \"" + file.getName() + "\"");
                    if (Desktop.isDesktopSupported()) {
                        try {
                            Desktop.getDesktop().open(file);
                        } catch (Exception ignored) {
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi", "Tạo báo cáo PDF thất bại: " + e.getMessage());
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

    private static Color paletteColor(int index) {
        Color[] palette = {
                AppColor.ACCENT, AppColor.INFO, AppColor.WARNING, AppColor.TEAL,
                AppColor.RED_ALT, AppColor.BLUE, AppColor.ORANGE, AppColor.YELLOW
        };
        return palette[index % palette.length];
    }

    private JComponent emptyRow(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.MD, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** 1 dong "thanh ngang" chung cho ca 2 bang (theo danh muc / theo khoang gia): ten + thanh ty le + so lieu. */
    private JPanel buildBreakdownRow(String label, int productCount, long quantity, BigDecimal value, double ratio, Color color) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        JPanel topLine = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        topLine.setOpaque(false);
        topLine.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel valueLabel = new JLabel(NumberUtil.formatThousands(value.longValue()) + " đ");
        valueLabel.setFont(AppFont.SMALL_BOLD);
        valueLabel.setForeground(AppColor.TEXT_PRIMARY);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        valueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel qtyLabel = new JLabel(NumberUtil.formatThousands(quantity) + " SP tồn  ·  " + productCount + " mặt hàng");
        qtyLabel.setFont(AppFont.FOOTER);
        qtyLabel.setForeground(AppColor.TEXT_MUTED);
        qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        qtyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(valueLabel);
        right.add(qtyLabel);

        topLine.add(nameLabel, BorderLayout.CENTER);
        topLine.add(right, BorderLayout.EAST);

        row.add(topLine);
        row.add(Box.createVerticalStrut(4));
        row.add(buildRatioBar(ratio, color));
        return row;
    }

    private JComponent buildRatioBar(double ratio, Color color) {
        JPanel bar = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, w, h, h, h);
                int filled = (int) Math.round(NumberUtil.clamp(ratio, 0, 1) * w);
                if (filled > 0) {
                    g2.setColor(color);
                    g2.fillRoundRect(0, 0, Math.max(filled, h), h, h, h);
                }
                g2.dispose();
            }
        };
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setPreferredSize(new Dimension(10, 8));
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        return bar;
    }

    /** Nut "chip" bo tron dung cho preset khoang thang (3 thang gan day/6 thang gan day...). */
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

    /** Chu thich dang tinh (khong bam duoc): 1 dau cham mau + nhan, dung cho legend cua bieu do cot ton kho. */
    private JLabel legendDot(String label, Color color) {
        JLabel dot = new JLabel("\u25CF  " + label);
        dot.setFont(AppFont.SMALL);
        dot.setForeground(color);
        return dot;
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

    private static JSeparator verticalSeparator() {
        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 26));
        sep.setForeground(AppColor.BORDER);
        return sep;
    }

    static class ScrollableColumn extends JPanel implements Scrollable {
        ScrollableColumn() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 64; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
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