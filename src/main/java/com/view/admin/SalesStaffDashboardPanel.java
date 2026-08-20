package com.view.admin;

import com.components.BaseDialog;
import com.components.LoadingOverlay;
import com.components.SectionHeader;
import com.components.StatBadge;
import com.components.StatCard;
import com.components.dashboard.DashboardCard;
import com.dao.DashboardDAO;
import com.dao.DashboardDAO.PendingReturnItem;
import com.dao.DashboardDAO.StaffDayStats;
import com.dao.DashboardDAO.StaffInvoiceItem;
import com.dao.ShiftDAO;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.Shift;
import com.model.User;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dashboard riêng cho <b>Nhân viên bán hàng</b> ({@code Role.SALES_STAFF}).
 * <p>
 * Tập trung ca làm việc tại quầy và hiệu suất cá nhân theo đúng ca đang mở:
 * <ul>
 *   <li>Trạng thái ca OPEN / chưa mở ca</li>
 *   <li>Doanh thu · số hóa đơn · SP đã bán của chính NV trong ShiftID hiện tại</li>
 *   <li>Tiền mặt dự kiến của ca</li>
 *   <li>Đơn online đang được giao cho chính NV</li>
 *   <li>Yêu cầu hủy và đổi/trả đang chờ duyệt trong ca</li>
 * </ul>
 * UI đồng bộ {@link SectionHeader}, {@link StatCard}, {@link DashboardCard}.
 */
public class SalesStaffDashboardPanel extends JPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final DashboardDAO dashboardDao = new DashboardDAO();
    private final ShiftDAO shiftDao = new ShiftDAO();
    private final LoadingOverlay loadingOverlay = new LoadingOverlay("Đang tải ca làm việc...");

    private StatCard shiftStatusCard;
    private StatCard myRevenueCard;
    private StatCard myInvoiceCard;
    private StatCard myItemsCard;
    private StatCard cancelledCard;
    private StatCard pendingReturnCard;
    private StatCard expectedCashCard;
    private StatCard assignedOrdersCard;
    private StatCard pendingCancelCard;

    private JPanel shiftDetailPanel;
    private JPanel recentInvoiceListPanel;
    private JPanel pendingReturnListPanel;

    public SalesStaffDashboardPanel() {
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
        User user = AuthService.getInstance().getCurrentUser();
        String name = user != null ? user.getFullName() : null;
        String subtitle = (name == null || name.isBlank())
                ? "Ca làm việc, doanh thu cá nhân và hóa đơn gần đây tại quầy"
                : "Chào " + name + " — theo dõi KPI theo đúng ca làm việc hiện tại";
        return new SectionHeader(FontAwesomeSolid.CASH_REGISTER, AppColor.ACCENT,
                "Tổng quan quầy bán", subtitle);
    }

    // ---------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------

    private JPanel buildDynamicContent() {
        JPanel content = new ScrollableColumn();

        content.add(buildStatsRow());
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        content.add(buildTwoColumnRow(buildShiftCard(), 1.1, buildPendingReturnsCard(), 1.0, 280));
        content.add(Box.createVerticalStrut(AppSpacing.LG));

        content.add(buildFullWidthRow(buildRecentInvoicesCard(), 320));

        return content;
    }

    private JPanel buildStatsRow() {
        shiftStatusCard = new StatCard("Trạng thái ca", "—",
                FontAwesomeSolid.USER_CLOCK, AppColor.INFO);
        myRevenueCard = new StatCard("Doanh thu ca của tôi", "0 đ",
                FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT);
        myInvoiceCard = new StatCard("Hóa đơn ca này", "0",
                FontAwesomeSolid.RECEIPT, AppColor.TEAL);
        myItemsCard = new StatCard("SP bán trong ca", "0",
                FontAwesomeSolid.BOX_OPEN, AppColor.SUCCESS);
        cancelledCard = new StatCard("Hóa đơn hủy trong ca", "0",
                FontAwesomeSolid.BAN, AppColor.ERROR);
        pendingReturnCard = new StatCard("Đổi/trả chờ duyệt", "0",
                FontAwesomeSolid.EXCHANGE_ALT, AppColor.WARNING);
        expectedCashCard = new StatCard("Tiền mặt dự kiến", "0 đ",
                FontAwesomeSolid.WALLET, AppColor.INFO);
        assignedOrdersCard = new StatCard("Đơn online được giao", "0",
                FontAwesomeSolid.TRUCK, AppColor.ACCENT);
        pendingCancelCard = new StatCard("Yêu cầu hủy chờ duyệt", "0",
                FontAwesomeSolid.HOURGLASS_HALF, AppColor.WARNING);

        StatCard[] cards = {
                shiftStatusCard, myRevenueCard, myInvoiceCard,
                myItemsCard, expectedCashCard, assignedOrdersCard,
                cancelledCard, pendingReturnCard, pendingCancelCard
        };

        JPanel row = new JPanel() {
            private int lastCols = -1;

            {
                setOpaque(false);
                setAlignmentX(Component.LEFT_ALIGNMENT);
                setLayout(new GridLayout(3, 3, AppSpacing.MD, AppSpacing.MD));
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
                    return new Dimension(d.width, StatCard.PREFERRED_HEIGHT * 3 + AppSpacing.MD * 2);
                }
                return d;
            }
        };
        return row;
    }

    private JPanel buildTwoColumnRow(JComponent left, double leftW,
                                    JComponent right, double rightW, int height) {
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
        gbc.weightx = leftW;
        gbc.insets = new Insets(0, 0, 0, AppSpacing.LG);
        row.add(left, gbc);

        gbc.gridx = 1;
        gbc.weightx = rightW;
        gbc.insets = new Insets(0, 0, 0, 0);
        row.add(right, gbc);
        return row;
    }

    private JPanel buildFullWidthRow(JComponent comp, int height) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        row.setPreferredSize(new Dimension(10, height));
        row.add(comp, BorderLayout.CENTER);
        return row;
    }

    private DashboardCard buildShiftCard() {
        DashboardCard card = new DashboardCard(
                "Ca làm việc hiện tại",
                "Mọi hóa đơn tại quầy phải thuộc ca đang mở",
                FontAwesomeSolid.CLOCK, AppColor.INFO);
        shiftDetailPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(shiftDetailPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildPendingReturnsCard() {
        DashboardCard card = new DashboardCard(
                "Đổi / trả của tôi đang chờ",
                "Chỉ lấy yêu cầu thuộc hóa đơn trong ca đang mở",
                FontAwesomeSolid.EXCHANGE_ALT, AppColor.WARNING);
        pendingReturnListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(pendingReturnListPanel), BorderLayout.CENTER);
        return card;
    }

    private DashboardCard buildRecentInvoicesCard() {
        DashboardCard card = new DashboardCard(
                "Hóa đơn gần đây trong ca",
                "Chỉ hiển thị hóa đơn thuộc ShiftID đang mở",
                FontAwesomeSolid.FILE_INVOICE, AppColor.ACCENT);
        recentInvoiceListPanel = new ScrollableColumn();
        card.getContentPanel().add(plainScroll(recentInvoiceListPanel), BorderLayout.CENTER);
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
    // Data
    // ---------------------------------------------------------------

    private static class StaffDashData {
        Shift openShift;
        StaffDayStats shiftStats;
        BigDecimal expectedCash = BigDecimal.ZERO;
        int assignedActiveOrders;
        int myPendingReturns;
        int pendingCancelRequests;
        List<PendingReturnItem> pendingReturnItems = List.of();
        List<StaffInvoiceItem> recentInvoices = List.of();
    }

    private void loadData() {
        loadingOverlay.start("Đang tải dữ liệu quầy...");
        SwingWorker<StaffDashData, Void> worker = new SwingWorker<>() {
            @Override
            protected StaffDashData doInBackground() {
                User user = AuthService.getInstance().getCurrentUser();
                int userId = user != null ? user.getUserId() : 0;

                StaffDashData data = new StaffDashData();
                data.shiftStats = new StaffDayStats(0, BigDecimal.ZERO, 0, 0);

                if (userId > 0) {
                    data.openShift = shiftDao.findOpenShiftByUserId(userId);
                    // Đơn online là hàng việc được giao cho nhân viên, không phụ thuộc ca vì
                    // bảng Orders không có ShiftID. Chỉ đếm các đơn chưa kết thúc.
                    data.assignedActiveOrders = dashboardDao.countMyAssignedActiveOrders(userId);

                    if (data.openShift != null && data.openShift.isOpen()) {
                        int shiftId = data.openShift.getShiftId();

                        // Tất cả KPI bán hàng phía dưới đều khóa theo đúng ShiftID hiện tại.
                        data.shiftStats = dashboardDao.getStaffShiftStats(userId, shiftId);
                        data.expectedCash = calculateExpectedCash(data.openShift);
                        data.myPendingReturns = dashboardDao.countMyPendingReturnsForShift(userId, shiftId);
                        data.pendingCancelRequests = dashboardDao
                                .countMyPendingCancelRequestsForShift(userId, shiftId);
                        data.pendingReturnItems = dashboardDao
                                .getMyPendingReturnsForShift(userId, shiftId, 8);
                        data.recentInvoices = dashboardDao
                                .getStaffRecentInvoicesForShift(userId, shiftId, 10);
                    }
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
                    BaseDialog.error(SalesStaffDashboardPanel.this, "Lỗi",
                            "Không thể tải dashboard quầy: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyData(StaffDashData data) {
        StaffDayStats stats = data.shiftStats != null
                ? data.shiftStats : new StaffDayStats(0, BigDecimal.ZERO, 0, 0);

        // Ca
        if (data.openShift != null && data.openShift.isOpen()) {
            shiftStatusCard.setValue("Đang mở");
            String since = data.openShift.getStartTime() != null
                    ? data.openShift.getStartTime().format(TIME_FMT) : "";
            shiftStatusCard.setSubtitle(since.isEmpty() ? "Ca #" + data.openShift.getShiftId()
                    : "Bắt đầu " + since);
        } else {
            shiftStatusCard.setValue("Chưa mở ca");
            shiftStatusCard.setTrend("Cần mở ca trước khi bán", false);
        }

        boolean hasOpenShift = data.openShift != null && data.openShift.isOpen();
        String shiftLabel = hasOpenShift ? "Ca #" + data.openShift.getShiftId() : "Chưa có ca OPEN";

        myRevenueCard.setValue(NumberUtil.formatThousands(stats.revenue.longValue()) + " đ");
        myRevenueCard.setSubtitle(hasOpenShift
                ? "Hóa đơn ACTIVE của " + shiftLabel
                : "Mở ca để bắt đầu tính KPI");

        myInvoiceCard.setValue(NumberUtil.formatThousands(stats.invoiceCount));
        if (stats.invoiceCount > 0 && stats.revenue.compareTo(BigDecimal.ZERO) > 0) {
            long aov = stats.revenue.longValue() / stats.invoiceCount;
            myInvoiceCard.setSubtitle("AOV " + NumberUtil.formatThousands(aov) + " đ · " + shiftLabel);
        } else {
            myInvoiceCard.setSubtitle(hasOpenShift ? "Chưa có hóa đơn trong ca" : "Chưa có ca OPEN");
        }

        myItemsCard.setValue(NumberUtil.formatThousands(stats.itemsSold));
        myItemsCard.setSubtitle(hasOpenShift ? "Số lượng SP bán trong " + shiftLabel : "Chưa có ca OPEN");

        expectedCashCard.setValue(NumberUtil.formatThousands(data.expectedCash.longValue()) + " đ");
        expectedCashCard.setSubtitle(hasOpenShift
                ? "Đầu ca + CASH + thu - chi - hoàn"
                : "Chưa có ca OPEN");

        assignedOrdersCard.setValue(NumberUtil.formatThousands(data.assignedActiveOrders));
        assignedOrdersCard.setSubtitle(data.assignedActiveOrders == 0
                ? "Không có đơn online đang phụ trách"
                : "NEW / CONFIRMED / SHIPPING");

        cancelledCard.setValue(NumberUtil.formatThousands(stats.cancelledCount));
        if (stats.cancelledCount == 0) {
            cancelledCard.setSubtitle(hasOpenShift ? "Không có hóa đơn hủy trong ca" : "Chưa có ca OPEN");
        } else {
            cancelledCard.setTrend(stats.cancelledCount + " HĐ hủy trong " + shiftLabel, false);
        }

        pendingReturnCard.setValue(NumberUtil.formatThousands(data.myPendingReturns));
        if (data.myPendingReturns == 0) {
            pendingReturnCard.setSubtitle(hasOpenShift ? "Không có đổi/trả chờ duyệt trong ca" : "Chưa có ca OPEN");
        } else {
            pendingReturnCard.setTrend(data.myPendingReturns + " đang chờ QL duyệt · " + shiftLabel, false);
        }

        pendingCancelCard.setValue(NumberUtil.formatThousands(data.pendingCancelRequests));
        if (data.pendingCancelRequests == 0) {
            pendingCancelCard.setSubtitle(hasOpenShift
                    ? "Không có yêu cầu hủy đang chờ"
                    : "Chưa có ca OPEN");
        } else {
            pendingCancelCard.setTrend(data.pendingCancelRequests
                    + " yêu cầu PENDING/PROCESSING · " + shiftLabel, false);
        }

        renderShiftDetail(data.openShift);
        renderPendingReturns(data.pendingReturnItems);
        renderRecentInvoices(data.recentInvoices);
    }

    /**
     * Công thức đối soát tiền mặt hiện tại của ca. ShiftDAO đã nạp sẵn các
     * thành phần cashSales/cashIn/cashOut/cashRefunds trong cùng ShiftID.
     */
    private static BigDecimal calculateExpectedCash(Shift shift) {
        if (shift == null) return BigDecimal.ZERO;
        BigDecimal opening = shift.getOpeningCash() != null ? shift.getOpeningCash() : BigDecimal.ZERO;
        return opening
                .add(shift.getCashSales())
                .add(shift.getCashIn())
                .subtract(shift.getCashOut())
                .subtract(shift.getCashRefunds());
    }

    // ---------------------------------------------------------------
    // Lists / detail
    // ---------------------------------------------------------------

    private void renderShiftDetail(Shift shift) {
        shiftDetailPanel.removeAll();
        if (shift == null || !shift.isOpen()) {
            shiftDetailPanel.add(emptyRow("Bạn chưa có ca đang mở. Hãy mở ca tại mục Quản lý ca trước khi bán."));
        } else {
            shiftDetailPanel.add(detailLine("Mã ca", "#" + shift.getShiftId()));
            shiftDetailPanel.add(Box.createVerticalStrut(6));
            shiftDetailPanel.add(detailLine("Bắt đầu",
                    shift.getStartTime() != null ? shift.getStartTime().format(TIME_FMT) : "—"));
            shiftDetailPanel.add(Box.createVerticalStrut(6));
            shiftDetailPanel.add(detailLine("Tiền đầu ca",
                    NumberUtil.formatThousands(
                            shift.getOpeningCash() != null ? shift.getOpeningCash().longValue() : 0) + " đ"));
            shiftDetailPanel.add(Box.createVerticalStrut(6));
            shiftDetailPanel.add(detailLine("Tiền mặt dự kiến",
                    NumberUtil.formatThousands(calculateExpectedCash(shift).longValue()) + " đ"));
            shiftDetailPanel.add(Box.createVerticalStrut(6));
            shiftDetailPanel.add(detailLine("Hóa đơn trong ca",
                    NumberUtil.formatThousands(shift.getInvoiceCount())));
            if (shift.getOpeningNote() != null && !shift.getOpeningNote().isBlank()) {
                shiftDetailPanel.add(Box.createVerticalStrut(6));
                shiftDetailPanel.add(detailLine("Ghi chú", shift.getOpeningNote()));
            }
        }
        shiftDetailPanel.revalidate();
        shiftDetailPanel.repaint();
    }

    private JPanel detailLine(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        JLabel lb = new JLabel(label);
        lb.setFont(AppFont.SMALL_BOLD);
        lb.setForeground(AppColor.TEXT_MUTED);
        JLabel val = new JLabel(value != null ? value : "—");
        val.setFont(AppFont.BODY_BOLD);
        val.setForeground(AppColor.TEXT_PRIMARY);
        row.add(lb, BorderLayout.WEST);
        row.add(val, BorderLayout.EAST);
        return row;
    }

    private void renderPendingReturns(List<PendingReturnItem> items) {
        pendingReturnListPanel.removeAll();
        if (items == null || items.isEmpty()) {
            pendingReturnListPanel.add(emptyRow("Không có yêu cầu đổi/trả đang chờ duyệt"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                pendingReturnListPanel.add(buildPendingReturnRow(items.get(i), i == items.size() - 1));
            }
        }
        pendingReturnListPanel.revalidate();
        pendingReturnListPanel.repaint();
    }

    private JPanel buildPendingReturnRow(PendingReturnItem item, boolean isLast) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        var padding = new EmptyBorder(8, 4, 8, 4);
        row.setBorder(isLast ? padding
                : new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppColor.BORDER), padding));

        Color accent = item.isExchange() ? AppColor.INFO : AppColor.WARNING;
        FontIcon icon = FontIcon.of(
                item.isExchange() ? FontAwesomeSolid.EXCHANGE_ALT : FontAwesomeSolid.UNDO, 14);
        icon.setIconColor(accent);

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
        line1.add(new StatBadge(item.isExchange() ? "Đổi" : "Trả", accent));

        String when = item.createdAt != null ? item.createdAt.format(TIME_FMT) : "";
        JLabel meta = new JLabel(when.isEmpty() ? "Chờ duyệt" : "Gửi lúc " + when);
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);

        left.add(line1);
        left.add(meta);

        JLabel value = new JLabel(NumberUtil.formatThousands(item.totalValue.longValue()) + " đ");
        value.setFont(AppFont.BODY_BOLD);
        value.setForeground(AppColor.TEXT_PRIMARY);

        row.add(circleIcon(icon, accent), BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        row.add(value, BorderLayout.EAST);
        return row;
    }

    private void renderRecentInvoices(List<StaffInvoiceItem> items) {
        recentInvoiceListPanel.removeAll();
        if (items == null || items.isEmpty()) {
            recentInvoiceListPanel.add(emptyRow("Bạn chưa tạo hóa đơn nào"));
        } else {
            for (int i = 0; i < items.size(); i++) {
                recentInvoiceListPanel.add(buildInvoiceRow(items.get(i), i == items.size() - 1));
            }
        }
        recentInvoiceListPanel.revalidate();
        recentInvoiceListPanel.repaint();
    }

    private JPanel buildInvoiceRow(StaffInvoiceItem item, boolean isLast) {
        JPanel row = new JPanel(new BorderLayout(AppSpacing.SM, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        // 52px không đủ cho: dòng mã HĐ + badge + dòng phương thức/thời gian
        // + padding trên/dưới, làm chữ meta bị cắt nửa. Giữ chiều cao 68px ổn định.
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 68));
        var padding = new EmptyBorder(8, 4, 8, 4);
        row.setBorder(isLast ? padding
                : new CompoundBorder(new MatteBorder(0, 0, 1, 0, AppColor.BORDER), padding));

        boolean cancelled = item.isCancelled();
        Color accent = cancelled ? AppColor.ERROR : AppColor.SUCCESS;
        FontIcon icon = FontIcon.of(cancelled ? FontAwesomeSolid.BAN : FontAwesomeSolid.CHECK_CIRCLE, 14);
        icon.setIconColor(accent);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);
        line1.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        JLabel code = new JLabel(item.invoiceCode != null ? item.invoiceCode : "—");
        code.setFont(AppFont.BODY_BOLD);
        code.setForeground(AppColor.TEXT_PRIMARY);
        line1.add(code);
        line1.add(new StatBadge(cancelled ? "Đã hủy" : "Hoàn tất", accent));

        String pay = paymentLabel(item.paymentMethod);
        String when = item.createdAt != null ? item.createdAt.format(TIME_FMT) : "";
        JLabel meta = new JLabel(pay + (when.isEmpty() ? "" : " · " + when));
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setBorder(new EmptyBorder(3, 0, 1, 0));
        meta.setMinimumSize(new Dimension(0, 22));
        meta.setPreferredSize(new Dimension(meta.getPreferredSize().width, 22));
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        left.add(line1);
        left.add(Box.createVerticalStrut(1));
        left.add(meta);

        JLabel amount = new JLabel(NumberUtil.formatThousands(item.totalAmount.longValue()) + " đ");
        amount.setFont(AppFont.BODY_BOLD);
        amount.setForeground(cancelled ? AppColor.TEXT_MUTED : AppColor.ACCENT);

        row.add(circleIcon(icon, accent), BorderLayout.WEST);
        row.add(left, BorderLayout.CENTER);
        row.add(amount, BorderLayout.EAST);
        return row;
    }

    private static String paymentLabel(String method) {
        if (method == null) return "—";
        return switch (method.toUpperCase()) {
            case "CASH" -> "Tiền mặt";
            case "CARD" -> "Thẻ";
            case "BANK_TRANSFER", "TRANSFER" -> "Chuyển khoản";
            case "PAYPAL" -> "PayPal";
            default -> method;
        };
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
