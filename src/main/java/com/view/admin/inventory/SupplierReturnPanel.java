package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.dao.SupplierDAO;
import com.dao.SupplierReturnDAO;
import com.model.Supplier;
import com.model.SupplierReturn;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class SupplierReturnPanel extends BaseCrudPanel<SupplierReturn> {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private final SupplierReturnDAO returnDAO = new SupplierReturnDAO();

    // ====== FILTER CHUẨN ======
    private final SupplierDAO supplierDAO = new SupplierDAO();
    private FilterDropdown<SupplierOption> supplierFilter;
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearFiltersLink;
    // =========================

    public SupplierReturnPanel() {
        super();
        table.setColumnWidths(110, 160, 110, 80, 120, 130, 130, 100);
        table.setColumnMinWidths(90, 120, 90, 60, 100, 100, 110, 80);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);
        // Width ở trên chỉ là giá trị khởi tạo trước khi có data; sau mỗi lần load,
        // autoFitColumnsToContent() (gọi trong afterRender) sẽ tính lại theo nội dung thật.

        // ====== THANH CUỘN NGANG ======
        // Tắt auto-resize để bảng giữ nguyên độ rộng cột đã set, cho phép cuộn ngang
        // khi tổng độ rộng cột vượt quá khung nhìn (thay vì Swing tự ép co cột lại).
        table.getTable().setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        if (table.getTable().getParent() instanceof JViewport) {
            JViewport viewport = (JViewport) table.getTable().getParent();
            if (viewport.getParent() instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) viewport.getParent();
                scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            }
        }
        // ===============================

        // ====== CĂN GIỮA CÁC CỘT (trừ cột 0 - có icon copy, và cột 7 - badge trạng thái) ======
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                return c;
            }
        };
        for (int col = 1; col <= 6; col++) {
            table.getTable().getColumnModel().getColumn(col).setCellRenderer(centerRenderer);
        }
        // ==========================================================================================

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
                    c.setToolTipText("Click để copy mã phiếu trả NCC: " + text);
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
                        AppAlert.success(SupplierReturnPanel.this, "Copy thành công", "Đã copy mã phiếu trả NCC: " + text);
                    }
                }
            }
        });

        buildFilterBar();
        initialLoad();
    }

    // ================================================================
    // ====== FILTER CHUẨN: GIỐNG ProductPanel ======
    // ================================================================
    private static final class SupplierOption {
        final Integer supplierId;
        final String label;
        SupplierOption(Integer supplierId, String label) {
            this.supplierId = supplierId;
            this.label = label;
        }
        @Override
        public String toString() { return label; }
    }

    private void buildFilterBar() {
        // 1) FilterDropdown NCC (icon TRUCK = xe tải / nhà cung cấp)
        List<Supplier> suppliers = supplierDAO.findAllOrderByName();
        SupplierOption[] supplierOptions = new SupplierOption[suppliers.size() + 1];
        supplierOptions[0] = new SupplierOption(null, "Tất cả NCC");
        for (int i = 0; i < suppliers.size(); i++) {
            Supplier s = suppliers.get(i);
            supplierOptions[i + 1] = new SupplierOption(s.getSupplierId(), s.getSupplierName());
        }
        supplierFilter = new FilterDropdown<>(FontAwesomeSolid.TRUCK, supplierOptions);
        supplierFilter.onChange(opt -> onFilterChanged());
        addToolbarFilter(supplierFilter);

        // 2) Từ ngày + Đến ngày
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);
        JLabel fromLabel = new JLabel("Từ");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel toLabel = new JLabel("Đến");
        toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLabel.setForeground(AppColor.TEXT_MUTED);
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(fromLabel);
        dateRow.add(fromDateFilter);
        dateRow.add(toLabel);
        dateRow.add(toDateFilter);
        fromDateFilter.onChange(d -> onFilterChanged());
        toDateFilter.onChange(d -> onFilterChanged());
        addToolbarFilter(dateRow);

        // 3) Nút "Xóa lọc" CHUẨN
        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearFiltersLink = new JLabel("", clearIcon, SwingConstants.LEFT);
        clearFiltersLink.setIconTextGap(6);
        clearFiltersLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clearFiltersLink.setForeground(AppColor.TEXT_MUTED);
        clearFiltersLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearFiltersLink.setVisible(false);
        clearFiltersLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                supplierFilter.resetToAll();
                fromDateFilter.setValue(null);
                toDateFilter.setValue(null);
                onFilterChanged();
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                clearFiltersLink.setForeground(AppColor.ERROR);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                clearFiltersLink.setForeground(AppColor.TEXT_MUTED);
            }
        });
        addToolbarFilter(clearFiltersLink);
    }

    private void onFilterChanged() {
        LocalDate from = fromDateFilter.getValue();
        LocalDate to = toDateFilter.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "\"Từ ngày\" (" + from + ") không được sau \"Đến ngày\" (" + to + ").");
            return;
        }
        boolean anyActive = supplierFilter.isFilterActive()
                || fromDateFilter.getValue() != null
                || toDateFilter.getValue() != null;
        if (clearFiltersLink != null) clearFiltersLink.setVisible(anyActive);
        applyFilters();
    }

    private Integer selectedSupplierId() {
        SupplierOption opt = supplierFilter == null ? null : supplierFilter.getSelected();
        return opt == null ? null : opt.supplierId;
    }
    private LocalDate selectedFromDate() {
        return fromDateFilter == null ? null : fromDateFilter.getValue();
    }
    private LocalDate selectedToDate() {
        return toDateFilter == null ? null : toDateFilter.getValue();
    }
    // ================================================================
    // ====================== HẾT FILTER CHUẨN =======================
    // ================================================================

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.UNDO; }

    @Override
    protected String getPageTitle() { return "Trả hàng nhà cung cấp"; }

    @Override
    protected String getPageSubtitle() {
        return "Lập phiếu trả hàng lô lỗi/hỏng về NCC, tự động trừ kho và ghi nhận công nợ hoàn tiền";
    }

    @Override
    protected String getAddButtonLabel() {
        return AuthService.getInstance().can(AppPermission.SUPPLIER_RETURN_CREATE) ? "Lập phiếu trả NCC" : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã phiếu", "Nhà cung cấp", "Lý do", "Số dòng", "Hoàn tiền", "Người lập", "Ngày lập", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(SupplierReturn item) {
        return new Object[]{
                item.getSupplierReturnCode(),
                item.getSupplierName(),
                item.getReasonLabel(),
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalRefundAmount() != null ? item.getTotalRefundAmount().longValue() : 0),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{3, 4}; }

    @Override
    protected String getEntityLabel() { return "phiếu trả NCC"; }

    @Override
    protected String getItemDisplayName(SupplierReturn item) {
        return item.getSupplierReturnCode() + " - " + item.getSupplierName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<SupplierReturn> result) {
        table.getTable().repaint();
        SwingUtilities.invokeLater(this::autoFitColumnsToContent);
    }

    // ====== TỰ GIÃN CỘT THEO NỘI DUNG ======
    // Đo độ rộng thực tế của header + từng ô dữ liệu (dùng renderer đang gán cho cột đó,
    // kể cả renderer căn giữa/badge/icon copy) rồi set preferredWidth = max, có padding.
    // Kết hợp với AUTO_RESIZE_OFF: cột nào nội dung dài sẽ giãn ra, tổng bảng vượt khung
    // thì thanh cuộn ngang tự xuất hiện.
    private void autoFitColumnsToContent() {
        JTable jTable = table.getTable();
        int columnCount = jTable.getColumnCount();
        int rowCount = jTable.getRowCount();
        int[] minWidths = {90, 120, 90, 60, 100, 100, 110, 80};

        for (int col = 0; col < columnCount; col++) {
            javax.swing.table.TableColumn column = jTable.getColumnModel().getColumn(col);
            int maxWidth = col < minWidths.length ? minWidths[col] : 80;

            // Đo header
            TableCellRenderer headerRenderer = jTable.getTableHeader() != null
                    ? jTable.getTableHeader().getDefaultRenderer() : null;
            if (headerRenderer != null) {
                Component headerComp = headerRenderer.getTableCellRendererComponent(
                        jTable, column.getHeaderValue(), false, false, -1, col);
                maxWidth = Math.max(maxWidth, headerComp.getPreferredSize().width + 20);
            }

            // Đo từng ô dữ liệu đang hiển thị (trang hiện tại)
            for (int row = 0; row < rowCount; row++) {
                TableCellRenderer cellRenderer = jTable.getCellRenderer(row, col);
                Component cellComp = jTable.prepareRenderer(cellRenderer, row, col);
                maxWidth = Math.max(maxWidth, cellComp.getPreferredSize().width + 24);
            }

            column.setPreferredWidth(maxWidth);
        }
        jTable.revalidate();
        jTable.repaint();
    }
    // ==========================================

    // ====== 3 OVERRIDE ======
    @Override
    protected PaginationHelper.PaginationResult<SupplierReturn> fetchPage(int page, int pageSize) {
        return returnDAO.getPagedFiltered(page, pageSize,
                selectedSupplierId(), selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<SupplierReturn> searchPage(String keyword, int page, int pageSize) {
        return returnDAO.searchFiltered(keyword, page, pageSize,
                selectedSupplierId(), selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<SupplierReturn> fetchAllForExport() {
        return returnDAO.getAllFiltered(
                selectedSupplierId(), selectedFromDate(), selectedToDate());
    }
    // ========================

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm mã phiếu, NCC, tên SP, mã SP, mã lô, lý do, người lập...";
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (SupplierReturn r : returnDAO.getAll()) {
            if (r.getSupplierReturnCode() != null) names.add(r.getSupplierReturnCode());
            if (r.getSupplierName() != null) names.add(r.getSupplierName());
            if (r.getReasonLabel() != null) names.add(r.getReasonLabel());
            if (r.getCreatedByName() != null) names.add(r.getCreatedByName());
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
        SupplierReturn item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SupplierReturnDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, returnDAO).setVisible(true);
    }

    @Override
    protected void openForm(SupplierReturn item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        SupplierReturnFormDialog dialog = new SupplierReturnFormDialog(
                owner instanceof Frame ? (Frame) owner : null);
        dialog.onSaved((id, n) -> onDataChanged());
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(SupplierReturn item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private String statusLabel(SupplierReturn r) {
        return r.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) { }
    }
}