package com.view.admin.product;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.CategoryDAO;
import com.dao.ProductDAO;
import com.model.Category;
import com.model.Product;
import com.model.permission.AppPermission;
import com.service.AuthService;
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
import java.awt.Cursor;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ProductPanel extends BaseCrudPanel<Product> {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private AutoRowNumber stt;
    private FilterDropdown<CategoryOption> categoryFilter;
    private FilterDropdown<PriceRangeOption> priceFilter;
    private JLabel clearFiltersLink;

    public ProductPanel() {
        super();

        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic);
        if (canManageProducts()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                            this::editRowPublic)
                    .add("status-toggle",
                            this::statusToggleIcon,
                            this::statusToggleColor,
                            this::statusToggleTooltip,
                            this::toggleStatusRow,
                            null);
        }
        table.setActionColumn(actions);

        stt = table.setAutoRowNumberColumn(0);
        table.setImageColumn(1, 40);
        table.setBadgeColumn(8, this::statusLabel, this::statusColor);
        table.setColumnWidths(45, 52, 85, 160, 110, 95, 95, 70, 105);
        table.setColumnMinWidths(40, 48, 70, 100, 85, 75, 75, 55, 95);

        // Cột "Mã SP" (index 2): thêm icon copy
        table.getTable().getColumnModel().getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
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
                    c.setToolTipText("Click để copy mã sản phẩm: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã SP
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 2 && viewRow >= 0) { // Cột Mã SP
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 2);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(ProductPanel.this, "Copy thành công", "Đã copy mã sản phẩm: " + text);
                    }
                }
            }
        });

        buildFilterBar();
        initialLoad();
    }

    // ---------------------------------------------------------------
    // Bo loc: danh muc + khoang gia ban (hien canh o tim kiem tren toolbar)
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

    private enum PriceRangeOption {
        ALL("Tất cả mức giá", null, null),
        UNDER_50K("Dưới 50.000đ", null, new BigDecimal(50_000)),
        R_50_100K("50.000đ - 100.000đ", new BigDecimal(50_000), new BigDecimal(100_000)),
        R_100_300K("100.000đ - 300.000đ", new BigDecimal(100_000), new BigDecimal(300_000)),
        R_300_500K("300.000đ - 500.000đ", new BigDecimal(300_000), new BigDecimal(500_000)),
        R_500K_1M("500.000đ - 1.000.000đ", new BigDecimal(500_000), new BigDecimal(1_000_000)),
        OVER_1M("Trên 1.000.000đ", new BigDecimal(1_000_000), null);

        final String label;
        final BigDecimal min;
        final BigDecimal max;

        PriceRangeOption(String label, BigDecimal min, BigDecimal max) {
            this.label = label;
            this.min = min;
            this.max = max;
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

        priceFilter = new FilterDropdown<>(FontAwesomeSolid.TAG, PriceRangeOption.values());
        priceFilter.onChange(opt -> onFilterChanged());
        addToolbarFilter(priceFilter);

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
                priceFilter.resetToAll();
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
        boolean anyActive = categoryFilter.isFilterActive() || priceFilter.isFilterActive();
        if (clearFiltersLink != null) clearFiltersLink.setVisible(anyActive);
        applyFilters();
    }

    private Integer selectedCategoryId() {
        CategoryOption opt = categoryFilter == null ? null : categoryFilter.getSelected();
        return opt == null ? null : opt.categoryId;
    }

    private BigDecimal selectedMinPrice() {
        PriceRangeOption opt = priceFilter == null ? null : priceFilter.getSelected();
        return opt == null ? null : opt.min;
    }

    private BigDecimal selectedMaxPrice() {
        PriceRangeOption opt = priceFilter == null ? null : priceFilter.getSelected();
        return opt == null ? null : opt.max;
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BOX; }
    @Override
    protected String getPageTitle() { return "Quản lý sản phẩm"; }
    @Override
    protected String getPageSubtitle() { return "Quản lý danh sách sản phẩm, giá và tồn kho trong hệ thống"; }
    @Override
    protected String getAddButtonLabel() { return canManageProducts() ? "Thêm sản phẩm" : null; }

    private boolean canManageProducts() {
        return AuthService.getInstance().can(AppPermission.PRODUCT_MANAGE);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Ảnh", "Mã sản phẩm", "Tên sản phẩm", "Danh mục", "Giá nhập", "Giá bán", "Tồn kho", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Product item) {
        return new Object[]{
                "",
                item.getImageUrl(),
                item.getProductCode(),
                item.getProductName(),
                item.getCategoryName(),
                NumberUtil.formatThousands(item.getImportPrice().longValue()),
                NumberUtil.formatThousands(item.getSellPrice().longValue()),
                item.getStock(),
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{5, 6, 7}; }

    @Override
    protected String getEntityLabel() { return "sản phẩm"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Product> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Product item) {
        return item.getProductName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Product> fetchPage(int page, int pageSize) {
        return productDAO.getPagedFiltered(page, pageSize, null,
                selectedCategoryId(), selectedMinPrice(), selectedMaxPrice());
    }

    @Override
    protected PaginationHelper.PaginationResult<Product> searchPage(String keyword, int page, int pageSize) {
        return productDAO.getPagedFiltered(page, pageSize, keyword,
                selectedCategoryId(), selectedMinPrice(), selectedMaxPrice());
    }

    @Override
    protected List<Product> fetchAllForExport() {
        return productDAO.getAll();
    }

    @Override
    protected void openForm(Product item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductFormDialog dialog = new ProductFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, productDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean deleteItem(Product item) { return false; }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã SP, tên sản phẩm, danh mục..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Product p : productDAO.getAll()) {
            if (p.getProductName() != null && !p.getProductName().isBlank()) {
                names.add(p.getProductName());
            }
            if (p.getProductCode() != null && !p.getProductCode().isBlank()) {
                names.add(p.getProductCode());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected boolean supportsImport() { return true; }

    @Override
    protected String[] getImportColumns() {
        return new String[]{"Tên sản phẩm", "Danh mục", "Thương hiệu", "Đơn vị tính", "Khối lượng/dung tích",
                "Mô tả", "Giá bán", "Chênh lệch riêng", "Tồn kho tối thiểu"};
    }

    @Override
    protected String getImportInstructions() {
        return "Danh mục phải trùng tên với 1 danh mục đã có sẵn trong hệ thống (tạo danh mục trước nếu chưa có). "
                + "Giá nhập và tồn kho ban đầu luôn là 0 - nhập hàng qua Phiếu nhập kho sau khi thêm sản phẩm. "
                + "Thương hiệu, đơn vị tính, khối lượng/dung tích, mô tả, chênh lệch riêng có thể để trống.";
    }

    @Override
    protected com.importer.ImportRowResult importRow(String[] cells, int rowNumber) {
        String name = cellAt(cells, 0);
        String categoryName = cellAt(cells, 1);
        String brand = cellAt(cells, 2);
        String unit = cellAt(cells, 3);
        String weightVolume = cellAt(cells, 4);
        String description = cellAt(cells, 5);
        String sellPriceText = cellAt(cells, 6);
        String marginText = cellAt(cells, 7);
        String minStockText = cellAt(cells, 8);

        if (name.isEmpty()) {
            return com.importer.ImportRowResult.failure("thiếu tên sản phẩm.");
        }
        if (name.length() > 150) {
            return com.importer.ImportRowResult.failure("tên sản phẩm tối đa 150 ký tự.");
        }
        if (categoryName.isEmpty()) {
            return com.importer.ImportRowResult.failure("thiếu danh mục.");
        }

        Category category = findCategoryByName(categoryName);
        if (category == null) {
            return com.importer.ImportRowResult.failure("danh mục \"" + categoryName + "\" không tồn tại.");
        }

        java.math.BigDecimal sellPrice = parseNonNegativeAmount(sellPriceText);
        if (sellPriceText.isEmpty()) {
            return com.importer.ImportRowResult.failure("thiếu giá bán.");
        }
        if (sellPrice == null) {
            return com.importer.ImportRowResult.failure("giá bán phải là số nguyên không âm.");
        }

        java.math.BigDecimal margin = null;
        if (!marginText.isEmpty()) {
            margin = parseNonNegativeAmount(marginText);
            if (margin == null) {
                return com.importer.ImportRowResult.failure("chênh lệch riêng phải là số nguyên không âm.");
            }
        }

        int minStock = 0;
        if (!minStockText.isEmpty()) {
            try {
                minStock = Integer.parseInt(minStockText.trim());
                if (minStock < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                return com.importer.ImportRowResult.failure("tồn kho tối thiểu phải là số nguyên không âm.");
            }
        }

        Product product = new Product();
        product.setProductName(name);
        product.setCategoryId(category.getCategoryId());
        product.setCategoryName(category.getCategoryName());
        product.setBrand(brand.isEmpty() ? null : brand);
        product.setUnit(unit.isEmpty() ? null : unit);
        product.setWeightVolume(weightVolume.isEmpty() ? null : weightVolume);
        product.setDescription(description.isEmpty() ? null : description);
        // Giống ProductFormDialog (ADD): giá nhập và tồn kho luôn = 0, nhập hàng
        // sau đó qua Phiếu nhập kho để tạo lô đầu tiên (giữ đúng logic FEFO).
        product.setImportPrice(java.math.BigDecimal.ZERO);
        product.setSellPrice(sellPrice);
        product.setMargin(margin);
        product.setAutoPrice(false);
        product.setStock(0);
        product.setMinStock(minStock);
        product.setStatus("ACTIVE");

        if (!productDAO.insert(product)) {
            return com.importer.ImportRowResult.failure("lưu thất bại.");
        }

        com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_CREATE,
                "Đã nhập sản phẩm \"" + name + "\" từ file", product, null);
        return com.importer.ImportRowResult.success();
    }

    private static String cellAt(String[] cells, int index) {
        String v = index < cells.length ? cells[index] : null;
        return v == null ? "" : v.trim();
    }

    private Category findCategoryByName(String name) {
        for (Category c : categoryDAO.findAll()) {
            if (c.getCategoryName() != null && c.getCategoryName().equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    /** Giống isValidNonNegativeAmount() của ProductFormDialog: số nguyên không âm, chấp nhận dấu chấm/phẩy phân cách nghìn. */
    private static java.math.BigDecimal parseNonNegativeAmount(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            java.math.BigDecimal amount = new java.math.BigDecimal(text.trim().replace(".", "").replace(",", ""));
            return amount.signum() < 0 ? null : amount;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private void viewRowPublic(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductDetailDialog dialog = new ProductDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    private void editRowPublic(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private boolean isDisabledRow(int modelRow) {
        Product item = rowToItem(modelRow);
        return item != null && !item.isActive();
    }

    private FontAwesomeSolid statusToggleIcon(int modelRow) {
        return isDisabledRow(modelRow) ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.BAN;
    }

    private Color statusToggleColor(int modelRow) {
        return isDisabledRow(modelRow) ? AppColor.SUCCESS : AppColor.WARNING;
    }

    private String statusToggleTooltip(int modelRow) {
        return isDisabledRow(modelRow) ? "Mở bán lại" : "Ngừng bán";
    }

    private void toggleStatusRow(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;
        boolean willDisable = item.isActive();
        boolean confirmed = BaseDialog.confirm(this,
                willDisable ? "Ngừng bán sản phẩm" : "Mở bán lại sản phẩm",
                (willDisable
                        ? "Ngừng bán \"" + item.getProductName() + "\"? Sản phẩm sẽ không còn hiển thị cho khách hàng."
                        : "Mở bán lại \"" + item.getProductName() + "\"? Sản phẩm sẽ hiển thị lại cho khách hàng."),
                willDisable ? "Ngừng bán" : "Mở bán lại",
                willDisable ? AppColor.WARNING : AppColor.SUCCESS,
                willDisable ? AppColor.WARNING : AppColor.SUCCESS,
                willDisable ? FontAwesomeSolid.BAN : FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;

        item.setStatus(willDisable ? "DISABLED" : "ACTIVE");
        if (productDAO.update(item)) {
            BaseDialog.success(this, "Thành công",
                    willDisable ? "Đã ngừng bán \"" + item.getProductName() + "\"." : "Đã mở bán lại \"" + item.getProductName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private String statusLabel(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? "Ngừng bán" : "Đang bán";
    }

    private Color statusColor(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Helper: copy mã sản phẩm vào clipboard
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