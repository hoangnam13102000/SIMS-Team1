package com.view.admin.inventory;

import com.dao.PurchaseReceiptDAO;
import com.model.PurchaseReceipt;
import com.model.PurchaseReceiptDetail;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
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
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;

/**
 * Dialog CHI XEM (khong sua/xoa) chi tiet 1 phieu nhap kho: thong tin chung
 * (ma phieu, NCC, nguoi tao, ngay tao, trang thai) + bang cac dong san pham
 * da nhap trong phieu do (moi dong ung voi 1 lo hang duoc sinh ra qua
 * trigger trg_PurchaseReceiptDetails_Insert).
 */
public class PurchaseReceiptDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public PurchaseReceiptDetailDialog(Frame owner, PurchaseReceipt receipt, PurchaseReceiptDAO receiptDAO) {
        super(owner, "Chi tiết phiếu nhập kho", Dialog.ModalityType.APPLICATION_MODAL);
        List<PurchaseReceiptDetail> details = receiptDAO.getDetails(receipt.getReceiptId());

        setSize(700, 560);
        setMinimumSize(new Dimension(560, 420));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(receipt), BorderLayout.NORTH);
        add(buildBody(receipt, details), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------
    // Header: icon + ma phieu + trang thai
    // ---------------------------------------------------------------

    private JPanel buildHeader(PurchaseReceipt receipt) {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        boolean cancelled = receipt.isCancelled();
        FontIcon icon = FontIcon.of(FontAwesomeSolid.FILE_INVOICE, 18);
        icon.setIconColor(cancelled ? AppColor.ERROR : AppColor.ACCENT);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(cancelled ? AppColor.ERROR_BG : AppColor.ACCENT_BG_SOFT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(receipt.getReceiptCode());
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(receipt.getSupplierName()
                + "  \u00b7  " + (cancelled ? "Đã hủy" : "Hoàn tất"));
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(cancelled ? AppColor.ERROR : AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body: thong tin chung (dang luoi) + bang dong san pham
    // ---------------------------------------------------------------

    private JScrollPane buildBody(PurchaseReceipt receipt, List<PurchaseReceiptDetail> details) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        content.add(infoRow("Nhà cung cấp", receipt.getSupplierName()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Người tạo phiếu", receipt.getCreatedByName()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Ngày tạo",
                receipt.getCreatedAt() != null ? receipt.getCreatedAt().format(DATE_TIME_FORMAT) : "-"));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Tổng tiền phiếu",
                NumberUtil.formatThousands(receipt.getTotalAmount().longValue()) + " đ"));

        content.add(Box.createVerticalStrut(18));

        JLabel sectionLabel = new JLabel("Danh sách sản phẩm (" + details.size() + ")");
        sectionLabel.setFont(AppFont.BODY_BOLD);
        sectionLabel.setForeground(AppColor.TEXT_PRIMARY);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sectionLabel);
        content.add(Box.createVerticalStrut(8));

        JTable table = buildDetailTable(details);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        tableScroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        tableScroll.setPreferredSize(new Dimension(640, 220));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));
        content.add(tableScroll);

        JScrollPane outerScroll = new JScrollPane(content);
        outerScroll.setBorder(null);
        outerScroll.getViewport().setBackground(AppColor.WHITE);
        outerScroll.getVerticalScrollBar().setUnitIncrement(16);
        return outerScroll;
    }

    private JPanel infoRow(String label, String value) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setPreferredSize(new Dimension(140, 20));

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueComp.setFont(AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.CENTER);
        return row;
    }

    private JTable buildDetailTable(List<PurchaseReceiptDetail> details) {
        String[] columns = {"Sản phẩm", "Số lô (NCC)", "NSX", "HSD", "SL", "Giá nhập", "Thành tiền"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (PurchaseReceiptDetail d : details) {
            model.addRow(new Object[]{
                    d.getProductName(),
                    emptyDash(d.getLotNumber()),
                    d.getManufactureDate() != null ? d.getManufactureDate().format(DATE_FORMAT) : "-",
                    d.getExpiryDate() != null ? d.getExpiryDate().format(DATE_FORMAT) : "-",
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getImportPrice().longValue()),
                    NumberUtil.formatThousands(d.lineTotal().longValue())
            });
        }

        JTable table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(28);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.getTableHeader().setBackground(AppColor.BG_LIGHT);
        table.getTableHeader().setForeground(AppColor.TEXT_MUTED);
        table.setGridColor(AppColor.BORDER);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        return table;
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    // ---------------------------------------------------------------
    // Footer: nut Dong
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JButton closeButton = new JButton("Đóng");
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeButton.setFocusPainted(false);
        closeButton.setBackground(AppColor.BORDER);
        closeButton.setForeground(AppColor.TEXT_PRIMARY);
        closeButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        closeButton.addActionListener(e -> dispose());
        footer.add(closeButton);

        getRootPane().setDefaultButton(closeButton);
        return footer;
    }
}