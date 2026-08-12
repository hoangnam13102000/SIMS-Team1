package com.view.admin.inventory;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.crud.BaseCrudPanel;
import com.dao.ProductDAO;
import com.dao.StockReconciliationDAO;
import com.model.Product;
import com.model.StockReconciliation;
import com.service.AuthService;
import com.theme.AppColor;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Man hinh "Đối chiếu / kiểm kê kho cuối ngày":
 * - Moi ngay tu dong tao du danh sach san pham ACTIVE de doi chieu.
 * - Co the sua truc tiep cot "Ton thuc te" tren bang (chi dong cua hom nay).
 * - Loc theo khoang ngay; mac dinh hien phien hom nay.
 */
public class StockReconciliationPanel extends BaseCrudPanel<StockReconciliation> {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Chi so cot model: Ton thuc te — cho phep sua truc tiep. */
    private static final int COL_ACTUAL = 3;
    private static final int COL_DIFF = 4;

    private final StockReconciliationDAO reconciliationDAO = new StockReconciliationDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;

    /** Chan TableModelListener khi dang reload de tranh luu nham. */
    private boolean suppressActualEdit = false;

    public StockReconciliationPanel() {
        super();

        // Cột: Mã SP | Sản phẩm | Tồn hệ thống | Tồn thực tế | Chênh lệch | Người đối chiếu | Thời gian
        table.setColumnWidths(90, 170, 100, 100, 90, 130, 130);
        table.setColumnMinWidths(70, 130, 80, 80, 70, 100, 110);
        table.setBadgeColumn(COL_DIFF, this::discrepancyLabel, this::discrepancyColor);

        // Cho sua truc tiep cot "Ton thuc te"
        table.setEditableColumns(COL_ACTUAL);
        installActualStockEditor();

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

        // Gợi ý cột tồn thực tế có thể sửa (icon bút)
        table.getTable().getColumnModel().getColumn(COL_ACTUAL).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG
                        : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));

                int modelRow = tbl.convertRowIndexToModel(row);
                StockReconciliation item = rowToItem(modelRow);
                boolean editableToday = item != null && item.getCreatedAt() != null
                        && item.getCreatedAt().toLocalDate().equals(LocalDate.now());

                if (editableToday) {
                    FontIcon editIcon = FontIcon.of(FontAwesomeSolid.PEN, 11);
                    editIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(editIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                    c.setToolTipText("Double-click hoặc F2 để sửa tồn thực tế");
                    c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    c.setIcon(null);
                    c.setToolTipText("Chỉ được sửa tồn thực tế của phiên hôm nay");
                    c.setCursor(Cursor.getDefaultCursor());
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
                if (viewCol == 0 && viewRow >= 0) {
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 0);
                    String text = value != null ? value.toString() : "";
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        AppAlert.success(StockReconciliationPanel.this, "Copy thành công", "Đã copy mã sản phẩm: " + text);
                    }
                }
            }
        });

        buildDateFilterBar();

        // Mac dinh loc theo hom nay
        LocalDate today = LocalDate.now();
        fromDateFilter.setValue(today);
        toDateFilter.setValue(today);
        if (clearDateFilterLink != null) {
            clearDateFilterLink.setVisible(true);
        }

        // Tao phien hom nay (neu chua co) roi moi load
        ensureTodaySessionThenLoad();
    }

    /**
     * Tao du danh sach SP ACTIVE cho hom nay (neu thieu), roi load bang.
     */
    private void ensureTodaySessionThenLoad() {
        Integer userId = currentUserId();
        if (userId == null) {
            initialLoad();
            return;
        }
        // Chay ensure o background de khong treo UI
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return reconciliationDAO.ensureDailySession(LocalDate.now(), userId);
            }

            @Override
            protected void done() {
                try {
                    int created = get();
                    if (created > 0) {
                        AppAlert.success(StockReconciliationPanel.this, "Phiên đối chiếu hôm nay",
                                "Đã tạo " + created + " sản phẩm để đối chiếu kho.");
                    }
                } catch (Exception ignored) {
                    // Bo qua, van load binh thuong
                }
                initialLoad();
            }
        };
        worker.execute();
    }

    private Integer currentUserId() {
        return AuthService.getInstance().getCurrentUser() != null
                ? AuthService.getInstance().getCurrentUser().getUserId() : null;
    }

    // ---------------------------------------------------------------
    // Bo loc ngay
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    // Sua ton thuc te truc tiep tren bang
    // ---------------------------------------------------------------

    private void installActualStockEditor() {
        table.getModel().addTableModelListener(e -> {
            if (suppressActualEdit) return;
            if (e.getType() != TableModelEvent.UPDATE) return;
            if (e.getColumn() != COL_ACTUAL && e.getColumn() != TableModelEvent.ALL_COLUMNS) return;

            int modelRow = e.getFirstRow();
            if (modelRow < 0) return;

            StockReconciliation item = rowToItem(modelRow);
            if (item == null) return;

            // Chi cho sua dong cua hom nay
            if (item.getCreatedAt() == null || !item.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
                suppressActualEdit = true;
                try {
                    table.getModel().setValueAt(item.getActualStock(), modelRow, COL_ACTUAL);
                } finally {
                    suppressActualEdit = false;
                }
                AppAlert.error(this, "Không thể sửa",
                        "Chỉ được sửa tồn thực tế của phiên đối chiếu hôm nay.");
                return;
            }

            Object raw = table.getModel().getValueAt(modelRow, COL_ACTUAL);
            int newActual;
            try {
                if (raw == null || raw.toString().isBlank()) {
                    throw new NumberFormatException("empty");
                }
                newActual = Integer.parseInt(raw.toString().trim().replace(",", "").replace(".", ""));
                if (newActual < 0) throw new NumberFormatException("negative");
            } catch (NumberFormatException ex) {
                suppressActualEdit = true;
                try {
                    table.getModel().setValueAt(item.getActualStock(), modelRow, COL_ACTUAL);
                } finally {
                    suppressActualEdit = false;
                }
                AppAlert.error(this, "Số không hợp lệ", "Tồn thực tế phải là số nguyên ≥ 0.");
                return;
            }

            if (newActual == item.getActualStock()) return;

            Integer userId = currentUserId();
            if (userId == null) return;

            boolean ok = reconciliationDAO.updateActualStock(
                    item.getReconciliationId(), newActual, userId);
            if (!ok) {
                suppressActualEdit = true;
                try {
                    table.getModel().setValueAt(item.getActualStock(), modelRow, COL_ACTUAL);
                } finally {
                    suppressActualEdit = false;
                }
                AppAlert.error(this, "Cập nhật thất bại",
                        "Không thể cập nhật tồn thực tế. Vui lòng thử lại.");
                return;
            }

            // Cap nhat model local + cot chenh lech
            item.setActualStock(newActual);
            item.setDiscrepancy(newActual - item.getSystemStock());
            suppressActualEdit = true;
            try {
                table.getModel().setValueAt(discrepancyText(item.getDiscrepancy()), modelRow, COL_DIFF);
            } finally {
                suppressActualEdit = false;
            }
            table.getTable().repaint();
            AppAlert.success(this, "Đã cập nhật",
                    "Tồn thực tế \"" + item.getProductName() + "\" = " + newActual
                            + " (chênh lệch: " + discrepancyText(item.getDiscrepancy()) + ")");
        });
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockReconciliation> result) {
        // Khi reload, chan listener de khong ghi de du lieu
        suppressActualEdit = true;
        try {
            table.getTable().repaint();
        } finally {
            // Delay nhe de model settle
            SwingUtilities.invokeLater(() -> suppressActualEdit = false);
        }
    }

    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BALANCE_SCALE; }
    @Override
    protected String getPageTitle() { return "Đối chiếu kho cuối ngày"; }
    @Override
    protected String getPageSubtitle() {
        return "Mỗi ngày tự tạo đủ danh sách SP — sửa tồn thực tế trực tiếp trên bảng";
    }
    @Override
    protected String getAddButtonLabel() { return "Đồng bộ SP hôm nay"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Mã SP", "Sản phẩm", "Tồn hệ thống", "Tồn thực tế",
                "Chênh lệch", "Người đối chiếu", "Thời gian"};
    }

    @Override
    protected Object[] mapRowToColumns(StockReconciliation item) {
        return new Object[]{
                item.getProductCode(),
                item.getProductName(),
                item.getSystemStock(),
                item.getActualStock(),
                discrepancyText(item.getDiscrepancy()),
                item.getCreatedByName(),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-"
        };
    }

    @Override
    protected int[] numericColumns() { return new int[]{2, 3}; }

    @Override
    protected String getEntityLabel() { return "phiên đối chiếu kho"; }

    @Override
    protected String getItemDisplayName(StockReconciliation item) {
        return item.getProductName() + " - " + (item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "");
    }

    @Override
    protected PaginationHelper.PaginationResult<StockReconciliation> fetchPage(int page, int pageSize) {
        return reconciliationDAO.getPagedFiltered(page, pageSize, null, selectedFromDate(), selectedToDate());
    }

    @Override
    protected PaginationHelper.PaginationResult<StockReconciliation> searchPage(String keyword, int page, int pageSize) {
        return reconciliationDAO.getPagedFiltered(page, pageSize, keyword, selectedFromDate(), selectedToDate());
    }

    @Override
    protected List<StockReconciliation> fetchAllForExport() {
        return reconciliationDAO.getAll();
    }

    @Override
    protected String getSearchPlaceholder() { return "Tìm theo tên sản phẩm, mã SP, người đối chiếu..."; }

    @Override
    protected boolean supportsEdit() { return false; }
    @Override
    protected boolean supportsDelete() { return false; }
    @Override
    protected boolean supportsView() { return true; }

    @Override
    protected void viewRow(int modelRow) {
        StockReconciliation item = rowToItem(modelRow);
        if (item == null) return;
        Window owner = SwingUtilities.getWindowAncestor(this);
        StockReconciliationDetailDialog dialog = new StockReconciliationDetailDialog(
                owner instanceof Frame ? (Frame) owner : null, item);
        dialog.setVisible(true);
    }

    /**
     * Nut "Dong bo SP hom nay": chen cac SP ACTIVE moi chua co trong phien hom nay.
     * (Khong mo StockCountDialog nua — sua ton thuc te truc tiep tren bang.)
     */
    @Override
    protected void openForm(StockReconciliation item) {
        Integer userId = currentUserId();
        if (userId == null) return;

        List<Product> activeProducts = productDAO.findAllActive();
        if (activeProducts.isEmpty()) {
            BaseDialog.info(this, "Không có sản phẩm",
                    "Chưa có sản phẩm đang bán nào để đối chiếu. Vui lòng thêm sản phẩm trước.");
            return;
        }

        int created = reconciliationDAO.ensureDailySession(LocalDate.now(), userId);
        if (created > 0) {
            AppAlert.success(this, "Đồng bộ thành công",
                    "Đã thêm " + created + " sản phẩm vào phiên đối chiếu hôm nay.");
            // Dam bao filter dang o hom nay
            LocalDate today = LocalDate.now();
            fromDateFilter.setValue(today);
            toDateFilter.setValue(today);
            if (clearDateFilterLink != null) clearDateFilterLink.setVisible(true);
            reload();
        } else {
            AppAlert.success(this, "Đã đồng bộ",
                    "Phiên hôm nay đã có đủ " + activeProducts.size() + " sản phẩm đang bán.");
            LocalDate today = LocalDate.now();
            fromDateFilter.setValue(today);
            toDateFilter.setValue(today);
            if (clearDateFilterLink != null) clearDateFilterLink.setVisible(true);
            applyFilters();
        }
    }

    @Override
    protected boolean deleteItem(StockReconciliation item) { return false; }

    @Override
    protected void onDataChanged() {
        reload();
    }

    private String discrepancyText(int discrepancy) {
        if (discrepancy == 0) return "Khớp";
        return (discrepancy > 0 ? "+" : "") + discrepancy;
    }

    private String discrepancyLabel(Object value) {
        return String.valueOf(value);
    }

    private Color discrepancyColor(Object value) {
        String label = String.valueOf(value);
        if ("Khớp".equals(label)) return AppColor.SUCCESS;
        return label.startsWith("+") ? AppColor.WARNING : AppColor.ERROR;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
        }
    }
}