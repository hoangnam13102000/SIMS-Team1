package com.view.admin.order;

import com.components.crud.BaseCrudPanel;
import com.dao.OrderDAO;
import com.model.Order;
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
 * Trang "Quản lý đơn hàng online" - danh sách các đơn khách tự đặt ở
 * ClientMainFrame (giỏ hàng -> thanh toán), lấy từ bảng Orders +
 * OrderDetails (xem sql/SIMS.sql). Tách riêng khỏi {@link com.view.admin.invoice.InvoicePanel}
 * vì Orders không gắn ca làm việc/nhân viên lập như Invoices - xem javadoc
 * {@link Order}.
 * <p>
 * Chỉ TRA CỨU + XÁC NHẬN/GIAO HÀNG/HOÀN THÀNH/HỦY (thực hiện trong
 * {@link OrderDetailDialog}, đi qua flow NEW -> CONFIRMED -> SHIPPING ->
 * COMPLETED, hủy được ở NEW/CONFIRMED), không tạo/sửa/xóa tại đây: đơn hàng
 * chỉ được tạo từ luồng đặt hàng online của khách, và không cho sửa/xóa vật
 * lý để giữ đúng lịch sử đơn hàng.
 */
public class OrderPanel extends BaseCrudPanel<Order> {

    /** Format ngắn để cột Ngày đặt không bị ellipsis khi AUTO_RESIZE. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final OrderDAO orderDAO = new OrderDAO();

    public OrderPanel() {
        super();

        // Không STT / SĐT / SL — Mã đơn | Khách hàng | Ngày đặt | Tổng tiền | PTTT | Thanh toán | Trạng thái
        table.setBadgeColumn(5, this::statusLabel, this::paymentStatusColor);
        table.setBadgeColumn(6, this::statusLabel, this::orderStatusColor);

        initialLoad();
        applyColumnWidths();
    }

    private void applyColumnWidths() {
        // Mã đơn (DH####) min đủ hiện full; các cột khác co được. Không scroll ngang.
        table.setColumnWidths(110, 160, 130, 110, 80, 130, 125);
        table.setColumnMinWidths(100, 120, 110, 90, 70, 110, 110);
        if (table.getTable().getColumnModel().getColumnCount() > 0) {
            var col = table.getTable().getColumnModel().getColumn(0);
            col.setMinWidth(100);
            col.setPreferredWidth(110);
        }
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.SHOPPING_CART; }

    @Override
    protected String getPageTitle() { return "Quản lý đơn hàng online"; }

    @Override
    protected String getPageSubtitle() {
        return "Tra cứu, xác nhận và hủy các đơn hàng khách đặt trực tuyến";
    }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                "Mã đơn", "Khách hàng", "Ngày đặt",
                "Tổng tiền", "PTTT", "Thanh toán", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(Order item) {
        return new Object[]{
                item.getOrderCode(),
                item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ",
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                paymentMethodLabel(item.getPaymentMethod()),
                paymentStatusLabel(item.getPaymentStatus()),
                orderStatusLabel(item.getOrderStatus())
        };
    }

    /** Tổng tiền (chỉ số 3) — sort theo số. */
    @Override
    protected int[] numericColumns() { return new int[]{3}; }

    @Override
    protected String getEntityLabel() { return "đơn hàng"; }

    @Override
    protected String getItemDisplayName(Order item) {
        return item.getOrderCode() + " - "
                + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
    }

    @Override
    protected PaginationHelper.PaginationResult<Order> fetchPage(int page, int pageSize) {
        return orderDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Order> searchPage(String keyword, int page, int pageSize) {
        return orderDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<Order> fetchAllForExport() {
        return orderDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm theo mã đơn, khách hàng, email, SĐT...";
    }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        Order item = rowToItem(modelRow);
        if (item == null) return;
        openDetailDialog(item);
    }

    @Override
    protected void openForm(Order item) {
        // Không bao giờ được gọi: getAddButtonLabel() = null và supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(Order item) { return false; }

    private void openDetailDialog(Order item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        OrderDetailDialog dialog = new OrderDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, orderDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color paymentStatusColor(Object value) {
        String v = String.valueOf(value);
        if (paymentStatusLabel("PAID").equals(v)) return AppColor.SUCCESS;
        if (paymentStatusLabel("FAILED").equals(v)) return AppColor.ERROR;
        return AppColor.WARNING;
    }

    private Color orderStatusColor(Object value) {
        String v = String.valueOf(value);
        if (orderStatusLabel("COMPLETED").equals(v)) return AppColor.SUCCESS;
        if (orderStatusLabel("CONFIRMED").equals(v)) return AppColor.INFO;
        if (orderStatusLabel("SHIPPING").equals(v)) return AppColor.ACCENT;
        if (orderStatusLabel("CANCELLED").equals(v)) return AppColor.ERROR;
        return AppColor.WARNING; // NEW - Cho xac nhan
    }

    static String paymentMethodLabel(String method) {
        if (method == null) return "-";
        switch (method) {
            case "COD": return "COD";
            case "PAYPAL": return "PayPal";
            default: return method;
        }
    }

    static String paymentStatusLabel(String status) {
        if (status == null) return "-";
        switch (status) {
            case "PENDING": return "Chờ TT";
            case "PAID": return "Đã thanh toán";
            case "FAILED": return "Thất bại";
            default: return status;
        }
    }

    static String orderStatusLabel(String status) {
        if (status == null) return "-";
        switch (status) {
            case "NEW": return "Chờ xác nhận";
            case "CONFIRMED": return "Đã xác nhận";
            case "SHIPPING": return "Đang giao";
            case "COMPLETED": return "Hoàn thành";
            case "CANCELLED": return "Đã hủy";
            default: return status;
        }
    }
}