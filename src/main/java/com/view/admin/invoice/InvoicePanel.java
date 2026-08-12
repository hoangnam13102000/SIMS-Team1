package com.view.admin.invoice;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.InvoiceDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
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

public class InvoicePanel extends BaseCrudPanel<Invoice> {

    /** Chỉ ngày, không giờ. */
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceDAO invoiceDAO = new InvoiceDAO();

    /** Loc theo khoang ngay tao hoa don. allowEmpty = true: mac dinh KHONG loc (hien tat ca). */
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    public InvoicePanel() {
        super();

        // Không STT / Số mặt hàng. Ngày tạo chỉ dd/MM/yyyy.
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);

        // Cột "Mã hóa đơn" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã hóa đơn: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã hóa đơn
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã hóa đơn
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(InvoicePanel.this, "Copy thành công", "Đã copy mã hóa đơn: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();
        initialLoad();
        applyColumnWidths();

        // Them icon "Xuat PDF" canh icon Xem, de xuat hoa don ngay tren bang
        // ma khong can mo dialog chi tiet.
        table.setActionColumn(new ActionColumn()
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        modelRow -> { if (supportsView()) viewRow(modelRow); })
                .add("export", FontAwesomeSolid.FILE_PDF, AppColor.ACCENT, "Xuất hóa đơn PDF",
                        this::exportRowPdf));
    }

    // ---------------------------------------------------------------
    // Bo loc: khoang ngay tao hoa don (hien canh o tim kiem tren toolbar)
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
        return invoiceDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<Invoice> searchPage(String keyword, int page, int pageSize) {
        return invoiceDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<Invoice> fetchAllForExport() {
        return invoiceDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã hóa đơn, khách hàng, người tạo..."; }

    /** Gợi ý autocomplete: mã hóa đơn, tên khách hàng, người tạo. */
    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Invoice inv : invoiceDAO.getAll()) {
            if (inv.getInvoiceCode() != null && !inv.getInvoiceCode().isBlank()) {
                names.add(inv.getInvoiceCode());
            }
            if (inv.getCustomerName() != null && !inv.getCustomerName().isBlank()) {
                names.add(inv.getCustomerName());
            }
            if (inv.getCreatedByName() != null && !inv.getCreatedByName().isBlank()) {
                names.add(inv.getCreatedByName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names)); // loại trùng, giữ thứ tự
    }

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

    // ---------------------------------------------------------------
    // Xuat hoa don PDF truc tiep tu icon tren bang (khong can mo dialog)
    // ---------------------------------------------------------------

    private void exportRowPdf(int modelRow) {
        Invoice item = rowToItem(modelRow);
        if (item == null) return;
        try {
            List<InvoiceDetail> details = invoiceDAO.getDetails(item.getInvoiceId());

            String fileName = "HoaDon_" + item.getInvoiceCode()
                    .replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "sims_invoices");
            if (!tempDir.exists()) tempDir.mkdirs();
            File pdfFile = new File(tempDir, fileName);

            InvoicePdfExporter.exportInvoice(item, details, pdfFile);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(),
                        "Xuất PDF", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi tạo file PDF: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ---------------------------------------------------------------
    // Helper: copy mã hóa đơn vào clipboard
    // ---------------------------------------------------------------

    /** Copy chuỗi vào clipboard hệ thống. */
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