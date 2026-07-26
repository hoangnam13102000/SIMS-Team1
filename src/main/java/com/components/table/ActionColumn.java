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

    /** Do rong goi y cho cot, dua tren so luong nut. */
    public int preferredWidth() {
        return 30 + Math.max(1, items.size()) * 38;
    }

    /** Renderer ve cac icon canh nhau, mau nen striped-row lay tu colorProvider. */
    public TableCellRenderer renderer(RowColorProvider colorProvider) {
        return (t, value, isSelected, hasFocus, row, column) -> {
            JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, AppSpacing.SM, AppSpacing.SM));
            panel.setOpaque(true);
            panel.setBackground(colorProvider.colorFor(row, isSelected));

            int modelRow = t.convertRowIndexToModel(row);
            for (Item item : items) {
                boolean enabled = item.enabledPredicate.test(modelRow);
                FontIcon icon = FontIcon.of(item.iconProvider.apply(modelRow), iconSize);
                icon.setIconColor(enabled ? item.colorProvider.apply(modelRow) : AppColor.TEXT_DISABLED);

                JLabel label = new JLabel(icon);
                label.setHorizontalAlignment(SwingConstants.CENTER);
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