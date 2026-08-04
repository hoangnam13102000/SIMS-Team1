package com.view.admin.product;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.ProductDAO;
import com.dao.StockAlertDAO;
import com.model.Product;
import com.model.StockAlert;
import com.model.permission.AppPermission;
import com.service.AuthService;
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
    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();
    private AutoRowNumber stt;

    public ProductPanel() {
        super();

        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
                        this::viewRowPublic);

        // "Sửa" và "Ngừng bán/Đang bán" thay đổi dữ liệu sản phẩm nên chỉ hiện
        // cho ai có quyền PRODUCT_MANAGE (Admin). Vai trò chỉ có PRODUCT_VIEW
        // (Sales Manager, Sales Staff) chỉ được xem - xem canManageProducts().
        if (canManageProducts()) {
            actions.add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chỉnh sửa",
                            this::editRowPublic)
                    .add("status-toggle",
                            this::statusToggleIcon,
                            this::statusToggleColor,
                            this::statusToggleTooltip,
                            this::toggleStatusRow,
                            null);
        }

        // Chi NV ban hang (quyen STOCK_ALERT_REPORT) moi thay nut bao het
        // hang - Quan ly kho/Admin xem trang nay khong can bao lai cho
        // chinh minh. Nut bi "khoa xam" (enabledPredicate) o cac SP con du
        // ton, tranh bao trung/bao sai.
        if (AuthService.getInstance().can(AppPermission.STOCK_ALERT_REPORT)) {
            actions.add("report-alert", FontAwesomeSolid.BELL, AppColor.WARNING, "Báo hết/sắp hết hàng",
                    this::reportAlertRow, this::canReportAlert);
        }

        table.setActionColumn(actions);

        stt = table.setAutoRowNumberColumn(0);
        table.setImageColumn(1, 40);
        table.setBadgeColumn(8, this::statusLabel, this::statusColor);

        // Preferred theo tỷ lệ; minWidth đủ badge "Đang bán"/"Ngừng bán" không bị clip.
        // Không enableHorizontalScroll → cột co giãn theo khung, không scrollbar ngang.
        // Text dài (tên SP, danh mục...) nếu tràn sẽ hiện "..." + tooltip full khi hover.
        table.setColumnWidths(45, 52, 85, 160, 110, 95, 95, 70, 105);
        table.setColumnMinWidths(40, 48, 70, 100, 85, 75, 75, 55, 95);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BOX; }

    @Override
    protected String getPageTitle() { return "Quản lý sản phẩm"; }

    @Override
    protected String getPageSubtitle() { return "Quản lý danh sách sản phẩm, giá và tồn kho trong hệ thống"; }

    @Override
    protected String getAddButtonLabel() { return canManageProducts() ? "Thêm sản phẩm" : null; }

    /**
     * true nếu user hiện tại được phép thêm/sửa/ngừng-bán sản phẩm (PRODUCT_MANAGE - Admin).
     * Role chỉ có PRODUCT_VIEW (Sales Manager, Sales Staff) vào được trang này để tra cứu
     * nhưng KHÔNG được sửa dữ liệu - trước đây trang này không phân biệt 2 quyền này nên
     * mọi vai trò vào được trang đều thấy đủ nút Thêm/Sửa/Ngừng bán.
     */
    private boolean canManageProducts() {
        return AuthService.getInstance().can(AppPermission.PRODUCT_MANAGE);
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Ảnh", "Mã SP", "Tên sản phẩm", "Danh mục", "Giá nhập", "Giá bán", "Tồn kho", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Product item) {
        return new Object[]{
                "",
                item.getImageUrl(),
                item.getProductCode(),
                item.getProductName(),
                item.getCategoryName(),
                NumberUtil.formatThousands(item.getImportPrice().longValue()),
                NumberUtil.formatThousands(item.getSellPrice().longValue()),
                item.getStock(),
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{5, 6, 7}; }

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

    /** Không hỗ trợ xóa cứng - dùng "Ngừng bán" trong cột Thao tác hoặc form Sửa thay thế. */
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

    private void viewRowPublic(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ProductDetailDialog dialog = new ProductDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.onEditRequested(() -> openForm(item));
        dialog.setVisible(true);
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

    /** Chỉ cho báo khi SP còn hàng để bán (ACTIVE) và đang hết/sắp hết hàng. */
    private boolean canReportAlert(int modelRow) {
        Product item = rowToItem(modelRow);
        return item != null && item.isActive() && (item.isOutOfStock() || item.isLowStock());
    }

    private void reportAlertRow(int modelRow) {
        Product item = rowToItem(modelRow);
        if (item == null) return;

        String alertType = item.isOutOfStock() ? "OUT_OF_STOCK" : "LOW_STOCK";
        boolean confirmed = BaseDialog.confirm(this,
                "Báo hết/sắp hết hàng",
                (item.isOutOfStock()
                        ? "Báo cho Quản lý kho \"" + item.getProductName() + "\" đã HẾT hàng?"
                        : "Báo cho Quản lý kho \"" + item.getProductName() + "\" SẮP hết hàng (còn "
                                + item.getStock() + "/" + item.getMinStock() + ")?"),
                "Gửi báo cáo", AppColor.WARNING, AppColor.WARNING, FontAwesomeSolid.BELL);
        if (!confirmed) return;

        StockAlert alert = new StockAlert();
        alert.setProductId(item.getProductId());
        alert.setAlertType(alertType);
        alert.setStockAtReport(item.getStock());
        alert.setReportedBy(AuthService.getInstance().getCurrentUser().getUserId());

        if (stockAlertDAO.create(alert)) {
            BaseDialog.success(this, "Đã gửi báo cáo",
                    "Quản lý kho sẽ nhận được thông báo về \"" + item.getProductName() + "\".");
        } else {
            BaseDialog.error(this, "Không thể gửi báo cáo",
                    "Sản phẩm này đã có báo cáo đang chờ xử lý, hoặc có lỗi hệ thống. Vui lòng thử lại.");
        }
    }

    private String statusLabel(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? "Ngừng bán" : "Đang bán";
    }

    private Color statusColor(Object value) {
        return "DISABLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }
}