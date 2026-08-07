package com.components.report;

import com.dao.InventoryReportDAO.ProductStock;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

/**
 * Bieu do cot "Ton kho theo san pham", tu ve bang Graphics2D (dong bo cach
 * lam voi {@link RevenueChartPanel}). Moi cot la 1 san pham, chieu cao la
 * SL ton hien tai (Products.Stock); mau cot canh bao truc tiep theo tinh
 * trang HSD cua cac lo con hang cua san pham do (xem {@link #colorFor}) -
 * giup nguoi xem bao cao thay NGAY san pham nao dang ton nhieu/it VA can
 * uu tien xu ly vi sap/da het han, khong can mo them trang Quan ly lo hang.
 */
public class ProductStockChartPanel extends JComponent {

    private static final int NEAR_EXPIRY_DAYS = 7; // dong bo voi InventoryBatchPanel
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int PAD_LEFT = 56;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 16;
    private static final int PAD_BOTTOM = 46;
    private static final int GRID_LINES = 4;

    private List<ProductStock> data = Collections.emptyList();
    private int hoverIndex = -1;

    public ProductStockChartPanel() {
        setOpaque(false);
        // Chiều cao tối thiểu đủ để vẽ lưới + cột; tránh bị BoxLayout/BorderLayout
        // ép height ≈ 0 khiến paintComponent thoát sớm → "không thấy biểu đồ".
        setPreferredSize(new java.awt.Dimension(10, 280));
        setMinimumSize(new java.awt.Dimension(100, 180));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = indexAt(e.getX());
                if (idx != hoverIndex) {
                    hoverIndex = idx;
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverIndex != -1) {
                    hoverIndex = -1;
                    repaint();
                }
            }
        });
    }

    public void setData(List<ProductStock> data) {
        this.data = data != null ? data : Collections.emptyList();
        this.hoverIndex = -1;
        revalidate();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int idx = indexAt(event.getX());
        if (idx < 0 || idx >= data.size()) return null;
        ProductStock p = data.get(idx);

        StringBuilder sb = new StringBuilder("<html><b>").append(escapeHtml(p.productName)).append("</b>")
                .append("<br>Tồn kho: ").append(NumberUtil.formatThousands(p.stock))
                .append(" (mức tối thiểu: ").append(NumberUtil.formatThousands(p.minStock)).append(")");

        if (p.stock == 0) {
            sb.append("<br><span style='color:#dc2626'>Đã hết hàng</span>");
        } else if (p.hasExpiredBatch) {
            sb.append("<br><span style='color:#dc2626'>Có lô đã hết hạn còn tồn</span>");
        } else if (p.nearestExpiry != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), p.nearestExpiry);
            if (days <= NEAR_EXPIRY_DAYS) {
                sb.append("<br><span style='color:#b45309'>Lô gần nhất hết hạn: ")
                        .append(p.nearestExpiry.format(DATE_FORMAT)).append(" (còn ").append(days).append(" ngày)</span>");
            } else {
                sb.append("<br>Hạn dùng gần nhất: ").append(p.nearestExpiry.format(DATE_FORMAT));
            }
        }
        if (p.stock > 0 && p.stock <= p.minStock) {
            sb.append("<br><span style='color:#b45309'>Dưới mức tồn tối thiểu</span>");
        }
        sb.append("</html>");
        return sb.toString();
    }

    private int indexAt(int mouseX) {
        if (data.isEmpty()) return -1;
        int plotWidth = getWidth() - PAD_LEFT - PAD_RIGHT;
        if (plotWidth <= 0) return -1;
        double slot = plotWidth / (double) data.size();
        int idx = (int) ((mouseX - PAD_LEFT) / slot);
        return (idx >= 0 && idx < data.size()) ? idx : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (data.isEmpty()) {
            drawEmptyState(g2, w, h);
            g2.dispose();
            return;
        }

        int maxStock = 1;
        for (ProductStock p : data) maxStock = Math.max(maxStock, p.stock);
        long axisMax = niceCeiling(maxStock);

        int plotLeft = PAD_LEFT;
        int plotTop = PAD_TOP;
        int plotWidth = w - PAD_LEFT - PAD_RIGHT;
        int plotBottom = h - PAD_BOTTOM;
        int plotHeight = plotBottom - plotTop;
        if (plotWidth <= 0 || plotHeight <= 0) {
            g2.dispose();
            return;
        }

        drawGridAndAxisLabels(g2, plotLeft, plotTop, plotWidth, plotHeight, axisMax);
        drawBars(g2, plotLeft, plotTop, plotWidth, plotHeight, axisMax);
        drawXAxisLabels(g2, plotLeft, plotBottom, plotWidth);

        g2.dispose();
    }

    private void drawEmptyState(Graphics2D g2, int w, int h) {
        g2.setFont(AppFont.BODY);
        g2.setColor(AppColor.TEXT_MUTED);
        String msg = "Không có sản phẩm nào để hiển thị";
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(msg);
        g2.drawString(msg, (w - tw) / 2, h / 2);
    }

    private void drawGridAndAxisLabels(Graphics2D g2, int left, int top, int width, int height, long axisMax) {
        g2.setFont(AppFont.SMALL);
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i <= GRID_LINES; i++) {
            int y = top + height - (int) ((double) i / GRID_LINES * height);
            g2.setColor(AppColor.TABLE_GRID);
            g2.drawLine(left, y, left + width, y);

            long value = axisMax * i / GRID_LINES;
            String label = NumberUtil.formatCompact(value);
            g2.setColor(AppColor.TEXT_MUTED);
            int tw = fm.stringWidth(label);
            g2.drawString(label, left - tw - 8, y + fm.getAscent() / 2 - 1);
        }
    }

    private void drawBars(Graphics2D g2, int left, int top, int width, int height, long axisMax) {
        int n = data.size();
        double slot = width / (double) n;
        double barWidth = Math.max(2, Math.min(slot * 0.6, 44));

        for (int i = 0; i < n; i++) {
            ProductStock p = data.get(i);
            double ratio = axisMax == 0 ? 0 : p.stock / (double) axisMax;
            int barHeight = (int) Math.round(ratio * height);
            double x = left + i * slot + (slot - barWidth) / 2.0;
            int y = top + height - barHeight;

            boolean hovered = i == hoverIndex;
            Color base = colorFor(p);
            g2.setColor(hovered ? base.darker() : base);

            int drawH = Math.max(barHeight, barHeight > 0 ? 2 : 0);
            RoundRectangle2D.Double bar = new RoundRectangle2D.Double(x, y, barWidth,
                    Math.max(drawH, 0), 4, 4);
            if (drawH > 0) {
                g2.fill(bar);
            } else {
                // SL ton = 0: van ve 1 vach mong o day truc de biet la "co san pham nay, dang het hang"
                g2.setColor(AppColor.TEXT_MUTED);
                g2.fillRoundRect((int) x, top + height - 2, (int) barWidth, 2, 2, 2);
            }
        }
    }

    /** Mau cot theo muc do canh bao: do (co lo het han) > cam (sap het han/duoi ton toi thieu) > xam (het hang) > xanh (binh thuong). */
    private Color colorFor(ProductStock p) {
        if (p.hasExpiredBatch) return AppColor.ERROR;
        if (p.nearestExpiry != null) {
            long days = ChronoUnit.DAYS.between(LocalDate.now(), p.nearestExpiry);
            if (days <= NEAR_EXPIRY_DAYS) return AppColor.WARNING;
        }
        if (p.stock == 0) return AppColor.TEXT_MUTED;
        if (p.stock <= p.minStock) return AppColor.WARNING;
        return AppColor.ACCENT;
    }

    private void drawXAxisLabels(Graphics2D g2, int left, int bottom, int width) {
        int n = data.size();
        double slot = width / (double) n;
        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();

        for (int i = 0; i < n; i++) {
            String name = data.get(i).productName;
            String label = truncate(name, fm, slot - 4);
            int tw = fm.stringWidth(label);
            double x = left + i * slot + slot / 2.0 - tw / 2.0;
            g2.drawString(label, (float) x, bottom + fm.getAscent() + 14);
        }
    }

    private static String truncate(String text, FontMetrics fm, double maxWidth) {
        if (text == null) return "";
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c + ellipsis) > maxWidth) break;
            sb.append(c);
        }
        return sb.length() == 0 ? ellipsis : sb + ellipsis;
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Lam tron 1 gia tri len thanh so "dep" (1/2/5 x 10^k) de chia luoi truc Y khong le. */
    private static long niceCeiling(long value) {
        if (value <= 0) return 1;
        long magnitude = 1;
        while (magnitude * 10 <= value) magnitude *= 10;
        long[] steps = {1, 2, 5, 10};
        for (long step : steps) {
            long candidate = step * magnitude;
            if (candidate >= value) return candidate;
        }
        return 10 * magnitude;
    }
}