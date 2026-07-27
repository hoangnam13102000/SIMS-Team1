package com.view.admin.account;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;


public class UserAccountPanel extends BaseCrudPanel<User> {

    private final UserDAO userDAO = new UserDAO();
    private AutoRowNumber stt;

    public UserAccountPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                .add("lock-toggle",
                        this::lockToggleIcon,
                        this::lockToggleColor,
                        this::lockToggleTooltip,
                        this::toggleLockRow,
                        row -> canManage(row)));

        stt = table.setAutoRowNumberColumn(0);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);
        table.setBadgeColumn(6, this::lockLabel, this::lockColor);

        // Dat do rong ro rang cho tung cot - neu khong JTable se tu chia deu
        // theo do rong khung nhin, khien cac tieu de dai (vd "Tên đăng nhập",
        // "Họ và tên") bi cat thanh "..." (giong da sua o CustomerPanel).
        table.setColumnWidths(50, 115, 115, 180, 150, 130, 115);
        table.setColumnMinWidths(45, 90, 85, 90, 110, 100, 95);

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
        return new String[]{"STT", "Tên đăng nhập", "Họ và tên", "Email", "Vai trò", "Trạng thái", "Khóa"};
    }

    @Override
    protected Object[] mapRowToColumns(User item) {
        return new Object[]{
                "",
                item.getUsername(),
                item.getFullName(),
                item.getEmail(),
                roleLabel(item.getRole()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    @Override
    protected String getEntityLabel() { return "tài khoản"; }

    /** STT phải tính theo đúng trang đang xem, không luôn bắt đầu lại từ 1 (giống CustomerPanel). */
    @Override
    protected void afterRender(PaginationHelper.PaginationResult<User> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

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
     * Goi y autocomplete gom ho ten VA ten dang nhap cua toan bo tai khoan -
     * khop voi 2 tieu chi chinh trong placeholder tim kiem o tren. Duoc
     * BaseCrudPanel goi tren 1 background thread (SwingWorker) nen truy van
     * userDAO.getAll() (blocking) o day an toan, khong lam treo UI.
     */
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
        return new ArrayList<>(new LinkedHashSet<>(names)); // loai trung, giu thu tu
    }

    /**
     * Chua co noi nao trong app publish DataChangedEvent cho Users (chi co
     * hang so ORDER/PHONE), nen co che AutoRefresher (bind san trong
     * BaseCrudPanel) khong bao gio tu kich hoat cho panel nay. Override truc
     * tiep goi reload() de bang luon phan anh dung du lieu ngay sau khi
     * Them/Sua/Khoa/Mo khoa thanh cong.
     */
    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Hành động: sửa / khóa / mở khóa
    // ---------------------------------------------------------------

    private void editRowPublic(int modelRow) {
        User item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private int currentUserId() {
        User current = AuthService.getInstance().getCurrentUser();
        return current != null ? current.getUserId() : -1;
    }

    /** Không cho Admin tự khóa chính tài khoản đang đăng nhập - tránh tự khóa mình ngoài hệ thống. */
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