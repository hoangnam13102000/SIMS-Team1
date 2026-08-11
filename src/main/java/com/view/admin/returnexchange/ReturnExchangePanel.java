package com.view.admin.returnexchange;

import com.components.AppAlert;
import com.components.crud.BaseCrudPanel;
import com.dao.ReturnExchangeDAO;
import com.i18n.Lang;
import com.model.ReturnExchange;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

public class ReturnExchangePanel extends BaseCrudPanel<ReturnExchange> {

    private final ReturnExchangeDAO returnExchangeDAO = new ReturnExchangeDAO();

    public ReturnExchangePanel() {
        super();

        table.setBadgeColumn(1, this::typeLabel, this::typeColor);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Cột "Mã HĐ" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã hóa đơn: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã HĐ
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã HĐ
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(ReturnExchangePanel.this, "Copy thành công", "Đã copy mã hóa đơn: " + text);
                    }
                }
            }
        });

        initialLoad();
        applyColumnWidths();
    }

    /**
     * Mã HĐ = "HD-yyyyMMdd-####" (16 ký tự). Khóa minWidth cao để AUTO_RESIZE
     * không co cột này; các cột khác (Lý do...) chịu co khi khung hẹp.
     * Không bật horizontal scroll.
     */
    private void applyColumnWidths() {
        // preferred: Mã HĐ rộng; Lý do / Người tạo linh hoạt
        table.setColumnWidths(190, 95, 200, 100, 130, 115);
        // min: Mã HĐ không dưới 185; các cột còn lại cho phép co mạnh
        table.setColumnMinWidths(185, 75, 80, 80, 80, 95);
        if (table.getTable().getColumnModel().getColumnCount() > 0) {
            var col = table.getTable().getColumnModel().getColumn(0);
            col.setMinWidth(185);
            col.setPreferredWidth(190);
        }
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.EXCHANGE_ALT; }
    @Override
    protected String getPageTitle() { return Lang.get("returnExchange.title"); }
    @Override
    protected String getPageSubtitle() {
        return Lang.get("returnExchange.subtitle");
    }
    @Override
    protected String getAddButtonLabel() { return null; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{
                Lang.get("returnExchange.col.invoiceCode"),
                Lang.get("returnExchange.col.type"), Lang.get("returnExchange.col.reason"),
                Lang.get("returnExchange.col.value"),
                Lang.get("returnExchange.col.createdBy"),
                Lang.get("returnExchange.col.status")
        };
    }

    @Override
    protected Object[] mapRowToColumns(ReturnExchange item) {
        return new Object[]{
                item.getInvoiceCode(),
                item.getType(),
                item.getReason(),
                NumberUtil.formatThousands(item.getTotalValue() != null ? item.getTotalValue().longValue() : 0),
                item.getCreatedByName() != null ? item.getCreatedByName() : "-",
                item.getStatus()
        };
    }

    /** Cột "Giá trị" (chỉ số 3) — sort theo số. */
    @Override
    protected int[] numericColumns() { return new int[]{3}; }

    @Override
    protected String getEntityLabel() { return Lang.get("returnExchange.entityLabel"); }

    @Override
    protected String getItemDisplayName(ReturnExchange item) { return item.getInvoiceCode(); }

    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> fetchPage(int page, int pageSize) {
        return returnExchangeDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<ReturnExchange> searchPage(String keyword, int page, int pageSize) {
        return returnExchangeDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<ReturnExchange> fetchAllForExport() {
        return returnExchangeDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return Lang.get("returnExchange.searchPlaceholder"); }

    /** Gợi ý autocomplete: mã hóa đơn, người tạo. */
    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (ReturnExchange re : returnExchangeDAO.getAll()) {
            if (re.getInvoiceCode() != null && !re.getInvoiceCode().isBlank()) {
                names.add(re.getInvoiceCode());
            }
            if (re.getCreatedByName() != null && !re.getCreatedByName().isBlank()) {
                names.add(re.getCreatedByName());
            }
        }
        return new ArrayList<>(new LinkedHashSet<>(names)); // loại trùng, giữ thứ tự
    }

    @Override
    protected boolean supportsEdit() { return false; }
    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        ReturnExchange item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        ReturnExchangeDetailDialog dialog = new ReturnExchangeDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, returnExchangeDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void openForm(ReturnExchange item) {
        // Khong bao gio duoc goi: getAddButtonLabel() = null va supportsEdit() = false.
    }

    @Override
    protected boolean deleteItem(ReturnExchange item) { return false; }

    @Override
    protected void onDataChanged() { reload(); }

    private String typeLabel(Object value) {
        return ReturnExchange.TYPE_EXCHANGE.equalsIgnoreCase(String.valueOf(value))
                ? Lang.get("returnExchange.type.exchange") : Lang.get("returnExchange.type.return");
    }

    private Color typeColor(Object value) {
        return ReturnExchange.TYPE_EXCHANGE.equalsIgnoreCase(String.valueOf(value)) ? AppColor.ACCENT : AppColor.INFO;
    }

    private String statusLabel(Object value) {
        String v = String.valueOf(value);
        switch (v) {
            case "PENDING": return Lang.get("returnExchange.status.pending");
            case "APPROVED": return Lang.get("returnExchange.status.approved");
            case "REJECTED": return Lang.get("returnExchange.status.rejected");
            default: return v;
        }
    }

    private Color statusColor(Object value) {
        String v = String.valueOf(value);
        if ("APPROVED".equals(v)) return AppColor.SUCCESS;
        if ("REJECTED".equals(v)) return AppColor.ERROR;
        return AppColor.WARNING; // PENDING
    }

    // ---------------------------------------------------------------
    // Helper: copy mã hóa đơn vào clipboard
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