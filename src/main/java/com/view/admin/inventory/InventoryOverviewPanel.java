package com.view.admin.inventory;

import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.Pagination;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatBadge;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.report.MonthlyCategoryTrendPanel;
import com.dao.DashboardDAO;
import com.dao.DashboardDAO.LowStockItem;
import com.dao.InventoryBatchDAO;
import com.dao.ProductDAO;
import com.dao.InventoryReportDAO;
import com.dao.InventoryReportDAO.MovementSummary;
import com.dao.InventoryReportDAO.OverallSummary;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
import com.dao.StockAlertDAO;
import com.dao.StockReconciliationDAO;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.Product;
import com.model.InventoryBatch;
import com.model.StockAlert;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.DateUtil;
import com.utils.FileUtil;
import com.utils.PaginationHelper;
import com.utils.TableExportUtil;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * "Tổng quan kho" - trang landing của mục "Quản lý kho" (đặt trước Phiếu
 * nhập, Lô hàng, Kiểm kê...). CHỈ XEM (không thêm/sửa/xóa trực tiếp ở đây) -
 * tồn kho chỉ thay đổi qua Phiếu nhập, Đơn hàng, Kiểm kê, Hủy hàng... đúng
 * nguyên tắc "1 nguồn sự thật" đã áp dụng xuyên suốt SIMS.
 * <p>
 * Trang này vẫn là dashboard nhưng có thêm bảng sản phẩm tồn kho ở cuối trang,
 * hỗ trợ tìm kiếm, phân trang và xuất Excel; phần lô hiển thị theo thứ tự FEFO
 * để nhân viên thấy ngay lô cần ưu tiên xuất.
 * mẫu của {@link com.view.admin.DashboardPanel}: {@link #buildHeader()} +
 * {@link #buildDynamicContent()} (StatCard → 2 hàng thẻ 2-cột chứa biểu đồ/
 * danh sách cuộn) - cho cái nhìn tổng quan nhanh, rồi điều hướng sang trang
 * chi tiết tương ứng khi cần xử lý sâu hơn.
 */
public class InventoryOverviewPanel extends JPanel {

    private final DashboardDAO dashboardDao = new DashboardDAO();
    private final InventoryReportDAO inventoryReportDao = new InventoryReportDAO();
    private final InventoryBatchDAO inventoryBatchDao = new InventoryBatchDAO();
    private final ProductDAO productDao = new ProductDAO();
    private final StockAlertDAO stockAlertDao = new StockAlertDAO();
    private final StockReconciliationDAO reconciliationDao = new StockReconciliationDAO();

    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải dữ liệu...");

    private StatCard valueCard;
    private StatCard quantityCard;
    private StatCard lowStockCard;
    private StatCard outOfStockCard;

    private MonthlyCategoryTrendPanel trendChartPanel;
    private JPanel lowStockListPanel;
    private JPanel stockAlertListPanel;

    private BaseTable inventoryProductTable;
    private Pagination inventoryProductPagination;
    private BaseSearch inventoryProductSearch;
    private JLabel inventoryProductCount;
    private String inventoryProductKeyword = "";
    private int inventoryProductPage = 1;
    private int inventoryProductPageSize = 10;

    private JLabel activeAlertValue;
    private JLabel expiringValue;
    private JLabel expiredValue;
    private JLabel discrepancyValue;
    private JLabel inboundValue;
    private JLabel outboundValue;
    private JLabel disposalValue;
    private JLabel transactionValue;

    public InventoryOverviewPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel content = buildDynamicContent();
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        AutoRefresher.bind(this, DataChangedEvent.class, 400, () -> {
            loadData();
            loadInventoryProductPage();
        });
        loadData();
        loadInventoryProductPage();
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private SectionHeader buildHeader() {
        return new SectionHeader(FontAwesomeSolid.WAREHOUSE, AppColor.ACCENT, "Tổng quan kho",
                "Toàn cảnh tồn kho hiện tại - giá trị hàng tồn, cảnh báo hết/sắp hết hàng "
                        + "và biến động nhập/xuất trong tuần");
    }

    // ---------------------------------------------------------------
    // Nội dung động (stat cards + biểu đồ + danh sách)
    // ---------------------------------------------------------------

    private JPanel buildDynamicContent() {
        JPanel content = new ScrollableColumn();

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildTwoColumnRow(buildMovementCard(), 1.3, buildStockAlertCard(), 1.0, 320));        
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildTwoColumnRow(buildTrendChartCard(), 2.0, buildLowStockCard(), 1.0, 340));
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildInventoryProductCard());
        // Chừa khoảng đệm cuối trang để các nút nổi góc phải dưới (Cài đặt/AI)
        // không che mất pagination hoặc dòng cuối của bảng khi người dùng cuộn xuống đáy.
        // content.add(Box.createVerticalStrut(96));

        return content;
    }

    /**
     * Hàng thống kê responsive - ưu tiên hiện đủ chữ title/footer:
     * - ≥760px: 1 hàng × 4
     * - ≥420px: 2 cột
     * - hẹp hơn: 1 cột
     */
    private JPanel buildStatsRow() {
        valueCard = new StatCard("Giá trị tồn kho", "0 đ", FontAwesomeSolid.WALLET, AppColor.ACCENT);
        quantityCard = new StatCard("Tổng số lượng tồn", "0", FontAwesomeSolid.CUBES, AppColor.INFO);
        lowStockCard = new StatCard("Cần đặt hàng lại", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);
        outOfStockCard = new StatCard("Hết hàng", "0", FontAwesomeSolid.TIMES_CIRCLE, AppColor.ERROR);

        StatCard[] cards = {valueCard, quantityCard, lowStockCard, outOfStockCard};

        JPanel row = new JPanel() {
            private int lastCols = -1;

            {
                setOpaque(false);
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setLayout(new GridLayout(1, 4, AppSpacing.MD, AppSpacing.MD));
                for (StatCard c : cards) add(c);

                addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        relayout();
                    }
                });
            }

            private void relayout() {
                int w = getWidth();
                if (w <= 0) return;
                int cols = (w >= 760) ? 4 : (w >= 420) ? 2 : 1;
                if (cols == lastCols) return;
                lastCols = cols;

                int rows = (int) Math.ceil(4.0 / cols);
                setLayout(new GridLayout(rows, cols, AppSpacing.MD, AppSpacing.MD));
                removeAll();
                for (StatCard c : cards) add(c);
                int rowH = StatCard.PREFERRED_HEIGHT;
                int totalH = rows * rowH + Math.max(0, rows - 1) * AppSpacing.MD;
                setPreferredSize(new Dimension(10, totalH));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH + 4));
                revalidate();
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (lastCols <= 0) {
                    return new Dimension(d.width, StatCard.PREFERRED_HEIGHT);
                }
                return d;
            }
        };
        return row;
    }

    /** 1 hàng 2 cột không đều nhau (vd 2:1) - dùng GridBagLayout(fill=BOTH) để luôn khớp đúng chiều rộng/cao. */
    private JPanel buildTwoColumnRow(JComponent left, double leftWeight, JComponent right, double rightWeight, int height) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        row.setPreferredSize(new Dimension(10, height));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = leftWeight;
        gbc.insets = new Insets(0, 0, 0, AppSpacing.LG);
        row.add(left, gbc);

        gbc.gridx = 1;
        gbc.weightx = rightWeight;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(right, gbc);

        return row;
    }

    private DashboardCard buildTrendChartCard() {
        DashboardCard card = new DashboardCard("Xu hướng tồn kho 6 tháng",
                "Snapshot tồn kho cuối tháng theo danh mục",
                FontAwesomeSolid.CHART_LINE, AppColor.ACCENT);
        trendChartPanel = new MonthlyCategoryTrendPanel();
        card.getContentPanel().add(trendChartPanel, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildLowStockCard() {
        DashboardCard card = new DashboardCard("Sản phẩm sắp hết hàng",
                "Tồn kho ở mức hoặc dưới mức tối thiểu",
                FontAwesomeSolid.BOX, AppColor.WARNING);
        lowStockListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(lowStockListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildMovementCard() {
        DashboardCard card = new DashboardCard("Cảnh báo & việc cần xử lý",
                "Ưu tiên các vấn đề có thể ảnh hưởng trực tiếp đến tồn kho",
                FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);

        JPanel content = new JPanel(new GridLayout(2, 4, AppSpacing.MD, AppSpacing.SM));
        content.setOpaque(false);
        content.add(metricTile("Cảnh báo đang xử lý", "0", AppColor.ERROR));
        content.add(metricTile("Lô sắp hết hạn", "0", AppColor.WARNING));
        content.add(metricTile("Lô đã hết hạn", "0", AppColor.ERROR));
        content.add(metricTile("Phiếu chưa kiểm kê", "0", AppColor.ERROR));
        content.add(metricTile("Nhập hôm nay", "0", AppColor.SUCCESS));
        content.add(metricTile("Xuất hôm nay", "0", AppColor.ACCENT));
        content.add(metricTile("Tiêu hủy hôm nay", "0", AppColor.ERROR));
        content.add(metricTile("Giao dịch hôm nay", "0", AppColor.INFO));
        card.getContentPanel().add(content, BorderLayout.CENTER);
        return card;
    }

    private JPanel metricTile(String label, String initialValue, Color accent) {
        JPanel tile = new JPanel();
        tile.setOpaque(true);
        tile.setBackground(AppColor.BG_LIGHTER);
        tile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER),
                new EmptyBorder(6, 9, 6, 9)));
        tile.setLayout(new BoxLayout(tile, BoxLayout.Y_AXIS));

        JLabel value = new JLabel(initialValue);
        value.setFont(AppFont.HEADING_MD);
        value.setForeground(accent);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caption = new JLabel(label);
        caption.setFont(AppFont.SMALL);
        caption.setForeground(AppColor.TEXT_MUTED);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);

        tile.add(value);
        tile.add(Box.createVerticalStrut(2));
        tile.add(caption);

        if ("Cảnh báo đang xử lý".equals(label)) activeAlertValue = value;
        else if ("Lô sắp hết hạn".equals(label)) expiringValue = value;
        else if ("Lô đã hết hạn".equals(label)) expiredValue = value;
        else if ("Phiếu chưa kiểm kê".equals(label)) discrepancyValue = value;
        else if ("Nhập hôm nay".equals(label)) inboundValue = value;
        else if ("Xuất hôm nay".equals(label)) outboundValue = value;
        else if ("Tiêu hủy hôm nay".equals(label)) disposalValue = value;
        else if ("Giao dịch hôm nay".equals(label)) transactionValue = value;
        return tile;
    }

    private DashboardCard buildStockAlertCard() {
        DashboardCard card = new DashboardCard("Báo cáo từ nhân viên bán hàng",
                "Hết/sắp hết hàng - chưa xử lý",
                FontAwesomeSolid.BELL, AppColor.RED_ALT);
        stockAlertListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(stockAlertListPanel), BorderLayout.CENTER);
        return card;
    }

    // ---------------------------------------------------------------
    // Bảng sản phẩm + lô FEFO
    // ---------------------------------------------------------------

    private DashboardCard buildInventoryProductCard() {
        DashboardCard card = new DashboardCard(
                "Danh sách sản phẩm & lô",
                "Lô còn hàng được sắp theo hạn sử dụng gần nhất (FEFO) · tìm kiếm, phân trang và xuất Excel",
                FontAwesomeSolid.CLIPBOARD_LIST, AppColor.ACCENT);

        JButton exportButton = new JButton("Xuất Excel");
        exportButton.setFont(AppFont.BODY_BOLD);
        exportButton.setForeground(AppColor.ACCENT);
        exportButton.setBackground(AppColor.WHITE);
        exportButton.setFocusPainted(false);
        exportButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exportButton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER),
                new EmptyBorder(7, 12, 7, 12)));
        org.kordamp.ikonli.swing.FontIcon exportIcon = org.kordamp.ikonli.swing.FontIcon.of(
                FontAwesomeSolid.FILE_EXCEL, 14);
        exportIcon.setIconColor(AppColor.ACCENT);
        exportButton.setIcon(exportIcon);
        exportButton.setIconTextGap(7);
        exportButton.addActionListener(e -> exportInventoryProducts());
        card.setHeaderAction(exportButton);

        JPanel content = new JPanel(new BorderLayout(AppSpacing.SM, AppSpacing.SM));
        content.setOpaque(false);

        JPanel toolbar = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        toolbar.setOpaque(false);
        inventoryProductSearch = new BaseSearch("Tìm mã SP, tên sản phẩm, danh mục, thương hiệu...");
        inventoryProductSearch.onSearch(keyword -> {
            inventoryProductKeyword = keyword == null ? "" : keyword.trim();
            inventoryProductPage = 1;
            loadInventoryProductPage();
        });
        toolbar.add(inventoryProductSearch, BorderLayout.WEST);

        inventoryProductCount = new JLabel("0 sản phẩm");
        inventoryProductCount.setFont(AppFont.SMALL);
        inventoryProductCount.setForeground(AppColor.TEXT_MUTED);
        toolbar.add(inventoryProductCount, BorderLayout.EAST);
        content.add(toolbar, BorderLayout.NORTH);

        inventoryProductTable = new BaseTable(new String[]{
                "Mã SP", "Tên sản phẩm", "Tồn kho", "Mã lô", "HSD ", "SL lô", "Trạng thái"
        });
        inventoryProductTable.setRowHeight(50);

        // Căn giữa nội dung toàn bộ danh sách sản phẩm.
        // Căn giữa renderer của từng cột (BaseTable có thể dùng renderer riêng,
        // nên set default renderer Object.class chưa đủ).
        for (int col = 0; col < inventoryProductTable.getTable().getColumnCount(); col++) {
            javax.swing.table.DefaultTableCellRenderer renderer =
                    new javax.swing.table.DefaultTableCellRenderer();
            renderer.setHorizontalAlignment(SwingConstants.CENTER);
            inventoryProductTable.getTable().getColumnModel()
                    .getColumn(col).setCellRenderer(renderer);
        }

        // Cho phép bảng tự co giãn theo chiều rộng; khi màn hình hẹp hơn tổng
        // chiều rộng cột thì dùng thanh cuộn ngang thay vì ép chữ/cột quá chật.
        inventoryProductTable.enableHorizontalScroll();
        inventoryProductTable.setColumnWidths(95, 230, 90, 150, 105, 90, 110);
        inventoryProductTable.setColumnMinWidths(80, 150, 70, 120, 90, 75, 90);
        inventoryProductTable.getTable().getColumnModel().getColumn(2).setPreferredWidth(90);
        inventoryProductTable.getTable().getColumnModel().getColumn(5).setPreferredWidth(90);
        inventoryProductTable.getTable().getColumnModel().getColumn(6).setPreferredWidth(110);
        content.add(inventoryProductTable, BorderLayout.CENTER);

        inventoryProductPagination = new Pagination();
        inventoryProductPagination.setPageSize(inventoryProductPageSize);
        inventoryProductPagination.addPropertyChangeListener("pageChanged", e -> {
            inventoryProductPage = inventoryProductPagination.getCurrentPage();
            loadInventoryProductPage();
        });
        inventoryProductPagination.addPropertyChangeListener("pageSizeChanged", e -> {
            inventoryProductPageSize = inventoryProductPagination.getPageSize();
            inventoryProductPage = 1;
            loadInventoryProductPage();
        });
        content.add(inventoryProductPagination, BorderLayout.SOUTH);

        card.getContentPanel().add(content, BorderLayout.CENTER);
        // Bảng là khu vực dữ liệu chính nên cần chiều cao tối thiểu ổn định.
        // Nếu không đặt kích thước, BoxLayout của trang dashboard có thể thu
        // card về quá thấp khi viewport thay đổi, khiến header bảng/pagination
        // bị cảm giác "cắt" hoặc dồn sát nhau.
        card.setMinimumSize(new Dimension(0, 430));
        card.setPreferredSize(new Dimension(0, 470));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 470));
        return card;
    }

    private void loadInventoryProductPage() {
        if (inventoryProductTable == null) return;
        SwingWorker<PaginationHelper.PaginationResult<Product>, Void> worker =
                new SwingWorker<PaginationHelper.PaginationResult<Product>, Void>() {
            @Override
            protected PaginationHelper.PaginationResult<Product> doInBackground() {
                return productDao.getPagedInventoryOverview(
                        inventoryProductPage, inventoryProductPageSize, inventoryProductKeyword);
            }

            @Override
            protected void done() {
                try {
                    PaginationHelper.PaginationResult<Product> result = get();
                    renderInventoryProductPage(result);
                } catch (Exception e) {
                    BaseDialog.error(InventoryOverviewPanel.this, "Lỗi",
                            "Không thể tải danh sách sản phẩm: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void renderInventoryProductPage(PaginationHelper.PaginationResult<Product> result) {
        List<Product> products = result.getData() == null ? List.of() : result.getData();
        List<Integer> productIds = new ArrayList<>();
        for (Product p : products) productIds.add(p.getProductId());
        Map<Integer, List<InventoryBatch>> batchesByProduct =
                inventoryBatchDao.getActiveBatchesByProductIds(productIds);

        inventoryProductTable.clear();
        int sttOffset = (result.getCurrentPage() - 1) * result.getPageSize();
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            List<InventoryBatch> batches = batchesByProduct.getOrDefault(product.getProductId(), List.of());
            InventoryBatch fefo = batches.isEmpty() ? null : batches.get(0);
            String batchCode = fefo == null
                    ? "Chưa có lô còn hàng"
                    : (fefo.getBatchCode() == null || fefo.getBatchCode().isBlank()
                        ? "—" : fefo.getBatchCode());
            String expiry = fefo == null || fefo.getExpiryDate() == null
                    ? "—" : formatLocalDate(fefo.getExpiryDate());
            String fefoQty = fefo == null ? "0" : NumberUtil.formatThousands(fefo.getRemainingQty());
            String status = fefo == null
                    ? (product.getStock() <= 0 ? "Hết hàng" : "Không có lô")
                    : fefoStatus(fefo);

            inventoryProductTable.addRow(new Object[]{
                    product.getProductCode(),
                    product.getProductName(),
                    NumberUtil.formatThousands(product.getStock()),
                    batchCode,
                    expiry,
                    fefoQty,
                    status
            });
        }

        inventoryProductPagination.setCurrentPage(result.getCurrentPage());
        inventoryProductPagination.setPageSize(result.getPageSize());
        inventoryProductPagination.setTotalItems(result.getTotalRecords());
        inventoryProductCount.setText(NumberUtil.formatThousands(result.getTotalRecords()) + " sản phẩm");
        inventoryProductTable.revalidate();
        inventoryProductTable.repaint();
    }

    private String formatLocalDate(LocalDate date) {
        return date == null ? "" : date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private String fefoStatus(InventoryBatch batch) {
        if ("EXPIRED".equalsIgnoreCase(batch.getStatus())) return "Đã hết hạn";
        Long days = batch.daysUntilExpiry();
        if (days != null && days <= 30) return "Sắp hết hạn";
        return "Đang hoạt động";
    }

    private void exportInventoryProducts() {
        File chosen = FileUtil.chooseSaveLocation(this,
                "danh_sach_ton_kho_FEFO_" + new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx");
        if (chosen == null) return;
        File file = chosen.getName().toLowerCase().endsWith(".xlsx")
                ? chosen : new File(chosen.getParentFile(), chosen.getName() + ".xlsx");

        loadingOverlay.start("Đang xuất danh sách tồn kho...");
        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                List<Product> products = productDao.getAllInventoryOverview(inventoryProductKeyword);
                List<Integer> ids = new ArrayList<>();
                for (Product p : products) ids.add(p.getProductId());
                Map<Integer, List<InventoryBatch>> batchesByProduct =
                        inventoryBatchDao.getActiveBatchesByProductIds(ids);

                String[] headers = {"Mã SP", "Tên sản phẩm", "Tồn kho", "Mã Lô", "HSD", "SL lô", "Trạng thái"};
                List<Object[]> rows = new ArrayList<>();
                for (Product product : products) {
                    List<InventoryBatch> batches = batchesByProduct.getOrDefault(product.getProductId(), List.of());
                    InventoryBatch fefo = batches.isEmpty() ? null : batches.get(0);
                    rows.add(new Object[]{
                            product.getProductCode(), product.getProductName(), product.getStock(),
                            fefo == null ? "Chưa có lô còn hàng" :
                                    (fefo.getBatchCode() == null || fefo.getBatchCode().isBlank() ? "—" : fefo.getBatchCode()),
                            fefo == null || fefo.getExpiryDate() == null ? "" : formatLocalDate(fefo.getExpiryDate()),
                            fefo == null ? 0 : fefo.getRemainingQty(),
                            fefo == null ? (product.getStock() <= 0 ? "Hết hàng" : "Không có lô") : fefoStatus(fefo)
                    });
                }
                TableExportUtil.exportExcel(file, "Tồn kho FEFO", headers, rows);
                return rows.size();
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    BaseDialog.success(InventoryOverviewPanel.this, "Thành công",
                            "Đã xuất " + get() + " sản phẩm vào file \"" + file.getName() + "\".");
                } catch (Exception e) {
                    BaseDialog.error(InventoryOverviewPanel.this, "Lỗi",
                            "Xuất Excel thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
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

    /** JPanel cột dọc (BoxLayout Y_AXIS) luôn "tracks" chiều rộng vùng nhìn của JScrollPane cha - tránh cuộn ngang. */
    private static class ScrollableColumn extends JPanel implements Scrollable {
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
    // Tải dữ liệu
    // ---------------------------------------------------------------

    private static class InventorySnapshot {
        OverallSummary summary;
        List<LowStockItem> lowStockItems;
        List<StockAlert> pendingAlerts;
        MonthlyCategoryTrend trend;
        MovementSummary movement;
        int activeAlerts;
        int expiringSoon;
        int expiredWithStock;
        int discrepancies;
    }

    private void loadData() {
        loadingOverlay.start("Đang tải dữ liệu...");
        SwingWorker<InventorySnapshot, Void> worker = new SwingWorker<InventorySnapshot, Void>() {
            @Override
            protected InventorySnapshot doInBackground() {
                InventorySnapshot data = new InventorySnapshot();
                data.summary = inventoryReportDao.getOverallSummary();
                data.lowStockItems = dashboardDao.getLowStockProducts(8);

                List<StockAlert> alerts = stockAlertDao.getUnseenForInventoryManager();
                data.pendingAlerts = alerts.size() > 8 ? alerts.subList(0, 8) : alerts;

                data.trend = inventoryReportDao.getMonthlyCategoryStockTrend(
                        LocalDate.now().minusMonths(5).withDayOfMonth(1), LocalDate.now());
                data.movement = inventoryReportDao.getMovementSummary(1);
                data.activeAlerts = stockAlertDao.countActive();
                data.expiringSoon = inventoryBatchDao.countExpiringSoon(30);
                data.expiredWithStock = inventoryBatchDao.countExpiredWithStock();
                data.discrepancies = reconciliationDao.countUncheckedToday();
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    applyData(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(InventoryOverviewPanel.this, "Lỗi", "Không thể tải dữ liệu tổng quan kho: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyData(InventorySnapshot data) {
        OverallSummary s = data.summary;
        // Dashboard kho ưu tiên giá trị theo GIÁ NHẬP/GIÁ VỐN thay vì giá bán.
        valueCard.setValue(NumberUtil.formatThousands(s.valueAtImportPrice.longValue()) + " đ");
        valueCard.setSubtitle("Theo giá nhập · " + s.productCount + " sản phẩm");

        quantityCard.setValue(NumberUtil.formatThousands(s.totalQuantity));
        quantityCard.setSubtitle("Đơn vị tồn kho hiện tại");

        int needReorderCount = s.lowStockCount + s.outOfStockCount;
        lowStockCard.setValue(String.valueOf(needReorderCount));
        lowStockCard.setSubtitle("SP ở mức tối thiểu hoặc thấp hơn");

        outOfStockCard.setValue(String.valueOf(s.outOfStockCount));
        outOfStockCard.setSubtitle("SP hiện không còn hàng");

        trendChartPanel.setData(data.trend);

        if (activeAlertValue != null) activeAlertValue.setText(String.valueOf(data.activeAlerts));
        if (expiringValue != null) expiringValue.setText(String.valueOf(data.expiringSoon));
        if (expiredValue != null) expiredValue.setText(String.valueOf(data.expiredWithStock));
        if (discrepancyValue != null) discrepancyValue.setText(String.valueOf(data.discrepancies));
        if (inboundValue != null) inboundValue.setText(NumberUtil.formatThousands(data.movement.inboundQuantity));
        if (outboundValue != null) outboundValue.setText(NumberUtil.formatThousands(data.movement.outboundQuantity));
        if (disposalValue != null) disposalValue.setText(NumberUtil.formatThousands(data.movement.disposalQuantity));
        if (transactionValue != null) transactionValue.setText(NumberUtil.formatThousands(data.movement.transactionCount));

        renderLowStock(data.lowStockItems);
        renderStockAlerts(data.pendingAlerts);
    }

    // ---------------------------------------------------------------
    // Danh sách: sản phẩm sắp hết hàng
    // ---------------------------------------------------------------

    private void renderLowStock(List<LowStockItem> items) {
        lowStockListPanel.removeAll();
        if (items.isEmpty()) {
            lowStockListPanel.add(emptyRow("Không có sản phẩm nào dưới mức tồn kho tối thiểu"));
        } else {
            for (LowStockItem item : items) {
                lowStockListPanel.add(buildLowStockRow(item));
                lowStockListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        lowStockListPanel.revalidate();
        lowStockListPanel.repaint();
    }

    private JPanel buildLowStockRow(LowStockItem item) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(item.productName);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        JLabel codeLabel = new JLabel(item.productCode);
        codeLabel.setFont(AppFont.FOOTER);
        codeLabel.setForeground(AppColor.TEXT_MUTED);
        left.add(nameLabel);
        left.add(codeLabel);

        boolean out = item.isOutOfStock();
        StatBadge badge = new StatBadge(out ? "Hết hàng" : item.stock + "/" + item.minStock,
                out ? AppColor.ERROR : AppColor.WARNING);

        row.add(left, BorderLayout.CENTER);
        row.add(badge, BorderLayout.EAST);
        return row;
    }

    // ---------------------------------------------------------------
    // Danh sách: báo cáo thiếu hàng từ NV bán hàng (chưa xử lý)
    // ---------------------------------------------------------------

    private void renderStockAlerts(List<StockAlert> alerts) {
        stockAlertListPanel.removeAll();
        if (alerts.isEmpty()) {
            stockAlertListPanel.add(emptyRow("Không có báo cáo nào chưa xử lý"));
        } else {
            for (StockAlert alert : alerts) {
                stockAlertListPanel.add(buildStockAlertRow(alert));
                stockAlertListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        stockAlertListPanel.revalidate();
        stockAlertListPanel.repaint();
    }

    private JPanel buildStockAlertRow(StockAlert alert) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(alert.getProductName());
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        JLabel byLabel = new JLabel("Báo bởi " + alert.getReportedByName());
        byLabel.setFont(AppFont.FOOTER);
        byLabel.setForeground(AppColor.TEXT_MUTED);
        left.add(nameLabel);
        left.add(byLabel);

        boolean out = "OUT_OF_STOCK".equals(alert.getAlertType());

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        StatBadge badge = new StatBadge(out ? "Hết hàng" : "Sắp hết", out ? AppColor.ERROR : AppColor.WARNING);
        badge.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel timeLabel = new JLabel(DateUtil.timeAgo(toDate(alert.getCreatedAt())));
        timeLabel.setFont(AppFont.FOOTER);
        timeLabel.setForeground(AppColor.TEXT_DISABLED);
        timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(badge);
        right.add(timeLabel);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static Date toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ---------------------------------------------------------------
    // Chung
    // ---------------------------------------------------------------

    private JPanel emptyRow(String message) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(28, 8, 28, 8));
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        row.add(label, BorderLayout.CENTER);
        return row;
    }
}