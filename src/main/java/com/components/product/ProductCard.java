package com.components.product;

import com.model.Product;
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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;


public class ProductCard extends JPanel {

    private static final int IMAGE_HEIGHT = 150;
    private static final int IMAGE_ICON_SIZE = 46;
    private static final int HEART_SIZE = 30;

    private boolean hover = false;
    private boolean favorite = false;

    public ProductCard(Product product) {
        this(product, null);
    }

    public ProductCard(Product product, Consumer<Product> onClick) {
        this(product, onClick, null, null);
    }

    public ProductCard(Product product, Consumer<Product> onClick,
                        Consumer<Product> onAddToCart, Consumer<Product> onFavorite) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(2, 2, 2, 2)); // chua cho bong do khong bi cat vien

        add(buildImageStack(product, onFavorite), BorderLayout.NORTH);
        add(buildInfoArea(product, onAddToCart), BorderLayout.CENTER);

        if (onClick != null) {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.accept(product);
                }

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
    }

    // ---------- Vung anh (bo goc tren, pill danh muc + nut tim de len tren) ----------

    private JLayeredPane buildImageStack(Product product, Consumer<Product> onFavorite) {
        JLayeredPane stack = new JLayeredPane();
        stack.setOpaque(false);
        stack.setPreferredSize(new Dimension(10, IMAGE_HEIGHT));
        stack.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                layoutImageStack(stack);
            }
        });

        JPanel imageArea = buildImageArea(product);
        imageArea.setBounds(0, 0, 10, IMAGE_HEIGHT);
        stack.add(imageArea, Integer.valueOf(0));
        stack.putClientProperty("imageArea", imageArea);

        if (!product.isOutOfStock() && product.getCategoryName() != null && !product.getCategoryName().isBlank()) {
            JLabel pill = buildCategoryPill(product.getCategoryName());
            pill.setSize(pill.getPreferredSize());
            stack.add(pill, Integer.valueOf(1));
            stack.putClientProperty("pill", pill);
        }
        if (product.isOutOfStock()) {
            JLabel pill = buildOutOfStockPill();
            pill.setSize(pill.getPreferredSize());
            stack.add(pill, Integer.valueOf(1));
            stack.putClientProperty("pill", pill);
        }

        JButton heart = buildHeartButton(product, onFavorite);
        heart.setSize(HEART_SIZE, HEART_SIZE);
        stack.add(heart, Integer.valueOf(1));
        stack.putClientProperty("heart", heart);

        return stack;
    }

    private void layoutImageStack(JLayeredPane stack) {
        int w = stack.getWidth();
        int h = stack.getHeight();

        Component imageArea = (Component) stack.getClientProperty("imageArea");
        if (imageArea != null) imageArea.setBounds(0, 0, w, h);

        Component pill = (Component) stack.getClientProperty("pill");
        if (pill != null) {
            Dimension d = pill.getPreferredSize();
            pill.setBounds(AppSpacing.SM, AppSpacing.SM, d.width, d.height);
        }

        Component heart = (Component) stack.getClientProperty("heart");
        if (heart != null) {
            heart.setBounds(w - HEART_SIZE - AppSpacing.SM, AppSpacing.SM, HEART_SIZE, HEART_SIZE);
        }
    }

    /** Neu san pham co ImageUrl hop le thi ve anh that (cover-fit, bo goc tren); neu khong co/loi anh thi
     *  dung icon dai dien theo danh muc tren nen mau nhe nhu truoc, bo goc tren giong khung anh that. */
    private JPanel buildImageArea(Product product) {
        BufferedImage realImage = loadProductImage(product.getImageUrl());

        JPanel wrapper = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setClip(topRoundedShape(getWidth(), getHeight(), AppRadius.LARGE));

                if (realImage != null) {
                    drawCoverFit(g2, realImage, getWidth(), getHeight());
                } else {
                    g2.setColor(categoryTint(product.getCategoryName()));
                    g2.fillRect(0, 0, getWidth(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wrapper.setOpaque(false);

        if (realImage == null) {
            FontIcon icon = FontIcon.of(categoryIcon(product.getCategoryName()), IMAGE_ICON_SIZE);
            icon.setIconColor(categoryIconColor(product.getCategoryName()));
            JLabel iconLabel = new JLabel(icon);
            wrapper.add(iconLabel);
        }

        return wrapper;
    }

    /** Doc anh san pham an toan tu duong dan/URL - tra ve null neu chua co hoac loi (khong lam crash luoi san pham). */
    private BufferedImage loadProductImage(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) return null;
        return ImageUtil.readSafe(imageUrl);
    }

    /** Ve anh phu kin het vung (w x h) kieu "cover" - giu ty le, cat bot phan du, luon lap day khung, khong bien dang. */
    private void drawCoverFit(Graphics2D g2, BufferedImage img, int w, int h) {
        double scale = Math.max((double) w / img.getWidth(), (double) h / img.getHeight());
        int drawW = (int) Math.ceil(img.getWidth() * scale);
        int drawH = (int) Math.ceil(img.getHeight() * scale);
        int x = (w - drawW) / 2;
        int y = (h - drawH) / 2;
        g2.drawImage(img, x, y, drawW, drawH, null);
    }

    private Path2D topRoundedShape(int w, int h, int radius) {
        Path2D path = new Path2D.Float();
        path.moveTo(0, radius);
        path.quadTo(0, 0, radius, 0);
        path.lineTo(Math.max(radius, w - radius), 0);
        path.quadTo(w, 0, w, radius);
        path.lineTo(w, h);
        path.lineTo(0, h);
        path.closePath();
        return path;
    }

    private JLabel buildCategoryPill(String categoryName) {
        return pillLabel(categoryName, new Color(255, 255, 255, 235), AppColor.TEXT_PRIMARY);
    }

    private JLabel buildOutOfStockPill() {
        return pillLabel("Hết hàng", AppColor.ERROR, Color.WHITE);
    }

    private JLabel pillLabel(String text, Color bg, Color fg) {
        JLabel label = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(fg);
        label.setBorder(new EmptyBorder(4, 10, 4, 10));
        return label;
    }

    /** Nut tim (yeu thich) noi tren goc phai anh - CHI toggle hien thi cuc bo, chua luu DB (xem javadoc lop nay). */
    private JButton buildHeartButton(Product product, Consumer<Product> onFavorite) {
        JButton heart = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(255, 255, 255, 235));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();

                FontIcon icon = FontIcon.of(FontAwesomeSolid.HEART, 14);
                icon.setIconColor(favorite ? AppColor.ERROR : AppColor.TEXT_MUTED);
                int ix = (getWidth() - icon.getIconWidth()) / 2;
                int iy = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, ix, iy);
            }
        };
        heart.setOpaque(false);
        heart.setContentAreaFilled(false);
        heart.setBorderPainted(false);
        heart.setFocusPainted(false);
        heart.setCursor(new Cursor(Cursor.HAND_CURSOR));
        heart.addActionListener(e -> {
            favorite = !favorite;
            heart.repaint();
            if (onFavorite != null) onFavorite.accept(product);
        });
        return heart;
    }

    // ---------- Vung thong tin (ten, gia, nut them vao gio) ----------

    private JPanel buildInfoArea(Product product, Consumer<Product> onAddToCart) {
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));

        JLabel nameLabel = new JLabel("<html>" + escapeHtml(product.getProductName()) + "</html>");
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_TITLE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel priceLabel = new JLabel(formatPrice(product));
        priceLabel.setFont(AppFont.getLargeBold());
        priceLabel.setForeground(AppColor.TEXT_TITLE);
        priceLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceLabel.setBorder(new EmptyBorder(4, 0, 10, 0));

        info.add(nameLabel);
        info.add(priceLabel);
        info.add(buildAddToCartButton(product, onAddToCart));

        return info;
    }

    private JButton buildAddToCartButton(Product product, Consumer<Product> onAddToCart) {
        boolean disabled = product.isOutOfStock();
        String text = disabled ? "Hết hàng" : "Thêm vào giỏ";

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

                Color bg = !isEnabled() ? AppColor.BG_LIGHTER : (btnHover ? AppColor.ACCENT : AppColor.WHITE);
                Color border = !isEnabled() ? AppColor.BORDER : AppColor.ACCENT;
                Color fg = !isEnabled() ? AppColor.TEXT_DISABLED : (btnHover ? Color.WHITE : AppColor.ACCENT_HOVER);

                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.setColor(border);
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();

                setForeground(fg);
                super.paintComponent(g);
            }
        };
        button.setText(text);
        button.setIcon(cartIcon(disabled ? AppColor.TEXT_DISABLED : AppColor.ACCENT_HOVER));
        button.setFont(AppFont.SMALL_BOLD);
        button.setIconTextGap(8);
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 0, 9, 0));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setEnabled(!disabled);
        button.setCursor(new Cursor(disabled ? Cursor.DEFAULT_CURSOR : Cursor.HAND_CURSOR));
        if (!disabled && onAddToCart != null) {
            button.addActionListener(e -> onAddToCart.accept(product));
        }
        return button;
    }

    private Icon cartIcon(Color color) {
        FontIcon icon = FontIcon.of(FontAwesomeSolid.SHOPPING_CART, 12);
        icon.setIconColor(color);
        return icon;
    }

    private String formatPrice(Product product) {
        long price = product.getSellPrice() == null ? 0 : product.getSellPrice().longValue();
        return NumberUtil.formatThousands(price) + " đ";
    }

    /** Chon icon dai dien don gian theo ten danh muc - suy doan tu chuoi, khong phu thuoc bang danh muc co dinh nao. */
    private FontAwesomeSolid categoryIcon(String categoryName) {
        if (categoryName == null) return FontAwesomeSolid.BOX;
        String c = categoryName.toLowerCase();
        if (c.contains("điện thoại") || c.contains("phone")) return FontAwesomeSolid.MOBILE_ALT;
        if (c.contains("laptop") || c.contains("máy tính")) return FontAwesomeSolid.LAPTOP;
        if (c.contains("tai nghe") || c.contains("headphone")) return FontAwesomeSolid.HEADPHONES;
        if (c.contains("đồng hồ") || c.contains("watch")) return FontAwesomeSolid.CLOCK;
        if (c.contains("sạc") || c.contains("cáp") || c.contains("cable")) return FontAwesomeSolid.PLUG;
        if (c.contains("ốp") || c.contains("case")) return FontAwesomeSolid.SHIELD_ALT;
        return FontAwesomeSolid.BOX;
    }

    /** Mau nen "anh" thay doi nhe theo danh muc de cac the khong bi lap lai 1 mau don dieu tren luoi. */
    private Color categoryTint(String categoryName) {
        int hue = categoryName == null ? 0 : Math.floorMod(categoryName.toLowerCase().hashCode(), 5);
        return switch (hue) {
            case 1 -> new Color(255, 244, 230); // cam nhat
            case 2 -> new Color(232, 245, 233); // xanh la nhat
            case 3 -> new Color(232, 240, 254); // xanh duong nhat
            case 4 -> new Color(253, 235, 240); // hong nhat
            default -> AppColor.ACCENT_BG_SOFT;
        };
    }

    private Color categoryIconColor(String categoryName) {
        int hue = categoryName == null ? 0 : Math.floorMod(categoryName.toLowerCase().hashCode(), 5);
        return switch (hue) {
            case 1 -> new Color(217, 119, 6);
            case 2 -> new Color(21, 128, 61);
            case 3 -> new Color(37, 99, 235);
            case 4 -> new Color(219, 39, 119);
            default -> AppColor.ACCENT_HOVER;
        };
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth() - 4;
        int h = getHeight() - 4;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(2, 2);

        // Bong do nhe nhieu lop de tao cam giac "noi" (blur gia lap) - dam hon khi hover.
        int shadowLayers = hover ? 5 : 3;
        int baseAlpha = hover ? 10 : 6;
        for (int i = shadowLayers; i >= 1; i--) {
            g2.setColor(new Color(15, 23, 42, baseAlpha));
            RoundRectangle2D.Float shadow = new RoundRectangle2D.Float(-i, -i + i * 2, w + i * 2, h + i * 2, AppRadius.LARGE, AppRadius.LARGE);
            g2.fill(shadow);
        }

        RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w, h, AppRadius.LARGE, AppRadius.LARGE);
        g2.setColor(AppColor.WHITE);
        g2.fill(shape);
        g2.setColor(hover ? AppColor.ACCENT_SOFT : AppColor.BORDER);
        g2.setStroke(new BasicStroke(hover ? 1.4f : 1f));
        g2.draw(shape);

        g2.dispose();
        super.paintComponent(g);
    }
}