package com.view.admin.pos;

import com.model.InvoicePayment;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Thu thập dữ liệu thanh toán tại quầy trước khi tạo Invoice.
 * Hiện hỗ trợ tiền mặt, thẻ và kết hợp CASH + CARD.
 */
public final class PosPaymentDialog extends JDialog {
    public enum Mode { CASH, CARD, CASH_CARD }

    private final Mode mode;
    private final long total;
    private final JTextField cashAppliedField = new JTextField();
    private final JTextField cashTenderedField = new JTextField();
    private final JTextField cardReferenceField = new JTextField();
    private final JLabel cardAmountLabel = new JLabel("0 đ");
    private final JLabel changeLabel = new JLabel("0 đ");
    private List<InvoicePayment> result;

    private PosPaymentDialog(Window owner, Mode mode, long total) {
        super(owner, title(mode), ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.total = total;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
        pack();
        setMinimumSize(new Dimension(440, getHeight()));
        setLocationRelativeTo(owner);
        updateCalculatedLabels();
    }

    public static List<InvoicePayment> showCash(Component owner, long total) {
        return show(owner, Mode.CASH, total);
    }

    public static List<InvoicePayment> showCard(Component owner, long total) {
        return show(owner, Mode.CARD, total);
    }

    public static List<InvoicePayment> showCashCard(Component owner, long total) {
        return show(owner, Mode.CASH_CARD, total);
    }

    private static List<InvoicePayment> show(Component owner, Mode mode, long total) {
        Window window = owner != null ? SwingUtilities.getWindowAncestor(owner) : null;
        PosPaymentDialog dialog = new PosPaymentDialog(window, mode, total);
        dialog.setVisible(true);
        return dialog.result;
    }

    private static String title(Mode mode) {
        return switch (mode) {
            case CASH -> "Thanh toán tiền mặt";
            case CARD -> "Thanh toán thẻ";
            case CASH_CARD -> "Thanh toán kết hợp";
        };
    }

    private JPanel buildBody() {
        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(AppColor.WHITE);
        body.setBorder(new EmptyBorder(22, 26, 18, 26));

        JLabel totalLabel = new JLabel("Tổng hóa đơn: " + NumberUtil.formatThousands(total) + " đ");
        totalLabel.setFont(AppFont.HEADING_MD);
        totalLabel.setForeground(AppColor.TEXT_PRIMARY);
        totalLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(totalLabel);
        body.add(Box.createVerticalStrut(18));

        if (mode == Mode.CASH_CARD) {
            body.add(fieldRow("Tiền mặt áp dụng", cashAppliedField));
            body.add(Box.createVerticalStrut(10));
            body.add(readOnlyRow("Phần còn lại qua thẻ", cardAmountLabel));
            body.add(Box.createVerticalStrut(10));
        }

        if (mode == Mode.CASH || mode == Mode.CASH_CARD) {
            if (mode == Mode.CASH) cashAppliedField.setText(String.valueOf(total));
            body.add(fieldRow("Khách đưa tiền mặt", cashTenderedField));
            body.add(Box.createVerticalStrut(10));
            body.add(readOnlyRow("Tiền thừa", changeLabel));
            body.add(Box.createVerticalStrut(10));
        }

        if (mode == Mode.CARD || mode == Mode.CASH_CARD) {
            body.add(fieldRow("Mã giao dịch thẻ", cardReferenceField));
            JLabel hint = new JLabel("Mã trên máy POS/biên lai thẻ; bắt buộc để đối soát.");
            hint.setFont(AppFont.SMALL);
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setAlignmentX(Component.LEFT_ALIGNMENT);
            body.add(Box.createVerticalStrut(4));
            body.add(hint);
        }

        javax.swing.event.DocumentListener listener = new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedLabels(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedLabels(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateCalculatedLabels(); }
        };
        cashAppliedField.getDocument().addDocumentListener(listener);
        cashTenderedField.getDocument().addDocumentListener(listener);

        return body;
    }

    private JPanel fieldRow(String label, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        JLabel l = new JLabel(label);
        l.setFont(AppFont.BODY);
        l.setForeground(AppColor.TEXT_SECONDARY);
        l.setPreferredSize(new Dimension(155, 32));
        field.setFont(AppFont.BODY);
        field.setPreferredSize(new Dimension(220, 34));
        row.add(l, BorderLayout.WEST);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private JPanel readOnlyRow(String label, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        JLabel l = new JLabel(label);
        l.setFont(AppFont.BODY);
        l.setForeground(AppColor.TEXT_SECONDARY);
        l.setPreferredSize(new Dimension(155, 32));
        value.setFont(AppFont.BODY_BOLD);
        value.setForeground(AppColor.TEXT_PRIMARY);
        row.add(l, BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        return row;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        footer.setBackground(AppColor.BG_LIGHT);
        JButton cancel = new JButton("Hủy");
        cancel.addActionListener(e -> dispose());
        JButton ok = new JButton("Xác nhận");
        ok.setBackground(AppColor.ACCENT);
        ok.setForeground(Color.WHITE);
        ok.setFocusPainted(false);
        ok.addActionListener(e -> confirm());
        footer.add(cancel);
        footer.add(ok);
        getRootPane().setDefaultButton(ok);
        return footer;
    }

    private void confirm() {
        try {
            List<InvoicePayment> payments = new ArrayList<>();
            if (mode == Mode.CASH) {
                long tendered = parseMoney(cashTenderedField.getText());
                if (tendered < total) throw new IllegalArgumentException("Tiền khách đưa phải từ tổng hóa đơn trở lên.");
                payments.add(InvoicePayment.cash(BigDecimal.valueOf(total), BigDecimal.valueOf(tendered)));
            } else if (mode == Mode.CARD) {
                String ref = requiredCardRef();
                InvoicePayment card = new InvoicePayment(InvoicePayment.METHOD_CARD, BigDecimal.valueOf(total));
                card.setProvider("CARD");
                card.setProviderTransactionId(ref);
                card.setIdempotencyKey("CARD:" + ref);
                payments.add(card);
            } else {
                long cashApplied = parseMoney(cashAppliedField.getText());
                if (cashApplied <= 0 || cashApplied >= total)
                    throw new IllegalArgumentException("Tiền mặt áp dụng phải lớn hơn 0 và nhỏ hơn tổng hóa đơn.");
                long tendered = parseMoney(cashTenderedField.getText());
                if (tendered < cashApplied)
                    throw new IllegalArgumentException("Tiền khách đưa phải từ phần tiền mặt áp dụng trở lên.");
                String ref = requiredCardRef();
                payments.add(InvoicePayment.cash(BigDecimal.valueOf(cashApplied), BigDecimal.valueOf(tendered)));
                InvoicePayment card = new InvoicePayment(InvoicePayment.METHOD_CARD,
                        BigDecimal.valueOf(total - cashApplied));
                card.setProvider("CARD");
                card.setProviderTransactionId(ref);
                card.setIdempotencyKey("CARD:" + ref);
                payments.add(card);
            }
            this.result = payments;
            dispose();
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Dữ liệu thanh toán chưa hợp lệ",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private String requiredCardRef() {
        String ref = cardReferenceField.getText() != null ? cardReferenceField.getText().trim() : "";
        if (ref.isEmpty()) throw new IllegalArgumentException("Vui lòng nhập mã giao dịch thẻ.");
        if (ref.length() > 120) throw new IllegalArgumentException("Mã giao dịch thẻ quá dài.");
        return ref;
    }

    private long parseMoney(String raw) {
        if (raw == null) return 0;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) return 0;
        try { return Long.parseLong(digits); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("Số tiền không hợp lệ."); }
    }

    private void updateCalculatedLabels() {
        try {
            long applied = mode == Mode.CASH ? total : parseMoney(cashAppliedField.getText());
            if (mode == Mode.CASH_CARD) {
                long card = Math.max(0, total - applied);
                cardAmountLabel.setText(NumberUtil.formatThousands(card) + " đ");
            }
            if (mode == Mode.CASH || mode == Mode.CASH_CARD) {
                long tendered = parseMoney(cashTenderedField.getText());
                long change = Math.max(0, tendered - applied);
                changeLabel.setText(NumberUtil.formatThousands(change) + " đ");
            }
        } catch (Exception ignore) {
            cardAmountLabel.setText("0 đ");
            changeLabel.setText("0 đ");
        }
    }
}
