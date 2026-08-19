package com.view.admin.permission;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.RoleDAO;
import com.i18n.Lang;
import com.model.AppRole;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Trang <b>Quản lý vai trò</b>: tạo / sửa / xóa role (lifecycle).
 * Gán quyền chi tiết nằm ở {@link RolePermissionPanel}.
 * <p>
 * UX đồng bộ {@link BaseCrudPanel}: SectionHeader, BaseSearch, BaseTable,
 * phân trang, empty state, loading overlay.
 */
public class RoleManagementPanel extends BaseCrudPanel<AppRole> {

    private final RoleDAO roleDao = new RoleDAO();

    /** RoleID → số user đang dùng (trang hiện tại). */
    private Map<Integer, Integer> userCountByRole = new HashMap<>();

    public RoleManagementPanel() {
        super();

        ActionColumn actions = new ActionColumn().header("Thao tác");
        if (canManageRoles()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa tên / mô tả",
                    this::editRowPublic);
            actions.add("delete", FontAwesomeSolid.TRASH, AppColor.ERROR, "Xóa vai trò tùy chỉnh",
                    this::deleteRowPublic);
        }
        table.setActionColumn(actions);

        // Badge loại: Hệ thống / Tùy chỉnh
        table.setBadgeColumn(3, this::typeLabel, this::typeColor);

        table.setColumnWidths(140, 200, 280, 110, 100);
        table.setColumnMinWidths(100, 140, 160, 90, 80);

        initialLoad();
    }

    private boolean canManageRoles() {
        return AuthService.getInstance().can(AppPermission.RBAC_MANAGE);
    }

    // ---------------------------------------------------------------
    // BaseCrudPanel config
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() {
        return FontAwesomeSolid.USER_TAG;
    }

    @Override
    protected String getPageTitle() {
        return Lang.get("role.management.title");
    }

    @Override
    protected String getPageSubtitle() {
        return Lang.get("role.management.subtitle");
    }

    @Override
    protected String getAddButtonLabel() {
        return canManageRoles() ? Lang.get("role.management.add") : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                Lang.get("role.management.col.code"),
                Lang.get("role.management.col.name"),
                Lang.get("role.management.col.description"),
                Lang.get("role.management.col.type"),
                Lang.get("role.management.col.users")
        };
    }

    @Override
    protected Object[] mapRowToColumns(AppRole item) {
        int users = userCountByRole.getOrDefault(item.getRoleId(), 0);
        String desc = item.getDescription();
        if (desc == null || desc.isBlank()) desc = "—";
        return new Object[]{
                item.getRoleCode(),
                item.getRoleName(),
                desc,
                item.isSystemRole() ? "SYSTEM" : "CUSTOM",
                users
        };
    }

    @Override
    protected int[] numericColumns() {
        return new int[]{4};
    }

    @Override
    protected String getEntityLabel() {
        return Lang.get("role.management.entity");
    }

    @Override
    protected String getItemDisplayName(AppRole item) {
        return item.getRoleName() != null ? item.getRoleName() : item.getRoleCode();
    }

    @Override
    protected String getSearchPlaceholder() {
        return Lang.get("role.management.search");
    }

    @Override
    protected boolean supportsImport() {
        return false;
    }

    @Override
    protected boolean supportsExport() {
        return true;
    }

    /** Xóa mặc định của BaseCrudPanel tắt — dùng ActionColumn riêng (chỉ custom role). */
    @Override
    protected boolean supportsDelete() {
        return false;
    }

    @Override
    protected boolean supportsEdit() {
        return canManageRoles();
    }

    private String typeLabel(Object value) {
        if (value == null) return "—";
        String v = value.toString();
        if ("SYSTEM".equalsIgnoreCase(v)) return Lang.get("role.management.type.system");
        if ("CUSTOM".equalsIgnoreCase(v)) return Lang.get("role.management.type.custom");
        return v;
    }

    private Color typeColor(Object value) {
        if (value != null && "SYSTEM".equalsIgnoreCase(value.toString())) {
            return AppColor.INFO;
        }
        return AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Data
    // ---------------------------------------------------------------

    private List<AppRole> loadAllManaged() {
        // Không hiện CUSTOMER — giống trang phân quyền
        return roleDao.findManagedRoles();
    }

    private void refreshUserCounts(List<AppRole> items) {
        List<Integer> ids = new ArrayList<>();
        for (AppRole r : items) {
            ids.add(r.getRoleId());
        }
        userCountByRole = roleDao.countUsersGrouped(ids);
    }

    private PaginationHelper.PaginationResult<AppRole> paginate(
            List<AppRole> all, int page, int pageSize) {
        int total = all.size();
        int from = Math.max(0, (page - 1) * pageSize);
        if (from >= total) {
            return new PaginationHelper.PaginationResult<>(List.of(), page, pageSize, total);
        }
        int to = Math.min(from + pageSize, total);
        List<AppRole> slice = all.subList(from, to);
        refreshUserCounts(slice);
        return new PaginationHelper.PaginationResult<>(new ArrayList<>(slice), page, pageSize, total);
    }

    @Override
    protected PaginationHelper.PaginationResult<AppRole> fetchPage(int page, int pageSize) {
        return paginate(loadAllManaged(), page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<AppRole> searchPage(String keyword, int page, int pageSize) {
        String key = keyword != null ? keyword.trim().toLowerCase(Locale.ROOT) : "";
        List<AppRole> all = loadAllManaged();
        if (!key.isEmpty()) {
            all = all.stream()
                    .filter(r -> {
                        String name = r.getRoleName() != null ? r.getRoleName().toLowerCase(Locale.ROOT) : "";
                        String code = r.getRoleCode() != null ? r.getRoleCode().toLowerCase(Locale.ROOT) : "";
                        return name.contains(key) || code.contains(key);
                    })
                    .collect(Collectors.toList());
        }
        return paginate(all, page, pageSize);
    }

    @Override
    protected List<AppRole> fetchAllForExport() {
        return loadAllManaged();
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (AppRole r : loadAllManaged()) {
            if (r.getRoleName() != null && !r.getRoleName().isBlank()) set.add(r.getRoleName());
            if (r.getRoleCode() != null && !r.getRoleCode().isBlank()) set.add(r.getRoleCode());
        }
        return new ArrayList<>(set);
    }

    @Override
    protected void openForm(AppRole item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        RoleFormDialog dialog = new RoleFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, roleDao);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(AppRole item) {
        if (item == null || item.isSystemRole()) return false;
        String err = roleDao.deleteCustomRole(item.getRoleCode());
        if (err != null) {
            BaseDialog.error(this, "Không xóa được", err);
            return false;
        }
        return true;
    }

    @Override
    protected String getDeleteFailureMessage(AppRole item) {
        return "Không thể xóa vai trò \"" + getItemDisplayName(item) + "\".";
    }

    private void editRowPublic(int modelRow) {
        AppRole item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private void deleteRowPublic(int modelRow) {
        AppRole item = rowToItem(modelRow);
        if (item == null) return;
        if (item.isSystemRole()) {
            BaseDialog.info(this, "Không thể xóa",
                    "Vai trò hệ thống (\"" + getItemDisplayName(item) + "\") không được xóa.");
            return;
        }
        boolean confirmed = BaseDialog.confirmDelete(this, getEntityLabel(), getItemDisplayName(item));
        if (!confirmed) return;
        boolean ok = deleteItem(item);
        if (ok) {
            BaseDialog.success(this, "Thành công",
                    "Đã xóa vai trò \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        }
        // deleteItem đã hiện lỗi nếu fail
    }
}
