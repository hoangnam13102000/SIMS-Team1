package com.view.client;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.components.AppAlert;
import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.product.ProductGrid;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.CategoryDAO;
import com.dao.ProductDAO;
import com.i18n.Lang;
import com.model.Category;
import com.model.Product;
import com.service.CartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

public class ProductsPanel extends JPanel {

    private static final int ALL_CATEGORIES = -1;
    private static final Color SHOP_GREEN = new Color(34, 166, 94);
    private static final Color SHOP_GREEN_DARK = new Color(22, 128, 72);
    private static final Color SHOP_GREEN_SOFT = new Color(232, 248, 238);

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel chipBar;
    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final JTextField searchField = new JTextField();

    private final JSpinner minPriceSpinner = new JSpinner(
            new SpinnerNumberModel(
                    0L,
                    0L,
                    1_000_000_000L,
                    10_000L
            )
    );

    private final JSpinner maxPriceSpinner = new JSpinner(
            new SpinnerNumberModel(
                    1_000_000_000L,
                    0L,
                    1_000_000_000L,
                    10_000L
            )
    );

    private final JCheckBox inStockOnly =
            new JCheckBox("Chỉ hiện sản phẩm còn hàng");

    private final JComboBox<String> sortCombo =
            new JComboBox<>(new String[]{
                    "Mặc định",
                    "Tên A - Z",
                    "Giá thấp đến cao",
                    "Giá cao đến thấp"
            });

    private final JLabel resultLabel = new JLabel("0 sản phẩm");

    private List<Product> loadedProducts = List.of();
    private final LoadingOverlay loadingOverlay;

    private List<Category> categories = List.of();
    private int selectedCategoryId = ALL_CATEGORIES;
    private Consumer<Product> onProductClickListener;

    public ProductsPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        JPanel headerBlock = new JPanel();
        headerBlock.setOpaque(false);
        headerBlock.setLayout(new BoxLayout(headerBlock, BoxLayout.Y_AXIS));
        headerBlock.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, 0, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("products.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(Lang.get("products.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, AppSpacing.LG, 0));

        chipBar = new JPanel();
        chipBar.setOpaque(false);
        chipBar.setLayout(new BoxLayout(chipBar, BoxLayout.Y_AXIS));
        chipBar.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerBlock.add(title);
        headerBlock.add(subtitle);
        add(headerBlock, BorderLayout.NORTH);

        productGrid = new ProductGrid();
        productGrid.onCardClick(this::onProductSelected);
        productGrid.onAddToCart(this::addProductToCart);

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        loadingOverlay = new LoadingOverlay(Lang.get("products.loading"));

        JPanel catalogPanel = new JPanel(new BorderLayout());
        catalogPanel.setOpaque(false);
        catalogPanel.add(buildFilterToolbar(), BorderLayout.NORTH);
        catalogPanel.add(contentArea, BorderLayout.CENTER);

        JPanel bodyPanel = new JPanel(
                new BorderLayout(AppSpacing.LG, 0)
        );
        bodyPanel.setOpaque(false);
        bodyPanel.setBorder(
                new EmptyBorder(
                        0,
                        AppSpacing.XL,
                        AppSpacing.XL,
                        AppSpacing.XL
                )
        );

        bodyPanel.add(buildSidebar(), BorderLayout.WEST);
        bodyPanel.add(catalogPanel, BorderLayout.CENTER);

        add(
                LoadingOverlay.attach(bodyPanel, loadingOverlay),
                BorderLayout.CENTER
        );

        loadCategoriesThenProducts();
    }

    /** Goi tu ClientMainFrame khi nguoi dung bam vao 1 the o trang "Danh muc": chuyen sang trang nay va loc san theo danh muc do. */
    public void filterByCategory(int categoryId, String categoryName) {
        this.selectedCategoryId = categoryId;
        rebuildChipBar();
        loadProducts();
    }

    /** Bo loc, hien lai toan bo san pham - goi khi vao trang qua thanh dieu huong. */
    public void showAll() {
        resetFilters();
    }

    private void loadCategoriesThenProducts() {
        loadingOverlay.start();

        SwingWorker<List<Category>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Category> doInBackground() {
                try {
                    return categoryDAO.findAllActive();
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductsPanel.loadCategories", error);
                    categories = List.of();
                } else {
                    try {
                        categories = get();
                    } catch (Exception e) {
                        AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductsPanel.loadCategories - get()", e);
                        categories = List.of();
                    }
                }
                rebuildChipBar();
                loadProducts();
            }
        };
        worker.execute();
    }

    private void rebuildChipBar() {
        chipBar.removeAll();
        chipBar.add(buildChip(Lang.get("products.filter.all"), ALL_CATEGORIES));
        for (Category category : categories) {
            chipBar.add(buildChip(category.getCategoryName(), category.getCategoryId()));
        }
        chipBar.revalidate();
        chipBar.repaint();
    }

    private JComponent buildChip(String label, int categoryId) {
        boolean active = categoryId == selectedCategoryId;

        JLabel chip = new JLabel(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(
                        active
                                ? SHOP_GREEN
                                : AppColor.BG_LIGHTER
                );
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                if (!active) {
                    g2.setColor(AppColor.BORDER);
                    g2.setStroke(new BasicStroke(1f));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        chip.setFont(AppFont.SMALL_BOLD);
        chip.setForeground(active ? Color.WHITE : AppColor.TEXT_PRIMARY);
        chip.setBorder(new EmptyBorder(7, 16, 7, 16));
        chip.setCursor(new Cursor(Cursor.HAND_CURSOR));
        chip.setAlignmentX(Component.LEFT_ALIGNMENT);

        chip.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );

        chip.setHorizontalAlignment(SwingConstants.LEFT);
        chip.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (selectedCategoryId == categoryId) return;
                selectedCategoryId = categoryId;
                rebuildChipBar();
                loadProducts();
            }
        });
        return chip;
    }

    private void loadProducts() {
        loadingOverlay.start();
        int categoryIdToLoad = selectedCategoryId;

        SwingWorker<List<Product>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Product> doInBackground() {
                try {
                    return categoryIdToLoad == ALL_CATEGORIES
                            ? productDAO.findAllActive()
                            : productDAO.findActiveByCategory(categoryIdToLoad);
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductsPanel.loadProducts", error);
                    showError();
                    return;
                }
                try {
                	loadedProducts = get();
                	applyFilters();
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductsPanel.loadProducts - get()", e);
                    showError();
                }
            }
        };
        worker.execute();
    }

    private void renderProducts(List<Product> products) {
    	int total = products == null ? 0 : products.size();
        resultLabel.setText(total + " sản phẩm");
        contentArea.removeAll();

        if (products == null || products.isEmpty()) {
            contentArea.add(EmptyState.noData(Lang.get("products.noData.entity")), BorderLayout.CENTER);
        } else {
            productGrid.setProducts(products);

            JPanel gridWrapper = new JPanel(new BorderLayout());
            gridWrapper.setOpaque(false);
            gridWrapper.setBorder(new EmptyBorder(0, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
            gridWrapper.add(productGrid, BorderLayout.NORTH);

            JScrollPane scrollPane = new JScrollPane(gridWrapper);
            scrollPane.setBorder(null);
            scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
            scrollPane.getVerticalScrollBar().setUnitIncrement(16);
            contentArea.add(scrollPane, BorderLayout.CENTER);
        }

        contentArea.revalidate();
        contentArea.repaint();
    }

    private void showError() {
        contentArea.removeAll();
        contentArea.add(EmptyState.error(Lang.get("products.loadError")), BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    /** Goi tu ClientMainFrame de dieu huong sang trang chi tiet khi bam the san pham. */
    public void onProductClick(Consumer<Product> listener) {
        this.onProductClickListener = listener;
    }

    private void onProductSelected(Product product) {
        if (onProductClickListener != null) onProductClickListener.accept(product);
    }
    
    private void addProductToCart(Product product) {
        if (product == null) {
            return;
        }

        if (product.isOutOfStock()) {
            AppAlert.warning(
                    this,
                    "Hết hàng",
                    "Sản phẩm này hiện đã hết hàng."
            );
            return;
        }

        CartService.getInstance().addToCart(product, 1);

        AppAlert.success(
                this,
                "Đã thêm vào giỏ",
                "\"" + product.getProductName()
                        + "\" đã được thêm vào giỏ hàng."
        );
    }
    
    private JComponent buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppColor.WHITE);
        sidebar.setPreferredSize(new Dimension(240, 500));

        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER),
                new EmptyBorder(18, 18, 18, 18)
        ));

        JLabel filterTitle = new JLabel("BỘ LỌC SẢN PHẨM");
        filterTitle.setFont(AppFont.HEADING_MD);
        filterTitle.setForeground(SHOP_GREEN_DARK);
        filterTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel categoryTitle = new JLabel("DANH MỤC");
        categoryTitle.setFont(AppFont.SMALL_BOLD);
        categoryTitle.setForeground(AppColor.TEXT_SECONDARY);
        categoryTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel priceTitle = new JLabel("KHOẢNG GIÁ");
        priceTitle.setFont(AppFont.SMALL_BOLD);
        priceTitle.setForeground(AppColor.TEXT_SECONDARY);
        priceTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel pricePanel = new JPanel(new GridLayout(2, 2, 8, 8));
        pricePanel.setOpaque(false);
        pricePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        pricePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        pricePanel.add(new JLabel("Từ:"));
        pricePanel.add(minPriceSpinner);
        pricePanel.add(new JLabel("Đến:"));
        pricePanel.add(maxPriceSpinner);

        JButton applyPriceButton = new JButton("Áp dụng giá");
        applyPriceButton.setFont(AppFont.SMALL_BOLD);
        applyPriceButton.setForeground(Color.WHITE);
        applyPriceButton.setBackground(SHOP_GREEN);
        applyPriceButton.setFocusPainted(false);
        applyPriceButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        applyPriceButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );

        applyPriceButton.addActionListener(e -> applyFilters());

        inStockOnly.setOpaque(false);
        inStockOnly.setFont(AppFont.SMALL);
        inStockOnly.setAlignmentX(Component.LEFT_ALIGNMENT);
        inStockOnly.addActionListener(e -> applyFilters());

        JButton resetButton = new JButton("Xóa tất cả bộ lọc");
        resetButton.setFont(AppFont.SMALL_BOLD);
        resetButton.setForeground(SHOP_GREEN_DARK);
        resetButton.setBackground(SHOP_GREEN_SOFT);
        resetButton.setFocusPainted(false);
        resetButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resetButton.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 38)
        );

        resetButton.addActionListener(e -> resetFilters());

        sidebar.add(filterTitle);
        sidebar.add(Box.createVerticalStrut(20));

        sidebar.add(categoryTitle);
        sidebar.add(Box.createVerticalStrut(8));
        sidebar.add(chipBar);

        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(new JSeparator());
        sidebar.add(Box.createVerticalStrut(20));

        sidebar.add(priceTitle);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(pricePanel);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(applyPriceButton);

        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(inStockOnly);

        sidebar.add(Box.createVerticalStrut(20));
        sidebar.add(resetButton);

        return sidebar;
    }
    
    private JComponent buildFilterToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(0, 0, 16, 0));

        searchField.setFont(AppFont.FIELD);
        searchField.setPreferredSize(new Dimension(300, 38));
        searchField.putClientProperty(
                "JTextField.placeholderText",
                "Tìm tên, mã hoặc thương hiệu..."
        );

        searchField.getDocument().addDocumentListener(
                new DocumentListener() {
                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        applyFilters();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        applyFilters();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        applyFilters();
                    }
                }
        );

        sortCombo.setFont(AppFont.SMALL);
        sortCombo.setPreferredSize(new Dimension(160, 38));
        sortCombo.addActionListener(e -> applyFilters());

        resultLabel.setFont(AppFont.SMALL_BOLD);
        resultLabel.setForeground(SHOP_GREEN_DARK);

        JPanel right = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 10, 0)
        );
        right.setOpaque(false);
        right.add(resultLabel);
        right.add(new JLabel("Sắp xếp:"));
        right.add(sortCombo);

        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(right, BorderLayout.EAST);

        return toolbar;
    }
    
    private void applyFilters() {
        String keyword = searchField.getText() == null
                ? ""
                : searchField.getText()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        long minPrice =
                ((Number) minPriceSpinner.getValue()).longValue();

        long maxPrice =
                ((Number) maxPriceSpinner.getValue()).longValue();

        if (minPrice > maxPrice) {
            long temp = minPrice;
            minPrice = maxPrice;
            maxPrice = temp;

            minPriceSpinner.setValue(minPrice);
            maxPriceSpinner.setValue(maxPrice);
        }

        List<Product> filtered = new ArrayList<>();

        for (Product product : loadedProducts) {
            long price = product.getSellPrice() == null
                    ? 0
                    : product.getSellPrice().longValue();

            if (price < minPrice || price > maxPrice) {
                continue;
            }

            if (inStockOnly.isSelected()
                    && product.isOutOfStock()) {
                continue;
            }

            if (!keyword.isEmpty()
                    && !matchesKeyword(product, keyword)) {
                continue;
            }

            filtered.add(product);
        }

        switch (sortCombo.getSelectedIndex()) {
            case 1 -> filtered.sort(
                    Comparator.comparing(
                            product -> safe(product.getProductName()),
                            String.CASE_INSENSITIVE_ORDER
                    )
            );

            case 2 -> filtered.sort(
                    Comparator.comparingLong(this::getProductPrice)
            );

            case 3 -> filtered.sort(
                    Comparator.comparingLong(this::getProductPrice)
                            .reversed()
            );

            default -> {
                // Giữ thứ tự từ database.
            }
        }

        renderProducts(filtered);
    }
    
    private boolean matchesKeyword(
            Product product,
            String keyword
    ) {
        return safe(product.getProductName())
                .toLowerCase(Locale.ROOT)
                .contains(keyword)

                || safe(product.getProductCode())
                .toLowerCase(Locale.ROOT)
                .contains(keyword)

                || safe(product.getBrand())
                .toLowerCase(Locale.ROOT)
                .contains(keyword)

                || safe(product.getCategoryName())
                .toLowerCase(Locale.ROOT)
                .contains(keyword);
    }

    private long getProductPrice(Product product) {
        return product.getSellPrice() == null
                ? 0
                : product.getSellPrice().longValue();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
    
    private void resetFilters() {
        selectedCategoryId = ALL_CATEGORIES;

        searchField.setText("");
        minPriceSpinner.setValue(0L);
        maxPriceSpinner.setValue(1_000_000_000L);

        inStockOnly.setSelected(false);
        sortCombo.setSelectedIndex(0);

        rebuildChipBar();
        loadProducts();
    }
}