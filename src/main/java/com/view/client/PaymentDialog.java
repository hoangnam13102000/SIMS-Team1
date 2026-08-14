package com.view.client;

import com.i18n.Lang;
import com.model.CartItem;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import com.utils.NumberUtil;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeBrands;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Dialog "Thanh toán đơn hàng" - hiển thị tóm tắt đơn hàng + cho phép chọn
 * phương thức thanh toán (Tiền mặt khi nhận hàng / PayPal) trước khi xác nhận.
 * <p>
 * Dùng chung style card/border/accent voi phan con lai cua app (xem
 * {@link com.components.BaseDialog}, {@link CartPanel}) thay vi copy nguyen
 * giao dien PayPal that - he thong chua co cong thanh toan that su, day chi
 * la buoc chon phuong thuc truoc khi mo phong dat hang (xem CartPanel#handleCheckout).
 */
public final class PaymentDialog {

    /** Phuong thuc thanh toan nguoi dung chon trong dialog. */
    public enum Method { COD, PAYPAL }

    private static final int WIDTH = 440;
    private static final Color SELECTED_BG = AppColor.ACCENT_BG_SOFT;

    private PaymentDialog() {}

    /**
     * Hien thi dialog. Tra ve phuong thuc da chon neu nguoi dung bam
     * "Xác nhận thanh toán", hoac null neu bam Hủy / dong dialog (ESC, nut X).
     */
    public static Method show(Component parent, List<CartItem> items, long total) {
        Method[] result = {null};
        Method[] selected = {Method.COD};

        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(parent), "",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppColor.WHITE);
        dialog.setResizable(false);
        dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(24, 24, 4, 24));
        body.add(fixedWidth(buildHeader(dialog)));
        body.add(Box.createVerticalStrut(18));
        body.add(fixedWidth(buildOrderSummary(items, total)));
        body.add(Box.createVerticalStrut(20));
        body.add(fixedWidth(buildSectionLabel(Lang.get("payment.method.title"))));
        body.add(Box.createVerticalStrut(10));

        MethodCard codCard = new MethodCard(
                Method.COD, FontAwesomeSolid.MONEY_BILL_WAVE, new Color(220, 252, 231), new Color(22, 163, 74),
                Lang.get("payment.method.cod.title"), Lang.get("payment.method.cod.subtitle"));
        MethodCard paypalCard = new MethodCard(
                Method.PAYPAL, FontAwesomeBrands.PAYPAL, new Color(224, 231, 255), new Color(37, 99, 235),
                Lang.get("payment.method.paypal.title"), Lang.get("payment.method.paypal.subtitle"));

        MethodCard[] allCards = {codCard, paypalCard};
        for (MethodCard card : allCards) {
            card.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    selected[0] = card.method;
                    for (MethodCard c : allCards) c.setSelected(c.method == selected[0]);
                }
            });
        }
        codCard.setSelected(true);

        body.add(fixedWidth(codCard));
        body.add(Box.createVerticalStrut(10));
        body.add(fixedWidth(paypalCard));
        body.add(Box.createVerticalStrut(20));

        dialog.add(body, BorderLayout.CENTER);
        dialog.add(buildFooter(dialog, result, selected), BorderLayout.SOUTH);

        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
        return result[0];
    }

    // ==================== Header: tieu de + nut dong (X) ====================

    private static JPanel buildHeader(JDialog dialog) {
        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel(Lang.get("payment.title"));
        title.setFont(AppFont.HEADING_LG);
        title.setForeground(AppColor.TEXT_TITLE);
        row.add(title, BorderLayout.WEST);

        JButton close = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                FontIcon icon = FontIcon.of(FontAwesomeSolid.TIMES, 16);
                icon.setIconColor(AppColor.TEXT_MUTED);
                int ix = (getWidth() - icon.getIconWidth()) / 2;
                int iy = (getHeight() - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g, ix, iy);
            }
        };
        close.setPreferredSize(new Dimension(28, 28));
        close.setOpaque(false);
        close.setContentAreaFilled(false);
        close.setBorderPainted(false);
        close.setFocusPainted(false);
        close.setCursor(new Cursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> dialog.dispose());
        row.add(close, BorderLayout.EAST);

        return row;
    }

    // ==================== Tom tat don hang ====================

    private static JPanel buildOrderSummary(List<CartItem> items, long total) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.PAGE_BG);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));

        for (CartItem item : items) {
            card.add(summaryRow(
                    item.getProduct().getProductName() + " x" + item.getQuantity(),
                    NumberUtil.formatThousands(item.getSubtotal()) + " đ",
                    AppFont.BODY, AppColor.TEXT_SECONDARY, AppFont.BODY_BOLD, AppColor.TEXT_PRIMARY));
            card.add(Box.createVerticalStrut(8));
        }

        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        card.add(sep);
        card.add(Box.createVerticalStrut(10));

        card.add(summaryRow(
                Lang.get("payment.summary.total"),
                NumberUtil.formatThousands(total) + " đ",
                AppFont.HEADING_MD, AppColor.TEXT_TITLE,
                new Font("Segoe UI", Font.BOLD, 18), AppColor.ACCENT_HOVER));

        return card;
    }

    private static JPanel summaryRow(String label, String value, Font labelFont, Color labelColor,
                                      Font valueFont, Color valueColor) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(labelFont);
        labelComp.setForeground(labelColor);

        JLabel valueComp = new JLabel(value);
        valueComp.setFont(valueFont);
        valueComp.setForeground(valueColor);
        valueComp.setHorizontalAlignment(SwingConstants.RIGHT);

        row.add(labelComp, BorderLayout.WEST);
        row.add(valueComp, BorderLayout.EAST);
        return row;
    }

    private static JLabel buildSectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    // ==================== Footer: Huy + Xac nhan ====================

    private static JPanel buildFooter(JDialog dialog, Method[] result, Method[] selected) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(new EmptyBorder(0, 24, 24, 24));

        JButton cancel = new JButton(Lang.get("payment.cancel")) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.CANCEL_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppConstant.RADIUS_LG, AppConstant.RADIUS_LG);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        cancel.setPreferredSize(new Dimension(100, 46));
        cancel.setFont(AppFont.BODY_BOLD);
        cancel.setForeground(AppColor.TEXT_PRIMARY);
        cancel.setFocusPainted(false);
        cancel.setContentAreaFilled(false);
        cancel.setBorderPainted(false);
        cancel.setOpaque(false);
        cancel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancel.addActionListener(e -> dialog.dispose());

        JButton confirm = new JButton() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppConstant.RADIUS_LG, AppConstant.RADIUS_LG);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        confirm.setText(Lang.get("payment.confirm"));
        confirm.setPreferredSize(new Dimension(WIDTH - 48 - 100 - 12, 46));
        confirm.setFont(new Font("Segoe UI", Font.BOLD, 14));
        confirm.setForeground(Color.WHITE);
        confirm.setFocusPainted(false);
        confirm.setContentAreaFilled(false);
        confirm.setBorderPainted(false);
        confirm.setOpaque(false);
        confirm.setCursor(new Cursor(Cursor.HAND_CURSOR));
        confirm.addActionListener(e -> {
            result[0] = selected[0];
            dialog.dispose();
        });

        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(confirm, BorderLayout.CENTER);

        footer.add(cancel, BorderLayout.WEST);
        footer.add(Box.createHorizontalStrut(12), BorderLayout.CENTER);
        footer.add(right, BorderLayout.EAST);
        return footer;
    }

    /** Ep chieu rong co dinh (khop voi WIDTH cua dialog) khi xep trong BoxLayout.Y_AXIS. */
    private static JComponent fixedWidth(JComponent comp) {
        comp.setAlignmentX(Component.LEFT_ALIGNMENT);
        int height = comp.getPreferredSize().height;
        comp.setPreferredSize(new Dimension(WIDTH - 48, height));
        comp.setMaximumSize(new Dimension(WIDTH - 48, height));
        return comp;
    }

    // ==================== The chon phuong thuc (selectable card) ====================

    /** The chon 1 phuong thuc thanh toan - bam vao bat ky diem nao tren the deu chon duoc. */
    private static final class MethodCard extends JPanel {
        private final Method method;
        private boolean selectedState;
        private final RadioDot radio = new RadioDot();

        MethodCard(Method method, Ikon iconType, Color iconBg, Color iconColor,
                   String title, String subtitle) {
            this.method = method;
            setLayout(new BorderLayout(14, 0));
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(AppColor.BORDER, 1, true),
                    new EmptyBorder(14, 14, 14, 14)));
            setBackground(AppColor.WHITE);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
            setPreferredSize(new Dimension(WIDTH - 48, 74));

            JLabel iconLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(iconBg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                    FontIcon icon = FontIcon.of(iconType, 18);
                    icon.setIconColor(iconColor);
                    int ix = (getWidth() - icon.getIconWidth()) / 2;
                    int iy = (getHeight() - icon.getIconHeight()) / 2;
                    icon.paintIcon(this, g2, ix, iy);
                    g2.dispose();
                }
            };
            iconLabel.setPreferredSize(new Dimension(44, 44));
            add(iconLabel, BorderLayout.WEST);

            JPanel textCol = new JPanel();
            textCol.setOpaque(false);
            textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

            JLabel titleLabel = new JLabel(title);
            titleLabel.setFont(AppFont.BODY_BOLD);
            titleLabel.setForeground(AppColor.TEXT_PRIMARY);
            titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel subtitleLabel = new JLabel(subtitle);
            subtitleLabel.setFont(AppFont.SMALL);
            subtitleLabel.setForeground(AppColor.TEXT_MUTED);
            subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            textCol.add(titleLabel);
            textCol.add(Box.createVerticalStrut(2));
            textCol.add(subtitleLabel);
            add(textCol, BorderLayout.CENTER);

            radio.setPreferredSize(new Dimension(22, 22));
            JPanel radioWrap = new JPanel(new GridBagLayout());
            radioWrap.setOpaque(false);
            radioWrap.add(radio);
            add(radioWrap, BorderLayout.EAST);

            propagateHoverCursor(this);
        }

        private void propagateHoverCursor(Container container) {
            for (Component c : container.getComponents()) {
                c.setCursor(new Cursor(Cursor.HAND_CURSOR));
                if (c instanceof Container) propagateHoverCursor((Container) c);
            }
        }

        void setSelected(boolean value) {
            this.selectedState = value;
            setBackground(value ? SELECTED_BG : AppColor.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    new LineBorder(value ? AppColor.ACCENT_HOVER : AppColor.BORDER, value ? 2 : 1, true),
                    new EmptyBorder(value ? 13 : 14, value ? 13 : 14, value ? 13 : 14, value ? 13 : 14)));
            radio.setSelected(value);
            repaint();
        }
    }

    /** Cham tron kieu radio button - vien khi rong, cham dac accent khi duoc chon. */
    private static final class RadioDot extends JComponent {
        private boolean selected;

        void setSelected(boolean value) {
            this.selected = value;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int size = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            g2.setColor(selected ? AppColor.ACCENT_HOVER : AppColor.BORDER);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(x, y, size, size);

            if (selected) {
                int dotSize = size - 10;
                g2.setColor(AppColor.ACCENT_HOVER);
                g2.fillOval(x + 5, y + 5, dotSize, dotSize);
            }
            g2.dispose();
        }
    }
}