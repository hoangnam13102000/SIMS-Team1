package com.view.admin.invoice;

import com.components.AppAlert;
import com.components.DatePickerField;
import com.components.barcode.BarcodeScannerDialog;
import com.components.crud.BaseCrudPanel;
import com.components.table.ActionColumn;
import com.dao.InvoiceDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.theme.AppColor;
import com.utils.FileUtil;
import com.utils.NumberUtil;
import com.utils.InvoiceQrUtil;
import com.utils.PaginationHelper;
import com.utils.pdf.InvoicePdfExporter;
import com.service.InvoiceService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class InvoicePanel extends BaseCrudPanel<Invoice> {
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private final InvoiceDAO invoiceDAO = new InvoiceDAO();
	private final InvoiceService invoiceService = new InvoiceService();

	private DatePickerField fromDateFilter;
	private DatePickerField toDateFilter;
	private JLabel clearDateFilterLink;
	private JButton qrScanButton;
	private boolean compactToolbarMode;

	public InvoicePanel() {
		super();
		table.setBadgeColumn(6, this::statusLabel, this::statusColor);
		table.setBadgeColumn(8, this::returnStateLabel, this::returnStateColor);
		table.setBadgeColumn(9, this::cancelRequestLabel, this::cancelRequestColor);

		table.getTable().getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				JLabel c = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
						column);
				String text = value != null ? value.toString() : "";
				c.setText(text);
				c.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
				c.setHorizontalAlignment(SwingConstants.LEFT);
				c.setBackground(isSelected ? AppColor.ACCENT_SELECTION_BG
						: (row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD));
				if (text != null && !text.isBlank()) {
					FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 11);
					copyIcon.setIconColor(AppColor.ACCENT);
					c.setIcon(copyIcon);
					c.setIconTextGap(6);
					c.setHorizontalTextPosition(SwingConstants.LEFT);
					c.setToolTipText("Click để copy mã hóa đơn: " + text);
				} else {
					c.setIcon(null);
					c.setToolTipText(null);
				}
				return c;
			}
		});

		table.getTable().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int viewCol = table.getTable().columnAtPoint(e.getPoint());
				int viewRow = table.getTable().rowAtPoint(e.getPoint());
				if (viewCol == 0 && viewRow >= 0) {
					int modelRow = table.getTable().convertRowIndexToModel(viewRow);
					Object value = table.getTable().getModel().getValueAt(modelRow, 0);
					String text = value != null ? value.toString() : "";
					if (text != null && !text.isBlank()) {
						copyToClipboard(text);
						AppAlert.success(InvoicePanel.this, "Copy thành công", "Đã copy mã hóa đơn: " + text);
					}
				}
			}
		});

		buildDateFilterBar();
		addInvoiceQrScannerButton();
		table.setActionColumn(new ActionColumn()
				.add("view", FontAwesomeSolid.EYE, AppColor.TABLE_VIEW_ACTION, "Xem chi tiết", modelRow -> {
					if (supportsView())
						viewRow(modelRow);
				}).add("export", FontAwesomeSolid.FILE_PDF, AppColor.ACCENT, "Xuất hóa đơn PDF", this::exportRowPdf));
		applyColumnWidths(false);
		installResponsiveInvoiceLayout();
		initialLoad();
	}

	private void addInvoiceQrScannerButton() {
		qrScanButton = new JButton("Quét QR HĐ");
		qrScanButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
		qrScanButton.setFocusPainted(false);
		qrScanButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
		qrScanButton.setToolTipText("Quét QR để tìm nhanh hóa đơn");
		FontIcon icon = FontIcon.of(FontAwesomeSolid.CAMERA, 13);
		icon.setIconColor(AppColor.ACCENT);
		qrScanButton.setIcon(icon);
		qrScanButton.setIconTextGap(6);
		qrScanButton.addActionListener(e -> {
			Window owner = SwingUtilities.getWindowAncestor(this);
			BarcodeScannerDialog dialog = new BarcodeScannerDialog(owner,
					"Quét QR hóa đơn", "Đưa QR trên hóa đơn vào giữa khung hình");
			dialog.onScanned(raw -> {
				String invoiceCode = InvoiceQrUtil.extractInvoiceCode(raw);
				if (invoiceCode == null) {
					AppAlert.warning(this, "QR không hợp lệ",
							"Mã vừa quét không phải QR hóa đơn SIMS.");
					return;
				}
				if (searchBar != null) searchBar.setText(invoiceCode);
				applyFilters();
			});
			dialog.setVisible(true);
		});
		addToolbarFilter(qrScanButton);
	}

	private void buildDateFilterBar() {
		fromDateFilter = new DatePickerField(null, true);
		toDateFilter = new DatePickerField(null, true);
		JLabel fromLabel = new JLabel("Từ ngày");
		fromLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		fromLabel.setForeground(AppColor.TEXT_MUTED);
		JLabel toLabel = new JLabel("Đến ngày");
		toLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		toLabel.setForeground(AppColor.TEXT_MUTED);
		JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
		dateRow.setOpaque(false);
		dateRow.add(fromLabel);
		dateRow.add(fromDateFilter);
		dateRow.add(toLabel);
		dateRow.add(toDateFilter);
		fromDateFilter.onChange(d -> onDateFilterChanged());
		toDateFilter.onChange(d -> onDateFilterChanged());
		addToolbarFilter(dateRow);

		FontIcon clearIcon = FontIcon.of(FontAwesomeSolid.TIMES, 12);
		clearIcon.setIconColor(AppColor.TEXT_MUTED);
		clearDateFilterLink = new JLabel("Xóa lọc ngày", clearIcon, SwingConstants.LEFT);
		clearDateFilterLink.setIconTextGap(6);
		clearDateFilterLink.setFont(new Font("Segoe UI", Font.BOLD, 13));
		clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
		clearDateFilterLink.setCursor(new Cursor(Cursor.HAND_CURSOR));
		clearDateFilterLink.setVisible(false);
		clearDateFilterLink.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				fromDateFilter.setValue(null);
				toDateFilter.setValue(null);
				onDateFilterChanged();
			}

			@Override
			public void mouseEntered(MouseEvent e) {
				clearDateFilterLink.setForeground(AppColor.ERROR);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				clearDateFilterLink.setForeground(AppColor.TEXT_MUTED);
			}
		});
		addToolbarFilter(clearDateFilterLink);
	}

	// ================================================================
	// ====== CHỈ SỬA HÀM NÀY: Thêm validate Từ ngày > Đến ngày ======
	// ================================================================
	private void onDateFilterChanged() {
		LocalDate from = fromDateFilter.getValue();
		LocalDate to = toDateFilter.getValue();
		if (from != null && to != null && from.isAfter(to)) {
			AppAlert.warning(this, "Khoảng ngày không hợp lệ",
					"\"Từ ngày\" (" + from + ") không được sau \"Đến ngày\" (" + to + ").");
			return;
		}

		if (clearDateFilterLink != null) {
			clearDateFilterLink.setVisible(fromDateFilter.getValue() != null || toDateFilter.getValue() != null);
		}
		applyFilters();
	}
	// ================================================================
	// ====================== HẾT PHẦN SỬA ===========================
	// ================================================================

	private LocalDate selectedFromDate() {
		return fromDateFilter == null ? null : fromDateFilter.getValue();
	}

	private LocalDate selectedToDate() {
		return toDateFilter == null ? null : toDateFilter.getValue();
	}

	private void applyColumnWidths(boolean compact) {
		if (compact) {
			// Khi cửa sổ thu nhỏ, ưu tiên giữ cột "Thao tác" luôn nhìn thấy.
			// Các cột text vẫn có tooltip/ellipsis nên có thể co hơn mà không mất dữ liệu.
			table.setColumnWidths(145, 105, 95, 90, 90, 88, 85, 88, 90, 95);
			table.setColumnMinWidths(125, 75, 70, 78, 72, 72, 75, 72, 80, 82);
		} else {
			table.setColumnWidths(160, 135, 115, 100, 105, 105, 95, 105, 110, 120);
			table.setColumnMinWidths(140, 90, 82, 86, 84, 84, 82, 84, 90, 96);
		}

		int count = table.getTable().getColumnModel().getColumnCount();
		if (count > 0) {
			var first = table.getTable().getColumnModel().getColumn(0);
			first.setMinWidth(compact ? 125 : 140);
			first.setPreferredWidth(compact ? 145 : 160);
		}

		// BaseTable mặc định dành khá nhiều chỗ cho 2 icon action. Với trang hóa đơn
		// chỉ có Xem + PDF, 92px là đủ cả header và vùng click, giúp cột không bị
		// đẩy ra ngoài khi cửa sổ nhỏ.
		if (count > getColumnNames().length) {
			var action = table.getTable().getColumnModel().getColumn(count - 1);
			action.setMinWidth(88);
			action.setPreferredWidth(92);
			action.setMaxWidth(100);
		}
	}

	private void installResponsiveInvoiceLayout() {
		addComponentListener(new java.awt.event.ComponentAdapter() {
			@Override
			public void componentResized(java.awt.event.ComponentEvent e) {
				updateResponsiveInvoiceLayout();
			}
		});
		SwingUtilities.invokeLater(this::updateResponsiveInvoiceLayout);
	}

	private void updateResponsiveInvoiceLayout() {
		int width = getWidth();
		if (width <= 0) return;

		boolean compact = width < 1180;
		if (compactToolbarMode == compact && qrScanButton != null) return;
		compactToolbarMode = compact;

		if (searchBar != null) {
			searchBar.setPreferredWidth(compact ? 250 : 320);
		}
		if (fromDateFilter != null) {
			fromDateFilter.setPreferredSize(new Dimension(compact ? 112 : 130, 34));
		}
		if (toDateFilter != null) {
			toDateFilter.setPreferredSize(new Dimension(compact ? 112 : 130, 34));
		}
		if (qrScanButton != null) {
			qrScanButton.setText(compact ? "QR" : "Quét QR HĐ");
			qrScanButton.setMargin(compact ? new Insets(6, 9, 6, 9) : new Insets(6, 12, 6, 12));
		}

		applyColumnWidths(compact);
		revalidate();
		repaint();
	}

	@Override
	protected FontAwesomeSolid getIcon() {
		return FontAwesomeSolid.RECEIPT;
	}

	@Override
	protected String getPageTitle() {
		return "Quản lý hóa đơn";
	}

	@Override
	protected String getPageSubtitle() {
		return "Tra cứu lịch sử các hóa đơn bán hàng đã lập";
	}

	@Override
	protected String getAddButtonLabel() {
		return null;
	}

	@Override
	protected String[] getColumnNames() {
		return new String[] { "Mã hóa đơn", "Khách hàng", "Người tạo", "Ngày tạo", "Tổng tiền", "PT thanh toán",
				"Trạng thái", "Đã hoàn", "Đổi/trả", "Yêu cầu hủy" };
	}

	@Override
	protected Object[] mapRowToColumns(Invoice item) {
		return new Object[] { item.getInvoiceCode(),
				item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ", item.getCreatedByName(),
				item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_FORMAT) : "-",
				NumberUtil.formatThousands(item.getTotalAmount() != null ? item.getTotalAmount().longValue() : 0L),
				paymentMethodLabel(item.getPaymentMethod()), statusLabel(item), refundedLabel(item),
				returnStateLabel(item), cancelRequestLabel(item) };
	}

	@Override
	protected int[] numericColumns() {
		return new int[] { 4, 7 };
	}

	@Override
	protected String getEntityLabel() {
		return "hóa đơn";
	}

	@Override
	protected String getItemDisplayName(Invoice item) {
		return item.getInvoiceCode() + " - " + (item.getCustomerName() != null ? item.getCustomerName() : "Khách lẻ");
	}

	@Override
	protected PaginationHelper.PaginationResult<Invoice> fetchPage(int page, int pageSize) {
		return invoiceService.getVisiblePaged(page, pageSize, null, selectedFromDate(), selectedToDate());
	}

	@Override
	protected PaginationHelper.PaginationResult<Invoice> searchPage(String keyword, int page, int pageSize) {
		String normalized = InvoiceQrUtil.normalizeSearchKeyword(keyword);
		return invoiceService.getVisiblePaged(page, pageSize, normalized, selectedFromDate(), selectedToDate());
	}

	@Override
	protected List<Invoice> fetchAllForExport() {
		return invoiceService.getVisibleAll();
	}

	@Override
	protected String getSearchPlaceholder() {
		return "Tìm theo mã hóa đơn, khách hàng, người tạo...";
	}

	@Override
	protected List<String> fetchAutocompleteSuggestions() {
		List<String> names = new ArrayList<>();
		for (Invoice inv : invoiceService.getVisibleAll()) {
			if (inv.getInvoiceCode() != null && !inv.getInvoiceCode().isBlank()) {
				names.add(inv.getInvoiceCode());
			}
			if (inv.getCustomerName() != null && !inv.getCustomerName().isBlank()) {
				names.add(inv.getCustomerName());
			}
			if (inv.getCreatedByName() != null && !inv.getCreatedByName().isBlank()) {
				names.add(inv.getCreatedByName());
			}
		}
		return new ArrayList<>(new LinkedHashSet<>(names));
	}

	@Override
	protected boolean supportsEdit() {
		return false;
	}

	@Override
	protected boolean supportsDelete() {
		return false;
	}

	@Override
	protected boolean supportsView() {
		return true;
	}

	@Override
	protected void viewRow(int modelRow) {
		Invoice item = rowToItem(modelRow);

		if (item == null) {
			return;
		}

		Invoice visibleInvoice = invoiceService.findVisibleById(item.getInvoiceId());

		if (visibleInvoice == null) {

			AppAlert.warning(this, "Không có quyền", "Bạn không được phép xem hóa đơn này.");

			reload();

			return;
		}

		openDetailDialog(visibleInvoice);
	}

	@Override
	protected void openForm(Invoice item) {
	}

	@Override
	protected boolean deleteItem(Invoice item) {
		return false;
	}

	private void openDetailDialog(Invoice item) {
		Window owner = SwingUtilities.getWindowAncestor(this);
		InvoiceDetailDialog dialog = new InvoiceDetailDialog(owner instanceof Frame ? (Frame) owner : null, item,
				invoiceDAO);
		dialog.setVisible(true);
		// Request/approve/reject co the thay doi trang thai sau khi dialog dong.
		reload();
	}

	@Override
	protected void onDataChanged() {
		reload();
	}

	private String statusLabel(Invoice inv) {
		return inv.isCancelled() ? "Đã hủy" : "Hoàn tất";
	}

	private String statusLabel(Object value) {
		return String.valueOf(value);
	}

	private Color statusColor(Object value) {
		return "Đã hủy".equals(String.valueOf(value)) ? AppColor.ERROR : AppColor.SUCCESS;
	}

	private String refundedLabel(Invoice inv) {
		if (inv == null || inv.getRefundedAmount() == null || inv.getRefundedAmount().signum() <= 0) {
			return "—";
		}
		return NumberUtil.formatThousands(inv.getRefundedAmount().longValue());
	}

	private String returnStateLabel(Invoice inv) {
		if (inv == null || inv.isCancelled())
			return "—";
		String state = inv.getReturnState();
		if (state == null || "NONE".equalsIgnoreCase(state))
			return "—";
		if ("FULL".equalsIgnoreCase(state))
			return "Đã trả hết";
		if ("PARTIAL".equalsIgnoreCase(state))
			return "Trả một phần";
		try {
			return inv.getReturnStateLabel();
		} catch (Exception ignore) {
			return state;
		}
	}

	private String returnStateLabel(Object value) {
		return String.valueOf(value);
	}

	private Color returnStateColor(Object value) {
		String s = String.valueOf(value);
		if ("Đã trả hết".equals(s))
			return AppColor.WARNING;
		if ("Trả một phần".equals(s))
			return AppColor.ACCENT;
		return AppColor.TEXT_MUTED;
	}

	private String cancelRequestLabel(Invoice inv) {
		if (inv == null || inv.getCancelRequestStatus() == null || inv.getCancelRequestStatus().isBlank())
			return "—";
		switch (inv.getCancelRequestStatus().toUpperCase()) {
		case "PENDING":
			return "Chờ duyệt";
		case "PROCESSING":
			return "Đang xử lý";
		case "APPROVED":
			return "Đã duyệt";
		case "REJECTED":
			return "Đã từ chối";
		default:
			return inv.getCancelRequestStatus();
		}
	}

	private String cancelRequestLabel(Object value) {
		return String.valueOf(value);
	}

	private Color cancelRequestColor(Object value) {
		String s = String.valueOf(value);
		if ("Chờ duyệt".equals(s))
			return AppColor.WARNING;
		if ("Đang xử lý".equals(s))
			return AppColor.INFO;
		if ("Đã duyệt".equals(s))
			return AppColor.SUCCESS;
		if ("Đã từ chối".equals(s))
			return AppColor.ERROR;
		return AppColor.TEXT_MUTED;
	}

	static String paymentMethodLabel(String method) {
		if (method == null)
			return "-";
		switch (method) {
		case "CASH":
			return "Tiền mặt";
		case "BANK_TRANSFER":
			return "Chuyển khoản";
		case "PAYPAL":
			return "PayPal";
		case "CARD":
			return "Thẻ";
		case "MIXED":
			return "Kết hợp";
		default:
			return method;
		}
	}

	private void exportRowPdf(int modelRow) {
		Invoice item = rowToItem(modelRow);
		if (item == null)
			return;
		try {
			invoiceDAO.attachReturnSummary(item);
			List<InvoiceDetail> details = invoiceDAO.getDetails(item.getInvoiceId());
			File pdfFile = FileUtil.uniqueTempFile("sims_invoices", "HoaDon_" + item.getInvoiceCode(), "pdf");
			InvoicePdfExporter.exportInvoice(item, details, pdfFile);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(pdfFile);
			} else {
				JOptionPane.showMessageDialog(this, "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(), "Xuất PDF",
						JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (Throwable ex) {
			// Bat rong hon Exception: loi khoi tao class PDF (static initializer)
			// duoc JVM boc thanh Error, se khong bi "nuot" im lang nua.
			JOptionPane.showMessageDialog(this, "Lỗi tạo file PDF: " + ex.getMessage(), "Lỗi",
					JOptionPane.ERROR_MESSAGE);
		}
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