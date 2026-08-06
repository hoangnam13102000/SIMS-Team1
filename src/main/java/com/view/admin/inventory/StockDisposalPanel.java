package com.view.admin.inventory;

import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
import com.dao.StockDisposalDAO;
import com.model.StockDisposal;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Quan ly phieu tieu huy hang + xem ton that tai chinh.
 */
public class StockDisposalPanel extends BaseCrudPanel<StockDisposal> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final StockDisposalDAO disposalDAO = new StockDisposalDAO();
    private AutoRowNumber stt;

    public StockDisposalPanel() {
        super();
        stt = table.setAutoRowNumberColumn(0);
        // STT | Ma phieu | Ly do | So dong | Ton that | Nguoi lap | Ngay | Trang thai
        table.setColumnWidths(45, 110, 120, 80, 120, 130, 130, 100);
        table.setColumnMinWidths(35, 90, 100, 60, 100, 100, 110, 80);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);
        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.TRASH; }

    @Override
    protected String getPageTitle() { return "Tiêu hủy hàng"; }

    @Override
    protected String getPageSubtitle() {
        return "Lập phiếu tiêu hủy theo lô, ghi nhận tổn thất tài chính (giá nhập × SL)";
    }

    @Override
    protected String getAddButtonLabel() {
        return AuthService.getInstance().can(AppPermission.STOCK_DISPOSE) ? "Lập phiếu tiêu hủy" : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã phiếu", "Lý do", "Số dòng", "Tổn thất", "Người lập", "Ngày lập", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(StockDisposal item) {
        return new Object[]{
                "",
                item.getDisposalCode(),
                item.getReasonLabel(),
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalLossAmount() != null ? item.getTotalLossAmount().longValue() : 0),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{3, 4}; }

    @Override
    protected String getEntityLabel() { return "phiếu tiêu hủy"; }

    @Override
    protected String getItemDisplayName(StockDisposal item) {
        return item.getDisposalCode() + " - " + item.getReasonLabel();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockDisposal> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<StockDisposal> fetchPage(int page, int pageSize) {
        return disposalDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<StockDisposal> searchPage(String keyword, int page, int pageSize) {
        return disposalDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<StockDisposal> fetchAllForExport() {
        return disposalDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm mã phiếu, lý do, người lập...";
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (StockDisposal d : disposalDAO.getAll()) {
            if (d.getDisposalCode() != null) names.add(d.getDisposalCode());
            if (d.getReasonLabel() != null) names.add(d.getReasonLabel());
            if (d.getCreatedByName() != null) names.add(d.getCreatedByName());
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected boolean supportsEdit() { return false; }

    @Override
    protected boolean supportsDelete() { return false; }

    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        StockDisposal item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        new StockDisposalDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, disposalDAO).setVisible(true);
    }

    @Override
    protected void openForm(StockDisposal item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        StockDisposalFormDialog dialog = new StockDisposalFormDialog(
                owner instanceof Frame ? (Frame) owner : null);
        dialog.onSaved((id, n) -> onDataChanged());
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(StockDisposal item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private String statusLabel(StockDisposal d) {
        return d.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }
}