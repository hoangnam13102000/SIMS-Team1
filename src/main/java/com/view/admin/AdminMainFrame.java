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
import com.view.admin.order.OrderPanel;
import com.view.admin.invoice.InvoicePanel;
import com.view.admin.pos.PosPanel;
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
        // ---- Vi du them 1 trang moi khi ban ghep tinh nang that ----
     // --- Nhóm Bán hàng ---
        layout.addSection(Lang.get("sidebar.section.sales"));
        layout.addPage("pos", Lang.get("sidebar.pos"), FontAwesomeSolid.STORE, new PosPanel(),
                AppPermission.INVOICE_CREATE);
        layout.addPage("invoices", Lang.get("sidebar.invoices"), FontAwesomeSolid.RECEIPT, new InvoicePanel(),
                AppPermission.INVOICE_CREATE, AppPermission.INVOICE_CANCEL);
        
        layout.addPage("orders", Lang.get("sidebar.orders.short"), FontAwesomeSolid.SHOPPING_CART, new OrderPanel(),
                AppPermission.ORDER_VIEW, AppPermission.ORDER_MANAGE);
        // layout.addPage("products", "San pham", FontAwesomeSolid.BOX, new ProductPanel(), AppPermission.PRODUCT_VIEW);

        // --- Chat hỗ trợ khách hàng (real-time qua WebSocket, xem com.ws) ---
        layout.addSection(Lang.get("sidebar.section.support"));
        ChatPanel chatPanel = new ChatPanel();
        chatPanel.setOnUnreadCountChanged(count -> layout.setBadge("chat", count));
        layout.addPage("chat", Lang.get("sidebar.chat"), FontAwesomeSolid.COMMENT_DOTS, chatPanel);

        ProfilePanel profilePanel = new ProfilePanel();
        profilePanel.onSaved(this::rebuildContent);
        layout.addHiddenPage("profile", profilePanel);
        layout.getHeader().onProfile(() -> layout.showPage("profile"));

        layout.onPageChange(key -> currentPageKey = key);
        layout.showPage(currentPageKey);
        layout.onLogout(this::doLogout);
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