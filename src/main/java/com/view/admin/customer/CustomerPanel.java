package com.view.admin.customer;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.CustomerDAO;
import com.model.Customer;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;


public class CustomerPanel extends BaseCrudPanel<Customer> {

    private final CustomerDAO customerDAO = new CustomerDAO();
    private AutoRowNumber stt;

    public CustomerPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic)
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                // Khoa/Mo khoa gop chung 1 slot, giong UserAccountPanel - icon/mau/
                // tooltip doi theo trang thai IsLocked cua tung dong. Overload nay
                // (icon/mau/tooltip la ham theo modelRow) BAT BUOC 6 tham so, tham
                // so cuoi (enabledPredicate) truyen null => luon cho phep bam
                // (khac UserAccountPanel dung canManage() de tu-chan-khoa-chinh-minh,
                // khong can o day vi Admin khong nam trong danh sach Customer).
                .add("lock-toggle",
                        this::lockToggleIcon,
                        this::lockToggleColor,
                        this::lockToggleTooltip,
                        this::toggleLockRow,
                        null));

        stt = table.setAutoRowNumberColumn(0);
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);
        table.setBadgeColumn(7, this::lockLabel, this::lockColor);

        // Dat do rong "ua thich" theo ty le hop ly cho tung cot (STT/Trang
        // thai/Khoa nho gon, Ho ten/Email rong hon) - ket hop voi hanh vi mac
        // dinh cua JTable (AUTO_RESIZE_SUBSEQUENT_COLUMNS, khong bat
        // enableHorizontalScroll() o day) se tu co gian ty le theo dung do
        // rong khung nhin hien tai, tranh phai cuon ngang moi thay het cac cot.
        table.setColumnWidths(55, 130, 85, 130, 80, 95, 115, 110);
        table.setColumnMinWidths(48, 100, 60, 90, 65, 75, 90, 95);

        initialLoad();
    }

    // ---------------------------------------------------------------
    // Cấu hình BaseCrudPanel
    // ---------------------------------------------------------------

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.ID_CARD; }

    @Override
    protected String getPageTitle() { return "Quản lý khách hàng"; }

    @Override
    protected String getPageSubtitle() { return "Xem và quản lý thông tin, điểm thành viên của khách hàng"; }

    /** Khach hang tu dang ky qua RegisterFrame - khong tao moi tu trang quan tri nay. */
    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
    	return new String[]{"STT", "Đăng nhập", "Họ tên", "Email", "SĐT", "Điểm TV", "Trạng thái", "Khóa"};
    }

    @Override
    protected Object[] mapRowToColumns(Customer item) {
        return new Object[]{
                "",
                item.getUsername(),
                item.getFullName(),
                item.getEmail(),
                item.getPhone(),
                NumberUtil.formatThousands(item.getMemberPoint()),
                item.getStatus(),
                item.isLocked() ? "LOCKED" : "NORMAL"
        };
    }

    /** Cột "Điểm thành viên" (chỉ số 5) đã format bằng NumberUtil - sort theo giá trị số thay vì chữ cái. */
    @Override
    protected int[] numericColumns() { return new int[]{5}; }

    /** STT phải tính theo đúng trang đang xem (vd trang 2, 10 dòng/trang thì
     *  dòng đầu là STT 11) chứ không luôn bắt đầu lại từ 1. */
    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Customer> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getEntityLabel() { return "khách hàng"; }

    @Override
    protected String getItemDisplayName(Customer item) {
        return item.getFullName() + " (" + item.getUsername() + ")";
    }

    @Override
    protected PaginationHelper.PaginationResult<Customer> fetchPage(int page, int pageSize) {
        return customerDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Customer> searchPage(String keyword, int page, int pageSize) {
        return customerDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<Customer> fetchAllForExport() {
        return customerDAO.getAll();
    }

    @Override
    protected void openForm(Customer item) {
        if (item == null) return; // khong ho tro them moi - xem getAddButtonLabel()
        Window owner = SwingUtilities.getWindowAncestor(this);
        CustomerFormDialog dialog = new CustomerFormDialog(
                owner instanceof Frame ? (Frame) owner : null, CrudMode.EDIT, item, customerDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    /** Khong ho tro xoa cung - dung "Vo hieu hoa" trong form Sua thay the (giong UserAccountPanel). */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Customer item) { return false; }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên đăng nhập, họ tên, email..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Customer c : customerDAO.getAll()) {
            if (c.getFullName() != null && !c.getFullName().isBlank()) {
                names.add(c.getFullName());
            }
            if (c.getUsername() != null && !c.getUsername().isBlank()) {
                names.add(c.getUsername());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names)); // loai trung, giu thu tu
    }

    /**
     * Chua co noi nao trong app publish DataChangedEvent cho Customers, nen
     * override truc tiep goi reload() de bang phan anh ngay du lieu moi sau
     * khi Sua/Khoa/Mo khoa thanh cong (giong UserAccountPanel).
     */
    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Hành động: sửa / khóa / mở khóa
    // ---------------------------------------------------------------

    private void viewRowPublic(int modelRow) {
        Customer item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        CustomerDetailDialog dialog = new CustomerDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    private void editRowPublic(int modelRow) {
        Customer item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private boolean isLockedRow(int modelRow) {
        Customer item = rowToItem(modelRow);
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
        Customer item = rowToItem(modelRow);
        if (item == null) return;

        boolean confirmed = BaseDialog.confirm(this, "Khóa tài khoản",
                "Khóa tài khoản \"" + getItemDisplayName(item) + "\"? Khách hàng này sẽ không thể đăng nhập cho tới khi được mở khóa lại.",
                "Khóa tài khoản", AppColor.WARNING, AppColor.WARNING, FontAwesomeSolid.LOCK);
        if (!confirmed) return;

        if (customerDAO.setLocked(item.getCustomerId(), true)) {
            BaseDialog.success(this, "Thành công", "Đã khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể khóa", "Khóa tài khoản thất bại. Vui lòng thử lại.");
        }
    }

    private void unlockRow(int modelRow) {
        Customer item = rowToItem(modelRow);
        if (item == null) return;

        if (customerDAO.setLocked(item.getCustomerId(), false)) {
            BaseDialog.success(this, "Thành công", "Đã mở khóa tài khoản \"" + getItemDisplayName(item) + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể mở khóa", "Mở khóa tài khoản thất bại. Vui lòng thử lại.");
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

    private String lockLabel(Object value) {
        return "LOCKED".equals(value) ? "Đang khóa" : "Bình thường";
    }

    private Color lockColor(Object value) {
        return "LOCKED".equals(value) ? AppColor.ERROR : AppColor.TEXT_MUTED;
    }
}