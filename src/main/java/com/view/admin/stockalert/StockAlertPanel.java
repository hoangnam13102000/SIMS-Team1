package com.view.admin.stockalert;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.CategoryDAO;
import com.dao.StockAlertDAO;
import com.model.Category;
import com.model.StockAlert;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StockAlertPanel extends BaseCrudPanel<StockAlert> {
    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();

    // ====== FILTER CHUẨN: FilterDropdown + DatePickerField ======
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private FilterDropdown<CategoryOption> categoryFilter;
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearFiltersLink;
    // ============================================================

    public StockAlertPanel() {
        super();
        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("plan", FontAwesomeSolid.CALENDAR_PLUS, AppColor.ACCENT, "Đã lên kế hoạch nhập bổ sung",
                        this::planRow, this::canPlan)
                .add("resolve", FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS, "Đánh dấu đã xử lý xong",
                        this::resolveRow, this::canResolve));

        table.setBadgeColumn(2, this::alertTypeLabel, this::alertTypeColor);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Cột 0 (Mã SP): căn giữa + icon copy
        table.getTable().getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                String text = value != null ? value.toString() : "";
                c.setText(text);
                c.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                if (text != null && !text.isBlank()) {
                    FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 11);
                    copyIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(copyIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.RIGHT);
                    c.setToolTipText("Click để copy mã sản phẩm: " + text);
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
                        AppAlert.success(StockAlertPanel.this, "Copy thành công", "Đã copy mã sản phẩm: " + text);
                    }
                }
            }
        });

        // ====== DÙNG buildFilterBar CHUẨN ======
        buildFilterBar();
        initialLoad();
        applyColumnWidths();
        centerColumns(1, 3, 4); // Tên sản phẩm, Người báo cáo, Thời gian
        stockAlertDAO.markAllSeen();
    }

    // Renderer căn giữa dùng chung, vẫn giữ màu xen kẽ dòng + màu chọn
    private void centerColumns(int... columnIndexes) {
        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                return c;
            }
        };
        for (int idx : columnIndexes) {
            table.getTable().getColumnModel().getColumn(idx).setCellRenderer(centerRenderer);
        }
    }

    // ================================================================
    // ====== FILTER CHUẨN: GIỐNG HỆT ProductPanel / InventoryBatchPanel ======
    // ================================================================
    private static final class CategoryOption {
        final Integer categoryId;
        final String label;
        CategoryOption(Integer categoryId, String label) {
            this.categoryId = categoryId;
            this.label = label;
        }
        @Override
        public String toString() { return label; }
    }

    private void buildFilterBar() {
        // 1) FilterDropdown Loại SP (icon LAYER_GROUP giống ProductPanel)
        List<Category> categories = categoryDAO.findAll();
        CategoryOption[] categoryOptions = new CategoryOption[categories.size() + 1];
        categoryOptions[0] = new CategoryOption(null, "Tất cả loại SP");
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            categoryOptions[i + 1] = new CategoryOption(c.getCategoryId(), c.getCategoryName());
        }
        categoryFilter = new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, categoryOptions);
        categoryFilter.onChange(opt -> onFilterChanged());
        addToolbarFilter(categoryFilter);

        // 2) Từ ngày + Đến ngày (giống Invoice/Order/ReturnExchange)
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

        // 3) Nút "Xóa lọc" CHUẨN (chữ + icon, giống ProductPanel)
        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearFiltersLink = new JLabel("Xóa lọc", clearIcon, SwingConstants.LEFT);
        clearFiltersLink.setIconTextGap(6);
        clearFiltersLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clearFiltersLink.setForeground(AppColor.TEXT_MUTED);
        clearFiltersLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearFiltersLink.setVisible(false);
        clearFiltersLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                categoryFilter.resetToAll();
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
        // Validate Từ > Đến
        LocalDate from = fromDateFilter.getValue();
        LocalDate to = toDateFilter.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "\"Từ ngày\" (" + from + ") không được sau \"Đến ngày\" (" + to + ").");
            return;
        }
        // Nút Xóa lọc chỉ hiện khi CÓ bộ lọc nào đang bật
        boolean anyActive = categoryFilter.isFilterActive()
                || fromDateFilter.getValue() != null
                || toDateFilter.getValue() != null;
        if (clearFiltersLink != null) clearFiltersLink.setVisible(anyActive);
        applyFilters();
    }

    // Helper đọc giá trị filter (null = không lọc)
    private Integer selectedCategoryId() {
        CategoryOption opt = categoryFilter == null ? null : categoryFilter.getSelected();
        return opt == null ? null : opt.categoryId;
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

    private void applyColumnWidths() {
        table.setColumnWidths(100, 200, 120, 150, 130, 130);
        table.setColumnMinWidths(90, 150, 105, 120, 110, 110);
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCLAMATION_TRIANGLE; }

    @Override
    protected String getPageTitle() { return "Cảnh báo tồn kho"; }

    @Override
    protected String getPageSubtitle() {
        return "Các báo cáo hết/sắp hết hàng từ nhân viên bán hàng - lên kế hoạch nhập hàng bổ sung";
    }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                "Mã SP", "Tên sản phẩm", "Loại cảnh báo",
                "Người báo cáo", "Thời gian", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(StockAlert item) {
        return new Object[]{
                item.getProductCode(),
                item.getProductName(),
                item.getAlertType(),
                item.getReportedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{}; }

    @Override
    protected String getEntityLabel() { return "cảnh báo"; }

    @Override
    protected String getItemDisplayName(StockAlert item) { return item.getProductName(); }

    // ====== 3 OVERRIDE GỌI DAO FILTER ======
    @Override
    protected PaginationHelper.PaginationResult<StockAlert> fetchPage(int page, int pageSize) {
        return stockAlertDAO.getPagedFiltered(page, pageSize,
                selectedCategoryId(), selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<StockAlert> searchPage(String keyword, int page, int pageSize) {
        return stockAlertDAO.searchFiltered(keyword, page, pageSize,
                selectedCategoryId(), selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<StockAlert> fetchAllForExport() {
        return stockAlertDAO.getAllFiltered(
                selectedCategoryId(), selectedFromDate(), selectedToDate());
    }
    // ========================================

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, mã SP..."; }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return false; }

    @Override
    protected void openForm(StockAlert item) { }

    @Override
    protected boolean deleteItem(StockAlert item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private boolean canPlan(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        return item != null && "NEW".equals(item.getStatus());
    }

    private boolean canResolve(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        return item != null && !item.isResolved();
    }

    private void planRow(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this,
                "Lên kế hoạch nhập bổ sung",
                "Đánh dấu \"" + item.getProductName() + "\" đã được lên kế hoạch nhập hàng bổ sung?",
                "Xác nhận", AppColor.ACCENT, AppColor.ACCENT, FontAwesomeSolid.CALENDAR_PLUS);
        if (!confirmed) return;
        if (stockAlertDAO.markPlanned(item.getAlertId())) {
            BaseDialog.success(this, "Thành công",
                    "Đã đánh dấu \"" + item.getProductName() + "\" đang được lên kế hoạch nhập bổ sung.");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private void resolveRow(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this,
                "Đánh dấu đã xử lý",
                "Xác nhận \"" + item.getProductName() + "\" đã được nhập hàng bổ sung / không còn cần xử lý?",
                "Xác nhận", AppColor.SUCCESS, AppColor.SUCCESS, FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;
        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        if (stockAlertDAO.resolve(item.getAlertId(), currentUserId)) {
            BaseDialog.success(this, "Thành công", "Đã xử lý xong \"" + item.getProductName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private String alertTypeLabel(Object value) {
        return "OUT_OF_STOCK".equalsIgnoreCase(String.valueOf(value)) ? "Hết hàng" : "Sắp hết hàng";
    }

    private Color alertTypeColor(Object value) {
        return "OUT_OF_STOCK".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.WARNING;
    }

    private String statusLabel(Object value) {
        String v = String.valueOf(value);
        switch (v) {
            case "NEW": return "Mới";
            case "PLANNED": return "Đã lên kế hoạch";
            case "RESOLVED": return "Đã xử lý";
            default: return v;
        }
    }

    private Color statusColor(Object value) {
        String v = String.valueOf(value);
        if ("RESOLVED".equals(v)) return AppColor.SUCCESS;
        if ("PLANNED".equals(v)) return AppColor.INFO;
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