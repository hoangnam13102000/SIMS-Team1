package com.view.admin;

import com.components.BaseDialog;
import com.components.SettingsButton;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.view.LoginFrame;
import com.view.client.ProfilePanel;
import com.view.layouts.MainLayout;
import com.view.admin.account.UserAccountPanel;
import com.view.admin.category.CategoryPanel;
import com.view.admin.customer.CustomerPanel;
import com.view.admin.employee.EmployeePanel;
import com.view.admin.product.ProductPanel;
import com.view.admin.supplier.SupplierPanel;
import com.view.admin.inventory.InventoryBatchPanel;
import com.view.admin.inventory.PurchaseReceiptPanel;
import com.view.admin.inventory.StockReconciliationPanel;
import com.view.admin.order.OrderPanel;
import com.view.admin.invoice.InvoicePanel;
import com.view.admin.report.RevenueReportPanel;
import com.view.admin.pos.PosPanel;
import com.view.admin.stockalert.StockAlertPanel;
import com.view.admin.auditlog.AuditLogPanel;
import com.service.OrderNotifyPoller;
import com.service.StockAlertNotifyPoller;
import com.view.admin.returnexchange.ReturnExchangePanel;
import com.view.admin.exceptionreport.ExceptionReportPanel;
import com.ws.ChatServer;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class AdminMainFrame extends JFrame {

    private MainLayout layout;
    private String currentPageKey = "dashboard";
    private final Runnable onThemeChanged = this::rebuildContent;
    private final Runnable onLangChanged = this::rebuildContent;
    // Polling dinh ky xuong DB de phat hien don hang online moi (xem
    // com.service.OrderNotifyPoller) - truoc day class nay ton tai san
    // nhung chua bao gio duoc start() o dau ca nen KHONG co chuong/thong
    // bao nao khi co hoa don/don hang online moi.
    private final OrderNotifyPoller orderNotifyPoller = new OrderNotifyPoller();
    // Polling tuong tu cho bao cao het/sap het hang cua NV ban hang (xem
    // com.service.StockAlertNotifyPoller) - cap nhat badge rieng o muc
    // "stockAlerts" tren Sidebar, khong dung chung chuong voi don hang.
    private final StockAlertNotifyPoller stockAlertNotifyPoller = new StockAlertNotifyPoller();

    public AdminMainFrame() {
        setTitle(Lang.get("admin.frame.title"));
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 680));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        // Server WebSocket cho chat ho tro khach hang real-time (xem com.ws.ChatServer).
        ChatServer.getInstance().start();

        buildContent();

        // Nut cai dat (Sang/Toi, Ngon ngu) noi goc phai duoi man hinh.
        SettingsButton.attach(this);

        // Moi khi co don hang online moi (SeenByAdmin=0 tang len): cap nhat
        // dropdown thong bao + cham do o chuong tren Header, va so badge o
        // muc "orders" tren Sidebar. Dang ky 1 lan duy nhat o day - vi lambda
        // doc truc tiep field "layout" (khong phai bien local), no van luon
        // tro dung MainLayout/Header MOI NHAT sau moi lan rebuildContent().
        orderNotifyPoller.onUnseenChanged((count, preview) -> {
            layout.setBadge("orders", count);
            layout.getHeader().setNotifications(preview);
            layout.getHeader().setNotificationBadge(count > 0);
        });
        orderNotifyPoller.start();

        // Moi khi co bao cao het/sap het hang moi tu NV ban hang: cap nhat
        // so badge o muc "stockAlerts" tren Sidebar (rieng, khong dung
        // chung chuong Header voi don hang online).
        stockAlertNotifyPoller.onUnseenChanged((count, preview) -> layout.setBadge("stockAlerts", count));
        stockAlertNotifyPoller.start();

        // Moi khi ThemeManager doi theme (Light/Dark), xay lai toan bo noi
        // dung de tat ca component doc lai dung mau + FlatLaf UI moi nhat.
        ThemeManager.getInstance().addRebuildListener(onThemeChanged);

        // Moi khi LanguageManager doi ngon ngu (Viet/Anh), xay lai toan bo
        // noi dung de tat ca component doc lai chuoi dich moi.
        LanguageManager.getInstance().addRebuildListener(onLangChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ThemeManager.getInstance().removeRebuildListener(onThemeChanged);
                LanguageManager.getInstance().removeRebuildListener(onLangChanged);
                orderNotifyPoller.stop();
                stockAlertNotifyPoller.stop();
                ChatServer.getInstance().stopServer();
                AuthService.getInstance().logout();
                new LoginFrame();
            }
        });

        setVisible(true);
    }

    /** Xay (hoac xay lai) toan bo noi dung ben trong frame - MainLayout + cac trang. */
    private void buildContent() {
        setTitle(Lang.get("admin.frame.title"));
        if (layout != null) {
            remove(layout);
        }
        getContentPane().setBackground(AppColor.PAGE_BG);

        layout = new MainLayout(Lang.get("admin.mainlayout.title"));

        // --- Tổng quan ---
        layout.addPage("dashboard", Lang.get("sidebar.dashboard"), FontAwesomeSolid.TACHOMETER_ALT, new DashboardPanel(), AppPermission.DASHBOARD_VIEW);

        // --- Nhóm Người dùng ---
        layout.addSection(Lang.get("sidebar.section.users"));
        layout.addPage("users", Lang.get("sidebar.users.short"), FontAwesomeSolid.USERS_COG, new UserAccountPanel(), AppPermission.USER_MANAGE);
        layout.addPage("employees", Lang.get("sidebar.employees.short"), FontAwesomeSolid.USER_TIE, new EmployeePanel(), AppPermission.USER_MANAGE);
        layout.addPage("customers", Lang.get("sidebar.customers.short"), FontAwesomeSolid.ID_CARD, new CustomerPanel(), AppPermission.CUSTOMER_MANAGE);

        // --- Nhóm Hàng hóa ---
        layout.addSection(Lang.get("sidebar.section.catalog"));
        layout.addPage("categories", Lang.get("sidebar.categories.short"), FontAwesomeSolid.TAGS, new CategoryPanel(), AppPermission.CATEGORY_MANAGE);
        layout.addPage("products", Lang.get("sidebar.products.short"), FontAwesomeSolid.BOX, new ProductPanel(),
                AppPermission.PRODUCT_MANAGE, AppPermission.PRODUCT_VIEW);
        layout.addPage("suppliers", Lang.get("sidebar.suppliers.short"), FontAwesomeSolid.TRUCK, new SupplierPanel(), AppPermission.SUPPLIER_MANAGE);
        layout.addPage("inventoryBatches", Lang.get("sidebar.inventoryBatches"), FontAwesomeSolid.BOXES, new InventoryBatchPanel(),
                AppPermission.STOCK_IMPORT, AppPermission.STOCK_VIEW);
        layout.addPage("purchaseReceipts", Lang.get("sidebar.purchaseReceipts"), FontAwesomeSolid.FILE_INVOICE, new PurchaseReceiptPanel(),
                AppPermission.STOCK_IMPORT, AppPermission.STOCK_VIEW);
        layout.addPage("stockReconciliation", Lang.get("sidebar.stockReconciliation"), FontAwesomeSolid.BALANCE_SCALE, new StockReconciliationPanel(),
                AppPermission.STOCK_RECONCILE);
        layout.addPage("stockAlerts", Lang.get("sidebar.stockAlerts"), FontAwesomeSolid.EXCLAMATION_TRIANGLE, new StockAlertPanel(),
                AppPermission.STOCK_ALERT_VIEW);
        layout.addPage("exceptionReport", Lang.get("sidebar.exceptionReport"), FontAwesomeSolid.EXCLAMATION_TRIANGLE, new ExceptionReportPanel(),
                AppPermission.EXCEPTION_REPORT_CREATE, AppPermission.EXCEPTION_REPORT_HANDLE);
        // ---- Vi du them 1 trang moi khi ban ghep tinh nang that ----

        // --- Nhóm Bán hàng ---
        layout.addSection(Lang.get("sidebar.section.sales"));
        layout.addPage("pos", Lang.get("sidebar.pos"), FontAwesomeSolid.STORE, new PosPanel(),
                AppPermission.INVOICE_CREATE);
        layout.addPage("invoices", Lang.get("sidebar.invoices"), FontAwesomeSolid.RECEIPT, new InvoicePanel(),
                AppPermission.INVOICE_CREATE, AppPermission.INVOICE_CANCEL);
        // Doi/tra hang gan lien voi hoa don (tao boi NV ban hang, duyet boi
        // Quan ly ban hang theo R4) - thuoc nghiep vu Ban hang, khong phai
        // Hang hoa/Kho, nen chuyen ve day thay vi de chung voi danh muc/kho.
        layout.addPage("returnExchange", Lang.get("sidebar.returnExchange"), FontAwesomeSolid.EXCHANGE_ALT, new ReturnExchangePanel(),
                AppPermission.RETURN_EXCHANGE_CREATE, AppPermission.RETURN_EXCHANGE_APPROVE);
        layout.addPage("revenueReport", Lang.get("sidebar.revenueReport"), FontAwesomeSolid.CHART_LINE, new RevenueReportPanel(),
                AppPermission.REVENUE_REPORT_VIEW);

        layout.addPage("orders", Lang.get("sidebar.orders.short"), FontAwesomeSolid.SHOPPING_CART, new OrderPanel(),
                AppPermission.ORDER_VIEW, AppPermission.ORDER_MANAGE);
        // layout.addPage("products", "San pham", FontAwesomeSolid.BOX, new ProductPanel(), AppPermission.PRODUCT_VIEW);

        // --- Chat hỗ trợ khách hàng (real-time qua WebSocket, xem com.ws) ---
        layout.addSection(Lang.get("sidebar.section.support"));
        ChatPanel chatPanel = new ChatPanel();
        chatPanel.setOnUnreadCountChanged(count -> layout.setBadge("chat", count));
        layout.addPage("chat", Lang.get("sidebar.chat"), FontAwesomeSolid.COMMENT_DOTS, chatPanel);

        // --- Nhóm Hệ thống ---
        layout.addSection(Lang.get("sidebar.section.system"));
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
        // Bam vao chuong thong bao -> nhay thang sang trang "Don hang" de xem
        // ngay don/hoa don online moi (giong myShop: bell click = xem don hang).
        layout.getHeader().onBellClick(() -> layout.showPage("orders"));
        add(layout, BorderLayout.CENTER);

        revalidate();
        repaint();
    }

    private void rebuildContent() {
        buildContent();
        // Cac component ngoai MainLayout (vd SettingsButton tren layered pane)
        // doc mau truc tiep tu AppColor luc paintComponent nen chi can repaint.
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
            // Chi can dispose() - windowClosed() ben duoi se lo logout() va
            // mo lai LoginFrame, tranh bi goi 2 lan.
            dispose();
        }
    }
}