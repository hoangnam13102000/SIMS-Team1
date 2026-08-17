package com.view.client;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SettingsButton;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.model.Product;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.utils.AppIcon;
import com.view.LoginFrame;
import com.view.layouts.Footer;
import com.ws.ChatClient;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ClientMainFrame extends JFrame {

    private CardLayout cardLayout;
    private JPanel contentPanel;
    private ClientHeader header;
    private String currentPageKey = "home";
    private final Runnable onThemeChanged = this::rebuildContent;
    private final Runnable onLangChanged = this::rebuildContent;

    /** Loading dung chung toan he thong (giong het BaseFormDialog) - hien trong luc rebuildContent() dung lai giao dien khi doi Theme/Accent/Ngon ngu. */
    private final LoadingOverlay themeLoadingOverlay = new LoadingOverlay();

    public ClientMainFrame() {
        setTitle("SIMS - " + AuthService.getInstance().getCurrentUser().getFullName());
        setSize(1280, 760);
        setMinimumSize(new Dimension(1024, 600));
        setLocationRelativeTo(null);
        AppIcon.apply(this);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        buildContent();

        // 2 bong bóng: Chat (60) + khe 16 + AI (60) + khe 16 → Settings không bị đè
        SettingsButton.attach(this, 60 + 16 + 60 + 16, false);
        ChatWidget.install(this);
        AiAssistantWidget.install(this);
        installThemeLoadingOverlay();

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

    /**
     * Boc themeLoadingOverlay vao glassPane hien tai (dang la ChatWidget/AiAssistantWidget
     * da wrap voi nhau) CHI 1 LAN duy nhat luc khoi tao - khong boc lai moi lan
     * rebuildContent(). Sau do rebuildContent() chi can goi themeLoadingOverlay.start()/.stop()
     * y het cach BaseFormDialog dang dung, khong phai giu/tra glassPane qua lai nua.
     */
    private void installThemeLoadingOverlay() {
        Component existingGlass = getGlassPane();
        themeLoadingOverlay.setBounds(0, 0, getWidth(), getHeight());

        if (existingGlass != null && existingGlass.isVisible()) {
            JPanel layered = new JPanel(null);
            layered.setOpaque(false);
            existingGlass.setBounds(0, 0, getWidth(), getHeight());
            layered.add(themeLoadingOverlay);
            layered.add(existingGlass);
            layered.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    existingGlass.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                    themeLoadingOverlay.setBounds(0, 0, layered.getWidth(), layered.getHeight());
                }
            });
            setGlassPane(layered);
            layered.setVisible(true);
        } else {
            setGlassPane(themeLoadingOverlay);
        }
    }

    /** Ket qua buildLayout() - tra ve theo cap doi tuong CUC BO, chua gan vao field cua frame. */
    private static final class LayoutBuildResult {
        final CardLayout cardLayout;
        final JPanel contentPanel;
        final ClientHeader header;
        final Footer footer;

        LayoutBuildResult(CardLayout cardLayout, JPanel contentPanel, ClientHeader header, Footer footer) {
            this.cardLayout = cardLayout;
            this.contentPanel = contentPanel;
            this.header = header;
            this.footer = footer;
        }
    }

    /**
     * Dung header + toan bo trang (Home, Products, Cart, Profile...) thanh 1
     * bo component MOI, hoan toan doc lap - KHONG dung/ghi bat ky field nao
     * cua frame (cardLayout/contentPanel/header) va KHONG dung API nao cua
     * JFrame dang hien (add/removeAll/revalidate/repaint). Nho vay ham nay
     * an toan de goi tu luong nen (xem rebuildContent()): trong luc dang
     * chay, header/contentPanel CU van con nguyen, frame van hien thi va
     * tuong tac binh thuong.
     * <p>
     * Mot so panel con (vd ProductsPanel, HomePanel) query CSDL ngay trong
     * constructor - day chinh la phan ton thoi gian, nen moi can tach rieng
     * ra de chay duoc o luong nen.
     */
    private LayoutBuildResult buildLayout() {
        CardLayout cardLayout = new CardLayout();
        JPanel contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(AppColor.PAGE_BG);
        ClientHeader header = new ClientHeader();

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

        addPage(header, contentPanel, "home", Lang.get("client.nav.home"), FontAwesomeSolid.HOME, homePanel);
        header.addCategoriesDropdown(Lang.get("client.nav.categories"), FontAwesomeSolid.TAGS);
        addPage(header, contentPanel, "products", Lang.get("client.nav.products"), FontAwesomeSolid.STORE, productsPanel);
        addPage(header, contentPanel, "about", Lang.get("client.nav.about"), FontAwesomeSolid.INFO_CIRCLE, new AboutPanel());
        addPage(header, contentPanel, "contact", Lang.get("client.nav.contact"), FontAwesomeSolid.ENVELOPE, new ContactPanel());
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

        return new LayoutBuildResult(cardLayout, contentPanel, header, new Footer());
    }

    /**
     * Gan LayoutBuildResult vao frame (thay header/contentPanel cu bang bo moi) -
     * CHI duoc goi tren EDT vi dung/xoa component dang hien (removeAll/add/revalidate).
     * showPage(currentPageKey) phai goi O DAY (sau khi da gan xong field
     * cardLayout/contentPanel/header MOI), khong duoc goi ben trong buildLayout() -
     * vi showPage() doc this.cardLayout/this.contentPanel/this.header (field), goi
     * som se thao tac nham vao bo CU dang hien (hoac te hon, tu luong nen).
     */
    private void applyLayoutResult(LayoutBuildResult result) {
        getContentPane().removeAll();
        getContentPane().setBackground(AppColor.PAGE_BG);

        cardLayout = result.cardLayout;
        contentPanel = result.contentPanel;
        header = result.header;

        add(header, BorderLayout.NORTH);
        add(contentPanel, BorderLayout.CENTER);
        add(result.footer, BorderLayout.SOUTH);

        showPage(currentPageKey);

        revalidate();
        repaint();
    }

    /** Dung dung 1 lan luc khoi tao frame - chay dong bo tren EDT, khong can loading overlay. */
    private void buildContent() {
        applyLayoutResult(buildLayout());
    }

    /**
     * Goi khi doi Theme/Accent/Ngon ngu: dung LAI toan bo noi dung (buildLayout(),
     * chay o luong nen qua SwingWorker) trong luc themeLoadingOverlay dang hien va
     * XOAY THAT - vi phan nang (query CSDL trong cac panel con) khong con chan
     * luong UI nua, Timer cua overlay tick binh thuong. Ghep ket qua vao frame
     * (applyLayoutResult) chi xay ra o done() - tu dong chay lai tren EDT.
     */
    private void rebuildContent() {
        themeLoadingOverlay.setBounds(0, 0, getWidth(), getHeight());
        themeLoadingOverlay.start("Đang cập nhật giao diện...");

        new SwingWorker<LayoutBuildResult, Void>() {
            @Override
            protected LayoutBuildResult doInBackground() {
                return buildLayout();
            }

            @Override
            protected void done() {
                try {
                    applyLayoutResult(get());
                    getLayeredPane().repaint();
                } catch (Exception ex) {
                    com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.UI_DATA_LOAD_FAIL,
                            "ClientMainFrame.rebuildContent - build lai giao dien loi", ex);
                } finally {
                    themeLoadingOverlay.stop();
                }
            }
        }.execute();
    }

    private void addPage(ClientHeader header, JPanel contentPanel, String key, String label,
                          FontAwesomeSolid icon, JPanel panel) {
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