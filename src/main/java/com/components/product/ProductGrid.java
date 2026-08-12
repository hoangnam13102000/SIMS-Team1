package com.components.product;

import com.model.Product;
import com.theme.AppSpacing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.function.Consumer;

public class ProductGrid extends JPanel {

    private static final int CARD_MIN_WIDTH = 220;
    private static final int GAP = AppSpacing.LG;

    private final JPanel grid;
    private List<Product> products = List.of();
    private Consumer<Product> onCardClick;
    private Consumer<Product> onAddToCart;
    private Consumer<Product> onReportStock;
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

    public void onAddToCart(Consumer<Product> listener) {
        this.onAddToCart = listener;
    }

    /** Callback báo hết/sắp hết hàng thủ công (POS → Quản lý kho). */
    public void onReportStock(Consumer<Product> listener) {
        this.onReportStock = listener;
    }

    public void setProducts(List<Product> products) {
        this.products = products != null ? products : List.of();
        rebuildCards();
    }

    private void rebuildCards() {
        currentColumns = -1;
        relayout(true);
    }

    private void relayout(boolean forceRebuildCards) {
        int width = getWidth();
        if (width <= 0) width = CARD_MIN_WIDTH;

        int columns = Math.max(1, width / (CARD_MIN_WIDTH + GAP));
        if (!forceRebuildCards && columns == currentColumns) {
            return;
        }
        currentColumns = columns;

        grid.removeAll();
        grid.setLayout(new GridLayout(0, columns, GAP, GAP));
        for (Product product : products) {
            ProductCard card = new ProductCard(product, onCardClick, onAddToCart, null, onReportStock);
            grid.add(card);
        }

        grid.revalidate();
        grid.repaint();
    }

    public List<Product> getProducts() {
        return products;
    }

    public boolean isEmpty() {
        return products.isEmpty();
    }
}