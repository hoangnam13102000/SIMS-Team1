package com.view.admin.shift;

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

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.math.BigDecimal;
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

	private final DefaultTableModel transactionModel = readOnlyModel("Mã giao dịch", "Loại", "Số tiền", "Lý do",
			"Người tạo", "Thời gian");

	private final JTable transactionTable = buildTable(transactionModel);

	private final JLabel transactionTitle = new JLabel("Thu/chi của ca");

	private List<Shift> visibleShifts = new ArrayList<>();

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

		SectionHeader header = new SectionHeader(FontAwesomeSolid.CLOCK, AppColor.ACCENT, "Ca bán hàng & đối soát quỹ",
				subtitle);

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
		
		cards.setAlignmentX(
		        Component.LEFT_ALIGNMENT
		);

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
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.SM, 0));

		row.setOpaque(false);
		
		row.setAlignmentX(
		        Component.LEFT_ALIGNMENT
		);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

		/*
		 * Quan ly chi co SHIFT_VIEW_ALL khong thay cac nut thao tac.
		 */
		if (canOperate) {
			row.add(openButton);
			row.add(cashInButton);
			row.add(cashOutButton);
			row.add(closeButton);
		}

		row.add(refreshButton);

		return row;
	}

	private JComponent buildTables() {
		JPanel historyCard = tableCard("Lịch sử ca gần nhất", historyTable);

		transactionTitle.setFont(AppFont.HEADING_MD);

		transactionTitle.setForeground(AppColor.TEXT_PRIMARY);

		JPanel transactionCard = tableCard(transactionTitle, transactionTable);

		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, historyCard, transactionCard);

		split.setResizeWeight(0.58);
		split.setDividerSize(8);
		split.setBorder(null);
		split.setOpaque(false);

		split.setAlignmentX(Component.LEFT_ALIGNMENT);
		
		split.setPreferredSize(
		        new Dimension(
		                900,
		                430
		        )
		);

		split.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

		return split;
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

				List<Shift> history = shiftService.getVisibleHistory(50);

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

		historyModel.setRowCount(0);

		int openRow = -1;

		for (int index = 0; index < visibleShifts.size(); index++) {
			Shift shift = visibleShifts.get(index);

			if (currentOpenShift != null && shift.getShiftId() == currentOpenShift.getShiftId()) {
				openRow = index;
			}

			BigDecimal expected;

			if (shift.isOpen()) {
				expected = expectedOf(shift);
			} else {
				expected = shift.getExpectedCash();
			}

			historyModel.addRow(new Object[] { "#" + shift.getShiftId(),

					shift.getUserName(),

					dateTime(shift.getStartTime()),

					dateTime(shift.getEndTime()),

					shift.isOpen() ? "Đang mở" : "Đã đóng",

					shift.getInvoiceCount(),

					money(expected),

					moneyOrDash(shift.getCountedCash()),

					signedMoneyOrDash(shift.getCashDifference()) });
		}

		if (!visibleShifts.isEmpty()) {
			int selectedRow = openRow >= 0 ? openRow : 0;

			historyTable.setRowSelectionInterval(selectedRow, selectedRow);

		} else {
			transactionModel.setRowCount(0);

			transactionTitle.setText("Thu/chi của ca");
		}

		updatingHistory = false;

		loadSelectedTransactions();
	}

	private void loadSelectedTransactions() {
		int viewRow = historyTable.getSelectedRow();

		if (viewRow < 0) {
			return;
		}

		int modelRow = historyTable.convertRowIndexToModel(viewRow);

		if (modelRow < 0 || modelRow >= visibleShifts.size()) {
			return;
		}

		Shift selectedShift = visibleShifts.get(modelRow);

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

	private void renderTransactions(List<ShiftCashTransaction> transactions) {
		transactionModel.setRowCount(0);

		for (ShiftCashTransaction transaction : transactions) {
			String typeLabel = transaction.isCashIn() ? "Thu tiền" : "Chi tiền";

			transactionModel.addRow(new Object[] { transaction.getTransactionCode(),

					typeLabel,

					money(transaction.getAmount()),

					transaction.getReason(),

					transaction.getCreatedByName(),

					dateTime(transaction.getCreatedAt()) });
		}
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
		JButton button = new JButton(text);

		button.setFont(AppFont.BUTTON);

		button.setForeground(Color.WHITE);

		button.setBackground(color);

		button.setFocusPainted(false);

		button.setBorder(new EmptyBorder(9, 16, 9, 16));

		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		return button;
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
