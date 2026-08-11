package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.crud.BaseCrudPanel;
import com.dao.SupplierReturnDAO;
import com.model.SupplierReturn;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Quan ly phieu tra hang lo ve nha cung cap (hang loi/hong/sai quy cach).
 * Tru kho ngay khi lap phieu, cong don cong no NCC (Suppliers.DebtBalance).
 */
public class SupplierReturnPanel extends BaseCrudPanel<SupplierReturn> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final SupplierReturnDAO returnDAO = new SupplierReturnDAO();

    public SupplierReturnPanel() {
        super();

        // Mã phiếu | NCC | Lý do | Số dòng | Hoàn tiền | Người lập | Ngày lập | Trạng thái
        table.setColumnWidths(110, 160, 110, 80, 120, 130, 130, 100);
        table.setColumnMinWidths(90, 120, 90, 60, 100, 100, 110, 80);
        table.setBadgeColumn(7, this::statusLabel, this::statusColor);

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
                    c.setToolTipText("Click để copy mã phiếu trả NCC: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã phiếu trả NCC
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
                        AppAlert.success(SupplierReturnPanel.this, "Copy thành công", "Đã copy mã phiếu trả NCC: " + text);
                    }
                }
            }
        });

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.UNDO; }
    @Override
    protected String getPageTitle() { return "Trả hàng nhà cung cấp"; }
    @Override
    protected String getPageSubtitle() {
        return "Lập phiếu trả hàng lô lỗi/hỏng về NCC, tự động trừ kho và ghi nhận công nợ hoàn tiền";
    }
    @Override
    protected String getAddButtonLabel() {
        return AuthService.getInstance().can(AppPermission.SUPPLIER_RETURN_CREATE) ? "Lập phiếu trả NCC" : null;
    }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã phiếu", "Nhà cung cấp", "Lý do", "Số dòng", "Hoàn tiền", "Người lập", "Ngày lập", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(SupplierReturn item) {
        return new Object[]{
                item.getSupplierReturnCode(),
                item.getSupplierName(),
                item.getReasonLabel(),
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalRefundAmount() != null ? item.getTotalRefundAmount().longValue() : 0),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{3, 4}; }

    @Override
    protected String getEntityLabel() { return "phiếu trả NCC"; }

    @Override
    protected String getItemDisplayName(SupplierReturn item) {
        return item.getSupplierReturnCode() + " - " + item.getSupplierName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<SupplierReturn> result) {
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<SupplierReturn> fetchPage(int page, int pageSize) {
        return returnDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<SupplierReturn> searchPage(String keyword, int page, int pageSize) {
        return returnDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<SupplierReturn> fetchAllForExport() {
        return returnDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() {
        return "Tìm mã phiếu, NCC, lý do, người lập...";
    }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (SupplierReturn r : returnDAO.getAll()) {
            if (r.getSupplierReturnCode() != null) names.add(r.getSupplierReturnCode());
            if (r.getSupplierName() != null) names.add(r.getSupplierName());
            if (r.getReasonLabel() != null) names.add(r.getReasonLabel());
            if (r.getCreatedByName() != null) names.add(r.getCreatedByName());
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
        SupplierReturn item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        new SupplierReturnDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, returnDAO).setVisible(true);
    }

    @Override
    protected void openForm(SupplierReturn item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        SupplierReturnFormDialog dialog = new SupplierReturnFormDialog(
                owner instanceof Frame ? (Frame) owner : null);
        dialog.onSaved((id, n) -> onDataChanged());
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(SupplierReturn item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private String statusLabel(SupplierReturn r) {
        return r.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Helper: copy mã phiếu trả NCC vào clipboard
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