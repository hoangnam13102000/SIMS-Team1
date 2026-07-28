package com.components.category;

import com.i18n.Lang;
import com.model.Category;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

/**
 * The danh muc (dung o CategoriesPanel phia client) - icon dai dien + ten
 * danh muc + so san pham dang ban, bo giong phong cach ProductCard (bo goc,
 * bong do nhe, hover nang len) de dong bo giao dien toan he thong.
 */
public class CategoryCard extends JPanel {

    private static final int ICON_BADGE_SIZE = 56;

    private boolean hover = false;

    public CategoryCard(Category category, Consumer<Category> onClick) {
        setOpaque(false);
        setLayout(new BorderLayout());
        setCursor(new Cursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(2, 2, 2, 2)); // chua cho bong do khong bi cat vien

        add(buildContent(category), BorderLayout.CENTER);

        if (onClick != null) {
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    onClick.accept(category);
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

    private JPanel buildContent(Category category) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JPanel iconBadge = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(categoryTint(category.getCategoryName()));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setOpaque(false);
        iconBadge.setPreferredSize(new Dimension(ICON_BADGE_SIZE, ICON_BADGE_SIZE));
        iconBadge.setMaximumSize(new Dimension(ICON_BADGE_SIZE, ICON_BADGE_SIZE));
        iconBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(categoryIcon(category.getCategoryName()), 24);
        icon.setIconColor(categoryIconColor(category.getCategoryName()));
        iconBadge.add(new JLabel(icon));

        JLabel nameLabel = new JLabel(category.getCategoryName());
        nameLabel.setFont(AppFont.HEADING_MD);
        nameLabel.setForeground(AppColor.TEXT_TITLE);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameLabel.setBorder(new EmptyBorder(AppSpacing.MD, 0, 2, 0));

        int count = category.getActiveProductCount();
        JLabel countLabel = new JLabel(count <= 0
                ? Lang.get("categories.card.empty")
                : Lang.get("categories.card.count", count));
        countLabel.setFont(AppFont.BODY);
        countLabel.setForeground(AppColor.TEXT_MUTED);
        countLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        content.add(iconBadge);
        content.add(nameLabel);
        content.add(countLabel);
        return content;
    }

    /** Chon icon dai dien don gian theo ten danh muc - suy doan tu chuoi, giong ProductCard. */
    private FontAwesomeSolid categoryIcon(String categoryName) {
        if (categoryName == null) return FontAwesomeSolid.TAGS;
        String c = categoryName.toLowerCase();
        if (c.contains("điện thoại") || c.contains("phone")) return FontAwesomeSolid.MOBILE_ALT;
        if (c.contains("laptop") || c.contains("máy tính")) return FontAwesomeSolid.LAPTOP;
        if (c.contains("tai nghe") || c.contains("headphone")) return FontAwesomeSolid.HEADPHONES;
        if (c.contains("đồng hồ") || c.contains("watch")) return FontAwesomeSolid.CLOCK;
        if (c.contains("sạc") || c.contains("cáp") || c.contains("cable")) return FontAwesomeSolid.PLUG;
        if (c.contains("ốp") || c.contains("case")) return FontAwesomeSolid.SHIELD_ALT;
        if (c.contains("rau") || c.contains("củ")) return FontAwesomeSolid.CARROT;
        if (c.contains("trái cây") || c.contains("hoa quả")) return FontAwesomeSolid.APPLE_ALT;
        if (c.contains("đồ uống") || c.contains("nước")) return FontAwesomeSolid.MUG_HOT;
        if (c.contains("thực phẩm") || c.contains("khô")) return FontAwesomeSolid.BREAD_SLICE;
        return FontAwesomeSolid.TAGS;
    }

    /** Mau nen icon thay doi nhe theo danh muc, giong logic categoryTint cua ProductCard. */
    private Color categoryTint(String categoryName) {
        int hue = categoryName == null ? 0 : Math.floorMod(categoryName.toLowerCase().hashCode(), 5);
        return switch (hue) {
            case 1 -> new Color(255, 244, 230);
            case 2 -> new Color(232, 245, 233);
            case 3 -> new Color(232, 240, 254);
            case 4 -> new Color(253, 235, 240);
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

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth() - 4;
        int h = getHeight() - 4;
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.translate(2, 2);

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