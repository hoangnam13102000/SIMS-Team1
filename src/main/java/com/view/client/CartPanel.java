package com.view.client;

import com.model.CartItem;
import com.service.CartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Trang gio hang day du (mo tu icon gio hang / nut "Xem gio hang" o dropdown).
 * Tham khao myShop CartPanel, dung Product thay Phone.
 */
public class CartPanel extends JPanel {

    private final JPanel listPanel;
    private final JLabel totalLabel;
    private final JButton checkoutButton;
    private Runnable onCheckoutSuccess;

    public CartPanel() {
        setLayout(new BorderLayout(0, 16));
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(24, 28, 24, 28));

        JLabel title = new JLabel("Giỏ hàng của bạn");
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);

        listPanel = new JPanel();
        listPanel.setOpaque(false);
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        totalLabel = new JLabel();
        totalLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        totalLabel.setForeground(AppColor.TEXT_PRIMARY);

        checkoutButton = new JButton("Thanh toán");
        checkoutButton.setFocusPainted(false);
        checkoutButton.setBackground(AppColor.ACCENT_HOVER);
        checkoutButton.setForeground(Color.WHITE);
        checkoutButton.setFont(AppFont.BODY_BOLD);
        checkoutButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        checkoutButton.setBorder(new EmptyBorder(10, 20, 10, 20));
        checkoutButton.addActionListener(e -> openCheckout());

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 0, 0, 0));
        footer.add(totalLabel, BorderLayout.WEST);
        footer.add(checkoutButton, BorderLayout.EAST);

        add(title, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        loadCart();
    }

    public void onCheckoutSuccess(Runnable listener) {
        this.onCheckoutSuccess = listener;
    }

    /** Goi lai moi khi mo trang gio hang de dong bo UI. */
    public void loadCart() {
        listPanel.removeAll();
        java.util.List<CartItem> items = CartService.getInstance().getItems();

        if (items.isEmpty()) {
            JLabel empty = new JLabel("Giỏ hàng đang trống. Hãy thêm sản phẩm từ trang chủ.");
            empty.setFont(AppFont.BODY);
            empty.setForeground(AppColor.TEXT_MUTED);
            empty.setBorder(new EmptyBorder(40, 0, 40, 0));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            listPanel.add(empty);
            checkoutButton.setEnabled(false);
        } else {
            checkoutButton.setEnabled(true);
            for (CartItem item : items) {
                listPanel.add(buildRow(item));
                listPanel.add(Box.createVerticalStrut(10));
            }
        }

        totalLabel.setText("Tổng: " + NumberUtil.formatThousands(CartService.getInstance().getTotal()) + " đ");
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel buildRow(CartItem item) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(true);
        row.setBackground(AppColor.WHITE);
        row.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
            new EmptyBorder(12, 14, 12, 14)
        ));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel name = new JLabel(item.getProduct().getProductName());
        name.setFont(AppFont.BODY_BOLD);
        name.setForeground(AppColor.TEXT_PRIMARY);

        JLabel meta = new JLabel(NumberUtil.formatThousands(
                item.getProduct().getSellPrice() == null ? 0 : item.getProduct().getSellPrice().longValue())
                + " đ  ·  " + (item.getProduct().getCategoryName() == null ? "" : item.getProduct().getCategoryName()));
        meta.setFont(AppFont.SMALL);
        meta.setForeground(AppColor.TEXT_MUTED);

        JPanel left = new JPanel();
        left.setOpaque(false);
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(name);
        left.add(Box.createVerticalStrut(4));
        left.add(meta);

        SpinnerNumberModel model = new SpinnerNumberModel(
                item.getQuantity(), 1, Math.max(1, item.getProduct().getStock()), 1);
        JSpinner quantitySpinner = new JSpinner(model);
        quantitySpinner.setPreferredSize(new Dimension(64, 30));
        quantitySpinner.addChangeListener(e -> {
            CartService.getInstance().updateQuantity(
                    item.getProduct().getProductId(), (int) quantitySpinner.getValue());
            loadCart();
        });

        JLabel subtotal = new JLabel(NumberUtil.formatThousands(item.getSubtotal()) + " đ");
        subtotal.setFont(AppFont.BODY_BOLD);
        subtotal.setForeground(AppColor.ACCENT_HOVER);

        JButton removeButton = new JButton("Xóa");
        removeButton.setForeground(AppColor.ERROR);
        removeButton.setBorderPainted(false);
        removeButton.setContentAreaFilled(false);
        removeButton.setFocusPainted(false);
        removeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        removeButton.addActionListener(e -> {
            CartService.getInstance().removeItem(item.getProduct().getProductId());
            loadCart();
        });

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        right.setOpaque(false);
        right.add(quantitySpinner);
        right.add(subtotal);
        right.add(removeButton);

        row.add(left, BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    private void openCheckout() {
        // SIMS chua co CheckoutDialog day du nhu myShop - thong bao tam.
        JOptionPane.showMessageDialog(this,
                "Chức năng thanh toán sẽ được bổ sung sau.\nTổng hiện tại: "
                        + NumberUtil.formatThousands(CartService.getInstance().getTotal()) + " đ",
                "Thanh toán",
                JOptionPane.INFORMATION_MESSAGE);
        if (onCheckoutSuccess != null) onCheckoutSuccess.run();
    }
}
