package com.view.client;

import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.product.ProductGrid;
import com.core.log.AppLogger;
import com.components.AppAlert;
import com.core.log.ErrorCode;
import com.dao.ProductDAO;
import com.i18n.Lang;
import com.model.Product;
import com.service.CartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import com.utils.ImageUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

public class HomePanel extends JPanel {

    // ===================== MAU RIENG CHO HERO BANNER (co dinh, khong doi theo Light/Dark) =====================
    private static final Color HERO_TOP = new Color(11, 78, 47);
    private static final Color HERO_BOTTOM = new Color(23, 117, 66);
    private static final Color HERO_YELLOW = new Color(250, 204, 21);
    private static final Color HERO_YELLOW_HOVER = new Color(234, 179, 8);
    private static final Color HERO_GREEN_TEXT = new Color(11, 61, 37);
    private static final Color HERO_SUBTITLE = new Color(214, 235, 222);

    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final LoadingOverlay loadingOverlay;

    private String currentKeyword = "";
    private java.util.function.Consumer<Product> onProductClickListener;
    private Runnable onShopNowListener;

    public HomePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        // Toan bo trang Home (hero + feature strip + tieu de + luoi san pham) duoc
        // dat chung trong 1 panel cuon duoc (pageContent), thay vi chi cuon rieng
        // phan luoi san pham - tranh truong hop hero/feature strip qua cao lam mat
        // het khong gian hien thi ma khong the cuon xuong xem tiep.
        ScrollablePanel pageContent = new ScrollablePanel();
        pageContent.setLayout(new BorderLayout());
        pageContent.setOpaque(false);
        pageContent.add(buildHeaderBlock(), BorderLayout.NORTH);

        productGrid = new ProductGrid();
        productGrid.onAddToCart(product -> {
            if (product.isOutOfStock()) {
                AppAlert.warning(this, "Sản phẩm đã hết hàng.");
                return;
            }

            CartService.getInstance().addToCart(product, 1);

            AppAlert.success(this, "Đã thêm \"" + product.getProductName() + "\" vào giỏ hàng.");
        });
        productGrid.onCardClick(this::onProductSelected);

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);
        pageContent.add(contentArea, BorderLayout.CENTER);

        JScrollPane scrollPane = new JScrollPane(pageContent);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        loadingOverlay = new LoadingOverlay(Lang.get("home.loading"));

        add(LoadingOverlay.attach(scrollPane, loadingOverlay), BorderLayout.CENTER);

        loadProducts(null);
    }

    /** Panel cuon "kieu trang web": luon khop chieu rong voi khung nhin (khong
     *  cuon ngang), chi cao tang theo noi dung thuc te (khong bi ep gian deu). */
    private static class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /** Khoi toan bo phan dau trang: hero banner + dai cam ket chat luong + tieu de khu vuc san pham. */
    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, 0, AppSpacing.XL));

        JPanel featureWrap = new JPanel(new BorderLayout());
        featureWrap.setOpaque(false);
        featureWrap.setBorder(new EmptyBorder(AppSpacing.XL, 0, 0, 0));
        featureWrap.add(buildFeatureStrip(), BorderLayout.NORTH);

        JPanel sectionWrap = new JPanel(new BorderLayout());
        sectionWrap.setOpaque(false);
        sectionWrap.setBorder(new EmptyBorder(AppSpacing.XL, 0, AppSpacing.SM, 0));
        sectionWrap.add(buildSectionHeader(), BorderLayout.NORTH);

        featureWrap.add(sectionWrap, BorderLayout.SOUTH);

        wrapper.add(buildHeroBanner(), BorderLayout.NORTH);
        wrapper.add(featureWrap, BorderLayout.SOUTH);
        return wrapper;
    }

    // ===================== HERO BANNER =====================

    private JPanel buildHeroBanner() {
        HeroBannerPanel hero = new HeroBannerPanel();
        hero.setLayout(new BorderLayout(AppSpacing.XL, 0));
        hero.setBorder(new EmptyBorder(36, 40, 36, 32));
        hero.setPreferredSize(new Dimension(10, 300));

        hero.add(buildHeroText(), BorderLayout.CENTER);
        hero.add(buildHeroCollage(), BorderLayout.EAST);
        return hero;
    }

    private JPanel buildHeroText() {
        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel eyebrow = new JLabel(Lang.get("home.hero.eyebrow"));
        eyebrow.setFont(AppFont.bold(13));
        eyebrow.setForeground(HERO_YELLOW);
        eyebrow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("<html><div style='width:380px;font-family:Segoe UI;"
                + "font-size:27px;font-weight:bold;color:#FFFFFF;line-height:120%;'>"
                + Lang.get("home.hero.title") + "</div></html>");
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(10, 0, 12, 0));

        JLabel subtitle = new JLabel("<html><div style='width:340px;font-family:Segoe UI;"
                + "font-size:13px;color:" + toHex(HERO_SUBTITLE) + ";line-height:140%;'>"
                + Lang.get("home.hero.subtitle") + "</div></html>");
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(0, 0, 22, 0));

        HeroCtaButton cta = new HeroCtaButton(Lang.get("home.hero.cta"));
        cta.setAlignmentX(Component.LEFT_ALIGNMENT);
        cta.addActionListener(e -> {
            if (onShopNowListener != null) onShopNowListener.run();
        });

        JPanel dots = buildHeroDots();
        dots.setAlignmentX(Component.LEFT_ALIGNMENT);
        dots.setBorder(new EmptyBorder(26, 0, 0, 0));

        text.add(eyebrow);
        text.add(title);
        text.add(subtitle);
        text.add(cta);
        text.add(dots);
        return text;
    }

    private JPanel buildHeroDots() {
        JPanel dots = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        dots.setOpaque(false);
        dots.add(new HeroDot(true));
        dots.add(new HeroDot(false));
        dots.add(new HeroDot(false));
        dots.add(new HeroDot(false));
        return dots;
    }

    private JComponent buildHeroCollage() {
        JPanel collage = new JPanel(null) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                drawProductCircle(g2, this, "uploads/products/ca-rot.jpg", 2, 34, 92);
                drawProductCircle(g2, this, "uploads/products/ca-chua.jpg", 94, 2, 116);
                drawProductCircle(g2, this, "uploads/products/chuoi-gia.jpg", 198, 44, 104);
                drawProductCircle(g2, this, "uploads/products/tao-envy.jpg", 26, 160, 96);
                drawProductCircle(g2, this, "uploads/products/ca-phe-bot.jpg", 148, 178, 82);
                drawProductCircle(g2, this, "uploads/products/nuoc-suoi.jpg", 234, 194, 68);
                g2.dispose();
            }
        };
        collage.setOpaque(false);
        collage.setPreferredSize(new Dimension(320, 280));
        return collage;
    }

    private static void drawProductCircle(Graphics2D g2, Component owner, String path, int x, int y, int diameter) {
        g2.setColor(new Color(0, 0, 0, 55));
        g2.fillOval(x + 3, y + 6, diameter, diameter);

        ImageIcon icon = ImageUtil.circularIcon(path, diameter);
        icon.paintIcon(owner, g2, x, y);

        g2.setColor(new Color(255, 255, 255, 235));
        g2.setStroke(new BasicStroke(3f));
        g2.drawOval(x, y, diameter - 1, diameter - 1);
    }

    /** Nen bo tron, gradient xanh la cua hero banner - luon giong nhau bat ke Light/Dark. */
    private static class HeroBannerPanel extends JPanel {
        HeroBannerPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, h, AppRadius.EXTRA_LARGE, AppRadius.EXTRA_LARGE);
            g2.setClip(shape);

            GradientPaint gp = new GradientPaint(0, 0, HERO_TOP, w, h, HERO_BOTTOM);
            g2.setPaint(gp);
            g2.fill(shape);

            // Vong tron trang tri mo, tao chieu sau phia sau collage anh
            g2.setColor(new Color(255, 255, 255, 18));
            int d = (int) (h * 1.35);
            g2.fillOval(w - d + 60, h - d + 40, d, d);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Nut CTA hinh vien thuoc, mau vang, dung cho "Mua ngay" trong hero banner. */
    private static class HeroCtaButton extends JButton {
        private boolean hover = false;

        HeroCtaButton(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setForeground(HERO_GREEN_TEXT);
            setFont(AppFont.BUTTON);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(13, 26, 13, 22));

            FontIcon arrow = FontIcon.of(FontAwesomeSolid.CHEVRON_RIGHT, 12);
            arrow.setIconColor(HERO_GREEN_TEXT);
            setIcon(arrow);
            setHorizontalTextPosition(SwingConstants.LEFT);
            setIconTextGap(10);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? HERO_YELLOW_HOVER : HERO_YELLOW);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Cham tron trang tri (pagination dot) trong hero banner - chi mang tinh trang tri. */
    private static class HeroDot extends JComponent {
        private final boolean active;

        HeroDot(boolean active) {
            this.active = active;
            setOpaque(false);
            setPreferredSize(new Dimension(active ? 22 : 8, 8));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(active ? HERO_YELLOW : new Color(255, 255, 255, 90));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
        }
    }

    // ===================== DAI CAM KET CHAT LUONG (feature strip) =====================

    private JPanel buildFeatureStrip() {
        JPanel strip = new JPanel(new GridLayout(1, 4, AppSpacing.LG, 0));
        strip.setOpaque(false);
        strip.add(buildFeatureCard(FontAwesomeSolid.CARROT,
                Lang.get("home.features.item1.title"), Lang.get("home.features.item1.desc")));
        strip.add(buildFeatureCard(FontAwesomeSolid.SHIELD_ALT,
                Lang.get("home.features.item2.title"), Lang.get("home.features.item2.desc")));
        strip.add(buildFeatureCard(FontAwesomeSolid.BAN,
                Lang.get("home.features.item3.title"), Lang.get("home.features.item3.desc")));
        strip.add(buildFeatureCard(FontAwesomeSolid.TRUCK,
                Lang.get("home.features.item4.title"), Lang.get("home.features.item4.desc")));
        return strip;
    }

    private JPanel buildFeatureCard(FontAwesomeSolid icon, String title, String desc) {
        FeatureCard card = new FeatureCard();
        card.setLayout(new BorderLayout());
        card.setBorder(new EmptyBorder(2, 2, 2, 2));

        JPanel inner = new JPanel(new BorderLayout());
        inner.setOpaque(false);
        inner.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JPanel iconRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        iconRow.setOpaque(false);
        iconRow.add(new IconBadge(icon, AppColor.SUCCESS, AppColor.SUCCESS_BG, 46));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLabel.setBorder(new EmptyBorder(AppSpacing.SM, 0, 4, 0));

        JLabel descLabel = new JLabel("<html><div style='width:175px;font-family:Segoe UI;"
                + "font-size:12px;color:" + toHex(AppColor.TEXT_MUTED) + ";line-height:135%;'>"
                + escapeHtml(desc) + "</div></html>");
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(titleLabel);
        textCol.add(descLabel);

        inner.add(iconRow, BorderLayout.NORTH);
        inner.add(textCol, BorderLayout.CENTER);
        card.add(inner, BorderLayout.CENTER);
        return card;
    }

    /** The tron mau icon cho feature strip (vd la xanh, khien, xe tai...). */
    private static class IconBadge extends JComponent {
        private final FontIcon icon;
        private final Color bg;

        IconBadge(FontAwesomeSolid glyph, Color fg, Color bg, int size) {
            this.icon = FontIcon.of(glyph, (int) Math.round(size * 0.42));
            this.icon.setIconColor(fg);
            this.bg = bg;
            setOpaque(false);
            setPreferredSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillOval(0, 0, getWidth(), getHeight());
            int ix = (getWidth() - icon.getIconWidth()) / 2;
            int iy = (getHeight() - icon.getIconHeight()) / 2;
            icon.paintIcon(this, g2, ix, iy);
            g2.dispose();
        }
    }

    /** The bo tron nen trang, bong do nhe - dung chung style voi ProductCard de dong bo giao dien. */
    private static class FeatureCard extends JPanel {
        FeatureCard() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth() - 4;
            int h = getHeight() - 4;
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.translate(2, 2);

            for (int i = 3; i >= 1; i--) {
                g2.setColor(new Color(15, 23, 42, 6));
                RoundRectangle2D.Float shadow = new RoundRectangle2D.Float(-i, -i + i * 2, w + i * 2, h + i * 2, AppRadius.LARGE, AppRadius.LARGE);
                g2.fill(shadow);
            }

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, h, AppRadius.LARGE, AppRadius.LARGE);
            g2.setColor(AppColor.WHITE);
            g2.fill(shape);
            g2.setColor(AppColor.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(shape);

            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ===================== TIEU DE KHU VUC SAN PHAM =====================

    private JPanel buildSectionHeader() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));

        JLabel badge = new JLabel(Lang.get("home.section.badge"));
        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(AppColor.ACCENT);
        badge.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(Lang.get("home.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(4, 0, 0, 0));

        JLabel subtitle = new JLabel(Lang.get("home.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, 0, 0));

        wrapper.add(badge);
        wrapper.add(title);
        wrapper.add(subtitle);
        return wrapper;
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Goi tu ClientHeader khi nguoi dung nhap tu khoa va nhan Enter/nut Tim. keyword null hoac rong -> hien tat ca. */
    public void search(String keyword) {
        loadProducts(keyword);
    }

    /** Goi tu ClientMainFrame de dieu huong sang trang San pham khi bam nut "Mua ngay" tren hero banner. */
    public void onShopNow(Runnable listener) {
        this.onShopNowListener = listener;
    }

    private void loadProducts(String keyword) {
        this.currentKeyword = keyword == null ? "" : keyword.trim();
        loadingOverlay.start();

        SwingWorker<List<Product>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Product> doInBackground() {
                try {
                    return currentKeyword.isEmpty()
                            ? productDAO.findAllActive()
                            : productDAO.searchActive(currentKeyword);
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "HomePanel.loadProducts", error);
                    showError();
                    return;
                }
                try {
                    renderProducts(get());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "HomePanel.loadProducts - get()", e);
                    showError();
                }
            }
        };
        worker.execute();
    }

    private void renderProducts(List<Product> products) {
        contentArea.removeAll();

        if (products == null || products.isEmpty()) {
            EmptyState empty = currentKeyword.isEmpty()
                    ? EmptyState.noData(Lang.get("home.noData.entity"))
                    : EmptyState.noSearchResult(currentKeyword);
            contentArea.add(empty, BorderLayout.CENTER);
        } else {
            productGrid.setProducts(products);

            // Boc grid trong 1 panel BorderLayout.NORTH de GridLayout khong bi keo
            // gian theo chieu cao cua contentArea - moi the giu dung kich thuoc
            // "tu nhien" thay vi bi chia deu het chieu cao khung nhin.
            JPanel gridWrapper = new JPanel(new BorderLayout());
            gridWrapper.setOpaque(false);
            gridWrapper.setBorder(new EmptyBorder(0, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
            gridWrapper.add(productGrid, BorderLayout.NORTH);

            contentArea.add(gridWrapper, BorderLayout.CENTER);
        }

        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showError() {
        contentArea.removeAll();
        contentArea.add(EmptyState.error(Lang.get("home.loadError")), BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    /** Goi tu ClientMainFrame de dieu huong sang trang chi tiet khi bam vao 1 the san pham. */
    public void onProductClick(java.util.function.Consumer<Product> listener) {
        this.onProductClickListener = listener;
    }

    private void onProductSelected(Product product) {
        if (onProductClickListener != null) onProductClickListener.accept(product);
    }
}