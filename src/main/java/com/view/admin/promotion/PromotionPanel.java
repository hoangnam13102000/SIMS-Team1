package com.view.admin.promotion;

import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.crud.TrashConfig;
import com.components.table.AutoRowNumber;
import com.dao.PromotionDAO;
import com.model.Promotion;
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
 * Man hinh "Quan ly khuyen mai / ma giam gia" - danh cho Quan ly ban hang
 * (AppPermission.PROMOTION_MANAGE). Cung cap CRUD day du (them/sua/xoa mem/
 * thung rac) tren bang Promotions - xem Migration_Promotions.sql.
 */
public class PromotionPanel extends BaseCrudPanel<Promotion> {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PromotionDAO promotionDAO = new PromotionDAO();
    private AutoRowNumber stt;

    public PromotionPanel() {
        super();

        stt = table.setAutoRowNumberColumn(0);
        table.setColumnWidths(50, 130, 200, 130, 140, 170, 100, 130);
        table.setColumnMinWidths(40, 100, 150, 100, 110, 150, 80, 110);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.PERCENT; }

    @Override
    protected String getPageTitle() { return "Quản lý khuyến mãi"; }

    @Override
    protected String getPageSubtitle() { return "Tạo và quản lý mã giảm giá áp dụng khi bán hàng"; }

    @Override
    protected String getAddButtonLabel() { return "Thêm khuyến mãi"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"STT", "Mã", "Tên chương trình", "Giá trị giảm", "Đơn tối thiểu",
                "Hiệu lực", "Đã dùng", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Promotion item) {
        return new Object[]{
                "",
                item.getCode(),
                item.getName(),
                discountDisplay(item),
                moneyOrDash(item.getMinOrderAmount()),
                formatDate(item.getStartDate()) + " - " + formatDate(item.getEndDate()),
                item.getUsedCount() + (item.getUsageLimit() != null ? "/" + item.getUsageLimit() : ""),
                statusLabel(item)
        };
    }

    @Override
    protected String getEntityLabel() { return "khuyến mãi"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Promotion> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Promotion item) {
        return item.getCode() + " - " + item.getName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Promotion> fetchPage(int page, int pageSize) {
        return promotionDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<Promotion> searchPage(String keyword, int page, int pageSize) {
        return promotionDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<Promotion> fetchAllForExport() {
        return promotionDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã hoặc tên chương trình..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (Promotion p : promotionDAO.getAll()) {
            if (p.getCode() != null && !p.getCode().isBlank()) names.add(p.getCode());
            if (p.getName() != null && !p.getName().isBlank()) names.add(p.getName());
        }
        return new ArrayList<>(new LinkedHashSet<>(names));
    }

    @Override
    protected void openForm(Promotion item) {
        CrudMode mode = item == null ? CrudMode.ADD : CrudMode.EDIT;
        Window owner = SwingUtilities.getWindowAncestor(this);
        PromotionFormDialog dialog = new PromotionFormDialog(
                owner instanceof Frame ? (Frame) owner : null, mode, item, promotionDAO);
        dialog.onSaved(this::handleFormSaved);
        dialog.setVisible(true);
    }

    /** Xoa mem (IsDeleted = 1) - giu lich su cac ma da tung dung de doi chieu doanh thu/hoa don cu. */
    @Override
    protected boolean deleteItem(Promotion item) {
        return promotionDAO.softDelete(item.getPromotionId());
    }

    @Override
    protected TrashConfig<Promotion> getTrashConfig() {
        return new TrashConfig<>(
                promotionDAO::getDeletedItems,
                item -> promotionDAO.restore(item.getPromotionId()),
                item -> promotionDAO.hardDeletePromotion(item.getPromotionId())
        );
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------

    private static String discountDisplay(Promotion item) {
        if (item.getDiscountValue() == null) return "-";
        if (item.isPercent()) {
            String base = item.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
            if (item.getMaxDiscountAmount() != null) {
                base += " (tối đa " + NumberUtil.formatThousands(item.getMaxDiscountAmount().longValue()) + "đ)";
            }
            return base;
        }
        return NumberUtil.formatThousands(item.getDiscountValue().longValue()) + "đ";
    }

    private static String moneyOrDash(java.math.BigDecimal value) {
        if (value == null || value.signum() == 0) return "-";
        return NumberUtil.formatThousands(value.longValue()) + "đ";
    }

    private static String formatDate(java.time.LocalDate date) {
        return date == null ? "-" : date.format(DATE_FORMAT);
    }

    private String statusLabel(Promotion item) {
        switch (item.computeStatus()) {
            case "PAUSED": return "Đã tắt";
            case "EXHAUSTED": return "Hết lượt dùng";
            case "UPCOMING": return "Sắp diễn ra";
            case "EXPIRED": return "Đã kết thúc";
            default: return "Đang áp dụng";
        }
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        String label = String.valueOf(value);
        switch (label) {
            case "Đang áp dụng": return AppColor.SUCCESS;
            case "Sắp diễn ra": return AppColor.INFO;
            case "Đã tắt": return AppColor.WARNING;
            case "Hết lượt dùng": return AppColor.ERROR;
            default: return AppColor.TEXT_MUTED; // Đã kết thúc
        }
    }
}