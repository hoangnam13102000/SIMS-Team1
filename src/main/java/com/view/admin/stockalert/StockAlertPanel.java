package com.view.admin.stockalert;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.components.table.AutoRowNumber;
import com.dao.StockAlertDAO;
import com.model.StockAlert;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Trang "Cảnh báo tồn kho" (Quan ly kho) - danh sách các báo cáo hết/sắp
 * hết hàng do NV bán hàng gửi lên (xem sql/StockAlerts_Migration.sql +
 * {@link StockAlertDAO}), để lên kế hoạch nhập hàng bổ sung.
 * <p>
 * Không tạo/sửa/xóa tại đây - báo cáo chỉ được tạo từ phía NV bán hàng
 * (xem nút "Báo hết hàng" ở {@link com.view.admin.product.ProductPanel}),
 * Quan ly kho chỉ xử lý chuyển trạng thái NEW -&gt; PLANNED -&gt; RESOLVED.
 */
public class StockAlertPanel extends BaseCrudPanel<StockAlert> {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();
    private AutoRowNumber stt;

    public StockAlertPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("plan", FontAwesomeSolid.CALENDAR_PLUS, AppColor.ACCENT, "Đã lên kế hoạch nhập bổ sung",
                        this::planRow, this::canPlan)
                .add("resolve", FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS, "Đánh dấu đã xử lý xong",
                        this::resolveRow, this::canResolve));

        stt = table.setAutoRowNumberColumn(0);
        table.setBadgeColumn(3, this::alertTypeLabel, this::alertTypeColor);
        table.setBadgeColumn(8, this::statusLabel, this::statusColor);

        table.setColumnWidths(40, 85, 170, 105, 90, 90, 120, 120, 110);
        table.setColumnMinWidths(36, 70, 130, 90, 75, 75, 100, 105, 95);

        initialLoad();

        // Quan ly kho vao trang nay -> coi nhu da xem het cac bao cao hien
        // co, badge tren sidebar se ve 0 trong lan poll ke tiep (toi da 5s).
        stockAlertDAO.markAllSeen();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCLAMATION_TRIANGLE; }

    @Override
    protected String getPageTitle() { return "Cảnh báo tồn kho"; }

    @Override
    protected String getPageSubtitle() {
        return "Các báo cáo hết/sắp hết hàng từ nhân viên bán hàng - lên kế hoạch nhập hàng bổ sung";
    }

    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                "STT", "Mã SP", "Tên sản phẩm", "Loại cảnh báo", "Tồn khi báo cáo",
                "Tồn tối thiểu", "Người báo cáo", "Thời gian", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(StockAlert item) {
        return new Object[]{
                "",
                item.getProductCode(),
                item.getProductName(),
                item.getAlertType(),
                item.getStockAtReport(),
                item.getMinStock(),
                item.getReportedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{4, 5}; }

    @Override
    protected String getEntityLabel() { return "cảnh báo"; }

    @Override
    protected String getItemDisplayName(StockAlert item) { return item.getProductName(); }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockAlert> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<StockAlert> fetchPage(int page, int pageSize) {
        return stockAlertDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<StockAlert> searchPage(String keyword, int page, int pageSize) {
        return stockAlertDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<StockAlert> fetchAllForExport() {
        return stockAlertDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, mã SP..."; }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return false; }

    @Override
    protected void openForm(StockAlert item) {
        // Khong bao gio duoc goi: getAddButtonLabel() = null va supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(StockAlert item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private boolean canPlan(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        return item != null && "NEW".equals(item.getStatus());
    }

    private boolean canResolve(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        return item != null && !item.isResolved();
    }

    private void planRow(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this,
                "Lên kế hoạch nhập bổ sung",
                "Đánh dấu \"" + item.getProductName() + "\" đã được lên kế hoạch nhập hàng bổ sung?",
                "Xác nhận", AppColor.ACCENT, AppColor.ACCENT, FontAwesomeSolid.CALENDAR_PLUS);
        if (!confirmed) return;

        if (stockAlertDAO.markPlanned(item.getAlertId())) {
            BaseDialog.success(this, "Thành công",
                    "Đã đánh dấu \"" + item.getProductName() + "\" đang được lên kế hoạch nhập bổ sung.");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private void resolveRow(int modelRow) {
        StockAlert item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this,
                "Đánh dấu đã xử lý",
                "Xác nhận \"" + item.getProductName() + "\" đã được nhập hàng bổ sung / không còn cần xử lý?",
                "Xác nhận", AppColor.SUCCESS, AppColor.SUCCESS, FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;

        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        if (stockAlertDAO.resolve(item.getAlertId(), currentUserId)) {
            BaseDialog.success(this, "Thành công", "Đã xử lý xong \"" + item.getProductName() + "\".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private String alertTypeLabel(Object value) {
        return "OUT_OF_STOCK".equalsIgnoreCase(String.valueOf(value)) ? "Hết hàng" : "Sắp hết hàng";
    }

    private Color alertTypeColor(Object value) {
        return "OUT_OF_STOCK".equalsIgnoreCase(String.valueOf(value)) ? AppColor.ERROR : AppColor.WARNING;
    }

    private String statusLabel(Object value) {
        String v = String.valueOf(value);
        switch (v) {
            case "NEW": return "Mới";
            case "PLANNED": return "Đã lên kế hoạch";
            case "RESOLVED": return "Đã xử lý";
            default: return v;
        }
    }

    private Color statusColor(Object value) {
        String v = String.valueOf(value);
        if ("RESOLVED".equals(v)) return AppColor.SUCCESS;
        if ("PLANNED".equals(v)) return AppColor.INFO;
        return AppColor.WARNING; // NEW
    }
}