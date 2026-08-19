package com.view.admin.order;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.InvoiceDAO;
import com.dao.OrderDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Order;
import com.model.OrderDetail;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import com.utils.pdf.InvoicePdfExporter;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
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
    private final InvoiceDAO invoiceDAO = new InvoiceDAO();

    /** Lọc theo khoảng ngày đặt hàng. allowEmpty = true: mặc định KHÔNG lọc (hiện tất cả). */
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    public OrderPanel() {
        super();

        // Không STT / SĐT / SL — Mã đơn | Khách hàng | Ngày đặt | Tổng tiền | PTTT | Thanh toán | Trạng thái
        table.setBadgeColumn(6, this::statusLabel, this::paymentStatusColor);
        table.setBadgeColumn(7, this::statusLabel, this::orderStatusColor);

        // Cột "Mã đơn" (index 0): thêm icon copy
        table.getTable().getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                String text = value != null ? value.toString() : "";
                c.setText(text);
                c.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                c.setHorizontalAlignment(SwingConstants.LEFT);
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                if (text != null && !text.isBlank()) {
                    FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 11);
                    copyIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(copyIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                    c.setToolTipText("Click để copy mã đơn hàng: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã đơn hàng
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã đơn
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(OrderPanel.this, "Copy thành công", "Đã copy mã đơn hàng: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();
        initialLoad();
        applyColumnWidths();

        // Thêm icon "Xuất PDF" cạnh icon Xem, giống trang hóa đơn tại quầy.
        // Luôn cho bấm được; nếu đơn chưa có hóa đơn liên kết thì hiện thông báo rõ ràng.
        table.setActionColumn(new ActionColumn()
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        modelRow -> { if (supportsView()) viewRow(modelRow); })
                .add("export", FontAwesomeSolid.FILE_PDF, AppColor.ACCENT, "Xuất hóa đơn PDF",
                        this::exportRowPdf));
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
        table.setColumnWidths(105, 145, 145, 125, 105, 75, 120, 115);
        table.setColumnMinWidths(95, 115, 110, 105, 90, 65, 105, 105);
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
        return isAssignedOnlyScope()
                ? "Tra cứu và xử lý các đơn hàng online được giao cho bạn"
                : "Tra cứu, gán nhân viên và xử lý các đơn hàng khách đặt trực tuyến";
    }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                "Mã đơn", "Khách hàng", "Phụ trách", "Ngày đặt",
                "Tổng tiền", "PTTT", "Thanh toán", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(Order item) {
        return new Object[]{
                item.getOrderCode(),
                item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ",
                item.getAssignedToName() != null ? item.getAssignedToName() : "Chưa gán",
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                paymentMethodLabel(item.getPaymentMethod()),
                paymentStatusLabel(item.getPaymentStatus()),
                orderStatusLabel(item.getOrderStatus())
        };
    }

    /** Tổng tiền (chỉ số 3) — sort theo số. */
    @Override
    protected int[] numericColumns() { return new int[]{4}; }

    @Override
    protected String getEntityLabel() { return "đơn hàng"; }

    @Override
    protected String getItemDisplayName(Order item) {
        return item.getOrderCode() + " - "
                + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
    }

    @Override
    protected PaginationHelper.PaginationResult<Order> fetchPage(int page, int pageSize) {
        return orderDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate(), assignedScopeUserId());
    }

    @Override
    protected PaginationHelper.PaginationResult<Order> searchPage(String keyword, int page, int pageSize) {
        return orderDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate(), assignedScopeUserId());
    }

    @Override
    protected List<Order> fetchAllForExport() {
        Integer userId = assignedScopeUserId();
        return userId != null ? orderDAO.getAssignedToUser(userId) : orderDAO.getAll();
    }

    private boolean isAssignedOnlyScope() {
        boolean broad = PermissionManager.getInstance().can(AppPermission.ORDER_VIEW)
                || PermissionManager.getInstance().can(AppPermission.ORDER_MANAGE);
        boolean assigned = PermissionManager.getInstance().can(AppPermission.ORDER_VIEW_ASSIGNED)
                || PermissionManager.getInstance().can(AppPermission.ORDER_PROCESS_ASSIGNED);
        return !broad && assigned;
    }

    private Integer assignedScopeUserId() {
        if (!isAssignedOnlyScope() || AuthService.getInstance().getCurrentUser() == null) {
            return null;
        }
        return AuthService.getInstance().getCurrentUser().getUserId();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm theo mã đơn, khách hàng, email, SĐT...";
    }

    /** Gợi ý autocomplete: mã đơn, tên khách hàng, email, SĐT. */
    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        Integer userId = assignedScopeUserId();
        List<Order> source = userId != null ? orderDAO.getAssignedToUser(userId) : orderDAO.getAll();
        for (Order o : source) {
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
        Integer scopeUserId = assignedScopeUserId();
        if (scopeUserId != null && !item.isAssignedTo(scopeUserId)) {
            AppAlert.error(this, "Không có quyền", "Bạn chỉ được xem đơn hàng được gán cho chính mình.");
            reload();
            return;
        }
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

    // ---------------------------------------------------------------
    // Xuất hóa đơn PDF trực tiếp từ icon trên bảng (không cần mở dialog).
    // Ưu tiên hóa đơn đã liên kết; nếu thiếu (dữ liệu cũ) thì tự lập lại
    // cho đơn đã hoàn thành / PayPal đã thanh toán; cuối cùng fallback
    // xuất PDF từ chính dữ liệu đơn hàng để luôn tra cứu lịch sử được.
    // ---------------------------------------------------------------

    private void exportRowPdf(int modelRow) {
        Order item = rowToItem(modelRow);
        if (item == null) return;

        try {
            Integer invoiceId = item.getInvoiceId();

            // Đơn đã hoàn thành / đã thanh toán nhưng chưa có HĐ → lập bù
            if (invoiceId == null) {
                boolean eligible = "COMPLETED".equalsIgnoreCase(item.getOrderStatus())
                        || ("PAYPAL".equalsIgnoreCase(item.getPaymentMethod())
                            && "PAID".equalsIgnoreCase(item.getPaymentStatus()));
                if (eligible) {
                    int actorId = AuthService.getInstance().getCurrentUser().getUserId();
                    invoiceId = orderDAO.ensureInvoiceForOrder(item.getOrderId(), actorId);
                    if (invoiceId != null) {
                        item.setInvoiceId(invoiceId);
                    }
                }
            }

            Invoice invoice = null;
            List<InvoiceDetail> details = null;

            if (invoiceId != null) {
                List<Invoice> found = invoiceDAO.getByCondition("inv.InvoiceID = " + invoiceId);
                if (!found.isEmpty()) {
                    invoice = found.get(0);
                    details = invoiceDAO.getDetails(invoice.getInvoiceId());
                }
            }

            // Fallback: dựng hóa đơn tạm từ đơn hàng để vẫn in được lịch sử
            if (invoice == null) {
                boolean canFallback = "COMPLETED".equalsIgnoreCase(item.getOrderStatus())
                        || "PAID".equalsIgnoreCase(item.getPaymentStatus());
                if (!canFallback) {
                    JOptionPane.showMessageDialog(this,
                            "Đơn hàng này chưa đủ điều kiện xuất hóa đơn.\n\n"
                            + "• Đơn COD: cần chuyển sang \"Hoàn thành\".\n"
                            + "• Đơn PayPal: cần trạng thái thanh toán \"Đã thanh toán\".",
                            "Chưa thể xuất PDF", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                invoice = buildInvoiceFromOrder(item);
                details = buildInvoiceDetailsFromOrder(item);
                if (details.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Đơn hàng không có sản phẩm để xuất hóa đơn.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            String code = invoice.getInvoiceCode() != null ? invoice.getInvoiceCode() : item.getOrderCode();
            String fileName = "HoaDon_" + code.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "sims_invoices");
            if (!tempDir.exists()) tempDir.mkdirs();
            File pdfFile = new File(tempDir, fileName);

            InvoicePdfExporter.exportInvoice(invoice, details, pdfFile);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(),
                        "Xuất PDF", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Throwable ex) {
            // Bat rong hon Exception: loi khoi tao class PDF (static initializer)
            // duoc JVM boc thanh Error, se khong bi "nuot" im lang nua.
            JOptionPane.showMessageDialog(this,
                    "Lỗi tạo file PDF: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Dựng object Invoice tạm từ Order để xuất PDF khi chưa có bản ghi Invoices. */
    private Invoice buildInvoiceFromOrder(Order order) {
        Invoice inv = new Invoice();
        inv.setInvoiceCode(order.getOrderCode() != null ? "HD-" + order.getOrderCode() : "HD-ONLINE");
        inv.setCustomerId(order.getCustomerId());
        inv.setCustomerName(order.getCustomerName());
        inv.setCreatedAt(order.getCompletedAt() != null ? order.getCompletedAt() : order.getCreatedAt());
        inv.setCreatedByName("Online");
        inv.setSubTotal(order.getSubTotal());
        inv.setDiscountAmount(order.getDiscountAmount());
        inv.setPromotionCode(order.getPromotionCode());
        inv.setTotalAmount(order.getTotalAmount());
        // Map PTTT đơn online sang nhãn hóa đơn
        String pm = order.getPaymentMethod();
        if ("PAYPAL".equalsIgnoreCase(pm)) {
            inv.setPaymentMethod("PAYPAL");
        } else if ("COD".equalsIgnoreCase(pm)) {
            inv.setPaymentMethod("CASH");
        } else {
            inv.setPaymentMethod(pm);
        }
        inv.setStatus("ACTIVE");
        return inv;
    }

    private List<InvoiceDetail> buildInvoiceDetailsFromOrder(Order order) {
        List<InvoiceDetail> result = new ArrayList<>();
        List<OrderDetail> lines = orderDAO.getDetailsByOrderId(order.getOrderId());
        for (OrderDetail line : lines) {
            InvoiceDetail d = new InvoiceDetail();
            d.setProductId(line.getProductId());
            d.setProductName(line.getProductName());
            d.setQuantity(line.getQuantity());
            d.setUnitPrice(line.getUnitPrice());
            if (line.getLineTotal() != null) {
                d.setLineTotal(line.getLineTotal());
            } else if (line.getUnitPrice() != null) {
                d.setLineTotal(line.getUnitPrice().multiply(
                        java.math.BigDecimal.valueOf(line.getQuantity())));
            }
            result.add(d);
        }
        return result;
    }

    // ---------------------------------------------------------------
    // Helper: copy mã đơn hàng vào clipboard
    // ---------------------------------------------------------------
    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
            // Bỏ qua nếu không copy được
        }
    }
}