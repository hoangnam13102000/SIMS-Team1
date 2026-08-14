package com.view.admin.employee;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.FilterDropdown;
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
    private FilterDropdown<RoleOption> roleFilter;
    private JLabel clearFiltersLink;
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

        // Cột "Mã nhân viên" (index 0): thêm icon copy
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
        
        // Xử lý click vào icon copy mã nhân viên
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã nhân viên
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

    /** Option cho FilterDropdown vai trò - value null = "Tất cả vai trò". */
    private static final class RoleOption {
        final Role role;
        final String label;
        RoleOption(Role role, String label) {
            this.role = role;
            this.label = label;
        }
        @Override
        public String toString() { return label; }
    }

    private void setupRoleFilter() {
        RoleOption[] roleOptions = new RoleOption[]{
                new RoleOption(null, "Tất cả vai trò"),
                new RoleOption(Role.ADMIN, roleLabel(Role.ADMIN)),
                new RoleOption(Role.SALES_MANAGER, roleLabel(Role.SALES_MANAGER)),
                new RoleOption(Role.INVENTORY_MANAGER, roleLabel(Role.INVENTORY_MANAGER)),
                new RoleOption(Role.SALES_STAFF, roleLabel(Role.SALES_STAFF))
        };

        roleFilter = new FilterDropdown<>(FontAwesomeSolid.USER_CIRCLE, roleOptions);
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
    }

    private void onFilterChanged() {
        RoleOption opt = roleFilter.getSelected();
        selectedRole = opt == null ? null : opt.role;
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
                roleFilter.resetToAll();
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

    // ---------------------------------------------------------------
    // Helper: copy mã nhân viên vào clipboard
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