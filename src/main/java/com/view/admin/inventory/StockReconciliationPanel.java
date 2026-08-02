package com.view.admin.inventory;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
import com.dao.ProductDAO;
import com.dao.StockReconciliationDAO;
import com.model.Product;
import com.model.StockReconciliation;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Man hinh "Đối chiếu / kiểm kê kho cuối ngày" (Quan ly kho): lich su cac
 * lan doi chieu (chi xem, khong sua/xoa - xem trg_StockReconciliation_BlockDelete
 * o sql/Trigger_SIMS.sql, ap dung nguyen tac R3). Nut "Kiem ke moi" mo
 * StockCountDialog - nhap so dem thuc te cho toan bo san pham dang ban,
 * luu thanh 1 phien doi chieu duy nhat.
 */
public class StockReconciliationPanel extends BaseCrudPanel<StockReconciliation> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StockReconciliationDAO reconciliationDAO = new StockReconciliationDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private AutoRowNumber stt;

    public StockReconciliationPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // STT | Ma SP | San pham | Ton he thong | Ton thuc te | Chenh lech | Nguoi doi chieu | Thoi gian
        table.setColumnWidths(45, 90, 170, 100, 100, 90, 130, 130);
        table.setColumnMinWidths(35, 70, 130, 80, 80, 70, 100, 110);
        table.setBadgeColumn(5, this::discrepancyLabel, this::discrepancyColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BALANCE_SCALE; }

    @Override
    protected String getPageTitle() { return "Đối chiếu kho cuối ngày"; }

    @Override
    protected String getPageSubtitle() { return "So sánh tồn hệ thống với số đếm thực tế, phát hiện thất thoát"; }

    @Override
    protected String getAddButtonLabel() { return "Kiểm kê mới"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã SP", "Sản phẩm", "Tồn hệ thống", "Tồn thực tế",
                "Chênh lệch", "Người đối chiếu", "Thời gian"};
    }

    @Override
    protected Object[] mapRowToColumns(StockReconciliation item) {
        return new Object[]{
                "",
                item.getProductCode(),
                item.getProductName(),
                item.getSystemStock(),
                item.getActualStock(),
                discrepancyText(item.getDiscrepancy()),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-"
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{3, 4}; }

    @Override
    protected String getEntityLabel() { return "phiên đối chiếu kho"; }

    @Override
    protected String getItemDisplayName(StockReconciliation item) {
        return item.getProductName() + " - " + (item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "");
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockReconciliation> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<StockReconciliation> fetchPage(int page, int pageSize) {
        return reconciliationDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<StockReconciliation> searchPage(String keyword, int page, int pageSize) {
        return reconciliationDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<StockReconciliation> fetchAllForExport() {
        return reconciliationDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, mã SP, người đối chiếu..."; }

    // ---------------------------------------------------------------
    // La chung tu doi soat - chi xem, khong sua/xoa (giong tinh than
    // InventoryBatchPanel voi lo hang).
    // ---------------------------------------------------------------

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        StockReconciliation item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        StockReconciliationDetailDialog dialog = new StockReconciliationDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    @Override
    protected void openForm(StockReconciliation item) {
        // item luon null o day vi supportsEdit() = false - chi con duong vao la nut "Kiem ke moi".
        List<Product> activeProducts = productDAO.findAllActive();
        if (activeProducts.isEmpty()) {
            BaseDialog.info(this, "Không có sản phẩm",
                    "Chưa có sản phẩm đang bán nào để kiểm kê. Vui lòng thêm sản phẩm trước.");
            return;
        }

        Integer userId = AuthService.getInstance().getCurrentUser() != null
                ? AuthService.getInstance().getCurrentUser().getUserId() : null;
        if (userId == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        StockCountDialog dialog = new StockCountDialog(
                owner instanceof Frame ? (Frame) owner : null, activeProducts, reconciliationDAO, userId);
        dialog.onSaved(this::reload);
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(StockReconciliation item) { return false; }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Nhan/mau chenh lech
    // ---------------------------------------------------------------

    private String discrepancyText(int discrepancy) {
        if (discrepancy == 0) return "Khớp";
        return (discrepancy > 0 ? "+" : "") + discrepancy;
    }

    /** BaseTable.setBadgeColumn goi lai voi gia tri DA la chuoi nhan (khong phai StockReconciliation). */
    private String discrepancyLabel(Object value) {
        return String.valueOf(value);
    }

    private Color discrepancyColor(Object value) {
        String label = String.valueOf(value);
        if ("Khớp".equals(label)) return AppColor.SUCCESS;
        return label.startsWith("+") ? AppColor.WARNING : AppColor.ERROR;
    }
}