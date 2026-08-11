package com.components.report;

import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Biểu đồ "Thu / Chi / Lợi nhuận ròng" theo ngày cho tab Lợi nhuận:
 * - Cột nhóm: "Thu" (doanh thu) và "Chi" (giá vốn + thiệt hại - hoàn trả NCC)
 * - Đường "Lợi nhuận ròng" (Thu - Chi)
 */
public class FinanceChartPanel extends JComponent {

    private static final DateTimeFormatter AXIS_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int PAD_LEFT = 64;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 20;
    private static final int PAD_BOTTOM = 34;
    private static final int GRID_LINES = 4;

    private List<DailyFinancePoint> data = Collections.emptyList();
    private int hoverIndex = -1;

    public FinanceChartPanel() {
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(10, 260));
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

    public void setData(List<DailyFinancePoint> data) {
        this.data = data != null ? data : Collections.emptyList();
        this.hoverIndex = -1;
        revalidate();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int idx = indexAt(event.getX());
        if (idx < 0 || idx >= data.size()) return null;
        DailyFinancePoint p = data.get(idx);
        boolean profitPositive = p.netProfit().signum() >= 0;
        return "<html>" + p.date.format(AXIS_FORMAT)
                + "<br>Thu (doanh thu): " + NumberUtil.formatThousands(p.revenue.longValue()) + " đ"
                + "<br>Chi (giá vốn): " + NumberUtil.formatThousands(p.cost.longValue()) + " đ"
                + "<br>Chi (thiệt hại): " + NumberUtil.formatThousands(p.disposalLoss.longValue()) + " đ"
                + "<br>Hoàn trả NCC: " + NumberUtil.formatThousands(p.supplierRefund.longValue()) + " đ"
                + "<br>Tổng chi (sau hoàn): " + NumberUtil.formatThousands(p.totalExpense().longValue()) + " đ"
                + "<br><b>Lợi nhuận ròng: <span style='color:" + (profitPositive ? "#16a34a" : "#dc2626") + "'>"
                + NumberUtil.formatThousands(p.netProfit().longValue()) + " đ</span></b>"
                + "<br>Hóa đơn: " + p.invoiceCount + "</html>";
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

        long maxValue = 1;
        for (DailyFinancePoint p : data) {
            maxValue = Math.max(maxValue, p.revenue.longValue());
            maxValue = Math.max(maxValue, Math.max(0, p.totalExpense().longValue()));
        }
        long axisMax = niceCeiling(maxValue);

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
        drawGroupedBars(g2, plotLeft, plotTop, plotWidth, plotHeight, axisMax);
        drawProfitLine(g2, plotLeft, plotTop, plotWidth, plotHeight, axisMax);
        drawXAxisLabels(g2, plotLeft, plotBottom, plotWidth);
        drawLegend(g2, plotLeft, w);

        g2.dispose();
    }

    private void drawEmptyState(Graphics2D g2, int w, int h) {
        g2.setFont(AppFont.BODY);
        g2.setColor(AppColor.TEXT_MUTED);
        String msg = "Không có dữ liệu trong khoảng thời gian này";
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

    private void drawGroupedBars(Graphics2D g2, int left, int top, int width, int height, long axisMax) {
        int n = data.size();
        double slot = width / (double) n;
        double groupWidth = Math.min(slot * 0.7, 42);
        double barWidth = Math.max(2, groupWidth / 2.0 - 1.5);

        Color revenueColor = AppColor.ACCENT;
        Color revenueHover = AppColor.ACCENT_HOVER != null ? AppColor.ACCENT_HOVER : revenueColor.brighter();
        Color expenseColor = AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 38, 38);
        Color expenseHover = expenseColor.brighter();

        for (int i = 0; i < n; i++) {
            DailyFinancePoint p = data.get(i);
            boolean hovered = i == hoverIndex;
            double groupX = left + i * slot + (slot - groupWidth) / 2.0;

            double revRatio = axisMax == 0 ? 0 : p.revenue.doubleValue() / axisMax;
            int revHeight = (int) Math.round(revRatio * height);
            g2.setColor(hovered ? revenueHover : revenueColor);
            drawBar(g2, groupX, top + height - revHeight, barWidth, revHeight);

            // Chi sau khi trừ hoàn NCC (không vẽ âm)
            double expVal = Math.max(0, p.totalExpense().doubleValue());
            double expRatio = axisMax == 0 ? 0 : expVal / axisMax;
            int expHeight = (int) Math.round(expRatio * height);
            g2.setColor(hovered ? expenseHover : expenseColor);
            drawBar(g2, groupX + barWidth + 3, top + height - expHeight, barWidth, expHeight);
        }
    }

    private void drawBar(Graphics2D g2, double x, int y, double width, int height) {
        if (height <= 0) return;
        RoundRectangle2D.Double bar = new RoundRectangle2D.Double(x, y, width, height, 3, 3);
        g2.fill(bar);
    }

    private void drawProfitLine(Graphics2D g2, int left, int top, int width, int height, long axisMax) {
        int n = data.size();
        if (n == 0 || axisMax == 0) return;
        double slot = width / (double) n;

        Color lineColor = AppColor.TEXT_PRIMARY != null ? AppColor.TEXT_PRIMARY : new Color(30, 41, 59);
        Color positiveColor = AppColor.SUCCESS != null ? AppColor.SUCCESS : new Color(22, 163, 74);
        Color negativeColor = AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 38, 38);

        Path2D.Double path = new Path2D.Double();
        double[] xs = new double[n];
        double[] ys = new double[n];
        for (int i = 0; i < n; i++) {
            BigDecimal profit = data.get(i).netProfit();
            double ratio = clamp(profit.doubleValue() / axisMax, 0, 1);
            xs[i] = left + i * slot + slot / 2.0;
            ys[i] = top + height - ratio * height;
        }

        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(lineColor);
        path.moveTo(xs[0], ys[0]);
        for (int i = 1; i < n; i++) {
            path.lineTo(xs[i], ys[i]);
        }
        g2.draw(path);

        for (int i = 0; i < n; i++) {
            boolean hovered = i == hoverIndex;
            boolean positive = data.get(i).netProfit().signum() >= 0;
            g2.setColor(positive ? positiveColor : negativeColor);
            double r = hovered ? 5 : 3.5;
            Ellipse2D.Double dot = new Ellipse2D.Double(xs[i] - r, ys[i] - r, r * 2, r * 2);
            g2.fill(dot);
            if (hovered) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(Color.WHITE);
                g2.draw(dot);
            }
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void drawXAxisLabels(Graphics2D g2, int left, int bottom, int width) {
        int n = data.size();
        double slot = width / (double) n;
        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();

        int labelStep = Math.max(1, (int) Math.ceil((fm.stringWidth("00/00") + 10) / slot));
        for (int i = 0; i < n; i += labelStep) {
            String label = data.get(i).date.format(AXIS_FORMAT);
            int tw = fm.stringWidth(label);
            double x = left + i * slot + slot / 2.0 - tw / 2.0;
            g2.drawString(label, (float) x, bottom + fm.getAscent() + 6);
        }
    }

    private void drawLegend(Graphics2D g2, int left, int w) {
        g2.setFont(AppFont.SMALL);
        FontMetrics fm = g2.getFontMetrics();
        int y = 12;
        int x = left;

        x = drawLegendItem(g2, x, y, fm, AppColor.ACCENT, "Thu (doanh thu)");
        x = drawLegendItem(g2, x, y, fm,
                AppColor.ERROR != null ? AppColor.ERROR : new Color(220, 38, 38),
                "Chi (sau hoàn NCC)");
        drawLegendLineItem(g2, x, y, fm, "Lợi nhuận ròng");
    }

    private int drawLegendItem(Graphics2D g2, int x, int y, FontMetrics fm, Color color, String label) {
        g2.setColor(color);
        g2.fillRoundRect(x, y - 8, 10, 10, 2, 2);
        g2.setColor(AppColor.TEXT_MUTED);
        g2.drawString(label, x + 16, y + fm.getAscent() / 2 - 2);
        return x + 16 + fm.stringWidth(label) + 18;
    }

    private void drawLegendLineItem(Graphics2D g2, int x, int y, FontMetrics fm, String label) {
        Stroke old = g2.getStroke();
        g2.setColor(AppColor.TEXT_PRIMARY != null ? AppColor.TEXT_PRIMARY : new Color(30, 41, 59));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.drawLine(x, y - 3, x + 14, y - 3);
        g2.fillOval(x + 5, y - 6, 6, 6);
        g2.setStroke(old);
        g2.setColor(AppColor.TEXT_MUTED);
        g2.drawString(label, x + 20, y + fm.getAscent() / 2 - 2);
    }

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