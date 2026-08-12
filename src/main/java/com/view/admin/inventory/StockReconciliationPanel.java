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
import javax.swing.table.TableModel;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * Man hinh "Doi chieu / kiem ke kho cuoi ngay":
 * - Moi ngay 00:00 TU DONG khoa phien cu (khong sua ton thuc te nua) va tao
 *   phien moi cho ngay hom nay voi day du danh sach SP ACTIVE.
 * - Khi app dang mo, Timer chay moi 30s de phat hien diem chuyen ngay.
 * - Cac dong ngay HOM NAY: cho phep sua "Ton thuc te" truc tiep.
 * - Cac dong NGAY CU: icon KHOA, chi xem, khong chinh sua duoc.
 * - Sap xep: phien hom nay LUON o dau, cac phien cu theo thu tu ngay giam.
 * - Loc theo khoang ngay; mac dinh hien phien hom nay.
 */
public class StockReconciliationPanel extends BaseCrudPanel<StockReconciliation> {
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    /** Chi so cot model: Ton thuc te — cho phep sua truc tiep (CHI dong hom nay). */
    private static final int COL_ACTUAL = 3;
    private static final int COL_DIFF = 4;
    /** Tan suat kiem tra diem chuyen ngay (ms) — 30 giay mot lan. */
    private static final int DAY_ROLLOVER_CHECK_MS = 30_000;

    private final StockReconciliationDAO reconciliationDAO = new StockReconciliationDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private DatePickerField fromDateFilter;
    private DatePickerField toDateFilter;
    private JLabel clearDateFilterLink;
    /** Chan TableModelListener khi dang reload de tranh luu nham. */
    private boolean suppressActualEdit = false;
    /** Ngay ma Panel dang theo doi — khi thay doi nghia la da sang ngay moi. */
    private LocalDate trackedDate;
    /** Timer kiem tra diem chuyen ngay 00:00. */
    private Timer rolloverTimer;

    public StockReconciliationPanel() {
        super();
        trackedDate = LocalDate.now();

        // Cot: Ma SP | San pham | Ton he thong | Ton thuc te | Chenh lech | Nguoi doi chieu | Thoi gian
        table.setColumnWidths(90, 170, 100, 100, 90, 130, 130);
        table.setColumnMinWidths(70, 130, 80, 80, 70, 100, 110);
        table.setBadgeColumn(COL_DIFF, this::discrepancyLabel, this::discrepancyColor);

        // KHONG su dung setEditableColumns — chung ta se override isCellEditable
        // bang DefaultCellEditor tuy chinh + chan them trong TableModelListener.
        installConditionalActualEditor();

        // Cot "Ma SP" (index 0): them icon copy
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
                    c.setToolTipText("Click de copy ma san pham: " + text);
                } else {
                    c.setIcon(null);
                    c.setToolTipText(null);
                }
                return c;
            }
        });

        // Cot "Ton thuc te" (COL_ACTUAL): render khac biet theo ngay
        //  - Hom nay: icon BUT mau xanh → cho phep sua
        //  - Ngay cu: icon LOCK mau xam + chu mo → chi xem, da khoa
        //  → NEN CAC O DEU NHAU, KHONG to mau khac biet (khong gay khoi mat)
        table.getTable().getColumnModel().getColumn(COL_ACTUAL).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                        tbl, value, isSelected, hasFocus, row, column);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                int modelRow = tbl.convertRowIndexToModel(row);
                StockReconciliation item = rowToItem(modelRow);
                boolean isToday = item != null && item.getCreatedAt() != null
                        && item.getCreatedAt().toLocalDate().equals(LocalDate.now());

                // ✅ NEN LUON DEU NHAU — theo mau dong chan/le MAC DINH cua bang
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG
                        : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));

                if (isToday) {
                    // ——— DONG HOM NAY ———
                    c.setForeground(AppColor.TEXT_PRIMARY);
                    FontIcon editIcon = FontIcon.of(FontAwesomeSolid.PEN, 11);
                    editIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(editIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                    c.setToolTipText("Double-click hoac F2 de sua ton thuc te (phiên hom nay)");
                    c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    // ——— DONG NGAY CU (DA KHOA) ———
                    // Nen GIU NGUYEN nhu dong binh thuong, chi thay:
                    //   + chu → TEXT_MUTED (mo hon)
                    //   + icon → LOCK (xam) thay vi PEN (xanh)
                    //   + cursor → mac dinh (khong co ban tay)
                    c.setForeground(AppColor.TEXT_MUTED);
                    FontIcon lockIcon = FontIcon.of(FontAwesomeSolid.LOCK, 11);
                    lockIcon.setIconColor(AppColor.TEXT_MUTED);
                    c.setIcon(lockIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                    c.setToolTipText("Phiên đã khóa lúc 00:00 — chỉ xem, không sửa được");
                    c.setCursor(Cursor.getDefaultCursor());
                }
                return c;
            }
        });

        // Xu ly click vao icon copy ma SP
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
                        AppAlert.success(StockReconciliationPanel.this, "Copy thanh cong", "Da copy ma san pham: " + text);
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

        // Khoi tao Timer kiem tra diem chuyen ngay
        installDayRolloverTimer();

        // Tao phien hom nay (neu chua co) roi moi load
        ensureTodaySessionThenLoad();
    }

    // ------------------------------------------------------------------
    // Timer kiem tra diem chuyen ngay 00:00
    // ------------------------------------------------------------------
    private void installDayRolloverTimer() {
        rolloverTimer = new Timer(DAY_ROLLOVER_CHECK_MS, e -> rolloverToNewDayIfNeeded());
        rolloverTimer.setRepeats(true);
        rolloverTimer.setCoalesce(true);
        rolloverTimer.start();
    }

    /**
     * Neu LocalDate.now() da khac trackedDate nghia la da sang ngay moi
     * (qua moc 00:00). Khi do:
     *   1. Khoa toan bo phien cu (DAO already chi cho sua hom nay)
     *   2. Tao phien moi cho ngay hom nay
     *   3. Thong bao cho nguoi dung
     *   4. Chuyen filter ve hom nay + reload bang → SP cu tu dong ra sau
     */
    private void rolloverToNewDayIfNeeded() {
        LocalDate now = LocalDate.now();
        if (now.equals(trackedDate)) return; // chua sang ngay moi

        final LocalDate oldDate = trackedDate;
        trackedDate = now;

        Integer userId = currentUserId();
        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() {
                return userId != null
                        ? reconciliationDAO.ensureDailySession(now, userId)
                        : 0;
            }
            @Override
            protected void done() {
                try {
                    int created = get();
                    String msg = "Đã qua 00:00 — Phiên đối chiếu ngày "
                            + oldDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            + " đã bị KHÓA (không sửa được nữa). ";
                    if (created > 0) {
                        msg += "Đã tự động tạo phiên mới cho hôm nay (" + created + " SP).";
                    } else {
                        msg += "Đã sẵn sàng phiên mới cho hôm nay.";
                    }
                    AppAlert.info(StockReconciliationPanel.this, "Chuyển ngày", msg);
                    // Chuyen filter ve hom nay + reload → SP moi len dau, SP cu xuong cuoi
                    fromDateFilter.setValue(now);
                    toDateFilter.setValue(now);
                    if (clearDateFilterLink != null) clearDateFilterLink.setVisible(true);
                    reload();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    // ------------------------------------------------------------------
    // Editor "Ton thuc te" — CHI cho phep sua dong hom nay (3 lop chan)
    // ------------------------------------------------------------------
    private void installConditionalActualEditor() {
        // LOP 1 (UI thap nhat): CellEditor — KHONG CHO MO editor neu la ngay cu
        JTable jt = table.getTable();
        jt.getColumnModel().getColumn(COL_ACTUAL).setCellEditor(new DefaultCellEditor(new JTextField()) {
            @Override
            public boolean isCellEditable(EventObject anEvent) {
                if (!super.isCellEditable(anEvent)) return false;
                int row = jt.getEditingRow();
                if (row < 0) return false;
                int modelRow = jt.convertRowIndexToModel(row);
                StockReconciliation item = rowToItem(modelRow);
                boolean editable = item != null && item.getCreatedAt() != null
                        && item.getCreatedAt().toLocalDate().equals(LocalDate.now());
                if (!editable) {
                    SwingUtilities.invokeLater(() ->
                        AppAlert.warning(StockReconciliationPanel.this,
                                "Phiên đã khóa",
                                "Đã qua 00:00 — chỉ được sửa tồn thực tế của phiên hôm nay."));
                }
                return editable;
            }
        });

        // LOP 2 (Model): TableModelListener — bao ve them, neu lọt qua Lop 1
        table.getModel().addTableModelListener(e -> {
            if (suppressActualEdit) return;
            if (e.getType() != TableModelEvent.UPDATE) return;
            if (e.getColumn() != COL_ACTUAL && e.getColumn() != TableModelEvent.ALL_COLUMNS) return;
            int modelRow = e.getFirstRow();
            if (modelRow < 0) return;
            StockReconciliation item = rowToItem(modelRow);
            if (item == null) return;

            // Chan ngay cu
            if (item.getCreatedAt() == null
                    || !item.getCreatedAt().toLocalDate().equals(LocalDate.now())) {
                suppressActualEdit = true;
                try {
                    table.getModel().setValueAt(item.getActualStock(), modelRow, COL_ACTUAL);
                } finally {
                    suppressActualEdit = false;
                }
                AppAlert.error(this, "Khong the sua",
                        "Da qua 00:00 — phien cu da khoa. Chi duoc sua phien hom nay.");
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
                AppAlert.error(this, "So khong hop le", "Ton thuc te phai la so nguyen ≥ 0.");
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
                AppAlert.error(this, "Cap nhat that bai",
                        "Khong the cap nhat ton thuc te. Vui long thu lai.");
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
            AppAlert.success(this, "Da cap nhat",
                    "Ton thuc te \"" + item.getProductName() + "\" = " + newActual
                            + " (chenh lech: " + discrepancyText(item.getDiscrepancy()) + ")");
        });
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<StockReconciliation> result) {
        suppressActualEdit = true;
        try {
            table.getTable().repaint();
            // Sau moi lan reload, kiem tra lai xem co sang ngay moi khong
            // (phong truong hop mo app tu hom qua den sang hom sau)
            rolloverToNewDayIfNeeded();
        } finally {
            SwingUtilities.invokeLater(() -> suppressActualEdit = false);
        }
    }

    // ------------------------------------------------------------------
    // Khoi tao phien hom nay
    // ------------------------------------------------------------------
    private void ensureTodaySessionThenLoad() {
        Integer userId = currentUserId();
        if (userId == null) {
            initialLoad();
            return;
        }
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
                        AppAlert.success(StockReconciliationPanel.this, "Phien doi chieu hom nay",
                                "Da tao " + created + " san pham de doi chieu kho.");
                    }
                } catch (Exception ignored) {
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

    // ------------------------------------------------------------------
    // Bo loc ngay
    // ------------------------------------------------------------------
    private void buildDateFilterBar() {
        fromDateFilter = new DatePickerField(null, true);
        toDateFilter = new DatePickerField(null, true);
        JLabel fromLabel = new JLabel("Tu ngay");
        fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel toLabel = new JLabel("Den ngay");
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
        clearDateFilterLink.setToolTipText("Xoa loc ngay");
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

    // ------------------------------------------------------------------
    // Override metadata
    // ------------------------------------------------------------------
    @Override
    protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.BALANCE_SCALE; }

    @Override
    protected String getPageTitle() { return "Doi chieu kho cuoi ngay"; }

    @Override
    protected String getPageSubtitle() {
        return "00:00 tự động KHÓA phiên cũ + tạo phiên mới hôm nay — sửa tồn thực tế trực tiếp trên bảng";
    }

    @Override
    protected String getAddButtonLabel() { return "Dong bo SP hom nay"; }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Ma SP", "San pham", "Ton he thong", "Ton thuc te",
                "Chenh lech", "Nguoi doi chieu", "Thoi gian"};
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
    protected String getEntityLabel() { return "phien doi chieu kho"; }

    @Override
    protected String getItemDisplayName(StockReconciliation item) {
        return item.getProductName() + " - "
                + (item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "");
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
    protected String getSearchPlaceholder() { return "Tim theo ten san pham, ma SP, nguoi doi chieu..."; }

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
     * Neu da sang ngay moi ma chua tao phien → cung tao luon.
     */
    @Override
    protected void openForm(StockReconciliation item) {
        Integer userId = currentUserId();
        if (userId == null) return;
        // Kiem tra diem chuyen ngay truoc khi dong bo
        rolloverToNewDayIfNeeded();

        List<Product> activeProducts = productDAO.findAllActive();
        if (activeProducts.isEmpty()) {
            BaseDialog.info(this, "Khong co san pham",
                    "Chua co san pham dang ban nao de doi chieu. Vui long them san pham truoc.");
            return;
        }
        int created = reconciliationDAO.ensureDailySession(LocalDate.now(), userId);
        if (created > 0) {
            AppAlert.success(this, "Dong bo thanh cong",
                    "Da them " + created + " san pham vao phien doi chieu hom nay.");
            LocalDate today = LocalDate.now();
            fromDateFilter.setValue(today);
            toDateFilter.setValue(today);
            if (clearDateFilterLink != null) clearDateFilterLink.setVisible(true);
            reload();
        } else {
            AppAlert.success(this, "Da dong bo",
                    "Phien hom nay da co du " + activeProducts.size() + " san pham dang ban.");
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private String discrepancyText(int discrepancy) {
        if (discrepancy == 0) return "Khop";
        return (discrepancy > 0 ? "+" : "") + discrepancy;
    }

    private String discrepancyLabel(Object value) {
        return String.valueOf(value);
    }

    private Color discrepancyColor(Object value) {
        String label = String.valueOf(value);
        if ("Khop".equals(label)) return AppColor.SUCCESS;
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

    private static Color hexColor(String hex) {
        return Color.decode(hex);
    }
}