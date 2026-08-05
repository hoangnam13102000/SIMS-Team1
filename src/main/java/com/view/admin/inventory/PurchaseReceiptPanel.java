package com.view.admin.inventory;

import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
import com.dao.PurchaseReceiptDAO;
import com.model.PurchaseReceipt;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;


public class PurchaseReceiptPanel extends BaseCrudPanel<PurchaseReceipt> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PurchaseReceiptDAO receiptDAO = new PurchaseReceiptDAO();
    private AutoRowNumber stt;

    public PurchaseReceiptPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        table.setColumnWidths(45, 110, 160, 130, 130, 90, 130, 110);
        table.setColumnMinWidths(35, 95, 130, 110, 110, 70, 110, 90);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.FILE_INVOICE; }

    @Override
    protected String getPageTitle() { return "Quản lý nhập kho"; }

    @Override
    protected String getPageSubtitle() { return "Tra cứu lịch sử các phiếu nhập kho đã lập theo từng nhà cung cấp"; }

    // Nhap kho moi thuc hien o trang "Quan ly lo hang" (nut "Nhap lo hang
    // moi") - trang nay chi tra cuu lai, nen an nut them.
    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã phiếu", "Nhà cung cấp", "Người tạo", "Ngày tạo",
                "Số mặt hàng", "Tổng tiền", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(PurchaseReceipt item) {
        return new Object[]{
                "",
                item.getReceiptCode(),
                item.getSupplierName(),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{5, 6}; }

    @Override
    protected String getEntityLabel() { return "phiếu nhập kho"; }

    @Override
    protected String getItemDisplayName(PurchaseReceipt item) {
        return item.getReceiptCode() + " - " + item.getSupplierName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<PurchaseReceipt> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> fetchPage(int page, int pageSize) {
        return receiptDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> searchPage(String keyword, int page, int pageSize) {
        return receiptDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<PurchaseReceipt> fetchAllForExport() {
        return receiptDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã phiếu, nhà cung cấp, người tạo..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (PurchaseReceipt r : receiptDAO.getAll()) {
            if (r.getReceiptCode() != null && !r.getReceiptCode().isBlank()) {
                names.add(r.getReceiptCode());
            }
            if (r.getSupplierName() != null && !r.getSupplierName().isBlank()) {
                names.add(r.getSupplierName());
            }
            if (r.getCreatedByName() != null && !r.getCreatedByName().isBlank()) {
                names.add(r.getCreatedByName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    // ---------------------------------------------------------------
    // Chi xem chi tiet - khong sua/xoa (xem ly do o javadoc dau file).
    // ---------------------------------------------------------------

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        PurchaseReceipt item = rowToItem(modelRow);
        if (item == null) return;
        openDetailDialog(item);
    }

    @Override
    protected void openForm(PurchaseReceipt item) {
        // Khong bao gio duoc goi: getAddButtonLabel() = null va supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(PurchaseReceipt item) { return false; }

    private void openDetailDialog(PurchaseReceipt item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        PurchaseReceiptDetailDialog dialog = new PurchaseReceiptDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, receiptDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Nhan/mau trang thai phieu
    // ---------------------------------------------------------------

    private String statusLabel(PurchaseReceipt r) {
        return r.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    /** BaseTable.setBadgeColumn goi lai ham nay voi gia tri DA la chuoi nhan (khong phai PurchaseReceipt). */
    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }
}