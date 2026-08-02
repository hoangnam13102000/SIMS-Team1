package com.view.admin.returnexchange;

import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
import com.dao.ReturnExchangeDAO;
import com.i18n.Lang;
import com.model.ReturnExchange;
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
 * Trang "Đổi / trả hàng" - danh sách các yêu cầu đổi/trả được NV bán hàng
 * tạo cho từng hóa đơn (xem {@link com.view.admin.invoice.InvoiceDetailDialog}),
 * cùng nơi Quản lý bán hàng duyệt/từ chối các yêu cầu giá trị lớn (R4, xem
 * {@link ReturnExchangeDAO#APPROVAL_THRESHOLD}).
 * <p>
 * Chỉ TRA CỨU + XEM CHI TIẾT/DUYỆT (thực hiện trong {@link ReturnExchangeDetailDialog}),
 * không tạo/sửa/xóa tại đây - việc cộng/trừ kho và điều chỉnh hóa đơn gốc do
 * trigger trg_ReturnExchange_ApprovedStock đảm nhiệm khi 1 yêu cầu chuyển
 * sang APPROVED.
 */
public class ReturnExchangePanel extends BaseCrudPanel<ReturnExchange> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final ReturnExchangeDAO returnExchangeDAO = new ReturnExchangeDAO();
    private AutoRowNumber stt;

    public ReturnExchangePanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // STT | Mã HĐ | Loại | Lý do | Giá trị | Cần duyệt | Người tạo | Ngày tạo | Trạng thái
        table.setColumnWidths(40, 90, 90, 220, 110, 90, 120, 110, 110);
        table.setColumnMinWidths(36, 75, 75, 150, 90, 75, 100, 95, 95);
        table.setBadgeColumn(2, this::typeLabel, this::typeColor);
        table.setBadgeColumn(5, this::approvalLabel, this::approvalColor);
        table.setBadgeColumn(8, this::statusLabel, this::statusColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCHANGE_ALT; }

    @Override
    protected String getPageTitle() { return Lang.get("returnExchange.title"); }

    @Override
    protected String getPageSubtitle() {
        return Lang.get("returnExchange.subtitle");
    }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                Lang.get("returnExchange.col.stt"), Lang.get("returnExchange.col.invoiceCode"),
                Lang.get("returnExchange.col.type"), Lang.get("returnExchange.col.reason"),
                Lang.get("returnExchange.col.value"), Lang.get("returnExchange.col.requiresApproval"),
                Lang.get("returnExchange.col.createdBy"), Lang.get("returnExchange.col.createdAt"),
                Lang.get("returnExchange.col.status")
        };
    }

    @Override
    protected Object[] mapRowToColumns(ReturnExchange item) {
        return new Object[]{
                "",
                item.getInvoiceCode(),
                item.getType(),
                item.getReason(),
                NumberUtil.formatThousands(item.getTotalValue() != null ? item.getTotalValue().longValue() : 0),
                item.isRequiresApproval() ? Lang.get("returnExchange.bool.yes") : Lang.get("returnExchange.bool.no"),
                item.getCreatedByName() != null ? item.getCreatedByName() : "-",
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{4}; }

    @Override
    protected String getEntityLabel() { return Lang.get("returnExchange.entityLabel"); }

    @Override
    protected String getItemDisplayName(ReturnExchange item) { return item.getInvoiceCode(); }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<ReturnExchange> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> fetchPage(int page, int pageSize) {
        return returnExchangeDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> searchPage(String keyword, int page, int pageSize) {
        return returnExchangeDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<ReturnExchange> fetchAllForExport() {
        return returnExchangeDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return Lang.get("returnExchange.searchPlaceholder"); }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        ReturnExchange item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ReturnExchangeDetailDialog dialog = new ReturnExchangeDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, returnExchangeDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void openForm(ReturnExchange item) {
        // Khong bao gio duoc goi: getAddButtonLabel() = null va supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(ReturnExchange item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private String typeLabel(Object value) {
        return ReturnExchange.TYPE_EXCHANGE.equalsIgnoreCase(String.valueOf(value))
                ? Lang.get("returnExchange.type.exchange") : Lang.get("returnExchange.type.return");
    }

    private Color typeColor(Object value) {
        return ReturnExchange.TYPE_EXCHANGE.equalsIgnoreCase(String.valueOf(value)) ? AppColor.ACCENT : AppColor.INFO;
    }

    private String approvalLabel(Object value) {
        return String.valueOf(value);
    }

    private Color approvalColor(Object value) {
        return Lang.get("returnExchange.bool.yes").equals(String.valueOf(value)) ? AppColor.WARNING : AppColor.TEXT_MUTED;
    }

    private String statusLabel(Object value) {
        String v = String.valueOf(value);
        switch (v) {
            case "PENDING": return Lang.get("returnExchange.status.pending");
            case "APPROVED": return Lang.get("returnExchange.status.approved");
            case "REJECTED": return Lang.get("returnExchange.status.rejected");
            default: return v;
        }
    }

    private Color statusColor(Object value) {
        String v = String.valueOf(value);
        if ("APPROVED".equals(v)) return AppColor.SUCCESS;
        if ("REJECTED".equals(v)) return AppColor.ERROR;
        return AppColor.WARNING; // PENDING
    }
}