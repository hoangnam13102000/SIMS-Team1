package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.dao.PurchaseReceiptDAO;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.model.PurchaseReceipt;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

public class PurchaseReceiptPanel extends BaseCrudPanel<PurchaseReceipt> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PurchaseReceiptDAO receiptDAO = new PurchaseReceiptDAO();

    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;
    private boolean adjustingDateFilter;

    public PurchaseReceiptPanel() {
        super();

        // Nha cung cap/Nguoi tao can nhieu cho hon de khong bi cat "...".
        // Cot "So mat hang" truoc do qua hep khien header wrap 3 dong va bi
        // cat mat chu cuoi - noi rong ra de header nam gon 1 dong.
        table.setColumnWidths(140, 270, 190, 150, 110, 130, 110);
        table.setColumnMinWidths(110, 200, 150, 130, 90, 110, 90);
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);

        // Cột "Mã phiếu" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã phiếu nhập: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã phiếu nhập
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã phiếu
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(PurchaseReceiptPanel.this, "Copy thành công", "Đã copy mã phiếu nhập: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();
        initialLoad();
    }

    private void buildDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);

        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);

        JLabel toLabel = new JLabel("đến");
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

        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 14);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearDateFilterLink = new JLabel(clearIcon);
        clearDateFilterLink.setToolTipText("Xóa lọc ngày");
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
                clearIcon.setIconColor(AppColor.ERROR);
                clearDateFilterLink.repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearIcon.setIconColor(AppColor.TEXT_MUTED);
                clearDateFilterLink.repaint();
            }
        });
        addToolbarFilter(clearDateFilterLink);
    }

    private void onDateFilterChanged() {
        if (adjustingDateFilter) return;

        LocalDate from = selectedFromDate();
        LocalDate to = selectedToDate();
        if (from != null && to != null && to.isBefore(from)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
            adjustingDateFilter = true;
            try {
                toDateFilter.setValue(null);
            } finally {
                adjustingDateFilter = false;
            }
        }

        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(
                    fromDateFilter.getValue() != null || toDateFilter.getValue() != null);
        }
        applyFilters();
    }

    private LocalDate selectedFromDate() {
        return fromDateFilter == null ? null : fromDateFilter.getValue();
    }

    private LocalDate selectedToDate() {
        return toDateFilter == null ? null : toDateFilter.getValue();
    }


    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.FILE_INVOICE; }
    @Override
    protected String getPageTitle() { return "Quản lý nhập kho"; }
    @Override
    protected String getPageSubtitle() { return "Lập phiếu nhập nhiều sản phẩm và tra cứu lịch sử theo nhà cung cấp"; }
    @Override
    protected String getAddButtonLabel() {
        return AuthService.getInstance().can(AppPermission.STOCK_IMPORT) ? "Lập phiếu nhập" : null;
    }

    @Override
    protected String[] getColumnNames() {
        // "Số SP" (ngan hon "Số mặt hàng") de header luon nam gon 1 dong du
        // cot khong qua rong, tranh bi wrap 2-3 dong va cat mat chu.
        return new String[]{"Mã phiếu", "Nhà cung cấp", "Người tạo", "Ngày tạo",
                "Số SP", "Tổng tiền", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(PurchaseReceipt item) {
        return new Object[]{
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
    protected int[] numericColumns() { return new int[]{4, 5}; }

    @Override
    protected String getEntityLabel() { return "phiếu nhập kho"; }

    @Override
    protected String getItemDisplayName(PurchaseReceipt item) {
        return item.getReceiptCode() + " - " + item.getSupplierName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<PurchaseReceipt> result) {
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> fetchPage(int page, int pageSize) {
        return receiptDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> searchPage(String keyword, int page, int pageSize) {
        return receiptDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<PurchaseReceipt> fetchAllForExport() {
        return receiptDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm mã phiếu, mã sản phẩm, tên sản phẩm, nhà cung cấp..."; }

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
            // Gợi ý tìm kiếm theo sản phẩm trong phiếu: mã SP + tên SP.
            if (r.getReceiptId() > 0) {
                for (com.model.PurchaseReceiptDetail d : receiptDAO.getDetails(r.getReceiptId())) {
                    if (d.getProductCode() != null && !d.getProductCode().isBlank()) {
                        names.add(d.getProductCode());
                    }
                    if (d.getProductName() != null && !d.getProductName().isBlank()) {
                        names.add(d.getProductName());
                    }
                }
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

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
        Window owner = SwingUtilities.getWindowAncestor(this);
        PurchaseReceiptFormDialog dialog = new PurchaseReceiptFormDialog(
                owner instanceof Frame ? (Frame) owner : null);
        dialog.onSaved((receiptId, lineCount) -> onDataChanged());
        dialog.setVisible(true);
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

    private String statusLabel(PurchaseReceipt r) {
        return r.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Helper: copy mã phiếu nhập vào clipboard
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