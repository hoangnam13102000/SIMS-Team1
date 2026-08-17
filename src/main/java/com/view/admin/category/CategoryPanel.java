package com.view.admin.category;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.CategoryDAO;
import com.model.Category;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;

public class CategoryPanel extends BaseCrudPanel<Category> {

    private final CategoryDAO categoryDAO = new CategoryDAO();

    /**
     * So san pham (moi trang thai) cua tung danh muc dang hien tren TRANG
     * hien tai - key = CategoryID, danh muc khong co san pham thi KHONG co
     * mat trong map (xem {@link #hasProducts}). Duoc nap lai trong
     * {@link #fetchPage}/{@link #searchPage} (chay tren background thread
     * cua BaseCrudPanel) ngay truoc khi render, de nut "Xoa"/"Vo hieu hoa"
     * o cot Thao tac biet chinh xac tung dong nen hien nut nao MA KHONG
     * phai truy van CSDL rieng le luc ve icon (tranh N+1 query).
     */
    private Map<Integer, Integer> productCountByCategory = new HashMap<>();

    public CategoryPanel() {
        super();

        ActionColumn actions = new ActionColumn().header("Thao tác");
        if (canEditCategories()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                    this::editRowPublic);
            // Xoa / vo hieu hoa chi CATEGORY_MANAGE (day du).
            if (canCreateCategories()) {
                actions.add("delete-or-toggle",
                        this::deleteOrToggleIcon,
                        this::deleteOrToggleColor,
                        this::deleteOrToggleTooltip,
                        this::handleDeleteOrToggle,
                        null);
            }
        }
        table.setActionColumn(actions);

        table.setBadgeColumn(1, this::statusLabel, this::statusColor);

        table.setColumnWidths(350, 150);
        table.setColumnMinWidths(200, 120);

        initialLoad();
    }

    // ---------------------------------------------------------------
    // Cấu hình BaseCrudPanel
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.TAGS; }

    @Override
    protected String getPageTitle() { return "Quản lý danh mục"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý danh mục sản phẩm hiển thị trên hệ thống"; }

    @Override
    protected String getAddButtonLabel() {
        return canCreateCategories() ? "Thêm danh mục" : null;
    }

    /** Thêm / import / xoá-toggle — chỉ CATEGORY_MANAGE. */
    private boolean canCreateCategories() {
        return AuthService.getInstance().can(AppPermission.CATEGORY_MANAGE);
    }

    /** Sửa — CATEGORY_MANAGE hoặc CATEGORY_EDIT. */
    private boolean canEditCategories() {
        return AuthService.getInstance().can(AppPermission.CATEGORY_MANAGE)
                || AuthService.getInstance().can(AppPermission.CATEGORY_EDIT);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Tên danh mục", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Category item) {
        return new Object[]{
                item.getCategoryName(),
                item.getStatus()
        };
    }

    @Override
    protected String getEntityLabel() { return "danh mục"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Category> result) {
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Category item) {
        return item.getCategoryName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Category> fetchPage(int page, int pageSize) {
        PaginationHelper.PaginationResult<Category> result = categoryDAO.getPaged(page, pageSize);
        refreshProductCounts(result.getData());
        return result;
    }

    @Override
    protected PaginationHelper.PaginationResult<Category> searchPage(String keyword, int page, int pageSize) {
        PaginationHelper.PaginationResult<Category> result = categoryDAO.search(keyword, page, pageSize);
        refreshProductCounts(result.getData());
        return result;
    }

    /** Nap lai productCountByCategory cho dung danh sach danh muc vua fetch - goi tren background thread, xem javadoc field. */
    private void refreshProductCounts(List<Category> items) {
        List<Integer> ids = new ArrayList<>();
        for (Category c : items) {
            ids.add(c.getCategoryId());
        }
        productCountByCategory = categoryDAO.countProductsGrouped(ids);
    }

    @Override
    protected List<Category> fetchAllForExport() {
        return categoryDAO.getAll();
    }

    @Override
    protected void openForm(Category item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        CategoryFormDialog dialog = new CategoryFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, categoryDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    /**
     * Cot Thao tac o day la ActionColumn TU BIEN SOAN rieng (xem constructor),
     * KHONG dung co che view/edit/delete mac dinh cua BaseCrudPanel - nut
     * "Xoa" that su nam trong slot "delete-or-toggle" ben duoi, CHI hien khi
     * danh muc chua co san pham nao. Vi vay tra ve false o day de an di cot
     * xoa mac dinh (rong, khong dung toi); deleteItem() van duoc trien khai
     * day du de dung chung logic voi handleDeleteOrToggle() ben duoi.
     */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Category item) {
        return categoryDAO.deleteCategory(item.getCategoryId());
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên danh mục..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Category c : categoryDAO.getAll()) {
            if (c.getCategoryName() != null && !c.getCategoryName().isBlank()) {
                names.add(c.getCategoryName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected boolean supportsImport() {
        return canCreateCategories();
    }

    @Override
    protected String[] getImportColumns() { return new String[]{"Tên danh mục"}; }

    @Override
    protected String getImportInstructions() {
        return "Mỗi dòng là 1 danh mục. Tên danh mục không được trùng với danh mục đã có.";
    }

    @Override
    protected com.importer.ImportRowResult importRow(String[] cells, int rowNumber) {
        String name = cells.length > 0 && cells[0] != null ? cells[0].trim() : "";
        if (name.isEmpty()) {
            return com.importer.ImportRowResult.failure("thiếu tên danh mục.");
        }
        if (categoryDAO.nameExistsExcluding(name, -1)) {
            return com.importer.ImportRowResult.failure("danh mục \"" + name + "\" đã tồn tại.");
        }
        Category category = new Category();
        category.setCategoryName(name);
        category.setStatus("ACTIVE");
        if (!categoryDAO.insertCategory(category)) {
            return com.importer.ImportRowResult.failure("lưu thất bại.");
        }
        com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_CREATE,
                "Đã nhập danh mục \"" + name + "\" từ file", category, null);
        return com.importer.ImportRowResult.success();
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Hành động: sửa / xóa (chưa có sản phẩm) / bật-tắt trạng thái (đã có sản phẩm)
    // ---------------------------------------------------------------

    private void editRowPublic(int modelRow) {
        Category item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private boolean isDisabledRow(int modelRow) {
        Category item = rowToItem(modelRow);
        return item != null && !item.isActive();
    }

    /** true = danh muc nay DA co san pham (moi trang thai) -> chi cho Vo hieu hoa, khong cho Xoa cung. */
    private boolean hasProducts(int modelRow) {
        Category item = rowToItem(modelRow);
        if (item == null) return true; // an toan: khong xac dinh duoc -> khong cho xoa
        return productCountByCategory.getOrDefault(item.getCategoryId(), 0) > 0;
    }

    private FontAwesomeSolid deleteOrToggleIcon(int modelRow) {
        if (!hasProducts(modelRow)) return FontAwesomeSolid.TRASH_ALT;
        return isDisabledRow(modelRow) ? FontAwesomeSolid.TOGGLE_OFF : FontAwesomeSolid.TOGGLE_ON;
    }

    private Color deleteOrToggleColor(int modelRow) {
        if (!hasProducts(modelRow)) return AppColor.ERROR;
        return isDisabledRow(modelRow) ? AppColor.TEXT_MUTED : AppColor.SUCCESS;
    }

    private String deleteOrToggleTooltip(int modelRow) {
        if (!hasProducts(modelRow)) return "Xóa danh mục";
        return isDisabledRow(modelRow) ? "Kích hoạt lại" : "Vô hiệu hóa";
    }

    private void handleDeleteOrToggle(int modelRow) {
        if (!hasProducts(modelRow)) {
            deleteCategoryRow(modelRow);
            return;
        }
        Category item = rowToItem(modelRow);
        if (item == null) return;
        if (item.isActive()) {
            disableCategory(item);
        } else {
            enableCategory(item);
        }
    }

    private void deleteCategoryRow(int modelRow) {
        Category item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirmDelete(this, getEntityLabel(), getItemDisplayName(item));
        if (!confirmed) return;
        if (deleteItem(item)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_DELETE,
                    "Đã xóa " + getEntityLabel() + " \"" + getItemDisplayName(item) + "\"", item, null);
            BaseDialog.success(this, "Thành công", "Đã xóa " + getEntityLabel() + " \"" + getItemDisplayName(item) + "\"");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể xóa",
                    "Xóa thất bại. Danh mục này có thể vừa được gán cho sản phẩm khác, vui lòng tải lại trang.");
        }
    }

    private void disableCategory(Category item) {
        boolean confirmed = BaseDialog.confirm(this, "Vô hiệu hóa danh mục",
                "Vô hiệu hóa danh mục \"" + item.getCategoryName() + "\"? Danh mục này sẽ không còn hiển thị cho khách hàng, "
                        + "và TẤT CẢ sản phẩm thuộc danh mục này sẽ không thể bán được (kể cả tại quầy POS) cho đến khi kích hoạt lại.",
                "Vô hiệu hóa", AppColor.WARNING, AppColor.WARNING, FontAwesomeSolid.TOGGLE_OFF);
        if (!confirmed) return;
        item.setStatus("DISABLED");
        if (categoryDAO.updateCategory(item)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_STATUS_CHANGE,
                    "Đã vô hiệu hóa danh mục \"" + item.getCategoryName() + "\"");
            BaseDialog.success(this, "Thành công", "Đã vô hiệu hóa danh mục \"" + item.getCategoryName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Vô hiệu hóa thất bại. Vui lòng thử lại.");
        }
    }

    private void enableCategory(Category item) {
        item.setStatus("ACTIVE");
        if (categoryDAO.updateCategory(item)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_STATUS_CHANGE,
                    "Đã kích hoạt lại danh mục \"" + item.getCategoryName() + "\"");
            BaseDialog.success(this, "Thành công", "Đã kích hoạt lại danh mục \"" + item.getCategoryName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Kích hoạt thất bại. Vui lòng thử lại.");
        }
    }

    // ---------------------------------------------------------------
    // Nhãn / màu hiển thị
    // ---------------------------------------------------------------

    private String statusLabel(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? "Vô hiệu hóa" : "Đang hoạt động";
    }

    private Color statusColor(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }
}