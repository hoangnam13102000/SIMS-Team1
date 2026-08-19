package com.view.admin.invoice;

import com.components.BaseDialog;
import com.dao.ProductDAO;
import com.dao.ReturnExchangeDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Product;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dialog tạo yêu cầu đổi/trả hàng cho 1 hóa đơn ACTIVE — mở từ nút "Đổi / trả hàng"
 * trên {@link InvoiceDetailDialog}.
 * <p>
 * UI đồng bộ layout dialog kho (header icon badge + card form + footer chuẩn project).
 * Logic nghiệp vụ giữ nguyên:
 * <ul>
 *   <li>Trả hàng / Đổi hàng</li>
 *   <li>SL trả giới hạn theo {@link ReturnExchangeDAO#getReturnableQuantities}</li>
 *   <li>Đổi hàng: chọn SP mới (OUT) + kiểm tra tồn</li>
 *   <li>RETURN: chọn phương thức hoàn tiền; EXCHANGE: không gán refund</li>
 * </ul>
 */
public class ReturnExchangeDialog extends JDialog {

	private final Invoice invoice;
	private final ReturnExchangeDAO returnExchangeDAO;
	private final ProductDAO productDAO = new ProductDAO();

	private final Map<Integer, InvoiceDetail> invoiceLinesByProduct = new LinkedHashMap<>();
	private final Map<Integer, JSpinner> returnSpinners = new LinkedHashMap<>();
	private final Map<Integer, Integer> returnableQty;

	private JRadioButton typeReturn;
	private JRadioButton typeExchange;
	private JPanel exchangeSection;
	private JPanel exchangeListPanel;

	private JPanel refundSection;
	private JComboBox<String> refundMethodCombo;
	private JComboBox<Product> productCombo;
	private JSpinner exchangeQtySpinner;
	private JTextArea reasonArea;

	private final List<ReturnExchangeDetail> exchangeOutLines = new ArrayList<>();

	private boolean created = false;

	public ReturnExchangeDialog(Frame owner, Invoice invoice, List<InvoiceDetail> invoiceDetails,
			ReturnExchangeDAO returnExchangeDAO) {
		super(owner, "Đổi / trả hàng", Dialog.ModalityType.APPLICATION_MODAL);
		this.invoice = invoice;
		this.returnExchangeDAO = returnExchangeDAO;
		this.returnableQty = returnExchangeDAO.getReturnableQuantities(invoice.getInvoiceId());
		for (InvoiceDetail d : invoiceDetails) {
			invoiceLinesByProduct.put(d.getProductId(), d);
		}

		setSize(760, 720);
		setMinimumSize(new Dimension(640, 580));
		setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		getContentPane().setBackground(AppColor.WHITE);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(invoiceDetails), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		getRootPane().registerKeyboardAction(e -> dispose(),
				KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		setLocationRelativeTo(owner);
	}

	/** true nếu dialog đã tạo yêu cầu thành công (InvoiceDetailDialog reload). */
	public boolean isCreated() {
		return created;
	}

	// ---------------------------------------------------------------
	// Header — icon badge tròn + mã HĐ (đồng bộ dialog chi tiết phiếu)
	// ---------------------------------------------------------------

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(14, 0));
		header.setBackground(AppColor.WHITE);
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
				new EmptyBorder(18, 24, 18, 24)));

		FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCHANGE_ALT, 18);
		icon.setIconColor(AppColor.ACCENT);
		JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(AppColor.ACCENT_BG_SOFT);
				g2.fillOval(0, 0, getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};
		iconBadge.setPreferredSize(new Dimension(44, 44));
		iconBadge.setOpaque(false);

		JPanel titleBox = new JPanel();
		titleBox.setOpaque(false);
		titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

		JLabel titleLabel = new JLabel("Đổi / trả hàng · " + invoice.getInvoiceCode());
		titleLabel.setFont(AppFont.DIALOG_TITLE);
		titleLabel.setForeground(AppColor.TEXT_PRIMARY);
		titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel subtitleLabel = new JLabel("Ghi nhận hàng khách trả và/hoặc hàng đổi mới giao");
		subtitleLabel.setFont(AppFont.BODY);
		subtitleLabel.setForeground(AppColor.TEXT_MUTED);
		subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

		titleBox.add(titleLabel);
		titleBox.add(Box.createVerticalStrut(4));
		titleBox.add(subtitleLabel);

		header.add(iconBadge, BorderLayout.WEST);
		header.add(titleBox, BorderLayout.CENTER);
		return header;
	}

	// ---------------------------------------------------------------
	// Body
	// ---------------------------------------------------------------

	private JScrollPane buildBody(List<InvoiceDetail> invoiceDetails) {
		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(AppColor.WHITE);
		content.setBorder(new EmptyBorder(18, 24, 18, 24));

		// ---- Loại yêu cầu (card) ----
		content.add(sectionLabel("Loại yêu cầu"));
		content.add(Box.createVerticalStrut(8));
		content.add(buildTypeCard());
		content.add(Box.createVerticalStrut(18));

		// ---- Sản phẩm khách trả lại ----
		content.add(sectionLabel("Sản phẩm khách trả lại"));
		content.add(Box.createVerticalStrut(8));
		content.add(buildInvoiceLinesPanel(invoiceDetails));
		content.add(Box.createVerticalStrut(18));

		// ---- Hàng đổi (chỉ hiện khi Đổi hàng) ----
		exchangeSection = buildExchangeSection();
		exchangeSection.setVisible(false);
		exchangeSection.setAlignmentX(Component.LEFT_ALIGNMENT);
		content.add(exchangeSection);
		content.add(Box.createVerticalStrut(18));

		// ---- Hoàn tiền (chỉ RETURN) ----
		refundSection = buildRefundSection();
		refundSection.setVisible(true);
		content.add(refundSection);
		content.add(Box.createVerticalStrut(18));

		// ---- Lý do ----
		content.add(sectionLabel("Lý do đổi/trả (bắt buộc)"));
		content.add(Box.createVerticalStrut(8));
		reasonArea = new JTextArea(3, 20);
		reasonArea.setFont(AppFont.BODY);
		reasonArea.setForeground(AppColor.TEXT_PRIMARY);
		reasonArea.setBackground(AppColor.WHITE);
		reasonArea.setCaretColor(AppColor.ACCENT);
		reasonArea.setLineWrap(true);
		reasonArea.setWrapStyleWord(true);
		reasonArea.setBorder(new EmptyBorder(10, 12, 10, 12));
		JScrollPane reasonScroll = new JScrollPane(reasonArea);
		reasonScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		reasonScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
		reasonScroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
		reasonScroll.getViewport().setBackground(AppColor.WHITE);
		content.add(reasonScroll);

		JScrollPane scroll = new JScrollPane(content);
		scroll.setBorder(null);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.getViewport().setBackground(AppColor.WHITE);
		return scroll;
	}

	private JLabel sectionLabel(String text) {
		JLabel label = new JLabel(text);
		label.setFont(AppFont.BODY_BOLD);
		label.setForeground(AppColor.TEXT_PRIMARY);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	/** Card chọn Trả / Đổi — nền BG_LIGHT, radio rõ ràng. */
	private JPanel buildTypeCard() {
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setOpaque(true);
		card.setBackground(AppColor.BG_LIGHT);
		card.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(12, 14, 12, 14)));
		card.setAlignmentX(Component.LEFT_ALIGNMENT);
		card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 96));

		typeReturn = new JRadioButton("Trả hàng — khách trả lại, không lấy sản phẩm khác", true);
		typeExchange = new JRadioButton("Đổi hàng — khách trả lại + lấy sản phẩm khác thay thế");
		styleRadio(typeReturn);
		styleRadio(typeExchange);

		ButtonGroup group = new ButtonGroup();
		group.add(typeReturn);
		group.add(typeExchange);

		typeReturn.addActionListener(e -> {
			exchangeSection.setVisible(false);
			if (refundSection != null) refundSection.setVisible(true);
			revalidate();
			repaint();
		});
		typeExchange.addActionListener(e -> {
			exchangeSection.setVisible(true);
			if (refundSection != null) refundSection.setVisible(false);
			revalidate();
			repaint();
		});

		card.add(typeReturn);
		card.add(Box.createVerticalStrut(6));
		card.add(typeExchange);
		return card;
	}

	private void styleRadio(JRadioButton rb) {
		rb.setOpaque(false);
		rb.setFont(AppFont.BODY);
		rb.setForeground(AppColor.TEXT_PRIMARY);
		rb.setFocusPainted(false);
		rb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		rb.setAlignmentX(Component.LEFT_ALIGNMENT);
	}

	private JPanel buildRefundSection() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(false);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);

		panel.add(sectionLabel("Phương thức hoàn tiền"));
		panel.add(Box.createVerticalStrut(8));

		refundMethodCombo = new JComboBox<>(new String[]{"Tiền mặt", "Chuyển khoản", "Thẻ", "PayPal"});
		refundMethodCombo.setFont(AppFont.BODY);
		refundMethodCombo.setBackground(AppColor.WHITE);
		refundMethodCombo.setForeground(AppColor.TEXT_PRIMARY);
		refundMethodCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
		refundMethodCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
		refundMethodCombo.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(4, 8, 4, 8)));
		refundMethodCombo.setSelectedIndex(0);
		panel.add(refundMethodCombo);
		return panel;
	}

	private JPanel buildInvoiceLinesPanel(List<InvoiceDetail> invoiceDetails) {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.setOpaque(true);
		panel.setBackground(AppColor.BG_LIGHT);
		panel.setAlignmentX(Component.LEFT_ALIGNMENT);
		panel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(8, 12, 8, 12)));

		boolean first = true;
		for (InvoiceDetail d : invoiceDetails) {
			int maxReturnable = Math.max(0, returnableQty.getOrDefault(d.getProductId(), 0));

			if (!first) {
				JPanel sep = new JPanel();
				sep.setOpaque(true);
				sep.setBackground(AppColor.BORDER);
				sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
				sep.setPreferredSize(new Dimension(10, 1));
				panel.add(Box.createVerticalStrut(4));
				panel.add(sep);
				panel.add(Box.createVerticalStrut(4));
			}
			first = false;

			JPanel row = new JPanel(new BorderLayout(12, 0));
			row.setOpaque(false);
			row.setBorder(new EmptyBorder(8, 0, 8, 0));
			row.setAlignmentX(Component.LEFT_ALIGNMENT);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

			JPanel left = new JPanel();
			left.setOpaque(false);
			left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));

			JLabel nameLabel = new JLabel(d.getProductName());
			nameLabel.setFont(AppFont.BODY_BOLD);
			nameLabel.setForeground(maxReturnable > 0 ? AppColor.TEXT_PRIMARY : AppColor.TEXT_MUTED);
			nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

			JLabel metaLabel = new JLabel("Đã bán " + d.getQuantity()
					+ "  ·  " + NumberUtil.formatThousands(d.getUnitPrice().longValue()) + " đ/sp"
					+ (maxReturnable == 0 ? "  ·  đã trả hết" : "  ·  còn trả được " + maxReturnable));
			metaLabel.setFont(AppFont.SMALL_BOLD != null ? AppFont.SMALL_BOLD : new Font("Segoe UI", Font.PLAIN, 11));
			metaLabel.setForeground(AppColor.TEXT_MUTED);
			metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

			left.add(nameLabel);
			left.add(Box.createVerticalStrut(2));
			left.add(metaLabel);

			JSpinner spinner = new JSpinner(new SpinnerNumberModel(0, 0, Math.max(maxReturnable, 0), 1));
			spinner.setPreferredSize(new Dimension(72, 32));
			spinner.setEnabled(maxReturnable > 0);
			returnSpinners.put(d.getProductId(), spinner);

			JPanel spinnerBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
			spinnerBox.setOpaque(false);
			JLabel maxLabel = new JLabel("SL trả");
			maxLabel.setFont(AppFont.SMALL_BOLD);
			maxLabel.setForeground(AppColor.TEXT_MUTED);
			spinnerBox.add(maxLabel);
			spinnerBox.add(spinner);

			row.add(left, BorderLayout.CENTER);
			row.add(spinnerBox, BorderLayout.EAST);
			panel.add(row);
		}
		return panel;
	}

	private JPanel buildExchangeSection() {
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
		section.setOpaque(false);

		section.add(sectionLabel("Hàng đổi mới giao cho khách"));
		section.add(Box.createVerticalStrut(8));

		List<Product> activeProducts = productDAO.findAllActive();

		JPanel pickerCard = new JPanel(new BorderLayout(10, 0));
		pickerCard.setOpaque(true);
		pickerCard.setBackground(AppColor.BG_LIGHT);
		pickerCard.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(10, 12, 10, 12)));
		pickerCard.setAlignmentX(Component.LEFT_ALIGNMENT);
		pickerCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

		productCombo = new JComboBox<>(activeProducts.toArray(new Product[0]));
		productCombo.setFont(AppFont.BODY);
		productCombo.setBackground(AppColor.WHITE);
		productCombo.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = new JLabel(value == null ? ""
					: value.getProductName() + "  ("
					+ NumberUtil.formatThousands(value.getSellPrice().longValue())
					+ " đ, còn " + value.getStock() + ")");
			label.setOpaque(true);
			label.setBackground(isSelected ? AppColor.ACCENT_BG_SOFT : AppColor.WHITE);
			label.setForeground(AppColor.TEXT_PRIMARY);
			label.setFont(AppFont.BODY);
			label.setBorder(new EmptyBorder(6, 8, 6, 8));
			return label;
		});

		exchangeQtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
		exchangeQtySpinner.setPreferredSize(new Dimension(72, 32));

		JButton addButton = pillButton("Thêm", AppColor.ACCENT_BG_SOFT, AppColor.ACCENT, false);
		addButton.addActionListener(e -> addExchangeLine());

		JPanel rightBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		rightBox.setOpaque(false);
		JLabel qtyLbl = new JLabel("SL");
		qtyLbl.setFont(AppFont.SMALL_BOLD);
		qtyLbl.setForeground(AppColor.TEXT_MUTED);
		rightBox.add(qtyLbl);
		rightBox.add(exchangeQtySpinner);
		rightBox.add(addButton);

		pickerCard.add(productCombo, BorderLayout.CENTER);
		pickerCard.add(rightBox, BorderLayout.EAST);
		section.add(pickerCard);
		section.add(Box.createVerticalStrut(10));

		exchangeListPanel = new JPanel();
		exchangeListPanel.setLayout(new BoxLayout(exchangeListPanel, BoxLayout.Y_AXIS));
		exchangeListPanel.setOpaque(true);
		exchangeListPanel.setBackground(AppColor.WHITE);
		exchangeListPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
		exchangeListPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
				new EmptyBorder(10, 12, 10, 12)));
		section.add(exchangeListPanel);
		refreshExchangeList();

		return section;
	}

	private void addExchangeLine() {
		Product selected = (Product) productCombo.getSelectedItem();
		if (selected == null) return;
		int qty = (int) exchangeQtySpinner.getValue();
		if (qty > selected.getStock()) {
			BaseDialog.error(this, "Không đủ tồn kho",
					"Sản phẩm \"" + selected.getProductName() + "\" chỉ còn " + selected.getStock() + " trong kho.");
			return;
		}
		ReturnExchangeDetail line = new ReturnExchangeDetail(
				selected.getProductId(), qty, ReturnExchangeDetail.DIRECTION_OUT, selected.getSellPrice());
		line.setProductName(selected.getProductName());
		exchangeOutLines.add(line);
		refreshExchangeList();
		exchangeQtySpinner.setValue(1);
	}

	private void refreshExchangeList() {
		exchangeListPanel.removeAll();
		if (exchangeOutLines.isEmpty()) {
			JLabel empty = new JLabel("Chưa thêm sản phẩm đổi nào.");
			empty.setFont(AppFont.BODY);
			empty.setForeground(AppColor.TEXT_MUTED);
			empty.setAlignmentX(Component.LEFT_ALIGNMENT);
			exchangeListPanel.add(empty);
		} else {
			for (int i = 0; i < exchangeOutLines.size(); i++) {
				ReturnExchangeDetail line = exchangeOutLines.get(i);
				int index = i;

				JPanel row = new JPanel(new BorderLayout(10, 0));
				row.setOpaque(false);
				row.setBorder(new EmptyBorder(6, 0, 6, 0));
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

				JLabel label = new JLabel(line.getProductName() + "  × " + line.getQuantity()
						+ "  ·  " + NumberUtil.formatThousands(line.getLineTotal().longValue()) + " đ");
				label.setFont(AppFont.BODY);
				label.setForeground(AppColor.TEXT_PRIMARY);

				JButton removeButton = new JButton("Xóa");
				removeButton.setFont(AppFont.SMALL_BOLD);
				removeButton.setFocusPainted(false);
				removeButton.setForeground(AppColor.ERROR);
				removeButton.setBackground(AppColor.WHITE);
				removeButton.setBorderPainted(false);
				removeButton.setContentAreaFilled(false);
				removeButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
				removeButton.addActionListener(e -> {
					exchangeOutLines.remove(index);
					refreshExchangeList();
				});

				row.add(label, BorderLayout.CENTER);
				row.add(removeButton, BorderLayout.EAST);
				exchangeListPanel.add(row);
			}
		}
		exchangeListPanel.revalidate();
		exchangeListPanel.repaint();
	}

	// ---------------------------------------------------------------
	// Footer
	// ---------------------------------------------------------------

	private JPanel buildFooter() {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		footer.setBackground(AppColor.BG_LIGHT);
		footer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
				new EmptyBorder(12, 24, 12, 24)));

		JButton cancelButton = pillButton("Hủy", AppColor.BORDER, AppColor.TEXT_PRIMARY, false);
		cancelButton.addActionListener(e -> dispose());
		footer.add(cancelButton);

		JButton submitButton = pillButton("Tạo yêu cầu", AppColor.ACCENT, Color.WHITE, true);
		submitButton.addActionListener(e -> handleSubmit());
		footer.add(submitButton);

		getRootPane().setDefaultButton(submitButton);
		return footer;
	}

	/** Nút bo góc nhẹ, hover tối màu — cùng tinh thần BaseDialog. */
	private JButton pillButton(String text, Color bg, Color fg, boolean primary) {
		JButton btn = new JButton(text) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(getModel().isPressed() ? bg.darker()
						: getModel().isRollover() ? (primary ? bg.brighter() : bg.darker()) : bg);
				g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
				g2.dispose();
				super.paintComponent(g);
			}
		};
		btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
		btn.setForeground(fg);
		btn.setFocusPainted(false);
		btn.setContentAreaFilled(false);
		btn.setBorderPainted(false);
		btn.setOpaque(false);
		btn.setBorder(new EmptyBorder(10, 20, 10, 20));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		btn.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseEntered(MouseEvent e) { btn.repaint(); }
			@Override
			public void mouseExited(MouseEvent e) { btn.repaint(); }
		});
		return btn;
	}

	private String selectedRefundMethod() {
		return switch (refundMethodCombo.getSelectedIndex()) {
			case 1 -> ReturnExchange.REFUND_BANK_TRANSFER;
			case 2 -> ReturnExchange.REFUND_CARD;
			case 3 -> ReturnExchange.REFUND_PAYPAL;
			default -> ReturnExchange.REFUND_CASH;
		};
	}

	private void handleSubmit() {
		boolean exchange = typeExchange.isSelected();
		String reason = reasonArea.getText();
		if (reason == null || reason.isBlank()) {
			BaseDialog.error(this, "Thiếu lý do", "Vui lòng nhập lý do đổi/trả hàng.");
			return;
		}

		List<ReturnExchangeDetail> details = new ArrayList<>();
		for (Map.Entry<Integer, JSpinner> entry : returnSpinners.entrySet()) {
			int qty = (int) entry.getValue().getValue();
			if (qty <= 0) continue;
			InvoiceDetail line = invoiceLinesByProduct.get(entry.getKey());
			ReturnExchangeDetail d = new ReturnExchangeDetail(entry.getKey(), qty,
					ReturnExchangeDetail.DIRECTION_IN, line.getUnitPrice());
			d.setProductName(line.getProductName());
			details.add(d);
		}
		if (exchange) {
			details.addAll(exchangeOutLines);
		}

		if (details.isEmpty()) {
			BaseDialog.error(this, "Chưa chọn sản phẩm",
					"Vui lòng nhập số lượng sản phẩm khách trả lại (và/hoặc hàng đổi nếu chọn \"Đổi hàng\").");
			return;
		}

		ReturnExchange header = new ReturnExchange();
		header.setInvoiceId(invoice.getInvoiceId());
		header.setType(exchange ? ReturnExchange.TYPE_EXCHANGE : ReturnExchange.TYPE_RETURN);
		header.setReason(reason.trim());

		if (exchange) {
			header.setRefundMethod(null);
			header.setRefundStatus(ReturnExchange.REFUND_STATUS_NONE);
		} else {
			header.setRefundMethod(selectedRefundMethod());
			header.setRefundStatus(ReturnExchange.REFUND_STATUS_PENDING);
		}

		header.setCreatedBy(AuthService.getInstance().getCurrentUser().getUserId());

		String error = returnExchangeDAO.createReturnExchange(header, details);
		if (error != null) {
			BaseDialog.error(this, "Không thể tạo yêu cầu", error);
			return;
		}

		created = true;
		String message = header.isRequiresApproval()
				? "Đã tạo yêu cầu đổi/trả cho hóa đơn " + invoice.getInvoiceCode()
						+ ". Giá trị lớn nên cần Quản lý bán hàng duyệt trước khi cập nhật kho."
				: "Đã tạo và xử lý xong yêu cầu đổi/trả cho hóa đơn " + invoice.getInvoiceCode() + ".";
		// Anchor vào owner (Frame), không dùng this — tránh toast bị dispose theo dialog.
		BaseDialog.success(getOwner(), "Thành công", message);
		dispose();
	}
}
