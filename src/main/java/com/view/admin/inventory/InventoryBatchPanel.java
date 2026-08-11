package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.dao.CategoryDAO;
import com.dao.InventoryBatchDAO;
import com.model.Category;
import com.model.InventoryBatch;
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
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class InventoryBatchPanel extends BaseCrudPanel<InventoryBatch> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int NEAR_EXPIRY_DAYS = 7;

    private final InventoryBatchDAO batchDAO = new InventoryBatchDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private FilterDropdown<CategoryOption> categoryFilter;
    private JLabel clearFiltersLink;

    public InventoryBatchPanel() {
        super();

        // Giu lai cot can thiet de quet nhanh theo FEFO tren 1 man hinh, khong
        // can cuon ngang: Ma lo, So lo NCC (doi chieu bao bi), San pham, HSD
        // (uu tien FEFO), Con lai, Trang thai. Cac thong tin it dùng hang ngay
        // (NSX, Nha cung cap, SL nhap ban dau, Gia nhap) xem trong dialog chi
        // tiet (nut Xem) - da co du o InventoryBatchFormDialog VIEW mode.
        table.setColumnWidths(150, 130, 220, 130, 90, 130);
        table.setColumnMinWidths(120, 100, 160, 100, 70, 100);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Cột "Mã lô" (index 0) và "Số lô NCC" (index 1): thêm icon copy
        for (int colIdx : new int[]{0, 1}) {
            final int col = colIdx;
            final String colName = colIdx == 0 ? "mã lô" : "số lô NCC";
            table.getTable().getColumnModel().getColumn(col).setCellRenderer(new DefaultTableCellRenderer() {
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
                    if (text != null && !text.isBlank() && !"—".equals(text)) {
                        FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 11);
                        copyIcon.setIconColor(AppColor.ACCENT);
                        c.setIcon(copyIcon);
                        c.setIconTextGap(6);
                        c.setHorizontalTextPosition(SwingConstants.LEFT);
                        c.setToolTipText("Click để copy " + colName + ": " + text);
                    } else {
                        c.setIcon(null);
                        c.setToolTipText(null);
                    }
                    return c;
                }
            });
        }
        
        // Xử lý click vào icon copy cho cột Mã lô (0) và Số lô NCC (1)
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if ((viewCol == 0 || viewCol == 1) && viewRow >= 0) {
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, viewCol);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank() && !"—".equals(text)) {
                        copyToClipboard(text);
                        String colName = viewCol == 0 ? "mã lô" : "số lô NCC";
                        AppAlert.success(InventoryBatchPanel.this, "Copy thành công", "Đã copy " + colName + ": " + text);
                    }
                }
            }
        });

        buildFilterBar();
        initialLoad();
    }

    // ---------------------------------------------------------------
    // Bo loc: danh muc san pham (hien canh o tim kiem tren toolbar) -
    // giup xem nhanh "trong danh muc X, lo nao se duoc ban truoc theo FEFO".
    // ---------------------------------------------------------------

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
        List<Category> categories = categoryDAO.findAll();
        CategoryOption[] categoryOptions = new CategoryOption[categories.size() + 1];
        categoryOptions[0] = new CategoryOption(null, "Tất cả danh mục");
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            categoryOptions[i + 1] = new CategoryOption(c.getCategoryId(), c.getCategoryName());
        }

        categoryFilter = new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, categoryOptions);
        categoryFilter.onChange(opt -> onFilterChanged());
        addToolbarFilter(categoryFilter);

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
        if (clearFiltersLink != null) clearFiltersLink.setVisible(categoryFilter.isFilterActive());
        applyFilters();
    }

    private Integer selectedCategoryId() {
        CategoryOption opt = categoryFilter == null ? null : categoryFilter.getSelected();
        return opt == null ? null : opt.categoryId;
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BOXES; }
    @Override
    protected String getPageTitle() { return "Quản lý lô hàng"; }
    @Override
    protected String getPageSubtitle() {
        return "Theo dõi lô hàng theo hạn sử dụng (FEFO). Vào \"Quản lý nhập kho\" để lập phiếu nhập.";
    }
    @Override
    protected String getAddButtonLabel() {
        // Trang này chỉ để theo dõi lô hàng (read-only), không tạo lô/nhập kho tại đây.
        // Việc lập phiếu nhập thực hiện ở trang "Quản lý nhập kho" (PurchaseReceiptPanel).
        return null;
    }

    @Override
    protected String[] getColumnNames() {
        // Chi giu cot can cho quyet dinh nhanh (xuat lo nao truoc theo FEFO,
        // doi chieu so lo NCC). NSX/Nha cung cap/SL nhap/Gia nhap xem trong
        // dialog chi tiet qua nut Xem (cot Thao tac).
        return new String[]{"Mã lô", "Số lô NCC", "Sản phẩm", "HSD", "Còn lại", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(InventoryBatch item) {
        return new Object[]{
                item.getBatchCode(),
                item.getLotNumber() != null && !item.getLotNumber().isBlank() ? item.getLotNumber() : "—",
                item.getProductName(),
                formatDate(item.getExpiryDate()),
                item.getRemainingQty(),
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{4}; }

    @Override
    protected String getEntityLabel() { return "lô hàng"; }

    @Override
    protected String getItemDisplayName(InventoryBatch item) {
        return item.getBatchCode() + " - " + item.getProductName();
    }

    @Override
    protected PaginationHelper.PaginationResult<InventoryBatch> fetchPage(int page, int pageSize) {
        return batchDAO.getPagedFiltered(page, pageSize, null, selectedCategoryId());
    }

    @Override
    protected PaginationHelper.PaginationResult<InventoryBatch> searchPage(String keyword, int page, int pageSize) {
        return batchDAO.getPagedFiltered(page, pageSize, keyword, selectedCategoryId());
    }

    @Override
    protected List<InventoryBatch> fetchAllForExport() {
        batchDAO.syncExpiredStatus();
        return batchDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm theo mã lô, tên SP, mã SP, số lô, nhà cung cấp...";
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (InventoryBatch b : batchDAO.getAll()) {
            if (b.getBatchCode() != null && !b.getBatchCode().isBlank()) names.add(b.getBatchCode());
            if (b.getProductName() != null && !b.getProductName().isBlank()) names.add(b.getProductName());
            if (b.getProductCode() != null && !b.getProductCode().isBlank()) names.add(b.getProductCode());
            if (b.getLotNumber() != null && !b.getLotNumber().isBlank()) names.add(b.getLotNumber());
            if (b.getSupplierName() != null && !b.getSupplierName().isBlank()) names.add(b.getSupplierName());
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
        InventoryBatch item = rowToItem(modelRow);
        if (item == null) return;
        openDialog(CrudMode.VIEW, item);
    }

    @Override
    protected void openForm(InventoryBatch item) {
        // Không hỗ trợ thêm mới từ trang này (getAddButtonLabel() = null nên
        // nút Add không hiển thị và hàm này thực tế không được gọi tới).
    }

    @Override
    protected boolean deleteItem(InventoryBatch item) { return false; }

    private void openDialog(CrudMode mode, InventoryBatch item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        Frame frame = owner instanceof Frame ? (Frame) owner : null;
        InventoryBatchFormDialog dialog = new InventoryBatchFormDialog(
                frame, mode, item, batchDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private String statusLabel(InventoryBatch b) {
        if (b.getRemainingQty() <= 0) return "Đã bán hết";
        Long days = b.daysUntilExpiry();
        if (days != null && days < 0) return "Hết hạn";
        if (days != null && days <= NEAR_EXPIRY_DAYS) return "Sắp hết hạn";
        return "Còn hàng";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        String label = String.valueOf(value);
        switch (label) {
            case "Đã bán hết": return AppColor.TEXT_MUTED;
            case "Hết hạn": return AppColor.ERROR;
            case "Sắp hết hạn": return AppColor.WARNING;
            default: return AppColor.SUCCESS;
        }
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }

    // ---------------------------------------------------------------
    // Helper: copy mã vào clipboard
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