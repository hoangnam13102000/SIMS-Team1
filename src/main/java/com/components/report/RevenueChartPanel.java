package com.components.report;

import com.dao.RevenueReportDAO.DailyPoint;
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
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Bieu do cot doanh thu theo ngay, tu ve bang Graphics2D (khong phu thuoc
 * JFreeChart hay thu vien chart nao khac - dong bo cach lam voi StatCard/
 * DashboardCard, tranh phai them dependency chi cho 1 bieu do don gian).
 * Di chuot vao 1 cot se highlight + hien tooltip ngay/doanh thu/so hoa don.
 */
public class RevenueChartPanel extends JComponent {

    private static final DateTimeFormatter AXIS_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int PAD_LEFT = 64;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 16;
    private static final int PAD_BOTTOM = 34;
    private static final int GRID_LINES = 4;

    private List<DailyPoint> data = Collections.emptyList();
    private int hoverIndex = -1;

    public RevenueChartPanel() {
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(10, 240));
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

    public void setData(List<DailyPoint> data) {
        this.data = data != null ? data : Collections.emptyList();
        this.hoverIndex = -1;
        revalidate();
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int idx = indexAt(event.getX());
        if (idx < 0 || idx >= data.size()) return null;
        DailyPoint p = data.get(idx);
        return "<html>" + p.date.format(AXIS_FORMAT)
                + "<br>Doanh thu: " + NumberUtil.formatThousands(p.revenue.longValue()) + " đ"
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

        long maxRevenue = 1;
        for (DailyPoint p : data) {
            maxRevenue = Math.max(maxRevenue, p.revenue.longValue());
        }
        // Lam tron truc Y len boi so "dep" de nhan luoi khong bi le.
        long axisMax = niceCeiling(maxRevenue);

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
        String msg = "Không có dữ liệu doanh thu trong khoảng thời gian này";
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
        double barWidth = Math.max(2, Math.min(slot * 0.6, 36));

        for (int i = 0; i < n; i++) {
            DailyPoint p = data.get(i);
            double ratio = axisMax == 0 ? 0 : p.revenue.doubleValue() / axisMax;
            int barHeight = (int) Math.round(ratio * height);
            double x = left + i * slot + (slot - barWidth) / 2.0;
            int y = top + height - barHeight;

            boolean hovered = i == hoverIndex;
            Color base = hovered ? AppColor.ACCENT_HOVER : AppColor.ACCENT;
            g2.setColor(barHeight > 0 ? base : AppColor.TABLE_GRID);

            int drawH = Math.max(barHeight, barHeight > 0 ? 2 : 0);
            RoundRectangle2D.Double bar = new RoundRectangle2D.Double(x, y, barWidth,
                    Math.max(drawH, 0), 4, 4);
            if (drawH > 0) {
                g2.fill(bar);
            }
        }
    }

    private void drawXAxisLabels(Graphics2D g2, int left, int bottom, int width) {
        int n = data.size();
        double slot = width / (double) n;
        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();

        // Chi ve nhan cho 1 so ngay (tranh chong chu khi khoang thoi gian dai) -
        // buoc nhay du de moi nhan cach nhau it nhat ~ 1 chieu rong chu.
        int labelStep = Math.max(1, (int) Math.ceil((fm.stringWidth("00/00") + 10) / slot));
        for (int i = 0; i < n; i += labelStep) {
            String label = data.get(i).date.format(AXIS_FORMAT);
            int tw = fm.stringWidth(label);
            double x = left + i * slot + slot / 2.0 - tw / 2.0;
            g2.drawString(label, (float) x, bottom + fm.getAscent() + 6);
        }
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