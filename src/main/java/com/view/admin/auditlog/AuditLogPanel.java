package com.view.admin.auditlog;

import com.components.AppAlert;
import com.components.BaseTable;
import com.components.DatePickerField;
import com.components.FilterDropdown;
import com.components.LoadingOverlay;
import com.components.Pagination;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.crud.BaseCrudPanel;
import com.dao.AuditLogDAO;
import com.disaster.DisasterRecoveryBootstrap;
import com.event.AutoRefresher;
import com.event.LogWrittenEvent;
import com.model.ActivityLog;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AuditLogPanel extends BaseCrudPanel<ActivityLog> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
    private static final String CARD_AUDIT = "AUDIT_LOG";
    private static final String CARD_INCIDENT = "INCIDENT_LOG";

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    private FilterDropdown<ActionOption> actionFilter;
    private FilterDropdown<EntityTypeOption> entityTypeFilter;
    private DatePickerField dateFromFilter;
    private DatePickerField dateToFilter;
    private String selectedAction;
    private String selectedEntityType;
    private LocalDate selectedFromDate;
    private LocalDate selectedToDate;
    private boolean adjustingDateFilter;

    private StatCard totalCard;
    private StatCard todayCard;
    private StatCard failedLoginCard;
    private StatCard activeUserCard;

    // ====== Chuyển đổi giữa 2 bảng ======
    private CardLayout mainCardLayout;
    private JPanel mainCardPanel;
    private JButton btnAuditTab;
    private JButton btnIncidentTab;

    // ====== Bảng nhật ký sự cố ======
    private BaseTable incidentTable;
    private Pagination incidentPagination;
    private JLabel incidentCountLabel;
    private List<String> allIncidentLines;
    private LoadingOverlay incidentLoadingOverlay;
    private DatePickerField incidentDateFilter;

    public AuditLogPanel() {
        super();

        table.setBadgeColumn(2,
                v -> actionLabel(v == null ? null : String.valueOf(v)),
                v -> actionColor(v == null ? null : String.valueOf(v)));

        table.setBadgeColumn(3,
                v -> entityLabel(v == null ? null : String.valueOf(v)),
                v -> entityColor(v == null ? null : String.valueOf(v)));

        final FontIcon copyIconTemplate = FontIcon.of(FontAwesomeSolid.COPY, 12);
        copyIconTemplate.setIconColor(AppColor.ACCENT);
        table.getTable().getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel c = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
                String text = value != null ? value.toString() : "";
                c.setText(text);
                c.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
                c.setHorizontalAlignment(SwingConstants.LEFT);
                c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG : (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
                if (extractProductCode(text) != null) {
                    FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 12);
                    copyIcon.setIconColor(AppColor.ACCENT);
                    c.setIcon(copyIcon);
                    c.setIconTextGap(6);
                    c.setHorizontalTextPosition(SwingConstants.LEFT);
                } else {
                    c.setIcon(null);
                }
                c.setToolTipText(extractProductCode(text) != null ? "Click để copy mã sản phẩm" : null);
                return c;
            }
        });

        table.getTable().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewCol = table.getTable().columnAtPoint(e.getPoint());
                int viewRow = table.getTable().rowAtPoint(e.getPoint());
                if (viewCol == 4 && viewRow >= 0) {
                    int modelRow = table.getTable().convertRowIndexToModel(viewRow);
                    Object value = table.getTable().getModel().getValueAt(modelRow, 4);
                    String text = value != null ? value.toString() : "";
                    String productCode = extractProductCode(text);
                    if (productCode != null) {
                        copyToClipboard(productCode);
                        JOptionPane.showMessageDialog(AuditLogPanel.this,
                            "Đã copy mã sản phẩm: " + productCode,
                            "Copy thành công", JOptionPane.INFORMATION_MESSAGE);
                    }
                }
            }
        });

        setupFilters();
        makeToolbarFiltersResponsive();
        AutoRefresher.bind(this, LogWrittenEvent.class, 400, this::reload);

        // ====== Sau khi BaseCrudPanel khởi tạo xong, bọc lại khu vực CENTER trong CardLayout ======
        wrapCenterWithTabSwitcher();

        initialLoad();
    }

    private void wrapCenterWithTabSwitcher() {
        // Lấy component hiện tại ở BorderLayout.CENTER (đó là bảng audit card từ BaseCrudPanel)
        Component centerComp = null;
        for (Component comp : getComponents()) {
            if (((BorderLayout) getLayout()).getConstraints(comp) == BorderLayout.CENTER) {
                centerComp = comp;
                break;
            }
        }

        if (centerComp == null) return;

        // Xóa nó ra khỏi layout hiện tại
        remove(centerComp);

        // Tạo CardLayout chứa 2 bảng
        mainCardLayout = new CardLayout();
        mainCardPanel = new JPanel(mainCardLayout);
        mainCardPanel.setOpaque(false);

        // Card 1: Bảng Audit Log (component gốc từ BaseCrudPanel)
        mainCardPanel.add(centerComp, CARD_AUDIT);

        // Card 2: Bảng Nhật ký sự cố
        JPanel incidentCard = buildIncidentTableCard();
        mainCardPanel.add(incidentCard, CARD_INCIDENT);

        // Thêm lại vào CENTER
        add(mainCardPanel, BorderLayout.CENTER);
    }

    @Override
    protected JComponent buildAdditionalTopContent() {
        JPanel tabBar = new JPanel(new BorderLayout());
        tabBar.setOpaque(false);
        tabBar.setBorder(new EmptyBorder(0, 0, 12, 0));

        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        tabs.setOpaque(false);

        btnAuditTab = createTabButton("Nhật ký audit", FontAwesomeSolid.HISTORY, true);
        btnIncidentTab = createTabButton("Nhật ký sự cố", FontAwesomeSolid.EXCLAMATION_TRIANGLE, false);

        btnAuditTab.addActionListener(e -> switchToAuditTab());
        btnIncidentTab.addActionListener(e -> switchToIncidentTab());

        tabs.add(btnAuditTab);
        tabs.add(Box.createHorizontalStrut(8));
        tabs.add(btnIncidentTab);

        tabBar.add(tabs, BorderLayout.WEST);
        return tabBar;
    }

    private JButton createTabButton(String text, FontAwesomeSolid icon, boolean active) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(active ? Color.WHITE : AppColor.TEXT_SECONDARY);

        JButton btn = new JButton(text, fontIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                boolean isActive = getClientProperty("activeTab") != null && getClientProperty("activeTab").equals(Boolean.TRUE);
                if (isActive) {
                    g2.setColor(AppColor.ACCENT);
                } else {
                    g2.setColor(getBackground());
                }
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.putClientProperty("activeTab", active);
        btn.setFont(AppFont.BODY_BOLD);
        btn.setForeground(active ? Color.WHITE : AppColor.TEXT_SECONDARY);
        btn.setBackground(active ? AppColor.ACCENT : AppColor.WHITE);
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setIconTextGap(8);

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!Boolean.TRUE.equals(btn.getClientProperty("activeTab"))) {
                    btn.setBackground(AppColor.BG_LIGHTER);
                    btn.repaint();
                }
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!Boolean.TRUE.equals(btn.getClientProperty("activeTab"))) {
                    btn.setBackground(AppColor.WHITE);
                    btn.repaint();
                }
            }
        });

        return btn;
    }

    private void setTabActive(JButton btn, boolean active) {
        btn.putClientProperty("activeTab", active);
        FontIcon icon = (FontIcon) btn.getIcon();
        if (active) {
            btn.setBackground(AppColor.ACCENT);
            btn.setForeground(Color.WHITE);
            if (icon != null) icon.setIconColor(Color.WHITE);
        } else {
            btn.setBackground(AppColor.WHITE);
            btn.setForeground(AppColor.TEXT_SECONDARY);
            if (icon != null) icon.setIconColor(AppColor.TEXT_SECONDARY);
        }
        btn.repaint();
    }

    private void switchToAuditTab() {
        setTabActive(btnAuditTab, true);
        setTabActive(btnIncidentTab, false);
        if (mainCardLayout != null && mainCardPanel != null) {
            mainCardLayout.show(mainCardPanel, CARD_AUDIT);
        }
    }

    private void switchToIncidentTab() {
        setTabActive(btnAuditTab, false);
        setTabActive(btnIncidentTab, true);
        if (mainCardLayout != null && mainCardPanel != null) {
            mainCardLayout.show(mainCardPanel, CARD_INCIDENT);
        }
        loadIncidentData(incidentPagination.getCurrentPage(), incidentPagination.getPageSize());
    }

    private JPanel buildIncidentTableCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));

        // ====== Toolbar cho bảng sự cố ======
        JPanel incidentToolbar = new JPanel(new BorderLayout());
        incidentToolbar.setBackground(AppColor.WHITE);
        incidentToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(10, 16, 10, 16)));

        JPanel filterLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        filterLeft.setOpaque(false);

        JLabel dateLabel = new JLabel("Ngày:");
        dateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        dateLabel.setForeground(AppColor.TEXT_MUTED);

        incidentDateFilter = new DatePickerField(LocalDate.now(), true);
        incidentDateFilter.setPreferredSize(new Dimension(150, 32));
        incidentDateFilter.onChange(v -> loadIncidentData(1, incidentPagination.getPageSize()));

        filterLeft.add(dateLabel);
        filterLeft.add(incidentDateFilter);

        incidentCountLabel = new JLabel();
        incidentCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        incidentCountLabel.setForeground(AppColor.TEXT_MUTED);

        incidentToolbar.add(filterLeft, BorderLayout.WEST);
        incidentToolbar.add(incidentCountLabel, BorderLayout.EAST);

        // ====== Bảng sự cố ======
        incidentTable = new BaseTable(new String[]{"Thời gian", "Mức độ", "Loại", "Nguồn", "Mô tả"});
        incidentTable.setBadgeColumn(1, o -> String.valueOf(o), this::severityColor);
        incidentTable.enableSorting();

        incidentLoadingOverlay = new LoadingOverlay("Đang tải dữ liệu...");
        JPanel wrappedIncidentTable = LoadingOverlay.attach(incidentTable, incidentLoadingOverlay);

        // ====== Phân trang cho bảng sự cố ======
        incidentPagination = new Pagination();
        incidentPagination.setVisiblePages(5);
        incidentPagination.addPropertyChangeListener("pageChanged",
                e -> loadIncidentData((int) e.getNewValue(), incidentPagination.getPageSize()));
        incidentPagination.addPropertyChangeListener("pageSizeChanged",
                e -> loadIncidentData(1, (int) e.getNewValue()));

        JPanel incidentPaginationWrapper = new JPanel(new BorderLayout());
        incidentPaginationWrapper.setBackground(AppColor.WHITE);
        incidentPaginationWrapper.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER));
        incidentPaginationWrapper.add(incidentPagination, BorderLayout.CENTER);

        card.add(incidentToolbar, BorderLayout.NORTH);
        card.add(wrappedIncidentTable, BorderLayout.CENTER);
        card.add(incidentPaginationWrapper, BorderLayout.SOUTH);

        return card;
    }

    private Color severityColor(Object severity) {
        switch (String.valueOf(severity)) {
            case "CRITICAL": return AppColor.ERROR;
            case "HIGH": return AppColor.WARNING;
            case "MEDIUM": return AppColor.INFO;
            default: return AppColor.TEXT_MUTED;
        }
    }

    private void loadIncidentData(int page, int pageSize) {
        if (!DisasterRecoveryBootstrap.isInitialized()) {
            incidentTable.clear();
            incidentCountLabel.setText("Hệ thống ghi nhận sự cố chưa sẵn sàng");
            incidentPagination.setTotalItems(0);
            return;
        }

        incidentLoadingOverlay.start("Đang tải nhật ký sự cố...");
        SwingWorker<PaginationHelper.PaginationResult<Object[]>, Void> worker = new SwingWorker<>() {
            @Override
            protected PaginationHelper.PaginationResult<Object[]> doInBackground() {
                LocalDate filterDate = incidentDateFilter != null ? incidentDateFilter.getValue() : LocalDate.now();
                if (filterDate == null) filterDate = LocalDate.now();

                List<String> lines = DisasterRecoveryBootstrap.getIncidentSink().readRawLines(filterDate);
                allIncidentLines = lines;

                int totalRecords = lines.size();
                int fromIndex = (page - 1) * pageSize;
                int toIndex = Math.min(fromIndex + pageSize, totalRecords);

                List<Object[]> pageData = new ArrayList<>();
                if (fromIndex < totalRecords) {
                    for (int i = fromIndex; i < toIndex; i++) {
                        String line = lines.get(i);
                        pageData.add(new Object[]{
                                extractJsonField(line, "timestamp"),
                                extractJsonField(line, "severity"),
                                extractJsonField(line, "type"),
                                extractJsonField(line, "source"),
                                extractJsonField(line, "message")
                        });
                    }
                }

                return new PaginationHelper.PaginationResult<>(pageData, page, pageSize, totalRecords);
            }

            @Override
            protected void done() {
                try {
                    PaginationHelper.PaginationResult<Object[]> result = get();
                    incidentTable.clear();
                    for (Object[] row : result.getData()) {
                        incidentTable.addRow(row);
                    }
                    incidentPagination.setTotalItems(result.getTotalRecords());
                    incidentPagination.setPageSize(result.getPageSize());
                    incidentPagination.setCurrentPage(result.getCurrentPage());
                    incidentCountLabel.setText("Tổng cộng: " + result.getTotalRecords() + " sự cố");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    incidentLoadingOverlay.stop();
                }
            }
        };
        worker.execute();
    }

    private static String extractJsonField(String jsonLine, String field) {
        String key = "\"" + field + "\":\"";
        int start = jsonLine.indexOf(key);
        if (start < 0) return "";
        start += key.length();
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < jsonLine.length(); i++) {
            char c = jsonLine.charAt(i);
            if (c == '\\' && i + 1 < jsonLine.length()) { sb.append(jsonLine.charAt(i + 1)); i++; }
            else if (c == '"') break;
            else sb.append(c);
        }
        return sb.toString();
    }

    @Override
    protected List<JComponent> buildStatsCards() {
        totalCard = new StatCard("Tổng nhật ký", "0", FontAwesomeSolid.HISTORY, AppColor.ACCENT, true);
        todayCard = new StatCard("Hoạt động hôm nay", "0", FontAwesomeSolid.BOLT, AppColor.SUCCESS, true);
        failedLoginCard = new StatCard("Đăng nhập thất bại", "0", FontAwesomeSolid.EXCLAMATION_TRIANGLE, AppColor.ERROR, true);
        activeUserCard = new StatCard("Người dùng hoạt động", "0", FontAwesomeSolid.USERS, AppColor.WARNING, true);
        List<JComponent> cards = new ArrayList<>();
        cards.add(totalCard);
        cards.add(todayCard);
        cards.add(failedLoginCard);
        cards.add(activeUserCard);
        return cards;
    }

    private void refreshStatsCards() {
        if (totalCard == null) return;
        SwingWorker<AuditLogDAO.AuditLogStats, Void> worker = new SwingWorker<>() {
            @Override protected AuditLogDAO.AuditLogStats doInBackground() {
                return auditLogDAO.getStatsSummary();
            }
            @Override protected void done() {
                try {
                    AuditLogDAO.AuditLogStats stats = get();
                    totalCard.setValue(NumberUtil.formatThousands(stats.totalLogs));
                    todayCard.setValue(NumberUtil.formatThousands(stats.todayLogs));
                    failedLoginCard.setValue(NumberUtil.formatThousands(stats.failedLoginsToday));
                    activeUserCard.setValue(NumberUtil.formatThousands(stats.activeUsersToday));
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }

    @Override protected FontAwesomeSolid getIcon() { return FontAwesomeSolid.HISTORY; }
    @Override protected String getPageTitle() { return "Nhật ký hệ thống"; }
    @Override protected String getPageSubtitle() { return "Lịch sử thao tác người dùng và ghi nhận sự cố hệ thống"; }
    @Override protected String getAddButtonLabel() { return null; }
    @Override protected boolean supportsEdit() { return false; }
    @Override protected boolean supportsDelete() { return false; }
    @Override protected boolean supportsView() { return true; }
    @Override protected String getSearchPlaceholder() { return "Tìm theo người dùng, mô tả, đối tượng..."; }
    @Override protected String getEntityLabel() { return "nhật ký"; }
    @Override protected String getItemDisplayName(ActivityLog item) { return item.getDescription(); }

    @Override
    protected String[] getColumnNames() {
        return new String[]{"Thời gian", "Người dùng", "Hành động", "Đối tượng", "Mô tả"};
    }

    @Override
    protected Object[] mapRowToColumns(ActivityLog item) {
        return new Object[]{
                item.getCreatedAt() != null ? DATE_FORMAT.format(item.getCreatedAt()) : "",
                item.getUsername() != null ? item.getUsername() : "SYSTEM",
                item.getAction(),
                item.getEntityType(),
                item.getDescription() != null ? item.getDescription() : ""
        };
    }

    @Override
    protected PaginationHelper.PaginationResult<ActivityLog> fetchPage(int page, int pageSize) {
        return auditLogDAO.filter(page, pageSize, null, selectedAction, selectedEntityType,
                toStartOfDay(selectedFromDate), toEndOfDay(selectedToDate));
    }

    @Override
    protected void afterRender(PaginationHelper.PaginationResult<ActivityLog> result) {
        table.getTable().repaint();
        refreshStatsCards();
    }

    @Override
    protected PaginationHelper.PaginationResult<ActivityLog> searchPage(String keyword, int page, int pageSize) {
        return auditLogDAO.filter(page, pageSize, keyword, selectedAction, selectedEntityType,
                toStartOfDay(selectedFromDate), toEndOfDay(selectedToDate));
    }

    private static Date toStartOfDay(LocalDate date) {
        if (date == null) return null;
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static Date toEndOfDay(LocalDate date) {
        if (date == null) return null;
        return Date.from(LocalTime.of(23, 59, 59).atDate(date).atZone(ZoneId.systemDefault()).toInstant());
    }

    @Override
    protected List<ActivityLog> fetchAllForExport() {
        return auditLogDAO.getRecentForExport();
    }

    @Override protected void openForm(ActivityLog item) { }
    @Override protected boolean deleteItem(ActivityLog item) { return false; }

    @Override
    protected void viewRow(int modelRow) {
        ActivityLog item = currentPageData != null && modelRow >= 0 && modelRow < currentPageData.size()
                ? currentPageData.get(modelRow) : null;
        if (item == null) return;
        AuditLogDetailDialog.show(SwingUtilities.getWindowAncestor(this), item,
                actionLabel(item.getAction()), entityLabel(item.getEntityType()));
    }

    private void setupFilters() {
        List<String> actions = auditLogDAO.getDistinctActions();
        ActionOption[] actionOptions = new ActionOption[actions.size() + 1];
        actionOptions[0] = new ActionOption(null, "Tất cả hành động");
        for (int i = 0; i < actions.size(); i++) {
            String a = actions.get(i);
            actionOptions[i + 1] = new ActionOption(a, actionLabelStatic(a));
        }
        actionFilter = new FilterDropdown<>(FontAwesomeSolid.BOLT, actionOptions);
        actionFilter.setPreferredSize(new Dimension(160, 34));
        actionFilter.setMaximumSize(new Dimension(180, 34));
        actionFilter.onChange(opt -> {
            selectedAction = opt != null ? opt.value : null;
            applyFilters();
        });
        addToolbarFilter(actionFilter);

        List<String> entityTypes = auditLogDAO.getDistinctEntityTypes();
        EntityTypeOption[] entityOptions = new EntityTypeOption[entityTypes.size() + 1];
        entityOptions[0] = new EntityTypeOption(null, "Tất cả đối tượng");
        for (int i = 0; i < entityTypes.size(); i++) {
            String e = entityTypes.get(i);
            entityOptions[i + 1] = new EntityTypeOption(e, entityLabelStatic(e));
        }
        entityTypeFilter = new FilterDropdown<>(FontAwesomeSolid.LAYER_GROUP, entityOptions);
        entityTypeFilter.setPreferredSize(new Dimension(160, 34));
        entityTypeFilter.setMaximumSize(new Dimension(180, 34));
        entityTypeFilter.onChange(opt -> {
            selectedEntityType = opt != null ? opt.value : null;
            applyFilters();
        });
        addToolbarFilter(entityTypeFilter);

        addToolbarFilter(buildDateRangeFilter());
    }

    /** Lớp bọc giá trị action cho FilterDropdown - giống CategoryOption bên ProductPanel */
    private static class ActionOption {
        final String value;
        final String label;
        ActionOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }

    /** Lớp bọc giá trị entity type cho FilterDropdown */
    private static class EntityTypeOption {
        final String value;
        final String label;
        EntityTypeOption(String value, String label) {
            this.value = value;
            this.label = label;
        }
        @Override public String toString() { return label; }
    }

    private JPanel buildDateRangeFilter() {
        JPanel wrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        wrap.setOpaque(false);

        dateFromFilter = new DatePickerField(null, true);
        dateFromFilter.setPreferredSize(new Dimension(110, 34));
        dateFromFilter.setToolTipText("Từ ngày");
        dateFromFilter.onChange(v -> {
            if (adjustingDateFilter) return;
            LocalDate from = v;
            LocalDate to = dateToFilter != null ? dateToFilter.getValue() : null;
            if (from != null && to != null && to.isBefore(from)) {
                AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                        "Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
                adjustingDateFilter = true;
                try {
                    dateToFilter.setValue(null);
                    selectedToDate = null;
                } finally {
                    adjustingDateFilter = false;
                }
            }
            selectedFromDate = v;
            applyFilters();
        });

        JLabel sep = new JLabel("–");
        sep.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sep.setForeground(AppColor.TEXT_MUTED);

        dateToFilter = new DatePickerField(null, true);
        dateToFilter.setPreferredSize(new Dimension(110, 34));
        dateToFilter.setToolTipText("Đến ngày");
        dateToFilter.onChange(v -> {
            if (adjustingDateFilter) return;
            LocalDate from = dateFromFilter != null ? dateFromFilter.getValue() : null;
            LocalDate to = v;
            if (from != null && to != null && to.isBefore(from)) {
                AppAlert.warning(this, "Khoảng ngày không hợp lệ",
                        "Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
                adjustingDateFilter = true;
                try {
                    dateToFilter.setValue(null);
                    selectedToDate = null;
                } finally {
                    adjustingDateFilter = false;
                }
                return;
            }
            selectedToDate = v;
            applyFilters();
        });

        wrap.add(dateFromFilter);
        wrap.add(sep);
        wrap.add(dateToFilter);
        return wrap;
    }

    @Override
    protected void onDataChanged() {
        reload();
    }

    /**
     * Tìm panel chứa các filter (toolbarLeft) thông qua cây component
     * và thay đổi layout để các filter tự động xuống dòng khi không đủ diện tích.
     * Đồng thời điều chỉnh kích thước search bar và giảm khoảng trắng statsCards.
     */
    private void makeToolbarFiltersResponsive() {
        SwingUtilities.invokeLater(() -> {
            // Duyệt qua cây component để tìm toolbarLeft (JPanel FlowLayout chứa các filter)
            JPanel toolbarLeft = findToolbarLeftPanel(this);
            if (toolbarLeft != null) {
                // Thay đổi FlowLayout: giảm khoảng cách, cho phép xuống dòng
                toolbarLeft.setLayout(new FlowLayout(FlowLayout.LEFT, 6, 6));
                toolbarLeft.revalidate();
                toolbarLeft.repaint();
            }

            // Điều chỉnh kích thước search bar
            if (searchBar != null) {
                Dimension currentSize = searchBar.getPreferredSize();
                searchBar.setPreferredSize(new Dimension(260, (int) currentSize.getHeight()));
                searchBar.revalidate();
            }

            // Giảm khoảng trắng giữa statcard và bảng (statsCardsRow)
            JPanel statsCardsRow = findStatsCardsRow(this);
            if (statsCardsRow != null) {
                statsCardsRow.setBorder(new EmptyBorder(0, 0, 8, 0));
                statsCardsRow.revalidate();
                statsCardsRow.repaint();
            }
        });
    }

    /**
     * Tìm statsCardsRow - JPanel chứa các StatCard, có GridLayout và EmptyBorder.
     */
    private JPanel findStatsCardsRow(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                LayoutManager layout = panel.getLayout();
                if (layout instanceof GridLayout
                        && panel.getComponentCount() >= 3
                        && panel.getBorder() instanceof EmptyBorder) {
                    // Kiểm tra xem đây có phải statsCardsRow không:
                    // con đầu tiên phải là StatCard
                    Component firstChild = panel.getComponent(0);
                    if (firstChild != null && firstChild.getClass().getName().contains("StatCard")) {
                        return panel;
                    }
                }
                // Đệ quy tìm tiếp
                JPanel found = findStatsCardsRow(panel);
                if (found != null) return found;
            } else if (comp instanceof Container) {
                JPanel found = findStatsCardsRow((Container) comp);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * Duyệt đệ quy qua cây component để tìm JPanel chứa các filter.
     * Panel này có đặc điểm: là con của một JPanel (toolbar),
     * có FlowLayout, và chứa ít nhất 3 component (searchBar + 2 combobox + dateRange).
     */
    private JPanel findToolbarLeftPanel(Container container) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JPanel) {
                JPanel panel = (JPanel) comp;
                LayoutManager layout = panel.getLayout();
                if (layout instanceof FlowLayout
                        && panel.getComponentCount() >= 3
                        && !(panel.getParent() instanceof JViewport)) {
                    // Kiểm tra xem đây có phải toolbarLeft không:
                    // cha của nó phải là một JPanel có BorderLayout (toolbar)
                    Container parent = panel.getParent();
                    if (parent != null && parent.getLayout() instanceof BorderLayout) {
                        Object constraints = ((BorderLayout) parent.getLayout()).getConstraints(panel);
                        if (BorderLayout.WEST.equals(constraints)) {
                            return panel;
                        }
                    }
                }
                // Đệ quy tìm tiếp
                JPanel found = findToolbarLeftPanel(panel);
                if (found != null) return found;
            } else if (comp instanceof Container) {
                JPanel found = findToolbarLeftPanel((Container) comp);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String actionLabelStatic(String action) { return actionLabel(action); }
    private static String entityLabelStatic(String entityType) { return entityLabel(entityType); }

    static String actionLabel(String action) {
        if (action == null) return "";
        switch (action) {
            case ActivityLog.ACTION_CREATE: return "Thêm mới";
            case ActivityLog.ACTION_UPDATE: return "Cập nhật";
            case ActivityLog.ACTION_DELETE: return "Xóa";
            case ActivityLog.ACTION_RESTORE: return "Khôi phục";
            case ActivityLog.ACTION_PERMANENT_DELETE: return "Xóa vĩnh viễn";
            case ActivityLog.ACTION_STATUS_CHANGE: return "Đổi trạng thái";
            case ActivityLog.ACTION_LOGIN: return "Đăng nhập";
            case ActivityLog.ACTION_LOGIN_FAILED: return "Đăng nhập thất bại";
            case ActivityLog.ACTION_LOGOUT: return "Đăng xuất";
            case ActivityLog.ACTION_PASSWORD_RESET: return "Đặt lại mật khẩu";
            case "USER_LOCK": return "Khóa tài khoản";
            case "USER_UNLOCK": return "Mở khóa tài khoản";
            default: return action;
        }
    }

    static Color actionColor(String action) {
        if (action == null) return AppColor.TEXT_MUTED;
        switch (action) {
            case ActivityLog.ACTION_CREATE:
            case ActivityLog.ACTION_RESTORE:
            case "USER_UNLOCK":
                return AppColor.SUCCESS;
            case ActivityLog.ACTION_LOGIN:
                return AppColor.TEAL;
            case ActivityLog.ACTION_LOGOUT:
                return AppColor.INFO;
            case ActivityLog.ACTION_UPDATE:
            case ActivityLog.ACTION_STATUS_CHANGE:
                return AppColor.WARNING;
            case ActivityLog.ACTION_PASSWORD_RESET:
                return AppColor.ORANGE != null ? AppColor.ORANGE : AppColor.WARNING;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
            case ActivityLog.ACTION_LOGIN_FAILED:
            case "USER_LOCK":
                return AppColor.ERROR;
            default:
                return AppColor.ACCENT;
        }
    }

    static String entityLabel(String entityType) {
        if (entityType == null) return "";
        switch (entityType) {
            case ActivityLog.ENTITY_CATEGORY: return "Danh mục";
            case ActivityLog.ENTITY_CUSTOMER: return "Khách hàng";
            case ActivityLog.ENTITY_SUPPLIER: return "Nhà cung cấp";
            case ActivityLog.ENTITY_EMPLOYEE: return "Nhân viên";
            case ActivityLog.ENTITY_PRODUCT: return "Sản phẩm";
            case ActivityLog.ENTITY_USER:
            case "Users":
                return "Tài khoản";
            case ActivityLog.ENTITY_ORDER: return "Đơn hàng";
            case ActivityLog.ENTITY_INVENTORY_BATCH: return "Lô hàng";
            case ActivityLog.ENTITY_INVOICE: return "Hóa đơn";
            case ActivityLog.ENTITY_PURCHASE_RECEIPT: return "Phiếu nhập kho";
            case ActivityLog.ENTITY_STOCK_ALERT: return "Cảnh báo tồn kho";
            case ActivityLog.ENTITY_PHONE: return "Điện thoại";
            default: return entityType;
        }
    }

    static Color entityColor(String entityType) {
        if (entityType == null) return AppColor.TEXT_MUTED;
        switch (entityType) {
            case ActivityLog.ENTITY_USER:
            case "Users":
            case ActivityLog.ENTITY_EMPLOYEE:
                return AppColor.ACCENT;
            case ActivityLog.ENTITY_CUSTOMER:
                return AppColor.BLUE;
            case ActivityLog.ENTITY_PRODUCT:
            case ActivityLog.ENTITY_CATEGORY:
                return AppColor.TEAL;
            case ActivityLog.ENTITY_INVOICE:
            case ActivityLog.ENTITY_ORDER:
                return AppColor.SUCCESS;
            case ActivityLog.ENTITY_SUPPLIER:
            case ActivityLog.ENTITY_PURCHASE_RECEIPT:
            case ActivityLog.ENTITY_INVENTORY_BATCH:
                return AppColor.WARNING;
            case ActivityLog.ENTITY_STOCK_ALERT:
                return AppColor.ERROR;
            default:
                return AppColor.TEXT_MUTED;
        }
    }

    private static String extractProductCode(String text) {
        if (text == null || text.isBlank()) return null;
        Pattern p1 = Pattern.compile("mã\\s+(SP[A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
        Matcher m1 = p1.matcher(text);
        if (m1.find()) {
            return m1.group(1).toUpperCase();
        }
        Pattern p2 = Pattern.compile("\\b(SP[A-Z0-9]{2,})\\b", Pattern.CASE_INSENSITIVE);
        Matcher m2 = p2.matcher(text);
        if (m2.find()) {
            return m2.group(1).toUpperCase();
        }
        return null;
    }

    private void copyToClipboard(String text) {
        try {
            StringSelection selection = new StringSelection(text);
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(selection, null);
        } catch (Exception ignored) {
        }
    }
}
