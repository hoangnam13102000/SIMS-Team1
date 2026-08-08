package com.view.admin;

import com.components.BaseDialog;
import com.components.SettingsButton;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.model.NotificationItem;
import com.model.Order;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.service.OrderNotifyPoller;
import com.service.StockAlertNotifyPoller;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.view.LoginFrame;
import com.view.admin.account.UserAccountPanel;
import com.view.admin.auditlog.AuditLogPanel;
import com.view.admin.category.CategoryPanel;
import com.view.admin.customer.CustomerPanel;
import com.view.admin.employee.EmployeePanel;
import com.view.admin.exceptionreport.ExceptionReportPanel;
import com.view.admin.inventory.InventoryBatchPanel;
import com.view.admin.inventoryreport.InventoryReportPanel;
import com.view.admin.inventory.PurchaseReceiptPanel;
import com.view.admin.inventory.StockReconciliationPanel;
import com.view.admin.inventory.StockDisposalPanel;
import com.view.admin.invoice.InvoicePanel;
import com.view.admin.order.OrderPanel;
import com.view.admin.pos.PosPanel;
import com.view.admin.product.ProductPanel;
import com.view.admin.report.RevenueReportPanel;
import com.view.admin.returnexchange.ReturnExchangePanel;
import com.view.admin.stockalert.StockAlertPanel;
import com.view.admin.supplier.SupplierPanel;
import com.view.client.ProfilePanel;
import com.view.layouts.MainLayout;
import com.ws.ChatClient;
import com.ws.ChatServer;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

public class AdminMainFrame extends JFrame {

    private MainLayout layout;
    private final List<NotificationItem> orderNotifications = new ArrayList<>();
    private final List<NotificationItem> chatNotifications = new ArrayList<>();
    private ChatPanel chatPanelRef;
    private String currentPageKey = "dashboard";
    private final Runnable onThemeChanged = this::rebuildContent;
    private final Runnable onLangChanged = this::rebuildContent;
    private final OrderNotifyPoller orderNotifyPoller = new OrderNotifyPoller();
    private final StockAlertNotifyPoller stockAlertNotifyPoller = new StockAlertNotifyPoller();

    public AdminMainFrame() {
        setTitle(Lang.get("admin.frame.title"));
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        ChatServer.getInstance().start();
        if (AuthService.getInstance().isLoggedIn()) {
            var u = AuthService.getInstance().getCurrentUser();
            String role = u.getRole() != null ? u.getRole().name() : "";
            ChatClient.getInstance().connectStaff(u.getUserId(), u.getFullName(), role);
        }
        buildContent();

        // Lui Settings sang trái để không đè bong bóng AI (60px) + khe 16px
        SettingsButton.attach(this, 60 + 16, true);

        orderNotifyPoller.onUnseenChanged((count, preview) -> {
            layout.setBadge("orders", count);
            orderNotifications.clear();
            orderNotifications.addAll(toNotificationItems(preview));
            refreshHeaderNotifications();
        });
        orderNotifyPoller.start();

        stockAlertNotifyPoller.onUnseenChanged((count, preview) -> layout.setBadge("stockAlerts", count));
        stockAlertNotifyPoller.start();

        ThemeManager.getInstance().addRebuildListener(onThemeChanged);
        LanguageManager.getInstance().addRebuildListener(onLangChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ThemeManager.getInstance().removeRebuildListener(onThemeChanged);
                LanguageManager.getInstance().removeRebuildListener(onLangChanged);
                orderNotifyPoller.stop();
                stockAlertNotifyPoller.stop();
                ChatClient.getInstance().disconnect();
                AuthService.getInstance().logout();
                new LoginFrame();
            }
        });

        setVisible(true);
        AdminAiAssistantWidget.install(this);
    }

    private void buildContent() {
        setTitle(Lang.get("admin.frame.title"));
        if (layout != null) {
            remove(layout);
        }
        getContentPane().setBackground(AppColor.PAGE_BG);

        layout = new MainLayout(Lang.get("admin.mainlayout.title"));

        layout.addPage("dashboard", Lang.get("sidebar.dashboard"), FontAwesomeSolid.TACHOMETER_ALT, new DashboardPanel(), AppPermission.DASHBOARD_VIEW);

        layout.addSection(Lang.get("sidebar.section.users"));
        layout.addPage("users", Lang.get("sidebar.users.short"), FontAwesomeSolid.USERS_COG, new UserAccountPanel(), AppPermission.USER_MANAGE);
        layout.addPage("employees", Lang.get("sidebar.employees.short"), FontAwesomeSolid.USER_TIE, new EmployeePanel(), AppPermission.USER_MANAGE);
        layout.addPage("customers", Lang.get("sidebar.customers.short"), FontAwesomeSolid.ID_CARD, new CustomerPanel(), AppPermission.CUSTOMER_MANAGE);

        layout.addSection(Lang.get("sidebar.section.catalog"));
        layout.addPage("categories", Lang.get("sidebar.categories.short"), FontAwesomeSolid.TAGS, new CategoryPanel(), AppPermission.CATEGORY_MANAGE);
        layout.addPage("products", Lang.get("sidebar.products.short"), FontAwesomeSolid.BOX, new ProductPanel(),
                AppPermission.PRODUCT_MANAGE, AppPermission.PRODUCT_VIEW);
        layout.addPage("suppliers", Lang.get("sidebar.suppliers.short"), FontAwesomeSolid.TRUCK, new SupplierPanel(), AppPermission.SUPPLIER_MANAGE);
        layout.addPage("exceptionReport", Lang.get("sidebar.exceptionReport"), FontAwesomeSolid.EXCLAMATION_TRIANGLE, new ExceptionReportPanel(),
                AppPermission.EXCEPTION_REPORT_CREATE, AppPermission.EXCEPTION_REPORT_HANDLE);

        layout.addSection(Lang.get("sidebar.section.warehouse"));
        layout.addPage("purchaseReceipts", Lang.get("sidebar.purchaseReceipts"), FontAwesomeSolid.FILE_INVOICE, new PurchaseReceiptPanel(),
                AppPermission.STOCK_IMPORT, AppPermission.STOCK_VIEW);
        layout.addPage("inventoryBatches", Lang.get("sidebar.inventoryBatches"), FontAwesomeSolid.BOXES, new InventoryBatchPanel(),
                AppPermission.STOCK_IMPORT, AppPermission.STOCK_VIEW);
        layout.addPage("stockReconciliation", Lang.get("sidebar.stockReconciliation"), FontAwesomeSolid.BALANCE_SCALE, new StockReconciliationPanel(),
                AppPermission.STOCK_RECONCILE);
        layout.addPage("stockDisposal", Lang.get("sidebar.stockDisposal"), FontAwesomeSolid.TRASH, new StockDisposalPanel(),
                AppPermission.STOCK_DISPOSE, AppPermission.STOCK_DISPOSE_VIEW);
        layout.addPage("stockAlerts", Lang.get("sidebar.stockAlerts"), FontAwesomeSolid.EXCLAMATION_TRIANGLE, new StockAlertPanel(),
                AppPermission.STOCK_ALERT_VIEW);
        layout.addPage("inventoryReport", Lang.get("sidebar.inventoryReport"), FontAwesomeSolid.WAREHOUSE, new InventoryReportPanel(),
                AppPermission.STOCK_VIEW);

        layout.addSection(Lang.get("sidebar.section.sales"));
        layout.addPage("pos", Lang.get("sidebar.pos"), FontAwesomeSolid.STORE, new PosPanel(),
                AppPermission.INVOICE_CREATE);
        layout.addPage("invoices", Lang.get("sidebar.invoices"), FontAwesomeSolid.RECEIPT, new InvoicePanel(),
                AppPermission.INVOICE_CREATE, AppPermission.INVOICE_CANCEL);
        layout.addPage("returnExchange", Lang.get("sidebar.returnExchange"), FontAwesomeSolid.EXCHANGE_ALT, new ReturnExchangePanel(),
                AppPermission.RETURN_EXCHANGE_CREATE, AppPermission.RETURN_EXCHANGE_APPROVE);
        layout.addPage("revenueReport", Lang.get("sidebar.revenueReport"), FontAwesomeSolid.CHART_LINE, new RevenueReportPanel(),
                AppPermission.REVENUE_REPORT_VIEW, AppPermission.PROFIT_REPORT_VIEW);

        layout.addPage("orders", Lang.get("sidebar.orders.short"), FontAwesomeSolid.SHOPPING_CART, new OrderPanel(),
                AppPermission.ORDER_VIEW, AppPermission.ORDER_MANAGE);

        layout.addSection(Lang.get("sidebar.section.support"));
        chatPanelRef = new ChatPanel();
        chatPanelRef.setOnUnreadCountChanged(count -> layout.setBadge("chat", count));
        chatPanelRef.setOnUnreadNotifications(items -> {
            chatNotifications.clear();
            if (items != null) chatNotifications.addAll(items);
            refreshHeaderNotifications();
        });
        layout.addPage("chat", Lang.get("sidebar.chat"), FontAwesomeSolid.COMMENT_DOTS, chatPanelRef);

        layout.addSection(Lang.get("sidebar.section.system"));
        layout.addPage("settings", Lang.get("sidebar.settings"), FontAwesomeSolid.COGS, new SettingsPanel(),
                AppPermission.SETTINGS_MANAGE);
        layout.addPage("backup", Lang.get("sidebar.backup"), FontAwesomeSolid.SHIELD_ALT, new BackupRecoveryPanel(),
                AppPermission.BACKUP_MANAGE);
        layout.addPage("auditLogs", Lang.get("sidebar.auditLogs"), FontAwesomeSolid.HISTORY, new AuditLogPanel(),
                AppPermission.AUDIT_LOG_VIEW);

        ProfilePanel profilePanel = new ProfilePanel();
        profilePanel.onSaved(this::rebuildContent);
        layout.addHiddenPage("profile", profilePanel);
        layout.getHeader().onProfile(() -> layout.showPage("profile"));

        layout.onPageChange(key -> currentPageKey = key);
        layout.showPage(currentPageKey);
        layout.onLogout(this::doLogout);

        layout.getHeader().onBellClick(null);
        layout.getHeader().onNotificationClick(item -> {
            if (item == null) return;
            dismissNotificationSource(item);
            if (item.getType() == NotificationItem.Type.MESSAGE) {
                layout.showPage("chat");
            } else if (item.getType() == NotificationItem.Type.ORDER) {
                layout.showPage("orders");
            } else if (item.getType() == NotificationItem.Type.STOCK) {
                layout.showPage("stockAlerts");
            }
        });
        layout.getHeader().onNotificationDismiss(this::dismissNotificationSource);
        layout.getHeader().onClearAllNotifications(() -> {
            if (chatPanelRef != null) chatPanelRef.clearAllUnread();
            try {
                new com.dao.OrderDAO().markAllSeen();
            } catch (Exception e) {
                // Badge tren UI van duoc xoa ngay ben duoi du DB update loi (trai nghiem nguoi
                // dung uu tien hon), nhung phai ghi log vi cho toi lan sau se lai bao "chua xem".
                com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.ORDER_STATUS_UPDATE_FAIL,
                        "AdminMainFrame.onClearAllNotifications - markAllSeen that bai", e);
            }
            orderNotifications.clear();
            chatNotifications.clear();
            layout.setBadge("orders", 0);
            layout.setBadge("chat", 0);
            refreshHeaderNotifications();
        });
        refreshHeaderNotifications();
        add(layout, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private void rebuildContent() {
        buildContent();
        getLayeredPane().repaint();
    }

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

    private static List<NotificationItem> toNotificationItems(List<Order> orders) {
        List<NotificationItem> items = new ArrayList<>();
        for (Order o : orders) {
            items.add(new NotificationItem(
                "order-" + o.getOrderId(),
                NotificationItem.Type.ORDER,
                Lang.get("admin.header.notification.newOrder") + " " + o.getOrderCode(),
                o.getCustomerName() + " - " + o.getTotalAmount() + "đ",
                o.getCreatedAt(),
                o.getOrderId()
            ));
        }
        return items;
    }

    private void refreshHeaderNotifications() {
        if (layout == null || layout.getHeader() == null) return;
        List<NotificationItem> merged = new ArrayList<>();
        merged.addAll(chatNotifications);
        merged.addAll(orderNotifications);
        layout.getHeader().setNotifications(merged);
    }

    private void dismissNotificationSource(NotificationItem item) {
        if (item == null) return;
        if (item.getType() == NotificationItem.Type.MESSAGE) {
            if (chatPanelRef != null) {
                chatPanelRef.markNotificationRead(item.getId());
            }
            chatNotifications.removeIf(n -> item.getId().equals(n.getId()));
        } else if (item.getType() == NotificationItem.Type.ORDER) {
            Integer orderId = item.getRefId();
            if (orderId != null) {
                try {
                    new com.dao.OrderDAO().markSeen(orderId);
                } catch (Exception e) {
                    com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.ORDER_STATUS_UPDATE_FAIL,
                            "AdminMainFrame - markSeen that bai, orderId=" + orderId, e);
                }
            }
            orderNotifications.removeIf(n -> item.getId().equals(n.getId()));
            layout.setBadge("orders", orderNotifications.size());
        }
        if (item.getType() != NotificationItem.Type.MESSAGE) {
            refreshHeaderNotifications();
        }
    }
}