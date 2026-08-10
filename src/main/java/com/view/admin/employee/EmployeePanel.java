package com.view.admin.employee;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.EmployeeDAO;
import com.model.Employee;
import com.model.Role;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.JComboBox;
import javax.swing.BorderFactory;

/**
 * Trang Quản lý nhân viên - dựa trên bảng Employees (kế thừa Users, xem
 * {@link com.model.Employee}), KHÔNG còn nhét thông tin nhân viên vào bảng
 * Users như trước. Thêm nhân viên KHÔNG cần Admin nhập username/mật khẩu:
 * mã nhân viên (EmployeeID - UUID), username và mật khẩu ban đầu đều do hệ
 * thống tự sinh (xem {@link EmployeeDAO#createEmployee}), mật khẩu được gửi
 * qua email nhân viên chứ không hiển thị/lưu lại trong ứng dụng.
 */
public class EmployeePanel extends BaseCrudPanel<Employee> {

    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private JComboBox<String> roleFilter;
    private Role selectedRole;

    public EmployeePanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic)
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                .add("lock-toggle",
                        this::lockToggleIcon,
                        this::lockToggleColor,
                        this::lockToggleTooltip,
                        this::toggleLockRow,
                        row -> canManage(row)));

        table.setBadgeColumn(5, this::statusLabel, this::statusColor);
        table.setBadgeColumn(6, this::lockLabel, this::lockColor);

        // Preferred theo tỷ lệ; minWidth đủ cho badge "Đang hoạt động" / "Bình thường"
        // không bị clip. Không enableHorizontalScroll → cột co giãn theo khung,
        // không scrollbar ngang. Text dài (mã NV, email...) nếu vẫn tràn sẽ hiện
        // "..." + tooltip full khi hover (BaseTable striped renderer).
        table.setColumnWidths(110, 110, 110, 150, 120, 145, 115);
        table.setColumnMinWidths(85, 85, 90, 110, 95, 140, 110);

        setupRoleFilter();
        initialLoad();
    }

    // ---------------------------------------------------------------
    // Cấu hình BaseCrudPanel
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.USER_TIE; }

    @Override
    protected String getPageTitle() { return "Quản lý nhân viên"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý hồ sơ nhân viên trong hệ thống (không bao gồm khách hàng)"; }

    @Override
    protected String getAddButtonLabel() { return "Thêm nhân viên"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã nhân viên", "Họ và tên", "Tên đăng nhập", "Email", "Vai trò", "Trạng thái", "Khóa"};
    }

    @Override
    protected Object[] mapRowToColumns(Employee item) {
        return new Object[]{
                item.getEmployeeId(),
                item.getFullName(),
                item.getUsername(),
                item.getEmail(),
                roleLabel(item.getRole()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    @Override
    protected String getEntityLabel() { return "nhân viên"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Employee> result) {
        // Không còn cột STT nên không cần cập nhật pageOffset nữa.
    }

    @Override
    protected String getItemDisplayName(Employee item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    @Override
    protected PaginationHelper.PaginationResult<Employee> fetchPage(int page, int pageSize) {
        String keyword = searchBar != null && searchBar.getText() != null ? searchBar.getText().trim() : "";
        return employeeDAO.filterByRole(keyword, selectedRole, page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Employee> searchPage(String keyword, int page, int pageSize) {
        return employeeDAO.filterByRole(keyword, selectedRole, page, pageSize);
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

    /** Không hỗ trợ xóa cứng - dùng "Vô hiệu hóa" trong form Sửa thay thế, giống UserAccountPanel/CustomerPanel. */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Employee item) { return false; }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã NV, tên đăng nhập, họ tên, email..."; }

    private void setupRoleFilter() {
        roleFilter = new JComboBox<>(new String[]{"Tất cả vai trò", "Quản trị viên", "Quản lý bán hàng", "Quản lý kho", "Nhân viên bán hàng"});
        roleFilter.setFont(new java.awt.Font("Segoe UI", java.awt.Font.PLAIN, 13));
        roleFilter.setBackground(AppColor.WHITE);
        roleFilter.setForeground(AppColor.TEXT_PRIMARY);
        roleFilter.setPreferredSize(new Dimension(190, 38));
        roleFilter.setFocusable(false);
        roleFilter.setToolTipText("Lọc danh sách theo vai trò");
        roleFilter.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        roleFilter.addActionListener(e -> {
            selectedRole = roleFromFilterIndex(roleFilter.getSelectedIndex());
            applyFilters();
        });
        addToolbarFilter(roleFilter);
    }

    private Role roleFromFilterIndex(int index) {
        switch (index) {
            case 1: return Role.ADMIN;
            case 2: return Role.SALES_MANAGER;
            case 3: return Role.INVENTORY_MANAGER;
            case 4: return Role.SALES_STAFF;
            
            default: return null;
        }
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
    protected boolean supportsImport() { return true; }

    @Override
    protected String[] getImportColumns() {
        return new String[]{"Họ và tên", "Email", "Số điện thoại", "Vai trò", "Ngày sinh", "Giới tính", "Lương", "Ngày vào làm"};
    }

    @Override
    protected String getImportInstructions() {
        return "Vai trò nhận 1 trong các giá trị: Quản trị viên, Quản lý bán hàng, Quản lý kho, Nhân viên bán hàng. "
                + "Ngày sinh/Ngày vào làm theo định dạng dd/MM/yyyy. Giới tính: Nam, Nữ, Khác (có thể để trống). "
                + "Số điện thoại, ngày sinh, giới tính, lương có thể để trống. Username và mật khẩu sẽ được hệ thống tự sinh và gửi qua email.";
    }

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

        Role role = parseRole(roleText);
        if (role == null) {
            return com.importer.ImportRowResult.failure("vai trò \"" + roleText
                    + "\" không hợp lệ (Quản trị viên, Quản lý bán hàng, Quản lý kho, Nhân viên bán hàng).");
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
        employee.setRole(role);
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

    private static Role parseRole(String text) {
        if (text == null) return null;
        switch (text.trim().toLowerCase(java.util.Locale.forLanguageTag("vi"))) {
            case "quản trị viên": case "admin": return Role.ADMIN;
            case "quản lý bán hàng": case "sales_manager": return Role.SALES_MANAGER;
            case "quản lý kho": case "inventory_manager": return Role.INVENTORY_MANAGER;
            case "nhân viên bán hàng": case "sales_staff": return Role.SALES_STAFF;
            default: return null;
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

    /**
     * Giống UserAccountPanel/CustomerPanel: chưa có nơi nào publish DataChangedEvent
     * cho Users/Employees nên reload() trực tiếp sau mỗi thao tác.
     */
    @Override
    protected void onDataChanged() {
        reload();
    }

    /**
     * Sau khi thêm nhân viên mới:
     * <ul>
     *   <li>Không hiện dialog "Đã thêm nhân viên mới" (EmployeeFormDialog đã báo
     *       chi tiết mã NV / username / mật khẩu-email).</li>
     *   <li>Xóa bộ lọc vai trò + ô tìm kiếm và về trang 1 — vì danh sách ORDER BY
     *       UserID DESC, nhân viên vừa tạo luôn nằm trang đầu. Nếu giữ nguyên
     *       trang/filter hiện tại thì bảng trông như "không auto-refresh".</li>
     * </ul>
     * Khi sửa: giữ hành vi mặc định (thông báo + reload trang hiện tại).
     */
    @Override
    protected void handleFormSaved(Employee item, CrudMode mode) {
        if (mode == CrudMode.ADD) {
            selectedRole = null;
            if (roleFilter != null) {
                roleFilter.setSelectedIndex(0);
            }
            if (searchBar != null) {
                searchBar.setText("");
            }
            // applyFilters() luôn load trang 1 (không giữ page cũ)
            applyFilters();
            return;
        }
        BaseDialog.success(this, "Thành công", "Đã cập nhật " + getEntityLabel());
        onDataChanged();
    }

    // ---------------------------------------------------------------
    // Hành động: sửa / khóa / mở khóa
    // ---------------------------------------------------------------

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

    /** Không cho Admin tự khóa chính tài khoản đang đăng nhập. */
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

    // ---------------------------------------------------------------
    // Nhãn / màu hiển thị
    // ---------------------------------------------------------------

    private static String roleLabel(Role role) {
        switch (role) {
            case ADMIN: return "Quản trị viên";
            case SALES_MANAGER: return "Quản lý bán hàng";
            case INVENTORY_MANAGER: return "Quản lý kho";
            case SALES_STAFF: return "Nhân viên bán hàng";
            case CUSTOMER: return "Khách hàng";
            default: return role.name();
        }
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