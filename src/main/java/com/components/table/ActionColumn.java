package com.components.table;

import com.theme.AppColor;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntPredicate;

/**
 * Cot "Thao tac" tai su dung duoc cho bat ky JTable/topic nao (khong rieng gi
 * myShop): khai bao 1 danh sach cac nut icon (xem, sua, xoa, duyet, in, khoa
 * tai khoan...), moi nut co icon/mau/tooltip/handler rieng va co the bat/tat
 * theo tung dong bang enabledPredicate (vd nut "Duyet" chi hien active khi
 * don hang dang o trang thai cho duyet).
 *
 * Cach dung:
 *   ActionColumn actions = new ActionColumn()
 *       .add("view", FontAwesomeSolid.EYE, AppColor.TEXT_SECONDARY, "Xem chi tiet", row -> onView(row))
 *       .add("edit", FontAwesomeSolid.EDIT, AppColor.ACCENT, "Chinh sua", row -> onEdit(row))
 *       .add("delete", FontAwesomeSolid.TRASH, AppColor.ERROR, "Xoa", row -> onDelete(row),
 *              row -> canDelete(row)); // enabledPredicate tuy chon
 *
 * Sau do BaseTable#setActionColumn(actions) se tu lo phan gan renderer + click
 * handler vao dung cot.
 */
public class ActionColumn {

    /** 1 nut hanh dong trong cot. */
    public static final class Item {
        final String id;
        final FontAwesomeSolid icon;
        final Color color;
        final String tooltip;
        final IntConsumer onClick;
        final IntPredicate enabledPredicate;

        private Item(String id, FontAwesomeSolid icon, Color color, String tooltip,
                      IntConsumer onClick, IntPredicate enabledPredicate) {
            this.id = id;
            this.icon = icon;
            this.color = color;
            this.tooltip = tooltip;
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
        items.add(new Item(id, icon, color, tooltip, onClick, enabledPredicate));
        return this;
    }

    public List<Item> getItems() { return items; }
    public String getHeaderName() { return headerName; }

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
                FontIcon icon = FontIcon.of(item.icon, iconSize);
                icon.setIconColor(enabled ? item.color : AppColor.TEXT_DISABLED);

                JLabel label = new JLabel(icon);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setCursor(new Cursor(enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                label.setToolTipText(item.tooltip);
                panel.add(label);
            }
            return panel;
        };
    }

    /**
     * Gan click/hover handler vao table cho dung cot actionColumnIndex. Cac
     * slot duoc chia deu theo be rong cell (giong renderer), click vao slot i
     * se goi onClick cua item i (neu enabledPredicate cho phep).
     */
    public void installClickHandler(JTable table, int actionColumnIndex) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (items.isEmpty()) return;
                int viewCol = table.columnAtPoint(e.getPoint());
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow < 0 || viewCol != actionColumnIndex) return;

                Rectangle cellRect = table.getCellRect(viewRow, viewCol, false);
                int relativeX = e.getX() - cellRect.x;
                int slotWidth = Math.max(1, cellRect.width / items.size());
                int slot = Math.min(items.size() - 1, Math.max(0, relativeX / slotWidth));

                int modelRow = table.convertRowIndexToModel(viewRow);
                Item item = items.get(slot);
                if (item.enabledPredicate.test(modelRow)) {
                    item.onClick.accept(modelRow);
                }
            }
        });

        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int viewCol = table.columnAtPoint(e.getPoint());
                boolean overActions = viewCol == actionColumnIndex && table.rowAtPoint(e.getPoint()) >= 0;
                table.setCursor(overActions ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
            }
        });
    }
}