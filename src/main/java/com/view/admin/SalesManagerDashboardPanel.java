package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatBadge;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.report.FinanceChartPanel;
import com.dao.DashboardDAO;
import com.dao.DashboardDAO.PendingExceptionItem;
import com.dao.DashboardDAO.PendingReturnItem;
import com.dao.RevenueReportDAO;
import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.dao.RevenueReportDAO.ProfitSummary;
import com.dao.RevenueReportDAO.Summary;
import com.dao.RevenueReportDAO.TopProduct;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard riêng cho <b>Quản lý bán hàng</b> ({@code Role.SALES_MANAGER}).
 * <p>
 * Tập trung nghiệp vụ báo cáo &amp; phê duyệt:
 * <ul>
 *   <li>KPI doanh thu / hóa đơn hôm nay &amp; tháng</li>
 *   <li>Lợi nhuận ròng tháng, AOV</li>
 *   <li>Hàng đợi duyệt đổi/trả &amp; báo cáo ngoại lệ</li>
 *   <li>Biểu đồ thu–chi–lợi nhuận 7 ngày, top sản phẩm bán chạy</li>
 * </ul>
 * Layout tái sử dụng {@link SectionHeader}, {@link StatCard}, {@link DashboardCard},
 * {@link FinanceChartPanel} — đồng bộ với {@link DashboardPanel} / {@link com.view.admin.inventory.InventoryOverviewPanel}.
 */
public class SalesManagerDashboardPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final DashboardDAO dashboardDao = new DashboardDAO();
    private final RevenueReportDAO revenueDao = new RevenueReportDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải báo cáo...");

    private StatCard todayRevenueCard;
    private StatCard todayInvoiceCard;
    private StatCard monthRevenueCard;
    private StatCard monthProfitCard;
    private StatCard pendingReturnCard;
    private StatCard pendingExceptionCard;

    private FinanceChartPanel weeklyChartPanel;
    private JPanel topProductListPanel;
    private JPanel pendingReturnListPanel;
    private JPanel pendingExceptionListPanel;

    public SalesManagerDashboardPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        JPanel content = buildDynamicContent();
        JScrollPane scroll = new JScrollPane(content);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        add(buildHeader(), BorderLayout.NORTH);
        add(LoadingOverlay.attach(scroll, loadingOverlay), BorderLayout.CENTER);

        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private SectionHeader buildHeader() {
        var currentUser = AuthService.getInstance().getCurrentUser();
        String name = currentUser != null ? currentUser.getFullName() : null;
        String subtitle = (name == null || name.isBlank())
                ? "Theo dõi doanh thu, phê duyệt đổi/trả và xử lý báo cáo ngoại lệ"
                : "Chào " + name + " — theo dõi doanh thu, phê duyệt đổi/trả và báo cáo ngoại lệ";
        return new SectionHeader(FontAwesomeSolid.CHART_LINE, AppColor.ACCENT,
                "Tổng quan bán hàng", subtitle);
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private JPanel buildDynamicContent() {
        JPanel content = new ScrollableColumn();

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        // Biểu đồ 7 ngày + Top SP bán chạy
        content.add(buildTwoColumnRow(buildRevenueChartCard(), 2.0, buildTopProductsCard(), 1.0, 340));
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        // Hàng đợi cần xử lý: đổi/trả + ngoại lệ
        content.add(buildTwoColumnRow(buildPendingReturnsCard(), 1.2, buildPendingExceptionsCard(), 1.0, 320));

        return content;
    }

    private JPanel buildStatsRow() {
        todayRevenueCard = new StatCard("Doanh thu hôm nay", "0 đ",
                FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        todayInvoiceCard = new StatCard("Hóa đơn hôm nay", "0",
                FontAwesomeSolid.RECEIPT, AppColor.INFO);
        monthRevenueCard = new StatCard("Doanh thu tháng", "0 đ",
                FontAwesomeSolid.CALENDAR_ALT, AppColor.TEAL);
        monthProfitCard = new StatCard("Lợi nhuận ròng tháng", "0 đ",
                FontAwesomeSolid.CHART_LINE, AppColor.SUCCESS);
        pendingReturnCard = new StatCard("Đổi/trả chờ duyệt", "0",
                FontAwesomeSolid.EXCHANGE_ALT, AppColor.WARNING);
        pendingExceptionCard = new StatCard("Báo cáo ngoại lệ", "0",
                FontAwesomeSolid.EXCLAMATION_CIRCLE, AppColor.ERROR);

        StatCard[] cards = {
                todayRevenueCard, todayInvoiceCard, monthRevenueCard,
                monthProfitCard, pendingReturnCard, pendingExceptionCard
        };

        JPanel row = new JPanel() {
            private int lastCols = -1;

            {
                setOpaque(false);
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setLayout(new GridLayout(2, 3, AppSpacing.MD, AppSpacing.MD));
                for (StatCard c : cards) add(c);
                addComponentListener(new java.awt.event.ComponentAdapter() {
                    @Override
                    public void componentResized(java.awt.event.ComponentEvent e) {
                        relayout();
                    }
                });
            }

            private void relayout() {
                int w = getWidth();
                if (w <= 0) return;
                int cols = (w >= 1200) ? 6 : (w >= 900) ? 3 : (w >= 560) ? 2 : 1;
                if (cols == lastCols) return;
                lastCols = cols;
                int rows = (int) Math.ceil((double) cards.length / cols);
                setLayout(new GridLayout(rows, cols, AppSpacing.MD, AppSpacing.MD));
                removeAll();
                for (StatCard c : cards) add(c);
                int totalH = rows * StatCard.PREFERRED_HEIGHT + Math.max(0, rows - 1) * AppSpacing.MD;
                setPreferredSize(new Dimension(10, totalH));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH + 4));
                revalidate();
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (lastCols <= 0) {
                    return new Dimension(d.width, StatCard.PREFERRED_HEIGHT * 2 + AppSpacing.MD);
                }
                return d;
            }
        };
        return row;
    }

    private JPanel buildTwoColumnRow(JComponent left, double leftWeight,
                                    JComponent right, double rightWeight, int height) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        row.setPreferredSize(new Dimension(10, height));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.weighty = 1.0;

        gbc.gridx = 0;
        gbc.weightx = leftWeight;
        gbc.insets = new Insets(0, 0, 0, AppSpacing.LG);
        row.add(left, gbc);

        gbc.gridx = 1;
        gbc.weightx = rightWeight;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(right, gbc);
        return row;
    }

    private DashboardCard buildRevenueChartCard() {
        DashboardCard card = new DashboardCard(
                "Thu / Chi / Lợi nhuận 7 ngày",
                "Thu = doanh thu · Chi = giá vốn + thiệt hại · Lợi nhuận ròng = Thu − Chi",
                FontAwesomeSolid.CHART_BAR, AppColor.ACCENT);
        weeklyChartPanel = new FinanceChartPanel();
        card.getContentPanel().add(weeklyChartPanel, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildTopProductsCard() {
        DashboardCard card = new DashboardCard(
                "Top sản phẩm tuần này",
                "Xếp theo doanh thu 7 ngày gần nhất",
                FontAwesomeSolid.TROPHY, AppColor.WARNING);
        topProductListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(topProductListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildPendingReturnsCard() {
        DashboardCard card = new DashboardCard(
                "Đổi / trả chờ duyệt",
                "Cần phê duyệt trước khi cập nhật kho",
                FontAwesomeSolid.EXCHANGE_ALT, AppColor.WARNING);
        pendingReturnListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(pendingReturnListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildPendingExceptionsCard() {
        DashboardCard card = new DashboardCard(
                "Báo cáo ngoại lệ chờ xử lý",
                "Nhân viên bán hàng gửi lên — cần phản hồi",
                FontAwesomeSolid.EXCLAMATION_CIRCLE, AppColor.ERROR);
        pendingExceptionListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(pendingExceptionListPanel), BorderLayout.CENTER);
        return card;
    }

    private static JScrollPane plainScroll(JComponent view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static class ScrollableColumn extends JPanel implements Scrollable {
        ScrollableColumn() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(false);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) { return 120; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    // ---------------------------------------------------------------
    // Data load
    // ---------------------------------------------------------------

    private static class SalesDashData {
        Summary today;
        Summary month;
        ProfitSummary monthProfit;
        List<DailyFinancePoint> weeklyFinance;
        List<TopProduct> topProducts;
        int pendingReturns;
        int pendingExceptions;
        List<PendingReturnItem> pendingReturnItems;
        List<PendingExceptionItem> pendingExceptionItems;
        DashboardDAO.Overview overview;
    }

    private void loadData() {
        loadingOverlay.start("Đang tải báo cáo bán hàng...");
        SwingWorker<SalesDashData, Void> worker = new SwingWorker<>() {
            @Override
            protected SalesDashData doInBackground() {
                LocalDate today = LocalDate.now();
                LocalDate monthStart = today.withDayOfMonth(1);
                LocalDate weekStart = today.minusDays(6);

                SalesDashData data = new SalesDashData();
                data.today = revenueDao.getSummary(today, today);
                data.month = revenueDao.getSummary(monthStart, today);
                data.monthProfit = revenueDao.getProfitSummary(monthStart, today);
                data.weeklyFinance = revenueDao.getDailyFinance(weekStart, today);
                data.topProducts = revenueDao.getTopProducts(weekStart, today, 8);
                data.pendingReturns = dashboardDao.countPendingReturnExchanges();
                data.pendingExceptions = dashboardDao.countPendingExceptionReports();
                data.pendingReturnItems = dashboardDao.getPendingReturnExchanges(8);
                data.pendingExceptionItems = dashboardDao.getPendingExceptionReports(8);
                data.overview = dashboardDao.getOverview();
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    applyData(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(SalesManagerDashboardPanel.this, "Lỗi",
                            "Không thể tải dashboard bán hàng: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyData(SalesDashData data) {
        // --- KPI hôm nay ---
        todayRevenueCard.setValue(NumberUtil.formatThousands(data.today.totalRevenue.longValue()) + " đ");
        todayRevenueCard.setSubtitle("Tính đến thời điểm hiện tại");

        todayInvoiceCard.setValue(NumberUtil.formatThousands(data.today.invoiceCount));
        BigDecimal aov = data.today.avgOrderValue();
        todayInvoiceCard.setSubtitle(aov != null
                ? "AOV " + NumberUtil.formatThousands(aov.longValue()) + " đ · "
                + NumberUtil.formatThousands(data.today.itemsSold) + " SP"
                : NumberUtil.formatThousands(data.today.itemsSold) + " sản phẩm đã bán");

        // --- KPI tháng ---
        monthRevenueCard.setValue(NumberUtil.formatThousands(data.month.totalRevenue.longValue()) + " đ");
        monthRevenueCard.setSubtitle(NumberUtil.formatThousands(data.month.invoiceCount) + " hóa đơn trong tháng");

        BigDecimal net = data.monthProfit != null && data.monthProfit.netProfit != null
                ? data.monthProfit.netProfit : BigDecimal.ZERO;
        monthProfitCard.setValue(NumberUtil.formatThousands(net.longValue()) + " đ");
        Double margin = data.monthProfit != null ? data.monthProfit.netMarginPercent() : null;
        if (margin != null) {
            monthProfitCard.setSubtitle(String.format("Biên ròng %.1f%%", margin));
        } else {
            monthProfitCard.setSubtitle("Thu − Chi (giá vốn + thiệt hại)");
        }

        // --- Hàng đợi ---
        pendingReturnCard.setValue(NumberUtil.formatThousands(data.pendingReturns));
        if (data.pendingReturns == 0) {
            pendingReturnCard.setSubtitle("Không còn yêu cầu chờ duyệt");
        } else {
            pendingReturnCard.setTrend(data.pendingReturns + " cần xử lý", false);
        }

        pendingExceptionCard.setValue(NumberUtil.formatThousands(data.pendingExceptions));
        if (data.pendingExceptions == 0) {
            pendingExceptionCard.setSubtitle("Không có báo cáo chờ xử lý");
        } else {
            pendingExceptionCard.setTrend(data.pendingExceptions + " báo cáo mới", false);
        }

        weeklyChartPanel.setData(data.weeklyFinance);
        renderTopProducts(data.topProducts);
        renderPendingReturns(data.pendingReturnItems);
        renderPendingExceptions(data.pendingExceptionItems);
    }

    // ---------------------------------------------------------------
    // Lists
    // ---------------------------------------------------------------

    private void renderTopProducts(List<TopProduct> products) {
        topProductListPanel.removeAll();
        if (products == null || products.isEmpty()) {
            topProductListPanel.add(emptyRow("Chưa có doanh thu trong 7 ngày gần đây"));
        } else {
            int rank = 1;
            for (TopProduct p : products) {
                topProductListPanel.add(buildTopProductRow(rank++, p));
                topProductListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        topProductListPanel.revalidate();
        topProductListPanel.repaint();
    }

    private JPanel buildTopProductRow(int rank, TopProduct p) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        row.setBorder(new EmptyBorder(6, 4, 6, 4));

        JLabel rankBadge = new JLabel(String.valueOf(rank), SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = rank <= 3 ? AppColor.ACCENT_BG_SOFT : AppColor.BG_LIGHT;
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        rankBadge.setFont(AppFont.SMALL_BOLD);
        rankBadge.setForeground(rank <= 3 ? AppColor.ACCENT : AppColor.TEXT_MUTED);
        rankBadge.setPreferredSize(new Dimension(28, 28));
        rankBadge.setOpaque(false);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel name = new JLabel(p.productName != null ? p.productName : "—");
        name.setFont(AppFont.BODY_BOLD);
        name.setForeground(AppColor.TEXT_PRIMARY);
        JLabel meta = new JLabel(NumberUtil.formatThousands(p.quantity) + " SP đã bán");
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);
        left.add(name);
        left.add(meta);

        JLabel revenue = new JLabel(NumberUtil.formatThousands(
                p.revenue != null ? p.revenue.longValue() : 0) + " đ");
        revenue.setFont(AppFont.BODY_BOLD);
        revenue.setForeground(AppColor.ACCENT);

        row.add(rankBadge, BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        row.add(revenue, BorderLayout.EAST);
        return row;
    }

    private void renderPendingReturns(List<PendingReturnItem> items) {
        pendingReturnListPanel.removeAll();
        if (items == null || items.isEmpty()) {
            pendingReturnListPanel.add(emptyRow("Không có yêu cầu đổi/trả đang chờ duyệt"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                boolean last = i == items.size() - 1;
                pendingReturnListPanel.add(buildPendingReturnRow(items.get(i), last));
            }
        }
        pendingReturnListPanel.revalidate();
        pendingReturnListPanel.repaint();
    }

    private JPanel buildPendingReturnRow(PendingReturnItem item, boolean isLast) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        var padding = new EmptyBorder(10, 4, 10, 4);
        row.setBorder(isLast ? padding
                : new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppColor.BORDER), padding));

        Color accent = item.isExchange() ? AppColor.INFO : AppColor.WARNING;
        FontIcon icon = FontIcon.of(
                item.isExchange() ? FontAwesomeSolid.EXCHANGE_ALT : FontAwesomeSolid.UNDO, 14);
        icon.setIconColor(accent);
        JLabel iconLabel = circleIcon(icon, accent);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel code = new JLabel(item.invoiceCode != null ? item.invoiceCode : "—");
        code.setFont(AppFont.BODY_BOLD);
        code.setForeground(AppColor.TEXT_PRIMARY);
        line1.add(code);
        line1.add(new StatBadge(item.isExchange() ? "Đổi hàng" : "Trả hàng", accent));

        String who = item.createdByName != null ? item.createdByName : "—";
        String when = item.createdAt != null ? item.createdAt.format(TIME_FMT) : "";
        JLabel meta = new JLabel(who + (when.isEmpty() ? "" : " · " + when));
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(line1);
        left.add(meta);

        JLabel value = new JLabel(NumberUtil.formatThousands(item.totalValue.longValue()) + " đ");
        value.setFont(AppFont.BODY_BOLD);
        value.setForeground(AppColor.TEXT_PRIMARY);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void renderPendingExceptions(List<PendingExceptionItem> items) {
        pendingExceptionListPanel.removeAll();
        if (items == null || items.isEmpty()) {
            pendingExceptionListPanel.add(emptyRow("Không có báo cáo ngoại lệ chờ xử lý"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                boolean last = i == items.size() - 1;
                pendingExceptionListPanel.add(buildPendingExceptionRow(items.get(i), last));
            }
        }
        pendingExceptionListPanel.revalidate();
        pendingExceptionListPanel.repaint();
    }

    private JPanel buildPendingExceptionRow(PendingExceptionItem item, boolean isLast) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 62));
        var padding = new EmptyBorder(10, 4, 10, 4);
        row.setBorder(isLast ? padding
                : new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppColor.BORDER), padding));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCLAMATION_CIRCLE, 14);
        icon.setIconColor(AppColor.ERROR);
        JLabel iconLabel = circleIcon(icon, AppColor.ERROR);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        String who = item.createdByName != null ? item.createdByName : "Nhân viên";
        String when = item.createdAt != null ? item.createdAt.format(TIME_FMT) : "";
        JLabel title = new JLabel(who + (when.isEmpty() ? "" : " · " + when));
        title.setFont(AppFont.BODY_BOLD);
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String raw = item.content != null ? item.content.trim().replaceAll("\\s+", " ") : "";
        if (raw.length() > 72) raw = raw.substring(0, 69) + "...";
        JLabel preview = new JLabel(raw.isEmpty() ? "(Không có nội dung)" : raw);
        preview.setFont(AppFont.SMALL);
        preview.setForeground(AppColor.TEXT_MUTED);
        preview.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(title);
        left.add(preview);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        return row;
    }

    private JLabel circleIcon(FontIcon icon, Color color) {
        JLabel label = new JLabel(icon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setVerticalAlignment(SwingConstants.CENTER);
        Dimension size = new Dimension(34, 34);
        label.setPreferredSize(size);
        label.setMinimumSize(size);
        label.setMaximumSize(size);
        return label;
    }

    private JPanel emptyRow(String message) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 12));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel label = new JLabel(message);
        label.setFont(AppFont.BODY);
        label.setForeground(AppColor.TEXT_MUTED);
        row.add(label);
        return row;
    }
}
