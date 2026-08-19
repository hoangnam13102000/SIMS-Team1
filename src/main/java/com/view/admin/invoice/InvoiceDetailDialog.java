package com.view.admin.invoice;

import com.components.AppAlert;
import com.dao.InvoiceDAO;
import com.dao.ReturnExchangeDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.InvoiceCancelRequest;
import com.model.ReturnExchange;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.theme.AppColor;
import com.utils.ImageUtil;
import com.utils.NumberUtil;
import com.utils.PaginationHelper;
import com.utils.pdf.InvoicePdfExporter;
import com.service.AuthService;
import com.service.InvoiceCancelRequestService;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.Desktop;
import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Chi tiet hoa don ban hang + tom tat doi/tra. Khong sua dong InvoiceDetails
 * goc; chi hien thi SL da tra / con lai va so tien da hoan.
 */
public class InvoiceDetailDialog extends JDialog {

	private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

	private final Invoice invoice;
	private final InvoiceDAO invoiceDAO;
	private final ReturnExchangeDAO returnExchangeDAO = new ReturnExchangeDAO();
	private final InvoiceCancelRequestService cancelRequestService = new InvoiceCancelRequestService();

	private JLabel returnNoteLabel;
	private JLabel refundedValueLabel;
	private JLabel netValueLabel;

	public InvoiceDetailDialog(Frame owner, Invoice invoice, InvoiceDAO invoiceDAO) {
		super(owner, "Chi tiết hóa đơn", true);
		this.invoice = invoice;
		this.invoiceDAO = invoiceDAO;

		// Dam bao co tom tat doi/tra
		if (invoice != null) {
			invoiceDAO.attachReturnSummary(invoice);
		}

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setSize(920, 640);
		setLocationRelativeTo(owner);
		setLayout(new BorderLayout(0, 0));
		getContentPane().setBackground(AppColor.WHITE);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);
	}

	// ---------------------------------------------------------------
	// Header
	// ---------------------------------------------------------------

	private JComponent buildHeader() {
		JPanel header = new JPanel(new BorderLayout(12, 8));
		header.setBorder(new EmptyBorder(16, 20, 12, 20));
		header.setBackground(AppColor.WHITE);

		JPanel titleCol = new JPanel();
		titleCol.setOpaque(false);
		titleCol.setLayout(new BoxLayout(titleCol, BoxLayout.Y_AXIS));

		JLabel title = new JLabel(invoice.getInvoiceCode() != null ? invoice.getInvoiceCode() : "—");
		title.setFont(new Font("Segoe UI", Font.BOLD, 18));
		title.setForeground(AppColor.TEXT_PRIMARY);
		titleCol.add(title);

		JLabel sub = new JLabel(buildSubtitle());
		sub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		sub.setForeground(AppColor.TEXT_MUTED);
		titleCol.add(Box.createVerticalStrut(4));
		titleCol.add(sub);

		returnNoteLabel = new JLabel(" ");
		returnNoteLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		returnNoteLabel.setForeground(AppColor.ACCENT);
		titleCol.add(Box.createVerticalStrut(4));
		titleCol.add(returnNoteLabel);
		refreshReturnNote();

		header.add(titleCol, BorderLayout.CENTER);

		JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		badges.setOpaque(false);
		badges.add(badge(statusText(), statusColor()));
		if (invoice.hasReturns()) {
			badges.add(badge(returnStateText(), returnStateColor()));
		}
		header.add(badges, BorderLayout.EAST);

		return header;
	}

	private String buildSubtitle() {
		String customer = invoice.getCustomerName() != null ? invoice.getCustomerName() : "Khách lẻ";
		String created = invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(DATE_TIME) : "—";
		String creator = invoice.getCreatedByName() != null ? invoice.getCreatedByName() : "—";
		return customer + "  ·  " + created + "  ·  NV: " + creator;
	}

	private void refreshReturnNote() {
		if (invoice.hasReturns()) {
			returnNoteLabel.setText(invoice.getReturnNote());
			returnNoteLabel.setVisible(true);
		} else {
			returnNoteLabel.setText(" ");
			returnNoteLabel.setVisible(false);
		}
	}

	// ---------------------------------------------------------------
	// Body: info + lines + returns
	// ---------------------------------------------------------------

	private JComponent buildBody() {
		JPanel body = new JPanel(new BorderLayout(0, 12));
		body.setBorder(new EmptyBorder(0, 20, 12, 20));
		body.setBackground(AppColor.WHITE);

		body.add(buildMoneySummary(), BorderLayout.NORTH);

		JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		tabs.addTab("Sản phẩm", buildProductTable());
		tabs.addTab("Phiếu đổi/trả", buildReturnTable());
		body.add(tabs, BorderLayout.CENTER);

		return body;
	}

	private JComponent buildMoneySummary() {

		JPanel container = new JPanel(new BorderLayout(0, 10));

		container.setOpaque(false);

		JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));

		row.setOpaque(false);

		/*
		 * Số tiền hóa đơn tại thời điểm khách mua. Không thay đổi sau RETURN.
		 */
		row.add(summaryCard("Tổng ban đầu", formatMoney(invoice.getOriginalTotalAmount()), AppColor.TEXT_PRIMARY));

		/*
		 * Tổng refund đã được APPROVED.
		 */
		row.add(summaryCard("Đã duyệt hoàn", formatMoney(invoice.getRefundedAmount()),
				invoice.getRefundedAmount().signum() > 0 ? AppColor.WARNING : AppColor.TEXT_MUTED));

		/*
		 * Tổng tiền refund thực sự COMPLETED.
		 */
		row.add(summaryCard("Đã hoàn thực tế", formatMoney(invoice.getCompletedRefundAmount()),
				invoice.getCompletedRefundAmount().signum() > 0 ? AppColor.WARNING : AppColor.TEXT_MUTED));

		/*
		 * Giá trị hàng còn lại sau đổi/trả.
		 */
		row.add(summaryCard("Giá trị còn lại", formatMoney(invoice.getTotalAmount()), AppColor.SUCCESS));

		container.add(row, BorderLayout.NORTH);

		container.add(buildPaymentInfoRow(), BorderLayout.CENTER);

		return container;
	}

	/**
	 * Hang thong tin thanh toan: phuong thuc, ma khuyen mai da dung, diem da tru.
	 */
	private JComponent buildPaymentInfoRow() {
		JPanel row = new JPanel(new GridLayout(1, 3, 12, 0));
		row.setOpaque(false);

		row.add(summaryCard("Phương thức thanh toán", InvoicePanel.paymentMethodLabel(invoice.getPaymentMethod()),
				AppColor.TEXT_PRIMARY));

		String promo = invoice.getPromotionCode() != null && !invoice.getPromotionCode().isBlank()
				? invoice.getPromotionCode()
				: "—";
		row.add(summaryCard("Mã khuyến mãi đã dùng", promo, AppColor.TEXT_PRIMARY));

		String pointsText = invoice.getPointsUsed() > 0
				? invoice.getPointsUsed() + " điểm  (-" + formatMoney(invoice.getPointsDiscountAmount()) + ")"
				: "Không dùng điểm";
		row.add(summaryCard("Điểm đã trừ", pointsText, AppColor.TEXT_PRIMARY));

		return row;
	}

	private JPanel summaryCard(String label, String value, Color valueColor) {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1),
				new EmptyBorder(10, 12, 10, 12)));
		card.setBackground(AppColor.WHITE);

		JLabel lb = new JLabel(label);
		lb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
		lb.setForeground(AppColor.TEXT_MUTED);
		card.add(lb);

		JLabel val = new JLabel(value);
		val.setFont(new Font("Segoe UI", Font.BOLD, 15));
		val.setForeground(valueColor != null ? valueColor : AppColor.TEXT_PRIMARY);
		card.add(Box.createVerticalStrut(4));
		card.add(val);
		return card;
	}

	private JComponent buildProductTable() {
		String[] cols = { "Ảnh", "Mã SP", "Tên sản phẩm", "Đơn giá", "SL", "Đã trả", "Còn lại", "Thành tiền" };
		DefaultTableModel model = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		List<InvoiceDetail> details = invoiceDAO.getDetails(invoice.getInvoiceId());
		int thumbSize = 40;
		for (InvoiceDetail d : details) {
			model.addRow(new Object[] { ImageUtil.loadIcon(d.getProductImageUrl(), thumbSize, thumbSize),
					d.getProductCode() != null ? d.getProductCode() : "—",
					d.getProductName() != null ? d.getProductName() : "—", formatMoney(d.getUnitPrice()),
					d.getQuantity(), d.getReturnedQuantity(), d.getRemainingQuantity(),
					formatMoney(d.getLineTotal()) });
		}

		JTable table = new JTable(model);
		table.setRowHeight(thumbSize + 8);
		table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
		table.setFillsViewportHeight(true);
		// Bang nay chi de xem, khong co hanh dong nao gan voi viec chon dong ->
		// tat selection/focus de tranh hien tuong dong dau tien bi to sang
		// (nhu dang hover) ngay khi mo dialog, chi tro lai binh thuong sau khi
		// nguoi dung bam chuot vao bang.
		table.setRowSelectionAllowed(false);
		table.setColumnSelectionAllowed(false);
		table.setCellSelectionEnabled(false);
		table.setFocusable(false);

		table.getColumnModel().getColumn(0).setMinWidth(thumbSize + 16);
		table.getColumnModel().getColumn(0).setMaxWidth(thumbSize + 16);
		table.getColumnModel().getColumn(0).setPreferredWidth(thumbSize + 16);

		DefaultTableCellRenderer right = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(tbl, value, false, false, row, column);
				applyRowStyle(c, row, details);
				return c;
			}
		};
		right.setHorizontalAlignment(SwingConstants.RIGHT);
		for (int c : new int[] { 3, 4, 5, 6, 7 }) {
			table.getColumnModel().getColumn(c).setCellRenderer(right);
		}

		// Cot anh: chi hien icon, canh giua, khong text.
		// Luu y: DefaultTableCellRenderer.setValue() mac dinh CHI xu ly text
		// (goi setText(value.toString())) - no KHONG tu nhan dien Icon nhu
		// renderer noi bo rieng cua JTable (IconRenderer, chi ap dung khi
		// KHONG set renderer rieng cho cot). Vi vay phai tu goi setIcon() +
		// xoa text thay vi de super.getTableCellRendererComponent() tu xu ly,
		// neu khong se in ra "javax.swing.ImageIcon@..." thay vi anh.
		DefaultTableCellRenderer imageRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				JLabel label = (JLabel) super.getTableCellRendererComponent(tbl, null, false, false, row, column);
				label.setIcon(value instanceof Icon ? (Icon) value : null);
				label.setText(null);
				applyRowStyle(label, row, details);
				return label;
			}
		};
		imageRenderer.setHorizontalAlignment(SwingConstants.CENTER);
		table.getColumnModel().getColumn(0).setCellRenderer(imageRenderer);

		// To dam dong co doi/tra
		table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(tbl, value, false, false, row, column);
				applyRowStyle(c, row, details);
				if (c instanceof JLabel) {
					int align = (column >= 3) ? SwingConstants.RIGHT : SwingConstants.LEFT;
					((JLabel) c).setHorizontalAlignment(align);
					((JLabel) c).setBorder(new EmptyBorder(0, 8, 0, 8));
				}
				return c;
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
		return scroll;
	}

	/**
	 * Mau nen/chu co dinh cho 1 dong san pham, khong phu thuoc trang thai
	 * selected/focus.
	 */
	private void applyRowStyle(Component c, int row, List<InvoiceDetail> details) {
		Color bg;
		Color fg = AppColor.TEXT_PRIMARY;
		if (row < details.size()) {
			InvoiceDetail d = details.get(row);
			if (d.isFullyReturned()) {
				bg = new Color(0xFEF3C7);
				fg = new Color(0x1E293B); // nen mau sang co dinh -> can chu toi co dinh de doc duoc o ca 2 theme
			} else if (d.isPartiallyReturned()) {
				bg = new Color(0xEFF6FF);
				fg = new Color(0x1E293B);
			} else {
				bg = row % 2 == 0 ? AppColor.WHITE : AppColor.TABLE_ROW_ODD;
			}
		} else {
			bg = AppColor.WHITE;
		}
		c.setBackground(bg);
		c.setForeground(fg);
	}

	private JComponent buildReturnTable() {
		String[] cols = { "Mã phiếu", "Loại", "Giá trị hoàn", "Trạng thái", "Ngày tạo", "Lý do" };
		DefaultTableModel model = new DefaultTableModel(cols, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		// Lay cac phieu doi/tra cua hoa don (toi da 200)
		PaginationHelper.PaginationResult<ReturnExchange> page = returnExchangeDAO.getPaged(1, 200,
				"r.InvoiceID = " + invoice.getInvoiceId());
		List<ReturnExchange> returns = page != null && page.getData() != null ? page.getData() : List.of();

		if (returns.isEmpty()) {
			JLabel empty = new JLabel("Chưa có phiếu đổi/trả cho hóa đơn này.", SwingConstants.CENTER);
			empty.setFont(new Font("Segoe UI", Font.PLAIN, 13));
			empty.setForeground(AppColor.TEXT_MUTED);
			empty.setBorder(new EmptyBorder(40, 20, 40, 20));
			return empty;
		}

		for (ReturnExchange r : returns) {
			model.addRow(new Object[] { "PT-" + r.getReturnId(),
					ReturnExchange.TYPE_EXCHANGE.equalsIgnoreCase(r.getType()) ? "Đổi" : "Trả",
					formatMoney(r.getTotalValue()), statusReturn(r.getStatus()),
					r.getCreatedAt() != null ? r.getCreatedAt().format(DATE_TIME) : "—",
					r.getReason() != null ? r.getReason() : "—" });
		}

		JTable table = new JTable(model);
		table.setRowHeight(36);
		table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
		table.setFillsViewportHeight(true);

		JScrollPane scroll = new JScrollPane(table);
		scroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
		return scroll;
	}

	// ---------------------------------------------------------------
	// Footer actions
	// ---------------------------------------------------------------

	private JComponent buildFooter() {
		JPanel footer = new JPanel(new BorderLayout());
		footer.setBorder(new EmptyBorder(12, 20, 16, 20));
		footer.setBackground(AppColor.WHITE);

		JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		left.setOpaque(false);

		// Nut "Doi / tra hang": chi hien khi hoa don CHUA huy, con quyen tao
		// phieu doi/tra, VA con it nhat 1 dong san pham CHUA tra het (con hang
		// de tra). Da tra het het roi (het hang) -> an nut.
		List<InvoiceDetail> footerDetails = invoiceDAO.getDetails(invoice.getInvoiceId());
		boolean hasReturnableItems = footerDetails.stream().anyMatch(d -> d.getRemainingQuantity() > 0);
		boolean canReturnExchange = !invoice.isCancelled() && hasReturnableItems
				&& PermissionManager.getInstance().can(AppPermission.RETURN_EXCHANGE_CREATE);

		if (canReturnExchange) {
			JButton returnBtn = new JButton("Đổi / trả hàng");
			FontIcon returnIcon = FontIcon.of(FontAwesomeSolid.EXCHANGE_ALT, 14);
			returnIcon.setIconColor(Color.WHITE);
			returnBtn.setIcon(returnIcon);
			styleButton(returnBtn, AppColor.ACCENT, Color.WHITE);
			returnBtn.addActionListener(e -> onReturnExchange());
			left.add(returnBtn);
		}

		InvoiceCancelRequest activeCancelRequest = cancelRequestService.getActiveForInvoice(invoice.getInvoiceId());

		String cancelRequestStatus = invoice.getCancelRequestStatus();

		boolean hasActiveCancelRequestStatus = "PENDING".equalsIgnoreCase(cancelRequestStatus)
				|| "PROCESSING".equalsIgnoreCase(cancelRequestStatus);

		/*
		 * Fallback: InvoicePanel đã đọc được trạng thái PENDING/PROCESSING nhưng DAO
		 * chi tiết chưa lấy được object request thì thử lấy request mới nhất.
		 */
		if (activeCancelRequest == null && hasActiveCancelRequestStatus) {
			activeCancelRequest = cancelRequestService.getLatestForInvoice(invoice.getInvoiceId());
		}

		com.model.User currentUser = AuthService.getInstance().getCurrentUser();

		boolean isInvoiceOwner = currentUser != null && invoice.getCreatedBy() == currentUser.getUserId();

		/*
		 * ========================================================= SALES_STAFF
		 * =========================================================
		 *
		 * Nhân viên không được hủy trực tiếp. Chỉ được gửi yêu cầu hủy hóa đơn CASH của
		 * chính mình.
		 */
		boolean canRequestPermission = PermissionManager.getInstance().can(AppPermission.INVOICE_CANCEL_REQUEST);

		if (!invoice.isCancelled() && isInvoiceOwner && canRequestPermission) {

			if (hasActiveCancelRequestStatus || activeCancelRequest != null) {

				boolean processing = "PROCESSING".equalsIgnoreCase(cancelRequestStatus)
						|| (activeCancelRequest != null && activeCancelRequest.isProcessing());

				JButton pendingBtn = new JButton(processing ? "Yêu cầu hủy đang xử lý" : "Đã gửi yêu cầu hủy");

				styleButton(pendingBtn, AppColor.DISABLED_BTN, Color.WHITE);

				pendingBtn.setEnabled(false);
				left.add(pendingBtn);

			} else if ("CASH".equalsIgnoreCase(invoice.getPaymentMethod()) && !invoice.hasReturns()) {

				JButton requestBtn = new JButton("Yêu cầu hủy");

				styleButton(requestBtn, AppColor.WARNING, Color.WHITE);

				requestBtn.addActionListener(e -> onRequestCancelInvoice());

				left.add(requestBtn);
			}
		}

		/*
		 * ========================================================= SALES_MANAGER /
		 * ADMIN =========================================================
		 */
		boolean canReviewCancelRequest = PermissionManager.getInstance().can(AppPermission.INVOICE_CANCEL)
				&& PermissionManager.getInstance().can(AppPermission.INVOICE_VIEW_ALL);

		/*
		 * QUAN TRỌNG: Hiện nút xử lý dựa trên trạng thái đã được InvoiceDAO đọc từ DB,
		 * không phụ thuộc hoàn toàn vào activeCancelRequest.
		 */
		if (!invoice.isCancelled() && hasActiveCancelRequestStatus && canReviewCancelRequest) {

			boolean processing = "PROCESSING".equalsIgnoreCase(cancelRequestStatus)
					|| (activeCancelRequest != null && activeCancelRequest.isProcessing());

			JButton reviewBtn = new JButton(processing ? "Yêu cầu đang được xử lý" : "Xử lý yêu cầu hủy");

			styleButton(reviewBtn, processing ? AppColor.DISABLED_BTN : AppColor.ERROR, Color.WHITE);

			reviewBtn.setEnabled(!processing);

			if (!processing) {
				reviewBtn.addActionListener(e -> onProcessCancelRequest());
			}

			left.add(reviewBtn);
		}

		/*
		 * Manager/Admin chỉ được hủy trực tiếp nếu hóa đơn THỰC SỰ KHÔNG có request
		 * PENDING/PROCESSING.
		 */
		boolean canCancelInvoiceDirectly = !invoice.isCancelled() && !hasActiveCancelRequestStatus
				&& activeCancelRequest == null && "CASH".equalsIgnoreCase(invoice.getPaymentMethod())
				&& !invoice.hasReturns() && canReviewCancelRequest;

		if (canCancelInvoiceDirectly) {

			JButton cancelBtn = new JButton("Hủy hóa đơn");

			styleButton(cancelBtn, AppColor.ERROR, Color.WHITE);

			cancelBtn.addActionListener(e -> onCancelInvoice());

			left.add(cancelBtn);
		}

		JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		right.setOpaque(false);

		JButton pdfBtn = new JButton("Xuất PDF");
		FontIcon pdfIcon = FontIcon.of(FontAwesomeSolid.FILE_PDF, 14);
		pdfIcon.setIconColor(Color.WHITE);
		pdfBtn.setIcon(pdfIcon);
		styleButton(pdfBtn, AppColor.ACCENT, Color.WHITE);
		pdfBtn.addActionListener(e -> exportPdf());

		JButton closeBtn = new JButton("Đóng");
		styleButton(closeBtn, AppColor.CANCEL_BG, AppColor.TEXT_PRIMARY);
		closeBtn.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1),
				new EmptyBorder(7, 15, 7, 15)));
		closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
			@Override
			public void mouseEntered(java.awt.event.MouseEvent e) {
				closeBtn.setBackground(AppColor.CANCEL_HOVER);
			}

			@Override
			public void mouseExited(java.awt.event.MouseEvent e) {
				closeBtn.setBackground(AppColor.CANCEL_BG);
			}
		});
		closeBtn.addActionListener(e -> dispose());

		right.add(pdfBtn);
		right.add(closeBtn);

		footer.add(left, BorderLayout.WEST);
		footer.add(right, BorderLayout.EAST);
		return footer;
	}

	private void styleButton(JButton btn, Color bg, Color fg) {
		btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
		btn.setBackground(bg);
		btn.setForeground(fg);
		btn.setFocusPainted(false);
		btn.setBorder(new EmptyBorder(8, 16, 8, 16));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private void onReturnExchange() {
		List<InvoiceDetail> details = invoiceDAO.getDetails(invoice.getInvoiceId());
		ReturnExchangeDialog dialog = new ReturnExchangeDialog((Frame) getOwner(), invoice, details, returnExchangeDAO);
		dialog.setVisible(true);
		if (dialog.isCreated()) {
			// Dong dialog nay de nguoi dung mo lai tu InvoicePanel, luc do
			// tong tien/SL da tra/con lai se duoc doc lai moi nhat.
			dispose();
		}
	}

	private void onRequestCancelInvoice() {
		if (!PermissionManager.getInstance().can(AppPermission.INVOICE_CANCEL_REQUEST)) {
			AppAlert.error(this, "Không có quyền", "Bạn không có quyền gửi yêu cầu hủy hóa đơn.");
			return;
		}

		String reason = JOptionPane.showInputDialog(this, "Lý do yêu cầu hủy hóa đơn " + invoice.getInvoiceCode() + ":",
				"Yêu cầu hủy hóa đơn", JOptionPane.QUESTION_MESSAGE);
		if (reason == null)
			return;
		if (reason.isBlank()) {
			AppAlert.error(this, "Thiếu lý do", "Vui lòng nhập lý do yêu cầu hủy.");
			return;
		}

		int confirm = JOptionPane.showConfirmDialog(this,
				"Gửi yêu cầu hủy hóa đơn " + invoice.getInvoiceCode() + " cho Quản lý bán hàng/Admin duyệt?",
				"Xác nhận gửi yêu cầu", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
		if (confirm != JOptionPane.YES_OPTION)
			return;

		String error = cancelRequestService.requestCancel(invoice.getInvoiceId(), reason);
		if (error != null) {
			AppAlert.error(this, "Không gửi được", error);
			return;
		}

		AppAlert.success(this, "Đã gửi",
				"Yêu cầu hủy hóa đơn " + invoice.getInvoiceCode() + " đã được gửi và đang chờ quản lý duyệt.");
		dispose();
	}

	private void onProcessCancelRequest() {

	    System.out.println(
	            "[InvoiceCancel] code="
	            + invoice.getInvoiceCode()
	            + ", invoiceId="
	            + invoice.getInvoiceId()
	            + ", requestStatus="
	            + invoice.getCancelRequestStatus()
	    );

	    InvoiceCancelRequest request =
	            cancelRequestService.getActiveForInvoice(
	                    invoice.getInvoiceId()
	            );

	    /*
	     * Fallback:
	     * Nếu request active không tải được thì thử lấy request mới nhất.
	     */
	    if (request == null) {
	        request =
	                cancelRequestService.getLatestForInvoice(
	                        invoice.getInvoiceId()
	                );
	    }

	    if (request == null) {
	        AppAlert.error(
	                this,
	                "Không đọc được yêu cầu",
	                "Không tải được chi tiết yêu cầu hủy. "
	                + "Vui lòng làm mới danh sách rồi thử lại."
	        );
	        return;
	    }

	    if (!request.isPending() && !request.isProcessing()) {
	        AppAlert.warning(
	                this,
	                "Yêu cầu đã xử lý",
	                "Yêu cầu hủy này không còn ở trạng thái chờ duyệt."
	        );
	        return;
	    }

	    if (request.isProcessing()) {
	        AppAlert.warning(
	                this,
	                "Đang xử lý",
	                "Yêu cầu này đang được một quản lý khác xử lý."
	        );
	        return;
	    }

	    String requestedAt =
	            request.getRequestedAt() != null
	                    ? request.getRequestedAt().format(DATE_TIME)
	                    : "—";

	    String requester =
	            request.getRequestedByName() != null
	                    ? request.getRequestedByName()
	                    : ("User #" + request.getRequestedBy());

	    String message =
	            "Hóa đơn: " + invoice.getInvoiceCode()
	            + "\nNhân viên yêu cầu: " + requester
	            + "\nThời gian: " + requestedAt
	            + "\n\nLý do:\n"
	            + request.getReason();

	    Object[] options = {
	            "Duyệt hủy",
	            "Từ chối",
	            "Đóng"
	    };

	    int choice = JOptionPane.showOptionDialog(
	            this,
	            message,
	            "Xử lý yêu cầu hủy hóa đơn",
	            JOptionPane.DEFAULT_OPTION,
	            JOptionPane.WARNING_MESSAGE,
	            null,
	            options,
	            options[2]
	    );

	    /*
	     * DUYỆT
	     */
	    if (choice == 0) {

	        int confirm = JOptionPane.showConfirmDialog(
	                this,
	                "Sau khi duyệt, hệ thống sẽ hủy hóa đơn "
	                + "và hoàn kho/điểm theo nghiệp vụ hiện tại.\n\n"
	                + "Bạn chắc chắn muốn duyệt yêu cầu này?",
	                "Xác nhận duyệt hủy",
	                JOptionPane.YES_NO_OPTION,
	                JOptionPane.WARNING_MESSAGE
	        );

	        if (confirm != JOptionPane.YES_OPTION) {
	            return;
	        }

	        String error =
	                cancelRequestService.approve(
	                        request.getRequestId(),
	                        "Đã kiểm tra và chấp thuận yêu cầu hủy."
	                );

	        if (error != null) {
	            AppAlert.error(
	                    this,
	                    "Không duyệt được",
	                    error
	            );
	            return;
	        }

	        AppAlert.success(
	                this,
	                "Đã duyệt",
	                "Yêu cầu hủy đã được duyệt. "
	                + "Hóa đơn " + invoice.getInvoiceCode()
	                + " đã được hủy."
	        );

	        dispose();
	    }

	    /*
	     * TỪ CHỐI
	     */
	    else if (choice == 1) {

	        String note = JOptionPane.showInputDialog(
	                this,
	                "Nhập lý do từ chối yêu cầu hủy:",
	                "Từ chối yêu cầu hủy",
	                JOptionPane.QUESTION_MESSAGE
	        );

	        if (note == null) {
	            return;
	        }

	        if (note.isBlank()) {
	            AppAlert.error(
	                    this,
	                    "Thiếu lý do",
	                    "Vui lòng nhập lý do từ chối."
	            );
	            return;
	        }

	        String error =
	                cancelRequestService.reject(
	                        request.getRequestId(),
	                        note.trim()
	                );

	        if (error != null) {
	            AppAlert.error(
	                    this,
	                    "Không từ chối được",
	                    error
	            );
	            return;
	        }

	        AppAlert.success(
	                this,
	                "Đã từ chối",
	                "Yêu cầu hủy đã bị từ chối. "
	                + "Hóa đơn " + invoice.getInvoiceCode()
	                + " vẫn hoạt động."
	        );

	        dispose();
	    }
	}

	private void onCancelInvoice() {
		if (!PermissionManager.getInstance().can(AppPermission.INVOICE_CANCEL)) {
			AppAlert.error(this, "Không có quyền", "Bạn không có quyền hủy hóa đơn.");
			return;
		}

		String reason = JOptionPane.showInputDialog(this, "Lý do hủy hóa đơn:", "Hủy hóa đơn",
				JOptionPane.QUESTION_MESSAGE);
		if (reason == null)
			return;
		if (reason.isBlank()) {
			AppAlert.error(this, "Thiếu lý do", "Vui lòng nhập lý do hủy.");
			return;
		}

		/*
		 * INVOICE_VIEW_ALL: Manager/Admin không bị giới hạn CreatedBy.
		 *
		 * SALES_STAFF: truyền UserID để DAO bắt buộc invoice.CreatedBy phải là chính
		 * nhân viên đang đăng nhập.
		 */
		Integer cancelScopeUserId = PermissionManager.getInstance().can(AppPermission.INVOICE_VIEW_ALL) ? null
				: AuthService.getInstance().getCurrentUser().getUserId();

		String err = invoiceDAO.cancelInvoice(invoice.getInvoiceId(), reason.trim(), cancelScopeUserId);
		if (err != null) {
			AppAlert.error(this, "Không hủy được", err);
			return;
		}
		AppAlert.success(this, "Đã hủy", "Hóa đơn " + invoice.getInvoiceCode() + " đã được hủy.");
		dispose();
	}

	private void exportPdf() {
		try {
			List<InvoiceDetail> details = invoiceDAO.getDetails(invoice.getInvoiceId());
			String safeCode = invoice.getInvoiceCode() != null
					? invoice.getInvoiceCode().replaceAll("[^a-zA-Z0-9]", "_")
					: "HD";
			// Ten file phai DUY NHAT cho moi lan xuat (them timestamp), KHONG dung
			// ten co dinh theo ma hoa don: lan xuat truoc nguoi dung co the da mo
			// file PDF do bang trinh xem ben ngoai (Desktop.open() ben duoi) va
			// trinh xem van dang giu file mo -> Windows khoa file lai. Neu dung
			// lai dung 1 ten, lan ghi tiep theo se bi loi "The process cannot
			// access the file because it is being used by another process".
			String timestamp = java.time.LocalDateTime.now()
					.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
			String fileName = "HoaDon_" + safeCode + "_" + timestamp + ".pdf";
			File tempDir = new File(System.getProperty("java.io.tmpdir"), "sims_invoices");
			if (!tempDir.exists())
				tempDir.mkdirs();
			File pdfFile = new File(tempDir, fileName);
			InvoicePdfExporter.exportInvoice(invoice, details, pdfFile);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(pdfFile);
			} else {
				JOptionPane.showMessageDialog(this, "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(), "Xuất PDF",
						JOptionPane.INFORMATION_MESSAGE);
			}
		} catch (Throwable ex) {
			// Bat rong hon Exception: loi khoi tao class PDF (static initializer)
			// duoc JVM boc thanh Error, se khong bi "nuot" im lang nua.
			AppAlert.error(this, "Lỗi PDF", ex.getMessage());
		}
	}

	// ---------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------

	private static BigDecimal nvl(BigDecimal v) {
		return v != null ? v : BigDecimal.ZERO;
	}

	private static String formatMoney(BigDecimal v) {
		if (v == null)
			return "0";
		return NumberUtil.formatThousands(v.longValue()) + "đ";
	}

	private String statusText() {
		return invoice.isCancelled() ? "Đã hủy" : "Đã hoàn tất";
	}

	private Color statusColor() {
		return invoice.isCancelled() ? AppColor.ERROR : AppColor.SUCCESS;
	}

	private String returnStateText() {
		String s = invoice.getReturnState();
		if ("FULL".equalsIgnoreCase(s))
			return "Đã trả hết";
		if ("PARTIAL".equalsIgnoreCase(s))
			return "Trả một phần";
		return "—";
	}

	private Color returnStateColor() {
		String s = invoice.getReturnState();
		if ("FULL".equalsIgnoreCase(s))
			return AppColor.WARNING;
		if ("PARTIAL".equalsIgnoreCase(s))
			return AppColor.ACCENT;
		return AppColor.TEXT_MUTED;
	}

	private static String statusReturn(String status) {
		if (status == null)
			return "—";
		return switch (status.toUpperCase()) {
		case "APPROVED" -> "Đã duyệt";
		case "PENDING" -> "Chờ duyệt";
		case "REJECTED" -> "Từ chối";
		default -> status;
		};
	}

	private JLabel badge(String text, Color color) {
		JLabel lb = new JLabel(text);
		lb.setOpaque(true);
		lb.setBackground(color);
		lb.setForeground(Color.WHITE);
		lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
		lb.setBorder(new EmptyBorder(4, 10, 4, 10));
		return lb;
	}
}