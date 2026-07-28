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

/**
 * Cot hien thi gia tri dang "badge" (pill bo tron) thay vi text thuong - dung
 * cho trang thai don hang, ton kho, vai tro user, loai hanh dong log... Nhan
 * vao 1 gia tri goc cua o va tra ve (nhan hien thi, mau) tuong ung, khong gan
 * cung logic domain nao nen tai su dung duoc cho bat ky topic khac.
 *
 * Cach dung:
 *   table.getColumnModel().getColumn(4).setCellRenderer(
 *       StatusColumn.renderer(v -> OrderStatusUtil.label((String) v),
 *                              v -> OrderStatusUtil.color((String) v),
 *                              rowColorProvider));
 */
public final class StatusColumn {

    private StatusColumn() {}

    public static TableCellRenderer renderer(Function<Object, String> labelFn,
                                              Function<Object, Color> colorFn,
                                              RowColorProvider colorProvider) {
        StatBadge badge = new StatBadge("", AppColor.TEXT_MUTED);
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(true);
        wrapper.add(badge);

        return (t, value, isSelected, hasFocus, row, column) -> {
            wrapper.setBackground(colorProvider.colorFor(row, isSelected));
            // Padding ngang SM (thay LG) de badge dai ("Đang hoạt động") khong bi cat
            // khi cot co gian theo khung ma khong bat horizontal scroll.
            wrapper.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.SM, AppSpacing.SM, AppSpacing.SM));
            badge.setBadge(labelFn.apply(value), colorFn.apply(value));
            return wrapper;
        };
    }
}