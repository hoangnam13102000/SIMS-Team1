package com.view.admin.returnexchange;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.dao.ReturnExchangeDAO;
import com.i18n.Lang;
import com.model.ReturnExchange;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ReturnExchangePanel extends BaseCrudPanel<ReturnExchange> {
    private final ReturnExchangeDAO returnExchangeDAO = new ReturnExchangeDAO();

    // ====== Bộ lọc theo thời gian ======
    private DatePickerField fromDatePicker;
    private DatePickerField toDatePicker;
    private JButton clearFilterButton;
    private LocalDate activeFromDate;
    private LocalDate activeToDate;
    /** Tránh re-apply lọc liên tục khi code tự set giá trị date picker. */
    private boolean applyingFilterInternally;

    public ReturnExchangePanel() {
        super();
        table.setBadgeColumn(1, this::typeLabel, this::typeColor);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Cột "Mã HĐ" (index 0): thêm icon copy
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

        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) {
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(ReturnExchangePanel.this, "Copy thành công", "Đã copy mã hóa đơn: " + text);
                    }
                }
            }
        });

        buildDateFilterUI();
        initialLoad();
        applyColumnWidths();
    }

    // ==================================================================
    // UI Bộ lọc thời gian — TỰ ĐỘNG LỌC (không có nút Lọc) + NÚT ICON X
    // ==================================================================
    private void buildDateFilterUI() {
        JLabel fromLbl = new JLabel("Từ:");
        fromLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLbl.setForeground(AppColor.TEXT_SECONDARY);
        fromLbl.setBorder(new EmptyBorder(0, 6, 0, 0));

        fromDatePicker = new DatePickerField(null, true);
        fromDatePicker.setToolTipText("Ngày tạo yêu cầu sớm nhất");

        JLabel toLbl = new JLabel("Đến:");
        toLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLbl.setForeground(AppColor.TEXT_SECONDARY);

        toDatePicker = new DatePickerField(null, true);
        toDatePicker.setToolTipText("Ngày tạo yêu cầu muộn nhất");

        // ====== TỰ ĐỘNG LỌC khi người dùng chọn/xóa ngày ======
        // (giống chuẩn các trang khác: thay đổi giá trị là apply ngay)
        fromDatePicker.onChange(d -> autoApplyFilter());
        toDatePicker.onChange(d -> autoApplyFilter());

        // ====== NÚT BỎ LỌC: chỉ có ICON X (không có chữ) ======
        clearFilterButton = new JButton();
        FontIcon xIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        xIcon.setIconColor(AppColor.TEXT_SECONDARY);
        clearFilterButton.setIcon(xIcon);
        clearFilterButton.setToolTipText("Bỏ lọc theo thời gian");
        clearFilterButton.setFocusPainted(false);
        clearFilterButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearFilterButton.setContentAreaFilled(false);
        clearFilterButton.setBorderPainted(false);
        clearFilterButton.setOpaque(false);
        clearFilterButton.setPreferredSize(new Dimension(28, 28));
        // Hiệu ứng hover: đổi màu icon
        clearFilterButton.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                xIcon.setIconColor(AppColor.ERROR);
                clearFilterButton.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                xIcon.setIconColor(AppColor.TEXT_SECONDARY);
                clearFilterButton.repaint();
            }
        });
        clearFilterButton.addActionListener(e -> clearDateFilter());
        // Mặc định ẩn nút X — chỉ hiện khi có lọc đang áp dụng
        clearFilterButton.setVisible(false);

        // Thứ tự trên toolbar: search → Từ: [date] → Đến: [date] → [X]
        addToolbarFilter(fromLbl);
        addToolbarFilter(fromDatePicker);
        addToolbarFilter(toLbl);
        addToolbarFilter(toDatePicker);
        addToolbarFilter(clearFilterButton);
    }

    /**
     * Tự động áp dụng lọc: validate → cập nhật activeFrom/To → gọi applyFilters().
     * Được gọi mỗi khi DatePicker thay đổi giá trị (onChange).
     */
    private void autoApplyFilter() {
        if (applyingFilterInternally) return; // tránh vòng lặp khi clear tự set null

        LocalDate from = fromDatePicker.getValue();
        LocalDate to = toDatePicker.getValue();

        // Validate: Từ không được sau Đến
        if (from != null && to != null && from.isAfter(to)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "\"Từ ngày\" (" + from + ") không được sau \"Đến ngày\" (" + to + ").");
            return;
        }

        activeFromDate = from;
        activeToDate = to;

        // Hiện/ẩn nút X tùy thuộc: có lọc nào đang bật thì hiện X
        boolean hasActiveFilter = (activeFromDate != null || activeToDate != null);
        clearFilterButton.setVisible(hasActiveFilter);

        // Load lại dữ liệu với bộ lọc mới (kết hợp cả keyword search nếu có)
        applyFilters();
    }

    /** Bỏ lọc: xóa trắng 2 ô ngày, ẩn nút X, load lại toàn bộ. */
    private void clearDateFilter() {
        applyingFilterInternally = true;
        try {
            fromDatePicker.setValue(null);
            toDatePicker.setValue(null);
            activeFromDate = null;
            activeToDate = null;
            clearFilterButton.setVisible(false);
        } finally {
            applyingFilterInternally = false;
        }
        applyFilters();
    }

    // ==================================================================
    // Override các hook lấy dữ liệu
    // ==================================================================
    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> fetchPage(int page, int pageSize) {
        return returnExchangeDAO.getPagedFiltered(page, pageSize, activeFromDate, activeToDate);
    }

    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> searchPage(String keyword, int page, int pageSize) {
        return returnExchangeDAO.searchFiltered(keyword, page, pageSize, activeFromDate, activeToDate);
    }

    @Override
    protected List<ReturnExchange> fetchAllForExport() {
        return returnExchangeDAO.getAllFiltered(activeFromDate, activeToDate);
    }

    // ==================================================================
    // Các phương thức còn lại — giữ nguyên
    // ==================================================================
    private void applyColumnWidths() {
        table.setColumnWidths(190, 95, 200, 100, 130, 115);
        table.setColumnMinWidths(185, 75, 80, 80, 80, 95);
        if (table.getTable().getColumnModel().getColumnCount() > 0) {
            var col = table.getTable().getColumnModel().getColumn(0);
            col.setMinWidth(185);
            col.setPreferredWidth(190);
        }
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCHANGE_ALT; }

    @Override
    protected String getPageTitle() { return Lang.get("returnExchange.title"); }

    @Override
    protected String getPageSubtitle() { return Lang.get("returnExchange.subtitle"); }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                Lang.get("returnExchange.col.invoiceCode"),
                Lang.get("returnExchange.col.type"), Lang.get("returnExchange.col.reason"),
                Lang.get("returnExchange.col.value"),
                Lang.get("returnExchange.col.createdBy"),
                Lang.get("returnExchange.col.status")
        };
    }

    @Override
    protected Object[] mapRowToColumns(ReturnExchange item) {
        return new Object[]{
                item.getInvoiceCode(),
                item.getType(),
                item.getReason(),
                NumberUtil.formatThousands(item.getTotalValue() != null ? item.getTotalValue().longValue() : 0),
                item.getCreatedByName() != null ? item.getCreatedByName() : "-",
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{3}; }

    @Override
    protected String getEntityLabel() { return Lang.get("returnExchange.entityLabel"); }

    @Override
    protected String getItemDisplayName(ReturnExchange item) { return item.getInvoiceCode(); }

    @Override
    protected String getSearchPlaceholder() { return Lang.get("returnExchange.searchPlaceholder"); }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (ReturnExchange re : returnExchangeDAO.getAll()) {
            if (re.getInvoiceCode() != null && !re.getInvoiceCode().isBlank()) names.add(re.getInvoiceCode());
            if (re.getCreatedByName() != null && !re.getCreatedByName().isBlank()) names.add(re.getCreatedByName());
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
        ReturnExchange item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ReturnExchangeDetailDialog dialog = new ReturnExchangeDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, returnExchangeDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void openForm(ReturnExchange item) { }

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
        return AppColor.WARNING;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) { }
    }
}