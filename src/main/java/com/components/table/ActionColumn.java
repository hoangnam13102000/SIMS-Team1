package com.components.table;

import com.theme.AppColor;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

public class ActionColumn {

    /** Bao cho renderer biet hien tai chuot dang hover o (viewRow, slotIndex) nao trong cot Thao tac. */
    public interface HoverState {
        boolean isHovered(int viewRow, int slotIndex);
    }

    /** Icon phinh to them bao nhieu px khi hover, de nguoi dung nhan biet nut co the bam. */
    private static final int HOVER_SIZE_DELTA = 4;

    /** Lam toi mau icon mot chut khi hover (giong hieu ung "pressed/active" cua nut). */
    private static Color darken(Color c, float factor) {
        int r = Math.max(0, Math.round(c.getRed() * (1 - factor)));
        int g = Math.max(0, Math.round(c.getGreen() * (1 - factor)));
        int b = Math.max(0, Math.round(c.getBlue() * (1 - factor)));
        return new Color(r, g, b, c.getAlpha());
    }

    /** 1 nut hanh dong trong cot. */
    public static final class Item {
        final String id;
        final IntFunction<FontAwesomeSolid> iconProvider;
        final IntFunction<Color> colorProvider;
        final IntFunction<String> tooltipProvider;
        final IntConsumer onClick;
        final IntPredicate enabledPredicate;

        private Item(String id, IntFunction<FontAwesomeSolid> iconProvider, IntFunction<Color> colorProvider,
                      IntFunction<String> tooltipProvider, IntConsumer onClick, IntPredicate enabledPredicate) {
            this.id = id;
            this.iconProvider = iconProvider;
            this.colorProvider = colorProvider;
            this.tooltipProvider = tooltipProvider;
            this.onClick = onClick;
            this.enabledPredicate = enabledPredicate != null ? enabledPredicate : row -> true;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private String headerName = "Thao tác";
    private int iconSize = 16;

    public ActionColumn header(String headerName) {
        this.headerName = headerName;
        return this;
    }

    public ActionColumn iconSize(int iconSize) {
        this.iconSize = iconSize;
        return this;
    }

    public ActionColumn add(String id, FontAwesomeSolid icon, Color color, String tooltip, IntConsumer onClick) {
        return add(id, icon, color, tooltip, onClick, null);
    }

    /** enabledPredicate nhan modelRow, tra ve false de "khoa xam" nut o dong do (khong go bo slot, tranh lech vi tri click). */
    public ActionColumn add(String id, FontAwesomeSolid icon, Color color, String tooltip,
                             IntConsumer onClick, IntPredicate enabledPredicate) {
        items.add(new Item(id, row -> icon, row -> color, row -> tooltip, onClick, enabledPredicate));
        return this;
    }

    public List<Item> getItems() { return items; }
    public String getHeaderName() { return headerName; }

    /**
     * Bien the "dong": icon/mau/tooltip duoc tinh lai theo tung modelRow -
     * dung khi 1 slot can the hien 2 trang thai doi lap (vd Khoa/Mo khoa gop
     * chung 1 nut duy nhat thay vi 2 slot rieng, tranh cot Thao tac qua nhieu
     * icon it dung toi).
     */
    public ActionColumn add(String id, IntFunction<FontAwesomeSolid> iconProvider, IntFunction<Color> colorProvider,
                             IntFunction<String> tooltipProvider, IntConsumer onClick, IntPredicate enabledPredicate) {
        items.add(new Item(id, iconProvider, colorProvider, tooltipProvider, onClick, enabledPredicate));
        return this;
    }

    /** Do rong goi y cho cot, dua tren so luong nut (icon + gap du de click). */
    public int preferredWidth() {
        return 36 + Math.max(1, items.size()) * 42;
    }

    /** Renderer ve cac icon canh nhau, mau nen striped-row lay tu colorProvider - khong co hieu ung hover. */
    public TableCellRenderer renderer(RowColorProvider colorProvider) {
        return renderer(colorProvider, (viewRow, slotIndex) -> false);
    }

    /**
     * Renderer ve cac icon canh nhau, mau nen striped-row lay tu rowColor; hoverState cho biet
     * o (viewRow, slotIndex) nao dang duoc chuot tro toi de phong to + doi mau icon do, giup
     * nguoi dung nhan biet nut co the bam duoc. Slot moi nut luon giu kich thuoc co dinh
     * (iconSize + HOVER_SIZE_DELTA) de icon phinh to khong lam xe cac nut ben canh.
     */
    public TableCellRenderer renderer(RowColorProvider rowColor, HoverState hoverState) {
        return (t, value, isSelected, hasFocus, row, column) -> {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, AppSpacing.SM, AppSpacing.SM));
            panel.setOpaque(true);
            panel.setBackground(rowColor.colorFor(row, isSelected));

            int modelRow = t.convertRowIndexToModel(row);
            int slotBox = iconSize + HOVER_SIZE_DELTA;
            for (int slot = 0; slot < items.size(); slot++) {
                Item item = items.get(slot);
                boolean enabled = item.enabledPredicate.test(modelRow);
                boolean hovered = enabled && hoverState.isHovered(row, slot);

                int renderSize = hovered ? slotBox : iconSize;
                FontIcon icon = FontIcon.of(item.iconProvider.apply(modelRow), renderSize);
                Color baseColor = item.colorProvider.apply(modelRow);
                icon.setIconColor(!enabled ? AppColor.TEXT_DISABLED : (hovered ? darken(baseColor, 0.18f) : baseColor));

                JLabel label = new JLabel(icon);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setVerticalAlignment(SwingConstants.CENTER);
                // Kich thuoc co dinh cho slot (du cho trang thai hover) - icon lon hon van nam
                // giua slot, khong day lech vi tri cac nut ben canh khi hover.
                Dimension fixedSlot = new Dimension(slotBox, slotBox);
                label.setPreferredSize(fixedSlot);
                label.setMinimumSize(fixedSlot);
                label.setCursor(new Cursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                label.setToolTipText(item.tooltipProvider.apply(modelRow));
                panel.add(label);
            }
            return panel;
        };
    }


    public void handleClick(int modelRow, int relativeX, int cellWidth) {
        if (items.isEmpty()) return;
        int slotWidth = Math.max(1, cellWidth / items.size());
        int slot = Math.min(items.size() - 1, Math.max(0, relativeX / slotWidth));
        Item item = items.get(slot);
        if (item.enabledPredicate.test(modelRow)) {
            item.onClick.accept(modelRow);
        }
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }
}