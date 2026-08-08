package com.view.admin.category;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.CategoryDAO;
import com.model.Category;
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
    private AutoRowNumber stt;

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

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                // 1 slot duy nhat, doi hanh vi theo tung dong: danh muc CHUA
                // co san pham nao -> nut "Xoa" (xoa cung); danh muc DA co san
                // pham -> nut "Vo hieu hoa/Kich hoat lai" (xoa mem), vi
                // Products.CategoryID la FOREIGN KEY khong CASCADE nen xoa
                // cung se that bai neu con san pham tham chieu.
                .add("delete-or-toggle",
                        this::deleteOrToggleIcon,
                        this::deleteOrToggleColor,
                        this::deleteOrToggleTooltip,
                        this::handleDeleteOrToggle,
                        null));

        stt = table.setAutoRowNumberColumn(0);
        table.setBadgeColumn(3, this::statusLabel, this::statusColor);

        table.setColumnWidths(60, 90, 260, 150);
        table.setColumnMinWidths(55, 70, 180, 120);

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
    protected String getAddButtonLabel() { return "Thêm danh mục"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã DM", "Tên danh mục", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Category item) {
        return new Object[]{
                "",
                item.getCategoryId(),
                item.getCategoryName(),
                item.getStatus()
        };
    }

    @Override
    protected String getEntityLabel() { return "danh mục"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Category> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
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

    /** Chưa có nơi nào publish DataChangedEvent cho Categories nên reload() trực tiếp sau mỗi thao tác. */
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