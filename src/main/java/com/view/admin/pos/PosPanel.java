package com.view.admin.pos;

import com.components.EmptyState;
import com.components.FilterDropdown;
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
import com.dao.StoreConfigDAO;
import com.dao.StockAlertDAO;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.CartItem;
import com.model.Category;
import com.model.Customer;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Product;
import com.model.Shift;
import com.model.User;
import com.service.AuthService;
import com.service.PosCartService;
import com.service.ShiftService;
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

public class PosPanel extends JPanel {
    private final ProductDAO productDAO = new ProductDAO();
    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final InvoiceDAO invoiceDAO = new InvoiceDAO();
    private final ShiftService shiftService = new ShiftService();
    private final StoreConfigDAO storeConfigDAO = new StoreConfigDAO();
    private final PosCartService cart = PosCartService.getInstance();

    private BigDecimal vatRate = BigDecimal.ZERO;
    private final ProductGrid productGrid = new ProductGrid();
    private final CardLayout productAreaLayout = new CardLayout();
    private final JPanel productAreaWrapper = new JPanel(productAreaLayout);
    private final JPanel emptyStateHolder = new JPanel(new BorderLayout());

    // ================================================================
    // ====== ĐỔI: JComboBox<Category> → FilterDropdown<CategoryOption> ======
    // ================================================================
    // private final JComboBox<Category> categoryCombo = new JComboBox<>();  // ← CŨ, ĐÃ XÓA
    private FilterDropdown<CategoryOption> categoryFilter;   // ← MỚI: FilterDropdown chuẩn
    // ================================================================
    private final BaseSearch searchBar = new BaseSearch("Tìm sản phẩm theo tên hoặc danh mục...");

    private final JPanel cartListPanel = new JPanel();
    private final JLabel customerStatusLabel = new JLabel();
    private final JButton clearCustomerButton = new JButton();
    private final JTextField customerSearchField = new JTextField();
    private final JLabel subtotalValue = new JLabel();
    private final JLabel discountValue = new JLabel();
    private final JLabel vatLabel = new JLabel();
    private final JLabel vatValue = new JLabel();
    private final JLabel totalValue = new JLabel();
    private final JLabel pointsDiscountValue = new JLabel();
    private final JCheckBox usePointsCheck = new JCheckBox("Dùng điểm");
    private final JSpinner pointsSpinner = new JSpinner(new SpinnerNumberModel(0, 0, 0, 1));
    private final JLabel pointsHintLabel = new JLabel(" ");
    private final JPanel pointsRowPanel = new JPanel(new BorderLayout(6, 0));
    private final JTextField promoCodeField = new JTextField();
    private final JButton applyPromoButton = new JButton("Áp dụng");
    private final JButton clearPromoButton = new JButton("Bỏ");
    private final JLabel promoStatusLabel = new JLabel(" ");
    private final JButton checkoutButton = new JButton();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang lập hóa đơn...");
    private String selectedPaymentMethod = "CASH";
    private final java.util.Map<String, JToggleButton> paymentButtons = new java.util.LinkedHashMap<>();
    private final Runnable cartListener = this::refreshCartSummary;

    // ================================================================
    // ====== THÊM: CategoryOption class (chuẩn FilterDropdown) ======
    // ================================================================
    private static final class CategoryOption {
        final Integer categoryId;
        final String label;
        CategoryOption(Integer categoryId, String label) {
            this.categoryId = categoryId;
            this.label = label;
        }
        @Override
        public String toString() { return label; }
    }
    // ================================================================

    public PosPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        User user = AuthService.getInstance().getCurrentUser();
        SectionHeader header = new SectionHeader(FontAwesomeSolid.SHOPPING_CART, AppColor.ACCENT,
                "Bán hàng tại quầy",
                "Nhân viên: " + (user != null ? user.getFullName() : "-"));
        add(header, BorderLayout.NORTH);

        vatRate = storeConfigDAO.getVatRate();
        cart.setPointRedeemRate(storeConfigDAO.getPointRedeemRate());
        cart.addListener(cartListener);

        // ================================================================
        // ====== SỬA LỖI: loadCategories TRƯỚC buildLeftPanel ======
        // ====== (buildLeftPanel cần categoryFilter đã được tạo) ======
        loadCategories();
        // ================================================================

        JPanel body = new JPanel(new BorderLayout(AppSpacing.LG, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));
        body.add(buildLeftPanel(), BorderLayout.CENTER);    // ← BÂY GIỜ categoryFilter ĐÃ TỒN TẠI
        JPanel right = buildRightPanel();
        right.setPreferredSize(new Dimension(420, 10));
        right.setMinimumSize(new Dimension(360, 10));
        body.add(right, BorderLayout.EAST);
        add(LoadingOverlay.attach(body, loadingOverlay), BorderLayout.CENTER);

        loadProducts(null, null);
        refreshCartSummary();
        // Nghe MOI DataChangedEvent (VAT/config, san pham bi khoa/ngung ban,
        // category bi vo hieu hoa...) va lam moi toan bo du lieu dang hien thi -
        // truoc day chi reload VAT nen POS "khong dong bo" khi Quan ly san pham
        // khoa san pham hoac Quan ly danh muc ngung ban 1 danh muc.
        AutoRefresher.bind(this, DataChangedEvent.class, 300, this::refreshPosData);
    }

    /**
     * Lam moi toan bo du lieu POS dang hien thi sau khi co DataChangedEvent
     * bat ky (VAT rate, san pham, danh muc...). Danh muc bi vo hieu hoa/ngung
     * ban se bien mat khoi danh sach loc va cac san pham thuoc category do
     * (hoac ban than san pham bi ngung ban) se tu dong bien mat khoi luoi -
     * dung productDAO.findActive()/categoryDAO.findAllActive() von da loc
     * theo Status = ACTIVE o ca 2 phia.
     * <p>
     * Bo loc danh muc dang chon se ve lai "Tat ca danh muc" moi lan lam moi -
     * don gian va an toan hon viec co gang giu nguyen lua chon cu (danh muc
     * dang chon co the vua bi vo hieu hoa).
     */
    private void refreshPosData() {
        reloadVatRate();
        if (categoryFilter != null) {
            categoryFilter.setItems(buildCategoryOptions());
        }
        loadProducts(searchBar.getText(), selectedCategoryId());
    }

    private void reloadVatRate() {
        vatRate = storeConfigDAO.getVatRate();
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

        BaseSearch search = searchBar;
        search.onSearch(keyword -> loadProducts(keyword, selectedCategoryId()));
        filterRow.add(search, BorderLayout.CENTER);

        // ================================================================
        // ====== MỚI: Dùng categoryFilter (FilterDropdown) thay vì JComboBox cũ ======
        // ================================================================
        // Giữ nguyên 200×40 để khớp với BaseSearch + nút quét mã 40×40 của POS
        categoryFilter.setPreferredSize(new Dimension(200, 40));
        categoryFilter.setMaximumSize(new Dimension(220, 40));

        // onChange = thay danh mục → reload sản phẩm (giữ nguyên keyword trong ô tìm kiếm)
        categoryFilter.onChange(opt -> loadProducts(search.getText(), selectedCategoryId()));

        JPanel rightFilter = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        rightFilter.setOpaque(false);
        rightFilter.add(categoryFilter, BorderLayout.CENTER);   // ← FilterDropdown thay JComboBox cũ
        rightFilter.add(buildScanButton(), BorderLayout.EAST);
        // ================================================================

        filterRow.add(rightFilter, BorderLayout.EAST);
        panel.add(filterRow, BorderLayout.NORTH);

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
        productGrid.onReportStock(this::openReportStockAlert);
        return panel;
    }

    private void openReportStockAlert(Product product) {
        if (product == null) return;
        User user = AuthService.getInstance().getCurrentUser();
        if (user == null) {
            AppAlert.error(this, "Chưa đăng nhập", "Vui lòng đăng nhập lại để gửi báo cáo tồn kho.");
            return;
        }
        StockAlertDAO alertDAO = new StockAlertDAO();
        if (alertDAO.hasActiveAlert(product.getProductId())) {
            AppAlert.warning(this, "Đã có cảnh báo đang xử lý",
                    "Sản phẩm \"" + product.getProductName()
                            + "\" đã có cảnh báo tồn kho chưa xử lý. Quản lý kho sẽ nhận được thông báo.");
            return;
        }
        Window owner = SwingUtilities.getWindowAncestor(this);
        new ReportStockAlertDialog(owner instanceof Frame ? (Frame) owner : null,
                product, user.getUserId())
                .setVisible(true);
    }

    private JButton buildScanButton() {
        JButton btn = iconButton(FontAwesomeSolid.CAMERA, AppColor.ACCENT);
        btn.setToolTipText("Quét mã vạch sản phẩm bằng webcam");
        btn.addActionListener(e -> openBarcodeScanner());
        return btn;
    }

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

    // ================================================================
    // ====== ĐỔI: selectedCategoryId đọc từ FilterDropdown ======
    // ================================================================
    private Integer selectedCategoryId() {
        CategoryOption opt = categoryFilter == null ? null : categoryFilter.getSelected();
        return opt == null ? null : opt.categoryId;   // null = Tất cả danh mục
    }

    // ================================================================
    // ====== ĐỔI: loadCategories tạo CategoryOption[] + FilterDropdown ======
    // ================================================================
    private void loadCategories() {
        categoryFilter = new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, buildCategoryOptions());
    }

    /**
     * Danh sach danh muc CON DANG BAN (Status = ACTIVE), dung ca luc khoi
     * tao lan dau (loadCategories) lan luc lam moi sau khi co thay doi tu
     * trang Quan ly danh muc (xem refreshPosData). Phan tu dau tien luon la
     * "Tat ca danh muc" (id = null).
     */
    private CategoryOption[] buildCategoryOptions() {
        List<Category> categories = categoryDAO.findAllActive();
        CategoryOption[] options = new CategoryOption[categories.size() + 1];
        options[0] = new CategoryOption(null, "Tất cả danh mục");   // index 0 = Tất cả (null id)
        for (int i = 0; i < categories.size(); i++) {
            Category c = categories.get(i);
            options[i + 1] = new CategoryOption(c.getCategoryId(), c.getCategoryName());
        }
        return options;
    }
    // ================================================================
    // ====================== HẾT PHẦN THAY ĐỔI ======================
    // ================================================================

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
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setOpaque(true);
        panel.setBackground(AppColor.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        JPanel top = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(sectionLabel("Khách hàng"));
        top.add(Box.createVerticalStrut(4));
        top.add(fixedHeight(buildCustomerSearchRow(), 34));
        top.add(Box.createVerticalStrut(2));
        top.add(fixedHeight(buildCustomerStatusRow(), 20));
        panel.add(top, BorderLayout.NORTH);

        JPanel cartSection = new JPanel(new BorderLayout(0, 2));
        cartSection.setOpaque(false);
        cartSection.add(sectionLabel("Giỏ hàng"), BorderLayout.NORTH);
        cartListPanel.setLayout(new BoxLayout(cartListPanel, BoxLayout.Y_AXIS));
        cartListPanel.setOpaque(false);
        cartListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane cartScroll = new JScrollPane(cartListPanel);
        cartScroll.setBorder(null);
        cartScroll.setOpaque(false);
        cartScroll.getViewport().setOpaque(false);
        cartScroll.getVerticalScrollBar().setUnitIncrement(14);
        cartScroll.setMinimumSize(new Dimension(10, 240));
        cartSection.add(cartScroll, BorderLayout.CENTER);
        panel.add(cartSection, BorderLayout.CENTER);

        JPanel bottom = new JPanel() {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(sep);
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(fixedHeight(buildPromoRowCompact(), 30));
        promoStatusLabel.setFont(AppFont.SMALL);
        promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
        promoStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        promoStatusLabel.setText(" ");
        bottom.add(promoStatusLabel);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(fixedHeight(buildPointsRow(), 28));
        pointsHintLabel.setFont(AppFont.SMALL);
        pointsHintLabel.setForeground(AppColor.TEXT_MUTED);
        pointsHintLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bottom.add(pointsHintLabel);
        bottom.add(Box.createVerticalStrut(4));
        bottom.add(fixedHeight(summaryRow(new JLabel("Tạm tính"), subtotalValue, AppFont.BODY, AppColor.TEXT_SECONDARY), 18));
        bottom.add(fixedHeight(summaryRow(new JLabel("Giảm giá"), discountValue, AppFont.BODY, AppColor.SUCCESS), 18));
        bottom.add(fixedHeight(summaryRow(vatLabel, vatValue, AppFont.BODY, AppColor.TEXT_SECONDARY), 18));
        bottom.add(fixedHeight(summaryRow(new JLabel("Trừ điểm"), pointsDiscountValue, AppFont.BODY, AppColor.SUCCESS), 18));
        bottom.add(Box.createVerticalStrut(2));
        bottom.add(fixedHeight(summaryRow(new JLabel("Tổng cộng"), totalValue, AppFont.HEADING_MD, AppColor.TEXT_TITLE), 26));
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(fixedHeight(buildPaymentMethodRow(), 32));
        bottom.add(Box.createVerticalStrut(6));
        bottom.add(fixedHeight(buildCheckoutButton(), 42));
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPromoRowCompact() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);

        JLabel label = new JLabel("Mã KM");
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(AppColor.TEXT_TITLE);
        label.setBorder(new EmptyBorder(0, 0, 0, 4));
        row.add(label, BorderLayout.WEST);

        // Ô nhập mã — GIẢM chiều cao 34px, giảm padding trên/dưới cho cân đối
        promoCodeField.setFont(AppFont.BODY);
        promoCodeField.setPreferredSize(new Dimension(140, 34));
        promoCodeField.setMaximumSize(new Dimension(180, 34));
        promoCodeField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1),
                new EmptyBorder(5, 10, 5, 10)  // ← Giảm 6→5px trên/dưới
        ));
        promoCodeField.setToolTipText("Nhập mã khuyến mãi rồi bấm Áp dụng");
        for (var al : promoCodeField.getActionListeners()) {
            promoCodeField.removeActionListener(al);
        }
        promoCodeField.addActionListener(e -> applyPromoFromField());
        row.add(promoCodeField, BorderLayout.CENTER);

        JPanel buttonGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonGroup.setOpaque(false);

        // Nút Áp dụng — CÙNG 34px, đủ chỗ hiển thị viền
        applyPromoButton.setText("Áp dụng");
        applyPromoButton.setFont(AppFont.SMALL_BOLD);
        applyPromoButton.setPreferredSize(new Dimension(90, 34));  // ← 36→34
        applyPromoButton.setOpaque(true);
        applyPromoButton.setContentAreaFilled(true);
        applyPromoButton.setBorderPainted(false);
        applyPromoButton.setFocusPainted(false);
        applyPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyPromoButton.setBackground(AppColor.ACCENT);
        applyPromoButton.setForeground(Color.WHITE);
        for (var al : applyPromoButton.getActionListeners()) {
            applyPromoButton.removeActionListener(al);
        }
        applyPromoButton.addActionListener(e -> applyPromoFromField());
        buttonGroup.add(applyPromoButton);

        // Nút Bỏ — CÙNG 34px
        clearPromoButton.setText("Bỏ");
        clearPromoButton.setFont(AppFont.SMALL_BOLD);
        clearPromoButton.setPreferredSize(new Dimension(60, 34));  // ← 36→34
        clearPromoButton.setOpaque(false);
        clearPromoButton.setContentAreaFilled(false);
        clearPromoButton.setBorderPainted(true);
        clearPromoButton.setFocusPainted(false);
        clearPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearPromoButton.setForeground(AppColor.ACCENT_HOVER);
        clearPromoButton.setBorder(new LineBorder(AppColor.BORDER, 1));
        clearPromoButton.setVisible(false);
        for (var al : clearPromoButton.getActionListeners()) {
            clearPromoButton.removeActionListener(al);
        }
        clearPromoButton.addActionListener(e -> {
            cart.clearPromotion();
            promoCodeField.setText("");
            promoCodeField.setEditable(true);
            promoStatusLabel.setText(" ");
            promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
        });
        buttonGroup.add(clearPromoButton);

        row.add(buttonGroup, BorderLayout.EAST);
        return row;
    }

    private JPanel buildPromoRow() {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        promoCodeField.setFont(AppFont.BODY);
        promoCodeField.setToolTipText("Nhập mã khuyến mãi rồi bấm Áp dụng");
        promoCodeField.addActionListener(e -> applyPromoFromField());
        row.add(promoCodeField, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.setOpaque(false);
        applyPromoButton.setFont(AppFont.SMALL);
        applyPromoButton.setFocusPainted(false);
        applyPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        applyPromoButton.addActionListener(e -> applyPromoFromField());
        buttons.add(applyPromoButton);
        clearPromoButton.setFont(AppFont.SMALL);
        clearPromoButton.setFocusPainted(false);
        clearPromoButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        clearPromoButton.setVisible(false);
        clearPromoButton.addActionListener(e -> {
            cart.clearPromotion();
            promoCodeField.setText("");
            promoCodeField.setEditable(true);
            promoStatusLabel.setText(" ");
            promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
        });
        buttons.add(clearPromoButton);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    private void applyPromoFromField() {
        String code = promoCodeField.getText() != null ? promoCodeField.getText().trim() : "";
        if (code.isEmpty()) {
            AppAlert.warning(this, "Vui lòng nhập mã khuyến mãi.");
            return;
        }
        if (cart.isEmpty()) {
            AppAlert.warning(this, "Giỏ hàng đang trống.");
            return;
        }
        var result = cart.applyPromotionCode(code);
        if (result.ok) {
            promoStatusLabel.setText("Đã áp dụng: " + result.promotion.getName()
                    + " (−" + NumberUtil.formatThousands(result.discountAmount.longValue()) + " đ)");
            promoStatusLabel.setForeground(AppColor.SUCCESS);
            clearPromoButton.setVisible(true);
            promoCodeField.setEditable(false);
        } else {
            promoStatusLabel.setText(result.message != null ? result.message : "Mã không hợp lệ");
            promoStatusLabel.setForeground(AppColor.ERROR);
            clearPromoButton.setVisible(false);
            promoCodeField.setEditable(true);
        }
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
        JPanel searchButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        searchButtons.setOpaque(false);
        JButton scanCustomerBtn = iconButton(FontAwesomeSolid.ID_CARD, AppColor.ACCENT);
        scanCustomerBtn.setToolTipText("Quét mã/thẻ khách hàng bằng webcam");
        scanCustomerBtn.addActionListener(e -> openCustomerBarcodeScanner());
        searchButtons.add(scanCustomerBtn);
        JButton searchBtn = iconButton(FontAwesomeSolid.SEARCH, AppColor.ACCENT_HOVER);
        searchBtn.addActionListener(e -> searchCustomer());
        searchButtons.add(searchBtn);
        row.add(searchButtons, BorderLayout.EAST);
        return row;
    }

    private void openCustomerBarcodeScanner() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        new BarcodeScannerDialog(owner, "Quét mã khách hàng", "Đưa mã vạch/thẻ thành viên của khách vào giữa khung hình")
                .onScanned(this::handleCustomerBarcodeScanned)
                .setVisible(true);
    }

    private void handleCustomerBarcodeScanned(String code) {
        Customer customer = customerDAO.findByCode(code);
        if (customer == null) {
            AppAlert.error(this, "Không tìm thấy khách hàng",
                    "Không có khách hàng nào khớp với mã vừa quét: \"" + code + "\".");
            return;
        }
        selectCustomer(customer);
        AppAlert.success(this, "Đã chọn khách hàng \"" + customer.getFullName() + "\".");
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
            JMenuItem item = new JMenuItem(c.getFullName() + "   -   " + phone
                    + "   -   " + c.getMemberPoint() + " điểm");
            item.setFont(AppFont.BODY);
            item.addActionListener(e -> selectCustomer(c));
            popup.add(item);
        }
        popup.show(customerSearchField, 0, customerSearchField.getHeight());
    }

    private void selectCustomer(Customer customer) {
        String phone = customer.getPhone() != null && !customer.getPhone().isBlank() ? customer.getPhone() : "";
        String label = customer.getFullName() + (phone.isEmpty() ? "" : " - " + phone)
                + " - Điểm: " + customer.getMemberPoint();
        cart.setCustomer(customer.getCustomerId(), label, customer.getMemberPoint());
        customerSearchField.setText("");
    }

    private JPanel buildPointsRow() {
        pointsRowPanel.setOpaque(false);
        usePointsCheck.setFont(AppFont.SMALL_BOLD);
        usePointsCheck.setOpaque(false);
        usePointsCheck.setFocusPainted(false);
        for (var al : usePointsCheck.getActionListeners()) {
            usePointsCheck.removeActionListener(al);
        }
        usePointsCheck.addActionListener(e -> {
            boolean on = usePointsCheck.isSelected();
            pointsSpinner.setEnabled(on);
            if (!on) {
                cart.setPointsToUse(0);
            } else {
                SpinnerNumberModel model = (SpinnerNumberModel) pointsSpinner.getModel();
                int max = model.getMaximum() instanceof Number n ? n.intValue() : 0;
                cart.setPointsToUse(max);
                pointsSpinner.setValue(max);
            }
        });
        pointsRowPanel.add(usePointsCheck, BorderLayout.WEST);
        pointsSpinner.setFont(AppFont.BODY);
        pointsSpinner.setEnabled(false);
        JComponent editor = pointsSpinner.getEditor();
        if (editor instanceof JSpinner.DefaultEditor de) {
            de.getTextField().setColumns(6);
        }
        pointsSpinner.addChangeListener(e -> {
            if (!usePointsCheck.isSelected()) return;
            int v = ((Number) pointsSpinner.getValue()).intValue();
            cart.setPointsToUse(v);
        });
        pointsRowPanel.add(pointsSpinner, BorderLayout.CENTER);
        return pointsRowPanel;
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
        long discount = cart.getDiscountAmountLong();
        long taxable = Math.max(0, subTotal - discount);
        long vat = calculateVat(taxable);
        long totalBeforePoints = taxable + vat;
        long pointsDisc = cart.getPointsDiscountAmount().longValue();
        if (pointsDisc > totalBeforePoints) pointsDisc = totalBeforePoints;
        long total = Math.max(0, totalBeforePoints - pointsDisc);
        vatLabel.setText("VAT (" + vatRate.stripTrailingZeros().toPlainString() + "%)");
        subtotalValue.setText(NumberUtil.formatThousands(subTotal) + " đ");
        discountValue.setText((discount > 0 ? "−" : "") + NumberUtil.formatThousands(discount) + " đ");
        vatValue.setText(NumberUtil.formatThousands(vat) + " đ");
        pointsDiscountValue.setText((pointsDisc > 0 ? "−" : "") + NumberUtil.formatThousands(pointsDisc) + " đ");
        totalValue.setText(NumberUtil.formatThousands(total) + " đ");
        boolean hasPromo = cart.getAppliedPromotion() != null;
        clearPromoButton.setVisible(hasPromo);
        promoCodeField.setEditable(!hasPromo);
        if (hasPromo) {
            promoCodeField.setText(cart.getAppliedPromotion().getCode());
        }
        boolean hasCustomer = cart.getCustomerId() != null;
        int memberPts = cart.getCustomerMemberPoint();
        long redeem = cart.getPointRedeemRate().longValue();
        pointsRowPanel.setVisible(hasCustomer && memberPts > 0);
        pointsHintLabel.setVisible(hasCustomer && memberPts > 0);
        if (hasCustomer && memberPts > 0) {
            int maxByMoney = redeem > 0 ? (int) Math.min(memberPts, totalBeforePoints / redeem) : 0;
            SpinnerNumberModel model = (SpinnerNumberModel) pointsSpinner.getModel();
            model.setMinimum(0);
            model.setMaximum(Math.max(0, maxByMoney));
            int cur = cart.getPointsToUse();
            if (cur > maxByMoney) cur = maxByMoney;
            if (((Number) pointsSpinner.getValue()).intValue() != cur) {
                pointsSpinner.setValue(cur);
            }
            usePointsCheck.setSelected(cur > 0);
            pointsSpinner.setEnabled(cur > 0 || usePointsCheck.isSelected());
            pointsHintLabel.setText("Còn " + memberPts + " điểm · 1 điểm = "
                    + NumberUtil.formatThousands(redeem) + " đ · tối đa dùng " + maxByMoney);
        } else {
            usePointsCheck.setSelected(false);
            pointsSpinner.setEnabled(false);
            pointsHintLabel.setText(" ");
        }
        checkoutButton.setEnabled(!cart.isEmpty());
    }

    private long calculateVat(long subTotal) {
        return vatRate.multiply(BigDecimal.valueOf(subTotal))
                .divide(new BigDecimal("100"), 0, java.math.RoundingMode.HALF_UP)
                .longValueExact();
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
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        row.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 8, 6, 8)));
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

    private JPanel summaryRow(JLabel labelComp, JLabel valueLabel, Font labelFont, Color labelColor) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
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
        // Kiem tra lai gio hang truoc khi mo dialog xac nhan: san pham da
        // them tu truoc co the vua bi Admin khoa hoac category cha vua bi
        // ngung ban o 1 tab khac trong luc thu ngan dang phuc vu khach. Neu
        // co, tu dong go khoi gio + bao cho thu ngan, KHONG cho thanh toan
        // tiep tuc voi gio hang cu (InvoiceDAO.createInvoice cung se tu choi
        // nhung kiem tra som o day cho trai nghiem ro rang hon).
        List<Integer> cartProductIds = new ArrayList<>();
        for (CartItem it : cart.getItems()) cartProductIds.add(it.getProduct().getProductId());
        java.util.Set<Integer> inactiveIds = productDAO.findInactiveIds(cartProductIds);
        if (!inactiveIds.isEmpty()) {
            List<String> removedNames = new ArrayList<>();
            for (CartItem it : new ArrayList<>(cart.getItems())) {
                if (inactiveIds.contains(it.getProduct().getProductId())) {
                    removedNames.add(it.getProduct().getProductName());
                    cart.removeItem(it.getProduct().getProductId());
                }
            }
            AppAlert.warning(this, "Sản phẩm sau đã ngừng bán (sản phẩm bị khóa hoặc danh mục ngừng bán) "
                    + "nên đã tự động bị xóa khỏi giỏ hàng: " + String.join(", ", removedNames)
                    + ". Vui lòng kiểm tra lại giỏ hàng trước khi thanh toán.");
            return;
        }
        User currentUser = AuthService.getInstance().getCurrentUser();

        if (currentUser == null) {
            AppAlert.error(
                    this,
                    "Phiên đăng nhập không hợp lệ",
                    "Không tìm thấy tài khoản đang đăng nhập. "
                            + "Vui lòng đăng nhập lại."
            );
            return;
        }

        Shift checkoutShift = shiftService.getMyOpenShift();

        if (checkoutShift == null) {
            AppAlert.warning(
                    this,
                    "Bạn chưa mở ca bán hàng",
                    "Hãy vào mục Ca bán hàng & đối soát quỹ, "
                            + "nhập tiền đầu ca và mở ca trước khi thanh toán."
            );
            return;
        }

        /*
         * Ghi nhớ đúng ca được dùng tại thời điểm bắt đầu thanh toán.
         * Không được tự chuyển hóa đơn sang một ca khác nếu ca này bị đóng.
         */
        int checkoutShiftId = checkoutShift.getShiftId();

        boolean confirmed = BaseDialog.confirm(this, "Xác nhận thanh toán",
                "Lập hóa đơn cho " + cart.getItems().size() + " mặt hàng, tổng cộng "
                        + totalValue.getText() + "?");
        if (!confirmed) return;
        List<CartItem> snapshot = new ArrayList<>(cart.getItems());
        Integer customerId = cart.getCustomerId();
        long expectedSubTotal = cart.getSubTotal();
        long discount = cart.getDiscountAmountLong();
        long taxable = Math.max(0, expectedSubTotal - discount);
        long vat = calculateVat(taxable);
        long totalBeforePoints = taxable + vat;
        long pointsDisc = cart.getPointsDiscountAmount().longValue();
        if (pointsDisc > totalBeforePoints) pointsDisc = totalBeforePoints;
        long expectedTotal = Math.max(0, totalBeforePoints - pointsDisc);
        BigDecimal discountBd = cart.getDiscountAmount();
        Integer promotionId = cart.getAppliedPromotion() != null
                ? cart.getAppliedPromotion().getPromotionId() : null;
        String promotionCode = cart.getAppliedPromotion() != null
                ? cart.getAppliedPromotion().getCode() : null;
        int pointsToUse = cart.getPointsToUse();
        BigDecimal pointsDiscountBd = cart.getPointsDiscountAmount();
        if ("PAYPAL".equals(selectedPaymentMethod)) {
        	payWithPayPalThenCreateInvoice(
        	        currentUser,
        	        checkoutShiftId,
        	        snapshot,
        	        customerId,
                    expectedSubTotal, expectedTotal, discountBd, promotionId, promotionCode,
                    pointsToUse, pointsDiscountBd);
        } else {
            createInvoiceAndFinish(currentUser, checkoutShiftId, snapshot, customerId, expectedSubTotal,
                    selectedPaymentMethod, null, null, discountBd, promotionId, promotionCode,
                    pointsToUse, pointsDiscountBd);
        }
    }

    private void createInvoiceAndFinish(User currentUser,int checkoutShiftId, List<CartItem> snapshot, Integer customerId,
                                         long expectedSubTotal, String paymentMethod,
                                         String payPalOrderId, String payPalCaptureId,
                                         BigDecimal discountAmount, Integer promotionId, String promotionCode,
                                         int pointsUsed, BigDecimal pointsDiscountAmount) {
        checkoutButton.setEnabled(false);
        loadingOverlay.start("Đang lập hóa đơn...");
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            private Invoice invoice;
            private String failureMessage;
            @Override
            protected Boolean doInBackground() {
            	Shift currentShift = shiftService.getMyOpenShift();

            	if (
            	        currentShift == null
            	        || currentShift.getShiftId() != checkoutShiftId
            	) {
            	    failureMessage =
            	            "Ca được dùng khi bắt đầu thanh toán không còn mở. "
            	            + "Vui lòng kiểm tra ca bán hàng rồi thực hiện lại.";

            	    return false;
            	}

            	invoice = new Invoice();
            	invoice.setShiftId(checkoutShiftId);
                invoice.setCreatedBy(currentUser.getUserId());
                invoice.setCustomerId(customerId);
                invoice.setPaymentMethod(paymentMethod);
                invoice.setPayPalOrderId(payPalOrderId);
                invoice.setPayPalCaptureId(payPalCaptureId);
                invoice.setVatRate(vatRate);
                invoice.setDiscountAmount(discountAmount != null ? discountAmount : BigDecimal.ZERO);
                invoice.setPromotionId(promotionId);
                invoice.setPromotionCode(promotionCode);
                invoice.setPointsUsed(pointsUsed);
                invoice.setPointsDiscountAmount(pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO);
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
                    promoCodeField.setText("");
                    promoCodeField.setEditable(true);
                    promoStatusLabel.setText(" ");
                    promoStatusLabel.setForeground(AppColor.TEXT_MUTED);
                    loadProducts(null, null);
                    Window owner = SwingUtilities.getWindowAncestor(PosPanel.this);
                    PaymentSuccessDialog successDialog = new PaymentSuccessDialog(
                            owner instanceof Frame ? (Frame) owner : null, invoice, invoiceDAO);
                    successDialog.setVisible(true);
                } else {
                	AppAlert.error(
                	        PosPanel.this,
                	        "Không thể lập hóa đơn",
                	        failureMessage != null
                	                ? failureMessage
                	                : "Ca có thể vừa bị đóng, sản phẩm đã hết hàng "
                	                        + "hoặc dữ liệu thanh toán không còn hợp lệ. "
                	                        + "Vui lòng kiểm tra ca và giỏ hàng rồi thử lại."
                	);
                    loadProducts(null, null);
                }
            }
        };
        worker.execute();
    }

    // ---------------- Thanh toan PayPal (sandbox) tai quay ----------------
    private void payWithPayPalThenCreateInvoice(User currentUser, int checkoutShiftId, List<CartItem> snapshot, Integer customerId,
                                                 long expectedSubTotal, long totalVnd,
                                                 BigDecimal discountAmount, Integer promotionId, String promotionCode,
                                                 int pointsUsed, BigDecimal pointsDiscountAmount) {
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

                boolean approvalReceived = false;

                try {
                    PayPalService.CreatedOrder created =
                            payPalService.createOrder(
                                    BigDecimal.valueOf(totalVnd),
                                    "POS-" + System.currentTimeMillis(),
                                    server.returnUrl(),
                                    server.cancelUrl()
                            );

                    approveUrlHolder[0] =
                            created.approveUrl();

                    publish(
                            created.approveUrl()
                    );

                    PayPalService.ApprovalResult approval =
                            server.await(
                                    Duration.ofMinutes(5)
                            );

                    if (!approval.approved()) {
                        return new PayPalPosResult(
                                false,
                                "CANCELLED",
                                created.payPalOrderId(),
                                null
                        );
                    }

                    approvalReceived = true;

                    /*
                     * Kiểm tra đúng ca đã bắt đầu thanh toán.
                     */
                    Shift currentShift =
                            shiftService.getMyOpenShift();

                    if (
                            currentShift == null
                            || currentShift.getShiftId()
                                    != checkoutShiftId
                    ) {
                        server.completeBrowserFailure(
                                "Ca bán hàng đã bị đóng "
                              + "hoặc đã chuyển sang ca khác. "
                              + "SIMS không gửi yêu cầu capture, "
                              + "khách hàng không bị trừ tiền."
                        );

                        server.awaitBrowserResponse(
                                Duration.ofSeconds(5)
                        );

                        return new PayPalPosResult(
                                false,
                                "SHIFT_CLOSED",
                                created.payPalOrderId(),
                                null
                        );
                    }

                    /*
                     * Chỉ gọi PayPal capture khi ca vẫn hợp lệ.
                     */
                    PayPalService.CaptureResult result =
                            payPalService.captureOrder(
                                    created.payPalOrderId()
                            );

                    if (result.success()) {
                        server.completeBrowserSuccess(
                                "PayPal đã xác nhận giao dịch "
                              + "và SIMS đã hoàn tất thanh toán."
                        );

                        server.awaitBrowserResponse(
                                Duration.ofSeconds(5)
                        );

                        return new PayPalPosResult(
                                true,
                                "COMPLETED",
                                created.payPalOrderId(),
                                result.captureId()
                        );
                    }

                    server.completeBrowserFailure(
                            "PayPal không thể hoàn tất giao dịch. "
                          + "Không có thanh toán thành công được ghi nhận."
                    );

                    server.awaitBrowserResponse(
                            Duration.ofSeconds(5)
                    );

                    return new PayPalPosResult(
                            false,
                            result.status(),
                            created.payPalOrderId(),
                            null
                    );

                } catch (Exception e) {
                    /*
                     * Nếu callback PayPal đã trở về nhưng quá trình
                     * capture phát sinh lỗi thì trả trang thất bại.
                     */
                    if (approvalReceived) {
                        server.completeBrowserFailure(
                                "Thanh toán thất bại do SIMS "
                              + "không thể hoàn tất giao dịch. "
                              + "Vui lòng quay lại ứng dụng để kiểm tra."
                        );

                        server.awaitBrowserResponse(
                                Duration.ofSeconds(5)
                        );
                    }

                    throw e;

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
                        createInvoiceAndFinish(
                                currentUser,
                                checkoutShiftId,
                                snapshot,
                                customerId,
                                expectedSubTotal,
                                "PAYPAL",
                                result.orderId(),
                                result.captureId(),
                                discountAmount,
                                promotionId,
                                promotionCode,
                                pointsUsed,
                                pointsDiscountAmount
                        );

                    } else if ("SHIFT_CLOSED".equals(result.status())) {
                        AppAlert.warning(
                                PosPanel.this,
                                "Ca bán hàng không còn hợp lệ",
                                "Ca được dùng khi bắt đầu thanh toán đã bị đóng "
                                        + "hoặc đã chuyển sang ca khác. "
                                        + "PayPal chưa được capture. Vui lòng mở đúng ca và thanh toán lại."
                        );

                    } else if (!"CANCELLED".equals(result.status())) {
                        AppAlert.error(
                                PosPanel.this,
                                "Thanh toán PayPal thất bại",
                                "Không thể chốt giao dịch PayPal. "
                                        + "Vui lòng thử lại hoặc chọn phương thức khác."
                        );

                    } else {
                        AppAlert.warning(
                                PosPanel.this,
                                "Đã hủy thanh toán PayPal",
                                "Khách chưa duyệt thanh toán trong trang PayPal. "
                                        + "Giỏ hàng vẫn được giữ nguyên."
                        );
                    }
                } catch (CancellationException ignored) {
                } catch (Exception e) {
                    AppAlert.error(PosPanel.this, "Thanh toán PayPal thất bại",
                            "Có lỗi xảy ra: " + e.getMessage());
                }
            }
        };
        workerRef[0] = worker;
        worker.execute();
        waitingDialog.setVisible(true);
    }

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