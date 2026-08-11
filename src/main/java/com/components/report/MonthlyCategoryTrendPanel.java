package com.components.report;

import com.dao.RevenueReportDAO.CategorySeries;
import com.dao.RevenueReportDAO.MonthlyCategoryTrend;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Bieu do duong (line chart) the hien SO LUONG san pham ban ra MOI THANG,
 * moi danh muc 1 duong rieng - tu ve bang Graphics2D, dong bo cach lam voi
 * {@link RevenueChartPanel} (khong phu thuoc thu vien chart ngoai).
 * Di chuot vao truc thang se hien tooltip so luong cua TAT CA danh muc dang
 * hien (xem {@link #setSeriesVisible(int, boolean)}) tai thang do.
 */
public class MonthlyCategoryTrendPanel extends JComponent {

    private static final int PAD_LEFT = 56;
    private static final int PAD_RIGHT = 16;
    private static final int PAD_TOP = 16;
    private static final int PAD_BOTTOM = 30;
    private static final int GRID_LINES = 4;

    /** Cung bang mau voi RevenueReportTab.paymentColor - dam bao 1 danh muc luon cung 1 mau giua cac bieu do. */
    private static final Color[] PALETTE = {
            AppColor.ACCENT, AppColor.INFO, AppColor.WARNING, AppColor.TEAL,
            AppColor.RED_ALT, AppColor.BLUE, AppColor.ORANGE, AppColor.YELLOW
    };

    private List<YearMonth> months = Collections.emptyList();
    private List<CategorySeries> series = Collections.emptyList();
    private boolean[] visible = new boolean[0];
    private int hoverMonthIndex = -1;

    public MonthlyCategoryTrendPanel() {
        setOpaque(false);
        // Chiều cao tối thiểu đủ để vẽ lưới + đường; tránh bị layout ép height ≈ 0.
        setPreferredSize(new java.awt.Dimension(10, 300));
        setMinimumSize(new java.awt.Dimension(100, 180));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = indexAt(e.getX());
                if (idx != hoverMonthIndex) {
                    hoverMonthIndex = idx;
                    repaint();
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                if (hoverMonthIndex != -1) {
                    hoverMonthIndex = -1;
                    repaint();
                }
            }
        });
    }

    public void setData(MonthlyCategoryTrend trend) {
        this.months = trend != null && trend.months != null ? trend.months : Collections.emptyList();
        this.series = trend != null && trend.series != null ? trend.series : Collections.emptyList();
        this.visible = new boolean[series.size()];
        Arrays.fill(visible, true);
        this.hoverMonthIndex = -1;
        revalidate();
        repaint();
    }

    /** Mau danh cho danh muc thu {@code index} (theo thu tu sap xep trong MonthlyCategoryTrend.series) - dung lai o hang chu thich (legend) ben ngoai. */
    public Color colorFor(int index) {
        return PALETTE[index % PALETTE.length];
    }

    /** An/hien 1 duong (bam vao chip chu thich) - khong lam mat du lieu, chi bo qua khi ve + tinh truc Y. */
    public void setSeriesVisible(int index, boolean isVisible) {
        if (index < 0 || index >= visible.length) return;
        if (visible[index] == isVisible) return;
        visible[index] = isVisible;
        repaint();
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int idx = indexAt(event.getX());
        if (idx < 0 || idx >= months.size()) return null;

        StringBuilder sb = new StringBuilder("<html>").append(monthLabel(months.get(idx), true));
        boolean any = false;
        for (int i = 0; i < series.size(); i++) {
            if (!visible[i]) continue;
            CategorySeries s = series.get(i);
            Color c = colorFor(i);
            sb.append("<br><span style='color:rgb(")
                    .append(c.getRed()).append(',').append(c.getGreen()).append(',').append(c.getBlue())
                    .append(")'>\u25CF</span> ")
                    .append(s.categoryName).append(": ")
                    .append(NumberUtil.formatThousands(s.quantityByMonth.get(idx)));
            any = true;
        }
        if (!any) sb.append("<br>Không có danh mục nào đang hiển thị");
        return sb.append("</html>").toString();
    }

    private int indexAt(int mouseX) {
        if (months.isEmpty()) return -1;
        int plotWidth = getWidth() - PAD_LEFT - PAD_RIGHT;
        if (plotWidth <= 0) return -1;
        if (months.size() == 1) return 0;
        double slot = plotWidth / (double) (months.size() - 1);
        int idx = (int) Math.round((mouseX - PAD_LEFT) / slot);
        return (idx >= 0 && idx < months.size()) ? idx : -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (months.isEmpty() || series.isEmpty()) {
            drawEmptyState(g2, w, h);
            g2.dispose();
            return;
        }

        long maxQty = 1;
        boolean anyVisible = false;
        for (int i = 0; i < series.size(); i++) {
            if (!visible[i]) continue;
            anyVisible = true;
            for (long q : series.get(i).quantityByMonth) maxQty = Math.max(maxQty, q);
        }
        long axisMax = niceCeiling(maxQty);

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
        drawHoverLine(g2, plotLeft, plotTop, plotWidth, plotHeight);

        if (anyVisible) {
            for (int i = 0; i < series.size(); i++) {
                if (visible[i]) {
                    drawSeries(g2, series.get(i), colorFor(i), plotLeft, plotTop, plotWidth, plotHeight, axisMax);
                }
            }
        }
        drawXAxisLabels(g2, plotLeft, plotBottom, plotWidth);

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

    private void drawHoverLine(Graphics2D g2, int left, int top, int width, int height) {
        if (hoverMonthIndex < 0 || months.size() < 2) return;
        double slot = width / (double) (months.size() - 1);
        double x = left + hoverMonthIndex * slot;
        g2.setColor(AppColor.TABLE_GRID);
        g2.drawLine((int) x, top, (int) x, top + height);
    }

    private void drawSeries(Graphics2D g2, CategorySeries s, Color color, int left, int top, int width, int height, long axisMax) {
        int n = months.size();
        double slot = n > 1 ? width / (double) (n - 1) : 0;

        Path2D.Double path = new Path2D.Double();
        for (int i = 0; i < n; i++) {
            double ratio = axisMax == 0 ? 0 : s.quantityByMonth.get(i) / (double) axisMax;
            double x = n > 1 ? left + i * slot : left + width / 2.0;
            double y = top + height - ratio * height;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(color);
        g2.draw(path);

        for (int i = 0; i < n; i++) {
            double ratio = axisMax == 0 ? 0 : s.quantityByMonth.get(i) / (double) axisMax;
            double x = n > 1 ? left + i * slot : left + width / 2.0;
            double y = top + height - ratio * height;

            boolean hovered = i == hoverMonthIndex;
            double r = hovered ? 5 : 3;
            g2.setColor(color);
            g2.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            if (hovered) {
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(AppColor.WHITE);
                g2.draw(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
            }
        }
    }

    private void drawXAxisLabels(Graphics2D g2, int left, int bottom, int width) {
        int n = months.size();
        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        FontMetrics fm = g2.getFontMetrics();
        double slot = n > 1 ? width / (double) (n - 1) : 0;

        // Chi ve nhan cho 1 so thang (tranh chong chu khi khoang thoi gian dai).
        int labelStep = 1;
        if (n > 1) {
            labelStep = Math.max(1, (int) Math.ceil((fm.stringWidth("00/0000") + 14) / slot));
        }
        for (int i = 0; i < n; i += labelStep) {
            String label = monthLabel(months.get(i), false);
            int tw = fm.stringWidth(label);
            double x = (n > 1 ? left + i * slot : left + width / 2.0) - tw / 2.0;
            g2.drawString(label, (float) x, bottom + fm.getAscent() + 6);
        }
    }

    private static String monthLabel(YearMonth ym, boolean full) {
        return full ? "Tháng " + ym.getMonthValue() + "/" + ym.getYear()
                : String.format("%02d/%d", ym.getMonthValue(), ym.getYear());
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