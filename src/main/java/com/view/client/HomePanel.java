package com.view.client;

import com.components.EmptyState;
import com.components.LoadingOverlay;
import com.components.product.ProductGrid;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.ProductDAO;
import com.model.Product;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

/**
 * Trang chu phia khach hang: hien danh sach san pham dang ban (Status =
 * ACTIVE) duoi dang luoi the (ProductGrid + ProductCard), tai du lieu nen
 * qua SwingWorker de khong dong UI thread. Ho tro loc theo tu khoa - goi
 * tu ClientHeader (xem ClientMainFrame#header.onSearch) qua ham search().
 */
public class HomePanel extends JPanel {

    private final ProductDAO productDAO = new ProductDAO();

    private final JPanel contentArea;
    private final ProductGrid productGrid;
    private final LoadingOverlay loadingOverlay;

    private String currentKeyword = "";

    public HomePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        add(buildHeaderBlock(), BorderLayout.NORTH);

        productGrid = new ProductGrid();
        productGrid.onCardClick(this::onProductSelected);

        contentArea = new JPanel(new BorderLayout());
        contentArea.setOpaque(false);

        loadingOverlay = new LoadingOverlay("Đang tải sản phẩm...");

        add(LoadingOverlay.attach(contentArea, loadingOverlay), BorderLayout.CENTER);

        loadProducts(null);
    }

    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL));

        JLabel title = new JLabel("Sản phẩm nổi bật");
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Khám phá các sản phẩm đang có tại Connect Mart");
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
                    ? EmptyState.noData("sản phẩm")
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
        contentArea.add(EmptyState.error("Không thể tải danh sách sản phẩm. Vui lòng thử lại sau."), BorderLayout.CENTER);
        contentArea.revalidate();
        contentArea.repaint();
    }

    private void onProductSelected(Product product) {
        // San pham hien chua co trang chi tiet/gio hang - de trong cho tinh nang
        // nghiep vu sau (xem gio hang, dat hang...). Tam thoi khong lam gi ca.
    }
}