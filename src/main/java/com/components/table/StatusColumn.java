package com.components.table;

import com.components.StatBadge;
import com.theme.AppColor;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.Color;
import java.util.function.Function;


public final class StatusColumn {

    private StatusColumn() {}

    public static TableCellRenderer renderer(Function<Object, String> labelFn,
                                              Function<Object, Color> colorFn,
                                              RowColorProvider colorProvider) {
        return renderer(labelFn, colorFn, colorProvider, FlowLayout.LEFT);
    }

    /** Badge renderer with configurable horizontal alignment for tables that
     * need numeric/status values centered without changing the default style. */
    public static TableCellRenderer renderer(Function<Object, String> labelFn,
                                              Function<Object, Color> colorFn,
                                              RowColorProvider colorProvider,
                                              int horizontalAlignment) {
        StatBadge badge = new StatBadge("", AppColor.TEXT_MUTED);
        JPanel wrapper = new JPanel(new FlowLayout(horizontalAlignment, 0, 0));
        wrapper.setOpaque(true);
        wrapper.add(badge);

        return (t, value, isSelected, hasFocus, row, column) -> {
            wrapper.setBackground(colorProvider.colorFor(row, isSelected));
            // Padding ngang SM (thay LG) de badge dai ("Đang hoạt động") khong bi cat
            // khi cot co gian theo khung ma khong bat horizontal scroll.
            wrapper.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.SM, AppSpacing.SM, AppSpacing.SM));
            String label = labelFn.apply(value);
            badge.setBadge(label, colorFn.apply(value));
            // Tooltip full label khi cot bi be qua muc, badge bi clip.
            badge.setToolTipText(label);
            wrapper.setToolTipText(label);
            return wrapper;
        };
    }
}