package com.view.client;

import com.dao.OrderDAO;
import com.model.Order;
import com.model.User;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;
import com.components.BaseDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

public class OrdersPanel extends JPanel {

    private final OrderDAO orderDAO = new OrderDAO();

    private final JTextField searchField = new JTextField();

    private final JComboBox<String> statusFilter =
            new JComboBox<>(new String[] {
                    "Tất cả trạng thái",
                    "Chờ xác nhận",
                    "Đã xác nhận",
                    "Đang giao",
                    "Hoàn thành",
                    "Đã hủy"
            });

    private final JLabel resultLabel =
            new JLabel("0 đơn hàng");

    private final JPanel ordersContainer =
            new JPanel();

    private List<Order> allOrders =
            new ArrayList<>();

    private final DateTimeFormatter dateFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public OrdersPanel() {

        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(28, 30, 28, 30));

        add(buildHeader(), BorderLayout.NORTH);

        ordersContainer.setLayout(
                new BoxLayout(ordersContainer, BoxLayout.Y_AXIS)
        );

        ordersContainer.setBackground(AppColor.PAGE_BG);

        JScrollPane scrollPane =
                new JScrollPane(ordersContainer);

        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppColor.PAGE_BG);
        scrollPane.getVerticalScrollBar().setUnitIncrement(18);

        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel buildHeader() {

        JPanel wrapper = new JPanel();
        wrapper.setLayout(
                new BoxLayout(wrapper, BoxLayout.Y_AXIS)
        );

        wrapper.setOpaque(false);
        wrapper.setBorder(
                new EmptyBorder(0, 0, 20, 0)
        );

        JLabel title =
                new JLabel("Đơn hàng của tôi");

        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(
                "Theo dõi, tra cứu và quản lý "
              + "các đơn hàng online của bạn"
        );

        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_SECONDARY);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel toolbar =
                new JPanel(new BorderLayout(12, 0));

        toolbar.setOpaque(false);
        toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);
        toolbar.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 42)
        );

        searchField.setFont(AppFont.FIELD);
        searchField.putClientProperty(
                "JTextField.placeholderText",
                "Tìm theo mã đơn, người nhận, số điện thoại..."
        );

        searchField.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                AppColor.FIELD_BORDER
                        ),
                        new EmptyBorder(9, 12, 9, 12)
                )
        );

        statusFilter.setFont(AppFont.FIELD);
        statusFilter.setPreferredSize(
                new Dimension(190, 40)
        );

        JButton refreshButton =
                createButton("Làm mới", AppColor.ACCENT);

        refreshButton.addActionListener(
                event -> loadOrders()
        );

        JPanel rightToolbar =
                new JPanel(new FlowLayout(
                        FlowLayout.RIGHT, 10, 0
                ));

        rightToolbar.setOpaque(false);
        rightToolbar.add(resultLabel);
        rightToolbar.add(statusFilter);
        rightToolbar.add(refreshButton);

        toolbar.add(searchField, BorderLayout.CENTER);
        toolbar.add(rightToolbar, BorderLayout.EAST);

        wrapper.add(title);
        wrapper.add(Box.createVerticalStrut(5));
        wrapper.add(subtitle);
        wrapper.add(Box.createVerticalStrut(18));
        wrapper.add(toolbar);

        searchField.getDocument().addDocumentListener(
                new DocumentListener() {

                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        applyFilters();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        applyFilters();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        applyFilters();
                    }
                }
        );

        statusFilter.addActionListener(
                event -> applyFilters()
        );

        return wrapper;
    }

    public void loadOrders() {

        User currentUser =
                AuthService.getInstance().getCurrentUser();

        if (currentUser == null) {
            showMessage(
                    "Bạn cần đăng nhập để xem đơn hàng."
            );
            return;
        }

        showMessage("Đang tải đơn hàng...");

        int customerId = currentUser.getUserId();

        SwingWorker<List<Order>, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected List<Order> doInBackground() {
                        return orderDAO.getOrdersByCustomer(
                                customerId
                        );
                    }

                    @Override
                    protected void done() {

                        try {
                            allOrders = get();
                            applyFilters();

                        } catch (Exception exception) {

                            allOrders = new ArrayList<>();

                            showMessage(
                                    "Không thể tải danh sách đơn hàng."
                            );
                        }
                    }
                };

        worker.execute();
    }

    private void applyFilters() {

        String keyword =
                searchField.getText()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        String selectedStatus =
                (String) statusFilter.getSelectedItem();

        List<Order> filtered =
                new ArrayList<>();

        for (Order order : allOrders) {

            boolean matchesKeyword =
                    contains(order.getOrderCode(), keyword)
                 || contains(order.getReceiverName(), keyword)
                 || contains(order.getReceiverPhone(), keyword)
                 || contains(order.getShippingAddress(), keyword);

            boolean matchesStatus =
                    matchesSelectedStatus(
                            order.getOrderStatus(),
                            selectedStatus
                    );

            if (matchesKeyword && matchesStatus) {
                filtered.add(order);
            }
        }

        renderOrders(filtered);
    }

    private boolean contains(
            String value,
            String keyword) {

        if (keyword.isEmpty()) {
            return true;
        }

        return value != null
                && value.toLowerCase(Locale.ROOT)
                        .contains(keyword);
    }

    private boolean matchesSelectedStatus(
            String databaseStatus,
            String selectedStatus) {

        if ("Tất cả trạng thái".equals(selectedStatus)) {
            return true;
        }

        String expectedStatus = switch (selectedStatus) {
            case "Chờ xác nhận" -> "PENDING";
            case "Đã xác nhận" -> "CONFIRMED";
            case "Đang giao" -> "SHIPPING";
            case "Hoàn thành" -> "COMPLETED";
            case "Đã hủy" -> "CANCELLED";
            default -> "";
        };

        return expectedStatus.equalsIgnoreCase(
                databaseStatus
        );
    }

    private void renderOrders(List<Order> orders) {

        ordersContainer.removeAll();

        resultLabel.setText(
                orders.size() + " đơn hàng"
        );

        if (orders.isEmpty()) {

            showMessage(
                    "Không tìm thấy đơn hàng phù hợp."
            );

            return;
        }

        for (Order order : orders) {

            JPanel card = buildOrderCard(order);

            ordersContainer.add(card);
            ordersContainer.add(
                    Box.createVerticalStrut(14)
            );
        }

        ordersContainer.revalidate();
        ordersContainer.repaint();
    }

    private JPanel buildOrderCard(Order order) {

        JPanel card =
                new JPanel(new BorderLayout(16, 14));

        card.setBackground(AppColor.WHITE);

        card.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                AppColor.BORDER
                        ),
                        new EmptyBorder(18, 20, 18, 20)
                )
        );

        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.setMaximumSize(
                new Dimension(Integer.MAX_VALUE, 225)
        );

        JPanel cardHeader =
                new JPanel(new BorderLayout());

        cardHeader.setOpaque(false);

        JLabel orderCodeLabel =
                new JLabel(order.getOrderCode());

        orderCodeLabel.setFont(AppFont.HEADING_MD);
        orderCodeLabel.setForeground(AppColor.TEXT_TITLE);

        String createdAtText =
                order.getCreatedAt() == null
                        ? ""
                        : order.getCreatedAt()
                                .format(dateFormatter);

        JLabel dateLabel =
                new JLabel(createdAtText);

        dateLabel.setFont(AppFont.SMALL);
        dateLabel.setForeground(AppColor.TEXT_MUTED);

        JPanel titlePanel = new JPanel();
        titlePanel.setOpaque(false);
        titlePanel.setLayout(
                new BoxLayout(titlePanel, BoxLayout.Y_AXIS)
        );

        titlePanel.add(orderCodeLabel);
        titlePanel.add(Box.createVerticalStrut(4));
        titlePanel.add(dateLabel);

        JLabel statusLabel =
                new JLabel(
                        statusText(order.getOrderStatus())
                );

        statusLabel.setFont(AppFont.BODY_BOLD);
        statusLabel.setOpaque(true);
        statusLabel.setForeground(
                statusForeground(order.getOrderStatus())
        );

        statusLabel.setBackground(
                statusBackground(order.getOrderStatus())
        );

        statusLabel.setBorder(
                new EmptyBorder(7, 12, 7, 12)
        );

        cardHeader.add(titlePanel, BorderLayout.WEST);
        cardHeader.add(statusLabel, BorderLayout.EAST);

        JPanel information =
                new JPanel(new GridLayout(2, 2, 16, 10));

        information.setOpaque(false);

        information.add(
                createInfo(
                        "Người nhận",
                        safe(order.getReceiverName())
                )
        );

        information.add(
                createInfo(
                        "Số sản phẩm",
                        order.getItemCount() + " dòng sản phẩm"
                )
        );

        information.add(
                createInfo(
                        "Thanh toán",
                        paymentText(order)
                )
        );

        long total =
                order.getTotalAmount() == null
                        ? 0
                        : order.getTotalAmount().longValue();

        information.add(
                createInfo(
                        "Tổng tiền",
                        NumberUtil.formatThousands(total) + " đ"
                )
        );

        JPanel actions =
                new JPanel(new FlowLayout(
                        FlowLayout.RIGHT, 10, 0
                ));

        actions.setOpaque(false);

        JButton detailButton =
                createButton(
                        "Xem chi tiết",
                        AppColor.INFO
                );

        detailButton.addActionListener(
                event -> showOrderDetails(order)
        );

        actions.add(detailButton);

        if (canCancel(order)) {

            JButton cancelButton =
                    createButton(
                            "Hủy đơn",
                            AppColor.ERROR
                    );

            cancelButton.addActionListener(
                    event -> cancelOrder(
                            order,
                            cancelButton
                    )
            );

            actions.add(cancelButton);
        }

        card.add(cardHeader, BorderLayout.NORTH);
        card.add(information, BorderLayout.CENTER);
        card.add(actions, BorderLayout.SOUTH);

        return card;
    }

    private JPanel createInfo(
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
        valueComponent.setForeground(
                AppColor.TEXT_PRIMARY
        );

        panel.add(labelComponent);
        panel.add(Box.createVerticalStrut(3));
        panel.add(valueComponent);

        return panel;
    }

    private String paymentText(Order order) {

        String method =
                "PAYPAL".equalsIgnoreCase(
                        order.getPaymentMethod()
                )
                        ? "PayPal"
                        : "Thanh toán khi nhận hàng";

        String paymentStatus =
                "PAID".equalsIgnoreCase(
                        order.getPaymentStatus()
                )
                        ? "Đã thanh toán"
                        : "Chưa thanh toán";

        return method + " · " + paymentStatus;
    }

    private String statusText(String status) {

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

    private Color statusForeground(String status) {

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

    private Color statusBackground(String status) {

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

    private JButton createButton(
            String text,
            Color background) {

        JButton button = new JButton(text);

        button.setFont(AppFont.BODY_BOLD);
        button.setForeground(Color.WHITE);
        button.setBackground(background);
        button.setFocusPainted(false);
        button.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        return button;
    }

    private void showMessage(String message) {

        ordersContainer.removeAll();

        JLabel label = new JLabel(message);
        label.setFont(AppFont.BODY);
        label.setForeground(AppColor.TEXT_SECONDARY);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setBorder(
                new EmptyBorder(50, 20, 50, 20)
        );

        ordersContainer.add(label);
        ordersContainer.revalidate();
        ordersContainer.repaint();
    }
    
    private boolean canCancel(Order order) {

        String status = order.getOrderStatus();

        return "PENDING".equalsIgnoreCase(status)
                || "CONFIRMED".equalsIgnoreCase(status);
    }
    
    private void showOrderDetails(Order selectedOrder) {

        User currentUser =
                AuthService.getInstance().getCurrentUser();

        if (currentUser == null) {
            BaseDialog.error(
                    this,
                    "Không thể xem đơn hàng",
                    "Phiên đăng nhập đã hết hạn."
            );
            return;
        }

        setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.WAIT_CURSOR
                )
        );

        SwingWorker<Order, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected Order doInBackground() {

                        return orderDAO
                                .getOrderByIdForCustomer(
                                        selectedOrder.getOrderId(),
                                        currentUser.getUserId()
                                );
                    }

                    @Override
                    protected void done() {

                        setCursor(
                                Cursor.getDefaultCursor()
                        );

                        try {
                            Order fullOrder = get();

                            if (fullOrder == null) {

                                BaseDialog.error(
                                        OrdersPanel.this,
                                        "Không tìm thấy đơn hàng",
                                        "Đơn hàng không tồn tại hoặc "
                                      + "không thuộc tài khoản của bạn."
                                );

                                return;
                            }

                            OrderDetailDialog.showDialog(
                                    OrdersPanel.this,
                                    fullOrder
                            );

                        } catch (Exception exception) {

                            BaseDialog.error(
                                    OrdersPanel.this,
                                    "Lỗi tải dữ liệu",
                                    "Không thể tải chi tiết đơn hàng."
                            );
                        }
                    }
                };

        worker.execute();
    }
    
    private void cancelOrder(
            Order order,
            JButton cancelButton) {

        String reason = BaseDialog.inputText(
                this,
                "Hủy đơn hàng",
                "Nhập lý do hủy đơn "
              + order.getOrderCode() + ":",
                "",
                "Tiếp tục"
        );

        if (reason == null) {
            return;
        }

        boolean confirmed = BaseDialog.confirm(
                this,
                "Xác nhận hủy đơn",
                "Bạn chắc chắn muốn hủy đơn "
              + order.getOrderCode()
              + "?\nThao tác này không thể hoàn tác.",
                "Hủy đơn",
                AppColor.ERROR,
                AppColor.ERROR_HOVER,
                FontAwesomeSolid.EXCLAMATION_TRIANGLE
        );

        if (!confirmed) {
            return;
        }

        User currentUser =
                AuthService.getInstance().getCurrentUser();

        if (currentUser == null) {

            BaseDialog.error(
                    this,
                    "Không thể hủy đơn",
                    "Phiên đăng nhập đã hết hạn."
            );

            return;
        }

        cancelButton.setEnabled(false);
        cancelButton.setText("Đang hủy...");

        SwingWorker<String, Void> worker =
                new SwingWorker<>() {

                    @Override
                    protected String doInBackground() {

                        return orderDAO
                                .cancelOrderByCustomer(
                                        order.getOrderId(),
                                        currentUser.getUserId(),
                                        reason
                                );
                    }

                    @Override
                    protected void done() {

                        cancelButton.setEnabled(true);
                        cancelButton.setText("Hủy đơn");

                        try {
                            String error = get();

                            if (error != null) {

                                BaseDialog.error(
                                        OrdersPanel.this,
                                        "Hủy đơn thất bại",
                                        error
                                );

                                return;
                            }

                            BaseDialog.success(
                                    OrdersPanel.this,
                                    "Hủy đơn thành công",
                                    "Đơn hàng "
                                  + order.getOrderCode()
                                  + " đã được hủy."
                            );

                            loadOrders();

                        } catch (Exception exception) {

                            BaseDialog.error(
                                    OrdersPanel.this,
                                    "Hủy đơn thất bại",
                                    "Có lỗi xảy ra khi hủy đơn hàng."
                            );
                        }
                    }
                };

        worker.execute();
    }

    private String safe(String value) {
        return value == null || value.isBlank()
                ? "—"
                : value;
    }
}
