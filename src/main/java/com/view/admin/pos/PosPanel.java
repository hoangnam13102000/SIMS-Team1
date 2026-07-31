package com.view.admin.pos;

import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.barcode.BarcodeScannerDialog;
import com.components.product.ProductGrid;
import com.dao.CategoryDAO;
import com.dao.CustomerDAO;
import com.dao.InvoiceDAO;
import com.dao.ProductDAO;
import com.dao.ShiftDAO;
import com.model.CartItem;
import com.model.Category;
import com.model.Customer;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Product;
import com.model.User;
import com.service.AuthService;
import com.service.PosCartService;
import com.service.payment.PayPalService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import com.utils.NumberUtil;
import com.utils.QrCodeUtil;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;

/**
 * Trang "Bán hàng tại quầy" (POS) - dành cho nhân viên bán hàng (quyền
 * {@link com.model.permission.AppPermission#INVOICE_CREATE}). Đây là nơi
 * DUY NHẤT trong app thực sự ghi hóa đơn xuống DB (xem
 * {@link InvoiceDAO#createInvoice}); trang "Quản lý hóa đơn" (InvoicePanel)
 * chỉ tra cứu lại lịch sử.
 * <p>
 * Layout: trái là lưới sản phẩm (tìm kiếm + lọc danh mục, bấm "Thêm vào giỏ"
 * để thêm), phải là giỏ hàng của phiên bán hàng hiện tại + chọn khách hàng
 * (tùy chọn, mặc định "Khách lẻ") + phương thức thanh toán + nút thanh toán.
 * <p>
 * Giỏ hàng dùng {@link PosCartService} (singleton, KHÁC với CartService phía
 * client) để không bị mất khi panel bị xây lại (đổi theme/ngôn ngữ).
 */
public class PosPanel extends JPanel {

    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final InvoiceDAO invoiceDAO = new InvoiceDAO();
    private final ShiftDAO shiftDAO = new ShiftDAO();
    private final PosCartService cart = PosCartService.getInstance();

    private final ProductGrid productGrid = new ProductGrid();
    private final CardLayout productAreaLayout = new CardLayout();
    private final JPanel productAreaWrapper = new JPanel(productAreaLayout);
    private final JPanel emptyStateHolder = new JPanel(new BorderLayout());
    private final JComboBox<Category> categoryCombo = new JComboBox<>();
    private final JPanel cartListPanel = new JPanel();
    private final JLabel customerStatusLabel = new JLabel();
    private final JButton clearCustomerButton = new JButton();
    private final JTextField customerSearchField = new JTextField();
    private final JLabel subtotalValue = new JLabel();
    private final JLabel vatValue = new JLabel();
    private final JLabel totalValue = new JLabel();
    private final JButton checkoutButton = new JButton();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang lập hóa đơn...");

    private String selectedPaymentMethod = "CASH";
    private final java.util.Map<String, JToggleButton> paymentButtons = new java.util.LinkedHashMap<>();

    private final Runnable cartListener = this::refreshCartSummary;

    public PosPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        User user = AuthService.getInstance().getCurrentUser();
        SectionHeader header = new SectionHeader(FontAwesomeSolid.SHOPPING_CART, AppColor.ACCENT,
                "Bán hàng tại quầy",
                "Nhân viên: " + (user != null ? user.getFullName() : "-"));
        add(header, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(AppSpacing.LG, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));
        body.add(buildLeftPanel(), BorderLayout.CENTER);

        JPanel right = buildRightPanel();
        right.setPreferredSize(new Dimension(380, 10));
        body.add(right, BorderLayout.EAST);

        add(LoadingOverlay.attach(body, loadingOverlay), BorderLayout.CENTER);

        cart.addListener(cartListener);
        loadCategories();
        loadProducts(null, null);
        refreshCartSummary();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        cart.removeListener(cartListener);
    }

    // =================================================================
    // Vung trai: tim kiem + luoi san pham
    // =================================================================

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, AppSpacing.MD));
        panel.setOpaque(false);

        JPanel filterRow = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        filterRow.setOpaque(false);

        BaseSearch search = new BaseSearch("Tìm sản phẩm theo tên hoặc danh mục...");
        search.onSearch(keyword -> loadProducts(keyword, selectedCategoryId()));
        filterRow.add(search, BorderLayout.CENTER);

        categoryCombo.setPreferredSize(new Dimension(200, 40));
        categoryCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                            boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof Category) {
                    setText(((Category) value).getCategoryName());
                } else {
                    setText("Tất cả danh mục");
                }
                return this;
            }
        });
        categoryCombo.addActionListener(e -> loadProducts(search.getText(), selectedCategoryId()));

        JPanel rightFilter = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        rightFilter.setOpaque(false);
        rightFilter.add(categoryCombo, BorderLayout.CENTER);
        rightFilter.add(buildScanButton(), BorderLayout.EAST);
        filterRow.add(rightFilter, BorderLayout.EAST);

        panel.add(filterRow, BorderLayout.NORTH);

        // Boc grid trong 1 panel BorderLayout.NORTH de GridLayout khong bi keo
        // gian theo chieu cao khung nhin - moi the giu dung kich thuoc tu nhien
        // (giong HomePanel#renderProducts).
        JPanel gridWrapper = new JPanel(new BorderLayout());
        gridWrapper.setOpaque(false);
        gridWrapper.add(productGrid, BorderLayout.NORTH);

        JScrollPane scroll = new JScrollPane(gridWrapper);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);

        emptyStateHolder.setOpaque(false);

        productAreaWrapper.setOpaque(false);
        productAreaWrapper.add(scroll, "grid");
        productAreaWrapper.add(emptyStateHolder, "empty");
        panel.add(productAreaWrapper, BorderLayout.CENTER);

        productGrid.onAddToCart(product -> {
            cart.addToCart(product, 1);
            AppAlert.success(this, "Đã thêm \"" + product.getProductName() + "\" vào giỏ hàng.");
        });

        return panel;
    }

    /** Nut mo dialog quet ma vach bang webcam (xem BarcodeScannerDialog). */
    private JButton buildScanButton() {
        JButton btn = iconButton(FontAwesomeSolid.CAMERA, AppColor.ACCENT);
        btn.setToolTipText("Quét mã vạch sản phẩm bằng webcam");
        btn.addActionListener(e -> openBarcodeScanner());
        return btn;
    }

    /**
     * Mo dialog quet ma vach (webcam + ZXing). Khi doc duoc 1 ma, tim san
     * pham DANG BAN co ProductCode khop (xem {@link ProductDAO#findActiveByCode})
     * roi tu dong them 1 don vi vao gio hang, giong nhu bam "Them vao gio" tren
     * luoi san pham. Neu khong tim thay san pham nao khop ma vua quet duoc thi
     * bao loi cho nhan vien biet thay vi im lang bo qua.
     */
    private void openBarcodeScanner() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new BarcodeScannerDialog(owner)
                .onScanned(this::handleBarcodeScanned)
                .setVisible(true);
    }

    private void handleBarcodeScanned(String code) {
        Product product = productDAO.findActiveByCode(code);
        if (product == null) {
            AppAlert.error(this, "Không tìm thấy sản phẩm",
                    "Không có sản phẩm nào đang bán khớp với mã vừa quét: \"" + code + "\".");
            return;
        }
        cart.addToCart(product, 1);
        AppAlert.success(this, "Đã thêm \"" + product.getProductName() + "\" vào giỏ hàng.");
    }

    private Integer selectedCategoryId() {
        Object selected = categoryCombo.getSelectedItem();
        return (selected instanceof Category) ? ((Category) selected).getCategoryId() : null;
    }

    private void loadCategories() {
        categoryCombo.removeAllItems();
        categoryCombo.addItem(null); // "Tat ca danh muc"
        for (Category category : categoryDAO.findAllActive()) {
            categoryCombo.addItem(category);
        }
    }

    private void loadProducts(String keyword, Integer categoryId) {
        List<Product> products = productDAO.findActive(keyword, categoryId);
        productGrid.setProducts(products);
        if (products.isEmpty()) {
            emptyStateHolder.removeAll();
            boolean hasKeyword = keyword != null && !keyword.isBlank();
            emptyStateHolder.add(hasKeyword ? EmptyState.noSearchResult(keyword) : EmptyState.noData("sản phẩm"),
                    BorderLayout.CENTER);
            emptyStateHolder.revalidate();
            productAreaLayout.show(productAreaWrapper, "empty");
        } else {
            productAreaLayout.show(productAreaWrapper, "grid");
        }
    }

    // =================================================================
    // Vung phai: khach hang + gio hang + thanh toan
    // =================================================================

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(true);
        panel.setBackground(AppColor.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG)));

        panel.add(sectionLabel("Khách hàng"));
        panel.add(Box.createVerticalStrut(AppSpacing.SM));
        panel.add(fixedHeight(buildCustomerSearchRow(), 40));
        panel.add(Box.createVerticalStrut(AppSpacing.SM));
        panel.add(fixedHeight(buildCustomerStatusRow(), 26));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        panel.add(sectionLabel("Giỏ hàng"));
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        cartListPanel.setLayout(new BoxLayout(cartListPanel, BoxLayout.Y_AXIS));
        cartListPanel.setOpaque(false);
        cartListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JScrollPane cartScroll = new JScrollPane(cartListPanel);
        cartScroll.setBorder(null);
        cartScroll.setOpaque(false);
        cartScroll.getViewport().setOpaque(false);
        cartScroll.getVerticalScrollBar().setUnitIncrement(14);
        cartScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        cartScroll.setPreferredSize(new Dimension(10, 260));
        cartScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        panel.add(cartScroll);

        panel.add(Box.createVerticalStrut(AppSpacing.MD));
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        panel.add(sep);
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        panel.add(fixedHeight(summaryRow("Tạm tính", subtotalValue, AppFont.BODY, AppColor.TEXT_SECONDARY), 22));
        panel.add(Box.createVerticalStrut(4));
        panel.add(fixedHeight(summaryRow("VAT (8%)", vatValue, AppFont.BODY, AppColor.TEXT_SECONDARY), 22));
        panel.add(Box.createVerticalStrut(6));
        panel.add(fixedHeight(summaryRow("Tổng cộng", totalValue, AppFont.HEADING_MD, AppColor.TEXT_TITLE), 30));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        panel.add(sectionLabel("Phương thức thanh toán"));
        panel.add(Box.createVerticalStrut(AppSpacing.SM));
        panel.add(fixedHeight(buildPaymentMethodRow(), 36));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        panel.add(fixedHeight(buildCheckoutButton(), 48));

        return panel;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_TITLE);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JComponent fixedHeight(JComponent comp, int height) {
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        comp.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        comp.setPreferredSize(new Dimension(comp.getPreferredSize().width, height));
        return comp;
    }

    // ---------------- Khach hang ----------------

    private JPanel buildCustomerSearchRow() {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);

        customerSearchField.setFont(AppFont.BODY);
        customerSearchField.setToolTipText("Nhập số điện thoại hoặc tên khách hàng - bỏ trống nếu là khách lẻ");
        customerSearchField.addActionListener(e -> searchCustomer());
        row.add(customerSearchField, BorderLayout.CENTER);

        JButton searchBtn = iconButton(FontAwesomeSolid.SEARCH, AppColor.ACCENT_HOVER);
        searchBtn.addActionListener(e -> searchCustomer());
        row.add(searchBtn, BorderLayout.EAST);

        return row;
    }

    private JPanel buildCustomerStatusRow() {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);

        customerStatusLabel.setFont(AppFont.SMALL);
        customerStatusLabel.setForeground(AppColor.TEXT_MUTED);
        row.add(customerStatusLabel, BorderLayout.CENTER);

        clearCustomerButton.setText("Bỏ chọn");
        clearCustomerButton.setFont(AppFont.SMALL);
        clearCustomerButton.setForeground(AppColor.ACCENT_HOVER);
        clearCustomerButton.setContentAreaFilled(false);
        clearCustomerButton.setBorderPainted(false);
        clearCustomerButton.setFocusPainted(false);
        clearCustomerButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearCustomerButton.addActionListener(e -> cart.clearCustomer());
        row.add(clearCustomerButton, BorderLayout.EAST);

        return row;
    }

    private void searchCustomer() {
        String keyword = customerSearchField.getText().trim();
        if (keyword.isEmpty()) {
            AppAlert.warning(this, "Vui lòng nhập số điện thoại hoặc tên khách hàng.");
            return;
        }
        List<Customer> results = customerDAO.search(keyword, 1, 8).getData();
        if (results.isEmpty()) {
            AppAlert.warning(this, "Không tìm thấy khách hàng nào khớp với \"" + keyword + "\".");
            return;
        }
        if (results.size() == 1) {
            selectCustomer(results.get(0));
            return;
        }
        showCustomerPicker(results);
    }
    private void showCustomerPicker(List<Customer> results) {
        JPopupMenu popup = new JPopupMenu();
        for (Customer c : results) {
            String phone = c.getPhone() != null && !c.getPhone().isBlank() ? c.getPhone() : "-";
            JMenuItem item = new JMenuItem(c.getFullName() + "   -   " + phone);
            item.setFont(AppFont.BODY);
            item.addActionListener(e -> selectCustomer(c));
            popup.add(item);
        }
        popup.show(customerSearchField, 0, customerSearchField.getHeight());
    }

    private void selectCustomer(Customer customer) {
        String phone = customer.getPhone() != null && !customer.getPhone().isBlank() ? customer.getPhone() : "";
        String label = customer.getFullName() + (phone.isEmpty() ? "" : " - " + phone);
        cart.setCustomer(customer.getCustomerId(), label);
        customerSearchField.setText("");
    }

    // ---------------- Phuong thuc thanh toan ----------------

    private JPanel buildPaymentMethodRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 6, 0));
        row.setOpaque(false);

        String[] methods = {"CASH", "BANK_TRANSFER", "PAYPAL", "CARD"};
        String[] labels = {"Tiền mặt", "Chuyển khoản", "PayPal (Sandbox)", "Thẻ"};

        ButtonGroup group = new ButtonGroup();
        for (int i = 0; i < methods.length; i++) {
            String method = methods[i];
            JToggleButton btn = new JToggleButton(labels[i]);
            btn.setFont(AppFont.SMALL_BOLD);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.setSelected(method.equals(selectedPaymentMethod));
            styleToggle(btn);
            btn.addActionListener(e -> {
                selectedPaymentMethod = method;
                for (JToggleButton other : paymentButtons.values()) styleToggle(other);
            });
            group.add(btn);
            paymentButtons.put(method, btn);
            row.add(btn);
        }
        return row;
    }

    private void styleToggle(JToggleButton btn) {
        boolean selected = btn.isSelected();
        btn.setOpaque(true);
        btn.setBackground(selected ? AppColor.ACCENT_BG_SOFT : AppColor.WHITE);
        btn.setForeground(selected ? AppColor.ACCENT_HOVER : AppColor.TEXT_SECONDARY);
        btn.setBorder(BorderFactory.createLineBorder(selected ? AppColor.ACCENT_HOVER : AppColor.BORDER, selected ? 2 : 1, true));
    }

    // ---------------- Gio hang ----------------

    private void refreshCartSummary() {
        rebuildCartRows();

        customerStatusLabel.setText(cart.getCustomerId() == null
                ? "Khách lẻ (không lưu thông tin)"
                : cart.getCustomerLabel());
        clearCustomerButton.setVisible(cart.getCustomerId() != null);

        long subTotal = cart.getSubTotal();
        long vat = Math.round(subTotal * 0.08);
        long total = subTotal + vat;

        subtotalValue.setText(NumberUtil.formatThousands(subTotal) + " đ");
        vatValue.setText(NumberUtil.formatThousands(vat) + " đ");
        totalValue.setText(NumberUtil.formatThousands(total) + " đ");

        checkoutButton.setEnabled(!cart.isEmpty());
    }

    private void rebuildCartRows() {
        cartListPanel.removeAll();
        List<CartItem> items = cart.getItems();
        if (items.isEmpty()) {
            JLabel empty = new JLabel("Giỏ hàng đang trống - bấm \"Thêm vào giỏ\" trên sản phẩm để bắt đầu.");
            empty.setFont(AppFont.SMALL);
            empty.setForeground(AppColor.TEXT_MUTED);
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            cartListPanel.add(empty);
        } else {
            for (CartItem item : new ArrayList<>(items)) {
                cartListPanel.add(buildCartRow(item));
                cartListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        cartListPanel.revalidate();
        cartListPanel.repaint();
    }

    private JPanel buildCartRow(CartItem item) {
        Product product = item.getProduct();
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 2));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 10, 8, 10)));

        JLabel nameLabel = new JLabel("<html>" + escapeHtml(product.getProductName()) + "</html>");
        nameLabel.setFont(AppFont.SMALL_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JLabel priceLabel = new JLabel(NumberUtil.formatThousands(
                product.getSellPrice() == null ? 0 : product.getSellPrice().longValue()) + " đ / " +
                (product.getUnit() != null ? product.getUnit() : "sp"));
        priceLabel.setFont(AppFont.SMALL);
        priceLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(nameLabel);
        left.add(priceLabel);
        row.add(left, BorderLayout.CENTER);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JPanel qtyRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        qtyRow.setOpaque(false);
        JButton minus = smallStepButton(FontAwesomeSolid.MINUS);
        JLabel qtyLabel = new JLabel(String.valueOf(item.getQuantity()));
        qtyLabel.setFont(AppFont.SMALL_BOLD);
        qtyLabel.setForeground(AppColor.TEXT_TITLE);
        qtyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        qtyLabel.setPreferredSize(new Dimension(24, 22));
        JButton plus = smallStepButton(FontAwesomeSolid.PLUS);
        minus.addActionListener(e -> cart.updateQuantity(product.getProductId(), item.getQuantity() - 1));
        plus.addActionListener(e -> cart.updateQuantity(product.getProductId(), item.getQuantity() + 1));
        qtyRow.add(minus);
        qtyRow.add(qtyLabel);
        qtyRow.add(plus);

        JButton removeBtn = smallStepButton(FontAwesomeSolid.TRASH);
        ((FontIcon) removeBtn.getIcon()).setIconColor(AppColor.ERROR);
        removeBtn.addActionListener(e -> cart.removeItem(product.getProductId()));
        JPanel removeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        removeRow.setOpaque(false);
        JLabel lineTotal = new JLabel(NumberUtil.formatThousands(item.getSubtotal()) + " đ");
        lineTotal.setFont(AppFont.SMALL_BOLD);
        lineTotal.setForeground(AppColor.TEXT_TITLE);
        removeRow.add(lineTotal);
        removeRow.add(Box.createHorizontalStrut(6));
        removeRow.add(removeBtn);

        right.add(qtyRow);
        right.add(removeRow);
        row.add(right, BorderLayout.EAST);

        return row;
    }

    private JButton smallStepButton(FontAwesomeSolid icon) {
        JButton btn = new JButton();
        FontIcon fontIcon = FontIcon.of(icon, 11);
        fontIcon.setIconColor(AppColor.TEXT_SECONDARY);
        btn.setIcon(fontIcon);
        btn.setPreferredSize(new Dimension(22, 22));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton iconButton(FontAwesomeSolid icon, Color color) {
        JButton btn = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.SMALL, AppRadius.SMALL);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(color);
        btn.setIcon(fontIcon);
        btn.setPreferredSize(new Dimension(40, 40));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JPanel summaryRow(String label, JLabel valueLabel, Font labelFont, Color labelColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);
        labelComp.setForeground(labelColor);
        row.add(labelComp, BorderLayout.WEST);

        valueLabel.setFont(labelFont == AppFont.HEADING_MD ? AppFont.HEADING_MD : AppFont.BODY_BOLD);
        valueLabel.setForeground(labelFont == AppFont.HEADING_MD ? AppColor.ACCENT_HOVER : AppColor.TEXT_PRIMARY);
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(valueLabel, BorderLayout.EAST);

        return row;
    }

    // ---------------- Nut thanh toan + logic tao hoa don ----------------

    private JPanel buildCheckoutButton() {
        checkoutButton.setText("Thanh toán");
        checkoutButton.setIcon(iconOf(FontAwesomeSolid.CHECK_CIRCLE, Color.WHITE));
        checkoutButton.setIconTextGap(8);
        checkoutButton.setFont(AppFont.BUTTON);
        checkoutButton.setForeground(Color.WHITE);
        checkoutButton.setBackground(AppColor.ACCENT_HOVER);
        checkoutButton.setOpaque(true);
        checkoutButton.setContentAreaFilled(true);
        checkoutButton.setFocusPainted(false);
        checkoutButton.setBorderPainted(false);
        checkoutButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        checkoutButton.addActionListener(e -> handleCheckout());

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(checkoutButton, BorderLayout.CENTER);
        return wrap;
    }

    private Icon iconOf(Ikon icon, Color color) {
        FontIcon fontIcon = FontIcon.of(icon, 16);
        fontIcon.setIconColor(color);
        return fontIcon;
    }

    private void handleCheckout() {
        if (cart.isEmpty()) {
            AppAlert.warning(this, "Giỏ hàng đang trống.");
            return;
        }
        User currentUser = AuthService.getInstance().getCurrentUser();
        if (currentUser == null) return;

        boolean confirmed = BaseDialog.confirm(this, "Xác nhận thanh toán",
                "Lập hóa đơn cho " + cart.getItems().size() + " mặt hàng, tổng cộng "
                        + totalValue.getText() + "?");
        if (!confirmed) return;

        List<CartItem> snapshot = new ArrayList<>(cart.getItems());
        Integer customerId = cart.getCustomerId();
        long expectedSubTotal = cart.getSubTotal();
        long vat = Math.round(expectedSubTotal * 0.08);
        long expectedTotal = expectedSubTotal + vat;

        if ("PAYPAL".equals(selectedPaymentMethod)) {
            payWithPayPalThenCreateInvoice(currentUser, snapshot, customerId, expectedSubTotal, expectedTotal);
        } else {
            createInvoiceAndFinish(currentUser, snapshot, customerId, expectedSubTotal,
                    selectedPaymentMethod, null, null);
        }
    }

    /**
     * Lap hoa don xuong DB (dung chung cho ca thanh toan thuong lan sau khi
     * PayPal da capture thanh cong) - luon chay o background thread vi la
     * thao tac DB blocking.
     */
    private void createInvoiceAndFinish(User currentUser, List<CartItem> snapshot, Integer customerId,
                                         long expectedSubTotal, String paymentMethod,
                                         String payPalOrderId, String payPalCaptureId) {
        checkoutButton.setEnabled(false);
        loadingOverlay.start("Đang lập hóa đơn...");

        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private Invoice invoice;

            @Override
            protected Boolean doInBackground() {
                int shiftId = shiftDAO.getOrOpenShiftId(currentUser.getUserId());
                if (shiftId <= 0) return false;

                invoice = new Invoice();
                invoice.setShiftId(shiftId);
                invoice.setCreatedBy(currentUser.getUserId());
                invoice.setCustomerId(customerId);
                invoice.setPaymentMethod(paymentMethod);
                invoice.setPayPalOrderId(payPalOrderId);
                invoice.setPayPalCaptureId(payPalCaptureId);
                // TODO: doc VAT_RATE tu bang StoreConfig khi co DAO rieng - hien
                // dung dung gia tri mac dinh cua cot Invoices.VATRate (xem SIMS.sql).
                invoice.setVatRate(new BigDecimal("8"));

                List<InvoiceDetail> details = new ArrayList<>();
                for (CartItem item : snapshot) {
                    InvoiceDetail detail = new InvoiceDetail();
                    detail.setProductId(item.getProduct().getProductId());
                    detail.setQuantity(item.getQuantity());
                    detail.setUnitPrice(item.getProduct().getSellPrice());
                    details.add(detail);
                }
                return invoiceDAO.createInvoice(invoice, details);
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                checkoutButton.setEnabled(!cart.isEmpty());
                boolean ok;
                try {
                    ok = Boolean.TRUE.equals(get());
                } catch (Exception ex) {
                    ok = false;
                }

                if (ok) {
                    cart.clear();
                    loadProducts(null, null); // ton kho vua doi
                    boolean stockLimited = invoice.getSubTotal() != null
                            && invoice.getSubTotal().longValue() < expectedSubTotal;
                    if (stockLimited) {
                        AppAlert.warning(PosPanel.this, "Đã lập hóa đơn " + invoice.getInvoiceCode(),
                                "Một số sản phẩm không đủ tồn kho nên đã được giới hạn số lượng. "
                                        + "Tổng tiền thực tế: " + NumberUtil.formatThousands(
                                        invoice.getTotalAmount().longValue()) + " đ. "
                                        + "Vui lòng kiểm tra lại trong \"Quản lý hóa đơn\".");
                    } else {
                        AppAlert.success(PosPanel.this, "Lập hóa đơn thành công",
                                "Hóa đơn " + invoice.getInvoiceCode() + " - Tổng tiền: "
                                        + NumberUtil.formatThousands(invoice.getTotalAmount().longValue()) + " đ");
                    }
                } else {
                    AppAlert.error(PosPanel.this, "Không thể lập hóa đơn",
                            "Có thể do sản phẩm trong giỏ đã hết hàng hoặc có lỗi hệ thống. "
                                    + "Vui lòng kiểm tra lại giỏ hàng và thử lại.");
                    loadProducts(null, null);
                }
            }
        };
        worker.execute();
    }

    // ---------------- Thanh toan PayPal (sandbox) tai quay ----------------

    /**
     * Luong PayPal THAT (sandbox): tao 1 don PayPal (Orders v2 API), hien thi
     * ma QR cua link "approve" de KHACH tu quet bang dien thoai cua ho (thay
     * vi mo trinh duyet tren may cua thu ngan - hop ly hon cho ngu canh quay
     * thu ngan, xem javadoc QrCodeUtil), cho ket qua duyet roi capture (chot
     * giao dich). CHI lap hoa don xuong DB SAU KHI capture thanh cong -
     * giong het pattern cua CartPanel#payWithPayPalThenPersist (trang khach
     * hang) de nhat quan trong toan he thong.
     */
    private void payWithPayPalThenCreateInvoice(User currentUser, List<CartItem> snapshot, Integer customerId,
                                                 long expectedSubTotal, long totalVnd) {
        PayPalService payPalService = new PayPalService();

        JLabel qrLabel = new JLabel("Đang khởi tạo đơn PayPal...");
        qrLabel.setFont(AppFont.SMALL);
        qrLabel.setForeground(AppColor.TEXT_MUTED);
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        qrLabel.setPreferredSize(new Dimension(260, 260));

        JButton openHereBtn = new JButton("Mở trên máy này");
        openHereBtn.setEnabled(false);
        openHereBtn.setFont(AppFont.SMALL_BOLD);
        openHereBtn.setFocusPainted(false);
        openHereBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        @SuppressWarnings("unchecked")
        SwingWorker<PayPalPosResult, String>[] workerRef = new SwingWorker[1];
        String[] approveUrlHolder = new String[1];

        JDialog waitingDialog = buildPayPalWaitingDialog(qrLabel, openHereBtn, () -> {
            if (workerRef[0] != null) workerRef[0].cancel(true);
        });

        openHereBtn.addActionListener(e -> {
            if (approveUrlHolder[0] == null) return;
            try {
                payPalService.openApprovalPage(approveUrlHolder[0]);
            } catch (Exception ex) {
                AppAlert.error(this, "Không thể mở trình duyệt: " + ex.getMessage());
            }
        });

        SwingWorker<PayPalPosResult, String> worker = new SwingWorker<>() {
            @Override
            protected PayPalPosResult doInBackground() throws Exception {
                PayPalService.LocalCallbackServer server = payPalService.startLocalCallbackServer();
                try {
                    PayPalService.CreatedOrder created = payPalService.createOrder(
                            BigDecimal.valueOf(totalVnd), "POS-" + System.currentTimeMillis(),
                            server.returnUrl(), server.cancelUrl());
                    approveUrlHolder[0] = created.approveUrl();
                    publish(created.approveUrl());

                    PayPalService.ApprovalResult approval = server.await(Duration.ofMinutes(5));
                    if (!approval.approved()) {
                        return new PayPalPosResult(false, "CANCELLED", null, null);
                    }
                    PayPalService.CaptureResult result = payPalService.captureOrder(created.payPalOrderId());
                    if (result.success()) {
                        return new PayPalPosResult(true, "COMPLETED", created.payPalOrderId(), result.captureId());
                    }
                    return new PayPalPosResult(false, result.status(), created.payPalOrderId(), null);
                } finally {
                    server.stop();
                }
            }

            @Override
            protected void process(List<String> chunks) {
                if (chunks.isEmpty()) return;
                String approveUrl = chunks.get(chunks.size() - 1);
                try {
                    BufferedImage qr = QrCodeUtil.generate(approveUrl, 260);
                    qrLabel.setText(null);
                    qrLabel.setIcon(new ImageIcon(qr));
                    openHereBtn.setEnabled(true);
                } catch (Exception ex) {
                    qrLabel.setText("<html><center>Không tạo được mã QR.<br>Dùng nút \"Mở trên máy này\".</center></html>");
                    openHereBtn.setEnabled(true);
                }
            }

            @Override
            protected void done() {
                waitingDialog.dispose();
                if (isCancelled()) return;
                try {
                    PayPalPosResult result = get();
                    if (result.success()) {
                        createInvoiceAndFinish(currentUser, snapshot, customerId, expectedSubTotal,
                                "PAYPAL", result.orderId(), result.captureId());
                    } else if (!"CANCELLED".equals(result.status())) {
                        AppAlert.error(PosPanel.this, "Thanh toán PayPal thất bại",
                                "Không thể chốt giao dịch PayPal. Vui lòng thử lại hoặc chọn phương thức khác.");
                    } else {
                        AppAlert.warning(PosPanel.this, "Đã hủy thanh toán PayPal",
                                "Khách chưa duyệt thanh toán trong trang PayPal. Giỏ hàng vẫn được giữ nguyên.");
                    }
                } catch (CancellationException ignored) {
                    // Nhan vien tu bam Huy tren dialog cho - khong bao loi.
                } catch (Exception e) {
                    AppAlert.error(PosPanel.this, "Thanh toán PayPal thất bại",
                            "Có lỗi xảy ra: " + e.getMessage());
                }
            }
        };
        workerRef[0] = worker;
        worker.execute();
        waitingDialog.setVisible(true); // modal - chan toi khi worker goi dispose() trong done()
    }

    /**
     * Dialog modal "Đang chờ thanh toán PayPal..." hien QR (khach quet bang
     * dien thoai) + nut du phong mo tren chinh may nay + nut Huy.
     */
    private JDialog buildPayPalWaitingDialog(JLabel qrLabel, JButton openHereBtn, Runnable onCancel) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this),
                "Thanh toán PayPal (Sandbox)", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColor.WHITE);
        body.setBorder(new EmptyBorder(28, 32, 20, 32));

        JLabel icon = new JLabel(iconOf(FontAwesomeBrands.PAYPAL, new Color(37, 99, 235)));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("Quét mã để thanh toán");
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel message = new JLabel("<html><div style='text-align:center;width:280px'>"
                + "Đưa mã QR này cho khách quét bằng camera điện thoại để mở trang "
                + "PayPal Sandbox và duyệt thanh toán.</div></html>");
        message.setFont(AppFont.BODY);
        message.setForeground(AppColor.TEXT_SECONDARY);
        message.setAlignmentX(Component.CENTER_ALIGNMENT);

        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        openHereBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton cancel = new JButton("Hủy");
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
        body.add(qrLabel);
        body.add(Box.createVerticalStrut(10));
        body.add(openHereBtn);
        body.add(Box.createVerticalStrut(16));
        body.add(cancel);

        dialog.getContentPane().add(body);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        return dialog;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

  
    private record PayPalPosResult(boolean success, String status, String orderId, String captureId) {}
}