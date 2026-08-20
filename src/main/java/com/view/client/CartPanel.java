package com.view.client;

import com.components.BaseDialog;
import com.components.EmptyState;
import com.dao.OrderDAO;
import com.i18n.Lang;
import com.model.CartItem;
import com.model.Order;
import com.model.OrderDetail;
import com.service.AuthService;
import com.service.CartService;
import com.service.payment.PayPalService;
import com.service.payment.VietQrPayOsService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.ImageUtil;
import com.utils.NumberUtil;
import com.utils.QrCodeUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

public class CartPanel extends JPanel {

    private static final int THUMB_SIZE = 72;
    private static final int SUMMARY_WIDTH = 340;

    private final CardLayout contentLayout = new CardLayout();
    private final JPanel contentArea;

    private final JPanel itemsRowsContainer;
    private final JLabel itemsCountLabel;

    private final JLabel subtotalCaptionLabel;
    private final JLabel subtotalValueLabel;
    private final JLabel discountValueLabel;
    private final JLabel totalValueLabel;
    private final JButton checkoutButton;
    private final JTextField promoCodeField = new JTextField();
    private final JButton applyPromoButton = new JButton("Áp dụng");
    private final JButton clearPromoButton = new JButton("Bỏ");
    private final JLabel promoStatusLabel = new JLabel(" ");

    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField addressField;

    private Runnable onCheckoutSuccess;
    private Runnable continueShoppingListener;

    public CartPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        add(buildHeaderBlock(), BorderLayout.NORTH);

        contentArea = new JPanel(contentLayout);
        contentArea.setOpaque(false);

        contentArea.add(buildEmptyStatePanel(), "empty");

        itemsCountLabel = new JLabel();
        itemsRowsContainer = new JPanel();
        itemsRowsContainer.setOpaque(false);
        itemsRowsContainer.setLayout(new BoxLayout(itemsRowsContainer, BoxLayout.Y_AXIS));

        subtotalCaptionLabel = new JLabel();
        subtotalValueLabel = new JLabel();
        discountValueLabel = new JLabel("0 đ");
        totalValueLabel = new JLabel();
        checkoutButton = buildCheckoutButton();

        contentArea.add(buildTwoColumnLayout(), "items");

        JScrollPane scroll = new JScrollPane(contentArea);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.PAGE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        prefillShippingFromAccount();

        CartService.getInstance().addListener(this::loadCart);
        loadCart();
    }

    public void onCheckoutSuccess(Runnable listener) {
        this.onCheckoutSuccess = listener;
    }

    /** Goi khi nguoi dung bam "Tiep tuc mua sam" tu trang thai gio hang rong - dieu huong sang trang San pham. */
    public void onContinueShopping(Runnable listener) {
        this.continueShoppingListener = listener;
    }

    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("cart.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        wrapper.add(title);
        return wrapper;
    }

    private JPanel buildEmptyStatePanel() {
        EmptyState empty = EmptyState.noData(Lang.get("cart.noData.entity"));
        empty.setAction(Lang.get("cart.continueShopping"), () -> {
            if (continueShoppingListener != null) continueShoppingListener.run();
        });

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(20, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        wrapper.add(empty, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildTwoColumnLayout() {
        JPanel wrapper = new JPanel(new GridBagLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.anchor = GridBagConstraints.NORTH;

        gbc.gridx = 0;
        gbc.weightx = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, AppSpacing.LG);
        wrapper.add(buildLeftColumn(), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.VERTICAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        wrapper.add(buildSummaryCard(), gbc);

        return wrapper;
    }

    private JPanel buildLeftColumn() {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));

        column.add(buildItemsCard());
        column.add(Box.createVerticalStrut(AppSpacing.MD));
        column.add(buildLinksRow());
        column.add(Box.createVerticalStrut(AppSpacing.LG));
        column.add(buildShippingCard());

        return column;
    }

    // ==================== Danh sach san pham (Cot trai) ====================

    private JPanel buildItemsCard() {
        JPanel card = card();

        itemsCountLabel.setFont(AppFont.SMALL);
        itemsCountLabel.setForeground(AppColor.TEXT_MUTED);
        itemsCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsCountLabel.setBorder(new EmptyBorder(0, 0, AppSpacing.MD, 0));

        card.add(itemsCountLabel);
        card.add(divider());
        card.add(itemsRowsContainer);
        return card;
    }

    /** Ve lai danh sach dong san pham trong the gio hang - khong dung toi shippingCard/cac o nhap. */
    private void rebuildItemRows(List<CartItem> items) {
        itemsRowsContainer.removeAll();
        for (int i = 0; i < items.size(); i++) {
            itemsRowsContainer.add(Box.createVerticalStrut(AppSpacing.MD));
            itemsRowsContainer.add(buildItemRow(items.get(i)));
            if (i < items.size() - 1) {
                itemsRowsContainer.add(Box.createVerticalStrut(AppSpacing.MD));
                itemsRowsContainer.add(divider());
            }
        }
        itemsRowsContainer.revalidate();
        itemsRowsContainer.repaint();
    }

    private JPanel buildItemRow(CartItem item) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        JLabel thumb = new JLabel(loadRoundedThumb(item.getProduct().getImageUrl(), THUMB_SIZE));
        thumb.setVerticalAlignment(SwingConstants.TOP);
        row.add(thumb, BorderLayout.WEST);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JPanel topRow = new JPanel(new BorderLayout());
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        topRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel name = new JLabel(item.getProduct().getProductName());
        name.setFont(new Font("Segoe UI", Font.BOLD, 14));
        name.setForeground(AppColor.TEXT_PRIMARY);
        topRow.add(name, BorderLayout.WEST);
        topRow.add(buildRemoveButton(item), BorderLayout.EAST);
        right.add(topRow);

        String categoryName = item.getProduct().getCategoryName();
        if (categoryName != null && !categoryName.isBlank()) {
            JLabel subtitle = new JLabel(categoryName);
            subtitle.setFont(AppFont.SMALL);
            subtitle.setForeground(AppColor.TEXT_MUTED);
            subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
            subtitle.setBorder(new EmptyBorder(2, 0, 0, 0));
            right.add(subtitle);
        }

        right.add(Box.createVerticalGlue());

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        bottomRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottomRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel priceStack = new JPanel();
        priceStack.setOpaque(false);
        priceStack.setLayout(new BoxLayout(priceStack, BoxLayout.Y_AXIS));

        JLabel price = new JLabel(NumberUtil.formatThousands(item.getSubtotal()) + " đ");
        price.setFont(new Font("Segoe UI", Font.BOLD, 15));
        price.setForeground(AppColor.ACCENT_HOVER);
        price.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceStack.add(price);

        JLabel unitCaption = new JLabel(NumberUtil.formatThousands(unitPrice(item)) + " đ / " + Lang.get("cart.unit"));
        unitCaption.setFont(AppFont.SMALL);
        unitCaption.setForeground(AppColor.TEXT_MUTED);
        unitCaption.setAlignmentX(Component.LEFT_ALIGNMENT);
        priceStack.add(unitCaption);

        bottomRow.add(priceStack, BorderLayout.WEST);
        bottomRow.add(buildQtyStepper(item), BorderLayout.EAST);
        right.add(bottomRow);

        row.add(right, BorderLayout.CENTER);
        return row;
    }

    private long unitPrice(CartItem item) {
        return item.getProduct().getSellPrice() == null ? 0 : item.getProduct().getSellPrice().longValue();
    }

    private JButton buildRemoveButton(CartItem item) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                FontIcon icon = FontIcon.of(FontAwesomeSolid.TRASH_ALT, 14);
                icon.setIconColor(AppColor.TEXT_MUTED);
                int ix = (getWidth() - icon.getIconWidth()) / 2;
                int iy = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, ix, iy);
            }
        };
        button.setPreferredSize(new Dimension(26, 26));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setToolTipText(Lang.get("cart.remove"));
        button.addActionListener(e -> CartService.getInstance().removeItem(item.getProduct().getProductId()));
        return button;
    }

    private JPanel buildQtyStepper(CartItem item) {
        JPanel stepper = new JPanel(new GridLayout(1, 3));
        stepper.setOpaque(false);
        stepper.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        stepper.setPreferredSize(new Dimension(108, 36));

        JButton minus = stepButton(FontAwesomeSolid.MINUS, item, -1);

        JLabel qtyLabel = new JLabel(String.valueOf(item.getQuantity()), SwingConstants.CENTER);
        qtyLabel.setFont(AppFont.BODY_BOLD);
        qtyLabel.setForeground(AppColor.TEXT_PRIMARY);
        qtyLabel.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 1, AppColor.BORDER));

        JButton plus = stepButton(FontAwesomeSolid.PLUS, item, 1);

        stepper.add(minus);
        stepper.add(qtyLabel);
        stepper.add(plus);
        return stepper;
    }

    private JButton stepButton(FontAwesomeSolid iconType, CartItem item, int delta) {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                FontIcon icon = FontIcon.of(iconType, 10);
                icon.setIconColor(AppColor.TEXT_SECONDARY);
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
        button.addActionListener(e ->
                CartService.getInstance().updateQuantity(item.getProduct().getProductId(), item.getQuantity() + delta));
        return button;
    }

    // ==================== "Tiep tuc mua sam" / "Xoa toan bo gio hang" ====================

    private JPanel buildLinksRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel continueLink = linkLabel("← " + Lang.get("cart.continueShopping"), AppColor.TEXT_MUTED);
        continueLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (continueShoppingListener != null) continueShoppingListener.run();
            }
        });

        JLabel clearLink = linkLabel(Lang.get("cart.clearAll"), AppColor.ERROR);
        clearLink.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleClearAll();
            }
        });

        row.add(continueLink, BorderLayout.WEST);
        row.add(clearLink, BorderLayout.EAST);
        return row;
    }

    private JLabel linkLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(color);
        label.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return label;
    }

    private void handleClearAll() {
        boolean confirmed = BaseDialog.confirm(
                this,
                Lang.get("cart.clearAll.confirm.title"),
                Lang.get("cart.clearAll.confirm.message"),
                Lang.get("cart.clearAll"),
                AppColor.ERROR,
                AppColor.ERROR_HOVER,
                FontAwesomeSolid.TRASH_ALT
        );
        if (confirmed) {
            CartService.getInstance().clear();
        }
    }

    // ==================== Form "Thong tin giao hang" (Cot trai) ====================

    private JPanel buildShippingCard() {
        JPanel card = card();
        card.add(cardTitle(Lang.get("cart.shipping.title")));

        JPanel row1 = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row1.setOpaque(false);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        row1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        nameField = new JTextField();
        styleField(nameField);
        row1.add(fieldGroup(Lang.get("cart.shipping.fullName") + " *", nameField));

        phoneField = new JTextField();
        styleField(phoneField);
        row1.add(fieldGroup(Lang.get("cart.shipping.phone"), phoneField));

        card.add(row1);

        emailField = new JTextField();
        styleField(emailField);
        card.add(fieldGroup(Lang.get("cart.shipping.email") + " *", emailField));

        addressField = new JTextField();
        addressField.putClientProperty("JTextField.placeholderText", Lang.get("cart.shipping.address.placeholder"));
        styleField(addressField);
        card.add(fieldGroup(Lang.get("cart.shipping.address") + " *", addressField));

        return card;
    }

    private void prefillShippingFromAccount() {
        if (!AuthService.getInstance().isLoggedIn()) return;
        com.model.User user = AuthService.getInstance().getCurrentUser();
        if (user.getFullName() != null) nameField.setText(user.getFullName());
        if (user.getPhone() != null) phoneField.setText(user.getPhone());
        if (user.getEmail() != null) emailField.setText(user.getEmail());
    }

    // ==================== Tom tat don hang (Cot phai) ====================

    private JPanel buildSummaryCard() {
        JPanel card = sideCard();
        card.add(cardTitle(Lang.get("cart.summary.title")));

        // Giữ nguyên toàn bộ các dòng tóm tắt — chỉ sửa layout cho căn đều như hình
        card.add(summaryRow(subtotalCaptionLabel, subtotalValueLabel, AppColor.TEXT_MUTED, AppColor.TEXT_PRIMARY, false));
        card.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel discountLabel = new JLabel("Giảm giá");
        card.add(summaryRow(discountLabel, discountValueLabel, AppColor.TEXT_MUTED, AppColor.SUCCESS, false));
        card.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel shippingLabel = new JLabel(Lang.get("cart.summary.shippingFee"));
        JLabel shippingValue = new JLabel(Lang.get("cart.summary.freeShipping"));
        card.add(summaryRow(shippingLabel, shippingValue, AppColor.TEXT_MUTED, AppColor.SUCCESS, false));

        card.add(Box.createVerticalStrut(AppSpacing.MD));
        card.add(buildPromoBlock());
        card.add(Box.createVerticalStrut(AppSpacing.MD));
        card.add(summaryDivider());
        card.add(Box.createVerticalStrut(AppSpacing.MD));

        JLabel totalLabel = new JLabel(Lang.get("cart.summary.total"));
        card.add(summaryRow(totalLabel, totalValueLabel, AppColor.TEXT_TITLE, AppColor.ACCENT_HOVER, true));
        card.add(Box.createVerticalStrut(AppSpacing.LG));

        card.add(checkoutButton);

        JLabel caption = new JLabel(Lang.get("cart.summary.caption"), SwingConstants.CENTER);
        caption.setFont(AppFont.SMALL);
        caption.setForeground(AppColor.TEXT_MUTED);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        caption.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));
        caption.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        caption.setPreferredSize(new Dimension(SUMMARY_WIDTH - 40, 28));
        card.add(caption);

        return card;
    }

    /**
     * Hàng nhãn trái – giá trị phải, chiếm full chiều ngang nội dung summary card
     * (SUMMARY_WIDTH - padding 20*2) để không còn khoảng trắng thừa bên trái/phải.
     */
    private JPanel summaryRow(JLabel labelComp, JLabel valueComp, Color labelColor, Color valueColor, boolean big) {
        final int innerWidth = SUMMARY_WIDTH - 40;
        final int rowHeight = big ? 34 : 24;

        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(innerWidth, rowHeight));
        row.setMaximumSize(new Dimension(innerWidth, rowHeight));
        row.setMinimumSize(new Dimension(innerWidth, rowHeight));

        labelComp.setFont(big ? AppFont.HEADING_MD : AppFont.BODY);
        labelComp.setForeground(labelColor);

        valueComp.setFont(big ? new Font("Segoe UI", Font.BOLD, 20) : AppFont.BODY_BOLD);
        valueComp.setForeground(valueColor);
        valueComp.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);
        return row;
    }

    /** Divider chỉ dùng trong summary card — width cố định = inner width. */
    private JComponent summaryDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setPreferredSize(new Dimension(SUMMARY_WIDTH - 40, 1));
        sep.setMaximumSize(new Dimension(SUMMARY_WIDTH - 40, 1));
        return sep;
    }

    private JButton buildCheckoutButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isEnabled() ? AppColor.ACCENT_HOVER : AppColor.DISABLED_BTN);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setText(Lang.get("cart.checkout") + "   →");
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(SUMMARY_WIDTH - 40, 48));
        button.setMaximumSize(new Dimension(SUMMARY_WIDTH - 40, 48));
        button.setMinimumSize(new Dimension(SUMMARY_WIDTH - 40, 48));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setOpaque(false);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("Segoe UI", Font.BOLD, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> handleCheckout());
        return button;
    }


    private JPanel buildPromoBlock() {
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Mã khuyến mãi");
        title.setFont(AppFont.SMALL_BOLD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(title);
        box.add(Box.createVerticalStrut(6));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(SUMMARY_WIDTH, 34));

        promoCodeField.setFont(AppFont.BODY);
        promoCodeField.putClientProperty("JTextField.placeholderText", "Nhập mã KM");
        promoCodeField.addActionListener(e -> applyPromoFromField());
        row.add(promoCodeField, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        btns.setOpaque(false);
        applyPromoButton.setFont(AppFont.SMALL);
        applyPromoButton.setFocusPainted(false);
        applyPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyPromoButton.addActionListener(e -> applyPromoFromField());
        btns.add(applyPromoButton);

        clearPromoButton.setFont(AppFont.SMALL);
        clearPromoButton.setFocusPainted(false);
        clearPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearPromoButton.setVisible(false);
        clearPromoButton.addActionListener(e -> {
            CartService.getInstance().clearPromotion();
            promoCodeField.setText("");
            promoCodeField.setEditable(true);
            promoStatusLabel.setText(" ");
            promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
            loadCart();
        });
        btns.add(clearPromoButton);
        row.add(btns, BorderLayout.EAST);
        box.add(row);

        promoStatusLabel.setFont(AppFont.SMALL);
        promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
        promoStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(promoStatusLabel);
        return box;
    }

    private void applyPromoFromField() {
        String code = promoCodeField.getText() != null ? promoCodeField.getText().trim() : "";
        if (code.isEmpty()) {
            BaseDialog.error(this, "Khuyến mãi", "Vui lòng nhập mã khuyến mãi.");
            return;
        }
        if (CartService.getInstance().isEmpty()) {
            BaseDialog.error(this, "Khuyến mãi", "Giỏ hàng đang trống.");
            return;
        }
        var result = CartService.getInstance().applyPromotionCode(code);
        if (result.ok) {
            promoStatusLabel.setText("Đã áp dụng: " + result.promotion.getName()
                    + " (−" + NumberUtil.formatThousands(result.discountAmount.longValue()) + " đ)");
            promoStatusLabel.setForeground(AppColor.SUCCESS);
            clearPromoButton.setVisible(true);
            promoCodeField.setEditable(false);
            loadCart();
        } else {
            promoStatusLabel.setText(result.message != null ? result.message : "Mã không hợp lệ");
            promoStatusLabel.setForeground(AppColor.ERROR);
            clearPromoButton.setVisible(false);
            promoCodeField.setEditable(true);
        }
    }

    // ==================== Nap lai / thanh toan ====================

    /** Goi lai moi khi CartService bao thay doi (them/sua/xoa) hoac khi mo trang - cap nhat so lieu + danh sach. */
    public void loadCart() {
        List<CartItem> items = CartService.getInstance().getItems();

        if (items.isEmpty()) {
            contentLayout.show(contentArea, "empty");
            return;
        }

        itemsCountLabel.setText(Lang.get("cart.itemCount", items.size()));
        rebuildItemRows(items);

        CartService cart = CartService.getInstance();
        long subtotal = cart.getTotal();
        long discount = cart.getDiscountAmountLong();
        long total = cart.getPayableTotal();

        if (subtotalCaptionLabel != null) {
            subtotalCaptionLabel.setText(Lang.get("cart.summary.subtotal", items.size()));
        }
        subtotalValueLabel.setText(NumberUtil.formatThousands(subtotal) + " đ");
        discountValueLabel.setText((discount > 0 ? "−" : "") + NumberUtil.formatThousands(discount) + " đ");
        totalValueLabel.setText(NumberUtil.formatThousands(total) + " đ");
        boolean hasPromo = cart.getAppliedPromotion() != null;
        clearPromoButton.setVisible(hasPromo);
        promoCodeField.setEditable(!hasPromo);
        if (hasPromo) {
            promoCodeField.setText(cart.getAppliedPromotion().getCode());
        }
        checkoutButton.setEnabled(true);

        contentLayout.show(contentArea, "items");
    }

    private final OrderDAO orderDAO = new OrderDAO();

    private void handleCheckout() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        String phone = phoneField.getText() == null ? "" : phoneField.getText().trim();
        String email = emailField.getText() == null ? "" : emailField.getText().trim();
        String address = addressField.getText() == null ? "" : addressField.getText().trim();

        if (name.isEmpty() || email.isEmpty() || address.isEmpty()) {
            BaseDialog.error(this, Lang.get("cart.shipping.validate.title"), Lang.get("cart.shipping.validate.required"));
            return;
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            BaseDialog.error(this, Lang.get("cart.shipping.validate.title"), Lang.get("cart.shipping.validate.email"));
            return;
        }

        CartService cart = CartService.getInstance();
        List<CartItem> cartItems = cart.getItems();
        if (cartItems.isEmpty()) return;
        long total = cart.getPayableTotal();
        PaymentDialog.Method method = PaymentDialog.show(this, cartItems, total);
        if (method == null) return;

        List<OrderDetail> details = new ArrayList<>();
        BigDecimal subTotal = BigDecimal.ZERO;
        for (CartItem item : cartItems) {
            BigDecimal unitPrice = item.getProduct().getSellPrice();
            details.add(new OrderDetail(item.getProduct().getProductId(), item.getProduct().getProductName(),
                    item.getQuantity(), unitPrice));
            subTotal = subTotal.add(unitPrice.multiply(BigDecimal.valueOf(item.getQuantity())));
        }

        BigDecimal discount = cart.getDiscountAmount();
        BigDecimal payable = cart.getPayableTotalDecimal();

        Order order = new Order();
        if (AuthService.getInstance().isLoggedIn()) {
            order.setCustomerId(AuthService.getInstance().getCurrentUser().getUserId());
        }
        order.setCustomerName(name);
        order.setCustomerEmail(email);
        order.setCustomerPhone(phone.isEmpty() ? null : phone);
        order.setShippingAddress(address);
        order.setSubTotal(subTotal);
        order.setDiscountAmount(discount);
        if (cart.getAppliedPromotion() != null) {
            order.setPromotionId(cart.getAppliedPromotion().getPromotionId());
            order.setPromotionCode(cart.getAppliedPromotion().getCode());
        }
        order.setTotalAmount(payable);
        order.setOrderStatus("NEW");

        if (method == PaymentDialog.Method.COD) {
            order.setPaymentMethod("COD");
            order.setPaymentStatus("PENDING");
            persistOrderAndFinish(order, details);
        } else if (method == PaymentDialog.Method.BANK_TRANSFER) {
            order.setPaymentMethod("BANK_TRANSFER");
            payWithVietQrThenPersist(order, details, payable);
        } else {
            order.setPaymentMethod("PAYPAL");
            payWithPayPalThenPersist(order, details, payable);
        }
    }

    /** Luu don xuong DB (nen luon chay o background thread - la thao tac DB blocking). */
    private void persistOrderAndFinish(Order order, List<OrderDetail> details) {
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                return orderDAO.createOrder(order, details);
            }

            @Override
            protected void done() {
                boolean ok;
                try {
                    ok = get();
                } catch (Exception e) {
                    ok = false;
                }
                if (ok) {
                    CartService.getInstance().clear();
                    promoCodeField.setText("");
                    promoCodeField.setEditable(true);
                    promoStatusLabel.setText(" ");
                    BaseDialog.success(CartPanel.this, Lang.get("cart.checkout.success.title"),
                            Lang.get("cart.checkout.success.message"));
                    if (onCheckoutSuccess != null) onCheckoutSuccess.run();
                } else {
                    BaseDialog.error(CartPanel.this, Lang.get("payment.error.title"), Lang.get("payment.order.save.failed"));
                }
            }
        };
        worker.execute();
    }


    /**
     * Chuyển khoản online qua VietQR/payOS, dùng cùng cổng thanh toán với POS.
     * Chỉ lưu Order sau khi payOS xác nhận PAID. Khi tiền đã vào nhưng DB không
     * lưu được đơn, hệ thống báo rõ để khách KHÔNG chuyển lại.
     */
    private void payWithVietQrThenPersist(Order order, List<OrderDetail> details, BigDecimal totalVnd) {
        long amountVnd;
        try {
            amountVnd = totalVnd.longValueExact();
        } catch (Exception ex) {
            BaseDialog.error(this, "Thanh toán chuyển khoản",
                    "Số tiền đơn hàng không hợp lệ để tạo VietQR.");
            return;
        }
        if (amountVnd <= 0) {
            BaseDialog.error(this, "Thanh toán chuyển khoản",
                    "Số tiền đơn hàng phải lớn hơn 0.");
            return;
        }

        final VietQrPayOsService vietQrService;
        try {
            vietQrService = new VietQrPayOsService();
        } catch (Exception ex) {
            BaseDialog.error(this, "Chưa cấu hình chuyển khoản",
                    "Không thể khởi tạo payOS/VietQR: " + ex.getMessage());
            return;
        }

        JLabel qrLabel = new JLabel("Đang tạo mã VietQR...", SwingConstants.CENTER);
        qrLabel.setPreferredSize(new Dimension(280, 280));
        qrLabel.setMinimumSize(new Dimension(280, 280));
        qrLabel.setMaximumSize(new Dimension(280, 280));
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel amountLabel = new JLabel(NumberUtil.formatThousands(amountVnd) + " đ");
        amountLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        amountLabel.setForeground(AppColor.ACCENT_HOVER);
        amountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel statusLabel = new JLabel("Đang kết nối payOS...");
        statusLabel.setFont(AppFont.SMALL);
        statusLabel.setForeground(AppColor.TEXT_MUTED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton openPageBtn = new JButton("Mở trang thanh toán");
        openPageBtn.setEnabled(false);
        openPageBtn.setFocusPainted(false);
        openPageBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        AtomicBoolean paymentLocked = new AtomicBoolean(false);
        AtomicBoolean userCancelled = new AtomicBoolean(false);
        String[] checkoutUrlHolder = new String[1];

        JDialog waitingDialog = buildOnlineVietQrWaitingDialog(
                qrLabel, amountLabel, statusLabel, openPageBtn, () -> {
                    if (paymentLocked.get()) {
                        BaseDialog.info(CartPanel.this, "Đang hoàn tất thanh toán",
                                "payOS đã xác nhận thanh toán. Hệ thống đang tạo đơn hàng, không thể hủy lúc này.");
                        return;
                    }
                    userCancelled.set(true);
                    statusLabel.setText("Đang hủy yêu cầu thanh toán...");
                });

        openPageBtn.addActionListener(e -> {
            String url = checkoutUrlHolder[0];
            if (url == null || url.isBlank()) return;
            try {
                vietQrService.openCheckoutPage(url);
            } catch (Exception ex) {
                BaseDialog.error(CartPanel.this, "Không thể mở trang thanh toán",
                        ex.getMessage() == null ? "Không mở được trình duyệt." : ex.getMessage());
            }
        });

        SwingWorker<OnlineVietQrResult, VietQrPayOsService.CreatedPayment> worker = new SwingWorker<>() {
            @Override
            protected OnlineVietQrResult doInBackground() throws Exception {
                VietQrPayOsService.CreatedPayment created = vietQrService.createPayment(amountVnd);
                checkoutUrlHolder[0] = created.checkoutUrl();
                publish(created);

                String paymentId = created.paymentLinkId();
                long deadlineNanos = System.nanoTime()
                        + Duration.ofSeconds(vietQrService.getExpireSeconds() + 15L).toNanos();
                Exception lastStatusError = null;

                while (System.nanoTime() < deadlineNanos) {
                    if (userCancelled.get()) {
                        VietQrPayOsService.PaymentStatus finalStatus;
                        try {
                            finalStatus = vietQrService.cancelPayment(paymentId, "Online customer cancelled");
                        } catch (Exception cancelError) {
                            try {
                                finalStatus = vietQrService.getPaymentStatus(paymentId);
                            } catch (Exception readError) {
                                return new OnlineVietQrResult(false, "STATUS_UNKNOWN", created.orderCode(),
                                        paymentId, null,
                                        "Không thể xác định giao dịch đã thanh toán hay chưa. "
                                      + "Không chuyển lại tiền cho đến khi kiểm tra được trạng thái.");
                            }
                        }

                        if (finalStatus.isPaid()) {
                            paymentLocked.set(true);
                            return finishPaidOnlineVietQr(order, details, amountVnd, created, finalStatus);
                        }

                        return new OnlineVietQrResult(false, "CANCELLED", created.orderCode(), paymentId,
                                finalStatus.reference(), "Đã hủy giao dịch VietQR. Giỏ hàng vẫn được giữ nguyên.");
                    }

                    try {
                        Thread.sleep(vietQrService.getPollIntervalMillis());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        return new OnlineVietQrResult(false, "STATUS_UNKNOWN", created.orderCode(), paymentId,
                                null, "Luồng kiểm tra VietQR bị gián đoạn. Hãy kiểm tra giao dịch trước khi thử lại.");
                    }

                    try {
                        VietQrPayOsService.PaymentStatus status = vietQrService.getPaymentStatus(paymentId);
                        lastStatusError = null;

                        if (status.isPaid()) {
                            paymentLocked.set(true);
                            return finishPaidOnlineVietQr(order, details, amountVnd, created, status);
                        }

                        if (status.isCancelledOrExpired()) {
                            return new OnlineVietQrResult(false, status.status(), created.orderCode(),
                                    paymentId, status.reference(), "Giao dịch VietQR không còn hiệu lực.");
                        }
                    } catch (Exception statusError) {
                        lastStatusError = statusError; // lỗi mạng tạm thời, tiếp tục polling
                    }
                }

                try {
                    VietQrPayOsService.PaymentStatus finalStatus =
                            vietQrService.cancelPayment(paymentId, "Online payment timeout");
                    if (finalStatus.isPaid()) {
                        paymentLocked.set(true);
                        return finishPaidOnlineVietQr(order, details, amountVnd, created, finalStatus);
                    }
                    return new OnlineVietQrResult(false, "TIMEOUT", created.orderCode(), paymentId,
                            finalStatus.reference(), "Mã VietQR đã hết thời gian chờ và được hủy.");
                } catch (Exception finalError) {
                    String detail = lastStatusError != null ? lastStatusError.getMessage() : finalError.getMessage();
                    return new OnlineVietQrResult(false, "STATUS_UNKNOWN", created.orderCode(), paymentId, null,
                            "Không thể xác định trạng thái cuối của VietQR"
                                    + (detail == null || detail.isBlank() ? "." : ": " + detail)
                                    + " Không chuyển lại tiền cho đến khi kiểm tra được giao dịch.");
                }
            }

            @Override
            protected void process(List<VietQrPayOsService.CreatedPayment> chunks) {
                if (chunks.isEmpty()) return;
                VietQrPayOsService.CreatedPayment created = chunks.get(chunks.size() - 1);
                try {
                    BufferedImage qr = QrCodeUtil.generate(created.qrContent(), 280);
                    qrLabel.setText(null);
                    qrLabel.setIcon(new ImageIcon(qr));
                } catch (Exception ex) {
                    qrLabel.setIcon(null);
                    qrLabel.setText("<html><center>Không tạo được ảnh QR.<br>"
                            + "Hãy dùng nút Mở trang thanh toán.</center></html>");
                }
                amountLabel.setText(NumberUtil.formatThousands(created.amount()) + " đ");
                statusLabel.setText("Nội dung: " + created.description() + " · Đang chờ chuyển khoản...");
                openPageBtn.setEnabled(created.checkoutUrl() != null && !created.checkoutUrl().isBlank());
            }

            @Override
            protected void done() {
                waitingDialog.dispose();
                try {
                    OnlineVietQrResult result = get();
                    if (result.success()) {
                        CartService.getInstance().clear();
                        promoCodeField.setText("");
                        promoCodeField.setEditable(true);
                        promoStatusLabel.setText(" ");
                        BaseDialog.success(CartPanel.this, Lang.get("cart.checkout.success.title"),
                                "Chuyển khoản đã được xác nhận và đơn hàng đã tạo thành công.");
                        if (onCheckoutSuccess != null) onCheckoutSuccess.run();
                        return;
                    }

                    if ("CANCELLED".equalsIgnoreCase(result.status())) {
                        BaseDialog.info(CartPanel.this, "Đã hủy chuyển khoản", result.message());
                    } else if ("TIMEOUT".equalsIgnoreCase(result.status())
                            || "EXPIRED".equalsIgnoreCase(result.status())) {
                        BaseDialog.error(CartPanel.this, "VietQR đã hết hạn", result.message());
                    } else if ("PAID_ORDER_FAILED".equalsIgnoreCase(result.status())) {
                        BaseDialog.error(CartPanel.this, "Đã nhận tiền nhưng chưa tạo được đơn",
                                result.message());
                    } else if ("PAYMENT_MISMATCH".equalsIgnoreCase(result.status())) {
                        BaseDialog.error(CartPanel.this, "Dữ liệu thanh toán không khớp", result.message());
                    } else {
                        BaseDialog.error(CartPanel.this, "Thanh toán chuyển khoản thất bại", result.message());
                    }
                } catch (Exception ex) {
                    BaseDialog.error(CartPanel.this, "Thanh toán chuyển khoản thất bại",
                            ex.getMessage() == null ? "Không thể hoàn tất giao dịch." : ex.getMessage());
                }
            }
        };

        worker.execute();
        waitingDialog.setVisible(true);
    }

    private OnlineVietQrResult finishPaidOnlineVietQr(Order order, List<OrderDetail> details, long amountVnd,
            VietQrPayOsService.CreatedPayment created, VietQrPayOsService.PaymentStatus paymentStatus) {

        if (paymentStatus.orderCode() != created.orderCode()
                || paymentStatus.amount() != amountVnd
                || paymentStatus.amountPaid() < amountVnd) {
            return new OnlineVietQrResult(false, "PAYMENT_MISMATCH", created.orderCode(),
                    created.paymentLinkId(), paymentStatus.reference(),
                    "payOS báo thanh toán nhưng mã hoặc số tiền không khớp đơn hàng hiện tại.");
        }

        order.setPaymentMethod("BANK_TRANSFER");
        order.setPaymentStatus("PAID");
        order.setPayOsOrderCode(created.orderCode());
        order.setPayOsPaymentLinkId(created.paymentLinkId());
        order.setBankTransferReference(paymentStatus.reference());

        if (orderDAO.createOrder(order, details)) {
            return new OnlineVietQrResult(true, "COMPLETED", created.orderCode(),
                    created.paymentLinkId(), paymentStatus.reference(), null);
        }

        // Recovery: JDBC có thể lỗi sau COMMIT. Kiểm tra orderCode payOS duy nhất trước
        // khi kết luận thất bại để không khiến khách chuyển tiền lần hai.
        Order recovered = orderDAO.findByPayOsOrderCode(created.orderCode());
        if (recovered != null
                && "BANK_TRANSFER".equalsIgnoreCase(recovered.getPaymentMethod())
                && "PAID".equalsIgnoreCase(recovered.getPaymentStatus())
                && recovered.getTotalAmount() != null
                && recovered.getTotalAmount().compareTo(BigDecimal.valueOf(amountVnd)) == 0) {
            order.setOrderId(recovered.getOrderId());
            order.setOrderCode(recovered.getOrderCode());
            return new OnlineVietQrResult(true, "COMPLETED_RECOVERED", created.orderCode(),
                    created.paymentLinkId(), paymentStatus.reference(), null);
        }

        return new OnlineVietQrResult(false, "PAID_ORDER_FAILED", created.orderCode(),
                created.paymentLinkId(), paymentStatus.reference(),
                "payOS đã xác nhận nhận " + NumberUtil.formatThousands(amountVnd)
                        + " đ nhưng SIMS chưa lưu được đơn hàng. "
                        + "Không chuyển lại tiền. Mã payOS: " + created.orderCode()
                        + ". Vui lòng liên hệ cửa hàng để đối soát.");
    }

    private JDialog buildOnlineVietQrWaitingDialog(JLabel qrLabel, JLabel amountLabel, JLabel statusLabel,
            JButton openPageBtn, Runnable onCancel) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Thanh toán chuyển khoản VietQR", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);
        dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColor.WHITE);
        body.setBorder(new EmptyBorder(22, 30, 18, 30));

        JLabel title = new JLabel("Quét VietQR để chuyển khoản");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel hint = new JLabel("<html><div style='text-align:center;width:360px'>"
                + "Quét bằng ứng dụng ngân hàng. Số tiền và nội dung đã được điền sẵn. "
                + "Đơn hàng chỉ được tạo sau khi payOS xác nhận đã thanh toán."
                + "</div></html>");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Hủy");
        cancel.setFocusPainted(false);
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.addActionListener(e -> {
            if (onCancel != null) onCancel.run();
        });
        buttons.add(openPageBtn);
        buttons.add(cancel);

        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(hint);
        body.add(Box.createVerticalStrut(12));
        body.add(qrLabel);
        body.add(Box.createVerticalStrut(8));
        body.add(amountLabel);
        body.add(Box.createVerticalStrut(6));
        body.add(statusLabel);
        body.add(Box.createVerticalStrut(14));
        body.add(buttons);

        dialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (onCancel != null) onCancel.run();
            }
        });

        dialog.getContentPane().add(body);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private record OnlineVietQrResult(boolean success, String status, long orderCode,
            String paymentLinkId, String reference, String message) {
    }

    /**
     * Luong PayPal that: mo 1 server cuc bo tam thoi de nhan redirect, tao
     * don PayPal (Orders v2), mo trinh duyet cho khach dang nhap/approve,
     * cho ket qua roi capture (chot giao dich) - tat ca chay o background
     * thread, hien 1 dialog "dang cho" trong luc do. Chi luu don xuong DB
     * SAU KHI capture thanh cong (PaymentStatus = PAID).
     */
    private void payWithPayPalThenPersist(Order order, List<OrderDetail> details, BigDecimal totalVnd) {
        PayPalService payPalService = new PayPalService();
        @SuppressWarnings("unchecked")
        SwingWorker<PayPalService.CaptureResult, Void>[] workerRef = new SwingWorker[1];
        JDialog waitingDialog = buildPayPalWaitingDialog(() -> {
            if (workerRef[0] != null) workerRef[0].cancel(true);
        });

        SwingWorker<PayPalService.CaptureResult, Void> worker = new SwingWorker<>() {
            @Override
            protected PayPalService.CaptureResult doInBackground() throws Exception {
                PayPalService.LocalCallbackServer server = payPalService.startLocalCallbackServer();
                try {
                    PayPalService.CreatedOrder created = payPalService.createOrder(
                            totalVnd, "SIMS-" + System.currentTimeMillis(), server.returnUrl(), server.cancelUrl());
                    order.setPayPalOrderId(created.payPalOrderId());
                    payPalService.openApprovalPage(created.approveUrl());

                    PayPalService.ApprovalResult approval =
                            server.await(
                                    Duration.ofMinutes(5)
                            );

                    if (!approval.approved()) {
                        return new PayPalService.CaptureResult(
                                false,
                                null,
                                "CANCELLED"
                        );
                    }

                    PayPalService.CaptureResult result =
                            payPalService.captureOrder(
                                    created.payPalOrderId()
                            );

                    if (result.success()) {
                        server.completeBrowserSuccess(
                                "Thanh toán đơn hàng online "
                              + "đã hoàn tất thành công."
                        );
                    } else {
                        server.completeBrowserFailure(
                                "PayPal không thể hoàn tất giao dịch. "
                              + "Đơn hàng chưa được thanh toán."
                        );
                    }

                    server.awaitBrowserResponse(
                            Duration.ofSeconds(5)
                    );

                    return result;
                } finally {
                    server.stop();
                }
            }

            @Override
            protected void done() {
                waitingDialog.dispose();
                if (isCancelled()) return;
                try {
                    PayPalService.CaptureResult result = get();
                    if (result.success()) {
                        order.setPaymentStatus("PAID");
                        order.setPayPalCaptureId(result.captureId());
                        persistOrderAndFinish(order, details);
                    } else if (!"CANCELLED".equals(result.status())) {
                        BaseDialog.error(CartPanel.this, Lang.get("payment.error.title"), Lang.get("payment.paypal.failed"));
                    }
                } catch (CancellationException ignored) {
                    // Nguoi dung tu bam Huy tren dialog cho - khong bao loi.
                } catch (Exception e) {
                    BaseDialog.error(CartPanel.this, Lang.get("payment.error.title"), Lang.get("payment.paypal.failed"));
                }
            }
        };
        workerRef[0] = worker;
        worker.execute();
        waitingDialog.setVisible(true); // modal - chan toi khi worker goi dispose() trong done()
    }

    /**
     * Dialog modal "Đang chờ thanh toán PayPal..." kèm nút Hủy.
     * @param onCancel callback khi người dùng bấm Hủy (thường dùng để cancel SwingWorker).
     */
    private JDialog buildPayPalWaitingDialog(Runnable onCancel) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), Lang.get("payment.paypal.waiting.title"),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColor.WHITE);
        body.setBorder(new EmptyBorder(28, 32, 20, 32));

        JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.EXTERNAL_LINK_ALT, 28, AppColor.ACCENT_HOVER));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel(Lang.get("payment.paypal.waiting.title"));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel message = new JLabel("<html><div style='text-align:center;width:280px'>"
                + Lang.get("payment.paypal.waiting.message") + "</div></html>");
        message.setFont(AppFont.BODY);
        message.setForeground(AppColor.TEXT_SECONDARY);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);

        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setMaximumSize(new Dimension(260, 6));

        JButton cancel = new JButton(Lang.get("payment.cancel"));
        cancel.setAlignmentX(Component.CENTER_ALIGNMENT);
        cancel.setFocusPainted(false);
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.addActionListener(e -> {
            if (onCancel != null) onCancel.run();
            dialog.dispose();
        });

        body.add(icon);
        body.add(Box.createVerticalStrut(12));
        body.add(title);
        body.add(Box.createVerticalStrut(8));
        body.add(message);
        body.add(Box.createVerticalStrut(16));
        body.add(progress);
        body.add(Box.createVerticalStrut(16));
        body.add(cancel);

        dialog.getContentPane().add(body);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    // ==================== Helper: card/label style dung chung (giong ProfilePanel) ====================

    private JPanel card() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));
        return card;
    }

    private JPanel sideCard() {
        JPanel card = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(SUMMARY_WIDTH, d.height);
            }

            @Override
            public Dimension getMaximumSize() {
                return getPreferredSize();
            }

            @Override
            public Dimension getMinimumSize() {
                return getPreferredSize();
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));
        return card;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 16));
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, AppSpacing.LG, 0));
        return label;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(8, 0, 4, 0));
        return label;
    }

    private JPanel fieldGroup(String labelText, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(fieldLabel(labelText.toUpperCase()));
        group.add(field);
        return group;
    }

    private void styleField(JTextField field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private JComponent divider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    /** Anh dai dien san pham bo goc, fallback icon hop neu chua co/loi anh - dung chung style voi dropdown gio hang tren header. */
    private ImageIcon loadRoundedThumb(String imageUrl, int size) {
        BufferedImage raw = (imageUrl == null || imageUrl.isBlank()) ? null : ImageUtil.readSafe(imageUrl);
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = square.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 14, 14));

        if (raw != null) {
            int side = Math.min(raw.getWidth(), raw.getHeight());
            BufferedImage cropped = raw.getSubimage((raw.getWidth() - side) / 2, (raw.getHeight() - side) / 2, side, side);
            g2.drawImage(cropped, 0, 0, size, size, null);
        } else {
            g2.setColor(AppColor.ACCENT_BG_SOFT);
            g2.fillRect(0, 0, size, size);
            FontIcon icon = FontIcon.of(FontAwesomeSolid.BOX, (int) (size * 0.45));
            icon.setIconColor(AppColor.ACCENT_HOVER);
            int ix = (size - icon.getIconWidth()) / 2;
            int iy = (size - icon.getIconHeight()) / 2;
            icon.paintIcon(null, g2, ix, iy);
        }
        g2.dispose();
        return new ImageIcon(square);
    }
}