package com.view.admin.inventoryreport;

import com.components.BaseDialog;
import com.components.AppAlert;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.Pagination;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.table.ActionColumn;
import com.components.report.MonthlyCategoryTrendPanel;
import com.components.report.ProductStockChartPanel;
import com.dao.InventoryReportDAO;
import com.dao.InventoryReportDAO.CategoryStock;
import com.dao.InventoryReportDAO.OverallSummary;
import com.dao.InventoryReportDAO.PriceRangeStock;
import com.dao.InventoryReportDAO.ProductStock;
import com.dao.InventoryReportDAO.BatchHistory;
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
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.Desktop;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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
    private BaseSearch batchHistorySearch;
    private DatePickerField historyFromField;
    private DatePickerField historyToField;
    private JLabel historyClearDateFilterLink;
    private BaseTable batchHistoryTable;
    private Timer batchHistorySearchTimer;
    private Pagination batchHistoryPagination;
    private int batchHistoryPage = 1;
    private int batchHistoryPageSize = 10;
    private List<BatchHistory> lastBatchHistoryRows = new ArrayList<>();
    private List<BatchHistory> currentBatchHistoryPageRows = new ArrayList<>();

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
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildBatchHistoryCard());

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


    private DashboardCard buildBatchHistoryCard() {
        DashboardCard card = new DashboardCard(
                "Lịch sử phiếu / hóa đơn làm thay đổi lô",
                "Theo dõi từng chứng từ đã nhập, xuất, trả, hủy hoặc điều chỉnh số lượng của từng lô",
                FontAwesomeSolid.HISTORY, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Dùng đúng style + padding nút "Tùy chọn" chuẩn của SectionHeader
        // (overflowButton) để đồng bộ toàn hệ thống - padding (8,14,8,14),
        // KHÔNG tự ý đổi padding rồi tự khóa lại preferred size của chính
        // nút (dễ lệch chuẩn nếu style nguồn thay đổi sau này). Kích thước
        // nút giờ chỉ phụ thuộc vào chính nội dung/style của nó (giống hệt
        // trang khác), KHÔNG còn phụ thuộc vào chiều cao header của
        // DashboardCard nhờ đã sửa DashboardCard.setHeaderAction() bọc
        // wrapper GridBagLayout (không kéo giãn) ở phần dùng chung.
        FontIcon historyExportChevron = FontIcon.of(FontAwesomeSolid.CHEVRON_DOWN, 10);
        historyExportChevron.setIconColor(AppColor.ACCENT);

        JButton historyExportButton = new JButton("Tùy chọn", historyExportChevron);
        historyExportButton.setHorizontalTextPosition(SwingConstants.LEFT);
        historyExportButton.setIconTextGap(8);
        historyExportButton.setFont(AppFont.BUTTON.deriveFont(13f));
        historyExportButton.setForeground(AppColor.ACCENT);
        historyExportButton.setBackground(AppColor.WHITE);
        historyExportButton.setFocusPainted(false);
        historyExportButton.setOpaque(true);
        historyExportButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        historyExportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));

        Color historyExportOutlineBg = AppColor.WHITE;
        Color historyExportHoverBg = new Color(
                AppColor.ACCENT.getRed(),
                AppColor.ACCENT.getGreen(),
                AppColor.ACCENT.getBlue(),
                30);
        historyExportButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                historyExportButton.setBackground(historyExportHoverBg);
            }

            @Override public void mouseExited(MouseEvent e) {
                historyExportButton.setBackground(historyExportOutlineBg);
            }
        });

        // Dropdown menu "Tùy chọn" - DÙNG CHUNG PATTERN VỚI SectionHeader
        // (tự vẽ từng dòng bằng JPanel + paintComponent, KHÔNG dùng JMenuItem
        // mặc định) để UI đồng bộ hoàn toàn với nút "Tùy chọn" ở đầu trang:
        // cùng padding, cùng bo góc hover, cùng màu sắc, cùng khoảng cách.
        JPanel historyOverflowListPanel = new JPanel();
        historyOverflowListPanel.setLayout(new BoxLayout(historyOverflowListPanel, BoxLayout.Y_AXIS));
        historyOverflowListPanel.setBackground(AppColor.WHITE);
        historyOverflowListPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        JPopupMenu historyOverflowMenu = new JPopupMenu();
        historyOverflowMenu.setBackground(AppColor.WHITE);
        historyOverflowMenu.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        historyOverflowMenu.add(historyOverflowListPanel);
        // Hàm tiện ích tạo 1 dòng menu (icon + text) - Y HẾT buildOverflowRow() của SectionHeader
        java.util.function.BiFunction<String, Runnable, JComponent> buildHistoryOverflowRow = (text, onClick) -> {
            FontAwesomeSolid iconType = text.contains("CSV") ? FontAwesomeSolid.FILE_CSV : FontAwesomeSolid.FILE_EXCEL;
            boolean[] hovered = {false};
            JPanel row = new JPanel(new BorderLayout(12, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (hovered[0]) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(AppColor.BG_LIGHTER);
                        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            row.setOpaque(false);
            row.setBorder(new EmptyBorder(10, 12, 10, 20));
            row.setCursor(new Cursor(Cursor.HAND_CURSOR));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            FontIcon icon = FontIcon.of(iconType, 14);
            icon.setIconColor(AppColor.TEXT_MUTED);
            JLabel iconLabel = new JLabel(icon);
            JLabel textLabel = new JLabel(text);
            textLabel.setFont(AppFont.BODY);
            textLabel.setForeground(AppColor.TEXT_PRIMARY);
            row.add(iconLabel, BorderLayout.WEST);
            row.add(textLabel, BorderLayout.CENTER);
            row.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) { hovered[0] = true; row.repaint(); }
                @Override
                public void mouseExited(MouseEvent e) { hovered[0] = false; row.repaint(); }
                @Override
                public void mouseClicked(MouseEvent e) {
                    historyOverflowMenu.setVisible(false);
                    if (onClick != null) onClick.run();
                }
            });
            return row;
        };
        historyOverflowListPanel.add(buildHistoryOverflowRow.apply("Xuất CSV", () -> exportBatchHistory("csv")));
        historyOverflowListPanel.add(buildHistoryOverflowRow.apply("Xuất Excel", () -> exportBatchHistory("xlsx")));
        // Khoảng cách nút -> menu = +4px, Y HẾT overflowButton của SectionHeader
        historyExportButton.addActionListener(e ->
                historyOverflowMenu.show(historyExportButton, 0, historyExportButton.getHeight() + 4));
        card.setHeaderAction(historyExportButton);

        card.setPreferredSize(new Dimension(10, 570));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 570));
        card.setMinimumSize(new Dimension(200, 470));

        // Toolbar filter: 1 o tim kiem chung (gop ca 3 o Ma lo/Ma SP/Ten SP
        // cu lai) + cum "Tu ngay ... Den ngay ..." tren CUNG 1 hang, dung
        // FlowLayout giong het cac trang khac (PurchaseReceiptPanel,
        // StockDisposalPanel...) - tu wrap xuong dong khi khong du cho thay
        // vi ep cac o bi bop nho/mat chu.
        JPanel filter = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.SM));
        filter.setOpaque(false);
        filter.setBorder(new EmptyBorder(0, 0, AppSpacing.SM, 0));

        batchHistorySearch = new BaseSearch("Tìm mã lô, mã chứng từ, mã SP, tên sản phẩm...");
        batchHistorySearch.setPreferredWidth(420);

        LocalDate today = LocalDate.now();
        historyFromField = new DatePickerField(today);
        historyToField = new DatePickerField(today);

        filter.add(batchHistorySearch);

        // Nhom "Tu ngay ... Den ngay ..." trong 1 sub-panel rieng (cung kieu
        // dateRow o PurchaseReceiptPanel/StockReconciliationPanel) de ca cum
        // ngay cung wrap xuong dong nhu 1 khoi, khong bi tach roi.
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        dateRow.setOpaque(false);

        JLabel from = new JLabel("Từ ngày");
        from.setFont(AppFont.SMALL); from.setForeground(AppColor.TEXT_MUTED);
        dateRow.add(from);
        dateRow.add(historyFromField);

        JLabel to = new JLabel("Đến ngày");
        to.setFont(AppFont.SMALL); to.setForeground(AppColor.TEXT_MUTED);
        dateRow.add(to);
        dateRow.add(historyToField);

        // Nut "Xoa loc" CHUAN (icon X, giong PurchaseReceiptPanel/
        // StockDisposalPanel...) - chi hien khi dang co loc/sap xep khac mac
        // dinh (mac dinh = hom nay -> hom nay, khong tim kiem, khong sap
        // xep). Bam vao se xoa trong o tim kiem, xoa sap xep cac cot va ve
        // lai khoang ngay mac dinh (hom nay) chu khong xoa trang ngay (bang
        // nay luon can 1 khoang ngay).
        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 14);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        historyClearDateFilterLink = new JLabel(clearIcon);
        historyClearDateFilterLink.setToolTipText("Xóa lọc");
        historyClearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        historyClearDateFilterLink.setVisible(false);
        historyClearDateFilterLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                LocalDate resetTo = LocalDate.now();
                if (batchHistorySearchTimer != null) batchHistorySearchTimer.stop();
                batchHistorySearch.clear();
                RowSorter<?> rowSorter = batchHistoryTable.getTable().getRowSorter();
                if (rowSorter != null) rowSorter.setSortKeys(null);
                historyFromField.setValue(resetTo);
                historyToField.setValue(resetTo);
                updateHistoryClearFilterVisibility();
                loadBatchHistory();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                clearIcon.setIconColor(AppColor.ERROR);
                historyClearDateFilterLink.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearIcon.setIconColor(AppColor.TEXT_MUTED);
                historyClearDateFilterLink.repaint();
            }
        });
        dateRow.add(historyClearDateFilterLink);

        filter.add(dateRow);

        batchHistoryTable = new BaseTable(new String[]{
                "Mã lô", "Thời gian", "Loại chứng từ", "Mã chứng từ",
                "Mã SP", "Tên sản phẩm", "Tồn kho", "Thay đổi"
        });
        batchHistoryTable.setRowHeight(36);
        batchHistoryTable.enableSorting();
        // Hiển thị SL thay đổi dạng badge +/-, cùng quy ước màu như bảng Đối chiếu kho.
        batchHistoryTable.setCenteredBadgeColumn(7, this::batchHistoryQuantityLabel, this::batchHistoryQuantityColor);
        batchHistoryTable.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewBatchHistoryRow));
        // Cho bang tu co gian theo be rong; khi khong du cho cho het cac cot
        // thi hien thanh cuon ngang thay vi bop chu/cot qua chat (giong
        // InventoryOverviewPanel).
        batchHistoryTable.enableHorizontalScroll();
        setupBatchHistoryCopyColumns();
        // Canh giữa các cột số liệu của bảng lịch sử: Tồn kho và Thay đổi.
        batchHistoryTable.getTable().getColumnModel().getColumn(6).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBorder(new EmptyBorder(8, 6, 8, 6));
                // Giữ màu nền zebra/selection giống các cột mặc định của BaseTable.
                c.setBackground(batchHistoryTable.rowColorProvider().colorFor(row, isSelected));
                c.setForeground(isSelected ? AppColor.TEXT_PRIMARY : AppColor.TABLE_ROW_TEXT);
                return c;
            }
        });
        RowSorter<?> historyRowSorter = batchHistoryTable.getTable().getRowSorter();
        if (historyRowSorter != null) {
            historyRowSorter.addRowSorterListener(e -> updateHistoryClearFilterVisibility());
        }

        JPanel body = new JPanel(new BorderLayout());
        body.setOpaque(false);
        body.add(filter, BorderLayout.NORTH);
        body.add(batchHistoryTable, BorderLayout.CENTER);

        batchHistoryPagination = new Pagination();
        batchHistoryPagination.setPageSize(batchHistoryPageSize);
        batchHistoryPagination.addPropertyChangeListener("pageChanged", e -> {
            batchHistoryPage = batchHistoryPagination.getCurrentPage();
            renderCurrentBatchHistoryPage();
        });
        batchHistoryPagination.addPropertyChangeListener("pageSizeChanged", e -> {
            batchHistoryPageSize = batchHistoryPagination.getPageSize();
            batchHistoryPage = 1;
            batchHistoryPagination.setTotalItems(lastBatchHistoryRows.size());
            renderCurrentBatchHistoryPage();
        });
        body.add(batchHistoryPagination, BorderLayout.SOUTH);

        card.getContentPanel().add(body, BorderLayout.CENTER);

        batchHistorySearchTimer = new Timer(350, e -> loadBatchHistory());
        batchHistorySearchTimer.setRepeats(false);

        DocumentListener listener = new DocumentListener() {
            private void changed() {
                if (batchHistorySearchTimer != null) batchHistorySearchTimer.restart();
                updateHistoryClearFilterVisibility();
            }
            public void insertUpdate(DocumentEvent e) { changed(); }
            public void removeUpdate(DocumentEvent e) { changed(); }
            public void changedUpdate(DocumentEvent e) { changed(); }
        };
        batchHistorySearch.getTextField().getDocument().addDocumentListener(listener);
        historyFromField.onChange(v -> { updateHistoryClearFilterVisibility(); loadBatchHistory(); });
        historyToField.onChange(v -> { updateHistoryClearFilterVisibility(); loadBatchHistory(); });
        updateHistoryClearFilterVisibility();

        return card;
    }

    /** Chi hien nut "Xoa loc" khi dang co loc/sap xep khac mac dinh: co tim kiem, co sap xep, hoac khoang ngay khac hom nay -> hom nay. */
    private void updateHistoryClearFilterVisibility() {
        if (historyClearDateFilterLink == null) return;
        LocalDate todayNow = LocalDate.now();
        LocalDate from = historyFromField.getValue();
        LocalDate to = historyToField.getValue();
        boolean dateIsDefault = todayNow.equals(from) && todayNow.equals(to);
        boolean hasKeyword = batchHistorySearch != null && !batchHistorySearch.getText().isBlank();
        RowSorter<?> rowSorter = batchHistoryTable != null ? batchHistoryTable.getTable().getRowSorter() : null;
        boolean hasSort = rowSorter != null && !rowSorter.getSortKeys().isEmpty();
        historyClearDateFilterLink.setVisible(!dateIsDefault || hasKeyword || hasSort);
    }

    /**
     * Nut copy CHUAN cua he thong (icon + click de copy vao clipboard,
     * giong PurchaseReceiptPanel/InventoryBatchPanel...) ap dung cho cac cot
     * ma trong bang lich su lo: Ma lo (0), Ma chung tu (3 - dung chung cho
     * ca phieu nhap/xuat/tra/hoy va hoa don ban), Ma SP (4).
     */
    private void setupBatchHistoryCopyColumns() {
        final int[] copyColumns = {0, 3, 4};
        final java.util.Map<Integer, String> colNames = new java.util.HashMap<>();
        colNames.put(0, "mã lô");
        colNames.put(3, "mã chứng từ");
        colNames.put(4, "mã SP");

        for (int colIdx : copyColumns) {
            final int col = colIdx;
            final String colName = colNames.get(colIdx);
            batchHistoryTable.getTable().getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable table, Object value,
                        boolean isSelected, boolean hasFocus, int row, int column) {
                    JLabel c = (JLabel) super.getTableCellRendererComponent(
                        table, value, isSelected, hasFocus, row, column);
                    String text = value != null ? value.toString() : "";
                    c.setText(text);
                    c.setBorder(new EmptyBorder(8, 12, 8, 12));
                    c.setHorizontalAlignment(SwingConstants.LEFT);
                    c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                    if (text != null && !text.isBlank() && !"—".equals(text)) {
                        FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 11);
                        copyIcon.setIconColor(AppColor.ACCENT);
                        c.setIcon(copyIcon);
                        c.setIconTextGap(6);
                        c.setHorizontalTextPosition(SwingConstants.LEFT);
                        c.setToolTipText("Click để copy " + colName + ": " + text);
                    } else {
                        c.setIcon(null);
                        c.setToolTipText(null);
                    }
                    return c;
                }
            });
        }

        // Xu ly click vao icon copy cho cac cot Ma lo (0) / Ma chung tu (3) / Ma SP (4)
        batchHistoryTable.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JTable table = batchHistoryTable.getTable();
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                boolean copyable = viewCol == 0 || viewCol == 3 || viewCol == 4;
                if (copyable && viewRow >= 0) {
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    Object value = table.getModel().getValueAt(modelRow, viewCol);
                    String text = value != null ? value.toString() : "";
                    if (!text.isBlank() && !"—".equals(text)) {
                        copyToClipboard(text);
                        String colName = colNames.get(viewCol);
                        AppAlert.success(InventoryReportPanel.this, "Copy thành công", "Đã copy " + colName + ": " + text);
                    }
                }
            }
        });
    }

    /** Copy chuoi vao clipboard he thong. */
    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
            // Bo qua neu khong copy duoc
        }
    }

    private void loadBatchHistory() {
        if (batchHistoryTable == null) return;
        LocalDate from = historyFromField.getValue();
        LocalDate to = historyToField.getValue();
        if (from != null && to != null && from.isAfter(to)) return;

        String keyword = batchHistorySearch.getText();

        SwingWorker<List<BatchHistory>, Void> worker = new SwingWorker<List<BatchHistory>, Void>() {
            @Override
            protected List<BatchHistory> doInBackground() {
                return dao.getBatchHistory(keyword, from, to);
            }

            @Override
            protected void done() {
                try {
                    lastBatchHistoryRows = get();
                    batchHistoryPage = 1;
                    if (batchHistoryPagination != null) {
                        batchHistoryPagination.setTotalItems(lastBatchHistoryRows.size());
                    }
                    renderCurrentBatchHistoryPage();
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi",
                            "Không thể tải lịch sử lô: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    /** Cat ra dung 1 trang tu {@link #lastBatchHistoryRows} (theo batchHistoryPage/PageSize hien tai) roi ve len bang. */
    private void renderCurrentBatchHistoryPage() {
        int total = lastBatchHistoryRows.size();
        int from = (batchHistoryPage - 1) * batchHistoryPageSize;
        int to = Math.min(from + batchHistoryPageSize, total);
        List<BatchHistory> pageRows = from < to
                ? lastBatchHistoryRows.subList(from, to)
                : Collections.emptyList();
        renderBatchHistory(pageRows);
    }

    /** Mở form chi tiết cho đúng dòng lịch sử hiện tại (model row = thứ tự trong trang). */
    private void viewBatchHistoryRow(int modelRow) {
        if (modelRow < 0 || modelRow >= currentBatchHistoryPageRows.size()) return;
        BatchHistory history = currentBatchHistoryPageRows.get(modelRow);
        new BatchHistoryDetailDialog(
                SwingUtilities.getWindowAncestor(this) instanceof Frame
                        ? (Frame) SwingUtilities.getWindowAncestor(this) : null,
                history).setVisible(true);
    }

    private void renderBatchHistory(List<BatchHistory> rows) {
        currentBatchHistoryPageRows = new ArrayList<>(rows);
        batchHistoryTable.clear();
        for (BatchHistory h : rows) {
            String time = h.changedAt == null ? "" :
                    h.changedAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            String direction = "IN".equalsIgnoreCase(h.direction) ? "NHẬP" : "XUẤT";
            batchHistoryTable.addRow(new Object[]{
                    h.batchCode, time, h.documentType, h.documentCode,
                    h.productCode, h.productName,
                    NumberUtil.formatThousands(h.stockBefore),
                    batchHistoryQuantityLabel(h)
            });
        }
        // setColumnWidths() (thay vi goi setPreferredWidth() tung cot rieng le)
        // de BaseTable tu tinh lai auto-resize-mode ngay sau khi doi do rong -
        // dieu kien de thanh cuon ngang (enableHorizontalScroll) hoat dong dung
        // ngay lan tai du lieu dau tien.
        batchHistoryTable.setColumnWidths(115, 90, 115, 90, 75, 160, 80, 85, 85);
        batchHistoryTable.setColumnMinWidths(100, 80, 100, 80, 65, 120, 70, 75, 75);
    }

    /** Hiển thị biến động số lượng có dấu, giống cột Chênh lệch của Đối chiếu kho. */
    private String batchHistoryQuantityLabel(Object value) {
        if (value instanceof BatchHistory) {
            BatchHistory h = (BatchHistory) value;
            int delta = "IN".equalsIgnoreCase(h.direction) ? h.quantity : -h.quantity;
            if (delta == 0) return "0";
            return (delta > 0 ? "+" : "") + NumberUtil.formatThousands(delta);
        }
        return String.valueOf(value);
    }

    /** Màu xanh cho nhập (+), đỏ cho xuất (-), cùng quy ước Đối chiếu kho. */
    private Color batchHistoryQuantityColor(Object value) {
        String label = String.valueOf(value);
        if (label.startsWith("+")) return AppColor.SUCCESS;
        if (label.startsWith("-")) return AppColor.ERROR;
        return AppColor.TEXT_MUTED;
    }

    // ---------------------------------------------------------------
    // Tai du lieu
    // ---------------------------------------------------------------

    private void loadAll() {
        loadSnapshot();
        loadTrend();
        loadBatchHistory();
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

    /** Xuất toàn bộ dữ liệu lịch sử đang được lọc (không chỉ trang hiện tại). */
    private void exportBatchHistory(String format) {
        if (lastBatchHistoryRows == null || lastBatchHistoryRows.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Không có dữ liệu lịch sử phiếu / hóa đơn để xuất.");
            return;
        }

        String defaultName = "lich_su_phieu_hoa_don_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        String[] headers = {
                "Mã lô", "Thời gian", "Loại chứng từ", "Mã chứng từ",
                "Mã SP", "Tên sản phẩm", "Tồn kho", "Thay đổi"
        };
        List<Object[]> rows = new ArrayList<>();
        for (BatchHistory h : lastBatchHistoryRows) {
            String time = h.changedAt == null ? "" :
                    h.changedAt.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            int delta = "IN".equalsIgnoreCase(h.direction) ? h.quantity : -h.quantity;
            rows.add(new Object[]{
                    h.batchCode, time, h.documentType, h.documentCode,
                    h.productCode, h.productName, h.stockBefore, delta
            });
        }

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, "Lịch sử phiếu hóa đơn", headers, rows);
                }
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(
                            InventoryReportPanel.this,
                            "Thành công",
                            "Đã xuất báo cáo vào file " + file.getName()
                    );
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryReportPanel.this, "Lỗi",
                            "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

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