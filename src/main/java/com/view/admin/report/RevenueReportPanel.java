package com.view.admin.report;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.report.RevenueChartPanel;
import com.dao.RevenueReportDAO;
import com.dao.RevenueReportDAO.DailyPoint;
import com.dao.RevenueReportDAO.PaymentSlice;
import com.dao.RevenueReportDAO.Summary;
import com.dao.RevenueReportDAO.TopProduct;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import com.utils.FileUtil;
import com.utils.NumberUtil;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Trang "Bao cao doanh thu": chon khoang thoi gian (co san preset nhanh),
 * xem tong quan + bieu do doanh thu theo ngay + doanh thu theo phuong thuc
 * thanh toan + top san pham ban chay, va xuat du lieu ra CSV/Excel.
 * <p>
 * Khong extends BaseCrudPanel vi day khong phai man hinh CRUD 1 danh sach -
 * layout rieng gom nhieu khoi thong ke, giong DashboardPanel nhung voi du
 * lieu that + bo loc thoi gian.
 */
public class RevenueReportPanel extends JPanel {

    private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RevenueReportDAO dao = new RevenueReportDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải báo cáo...");

    private DatePickerField fromField;
    private DatePickerField toField;

    private StatCard revenueCard;
    private StatCard invoiceCard;
    private StatCard avgCard;
    private StatCard itemsCard;

    private RevenueChartPanel chartPanel;
    private JPanel paymentListPanel;
    private JPanel topProductsListPanel;

    private List<DailyPoint> lastDailyRevenue = new ArrayList<>();

    public RevenueReportPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setOpaque(false);
        topSection.add(buildHeader());
        topSection.add(Box.createVerticalStrut(AppSpacing.MD));
        topSection.add(buildFilterBar());

        JPanel dynamicContent = buildDynamicContent();
        JScrollPane scroll = new JScrollPane(dynamicContent);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(topSection, BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        LocalDate today = LocalDate.now();
        fromField = new DatePickerField(today.minusDays(29));
        toField = new DatePickerField(today);
        fromField.onChange(v -> loadData());
        toField.onChange(v -> loadData());
        // fromField/toField duoc them vao filter bar o buildFilterBar() ben tren
        // (goi truoc khi field duoc khoi tao) - nen gan lai o day roi revalidate.
        wireDatePickersIntoFilterBar();

        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    // ---------------------------------------------------------------
    // Header + filter bar
    // ---------------------------------------------------------------

    private SectionHeader buildHeader() {
        SectionHeader header = new SectionHeader(FontAwesomeSolid.CHART_LINE, AppColor.ACCENT,
                "Báo cáo doanh thu", "Thống kê doanh thu bán hàng theo thời gian, phương thức thanh toán và sản phẩm");
        header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportReport("csv"));
        header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportReport("xlsx"));
        return header;
    }

    private JComponent filterBarDateSlot;

    private JPanel buildFilterBar() {
        RoundedPanel bar = new RoundedPanel();
        bar.setLayout(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, AppSpacing.SM));
        bar.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        bar.add(presetButton("Hôm nay", () -> applyRange(LocalDate.now(), LocalDate.now())));
        bar.add(presetButton("7 ngày qua", () -> applyRange(LocalDate.now().minusDays(6), LocalDate.now())));
        bar.add(presetButton("Tháng này", () -> applyRange(LocalDate.now().withDayOfMonth(1), LocalDate.now())));
        bar.add(presetButton("Quý này", () -> applyRange(startOfQuarter(LocalDate.now()), LocalDate.now())));
        bar.add(presetButton("Năm nay", () -> applyRange(LocalDate.now().withDayOfYear(1), LocalDate.now())));

        JSeparator sep = new JSeparator(SwingConstants.VERTICAL);
        sep.setPreferredSize(new Dimension(1, 26));
        sep.setForeground(AppColor.BORDER);
        bar.add(sep);

        JLabel fromLabel = new JLabel("Từ ngày");
        fromLabel.setFont(AppFont.SMALL);
        fromLabel.setForeground(AppColor.TEXT_MUTED);
        bar.add(fromLabel);

        filterBarDateSlot = bar; // placeholder ref de them fromField/toField sau khi khoi tao
        JLabel toLabel = new JLabel("Đến ngày");
        toLabel.setFont(AppFont.SMALL);
        toLabel.setForeground(AppColor.TEXT_MUTED);

        bar.putClientProperty("toLabel", toLabel);

        return bar;
    }

    /** DatePickerField duoc tao SAU buildFilterBar() (can LocalDate.now() 1 lan duy nhat o constructor) nen gan vao day. */
    private void wireDatePickersIntoFilterBar() {
        JPanel bar = (JPanel) filterBarDateSlot;
        bar.add(fromField);
        JLabel toLabel = (JLabel) bar.getClientProperty("toLabel");
        bar.add(toLabel);
        bar.add(toField);

        JButton refreshBtn = iconButton(FontAwesomeSolid.SYNC_ALT, "Làm mới");
        refreshBtn.addActionListener(e -> loadData());
        bar.add(refreshBtn);

        bar.revalidate();
        bar.repaint();
    }

    private void applyRange(LocalDate from, LocalDate to) {
        fromField.setValue(from);
        toField.setValue(to);
        loadData();
    }

    private static LocalDate startOfQuarter(LocalDate date) {
        int quarterMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
        return LocalDate.of(date.getYear(), quarterMonth, 1);
    }

    // ---------------------------------------------------------------
    // Noi dung dong (stat cards + chart + payment method + top products)
    // ---------------------------------------------------------------

    private JPanel buildDynamicContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildChartCard());
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(buildBottomRow());

        return content;
    }

    private JPanel buildStatsRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        revenueCard = new StatCard("Tổng doanh thu", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        invoiceCard = new StatCard("Số hóa đơn", "0", FontAwesomeSolid.RECEIPT, AppColor.INFO);
        avgCard = new StatCard("Giá trị TB/hóa đơn", "0 đ", FontAwesomeSolid.PERCENTAGE, AppColor.WARNING);
        itemsCard = new StatCard("Sản phẩm đã bán", "0", FontAwesomeSolid.BOXES, AppColor.TEAL);

        row.add(revenueCard);
        row.add(invoiceCard);
        row.add(avgCard);
        row.add(itemsCard);
        return row;
    }

    private DashboardCard buildChartCard() {
        DashboardCard card = new DashboardCard("Doanh thu theo ngày",
                "Chỉ tính hóa đơn hợp lệ (không bao gồm hóa đơn đã hủy)",
                FontAwesomeSolid.CHART_BAR, AppColor.ACCENT);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setPreferredSize(new Dimension(10, 300));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));

        chartPanel = new RevenueChartPanel();
        card.getContentPanel().add(chartPanel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildBottomRow() {
        JPanel row = new JPanel(new GridLayout(1, 2, AppSpacing.LG, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 320));
        row.setPreferredSize(new Dimension(10, 320));

        row.add(buildPaymentMethodCard());
        row.add(buildTopProductsCard());
        return row;
    }

    private DashboardCard buildPaymentMethodCard() {
        DashboardCard card = new DashboardCard("Doanh thu theo phương thức thanh toán",
                FontAwesomeSolid.CREDIT_CARD, AppColor.INFO);

        paymentListPanel = new JPanel();
        paymentListPanel.setLayout(new BoxLayout(paymentListPanel, BoxLayout.Y_AXIS));
        paymentListPanel.setOpaque(false);

        JScrollPane scroll = plainScroll(paymentListPanel);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTopProductsCard() {
        DashboardCard card = new DashboardCard("Top sản phẩm bán chạy",
                FontAwesomeSolid.TROPHY, AppColor.WARNING);

        topProductsListPanel = new JPanel();
        topProductsListPanel.setLayout(new BoxLayout(topProductsListPanel, BoxLayout.Y_AXIS));
        topProductsListPanel.setOpaque(false);

        JScrollPane scroll = plainScroll(topProductsListPanel);
        card.getContentPanel().add(scroll, BorderLayout.CENTER);
        return card;
    }

    private static JScrollPane plainScroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ---------------------------------------------------------------
    // Tai du lieu
    // ---------------------------------------------------------------

    private static class ReportData {
        Summary summary;
        Summary previousSummary;
        List<DailyPoint> daily;
        List<PaymentSlice> payments;
        List<TopProduct> topProducts;
    }

    private void loadData() {
        LocalDate from = fromField.getValue();
        LocalDate to = toField.getValue();
        if (from == null || to == null) return;
        if (from.isAfter(to)) {
            BaseDialog.error(this, "Khoảng thời gian không hợp lệ", "\"Từ ngày\" phải trước hoặc bằng \"Đến ngày\".");
            return;
        }

        loadingOverlay.start("Đang tải báo cáo...");
        SwingWorker<ReportData, Void> worker = new SwingWorker<ReportData, Void>() {
            @Override
            protected ReportData doInBackground() {
                long days = ChronoUnit.DAYS.between(from, to) + 1;
                LocalDate prevTo = from.minusDays(1);
                LocalDate prevFrom = prevTo.minusDays(days - 1);

                ReportData data = new ReportData();
                data.summary = dao.getSummary(from, to);
                data.previousSummary = dao.getSummary(prevFrom, prevTo);
                data.daily = dao.getDailyRevenue(from, to);
                data.payments = dao.getRevenueByPaymentMethod(from, to);
                data.topProducts = dao.getTopProducts(from, to, 10);
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    ReportData data = get();
                    applyData(data);
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Không thể tải báo cáo doanh thu: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyData(ReportData data) {
        lastDailyRevenue = data.daily;

        revenueCard.setValue(NumberUtil.formatThousands(data.summary.totalRevenue.longValue()) + " đ");
        Double growth = data.summary.growthPercent(data.previousSummary);
        if (growth == null) {
            revenueCard.setSubtitle("Không có dữ liệu kỳ trước để so sánh");
        } else {
            String sign = growth >= 0 ? "+" : "";
            revenueCard.setTrend(sign + NumberUtil.formatDecimal(growth, 1) + "% so với kỳ trước", growth >= 0);
        }

        invoiceCard.setValue(NumberUtil.formatThousands(data.summary.invoiceCount));
        invoiceCard.setSubtitle("Hóa đơn hợp lệ (không tính hóa đơn đã hủy)");

        avgCard.setValue(NumberUtil.formatThousands(data.summary.avgOrderValue().longValue()) + " đ");
        avgCard.setSubtitle("Doanh thu ÷ số hóa đơn");

        itemsCard.setValue(NumberUtil.formatThousands(data.summary.itemsSold));
        itemsCard.setSubtitle("Tổng số lượng sản phẩm đã bán");

        chartPanel.setData(data.daily);

        renderPaymentMethods(data.payments);
        renderTopProducts(data.topProducts);
    }

    private void renderPaymentMethods(List<PaymentSlice> slices) {
        paymentListPanel.removeAll();
        if (slices.isEmpty()) {
            paymentListPanel.add(emptyRow("Không có dữ liệu trong khoảng thời gian này"));
        } else {
            BigDecimal total = BigDecimal.ZERO;
            for (PaymentSlice s : slices) total = total.add(s.revenue);

            for (PaymentSlice s : slices) {
                double ratio = total.signum() == 0 ? 0 : s.revenue.doubleValue() / total.doubleValue();
                paymentListPanel.add(buildPaymentRow(paymentMethodLabel(s.method), s.revenue, s.invoiceCount, ratio));
                paymentListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        paymentListPanel.revalidate();
        paymentListPanel.repaint();
    }

    private void renderTopProducts(List<TopProduct> products) {
        topProductsListPanel.removeAll();
        if (products.isEmpty()) {
            topProductsListPanel.add(emptyRow("Không có sản phẩm nào được bán trong khoảng thời gian này"));
        } else {
            int rank = 1;
            for (TopProduct p : products) {
                topProductsListPanel.add(buildTopProductRow(rank++, p));
                topProductsListPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }
        topProductsListPanel.revalidate();
        topProductsListPanel.repaint();
    }

    private JComponent emptyRow(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setBorder(new EmptyBorder(AppSpacing.MD, 0, AppSpacing.MD, 0));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel buildPaymentRow(String label, BigDecimal revenue, int invoiceCount, double ratio) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));

        JPanel textRow = new JPanel(new BorderLayout());
        textRow.setOpaque(false);
        textRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel nameLabel = new JLabel(label);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JLabel valueLabel = new JLabel(NumberUtil.formatThousands(revenue.longValue()) + " đ  ·  " + invoiceCount + " hóa đơn");
        valueLabel.setFont(AppFont.SMALL);
        valueLabel.setForeground(AppColor.TEXT_MUTED);

        textRow.add(nameLabel, BorderLayout.WEST);
        textRow.add(valueLabel, BorderLayout.EAST);

        RatioBar bar = new RatioBar(ratio, AppColor.ACCENT);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        bar.setPreferredSize(new Dimension(10, 8));

        row.add(textRow);
        row.add(Box.createVerticalStrut(4));
        row.add(bar);
        return row;
    }

    private JPanel buildTopProductRow(int rank, TopProduct product) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel rankLabel = new JLabel(String.valueOf(rank), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(rankColor(rank));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rankLabel.setForeground(Color.WHITE);
        rankLabel.setFont(AppFont.SMALL_BOLD);
        rankLabel.setPreferredSize(new Dimension(22, 22));

        JLabel nameLabel = new JLabel(product.productName);
        nameLabel.setFont(AppFont.BODY);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));

        JLabel revenueLabel = new JLabel(NumberUtil.formatThousands(product.revenue.longValue()) + " đ");
        revenueLabel.setFont(AppFont.SMALL_BOLD);
        revenueLabel.setForeground(AppColor.TEXT_PRIMARY);
        revenueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        revenueLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JLabel qtyLabel = new JLabel("Đã bán: " + NumberUtil.formatThousands(product.quantity));
        qtyLabel.setFont(AppFont.FOOTER);
        qtyLabel.setForeground(AppColor.TEXT_MUTED);
        qtyLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        qtyLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);

        right.add(revenueLabel);
        right.add(qtyLabel);

        JPanel left = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        left.setOpaque(false);
        left.add(rankLabel, BorderLayout.WEST);
        left.add(nameLabel, BorderLayout.CENTER);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static Color rankColor(int rank) {
        switch (rank) {
            case 1: return new Color(234, 179, 8);   // vang
            case 2: return new Color(148, 163, 184); // bac
            case 3: return new Color(180, 83, 9);    // dong
            default: return AppColor.ACCENT;
        }
    }

    private static String paymentMethodLabel(String method) {
        if (method == null) return "Khác";
        switch (method) {
            case "CASH": return "Tiền mặt";
            case "BANK_TRANSFER": return "Chuyển khoản";
            case "PAYPAL": return "PayPal";
            case "CARD": return "Thẻ";
            default: return method;
        }
    }

    // ---------------------------------------------------------------
    // Xuat CSV / Excel (doanh thu theo ngay trong khoang dang xem)
    // ---------------------------------------------------------------

    private void exportReport(String format) {
        if (lastDailyRevenue.isEmpty()) {
            BaseDialog.info(this, "Không có dữ liệu", "Chưa có dữ liệu doanh thu để xuất trong khoảng thời gian đang chọn.");
            return;
        }

        String defaultName = "bao_cao_doanh_thu_" + timestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultName);
        if (chosen == null) return;
        File file = ensureExtension(chosen, format);

        String[] headers = {"Ngày", "Số hóa đơn", "Doanh thu"};
        List<Object[]> rows = new ArrayList<>();
        for (DailyPoint p : lastDailyRevenue) {
            rows.add(new Object[]{p.date.format(FILE_DATE_FORMAT), p.invoiceCount, p.revenue.longValue()});
        }

        loadingOverlay.start("Đang xuất dữ liệu...");
        SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                if ("csv".equals(format)) {
                    TableExportUtil.exportCsv(file, headers, rows);
                } else {
                    TableExportUtil.exportExcel(file, "Doanh thu", headers, rows);
                }
                return null;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    get();
                    BaseDialog.success(RevenueReportPanel.this, "Thành công",
                            "Đã xuất báo cáo vào file \"" + file.getName() + "\"");
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(RevenueReportPanel.this, "Lỗi", "Xuất file thất bại: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
    }

    private static File ensureExtension(File file, String ext) {
        String name = file.getName();
        if (name.toLowerCase().endsWith("." + ext)) return file;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        return new File(file.getParentFile(), base + "." + ext);
    }

    // ---------------------------------------------------------------
    // Component phu tro rieng cho trang nay
    // ---------------------------------------------------------------

    /** Nut "chip" bo tron cho cac preset khoang thoi gian (Hom nay/7 ngay qua...). */
    private JButton presetButton(String text, Runnable onClick) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.LARGE, AppRadius.LARGE);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setFont(AppFont.SMALL_BOLD);
        button.setForeground(AppColor.TEXT_SECONDARY);
        button.setBackground(AppColor.BG_LIGHTER);
        button.setBorder(new EmptyBorder(6, 14, 6, 14));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(AppColor.ACCENT_SOFT); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(AppColor.BG_LIGHTER); }
        });
        button.addActionListener(e -> onClick.run());
        return button;
    }

    private JButton iconButton(FontAwesomeSolid icon, String tooltip) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(AppColor.TEXT_SECONDARY);
        JButton button = new JButton(fontIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppRadius.MEDIUM, AppRadius.MEDIUM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setBackground(AppColor.BG_LIGHTER);
        button.setBorder(new EmptyBorder(8, 10, 8, 10));
        button.setFocusPainted(false);
        button.setContentAreaFilled(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setToolTipText(tooltip);
        button.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { button.setBackground(AppColor.ACCENT_SOFT); }
            @Override public void mouseExited(MouseEvent e) { button.setBackground(AppColor.BG_LIGHTER); }
        });
        return button;
    }

    /** Panel nen trang, bo goc - dung lam khung cho filter bar. */
    private static class RoundedPanel extends JPanel {
        RoundedPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, AppRadius.MEDIUM, AppRadius.MEDIUM);
            g2.setColor(AppColor.WHITE);
            g2.fill(shape);
            g2.setColor(AppColor.BORDER);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Thanh ty le ngang (0..1), dung cho danh sach doanh thu theo phuong thuc thanh toan. */
    private static class RatioBar extends JPanel {
        private final double ratio;
        private final Color color;

        RatioBar(double ratio, Color color) {
            this.ratio = Math.max(0, Math.min(1, ratio));
            this.color = color;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int h = getHeight();
            g2.setColor(AppColor.TABLE_GRID);
            g2.fillRoundRect(0, 0, getWidth(), h, h, h);
            int fillW = (int) Math.round(getWidth() * ratio);
            if (fillW > 0) {
                g2.setColor(color);
                g2.fillRoundRect(0, 0, fillW, h, h, h);
            }
            g2.dispose();
        }
    }
}