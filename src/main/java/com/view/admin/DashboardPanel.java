package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatBadge;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.components.report.FinanceChartPanel;
import com.dao.AuditLogDAO;
import com.dao.DashboardDAO;
import com.dao.DashboardDAO.LowStockItem;
import com.dao.RevenueReportDAO;
import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.dao.RevenueReportDAO.Summary;
import com.dao.StockAlertDAO;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.ActivityLog;
import com.model.StockAlert;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.DateUtil;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class DashboardPanel extends JPanel {

    private final DashboardDAO dashboardDao = new DashboardDAO();
    private final RevenueReportDAO revenueDao = new RevenueReportDAO();
    private final StockAlertDAO stockAlertDao = new StockAlertDAO();
    private final AuditLogDAO auditLogDao = new AuditLogDAO();

    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải dữ liệu...");

    private StatCard revenueCard;
    private StatCard invoiceCard;
    private StatCard productCard;
    private StatCard lowStockCard;
    private StatCard customerCard;
    private StatCard cancelledInvoiceCard;
    private StatCard returnedProductCard;

    private FinanceChartPanel weeklyChartPanel;
    private JPanel lowStockListPanel;
    private JPanel activityListPanel;
    private JPanel stockAlertListPanel;

    /*
     * Dashboard chung (DASHBOARD_VIEW) được cấp cho cả Sales Manager và
     * Sales Staff, nhưng dữ liệu tồn kho / cảnh báo tồn là nghiệp vụ của
     * Kho hàng - chỉ hiện khi user có quyền kho tương ứng (Admin luôn có
     * toàn quyền nên không bị ảnh hưởng). Tránh lộ "trang tổng quan kho"
     * lồng trong Dashboard chung cho các role không quản lý kho.
     */
    private final boolean canViewStock =
            PermissionManager.getInstance().can(AppPermission.STOCK_VIEW);
    private final boolean canViewStockAlerts =
            PermissionManager.getInstance().can(AppPermission.STOCK_ALERT_VIEW);

    public DashboardPanel() {
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
                ? "Toàn cảnh hoạt động kinh doanh của Connect Mart hôm nay"
                : "Chào " + name + ", đây là toàn cảnh hoạt động kinh doanh của Connect Mart hôm nay";

        SectionHeader header = new SectionHeader(FontAwesomeSolid.TACHOMETER_ALT, AppColor.ACCENT,
                "Tổng quan", subtitle);
        return header;
    }

    // ---------------------------------------------------------------
    // Nội dung động (stat cards + biểu đồ + danh sách)
    // ---------------------------------------------------------------

    private JPanel buildDynamicContent() {
        JPanel content = new ScrollableColumn();

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        /*
         * "Sản phẩm sắp hết hàng" là dữ liệu kho - chỉ ghép cột này khi user
         * có STOCK_VIEW, ngược lại biểu đồ thu/chi/lợi nhuận chiếm trọn hàng.
         */
        if (canViewStock) {
            content.add(buildTwoColumnRow(buildRevenueChartCard(), 2.0, buildLowStockCard(), 1.0, 340));
        } else {
            content.add(buildFullWidthRow(buildRevenueChartCard(), 340));
        }
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        /*
         * "Báo cáo từ nhân viên bán hàng" (cảnh báo tồn chưa xử lý) là việc
         * của Quản lý kho - chỉ ghép cột này khi user có STOCK_ALERT_VIEW,
         * ngược lại "Hoạt động gần đây" chiếm trọn hàng.
         */
        if (canViewStockAlerts) {
            content.add(buildTwoColumnRow(buildActivityCard(), 1.3, buildStockAlertCard(), 1.0, 320));
        } else {
            content.add(buildFullWidthRow(buildActivityCard(), 320));
        }

        return content;
    }

    /** 1 hàng full-width dùng khi cột "kho" bên cạnh bị ẩn do thiếu quyền. */
    private JPanel buildFullWidthRow(JComponent comp, int height) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        row.setPreferredSize(new Dimension(10, height));
        row.add(comp, BorderLayout.CENTER);
        return row;
    }

    /**
     * Hàng thống kê responsive — ưu tiên hiện đủ chữ title/footer:
     * - ≥1320px: 1 hàng × N (N = số card thực tế)
     * - ≥720px:  4 cột
     * - ≥480px:  3 cột
     * - hẹp hơn: 2 cột
     */
    private JPanel buildStatsRow() {
        revenueCard = new StatCard("Doanh thu hôm nay", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        invoiceCard = new StatCard("Hóa đơn hôm nay", "0", FontAwesomeSolid.RECEIPT, AppColor.INFO);
        productCard = new StatCard("Sản phẩm đang bán", "0", FontAwesomeSolid.BOX_OPEN, AppColor.TEAL);
        customerCard = new StatCard("Khách hàng", "0", FontAwesomeSolid.USERS, AppColor.BLUE);
        cancelledInvoiceCard = new StatCard("Hóa đơn bị hủy", "0", FontAwesomeSolid.BAN, AppColor.ERROR);
        returnedProductCard = new StatCard("SP trả lại hôm nay", "0", FontAwesomeSolid.UNDO, AppColor.RED_ALT);

        List<StatCard> cardList = new ArrayList<>();
        cardList.add(revenueCard);
        cardList.add(invoiceCard);
        cardList.add(productCard);

        /*
         * "Sắp / hết hàng" là số liệu tồn kho - chỉ hiện cho role có
         * STOCK_VIEW (Admin, Quản lý kho...). NV bán hàng / Quản lý bán
         * hàng chỉ có DASHBOARD_VIEW thì không được thấy số liệu kho này.
         */
        if (canViewStock) {
            lowStockCard = new StatCard("Sắp / hết hàng", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.WARNING);
            cardList.add(lowStockCard);
        }

        cardList.add(cancelledInvoiceCard);
        cardList.add(returnedProductCard);

        StatCard[] cards = cardList.toArray(new StatCard[0]);

        JPanel row = new JPanel() {
            private int lastCols = -1;

            {
                setOpaque(false);
                setAlignmentX(Component.LEFT_ALIGNMENT);
                // Mặc định 2 hàng × 4 cột (phổ biến) — relayout sẽ chỉnh lại theo width thật
                setLayout(new GridLayout(2, 4, AppSpacing.MD, AppSpacing.MD));
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
                // Ưu tiên hiện đủ chữ: chỉ 1 hàng khi rất rộng.
                // Màn hình thường → 4 cột để mỗi card đủ rộng hiện full title/footer.
                int cols = (w >= 1320) ? cards.length : (w >= 720) ? 4 : (w >= 480) ? 3 : 2;
                if (cols == lastCols) return;
                lastCols = cols;

                int rows = (int) Math.ceil((double) cards.length / cols);
                setLayout(new GridLayout(rows, cols, AppSpacing.MD, AppSpacing.MD));
                removeAll();
                for (StatCard c : cards) add(c);
                int rowH = StatCard.PREFERRED_HEIGHT;
                int totalH = rows * rowH + Math.max(0, rows - 1) * AppSpacing.MD;
                setPreferredSize(new Dimension(10, totalH));
                setMaximumSize(new Dimension(Integer.MAX_VALUE, totalH + 4));
                revalidate();
                repaint();
            }

            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                if (lastCols <= 0) {
                    int h = StatCard.PREFERRED_HEIGHT * 2 + AppSpacing.MD;
                    return new Dimension(d.width, h);
                }
                return d;
            }
        };
        return row;
    }

    /** 1 hàng 2 cột không đều nhau (vd 2:1) - dùng GridBagLayout(fill=BOTH) để luôn khớp đúng chiều rộng/cao. */
    private JPanel buildTwoColumnRow(JComponent left, double leftWeight, JComponent right, double rightWeight, int height) {
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
        DashboardCard card = new DashboardCard("Thu / Chi / Lợi nhuận 7 ngày gần đây",
                "Thu = doanh thu (chưa VAT) · Chi = giá vốn + thiệt hại · Lợi nhuận ròng = Thu − Chi",
                FontAwesomeSolid.CHART_BAR, AppColor.ACCENT);
        weeklyChartPanel = new FinanceChartPanel();
        card.getContentPanel().add(weeklyChartPanel, BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildLowStockCard() {
        DashboardCard card = new DashboardCard("Sản phẩm sắp hết hàng",
                "Tồn kho ở mức hoặc dưới mức tối thiểu",
                FontAwesomeSolid.BOX, AppColor.WARNING);
        lowStockListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(lowStockListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildActivityCard() {
        DashboardCard card = new DashboardCard("Hoạt động gần đây",
                "Nhật ký thao tác mới nhất trên hệ thống",
                FontAwesomeSolid.HISTORY, AppColor.INFO);
        activityListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(activityListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildStockAlertCard() {
        DashboardCard card = new DashboardCard("Báo cáo từ nhân viên bán hàng",
                "Hết/sắp hết hàng - chưa xử lý",
                FontAwesomeSolid.BELL, AppColor.RED_ALT);
        stockAlertListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(stockAlertListPanel), BorderLayout.CENTER);
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

    /** JPanel cột dọc (BoxLayout Y_AXIS) luôn "tracks" chiều rộng vùng nhìn của JScrollPane cha - tránh cuộn ngang. */
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
    // Tải dữ liệu
    // ---------------------------------------------------------------

    private static class DashboardData {
        Summary todaySummary;
        List<DailyFinancePoint> weeklyFinance;
        DashboardDAO.Overview overview;
        List<LowStockItem> lowStockItems;
        List<ActivityLog> recentActivity;
        List<StockAlert> pendingAlerts;
    }

    private void loadData() {
        loadingOverlay.start("Đang tải dữ liệu...");
        SwingWorker<DashboardData, Void> worker = new SwingWorker<DashboardData, Void>() {
            @Override
            protected DashboardData doInBackground() {
                LocalDate today = LocalDate.now();

                DashboardData data = new DashboardData();
                data.todaySummary = revenueDao.getSummary(today, today);
                data.weeklyFinance = revenueDao.getDailyFinance(today.minusDays(6), today);
                data.overview = dashboardDao.getOverview();
                data.recentActivity = auditLogDao.getPaged(1, 8).getData();

                // Chỉ truy vấn dữ liệu kho khi user thực sự có quyền xem,
                // tránh vừa lộ nghiệp vụ kho vừa tốn truy vấn thừa.
                data.lowStockItems = canViewStock
                        ? dashboardDao.getLowStockProducts(8)
                        : List.of();

                if (canViewStockAlerts) {
                    List<StockAlert> alerts = stockAlertDao.getUnseenForInventoryManager();
                    data.pendingAlerts = alerts.size() > 8 ? alerts.subList(0, 8) : alerts;
                } else {
                    data.pendingAlerts = List.of();
                }
                return data;
            }

            @Override
            protected void done() {
                loadingOverlay.stop();
                try {
                    applyData(get());
                } catch (Exception e) {
                    e.printStackTrace();
                    BaseDialog.error(DashboardPanel.this, "Lỗi", "Không thể tải dữ liệu tổng quan: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyData(DashboardData data) {
        revenueCard.setValue(NumberUtil.formatThousands(data.todaySummary.totalRevenue.longValue()) + " đ");
        revenueCard.setSubtitle("Tính đến thời điểm hiện tại");

        invoiceCard.setValue(NumberUtil.formatThousands(data.todaySummary.invoiceCount));
        invoiceCard.setSubtitle(NumberUtil.formatThousands(data.todaySummary.itemsSold) + " sản phẩm đã bán");

        productCard.setValue(NumberUtil.formatThousands(data.overview.totalProducts));
        productCard.setSubtitle("Đang mở bán");

        if (lowStockCard != null) {
            lowStockCard.setValue(NumberUtil.formatThousands(data.overview.lowStockCount));
            if (data.overview.lowStockCount == 0) {
                lowStockCard.setSubtitle("Tồn kho ổn định");
            } else {
                lowStockCard.setTrend("Cần nhập thêm hàng", false);
            }
        }

        customerCard.setValue(NumberUtil.formatThousands(data.overview.totalCustomers));
        customerCard.setSubtitle(NumberUtil.formatThousands(data.overview.totalEmployees) + " nhân viên đang làm việc");

        cancelledInvoiceCard.setValue(NumberUtil.formatThousands(data.overview.cancelledInvoicesToday));
        if (data.overview.cancelledInvoicesToday == 0) {
            cancelledInvoiceCard.setSubtitle("Không có hóa đơn bị hủy");
        } else {
            cancelledInvoiceCard.setTrend("Cần rà soát lý do hủy", false);
        }

        returnedProductCard.setValue(NumberUtil.formatThousands(data.overview.returnedProductsToday));
        returnedProductCard.setSubtitle("Đã duyệt đổi/trả hôm nay");

        weeklyChartPanel.setData(data.weeklyFinance);

        if (lowStockListPanel != null) {
            renderLowStock(data.lowStockItems);
        }
        renderActivity(data.recentActivity);
        if (stockAlertListPanel != null) {
            renderStockAlerts(data.pendingAlerts);
        }
    }

    // ---------------------------------------------------------------
    // Danh sách: sản phẩm sắp hết hàng
    // ---------------------------------------------------------------

    private void renderLowStock(List<LowStockItem> items) {
        lowStockListPanel.removeAll();
        if (items.isEmpty()) {
            lowStockListPanel.add(emptyRow("Không có sản phẩm nào dưới mức tồn kho tối thiểu"));
        } else {
            for (LowStockItem item : items) {
                lowStockListPanel.add(buildLowStockRow(item));
                lowStockListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        lowStockListPanel.revalidate();
        lowStockListPanel.repaint();
    }

    private JPanel buildLowStockRow(LowStockItem item) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        row.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(item.productName);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        JLabel codeLabel = new JLabel(item.productCode);
        codeLabel.setFont(AppFont.FOOTER);
        codeLabel.setForeground(AppColor.TEXT_MUTED);
        left.add(nameLabel);
        left.add(codeLabel);

        boolean out = item.isOutOfStock();
        StatBadge badge = new StatBadge(out ? "Hết hàng" : item.stock + "/" + item.minStock,
                out ? AppColor.ERROR : AppColor.WARNING);

        row.add(left, BorderLayout.CENTER);
        row.add(badge, BorderLayout.EAST);
        return row;
    }

    // ---------------------------------------------------------------
    // Danh sách: hoạt động gần đây (AuditLogs)
    // ---------------------------------------------------------------

    private void renderActivity(List<ActivityLog> logs) {
        activityListPanel.removeAll();
        if (logs.isEmpty()) {
            activityListPanel.add(emptyRow("Chưa có hoạt động nào được ghi nhận"));
        } else {
            for (int i = 0; i < logs.size(); i++) {
                boolean isLast = (i == logs.size() - 1);
                activityListPanel.add(buildActivityRow(logs.get(i), isLast));
            }
        }
        activityListPanel.revalidate();
        activityListPanel.repaint();
    }

    private JPanel buildActivityRow(ActivityLog log, boolean isLast) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
        // Đường kẻ mỏng ngăn cách các dòng (trừ dòng cuối), thay cho khoảng trống đơn thuần.
        Border padding = new EmptyBorder(10, 4, 10, 4);
        row.setBorder(isLast ? padding
                : new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppColor.BORDER), padding));

        // Avatar tròn thể hiện loại hành động, giúp tách bạch trực quan từng dòng với nhau.
        Color color = actionColor(log.getAction());
        FontIcon fontIcon = FontIcon.of(actionIcon(log.getAction()), 14);
        fontIcon.setIconColor(color);
        JLabel iconLabel = new JLabel(fontIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 30));
                g2.fillOval(0, 0, getWidth() - 1, getHeight() - 1);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setVerticalAlignment(SwingConstants.CENTER);
        Dimension avatarSize = new Dimension(34, 34);
        iconLabel.setPreferredSize(avatarSize);
        iconLabel.setMinimumSize(avatarSize);
        iconLabel.setMaximumSize(avatarSize);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel userLabel = new JLabel(log.getUsername());
        userLabel.setFont(AppFont.BODY_BOLD);
        userLabel.setForeground(AppColor.TEXT_PRIMARY);
        line1.add(userLabel);
        line1.add(new StatBadge(actionLabel(log.getAction()), color));

        String desc = (log.getDescription() != null && !log.getDescription().isBlank())
                ? log.getDescription() : entityLabel(log.getEntityType());
        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(AppFont.SMALL);
        descLabel.setForeground(AppColor.TEXT_MUTED);
        descLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(line1);
        left.add(Box.createVerticalStrut(2));
        left.add(descLabel);

        // Gắn thời gian vào mép trên thay vì kéo dài hết chiều cao dòng (tránh lệch giữa
        // lúc dòng 1 dòng text và lúc dòng 2 dòng text).
        JLabel timeLabel = new JLabel(DateUtil.timeAgo(log.getCreatedAt()));
        timeLabel.setFont(AppFont.FOOTER);
        timeLabel.setForeground(AppColor.TEXT_DISABLED);
        JPanel timeWrap = new JPanel(new BorderLayout());
        timeWrap.setOpaque(false);
        timeWrap.add(timeLabel, BorderLayout.NORTH);

        row.add(iconLabel, BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        row.add(timeWrap, BorderLayout.EAST);
        return row;
    }

    private static String actionLabel(String action) {
        if (action == null) return "";
        switch (action) {
            case ActivityLog.ACTION_CREATE: return "Thêm mới";
            case ActivityLog.ACTION_UPDATE: return "Cập nhật";
            case ActivityLog.ACTION_DELETE: return "Xóa";
            case ActivityLog.ACTION_PERMANENT_DELETE: return "Xóa vĩnh viễn";
            case ActivityLog.ACTION_RESTORE: return "Khôi phục";
            case ActivityLog.ACTION_LOGIN: return "Đăng nhập";
            case ActivityLog.ACTION_LOGIN_FAILED: return "Đăng nhập thất bại";
            case ActivityLog.ACTION_LOGOUT: return "Đăng xuất";
            case ActivityLog.ACTION_STATUS_CHANGE: return "Đổi trạng thái";
            case ActivityLog.ACTION_PASSWORD_RESET: return "Đặt lại mật khẩu";
            default: return action;
        }
    }

    private static Color actionColor(String action) {
        if (action == null) return AppColor.TEXT_MUTED;
        switch (action) {
            case ActivityLog.ACTION_CREATE: return AppColor.SUCCESS;
            case ActivityLog.ACTION_UPDATE: return AppColor.INFO;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
            case ActivityLog.ACTION_LOGIN_FAILED: return AppColor.ERROR;
            case ActivityLog.ACTION_LOGIN:
            case ActivityLog.ACTION_LOGOUT: return AppColor.TEAL;
            case ActivityLog.ACTION_RESTORE: return AppColor.ACCENT;
            default: return AppColor.WARNING;
        }
    }

    /** Icon tương ứng từng loại hành động, dùng làm avatar tròn đầu mỗi dòng trong "Hoạt động gần đây". */
    private static FontAwesomeSolid actionIcon(String action) {
        if (action == null) return FontAwesomeSolid.CIRCLE;
        switch (action) {
            case ActivityLog.ACTION_CREATE: return FontAwesomeSolid.PLUS;
            case ActivityLog.ACTION_UPDATE: return FontAwesomeSolid.PEN;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE: return FontAwesomeSolid.TRASH_ALT;
            case ActivityLog.ACTION_RESTORE: return FontAwesomeSolid.UNDO;
            case ActivityLog.ACTION_LOGIN: return FontAwesomeSolid.SIGN_IN_ALT;
            case ActivityLog.ACTION_LOGIN_FAILED: return FontAwesomeSolid.EXCLAMATION_TRIANGLE;
            case ActivityLog.ACTION_LOGOUT: return FontAwesomeSolid.SIGN_OUT_ALT;
            case ActivityLog.ACTION_STATUS_CHANGE: return FontAwesomeSolid.TOGGLE_ON;
            case ActivityLog.ACTION_PASSWORD_RESET: return FontAwesomeSolid.KEY;
            default: return FontAwesomeSolid.CIRCLE;
        }
    }

    private static String entityLabel(String entityType) {
        if (entityType == null) return "";
        switch (entityType) {
            case ActivityLog.ENTITY_PRODUCT: return "Sản phẩm";
            case ActivityLog.ENTITY_CATEGORY: return "Danh mục";
            case ActivityLog.ENTITY_CUSTOMER: return "Khách hàng";
            case ActivityLog.ENTITY_EMPLOYEE: return "Nhân viên";
            case ActivityLog.ENTITY_SUPPLIER: return "Nhà cung cấp";
            case ActivityLog.ENTITY_USER: return "Tài khoản";
            case ActivityLog.ENTITY_INVOICE: return "Hóa đơn";
            case ActivityLog.ENTITY_PURCHASE_RECEIPT: return "Phiếu nhập";
            case ActivityLog.ENTITY_ORDER: return "Đơn hàng";
            case ActivityLog.ENTITY_INVENTORY_BATCH: return "Lô hàng";
            case ActivityLog.ENTITY_STOCK_ALERT: return "Cảnh báo tồn kho";
            case ActivityLog.ENTITY_PHONE: return "Điện thoại";
            default: return entityType;
        }
    }

    // ---------------------------------------------------------------
    // Danh sách: báo cáo thiếu hàng từ NV bán hàng (chưa xử lý)
    // ---------------------------------------------------------------

    private void renderStockAlerts(List<StockAlert> alerts) {
        stockAlertListPanel.removeAll();
        if (alerts.isEmpty()) {
            stockAlertListPanel.add(emptyRow("Không có báo cáo nào chưa xử lý"));
        } else {
            for (StockAlert alert : alerts) {
                stockAlertListPanel.add(buildStockAlertRow(alert));
                stockAlertListPanel.add(Box.createVerticalStrut(AppSpacing.XS));
            }
        }
        stockAlertListPanel.revalidate();
        stockAlertListPanel.repaint();
    }

    private JPanel buildStockAlertRow(StockAlert alert) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBorder(new EmptyBorder(6, 4, 6, 4));

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        JLabel nameLabel = new JLabel(alert.getProductName());
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        JLabel byLabel = new JLabel("Báo bởi " + alert.getReportedByName());
        byLabel.setFont(AppFont.FOOTER);
        byLabel.setForeground(AppColor.TEXT_MUTED);
        left.add(nameLabel);
        left.add(byLabel);

        boolean out = "OUT_OF_STOCK".equals(alert.getAlertType());

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        StatBadge badge = new StatBadge(out ? "Hết hàng" : "Sắp hết", out ? AppColor.ERROR : AppColor.WARNING);
        badge.setAlignmentX(Component.RIGHT_ALIGNMENT);
        JLabel timeLabel = new JLabel(DateUtil.timeAgo(toDate(alert.getCreatedAt())));
        timeLabel.setFont(AppFont.FOOTER);
        timeLabel.setForeground(AppColor.TEXT_DISABLED);
        timeLabel.setAlignmentX(Component.RIGHT_ALIGNMENT);
        right.add(badge);
        right.add(timeLabel);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private static Date toDate(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    // ---------------------------------------------------------------
    // Chung
    // ---------------------------------------------------------------

    private JPanel emptyRow(String message) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(28, 8, 28, 8));
        JLabel label = new JLabel(message, SwingConstants.CENTER);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        row.add(label, BorderLayout.CENTER);
        return row;
    }
}