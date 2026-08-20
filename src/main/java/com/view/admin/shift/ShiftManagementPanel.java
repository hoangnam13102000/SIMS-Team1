package com.view.admin.shift;

import com.components.Pagination;
import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.BaseTable;
import com.components.DatePickerField;
import com.components.FilterDropdown;
import com.components.SectionHeader;
import com.components.StatCard;
import com.components.table.ActionColumn;
import com.components.table.RowColorProvider;
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
import com.utils.CurrencyDocumentFilter;
import com.utils.FileUtil;
import com.utils.TableExportUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

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

	/*
	 * 5 StatCard tổng quan ca - đồng bộ với StatCard dùng chung ở
	 * AuditLogPanel/DashboardPanel/... thay cho ô tự vẽ trước đây.
	 */
	private final StatCard statusCard =
			new StatCard("Trạng thái ca", "—", FontAwesomeSolid.CLOCK, AppColor.ACCENT, true);

	private final StatCard openingCard =
			new StatCard("Tiền đầu ca", "—", FontAwesomeSolid.WALLET, AppColor.SUCCESS, true);

	private final StatCard salesCard =
			new StatCard("Doanh thu tiền mặt", "—", FontAwesomeSolid.MONEY_BILL_WAVE, AppColor.ACCENT, true);

	private final StatCard movementsCard =
			new StatCard("Thu / chi", "—", FontAwesomeSolid.EXCHANGE_ALT, AppColor.WARNING, true);

	private final StatCard expectedCard =
			new StatCard("Tiền hệ thống", "—", FontAwesomeSolid.CALCULATOR, AppColor.INFO, true);

	private final JButton openButton =
			actionButton("Mở ca", AppColor.SUCCESS, FontAwesomeSolid.PLAY_CIRCLE);

	private final JButton cashInButton =
			actionButton("Thu tiền", AppColor.ACCENT, FontAwesomeSolid.ARROW_DOWN);

	private final JButton cashOutButton =
			actionButton("Chi tiền", AppColor.WARNING, FontAwesomeSolid.ARROW_UP);

	private final JButton closeButton =
			actionButton("Đóng ca", AppColor.ERROR, FontAwesomeSolid.LOCK);

	private final JButton refreshButton =
			outlineButton("Làm mới", FontAwesomeSolid.SYNC_ALT);

	/*
	 * Bảng lịch sử ca + bảng Thu/Chi dùng chung BaseTable (shadow bo góc, header
	 * có mũi tên sort, sọc dòng...) để đồng bộ hình thức với các trang CRUD
	 * khác (AuditLogPanel, InvoicePanel...) thay vì JTable tự vẽ style riêng
	 * như trước.
	 */
	private final BaseTable historyTable = buildHistoryTable();

	/*
	 * Tìm kiếm + lọc dùng chung BaseSearch/FilterDropdown/DatePickerField,
	 * đồng bộ giao diện với các trang CRUD khác (vd AuditLogPanel) thay vì
	 * JTextField/JComboBox tự vẽ riêng như trước.
	 */
	private final BaseSearch historySearchField = new BaseSearch("Tìm mã ca, nhân viên...");

	private final FilterDropdown<String> historyStatusFilter =
			new FilterDropdown<>(FontAwesomeSolid.FILTER, new String[] { "Tất cả trạng thái", "Đang mở", "Chờ duyệt", "Đã duyệt", "Cần kiểm lại" });

	private DatePickerField historyDateFrom;
	private DatePickerField historyDateTo;

	private final Pagination historyPagination =
	        new Pagination();

	private final BaseTable transactionTable = buildTransactionTable();

	private final BaseSearch transactionSearchField = new BaseSearch("Tìm mã giao dịch, lý do, người tạo...");

	private final FilterDropdown<String> transactionTypeFilter =
			new FilterDropdown<>(FontAwesomeSolid.FILTER, new String[] { "Tất cả loại", "Thu tiền", "Chi tiền" });

	private DatePickerField transactionDateFrom;
	private DatePickerField transactionDateTo;

	private boolean adjustingHistoryDateFilter;
	private boolean adjustingTransactionDateFilter;

	private final Pagination transactionPagination =
	        new Pagination();

	private final JLabel transactionTitle = new JLabel("Thu/chi của ca");

	private final JLabel historyCountLabel = new JLabel();

	private final JLabel transactionCountLabel = new JLabel();

	private final JToggleButton historyTabButton = tabButton("Lịch sử ca gần nhất", FontAwesomeSolid.HISTORY);

	private final JToggleButton transactionTabButton = tabButton("Thu / chi của ca", FontAwesomeSolid.EXCHANGE_ALT);

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
			subtitle = "Mở ca trước khi bán hàng, " + "ghi nhận mọi khoản thu/chi " + "và gửi đối soát khi đóng ca";
		} else {
			subtitle = "Theo dõi lịch sử ca và " + "chênh lệch quỹ của " + "nhân viên bán hàng";
		}

		SectionHeader header = new SectionHeader(
		        FontAwesomeSolid.CLOCK,
		        AppColor.ACCENT,
		        "Ca của tôi & đối soát quỹ",
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

		/*
		 * Sua loi UX: truoc day moi nut duoc gan listener 2 lan (tao dialog mo
		 * ca / hop thoai thu-chi hien ra 2 lan lien tiep khi bam 1 lan). Gio
		 * chi gan 1 lan cho moi nut.
		 */
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

		historyTable.getTable().getSelectionModel().addListSelectionListener(event -> {
			if (!event.getValueIsAdjusting() && !updatingHistory) {
				loadSelectedTransactions();
			}
		});

		/*
		 * Nut "Xem chi tiet" o bang Lich su ca: chon dong chi doi tab Thu/Chi
		 * ben duoi (thong tin tom tat), con nut nay mo hop thoai day du - gio
		 * mo/dong, ghi chu mo/dong ca, nguoi duyet + ghi chu duyet/tu choi va
		 * toan bo giao dich thu/chi cua ca do - khong phai doi/lam moi tab.
		 */
		historyTable.setActionColumn(new ActionColumn()
				.add("resubmit", FontAwesomeSolid.REDO, AppColor.WARNING, "Kiểm đếm lại & gửi",
						this::resubmitShiftAtModelRow, this::isRejectedAtModelRow)
				.add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết",
						this::viewShiftDetail));

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

		cards.setMaximumSize(new Dimension(Integer.MAX_VALUE, StatCard.PREFERRED_HEIGHT));

		cards.add(statusCard);
		cards.add(openingCard);
		cards.add(salesCard);
		cards.add(movementsCard);
		cards.add(expectedCard);

		return cards;
	}

	private JPanel buildActions() {
		JPanel row = new JPanel(new BorderLayout());

		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

		JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));

		actions.setOpaque(false);

		if (canOperate) {
			actions.add(openButton);
			actions.add(cashInButton);
			actions.add(cashOutButton);
			actions.add(closeButton);

			/*
			 * Vạch chia nhóm nút nghiệp vụ (mở/thu/chi/đóng ca) với nút
			 * "Làm mới" - phân tách rõ hành động ghi dữ liệu và hành động
			 * chỉ xem lại dữ liệu, tránh bấm nhầm.
			 */
			JSeparator separator = new JSeparator(SwingConstants.VERTICAL);

			separator.setPreferredSize(new Dimension(1, 24));

			separator.setForeground(AppColor.BORDER);

			actions.add(separator);
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

		/*
		 * Truoc day chi 430px nen JScrollPane trong BaseTable chi hien duoc
		 * 1-2 dong roi bi cat ngang, phai cuon moi thay het du lieu. Tang len
		 * de thay duoc nhieu dong hon cung 1 luc (con lai se tu cuon ben
		 * trong bang neu trang co nhieu hon so dong hien thi duoc).
		 */
		wrapper.setPreferredSize(new Dimension(900, 640));

		wrapper.setMinimumSize(new Dimension(400, 320));

		wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		wrapper.add(tabs, BorderLayout.NORTH);

		wrapper.add(cards, BorderLayout.CENTER);

		return wrapper;
	}

	private JPanel buildHistoryFilterBar() {

	    historyDateFrom = new DatePickerField(null, true);
	    historyDateTo = new DatePickerField(null, true);

	    return buildFilterBar(
	            historySearchField,
	            historyStatusFilter,
	            historyDateFrom,
	            historyDateTo,
	            historyCountLabel
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
	     * Tìm kiếm + loại + khoảng ngày nằm chung một dòng,
	     * cùng bố cục "search trái - bộ lọc trái - đếm dòng phải"
	     * như toolbar của các trang CRUD khác (BaseCrudPanel).
	     */
	    transactionDateFrom = new DatePickerField(null, true);
	    transactionDateTo = new DatePickerField(null, true);

	    JPanel filters =
	            buildFilterBar(
	                    transactionSearchField,
	                    transactionTypeFilter,
	                    transactionDateFrom,
	                    transactionDateTo,
	                    transactionCountLabel
	            );

	    filters.setAlignmentX(
	            Component.LEFT_ALIGNMENT
	    );

	    panel.add(filters);

	    return panel;
	}

	/**
	 * Thanh công cụ tìm kiếm + lọc dùng chung cho cả 2 tab, bố cục giống
	 * toolbar của BaseCrudPanel: tìm kiếm + các bộ lọc gộp bên trái, nhãn
	 * đếm số dòng bên phải. Khoảng "Từ ngày - Đến ngày" thay cho 3 combo
	 * ngày/tháng/năm rời rạc trước đây, đỡ rối mắt và dễ chọn khoảng ngày
	 * bất kỳ hơn (đồng bộ với bộ lọc thời gian của Nhật ký hệ thống).
	 */
	private JPanel buildFilterBar(
	        BaseSearch searchField,
	        FilterDropdown<String> comboBox,
	        DatePickerField dateFrom,
	        DatePickerField dateTo,
	        JLabel countLabel
	) {

	    JPanel bar = new JPanel(new BorderLayout(AppSpacing.MD, 0));

	    bar.setOpaque(false);

	    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));

	    left.setOpaque(false);

	    left.add(searchField);

	    left.add(comboBox);

	    dateFrom.setPreferredSize(new Dimension(120, 38));
	    dateFrom.setToolTipText("Từ ngày");

	    JLabel sep = new JLabel("–");

	    sep.setFont(AppFont.BODY);

	    sep.setForeground(AppColor.TEXT_MUTED);

	    dateTo.setPreferredSize(new Dimension(120, 38));
	    dateTo.setToolTipText("Đến ngày");

	    JPanel dateRange = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));

	    dateRange.setOpaque(false);

	    dateRange.add(dateFrom);
	    dateRange.add(sep);
	    dateRange.add(dateTo);

	    left.add(dateRange);

	    countLabel.setFont(AppFont.SMALL);

	    countLabel.setForeground(AppColor.TEXT_MUTED);

	    bar.add(left, BorderLayout.WEST);

	    bar.add(countLabel, BorderLayout.EAST);

	    return bar;
	}

	private JPanel filterTableCard(
	        JComponent header,
	        BaseTable table,
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

	    /*
	     * BaseTable tu quan ly JScrollPane + border bo goc/do bong rieng
	     * (giong bang o AuditLogPanel/InvoicePanel...), nen them thang vao
	     * card thay vi boc them 1 lop JScrollPane thu cong nhu truoc.
	     */

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
	            table,
	            BorderLayout.CENTER
	    );

	    card.add(
	            paginationWrapper,
	            BorderLayout.SOUTH
	    );


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
			statusCard.setValue("ĐANG MỞ  #" + shift.getShiftId());
			statusCard.setTrend("Đang bán hàng", true);

			openingCard.setValue(money(shift.getOpeningCash()));

			salesCard.setValue(money(shift.getCashSales()));

			movementsCard.setValue("+" + money(shift.getCashIn()) + " / -" + money(shift.getCashOut()));

			// P5 blind count: nhan vien khong duoc thay con so quy he thong khi ca dang mo.
			expectedCard.setValue("••••••");
			expectedCard.setTrend("Ẩn đến khi xác nhận tiền đếm", false);

		} else {
			statusCard.setValue("CHƯA MỞ CA");
			statusCard.setTrend("Chưa bán hàng", false);

			openingCard.setValue("—");
			salesCard.setValue("—");
			movementsCard.setValue("—");
			expectedCard.setValue("—");
		}

		openButton.setEnabled(canOperate && !open);

		cashInButton.setEnabled(canOperate && open);

		cashOutButton.setEnabled(canOperate && open);

		closeButton.setEnabled(canOperate && open);

		/*
		 * Tooltip giải thích lý do nút bị mờ (disabled) thay vì để người
		 * dùng tự đoán tại sao không bấm được - đặc biệt hữu ích cho nhân
		 * viên mới chưa quen quy trình mở/đóng ca.
		 */
		if (!canOperate) {
			String noPermission = "Bạn không có quyền thao tác ca bán hàng";

			openButton.setToolTipText(noPermission);
			cashInButton.setToolTipText(noPermission);
			cashOutButton.setToolTipText(noPermission);
			closeButton.setToolTipText(noPermission);

		} else if (open) {
			openButton.setToolTipText("Ca #" + shift.getShiftId() + " đang mở - đóng ca hiện tại trước khi mở ca mới");
			cashInButton.setToolTipText("Ghi nhận khoản tiền được thu thêm vào quỹ ca");
			cashOutButton.setToolTipText("Ghi nhận khoản tiền chi ra khỏi quỹ ca");
			closeButton.setToolTipText("Đóng ca và gửi đối soát cho quản lý duyệt");

		} else {
			String needOpen = "Mở ca trước khi thực hiện thao tác này";

			openButton.setToolTipText("Mở ca mới để bắt đầu bán hàng");
			cashInButton.setToolTipText(needOpen);
			cashOutButton.setToolTipText(needOpen);
			closeButton.setToolTipText(needOpen);
		}
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

	    historyCountLabel.setText("Tổng cộng: " + totalItems + " ca");


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

	    historyTable.clear();

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
	                        ? null
	                        : shift.getExpectedCash();


	        historyTable.addRow(
	                new Object[] {

	                    "#" + shift.getShiftId(),

	                    shift.getUserName(),

	                    dateTime(
	                            shift.getStartTime()
	                    ),

	                    dateTime(
	                            shift.getEndTime()
	                    ),

	                    shift.getStatusLabel(),

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
	                historyTable.getTable()
	                        .convertRowIndexToView(
	                                selectedModelRow
	                        );


	        if (selectedViewRow >= 0) {

	            historyTable.getTable()
	                    .setRowSelectionInterval(
	                            selectedViewRow,
	                            selectedViewRow
	                    );

	        } else if (
	                historyTable.getTable().getRowCount() > 0
	        ) {

	            historyTable.getTable()
	                    .setRowSelectionInterval(
	                            0,
	                            0
	                    );
	        }

	    } else {

	        historyTable.getTable().clearSelection();

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

	/**
	 * Nut "Xem chi tiết" tren cot Thao tac cua bang Lich su ca. modelRow ung
	 * voi vi tri trong pagedShifts (danh sach dung de addRow theo dung thu tu,
	 * xem renderHistory()) - KHONG dung view row vi ActionColumn da tu quy doi
	 * ve model row truoc khi goi callback nay.
	 */
	private void viewShiftDetail(int modelRow) {
		if (modelRow < 0 || modelRow >= pagedShifts.size()) return;
		Shift shift = pagedShifts.get(modelRow);
		ShiftDetailDialog.show(SwingUtilities.getWindowAncestor(this), shift, shiftService);
	}

	private boolean isRejectedAtModelRow(int modelRow) {
		return canOperate && modelRow >= 0 && modelRow < pagedShifts.size()
				&& pagedShifts.get(modelRow).isRejected();
	}

	private void resubmitShiftAtModelRow(int modelRow) {
		if (modelRow < 0 || modelRow >= pagedShifts.size()) return;
		Shift shift = pagedShifts.get(modelRow);
		if (!shift.isRejected()) {
			AppAlert.warning(this, "Chỉ có ca bị yêu cầu kiểm lại mới được gửi lại đối soát.");
			return;
		}
		showResubmitDialog(shift);
	}

	private void loadSelectedTransactions() {
		int viewRow = historyTable.getTable().getSelectedRow();

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

		int modelRow = historyTable.getTable().convertRowIndexToModel(viewRow);

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

	    transactionCountLabel.setText("Tổng cộng: " + totalItems + " giao dịch");


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

	    transactionTable.clear();


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


	            transactionTable.addRow(
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
	                        ? null
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

	                        shift.getStatusLabel(),

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
	 * Dialog mở ca — UI app, ô tiền format 1,200,000.
	 */
	private void showOpenDialog() {
		JTextField moneyField = moneyInputField(BigDecimal.ZERO);
		JTextArea noteArea = styledNoteArea("");

		boolean[] confirmed = {false};
		JDialog dialog = buildShiftDialog("Mở ca bán hàng");

		JPanel body = dialogBody();
		body.add(dialogHeader(FontAwesomeSolid.PLAY_CIRCLE, AppColor.ACCENT,
				"Mở ca bán hàng",
				"Nhập số tiền mặt đầu ca trước khi bán hàng."));
		body.add(Box.createVerticalStrut(16));
		body.add(fieldLabel("Tiền mặt đầu ca *"));
		body.add(Box.createVerticalStrut(6));
		body.add(moneyFieldWithSuffix(moneyField));
		body.add(Box.createVerticalStrut(4));
		body.add(hintLabel("Nhập số nguyên VND, tự format: 1200000 → 1,200,000"));
		body.add(Box.createVerticalStrut(14));
		body.add(fieldLabel("Ghi chú"));
		body.add(Box.createVerticalStrut(6));
		body.add(wrapNote(noteArea));

		JButton cancel = dialogSecondaryButton("Hủy");
		JButton ok = dialogPrimaryButton("Mở ca", AppColor.ACCENT, AppColor.ACCENT_HOVER);
		cancel.addActionListener(e -> dialog.dispose());
		ok.addActionListener(e -> {
			BigDecimal openingCash = CurrencyDocumentFilter.parse(moneyField.getText());
			if (openingCash == null) {
				AppAlert.warning(dialog, "Tiền đầu ca phải là số nguyên VND không âm.");
				return;
			}
			confirmed[0] = true;
			dialog.dispose();
			runOperation(() -> shiftService.openMyShift(openingCash, noteArea.getText()));
		});

		dialog.add(body, BorderLayout.CENTER);
		dialog.add(dialogFooter(cancel, ok), BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(ok);
		showShiftDialog(dialog);
	}

	/**
	 * Dialog thu / chi tiền trong ca.
	 */
	private void showCashMovementDialog(String type) {
		boolean cashIn = ShiftCashTransaction.CASH_IN.equals(type);
		JTextField moneyField = moneyInputField(null);
		JTextArea reasonArea = styledNoteArea("");

		JDialog dialog = buildShiftDialog(cashIn ? "Ghi nhận thu tiền" : "Ghi nhận chi tiền");

		JPanel body = dialogBody();
		Color accent = cashIn ? AppColor.SUCCESS : AppColor.WARNING;
		body.add(dialogHeader(
				cashIn ? FontAwesomeSolid.ARROW_DOWN : FontAwesomeSolid.ARROW_UP,
				accent,
				cashIn ? "Thu tiền vào quỹ ca" : "Chi tiền từ quỹ ca",
				cashIn
						? "Ghi nhận khoản tiền được bổ sung vào quỹ trong ca."
						: "Ghi nhận khoản tiền lấy ra khỏi quỹ trong ca."
		));
		body.add(Box.createVerticalStrut(16));
		body.add(fieldLabel("Số tiền *"));
		body.add(Box.createVerticalStrut(6));
		body.add(moneyFieldWithSuffix(moneyField));
		body.add(Box.createVerticalStrut(4));
		body.add(hintLabel("Nhập số nguyên VND > 0, ví dụ 50000 → 50,000"));
		body.add(Box.createVerticalStrut(14));
		body.add(fieldLabel(cashIn ? "Lý do thu *" : "Lý do chi *"));
		body.add(Box.createVerticalStrut(6));
		body.add(wrapNote(reasonArea));

		JButton cancel = dialogSecondaryButton("Hủy");
		JButton ok = dialogPrimaryButton(
				cashIn ? "Ghi nhận thu" : "Ghi nhận chi",
				accent,
				cashIn ? AppColor.SUCCESS : AppColor.WARNING
		);
		cancel.addActionListener(e -> dialog.dispose());
		ok.addActionListener(e -> {
			BigDecimal amount = CurrencyDocumentFilter.parse(moneyField.getText());
			if (amount == null || amount.signum() <= 0) {
				AppAlert.warning(dialog, "Số tiền phải là số nguyên VND lớn hơn 0.");
				return;
			}
			String reason = reasonArea.getText() != null ? reasonArea.getText().trim() : "";
			if (reason.isEmpty()) {
				AppAlert.warning(dialog, "Vui lòng nhập lý do.");
				return;
			}
			dialog.dispose();
			runOperation(() -> shiftService.addCashMovement(type, amount, reason));
		});

		dialog.add(body, BorderLayout.CENTER);
		dialog.add(dialogFooter(cancel, ok), BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(ok);
		showShiftDialog(dialog);
	}

	/**
	 * Tính tiền hệ thống trên background trước khi hiện dialog đóng ca.
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
					if (result.getData() == null) {
						AppAlert.error(ShiftManagementPanel.this, "Không thể tính số tiền đóng ca.");
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
	 * Dialog đóng ca — hiển thị công thức quỹ + nhập tiền đếm (format 1,200,000).
	 */
	private void showCloseDialog(ShiftCashSummary summary) {
		// P5 - blind cash count: KHONG hien ExpectedCash va KHONG prefill tien dem.
		JTextField countedField = moneyInputField(null);
		JTextArea noteArea = styledNoteArea("");

		JDialog dialog = buildShiftDialog("Đóng ca — kiểm đếm độc lập");
		JPanel body = dialogBody();
		body.add(dialogHeader(FontAwesomeSolid.LOCK, AppColor.ERROR,
				"Đóng ca bán hàng",
				"Hãy đếm tiền mặt thực tế trước. Số quỹ hệ thống được ẩn cho tới khi bạn xác nhận số đã đếm."));
		body.add(Box.createVerticalStrut(14));

		JPanel blindCard = new JPanel(new BorderLayout(10, 0));
		blindCard.setOpaque(true);
		blindCard.setBackground(AppColor.INFO_BG != null ? AppColor.INFO_BG : new Color(239, 246, 255));
		blindCard.setBorder(BorderFactory.createCompoundBorder(
				new LineBorder(AppColor.INFO, 1, true), new EmptyBorder(12, 14, 12, 14)));
		blindCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		blindCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
		JLabel blindText = new JLabel("<html><b>Kiểm đếm độc lập</b><br>"
				+ "Tiền hệ thống sẽ chỉ được hiện sau khi bạn nhập và xác nhận tiền thực tế.</html>");
		blindText.setFont(AppFont.BODY);
		blindText.setForeground(AppColor.TEXT_PRIMARY);
		blindCard.add(blindText, BorderLayout.CENTER);
		body.add(blindCard);
		body.add(Box.createVerticalStrut(14));
		body.add(fieldLabel("Tiền mặt đếm thực tế *"));
		body.add(Box.createVerticalStrut(6));
		body.add(moneyFieldWithSuffix(countedField));
		body.add(Box.createVerticalStrut(4));
		body.add(hintLabel("Không nhìn số hệ thống trước khi đếm để giảm sai lệch chủ quan."));
		body.add(Box.createVerticalStrut(14));
		body.add(fieldLabel("Giải trình (bắt buộc nếu có chênh lệch)"));
		body.add(Box.createVerticalStrut(6));
		body.add(wrapNote(noteArea));

		JButton cancel = dialogSecondaryButton("Hủy");
		JButton ok = dialogPrimaryButton("Đối chiếu & đóng ca", AppColor.ERROR, AppColor.ERROR_HOVER);
		cancel.addActionListener(e -> dialog.dispose());
		ok.addActionListener(e -> {
			BigDecimal countedCash = CurrencyDocumentFilter.parse(countedField.getText());
			if (countedCash == null) {
				AppAlert.warning(dialog, "Tiền đếm thực tế phải là số nguyên VND không âm.");
				return;
			}
			BigDecimal difference = summary.differenceFrom(countedCash);
			if (difference.signum() != 0 && (noteArea.getText() == null || noteArea.getText().isBlank())) {
				AppAlert.warning(dialog, "Sau khi đối chiếu phát hiện chênh lệch "
						+ signedMoneyOrDash(difference) + ". Vui lòng nhập giải trình rồi xác nhận lại.");
				return;
			}

			String confirm = "Bạn đã xác nhận số tiền đếm thực tế.\n\n"
					+ "Tiền hệ thống: " + money(summary.getExpectedCash()) + "\n"
					+ "Tiền thực tế:  " + money(countedCash) + "\n"
					+ "Chênh lệch:     " + signedMoneyOrDash(difference) + "\n\n"
					+ "Đóng ca và gửi kết quả này cho quản lý?";
			if (!BaseDialog.confirm(dialog, "Xác nhận đối soát", confirm)) return;
			dialog.dispose();
			runOperation(() -> shiftService.closeMyShift(countedCash, noteArea.getText()));
		});

		dialog.add(body, BorderLayout.CENTER);
		dialog.add(dialogFooter(cancel, ok), BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(ok);
		showShiftDialog(dialog);
	}

	/** P4: nhan vien kiem dem lai ca bi quan ly tu choi, tao revision moi. */
	private void showResubmitDialog(Shift shift) {
		JTextField countedField = moneyInputField(null);
		JTextArea noteArea = styledNoteArea("");
		JDialog dialog = buildShiftDialog("Kiểm đếm lại ca #" + shift.getShiftId());
		JPanel body = dialogBody();
		body.add(dialogHeader(FontAwesomeSolid.REDO, AppColor.WARNING,
				"Kiểm đếm lại & gửi đối soát",
				"Ca vẫn đã đóng. Hệ thống chỉ tạo một lần đối soát mới, không sửa/xóa lịch sử cũ."));
		body.add(Box.createVerticalStrut(12));
		if (shift.getApprovalNote() != null && !shift.getApprovalNote().isBlank()) {
			body.add(hintLabel("Yêu cầu của quản lý: " + shift.getApprovalNote()));
			body.add(Box.createVerticalStrut(12));
		}
		body.add(fieldLabel("Tiền mặt đếm lại *"));
		body.add(Box.createVerticalStrut(6));
		body.add(moneyFieldWithSuffix(countedField));
		body.add(Box.createVerticalStrut(12));
		body.add(fieldLabel("Giải trình / kết quả kiểm lại"));
		body.add(Box.createVerticalStrut(6));
		body.add(wrapNote(noteArea));

		JButton cancel = dialogSecondaryButton("Hủy");
		JButton ok = dialogPrimaryButton("Đối chiếu & gửi lại", AppColor.WARNING, AppColor.WARNING);
		cancel.addActionListener(e -> dialog.dispose());
		ok.addActionListener(e -> {
			BigDecimal counted = CurrencyDocumentFilter.parse(countedField.getText());
			if (counted == null) {
				AppAlert.warning(dialog, "Tiền đếm lại phải là số nguyên VND không âm.");
				return;
			}
			BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : BigDecimal.ZERO;
			BigDecimal difference = counted.subtract(expected);
			if (difference.signum() != 0 && (noteArea.getText() == null || noteArea.getText().isBlank())) {
				AppAlert.warning(dialog, "Vẫn còn chênh lệch " + signedMoneyOrDash(difference)
						+ ". Hãy nhập giải trình trước khi gửi lại.");
				return;
			}
			String message = "Tiền hệ thống: " + money(expected) + "\n"
					+ "Tiền đếm lại:  " + money(counted) + "\n"
					+ "Chênh lệch:     " + signedMoneyOrDash(difference) + "\n\n"
					+ "Gửi revision đối soát mới cho quản lý?";
			if (!BaseDialog.confirm(dialog, "Gửi lại đối soát", message)) return;
			dialog.dispose();
			runOperation(() -> shiftService.resubmitMyReconciliation(
					shift.getShiftId(), counted, noteArea.getText()));
		});
		dialog.add(body, BorderLayout.CENTER);
		dialog.add(dialogFooter(cancel, ok), BorderLayout.SOUTH);
		dialog.getRootPane().setDefaultButton(ok);
		showShiftDialog(dialog);
	}

	// ── Helpers dialog ca bán hàng ──────────────────────────────────────

	private JDialog buildShiftDialog(String title) {
		Window owner = SwingUtilities.getWindowAncestor(this);
		JDialog dialog = new JDialog(owner, title, Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		dialog.setLayout(new BorderLayout());
		dialog.getContentPane().setBackground(AppColor.WHITE);
		return dialog;
	}

	private void showShiftDialog(JDialog dialog) {
		dialog.pack();
		dialog.setMinimumSize(new Dimension(440, dialog.getHeight()));
		dialog.setLocationRelativeTo(this);
		dialog.setVisible(true);
	}

	private static JPanel dialogBody() {
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(AppColor.WHITE);
		body.setBorder(new EmptyBorder(22, 24, 8, 24));
		return body;
	}

	private static JPanel dialogHeader(FontAwesomeSolid icon, Color iconColor,
	                                   String title, String subtitle) {
		JPanel header = new JPanel();
		header.setOpaque(false);
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		row.setOpaque(false);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		FontIcon fi = FontIcon.of(icon, 24);
		fi.setIconColor(iconColor);
		row.add(new JLabel(fi));
		JLabel t = new JLabel(title);
		t.setFont(AppFont.DIALOG_TITLE);
		t.setForeground(AppColor.TEXT_PRIMARY);
		row.add(t);
		header.add(row);

		if (subtitle != null && !subtitle.isBlank()) {
			header.add(Box.createVerticalStrut(8));
			JLabel s = new JLabel("<html><div style='width:380px'>" + subtitle + "</div></html>");
			s.setFont(AppFont.BODY);
			s.setForeground(AppColor.TEXT_MUTED);
			s.setAlignmentX(Component.LEFT_ALIGNMENT);
			header.add(s);
		}
		return header;
	}

	private static JPanel dialogFooter(JButton... buttons) {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
		footer.setBackground(AppColor.WHITE);
		footer.setBorder(new EmptyBorder(8, 16, 16, 16));
		for (JButton b : buttons) footer.add(b);
		return footer;
	}

	private static JLabel fieldLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(AppFont.BODY_BOLD);
		l.setForeground(AppColor.TEXT_PRIMARY);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JLabel hintLabel(String text) {
		JLabel l = new JLabel(text);
		l.setFont(AppFont.SMALL);
		l.setForeground(AppColor.TEXT_MUTED);
		l.setAlignmentX(Component.LEFT_ALIGNMENT);
		return l;
	}

	private static JTextField moneyInputField(BigDecimal initial) {
		JTextField field = new JTextField(18);
		field.setFont(AppFont.FIELD);
		field.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.FIELD_BORDER != null ? AppColor.FIELD_BORDER : AppColor.BORDER, 1, true),
				new EmptyBorder(8, 12, 8, 12)
		));
		CurrencyDocumentFilter.install(field);
		if (initial != null) {
			field.setText(CurrencyDocumentFilter.format(initial));
		}
		field.setAlignmentX(Component.LEFT_ALIGNMENT);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		return field;
	}

	private static JPanel moneyFieldWithSuffix(JTextField field) {
		JPanel wrap = new JPanel(new BorderLayout(8, 0));
		wrap.setOpaque(false);
		wrap.setAlignmentX(Component.LEFT_ALIGNMENT);
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
		wrap.add(field, BorderLayout.CENTER);
		JLabel suffix = new JLabel("VNĐ");
		suffix.setFont(AppFont.BODY_BOLD);
		suffix.setForeground(AppColor.TEXT_MUTED);
		wrap.add(suffix, BorderLayout.EAST);
		return wrap;
	}

	private static JTextArea styledNoteArea(String initial) {
		JTextArea area = new JTextArea(initial != null ? initial : "", 3, 24);
		area.setFont(AppFont.FIELD);
		area.setLineWrap(true);
		area.setWrapStyleWord(true);
		area.setBorder(new EmptyBorder(8, 10, 8, 10));
		return area;
	}

	private static JScrollPane wrapNote(JTextArea area) {
		JScrollPane sp = new JScrollPane(area);
		sp.setAlignmentX(Component.LEFT_ALIGNMENT);
		sp.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
		sp.setBorder(BorderFactory.createLineBorder(
				AppColor.FIELD_BORDER != null ? AppColor.FIELD_BORDER : AppColor.BORDER, 1, true));
		return sp;
	}

	private static JButton dialogPrimaryButton(String text, Color bg, Color hover) {
		JButton button = new JButton(text);
		button.setFont(AppFont.BODY_BOLD);
		button.setForeground(Color.WHITE);
		button.setBackground(bg != null ? bg : AppColor.ACCENT);
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(10, 20, 10, 20));
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));
		button.setOpaque(true);
		Color base = button.getBackground();
		Color hv = hover != null ? hover : base;
		button.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override public void mouseEntered(java.awt.event.MouseEvent e) {
				button.setBackground(hv);
			}
			@Override public void mouseExited(java.awt.event.MouseEvent e) {
				button.setBackground(base);
			}
		});
		return button;
	}

	private static JButton dialogSecondaryButton(String text) {
		JButton button = new JButton(text);
		button.setFont(AppFont.BODY_BOLD);
		button.setForeground(AppColor.TEXT_PRIMARY);
		button.setBackground(AppColor.CANCEL_BG != null ? AppColor.CANCEL_BG : new Color(241, 245, 249));
		button.setFocusPainted(false);
		button.setBorder(new EmptyBorder(10, 18, 10, 18));
		button.setCursor(new Cursor(Cursor.HAND_CURSOR));
		button.setOpaque(true);
		return button;
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

			/*
			 * Trong lúc chờ kết quả từ server, cho người dùng biết rõ hệ
			 * thống đang xử lý thay vì chỉ thấy nút mờ đi không rõ lý do.
			 */
			String processing = "Đang xử lý, vui lòng đợi...";

			openButton.setToolTipText(processing);
			cashInButton.setToolTipText(processing);
			cashOutButton.setToolTipText(processing);
			closeButton.setToolTipText(processing);

		} else {
			renderSummary(currentOpenShift);
		}
	}

	/**
	 * Bảng lịch sử ca dùng BaseTable dùng chung (border bo góc + đổ bóng,
	 * header có mũi tên sort, sọc dòng...) để đồng bộ hình thức với các
	 * bảng khác trong ứng dụng (AuditLogPanel, InvoicePanel...) thay vì
	 * JTable tự vẽ style riêng như trước.
	 */
	private static BaseTable buildHistoryTable() {
		BaseTable table = new BaseTable(new String[] {
				"Mã ca", "Nhân viên", "Bắt đầu", "Kết thúc",
				"Trạng thái", "Hóa đơn", "Tiền hệ thống", "Tiền thực tế", "Chênh lệch"
		});

		table.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		table.enableSorting();

		/*
		 * Cột "Trạng thái" -> badge pill (Đang mở = xanh lá, Đã đóng = xám).
		 */
		table.setBadgeColumn(
				4,
				value -> String.valueOf(value),
				value -> {
					if ("Đang mở".equals(value)) return AppColor.SUCCESS;
					if ("Chờ duyệt".equals(value)) return AppColor.WARNING;
					if ("Cần kiểm lại".equals(value)) return AppColor.ERROR;
					return AppColor.TEXT_MUTED;
				});

		/*
		 * Cột "Chênh lệch" -> tô màu theo dấu (dương = xanh lá, âm = đỏ, "—" =
		 * xám) thay vì chữ đen đồng nhất, giúp nhận diện nhanh ca lệch quỹ.
		 */
		table.setCustomColumn(8, signedMoneyRenderer(table.rowColorProvider()));

		return table;
	}

	/** Bảng Thu/Chi của ca đang chọn, cùng chuẩn BaseTable như bảng lịch sử ca. */
	private static BaseTable buildTransactionTable() {
		BaseTable table = new BaseTable(new String[] {
				"Mã giao dịch", "Loại", "Số tiền", "Lý do", "Người tạo", "Thời gian"
		});

		table.getTable().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		table.enableSorting();

		/*
		 * Cột "Loại" -> badge pill (Thu tiền = xanh lá, Chi tiền = cam).
		 */
		table.setBadgeColumn(
				1,
				value -> String.valueOf(value),
				value -> "Thu tiền".equals(value) ? AppColor.SUCCESS : AppColor.WARNING);

		return table;
	}

	/**
	 * Renderer dùng chung cho cột tiền có dấu (+/-), tô màu theo dấu và giữ
	 * đúng sọc dòng của BaseTable đang truyền vào (rowColorProvider).
	 */
	private static TableCellRenderer signedMoneyRenderer(RowColorProvider rowColorProvider) {
		return new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				JLabel c = (JLabel) super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
				c.setBackground(rowColorProvider.colorFor(row, isSelected));
				c.setBorder(new EmptyBorder(0, 12, 0, 12));
				String text = value != null ? value.toString() : "";
				if (text.startsWith("+")) {
					c.setForeground(AppColor.SUCCESS);
				} else if (text.startsWith("-")) {
					c.setForeground(AppColor.ERROR);
				} else {
					c.setForeground(AppColor.TEXT_MUTED);
				}
				c.setFont(AppFont.BODY_BOLD);
				return c;
			}
		};
	}

	private static JButton actionButton(String text, Color color, FontAwesomeSolid iconCode) {

		FontIcon icon = FontIcon.of(iconCode, 15);

		icon.setIconColor(Color.WHITE);

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
				 * Vẽ icon + chữ căn giữa nút - icon giúp nhận diện nhanh
				 * hành động (mở/khóa/mũi tên thu-chi) thay vì chỉ có chữ.
				 */
				Color foreground = isEnabled()
						? Color.WHITE
						: blendColor(Color.WHITE, background, 0.28);

				icon.setIconColor(foreground);

				String label = getText();

				FontMetrics metrics = g2.getFontMetrics(getFont());

				int gap = 8;

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

	/**
	 * Nút phụ (vd "Làm mới") - viền mảnh, nền trắng, không tô đặc màu, để
	 * không cạnh tranh thị giác với các nút nghiệp vụ chính (mở/thu/chi/đóng
	 * ca). Cùng bo góc + kích thước với actionButton để thẳng hàng trên
	 * cùng 1 dòng.
	 */
	private static JButton outlineButton(String text, FontAwesomeSolid iconCode) {

		FontIcon icon = FontIcon.of(iconCode, 14);

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

				int gap = 8;

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

		button.setFont(AppFont.BUTTON);

		button.setForeground(AppColor.TEXT_SECONDARY);

		button.setFocusPainted(false);

		button.setBorderPainted(false);

		button.setContentAreaFilled(false);

		button.setOpaque(false);

		button.setRolloverEnabled(true);

		button.setBorder(new EmptyBorder(9, 16, 9, 16));

		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return button;
	}

	private static JToggleButton tabButton(String text, FontAwesomeSolid icon) {

		FontIcon fontIcon = FontIcon.of(icon, 14);

		JToggleButton button = new JToggleButton(text, fontIcon) {

			@Override
			protected void paintComponent(Graphics graphics) {

				Graphics2D g2 = (Graphics2D) graphics.create();

				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

				g2.setColor(getBackground());

				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);

				g2.dispose();

				super.paintComponent(graphics);
			}
		};

		button.setFont(AppFont.BUTTON);

		button.setIconTextGap(8);

		button.setFocusPainted(false);

		button.setContentAreaFilled(false);

		button.setOpaque(false);

		button.setBorderPainted(false);

		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		button.setBorder(new EmptyBorder(8, 18, 8, 18));

		return button;
	}

	private void updateTabStyles() {

		styleTab(historyTabButton);

		styleTab(transactionTabButton);
	}

	private void styleTab(JToggleButton button) {

		FontIcon icon = (FontIcon) button.getIcon();

		if (button.isSelected()) {

			button.setBackground(AppColor.ACCENT);

			button.setForeground(Color.WHITE);

			if (icon != null) icon.setIconColor(Color.WHITE);

		} else {

			button.setBackground(AppColor.WHITE);

			button.setForeground(AppColor.TEXT_SECONDARY);

			if (icon != null) icon.setIconColor(AppColor.TEXT_SECONDARY);
		}

		button.repaint();
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

	private void bindFilters() {

		/*
		 * ============================= LỊCH SỬ CA =============================
		 */

		historySearchField.onSearch(keyword -> applyHistoryFilter());

		historyStatusFilter.onChange(value -> applyHistoryFilter());

		/*
		 * ============================= THU / CHI =============================
		 */

		transactionSearchField.onSearch(keyword -> applyTransactionFilter());

		transactionTypeFilter.onChange(value -> applyTransactionFilter());

		bindDateRangeFilter(historyDateFrom, historyDateTo,
				() -> adjustingHistoryDateFilter, v -> adjustingHistoryDateFilter = v,
				this::applyHistoryFilter);

		bindDateRangeFilter(transactionDateFrom, transactionDateTo,
				() -> adjustingTransactionDateFilter, v -> adjustingTransactionDateFilter = v,
				this::applyTransactionFilter);
	}

	/**
	 * Gắn sự kiện cho cặp DatePickerField "Từ ngày - Đến ngày" của 1 tab:
	 * đổi ngày thì lọc lại; nếu "đến" nhỏ hơn "từ" thì cảnh báo và tự xóa
	 * "đến" thay vì lọc ra kết quả rỗng gây khó hiểu. Giống hệt cách
	 * AuditLogPanel xử lý khoảng ngày để 2 trang nhất quán với nhau.
	 */
	private void bindDateRangeFilter(DatePickerField fromField, DatePickerField toField,
			java.util.function.BooleanSupplier isAdjusting, java.util.function.Consumer<Boolean> setAdjusting,
			Runnable applyFilter) {

		fromField.onChange(from -> {
			if (isAdjusting.getAsBoolean()) return;

			LocalDate to = toField.getValue();
			if (from != null && to != null && to.isBefore(from)) {
				AppAlert.warning(this, "Khoảng ngày không hợp lệ",
						"Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
				setAdjusting.accept(true);
				try {
					toField.setValue(null);
				} finally {
					setAdjusting.accept(false);
				}
			}
			applyFilter.run();
		});

		toField.onChange(to -> {
			if (isAdjusting.getAsBoolean()) return;

			LocalDate from = fromField.getValue();
			if (from != null && to != null && to.isBefore(from)) {
				AppAlert.warning(this, "Khoảng ngày không hợp lệ",
						"Ngày \"đến\" phải lớn hơn hoặc bằng ngày \"từ\".");
				setAdjusting.accept(true);
				try {
					toField.setValue(null);
				} finally {
					setAdjusting.accept(false);
				}
				return;
			}
			applyFilter.run();
		});
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

		String selectedStatus = String.valueOf(historyStatusFilter.getSelected());

		List<Shift> result = new ArrayList<>();

		for (Shift shift : visibleShifts) {

			String statusLabel = shift.getStatusLabel();

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
			 * Khoảng ngày (Từ ngày - Đến ngày).
			 *
			 * Lịch sử ca lấy ngày Bắt đầu ca.
			 */
			boolean dateMatches = matchesDateRange(shift.getStartTime(),
					historyDateFrom.getValue(), historyDateTo.getValue());

			if (textMatches && statusMatches && dateMatches) {

				result.add(shift);
			}
		}

		return result;
	}

	private List<ShiftCashTransaction> getFilteredTransactions() {

		String query = lower(transactionSearchField.getText());

		String selectedType = String.valueOf(transactionTypeFilter.getSelected());

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
			 * Lọc theo khoảng ngày tạo giao dịch (Từ ngày - Đến ngày).
			 */
			boolean dateMatches = matchesDateRange(transaction.getCreatedAt(),
					transactionDateFrom.getValue(), transactionDateTo.getValue());

			if (textMatches && typeMatches && dateMatches) {

				result.add(transaction);
			}
		}

		return result;
	}
	
	/**
	 * Khớp thời điểm với khoảng "Từ ngày - Đến ngày" (2 đầu đều tùy chọn,
	 * để trống nghĩa là không giới hạn phía đó). Thay cho lọc rời rạc theo
	 * ngày/tháng/năm trước đây - cho phép chọn khoảng bất kỳ, không chỉ 1
	 * ngày/tháng/năm cụ thể.
	 */
	private static boolean matchesDateRange(
	        LocalDateTime dateTime,
	        LocalDate from,
	        LocalDate to
	) {

	    if (dateTime == null) {
	        return false;
	    }

	    LocalDate date = dateTime.toLocalDate();

	    return (from == null || !date.isBefore(from))
	            && (to == null || !date.isAfter(to));
	}

	private static String lower(
	        String value
	) {

	    return value == null
	            ? ""
	            : value.toLowerCase();
	}

	private static JTextField inputField(String value) {
		JTextField field = new JTextField(24);
		field.setFont(AppFont.FIELD);
		CurrencyDocumentFilter.install(field);
		if (value != null && !value.isBlank()) {
			BigDecimal parsed = CurrencyDocumentFilter.parse(value.replace(",", ""));
			if (parsed != null) {
				field.setText(CurrencyDocumentFilter.format(parsed));
			} else {
				field.setText(value);
			}
		}
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
		return CurrencyDocumentFilter.parse(raw);
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