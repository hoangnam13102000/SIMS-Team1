package com.view.admin.auditlog;

import com.components.StatCard;
import com.components.crud.BaseCrudPanel;
import com.dao.AuditLogDAO;
import com.event.AutoRefresher;
import com.event.LogWrittenEvent;
import com.model.ActivityLog;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Man hinh "Nhat ky audit" cho Admin - CHI XEM, khong them/sua/xoa. Hien thi
 * du lieu that su tu bang AuditLogs (truoc day khong co man hinh nao doc
 * bang nay, va ban than bang cung khong duoc ghi vi AppLogger.setSink()
 * chua tung duoc goi - xem DbAuditLogSink).
 * <p>
 * Tu dong lam moi khi co dong log moi (LogWrittenEvent, ban tu
 * DbAuditLogSink sau moi lan insert thanh cong) de Admin dang mo trang nay
 * thay ngay thao tac cua nguoi khac ma khong can F5 thu cong.
 */
public class AuditLogPanel extends BaseCrudPanel<ActivityLog> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy");

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private JComboBox<String> actionFilter;
    private JComboBox<String> entityTypeFilter;
    private String selectedAction;
    private String selectedEntityType;

    /** Cac StatCard tren dau trang - gia tri duoc lam moi trong afterRender()
     *  moi lan bang tai lai (kem ca khi co LogWrittenEvent moi). */
    private StatCard totalCard;
    private StatCard todayCard;
    private StatCard failedLoginCard;
    private StatCard activeUserCard;

    public AuditLogPanel() {
        super();

        // Cột "Hành động" (index 2): StatBadge màu theo loại action — trực quan hơn plain text
        table.setBadgeColumn(2,
                v -> actionLabel(v == null ? null : String.valueOf(v)),
                v -> actionColor(v == null ? null : String.valueOf(v)));
        // Cột "Đối tượng" (index 3): badge tông nhẹ theo entity type
        table.setBadgeColumn(3,
                v -> entityLabel(v == null ? null : String.valueOf(v)),
                v -> entityColor(v == null ? null : String.valueOf(v)));

        // Cột "Mô tả" (index 4): thêm icon copy mã SP nếu có
        final FontIcon copyIconTemplate = FontIcon.of(FontAwesomeSolid.COPY, 12);
        copyIconTemplate.setIconColor(AppColor.ACCENT);
        table.getTable().getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
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
                if (extractProductCode(text) != null) {
                    FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 12);
                    copyIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(copyIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                } else {
                    c.setIcon(null);
                }
                c.setToolTipText(extractProductCode(text) != null ? "Click để copy mã sản phẩm" : null);
                return c;
            }
        });
        
        // Xử lý click vào icon copy mã SP
        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 4 && viewRow >= 0) {
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 4);
                    String text = value != null ? value.toString() : "";
                    String productCode = extractProductCode(text);
                    if (productCode != null) {
                        copyToClipboard(productCode);
                        JOptionPane.showMessageDialog(AuditLogPanel.this, 
                            "Đã copy mã sản phẩm: " + productCode, 
                            "Copy thành công", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });

        setupFilters();
        AutoRefresher.bind(this, LogWrittenEvent.class, 400, this::reload);
        initialLoad();
    }

    // ---------------------------------------------------------------
    // StatCard tong quan
    // ---------------------------------------------------------------

    @Override
    protected List<JComponent> buildStatsCards() {
        totalCard = new StatCard("Tổng nhật ký", "0", FontAwesomeSolid.HISTORY, AppColor.ACCENT);
        todayCard = new StatCard("Hoạt động hôm nay", "0", FontAwesomeSolid.BOLT, AppColor.SUCCESS);
        // Rút gọn nhãn để 4 card cân đều, tránh truncate khi cột hẹp
        failedLoginCard = new StatCard("Đăng nhập thất bại", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.ERROR);
        activeUserCard = new StatCard("Người dùng hoạt động", "0", FontAwesomeSolid.USERS, AppColor.WARNING);
        List<JComponent> cards = new ArrayList<>();
        cards.add(totalCard);
        cards.add(todayCard);
        cards.add(failedLoginCard);
        cards.add(activeUserCard);
        return cards;
    }

    /** Truy van lai so lieu tong quan va cap nhat 4 StatCard. */
    private void refreshStatsCards() {
        if (totalCard == null) return;
        SwingWorker<AuditLogDAO.AuditLogStats, Void> worker = new SwingWorker<>() {
            @Override protected AuditLogDAO.AuditLogStats doInBackground() {
                return auditLogDAO.getStatsSummary();
            }
            @Override protected void done() {
                try {
                    AuditLogDAO.AuditLogStats stats = get();
                    totalCard.setValue(NumberUtil.formatThousands(stats.totalLogs));
                    todayCard.setValue(NumberUtil.formatThousands(stats.todayLogs));
                    failedLoginCard.setValue(NumberUtil.formatThousands(stats.failedLoginsToday));
                    activeUserCard.setValue(NumberUtil.formatThousands(stats.activeUsersToday));
                } catch (Exception ignored) {
                    // Khong chan UI neu truy van thong ke loi - StatCard chi giu gia tri cu.
                }
            }
        };
        worker.execute();
    }

    // ---------------------------------------------------------------
    // Cau hinh hien thi
    // ---------------------------------------------------------------

    @Override protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.HISTORY; }
    @Override protected String getPageTitle() { return "Nhật ký audit"; }
    @Override protected String getPageSubtitle() { return "Lịch sử thao tác (thêm/sửa/xóa/đăng nhập...) của người dùng trong hệ thống"; }
    @Override protected String getAddButtonLabel() { return null; } // Chi xem - khong them moi
    @Override protected boolean supportsEdit() { return false; }
    @Override protected boolean supportsDelete() { return false; }
    @Override protected boolean supportsView() { return true; }
    @Override protected String getSearchPlaceholder() { return "Tìm theo người dùng, mô tả, đối tượng..."; }
    @Override protected String getEntityLabel() { return "nhật ký"; }
    @Override protected String getItemDisplayName(ActivityLog item) { return item.getDescription(); }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Thời gian", "Người dùng", "Hành động", "Đối tượng", "Mô tả"};
    }

    @Override
    protected Object[] mapRowToColumns(ActivityLog item) {
        // Cột Hành động / Đối tượng giữ raw code — setBadgeColumn sẽ map label + màu
        return new Object[]{
                item.getCreatedAt() != null ? DATE_FORMAT.format(item.getCreatedAt()) : "",
                item.getUsername() != null ? item.getUsername() : "SYSTEM",
                item.getAction(),
                item.getEntityType(),
                item.getDescription() != null ? item.getDescription() : ""
        };
    }

    // ---------------------------------------------------------------
    // Du lieu
    // ---------------------------------------------------------------

    @Override
    protected PaginationHelper.PaginationResult<ActivityLog> fetchPage(int page, int pageSize) {
        return auditLogDAO.filter(page, pageSize, null, selectedAction, selectedEntityType, null, null);
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<ActivityLog> result) {
        table.getTable().repaint();
        refreshStatsCards();
    }

    @Override
    protected PaginationHelper.PaginationResult<ActivityLog> searchPage(String keyword, int page, int pageSize) {
        return auditLogDAO.filter(page, pageSize, keyword, selectedAction, selectedEntityType, null, null);
    }

    @Override
    protected List<ActivityLog> fetchAllForExport() {
        return auditLogDAO.getRecentForExport();
    }

    /** Chi xem - khong ho tro them/sua nen khong bao gio duoc goi (getAddButtonLabel()==null, supportsEdit()==false). */
    @Override protected void openForm(ActivityLog item) { }

    /** Chi xem - khong ho tro xoa nen khong bao gio duoc goi (supportsDelete()==false). */
    @Override protected boolean deleteItem(ActivityLog item) { return false; }

    @Override
    protected void viewRow(int modelRow) {
        ActivityLog item = currentPageData != null && modelRow >= 0 && modelRow < currentPageData.size()
                ? currentPageData.get(modelRow) : null;
        if (item == null) return;
        AuditLogDetailDialog.show(SwingUtilities.getWindowAncestor(this), item,
                actionLabel(item.getAction()), entityLabel(item.getEntityType()));
    }

    // ---------------------------------------------------------------
    // Bo loc: Hanh dong / Doi tuong
    // ---------------------------------------------------------------

    private void setupFilters() {
        actionFilter = buildFilterCombo("Tất cả hành động", auditLogDAO.getDistinctActions(), AuditLogPanel::actionLabelStatic);
        actionFilter.addActionListener(e -> {
            int idx = actionFilter.getSelectedIndex();
            selectedAction = idx <= 0 ? null : auditLogDAO.getDistinctActions().get(idx - 1);
            applyFilters();
        });
        addToolbarFilter(actionFilter);

        entityTypeFilter = buildFilterCombo("Tất cả đối tượng", auditLogDAO.getDistinctEntityTypes(), AuditLogPanel::entityLabelStatic);
        entityTypeFilter.addActionListener(e -> {
            int idx = entityTypeFilter.getSelectedIndex();
            selectedEntityType = idx <= 0 ? null : auditLogDAO.getDistinctEntityTypes().get(idx - 1);
            applyFilters();
        });
        addToolbarFilter(entityTypeFilter);
    }

    private JComboBox<String> buildFilterCombo(String allLabel, List<String> rawValues, java.util.function.Function<String, String> labelMapper) {
        String[] items = new String[rawValues.size() + 1];
        items[0] = allLabel;
        for (int i = 0; i < rawValues.size(); i++) {
            items[i + 1] = labelMapper.apply(rawValues.get(i));
        }
        JComboBox<String> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setBackground(AppColor.WHITE);
        combo.setForeground(AppColor.TEXT_PRIMARY);
        combo.setPreferredSize(new Dimension(190, 38));
        combo.setFocusable(false);
        combo.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        return combo;
    }

    /** Giống các panel khác chưa publish DataChangedEvent - reload() trực tiếp sau khi đổi bộ lọc. */
    @Override
    protected void onDataChanged() {
        reload();
    }

    // ---------------------------------------------------------------
    // Nhan hien thi tieng Viet cho Action / EntityType
    // ---------------------------------------------------------------

    private static String actionLabelStatic(String action) { return actionLabel(action); }
    private static String entityLabelStatic(String entityType) { return entityLabel(entityType); }

    static String actionLabel(String action) {
        if (action == null) return "";
        switch (action) {
            case ActivityLog.ACTION_CREATE: return "Thêm mới";
            case ActivityLog.ACTION_UPDATE: return "Cập nhật";
            case ActivityLog.ACTION_DELETE: return "Xóa";
            case ActivityLog.ACTION_RESTORE: return "Khôi phục";
            case ActivityLog.ACTION_PERMANENT_DELETE: return "Xóa vĩnh viễn";
            case ActivityLog.ACTION_STATUS_CHANGE: return "Đổi trạng thái";
            case ActivityLog.ACTION_LOGIN: return "Đăng nhập";
            case ActivityLog.ACTION_LOGIN_FAILED: return "Đăng nhập thất bại";
            case ActivityLog.ACTION_LOGOUT: return "Đăng xuất";
            case ActivityLog.ACTION_PASSWORD_RESET: return "Đặt lại mật khẩu";
            case "USER_LOCK": return "Khóa tài khoản";
            case "USER_UNLOCK": return "Mở khóa tài khoản";
            default: return action;
        }
    }

    /**
     * Màu StatBadge theo hành động — đồng bộ tinh thần với AuditLogDetailDialog,
     * phân biệt rõ mức độ nghiêm trọng / loại thao tác.
     */
    static Color actionColor(String action) {
        if (action == null) return AppColor.TEXT_MUTED;
        switch (action) {
            case ActivityLog.ACTION_CREATE:
            case ActivityLog.ACTION_RESTORE:
            case "USER_UNLOCK":
                return AppColor.SUCCESS;          // xanh lá — tạo mới / khôi phục
            case ActivityLog.ACTION_LOGIN:
                return AppColor.TEAL;             // teal — đăng nhập
            case ActivityLog.ACTION_LOGOUT:
                return AppColor.INFO;             // xanh dương — đăng xuất
            case ActivityLog.ACTION_UPDATE:
            case ActivityLog.ACTION_STATUS_CHANGE:
                return AppColor.WARNING;          // cam — cập nhật / đổi trạng thái
            case ActivityLog.ACTION_PASSWORD_RESET:
                return AppColor.ORANGE != null ? AppColor.ORANGE : AppColor.WARNING;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
            case ActivityLog.ACTION_LOGIN_FAILED:
            case "USER_LOCK":
                return AppColor.ERROR;            // đỏ — xóa / thất bại / khóa
            default:
                return AppColor.ACCENT;
        }
    }

    static String entityLabel(String entityType) {
        if (entityType == null) return "";
        switch (entityType) {
            case ActivityLog.ENTITY_CATEGORY: return "Danh mục";
            case ActivityLog.ENTITY_CUSTOMER: return "Khách hàng";
            case ActivityLog.ENTITY_SUPPLIER: return "Nhà cung cấp";
            case ActivityLog.ENTITY_EMPLOYEE: return "Nhân viên";
            case ActivityLog.ENTITY_PRODUCT: return "Sản phẩm";
            case ActivityLog.ENTITY_USER:
            case "Users":
                return "Tài khoản";
            case ActivityLog.ENTITY_ORDER: return "Đơn hàng";
            case ActivityLog.ENTITY_INVENTORY_BATCH: return "Lô hàng";
            case ActivityLog.ENTITY_INVOICE: return "Hóa đơn";
            case ActivityLog.ENTITY_PURCHASE_RECEIPT: return "Phiếu nhập kho";
            case ActivityLog.ENTITY_STOCK_ALERT: return "Cảnh báo tồn kho";
            case ActivityLog.ENTITY_PHONE: return "Điện thoại";
            default: return entityType;
        }
    }

    /** Màu badge nhẹ cho cột Đối tượng — phân nhóm domain. */
    static Color entityColor(String entityType) {
        if (entityType == null) return AppColor.TEXT_MUTED;
        switch (entityType) {
            case ActivityLog.ENTITY_USER:
            case "Users":
            case ActivityLog.ENTITY_EMPLOYEE:
                return AppColor.ACCENT;
            case ActivityLog.ENTITY_CUSTOMER:
                return AppColor.BLUE;
            case ActivityLog.ENTITY_PRODUCT:
            case ActivityLog.ENTITY_CATEGORY:
                return AppColor.TEAL;
            case ActivityLog.ENTITY_INVOICE:
            case ActivityLog.ENTITY_ORDER:
                return AppColor.SUCCESS;
            case ActivityLog.ENTITY_SUPPLIER:
            case ActivityLog.ENTITY_PURCHASE_RECEIPT:
            case ActivityLog.ENTITY_INVENTORY_BATCH:
                return AppColor.WARNING;
            case ActivityLog.ENTITY_STOCK_ALERT:
                return AppColor.ERROR;
            default:
                return AppColor.TEXT_MUTED;
        }
    }

    // ---------------------------------------------------------------
    // Helper: extract & copy mã sản phẩm
    // ---------------------------------------------------------------

    /**
     * Trích xuất mã sản phẩm từ chuỗi mô tả.
     * Pattern: "mã SPxxx" hoặc chỉ "SPxxx" (SP + chữ số/chữ cái).
     * Ví dụ: "Đã thêm mới sản phẩm ... với mã SP001" → "SP001"
     */
    private static String extractProductCode(String text) {
        if (text == null || text.isBlank()) return null;
        // Ưu tiên pattern "mã SPxxx"
        Pattern p1 = Pattern.compile("mã\\s+(SP[A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return m1.group(1).toUpperCase();
        }
        // Pattern dự phòng: tìm SPxxx đứng độc lập
        Pattern p2 = Pattern.compile("\\b(SP[A-Z0-9]{2,})\\b", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(1).toUpperCase();
        }
        return null;
    }

    /** Copy chuỗi vào clipboard hệ thống. */
    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
            // Bỏ qua nếu không copy được (trường hợp hiếm)
        }
    }
}