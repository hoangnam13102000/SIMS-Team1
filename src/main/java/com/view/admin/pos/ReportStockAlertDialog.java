package com.view.admin.pos;

import com.components.AppAlert;
import com.model.Product;
import com.theme.AppColor;
import com.theme.AppFont;
import com.service.StockAlertService;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;

/**
 * Dialog NV/QL bán hàng báo cáo hết hàng / sắp hết hàng thủ công tới Quản lý
 * kho (bảng StockAlerts, ReportedBy = user hiện tại).
 */
public class ReportStockAlertDialog extends JDialog {

	private final Product product;
	private final StockAlertService stockAlertService = new StockAlertService();
	private JTextArea noteArea;
	private Runnable onSuccess;

	public ReportStockAlertDialog(Frame owner, Product product) {
		super(owner, "Báo cáo tồn kho", Dialog.ModalityType.APPLICATION_MODAL);

		this.product = product;

		setSize(480, 420);
		setMinimumSize(new Dimension(420, 360));
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setLayout(new BorderLayout());
		getContentPane().setBackground(AppColor.WHITE);

		add(buildHeader(), BorderLayout.NORTH);
		add(buildBody(), BorderLayout.CENTER);
		add(buildFooter(), BorderLayout.SOUTH);

		getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
				JComponent.WHEN_IN_FOCUSED_WINDOW);

		setLocationRelativeTo(owner);
	}

	public ReportStockAlertDialog onSuccess(Runnable callback) {
		this.onSuccess = callback;
		return this;
	}

	private String resolveAlertType() {
		return product.getStock() <= 0 ? "OUT_OF_STOCK" : "LOW_STOCK";
	}

	private JPanel buildHeader() {
		JPanel header = new JPanel(new BorderLayout(12, 0));
		header.setBackground(AppColor.WHITE);
		header.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER), new EmptyBorder(16, 20, 16, 20)));

		FontIcon icon = FontIcon.of(FontAwesomeSolid.BELL, 18);
		icon.setIconColor(AppColor.WARNING);
		JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
			@Override
			protected void paintComponent(Graphics g) {
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g2.setColor(AppColor.WARNING_BG);
				g2.fillOval(0, 0, getWidth(), getHeight());
				g2.dispose();
				super.paintComponent(g);
			}
		};
		iconBadge.setPreferredSize(new Dimension(40, 40));

		JPanel titles = new JPanel();
		titles.setOpaque(false);
		titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

		JLabel title = new JLabel("Báo cáo tồn kho thủ công");
		title.setFont(AppFont.DIALOG_TITLE);
		title.setForeground(AppColor.TEXT_PRIMARY);
		title.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel sub = new JLabel("Gửi thông báo tới Quản lý kho để bổ sung hàng");
		sub.setFont(AppFont.BODY);
		sub.setForeground(AppColor.TEXT_MUTED);
		sub.setAlignmentX(Component.LEFT_ALIGNMENT);

		titles.add(title);
		titles.add(Box.createVerticalStrut(2));
		titles.add(sub);

		header.add(iconBadge, BorderLayout.WEST);
		header.add(titles, BorderLayout.CENTER);
		return header;
	}

	private JPanel buildBody() {
		JPanel body = new JPanel();
		body.setOpaque(false);
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(new EmptyBorder(16, 20, 8, 20));

		JPanel info = new JPanel(new BorderLayout(10, 0));
		info.setOpaque(false);
		info.setAlignmentX(Component.LEFT_ALIGNMENT);
		info.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
		info.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppColor.BORDER),
				new EmptyBorder(10, 12, 10, 12)));

		JPanel textBox = new JPanel();
		textBox.setOpaque(false);
		textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));

		JLabel name = new JLabel(product.getProductName());
		name.setFont(AppFont.BODY_BOLD);
		name.setForeground(AppColor.TEXT_PRIMARY);
		name.setAlignmentX(Component.LEFT_ALIGNMENT);

		String code = product.getProductCode() != null ? product.getProductCode() : "";
		JLabel meta = new JLabel(
				code + "  ·  Tồn hiện tại: " + product.getStock() + "  ·  Tối thiểu: " + product.getMinStock());
		meta.setFont(AppFont.SMALL);
		meta.setForeground(AppColor.TEXT_MUTED);
		meta.setAlignmentX(Component.LEFT_ALIGNMENT);

		textBox.add(name);
		textBox.add(Box.createVerticalStrut(4));
		textBox.add(meta);
		info.add(textBox, BorderLayout.CENTER);

		String typeLabel = product.getStock() <= 0 ? "Hết hàng" : "Sắp hết hàng";
		Color typeColor = product.getStock() <= 0 ? AppColor.ERROR : AppColor.WARNING;
		Color typeBg = product.getStock() <= 0 ? AppColor.ERROR_BG : AppColor.WARNING_BG;
		JLabel typeChip = new JLabel(typeLabel);
		typeChip.setFont(AppFont.SMALL_BOLD);
		typeChip.setForeground(typeColor);
		typeChip.setOpaque(true);
		typeChip.setBackground(typeBg);
		typeChip.setBorder(new EmptyBorder(4, 10, 4, 10));
		info.add(typeChip, BorderLayout.EAST);

		body.add(info);
		body.add(Box.createVerticalStrut(14));

		JLabel noteLabel = new JLabel("Ghi chú cho kho (tuỳ chọn)");
		noteLabel.setFont(AppFont.SMALL_BOLD);
		noteLabel.setForeground(AppColor.TEXT_MUTED);
		noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(noteLabel);
		body.add(Box.createVerticalStrut(6));

		noteArea = new JTextArea(4, 20);
		noteArea.setFont(AppFont.BODY);
		noteArea.setLineWrap(true);
		noteArea.setWrapStyleWord(true);
		noteArea.setBorder(new EmptyBorder(8, 10, 8, 10));
		noteArea.setBackground(AppColor.BG_LIGHT);
		noteArea.setForeground(AppColor.TEXT_PRIMARY);
		if (product.getStock() <= 0) {
			noteArea.setText("Kệ trống / khách hỏi mua nhưng hết hàng.");
		} else {
			noteArea.setText("Tồn thấp, cần nhập bổ sung sớm.");
		}

		JScrollPane noteScroll = new JScrollPane(noteArea);
		noteScroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
		noteScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		noteScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
		body.add(noteScroll);

		body.add(Box.createVerticalStrut(10));
		JLabel hint = new JLabel(
				"<html>Nếu sản phẩm đã có cảnh báo đang xử lý, hệ thống sẽ không tạo thêm bản ghi trùng.</html>");
		hint.setFont(AppFont.SMALL);
		hint.setForeground(AppColor.TEXT_MUTED);
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		body.add(hint);

		return body;
	}

	private JPanel buildFooter() {
		JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		footer.setBackground(AppColor.BG_LIGHT);
		footer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER), new EmptyBorder(12, 20, 12, 20)));

		JButton cancel = new JButton("Hủy");
		cancel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
		cancel.setFocusPainted(false);
		cancel.setBackground(AppColor.WHITE);
		cancel.setForeground(AppColor.TEXT_PRIMARY);
		cancel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(AppColor.BORDER),
				new EmptyBorder(7, 16, 7, 16)));
		cancel.addActionListener(e -> dispose());

		JButton send = new JButton("Gửi báo cáo");
		send.setFont(new Font("Segoe UI", Font.BOLD, 13));
		send.setFocusPainted(false);
		send.setBackground(AppColor.WARNING);
		send.setForeground(Color.WHITE);
		send.setBorder(new EmptyBorder(8, 18, 8, 18));
		send.addActionListener(e -> submit());

		footer.add(cancel);
		footer.add(send);
		getRootPane().setDefaultButton(send);
		return footer;
	}

	private void submit() {
		if (stockAlertService.hasActiveAlert(product.getProductId())) {
			AppAlert.warning(this, "Đã có cảnh báo đang xử lý", "Sản phẩm \"" + product.getProductName()
					+ "\" đã có cảnh báo tồn kho chưa xử lý xong. Không cần báo lại.");
			dispose();
			return;
		}

		String note = noteArea.getText() != null ? noteArea.getText().trim() : "";

		boolean ok = stockAlertService.reportStockAlert(product, note);
		if (ok) {
			AppAlert.success(this, "Đã gửi báo cáo",
					"Quản lý kho sẽ nhận thông báo về \"" + product.getProductName() + "\".");
			dispose();
			if (onSuccess != null)
				onSuccess.run();
		} else {
			AppAlert.error(this, "Không gửi được",
					"Không thể tạo cảnh báo. Có thể đã có cảnh báo đang xử lý hoặc lỗi hệ thống.");
		}
	}
}