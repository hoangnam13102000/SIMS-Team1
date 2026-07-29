package com.view.admin.employee;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
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
    private AutoRowNumber stt;

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

        stt = table.setAutoRowNumberColumn(0);
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);
        table.setBadgeColumn(7, this::lockLabel, this::lockColor);

        // Preferred theo tỷ lệ; minWidth đủ cho badge "Đang hoạt động" / "Bình thường"
        // không bị clip. Không enableHorizontalScroll → cột co giãn theo khung,
        // không scrollbar ngang. Text dài (mã NV, email...) nếu vẫn tràn sẽ hiện
        // "..." + tooltip full khi hover (BaseTable striped renderer).
        table.setColumnWidths(45, 110, 110, 110, 150, 120, 145, 115);
        table.setColumnMinWidths(40, 85, 85, 90, 110, 95, 140, 110);

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
        return new String[]{"STT", "Mã nhân viên", "Họ và tên", "Tên đăng nhập", "Email", "Vai trò", "Trạng thái", "Khóa"};
    }

    @Override
    protected Object[] mapRowToColumns(Employee item) {
        return new Object[]{
                "",
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
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Employee item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    @Override
    protected PaginationHelper.PaginationResult<Employee> fetchPage(int page, int pageSize) {
        return employeeDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Employee> searchPage(String keyword, int page, int pageSize) {
        return employeeDAO.search(keyword, page, pageSize);
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
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    /** Giống UserAccountPanel/CustomerPanel: chưa có nơi nào publish DataChangedEvent cho Users nên reload() trực tiếp sau mỗi thao tác. */
    @Override
    protected void onDataChanged() {
        reload();
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