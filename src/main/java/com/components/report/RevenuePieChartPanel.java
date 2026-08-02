package com.components.report;

import com.theme.AppColor;
import com.theme.AppFont;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Bieu do tron dang donut, tu ve bang Graphics2D (dong bo cach lam voi
 * RevenueChartPanel - khong phu thuoc thu vien chart ngoai). Di chuot vao 1
 * mieng se phinh nhe ra + hien tooltip ten/gia tri/so luong/ty le. Component
 * ben ngoai (vd danh sach chu thich) co the dong bo highlight qua
 * {@link #setOnHoverChange(IntConsumer)} va {@link #setHoverIndex(int)}.
 */
public class RevenuePieChartPanel extends JComponent {

    /** 1 mieng cua bieu do trong. */
    public static class Slice {
        public final String label;
        public final double value;
        public final String valueText;
        public final String countText;
        public final Color color;

        public Slice(String label, double value, String valueText, String countText, Color color) {
            this.label = label;
            this.value = value;
            this.valueText = valueText;
            this.countText = countText;
            this.color = color;
        }
    }

    private static final double HOVER_GROW = 6;

    private List<Slice> slices = Collections.emptyList();
    private int hoverIndex = -1;
    private IntConsumer onHoverChange;
    private String centerTitle = "Tổng";
    private String centerValue = "0 đ";

    public RevenuePieChartPanel() {
        setOpaque(false);
        setPreferredSize(new java.awt.Dimension(180, 180));
        ToolTipManager.sharedInstance().registerComponent(this);

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int idx = sliceAt(e.getX(), e.getY());
                if (idx != hoverIndex) {
                    setHoverIndex(idx);
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseExited(MouseEvent e) {
                setHoverIndex(-1);
            }
        });
    }

    public void setData(List<Slice> slices, String centerTitle, String centerValue) {
        this.slices = slices != null ? slices : Collections.emptyList();
        this.centerTitle = centerTitle;
        this.centerValue = centerValue;
        this.hoverIndex = -1;
        revalidate();
        repaint();
    }

    public void setOnHoverChange(IntConsumer listener) {
        this.onHoverChange = listener;
    }

    /** Cho phep component ben ngoai (vd 1 hang trong danh sach chu thich) chu dong highlight 1 mieng. */
    public void setHoverIndex(int idx) {
        if (idx == hoverIndex) return;
        hoverIndex = idx;
        repaint();
        if (onHoverChange != null) onHoverChange.accept(hoverIndex);
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        int idx = sliceAt(event.getX(), event.getY());
        if (idx < 0 || idx >= slices.size()) return null;
        Slice s = slices.get(idx);
        double total = totalValue();
        double percent = total == 0 ? 0 : s.value / total * 100;
        return "<html><b>" + s.label + "</b>"
                + "<br>" + s.valueText
                + "<br>" + s.countText
                + "<br>" + String.format(java.util.Locale.US, "%.1f", percent) + "% tổng doanh thu</html>";
    }

    private double totalValue() {
        double total = 0;
        for (Slice s : slices) total += s.value;
        return total;
    }

    private double[] geometry() {
        int w = getWidth();
        int h = getHeight();
        double size = Math.min(w, h) - HOVER_GROW * 2 - 4;
        double cx = w / 2.0;
        double cy = h / 2.0;
        double outerR = size / 2.0;
        return new double[]{cx, cy, outerR};
    }

    private int sliceAt(int mx, int my) {
        if (slices.isEmpty()) return -1;
        double[] geo = geometry();
        double cx = geo[0], cy = geo[1], outerR = geo[2];
        double innerR = outerR * (1.0 - 1.0 / 1.9);

        double dx = mx - cx;
        double dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < innerR - 2 || dist > outerR + HOVER_GROW) return -1;

        // Goc do tu tam 12h, chieu kim dong ho (khop voi cach ve o duoi).
        double angleDeg = Math.toDegrees(Math.atan2(dy, dx)); // 0 = 3h, tang theo chieu kim dong ho
        double fromTop = angleDeg + 90;
        if (fromTop < 0) fromTop += 360;

        double total = totalValue();
        if (total <= 0) return -1;
        double acc = 0;
        for (int i = 0; i < slices.size(); i++) {
            double sweep = slices.get(i).value / total * 360.0;
            if (fromTop >= acc && fromTop < acc + sweep) return i;
            acc += sweep;
        }
        return -1;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        if (slices.isEmpty() || totalValue() <= 0) {
            drawEmptyState(g2, w, h);
            g2.dispose();
            return;
        }

        double[] geo = geometry();
        double cx = geo[0], cy = geo[1], outerR = geo[2];
        double innerR = outerR * (1.0 - 1.0 / 1.9);
        double total = totalValue();

        double startAngle = 90; // Java2D: 0deg = 3h, duong nguoc chieu kim dong ho -> bat dau tu 12h
        List<double[]> boundaries = new ArrayList<>(); // [start,sweep] theo tung slice, cung thu tu slices

        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            double sweep = -(s.value / total * 360.0); // am de ve theo chieu kim dong ho
            boundaries.add(new double[]{startAngle, sweep});
            startAngle += sweep;
        }

        for (int i = 0; i < slices.size(); i++) {
            Slice s = slices.get(i);
            double[] b = boundaries.get(i);
            boolean hovered = i == hoverIndex;

            double r = hovered ? outerR + HOVER_GROW : outerR;

            Shape outer = new Arc2D.Double(cx - r, cy - r, r * 2, r * 2, b[0], b[1], Arc2D.PIE);
            java.awt.geom.Area area = new java.awt.geom.Area(outer);
            Shape innerHole = new Ellipse2D.Double(cx - innerR, cy - innerR, innerR * 2, innerR * 2);
            area.subtract(new java.awt.geom.Area(innerHole));

            Color base = s.color;
            g2.setColor(hovered ? base.brighter() : base);
            g2.fill(area);

            g2.setColor(AppColor.WHITE);
            g2.setStroke(new java.awt.BasicStroke(2f));
            g2.draw(area);
        }

        drawCenterLabel(g2, cx, cy, innerR);

        g2.dispose();
    }

    private void drawCenterLabel(Graphics2D g2, double cx, double cy, double innerR) {
        if (innerR < 24) return;

        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        FontMetrics fmTitle = g2.getFontMetrics();
        String title = hoverIndex >= 0 && hoverIndex < slices.size() ? slices.get(hoverIndex).label : centerTitle;
        int titleMax = (int) (innerR * 1.7);
        title = clipToWidth(title, fmTitle, titleMax);
        int tw = fmTitle.stringWidth(title);
        g2.drawString(title, (float) (cx - tw / 2.0), (float) (cy - 4));

        String value = hoverIndex >= 0 && hoverIndex < slices.size() ? slices.get(hoverIndex).valueText : centerValue;
        g2.setFont(AppFont.bold(15));
        g2.setColor(AppColor.TEXT_TITLE);
        FontMetrics fmValue = g2.getFontMetrics();
        value = clipToWidth(value, fmValue, (int) (innerR * 1.8));
        int vw = fmValue.stringWidth(value);
        g2.drawString(value, (float) (cx - vw / 2.0), (float) (cy + fmValue.getAscent() - 2));
    }

    private static String clipToWidth(String text, FontMetrics fm, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) return text;
        String ellipsis = "…";
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (fm.stringWidth(sb.toString() + c + ellipsis) > maxWidth) break;
            sb.append(c);
        }
        return sb + ellipsis;
    }

    private void drawEmptyState(Graphics2D g2, int w, int h) {
        g2.setFont(AppFont.SMALL);
        g2.setColor(AppColor.TEXT_MUTED);
        String msg = "Không có dữ liệu";
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(msg);
        g2.drawString(msg, (w - tw) / 2, h / 2);
    }
}