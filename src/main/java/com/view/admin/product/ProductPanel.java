package com.view.admin.product;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.ProductDAO;
import com.model.Product;
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

public class ProductPanel extends BaseCrudPanel<Product> {

    private final ProductDAO productDAO = new ProductDAO();
    private AutoRowNumber stt;

    public ProductPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                        this::editRowPublic)
                .add("status-toggle",
                        this::statusToggleIcon,
                        this::statusToggleColor,
                        this::statusToggleTooltip,
                        this::toggleStatusRow,
                        null));

        stt = table.setAutoRowNumberColumn(0);
        table.setImageColumn(1, 40);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);

        table.setColumnWidths(60, 60, 220, 140, 120, 120, 90, 120);
        table.setColumnMinWidths(55, 55, 160, 110, 100, 100, 80, 105);
        table.enableHorizontalScroll();

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BOX; }

    @Override
    protected String getPageTitle() { return "Quản lý sản phẩm"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý danh sách sản phẩm, giá và tồn kho trong hệ thống"; }

    @Override
    protected String getAddButtonLabel() { return "Thêm sản phẩm"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Ảnh", "Tên sản phẩm", "Danh mục", "Giá nhập", "Giá bán", "Tồn kho", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Product item) {
        return new Object[]{
                "",
                item.getImageUrl(),
                item.getProductName(),
                item.getCategoryName(),
                NumberUtil.formatThousands(item.getImportPrice().longValue()),
                NumberUtil.formatThousands(item.getSellPrice().longValue()),
                item.getStock(),
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{4, 5, 6}; }

    @Override
    protected String getEntityLabel() { return "sản phẩm"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Product> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Product item) {
        return item.getProductName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Product> fetchPage(int page, int pageSize) {
        return productDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Product> searchPage(String keyword, int page, int pageSize) {
        return productDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<Product> fetchAllForExport() {
        return productDAO.getAll();
    }

    @Override
    protected void openForm(Product item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductFormDialog dialog = new ProductFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, productDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    /** Khong ho tro xoa cung - dung "Ngung ban" trong cot Thao tac hoac form Sua thay the. */
    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean deleteItem(Product item) { return false; }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, danh mục..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Product p : productDAO.getAll()) {
            if (p.getProductName() != null && !p.getProductName().isBlank()) {
                names.add(p.getProductName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private void editRowPublic(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item != null) openForm(item);
    }

    private boolean isDisabledRow(int modelRow) {
        Product item = rowToItem(modelRow);
        return item != null && !item.isActive();
    }

    private FontAwesomeSolid statusToggleIcon(int modelRow) {
        return isDisabledRow(modelRow) ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.BAN;
    }

    private Color statusToggleColor(int modelRow) {
        return isDisabledRow(modelRow) ? AppColor.SUCCESS : AppColor.WARNING;
    }

    private String statusToggleTooltip(int modelRow) {
        return isDisabledRow(modelRow) ? "Mở bán lại" : "Ngừng bán";
    }

    private void toggleStatusRow(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;

        boolean willDisable = item.isActive();
        boolean confirmed = BaseDialog.confirm(this,
                willDisable ? "Ngừng bán sản phẩm" : "Mở bán lại sản phẩm",
                (willDisable
                        ? "Ngừng bán \"" + item.getProductName() + "\"? Sản phẩm sẽ không còn hiển thị cho khách hàng."
                        : "Mở bán lại \"" + item.getProductName() + "\"? Sản phẩm sẽ hiển thị lại cho khách hàng."),
                willDisable ? "Ngừng bán" : "Mở bán lại",
                willDisable ? AppColor.WARNING : AppColor.SUCCESS,
                willDisable ? AppColor.WARNING : AppColor.SUCCESS,
                willDisable ? FontAwesomeSolid.BAN : FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;

        item.setStatus(willDisable ? "DISABLED" : "ACTIVE");
        if (productDAO.update(item)) {
            BaseDialog.success(this, "Thành công",
                    willDisable ? "Đã ngừng bán \"" + item.getProductName() + "\"." : "Đã mở bán lại \"" + item.getProductName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private String statusLabel(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? "Ngừng bán" : "Đang bán";
    }

    private Color statusColor(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }
}