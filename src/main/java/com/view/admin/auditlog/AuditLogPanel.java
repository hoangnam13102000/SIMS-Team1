package com.view.admin.auditlog;

import com.components.StatCard;
import com.components.crud.BaseCrudPanel;
import com.components.table.AutoRowNumber;
import com.dao.AuditLogDAO;
import com.event.AutoRefresher;
import com.event.LogWrittenEvent;
import com.model.ActivityLog;
import com.theme.AppColor;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

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

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private JComboBox<String> actionFilter;
    private JComboBox<String> entityTypeFilter;
    private String selectedAction;
    private String selectedEntityType;

    /** Cot STT tu dong danh so theo trang - xem AutoRowNumber. Truoc day panel
     *  nay chua goi setAutoRowNumberColumn nen cot STT luon rong. */
    private AutoRowNumber stt;

    /** Cac StatCard tren dau trang - gia tri duoc lam moi trong afterRender()
     *  moi lan bang tai lai (kem ca khi co LogWrittenEvent moi). */
    private StatCard totalCard;
    private StatCard todayCard;
    private StatCard failedLoginCard;
    private StatCard activeUserCard;

    public AuditLogPanel() {
        super();
        stt = table.setAutoRowNumberColumn(0);
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
        return new String[]{"STT", "Thời gian", "Người dùng", "Hành động", "Đối tượng", "Mô tả"};
    }

    @Override
    protected Object[] mapRowToColumns(ActivityLog item) {
        return new Object[]{
                "",
                item.getCreatedAt() != null ? DATE_FORMAT.format(item.getCreatedAt()) : "",
                item.getUsername() != null ? item.getUsername() : "SYSTEM",
                actionLabel(item.getAction()),
                entityLabel(item.getEntityType()),
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

    /** STT phải tính theo đúng trang đang xem (giống các panel khác); đồng thời
     *  làm mới StatCard mỗi khi bảng tải lại (kể cả do LogWrittenEvent). */
    @Override
    protected void afterRender(PaginationHelper.PaginationResult<ActivityLog> result) {
        stt.setPageOffset((result.getCurrentPage() - 1) * result.getPageSize());
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
}