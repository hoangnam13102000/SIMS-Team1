package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.crud.BaseCrudPanel;
import com.dao.PurchaseReceiptDAO;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.model.PurchaseReceipt;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import javax.swing.SwingUtilities;

public class PurchaseReceiptPanel extends BaseCrudPanel<PurchaseReceipt> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PurchaseReceiptDAO receiptDAO = new PurchaseReceiptDAO();

    public PurchaseReceiptPanel() {
        super();

        // Nha cung cap/Nguoi tao can nhieu cho hon de khong bi cat "...".
        // Cot "So mat hang" truoc do qua hep khien header wrap 3 dong va bi
        // cat mat chu cuoi - noi rong ra de header nam gon 1 dong.
        table.setColumnWidths(140, 270, 190, 150, 110, 130, 110);
        table.setColumnMinWidths(110, 200, 150, 130, 90, 110, 90);
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
                    c.setToolTipText("Click để copy mã phiếu nhập: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã phiếu nhập
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
                        AppAlert.success(PurchaseReceiptPanel.this, "Copy thành công", "Đã copy mã phiếu nhập: " + text);
                    }
                }
            }
        });

        initialLoad();
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.FILE_INVOICE; }
    @Override
    protected String getPageTitle() { return "Quản lý nhập kho"; }
    @Override
    protected String getPageSubtitle() { return "Lập phiếu nhập nhiều sản phẩm và tra cứu lịch sử theo nhà cung cấp"; }
    @Override
    protected String getAddButtonLabel() {
        return AuthService.getInstance().can(AppPermission.STOCK_IMPORT) ? "Lập phiếu nhập" : null;
    }

    @Override
    protected String[] getColumnNames() {
        // "Số SP" (ngan hon "Số mặt hàng") de header luon nam gon 1 dong du
        // cot khong qua rong, tranh bi wrap 2-3 dong va cat mat chu.
        return new String[]{"Mã phiếu", "Nhà cung cấp", "Người tạo", "Ngày tạo",
                "Số SP", "Tổng tiền", "Trạng thái"};
    }

    @Override
    protected Object[] mapRowToColumns(PurchaseReceipt item) {
        return new Object[]{
                item.getReceiptCode(),
                item.getSupplierName(),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getItemCount(),
                NumberUtil.formatThousands(item.getTotalAmount().longValue()),
                statusLabel(item)
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{4, 5}; }

    @Override
    protected String getEntityLabel() { return "phiếu nhập kho"; }

    @Override
    protected String getItemDisplayName(PurchaseReceipt item) {
        return item.getReceiptCode() + " - " + item.getSupplierName();
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<PurchaseReceipt> result) {
        table.getTable().repaint();
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> fetchPage(int page, int pageSize) {
        return receiptDAO.getPaged(page, pageSize);
    }

    @Override
    protected PaginationHelper.PaginationResult<PurchaseReceipt> searchPage(String keyword, int page, int pageSize) {
        return receiptDAO.search(keyword, page, pageSize);
    }

    @Override
    protected List<PurchaseReceipt> fetchAllForExport() {
        return receiptDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo mã phiếu, nhà cung cấp, người tạo..."; }

    @Override
    protected List<String> fetchAutocompleteSuggestions() {
        List<String> names = new ArrayList<>();
        for (PurchaseReceipt r : receiptDAO.getAll()) {
            if (r.getReceiptCode() != null && !r.getReceiptCode().isBlank()) {
                names.add(r.getReceiptCode());
            }
            if (r.getSupplierName() != null && !r.getSupplierName().isBlank()) {
                names.add(r.getSupplierName());
            }
            if (r.getCreatedByName() != null && !r.getCreatedByName().isBlank()) {
                names.add(r.getCreatedByName());
            }
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
        PurchaseReceipt item = rowToItem(modelRow);
        if (item == null) return;
        openDetailDialog(item);
    }

    @Override
    protected void openForm(PurchaseReceipt item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        PurchaseReceiptFormDialog dialog = new PurchaseReceiptFormDialog(
                owner instanceof Frame ? (Frame) owner : null);
        dialog.onSaved((receiptId, lineCount) -> onDataChanged());
        dialog.setVisible(true);
    }

    @Override
    protected boolean deleteItem(PurchaseReceipt item) { return false; }

    private void openDetailDialog(PurchaseReceipt item) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        PurchaseReceiptDetailDialog dialog = new PurchaseReceiptDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item, receiptDAO);
        dialog.setVisible(true);
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private String statusLabel(PurchaseReceipt r) {
        return r.isCancelled() ? "Đã hủy" : "Hoàn tất";
    }

    private String statusLabel(Object value) {
        return String.valueOf(value);
    }

    private Color statusColor(Object value) {
        return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Helper: copy mã phiếu nhập vào clipboard
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