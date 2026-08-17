package com.view.admin.customer;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.crud.TrashConfig;
import com.components.table.ActionColumn;
import com.dao.CustomerDAO;
import com.model.Customer;
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

public class CustomerPanel extends BaseCrudPanel<Customer> {

    private final CustomerDAO customerDAO = new CustomerDAO();

    public CustomerPanel() {
        super();

        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic);
        if (canEditCustomers()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                    this::editRowPublic);
        }
        if (canManageCustomers()) {
            actions.add("delete", FontAwesomeSolid.TRASH_ALT, AppColor.ERROR, "Xóa khách hàng",
                    this::deleteRowPublic);
        }
        table.setActionColumn(actions);

        // Không STT / Điểm TV / Khóa — nhường chỗ cho Mã KH, Đăng nhập, Họ tên, Email, SĐT
        // để hiện full text (không "CUS_00...", không "gmail..."). Không scroll ngang.
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Mã KH ~ CUS_0009 (9 ký tự), Email dài nhất → ưu tiên width.
        table.setColumnWidths(110, 130, 160, 240, 120, 130);
        table.setColumnMinWidths(100, 110, 130, 200, 105, 120);

        // Cột "Mã KH" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã khách hàng: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã khách hàng
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã KH
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(CustomerPanel.this, "Copy thành công", "Đã copy mã khách hàng: " + text);
                    }
                }
            }
        });

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

    /** Xoá mềm / thùng rác — CUSTOMER_MANAGE. */
    private boolean canManageCustomers() {
        return AuthService.getInstance().can(AppPermission.CUSTOMER_MANAGE);
    }

    /** Sửa — CUSTOMER_MANAGE hoặc CUSTOMER_EDIT. */
    private boolean canEditCustomers() {
        return AuthService.getInstance().can(AppPermission.CUSTOMER_MANAGE)
                || AuthService.getInstance().can(AppPermission.CUSTOMER_EDIT);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã KH", "Đăng nhập", "Họ tên", "Email", "SĐT", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Customer item) {
        return new Object[]{
                item.getCustomerCode(),
                item.getUsername(),
                item.getFullName(),
                item.getEmail(),
                item.getPhone(),
                item.getStatus()
        };
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
        if (!canManageCustomers()) {
            return null;
        }
        return new TrashConfig<>(
                customerDAO::getDeletedItems,
                item -> customerDAO.restore(item.getCustomerId()));
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã KH, tên đăng nhập, họ tên, email..."; }

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
            if (c.getCustomerCode() != null && !c.getCustomerCode().isBlank()) {
                names.add(c.getCustomerCode());
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

    // ---------------------------------------------------------------
    // Helper: copy mã khách hàng vào clipboard
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