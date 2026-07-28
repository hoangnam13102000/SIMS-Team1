package com.view.client;

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
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;


public class ProductsPanel extends JPanel {

    private static final int ALL_CATEGORIES = -1;

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel chipBar;
    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final LoadingOverlay loadingOverlay;

    private List<Category> categories = List.of();
    private int selectedCategoryId = ALL_CATEGORIES;

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

        chipBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chipBar.setOpaque(false);
        chipBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        chipBar.setBorder(new EmptyBorder(0, 0, AppSpacing.LG, 0));

        headerBlock.add(title);
        headerBlock.add(subtitle);
        headerBlock.add(chipBar);
        add(headerBlock, BorderLayout.NORTH);

        productGrid = new ProductGrid();
        productGrid.onCardClick(this::onProductSelected);

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        loadingOverlay = new LoadingOverlay(Lang.get("products.loading"));

        add(LoadingOverlay.attach(contentArea, loadingOverlay), BorderLayout.CENTER);

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
        this.selectedCategoryId = ALL_CATEGORIES;
        rebuildChipBar();
        loadProducts();
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
                g2.setColor(active ? AppColor.ACCENT_HOVER : AppColor.BG_LIGHTER);
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
                    renderProducts(get());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ProductsPanel.loadProducts - get()", e);
                    showError();
                }
            }
        };
        worker.execute();
    }

    private void renderProducts(List<Product> products) {
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

    private void onProductSelected(Product product) {
        // San pham hien chua co trang chi tiet/gio hang - de trong cho tinh
        // nang nghiep vu sau (xem gio hang, dat hang...), giong HomePanel.
    }
}