package com.view.admin.shift;

import com.components.Pagination;
import com.components.AppAlert;
import com.components.SectionHeader;
import com.event.AutoRefresher;
import com.event.DataChangedEvent;
import com.model.Shift;
import com.model.ShiftCashSummary;
import com.model.ShiftCashTransaction;
import com.model.permission.AppPermission;
import com.service.AuthService;
import com.service.ShiftService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.NumberUtil;
import com.utils.FileUtil;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShiftManagementPanel extends JPanel {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	/*
	 * Giao dien chi goi service. Khong tao ShiftDAO truc tiep trong panel.
	 */
	private final ShiftService shiftService = new ShiftService();

	/*
	 * Xac dinh tai khoan hien tai co duoc phep mo/thu/chi/dong ca hay khong.
	 */
	private final boolean canOperate = AuthService.getInstance().can(AppPermission.SHIFT_OPERATE);

	private final JLabel statusValue = valueLabel();

	private final JLabel openingValue = valueLabel();

	private final JLabel salesValue = valueLabel();

	private final JLabel movementsValue = valueLabel();

	private final JLabel expectedValue = valueLabel();

	private final JButton openButton = actionButton("Mở ca", AppColor.SUCCESS);

	private final JButton cashInButton = actionButton("Thu tiền", AppColor.ACCENT);

	private final JButton cashOutButton = actionButton("Chi tiền", AppColor.WARNING);

	private final JButton closeButton = actionButton("Đóng ca", AppColor.ERROR);

	private final JButton refreshButton = actionButton("Làm mới", AppColor.TEXT_SECONDARY);

	private final DefaultTableModel historyModel = readOnlyModel("Mã ca", "Nhân viên", "Bắt đầu", "Kết thúc",
			"Trạng thái", "Hóa đơn", "Tiền hệ thống", "Tiền thực tế", "Chênh lệch");

	private final JTable historyTable = buildTable(historyModel);

	private final JTextField historySearchField = filterSearchField("Tìm mã ca, nhân viên...");

	private final JComboBox<String> historyStatusFilter = filterCombo("Tất cả trạng thái", "Đang mở", "Đã đóng");

	private final JComboBox<String> historyDayFilter = dayFilterCombo();

	private final JComboBox<String> historyMonthFilter = monthFilterCombo();

	private final JComboBox<String> historyYearFilter = yearFilterCombo();

	private final Pagination historyPagination =
	        new Pagination();

	private final DefaultTableModel transactionModel = readOnlyModel("Mã giao dịch", "Loại", "Số tiền", "Lý do",
			"Người tạo", "Thời gian");

	private final JTable transactionTable = buildTable(transactionModel);

	private final JTextField transactionSearchField = filterSearchField("Tìm mã giao dịch, lý do, người tạo...");

	private final JComboBox<String> transactionTypeFilter = filterCombo("Tất cả loại", "Thu tiền", "Chi tiền");

	private final JComboBox<String> transactionDayFilter = dayFilterCombo();

	private final JComboBox<String> transactionMonthFilter = monthFilterCombo();

	private final JComboBox<String> transactionYearFilter = yearFilterCombo();

	private final Pagination transactionPagination =
	        new Pagination();

	private final JLabel transactionTitle = new JLabel("Thu/chi của ca");

	private final JToggleButton historyTabButton = tabButton("Lịch sử ca gần nhất");

	private final JToggleButton transactionTabButton = tabButton("Thu / chi của ca");

	private List<Shift> visibleShifts = new ArrayList<>();

	// Danh sách ca đang hiển thị ở trang hiện tại
	private List<Shift> pagedShifts = new ArrayList<>();

	// Toàn bộ giao dịch Thu/Chi của ca đang chọn
	private List<ShiftCashTransaction> allTransactions = new ArrayList<>();

	private Shift currentOpenShift;

	/*
	 * Dung de ngan su kien chon bang chay trong luc dang nap lai model.
	 */
	private boolean updatingHistory;

	public ShiftManagementPanel() {
		setLayout(new BorderLayout(0, AppSpacing.LG));

		setBackground(AppColor.PAGE_BG);

		setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

		String subtitle;

		if (canOperate) {
			subtitle = "Mở ca trước khi bán hàng, " + "ghi nhận mọi khoản thu/chi " + "và đối soát khi đóng ca";
		} else {
			subtitle = "Theo dõi lịch sử ca và " + "chênh lệch quỹ của " + "nhân viên bán hàng";
		}

		SectionHeader header = new SectionHeader(
		        FontAwesomeSolid.CLOCK,
		        AppColor.ACCENT,
		        "Ca bán hàng & đối soát quỹ",
		        subtitle
		);


		/*
		 * Tùy chọn giống trang Quản lý hóa đơn.
		 *
		 * SectionHeader sẽ tự tạo nút:
		 *
		 * [ Tùy chọn ▼ ]
		 *
		 * khi có ít nhất một addOverflowAction().
		 */
		header.addOverflowAction(
		        "Xuất CSV",
		        FontAwesomeSolid.FILE_CSV,
		        () -> exportCurrentTab("csv")
		);

		header.addOverflowAction(
		        "Xuất Excel",
		        FontAwesomeSolid.FILE_EXCEL,
		        () -> exportCurrentTab("xlsx")
		);


		add(header, BorderLayout.NORTH);

		JPanel content = new JPanel();

		content.setOpaque(false);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		content.add(buildSummaryCards());

		content.add(Box.createVerticalStrut(AppSpacing.MD));

		content.add(buildActions());

		content.add(Box.createVerticalStrut(AppSpacing.MD));

		content.add(buildTables());

		add(content, BorderLayout.CENTER);

		/*
		 * Buoc 7A chi gan nut Lam moi. Cac nut nghiep vu se them o 7B.
		 */

		openButton.addActionListener(event -> showOpenDialog());

		cashInButton.addActionListener(event -> showCashMovementDialog(ShiftCashTransaction.CASH_IN));

		cashOutButton.addActionListener(event -> showCashMovementDialog(ShiftCashTransaction.CASH_OUT));

		closeButton.addActionListener(event -> loadClosePreview());

		refreshButton.addActionListener(event -> loadData());

		openButton.addActionListener(event -> showOpenDialog());

		cashInButton.addActionListener(event -> showCashMovementDialog(ShiftCashTransaction.CASH_IN));

		cashOutButton.addActionListener(event -> showCashMovementDialog(ShiftCashTransaction.CASH_OUT));

		closeButton.addActionListener(event -> loadClosePreview());

		refreshButton.addActionListener(event -> loadData());
		
		historyPagination.setVisiblePages(5);

		transactionPagination.setVisiblePages(5);

		// Kích hoạt tìm kiếm và lọc
		bindFilters();
		// Phân trang
		bindPagination();

		historyTable.getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !updatingHistory) {
				loadSelectedTransactions();
			}
		});

		/*
		 * Khi service phat DataChangedEvent.SHIFT, panel tu tai lai sau 300 ms.
		 */
		AutoRefresher.bind(this, DataChangedEvent.class, 300, this::loadData);

		loadData();
	}

	private JPanel buildSummaryCards() {
		JPanel cards = new JPanel(new GridLayout(1, 5, AppSpacing.MD, 0));

		cards.setOpaque(false);

		cards.setAlignmentX(Component.LEFT_ALIGNMENT);

		cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));

		cards.add(summaryCard("Trạng thái ca", statusValue));

		cards.add(summaryCard("Tiền đầu ca", openingValue));

		cards.add(summaryCard("Doanh thu tiền mặt", salesValue));

		cards.add(summaryCard("Thu / chi", movementsValue));

		cards.add(summaryCard("Tiền hệ thống", expectedValue));

		return cards;
	}

	private JPanel summaryCard(String title, JLabel value) {
		JPanel card = new JPanel();

		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

		card.setBackground(AppColor.WHITE);

		card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(16, 18, 16, 18)));

		JLabel titleLabel = new JLabel(title);

		titleLabel.setFont(AppFont.SMALL);

		titleLabel.setForeground(AppColor.TEXT_MUTED);

		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		value.setAlignmentX(Component.LEFT_ALIGNMENT);

		card.add(titleLabel);

		card.add(Box.createVerticalStrut(9));

		card.add(value);

		return card;
	}

	private JPanel buildActions() {
		JPanel row = new JPanel(new BorderLayout());

		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));

		actions.setOpaque(false);

		if (canOperate) {
			actions.add(openButton);
			actions.add(cashInButton);
			actions.add(cashOutButton);
			actions.add(closeButton);
		}

		actions.add(refreshButton);

		row.add(actions, BorderLayout.EAST);

		return row;
	}

	private JComponent buildTables() {

		transactionTitle.setFont(AppFont.HEADING_MD);
		transactionTitle.setForeground(AppColor.TEXT_PRIMARY);

		JPanel historyCard =
		        filterTableCard(
		                buildHistoryFilterBar(),
		                historyTable,
		                historyPagination
		        );

		JPanel transactionCard =
		        filterTableCard(
		                buildTransactionHeader(),
		                transactionTable,
		                transactionPagination
		        );

		/*
		 * CardLayout: chỉ hiển thị 1 bảng tại một thời điểm.
		 */
		CardLayout cardLayout = new CardLayout();

		JPanel cards = new JPanel(cardLayout);
		cards.setOpaque(false);

		cards.add(historyCard, "history");

		cards.add(transactionCard, "transactions");

		/*
		 * Nhóm 2 tab lại. Chỉ một tab được active.
		 */
		ButtonGroup group = new ButtonGroup();

		group.add(historyTabButton);
		group.add(transactionTabButton);

		/*
		 * Mặc định mở tab lịch sử ca.
		 */
		historyTabButton.setSelected(true);

		updateTabStyles();

		/*
		 * Bấm Lịch sử ca.
		 */
		historyTabButton.addActionListener(event -> {

			cardLayout.show(cards, "history");

			updateTabStyles();
		});

		/*
		 * Bấm Thu / chi.
		 */
		transactionTabButton.addActionListener(event -> {

			cardLayout.show(cards, "transactions");

			updateTabStyles();
		});

		/*
		 * Thanh chứa 2 tab.
		 */
		JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));

		tabs.setOpaque(false);

		tabs.add(historyTabButton);
		tabs.add(transactionTabButton);

		/*
		 * Khung cuối cùng:
		 *
		 * [Tab 1] [Tab 2]
		 *
		 * [ Nội dung bảng ]
		 */
		JPanel wrapper = new JPanel(new BorderLayout(0, AppSpacing.SM));

		wrapper.setOpaque(false);

		wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

		wrapper.setPreferredSize(new Dimension(900, 430));

		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		wrapper.add(tabs, BorderLayout.NORTH);

		wrapper.add(cards, BorderLayout.CENTER);

		return wrapper;
	}

	private JPanel buildHistoryFilterBar() {

	    return buildFilterBar(
	            historySearchField,
	            historyStatusFilter,
	            "Trạng thái:",
	            historyDayFilter,
	            historyMonthFilter,
	            historyYearFilter
	    );
	}

	private JPanel buildTransactionHeader() {

	    JPanel panel = new JPanel();

	    panel.setOpaque(false);

	    panel.setLayout(
	            new BoxLayout(
	                    panel,
	                    BoxLayout.Y_AXIS
	            )
	    );


	    /*
	     * Tiêu đề Thu/Chi vẫn nằm phía trên.
	     */
	    transactionTitle.setAlignmentX(
	            Component.LEFT_ALIGNMENT
	    );

	    panel.add(transactionTitle);

	    panel.add(
	            Box.createVerticalStrut(10)
	    );


	    /*
	     * Tìm kiếm + loại + ngày/tháng/năm
	     * nằm chung một dòng.
	     */
	    JPanel filters =
	            buildFilterBar(
	                    transactionSearchField,
	                    transactionTypeFilter,
	                    "Loại:",
	                    transactionDayFilter,
	                    transactionMonthFilter,
	                    transactionYearFilter
	            );

	    filters.setAlignmentX(
	            Component.LEFT_ALIGNMENT
	    );

	    panel.add(filters);

	    return panel;
	}

	private JPanel buildFilterBar(
	        JTextField searchField,
	        JComboBox<String> comboBox,
	        String labelText,
	        JComboBox<String> dayBox,
	        JComboBox<String> monthBox,
	        JComboBox<String> yearBox
	) {

	    JPanel bar =
	            new JPanel(
	                    new BorderLayout(
	                            AppSpacing.MD,
	                            0
	                    )
	            );

	    bar.setOpaque(false);


	    /*
	     * ==========================
	     * BÊN TRÁI: TÌM KIẾM
	     * ==========================
	     */

	    JPanel left =
	            new JPanel(
	                    new FlowLayout(
	                            FlowLayout.LEFT,
	                            0,
	                            0
	                    )
	            );

	    left.setOpaque(false);

	    left.add(searchField);


	    /*
	     * ==========================
	     * BÊN PHẢI: CÁC BỘ LỌC
	     * ==========================
	     */

	    JPanel right =
	            new JPanel(
	                    new FlowLayout(
	                            FlowLayout.RIGHT,
	                            AppSpacing.SM,
	                            0
	                    )
	            );

	    right.setOpaque(false);


	    /*
	     * Trạng thái / Loại
	     */
	    JLabel comboLabel =
	            new JLabel(labelText);

	    comboLabel.setFont(AppFont.BODY);

	    comboLabel.setForeground(
	            AppColor.TEXT_SECONDARY
	    );

	    right.add(comboLabel);

	    right.add(comboBox);


	    /*
	     * Lọc thời gian
	     */
	    JLabel dateLabel =
	            new JLabel("Lọc thời gian:");

	    dateLabel.setFont(AppFont.BODY);

	    dateLabel.setForeground(
	            AppColor.TEXT_SECONDARY
	    );

	    right.add(dateLabel);

	    right.add(dayBox);

	    right.add(monthBox);

	    right.add(yearBox);


	    /*
	     * Đưa 2 nhóm vào cùng một dòng.
	     */
	    bar.add(
	            left,
	            BorderLayout.WEST
	    );

	    bar.add(
	            right,
	            BorderLayout.EAST
	    );

	    return bar;
	}

	private JPanel filterTableCard(
	        JComponent header,
	        JTable table,
	        JComponent pagination
	) {

	    JPanel card =
	            new JPanel(
	                    new BorderLayout()
	            );

	    card.setBackground(
	            AppColor.WHITE
	    );

	    card.setBorder(
	            new LineBorder(
	                    AppColor.BORDER,
	                    1,
	                    true
	            )
	    );


	    /*
	     * ========================
	     * Thanh tìm kiếm + lọc
	     * ========================
	     */

	    JPanel toolbarWrapper =
	            new JPanel(
	                    new BorderLayout()
	            );

	    toolbarWrapper.setBackground(
	            AppColor.WHITE
	    );

	    toolbarWrapper.setBorder(
	            BorderFactory.createCompoundBorder(

	                    BorderFactory.createMatteBorder(
	                            0,
	                            0,
	                            1,
	                            0,
	                            AppColor.BORDER
	                    ),

	                    new EmptyBorder(
	                            14,
	                            16,
	                            14,
	                            16
	                    )
	            )
	    );

	    toolbarWrapper.add(
	            header,
	            BorderLayout.CENTER
	    );


	    /*
	     * ========================
	     * Bảng
	     * ========================
	     */

	    JScrollPane scroll =
	            new JScrollPane(table);

	    scroll.setBorder(null);

	    scroll.getViewport()
	            .setBackground(
	                    AppColor.WHITE
	            );


	    /*
	     * ========================
	     * Pagination
	     * ========================
	     */

	    JPanel paginationWrapper =
	            new JPanel(
	                    new BorderLayout()
	            );

	    paginationWrapper.setBackground(
	            AppColor.WHITE
	    );

	    paginationWrapper.setBorder(
	            BorderFactory.createMatteBorder(
	                    1,
	                    0,
	                    0,
	                    0,
	                    AppColor.BORDER
	            )
	    );

	    paginationWrapper.add(
	            pagination,
	            BorderLayout.CENTER
	    );


	    card.add(
	            toolbarWrapper,
	            BorderLayout.NORTH
	    );

	    card.add(
	            scroll,
	            BorderLayout.CENTER
	    );

	    card.add(
	            paginationWrapper,
	            BorderLayout.SOUTH
	    );


	    return card;
	}

	private JPanel tableCard(JTable table) {

		JPanel card = new JPanel(new BorderLayout());

		card.setBackground(AppColor.WHITE);

		card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(14, 16, 14, 16)));

		JScrollPane scroll = new JScrollPane(table);

		scroll.setBorder(new LineBorder(AppColor.BORDER));

		scroll.getViewport().setBackground(AppColor.WHITE);

		card.add(scroll, BorderLayout.CENTER);

		return card;
	}

	private JPanel tableCard(String title, JTable table) {
		JLabel label = new JLabel(title);

		label.setFont(AppFont.HEADING_MD);

		label.setForeground(AppColor.TEXT_PRIMARY);

		return tableCard(label, table);
	}

	private JPanel tableCard(JLabel title, JTable table) {
		JPanel card = new JPanel(new BorderLayout(0, 10));

		card.setBackground(AppColor.WHITE);

		card.setBorder(BorderFactory.createCompoundBorder(new LineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(14, 16, 14, 16)));

		card.add(title, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(table);

		scroll.setBorder(new LineBorder(AppColor.BORDER));

		scroll.getViewport().setBackground(AppColor.WHITE);

		card.add(scroll, BorderLayout.CENTER);

		return card;
	}

	private void loadData() {
		setBusy(true);

		SwingWorker<DashboardData, Void> worker = new SwingWorker<>() {

			@Override
			protected DashboardData doInBackground() {
				Shift openShift = shiftService.getMyOpenShift();

				List<Shift> history =
				        shiftService.getVisibleHistory(200);

				return new DashboardData(openShift, history);
			}

			@Override
			protected void done() {
				try {
					DashboardData data = get();

					currentOpenShift = data.openShift;

					visibleShifts = data.history;

					renderSummary(currentOpenShift);

					renderHistory();

				} catch (Exception e) {
					AppAlert.error(ShiftManagementPanel.this, "Không tải được dữ liệu " + "ca bán hàng.");

				} finally {
					setBusy(false);
				}
			}
		};

		worker.execute();
	}

	private void renderSummary(Shift shift) {
		boolean open = shift != null && shift.isOpen();

		if (open) {
			statusValue.setText("ĐANG MỞ  #" + shift.getShiftId());

			statusValue.setForeground(AppColor.SUCCESS);

			openingValue.setText(money(shift.getOpeningCash()));

			salesValue.setText(money(shift.getCashSales()));

			movementsValue.setText("+" + money(shift.getCashIn()) + " / -" + money(shift.getCashOut()));

			expectedValue.setText(money(expectedOf(shift)));

		} else {
			statusValue.setText("CHƯA MỞ CA");

			statusValue.setForeground(AppColor.WARNING);

			openingValue.setText("—");
			salesValue.setText("—");
			movementsValue.setText("—");
			expectedValue.setText("—");
		}

		openButton.setEnabled(canOperate && !open);

		cashInButton.setEnabled(canOperate && open);

		cashOutButton.setEnabled(canOperate && open);

		closeButton.setEnabled(canOperate && open);
	}

	private void renderHistory() {

	    updatingHistory = true;


	    /*
	     * ====================================
	     * 1. Lọc dữ liệu trước
	     * ====================================
	     */

	    List<Shift> filtered =
	            getFilteredShifts();

	    int totalItems =
	            filtered.size();


	    /*
	     * ====================================
	     * 2. Cập nhật Pagination
	     * ====================================
	     */

	    historyPagination.setTotalItems(
	            totalItems
	    );


	    int pageSize =
	            historyPagination.getPageSize();

	    int currentPage =
	            historyPagination.getCurrentPage();


	    /*
	     * ====================================
	     * 3. Tính vị trí dữ liệu trang hiện tại
	     * ====================================
	     */

	    int from =
	            totalItems == 0
	                    ? 0
	                    : (
	                        currentPage - 1
	                      ) * pageSize;


	    /*
	     * Trường hợp sau khi lọc,
	     * trang hiện tại vượt quá dữ liệu.
	     */
	    if (from >= totalItems && totalItems > 0) {

	        historyPagination.setCurrentPage(1);

	        currentPage = 1;

	        from = 0;
	    }


	    int to =
	            Math.min(
	                    from + pageSize,
	                    totalItems
	            );


	    /*
	     * ====================================
	     * 4. Lấy dữ liệu trang
	     * ====================================
	     */

	    pagedShifts =
	            totalItems == 0
	                    ? new ArrayList<>()
	                    : new ArrayList<>(
	                            filtered.subList(
	                                    from,
	                                    to
	                            )
	                    );


	    /*
	     * ====================================
	     * 5. Render JTable
	     * ====================================
	     */

	    historyModel.setRowCount(0);

	    int openRow = -1;


	    for (
	            int index = 0;
	            index < pagedShifts.size();
	            index++
	    ) {

	        Shift shift =
	                pagedShifts.get(index);


	        if (
	                currentOpenShift != null
	                && shift.getShiftId()
	                    == currentOpenShift.getShiftId()
	        ) {

	            openRow = index;
	        }


	        BigDecimal expected =
	                shift.isOpen()
	                        ? expectedOf(shift)
	                        : shift.getExpectedCash();


	        historyModel.addRow(
	                new Object[] {

	                    "#" + shift.getShiftId(),

	                    shift.getUserName(),

	                    dateTime(
	                            shift.getStartTime()
	                    ),

	                    dateTime(
	                            shift.getEndTime()
	                    ),

	                    shift.isOpen()
	                            ? "Đang mở"
	                            : "Đã đóng",

	                    shift.getInvoiceCount(),

	                    money(expected),

	                    moneyOrDash(
	                            shift.getCountedCash()
	                    ),

	                    signedMoneyOrDash(
	                            shift.getCashDifference()
	                    )
	                }
	        );
	    }


	    /*
	     * ====================================
	     * 6. Chọn dòng
	     * ====================================
	     */

	    if (!pagedShifts.isEmpty()) {

	        int selectedModelRow =
	                openRow >= 0
	                        ? openRow
	                        : 0;


	        int selectedViewRow =
	                historyTable
	                        .convertRowIndexToView(
	                                selectedModelRow
	                        );


	        if (selectedViewRow >= 0) {

	            historyTable
	                    .setRowSelectionInterval(
	                            selectedViewRow,
	                            selectedViewRow
	                    );

	        } else if (
	                historyTable.getRowCount() > 0
	        ) {

	            historyTable
	                    .setRowSelectionInterval(
	                            0,
	                            0
	                    );
	        }

	    } else {

	        historyTable.clearSelection();

	        allTransactions =
	                new ArrayList<>();

	        transactionTitle.setText(
	                "Thu/chi của ca"
	        );

	        transactionPagination.setCurrentPage(1);

	        renderTransactionPage();
	    }


	    updatingHistory = false;


	    /*
	     * Load bảng Thu / Chi theo ca được chọn.
	     */
	    loadSelectedTransactions();
	}

	private void loadSelectedTransactions() {
		int viewRow = historyTable.getSelectedRow();

		if (viewRow < 0) {

		    allTransactions =
		            new ArrayList<>();

		    transactionTitle.setText(
		            "Thu/chi của ca"
		    );

		    transactionPagination.setCurrentPage(1);

		    renderTransactionPage();

		    return;
		}

		int modelRow = historyTable.convertRowIndexToModel(viewRow);

		if (
		        modelRow < 0
		        || modelRow >= pagedShifts.size()
		) {
		    return;
		}

		Shift selectedShift =
		        pagedShifts.get(modelRow);

		transactionTitle.setText("Thu/chi của ca #" + selectedShift.getShiftId() + " — " + selectedShift.getUserName());

		SwingWorker<List<ShiftCashTransaction>, Void> worker = new SwingWorker<>() {

			@Override
			protected List<ShiftCashTransaction> doInBackground() {

				return shiftService.getTransactions(selectedShift.getShiftId());
			}

			@Override
			protected void done() {
				try {
					renderTransactions(get());

				} catch (Exception e) {
					renderTransactions(Collections.emptyList());
				}
			}
		};

		worker.execute();
	}

	private void renderTransactions(
	        List<ShiftCashTransaction> transactions
	) {

	    allTransactions =
	            transactions == null

	            ? new ArrayList<>()

	            : new ArrayList<>(
	                    transactions
	            );


	    /*
	     * Mỗi khi đổi ca,
	     * Thu/Chi quay về trang 1.
	     */
	    transactionPagination.setCurrentPage(1);


	    renderTransactionPage();
	}
	
	private void renderTransactionPage() {

	    /*
	     * ====================================
	     * 1. Lọc
	     * ====================================
	     */

	    List<ShiftCashTransaction> filtered =
	            getFilteredTransactions();

	    int totalItems =
	            filtered.size();


	    /*
	     * ====================================
	     * 2. Pagination
	     * ====================================
	     */

	    transactionPagination.setTotalItems(
	            totalItems
	    );


	    int pageSize =
	            transactionPagination.getPageSize();

	    int currentPage =
	            transactionPagination.getCurrentPage();


	    /*
	     * ====================================
	     * 3. Tính khoảng dữ liệu
	     * ====================================
	     */

	    int from =
	            totalItems == 0
	                    ? 0
	                    : (
	                        currentPage - 1
	                      ) * pageSize;


	    if (
	            from >= totalItems
	            && totalItems > 0
	    ) {

	        transactionPagination
	                .setCurrentPage(1);

	        currentPage = 1;

	        from = 0;
	    }


	    int to =
	            Math.min(
	                    from + pageSize,
	                    totalItems
	            );


	    /*
	     * ====================================
	     * 4. Render JTable
	     * ====================================
	     */

	    transactionModel.setRowCount(0);


	    if (totalItems > 0) {

	        for (
	                ShiftCashTransaction transaction
	                : filtered.subList(
	                        from,
	                        to
	                )
	        ) {

	            String typeLabel =
	                    transaction.isCashIn()
	                            ? "Thu tiền"
	                            : "Chi tiền";


	            transactionModel.addRow(
	                    new Object[] {

	                        transaction
	                                .getTransactionCode(),

	                        typeLabel,

	                        money(
	                                transaction
	                                        .getAmount()
	                        ),

	                        transaction.getReason(),

	                        transaction
	                                .getCreatedByName(),

	                        dateTime(
	                                transaction
	                                        .getCreatedAt()
	                        )
	                    }
	            );
	        }
	    }
	}
	
	/**
	 * Xuất dữ liệu của tab đang được mở.
	 *
	 * - Tab Lịch sử ca:
	 *      xuất toàn bộ ca sau tìm kiếm/lọc.
	 *
	 * - Tab Thu/Chi:
	 *      xuất toàn bộ giao dịch của ca đang chọn
	 *      sau tìm kiếm/lọc.
	 *
	 * Không chỉ xuất dữ liệu của trang pagination hiện tại.
	 */
	private void exportCurrentTab(String format) {

	    if (historyTabButton.isSelected()) {

	        exportShiftHistory(format);

	    } else {

	        exportShiftTransactions(format);
	    }
	}
	
	private void exportShiftHistory(String format) {

	    /*
	     * Lấy dữ liệu SAU khi đã áp dụng:
	     *
	     * - tìm kiếm
	     * - trạng thái
	     * - ngày
	     * - tháng
	     * - năm
	     *
	     * Không lấy pagedShifts vì pagedShifts
	     * chỉ chứa 10/20/... dòng của trang hiện tại.
	     */
	    List<Shift> shifts =
	            getFilteredShifts();


	    if (shifts.isEmpty()) {

	        AppAlert.warning(
	                this,
	                "Không có dữ liệu",
	                "Không có ca bán hàng nào phù hợp để xuất."
	        );

	        return;
	    }


	    /*
	     * Tiêu đề các cột trong file.
	     */
	    String[] headers = {
	            "Mã ca",
	            "Nhân viên",
	            "Bắt đầu",
	            "Kết thúc",
	            "Trạng thái",
	            "Hóa đơn",
	            "Tiền hệ thống",
	            "Tiền thực tế",
	            "Chênh lệch"
	    };


	    /*
	     * Chuyển List<Shift>
	     * thành List<Object[]> mà TableExportUtil cần.
	     */
	    List<Object[]> rows =
	            new ArrayList<>();


	    for (Shift shift : shifts) {

	        BigDecimal expected =
	                shift.isOpen()
	                        ? expectedOf(shift)
	                        : shift.getExpectedCash();


	        rows.add(
	                new Object[] {

	                        "#" + shift.getShiftId(),

	                        shift.getUserName(),

	                        dateTime(
	                                shift.getStartTime()
	                        ),

	                        dateTime(
	                                shift.getEndTime()
	                        ),

	                        shift.isOpen()
	                                ? "Đang mở"
	                                : "Đã đóng",

	                        shift.getInvoiceCount(),

	                        money(expected),

	                        moneyOrDash(
	                                shift.getCountedCash()
	                        ),

	                        signedMoneyOrDash(
	                                shift.getCashDifference()
	                        )
	                }
	        );
	    }


	    /*
	     * Gọi hàm xuất chung.
	     */
	    exportRows(
	            format,
	            "lich_su_ca",
	            "Lịch sử ca",
	            headers,
	            rows
	    );
	}
	
	private void exportShiftTransactions(String format) {

	    /*
	     * allTransactions chứa toàn bộ Thu/Chi
	     * của ca đang chọn.
	     *
	     * getFilteredTransactions() tiếp tục áp dụng
	     * tìm kiếm + loại + ngày/tháng/năm.
	     */
	    List<ShiftCashTransaction> transactions =
	            getFilteredTransactions();


	    if (transactions.isEmpty()) {

	        AppAlert.warning(
	                this,
	                "Không có dữ liệu",
	                "Ca đang chọn không có giao dịch Thu/Chi phù hợp để xuất."
	        );

	        return;
	    }


	    String[] headers = {
	            "Mã giao dịch",
	            "Loại",
	            "Số tiền",
	            "Lý do",
	            "Người tạo",
	            "Thời gian"
	    };


	    List<Object[]> rows =
	            new ArrayList<>();


	    for (
	            ShiftCashTransaction transaction
	            : transactions
	    ) {

	        String typeLabel =
	                transaction.isCashIn()
	                        ? "Thu tiền"
	                        : "Chi tiền";


	        rows.add(
	                new Object[] {

	                        transaction
	                                .getTransactionCode(),

	                        typeLabel,

	                        money(
	                                transaction.getAmount()
	                        ),

	                        transaction.getReason(),

	                        transaction
	                                .getCreatedByName(),

	                        dateTime(
	                                transaction.getCreatedAt()
	                        )
	                }
	        );
	    }


	    exportRows(
	            format,
	            "thu_chi_ca",
	            "Thu chi ca",
	            headers,
	            rows
	    );
	}
	
	private void exportRows(
	        String format,
	        String filePrefix,
	        String sheetName,
	        String[] headers,
	        List<Object[]> rows
	) {

	    /*
	     * VD:
	     *
	     * lich_su_ca_20260817_213500.csv
	     *
	     * hoặc:
	     *
	     * thu_chi_ca_20260817_213500.xlsx
	     */
	    String defaultFileName =
	            filePrefix
	            + "_"
	            + exportTimestamp()
	            + "."
	            + format;


	    /*
	     * Hiện hộp thoại Save As
	     * giống chức năng xuất của các màn quản lý.
	     */
	    File chosen =
	            FileUtil.chooseSaveLocation(
	                    this,
	                    defaultFileName
	            );


	    /*
	     * Người dùng bấm Cancel.
	     */
	    if (chosen == null) {
	        return;
	    }


	    /*
	     * Đảm bảo đúng đuôi file.
	     */
	    File file =
	            ensureExportExtension(
	                    chosen,
	                    format
	            );


	    /*
	     * Chạy xuất file ở background thread.
	     *
	     * Tránh giao diện bị đứng nếu dữ liệu lớn.
	     */
	    setBusy(true);


	    SwingWorker<Integer, Void> worker =
	            new SwingWorker<>() {

	        @Override
	        protected Integer doInBackground()
	                throws Exception {

	            if ("csv".equalsIgnoreCase(format)) {

	                TableExportUtil.exportCsv(
	                        file,
	                        headers,
	                        rows
	                );

	            } else {

	                TableExportUtil.exportExcel(
	                        file,
	                        sheetName,
	                        headers,
	                        rows
	                );
	            }


	            return rows.size();
	        }


	        @Override
	        protected void done() {

	            setBusy(false);


	            try {

	                int count = get();


	                AppAlert.success(
	                        ShiftManagementPanel.this,
	                        "Xuất file thành công",
	                        "Đã xuất "
	                        + count
	                        + " dòng vào file "
	                        + file.getName()
	                );


	            } catch (Exception e) {

	                e.printStackTrace();


	                AppAlert.error(
	                        ShiftManagementPanel.this,
	                        "Xuất file thất bại",
	                        e.getMessage() != null
	                                ? e.getMessage()
	                                : "Không thể tạo file."
	                );
	            }
	        }
	    };


	    worker.execute();
	}
	
	private static File ensureExportExtension(
	        File file,
	        String extension
	) {

	    String name =
	            file.getName();


	    /*
	     * Người dùng đã nhập đúng đuôi:
	     *
	     * abc.xlsx
	     */
	    if (
	            name.toLowerCase()
	                    .endsWith(
	                            "." + extension.toLowerCase()
	                    )
	    ) {

	        return file;
	    }


	    /*
	     * Nếu người dùng nhập:
	     *
	     * abc
	     *
	     * thì tự thành:
	     *
	     * abc.xlsx
	     */
	    int dot =
	            name.lastIndexOf('.');


	    String baseName =
	            dot > 0
	                    ? name.substring(0, dot)
	                    : name;


	    return new File(
	            file.getParentFile(),
	            baseName + "." + extension
	    );
	}
	
	private static String exportTimestamp() {

	    return LocalDateTime.now()
	            .format(
	                    DateTimeFormatter.ofPattern(
	                            "yyyyMMdd_HHmmss"
	                    )
	            );
	}

	/**
	 * Hien hop thoai nhap tien dau ca.
	 */
	private void showOpenDialog() {
		JTextField moneyField = inputField("0");

		JTextArea noteArea = noteArea();

		JPanel form = formPanel();

		addField(form, "Tiền mặt đầu ca (VND) *", moneyField);

		addField(form, "Ghi chú", new JScrollPane(noteArea));

		int option = JOptionPane.showConfirmDialog(this, form, "Mở ca bán hàng", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		/*
		 * Nguoi dung bam Cancel hoac dong cua so.
		 */
		if (option != JOptionPane.OK_OPTION) {
			return;
		}

		BigDecimal openingCash = parseMoney(moneyField.getText());

		if (openingCash == null) {
			AppAlert.warning(this, "Tiền đầu ca phải là " + "số nguyên VND không âm.");

			return;
		}

		runOperation(() -> shiftService.openMyShift(openingCash, noteArea.getText()));
	}

	/**
	 * Hien hop thoai ghi nhan thu hoac chi tien.
	 */
	private void showCashMovementDialog(String type) {
		boolean cashIn = ShiftCashTransaction.CASH_IN.equals(type);

		JTextField moneyField = inputField("");

		JTextArea reasonArea = noteArea();

		JPanel form = formPanel();

		addField(form, "Số tiền (VND) *", moneyField);

		String reasonLabel;

		if (cashIn) {
			reasonLabel = "Lý do thu *";
		} else {
			reasonLabel = "Lý do chi *";
		}

		addField(form, reasonLabel, new JScrollPane(reasonArea));

		String dialogTitle;

		if (cashIn) {
			dialogTitle = "Ghi nhận thu tiền";
		} else {
			dialogTitle = "Ghi nhận chi tiền";
		}

		int option = JOptionPane.showConfirmDialog(this, form, dialogTitle, JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.PLAIN_MESSAGE);

		if (option != JOptionPane.OK_OPTION) {
			return;
		}

		BigDecimal amount = parseMoney(moneyField.getText());

		if (amount == null) {
			AppAlert.warning(this, "Số tiền phải là số nguyên " + "VND lớn hơn 0.");

			return;
		}

		runOperation(() -> shiftService.addCashMovement(type, amount, reasonArea.getText()));
	}

	/**
	 * Tinh tien he thong tren background thread truoc khi hien hop thoai dong ca.
	 */
	private void loadClosePreview() {
		setBusy(true);

		SwingWorker<ShiftService.OperationResult<ShiftCashSummary>, Void> worker = new SwingWorker<>() {

			@Override
			protected ShiftService.OperationResult<ShiftCashSummary> doInBackground() {

				return shiftService.previewClose();
			}

			@Override
			protected void done() {
				setBusy(false);

				try {
					ShiftService.OperationResult<ShiftCashSummary> result = get();

					if (!result.isSuccess()) {
						AppAlert.warning(ShiftManagementPanel.this, result.getMessage());

						return;
					}

					showCloseDialog(result.getData());

				} catch (Exception e) {
					AppAlert.error(ShiftManagementPanel.this, "Không thể tính số tiền đóng ca.");
				}
			}
		};

		worker.execute();
	}

	/**
	 * Hien cong thuc doi soat va cho nhan vien nhap tien dem thuc te.
	 */
	private void showCloseDialog(ShiftCashSummary summary) {
		/*
		 * Mac dinh dien tien he thong. Nhan vien phai sua lai theo tien dem thuc te.
		 */
		JTextField countedField = inputField(summary.getExpectedCash().toPlainString());

		JTextArea noteArea = noteArea();

		JPanel form = formPanel();

		String formulaText = "<html>" + "Tiền hệ thống: <b>" + money(summary.getExpectedCash()) + "</b><br>" + "="
				+ money(summary.getOpeningCash()) + " đầu ca + " + money(summary.getCashSales()) + " bán tiền mặt + "
				+ money(summary.getCashIn()) + " thu - " + money(summary.getCashOut()) + " chi - "
				+ money(summary.getCashRefunds()) + " hoàn tiền" + "</html>";

		JLabel formula = new JLabel(formulaText);

		formula.setFont(AppFont.BODY);

		formula.setForeground(AppColor.TEXT_SECONDARY);

		formula.setBorder(new EmptyBorder(0, 0, 8, 0));

		formula.setAlignmentX(Component.LEFT_ALIGNMENT);

		form.add(formula);

		addField(form, "Tiền kiểm thực tế (VND) *", countedField);

		addField(form, "Giải trình " + "(bắt buộc nếu có chênh lệch)", new JScrollPane(noteArea));

		int option = JOptionPane.showConfirmDialog(this, form, "Đóng ca và đối soát", JOptionPane.OK_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE);

		if (option != JOptionPane.OK_OPTION) {
			return;
		}

		BigDecimal countedCash = parseMoney(countedField.getText());

		if (countedCash == null) {
			AppAlert.warning(this, "Tiền kiểm thực tế phải là " + "số nguyên VND không âm.");

			return;
		}

		BigDecimal difference = summary.differenceFrom(countedCash);

		if (difference.signum() != 0 && noteArea.getText().isBlank()) {
			AppAlert.warning(this, "Chênh lệch " + signedMoneyOrDash(difference) + ". Bạn phải nhập giải trình "
					+ "trước khi đóng ca.");

			return;
		}

		runOperation(() -> shiftService.closeMyShift(countedCash, noteArea.getText()));
	}

	/**
	 * Chay mot thao tac ghi du lieu tren background.
	 */
	private void runOperation(Operation operation) {
		setBusy(true);

		SwingWorker<ShiftService.OperationResult<?>, Void> worker = new SwingWorker<>() {

			@Override
			protected ShiftService.OperationResult<?> doInBackground() {

				return operation.execute();
			}

			@Override
			protected void done() {
				setBusy(false);

				try {
					ShiftService.OperationResult<?> result = get();

					if (result.isSuccess()) {
						AppAlert.success(ShiftManagementPanel.this, result.getMessage());

						/*
						 * Tai lai ca, tong tien, lich su va giao dich.
						 */
						loadData();

					} else {
						AppAlert.warning(ShiftManagementPanel.this, result.getMessage());
					}

				} catch (Exception e) {
					AppAlert.error(ShiftManagementPanel.this, "Thao tác ca bán hàng thất bại.");
				}
			}
		};

		worker.execute();
	}

	private void setBusy(boolean busy) {
		if (busy) {
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		} else {
			setCursor(Cursor.getDefaultCursor());
		}

		refreshButton.setEnabled(!busy);

		if (busy) {
			openButton.setEnabled(false);
			cashInButton.setEnabled(false);
			cashOutButton.setEnabled(false);
			closeButton.setEnabled(false);

		} else {
			renderSummary(currentOpenShift);
		}
	}

	private static JTable buildTable(DefaultTableModel model) {
		JTable table = new JTable(model);

		table.setRowHeight(34);

		table.setFont(AppFont.BODY);

		table.setForeground(AppColor.TABLE_ROW_TEXT);

		table.setBackground(AppColor.WHITE);

		table.setGridColor(AppColor.TABLE_GRID);

		table.setShowVerticalLines(false);

		table.setSelectionBackground(AppColor.ACCENT_SELECTION_BG);

		table.setSelectionForeground(AppColor.TEXT_PRIMARY);

		table.getTableHeader().setFont(AppFont.SMALL_BOLD);

		table.getTableHeader().setBackground(AppColor.TABLE_HEADER_BG);

		table.getTableHeader().setForeground(Color.WHITE);

		table.setAutoCreateRowSorter(true);

		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		DefaultTableCellRenderer renderer = new DefaultTableCellRenderer();

		renderer.setBorder(new EmptyBorder(0, 10, 0, 10));

		table.setDefaultRenderer(Object.class, renderer);

		return table;
	}

	private static DefaultTableModel readOnlyModel(String... columns) {
		return new DefaultTableModel(columns, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};
	}

	private static JLabel valueLabel() {
		JLabel label = new JLabel("—");

		label.setFont(AppFont.HEADING_MD);

		label.setForeground(AppColor.TEXT_PRIMARY);

		return label;
	}

	private static JButton actionButton(String text, Color color) {

		JButton button = new JButton(text) {

			@Override
			protected void paintComponent(Graphics graphics) {

				Graphics2D g2 = (Graphics2D) graphics.create();

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				Color background;

				/*
				 * Enabled: dùng màu thật.
				 *
				 * Disabled: dùng màu đó nhưng nhạt hơn.
				 */
				if (isEnabled()) {
					background = color;
				} else {
					background = mutedActionColor(color);
				}

				/*
				 * Khi nhấn.
				 */
				if (isEnabled() && getModel().isPressed()) {

					background = blendColor(color, Color.BLACK, 0.12);

					/*
					 * Khi rê chuột.
					 */
				} else if (isEnabled() && getModel().isRollover()) {

					background = blendColor(color, Color.WHITE, 0.10);
				}

				/*
				 * Vẽ nền bo góc.
				 */
				g2.setColor(background);

				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

				/*
				 * Vẽ chữ chính giữa nút.
				 */
				String label = getText();

				FontMetrics metrics = g2.getFontMetrics(getFont());

				int x = (getWidth() - metrics.stringWidth(label)) / 2;

				int y = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();

				g2.setFont(getFont());

				if (isEnabled()) {

					g2.setColor(Color.WHITE);

				} else {

					g2.setColor(blendColor(Color.WHITE, background, 0.28));
				}

				g2.drawString(label, x, y);

				g2.dispose();
			}
		};

		button.setFont(AppFont.BUTTON);

		button.setForeground(Color.WHITE);

		button.setBackground(color);

		button.setFocusPainted(false);

		button.setBorderPainted(false);

		button.setContentAreaFilled(false);

		button.setOpaque(false);

		button.setRolloverEnabled(true);

		button.setBorder(new EmptyBorder(9, 16, 9, 16));

		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return button;
	}

	private static JToggleButton tabButton(String text) {

		JToggleButton button = new JToggleButton(text);

		button.setFont(AppFont.BUTTON);

		button.setFocusPainted(false);

		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		button.setBorder(new EmptyBorder(9, 16, 9, 16));

		return button;
	}

	private void updateTabStyles() {

		styleTab(historyTabButton);

		styleTab(transactionTabButton);
	}

	private void styleTab(JToggleButton button) {

		if (button.isSelected()) {

			button.setBackground(AppColor.ACCENT);

			button.setForeground(Color.WHITE);

		} else {

			button.setBackground(AppColor.WHITE);

			button.setForeground(AppColor.TEXT_SECONDARY);
		}
	}

	private static Color mutedActionColor(Color base) {

		return blendColor(base, AppColor.PAGE_BG, 0.48);
	}

	private static Color blendColor(Color source, Color target, double ratio) {

		double safeRatio = Math.max(0.0, Math.min(1.0, ratio));

		int red = (int) Math.round(source.getRed() * (1.0 - safeRatio) + target.getRed() * safeRatio);

		int green = (int) Math.round(source.getGreen() * (1.0 - safeRatio) + target.getGreen() * safeRatio);

		int blue = (int) Math.round(source.getBlue() * (1.0 - safeRatio) + target.getBlue() * safeRatio);

		return new Color(red, green, blue);
	}

	private static JTextField filterSearchField(String placeholder) {

		JTextField field = new JTextField();

		field.setFont(AppFont.BODY);

		field.setForeground(AppColor.TEXT_PRIMARY);

		field.setBackground(AppColor.WHITE);

		field.setPreferredSize(new Dimension(330, 38));

		field.setBorder(BorderFactory.createCompoundBorder(new LineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(0, 12, 0, 12)));

		field.putClientProperty("JTextField.placeholderText", placeholder);

		return field;
	}

	private static JComboBox<String> filterCombo(String... values) {

		JComboBox<String> combo = new JComboBox<>(values);

		combo.setFont(AppFont.BODY);

		combo.setBackground(AppColor.WHITE);

		combo.setForeground(AppColor.TEXT_PRIMARY);

		combo.setPreferredSize(new Dimension(170, 38));

		return combo;
	}

	private static JComboBox<String> dayFilterCombo() {

		String[] values = new String[32];

		values[0] = "Tất cả ngày";

		for (int i = 1; i <= 31; i++) {
			values[i] = String.valueOf(i);
		}

		JComboBox<String> combo = filterCombo(values);

		combo.setPreferredSize(new Dimension(120, 38));

		return combo;
	}

	private static JComboBox<String> monthFilterCombo() {

		String[] values = new String[13];

		values[0] = "Tất cả tháng";

		for (int i = 1; i <= 12; i++) {
			values[i] = String.valueOf(i);
		}

		JComboBox<String> combo = filterCombo(values);

		combo.setPreferredSize(new Dimension(125, 38));

		return combo;
	}

	private static JComboBox<String> yearFilterCombo() {

		int currentYear = LocalDate.now().getYear();

		/*
		 * Năm hiện tại + 10 năm trước. Ví dụ 2026 -> 2026, 2025, ..., 2016.
		 */
		String[] values = new String[12];

		values[0] = "Tất cả năm";

		for (int i = 1; i < values.length; i++) {

			values[i] = String.valueOf(currentYear - (i - 1));
		}

		JComboBox<String> combo = filterCombo(values);

		combo.setPreferredSize(new Dimension(125, 38));

		return combo;
	}

	private void bindFilters() {

		/*
		 * ============================= LỊCH SỬ CA =============================
		 */

		historySearchField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent event) {
				applyHistoryFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				applyHistoryFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				applyHistoryFilter();
			}
		});

		historyStatusFilter.addActionListener(event -> applyHistoryFilter());

		historyDayFilter.addActionListener(event -> applyHistoryFilter());

		historyMonthFilter.addActionListener(event -> applyHistoryFilter());

		historyYearFilter.addActionListener(event -> applyHistoryFilter());

		/*
		 * ============================= THU / CHI =============================
		 */

		transactionSearchField.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent event) {
				applyTransactionFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent event) {
				applyTransactionFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent event) {
				applyTransactionFilter();
			}
		});

		transactionTypeFilter.addActionListener(event -> applyTransactionFilter());

		transactionDayFilter.addActionListener(event -> applyTransactionFilter());

		transactionMonthFilter.addActionListener(event -> applyTransactionFilter());

		transactionYearFilter.addActionListener(event -> applyTransactionFilter());
	}

	private void bindPagination() {

	    /*
	     * =====================================
	     * LỊCH SỬ CA
	     * =====================================
	     */

	    historyPagination.addPropertyChangeListener(
	            "pageChanged",
	            event -> {

	                renderHistory();
	            }
	    );


	    historyPagination.addPropertyChangeListener(
	            "pageSizeChanged",
	            event -> {

	                historyPagination.setCurrentPage(1);

	                renderHistory();
	            }
	    );


	    /*
	     * =====================================
	     * THU / CHI
	     * =====================================
	     */

	    transactionPagination.addPropertyChangeListener(
	            "pageChanged",
	            event -> {

	                renderTransactionPage();
	            }
	    );


	    transactionPagination.addPropertyChangeListener(
	            "pageSizeChanged",
	            event -> {

	                transactionPagination.setCurrentPage(1);

	                renderTransactionPage();
	            }
	    );
	}

	private void applyHistoryFilter() {

	    historyPagination.setCurrentPage(1);

	    renderHistory();
	}

	private void applyTransactionFilter() {

	    transactionPagination.setCurrentPage(1);

	    renderTransactionPage();
	}

	private List<Shift> getFilteredShifts() {

		String query = lower(historySearchField.getText());

		String selectedStatus = String.valueOf(historyStatusFilter.getSelectedItem());

		List<Shift> result = new ArrayList<>();

		for (Shift shift : visibleShifts) {

			String statusLabel = shift.isOpen() ? "Đang mở" : "Đã đóng";

			/*
			 * Tìm kiếm.
			 */
			boolean textMatches = query.isEmpty()

					|| lower("#" + shift.getShiftId()).contains(query)

					|| lower(shift.getUserName()).contains(query)

					|| lower(dateTime(shift.getStartTime())).contains(query)

					|| lower(dateTime(shift.getEndTime())).contains(query)

					|| lower(statusLabel).contains(query);

			/*
			 * Trạng thái.
			 */
			boolean statusMatches = "Tất cả trạng thái".equals(selectedStatus)

					|| selectedStatus.equals(statusLabel);

			/*
			 * Ngày / Tháng / Năm.
			 *
			 * Lịch sử ca lấy ngày Bắt đầu ca.
			 */
			boolean dateMatches = matchesSelectedDate(shift.getStartTime(), historyDayFilter, historyMonthFilter,
					historyYearFilter);

			if (textMatches && statusMatches && dateMatches) {

				result.add(shift);
			}
		}

		return result;
	}

	private List<ShiftCashTransaction> getFilteredTransactions() {

		String query = lower(transactionSearchField.getText());

		String selectedType = String.valueOf(transactionTypeFilter.getSelectedItem());

		List<ShiftCashTransaction> result = new ArrayList<>();

		for (ShiftCashTransaction transaction : allTransactions) {

			String typeLabel = transaction.isCashIn() ? "Thu tiền" : "Chi tiền";

			/*
			 * Tìm kiếm.
			 */
			boolean textMatches = query.isEmpty()

					|| lower(transaction.getTransactionCode()).contains(query)

					|| lower(typeLabel).contains(query)

					|| lower(money(transaction.getAmount())).contains(query)

					|| lower(transaction.getReason()).contains(query)

					|| lower(transaction.getCreatedByName()).contains(query)

					|| lower(dateTime(transaction.getCreatedAt())).contains(query);

			/*
			 * Loại Thu / Chi.
			 */
			boolean typeMatches = "Tất cả loại".equals(selectedType)

					|| selectedType.equals(typeLabel);

			/*
			 * Lọc theo ngày tạo giao dịch.
			 */
			boolean dateMatches = matchesSelectedDate(transaction.getCreatedAt(), transactionDayFilter,
					transactionMonthFilter, transactionYearFilter);

			if (textMatches && typeMatches && dateMatches) {

				result.add(transaction);
			}
		}

		return result;
	}
	
	private static boolean matchesSelectedDate(
	        LocalDateTime dateTime,
	        JComboBox<String> dayBox,
	        JComboBox<String> monthBox,
	        JComboBox<String> yearBox
	) {

	    if (dateTime == null) {
	        return false;
	    }

	    Integer day =
	            selectedNumber(dayBox);

	    Integer month =
	            selectedNumber(monthBox);

	    Integer year =
	            selectedNumber(yearBox);


	    return (
	            day == null
	            || dateTime.getDayOfMonth() == day
	    )
	    &&
	    (
	            month == null
	            || dateTime.getMonthValue() == month
	    )
	    &&
	    (
	            year == null
	            || dateTime.getYear() == year
	    );
	}

	private static Integer selectedNumber(
	        JComboBox<String> combo
	) {

	    Object selected =
	            combo.getSelectedItem();

	    if (selected == null) {
	        return null;
	    }

	    String value =
	            selected.toString();

	    /*
	     * "Tất cả ngày"
	     * "Tất cả tháng"
	     * "Tất cả năm"
	     */
	    if (value.startsWith("Tất cả")) {
	        return null;
	    }

	    try {

	        return Integer.parseInt(value);

	    } catch (NumberFormatException e) {

	        return null;
	    }
	}

	private static String lower(
	        String value
	) {

	    return value == null
	            ? ""
	            : value.toLowerCase();
	}

	private static JTextField inputField(String value) {
		JTextField field = new JTextField(value, 24);

		field.setFont(AppFont.FIELD);

		return field;
	}

	private static JTextArea noteArea() {
		JTextArea area = new JTextArea(3, 24);

		area.setFont(AppFont.FIELD);

		area.setLineWrap(true);

		area.setWrapStyleWord(true);

		return area;
	}

	private static JPanel formPanel() {
		JPanel panel = new JPanel();

		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

		panel.setBorder(new EmptyBorder(8, 8, 8, 8));

		return panel;
	}

	private static void addField(JPanel panel, String label, JComponent component) {
		JLabel title = new JLabel(label);

		title.setFont(AppFont.BODY_BOLD);

		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		component.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(title);

		panel.add(Box.createVerticalStrut(5));

		panel.add(component);

		panel.add(Box.createVerticalStrut(12));
	}

	/**
	 * Chap nhan:
	 *
	 * 1000000 1.000.000 1 000 000
	 *
	 * Khong chap nhan:
	 *
	 * -1000 1000.5 abc
	 */
	private static BigDecimal parseMoney(String raw) {
		if (raw == null) {
			return null;
		}

		String normalized = raw.trim().replace(".", "").replace(" ", "");

		/*
		 * Chi chap nhan chu so tu 0 den 9.
		 */
		if (!normalized.matches("\\d+")) {
			return null;
		}

		try {
			return new BigDecimal(normalized);

		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static BigDecimal expectedOf(Shift shift) {
		return shift.getOpeningCash()

				.add(shift.getCashSales())

				.add(shift.getCashIn())

				.subtract(shift.getCashOut())

				.subtract(shift.getCashRefunds());
	}

	private static String money(BigDecimal value) {
		if (value == null) {
			return "0 đ";
		}

		return NumberUtil.formatThousands(value.longValue()) + " đ";
	}

	private static String moneyOrDash(BigDecimal value) {
		if (value == null) {
			return "—";
		}

		return money(value);
	}

	private static String signedMoneyOrDash(BigDecimal value) {
		if (value == null) {
			return "—";
		}

		String prefix = value.signum() > 0 ? "+" : "";

		return prefix + money(value);
	}

	private static String dateTime(LocalDateTime value) {
		if (value == null) {
			return "—";
		}

		return value.format(DATE_TIME);
	}

	@FunctionalInterface
	private interface Operation {

		ShiftService.OperationResult<?> execute();
	}

	private static final class DashboardData {

		private final Shift openShift;

		private final List<Shift> history;

		private DashboardData(Shift openShift, List<Shift> history) {
			this.openShift = openShift;
			this.history = history;
		}
	}

}
