package com.view.client;

import com.components.BaseDialog;
import com.components.SettingsButton;
import com.i18n.Lang;
import com.i18n.LanguageManager;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.ThemeManager;
import com.view.LoginFrame;
import com.view.layouts.Footer;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Frame khach hang MAU cua framework - chi con lai 1 trang "Trang chu" (rong,
 * demo cach ghep header/footer) va "Trang ca nhan" (ProfilePanel, tinh nang
 * that: sua ho ten/so dien thoai + doi mat khau). Khi dung cho app that, them
 * cac trang nghiep vu cua ban bang addPage(key, label, icon, panel) o duoi.
 */
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

        ThemeManager.getInstance().addRebuildListener(onThemeChanged);
        LanguageManager.getInstance().addRebuildListener(onLangChanged);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                ThemeManager.getInstance().removeRebuildListener(onThemeChanged);
                LanguageManager.getInstance().removeRebuildListener(onLangChanged);
                AuthService.getInstance().logout();
                new LoginFrame();
            }
        });

        setVisible(true);
    }

    /** Xay (hoac xay lai) toan bo noi dung ben trong frame - header + cac trang + footer. */
    private void buildContent() {
        getContentPane().removeAll();
        getContentPane().setBackground(AppColor.PAGE_BG);

        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setBackground(AppColor.PAGE_BG);
        header = new ClientHeader();

        ProfilePanel profilePanel = new ProfilePanel();
        profilePanel.onSaved(() -> {
            header.refreshAccountLabel();
            setTitle("SIMS - " + AuthService.getInstance().getCurrentUser().getFullName());
        });

        HomePanel homePanel = new HomePanel();
        addPage("home", Lang.get("client.nav.home"), FontAwesomeSolid.HOME, homePanel);
        contentPanel.add(profilePanel, "profile"); // trang profile chi vao qua dropdown tai khoan

        // ---- Vi du them 1 trang moi khi ban ghep tinh nang that ----
        // addPage("products", "San pham", FontAwesomeSolid.STORE, new ProductStorePanel());

        header.onNavigate(this::showPage);
        header.onSearch(keyword -> {
            showPage("home");
            homePanel.search(keyword);
        });
        header.onProfile(() -> showPage("profile"));
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