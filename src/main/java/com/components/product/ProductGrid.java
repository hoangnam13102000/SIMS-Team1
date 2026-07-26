package com.components.product;

import com.model.Product;
import com.theme.AppSpacing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Luoi (grid) hien thi danh sach ProductCard, tu dong doi so cot theo be
 * rong hien co (giong cach StatCard tu doi co chu khi resize) de dung
 * duoc ca khi cua so nho lai. Khong tu goi DB - chi nhan List<Product> co
 * san tu ben ngoai (vd HomePanel sau khi ProductDAO tra ve), nen component
 * nay tai su dung duoc cho bat ky man hinh nao can hien thi luoi san pham
 * (trang danh muc, ket qua tim kiem...).
 */
public class ProductGrid extends JPanel {

    private static final int CARD_MIN_WIDTH = 220;
    private static final int GAP = AppSpacing.LG;

    private final JPanel grid;
    private List<Product> products = List.of();
    private Consumer<Product> onCardClick;
    private int currentColumns = -1;

    public ProductGrid() {
        setOpaque(false);
        setLayout(new BorderLayout());

        grid = new JPanel();
        grid.setOpaque(false);
        add(grid, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                relayout(false);
            }
        });
    }

    public void onCardClick(Consumer<Product> listener) {
        this.onCardClick = listener;
    }

    /** Nap danh sach san pham moi va ve lai toan bo luoi. */
    public void setProducts(List<Product> products) {
        this.products = products != null ? products : List.of();
        rebuildCards();
    }

    private void rebuildCards() {
        currentColumns = -1; // ep tinh lai layout vi noi dung (so luong the) da doi
        relayout(true);
    }

    private void relayout(boolean forceRebuildCards) {
        int width = getWidth();
        if (width <= 0) width = CARD_MIN_WIDTH;

        int columns = Math.max(1, width / (CARD_MIN_WIDTH + GAP));
        if (!forceRebuildCards && columns == currentColumns) {
            return; // so cot khong doi - khong can ve lai
        }
        currentColumns = columns;

        grid.removeAll();
        grid.setLayout(new GridLayout(0, columns, GAP, GAP));
        for (Product product : products) {
            ProductCard card = new ProductCard(product, onCardClick);
            grid.add(card);
        }

        grid.revalidate();
        grid.repaint();
    }

    public List<Product> getProducts() {
        return products;
    }

    /** Panel trong (chua co Product nao) de hien EmptyState/LoadingOverlay de len tren. */
    public boolean isEmpty() {
        return products.isEmpty();
    }
}