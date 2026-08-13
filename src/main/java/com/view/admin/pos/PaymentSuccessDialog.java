package com.view.admin.pos;

import com.model.Invoice;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.FileUtil;
import com.utils.NumberUtil;
import com.utils.pdf.InvoicePdfExporter;
import com.dao.InvoiceDAO;
import com.model.InvoiceDetail;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.Desktop;
import java.awt.event.KeyEvent;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialog hien thi sau khi thanh toan hoa don thanh cong.
 * Bao gom thong tin hoa don va nut "In hoa don" de xuat ra file PDF.
 */
public class PaymentSuccessDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Invoice invoice;
    private final InvoiceDAO invoiceDAO;
    private File lastGeneratedPdf;
    private JLabel statusLabel;

    public PaymentSuccessDialog(Frame owner, Invoice invoice, InvoiceDAO invoiceDAO) {
        super(owner, "Thanh toán thành công", Dialog.ModalityType.APPLICATION_MODAL);
        this.invoice = invoice;
        this.invoiceDAO = invoiceDAO;

        setSize(480, 520);
        setMinimumSize(new Dimension(420, 480));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildContent(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    private JPanel buildContent() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(28, 32, 20, 32));

        // Icon thanh cong tron xanh voi hieu ung glow
        JPanel iconPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        iconPanel.setOpaque(false);
        iconPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel circlePanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                g2.setColor(new Color(16, 185, 129, 30));
                g2.fillOval(x - 8, y - 8, size + 16, size + 16);

                g2.setColor(AppColor.SUCCESS);
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(4, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int cx = x + size / 2;
                int cy = y + size / 2;
                int s = size / 4;
                g2.drawLine(cx - s, cy, cx - s / 3, cy + s);
                g2.drawLine(cx - s / 3, cy + s, cx + s, cy - s / 2);

                g2.dispose();
            }
        };
        circlePanel.setOpaque(false);
        circlePanel.setPreferredSize(new Dimension(96, 96));
        circlePanel.setMaximumSize(new Dimension(96, 96));
        iconPanel.add(circlePanel);

        content.add(iconPanel);
        content.add(Box.createVerticalStrut(16));

        JLabel titleLabel = new JLabel("Thanh toán thành công!");
        titleLabel.setFont(AppFont.HEADING_LG);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(4));

        JLabel subtitleLabel = new JLabel("Hóa đơn đã được lập thành công");
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(subtitleLabel);
        content.add(Box.createVerticalStrut(24));

        // The thong tin hoa don
        JPanel infoCard = new JPanel();
        infoCard.setLayout(new BoxLayout(infoCard, BoxLayout.Y_AXIS));
        infoCard.setBackground(AppColor.BG_LIGHT);
        infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 20, 16, 20)
        ));
        infoCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(420, Integer.MAX_VALUE));

        infoCard.add(createInfoRow("Mã hóa đơn", invoice.getInvoiceCode(), true));
        infoCard.add(Box.createVerticalStrut(10));

        String totalStr = NumberUtil.formatThousands(invoice.getTotalAmount().longValue()) + " đ";
        infoCard.add(createInfoRow("Tổng tiền", totalStr, true));
        infoCard.add(Box.createVerticalStrut(10));

        String customer = invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()
                ? invoice.getCustomerName() : "Khách lẻ";
        infoCard.add(createInfoRow("Khách hàng", customer, false));
        infoCard.add(Box.createVerticalStrut(10));

        String dateStr = invoice.getCreatedAt() != null
                ? invoice.getCreatedAt().format(DATE_TIME_FORMAT) : "-";
        infoCard.add(createInfoRow("Thời gian", dateStr, false));

        if (invoice.getPointsEarned() > 0) {
            infoCard.add(Box.createVerticalStrut(10));
            infoCard.add(createInfoRow("Điểm thưởng", "+" + invoice.getPointsEarned() + " điểm", false));
        }

        content.add(infoCard);
        content.add(Box.createVerticalStrut(16));

        statusLabel = new JLabel(" ");
        statusLabel.setFont(AppFont.SMALL);
        statusLabel.setForeground(AppColor.TEXT_MUTED);
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(statusLabel);

        return content;
    }

    private JPanel createInfoRow(String label, String value, boolean highlight) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.BODY);
        labelComp.setForeground(AppColor.TEXT_MUTED);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(highlight ? AppFont.BODY_BOLD : AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);
        return row;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setBackground(AppColor.WHITE);
        buttons.setBorder(new EmptyBorder(0, 16, 24, 16));

        JButton printBtn = new JButton("In hóa đơn PDF");
        printBtn.setFont(AppFont.BUTTON);
        printBtn.setForeground(Color.WHITE);
        printBtn.setBackground(AppColor.ACCENT);
        printBtn.setOpaque(true);
        printBtn.setContentAreaFilled(true);
        printBtn.setFocusPainted(false);
        printBtn.setBorderPainted(false);
        printBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        printBtn.setIcon(FontIcon.of(FontAwesomeSolid.FILE_PDF, 16, Color.WHITE));
        printBtn.setIconTextGap(8);
        printBtn.setPreferredSize(new Dimension(160, 40));
        printBtn.addActionListener(e -> exportAndOpenPdf());

        JButton closeBtn = new JButton("Đóng");
        closeBtn.setFont(AppFont.BUTTON);
        closeBtn.setForeground(AppColor.TEXT_PRIMARY);
        closeBtn.setBackground(AppColor.WHITE);
        closeBtn.setOpaque(true);
        closeBtn.setContentAreaFilled(true);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1));
        closeBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeBtn.setPreferredSize(new Dimension(100, 40));
        closeBtn.addActionListener(e -> dispose());

        buttons.add(printBtn);
        buttons.add(closeBtn);
        return buttons;
    }

    private void exportAndOpenPdf() {
        try {
            statusLabel.setForeground(AppColor.TEXT_MUTED);
            statusLabel.setText("Đang tạo file PDF...");

            List<InvoiceDetail> details = invoiceDAO.getDetails(invoice.getInvoiceId());

            File pdfFile = FileUtil.uniqueTempFile("sims_invoices", "HoaDon_" + invoice.getInvoiceCode(), "pdf");

            InvoicePdfExporter.exportInvoice(invoice, details, pdfFile);
            lastGeneratedPdf = pdfFile;

            statusLabel.setForeground(AppColor.SUCCESS);
            statusLabel.setText("Đã tạo: " + pdfFile.getName());

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(),
                        "In hóa đơn", JOptionPane.INFORMATION_MESSAGE);
            }

        } catch (Exception ex) {
            statusLabel.setForeground(AppColor.ERROR);
            statusLabel.setText("Lỗi: Không thể tạo file PDF");
            JOptionPane.showMessageDialog(this,
                    "Lỗi tạo file PDF: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public File getLastGeneratedPdf() {
        return lastGeneratedPdf;
    }
}