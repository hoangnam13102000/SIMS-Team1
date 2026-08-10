package com.components.table;

import com.model.InventoryBatch;
import com.theme.AppColor;
import com.theme.AppFont;

import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.TableCellRenderer;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Cột "Lô hàng" - hiện mã lô (BatchCode) đang còn tồn của sản phẩm, ưu tiên
 * lô gần hết hạn nhất lên trước (đúng thứ tự FEFO của
 * {@code InventoryBatchDAO#getOrderBy()}), để nhân viên nhìn danh sách kho
 * là biết ngay cần tìm đúng lô nào trên kệ/kho vật lý khi bổ sung hàng, thay
 * vì phải mở riêng trang "Quản lý lô hàng" rồi lọc lại theo sản phẩm.
 * <p>
 * Không còn lô nào tồn (Stock = 0, mọi lô đã DEPLETED) -> hiện "Cần nhập
 * thêm" thay vì để trống - đúng lúc đó hành động cần làm KHÔNG phải "đi tìm
 * lô" mà là tạo phiếu nhập mới.
 * <p>
 * Value của ô là {@code List<InventoryBatch>} (đã lọc RemainingQty > 0,
 * Status = ACTIVE, sắp theo FEFO) - xem cách nạp dữ liệu theo lô ở
 * InventoryOverviewPanel (1 query duy nhất/trang, không N+1).
 */
public final class BatchLocatorColumn {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private BatchLocatorColumn() {
    }

    /** Value của ô: danh sách lô còn tồn (đã lọc + sắp FEFO). Bọc riêng để có toString() gọn khi export CSV/Excel. */
    public static final class Cell {
        public final List<InventoryBatch> batches;

        public Cell(List<InventoryBatch> batches) {
            this.batches = batches == null ? List.of() : batches;
        }

        /** Dùng khi export CSV/Excel (TableExportUtil ghi String.valueOf(value)). */
        @Override
        public String toString() {
            if (batches.isEmpty()) return "Cần nhập thêm";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < batches.size(); i++) {
                if (i > 0) sb.append("; ");
                InventoryBatch b = batches.get(i);
                sb.append(b.getBatchCode()).append(" (còn ").append(b.getRemainingQty()).append(")");
            }
            return sb.toString();
        }
    }

    public static TableCellRenderer renderer(RowColorProvider colorProvider) {
        ChipPanel panel = new ChipPanel();
        return (t, value, isSelected, hasFocus, row, column) -> {
            panel.setBackground(colorProvider.colorFor(row, isSelected));
            panel.setBatches(value instanceof Cell ? ((Cell) value).batches : List.of());
            return panel;
        };
    }

    private static final class ChipPanel extends JPanel {
        private List<InventoryBatch> batches = List.of();

        ChipPanel() {
            setOpaque(true);
            setLayout(null);
            setBorder(new EmptyBorder(6, 12, 6, 12));
        }

        void setBatches(List<InventoryBatch> batches) {
            this.batches = batches;
            if (batches.isEmpty()) {
                setToolTipText("Không còn lô nào tồn kho - cần tạo phiếu nhập mới");
                return;
            }
            StringBuilder tip = new StringBuilder("<html>");
            for (InventoryBatch b : batches) {
                tip.append(b.getBatchCode()).append(" - còn ").append(b.getRemainingQty());
                if (b.getExpiryDate() != null) {
                    tip.append(" · HSD ").append(b.getExpiryDate().format(DATE_FMT));
                }
                tip.append("<br>");
            }
            tip.append("</html>");
            setToolTipText(tip.toString());
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Insets in = getInsets();
            int x = in.left;
            int centerY = getHeight() / 2;

            if (batches.isEmpty()) {
                g2.setFont(AppFont.SMALL);
                g2.setColor(AppColor.TEXT_MUTED);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString("Cần nhập thêm", x, centerY + fm.getAscent() / 2 - 2);
                g2.dispose();
                return;
            }

            InventoryBatch first = batches.get(0);
            String chipText = first.getBatchCode() + " · " + first.getRemainingQty();
            g2.setFont(AppFont.SMALL_BOLD);
            FontMetrics fm = g2.getFontMetrics();
            int chipW = fm.stringWidth(chipText) + 16;
            int chipH = fm.getHeight() + 6;
            int chipY = centerY - chipH / 2;

            g2.setColor(AppColor.ACCENT_BG_SOFT);
            g2.fillRoundRect(x, chipY, chipW, chipH, chipH, chipH);
            g2.setColor(AppColor.ACCENT_HOVER);
            g2.drawString(chipText, x + 8, chipY + fm.getAscent() + 3);

            if (batches.size() > 1) {
                g2.setFont(AppFont.SMALL);
                g2.setColor(AppColor.TEXT_MUTED);
                g2.drawString("+" + (batches.size() - 1) + " lô khác", x + chipW + 8, chipY + fm.getAscent() + 3);
            }

            g2.dispose();
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(190, 44);
        }
    }
}