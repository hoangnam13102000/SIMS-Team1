package com.view.admin.order;

import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.dao.OrderDAO;
import com.model.Order;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;


public class OrderPanel extends BaseCrudPanel<Order> {

    /** Format ngắn để cột Ngày đặt không bị ellipsis khi AUTO_RESIZE. */
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final OrderDAO orderDAO = new OrderDAO();

    /** Lọc theo khoảng ngày đặt hàng. allowEmpty = true: mặc định KHÔNG lọc (hiện tất cả). */
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    public OrderPanel() {
        super();

        // Không STT / SĐT / SL — Mã đơn | Khách hàng | Ngày đặt | Tổng tiền | PTTT | Thanh toán | Trạng thái
        table.setBadgeColumn(5, this::statusLabel, this::paymentStatusColor);
        table.setBadgeColumn(6, this::statusLabel, this::orderStatusColor);

        buildDateFilterBar();

        initialLoad();
        applyColumnWidths();
    }

    // ---------------------------------------------------------------
    // Bộ lọc: khoảng ngày đặt hàng (hiện cạnh ô tìm kiếm trên toolbar)
    // ---------------------------------------------------------------

    private void buildDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);

        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel toLabel = new JLabel("Đến ngày");
        toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(fromLabel);
        dateRow.add(fromDateFilter);
        dateRow.add(toLabel);
        dateRow.add(toDateFilter);

        fromDateFilter.onChange(d -> onDateFilterChanged());
        toDateFilter.onChange(d -> onDateFilterChanged());
        addToolbarFilter(dateRow);

        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearDateFilterLink = new JLabel("Xóa lọc ngày", clearIcon, SwingConstants.LEFT);
        clearDateFilterLink.setIconTextGap(6);
        clearDateFilterLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
        clearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearDateFilterLink.setVisible(false);
        clearDateFilterLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                fromDateFilter.setValue(null);
                toDateFilter.setValue(null);
                onDateFilterChanged();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                clearDateFilterLink.setForeground(AppColor.ERROR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
            }
        });
        addToolbarFilter(clearDateFilterLink);
    }

    private void onDateFilterChanged() {
        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(fromDateFilter.getValue() != null || toDateFilter.getValue() != null);
        }
        applyFilters();
    }

    private LocalDate selectedFromDate() {
        return fromDateFilter == null ? null : fromDateFilter.getValue();
    }

    private LocalDate selectedToDate() {
        return toDateFilter == null ? null : toDateFilter.getValue();
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
        return orderDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<Order> searchPage(String keyword, int page, int pageSize) {
        return orderDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<Order> fetchAllForExport() {
        return orderDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm theo mã đơn, khách hàng, email, SĐT...";
    }

    /** Gợi ý autocomplete: mã đơn, tên khách hàng, email, SĐT. */
    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Order o : orderDAO.getAll()) {
            if (o.getOrderCode() != null && !o.getOrderCode().isBlank()) {
                names.add(o.getOrderCode());
            }
            if (o.getCustomerName() != null && !o.getCustomerName().isBlank()) {
                names.add(o.getCustomerName());
            }
            if (o.getCustomerEmail() != null && !o.getCustomerEmail().isBlank()) {
                names.add(o.getCustomerEmail());
            }
            if (o.getCustomerPhone() != null && !o.getCustomerPhone().isBlank()) {
                names.add(o.getCustomerPhone());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names)); // loại trùng, giữ thứ tự
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