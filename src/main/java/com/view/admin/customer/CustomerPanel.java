package com.view.admin.customer;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.crud.TrashConfig;
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
                .add("delete", FontAwesomeSolid.TRASH_ALT, AppColor.ERROR, "Xóa khách hàng",
                        this::deleteRowPublic));

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

    /**
     * Cot Thao tac o day la ActionColumn tu bien soan rieng (xem constructor) -
     * nut "Xoa" that su nam trong slot "delete" o do va goi thang
     * deleteRowPublic(), khong di qua co che view/edit/delete mac dinh cua
     * BaseCrudPanel. Tra ve false o day chi de an cot xoa mac dinh (rong,
     * khong dung toi); deleteItem() van duoc trien khai day du (xoa MEM qua
     * CustomerDAO.softDelete) de dung chung logic voi deleteRowPublic().
     */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Customer item) {
        return customerDAO.softDelete(item.getCustomerId());
    }

    /**
     * Bat tinh nang "Thung rac" (xoa mem/khoi phuc) cho khach hang - khong
     * cho xoa vinh vien (tham so hardDelete = null) vi Users.UserID con duoc
     * nhieu bang khac tham chieu (Orders, ActivityLogs...) khong CASCADE,
     * xoa that co the vi pham khoa ngoai. Nut "Thung rac" tu dong xuat hien
     * tren header (xem BaseCrudPanel.maybeAddTrashButton()).
     */
    @Override
    protected TrashConfig<Customer> getTrashConfig() {
        return new TrashConfig<>(
                customerDAO::getDeletedItems,
                item -> customerDAO.restore(item.getCustomerId()));
    }

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
     * khi Sua/Xoa thanh cong (giong UserAccountPanel). Rieng Khoi phuc trong
     * Thung rac da tu goi onChanged() -> reload() qua TrashDialog/openTrash().
     */
    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Hành động: xem / sửa / xóa mềm
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

    private void deleteRowPublic(int modelRow) {
        Customer item = rowToItem(modelRow);
        if (item == null) return;

        boolean confirmed = BaseDialog.confirmDelete(this, getEntityLabel(), getItemDisplayName(item));
        if (!confirmed) return;

        if (deleteItem(item)) {
            BaseDialog.success(this, "Thành công",
                    "Đã xóa " + getEntityLabel() + " \"" + getItemDisplayName(item) + "\". Có thể khôi phục trong Thùng rác.");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể xóa", "Xóa thất bại. Vui lòng thử lại.");
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