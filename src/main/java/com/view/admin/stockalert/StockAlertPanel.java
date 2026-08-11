package com.view.admin.stockalert;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.StockAlertDAO;
import com.model.StockAlert;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.Color;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class StockAlertPanel extends BaseCrudPanel<StockAlert> {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yy HH:mm");

    private final StockAlertDAO stockAlertDAO = new StockAlertDAO();

    public StockAlertPanel() {
        super();

        table.setActionColumn(new ActionColumn()
                .header("Thao tác")
                .add("plan", FontAwesomeSolid.CALENDAR_PLUS, AppColor.ACCENT, "Đã lên kế hoạch nhập bổ sung",
                        this::planRow, this::canPlan)
                .add("resolve", FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS, "Đánh dấu đã xử lý xong",
                        this::resolveRow, this::canResolve));

        // Không STT / Tồn khi báo / Tồn tối thiểu
        // Mã SP | Tên SP | Loại cảnh báo | Người báo cáo | Thời gian | Trạng thái
        table.setBadgeColumn(2, this::alertTypeLabel, this::alertTypeColor);
        table.setBadgeColumn(5, this::statusLabel, this::statusColor);

        // Cột "Mã SP" (index 0): thêm icon copy
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
                    c.setToolTipText("Click để copy mã sản phẩm: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã SP
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 0 && viewRow >= 0) { // Cột Mã SP
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(StockAlertPanel.this, "Copy thành công", "Đã copy mã sản phẩm: " + text);
                    }
                }
            }
        });

        initialLoad();
        applyColumnWidths();

        // Quan ly kho vao trang nay -> coi nhu da xem het cac bao cao hien
        // co, badge tren sidebar se ve 0 trong lan poll ke tiep (toi da 5s).
        stockAlertDAO.markAllSeen();
    }

    private void applyColumnWidths() {
        table.setColumnWidths(100, 200, 120, 150, 130, 130);
        table.setColumnMinWidths(90, 150, 105, 120, 110, 110);
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
                "Mã SP", "Tên sản phẩm", "Loại cảnh báo",
                "Người báo cáo", "Thời gian", "Trạng thái"
        };
    }

    @Override
    protected Object[] mapRowToColumns(StockAlert item) {
        return new Object[]{
                item.getProductCode(),
                item.getProductName(),
                item.getAlertType(),
                item.getReportedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-",
                item.getStatus()
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{}; }

    @Override
    protected String getEntityLabel() { return "cảnh báo"; }

    @Override
    protected String getItemDisplayName(StockAlert item) { return item.getProductName(); }

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

    // ---------------------------------------------------------------
    // Helper: copy mã sản phẩm vào clipboard
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