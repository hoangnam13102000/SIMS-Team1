package com.view.admin.order;

import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
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
import javax.swing.JTable;
import javax.swing.SwingUtilities;

/**
 * Trang "Quản lý đơn hàng online" - danh sách các đơn khách tự đặt ở
 * ClientMainFrame (giỏ hàng -> thanh toán), lấy từ bảng Orders +
 * OrderDetails (xem sql/SIMS.sql). Tách riêng khỏi {@link com.view.admin.invoice.InvoicePanel}
 * vì Orders không gắn ca làm việc/nhân viên lập như Invoices - xem javadoc
 * {@link Order}.
 * <p>
 * Chỉ TRA CỨU + XÁC NHẬN/HỦY (thực hiện trong {@link OrderDetailDialog}),
 * không tạo/sửa/xóa tại đây: đơn hàng chỉ được tạo từ luồng đặt hàng online
 * của khách, và không cho sửa/xóa vật lý để giữ đúng lịch sử đơn hàng.
 */
public class OrderPanel extends BaseCrudPanel<Order> {

    /** Format ngắn để cột Ngày đặt không bị ellipsis khi AUTO_RESIZE. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final OrderDAO orderDAO = new OrderDAO();
    private AutoRowNumber stt;

    public OrderPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        // STT | Mã đơn | Khách hàng | SĐT | Ngày đặt | SL | Tổng tiền | PTTT | Thanh toán | Trạng thái
        // Tổng preferred ~940px + cột Thao tác -> vừa viewport admin, không cần scroll ngang.
        table.setColumnWidths(40, 85, 150, 105, 120, 50, 90, 70, 120, 110);
        table.setColumnMinWidths(36, 70, 100, 90, 105, 45, 75, 60, 105, 95);
        table.getTable().setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setBadgeColumn(8, this::statusLabel, this::paymentStatusColor);
        table.setBadgeColumn(9, this::statusLabel, this::orderStatusColor);

        initialLoad();
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
        // Header ngắn để không wrap / cắt chữ khi AUTO_RESIZE.
        return new String[]{
                "STT", "Mã đơn", "Khách hàng", "SĐT", "Ngày đặt",
                "SL", "Tổng tiền", "PTTT", "Thanh toán", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(Order item) {
        return new Object[]{
                "",
                item.getOrderCode(),
                item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ",
                item.getCustomerPhone() != null ? item.getCustomerPhone() : "-",
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                paymentMethodLabel(item.getPaymentMethod()),
                paymentStatusLabel(item.getPaymentStatus()),
                orderStatusLabel(item.getOrderStatus())
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{5, 6}; }

    @Override
    protected String getEntityLabel() { return "đơn hàng"; }

    @Override
    protected String getItemDisplayName(Order item) {
        return item.getOrderCode() + " - "
                + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Order> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
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
        if (orderStatusLabel("CONFIRMED").equals(v)) return AppColor.SUCCESS;
        if (orderStatusLabel("CANCELLED").equals(v)) return AppColor.ERROR;
        return AppColor.WARNING;
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
            case "CANCELLED": return "Đã hủy";
            default: return status;
        }
    }
}