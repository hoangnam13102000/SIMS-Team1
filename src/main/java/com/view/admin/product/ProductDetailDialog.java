package com.view.admin.product;

import com.components.StatBadge;
import com.model.Product;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.ImageUtil;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Dialog xem nhanh (chỉ đọc) thông tin 1 sản phẩm, mở từ nút "Xem chi tiết"
 * (icon mắt) trong {@link ProductPanel}. Cùng bố cục "thẻ thông tin" với
 * {@link com.view.admin.customer.CustomerDetailDialog}, nhưng thêm ảnh banner
 * lớn + thẻ giá/lợi nhuận + badge trạng thái tồn kho để bắt mắt hơn, phù hợp
 * đặc thù sản phẩm (thay vì avatar tròn của khách hàng).
 */
public class ProductDetailDialog extends JDialog {

    /** Chiều cao ảnh bên trái (layout ngang). */
    private static final int IMAGE_H = 280;
    /** Chiều rộng cố định cột ảnh. */
    private static final int IMAGE_W = 300;
    private static final int ICON_BOX_SIZE = 40;

    private Runnable onEditRequested;

    public ProductDetailDialog(java.awt.Frame owner, Product product) {
        super(owner, "Chi tiết sản phẩm", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        setResizable(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(product), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        // Layout ngang: ảnh trái + info phải → rộng hơn, thấp hơn, ít phải cuộn.
        setSize(780, 520);
        setLocationRelativeTo(owner);
    }

    /** Dang ky callback khi bam nut "Chỉnh sửa" o footer - dialog tu dispose() TRUOC khi goi callback, dung de ProductPanel mo tiep ProductFormDialog. */
    public void onEditRequested(Runnable callback) {
        this.onEditRequested = callback;
    }

    // ---------------------------------------------------------------
    // Header: chỉ tiêu đề (không nút X — footer đã có "Đóng")
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        JLabel title = new JLabel("Chi tiết sản phẩm");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        header.add(title, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body (layout ngang): ảnh trái | tên + badge + giá + info phải
    // ---------------------------------------------------------------

    private JComponent buildBody(Product product) {
        JPanel root = new JPanel(new BorderLayout(20, 0));
        root.setBackground(AppColor.WHITE);
        root.setBorder(new EmptyBorder(20, 24, 12, 24));

        root.add(buildProductImage(product), BorderLayout.WEST);
        root.add(buildInfoColumn(product), BorderLayout.CENTER);

        // Giữ scroll phòng trường hợp font/DPI lớn, nhưng layout ngang thường đủ 1 màn.
        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    /** Ảnh sản phẩm cố định bên trái, bo góc, crop-to-fill. */
    private JComponent buildProductImage(Product product) {
        JLabel image = new JLabel();
        image.setHorizontalAlignment(SwingConstants.CENTER);
        image.setPreferredSize(new Dimension(IMAGE_W, IMAGE_H));
        image.setMinimumSize(new Dimension(IMAGE_W, IMAGE_H));
        image.setMaximumSize(new Dimension(IMAGE_W, IMAGE_H));
        image.setIcon(coverIcon(product.getImageUrl(), IMAGE_W, IMAGE_H, image));
        return image;
    }

    /** Cột phải: tên, badge, thẻ giá, các dòng thông tin (2 cột). */
    private JComponent buildInfoColumn(Product product) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(0, 4, 0, 0));

        col.add(buildTitleSection(product));
        col.add(Box.createVerticalStrut(14));
        col.add(buildPriceCard(product));
        col.add(Box.createVerticalStrut(16));
        col.add(buildDivider());
        col.add(Box.createVerticalStrut(14));
        col.add(buildInfoGrid(product));

        return col;
    }

    /** Lưới 2 cột: Danh mục | Mã SP, Tồn kho | Tồn tối thiểu — gọn theo chiều ngang. */
    private JComponent buildInfoGrid(Product product) {
        JPanel grid = new JPanel(new java.awt.GridLayout(2, 2, 16, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        grid.add(infoRow(FontAwesomeSolid.LAYER_GROUP, "Danh mục", emptyDash(product.getCategoryName())));
        grid.add(infoRow(FontAwesomeSolid.HASHTAG, "Mã sản phẩm", "SP" + String.format("%05d", product.getProductId())));
        grid.add(buildStockRow(product));
        grid.add(infoRow(FontAwesomeSolid.ARROW_DOWN, "Tồn kho tối thiểu", product.getMinStock() + " sản phẩm"));

        return grid;
    }

    private ImageIcon coverIcon(String path, int w, int h, Component context) {
        BufferedImage src = ImageUtil.readSafe(path);
        BufferedImage canvas = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, w, h, 16, 16));

        if (src != null) {
            double scale = Math.max((double) w / src.getWidth(), (double) h / src.getHeight());
            int scaledW = Math.max(1, (int) Math.ceil(src.getWidth() * scale));
            int scaledH = Math.max(1, (int) Math.ceil(src.getHeight() * scale));
            BufferedImage scaled = ImageUtil.scale(src, scaledW, scaledH);
            g2.drawImage(scaled, (w - scaledW) / 2, (h - scaledH) / 2, null);
        } else {
            g2.setColor(AppColor.BG_LIGHTER);
            g2.fillRect(0, 0, w, h);
            FontIcon placeholderIcon = FontIcon.of(FontAwesomeSolid.IMAGE, 44);
            placeholderIcon.setIconColor(AppColor.BORDER);
            placeholderIcon.paintIcon(context, g2, (w - placeholderIcon.getIconWidth()) / 2, (h - placeholderIcon.getIconHeight()) / 2);
        }
        g2.dispose();
        return new ImageIcon(canvas);
    }

    /** Ten san pham (to, dam) + hang badge: danh muc / trang thai ban / trang thai ton kho. */
    private JPanel buildTitleSection(Product product) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel nameLabel = new JLabel("<html>" + escapeHtml(product.getProductName()) + "</html>");
        nameLabel.setFont(AppFont.HEADING_LG);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(nameLabel);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.setBorder(new EmptyBorder(8, 0, 0, 0));
        badgeRow.add(new StatBadge(emptyDash(product.getCategoryName()), AppColor.ACCENT));
        badgeRow.add(product.isActive()
                ? new StatBadge("Đang bán", AppColor.SUCCESS)
                : new StatBadge("Ngừng bán", AppColor.ERROR));
        badgeRow.add(new StatBadge(stockStatusLabel(product), stockStatusColor(product)));
        section.add(badgeRow);

        return section;
    }

    /** The gia nhap/gia ban 2 cot + dong loi nhuan/bien loi nhuan noi bat ben duoi, nen mem bo goc. */
    private JComponent buildPriceCard(Product product) {
        JPanel card = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        card.setBorder(new EmptyBorder(16, 18, 16, 18));

        JPanel priceRow = new JPanel(new java.awt.GridLayout(1, 2, 20, 0));
        priceRow.setOpaque(false);
        priceRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        priceRow.add(priceColumn("Giá nhập", product.getImportPrice(), AppColor.TEXT_PRIMARY, AppFont.bold(16)));
        priceRow.add(priceColumn("Giá bán", product.getSellPrice(), AppColor.ACCENT, AppFont.getLargeBold()));
        card.add(priceRow);

        card.add(Box.createVerticalStrut(14));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(12));
        card.add(buildProfitRow(product));

        return card;
    }

    private JPanel priceColumn(String label, BigDecimal amount, Color valueColor, Font valueFont) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(AppFont.SMALL);
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(labelLabel);

        JLabel valueLabel = new JLabel(formatMoney(amount));
        valueLabel.setFont(valueFont);
        valueLabel.setForeground(valueColor);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        col.add(Box.createVerticalStrut(4));
        col.add(valueLabel);

        return col;
    }

    private JPanel buildProfitRow(Product product) {
        BigDecimal importPrice = product.getImportPrice() != null ? product.getImportPrice() : BigDecimal.ZERO;
        BigDecimal sellPrice = product.getSellPrice() != null ? product.getSellPrice() : BigDecimal.ZERO;
        BigDecimal profit = sellPrice.subtract(importPrice);
        boolean profitable = profit.signum() > 0;

        String marginText;
        if (sellPrice.signum() > 0) {
            BigDecimal marginPercent = profit.multiply(BigDecimal.valueOf(100))
                    .divide(sellPrice, 1, RoundingMode.HALF_UP);
            marginText = " (" + marginPercent.toPlainString() + "%)";
        } else {
            marginText = "";
        }

        Color color = profit.signum() < 0 ? AppColor.ERROR : (profitable ? AppColor.SUCCESS : AppColor.TEXT_MUTED);

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.CHART_LINE, 14);
        icon.setIconColor(color);
        row.add(new JLabel(icon), BorderLayout.WEST);

        JLabel textLabel = new JLabel("Lợi nhuận mỗi sản phẩm: " + formatMoney(profit) + marginText);
        textLabel.setFont(AppFont.BODY_BOLD);
        textLabel.setForeground(color);
        row.add(textLabel, BorderLayout.CENTER);

        return row;
    }

    /** Dong "Ton kho" rieng (khac infoRow thuong): them 1 StatBadge nho canh gia tri so de bao trang thai (Con hang/Sap het/Het hang) - net "bat mat" chinh cua dialog. */
    private JPanel buildStockRow(Product product) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_BOX_SIZE));

        row.add(iconBox(FontAwesomeSolid.WAREHOUSE), BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel labelLabel = new JLabel("Tồn kho");
        labelLabel.setFont(AppFont.SMALL);
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(labelLabel);

        JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        valueRow.setOpaque(false);
        valueRow.setBorder(null);
        valueRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLabel = new JLabel(product.getStock() + " sản phẩm");
        valueLabel.setFont(AppFont.BODY_BOLD.deriveFont(14f));
        valueLabel.setForeground(stockStatusColor(product));
        valueRow.add(valueLabel);
        valueRow.add(new StatBadge(stockStatusLabel(product), stockStatusColor(product)));

        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(valueRow);

        row.add(textPanel, BorderLayout.CENTER);
        return row;
    }

    /** 1 dòng thông tin: icon vuông bo góc bên trái, nhãn nhỏ + giá trị đậm bên phải. */
    private JPanel infoRow(FontAwesomeSolid iconType, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_BOX_SIZE));

        row.add(iconBox(iconType), BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(AppFont.SMALL);
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(labelLabel);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(AppFont.BODY_BOLD.deriveFont(14f));
        valueLabel.setForeground(AppColor.TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(valueLabel);

        row.add(textPanel, BorderLayout.CENTER);
        return row;
    }

    /** O vuong bo goc nen nhat, chua icon - dung chung cho tat ca dong thong tin. */
    private JComponent iconBox(FontAwesomeSolid iconType) {
        JPanel box = new JPanel(new java.awt.GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(ICON_BOX_SIZE, ICON_BOX_SIZE));
        FontIcon icon = FontIcon.of(iconType, 16);
        icon.setIconColor(AppColor.TEXT_MUTED);
        box.add(new JLabel(icon));
        return box;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String formatMoney(BigDecimal amount) {
        long value = amount == null ? 0 : amount.longValue();
        return NumberUtil.formatThousands(value) + " đ";
    }

    /** "Hết hàng" (đỏ) neu Stock = 0, "Sắp hết" (cam) neu Stock <= MinStock, "Còn hàng" (xanh) con lai. */
    private String stockStatusLabel(Product product) {
        if (product.isOutOfStock()) return "Hết hàng";
        if (product.isLowStock()) return "Sắp hết";
        return "Còn hàng";
    }

    private Color stockStatusColor(Product product) {
        if (product.isOutOfStock()) return AppColor.ERROR;
        if (product.isLowStock()) return AppColor.WARNING;
        return AppColor.SUCCESS;
    }

    // ---------------------------------------------------------------
    // Footer: nút Đóng + Chỉnh sửa
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton closeButton = new JButton("Đóng");
        closeButton.setFont(AppFont.BODY_BOLD);
        closeButton.setFocusPainted(false);
        closeButton.setBackground(AppColor.CANCEL_BG);
        closeButton.setForeground(AppColor.TEXT_PRIMARY);
        closeButton.setBorder(new EmptyBorder(9, 20, 9, 20));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dispose());
        buttons.add(closeButton);

        JButton editButton = new JButton("Chỉnh sửa", FontIcon.of(FontAwesomeSolid.EDIT, 13, Color.WHITE));
        editButton.setFont(AppFont.BODY_BOLD);
        editButton.setFocusPainted(false);
        editButton.setBackground(AppColor.ACCENT);
        editButton.setForeground(Color.WHITE);
        editButton.setBorder(new EmptyBorder(9, 20, 9, 20));
        editButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        editButton.addActionListener(e -> {
            dispose();
            if (onEditRequested != null) onEditRequested.run();
        });
        buttons.add(editButton);

        footer.add(buttons, BorderLayout.EAST);
        getRootPane().setDefaultButton(closeButton);
        return footer;
    }
}