package com.view.admin.exceptionreport;

import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.ExceptionReportDAO;
import com.model.ExceptionReport;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Trang "Báo cáo ngoại lệ" - NV bán hàng gửi báo cáo tự do cho Quản lý
 * bán hàng về các tình huống bất thường không thuộc luồng nghiệp vụ có
 * sẵn (sản phẩm khách cần mua nhưng chưa có trong hệ thống, khách yêu cầu
 * sản phẩm đặc biệt, v.v. - xem sql/SIMS.sql bảng ExceptionReports).
 * <p>
 * NV bán hàng (quyền {@link AppPermission#EXCEPTION_REPORT_CREATE}) chỉ
 * thấy nút "Gửi báo cáo". Quản lý bán hàng (quyền
 * {@link AppPermission#EXCEPTION_REPORT_HANDLE}) thấy nút hành động "Đánh
 * dấu đã xử lý" ở các báo cáo đang PENDING. Không có tác động đến kho/hóa
 * đơn nên không cần trigger DB - chỉ đơn giản chuyển PENDING -> HANDLED.
 */
public class ExceptionReportPanel extends BaseCrudPanel<ExceptionReport> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final ExceptionReportDAO exceptionReportDAO = new ExceptionReportDAO();

    public ExceptionReportPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("handle", FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS, "Đánh dấu đã xử lý",
                        this::handleRow, this::canHandle));

        // Nội dung | Người gửi | Ngày gửi | Người xử lý | Ngày xử lý | Trạng thái
        table.setColumnWidths(320, 130, 110, 130, 110, 110);
        table.setColumnMinWidths(200, 100, 95, 100, 95, 95);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCLAMATION_TRIANGLE; }
    @Override
    protected String getPageTitle() { return "Báo cáo ngoại lệ"; }
    @Override
    protected String getPageSubtitle() {
        return "Các tình huống bất thường NV bán hàng gửi lên - Quản lý bán hàng xem và xử lý";
    }
    @Override
    protected String getAddButtonLabel() {
        return PermissionManager.getInstance().can(AppPermission.EXCEPTION_REPORT_CREATE) ? "Gửi báo cáo" : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                "Nội dung", "Người gửi", "Ngày gửi", "Người xử lý", "Ngày xử lý", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(ExceptionReport item) {
        return new Object[]{
                item.getContent(),
                item.getCreatedByName() != null ? item.getCreatedByName() : "-",
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getHandledByName() != null ? item.getHandledByName() : "-",
                item.getHandledAt() != null ? item.getHandledAt().format(DATE_TIME_FORMAT) : "-",
                item.getStatus()
        };
    }

    @Override
    protected String getEntityLabel() { return "báo cáo ngoại lệ"; }
    @Override
    protected String getItemDisplayName(ExceptionReport item) { return "#" + item.getReportId(); }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<ExceptionReport> result) {
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<ExceptionReport> fetchPage(int page, int pageSize) {
        return exceptionReportDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<ExceptionReport> searchPage(String keyword, int page, int pageSize) {
        return exceptionReportDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<ExceptionReport> fetchAllForExport() {
        return exceptionReportDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo nội dung, người gửi..."; }
    @Override
    protected boolean supportsEdit() { return false; }
    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean supportsView() { return false; }

    @Override
    protected void openForm(ExceptionReport item) {
        // item luon la null tai day: supportsEdit() = false nen nut "Sua" khong bao gio goi ham nay,
        // chi nut "+ Gui bao cao" tren header goi openForm(null).
        String content = BaseDialog.inputText(this, "Gửi báo cáo ngoại lệ",
                "Nội dung báo cáo (VD: khách cần mua SP chưa có trong hệ thống, khách yêu cầu SP đặc biệt...):",
                "", "Gửi báo cáo");
        if (content == null || content.isBlank()) return;
        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        if (exceptionReportDAO.create(content, currentUserId)) {
            BaseDialog.success(this, "Thành công", "Đã gửi báo cáo ngoại lệ cho Quản lý bán hàng.");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể gửi báo cáo", "Gửi báo cáo thất bại. Vui lòng thử lại.");
        }
    }

    @Override
    protected boolean deleteItem(ExceptionReport item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private boolean canHandle(int modelRow) {
        if (!PermissionManager.getInstance().can(AppPermission.EXCEPTION_REPORT_HANDLE)) return false;
        ExceptionReport item = rowToItem(modelRow);
        return item != null && item.isPending();
    }

    private void handleRow(int modelRow) {
        ExceptionReport item = rowToItem(modelRow);
        if (item == null) return;
        boolean confirmed = BaseDialog.confirm(this, "Đánh dấu đã xử lý",
                "Xác nhận đã xử lý xong báo cáo ngoại lệ #" + item.getReportId() + "?",
                "Xác nhận", AppColor.SUCCESS, AppColor.SUCCESS, FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;
        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        if (exceptionReportDAO.handle(item.getReportId(), currentUserId)) {
            BaseDialog.success(this, "Thành công", "Đã đánh dấu xử lý xong báo cáo #" + item.getReportId() + ".");
            onDataChanged();
        } else {
            BaseDialog.error(this, "Không thể cập nhật", "Cập nhật trạng thái thất bại. Vui lòng thử lại.");
        }
    }

    private String statusLabel(Object value) {
        return "HANDLED".equalsIgnoreCase(String.valueOf(value)) ? "Đã xử lý" : "Chờ xử lý";
    }

    private Color statusColor(Object value) {
        return "HANDLED".equalsIgnoreCase(String.valueOf(value)) ? AppColor.SUCCESS : AppColor.WARNING;
    }
}