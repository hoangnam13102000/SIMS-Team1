package com.view.admin.inventory;

import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.AutoRowNumber;
import com.dao.InventoryBatchDAO;
import com.model.InventoryBatch;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.SwingUtilities;

public class InventoryBatchPanel extends BaseCrudPanel<InventoryBatch> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Nguong "sap het han" tinh bang so ngay - dung chung cho ca mau badge va bo loc canh bao.
    private static final int NEAR_EXPIRY_DAYS = 7;

    private final InventoryBatchDAO batchDAO = new InventoryBatchDAO();
    private AutoRowNumber stt;

    public InventoryBatchPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // STT | Ma lo | San pham | Nha cung cap | NSX | HSD | SL nhap | Con lai | Gia nhap | Trang thai
        table.setColumnWidths(45, 95, 150, 130, 90, 90, 70, 80, 110, 120);
        table.setColumnMinWidths(35, 80, 120, 100, 80, 80, 60, 70, 100, 100);
        table.setBadgeColumn(9, this::statusLabel, this::statusColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BOXES; }

    @Override
    protected String getPageTitle() { return "Quản lý lô hàng"; }

    @Override
    protected String getPageSubtitle() { return "Theo dõi lô hàng theo hạn sử dụng)"; }

    @Override
    protected String getAddButtonLabel() {
        // Chi ai co STOCK_IMPORT (Inventory Manager, Admin) moi duoc tao lo hang moi.
        // Truoc day trang nay chi gate theo "vao duoc trang hay khong" (STOCK_IMPORT
        // HOAC STOCK_VIEW), nen Sales Staff (chi co STOCK_VIEW de tra ton kho luc ban
        // hang) cung thay va bam duoc nut nay - day la loi da phat hien khi demo.
        return AuthService.getInstance().can(AppPermission.STOCK_IMPORT) ? "Nhập lô hàng mới" : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã lô", "Sản phẩm", "Nhà cung cấp",
                "NSX", "HSD", "SL nhập", "Còn lại", "Giá nhập", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(InventoryBatch item) {
        return new Object[]{
                "",
                item.getBatchCode(),
                item.getProductName(),
                item.getSupplierName(),
                formatDate(item.getManufactureDate()),
                formatDate(item.getExpiryDate()),
                item.getQuantity(),
                item.getRemainingQty(),
                NumberUtil.formatThousands(item.getImportPrice().longValue()),
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{6, 7, 8}; }

    @Override
    protected String getEntityLabel() { return "lô hàng"; }

    @Override
    protected String getItemDisplayName(InventoryBatch item) {
        return item.getBatchCode() + " - " + item.getProductName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<InventoryBatch> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<InventoryBatch> fetchPage(int page, int pageSize) {
        return batchDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<InventoryBatch> searchPage(String keyword, int page, int pageSize) {
        return batchDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<InventoryBatch> fetchAllForExport() {
        batchDAO.syncExpiredStatus();
        return batchDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, mã SP, số lô, nhà cung cấp..."; }

    // ---------------------------------------------------------------
    // Lo hang la du lieu kho, khong cho sua/xoa tuy tien (se pha vo tinh
    // toan ven FEFO va so cai ton kho) - chi xem chi tiet va them lo moi.
    // ---------------------------------------------------------------

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        InventoryBatch item = rowToItem(modelRow);
        if (item == null) return;
        openDialog(CrudMode.VIEW, item);
    }

    @Override
    protected void openForm(InventoryBatch item) {
        // item luon null o day vi supportsEdit() = false (nut Sua bi an, editRow
        // khong bao gio duoc goi) - chi con duong vao la nut "Nhap lo hang moi".
        openDialog(CrudMode.ADD, null);
    }

    @Override
    protected boolean deleteItem(InventoryBatch item) { return false; }

    private void openDialog(CrudMode mode, InventoryBatch item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        InventoryBatchFormDialog dialog = new InventoryBatchFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, batchDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Nhan/mau trang thai - tinh truc tiep tu HSD + so luong con lai,
    // khong chi dua vao cot Status (tranh phai cho syncExpiredStatus chay xong).
    // ---------------------------------------------------------------

    /** Tinh nhan tu du lieu goc (goi trong mapRowToColumns, truoc khi ghi vao bang). */
    private String statusLabel(InventoryBatch b) {
        if (b.getRemainingQty() <= 0) return "Đã bán hết";
        Long days = b.daysUntilExpiry();
        if (days != null && days < 0) return "Hết hạn";
        if (days != null && days <= NEAR_EXPIRY_DAYS) return "Sắp hết hạn";
        return "Còn hàng";
    }

    /** BaseTable.setBadgeColumn goi lai ham nay voi gia tri DA la chuoi nhan (khong phai InventoryBatch). */
    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        String label = String.valueOf(value);
        switch (label) {
            case "Đã bán hết": return AppColor.TEXT_MUTED;
            case "Hết hạn": return AppColor.ERROR;
            case "Sắp hết hạn": return AppColor.WARNING;
            default: return AppColor.SUCCESS; // "Còn hàng"
        }
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}