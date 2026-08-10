package com.view.admin.inventory;

import com.components.FilterDropdown;
import com.components.StatCard;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.components.table.BatchLocatorColumn;
import com.components.table.StockLevelColumn;
import com.dao.CategoryDAO;
import com.dao.InventoryBatchDAO;
import com.dao.InventoryReportDAO;
import com.dao.InventoryReportDAO.OverallSummary;
import com.dao.ProductDAO;
import com.model.Category;
import com.model.InventoryBatch;
import com.model.Product;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import com.view.admin.product.ProductDetailDialog;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * "Tổng quan kho" - trang landing của mục "Quản lý kho" (đặt trước Phiếu
 * nhập, Lô hàng...), lấy cảm hứng từ dashboard "Stock Overview" người dùng
 * gửi tham khảo. CHỈ XEM (không thêm/sửa/xóa trực tiếp ở đây) - tồn kho chỉ
 * thay đổi qua Phiếu nhập, Đơn hàng, Kiểm kê, Hủy hàng... đúng nguyên tắc
 * "1 nguồn sự thật" (InventoryBatch + trigger) đã áp dụng xuyên suốt SIMS.
 * <p>
 * 3 StatCard đầu trang tái sử dụng thẳng {@link InventoryReportDAO#getOverallSummary()}
 * (đã có sẵn cho trang "Báo cáo hàng tồn kho") - không tạo query trùng lặp.
 * <p>
 * Cột "Lô hàng" là điểm khác biệt chính với danh sách "Sản phẩm" thường:
 * hiện thẳng mã lô (BatchCode) đang còn tồn của sản phẩm đó, ưu tiên lô gần
 * hết hạn nhất lên trước (FEFO) - nhân viên nhìn là biết ngay cần tìm đúng
 * lô nào trong kho để bổ sung lên kệ, không phải mở riêng trang "Quản lý lô
 * hàng" rồi lọc lại theo sản phẩm. Dữ liệu lô được nạp 1 LẦN/TRANG (IN (...)
 * qua {@link InventoryBatchDAO#getActiveBatchesByProductIds}, không N+1)
 * ngay trong {@link #fetchPage}/{@link #searchPage}/{@link #fetchAllForExport}
 * (chạy nền), rồi {@link #mapRowToColumns} tra cứu lại từ map - tra cứu này
 * luôn chạy SAU khi map đã được nạp xong (cùng 1 luồng nền, hoặc EDT sau khi
 * SwingWorker hoàn tất) nên không cần đồng bộ hóa gì thêm.
 */
public class InventoryOverviewPanel extends BaseCrudPanel<Product> {

    private static final String STATUS_OUT = "Hết hàng";
    private static final String STATUS_LOW = "Sắp hết";
    private static final String STATUS_OK = "Còn hàng";

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final InventoryReportDAO inventoryReportDAO = new InventoryReportDAO();
    private final InventoryBatchDAO inventoryBatchDAO = new InventoryBatchDAO();

    private FilterDropdown<CategoryOption> categoryFilter;

    private StatCard valueCard;
    private StatCard lowStockCard;
    private StatCard outOfStockCard;

    /** Lô còn tồn theo ProductID cho ĐÚNG tập sản phẩm vừa fetch gần nhất (trang hiện tại hoặc toàn bộ khi export). */
    private volatile Map<Integer, List<InventoryBatch>> activeBatchesByProduct = Collections.emptyMap();

    public InventoryOverviewPanel() {
        super();

        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic);
        table.setActionColumn(actions);

        table.setImageColumn(0, 40);
        table.setCustomColumn(4, StockLevelColumn.renderer(table.rowColorProvider()));
        table.setCustomColumn(5, BatchLocatorColumn.renderer(table.rowColorProvider()));
        table.setBadgeColumn(6, v -> (String) v, this::statusColor);
        table.getSorter().comparator(4, (a, b) ->
                Integer.compare(((StockLevelColumn.Data) a).stock, ((StockLevelColumn.Data) b).stock));

        table.setColumnWidths(52, 85, 170, 130, 150, 190, 100);
        table.setColumnMinWidths(48, 70, 130, 100, 120, 160, 90);

        buildFilterBar();

        initialLoad();
    }

    // ---------------------------------------------------------------
    // Bộ lọc danh mục (giống ProductPanel)
    // ---------------------------------------------------------------

    private static final class CategoryOption {
        final Integer categoryId;
        final String label;

        CategoryOption(Integer categoryId, String label) {
            this.categoryId = categoryId;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void buildFilterBar() {
        List<Category> categories = categoryDAO.findAll();
        CategoryOption[] options = new CategoryOption[categories.size() + 1];
        options[0] = new CategoryOption(null, "Tất cả danh mục");
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            options[i + 1] = new CategoryOption(c.getCategoryId(), c.getCategoryName());
        }

        categoryFilter = new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, options);
        categoryFilter.onChange(opt -> applyFilters());
        addToolbarFilter(categoryFilter);
    }

    private Integer selectedCategoryId() {
        CategoryOption opt = categoryFilter == null ? null : categoryFilter.getSelected();
        return opt == null ? null : opt.categoryId;
    }

    // ---------------------------------------------------------------
    // StatCard tổng quan - tái sử dụng InventoryReportDAO.getOverallSummary()
    // ---------------------------------------------------------------

    @Override
    protected List<JComponent> buildStatsCards() {
        valueCard = new StatCard("Giá trị tồn kho", "0 đ", FontAwesomeSolid.WALLET, AppColor.ACCENT);
        lowStockCard = new StatCard("Cần đặt hàng lại", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);
        outOfStockCard = new StatCard("Hết hàng", "0", FontAwesomeSolid.TIMES_CIRCLE, AppColor.ERROR);

        List<JComponent> cards = new ArrayList<>();
        cards.add(valueCard);
        cards.add(lowStockCard);
        cards.add(outOfStockCard);
        return cards;
    }

    /** Truy vấn lại 3 chỉ số tổng quan (chạy nền, không chặn UI). */
    private void refreshStatsCards() {
        if (valueCard == null) return;
        SwingWorker<OverallSummary, Void> worker = new SwingWorker<>() {
            @Override
            protected OverallSummary doInBackground() {
                return inventoryReportDAO.getOverallSummary();
            }

            @Override
            protected void done() {
                try {
                    OverallSummary s = get();
                    valueCard.setValue(NumberUtil.formatThousands(s.valueAtSellPrice.longValue()) + " đ");
                    valueCard.setSubtitle(s.productCount + " sản phẩm · " + NumberUtil.formatThousands(s.totalQuantity) + " đơn vị tồn");
                    // "Cần đặt hàng lại" = mọi SP có Stock <= MinStock, KỂ CẢ hết hàng hẳn (Stock = 0)
                    // - InventoryReportDAO tách 2 nhóm nay rieng cho trang Bao cao (khong overlap),
                    // nhung o day gop lai cho dung nghia "can dat hang" ma nguoi dung mong doi.
                    int needReorderCount = s.lowStockCount + s.outOfStockCount;
                    lowStockCard.setValue(String.valueOf(needReorderCount));
                    lowStockCard.setSubtitle("Sản phẩm ở mức tồn tối thiểu hoặc thấp hơn");
                    outOfStockCard.setValue(String.valueOf(s.outOfStockCount));
                    outOfStockCard.setSubtitle("Sản phẩm hiện không còn hàng");
                } catch (Exception ignored) {
                    // Loi tai lai StatCard khong nghiem trong - bang du lieu chinh van hien thi binh thuong.
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------------
    // Cấu hình BaseCrudPanel - CHỈ XEM
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.WAREHOUSE; }

    @Override
    protected String getPageTitle() { return "Tổng quan kho"; }

    @Override
    protected String getPageSubtitle() { return "Toàn cảnh tồn kho hiện tại theo từng sản phẩm - tồn kho chỉ thay đổi qua phiếu nhập, đơn hàng, kiểm kê hoặc hủy hàng"; }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Ảnh", "Mã SP", "Tên sản phẩm", "Danh mục", "Tồn kho", "Lô hàng", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Product item) {
        List<InventoryBatch> batches = activeBatchesByProduct.getOrDefault(item.getProductId(), List.of());
        return new Object[]{
                item.getImageUrl(),
                item.getProductCode(),
                item.getProductName(),
                item.getCategoryName(),
                new StockLevelColumn.Data(item.getStock(), item.getMinStock()),
                new BatchLocatorColumn.Cell(batches),
                stockStatusLabel(item)
        };
    }

    @Override
    protected String getEntityLabel() { return "sản phẩm"; }

    @Override
    protected String getItemDisplayName(Product item) { return item.getProductName(); }

    @Override
    protected PaginationHelper.PaginationResult<Product> fetchPage(int page, int pageSize) {
        return withActiveBatches(productDAO.getPagedFiltered(page, pageSize, null, selectedCategoryId(), null, null));
    }

    @Override
    protected PaginationHelper.PaginationResult<Product> searchPage(String keyword, int page, int pageSize) {
        return withActiveBatches(productDAO.getPagedFiltered(page, pageSize, keyword, selectedCategoryId(), null, null));
    }

    @Override
    protected List<Product> fetchAllForExport() {
        List<Product> all = productDAO.getAll();
        loadActiveBatchesFor(all);
        return all;
    }

    /** Nạp lô còn tồn cho ĐÚNG tập sản phẩm vừa fetch (1 query IN (...), không N+1) rồi trả lại y nguyên kết quả. */
    private PaginationHelper.PaginationResult<Product> withActiveBatches(PaginationHelper.PaginationResult<Product> result) {
        loadActiveBatchesFor(result.getData());
        return result;
    }

    private void loadActiveBatchesFor(List<Product> products) {
        List<Integer> ids = new ArrayList<>();
        if (products != null) {
            for (Product p : products) ids.add(p.getProductId());
        }
        activeBatchesByProduct = inventoryBatchDAO.getActiveBatchesByProductIds(ids);
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã SP, tên sản phẩm, danh mục..."; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Product> result) {
        table.getTable().repaint();
        refreshStatsCards();
    }

    @Override
    protected void openForm(Product item) { /* Chỉ xem - không có form thêm/sửa ở màn hình này. */ }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Product item) { return false; }

    // ---------------------------------------------------------------
    // Trạng thái tồn kho (suy ra trực tiếp Stock vs MinStock)
    // ---------------------------------------------------------------

    private static String stockStatusLabel(Product item) {
        if (item.getStock() <= 0) return STATUS_OUT;
        if (item.getStock() <= item.getMinStock()) return STATUS_LOW;
        return STATUS_OK;
    }

    private Color statusColor(Object value) {
        String label = (String) value;
        if (STATUS_OUT.equals(label)) return AppColor.ERROR;
        if (STATUS_LOW.equals(label)) return AppColor.WARNING;
        return AppColor.SUCCESS;
    }

    private void viewRowPublic(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductDetailDialog dialog = new ProductDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }
}