package com.view.admin.account;

import com.components.BaseDialog;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.RoleDAO;
import com.dao.UserDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.AppRole;
import com.model.Role;
import com.model.User;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class UserAccountPanel extends BaseCrudPanel<User> {
    private final UserDAO userDAO = new UserDAO();
    private FilterDropdown<RoleFilterOption> roleFilter;
    private JLabel clearFiltersLink;
    
    // ⭐ Thay đổi: lưu roleCode dạng String
    private String selectedRoleCode;
    
    public UserAccountPanel() {
        super();
        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic);
        if (canEditUsers()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                    this::editRowPublic);
        }
        if (canManageUsers()) {
            actions.add("lock-toggle",
                    this::lockToggleIcon,
                    this::lockToggleColor,
                    this::lockToggleTooltip,
                    this::toggleLockRow,
                    row -> canManage(row));
        }
        table.setActionColumn(actions);
        table.setBadgeColumn(4, this::statusLabel, this::statusColor);
        table.setBadgeColumn(5, this::lockLabel, this::lockColor);
        table.setColumnWidths(115, 115, 180, 150, 130, 115);
        table.setColumnMinWidths(90, 85, 90, 110, 100, 95);
        
        setupRoleFilter();
        
        // ⭐ LẮNG NGHE SỰ KIỆN: Khi vai trò thay đổi → cập nhật bộ lọc + reload
        AppEventBus.getInstance().subscribe(DataChangedEvent.class, event -> {
            if (DataChangedEvent.ROLE.equals(event.entity) 
                || DataChangedEvent.ALL.equals(event.entity)) {
                setupRoleFilter();
                onDataChanged();
            }
        });
        
        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.USERS_COG; }
    @Override
    protected String getPageTitle() { return "Quản lý tài khoản"; }
    @Override
    protected String getPageSubtitle() { return "Quản lý tài khoản người dùng và phân quyền trong hệ thống"; }
    @Override
    protected String getAddButtonLabel() { return null; }

    private boolean canManageUsers() {
        return AuthService.getInstance().can(AppPermission.USER_MANAGE);
    }

    private boolean canEditUsers() {
        return AuthService.getInstance().can(AppPermission.USER_MANAGE)
                || AuthService.getInstance().can(AppPermission.USER_EDIT);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Tên đăng nhập", "Họ và tên", "Email", "Vai trò", "Trạng thái", "Khóa"};
    }

    // ⭐ CẬP NHẬT: Hiển thị tên vai trò từ roleCode
    @Override
    protected Object[] mapRowToColumns(User item) {
        return new Object[]{
                item.getUsername(),
                item.getFullName(),
                item.getEmail(),
                roleLabelFromCode(item.getRoleCode()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    @Override
    protected String getEntityLabel() { return "tài khoản"; }
    
    @Override
    protected void afterRender(PaginationHelper.PaginationResult<User> result) {
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(User item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    // ⭐ CẬP NHẬT: filter dùng roleCode String
    @Override
    protected PaginationHelper.PaginationResult<User> fetchPage(int page, int pageSize) {
        String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
        return userDAO.filterByRole(keyword, selectedRoleCode, page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<User> searchPage(String keyword, int page, int pageSize) {
        return userDAO.filterByRole(keyword, selectedRoleCode, page, pageSize);
    }

    @Override
    protected java.util.List<User> fetchAllForExport() {
        return userDAO.getAll();
    }

    @Override
    protected void openForm(User item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        UserFormDialog dialog = new UserFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, userDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean deleteItem(User item) { return false; }
    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã NV (tên đăng nhập), họ tên, email..."; }

    // ⭐ CẬP NHẬT: RoleFilterOption dùng roleCode String
    private static final class RoleFilterOption {
        final String roleCode;
        final String label;
        RoleFilterOption(String roleCode, String label) {
            this.roleCode = roleCode;
            this.label = label;
        }
        @Override
        public String toString() { return label; }
    }

    // ⭐ CẬP NHẬT: setupRoleFilter load từ DB thay vì hardcode
    private void setupRoleFilter() {
        // Xóa filter cũ nếu có
        if (roleFilter != null) {
            removeToolbarFilter(roleFilter);
            if (clearFiltersLink != null) removeToolbarFilter(clearFiltersLink);
        }
        
        RoleDAO roleDAO = new RoleDAO();
        List<AppRole> allRoles = roleDAO.findAll(); // Bao gồm cả CUSTOMER
        
        List<RoleFilterOption> options = new ArrayList<>();
        options.add(new RoleFilterOption(null, "Tất cả vai trò"));
        for (AppRole r : allRoles) {
            options.add(new RoleFilterOption(r.getRoleCode(), r.getRoleName()));
        }
        
        RoleFilterOption[] optionArray = options.toArray(new RoleFilterOption[0]);
        roleFilter = new FilterDropdown<>(FontAwesomeSolid.USER_CIRCLE, optionArray);
        roleFilter.onChange(opt -> onFilterChanged());
        addToolbarFilter(roleFilter);
        
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
                roleFilter.resetToAll();
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
        
        revalidate();
        repaint();
    }

    private void onFilterChanged() {
        RoleFilterOption opt = roleFilter.getSelected();
        selectedRoleCode = opt == null ? null : opt.roleCode;
        if (clearFiltersLink != null) clearFiltersLink.setVisible(roleFilter.isFilterActive());
        applyFilters();
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (User u : userDAO.getAll()) {
            if (u.getFullName() != null && !u.getFullName().isBlank()) {
                names.add(u.getFullName());
            }
            if (u.getUsername() != null && !u.getUsername().isBlank()) {
                names.add(u.getUsername());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private void viewRowPublic(int modelRow) {
        User item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        UserDetailDialog dialog = new UserDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    private void editRowPublic(int modelRow) {
        User item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private int currentUserId() {
        User current = AuthService.getInstance().getCurrentUser();
        return current != null ? current.getUserId() : -1;
    }

    private boolean canManage(int modelRow) {
        User item = rowToItem(modelRow);
        return item != null && item.getUserId() != currentUserId();
    }

    private boolean isLockedRow(int modelRow) {
        User item = rowToItem(modelRow);
        return item != null && item.isLocked();
    }

    private FontAwesomeSolid lockToggleIcon(int modelRow) {
        return isLockedRow(modelRow) ? FontAwesomeSolid.UNLOCK_ALT : FontAwesomeSolid.LOCK;
    }

    private Color lockToggleColor(int modelRow) {
        return isLockedRow(modelRow) ? AppColor.SUCCESS : AppColor.WARNING;
    }

    private String lockToggleTooltip(int modelRow) {
        return isLockedRow(modelRow) ? "Mở khóa tài khoản" : "Khóa tài khoản";
    }

    private void toggleLockRow(int modelRow) {
        if (isLockedRow(modelRow)) {
            unlockRow(modelRow);
        } else {
            lockRow(modelRow);
        }
    }

    private void lockRow(int modelRow) {
        User item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this, "Khóa tài khoản",
                "Khóa tài khoản \"" + getItemDisplayName(item) + "\"? Tài khoản này sẽ không thể đăng nhập cho tới khi được mở khóa lại.",
                "Khóa tài khoản", AppColor.WARNING, AppColor.WARNING, FontAwesomeSolid.LOCK);
        if (!confirmed) return;
        if (userDAO.setLocked(item.getUserId(), true)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_STATUS_CHANGE,
                    "Đã khóa tài khoản \"" + getItemDisplayName(item) + "\"");
            BaseDialog.success(this, "Thành công", "Đã khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể khóa", "Khóa tài khoản thất bại. Vui lòng thử lại.");
        }
    }

    private void unlockRow(int modelRow) {
        User item = rowToItem(modelRow);
        if (item == null) return;
        if (userDAO.setLocked(item.getUserId(), false)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_STATUS_CHANGE,
                    "Đã mở khóa tài khoản \"" + getItemDisplayName(item) + "\"");
            BaseDialog.success(this, "Thành công", "Đã mở khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể mở khóa", "Mở khóa tài khoản thất bại. Vui lòng thử lại.");
        }
    }

    // ⭐ CẬP NHẬT: roleLabel nhận roleCode String thay vì enum
    private static String roleLabelFromCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return "—";
        Role role = Role.tryParse(roleCode);
        if (role != null) {
            switch (role) {
                case ADMIN: return "Quản trị viên";
                case SALES_MANAGER: return "Quản lý bán hàng";
                case INVENTORY_MANAGER: return "Quản lý kho";
                case SALES_STAFF: return "Nhân viên bán hàng";
                case CUSTOMER: return "Khách hàng";
                default: return role.name();
            }
        }
        // ⭐ Với vai trò tùy chỉnh: load tên từ DB
        RoleDAO roleDAO = new RoleDAO();
        AppRole appRole = roleDAO.findByCode(roleCode);
        return appRole != null ? appRole.getRoleName() : roleCode;
    }

    private String statusLabel(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? "Vô hiệu hóa" : "Đang hoạt động";
    }

    private Color statusColor(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    private String lockLabel(Object value) {
        return "LOCKED".equals(value) ? "Đang khóa" : "Bình thường";
    }

    private Color lockColor(Object value) {
        return "LOCKED".equals(value) ? AppColor.ERROR : AppColor.TEXT_MUTED;
    }
}