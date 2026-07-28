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
import java.util.ArrayList;
import java.util.List;

/**
 * Trang "Danh muc" phia client - chon 1 danh muc tu dropdown (JComboBox),
 * san pham thuoc danh muc do hien ngay ben duoi (khong can chuyen trang).
 * Cau truc: 1 khoi tieu de + 1 dropdown + 1 luoi san pham cuon rieng, co
 * LoadingOverlay/EmptyState khi tai/khong co du lieu - giong HomePanel.
 */
public class CategoriesPanel extends JPanel {

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();

    private final JComboBox<CategoryOption> categoryCombo;
    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final LoadingOverlay loadingOverlay;

    public CategoriesPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        categoryCombo = new JComboBox<>();
        categoryCombo.setFont(AppFont.BODY);
        categoryCombo.setFocusable(false);
        categoryCombo.setPreferredSize(new Dimension(260, 34));
        categoryCombo.addActionListener(e -> loadProductsForSelectedCategory());

        add(buildHeaderBlock(), BorderLayout.NORTH);

        productGrid = new ProductGrid();
        // San pham hien chua co trang chi tiet/gio hang, giong HomePanel/ProductsPanel.
        productGrid.onCardClick(product -> { });

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        loadingOverlay = new LoadingOverlay(Lang.get("categories.loading"));

        add(LoadingOverlay.attach(contentArea, loadingOverlay), BorderLayout.CENTER);

        loadCategories();
    }

    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("categories.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(Lang.get("categories.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, AppSpacing.MD, 0));

        JPanel comboRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        comboRow.setOpaque(false);
        comboRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        comboRow.add(categoryCombo);

        wrapper.add(title);
        wrapper.add(subtitle);
        wrapper.add(comboRow);
        return wrapper;
    }

    /** Tai lai danh sach danh muc - goi tu ClientMainFrame moi khi doi theme/ngon ngu de cap nhat lai chu da dich. */
    public void reload() {
        loadCategories();
    }

    private void loadCategories() {
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
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoriesPanel.loadCategories", error);
                    loadingOverlay.stop();
                    showError(Lang.get("categories.loadError"));
                    return;
                }
                try {
                    populateCombo(get());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoriesPanel.loadCategories - get()", e);
                    loadingOverlay.stop();
                    showError(Lang.get("categories.loadError"));
                }
            }
        };
        worker.execute();
    }

    private void populateCombo(List<Category> categories) {
        List<CategoryOption> options = new ArrayList<>();
        options.add(new CategoryOption(-1, Lang.get("categories.dropdown.all")));
        if (categories != null) {
            for (Category category : categories) {
                options.add(new CategoryOption(category.getCategoryId(), category.getCategoryName()));
            }
        }

        categoryCombo.removeAllItems();
        for (CategoryOption option : options) {
            categoryCombo.addItem(option);
        }
        // addItem() o tren tu kich hoat 1 lan actionListener (chon item dau tien) nen san
        // pham se duoc nap lan dau qua do - khong can goi loadProductsForSelectedCategory() them.
    }

    private void loadProductsForSelectedCategory() {
        CategoryOption selected = (CategoryOption) categoryCombo.getSelectedItem();
        int categoryId = selected == null ? -1 : selected.categoryId;

        loadingOverlay.start();

        SwingWorker<List<Product>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Product> doInBackground() {
                try {
                    return categoryId < 0
                            ? productDAO.findAllActive()
                            : productDAO.findActiveByCategory(categoryId);
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoriesPanel.loadProducts", error);
                    showError(Lang.get("categories.loadError"));
                    return;
                }
                try {
                    renderProducts(get());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "CategoriesPanel.loadProducts - get()", e);
                    showError(Lang.get("categories.loadError"));
                }
            }
        };
        worker.execute();
    }

    private void renderProducts(List<Product> products) {
        contentArea.removeAll();

        if (products == null || products.isEmpty()) {
            contentArea.add(EmptyState.noData(Lang.get("categories.noData.entity")), BorderLayout.CENTER);
        } else {
            productGrid.setProducts(products);

            // Boc grid trong 1 panel BorderLayout.NORTH de JScrollPane khong
            // keo gian GridLayout theo chieu cao vien - giong HomePanel.
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

    private void showError(String message) {
        contentArea.removeAll();
        contentArea.add(EmptyState.error(message), BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    /** 1 muc trong dropdown: categoryId = -1 nghia la "Tat ca danh muc". */
    private static class CategoryOption {
        final int categoryId;
        final String label;

        CategoryOption(int categoryId, String label) {
            this.categoryId = categoryId;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}