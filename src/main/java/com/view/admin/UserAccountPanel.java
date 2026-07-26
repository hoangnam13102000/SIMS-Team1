package com.view.admin;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.dao.UserDAO;
import com.model.Role;
import com.model.User;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import javax.swing.SwingUtilities;

/**
 * Trang "Quản lý tài khoản" trong khu vực quản trị (Admin) - CRUD tài khoản
 * người dùng: thêm mới (chọn vai trò + mật khẩu ban đầu), sửa thông tin/vai
 * trò/trạng thái, khóa/mở khóa và đặt lại mật khẩu.
 * <p>
 * Không hỗ trợ xóa vĩnh viễn tài khoản (tương tự R3 - không xóa cứng dữ liệu
 * có thể liên quan tới giao dịch/log khác) - dùng "Vô hiệu hóa" (Status =
 * DISABLED) trong form Sửa để ngừng cho tài khoản đăng nhập thay vì xóa.
 */
public class UserAccountPanel extends BaseCrudPanel<User> {

    private final UserDAO userDAO = new UserDAO();

    public UserAccountPanel() {
        super();

        // Bo sung 2 nut hanh dong rieng (Khoa / Mo khoa / Dat lai mat khau)
        // ben canh nut "Sua" mac dinh da duoc BaseCrudPanel gan san trong
        // constructor cha (enableActions(supportsView/Edit/Delete)).
        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                // Khoa/Mo khoa gop chung 1 slot - icon/mau/tooltip doi theo
                // trang thai IsLocked cua tung dong thay vi 2 nut rieng luon
                // hien du (1 nut luon mo/xam) gay roi mat cho cot Thao tac.
                .add("lock-toggle",
                        this::lockToggleIcon,
                        this::lockToggleColor,
                        this::lockToggleTooltip,
                        this::toggleLockRow,
                        row -> canManage(row))
                .add("reset-password", FontAwesomeSolid.KEY, AppColor.INFO, "Đặt lại mật khẩu",
                        this::resetPasswordRow, row -> canManage(row)));

        table.setImageColumn(0, 36);
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);
        table.setBadgeColumn(7, this::lockLabel, this::lockColor);

        initialLoad();
    }

    // ---------------------------------------------------------------
    // Cấu hình BaseCrudPanel
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.USERS_COG; }

    @Override
    protected String getPageTitle() { return "Quản lý tài khoản"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý tài khoản người dùng và phân quyền trong hệ thống"; }

    @Override
    protected String getAddButtonLabel() { return "Thêm tài khoản"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"", "Tên đăng nhập", "Họ và tên", "Email", "Số điện thoại", "Vai trò", "Trạng thái", "Khóa"};
    }

    @Override
    protected Object[] mapRowToColumns(User item) {
        return new Object[]{
                item.getAvatarUrl(),
                item.getUsername(),
                item.getFullName(),
                item.getEmail(),
                item.getPhone(),
                roleLabel(item.getRole()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    @Override
    protected String getEntityLabel() { return "tài khoản"; }

    @Override
    protected String getItemDisplayName(User item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    @Override
    protected PaginationHelper.PaginationResult<User> fetchPage(int page, int pageSize) {
        return userDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<User> searchPage(String keyword, int page, int pageSize) {
        return userDAO.search(keyword, page, pageSize);
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

    /** Không hỗ trợ xóa cứng - dùng "Vô hiệu hóa" trong form Sửa thay thế. */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(User item) { return false; }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên đăng nhập, họ tên, email..."; }

    /**
     * Chua co noi nao trong app publish DataChangedEvent cho Users (chi co
     * hang so ORDER/PHONE), nen co che AutoRefresher (bind san trong
     * BaseCrudPanel) khong bao gio tu kich hoat cho panel nay. Override truc
     * tiep goi reload() de bang luon phan anh dung du lieu ngay sau khi
     * Them/Sua/Khoa/Mo khoa/Dat lai mat khau thanh cong.
     */
    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Hành động: sửa / khóa / mở khóa / đặt lại mật khẩu
    // ---------------------------------------------------------------

    private void editRowPublic(int modelRow) {
        User item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private int currentUserId() {
        User current = AuthService.getInstance().getCurrentUser();
        return current != null ? current.getUserId() : -1;
    }

    /** Không cho Admin tự khóa/đặt lại mật khẩu chính tài khoản đang đăng nhập - tránh tự khóa mình ngoài hệ thống. */
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
            BaseDialog.success(this, "Thành công", "Đã mở khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể mở khóa", "Mở khóa tài khoản thất bại. Vui lòng thử lại.");
        }
    }

    private void resetPasswordRow(int modelRow) {
        User item = rowToItem(modelRow);
        if (item == null) return;

        String newPassword = BaseDialog.inputText(this, "Đặt lại mật khẩu",
                "Mật khẩu mới cho \"" + getItemDisplayName(item) + "\" (tối thiểu 6 ký tự)", "", "Đặt lại");
        if (newPassword == null) return; // nguoi dung bam Huy

        if (newPassword.trim().length() < 6) {
            BaseDialog.error(this, "Không hợp lệ", "Mật khẩu phải có ít nhất 6 ký tự.");
            return;
        }

        if (userDAO.resetPassword(item.getUserId(), newPassword.trim())) {
            BaseDialog.success(this, "Thành công", "Đã đặt lại mật khẩu cho \"" + getItemDisplayName(item) + "\".");
        } else {
            BaseDialog.error(this, "Không thể đặt lại mật khẩu", "Vui lòng thử lại.");
        }
    }

    // ---------------------------------------------------------------
    // Nhãn / màu hiển thị
    // ---------------------------------------------------------------

    /**
     * Truoc day dung mang ROLE_LABELS lap chi so theo Role.ordinal() - de vo
     * dong bo khi enum Role them/bot gia tri (vd CUSTOMER dung ordinal 4
     * nhung mang chi co 4 phan tu -> ArrayIndexOutOfBoundsException khi
     * render bang). Doi sang switch tren ten enum de khong bao gio crash du
     * enum co thay doi sau nay, va luon co gia tri fallback ro rang.
     */
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