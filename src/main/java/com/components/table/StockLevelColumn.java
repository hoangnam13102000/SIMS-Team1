package com.components.table;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;

/**
 * Cột "Tồn kho" dạng thanh progress bar mini (số lượng + thanh màu), dùng
 * cho các màn hình tổng quan kho (vd InventoryOverviewPanel) để nhìn nhanh
 * mức tồn hiện tại so với mức tồn tối thiểu (Products.MinStock).
 * <p>
 * LƯU Ý: hệ thống không lưu "sức chứa kho vật lý" nên thanh KHÔNG thể hiện
 * %/sức chứa thật - mốc 100% của thanh được quy ước là
 * {@code max(3 x MinStock, Stock hiện tại, 1)}, chỉ mang tính tương đối để
 * mắt nhìn nhanh được xu hướng (đầy/vơi), không phải số liệu chính xác cần
 * đối chiếu. Màu thanh mới là tín hiệu quan trọng: đỏ = hết hàng, vàng =
 * dưới mức tối thiểu, xanh = ổn.
 * <p>
 * Cùng kiểu renderer-factory với {@link StatusColumn}/{@link ImageColumn}
 * - xem BaseTable#setCustomColumn để gắn cho 1 cột cụ thể.
 */
public final class StockLevelColumn {

    private StockLevelColumn() {
    }

    /** 1 giá trị ô của cột: tồn kho hiện tại + mức tồn tối thiểu. Đặt trực tiếp làm value của ô trong TableModel. */
    public static final class Data {
        public final int stock;
        public final int minStock;

        public Data(int stock, int minStock) {
            this.stock = stock;
            this.minStock = minStock;
        }

        /** Dùng khi export CSV/Excel (TableExportUtil ghi String.valueOf(value)). */
        @Override
        public String toString() {
            return stock + " (tối thiểu " + minStock + ")";
        }
    }

    public static TableCellRenderer renderer(RowColorProvider colorProvider) {
        BarPanel bar = new BarPanel();
        return (t, value, isSelected, hasFocus, row, column) -> {
            bar.setBackground(colorProvider.colorFor(row, isSelected));
            bar.setData(value instanceof Data ? (Data) value : new Data(0, 0));
            return bar;
        };
    }

    private static final class BarPanel extends JPanel {
        private int stock;
        private int minStock;
        private String label = "";
        private Color fillColor = AppColor.SUCCESS;

        BarPanel() {
            setOpaque(true);
            setLayout(null);
            setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        }

        void setData(Data d) {
            this.stock = Math.max(0, d.stock);
            this.minStock = Math.max(0, d.minStock);
            this.label = this.stock + " / tối thiểu " + this.minStock;

            if (this.stock <= 0) {
                fillColor = AppColor.ERROR;
            } else if (this.stock <= this.minStock) {
                fillColor = AppColor.WARNING;
            } else {
                fillColor = AppColor.SUCCESS;
            }
            setToolTipText("Tồn hiện tại: " + this.stock + " · Mức tối thiểu: " + this.minStock);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Insets in = getInsets();
            int textX = in.left;
            int textTop = in.top;

            g2.setFont(AppFont.SMALL_BOLD);
            g2.setColor(AppColor.TEXT_PRIMARY);
            g2.drawString(label, textX, textTop + g2.getFontMetrics().getAscent());

            int barH = 6;
            int barY = textTop + g2.getFontMetrics().getHeight() + 6;
            int barW = Math.max(10, getWidth() - in.left - in.right);
            int barX = in.left;

            g2.setColor(AppColor.BG_LIGHTER);
            g2.fillRoundRect(barX, barY, barW, barH, barH, barH);

            int target = Math.max(Math.max(minStock * 3, stock), 1);
            double ratio = Math.min(1.0, stock / (double) target);
            int fillW = (int) Math.round(barW * ratio);
            if (fillW > 0) {
                g2.setColor(fillColor);
                g2.fillRoundRect(barX, barY, Math.max(fillW, barH), barH, barH, barH);
            }

            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(150, 44);
        }
    }
}