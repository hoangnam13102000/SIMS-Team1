package com.view.client;

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
import com.utils.NumberUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.function.Consumer;

public class ProductDetailPanel extends JPanel {

    private static final int IMAGE_SIZE = 420;
    
    private static final Color SHOP_GREEN =
            new Color(34, 166, 94);

    private static final Color SHOP_GREEN_DARK =
            new Color(22, 128, 72);

    private static final Color SHOP_GREEN_SOFT =
            new Color(232, 248, 238);

    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel scrollContent;
    private final JLabel breadcrumbTail;
    private final ImagePanel imagePanel;
    private final Pill categoryPill;
    private final Pill stockBadge;
    private final JLabel nameLabel;
    private final JLabel priceLabel;
    private final JLabel stockHintLabel;
    private final JPanel metaRow;
    private final JLabel descriptionArea;
    private final JLabel qtyLabel;
    private final JButton addToCartButton;
    private final JButton buyNowButton;

    private final JPanel relatedSection;
    private final ProductGrid relatedGrid;
    private final LoadingOverlay relatedLoading;

    private Product currentProduct;
    private int quantity = 1;

    private Runnable onBack;
    private Runnable onBuyNow;
    private Consumer<Product> onRelatedProductClick;

    public ProductDetailPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        breadcrumbTail = new JLabel();
        imagePanel = new ImagePanel();
        categoryPill = pill();
        stockBadge = pill();
        nameLabel = new JLabel();
        priceLabel = new JLabel();
        stockHintLabel = new JLabel();
        metaRow = new JPanel();
        metaRow.setOpaque(false);
        metaRow.setLayout(new BoxLayout(metaRow, BoxLayout.Y_AXIS));
        metaRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        descriptionArea = new JLabel();
        descriptionArea.setVerticalAlignment(SwingConstants.TOP);
        descriptionArea.setFont(AppFont.BODY);
        descriptionArea.setForeground(AppColor.TEXT_SECONDARY);
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);

        qtyLabel = new JLabel("1", SwingConstants.CENTER);
        addToCartButton = buildPrimaryButton(
                Lang.get("productDetail.addToCart"),
                FontAwesomeSolid.SHOPPING_CART,
                SHOP_GREEN,
                SHOP_GREEN_DARK,
                Color.WHITE
        );
        buyNowButton = buildPrimaryButton(Lang.get("productDetail.buyNow"), FontAwesomeSolid.BOLT,
                AppColor.ORANGE, AppColor.WARNING, AppColor.TEXT_TITLE);

        addToCartButton.addActionListener(e -> {
            if (currentProduct != null) handleAddToCart(currentProduct, quantity);
        });
        buyNowButton.addActionListener(e -> {
            if (currentProduct == null) return;
            if (currentProduct.isOutOfStock()) {
                AppAlert.warning(this, Lang.get("productDetail.outOfStockWarning"));
                return;
            }
            CartService.getInstance().addToCart(currentProduct, quantity);
            if (onBuyNow != null) onBuyNow.run();
        });

        relatedGrid = new ProductGrid();
        relatedGrid.onCardClick(product -> {
            if (onRelatedProductClick != null) onRelatedProductClick.accept(product);
        });
        relatedGrid.onAddToCart(product -> handleAddToCart(product, 1));
        relatedLoading = new LoadingOverlay(Lang.get("productDetail.related.loading"));
        relatedSection = buildRelatedSection();

        scrollContent = new JPanel();
        scrollContent.setOpaque(false);
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.add(buildTopBar());
        scrollContent.add(buildDetailCard());
        scrollContent.add(Box.createVerticalStrut(AppSpacing.XL));
        scrollContent.add(relatedSection);

        JScrollPane scrollPane = new JScrollPane(scrollContent);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);
    }

    // ==================== API cong khai (goi tu ClientMainFrame) ====================

    public void showProduct(Product product) {
        if (product == null) return;
        this.currentProduct = product;
        this.quantity = 1;

        breadcrumbTail.setText(product.getProductName());
        imagePanel.setImageUrl(product.getImageUrl(), product.getCategoryName());
        categoryPill.setText(product.getCategoryName() == null || product.getCategoryName().isBlank()
                ? Lang.get("productDetail.noCategory") : product.getCategoryName());
        applyStockBadge(product);

        nameLabel.setText("<html><body style='width: 380px'>" + escapeHtml(product.getProductName()) + "</body></html>");
        priceLabel.setText(formatPrice(product));
        stockHintLabel.setText(stockHintText(product));
        stockHintLabel.setForeground(product.isOutOfStock() ? AppColor.ERROR
                : (product.isLowStock() ? AppColor.WARNING : AppColor.TEXT_MUTED));

        rebuildMetaRow(product);

        String desc = product.getDescription();
        String descText = desc == null || desc.isBlank() ? Lang.get("productDetail.noDescription") : desc;
        descriptionArea.setText("<html><body style='width: 400px'>" + escapeHtml(descText).replace("\n", "<br>") + "</body></html>");

        qtyLabel.setText(String.valueOf(quantity));
        addToCartButton.setEnabled(!product.isOutOfStock());
        buyNowButton.setEnabled(!product.isOutOfStock());

        SwingUtilities.invokeLater(() -> scrollRectToVisible(new Rectangle(0, 0, 1, 1)));

        loadRelatedProducts(product);
    }

    public void onBack(Runnable listener) {
        this.onBack = listener;
    }

    public void onBuyNow(Runnable listener) {
        this.onBuyNow = listener;
    }

    public void onRelatedProductClick(Consumer<Product> listener) {
        this.onRelatedProductClick = listener;
    }

    // ==================== Thanh tren: breadcrumb + nut quay lai ====================

    private JPanel buildTopBar() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.MD, AppSpacing.XL));

        JButton back = new JButton(Lang.get("productDetail.back"));
        back.setIcon(icon(FontAwesomeSolid.ARROW_LEFT, 12, AppColor.TEXT_SECONDARY));
        back.setIconTextGap(8);
        back.setFont(AppFont.SMALL_BOLD);
        back.setForeground(AppColor.TEXT_SECONDARY);
        back.setOpaque(false);
        back.setContentAreaFilled(false);
        back.setBorderPainted(false);
        back.setFocusPainted(false);
        back.setCursor(new Cursor(Cursor.HAND_CURSOR));
        back.addActionListener(e -> { if (onBack != null) onBack.run(); });

        JPanel breadcrumb = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        breadcrumb.setOpaque(false);
        breadcrumb.add(breadcrumbLabel(Lang.get("client.nav.home"), AppColor.TEXT_MUTED));
        breadcrumb.add(breadcrumbLabel("/", AppColor.TEXT_MUTED));
        breadcrumb.add(breadcrumbLabel(Lang.get("client.nav.products"), AppColor.TEXT_MUTED));
        breadcrumb.add(breadcrumbLabel("/", AppColor.TEXT_MUTED));
        breadcrumbTail.setFont(AppFont.SMALL_BOLD);
        breadcrumbTail.setForeground(AppColor.TEXT_PRIMARY);
        breadcrumb.add(breadcrumbTail);

        wrapper.add(back, BorderLayout.WEST);
        wrapper.add(breadcrumb, BorderLayout.EAST);
        return wrapper;
    }

    private JLabel breadcrumbLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(color);
        return label;
    }

    // ==================== The chi tiet chinh: anh + thong tin ====================

    private JPanel buildDetailCard() {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        // NONE: giu dung preferred size 420x420, khong bi GridBag co ve 0
        gbc.fill = GridBagConstraints.NONE;

        gbc.gridx = 0;
        gbc.weightx = 0;
        gbc.insets = new Insets(0, 0, 0, AppSpacing.XXL);
        card.add(imagePanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        card.add(buildInfoColumn(), gbc);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.setAlignmentX(Component.LEFT_ALIGNMENT);
        outer.setBorder(new EmptyBorder(0, AppSpacing.XL, 0, AppSpacing.XL));
        outer.add(card, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildInfoColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.setBorder(new EmptyBorder(0, 0, 0, 0));
        badgeRow.add(categoryPill);
        badgeRow.add(stockBadge);

        nameLabel.setFont(AppFont.getXL_Bold());
        nameLabel.setForeground(AppColor.TEXT_TITLE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));

        priceLabel.setFont(AppFont.getXXL_Bold());
        priceLabel.setForeground(SHOP_GREEN_DARK);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceLabel.setBorder(new EmptyBorder(AppSpacing.SM, 0, 2, 0));

        stockHintLabel.setFont(AppFont.SMALL);
        stockHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        stockHintLabel.setBorder(new EmptyBorder(0, 0, AppSpacing.LG, 0));

        JLabel descTitle = sectionLabel(Lang.get("productDetail.description"));
        descriptionArea.setBorder(new EmptyBorder(6, 0, AppSpacing.LG, 0));

        col.add(badgeRow);
        col.add(nameLabel);
        col.add(priceLabel);
        col.add(stockHintLabel);
        col.add(divider());
        col.add(Box.createVerticalStrut(AppSpacing.MD));
        col.add(metaRow);
        col.add(descTitle);
        col.add(descriptionArea);
        col.add(buildQtyAndActionsRow());
        return col;
    }

    private void rebuildMetaRow(Product product) {
        metaRow.removeAll();
        boolean any = false;
        any |= addMetaLine(Lang.get("productDetail.meta.code"), product.getProductCode());
        any |= addMetaLine(Lang.get("productDetail.meta.brand"), product.getBrand());
        any |= addMetaLine(Lang.get("productDetail.meta.unit"), product.getUnit());
        any |= addMetaLine(Lang.get("productDetail.meta.weight"), product.getWeightVolume());
        if (any) {
            metaRow.setBorder(new EmptyBorder(0, 0, AppSpacing.MD, 0));
        } else {
            metaRow.setBorder(new EmptyBorder(0, 0, 0, 0));
        }
        metaRow.revalidate();
        metaRow.repaint();
    }

    private boolean addMetaLine(String label, String value) {
        if (value == null || value.isBlank()) return false;
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        JLabel labelPart = new JLabel(label);
        labelPart.setFont(AppFont.SMALL);
        labelPart.setForeground(AppColor.TEXT_MUTED);
        labelPart.setPreferredSize(new Dimension(90, 18));

        JLabel valuePart = new JLabel(value);
        valuePart.setFont(AppFont.SMALL_BOLD);
        valuePart.setForeground(AppColor.TEXT_PRIMARY);

        row.add(labelPart, BorderLayout.WEST);
        row.add(valuePart, BorderLayout.CENTER);
        metaRow.add(row);
        return true;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_TITLE);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // ==================== Bo dem so luong + nut hanh dong ====================

    private JPanel buildQtyAndActionsRow() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(
                new BoxLayout(wrapper, BoxLayout.Y_AXIS)
        );
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        105
                )
        );

        wrapper.setBorder(
                new EmptyBorder(
                        AppSpacing.SM,
                        0,
                        0,
                        0
                )
        );

        // Hàng chọn số lượng
        JPanel quantityRow = new JPanel(
                new FlowLayout(
                        FlowLayout.LEFT,
                        AppSpacing.SM,
                        0
                )
        );

        quantityRow.setOpaque(false);
        quantityRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel quantityTitle = new JLabel("Số lượng:");
        quantityTitle.setFont(AppFont.SMALL_BOLD);
        quantityTitle.setForeground(AppColor.TEXT_PRIMARY);

        quantityRow.add(quantityTitle);
        quantityRow.add(buildQtyStepper());

        // Hàng chứa hai nút hành động
        JPanel buttonRow = new JPanel(
                new GridLayout(
                        1,
                        2,
                        AppSpacing.MD,
                        0
                )
        );

        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        buttonRow.setMaximumSize(
                new Dimension(
                        Integer.MAX_VALUE,
                        44
                )
        );

        buttonRow.setPreferredSize(
                new Dimension(
                        380,
                        44
                )
        );

        buttonRow.add(addToCartButton);
        buttonRow.add(buyNowButton);

        wrapper.add(quantityRow);
        wrapper.add(Box.createVerticalStrut(AppSpacing.SM));
        wrapper.add(buttonRow);

        return wrapper;
    }

    private JPanel buildQtyStepper() {
        JPanel stepper = new JPanel(new GridLayout(1, 3));
        stepper.setOpaque(false);
        stepper.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        stepper.setPreferredSize(new Dimension(120, 40));

        JButton minus = stepButton(FontAwesomeSolid.MINUS, -1);
        qtyLabel.setFont(AppFont.BODY_BOLD);
        qtyLabel.setForeground(AppColor.TEXT_PRIMARY);
        qtyLabel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, AppColor.BORDER));
        JButton plus = stepButton(FontAwesomeSolid.PLUS, 1);

        stepper.add(minus);
        stepper.add(qtyLabel);
        stepper.add(plus);
        return stepper;
    }

    private JButton stepButton(FontAwesomeSolid iconType, int delta) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                FontIcon icon = FontIcon.of(iconType, 11);
                icon.setIconColor(isEnabled() ? AppColor.TEXT_SECONDARY : AppColor.TEXT_DISABLED);
                int ix = (getWidth() - icon.getIconWidth()) / 2;
                int iy = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, ix, iy);
            }
        };
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> {
            if (currentProduct == null) return;
            int max = Math.max(1, currentProduct.getStock());
            quantity = NumberUtil.clamp(quantity + delta, 1, max);
            qtyLabel.setText(String.valueOf(quantity));
        });
        return button;
    }

    private JButton buildPrimaryButton(String text, FontAwesomeSolid iconType, Color bg, Color hoverBg, Color fg) {
        JButton button = new JButton() {
            private boolean btnHover = false;
            {
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { if (isEnabled()) { btnHover = true; repaint(); } }
                    @Override public void mouseExited(MouseEvent e) { btnHover = false; repaint(); }
                });
            }
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = !isEnabled() ? AppColor.BG_LIGHTER : (btnHover ? hoverBg : bg);
                g2.setColor(fill);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                setForeground(!isEnabled() ? AppColor.TEXT_DISABLED : fg);
                super.paintComponent(g);
            }
        };
        button.setText(text);
        button.setIcon(icon(iconType, 13, fg));
        button.setIconTextGap(8);
        button.setFont(AppFont.BUTTON);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(11, 16, 11, 16));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setPreferredSize(new Dimension(button.getPreferredSize().width, 44));
        return button;
    }

    // ==================== San pham lien quan ====================

    private JPanel buildRelatedSection() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setBorder(new EmptyBorder(0, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("productDetail.related.title"));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setBorder(new EmptyBorder(0, 0, AppSpacing.MD, 0));

        JPanel gridHolder = new JPanel(new BorderLayout());
        gridHolder.setOpaque(false);
        gridHolder.add(relatedGrid, BorderLayout.NORTH);

        wrapper.add(title, BorderLayout.NORTH);
        wrapper.add(LoadingOverlay.attach(gridHolder, relatedLoading), BorderLayout.CENTER);
        return wrapper;
    }

    private void loadRelatedProducts(Product product) {
        relatedSection.setVisible(true);
        relatedLoading.start();

        SwingWorker<List<Product>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Product> doInBackground() {
                try {
                    return productDAO.findActiveByCategory(product.getCategoryId());
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                relatedLoading.stop();
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductDetailPanel.loadRelated", error);
                    relatedGrid.setProducts(List.of());
                    relatedSection.setVisible(false);
                    return;
                }
                try {
                    List<Product> related = get().stream()
                            .filter(p -> p.getProductId() != product.getProductId())
                            .limit(8)
                            .toList();
                    relatedGrid.setProducts(related);
                    relatedSection.setVisible(!related.isEmpty());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductDetailPanel.loadRelated - get()", e);
                    relatedGrid.setProducts(List.of());
                    relatedSection.setVisible(false);
                }
            }
        };
        worker.execute();
    }

    // ==================== Hanh dong gio hang ====================

    private void handleAddToCart(Product product, int qty) {
        if (product.isOutOfStock()) {
            AppAlert.warning(this, Lang.get("productDetail.outOfStockWarning"));
            return;
        }
        CartService.getInstance().addToCart(product, qty);
        AppAlert.success(this, Lang.get("productDetail.addedToCart", product.getProductName()));
    }

    // ==================== Tien ich hien thi ====================

    private void applyStockBadge(Product product) {
        if (product.isOutOfStock()) {
            styleBadge(stockBadge, Lang.get("productDetail.status.outOfStock"), AppColor.ERROR_BG, AppColor.ERROR);
        } else if (product.isLowStock()) {
            styleBadge(stockBadge, Lang.get("productDetail.status.lowStock"), AppColor.WARNING_BG, AppColor.WARNING);
        } else {
            styleBadge(stockBadge, Lang.get("productDetail.status.inStock"), AppColor.SUCCESS_BG, AppColor.SUCCESS);
        }
        styleBadge(
                categoryPill,
                categoryPill.getText(),
                SHOP_GREEN_SOFT,
                SHOP_GREEN_DARK
        );
    }

    private String stockHintText(Product product) {
        if (product.isOutOfStock()) return Lang.get("productDetail.status.outOfStock");
        return Lang.get("productDetail.stockHint", product.getStock());
    }

    private Pill pill() {
        Pill label = new Pill();
        label.setFont(AppFont.SMALL_BOLD);
        label.setBorder(new EmptyBorder(5, 12, 5, 12));
        return label;
    }

    private void styleBadge(JLabel label, String text, Color bg, Color fg) {
        label.setText(text);
        label.setForeground(fg);
        if (label instanceof Pill pill) {
            pill.setPillBackground(bg);
        }
    }

    private static class Pill extends JLabel {
        private Color pillBg = new Color(0, 0, 0, 0);

        void setPillBackground(Color color) {
            this.pillBg = color;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(pillBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private JPanel divider() {
        JPanel line = new JPanel();
        line.setBackground(AppColor.BORDER);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        line.setPreferredSize(new Dimension(10, 1));
        return line;
    }

    private Icon icon(FontAwesomeSolid iconType, int size, Color color) {
        FontIcon icon = FontIcon.of(iconType, size);
        icon.setIconColor(color);
        return icon;
    }

    private String formatPrice(Product product) {
        long price = product.getSellPrice() == null ? 0 : product.getSellPrice().longValue();
        return NumberUtil.formatThousands(price) + " \u0111";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== Panel anh san pham (bo tron, cover-fit, fallback icon) ====================

    private static class ImagePanel extends JPanel {
        private BufferedImage image;
        private String categoryName;

        ImagePanel() {
            setOpaque(false);
            Dimension size = new Dimension(IMAGE_SIZE, IMAGE_SIZE);
            setPreferredSize(size);
            setMinimumSize(size);
            setMaximumSize(size);
            setLayout(new GridBagLayout());
        }

        void setImageUrl(String imageUrl, String categoryName) {
            this.categoryName = categoryName;
            this.image = (imageUrl == null || imageUrl.isBlank()) ? null : ImageUtil.readSafe(imageUrl);
            removeAll();
            if (image == null) {
                FontIcon icon = FontIcon.of(FontAwesomeSolid.BOX_OPEN, 90);
                icon.setIconColor(AppColor.ACCENT_HOVER);
                add(new JLabel(icon));
            }
            revalidate();
            repaint();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(IMAGE_SIZE, IMAGE_SIZE);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        public Dimension getMaximumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(
                    0, 0, getWidth(), getHeight(), AppRadius.EXTRA_LARGE, AppRadius.EXTRA_LARGE);
            g2.setClip(shape);

            if (image != null) {
                double scale = Math.max(
                        (double) getWidth() / image.getWidth(),
                        (double) getHeight() / image.getHeight());
                int drawW = (int) Math.ceil(image.getWidth() * scale);
                int drawH = (int) Math.ceil(image.getHeight() * scale);
                int x = (getWidth() - drawW) / 2;
                int y = (getHeight() - drawH) / 2;
                g2.drawImage(image, x, y, drawW, drawH, null);
            } else {
                int hue = categoryName == null ? 0
                        : Math.floorMod(categoryName.toLowerCase().hashCode(), 5);
                Color tint = switch (hue) {
                    case 1 -> new Color(255, 244, 230);
                    case 2 -> new Color(232, 245, 233);
                    case 3 -> new Color(232, 240, 254);
                    case 4 -> new Color(253, 235, 240);
                    default -> AppColor.ACCENT_BG_SOFT;
                };
                g2.setColor(tint);
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            g2.setClip(null);
            g2.setColor(AppColor.BORDER);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(shape);
            g2.dispose();

            super.paintComponent(g);
        }
    }
}