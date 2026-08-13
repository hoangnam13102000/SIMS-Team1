package com.view.admin.promotion;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.components.crud.CrudMode;
import com.components.crud.TrashConfig;
import com.dao.PromotionDAO;
import com.model.Promotion;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Color;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
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

    /** Lọc theo khoảng ngày giao với thời gian hiệu lực (StartDate–EndDate). */
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    public PromotionPanel() {
        super();

        table.setColumnWidths(120, 180, 120, 120, 160, 90, 80, 120);
        table.setColumnMinWidths(90, 140, 90, 100, 140, 70, 70, 100);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);

        // Cột "Mã" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã khuyến mãi: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã khuyến mãi
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(PromotionPanel.this, "Copy thành công", "Đã copy mã khuyến mãi: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();
        initialLoad();
    }

    // ---------------------------------------------------------------
    // Bộ lọc: khoảng thời gian hiệu lực khuyến mãi
    // ---------------------------------------------------------------

    private void buildDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);

        JLabel fromLabel = new JLabel("Hiệu lực từ");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);

        JLabel toLabel = new JLabel("đến");
        toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        toLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dateRow.setOpaque(false);
        dateRow.add(fromLabel);
        dateRow.add(fromDateFilter);
        dateRow.add(toLabel);
        dateRow.add(toDateFilter);

        fromDateFilter.onChange(d -> onDateFilterChanged());
        toDateFilter.onChange(d -> onDateFilterChanged());
        addToolbarFilter(dateRow);

        FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 14);
        clearIcon.setIconColor(AppColor.TEXT_MUTED);
        clearDateFilterLink = new JLabel(clearIcon);
        clearDateFilterLink.setToolTipText("Xóa lọc ngày");  // hiện khi hover
        clearDateFilterLink.setIconTextGap(6);
        clearDateFilterLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
        clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
        clearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearDateFilterLink.setVisible(false);
        clearDateFilterLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                fromDateFilter.setValue(null);
                toDateFilter.setValue(null);
                onDateFilterChanged();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                clearDateFilterLink.setForeground(AppColor.ERROR);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
            }
        });
        addToolbarFilter(clearDateFilterLink);
    }

    /** Tránh hiện cảnh báo lặp khi tự xóa ngày không hợp lệ. */
    private boolean adjustingDateFilter;

    private void onDateFilterChanged() {
        if (adjustingDateFilter) return;

        LocalDate from = selectedFromDate();
        LocalDate to = selectedToDate();
        if (from != null && to != null && to.isBefore(from)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
            adjustingDateFilter = true;
            try {
                // Giữ ngày "từ", xóa ngày "đến" để người dùng chọn lại
                toDateFilter.setValue(null);
            } finally {
                adjustingDateFilter = false;
            }
        }

        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(
                    fromDateFilter.getValue() != null || toDateFilter.getValue() != null);
        }
        applyFilters();
    }

    private LocalDate selectedFromDate() {
        return fromDateFilter == null ? null : fromDateFilter.getValue();
    }

    private LocalDate selectedToDate() {
        return toDateFilter == null ? null : toDateFilter.getValue();
    }


    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.PERCENT; }
    @Override
    protected String getPageTitle() { return "Quản lý khuyến mãi"; }
    @Override
    protected String getPageSubtitle() { return "Tạo và quản lý mã giảm giá — lọc theo khoảng thời gian hiệu lực"; }
    @Override
    protected String getAddButtonLabel() { return "Thêm khuyến mãi"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã", "Tên chương trình", "Giá trị giảm", "Đơn tối thiểu",
                "Hiệu lực", "Đã dùng", "Banner", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(Promotion item) {
        return new Object[]{
                item.getCode(),
                item.getName(),
                discountDisplay(item),
                moneyOrDash(item.getMinOrderAmount()),
                formatDate(item.getStartDate()) + " - " + formatDate(item.getEndDate()),
                item.getUsedCount() + (item.getUsageLimit() != null ? "/" + item.getUsageLimit() : ""),
                item.isShowOnBanner() ? "Có" : "—",
                statusLabel(item)
        };
    }

    @Override
    protected String getEntityLabel() { return "khuyến mãi"; }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<Promotion> result) {
        table.getTable().repaint();
    }

    @Override
    protected String getItemDisplayName(Promotion item) {
        return item.getCode() + " - " + item.getName();
    }

    @Override
    protected PaginationHelper.PaginationResult<Promotion> fetchPage(int page, int pageSize) {
        return promotionDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<Promotion> searchPage(String keyword, int page, int pageSize) {
        return promotionDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
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

    // ---------------------------------------------------------------
    // Helper: copy mã khuyến mãi vào clipboard
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