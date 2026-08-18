package com.view.admin.shift;

import com.components.AppAlert;
import com.components.BaseSearch;
import com.components.DatePickerField;
import com.components.EmptyState;
import com.components.FilterDropdown;
import com.components.SectionHeader;
import com.components.StatCard;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.Shift;
import com.service.ShiftService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Trang giám sát ca bán hàng dành cho Quản lý / Admin.
 * <p>
 * Lọc theo khoảng ngày bắt đầu ca, trạng thái (đang mở / tất cả),
 * tìm kiếm nhân viên. Hiển thị tổng quỹ, doanh thu, số hóa đơn.
 */
public class ShiftMonitorPanel extends JPanel {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ShiftService shiftService = new ShiftService();

    // ---- Stat cards ----
    private final StatCard openCountCard =
            new StatCard("Số ca", "0", FontAwesomeSolid.CLOCK, AppColor.ACCENT, true);
    private final StatCard totalExpectedCard =
            new StatCard("Tổng quỹ hệ thống", "0 đ", FontAwesomeSolid.CALCULATOR, AppColor.INFO, true);
    private final StatCard totalSalesCard =
            new StatCard("Doanh thu tiền mặt", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.SUCCESS, true);
    private final StatCard totalInvoicesCard =
            new StatCard("Tổng hóa đơn", "0", FontAwesomeSolid.FILE_INVOICE, AppColor.WARNING, true);

    // ---- Filters ----
    private final DatePickerField dateFrom = new DatePickerField(LocalDate.now(), true);
    private final DatePickerField dateTo = new DatePickerField(LocalDate.now(), true);
    private final FilterDropdown<String> statusFilter = new FilterDropdown<>(
            FontAwesomeSolid.FILTER,
            new String[]{"Đang mở", "Tất cả trạng thái", "Đã đóng"}
    );
    private final BaseSearch searchField = new BaseSearch("Tìm theo tên nhân viên hoặc mã ca...");
    private final JButton todayButton = new JButton("Hôm nay");
    private final JButton clearDateButton = new JButton("Xóa ngày");
    private final JButton refreshButton = new JButton("Làm mới");
    private final JLabel countLabel = new JLabel();

    // ---- Table ----
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{
                    "Mã ca", "Nhân viên", "Trạng thái", "Bắt đầu", "Đã làm",
                    "HĐ", "Tiền đầu", "DT tiền mặt", "Thu", "Chi", "Hoàn", "Quỹ hệ thống"
            }, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JTable table = new JTable(tableModel);
    private final JScrollPane tableScroll = new JScrollPane(table);
    private final JPanel tableWrapper = new JPanel(new BorderLayout());
    private final EmptyState emptyState = new EmptyState(
            FontAwesomeSolid.USER_CLOCK,
            "Không có ca nào phù hợp",
            "Thử đổi khoảng ngày hoặc bộ lọc trạng thái"
    );

    private List<Shift> loadedShifts = new ArrayList<>();
    private List<Shift> filteredShifts = new ArrayList<>();

    public ShiftMonitorPanel() {
        setLayout(new BorderLayout(0, AppSpacing.LG));
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        SectionHeader header = new SectionHeader(
                FontAwesomeSolid.DESKTOP,
                AppColor.ACCENT,
                "Giám sát ca bán hàng",
                "Theo dõi ca theo ngày — nhân viên, quỹ tiền mặt, doanh thu"
        );
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        content.add(buildStatCards());
        content.add(Box.createVerticalStrut(AppSpacing.MD));
        content.add(buildFilterBar());
        content.add(Box.createVerticalStrut(AppSpacing.SM));
        content.add(buildSearchBar());
        content.add(Box.createVerticalStrut(AppSpacing.SM));
        content.add(buildTableArea());

        add(content, BorderLayout.CENTER);

        styleTable();
        bindEvents();
        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

    // ==================== UI builders ====================

    private JPanel buildStatCards() {
        JPanel cards = new JPanel(new GridLayout(1, 4, AppSpacing.MD, 0));
        cards.setOpaque(false);
        cards.setAlignmentX(Component.LEFT_ALIGNMENT);
        cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        cards.add(openCountCard);
        cards.add(totalExpectedCard);
        cards.add(totalSalesCard);
        cards.add(totalInvoicesCard);
        return cards;
    }

    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 4));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

        JLabel fromLbl = new JLabel("Từ ngày");
        fromLbl.setFont(AppFont.BODY);
        fromLbl.setForeground(AppColor.TEXT_SECONDARY);

        JLabel toLbl = new JLabel("Đến ngày");
        toLbl.setFont(AppFont.BODY);
        toLbl.setForeground(AppColor.TEXT_SECONDARY);

        dateFrom.setPreferredSize(new Dimension(140, 36));
        dateTo.setPreferredSize(new Dimension(140, 36));
        statusFilter.setPreferredSize(new Dimension(160, 36));

        styleChipButton(todayButton, AppColor.ACCENT);
        styleChipButton(clearDateButton, AppColor.TEXT_SECONDARY);
        styleRefreshButton(refreshButton);

        bar.add(fromLbl);
        bar.add(dateFrom);
        bar.add(toLbl);
        bar.add(dateTo);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(statusFilter);
        bar.add(Box.createHorizontalStrut(8));
        bar.add(todayButton);
        bar.add(clearDateButton);
        bar.add(refreshButton);

        return bar;
    }

    private JPanel buildSearchBar() {
        JPanel bar = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        searchField.setPreferredSize(new Dimension(320, 36));
        bar.add(searchField, BorderLayout.WEST);

        countLabel.setFont(AppFont.BODY);
        countLabel.setForeground(AppColor.TEXT_SECONDARY);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        right.setOpaque(false);
        right.add(countLabel);
        bar.add(right, BorderLayout.EAST);

        return bar;
    }

    private JPanel buildTableArea() {
        tableWrapper.setOpaque(false);
        tableWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableWrapper.setLayout(new BorderLayout());

        tableScroll.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        tableScroll.getViewport().setBackground(AppColor.WHITE);
        tableScroll.setOpaque(false);

        tableWrapper.add(tableScroll, BorderLayout.CENTER);
        return tableWrapper;
    }

    private void styleChipButton(JButton btn, Color accent) {
        btn.setFont(AppFont.BUTTON);
        btn.setForeground(accent);
        btn.setBackground(AppColor.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
    }

    private void styleRefreshButton(JButton btn) {
        btn.setFont(AppFont.BUTTON);
        btn.setForeground(AppColor.TEXT_PRIMARY);
        btn.setBackground(AppColor.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 14, 8, 14)
        ));
        btn.setIcon(FontIcon.of(FontAwesomeSolid.SYNC_ALT, 14, AppColor.TEXT_SECONDARY));
    }

    private void styleTable() {
        table.setRowHeight(42);
        table.setFont(AppFont.BODY);
        table.setForeground(AppColor.TEXT_PRIMARY);
        table.setBackground(AppColor.WHITE);
        table.setSelectionBackground(new Color(
                AppColor.ACCENT.getRed(),
                AppColor.ACCENT.getGreen(),
                AppColor.ACCENT.getBlue(),
                40));
        table.setSelectionForeground(AppColor.TEXT_PRIMARY);
        table.setGridColor(new Color(
                AppColor.BORDER.getRed(),
                AppColor.BORDER.getGreen(),
                AppColor.BORDER.getBlue(),
                80));
        table.setShowHorizontalLines(true);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setFillsViewportHeight(true);
        table.setAutoCreateRowSorter(true);

        table.getTableHeader().setFont(AppFont.BODY_BOLD);
        table.getTableHeader().setForeground(AppColor.TEXT_SECONDARY);
        table.getTableHeader().setBackground(
                AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : new Color(248, 250, 252));
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 40));

        int[] widths = {70, 130, 90, 120, 90, 50, 100, 110, 90, 90, 90, 110};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);

        DefaultTableCellRenderer rightMoney = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                if (!isSelected) setForeground(AppColor.TEXT_PRIMARY);
                return c;
            }
        };

        DefaultTableCellRenderer statusRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.CENTER);
                setFont(getFont().deriveFont(Font.BOLD));
                String text = value != null ? value.toString() : "";
                if (!isSelected) {
                    if ("Đang mở".equals(text)) {
                        setForeground(AppColor.SUCCESS);
                    } else if ("Đã đóng".equals(text)) {
                        setForeground(AppColor.TEXT_MUTED);
                    } else {
                        setForeground(AppColor.TEXT_PRIMARY);
                    }
                }
                return c;
            }
        };

        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(2).setCellRenderer(statusRenderer);
        table.getColumnModel().getColumn(4).setCellRenderer(center);
        table.getColumnModel().getColumn(5).setCellRenderer(center);

        for (int col : new int[]{6, 7, 8, 9, 10}) {
            table.getColumnModel().getColumn(col).setCellRenderer(rightMoney);
        }

        table.getColumnModel().getColumn(11).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                                                           boolean isSelected, boolean hasFocus,
                                                           int row, int col) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, col);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setFont(getFont().deriveFont(Font.BOLD));
                if (!isSelected) setForeground(AppColor.INFO);
                return c;
            }
        });
    }

    // ==================== Events ====================

    private void bindEvents() {
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

        dateFrom.onChange(d -> loadData());
        dateTo.onChange(d -> loadData());

        statusFilter.onChange(s -> loadData());

        searchField.onSearch(keyword -> applyTextFilter());
        searchField.getTextField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { applyTextFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { applyTextFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { applyTextFilter(); }
        });

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    if (modelRow >= 0 && modelRow < filteredShifts.size()) {
                        showDetailDialog(filteredShifts.get(modelRow));
                    }
                }
            }
        });
    }

    // ==================== Data ====================

    private void loadData() {
        LocalDate from = dateFrom.getValue();
        LocalDate to = dateTo.getValue();

        // Đảm bảo from <= to
        if (from != null && to != null && from.isAfter(to)) {
            LocalDate tmp = from;
            from = to;
            to = tmp;
            dateFrom.setValue(from);
            dateTo.setValue(to);
        }

        String statusSel = statusFilter.getSelected();
        boolean closedOnly = "Đã đóng".equals(statusSel);
        // Chỉ query OPEN khi chọn "Đang mở"; "Tất cả" / "Đã đóng" → lấy mọi status
        boolean openOnlyQuery = "Đang mở".equals(statusSel)
                || (statusSel == null);

        final LocalDate fFrom = from;
        final LocalDate fTo = to;
        final boolean fOpenOnly = openOnlyQuery && !closedOnly;
        final boolean fClosedOnly = closedOnly;

        SwingWorker<List<Shift>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Shift> doInBackground() {
                // Khi chỉ xem "Đã đóng" vẫn query all status trong khoảng ngày rồi lọc
                return shiftService.getShiftsForMonitor(fFrom, fTo, fOpenOnly);
            }

            @Override
            protected void done() {
                try {
                    List<Shift> result = get();
                    if (result == null) result = new ArrayList<>();

                    if (fClosedOnly) {
                        result = result.stream()
                                .filter(s -> !s.isOpen())
                                .collect(Collectors.toList());
                    }

                    loadedShifts = result;
                    updateStatCards(loadedShifts);
                    applyTextFilter();
                } catch (Exception ex) {
                    AppAlert.error(ShiftMonitorPanel.this,
                            "Không tải được danh sách ca.\n" + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void applyTextFilter() {
        String q = searchField.getText() != null
                ? searchField.getText().trim().toLowerCase(Locale.ROOT)
                : "";

        if (q.isEmpty()) {
            filteredShifts = new ArrayList<>(loadedShifts);
        } else {
            filteredShifts = loadedShifts.stream()
                    .filter(s -> matches(s, q))
                    .collect(Collectors.toList());
        }

        rebuildTable(filteredShifts);
        updateCountLabel();
        toggleEmptyState(filteredShifts.isEmpty());
    }

    private boolean matches(Shift s, String q) {
        String name = s.getUserName() != null ? s.getUserName().toLowerCase(Locale.ROOT) : "";
        String id = String.valueOf(s.getShiftId());
        return name.contains(q) || id.contains(q);
    }

    private void rebuildTable(List<Shift> shifts) {
        tableModel.setRowCount(0);
        for (Shift s : shifts) {
            BigDecimal expected = expectedCash(s);
            tableModel.addRow(new Object[]{
                    s.getShiftId(),
                    s.getUserName() != null ? s.getUserName() : "—",
                    s.isOpen() ? "Đang mở" : "Đã đóng",
                    formatDateTime(s.getStartTime()),
                    s.isOpen() ? formatDuration(s.getStartTime()) : formatDateTime(s.getEndTime()),
                    s.getInvoiceCount(),
                    money(s.getOpeningCash()),
                    money(s.getCashSales()),
                    money(s.getCashIn()),
                    money(s.getCashOut()),
                    money(s.getCashRefunds()),
                    money(expected)
            });
        }
    }

    private void updateStatCards(List<Shift> shifts) {
        int count = shifts.size();
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        int totalInvoices = 0;

        for (Shift s : shifts) {
            totalExpected = totalExpected.add(expectedCash(s));
            totalSales = totalSales.add(nullSafe(s.getCashSales()));
            totalInvoices += s.getInvoiceCount();
        }

        openCountCard.setValue(String.valueOf(count));
        totalExpectedCard.setValue(money(totalExpected));
        totalSalesCard.setValue(money(totalSales));
        totalInvoicesCard.setValue(String.valueOf(totalInvoices));
    }

    private void updateCountLabel() {
        int shown = filteredShifts.size();
        int total = loadedShifts.size();
        if (shown == total) {
            countLabel.setText(total + " ca");
        } else {
            countLabel.setText("Hiển thị " + shown + " / " + total + " ca");
        }
    }

    private void toggleEmptyState(boolean empty) {
        tableWrapper.removeAll();
        if (empty) {
            tableWrapper.add(emptyState, BorderLayout.CENTER);
        } else {
            tableWrapper.add(tableScroll, BorderLayout.CENTER);
        }
        tableWrapper.revalidate();
        tableWrapper.repaint();
    }

    // ==================== Detail dialog ====================

    private void showDetailDialog(Shift shift) {
        JDialog dialog = new JDialog(
                SwingUtilities.getWindowAncestor(this),
                "Chi tiết ca #" + shift.getShiftId(),
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, AppSpacing.MD));
        root.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));
        root.setBackground(AppColor.WHITE);

        JLabel title = new JLabel("Ca #" + shift.getShiftId() + " — " +
                (shift.getUserName() != null ? shift.getUserName() : "Nhân viên"));
        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);
        root.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(0, 2, AppSpacing.MD, AppSpacing.SM));
        grid.setOpaque(false);

        addInfoRow(grid, "Nhân viên", shift.getUserName());
        addInfoRow(grid, "Trạng thái", shift.isOpen() ? "Đang mở" : "Đã đóng");
        addInfoRow(grid, "Bắt đầu", formatDateTime(shift.getStartTime()));
        addInfoRow(grid, shift.isOpen() ? "Thời gian đã làm" : "Kết thúc",
                shift.isOpen() ? formatDuration(shift.getStartTime()) : formatDateTime(shift.getEndTime()));
        addInfoRow(grid, "Số hóa đơn", String.valueOf(shift.getInvoiceCount()));
        addInfoRow(grid, "Tiền đầu ca", money(shift.getOpeningCash()));
        addInfoRow(grid, "Doanh thu tiền mặt", money(shift.getCashSales()));
        addInfoRow(grid, "Thu tiền", money(shift.getCashIn()));
        addInfoRow(grid, "Chi tiền", money(shift.getCashOut()));
        addInfoRow(grid, "Hoàn tiền mặt", money(shift.getCashRefunds()));
        addInfoRow(grid, "Quỹ hệ thống", money(expectedCash(shift)));

        if (shift.getOpeningNote() != null && !shift.getOpeningNote().isBlank()) {
            addInfoRow(grid, "Ghi chú mở ca", shift.getOpeningNote());
        }
        if (shift.getClosingNote() != null && !shift.getClosingNote().isBlank()) {
            addInfoRow(grid, "Ghi chú đóng ca", shift.getClosingNote());
        }

        root.add(grid, BorderLayout.CENTER);

        JButton closeBtn = new JButton("Đóng");
        closeBtn.setFont(AppFont.BUTTON);
        closeBtn.addActionListener(e -> dialog.dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        dialog.setContentPane(root);
        dialog.pack();
        dialog.setSize(Math.max(480, dialog.getWidth()), dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void addInfoRow(JPanel grid, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(AppFont.BODY);
        l.setForeground(AppColor.TEXT_MUTED);
        JLabel v = new JLabel(value != null ? value : "—");
        v.setFont(AppFont.BODY.deriveFont(Font.BOLD));
        v.setForeground(AppColor.TEXT_PRIMARY);
        grid.add(l);
        grid.add(v);
    }

    // ==================== Helpers ====================

    private static BigDecimal expectedCash(Shift s) {
        // Ca đã đóng: ưu tiên expectedCash đã lưu
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
        if (hours > 0) {
            return hours + " giờ " + minutes + " phút";
        }
        return minutes + " phút";
    }
}
