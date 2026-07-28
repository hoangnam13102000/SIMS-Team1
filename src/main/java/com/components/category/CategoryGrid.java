package com.components.category;

import com.model.Category;
import com.theme.AppSpacing;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.function.Consumer;

/**
 * Luoi (grid) hien thi danh sach CategoryCard, tu dong doi so cot theo be
 * rong hien co - giong het ProductGrid nhung cho danh muc (dung o
 * CategoriesPanel phia client).
 */
public class CategoryGrid extends JPanel {

    private static final int CARD_MIN_WIDTH = 200;
    private static final int GAP = AppSpacing.LG;

    private final JPanel grid;
    private List<Category> categories = List.of();
    private Consumer<Category> onCardClick;
    private int currentColumns = -1;

    public CategoryGrid() {
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

    public void onCardClick(Consumer<Category> listener) {
        this.onCardClick = listener;
    }

    /** Nap danh sach danh muc moi va ve lai toan bo luoi. */
    public void setCategories(List<Category> categories) {
        this.categories = categories != null ? categories : List.of();
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
        for (Category category : categories) {
            CategoryCard card = new CategoryCard(category, onCardClick);
            grid.add(card);
        }

        grid.revalidate();
        grid.repaint();
    }

    public List<Category> getCategories() {
        return categories;
    }

    public boolean isEmpty() {
        return categories.isEmpty();
    }
}