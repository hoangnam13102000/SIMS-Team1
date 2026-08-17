package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.dao.StockDisposalDAO;
import com.model.StockDisposal;
import com.model.permission.AppPermission;
import com.service.AuthService;
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
 * Quan ly phieu tieu huy hang + xem ton that tai chinh.
 */
public class StockDisposalPanel extends BaseCrudPanel<StockDisposal> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final StockDisposalDAO disposalDAO = new StockDisposalDAO();
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    public StockDisposalPanel() {
        super();

        // Mã phiếu | Lý do | Số dòng | Tổn thất | Người lập | Ngày lập | Trạng thái
        table.setColumnWidths(110, 120, 80, 120, 130, 130, 100);
        table.setColumnMinWidths(90, 100, 60, 100, 100, 110, 80);
        table.setBadgeColumn(6, this::statusLabel, this::statusColor);

        // Cột "Mã phiếu" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã phiếu tiêu hủy: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã phiếu tiêu hủy
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã phiếu
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(StockDisposalPanel.this, "Copy thành công", "Đã copy mã phiếu tiêu hủy: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();
        initialLoad();
    }

    private void buildDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);
        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel toLabel = new JLabel("Đến ngày");
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
        clearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearDateFilterLink.setToolTipText("Xóa lọc ngày");
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
                FontIcon hover = FontIcon.of(FontAwesomeSolid.TIMES, 14);
                hover.setIconColor(AppColor.ERROR);
                clearDateFilterLink.setIcon(hover);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                FontIcon normal = FontIcon.of(FontAwesomeSolid.TIMES, 14);
                normal.setIconColor(AppColor.TEXT_MUTED);
                clearDateFilterLink.setIcon(normal);
            }
        });
        addToolbarFilter(clearDateFilterLink);
    }

    private void onDateFilterChanged() {
        LocalDate from = fromDateFilter.getValue();
        LocalDate to = toDateFilter.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                    "\"Từ ngày\" (" + from + ") không được sau \"Đến ngày\" (" + to + ").");
            return;
        }

        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(fromDateFilter.getValue() != null || toDateFilter.getValue() != null);
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
        return new String[]{"Mã phiếu", "Lý do", "Số dòng", "Tổn thất", "Người lập", "Ngày lập", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(StockDisposal item) {
        return new Object[]{
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
    protected int[] numericColumns() { return new int[]{2, 3}; }

    @Override
    protected String getEntityLabel() { return "phiếu tiêu hủy"; }

    @Override
    protected String getItemDisplayName(StockDisposal item) {
        return item.getDisposalCode() + " - " + item.getReasonLabel();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockDisposal> result) {
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<StockDisposal> fetchPage(int page, int pageSize) {
        return disposalDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<StockDisposal> searchPage(String keyword, int page, int pageSize) {
        return disposalDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<StockDisposal> fetchAllForExport() {
        return disposalDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm mã phiếu, mã lô, lý do, người lập...";
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (StockDisposal d : disposalDAO.getAll()) {
            if (d.getDisposalCode() != null) names.add(d.getDisposalCode());
            if (d.getReasonLabel() != null) names.add(d.getReasonLabel());
            if (d.getCreatedByName() != null) names.add(d.getCreatedByName());
        }
        names.addAll(disposalDAO.getBatchSearchSuggestions());
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

    // ---------------------------------------------------------------
    // Helper: copy mã phiếu tiêu hủy vào clipboard
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