package com.view.admin.invoice;

import com.components.crud.BaseCrudPanel;
import com.dao.InvoiceDAO;
import com.model.Invoice;
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

/**
 * Trang "Quan ly hoa don" - danh sach cac hoa don ban hang (Invoices) da
 * duoc lap. Cung tinh chat so sach/lich su nhu PurchaseReceiptPanel: chi
 * TRA CUU + HUY, khong tao moi tai day - viec lap hoa don moi thuoc ve luong
 * ban hang (gio hang -> thanh toan) o phia client, hien tai van la mock nen
 * chua ghi xuong bang Invoices.
 * <p>
 * Khong cho sua/xoa vat ly: trigger trg_Invoices_BlockDelete chan xoa, va
 * sua se lam lech doanh thu/ton kho. "Huy" o day la HUY NGHIEP VU (doi
 * Status -> CANCELLED, hoan lai ton kho dung tung lo) thuc hien trong
 * InvoiceDetailDialog, khong dung co che deleteItem() mac dinh cua
 * BaseCrudPanel (vi can nhap ly do huy, khong chi la 1 dialog xac nhan don gian).
 */
public class InvoicePanel extends BaseCrudPanel<Invoice> {

    /** Chỉ ngày, không giờ. */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceDAO invoiceDAO = new InvoiceDAO();

    public InvoicePanel() {
        super();

        // Không STT / Số mặt hàng. Ngày tạo chỉ dd/MM/yyyy.
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);

        initialLoad();
        applyColumnWidths();
    }

    private void applyColumnWidths() {
        // Mã HĐ | Khách hàng | Người tạo | Ngày tạo | Tổng tiền | PT thanh toán | Trạng thái
        table.setColumnWidths(175, 150, 130, 110, 120, 120, 110);
        table.setColumnMinWidths(165, 110, 100, 100, 100, 100, 95);
        if (table.getTable().getColumnModel().getColumnCount() > 0) {
            var col = table.getTable().getColumnModel().getColumn(0);
            col.setMinWidth(165);
            col.setPreferredWidth(175);
        }
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.RECEIPT; }

    @Override
    protected String getPageTitle() { return "Quản lý hóa đơn"; }

    @Override
    protected String getPageSubtitle() { return "Tra cứu lịch sử các hóa đơn bán hàng đã lập"; }

    // Lap hoa don moi thuc hien o luong ban hang (gio hang/thanh toan) -
    // trang nay chi tra cuu lai, nen an nut them.
    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã hóa đơn", "Khách hàng", "Người tạo", "Ngày tạo",
                "Tổng tiền", "PT thanh toán", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Invoice item) {
        return new Object[]{
                item.getInvoiceCode(),
                item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ",
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FORMAT) : "-",
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                paymentMethodLabel(item.getPaymentMethod()),
                statusLabel(item)
        };
    }

    /** Tổng tiền (chỉ số 4) — sort theo số. */
    @Override
    protected int[] numericColumns() { return new int[]{4}; }

    @Override
    protected String getEntityLabel() { return "hóa đơn"; }

    @Override
    protected String getItemDisplayName(Invoice item) {
        return item.getInvoiceCode() + " - " + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
    }

    @Override
    protected PaginationHelper.PaginationResult<Invoice> fetchPage(int page, int pageSize) {
        return invoiceDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Invoice> searchPage(String keyword, int page, int pageSize) {
        return invoiceDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<Invoice> fetchAllForExport() {
        return invoiceDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã hóa đơn, khách hàng, người tạo..."; }

    // ---------------------------------------------------------------
    // Chi xem chi tiet - khong sua/xoa (xem ly do o javadoc dau file).
    // Huy hoa don thuc hien ben trong InvoiceDetailDialog.
    // ---------------------------------------------------------------

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        Invoice item = rowToItem(modelRow);
        if (item == null) return;
        openDetailDialog(item);
    }

    @Override
    protected void openForm(Invoice item) {
        // Khong bao gio duoc goi: getAddButtonLabel() = null va supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(Invoice item) { return false; }

    private void openDetailDialog(Invoice item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        InvoiceDetailDialog dialog = new InvoiceDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, invoiceDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Nhan/mau trang thai hoa don
    // ---------------------------------------------------------------

    private String statusLabel(Invoice inv) {
        return inv.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    /** BaseTable.setBadgeColumn goi lai ham nay voi gia tri DA la chuoi nhan (khong phai Invoice). */
    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    static String paymentMethodLabel(String method) {
        if (method == null) return "-";
        switch (method) {
            case "CASH": return "Tiền mặt";
            case "BANK_TRANSFER": return "Chuyển khoản";
            case "PAYPAL": return "PayPal";
            case "CARD": return "Thẻ";
            default: return method;
        }
    }
}