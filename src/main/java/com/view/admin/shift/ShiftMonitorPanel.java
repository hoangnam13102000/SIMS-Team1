package com.view.admin.shift;

import com.components.AppAlert;
import com.components.BaseSearch;
import com.components.DatePickerField;
import com.components.EmptyState;
import com.components.FilterDropdown;
import com.components.Pagination;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.table.RowColorProvider;
import com.components.table.StatusColumn;
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
import javax.swing.table.TableCellRenderer;
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
 * Trang giám sát ca bán hàng — UI bảng + phân trang đồng bộ
 * với ShiftManagementPanel / các trang CRUD (filterTableCard, buildTable, Pagination).
 */
public class ShiftMonitorPanel extends JPanel {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ShiftService shiftService = new ShiftService();

    private final StatCard openCountCard =
            new StatCard("Số ca", "0", FontAwesomeSolid.CLOCK, AppColor.ACCENT, true);
    private final StatCard totalExpectedCard =
            new StatCard("Tổng quỹ hệ thống", "0 đ", FontAwesomeSolid.CALCULATOR, AppColor.INFO, true);
    private final StatCard totalSalesCard =
            new StatCard("Doanh thu tiền mặt", "0 đ", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.SUCCESS, true);
    private final StatCard totalInvoicesCard =
            new StatCard("Tổng hóa đơn", "0", FontAwesomeSolid.FILE_INVOICE, AppColor.WARNING, true);

    private final DatePickerField dateFrom = new DatePickerField(LocalDate.now(), true);
    private final DatePickerField dateTo = new DatePickerField(LocalDate.now(), true);
    private final FilterDropdown<String> statusFilter = new FilterDropdown<>(
            FontAwesomeSolid.FILTER,
            new String[]{"Đang mở", "Tất cả trạng thái", "Đã đóng"}
    );
    private final BaseSearch searchField = new BaseSearch("Tìm theo tên nhân viên hoặc mã ca...");
    private final JButton todayButton = chipButton("Hôm nay", AppColor.ACCENT);
    private final JButton clearDateButton = chipButton("Xóa ngày", AppColor.TEXT_SECONDARY);
    private final JButton refreshButton = chipButton("Làm mới", AppColor.TEXT_PRIMARY);

    private final DefaultTableModel tableModel = readOnlyModel(
            "Mã ca", "Nhân viên", "Trạng thái", "Bắt đầu", "Đã làm / KT",
            "HĐ", "Tiền đầu", "DT tiền mặt", "Thu", "Chi", "Hoàn", "Quỹ hệ thống"
    );
    private final JTable table = buildTable(tableModel);
    private final Pagination pagination = new Pagination();
    private final JLabel countLabel = new JLabel();

    private final EmptyState emptyState = new EmptyState(
            FontAwesomeSolid.USER_CLOCK,
            "Không có ca nào phù hợp",
            "Thử đổi khoảng ngày hoặc bộ lọc trạng thái"
    );

    private final JPanel tableCardHolder = new JPanel(new BorderLayout());

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

        styleStatusColumn(table);
        styleMoneyColumns(table);

        pagination.setVisiblePages(5);
        tableCardHolder.setOpaque(false);
        tableCardHolder.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableCardHolder.add(filterTableCard(buildFilterHeader(), table, pagination), BorderLayout.CENTER);
        content.add(tableCardHolder);

        add(content, BorderLayout.CENTER);

        bindEvents();
        AutoRefresher.bind(this, DataChangedEvent.class, 400, this::loadData);
        loadData();
    }

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

    private JComponent buildFilterHeader() {
        JPanel wrap = new JPanel();
        wrap.setOpaque(false);
        wrap.setLayout(new BoxLayout(wrap, BoxLayout.Y_AXIS));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));
        row1.setOpaque(false);

        dateFrom.setPreferredSize(new Dimension(140, 36));
        dateTo.setPreferredSize(new Dimension(140, 36));
        statusFilter.setPreferredSize(new Dimension(160, 36));
        refreshButton.setIcon(FontIcon.of(FontAwesomeSolid.SYNC_ALT, 14, AppColor.TEXT_SECONDARY));

        row1.add(mutedLabel("Từ ngày"));
        row1.add(dateFrom);
        row1.add(mutedLabel("Đến ngày"));
        row1.add(dateTo);
        row1.add(Box.createHorizontalStrut(6));
        row1.add(statusFilter);
        row1.add(Box.createHorizontalStrut(6));
        row1.add(todayButton);
        row1.add(clearDateButton);
        row1.add(refreshButton);

        JPanel row2 = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        row2.setOpaque(false);
        row2.setBorder(new EmptyBorder(AppSpacing.SM, 0, 0, 0));
        searchField.setPreferredSize(new Dimension(320, 36));
        row2.add(searchField, BorderLayout.WEST);
        countLabel.setFont(AppFont.BODY);
        countLabel.setForeground(AppColor.TEXT_SECONDARY);
        countLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        row2.add(countLabel, BorderLayout.EAST);

        wrap.add(row1);
        wrap.add(row2);
        return wrap;
    }

    private JPanel filterTableCard(JComponent header, JTable table, JComponent paginationComp) {
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

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.WHITE);

        JPanel paginationWrapper = new JPanel(new BorderLayout());
        paginationWrapper.setBackground(AppColor.WHITE);
        paginationWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER));
        paginationWrapper.add(paginationComp, BorderLayout.CENTER);

        card.add(toolbarWrapper, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        card.add(paginationWrapper, BorderLayout.SOUTH);
        return card;
    }

    private static JLabel mutedLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFont.BODY);
        l.setForeground(AppColor.TEXT_SECONDARY);
        return l;
    }

    private static JButton chipButton(String text, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(AppFont.BUTTON);
        btn.setForeground(fg);
        btn.setBackground(AppColor.WHITE);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 12, 8, 12)
        ));
        return btn;
    }

    private static DefaultTableModel readOnlyModel(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setRowHeight(34);
        table.setFont(AppFont.BODY);
        table.setForeground(AppColor.TABLE_ROW_TEXT != null ? AppColor.TABLE_ROW_TEXT : AppColor.TEXT_PRIMARY);
        table.setBackground(AppColor.WHITE);
        table.setGridColor(AppColor.TABLE_GRID != null ? AppColor.TABLE_GRID : AppColor.BORDER);
        table.setShowVerticalLines(false);
        table.setSelectionBackground(
                AppColor.ACCENT_SELECTION_BG != null ? AppColor.ACCENT_SELECTION_BG : new Color(59, 130, 246, 40));
        table.setSelectionForeground(AppColor.TEXT_PRIMARY);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.getTableHeader().setBackground(
                AppColor.TABLE_HEADER_BG != null ? AppColor.TABLE_HEADER_BG : AppColor.ACCENT);
        table.getTableHeader().setForeground(Color.WHITE);
        table.getTableHeader().setReorderingAllowed(false);
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);

        RowColorProvider rowColorProvider = stripedRowColorProvider();
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                if (c instanceof JLabel) {
                    ((JLabel) c).setBorder(new EmptyBorder(0, 10, 0, 10));
                }
                return c;
            }
        };
        renderer.setBorder(new EmptyBorder(0, 10, 0, 10));
        table.setDefaultRenderer(Object.class, renderer);

        int[] widths = {70, 130, 100, 120, 100, 50, 100, 110, 90, 90, 90, 110};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return table;
    }

    private static RowColorProvider stripedRowColorProvider() {
        return (viewRow, isSelected) -> {
            if (isSelected) {
                return AppColor.ACCENT_SELECTION_BG != null
                        ? AppColor.ACCENT_SELECTION_BG
                        : new Color(59, 130, 246, 40);
            }
            Color odd = AppColor.TABLE_ROW_ODD != null ? AppColor.TABLE_ROW_ODD : new Color(248, 250, 252);
            return viewRow % 2 == 0 ? AppColor.WHITE : odd;
        };
    }

    private static void styleStatusColumn(JTable table) {
        TableCellRenderer badge = StatusColumn.renderer(
                value -> String.valueOf(value),
                value -> "Đang mở".equals(String.valueOf(value)) ? AppColor.SUCCESS : AppColor.TEXT_MUTED,
                stripedRowColorProvider()
        );
        table.getColumnModel().getColumn(2).setCellRenderer(badge);
    }

    private static void styleMoneyColumns(JTable table) {
        RowColorProvider rowColorProvider = stripedRowColorProvider();
        DefaultTableCellRenderer moneyRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setBorder(new EmptyBorder(0, 10, 0, 10));
                c.setHorizontalAlignment(SwingConstants.RIGHT);
                if (column == 11) {
                    c.setFont(AppFont.BODY_BOLD);
                    if (!isSelected) c.setForeground(AppColor.INFO);
                } else if (!isSelected) {
                    c.setForeground(AppColor.TEXT_PRIMARY);
                }
                return c;
            }
        };
        for (int col : new int[]{6, 7, 8, 9, 10, 11}) {
            table.getColumnModel().getColumn(col).setCellRenderer(moneyRenderer);
        }

        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                c.setBackground(rowColorProvider.colorFor(row, isSelected));
                c.setBorder(new EmptyBorder(0, 10, 0, 10));
                c.setHorizontalAlignment(SwingConstants.CENTER);
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(center);
        table.getColumnModel().getColumn(5).setCellRenderer(center);
    }

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

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int viewRow = table.getSelectedRow();
                    if (viewRow < 0) return;
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    int pageSize = pagination.getPageSize();
                    int page = Math.max(1, pagination.getCurrentPage());
                    int globalIndex = (page - 1) * pageSize + modelRow;
                    if (globalIndex >= 0 && globalIndex < filteredShifts.size()) {
                        showDetailDialog(filteredShifts.get(globalIndex));
                    }
                }
            }
        });
    }

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
        boolean closedOnly = "Đã đóng".equals(statusSel);
        boolean openOnlyQuery = "Đang mở".equals(statusSel) || statusSel == null;

        final LocalDate fFrom = from;
        final LocalDate fTo = to;
        final boolean fOpenOnly = openOnlyQuery && !closedOnly;
        final boolean fClosedOnly = closedOnly;

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
                    if (fClosedOnly) {
                        result = result.stream().filter(s -> !s.isOpen()).collect(Collectors.toList());
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
        updateCountLabel();
    }

    private boolean matches(Shift s, String q) {
        String name = s.getUserName() != null ? s.getUserName().toLowerCase(Locale.ROOT) : "";
        return name.contains(q) || String.valueOf(s.getShiftId()).contains(q);
    }

    private void renderPage() {
        tableModel.setRowCount(0);
        if (filteredShifts.isEmpty()) {
            showEmpty(true);
            return;
        }
        showEmpty(false);

        int pageSize = pagination.getPageSize();
        int page = Math.max(1, pagination.getCurrentPage());
        int fromIdx = (page - 1) * pageSize;
        int toIdx = Math.min(fromIdx + pageSize, filteredShifts.size());
        if (fromIdx >= filteredShifts.size()) {
            pagination.setCurrentPage(1);
            fromIdx = 0;
            toIdx = Math.min(pageSize, filteredShifts.size());
        }

        for (int i = fromIdx; i < toIdx; i++) {
            Shift s = filteredShifts.get(i);
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
                    money(expectedCash(s))
            });
        }
        updateCountLabel();
    }

    private void showEmpty(boolean empty) {
        tableCardHolder.removeAll();
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
        toolbarWrapper.add(buildFilterHeader(), BorderLayout.CENTER);

        JPanel paginationWrapper = new JPanel(new BorderLayout());
        paginationWrapper.setBackground(AppColor.WHITE);
        paginationWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER));
        paginationWrapper.add(pagination, BorderLayout.CENTER);

        card.add(toolbarWrapper, BorderLayout.NORTH);
        if (empty) {
            card.add(emptyState, BorderLayout.CENTER);
        } else {
            JScrollPane scroll = new JScrollPane(table);
            scroll.setBorder(null);
            scroll.getViewport().setBackground(AppColor.WHITE);
            card.add(scroll, BorderLayout.CENTER);
        }
        card.add(paginationWrapper, BorderLayout.SOUTH);

        tableCardHolder.add(card, BorderLayout.CENTER);
        tableCardHolder.revalidate();
        tableCardHolder.repaint();
    }

    private void updateStatCards(List<Shift> shifts) {
        BigDecimal totalExpected = BigDecimal.ZERO;
        BigDecimal totalSales = BigDecimal.ZERO;
        int totalInvoices = 0;
        for (Shift s : shifts) {
            totalExpected = totalExpected.add(expectedCash(s));
            totalSales = totalSales.add(nullSafe(s.getCashSales()));
            totalInvoices += s.getInvoiceCount();
        }
        openCountCard.setValue(String.valueOf(shifts.size()));
        totalExpectedCard.setValue(money(totalExpected));
        totalSalesCard.setValue(money(totalSales));
        totalInvoicesCard.setValue(String.valueOf(totalInvoices));
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
        countLabel.setText("Hiển thị " + from + "–" + to + " / " + total + " ca");
    }

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
