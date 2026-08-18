package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SettingsButton;
import com.event.AppEventBus;
import com.event.OrderStatusChangedEvent;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.dao.StockAlertDAO;
import com.model.NotificationItem;
import com.model.Order;
import com.model.Role;
import com.model.StockAlert;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.service.OrderNotifyPoller;
import com.service.StockAlertNotifyPoller;
import com.service.ReturnExchangeNotifyPoller;
import com.settings.NotificationSettings;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.utils.AppIcon;
import com.view.LoginFrame;
import com.view.admin.account.UserAccountPanel;
import com.view.admin.auditlog.AuditLogPanel;
import com.view.admin.category.CategoryPanel;
import com.view.admin.customer.CustomerPanel;
import com.view.admin.employee.EmployeePanel;
import com.view.admin.exceptionreport.ExceptionReportPanel;
import com.view.admin.inventory.InventoryBatchPanel;
import com.view.admin.inventory.InventoryOverviewPanel;
import com.view.admin.inventoryreport.InventoryReportPanel;
import com.view.admin.inventory.PurchaseReceiptPanel;
import com.view.admin.inventory.StockReconciliationPanel;
import com.view.admin.inventory.StockDisposalPanel;
import com.view.admin.inventory.SupplierReturnPanel;
import com.view.admin.invoice.InvoicePanel;
import com.view.admin.order.OrderPanel;
import com.view.admin.pos.PosPanel;
import com.view.admin.product.ProductPanel;
import com.view.admin.promotion.PromotionPanel;
import com.view.admin.report.RevenueReportPanel;
import com.view.admin.returnexchange.ReturnExchangePanel;
import com.view.admin.security.TwoFactorSettingsPanel;
import com.view.admin.shift.ShiftManagementPanel;
import com.view.admin.shift.ShiftMonitorPanel;
import com.view.admin.stockalert.StockAlertPanel;
import com.view.admin.supplier.SupplierPanel;
import com.view.admin.permission.RolePermissionPanel;
import com.view.client.ProfilePanel;
import com.view.layouts.MainLayout;
import com.ws.ChatClient;
import com.ws.ChatServer;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class AdminMainFrame extends JFrame {

    private MainLayout layout;

    private final List<NotificationItem> orderNotifications = new ArrayList<>();
    private final List<NotificationItem> chatNotifications = new ArrayList<>();

    /** Thông báo chuyển trạng thái đơn — tách khỏi orderNotifications (đơn MỚI). */
    private final List<NotificationItem> orderStatusNotifications = new ArrayList<>();

    private final List<NotificationItem> returnNotifications = new ArrayList<>();

    /** Cảnh báo tồn kho chưa xem (tự động + NV bán hàng báo thủ công). */
    private final List<NotificationItem> stockAlertNotifications = new ArrayList<>();

    private ChatPanel chatPanelRef;

    private String currentPageKey = "dashboard";

    private final Runnable onThemeChanged = this::rebuildContent;
    private final Runnable onLangChanged = this::rebuildContent;

    /**
     * Loading dùng chung toàn hệ thống.
     *
     * Không dùng RebuildOverlay.
     * Overlay được gắn vào glassPane một lần duy nhất khi khởi tạo frame.
     */
    private final LoadingOverlay themeLoadingOverlay = new LoadingOverlay();

    private final OrderNotifyPoller orderNotifyPoller =
            new OrderNotifyPoller();

    private final StockAlertNotifyPoller stockAlertNotifyPoller =
            new StockAlertNotifyPoller();

    private final ReturnExchangeNotifyPoller returnExchangeNotifyPoller =
            new ReturnExchangeNotifyPoller(this::onNewReturnNotifications);

    private final java.util.function.Consumer<OrderStatusChangedEvent>
            orderStatusListener = this::onOrderStatusChanged;

    public AdminMainFrame() {
        setTitle(Lang.get("admin.frame.title"));
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);

        AppIcon.apply(this);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        /*
         * ============================================================
         * CHAT SERVER / CLIENT
         * ============================================================
         */
        ChatServer.getInstance().start();

        if (AuthService.getInstance().isLoggedIn()) {
            var u = AuthService.getInstance().getCurrentUser();

            String role = u.getRole() != null
                    ? u.getRole().name()
                    : "";

            ChatClient.getInstance().connectStaff(
                    u.getUserId(),
                    u.getFullName(),
                    role
            );
        }

        /*
         * ============================================================
         * BUILD CONTENT BAN ĐẦU
         * ============================================================
         */
        buildContent();

        /*
         * Lui Settings sang trái để không đè bong bóng AI
         * 60px + khe 16px.
         */
        SettingsButton.attach(this, 52 + 16, true);

        /*
         * ============================================================
         * ORDER NOTIFICATION POLLER
         * ============================================================
         */
        orderNotifyPoller.onUnseenChanged((count, preview) -> {
            if (layout == null) {
                return;
            }

            layout.setBadge("orders", count);

            orderNotifications.clear();
            orderNotifications.addAll(
                    toNotificationItems(preview)
            );

            refreshHeaderNotifications();
        });

        orderNotifyPoller.start();

        /*
         * ============================================================
         * STOCK ALERT NOTIFICATION POLLER
         * ============================================================
         */
        stockAlertNotifyPoller.onUnseenChanged((count, preview) -> {
            if (layout == null) {
                return;
            }

            layout.setBadge("stockAlerts", count);

            stockAlertNotifications.clear();

            stockAlertNotifications.addAll(
                    toStockNotificationItems(
                            new StockAlertDAO()
                                    .getUnseenForInventoryManager()
                    )
            );

            refreshHeaderNotifications();
        });

        stockAlertNotifyPoller.start();

        /*
         * ============================================================
         * RETURN / EXCHANGE NOTIFICATION POLLER
         * ============================================================
         */
        returnExchangeNotifyPoller.start();

        /*
         * ============================================================
         * EVENT BUS
         * ============================================================
         */
        AppEventBus.getInstance()
                .subscribe(
                        OrderStatusChangedEvent.class,
                        orderStatusListener
                );

        /*
         * ============================================================
         * THEME / LANGUAGE LISTENER
         * ============================================================
         */
        ThemeManager.getInstance()
                .addRebuildListener(onThemeChanged);

        LanguageManager.getInstance()
                .addRebuildListener(onLangChanged);

        /*
         * ============================================================
         * WINDOW CLOSED
         * ============================================================
         */
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {

                ThemeManager.getInstance()
                        .removeRebuildListener(onThemeChanged);

                LanguageManager.getInstance()
                        .removeRebuildListener(onLangChanged);

                orderNotifyPoller.stop();

                stockAlertNotifyPoller.stop();

                returnExchangeNotifyPoller.stop();

                AppEventBus.getInstance()
                        .unsubscribe(
                                OrderStatusChangedEvent.class,
                                orderStatusListener
                        );

                ChatClient.getInstance().disconnect();

                AuthService.getInstance().logout();

                new LoginFrame();
            }
        });

        setVisible(true);

        /*
         * AI Assistant.
         */
        AdminAiAssistantWidget.install(this);

        /*
         * GlassPane phải được cài sau AdminAiAssistantWidget.install()
         * vì AI Assistant cũng có thể sử dụng glassPane.
         */
        installThemeLoadingOverlay();
    }

    /**
     * Kiểm tra user hiện tại có phải Quản lý kho hay không.
     */
    private boolean isInventoryManager() {
        var user = AuthService.getInstance().getCurrentUser();

        return user != null
                && user.getRole() == Role.INVENTORY_MANAGER;
    }

    /**
     * Kiểm tra user hiện tại có phải Quản lý bán hàng hay không.
     *
     * Quản lý bán hàng không hiện POS và một số chức năng kho.
     */
    private boolean isSalesManager() {
        var user = AuthService.getInstance().getCurrentUser();

        return user != null
                && user.getRole() == Role.SALES_MANAGER;
    }

    /**
     * Build toàn bộ MainLayout thành object độc lập.
     *
     * Hàm này không trực tiếp thay đổi JFrame.
     *
     * Vì vậy có thể chạy trong SwingWorker khi rebuild Theme/Language.
     */
    private MainLayout buildLayout() {

        setTitle(Lang.get("admin.frame.title"));

        MainLayout layout =
                new MainLayout(
                        Lang.get("admin.mainlayout.title")
                );

        /*
         * ============================================================
         * DASHBOARD
         * ============================================================
         *
         * Quản lý kho:
         *      Dashboard = InventoryOverviewPanel
         *
         * Role khác:
         *      Dashboard = DashboardPanel
         */
        JPanel dashboardPanel = isInventoryManager()
                ? new InventoryOverviewPanel()
                : new DashboardPanel();

        layout.addPage(
                "dashboard",
                Lang.get("sidebar.dashboard"),
                FontAwesomeSolid.TACHOMETER_ALT,
                dashboardPanel,
                AppPermission.DASHBOARD_VIEW
        );

        /*
         * ============================================================
         * USERS
         * ============================================================
         */
        layout.addSection(
                Lang.get("sidebar.section.users")
        );

        layout.addPage(
                "users",
                Lang.get("sidebar.users.short"),
                FontAwesomeSolid.USERS_COG,
                new UserAccountPanel(),
                AppPermission.USER_MANAGE,
                AppPermission.USER_EDIT,
                AppPermission.USER_VIEW
        );

        layout.addPage(
                "employees",
                Lang.get("sidebar.employees.short"),
                FontAwesomeSolid.USER_TIE,
                new EmployeePanel(),
                AppPermission.USER_MANAGE,
                AppPermission.USER_EDIT,
                AppPermission.USER_VIEW
        );

        layout.addPage(
                "customers",
                Lang.get("sidebar.customers.short"),
                FontAwesomeSolid.ID_CARD,
                new CustomerPanel(),
                AppPermission.CUSTOMER_MANAGE,
                AppPermission.CUSTOMER_EDIT,
                AppPermission.CUSTOMER_VIEW
        );

        /*
         * ============================================================
         * CATALOG
         * ============================================================
         */
        layout.addSection(
                Lang.get("sidebar.section.catalog")
        );

        layout.addPage(
                "categories",
                Lang.get("sidebar.categories.short"),
                FontAwesomeSolid.TAGS,
                new CategoryPanel(),
                AppPermission.CATEGORY_MANAGE,
                AppPermission.CATEGORY_EDIT,
                AppPermission.CATEGORY_VIEW
        );

        layout.addPage(
                "products",
                Lang.get("sidebar.products.short"),
                FontAwesomeSolid.BOX,
                new ProductPanel(),
                AppPermission.PRODUCT_MANAGE,
                AppPermission.PRODUCT_EDIT,
                AppPermission.PRODUCT_VIEW
        );

        layout.addPage(
                "suppliers",
                Lang.get("sidebar.suppliers.short"),
                FontAwesomeSolid.TRUCK,
                new SupplierPanel(),
                AppPermission.SUPPLIER_MANAGE,
                AppPermission.SUPPLIER_EDIT,
                AppPermission.SUPPLIER_VIEW
        );

        layout.addPage(
                "exceptionReport",
                Lang.get("sidebar.exceptionReport"),
                FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                new ExceptionReportPanel(),
                AppPermission.EXCEPTION_REPORT_VIEW,
                AppPermission.EXCEPTION_REPORT_CREATE,
                AppPermission.EXCEPTION_REPORT_HANDLE
        );

        /*
         * ============================================================
         * KHO
         * ============================================================
         *
         * Permission được map riêng theo từng chức năng:
         *
         * STOCK_VIEW
         *      -> Quản lý lô hàng
         *      (Tổng quan kho chỉ là Dashboard của Quản lý kho, không hiện menu riêng)
         *
         * STOCK_IMPORT
         *      -> Quản lý nhập kho
         *
         * STOCK_RECONCILE
         *      -> Kiểm kê / đối chiếu
         *
         * STOCK_DISPOSE / STOCK_DISPOSE_VIEW
         *      -> Tiêu hủy tồn kho
         *
         * STOCK_REPORT_VIEW
         *      -> Báo cáo hàng tồn kho
         *
         * Không ẩn cứng toàn bộ section theo role.
         * MainLayout sẽ tự kiểm tra permission của từng page.
         */
        layout.addSection(
                Lang.get("sidebar.section.warehouse")
        );

        /*
         * Tổng quan kho = dashboard riêng của Quản lý kho (đã gắn vào
         * page "dashboard" ở trên khi isInventoryManager()).
         *
         * KHÔNG đăng ký thêm menu "Tổng quan kho" cho role khác (NV bán
         * hàng / QL bán hàng...) dù họ có STOCK_VIEW — tránh nhầm với
         * "Tổng quan" (DASHBOARD_VIEW) và lộ màn hình kho cho sales.
         * STOCK_VIEW vẫn mở được các trang kho khác: lô hàng, v.v.
         */

        /*
         * Nhập kho.
         */
        layout.addPage(
                "purchaseReceipts",
                Lang.get("sidebar.purchaseReceipts"),
                FontAwesomeSolid.FILE_INVOICE,
                new PurchaseReceiptPanel(),
                AppPermission.STOCK_IMPORT
        );

        /*
         * Quản lý lô hàng.
         */
        layout.addPage(
                "inventoryBatches",
                Lang.get("sidebar.inventoryBatches"),
                FontAwesomeSolid.BOXES,
                new InventoryBatchPanel(),
                AppPermission.STOCK_VIEW
        );

        /*
         * Kiểm kê / đối chiếu.
         */
        layout.addPage(
                "stockReconciliation",
                Lang.get("sidebar.stockReconciliation"),
                FontAwesomeSolid.BALANCE_SCALE,
                new StockReconciliationPanel(),
                AppPermission.STOCK_RECONCILE
        );

        /*
         * Tiêu hủy tồn kho.
         */
        layout.addPage(
                "stockDisposal",
                Lang.get("sidebar.stockDisposal"),
                FontAwesomeSolid.TRASH,
                new StockDisposalPanel(),
                AppPermission.STOCK_DISPOSE,
                AppPermission.STOCK_DISPOSE_VIEW
        );

        /*
         * Trả hàng nhà cung cấp.
         */
        layout.addPage(
                "supplierReturn",
                Lang.get("sidebar.supplierReturn"),
                FontAwesomeSolid.UNDO,
                new SupplierReturnPanel(),
                AppPermission.SUPPLIER_RETURN_CREATE,
                AppPermission.SUPPLIER_RETURN_VIEW
        );

        /*
         * Cảnh báo tồn kho.
         */
        layout.addPage(
                "stockAlerts",
                Lang.get("sidebar.stockAlerts"),
                FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                new StockAlertPanel(),
                AppPermission.STOCK_ALERT_VIEW
        );

        /*
         * Báo cáo tồn kho.
         *
         * Đây là permission riêng:
         * STOCK_REPORT_VIEW
         */
        layout.addPage(
                "inventoryReport",
                Lang.get("sidebar.inventoryReport"),
                FontAwesomeSolid.WAREHOUSE,
                new InventoryReportPanel(),
                AppPermission.STOCK_REPORT_VIEW
        );

        /*
         * ============================================================
         * SALES
         * ============================================================
         */
        layout.addSection(
                Lang.get("sidebar.section.sales")
        );

        /*
         * Ca làm việc (mở/đóng ca + lịch sử).
         */
        layout.addPage(
                "shifts",
                Lang.get("sidebar.shifts"),
                FontAwesomeSolid.CLOCK,
                new ShiftManagementPanel(),
                AppPermission.SHIFT_OPERATE,
                AppPermission.SHIFT_VIEW_ALL
        );

        /*
         * Giám sát ca đang mở — phân quyền SHIFT_MONITOR
         * (mặc định: Admin + Quản lý bán hàng).
         */
        layout.addPage(
                "shiftMonitor",
                "Giám sát ca đang mở",
                FontAwesomeSolid.DESKTOP,
                new ShiftMonitorPanel(),
                AppPermission.SHIFT_MONITOR
        );

        /*
         * POS:
         *
         * SALES_MANAGER không được thấy POS.
         * Admin / nhân viên bán hàng vẫn thấy nếu có permission.
         */
        if (!isSalesManager()) {
            layout.addPage(
                    "pos",
                    Lang.get("sidebar.pos"),
                    FontAwesomeSolid.STORE,
                    new PosPanel(),
                    AppPermission.INVOICE_CREATE
            );
        }

        /*
         * Hóa đơn.
         */
        layout.addPage(
                "invoices",
                Lang.get("sidebar.invoices"),
                FontAwesomeSolid.RECEIPT,
                new InvoicePanel(),
                AppPermission.INVOICE_CREATE,
                AppPermission.INVOICE_CANCEL
        );

        /*
         * Đổi trả.
         */
        layout.addPage(
                "returnExchange",
                Lang.get("sidebar.returnExchange"),
                FontAwesomeSolid.EXCHANGE_ALT,
                new ReturnExchangePanel(),
                AppPermission.RETURN_EXCHANGE_CREATE,
                AppPermission.RETURN_EXCHANGE_APPROVE
        );

        /*
         * Báo cáo doanh thu / lợi nhuận.
         */
        layout.addPage(
                "revenueReport",
                Lang.get("sidebar.revenueReport"),
                FontAwesomeSolid.CHART_LINE,
                new RevenueReportPanel(),
                AppPermission.REVENUE_REPORT_VIEW,
                AppPermission.PROFIT_REPORT_VIEW
        );

        /*
         * Khuyến mãi.
         */
        layout.addPage(
                "promotions",
                Lang.get("sidebar.promotions"),
                FontAwesomeSolid.PERCENT,
                new PromotionPanel(),
                AppPermission.PROMOTION_MANAGE
        );

        /*
         * Đơn hàng.
         */
        layout.addPage(
                "orders",
                Lang.get("sidebar.orders.short"),
                FontAwesomeSolid.SHOPPING_CART,
                new OrderPanel(),
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE
        );

        /*
         * ============================================================
         * SUPPORT
         * ============================================================
         */
        layout.addSection(
                Lang.get("sidebar.section.support")
        );

        chatPanelRef = new ChatPanel();

        chatPanelRef.setOnUnreadCountChanged(
                count -> layout.setBadge("chat", count)
        );

        chatPanelRef.setOnUnreadNotifications(items -> {
            chatNotifications.clear();

            if (items != null) {
                chatNotifications.addAll(items);
            }

            refreshHeaderNotifications();
        });

        layout.addPage(
                "chat",
                Lang.get("sidebar.chat"),
                FontAwesomeSolid.COMMENT_DOTS,
                chatPanelRef
        );

        /*
         * ============================================================
         * SYSTEM
         * ============================================================
         */
        layout.addSection(
                Lang.get("sidebar.section.system")
        );

        /*
         * Settings hệ thống.
         */
        layout.addPage(
                "settings",
                Lang.get("sidebar.settings"),
                FontAwesomeSolid.COGS,
                new SettingsPanel(),
                AppPermission.SETTINGS_MANAGE
        );

        /*
         * ============================================================
         * RBAC
         * ============================================================
         *
         * Chỉ những user có RBAC_MANAGE mới thấy page.
         *
         * RolePermissionPanel dùng để:
         *      - xem vai trò
         *      - chỉnh permission của vai trò
         *      - quản lý phân quyền
         */
        layout.addPage(
                "rolePermissions",
                Lang.get("sidebar.rolePermissions"),
                FontAwesomeSolid.USER_SHIELD,
                new RolePermissionPanel(),
                AppPermission.RBAC_MANAGE
        );

        /*
         * ============================================================
         * 2FA
         * ============================================================
         *
         * Chỉ ADMIN.
         *
         * Đây là thiết lập cá nhân của tài khoản đang đăng nhập,
         * không phải quyền quản lý tài khoản khác.
         */
        if (AuthService.getInstance().isAdmin()) {
            layout.addPage(
                    "twoFactorSettings",
                    Lang.get("sidebar.twoFactor"),
                    FontAwesomeSolid.SHIELD_ALT,
                    new TwoFactorSettingsPanel()
            );
        }

        /*
         * Backup.
         */
        layout.addPage(
                "backup",
                Lang.get("sidebar.backup"),
                FontAwesomeSolid.SHIELD_ALT,
                new BackupRecoveryPanel(),
                AppPermission.BACKUP_MANAGE
        );

        /*
         * Audit log.
         */
        layout.addPage(
                "auditLogs",
                Lang.get("sidebar.auditLogs"),
                FontAwesomeSolid.HISTORY,
                new AuditLogPanel(),
                AppPermission.AUDIT_LOG_VIEW
        );

        /*
         * ============================================================
         * PROFILE
         * ============================================================
         */
        ProfilePanel profilePanel = new ProfilePanel();

        profilePanel.onSaved(this::rebuildContent);

        layout.addHiddenPage(
                "profile",
                profilePanel
        );

        layout.getHeader().onProfile(
                () -> layout.showPage("profile")
        );

        /*
         * ============================================================
         * PAGE CHANGE
         * ============================================================
         */
        layout.onPageChange(
                key -> currentPageKey = key
        );

        layout.showPage(currentPageKey);

        /*
         * ============================================================
         * LOGOUT
         * ============================================================
         */
        layout.onLogout(this::doLogout);

        /*
         * ============================================================
         * NOTIFICATION
         * ============================================================
         */
        layout.getHeader().onBellClick(null);

        layout.getHeader().onNotificationClick(item -> {

            if (item == null) {
                return;
            }

            dismissNotificationSource(item);

            if (item.getType() == NotificationItem.Type.MESSAGE) {

                layout.showPage("chat");

            } else if (item.getType() == NotificationItem.Type.ORDER) {

                layout.showPage("orders");

            } else if (item.getType() == NotificationItem.Type.STOCK) {

                if (!isSalesManager()) {
                    layout.showPage("stockAlerts");
                } else {
                    layout.showPage("dashboard");
                }

            } else if (item.getType() == NotificationItem.Type.RETURN) {

                layout.showPage("returnExchange");
            }
        });

        layout.getHeader().onNotificationDismiss(
                this::dismissNotificationSource
        );

        /*
         * ============================================================
         * CLEAR ALL NOTIFICATIONS
         * ============================================================
         */
        layout.getHeader().onClearAllNotifications(() -> {

            if (chatPanelRef != null) {
                chatPanelRef.clearAllUnread();
            }

            try {
                new com.dao.OrderDAO().markAllSeen();
            } catch (Exception e) {
                com.core.log.AppLogger.getInstance().error(
                        com.core.log.ErrorCode.ORDER_STATUS_UPDATE_FAIL,
                        "AdminMainFrame.onClearAllNotifications - markAllSeen that bai",
                        e
                );
            }

            try {
                new StockAlertDAO().markAllSeen();
            } catch (Exception e) {
                com.core.log.AppLogger.getInstance().error(
                        com.core.log.ErrorCode.DB_UPDATE_FAIL,
                        "AdminMainFrame.onClearAllNotifications - stockAlert markAllSeen that bai",
                        e
                );
            }

            orderNotifications.clear();
            orderStatusNotifications.clear();
            chatNotifications.clear();
            returnNotifications.clear();
            stockAlertNotifications.clear();

            layout.setBadge("orders", 0);
            layout.setBadge("chat", 0);
            layout.setBadge("returnExchange", 0);
            layout.setBadge("stockAlerts", 0);

            refreshHeaderNotifications();
        });

        return layout;
    }

    /**
     * Gắn MainLayout mới vào JFrame.
     *
     * Hàm này chỉ thực hiện thay đổi Swing component trên EDT.
     */
    private void applyLayoutResult(MainLayout newLayout) {

        getContentPane().removeAll();

        getContentPane().setBackground(
                AppColor.PAGE_BG
        );

        layout = newLayout;

        add(
                layout,
                BorderLayout.CENTER
        );

        /*
         * Nếu page hiện tại vẫn tồn tại trong layout mới
         * thì mở lại page đó.
         */
        layout.showPage(currentPageKey);

        refreshHeaderNotifications();

        revalidate();
        repaint();
    }

    /**
     * Build đồng bộ một lần khi khởi tạo frame.
     */
    private void buildContent() {
        applyLayoutResult(
                buildLayout()
        );
    }

    /**
     * ================================================================
     * REBUILD THEME / LANGUAGE
     * ================================================================
     *
     * Không sử dụng RebuildOverlay.
     *
     * Quy trình:
     *
     * 1. Hiện LoadingOverlay.
     * 2. Build MainLayout ở background thread.
     * 3. done() chạy trên EDT.
     * 4. Thay layout cũ bằng layout mới.
     * 5. Tắt LoadingOverlay.
     */
    private void rebuildContent() {

        themeLoadingOverlay.setBounds(
                0,
                0,
                getWidth(),
                getHeight()
        );

        themeLoadingOverlay.start(
                "Đang cập nhật giao diện..."
        );

        new SwingWorker<MainLayout, Void>() {

            @Override
            protected MainLayout doInBackground() {
                return buildLayout();
            }

            @Override
            protected void done() {

                try {

                    MainLayout newLayout = get();

                    applyLayoutResult(
                            newLayout
                    );

                    getLayeredPane().repaint();

                } catch (Exception ex) {

                    com.core.log.AppLogger.getInstance().error(
                            com.core.log.ErrorCode.UI_DATA_LOAD_FAIL,
                            "AdminMainFrame.rebuildContent - build lai giao dien loi",
                            ex
                    );

                } finally {

                    themeLoadingOverlay.stop();
                }
            }
        }.execute();
    }

    /**
     * ================================================================
     * INSTALL THEME LOADING OVERLAY
     * ================================================================
     *
     * GlassPane hiện tại có thể đã được AdminAiAssistantWidget
     * cài đặt.
     *
     * Vì vậy không ghi đè trực tiếp glassPane hiện tại.
     * Ta tạo một JPanel layered để chứa:
     *
     *      LoadingOverlay
     *      Existing GlassPane / AI Assistant
     *
     * Chỉ cài một lần duy nhất.
     */
    private void installThemeLoadingOverlay() {

        Component existingGlass = getGlassPane();

        themeLoadingOverlay.setBounds(
                0,
                0,
                getWidth(),
                getHeight()
        );

        if (existingGlass != null
                && existingGlass.isVisible()) {

            JPanel layered = new JPanel(null);

            layered.setOpaque(false);

            existingGlass.setBounds(
                    0,
                    0,
                    getWidth(),
                    getHeight()
            );

            layered.add(themeLoadingOverlay);
            layered.add(existingGlass);

            layered.addComponentListener(
                    new ComponentAdapter() {

                        @Override
                        public void componentResized(
                                ComponentEvent e
                        ) {

                            existingGlass.setBounds(
                                    0,
                                    0,
                                    layered.getWidth(),
                                    layered.getHeight()
                            );

                            themeLoadingOverlay.setBounds(
                                    0,
                                    0,
                                    layered.getWidth(),
                                    layered.getHeight()
                            );
                        }
                    }
            );

            setGlassPane(layered);

            layered.setVisible(true);

        } else {

            setGlassPane(
                    themeLoadingOverlay
            );
        }
    }

    /**
     * ================================================================
     * LOGOUT
     * ================================================================
     */
    private void doLogout() {

        boolean confirmed = BaseDialog.confirm(
                this,
                Lang.get("client.logout.confirm.title"),
                Lang.get("client.logout.confirm.message"),
                Lang.get("client.logout.confirm.button"),
                AppColor.ERROR,
                AppColor.ERROR_HOVER,
                FontAwesomeSolid.SIGN_OUT_ALT
        );

        if (confirmed) {
            dispose();
        }
    }

    /**
     * ================================================================
     * ORDER NOTIFICATION
     * ================================================================
     */
    private static List<NotificationItem> toNotificationItems(
            List<Order> orders
    ) {

        List<NotificationItem> items =
                new ArrayList<>();

        if (orders == null) {
            return items;
        }

        for (Order o : orders) {

            items.add(
                    new NotificationItem(
                            "order-" + o.getOrderId(),
                            NotificationItem.Type.ORDER,
                            Lang.get(
                                    "admin.header.notification.newOrder"
                            ) + " " + o.getOrderCode(),
                            o.getCustomerName()
                                    + " - "
                                    + o.getTotalAmount()
                                    + "đ",
                            o.getCreatedAt(),
                            o.getOrderId()
                    )
            );
        }

        return items;
    }

    /**
     * ================================================================
     * ORDER STATUS CHANGED
     * ================================================================
     */
    private void onOrderStatusChanged(
            OrderStatusChangedEvent evt
    ) {

        if (evt == null || layout == null) {
            return;
        }

        boolean canSeeOrders =
                PermissionManager.getInstance()
                        .can(AppPermission.ORDER_VIEW)
                        ||
                PermissionManager.getInstance()
                        .can(AppPermission.ORDER_MANAGE);

        if (!canSeeOrders) {
            return;
        }

        String code =
                evt.getOrderCode() != null
                        ? evt.getOrderCode()
                        : ("#" + evt.getOrderId());

        String title =
                switch (evt.getNewStatus()) {

                    case "CONFIRMED" ->
                            Lang.get(
                                    "admin.header.notification.orderConfirmed"
                            );

                    case "SHIPPING" ->
                            Lang.get(
                                    "admin.header.notification.orderShipping"
                            );

                    case "COMPLETED" ->
                            Lang.get(
                                    "admin.header.notification.orderCompleted"
                            );

                    case "CANCELLED" ->
                            Lang.get(
                                    "admin.header.notification.orderCancelled"
                            );

                    default ->
                            Lang.get(
                                    "admin.header.notification.orderUpdated"
                            );
                };

        String message =
                code
                        + (evt.isViaAssistant()
                        ? " · "
                        + Lang.get(
                                "admin.header.notification.viaAssistant"
                        )
                        : "");

        NotificationItem item =
                new NotificationItem(
                        "orderstatus-"
                                + evt.getOrderId()
                                + "-"
                                + System.currentTimeMillis(),
                        NotificationItem.Type.ORDER,
                        title,
                        message,
                        java.time.LocalDateTime.now(),
                        evt.getOrderId()
                );

        orderStatusNotifications.add(
                0,
                item
        );

        while (orderStatusNotifications.size() > 20) {

            orderStatusNotifications.remove(
                    orderStatusNotifications.size() - 1
            );
        }

        refreshHeaderNotifications();

        if (!NotificationSettings
                .getInstance()
                .isOrdersMuted()) {

            com.utils.NotificationSound.playDing();
        }
    }

    /**
     * ================================================================
     * RETURN / EXCHANGE NOTIFICATION
     * ================================================================
     */
    private void onNewReturnNotifications(
            List<NotificationItem> items
    ) {

        if (items == null || items.isEmpty()) {
            return;
        }

        for (NotificationItem item : items) {

            returnNotifications.removeIf(
                    n -> n.getId().equals(item.getId())
            );

            returnNotifications.add(
                    0,
                    item
            );
        }

        refreshHeaderNotifications();
    }

    /**
     * ================================================================
     * REFRESH HEADER NOTIFICATIONS
     * ================================================================
     */
    private void refreshHeaderNotifications() {

        if (layout == null
                || layout.getHeader() == null) {

            return;
        }

        List<NotificationItem> merged =
                new ArrayList<>();

        merged.addAll(chatNotifications);
        merged.addAll(orderNotifications);
        merged.addAll(orderStatusNotifications);
        merged.addAll(returnNotifications);
        merged.addAll(stockAlertNotifications);

        layout.setBadge(
                "returnExchange",
                returnNotifications.size()
        );

        layout.getHeader().setNotifications(
                merged
        );
    }

    /**
     * ================================================================
     * STOCK ALERT -> NOTIFICATION
     * ================================================================
     */
    private static List<NotificationItem> toStockNotificationItems(
            List<StockAlert> alerts
    ) {

        List<NotificationItem> items =
                new ArrayList<>();

        if (alerts == null) {
            return items;
        }

        for (StockAlert a : alerts) {

            String kind =
                    a.isOutOfStock()
                            ? "Hết hàng"
                            : "Sắp hết hàng";

            String who =
                    a.isAutoReported()
                            ? "Hệ thống (tự động)"
                            : (
                            a.getReportedByName() != null
                                    ? a.getReportedByName()
                                    : "NV bán hàng"
                    );

            String title =
                    kind
                            + " · "
                            + a.getProductName();

            String message =
                    a.getProductCode()
                            + " · còn "
                            + a.getStockAtReport()
                            + " · báo bởi "
                            + who;

            items.add(
                    new NotificationItem(
                            "stock-" + a.getAlertId(),
                            NotificationItem.Type.STOCK,
                            title,
                            message,
                            a.getCreatedAt(),
                            a.getAlertId()
                    )
            );
        }

        return items;
    }

    /**
     * ================================================================
     * DISMISS NOTIFICATION
     * ================================================================
     */
    private void dismissNotificationSource(
            NotificationItem item
    ) {

        if (item == null) {
            return;
        }

        /*
         * MESSAGE
         */
        if (item.getType()
                == NotificationItem.Type.MESSAGE) {

            if (chatPanelRef != null) {

                chatPanelRef.markNotificationRead(
                        item.getId()
                );
            }

            chatNotifications.removeIf(
                    n -> item.getId().equals(n.getId())
            );
        }

        /*
         * RETURN
         */
        else if (item.getType()
                == NotificationItem.Type.RETURN) {

            returnNotifications.removeIf(
                    n -> item.getId().equals(n.getId())
            );

            layout.setBadge(
                    "returnExchange",
                    returnNotifications.size()
            );
        }

        /*
         * STOCK
         */
        else if (item.getType()
                == NotificationItem.Type.STOCK) {

            /*
             * Đánh dấu toàn bộ cảnh báo tồn kho đã xem.
             */
            try {

                new StockAlertDAO()
                        .markAllSeen();

            } catch (Exception e) {

                com.core.log.AppLogger.getInstance().error(
                        com.core.log.ErrorCode.DB_UPDATE_FAIL,
                        "AdminMainFrame - stockAlert markAllSeen that bai",
                        e
                );
            }

            stockAlertNotifications.clear();

            layout.setBadge(
                    "stockAlerts",
                    0
            );
        }

        /*
         * ORDER
         */
        else if (item.getType()
                == NotificationItem.Type.ORDER) {

            Integer orderId =
                    item.getRefId();

            boolean isNewOrderNotification =
                    item.getId() != null
                            && item.getId()
                            .startsWith("order-");

            /*
             * Đơn hàng mới.
             */
            if (isNewOrderNotification
                    && orderId != null) {

                try {

                    new com.dao.OrderDAO()
                            .markSeen(orderId);

                } catch (Exception e) {

                    com.core.log.AppLogger.getInstance().error(
                            com.core.log.ErrorCode.ORDER_STATUS_UPDATE_FAIL,
                            "AdminMainFrame - markSeen that bai, orderId="
                                    + orderId,
                            e
                    );
                }

                orderNotifications.removeIf(
                        n -> item.getId().equals(n.getId())
                );

                layout.setBadge(
                        "orders",
                        orderNotifications.size()
                );

            }

            /*
             * Thông báo chuyển trạng thái đơn.
             */
            else {

                orderStatusNotifications.removeIf(
                        n -> item.getId().equals(n.getId())
                );
            }
        }

        /*
         * MESSAGE đã tự refresh ở ChatPanel.
         */
        if (item.getType()
                != NotificationItem.Type.MESSAGE) {

            refreshHeaderNotifications();
        }
    }
}