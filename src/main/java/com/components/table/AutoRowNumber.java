package com.components.table;

import com.theme.AppColor;
import com.theme.AppSpacing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * Cot "STT" tu dong danh so - khong lay gia tri tu model (co the de trong khi
 * addRow), ma tu tinh theo vi tri dong dang hien thi (row = view row, tuc la
 * da tinh ca sort/filter) cong voi pageOffset (vd trang 2, 10 dong/trang thi
 * dong dau se la STT 11). Goi setPageOffset(...) moi khi doi trang/kich thuoc
 * trang roi table.repaint() de cap nhat.
 *
 * Cach dung:
 *   AutoRowNumber stt = new AutoRowNumber();
 *   table.getColumnModel().getColumn(0).setCellRenderer(stt.renderer(rowColorProvider));
 *   ...
 *   stt.setPageOffset((currentPage - 1) * pageSize);
 *   table.repaint();
 */
public class AutoRowNumber {

    private int pageOffset = 0;

    public AutoRowNumber setPageOffset(int pageOffset) {
        this.pageOffset = Math.max(0, pageOffset);
        return this;
    }

    public int getPageOffset() {
        return pageOffset;
    }

    public TableCellRenderer renderer(RowColorProvider colorProvider) {
        DefaultTableCellRenderer base = new DefaultTableCellRenderer();
        return (t, value, isSelected, hasFocus, row, column) -> {
            JLabel c = (JLabel) base.getTableCellRendererComponent(
                    t, String.valueOf(pageOffset + row + 1), isSelected, hasFocus, row, column);
            c.setHorizontalAlignment(SwingConstants.CENTER);
            c.setOpaque(true);
            c.setBackground(colorProvider.colorFor(row, isSelected));
            c.setForeground(AppColor.TABLE_ROW_TEXT);
            c.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.LG, AppSpacing.SM, AppSpacing.LG));
            return c;
        };
    }
}