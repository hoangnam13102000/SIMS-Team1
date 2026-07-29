package com.view.client;

import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.product.ProductGrid;
import com.core.log.AppLogger;
import com.components.AppAlert;
import com.components.EmptyState;
import com.core.log.ErrorCode;
import com.dao.ProductDAO;
import com.i18n.Lang;
import com.model.Product;
import com.service.CartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

public class HomePanel extends JPanel {

    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final LoadingOverlay loadingOverlay;

    private String currentKeyword = "";
    private java.util.function.Consumer<Product> onProductClickListener;

    public HomePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        add(buildHeaderBlock(), BorderLayout.NORTH);

        productGrid = new ProductGrid();
        productGrid.onAddToCart(product -> {
            if (product.isOutOfStock()) {
                AppAlert.warning(this, "Sản phẩm đã hết hàng.");
                return;
            }

            CartService.getInstance().addToCart(product, 1);

            AppAlert.success(this, "Đã thêm \"" + product.getProductName() + "\" vào giỏ hàng.");
        });  
        productGrid.onCardClick(this::onProductSelected);

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        loadingOverlay = new LoadingOverlay(Lang.get("home.loading"));

        add(LoadingOverlay.attach(contentArea, loadingOverlay), BorderLayout.CENTER);

        loadProducts(null);
    }

    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("home.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(Lang.get("home.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(4, 0, 0, 0));

        wrapper.add(title);
        wrapper.add(subtitle);
        return wrapper;
    }

    /** Goi tu ClientHeader khi nguoi dung nhap tu khoa va nhan Enter/nut Tim. keyword null hoac rong -> hien tat ca. */
    public void search(String keyword) {
        loadProducts(keyword);
    }

    private void loadProducts(String keyword) {
        this.currentKeyword = keyword == null ? "" : keyword.trim();
        loadingOverlay.start();

        SwingWorker<List<Product>, Void> worker = new SwingWorker<>() {
            private Exception error;

            @Override
            protected List<Product> doInBackground() {
                try {
                    return currentKeyword.isEmpty()
                            ? productDAO.findAllActive()
                            : productDAO.searchActive(currentKeyword);
                } catch (Exception e) {
                    error = e;
                    return List.of();
                }
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                if (error != null) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "HomePanel.loadProducts", error);
                    showError();
                    return;
                }
                try {
                    renderProducts(get());
                } catch (Exception e) {
                    AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "HomePanel.loadProducts - get()", e);
                    showError();
                }
            }
        };
        worker.execute();
    }

    private void renderProducts(List<Product> products) {
        contentArea.removeAll();

        if (products == null || products.isEmpty()) {
            EmptyState empty = currentKeyword.isEmpty()
                    ? EmptyState.noData(Lang.get("home.noData.entity"))
                    : EmptyState.noSearchResult(currentKeyword);
            contentArea.add(empty, BorderLayout.CENTER);
        } else {
            productGrid.setProducts(products);

            // Boc grid trong 1 panel BorderLayout.NORTH de JScrollPane khong keo
            // gian GridLayout theo chieu cao vien - moi the giu dung kich thuoc
            // "tu nhien" thay vi bi chia deu het chieu cao khung nhin.
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
        contentArea.add(EmptyState.error(Lang.get("home.loadError")), BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    /** Goi tu ClientMainFrame de dieu huong sang trang chi tiet khi bam vao 1 the san pham. */
    public void onProductClick(java.util.function.Consumer<Product> listener) {
        this.onProductClickListener = listener;
    }

    private void onProductSelected(Product product) {
        if (onProductClickListener != null) onProductClickListener.accept(product);
    }
}