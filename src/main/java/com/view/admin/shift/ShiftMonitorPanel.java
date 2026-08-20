package com.view.admin.shift;

import com.components.AppAlert;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.DatePickerField;
import com.components.FilterDropdown;
import com.components.Pagination;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.table.ActionColumn;
import com.components.table.RowColorProvider;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.Shift;
import com.service.ShiftService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.FileUtil;
import com.utils.NumberUtil;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Trang giám sát + đối soát ca bán hàng.
 * QL xem ca theo ngày, lọc trạng thái, duyệt/từ chối ca chờ đối soát ngay trên bảng.
 *
 * Bảng dùng chung {@link BaseTable} (bo góc, đổ bóng, header có mũi tên sort,
 * sọc dòng, cột trạng thái dạng badge, cột Thao tác dạng icon...) để đồng bộ
 * hình thức với các trang CRUD khác (ShiftManagementPanel, AuditLogPanel,
 * InvoicePanel...) thay vì JTable tự vẽ style riêng như trước.
 */
public class ShiftMonitorPanel extends JPanel {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ShiftService shiftService = new ShiftService();

    // ── StatCard tổng quan — dùng chung với AuditLogPanel/DashboardPanel ──
    private final StatCard openCountCard =
            new StatCard("Cần xử lý", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.ERROR, true);
    private final StatCard pendingCountCard =
            new StatCard("Chờ duyệt", "0", FontAwesomeSolid.HOURGLASS_HALF, AppColor.WARNING, true);
    private final StatCard totalExpectedCard =
            new StatCard("Tổng quỹ hệ thống", "0 đ", FontAwesomeSolid.CALCULATOR, AppColor.INFO, true);
    private final StatCard totalSalesCard =
            new StatCard("Doanh thu tiền mặt", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.SUCCESS, true);

    // ── Bộ lọc — BaseSearch/FilterDropdown/DatePickerField dùng chung ──
    private final BaseSearch searchField = new BaseSearch("Tìm theo tên nhân viên hoặc mã ca...");
    private final FilterDropdown<String> statusFilter = new FilterDropdown<>(
            FontAwesomeSolid.FILTER,
            new String[]{"Tất cả trạng thái", "Đang mở", "Chờ duyệt", "Đã duyệt", "Cần kiểm lại"}
    );
    private DatePickerField dateFrom;
    private DatePickerField dateTo;

    private final JButton todayButton =
            outlineButton("Hôm nay", FontAwesomeSolid.CALENDAR_ALT);
    private final JButton clearDateButton =
            outlineButton("Xóa ngày", FontAwesomeSolid.TIMES);
    private final JButton pendingOnlyButton =
            outlineButton("Ca chờ duyệt", FontAwesomeSolid.HOURGLASS_HALF);
    private final JButton refreshButton =
            outlineButton("Làm mới", FontAwesomeSolid.SYNC_ALT);

    private final JLabel countLabel = new JLabel();

    // ── Bảng — BaseTable đồng bộ với các trang khác ──
    private final BaseTable table = buildTable();
    private final Pagination pagination = new Pagination();

    private List<Shift> loadedShifts = new ArrayList<>();
    private List<Shift> filteredShifts = new ArrayList<>();
    // Danh sách ca của TRANG hiện tại, đúng thứ tự dòng trong bảng — cho phép
    // ActionColumn (nhận modelRow) tra lại đúng Shift tương ứng.
    private List<Shift> pageShifts = new ArrayList<>();

    public ShiftMonitorPanel() {
        setLayout(new BorderLayout(0, AppSpacing.LG));
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        SectionHeader header = new SectionHeader(
                FontAwesomeSolid.DESKTOP,
                AppColor.ACCENT,
                "Giám sát & đối soát ca",
                "Theo dõi ca theo ngày — duyệt quỹ khi nhân viên đóng ca"
        );
        header.addOverflowAction("Xuất CSV", FontAwesomeSolid.FILE_CSV, () -> exportShifts("csv"));
        header.addOverflowAction("Xuất Excel", FontAwesomeSolid.FILE_EXCEL, () -> exportShifts("xlsx"));
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(buildStatCards());
        content.add(Box.createVerticalStrut(AppSpacing.MD));
        content.add(filterTableCard(buildFilterHeader(), table, pagination));

        add(content, BorderLayout.CENTER);

        pagination.setVisiblePages(5);
        bindEvents();
        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    // ══════════════════════════ LAYOUT ══════════════════════════

    private JPanel buildStatCards() {
        JPanel row = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, StatCard.PREFERRED_HEIGHT));
        row.add(openCountCard);
        row.add(pendingCountCard);
        row.add(totalExpectedCard);
        row.add(totalSalesCard);
        return row;
    }

    /**
     * Thanh công cụ 2 dòng, cùng bố cục với các trang CRUD khác:
     * dòng 1 = khoảng ngày + trạng thái + các nút lọc nhanh (bên phải),
     * dòng 2 = ô tìm kiếm (trái) + nhãn đếm số dòng (phải).
     */
    private JPanel buildFilterHeader() {
        dateFrom = new DatePickerField(LocalDate.now(), true);
        dateTo = new DatePickerField(LocalDate.now(), true);

        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        row1.setOpaque(false);

        JPanel dateAndStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        dateAndStatus.setOpaque(false);

        dateFrom.setPreferredSize(new Dimension(128, 38));
        dateFrom.setToolTipText("Từ ngày");
        dateTo.setPreferredSize(new Dimension(128, 38));
        dateTo.setToolTipText("Đến ngày");
        JLabel sep = new JLabel("–");
        sep.setFont(AppFont.BODY);
        sep.setForeground(AppColor.TEXT_MUTED);

        JPanel dateRange = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        dateRange.setOpaque(false);
        dateRange.add(dateFrom);
        dateRange.add(sep);
        dateRange.add(dateTo);

        statusFilter.setPreferredSize(new Dimension(160, 38));

        dateAndStatus.add(dateRange);
        dateAndStatus.add(statusFilter);

        JPanel quickActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));
        quickActions.setOpaque(false);
        quickActions.add(todayButton);
        quickActions.add(clearDateButton);
        quickActions.add(pendingOnlyButton);
        quickActions.add(refreshButton);

        row1.add(dateAndStatus, BorderLayout.WEST);
        row1.add(quickActions, BorderLayout.EAST);

        JPanel row2 = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        row2.setOpaque(false);
        row2.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));
        searchField.setPreferredSize(new Dimension(340, 38));
        row2.add(searchField, BorderLayout.WEST);
        countLabel.setFont(AppFont.SMALL);
        countLabel.setForeground(AppColor.TEXT_MUTED);
        row2.add(countLabel, BorderLayout.EAST);

        wrap.add(row1);
        wrap.add(row2);
        return wrap;
    }

    /**
     * Card bao ngoài: thanh lọc (trên) + BaseTable (giữa, tự có bo góc/đổ
     * bóng riêng) + phân trang (dưới) — cùng khuôn mẫu filterTableCard() của
     * ShiftManagementPanel/AuditLogPanel để mọi trang danh sách trong app
     * trông giống hệt nhau.
     */
    private JPanel filterTableCard(JComponent header, BaseTable table, JComponent paginationComp) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel toolbarWrapper = new JPanel(new BorderLayout());
        toolbarWrapper.setBackground(AppColor.WHITE);
        toolbarWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(14, 16, 14, 16)
        ));
        toolbarWrapper.add(header, BorderLayout.CENTER);

        JPanel paginationWrapper = new JPanel(new BorderLayout());
        paginationWrapper.setBackground(AppColor.WHITE);
        paginationWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER));
        paginationWrapper.add(paginationComp, BorderLayout.CENTER);

        card.add(toolbarWrapper, BorderLayout.NORTH);
        card.add(table, BorderLayout.CENTER);
        card.add(paginationWrapper, BorderLayout.SOUTH);
        return card;
    }

    // ══════════════════════════ BẢNG ══════════════════════════

    private static BaseTable buildTable() {
        // Gộp "Bắt đầu" + "Đã làm / KT" thành 1 cột "Thời gian" (2 dòng: mốc
        // bắt đầu + trạng thái đang làm bao lâu/đã kết thúc lúc nào), đồng
        // thời bỏ các cột DT tiền mặt/Thu/Chi/Hoàn/Quỹ hệ thống khỏi bảng
        // chính cho đỡ rối mắt — 5 số liệu này vẫn xem đầy đủ trong "Chi
        // tiết" (buildFundBreakdownCard) và trong Xuất CSV/Excel, bảng ở
        // đây chỉ cần đủ để quét nhanh + ra quyết định duyệt/từ chối.
        BaseTable table = new BaseTable(new String[]{
                "Mã ca", "Nhân viên", "Đối soát", "Thời gian", "HĐ", "Tiền đầu", "Chênh lệch"
        });
        table.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.enableSorting();

        // Cột "Trạng thái" -> badge pill, cùng bảng màu với ShiftManagementPanel.
        table.setBadgeColumn(
                2,
                value -> String.valueOf(value),
                value -> {
                    String v = String.valueOf(value);
                    if ("Đang mở".equals(v)) return AppColor.SUCCESS;
                    if ("Chờ duyệt".equals(v)) return AppColor.WARNING;
                    if ("Cần kiểm lại".equals(v)) return AppColor.ERROR;
                    return AppColor.TEXT_MUTED;
                });

        // "Mã ca" và "HĐ" -> căn giữa, cho dễ quét mắt theo cột.
        TableCellRenderer center = centeredRenderer(table.rowColorProvider());
        table.setCustomColumn(0, center);
        table.setCustomColumn(4, center);

        // "Thời gian" -> 2 dòng (mốc bắt đầu + phụ đề mờ bên dưới).
        table.setCustomColumn(3, timeRenderer(table.rowColorProvider()));

        // "Tiền đầu" va "Chênh lệch" -> can phai.
        table.setCustomColumn(5, moneyRenderer(table.rowColorProvider(), false));
        table.setCustomColumn(6, signedMoneyRenderer(table.rowColorProvider()));

        table.setColumnWidths(70, 150, 110, 210, 55, 120, 120);
        table.setRowHeight(52);
        return table;
    }

    private void installActionColumn() {
        ActionColumn actions = new ActionColumn()
                .header("Thao tác")
                .add("approve", FontAwesomeSolid.CHECK_CIRCLE, AppColor.SUCCESS, "Duyệt đối soát",
                        this::approveAtModelRow, this::isPendingAtModelRow)
                .add("reject", FontAwesomeSolid.TIMES_CIRCLE, AppColor.ERROR, "Yêu cầu kiểm lại",
                        this::rejectAtModelRow, this::isPendingAtModelRow)
                .add("detail", FontAwesomeSolid.EYE, AppColor.ACCENT, "Xem chi tiết",
                        this::detailAtModelRow);
        table.setActionColumn(actions);
    }

    private static TableCellRenderer centeredRenderer(RowColorProvider rowColorProvider) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setForeground(AppColor.TABLE_ROW_TEXT != null ? AppColor.TABLE_ROW_TEXT : AppColor.TEXT_PRIMARY);
                c.setHorizontalAlignment(SwingConstants.CENTER);
                c.setBorder(new EmptyBorder(0, 6, 0, 6));
                return c;
            }
        };
    }

    private static TableCellRenderer signedMoneyRenderer(RowColorProvider rowColorProvider) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setHorizontalAlignment(SwingConstants.RIGHT);
                c.setBorder(new EmptyBorder(0, 8, 0, 12));
                c.setFont(AppFont.BODY_BOLD);
                String text = String.valueOf(value);
                c.setForeground(text.startsWith("-") ? AppColor.ERROR
                        : text.startsWith("+") ? AppColor.WARNING : AppColor.SUCCESS);
                return c;
            }
        };
    }

    private static int riskScore(Shift s) {
        if (s == null) return 0;

        BigDecimal d = s.getCashDifference();
        boolean hasDifference = d != null && d.signum() != 0;

        // Chi uu tien cac ca con can hanh dong. Ca da duyet khong nen noi len
        // chi vi trong lich su tung co chenh lech da duoc quan ly chap nhan.
        if (s.isRejected()) return 120 + (hasDifference ? 30 : 0);
        if (s.isPendingApproval()) return 80 + (hasDifference ? 30 : 0);
        if (s.isOpen()) return 10;
        return 0;
    }

    private static String signedMoney(BigDecimal value) {
        if (value == null) return "—";
        int sign = value.signum();
        if (sign == 0) return "0 đ";
        String formatted = money(value.abs());
        return (sign > 0 ? "+" : "-") + formatted;
    }

    /**
     * Renderer cho cột "Thời gian" gộp: dòng 1 = mốc bắt đầu (đậm, màu chữ
     * chính), dòng 2 = phụ đề mờ — "→ giờ kết thúc" nếu ca đã đóng, hoặc
     * "● Đang mở · đã làm bao lâu" nếu ca còn mở. Value truyền vào đã là
     * chuỗi HTML dựng sẵn ở {@link #timeRangeHtml}, JLabel tự render HTML.
     */
    private static TableCellRenderer timeRenderer(RowColorProvider rowColorProvider) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setForeground(AppColor.TABLE_ROW_TEXT != null ? AppColor.TABLE_ROW_TEXT : AppColor.TEXT_PRIMARY);
                c.setHorizontalAlignment(SwingConstants.LEFT);
                c.setVerticalAlignment(SwingConstants.CENTER);
                c.setBorder(new EmptyBorder(2, 10, 2, 8));
                c.setFont(AppFont.BODY);
                return c;
            }
        };
    }

    /** Chuỗi HTML 2 dòng cho cột "Thời gian", theo đúng theme màu đang bật. */
    private static String timeRangeHtml(Shift s) {
        String start = formatDateTime(s.getStartTime());
        String sub;
        if (s.isOpen()) {
            sub = "<span style='color:" + toHex(AppColor.SUCCESS) + "'>&#9679;</span> Đang mở · "
                    + formatDuration(s.getStartTime());
        } else {
            sub = "&rarr; " + formatDateTime(s.getEndTime());
        }
        String mutedHex = toHex(AppColor.TEXT_MUTED);
        return "<html><body style='margin:0;padding:0;white-space:nowrap;'>"
                + start
                + "<br><span style='color:" + mutedHex + ";font-size:10px;'>" + sub + "</span>"
                + "</body></html>";
    }

    private static String toHex(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static TableCellRenderer moneyRenderer(RowColorProvider rowColorProvider, boolean bold) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setHorizontalAlignment(SwingConstants.RIGHT);
                c.setBorder(new EmptyBorder(0, 8, 0, 14));
                c.setFont(bold ? AppFont.BODY_BOLD : AppFont.BODY);
                c.setForeground(bold ? AppColor.TEXT_PRIMARY
                        : (AppColor.TABLE_ROW_TEXT != null ? AppColor.TABLE_ROW_TEXT : AppColor.TEXT_PRIMARY));
                return c;
            }
        };
    }

    // ══════════════════════════ SỰ KIỆN ══════════════════════════

    private void bindEvents() {
        installActionColumn();

        refreshButton.addActionListener(e -> loadData());
        todayButton.addActionListener(e -> {
            LocalDate today = LocalDate.now();
            dateFrom.setValue(today);
            dateTo.setValue(today);
            loadData();
        });
        clearDateButton.addActionListener(e -> {
            dateFrom.setValue(null);
            dateTo.setValue(null);
            loadData();
        });
        pendingOnlyButton.addActionListener(e -> {
            statusFilter.setSelected("Chờ duyệt");
            pagination.setCurrentPage(1);
            loadData();
        });
        dateFrom.onChange(d -> { pagination.setCurrentPage(1); loadData(); });
        dateTo.onChange(d -> { pagination.setCurrentPage(1); loadData(); });
        statusFilter.onChange(s -> { pagination.setCurrentPage(1); loadData(); });

        searchField.onSearch(k -> { pagination.setCurrentPage(1); applyTextFilterAndRender(); });
        searchField.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                pagination.setCurrentPage(1); applyTextFilterAndRender();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                pagination.setCurrentPage(1); applyTextFilterAndRender();
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) {
                pagination.setCurrentPage(1); applyTextFilterAndRender();
            }
        });

        pagination.addPropertyChangeListener("pageChanged", e -> renderPage());
        pagination.addPropertyChangeListener("pageSizeChanged", e -> {
            pagination.setCurrentPage(1);
            renderPage();
        });

        table.getTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getTable().getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Shift shift = shiftAtModelRow(modelRow);
                    if (shift != null) showDetailDialog(shift);
                }
            }
        });
    }

    // ══════════════════════════ DỮ LIỆU ══════════════════════════

    private void loadData() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
            dateFrom.setValue(from);
            dateTo.setValue(to);
        }

        String statusSel = statusFilter.getSelected();
        boolean openOnlyQuery = "Đang mở".equals(statusSel);

        final LocalDate fFrom = from;
        final LocalDate fTo = to;
        final boolean fOpenOnly = openOnlyQuery;
        final String fStatusSel = statusSel != null ? statusSel : "Tất cả trạng thái";

        SwingWorker<List<Shift>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Shift> doInBackground() {
                return shiftService.getShiftsForMonitor(fFrom, fTo, fOpenOnly);
            }

            @Override
            protected void done() {
                try {
                    List<Shift> result = get();
                    if (result == null) result = new ArrayList<>();
                    // P6: ca rui ro/can xu ly len dau truoc khi phan trang.
                    result.sort(Comparator
                            .comparingInt(ShiftMonitorPanel::riskScore).reversed()
                            .thenComparing(Shift::getStartTime, Comparator.nullsLast(Comparator.reverseOrder())));
                    if (!"Tất cả trạng thái".equals(fStatusSel) && !"Đang mở".equals(fStatusSel)) {
                        result = result.stream()
                                .filter(s -> fStatusSel.equals(s.getStatusLabel()))
                                .collect(Collectors.toList());
                    }
                    loadedShifts = result;
                    applyTextFilterAndRender();
                } catch (Exception ex) {
                    AppAlert.error(ShiftMonitorPanel.this,
                            "Không tải được danh sách ca.\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyTextFilterAndRender() {
        String q = searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) {
            filteredShifts = new ArrayList<>(loadedShifts);
        } else {
            filteredShifts = loadedShifts.stream()
                    .filter(s -> matches(s, q))
                    .collect(Collectors.toList());
        }
        updateStatCards(filteredShifts);
        pagination.setTotalItems(filteredShifts.size());
        renderPage();
    }

    private boolean matches(Shift s, String q) {
        if (String.valueOf(s.getShiftId()).contains(q)) return true;
        if (s.getUserName() != null && s.getUserName().toLowerCase(Locale.ROOT).contains(q)) return true;
        if (s.getStatusLabel() != null && s.getStatusLabel().toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private void renderPage() {
        table.clear();
        pageShifts = new ArrayList<>();

        int pageSize = pagination.getPageSize();
        int page = Math.max(1, pagination.getCurrentPage());
        int fromIdx = (page - 1) * pageSize;
        if (fromIdx >= filteredShifts.size() && !filteredShifts.isEmpty()) {
            pagination.setCurrentPage(1);
            page = 1;
            fromIdx = 0;
        }
        int toIdx = Math.min(fromIdx + pageSize, filteredShifts.size());

        for (int i = fromIdx; i < toIdx; i++) {
            Shift s = filteredShifts.get(i);
            pageShifts.add(s);
            // Cột bảng: Mã ca | NV | Đối soát | Thời gian | HĐ | Tiền đầu | Chênh lệch
            table.addRow(new Object[]{
                    "#" + s.getShiftId(),
                    s.getUserName() != null ? s.getUserName() : "—",
                    s.getStatusLabel(),
                    timeRangeHtml(s),
                    s.getInvoiceCount(),
                    money(s.getOpeningCash()),
                    signedMoney(s.getCashDifference())
            });
        }
        updateCountLabel();
    }

    private Shift shiftAtModelRow(int modelRow) {
        if (modelRow >= 0 && modelRow < pageShifts.size()) {
            return pageShifts.get(modelRow);
        }
        return null;
    }

    private boolean isPendingAtModelRow(int modelRow) {
        Shift s = shiftAtModelRow(modelRow);
        return s != null && s.isPendingApproval() && shiftService.canApproveReconciliation();
    }

    private void approveAtModelRow(int modelRow) {
        doApprove(shiftAtModelRow(modelRow));
    }

    private void rejectAtModelRow(int modelRow) {
        doReject(shiftAtModelRow(modelRow));
    }

    private void detailAtModelRow(int modelRow) {
        Shift s = shiftAtModelRow(modelRow);
        if (s != null) showDetailDialog(s);
    }

    private void updateStatCards(List<Shift> shifts) {
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        int pending = 0;
        int risk = 0;
        for (Shift s : shifts) {
            totalExpected = totalExpected.add(expectedCash(s));
            totalSales = totalSales.add(nullSafe(s.getCashSales()));
            if (s.isPendingApproval()) pending++;
            if (s.isPendingApproval() || s.isRejected()) risk++;
        }
        openCountCard.setValue(String.valueOf(risk));
        pendingCountCard.setValue(String.valueOf(pending));
        totalExpectedCard.setValue(money(totalExpected));
        totalSalesCard.setValue(money(totalSales));
    }

    private void updateCountLabel() {
        int total = filteredShifts.size();
        int pageSize = pagination.getPageSize();
        int page = Math.max(1, pagination.getCurrentPage());
        if (total == 0) {
            countLabel.setText("0 ca");
            return;
        }
        int from = (page - 1) * pageSize + 1;
        int to = Math.min(page * pageSize, total);
        countLabel.setText(from + "–" + to + " / " + total + " ca");
    }

    // ══════════════════════════ XUẤT DỮ LIỆU ══════════════════════════

    private void exportShifts(String format) {
        if (filteredShifts.isEmpty()) {
            AppAlert.warning(this, "Không có dữ liệu",
                    "Không có ca nào phù hợp với bộ lọc hiện tại để xuất.");
            return;
        }

        String[] headers = {
                "Mã ca", "Nhân viên", "Trạng thái", "Bắt đầu", "Kết thúc",
                "Hóa đơn", "Tiền đầu", "DT tiền mặt", "Thu", "Chi", "Hoàn", "Quỹ hệ thống"
        };
        List<Object[]> rows = new ArrayList<>();
        for (Shift s : filteredShifts) {
            rows.add(new Object[]{
                    "#" + s.getShiftId(),
                    s.getUserName(),
                    s.getStatusLabel(),
                    formatDateTime(s.getStartTime()),
                    formatDateTime(s.getEndTime()),
                    s.getInvoiceCount(),
                    money(s.getOpeningCash()),
                    money(s.getCashSales()),
                    money(s.getCashIn()),
                    money(s.getCashOut()),
                    money(s.getCashRefunds()),
                    money(expectedCash(s))
            });
        }

        String defaultFileName = "giam_sat_ca_" + exportTimestamp() + "." + format;
        File chosen = FileUtil.chooseSaveLocation(this, defaultFileName);
        if (chosen == null) return;
        File file = ensureExportExtension(chosen, format);

        try {
            if ("xlsx".equalsIgnoreCase(format)) {
                TableExportUtil.exportExcel(file, "Giám sát ca", headers, rows);
            } else {
                TableExportUtil.exportCsv(file, headers, rows);
            }
            AppAlert.success(this, "Đã xuất " + rows.size() + " ca ra " + file.getName());
        } catch (IOException ex) {
            AppAlert.error(this, "Xuất file thất bại.\n" + ex.getMessage());
        }
    }

    private static File ensureExportExtension(File file, String extension) {
        String name = file.getName();
        if (name.toLowerCase(Locale.ROOT).endsWith("." + extension.toLowerCase(Locale.ROOT))) {
            return file;
        }
        int dot = name.lastIndexOf('.');
        String baseName = dot > 0 ? name.substring(0, dot) : name;
        return new File(file.getParentFile(), baseName + "." + extension);
    }

    private static String exportTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    }

    // ══════════════════════════ NGHIỆP VỤ DUYỆT / TỪ CHỐI ══════════════════════════

    private void doApprove(Shift shift) {
        if (shift == null || !shift.isPendingApproval()) {
            AppAlert.error(this, "Chỉ duyệt được ca đang chờ đối soát.");
            return;
        }

        // Đồng bộ UI form hệ thống (banner + tóm tắt quỹ + ghi chú tùy chọn)
        String note = ShiftReviewDialog.show(this, ShiftReviewDialog.Mode.APPROVE, shift);
        if (note == null) return; // Hủy

        ShiftService.OperationResult<Shift> r = shiftService.approveShift(
                shift.getShiftId(), note.isBlank() ? null : note);
        if (r.isSuccess()) {
            AppAlert.success(this, r.getMessage());
            loadData();
        } else {
            AppAlert.error(this, r.getMessage());
        }
    }

    private void doReject(Shift shift) {
        if (shift == null || !shift.isPendingApproval()) {
            AppAlert.error(this, "Chỉ yêu cầu kiểm lại được ca đang chờ đối soát.");
            return;
        }

        String note = ShiftReviewDialog.show(this, ShiftReviewDialog.Mode.REJECT, shift);
        if (note == null) return; // Hủy
        if (note.isBlank()) {
            AppAlert.error(this, "Phải nhập lý do yêu cầu kiểm lại.");
            return;
        }

        ShiftService.OperationResult<Shift> r = shiftService.rejectShift(shift.getShiftId(), note);
        if (r.isSuccess()) {
            AppAlert.success(this, r.getMessage());
            loadData();
        } else {
            AppAlert.error(this, r.getMessage());
        }
    }

    // ══════════════════════════ DIALOG CHI TIẾT ══════════════════════════

    private void showDetailDialog(Shift shift) {
        if (shift == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, "Chi tiết ca #" + shift.getShiftId(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(241, 245, 249));

        // ── Header ──────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(true);
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 22, 16, 22)
        ));

        JPanel titleBlock = new JPanel();
        titleBlock.setOpaque(false);
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Ca #" + shift.getShiftId());
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(title);

        titleBlock.add(Box.createVerticalStrut(4));
        JLabel subtitle = new JLabel(
                (shift.getUserName() != null ? shift.getUserName() : "—")
                + "  ·  "
                + formatDateTime(shift.getStartTime())
                + (shift.isOpen()
                    ? "  ·  " + formatDuration(shift.getStartTime())
                    : " → " + formatDateTime(shift.getEndTime()))
        );
        subtitle.setFont(AppFont.SMALL);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleBlock.add(subtitle);

        header.add(titleBlock, BorderLayout.CENTER);
        header.add(statusBadge(shift), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ── Body (scroll) ───────────────────────────────────────────────
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(16, 18, 12, 18));

        body.add(buildFundSummaryCard(shift));
        body.add(Box.createVerticalStrut(12));
        body.add(buildFundBreakdownCard(shift));
        body.add(Box.createVerticalStrut(12));
        body.add(buildInfoCard(shift));

        JPanel notes = buildNotesCard(shift);
        if (notes != null) {
            body.add(Box.createVerticalStrut(12));
            body.add(notes);
        }

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(241, 245, 249));
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        root.add(scroll, BorderLayout.CENTER);

        // ── Footer ──────────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(true);
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 18, 14, 18)
        ));

        if (shift.isPendingApproval()) {
            JButton rejectBtn = primaryActionButton("Yêu cầu kiểm lại", AppColor.ERROR);
            rejectBtn.addActionListener(e -> {
                dialog.dispose();
                doReject(shift);
            });
            JButton approveBtn = primaryActionButton("Duyệt đối soát", AppColor.SUCCESS);
            approveBtn.addActionListener(e -> {
                dialog.dispose();
                doApprove(shift);
            });
            footer.add(rejectBtn);
            footer.add(approveBtn);
        }

        JButton closeBtn = new JButton("Đóng");
        closeBtn.setFont(AppFont.BUTTON);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dialog.dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.setSize(520, 640);
        dialog.setMinimumSize(new Dimension(460, 480));
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /** Badge trạng thái góc phải header. */
    private JComponent statusBadge(Shift shift) {
        Color c = AppColor.TEXT_MUTED;
        if (shift.isOpen()) c = AppColor.SUCCESS;
        else if (shift.isPendingApproval()) c = AppColor.WARNING;
        else if (shift.isRejected()) c = AppColor.ERROR;
        else if (shift.isClosed()) c = AppColor.INFO != null ? AppColor.INFO : AppColor.ACCENT;

        JLabel badge = new JLabel(shift.getStatusLabel());
        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(c);
        badge.setOpaque(true);
        badge.setBackground(new Color(c.getRed(), c.getGreen(), c.getBlue(), 28));
        badge.setBorder(new EmptyBorder(6, 14, 6, 14));
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        wrap.setOpaque(false);
        wrap.add(badge);
        return wrap;
    }

    /**
     * Card tóm tắt: Quỹ hệ thống | Tiền đếm | Chênh lệch.
     * Chênh lệch tô màu xanh/đỏ/xám.
     */
    private JPanel buildFundSummaryCard(Shift shift) {
        JPanel card = sectionCard("Đối soát quỹ");

        BigDecimal expected = expectedCash(shift);
        BigDecimal counted = shift.isOpen() ? null : shift.getCountedCash();
        BigDecimal diff = shift.isOpen() ? null : shift.getCashDifference();

        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        row.add(metricBox("Quỹ hệ thống", money(expected), AppColor.TEXT_PRIMARY,
                "Tiền đầu + DT TM + Thu − Chi − Hoàn"));
        row.add(metricBox(
                "Tiền đếm thực tế",
                counted != null ? money(counted) : "—",
                AppColor.TEXT_PRIMARY,
                shift.isOpen() ? "Chỉ có sau khi NV đóng ca" : "Số NV nhập khi đóng ca"
        ));

        Color diffColor = AppColor.TEXT_MUTED;
        String diffText = "—";
        String diffHint = "Counted − Quỹ hệ thống";
        if (diff != null) {
            int sign = diff.signum();
            if (sign == 0) {
                diffColor = AppColor.SUCCESS;
                diffText = "Khớp · 0 đ";
                diffHint = "Không chênh lệch";
            } else if (sign > 0) {
                diffColor = AppColor.WARNING;
                diffText = "+" + money(diff);
                diffHint = "Thừa so với hệ thống";
            } else {
                diffColor = AppColor.ERROR;
                diffText = money(diff); // đã có dấu âm
                diffHint = "Thiếu so với hệ thống";
            }
        }
        row.add(metricBox("Chênh lệch", diffText, diffColor, diffHint));

        card.add(Box.createVerticalStrut(10));
        card.add(row);
        return card;
    }

    private JPanel metricBox(String label, String value, Color valueColor, String hint) {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(true);
        box.setBackground(AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252));
        box.setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel l = new JLabel(label);
        l.setFont(AppFont.SMALL);
        l.setForeground(AppColor.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(l);

        box.add(Box.createVerticalStrut(4));
        JLabel v = new JLabel(value);
        v.setFont(AppFont.HEADING_MD != null ? AppFont.HEADING_MD : AppFont.BODY.deriveFont(Font.BOLD, 16f));
        v.setForeground(valueColor);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(v);

        box.add(Box.createVerticalStrut(2));
        JLabel h = new JLabel("<html><div style='width:120px'>" + hint + "</div></html>");
        h.setFont(AppFont.FOOTER != null ? AppFont.FOOTER : AppFont.SMALL);
        h.setForeground(AppColor.TEXT_MUTED);
        h.setAlignmentX(Component.LEFT_ALIGNMENT);
        box.add(h);
        return box;
    }

    /** Card công thức: từng dòng cộng/trừ tạo quỹ hệ thống. */
    private JPanel buildFundBreakdownCard(Shift shift) {
        JPanel card = sectionCard("Chi tiết dòng tiền trong ca");

        card.add(Box.createVerticalStrut(8));
        card.add(kvRow("Tiền đầu ca", money(shift.getOpeningCash()), false));
        card.add(kvRow("Doanh thu tiền mặt (" + shift.getInvoiceCount() + " HĐ)", money(shift.getCashSales()), false));
        card.add(kvRow("Thu tiền (CASH_IN)", money(shift.getCashIn()), false));
        card.add(kvRow("Chi tiền (CASH_OUT)", money(shift.getCashOut()), true));
        card.add(kvRow("Hoàn tiền mặt", money(shift.getCashRefunds()), true));

        card.add(Box.createVerticalStrut(6));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(sep);
        card.add(Box.createVerticalStrut(6));
        card.add(kvRowBold("= Quỹ hệ thống", money(expectedCash(shift))));

        return card;
    }

    /** Card thông tin ca + người xử lý. */
    private JPanel buildInfoCard(Shift shift) {
        JPanel card = sectionCard("Thông tin ca");

        card.add(Box.createVerticalStrut(8));
        card.add(kvRow("Nhân viên", nullToDash(shift.getUserName()), false));
        card.add(kvRow("Bắt đầu", formatDateTime(shift.getStartTime()), false));
        if (shift.isOpen()) {
            card.add(kvRow("Đã làm", formatDuration(shift.getStartTime()), false));
        } else {
            card.add(kvRow("Kết thúc", formatDateTime(shift.getEndTime()), false));
        }
        if (shift.getClosedByName() != null) {
            card.add(kvRow("Người đóng ca", shift.getClosedByName(), false));
        }
        if (shift.getApprovedByName() != null) {
            card.add(kvRow("Người duyệt", shift.getApprovedByName(), false));
        }
        if (shift.getApprovedAt() != null) {
            card.add(kvRow("Thời điểm duyệt", formatDateTime(shift.getApprovedAt()), false));
        }
        return card;
    }

    private JPanel buildNotesCard(Shift shift) {
        boolean hasOpen = shift.getOpeningNote() != null && !shift.getOpeningNote().isBlank();
        boolean hasClose = shift.getClosingNote() != null && !shift.getClosingNote().isBlank();
        boolean hasApproval = shift.getApprovalNote() != null && !shift.getApprovalNote().isBlank();
        if (!hasOpen && !hasClose && !hasApproval) return null;

        JPanel card = sectionCard("Ghi chú");
        card.add(Box.createVerticalStrut(8));
        if (hasOpen) {
            card.add(noteBlock("Mở ca", shift.getOpeningNote()));
        }
        if (hasClose) {
            if (hasOpen) card.add(Box.createVerticalStrut(8));
            card.add(noteBlock("Đóng ca (NV)", shift.getClosingNote()));
        }
        if (hasApproval) {
            if (hasOpen || hasClose) card.add(Box.createVerticalStrut(8));
            card.add(noteBlock("Phản hồi quản lý", shift.getApprovalNote()));
        }
        return card;
    }

    private JPanel noteBlock(String label, String text) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel l = new JLabel(label);
        l.setFont(AppFont.SMALL_BOLD);
        l.setForeground(AppColor.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(l);

        JTextArea area = new JTextArea(text);
        area.setFont(AppFont.BODY);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setOpaque(true);
        area.setBackground(AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252));
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        area.setFocusable(false);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        area.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        p.add(Box.createVerticalStrut(4));
        p.add(area);
        return p;
    }

    private JPanel sectionCard(String title) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(14, 16, 14, 16)
        ));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        JLabel t = new JLabel(title);
        t.setFont(AppFont.BODY_BOLD);
        t.setForeground(AppColor.TEXT_PRIMARY);
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(t);
        return card;
    }

    private JPanel kvRow(String label, String value, boolean negative) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        row.setBorder(new EmptyBorder(3, 0, 3, 0));

        JLabel l = new JLabel(label);
        l.setFont(AppFont.BODY);
        l.setForeground(AppColor.TEXT_MUTED);

        JLabel v = new JLabel(value != null ? value : "—");
        v.setFont(AppFont.BODY);
        v.setForeground(negative ? AppColor.ERROR : AppColor.TEXT_PRIMARY);
        v.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private JPanel kvRowBold(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        row.setBorder(new EmptyBorder(4, 0, 2, 0));

        JLabel l = new JLabel(label);
        l.setFont(AppFont.BODY_BOLD);
        l.setForeground(AppColor.TEXT_PRIMARY);

        JLabel v = new JLabel(value != null ? value : "—");
        v.setFont(AppFont.BODY_BOLD);
        v.setForeground(AppColor.ACCENT);
        v.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(l, BorderLayout.WEST);
        row.add(v, BorderLayout.EAST);
        return row;
    }

    private static String nullToDash(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private static JButton primaryActionButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(AppFont.BUTTON);
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        return btn;
    }

    /**
     * Nút phụ viền mảnh cho thanh lọc (Hôm nay/Xóa ngày/Ca chờ duyệt/Làm mới)
     * — cùng kiểu vẽ (bo góc, hover/pressed) với outlineButton() của
     * ShiftManagementPanel để 2 trang trong cùng module trông đồng bộ.
     */
    private static JButton outlineButton(String text, FontAwesomeSolid iconCode) {
        FontIcon icon = FontIcon.of(iconCode, 13);
        icon.setIconColor(AppColor.TEXT_SECONDARY);

        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color background = AppColor.WHITE;
                Color borderColor = AppColor.BORDER;
                Color foreground = AppColor.TEXT_SECONDARY;

                if (!isEnabled()) {
                    foreground = AppColor.TEXT_DISABLED;
                } else if (getModel().isPressed()) {
                    background = AppColor.BG_LIGHT;
                } else if (getModel().isRollover()) {
                    background = AppColor.BG_LIGHTER;
                    borderColor = AppColor.TEXT_MUTED;
                }

                g2.setColor(background);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);

                icon.setIconColor(foreground);
                String label = getText();
                FontMetrics metrics = g2.getFontMetrics(getFont());
                int gap = 7;
                int contentWidth = icon.getIconWidth() + gap + metrics.stringWidth(label);
                int startX = (getWidth() - contentWidth) / 2;
                int iconY = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g2, startX, iconY);

                int textX = startX + icon.getIconWidth() + gap;
                int textY = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g2.setFont(getFont());
                g2.setColor(foreground);
                g2.drawString(label, textX, textY);

                g2.dispose();
            }
        };

        button.setFont(AppFont.SMALL_BOLD != null ? AppFont.SMALL_BOLD : AppFont.BUTTON);
        button.setForeground(AppColor.TEXT_SECONDARY);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setOpaque(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(7, 12, 7, 12));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static BigDecimal expectedCash(Shift s) {
        if (!s.isOpen() && s.getExpectedCash() != null) {
            return s.getExpectedCash();
        }
        return nullSafe(s.getOpeningCash())
                .add(nullSafe(s.getCashSales()))
                .add(nullSafe(s.getCashIn()))
                .subtract(nullSafe(s.getCashOut()))
                .subtract(nullSafe(s.getCashRefunds()));
    }

    private static BigDecimal nullSafe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String money(BigDecimal value) {
        if (value == null) return "0 đ";
        return NumberUtil.formatThousands(value.longValue()) + " đ";
    }

    private static String formatDateTime(LocalDateTime t) {
        return t == null ? "—" : t.format(DATE_TIME);
    }

    private static String formatDuration(LocalDateTime start) {
        if (start == null) return "—";
        Duration d = Duration.between(start, LocalDateTime.now());
        long hours = d.toHours();
        long minutes = d.toMinutes() % 60;
        if (hours > 0) return hours + " giờ " + minutes + " phút";
        return minutes + " phút";
    }
}