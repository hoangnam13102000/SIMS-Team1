package com.view.client;

import com.components.BaseDialog;
import com.components.SettingsButton;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.model.Product;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.view.LoginFrame;
import com.view.layouts.Footer;
import com.ws.ChatClient;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ClientMainFrame extends JFrame {

    private CardLayout cardLayout;
    private JPanel contentPanel;
    private ClientHeader header;
    private String currentPageKey = "home";
    private final Runnable onThemeChanged = this::rebuildContent;
    private final Runnable onLangChanged = this::rebuildContent;

    public ClientMainFrame() {
        setTitle("SIMS - " + AuthService.getInstance().getCurrentUser().getFullName());
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        buildContent();

        SettingsButton.attach(this, 76, false);
        ChatWidget.install(this);

        ThemeManager.getInstance().addRebuildListener(onThemeChanged);
        LanguageManager.getInstance().addRebuildListener(onLangChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ThemeManager.getInstance().removeRebuildListener(onThemeChanged);
                LanguageManager.getInstance().removeRebuildListener(onLangChanged);
                ChatClient.getInstance().disconnect();
                AuthService.getInstance().logout();
                new LoginFrame();
            }
        });

        setVisible(true);
    }

    private void buildContent() {
        getContentPane().removeAll();
        getContentPane().setBackground(AppColor.PAGE_BG);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(AppColor.PAGE_BG);
        header = new ClientHeader();

        ProfilePanel profilePanel = new ProfilePanel();
        OrderHistoryPanel orderHistoryPanel = new OrderHistoryPanel();
        profilePanel.onSaved(() -> {
            header.refreshAccountLabel();
            setTitle("SIMS - " + AuthService.getInstance().getCurrentUser().getFullName());
        });

        HomePanel homePanel = new HomePanel();
        ProductsPanel productsPanel = new ProductsPanel();
        ProductDetailPanel productDetailPanel = new ProductDetailPanel();

        CartPanel cartPanel = new CartPanel();
        cartPanel.onCheckoutSuccess(() -> showPage("home"));
        cartPanel.onContinueShopping(() -> {
            showPage("products");
            productsPanel.showAll();
        });

        java.util.function.Consumer<Product> openProductDetail = product -> {
            productDetailPanel.showProduct(product);
            showPage("productDetail");
        };
        homePanel.onProductClick(openProductDetail);
        homePanel.onShopNow(() -> {
            showPage("products");
            productsPanel.showAll();
        });
        productsPanel.onProductClick(openProductDetail);
        productDetailPanel.onRelatedProductClick(openProductDetail);
        productDetailPanel.onBack(() -> showPage("products"));
        productDetailPanel.onBuyNow(() -> {
            showPage("cart");
            cartPanel.loadCart();
        });

        addPage("home", Lang.get("client.nav.home"), FontAwesomeSolid.HOME, homePanel);
        header.addCategoriesDropdown(Lang.get("client.nav.categories"), FontAwesomeSolid.TAGS);
        addPage("products", Lang.get("client.nav.products"), FontAwesomeSolid.STORE, productsPanel);
        addPage("about", Lang.get("client.nav.about"), FontAwesomeSolid.INFO_CIRCLE, new AboutPanel());
        addPage("contact", Lang.get("client.nav.contact"), FontAwesomeSolid.ENVELOPE, new ContactPanel());
        contentPanel.add(profilePanel, "profile");
        contentPanel.add(orderHistoryPanel, "orderHistory");
        contentPanel.add(cartPanel, "cart");
        contentPanel.add(productDetailPanel, "productDetail");

        header.onCategorySelect((categoryId, categoryName) -> {
            showPage("products");
            productsPanel.filterByCategory(categoryId, categoryName);
        });

        header.onNavigate(key -> {
            showPage(key);
            if ("products".equals(key)) {
                productsPanel.showAll();
            }
        });
        header.onSearch(keyword -> {
            showPage("home");
            homePanel.search(keyword);
        });
        header.onProfile(() -> showPage("profile"));
        header.onOrderHistory(() -> {
            orderHistoryPanel.refresh();
            showPage("orderHistory");
        });
        header.onCartClick(() -> {
            showPage("cart");
            cartPanel.loadCart();
        });
        header.onLogout(this::doLogout);

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(new Footer(), BorderLayout.SOUTH);

        showPage(currentPageKey);

        revalidate();
        repaint();
    }

    private void rebuildContent() {
        buildContent();
        getLayeredPane().repaint();
    }

    private void addPage(String key, String label, FontAwesomeSolid icon, JPanel panel) {
        header.addPage(key, label, icon);
        contentPanel.add(panel, key);
    }

    private void showPage(String key) {
        currentPageKey = key;
        cardLayout.show(contentPanel, key);
        header.setActive(key);
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
}
