package com.view.admin.employee;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.FilterDropdown;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.EmployeeDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Employee;
import com.model.Role;
import com.model.permission.AppPermission;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

public class EmployeePanel extends BaseCrudPanel<Employee> {
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private FilterDropdown<RoleFilterOption> roleFilter;
    private JLabel clearFiltersLink;
    
    // ⭐ Thay đổi: lưu roleCode dạng String thay vì Role enum
    private String selectedRoleCode;
    
    public EmployeePanel() {
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
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);
        table.setBadgeColumn(6, this::lockLabel, this::lockColor);
        table.setColumnWidths(110, 110, 110, 150, 120, 145, 115);
        table.setColumnMinWidths(85, 85, 90, 110, 95, 140, 110);
        
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
                    c.setToolTipText("Click để copy mã nhân viên: " + text);
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
                        AppAlert.success(EmployeePanel.this, "Copy thành công", "Đã copy mã nhân viên: " + text);
                    }
                }
            }
        });
        
        setupRoleFilter();
        
        // ⭐ LẮNG NGHE SỰ KIỆN: Khi vai trò thay đổi → reload
        AppEventBus.getInstance().subscribe(DataChangedEvent.class, event -> {
            if (DataChangedEvent.ROLE.equals(event.entity) 
                || DataChangedEvent.ALL.equals(event.entity)) {
                setupRoleFilter(); // Cập nhật lại bộ lọc vai trò
                onDataChanged();   // Reload bảng
            }
        });
        
        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.USER_TIE; }
    @Override
    protected String getPageTitle() { return "Quản lý nhân viên"; }
    @Override
    protected String getPageSubtitle() { return "Quản lý hồ sơ nhân viên trong hệ thống (không bao gồm khách hàng)"; }
    @Override
    protected String getAddButtonLabel() {
        return canManageUsers() ? "Thêm nhân viên" : null;
    }

    private boolean canManageUsers() {
        return AuthService.getInstance().can(AppPermission.USER_MANAGE);
    }

    private boolean canEditUsers() {
        return AuthService.getInstance().can(AppPermission.USER_MANAGE)
                || AuthService.getInstance().can(AppPermission.USER_EDIT);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã nhân viên", "Họ và tên", "Tên đăng nhập", "Email", "Vai trò", "Trạng thái", "Khóa"};
    }

    // ⭐ CẬP NHẬT: Hiển thị tên vai trò từ roleCode
    @Override
    protected Object[] mapRowToColumns(Employee item) {
        return new Object[]{
                item.getEmployeeId(),
                item.getFullName(),
                item.getUsername(),
                item.getEmail(),
                roleLabelFromCode(item.getRoleCode()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    @Override
    protected String getEntityLabel() { return "nhân viên"; }
    
    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Employee> result) {
    }

    @Override
    protected String getItemDisplayName(Employee item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    // ⭐ CẬP NHẬT: filter dùng roleCode String
    @Override
    protected PaginationHelper.PaginationResult<Employee> fetchPage(int page, int pageSize) {
        String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
        return employeeDAO.filterByRole(keyword, selectedRoleCode, page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Employee> searchPage(String keyword, int page, int pageSize) {
        return employeeDAO.filterByRole(keyword, selectedRoleCode, page, pageSize);
    }

    @Override
    protected List<Employee> fetchAllForExport() {
        return employeeDAO.getAll();
    }

    @Override
    protected void openForm(Employee item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        EmployeeFormDialog dialog = new EmployeeFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, employeeDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean deleteItem(Employee item) { return false; }
    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã NV, tên đăng nhập, họ tên, email..."; }

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
        
        com.dao.RoleDAO roleDAO = new com.dao.RoleDAO();
        java.util.List<com.model.AppRole> allRoles = roleDAO.findManagedRoles();
        
        java.util.List<RoleFilterOption> options = new java.util.ArrayList<>();
        options.add(new RoleFilterOption(null, "Tất cả vai trò"));
        for (com.model.AppRole r : allRoles) {
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
        for (Employee e : employeeDAO.getAll()) {
            if (e.getFullName() != null && !e.getFullName().isBlank()) {
                names.add(e.getFullName());
            }
            if (e.getUsername() != null && !e.getUsername().isBlank()) {
                names.add(e.getUsername());
            }
            if (e.getEmployeeId() != null && !e.getEmployeeId().isBlank()) {
                names.add(e.getEmployeeId());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected boolean supportsImport() {
        return canManageUsers();
    }

    @Override
    protected String[] getImportColumns() {
        return new String[]{"Họ và tên", "Email", "Số điện thoại", "Vai trò", "Ngày sinh", "Giới tính", "Lương", "Ngày vào làm"};
    }

    @Override
    protected String getImportInstructions() {
        return "Vai trò nhận mã vai trò (vd: ADMIN, SALES_STAFF) hoặc tên tiếng Việt. "
                + "Ngày sinh/Ngày vào làm theo định dạng dd/MM/yyyy. Giới tính: Nam, Nữ, Khác. "
                + "Username và mật khẩu sẽ được hệ thống tự sinh và gửi qua email.";
    }

    // ⭐ CẬP NHẬT: importRow parse roleCode thay vì enum
    @Override
    protected com.importer.ImportRowResult importRow(String[] cells, int rowNumber) {
        String fullName = cellAt(cells, 0);
        String email = cellAt(cells, 1);
        String phone = cellAt(cells, 2);
        String roleText = cellAt(cells, 3);
        String dobText = cellAt(cells, 4);
        String genderText = cellAt(cells, 5);
        String salaryText = cellAt(cells, 6);
        String hireDateText = cellAt(cells, 7);
        
        if (fullName.isEmpty()) {
            return com.importer.ImportRowResult.failure("thiếu họ và tên.");
        }
        String emailError = com.validation.Rules.required("Thiếu email.").validate(email);
        if (emailError == null) emailError = com.validation.Rules.email("Email không đúng định dạng.").validate(email);
        if (emailError != null) {
            return com.importer.ImportRowResult.failure(emailError);
        }
        if (employeeDAO.emailExistsExcluding(email.trim(), -1)) {
            return com.importer.ImportRowResult.failure("email \"" + email + "\" đã được dùng.");
        }
        if (!phone.isEmpty()) {
            String phoneError = com.validation.Rules.phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).").validate(phone);
            if (phoneError != null) {
                return com.importer.ImportRowResult.failure(phoneError);
            }
        }
        
        // ⭐ Parse roleCode từ text (hỗ trợ cả mã và tên tiếng Việt)
        String roleCode = parseRoleCode(roleText);
        if (roleCode == null) {
            return com.importer.ImportRowResult.failure("vai trò \"" + roleText
                    + "\" không hợp lệ. Dùng mã (ADMIN, SALES_STAFF...) hoặc tên tiếng Việt.");
        }
        
        java.time.LocalDate dob = null;
        if (!dobText.isEmpty()) {
            dob = parseDate(dobText);
            if (dob == null) {
                return com.importer.ImportRowResult.failure("ngày sinh \"" + dobText + "\" không đúng định dạng dd/MM/yyyy.");
            }
        }
        Employee.Gender gender = null;
        if (!genderText.isEmpty()) {
            gender = parseGender(genderText);
            if (gender == null) {
                return com.importer.ImportRowResult.failure("giới tính \"" + genderText + "\" không hợp lệ (Nam, Nữ, Khác).");
            }
        }
        java.math.BigDecimal salary = null;
        if (!salaryText.isEmpty()) {
            try {
                salary = new java.math.BigDecimal(salaryText.replace(",", "").replace(".", "").trim());
                if (salary.signum() < 0) {
                    return com.importer.ImportRowResult.failure("lương phải là số không âm.");
                }
            } catch (NumberFormatException e) {
                return com.importer.ImportRowResult.failure("lương \"" + salaryText + "\" không hợp lệ.");
            }
        }
        java.time.LocalDate hireDate = java.time.LocalDate.now();
        if (!hireDateText.isEmpty()) {
            java.time.LocalDate parsed = parseDate(hireDateText);
            if (parsed == null) {
                return com.importer.ImportRowResult.failure("ngày vào làm \"" + hireDateText + "\" không đúng định dạng dd/MM/yyyy.");
            }
            hireDate = parsed;
        }
        Employee employee = new Employee();
        employee.setFullName(fullName);
        employee.setEmail(email.trim());
        employee.setPhone(phone);
        employee.setRoleCode(roleCode); // ⭐ Dùng setRoleCode
        employee.setDateOfBirth(dob);
        employee.setGender(gender);
        employee.setSalary(salary);
        employee.setHireDate(hireDate);
        
        EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(employee);
        if (!result.success) {
            return com.importer.ImportRowResult.failure("lưu thất bại (email có thể đã được dùng).");
        }
        com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_CREATE,
                "Đã nhập nhân viên \"" + fullName + "\" từ file", employee, null);
        return com.importer.ImportRowResult.success();
    }

    private static String cellAt(String[] cells, int index) {
        String v = index < cells.length ? cells[index] : null;
        return v == null ? "" : v.trim();
    }

    // ⭐ MỚI: Parse roleCode từ text (hỗ trợ cả mã và tên tiếng Việt)
    private static String parseRoleCode(String text) {
        if (text == null || text.isBlank()) return null;
        String t = text.trim();
        
        // Thử parse theo enum cũ trước
        Role role = Role.tryParse(t);
        if (role != null) return role.name();
        
        // Thử theo tên tiếng Việt
        switch (t.toLowerCase(java.util.Locale.forLanguageTag("vi"))) {
            case "quản trị viên": case "admin": return "ADMIN";
            case "quản lý bán hàng": case "sales_manager": return "SALES_MANAGER";
            case "quản lý kho": case "inventory_manager": return "INVENTORY_MANAGER";
            case "nhân viên bán hàng": case "sales_staff": return "SALES_STAFF";
            default:
                // Thử trực tiếp như mã vai trò (cho vai trò tùy chỉnh)
                com.dao.RoleDAO roleDAO = new com.dao.RoleDAO();
                com.model.AppRole found = roleDAO.findByCode(t.toUpperCase());
                return found != null ? found.getRoleCode() : null;
        }
    }

    private static Employee.Gender parseGender(String text) {
        if (text == null) return null;
        switch (text.trim().toLowerCase(java.util.Locale.forLanguageTag("vi"))) {
            case "nam": case "male": return Employee.Gender.MALE;
            case "nữ": case "female": return Employee.Gender.FEMALE;
            case "khác": case "other": return Employee.Gender.OTHER;
            default: return null;
        }
    }

    private static java.time.LocalDate parseDate(String text) {
        try {
            return java.time.LocalDate.parse(text.trim(),
                    java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    @Override
    protected void handleFormSaved(Employee item, CrudMode mode) {
        if (mode == CrudMode.ADD) {
            selectedRoleCode = null;
            if (roleFilter != null) {
                roleFilter.resetToAll();
            }
            if (searchBar != null) {
                searchBar.setText("");
            }
            applyFilters();
            return;
        }
        BaseDialog.success(this, "Thành công", "Đã cập nhật " + getEntityLabel());
        onDataChanged();
    }

    private void viewRowPublic(int modelRow) {
        Employee item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        EmployeeDetailDialog dialog = new EmployeeDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    private void editRowPublic(int modelRow) {
        Employee item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private int currentUserId() {
        com.model.User current = AuthService.getInstance().getCurrentUser();
        return current != null ? current.getUserId() : -1;
    }

    private boolean canManage(int modelRow) {
        Employee item = rowToItem(modelRow);
        return item != null && item.getUserId() != currentUserId();
    }

    private boolean isLockedRow(int modelRow) {
        Employee item = rowToItem(modelRow);
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
        Employee item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this, "Khóa tài khoản",
                "Khóa tài khoản \"" + getItemDisplayName(item) + "\"? Nhân viên này sẽ không thể đăng nhập cho tới khi được mở khóa lại.",
                "Khóa tài khoản", AppColor.WARNING, AppColor.WARNING, FontAwesomeSolid.LOCK);
        if (!confirmed) return;
        if (employeeDAO.setLocked(item.getUserId(), true)) {
            com.core.log.ActivityLogHelper.record(getEntityLabel(), com.model.ActivityLog.ACTION_STATUS_CHANGE,
                    "Đã khóa tài khoản \"" + getItemDisplayName(item) + "\"");
            BaseDialog.success(this, "Thành công", "Đã khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể khóa", "Khóa tài khoản thất bại. Vui lòng thử lại.");
        }
    }

    private void unlockRow(int modelRow) {
        Employee item = rowToItem(modelRow);
        if (item == null) return;
        if (employeeDAO.setLocked(item.getUserId(), false)) {
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
        com.dao.RoleDAO roleDAO = new com.dao.RoleDAO();
        com.model.AppRole appRole = roleDAO.findByCode(roleCode);
        return appRole != null ? appRole.getRoleName() : roleCode;
    }

    // Giữ method cũ cho tương thích
    @Deprecated
    private static String roleLabel(Role role) {
        return roleLabelFromCode(role != null ? role.name() : null);
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

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
        }
    }
}