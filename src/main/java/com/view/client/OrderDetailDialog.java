package com.view.client;

import com.model.Order;
import com.model.OrderDetail;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

public final class OrderDetailDialog {

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private OrderDetailDialog() {
    }

    public static void showDialog(
            Component parent,
            Order order) {

        Window owner =
                SwingUtilities.getWindowAncestor(parent);

        JDialog dialog = new JDialog(
                owner,
                "Chi tiết đơn hàng",
                Dialog.ModalityType.APPLICATION_MODAL
        );

        dialog.setSize(850, 620);
        dialog.setMinimumSize(new Dimension(700, 520));
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(AppColor.WHITE);

        JPanel content =
                new JPanel(new BorderLayout(0, 18));

        content.setBackground(AppColor.WHITE);
        content.setBorder(
                new EmptyBorder(24, 28, 24, 28)
        );

        content.add(
                buildHeader(order),
                BorderLayout.NORTH
        );

        content.add(
                buildProductTable(order),
                BorderLayout.CENTER
        );

        content.add(
                buildFooter(dialog, order),
                BorderLayout.SOUTH
        );

        dialog.add(content);
        dialog.setVisible(true);
    }

    private static JPanel buildHeader(Order order) {

        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(
                new BoxLayout(wrapper, BoxLayout.Y_AXIS)
        );

        JPanel titleRow =
                new JPanel(new BorderLayout());

        titleRow.setOpaque(false);

        JLabel title =
                new JLabel("Đơn hàng " + order.getOrderCode());

        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);

        JLabel status =
                new JLabel(statusText(order.getOrderStatus()));

        status.setOpaque(true);
        status.setFont(AppFont.BODY_BOLD);
        status.setForeground(
                statusForeground(order.getOrderStatus())
        );
        status.setBackground(
                statusBackground(order.getOrderStatus())
        );
        status.setBorder(
                new EmptyBorder(7, 12, 7, 12)
        );

        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(status, BorderLayout.EAST);

        JPanel information =
                new JPanel(new GridLayout(0, 2, 24, 10));

        information.setOpaque(false);
        information.setBorder(
                new EmptyBorder(20, 0, 10, 0)
        );

        information.add(
                infoItem(
                        "Ngày đặt",
                        order.getCreatedAt() == null
                                ? "—"
                                : order.getCreatedAt()
                                        .format(DATE_FORMAT)
                )
        );

        information.add(
                infoItem(
                        "Người nhận",
                        safe(order.getReceiverName())
                )
        );

        information.add(
                infoItem(
                        "Số điện thoại",
                        safe(order.getReceiverPhone())
                )
        );

        information.add(
                infoItem(
                        "Email",
                        safe(order.getReceiverEmail())
                )
        );

        information.add(
                infoItem(
                        "Địa chỉ giao hàng",
                        safe(order.getShippingAddress())
                )
        );

        information.add(
                infoItem(
                        "Thanh toán",
                        paymentText(order)
                )
        );

        if ("CANCELLED".equalsIgnoreCase(
                order.getOrderStatus())) {

            information.add(
                    infoItem(
                            "Lý do hủy",
                            safe(order.getCancelReason())
                    )
            );
        }

        wrapper.add(titleRow);
        wrapper.add(information);

        return wrapper;
    }

    private static JPanel buildProductTable(Order order) {

        JPanel panel =
                new JPanel(new BorderLayout(0, 10));

        panel.setOpaque(false);

        JLabel title =
                new JLabel("Sản phẩm trong đơn hàng");

        title.setFont(AppFont.HEADING_MD);
        title.setForeground(AppColor.TEXT_TITLE);

        String[] columns = {
                "Mã sản phẩm",
                "Tên sản phẩm",
                "Số lượng",
                "Đơn giá",
                "Thành tiền"
        };

        DefaultTableModel model =
                new DefaultTableModel(columns, 0) {

                    @Override
                    public boolean isCellEditable(
                            int row,
                            int column) {

                        return false;
                    }
                };

        if (order.getDetails() != null) {

            for (OrderDetail detail :
                    order.getDetails()) {

                model.addRow(new Object[] {
                        detail.getProductCodeSnapshot(),
                        detail.getProductNameSnapshot(),
                        detail.getQuantity(),
                        formatMoney(detail.getUnitPrice()),
                        formatMoney(detail.getLineTotal())
                });
            }
        }

        JTable table = new JTable(model);

        table.setFont(AppFont.BODY);
        table.setRowHeight(38);
        table.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION
        );
        table.setShowVerticalLines(false);
        table.setGridColor(AppColor.TABLE_GRID);
        table.setForeground(AppColor.TABLE_ROW_TEXT);
        table.setBackground(AppColor.WHITE);

        table.getTableHeader().setFont(
                AppFont.BODY_BOLD
        );
        table.getTableHeader().setBackground(
                AppColor.TABLE_HEADER_BG
        );
        table.getTableHeader().setForeground(
                Color.WHITE
        );
        table.getTableHeader().setPreferredSize(
                new Dimension(0, 40)
        );

        table.getColumnModel()
                .getColumn(0)
                .setPreferredWidth(110);

        table.getColumnModel()
                .getColumn(1)
                .setPreferredWidth(250);

        table.getColumnModel()
                .getColumn(2)
                .setPreferredWidth(80);

        table.getColumnModel()
                .getColumn(3)
                .setPreferredWidth(120);

        table.getColumnModel()
                .getColumn(4)
                .setPreferredWidth(130);

        JScrollPane scrollPane =
                new JScrollPane(table);

        scrollPane.setBorder(
                BorderFactory.createLineBorder(
                        AppColor.BORDER
                )
        );

        panel.add(title, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private static JPanel buildFooter(
            JDialog dialog,
            Order order) {

        JPanel footer =
                new JPanel(new BorderLayout());

        footer.setOpaque(false);
        footer.setBorder(
                new EmptyBorder(8, 0, 0, 0)
        );

        long total =
                order.getTotalAmount() == null
                        ? 0
                        : order.getTotalAmount().longValue();

        JLabel totalLabel = new JLabel(
                "Tổng thanh toán: "
              + NumberUtil.formatThousands(total)
              + " đ"
        );

        totalLabel.setFont(AppFont.HEADING_MD);
        totalLabel.setForeground(AppColor.SUCCESS);

        JButton closeButton =
                new JButton("Đóng");

        closeButton.setFont(AppFont.BUTTON);
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(AppColor.ACCENT);
        closeButton.setFocusPainted(false);
        closeButton.setPreferredSize(
                new Dimension(110, 40)
        );

        closeButton.addActionListener(
                event -> dialog.dispose()
        );

        footer.add(totalLabel, BorderLayout.WEST);
        footer.add(closeButton, BorderLayout.EAST);

        return footer;
    }

    private static JPanel infoItem(
            String label,
            String value) {

        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(
                new BoxLayout(panel, BoxLayout.Y_AXIS)
        );

        JLabel labelComponent =
                new JLabel(label);

        labelComponent.setFont(AppFont.SMALL);
        labelComponent.setForeground(AppColor.TEXT_MUTED);

        JLabel valueComponent =
                new JLabel(value);

        valueComponent.setFont(AppFont.BODY_BOLD);
        valueComponent.setForeground(AppColor.TEXT_PRIMARY);

        panel.add(labelComponent);
        panel.add(Box.createVerticalStrut(4));
        panel.add(valueComponent);

        return panel;
    }

    private static String paymentText(Order order) {

        String method =
                "PAYPAL".equalsIgnoreCase(
                        order.getPaymentMethod())
                        ? "PayPal"
                        : "Thanh toán khi nhận hàng";

        String paymentStatus =
                switch (safe(order.getPaymentStatus())
                        .toUpperCase()) {

                    case "PAID" -> "Đã thanh toán";
                    case "CANCELLED" -> "Đã hủy";
                    case "REFUND_PENDING" -> "Chờ hoàn tiền";
                    case "REFUNDED" -> "Đã hoàn tiền";
                    default -> "Chưa thanh toán";
                };

        return method + " · " + paymentStatus;
    }

    private static String statusText(String status) {

        if (status == null) {
            return "Không xác định";
        }

        return switch (status.toUpperCase()) {
            case "PENDING" -> "Chờ xác nhận";
            case "CONFIRMED" -> "Đã xác nhận";
            case "SHIPPING" -> "Đang giao";
            case "COMPLETED" -> "Hoàn thành";
            case "CANCELLED" -> "Đã hủy";
            default -> status;
        };
    }

    private static Color statusForeground(
            String status) {

        if ("CANCELLED".equalsIgnoreCase(status)) {
            return AppColor.ERROR;
        }

        if ("COMPLETED".equalsIgnoreCase(status)) {
            return AppColor.SUCCESS;
        }

        if ("SHIPPING".equalsIgnoreCase(status)) {
            return AppColor.INFO;
        }

        return AppColor.WARNING;
    }

    private static Color statusBackground(
            String status) {

        if ("CANCELLED".equalsIgnoreCase(status)) {
            return AppColor.ERROR_BG;
        }

        if ("COMPLETED".equalsIgnoreCase(status)) {
            return AppColor.SUCCESS_BG;
        }

        if ("SHIPPING".equalsIgnoreCase(status)) {
            return AppColor.INFO_BG;
        }

        return AppColor.WARNING_BG;
    }

    private static String formatMoney(
            BigDecimal amount) {

        long value = amount == null
                ? 0
                : amount.longValue();

        return NumberUtil.formatThousands(value) + " đ";
    }

    private static String safe(String value) {

        return value == null || value.isBlank()
                ? "—"
                : value;
    }
}