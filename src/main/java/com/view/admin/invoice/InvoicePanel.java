package com.view.admin.invoice;

import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
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

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvoiceDAO invoiceDAO = new InvoiceDAO();
    private AutoRowNumber stt;

    public InvoicePanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // STT | Ma hoa don | Khach hang | Nguoi tao | Ngay tao | So mat hang | Tong tien | PT thanh toan | Trang thai
        table.setColumnWidths(45, 100, 140, 120, 130, 90, 120, 110, 100);
        table.setColumnMinWidths(35, 90, 110, 100, 110, 70, 100, 90, 90);
        table.setBadgeColumn(8, this::statusLabel, this::statusColor);

        initialLoad();
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
        return new String[]{"STT", "Mã hóa đơn", "Khách hàng", "Người tạo", "Ngày tạo",
                "Số mặt hàng", "Tổng tiền", "PT thanh toán", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Invoice item) {
        return new Object[]{
                "",
                item.getInvoiceCode(),
                item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ",
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                paymentMethodLabel(item.getPaymentMethod()),
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{5, 6}; }

    @Override
    protected String getEntityLabel() { return "hóa đơn"; }

    @Override
    protected String getItemDisplayName(Invoice item) {
        return item.getInvoiceCode() + " - " + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Invoice> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
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
            case "MOMO": return "MoMo";
            case "CARD": return "Thẻ";
            default: return method;
        }
    }
}