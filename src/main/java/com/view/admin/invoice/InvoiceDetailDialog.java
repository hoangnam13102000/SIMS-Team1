package com.view.admin.invoice;

import com.components.BaseDialog;
import com.dao.InvoiceDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
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
 * Dialog xem chi tiet 1 hoa don ban hang: thong tin chung (ma hoa don, khach
 * hang, nguoi tao, ngay tao, PT thanh toan, VAT, trang thai) + bang cac dong
 * san pham da ban. Neu hoa don con ACTIVE va nguoi dung co quyen
 * INVOICE_CANCEL thi hien them nut "Hủy hóa đơn" (yeu cau nhap ly do) -
 * dieu kien duoc phep huy that su (cung ngay, ca dang mo...) van do trigger
 * trg_Invoices_CancelSameDayOnly duoi DB quyet dinh, dialog chi hien thi lai
 * dung thong diep loi neu bi tu choi.
 */
public class InvoiceDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final InvoiceDAO invoiceDAO;
    private Invoice invoice;

    public InvoiceDetailDialog(Frame owner, Invoice invoice, InvoiceDAO invoiceDAO) {
        super(owner, "Chi tiết hóa đơn", Dialog.ModalityType.APPLICATION_MODAL);
        this.invoice = invoice;
        this.invoiceDAO = invoiceDAO;
        List<InvoiceDetail> details = invoiceDAO.getDetails(invoice.getInvoiceId());

        setSize(700, 620);
        setMinimumSize(new Dimension(560, 460));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(details), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------
    // Header: icon + ma hoa don + trang thai
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        boolean cancelled = invoice.isCancelled();
        FontIcon icon = FontIcon.of(FontAwesomeSolid.RECEIPT, 18);
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

        JLabel titleLabel = new JLabel(invoice.getInvoiceCode());
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(
                (invoice.getCustomerName() != null ? invoice.getCustomerName() : "Khách lẻ")
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

    private JScrollPane buildBody(List<InvoiceDetail> details) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        content.add(infoRow("Khách hàng", invoice.getCustomerName() != null ? invoice.getCustomerName() : "Khách lẻ"));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Người tạo hóa đơn", invoice.getCreatedByName()));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Ngày tạo",
                invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(DATE_TIME_FORMAT) : "-"));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Phương thức thanh toán", InvoicePanel.paymentMethodLabel(invoice.getPaymentMethod())));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Tạm tính", NumberUtil.formatThousands(invoice.getSubTotal().longValue()) + " đ"));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("VAT (" + invoice.getVatRate().stripTrailingZeros().toPlainString() + "%)",
                NumberUtil.formatThousands(invoice.getVatAmount().longValue()) + " đ"));
        content.add(Box.createVerticalStrut(8));
        content.add(infoRow("Tổng tiền hóa đơn",
                NumberUtil.formatThousands(invoice.getTotalAmount().longValue()) + " đ"));

        if (invoice.isCancelled()) {
            content.add(Box.createVerticalStrut(8));
            content.add(infoRow("Lý do hủy", invoice.getCancelReason()));
            content.add(Box.createVerticalStrut(8));
            content.add(infoRow("Thời điểm hủy",
                    invoice.getCancelledAt() != null ? invoice.getCancelledAt().format(DATE_TIME_FORMAT) : "-"));
        }

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
        labelComp.setPreferredSize(new Dimension(160, 20));

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueComp.setFont(AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.CENTER);
        return row;
    }

    private JTable buildDetailTable(List<InvoiceDetail> details) {
        String[] columns = {"Sản phẩm", "SL", "Đơn giá", "Thành tiền"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (InvoiceDetail d : details) {
            model.addRow(new Object[]{
                    d.getProductName(),
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                    NumberUtil.formatThousands(d.getLineTotal().longValue())
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

    // ---------------------------------------------------------------
    // Footer: nut Huy hoa don (neu du dieu kien + du quyen) + nut Dong
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        boolean canCancel = invoice.isCancellableToday()
                && PermissionManager.getInstance().can(AppPermission.INVOICE_CANCEL);

        if (canCancel) {
            JButton cancelButton = new JButton("Hủy hóa đơn");
            cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            cancelButton.setFocusPainted(false);
            cancelButton.setBackground(AppColor.ERROR_BG);
            cancelButton.setForeground(AppColor.ERROR);
            cancelButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            cancelButton.addActionListener(e -> handleCancel());
            footer.add(cancelButton);
        }

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

    private void handleCancel() {
        String reason = BaseDialog.inputText(this, "Hủy hóa đơn",
                "Lý do hủy hóa đơn " + invoice.getInvoiceCode() + ":", "", "Hủy hóa đơn");
        if (reason == null) return; // nguoi dung bam Huy tren dialog nhap ly do

        String error = invoiceDAO.cancelInvoice(invoice.getInvoiceId(), reason);
        if (error != null) {
            BaseDialog.error(this, "Không thể hủy hóa đơn", error);
            return;
        }

        BaseDialog.success(this, "Thành công", "Đã hủy hóa đơn " + invoice.getInvoiceCode() + ".");
        dispose();
    }
}