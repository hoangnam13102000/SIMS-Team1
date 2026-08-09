package com.view.client;

import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.EmptyState;
import com.dao.OrderDAO;
import com.model.Order;
import com.model.OrderDetail;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.ImageUtil;
import com.utils.NumberUtil;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trang "Lịch sử mua hàng" ở phía khách hàng (client) - chỉ hiển thị các đơn
 * hàng ({@link Order}) do CHÍNH khách đang đăng nhập đặt (Orders.CustomerID
 * = UserID hiện tại, xem cách gán ở {@link CartPanel#persistOrderAndFinish}).
 * <p>
 * Đồng bộ giao diện với các trang client khác (thẻ bo góc trắng viền mờ như
 * {@link CartPanel}/{@link ProfilePanel}, AppColor/AppFont/AppSpacing dùng
 * chung, {@link EmptyState} khi chưa có đơn nào), gồm 2 tính năng chính:
 * <ul>
 *   <li>Tra cứu hóa đơn (mã đơn) bằng {@link BaseSearch} có autocomplete -
 *   gợi ý được nạp sẵn từ chính danh sách đơn của khách, lọc tại chỗ.</li>
 *   <li>Hủy đơn hàng - chỉ khả dụng khi đơn đang ở trạng thái NEW (Chờ xác nhận)
 *   (đúng luật nghiệp vụ trong {@code OrderDAO.updateOrderStatus}), gọi lại
 *   DAO dùng chung với phía admin để đảm bảo hoàn kho/luật hủy nhất quán.</li>
 * </ul>
 */
public class OrderHistoryPanel extends JPanel {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int THUMB_SIZE = 56;

    private final OrderDAO orderDAO = new OrderDAO();

    private final JPanel rowsContainer;
    private final CardLayout stateLayout = new CardLayout();
    private final JPanel stateContainer;
    private final BaseSearch searchBar;

    private List<Order> allOrders = new ArrayList<>();
    private String currentKeyword = "";

    // Tự động kiểm tra dữ liệu mới khi khách đang ở trang lịch sử.
    // Poll nhẹ mỗi 3 giây để nhận biết hóa đơn/trạng thái trả hàng vừa cập nhật.
    private static final int AUTO_REFRESH_INTERVAL_MS = 3000;
    private final Timer autoRefreshTimer;
    private boolean autoRefreshInProgress = false;
    private String lastOrdersFingerprint = "";

    public OrderHistoryPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);

        searchBar = new BaseSearch("Tra cứu theo mã đơn hàng (VD: DH0001)...");
        searchBar.setPreferredWidth(340);
        searchBar.onSearch(keyword -> {
            currentKeyword = keyword == null ? "" : keyword;
            applyFilter();
        });

        add(buildHeaderBlock(), BorderLayout.NORTH);

        rowsContainer = new JPanel();
        rowsContainer.setOpaque(false);
        rowsContainer.setLayout(new BoxLayout(rowsContainer, BoxLayout.Y_AXIS));

        JPanel listWrapper = new JPanel(new BorderLayout());
        listWrapper.setOpaque(false);
        listWrapper.setBorder(new EmptyBorder(0, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        listWrapper.add(rowsContainer, BorderLayout.NORTH);

        stateContainer = new JPanel(stateLayout);
        stateContainer.setOpaque(false);
        stateContainer.add(listWrapper, "list");
        stateContainer.add(buildEmptyStatePanel(), "empty");

        JScrollPane scroll = new JScrollPane(stateContainer);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.PAGE_BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);

        loadOrders();

        // Khi đang đứng ở trang này, tự kiểm tra dữ liệu mới từ DB.
        // Không rebuild giao diện nếu dữ liệu không thay đổi để tránh nhấp nháy.
        autoRefreshTimer = new Timer(AUTO_REFRESH_INTERVAL_MS, e -> autoRefreshIfChanged());
        autoRefreshTimer.setRepeats(true);
        autoRefreshTimer.start();

        // Dừng timer khi panel bị loại khỏi giao diện để không giữ tài nguyên/DB polling.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.DISPLAYABILITY_CHANGED) != 0
                    && !isDisplayable()) {
                autoRefreshTimer.stop();
            }
        });
    }

    /** Gọi lại mỗi khi trang được mở (từ dropdown tài khoản) để luôn thấy đơn mới nhất/trạng thái mới nhất. */
    public void refresh() {
        loadOrders();
    }

    // ==================== Nạp dữ liệu ====================

    private void loadOrders() {
        int customerId = AuthService.getInstance().getCurrentUser().getUserId();
        allOrders = orderDAO.getByCustomerId(customerId);
        lastOrdersFingerprint = buildOrdersFingerprint(allOrders);

        List<String> suggestions = allOrders.stream()
                .map(Order::getOrderCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());
        searchBar.setSuggestions(suggestions);

        applyFilter();
    }

    /**
     * Kiểm tra thay đổi ở background để không khóa EDT khi DB đang truy vấn.
     * Bao gồm trạng thái hóa đơn/đơn hàng và kết quả đổi trả, nên khi nhân viên
     * duyệt trả hàng thì trang khách tự cập nhật mà không cần mở lại trang.
     */
    private void autoRefreshIfChanged() {
        if (!isShowing() || autoRefreshInProgress) return;

        autoRefreshInProgress = true;
        int customerId = AuthService.getInstance().getCurrentUser().getUserId();

        SwingWorker<List<Order>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Order> doInBackground() {
                return orderDAO.getByCustomerId(customerId);
            }

            @Override
            protected void done() {
                try {
                    List<Order> latestOrders = get();
                    String latestFingerprint = buildOrdersFingerprint(latestOrders);

                    if (!latestFingerprint.equals(lastOrdersFingerprint)) {
                        allOrders = latestOrders;
                        lastOrdersFingerprint = latestFingerprint;

                        List<String> suggestions = latestOrders.stream()
                                .map(Order::getOrderCode)
                                .filter(code -> code != null && !code.isBlank())
                                .collect(Collectors.toCollection(LinkedHashSet::new))
                                .stream().collect(Collectors.toList());
                        searchBar.setSuggestions(suggestions);

                        applyFilter();
                    }
                } catch (Exception ignored) {
                    // Không làm gián đoạn trang nếu DB tạm thời không truy cập được.
                } finally {
                    autoRefreshInProgress = false;
                }
            }
        };
        worker.execute();
    }

    private String buildOrdersFingerprint(List<Order> orders) {
        StringBuilder fingerprint = new StringBuilder();
        for (Order order : orders) {
            fingerprint.append(order.getOrderId()).append('|')
                    .append(order.getInvoiceId()).append('|')
                    .append(order.getOrderStatus()).append('|')
                    .append(order.getPaymentStatus()).append('|')
                    .append(order.getLatestReturnStatus()).append('|')
                    .append(order.getLatestReturnType()).append('|')
                    .append(order.getLatestReturnValue()).append('|')
                    .append(order.getLatestReturnReason()).append('|')
                    .append(order.getLatestReturnRejectionReason()).append('|')
                    .append(order.getLatestReturnCreatedAt()).append('|')
                    .append(order.getItemCount()).append('|')
                    .append(order.getTotalAmount()).append(';');
        }
        return fingerprint.toString();
    }

    private void applyFilter() {
        String kw = currentKeyword.trim().toLowerCase();
        List<Order> filtered = kw.isEmpty() ? allOrders : allOrders.stream()
                .filter(o -> (o.getOrderCode() != null && o.getOrderCode().toLowerCase().contains(kw)))
                .collect(Collectors.toList());

        rebuildRows(filtered);
        stateLayout.show(stateContainer, filtered.isEmpty() ? "empty" : "list");
    }

    private void rebuildRows(List<Order> orders) {
        rowsContainer.removeAll();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) rowsContainer.add(Box.createVerticalStrut(AppSpacing.MD));
            rowsContainer.add(buildOrderRow(orders.get(i)));
        }
        rowsContainer.revalidate();
        rowsContainer.repaint();
    }

    // ==================== Header (tiêu đề + ô tra cứu) ====================

    private JPanel buildHeaderBlock() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL));

        JLabel title = new JLabel("Lịch sử mua hàng");
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        wrapper.add(title, BorderLayout.WEST);
        wrapper.add(searchBar, BorderLayout.EAST);

        return wrapper;
    }

    private JPanel buildEmptyStatePanel() {
        EmptyState empty = EmptyState.noData("đơn hàng");
        empty.setSubtitle("Bạn chưa có đơn hàng nào. Hãy khám phá cửa hàng và đặt đơn đầu tiên!");

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(20, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));
        wrapper.add(empty, BorderLayout.CENTER);
        return wrapper;
    }

    // ==================== 1 dòng đơn hàng ====================

    private JPanel buildOrderRow(Order order) {
        JPanel card = new JPanel(new BorderLayout(AppSpacing.LG, 0));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 18, 16, 18)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));

        card.add(new JLabel(loadOrderThumb(order)), BorderLayout.WEST);
        card.add(buildRowCenter(order), BorderLayout.CENTER);
        card.add(buildRowEast(order), BorderLayout.EAST);

        return card;
    }

    private JPanel buildRowCenter(Order order) {
        JPanel center = new JPanel();
        center.setOpaque(false);
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));

        JPanel line1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        line1.setOpaque(false);
        line1.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel codeLabel = new JLabel(order.getOrderCode());
        codeLabel.setFont(AppFont.BODY_BOLD.deriveFont(15f));
        codeLabel.setForeground(AppColor.TEXT_PRIMARY);
        line1.add(codeLabel);
        line1.add(statusBadge(order));

        JLabel dateLabel = new JLabel(order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMAT) : "-");
        dateLabel.setFont(AppFont.SMALL);
        dateLabel.setForeground(AppColor.TEXT_MUTED);
        dateLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        dateLabel.setBorder(new EmptyBorder(4, 2, 0, 0));

        JLabel itemsLabel = new JLabel(order.getItemCount() + " sản phẩm · " + paymentMethodLabel(order.getPaymentMethod())
                + " · " + paymentStatusLabel(order.getPaymentStatus()));
        itemsLabel.setFont(AppFont.SMALL);
        itemsLabel.setForeground(AppColor.TEXT_MUTED);
        itemsLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsLabel.setBorder(new EmptyBorder(2, 2, 0, 0));

        center.add(line1);
        center.add(dateLabel);
        center.add(itemsLabel);
        if (order.getLatestReturnStatus() != null) {
            JLabel returnLabel = new JLabel(returnResultText(order));
            returnLabel.setFont(AppFont.SMALL_BOLD);
            returnLabel.setForeground(returnResultColor(order.getLatestReturnStatus()));
            returnLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            returnLabel.setBorder(new EmptyBorder(4, 2, 0, 0));
            center.add(returnLabel);
        }
        return center;
    }

    private JPanel buildRowEast(Order order) {
        JPanel east = new JPanel();
        east.setOpaque(false);
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));

        JLabel total = new JLabel(NumberUtil.formatThousands(order.getTotalAmount() != null
                ? order.getTotalAmount().longValue() : 0L) + " đ");
        total.setFont(AppFont.BODY_BOLD.deriveFont(15f));
        total.setForeground(AppColor.ACCENT_HOVER);
        total.setAlignmentX(Component.RIGHT_ALIGNMENT);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(Component.RIGHT_ALIGNMENT);

        if (isCancellable(order)) {
            JButton cancelButton = smallButton("Hủy đơn", AppColor.ERROR_BG, AppColor.ERROR);
            cancelButton.addActionListener(e -> handleCancel(order));
            buttons.add(cancelButton);
        }
        if (order.canRequestReturn()) {
            JButton returnButton = smallButton("Trả hàng", AppColor.ERROR_BG, AppColor.ERROR);
            returnButton.addActionListener(e -> handleReturnRequest(order));
            buttons.add(returnButton);
        } else if (order.getLatestReturnStatus() != null) {
            if ("REJECTED".equalsIgnoreCase(order.getLatestReturnStatus())) {
                JLabel rejectedTag = new JLabel("Đã từ chối");
                rejectedTag.setFont(AppFont.SMALL_BOLD);
                rejectedTag.setForeground(AppColor.ERROR);
                rejectedTag.setToolTipText("Đã từ chối yêu cầu trả hàng");
                buttons.add(rejectedTag);
            } else {
                JLabel returnedTag = new JLabel(returnResultShortLabel(order.getLatestReturnStatus()));
                returnedTag.setFont(AppFont.SMALL_BOLD);
                returnedTag.setForeground(returnResultColor(order.getLatestReturnStatus()));
                buttons.add(returnedTag);
            }
        }
        
        JButton detailButton = smallButton("Chi tiết", AppColor.BG_LIGHTER, AppColor.TEXT_PRIMARY);
        detailButton.addActionListener(e -> showDetailDialog(order));
        buttons.add(detailButton);

        east.add(total);
        east.add(Box.createVerticalStrut(8));
        east.add(buttons);
        return east;
    }


    private void showRejectionReason(Order order) {
        String reason = order.getLatestReturnRejectionReason();
        if (reason == null || reason.isBlank()) {
            reason = "Nhân viên chưa cung cấp lý do từ chối.";
        }
        BaseDialog.info(this, "Lý do từ chối trả hàng", reason);
    }

    private JButton smallButton(String text, Color bg, Color fg) {
        JButton button = new JButton(text);
        button.setFont(AppFont.SMALL_BOLD);
        button.setForeground(fg);
        button.setBackground(bg);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(7, 14, 7, 14));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ==================== Hủy đơn ====================

    private boolean isCancellable(Order order) {
        String status = order.getOrderStatus();
        return "NEW".equalsIgnoreCase(status);
    }

    private void handleCancel(Order order) {
        String reason = BaseDialog.inputText(this, "Hủy đơn hàng " + order.getOrderCode(),
                "Lý do hủy đơn:", "", "Hủy đơn");
        if (reason == null) return;

        boolean confirmed = BaseDialog.confirm(this, "Xác nhận hủy đơn hàng",
                "Bạn có chắc muốn hủy đơn hàng " + order.getOrderCode() + "? Hành động này không thể hoàn tác.",
                "Hủy đơn", AppColor.ERROR, AppColor.ERROR_HOVER, FontAwesomeSolid.TIMES_CIRCLE);
        if (!confirmed) return;

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(
                order.getOrderId(), "CANCELLED", userId, reason);
        if (!result.success) {
            BaseDialog.error(this, "Không thể hủy đơn", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công", "Đã hủy đơn hàng " + order.getOrderCode() + ".");
        loadOrders();
    }

    // ==================== Trả hàng ====================

    /**
     * Chỉ khả dụng trong 1 ngày kể từ lúc đơn COMPLETED ({@link Order#canRequestReturn()}) -
     * yêu cầu được gửi thẳng vào bảng đổi/trả của nhân viên bán hàng ngay khi
     * khách xác nhận (xem {@link OrderDAO#requestReturn}).
     */
    private void handleReturnRequest(Order order) {
        String reason = BaseDialog.inputText(this, "Trả hàng " + order.getOrderCode(),
                "Lý do trả hàng:", "", "Gửi yêu cầu");
        if (reason == null) return; // bấm Hủy

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        String error = orderDAO.requestReturn(order.getOrderId(), userId, reason);
        if (error != null) {
            BaseDialog.error(this, "Không thể gửi yêu cầu", error);
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đã gửi yêu cầu trả hàng cho đơn " + order.getOrderCode() + ". Nhân viên sẽ xử lý sớm nhất.");
        loadOrders();
    }

    // ==================== Dialog xem chi tiết ====================

    private void showDetailDialog(Order order) {
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Chi tiết đơn hàng",
                Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setResizable(false);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppColor.WHITE);
        root.setBorder(new EmptyBorder(24, 26, 20, 26));
        root.setPreferredSize(new Dimension(480, 560));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 16, 0));

        JLabel titleLabel = new JLabel(order.getOrderCode());
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        header.add(titleLabel, BorderLayout.WEST);
        header.add(statusBadge(order), BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        body.add(infoLine("Ngày đặt", order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMAT) : "-"));
        body.add(infoLine("Người nhận", order.getCustomerName()));
        body.add(infoLine("Số điện thoại", order.getCustomerPhone()));
        body.add(infoLine("Địa chỉ giao hàng", order.getShippingAddress()));
        body.add(infoLine("Phương thức thanh toán", paymentMethodLabel(order.getPaymentMethod())));
        body.add(infoLine("Trạng thái thanh toán", paymentStatusLabel(order.getPaymentStatus())));

        // Khi đơn đã hủy, hiển thị rõ lý do hủy trong chi tiết đơn hàng của khách.
        if (order.isCancelled()) {
            body.add(Box.createVerticalStrut(10));
            body.add(infoLine("Lý do hủy",
                    blankAsDash(order.getCancelReason())));
        }

        // Tách riêng nội dung đổi/trả khỏi thông tin đơn hàng để khách dễ nhận biết.
        if (order.getLatestReturnStatus() != null) {
            body.add(Box.createVerticalStrut(12));
            body.add(buildReturnSection(order));
            body.add(Box.createVerticalStrut(14));
        }

        body.add(divider());
        body.add(Box.createVerticalStrut(10));

        JLabel itemsTitle = new JLabel("Sản phẩm đã đặt");
        itemsTitle.setFont(AppFont.BODY_BOLD);
        itemsTitle.setForeground(AppColor.TEXT_PRIMARY);
        itemsTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        itemsTitle.setBorder(new EmptyBorder(0, 0, 8, 0));
        body.add(itemsTitle);

        List<OrderDetail> details = orderDAO.getDetailsByOrderId(order.getOrderId());
        for (OrderDetail d : details) {
            body.add(buildDetailLine(d));
            body.add(Box.createVerticalStrut(8));
        }

        body.add(Box.createVerticalStrut(4));
        body.add(divider());
        body.add(Box.createVerticalStrut(10));

        JPanel totalRow = new JPanel(new BorderLayout());
        totalRow.setOpaque(false);
        totalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel totalCaption = new JLabel("Tổng cộng");
        totalCaption.setFont(AppFont.BODY_BOLD);
        totalCaption.setForeground(AppColor.TEXT_PRIMARY);
        JLabel totalValue = new JLabel(NumberUtil.formatThousands(order.getTotalAmount() != null
                ? order.getTotalAmount().longValue() : 0L) + " đ");
        totalValue.setFont(AppFont.HEADING_MD);
        totalValue.setForeground(AppColor.ACCENT_HOVER);
        totalRow.add(totalCaption, BorderLayout.WEST);
        totalRow.add(totalValue, BorderLayout.EAST);
        body.add(totalRow);

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        root.add(scroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(16, 0, 0, 0));

        JButton closeButton = new JButton("Đóng");
        closeButton.setFont(AppFont.BUTTON);
        closeButton.setFocusPainted(false);
        closeButton.setBackground(AppColor.BORDER);
        closeButton.setForeground(AppColor.TEXT_PRIMARY);
        closeButton.setBorder(new EmptyBorder(8, 18, 8, 18));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dialog.dispose());
        footer.add(closeButton);

        root.add(footer, BorderLayout.SOUTH);

        dialog.getRootPane().setDefaultButton(closeButton);
        dialog.add(root);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Khối thông tin đổi/trả riêng biệt trong chi tiết đơn hàng.
     * Hiển thị đầy đủ: thời gian gửi yêu cầu, lý do khách, kết quả xử lý
     * và lý do từ chối của nhân viên nếu bị từ chối.
     */
    private JPanel buildReturnSection(Order order) {
        JPanel section = new JPanel();
        section.setOpaque(true);
        section.setBackground(AppColor.BG_LIGHTER);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(12, 14, 12, 14)
        ));
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Thông tin trả hàng");
        title.setFont(AppFont.BODY_BOLD);
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(title);
        section.add(Box.createVerticalStrut(8));

        String type = "EXCHANGE".equalsIgnoreCase(order.getLatestReturnType())
                ? "Đổi hàng" : "Trả hàng";
        JLabel typeLabel = new JLabel("Loại yêu cầu: " + type);
        typeLabel.setFont(AppFont.SMALL_BOLD);
        typeLabel.setForeground(AppColor.TEXT_SECONDARY);
        typeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(typeLabel);
        section.add(Box.createVerticalStrut(4));

        if (order.getLatestReturnCreatedAt() != null) {
            section.add(infoLine("Thời gian trả hàng",
                    order.getLatestReturnCreatedAt().format(DATE_FORMAT)));
        } else {
            section.add(infoLine("Thời gian trả hàng", "-"));
        }

        section.add(infoLine("Lí do của khách",
                blankAsDash(order.getLatestReturnReason())));

        String result = returnResultShortLabel(order.getLatestReturnStatus());
        section.add(infoLine("Kết quả trả hàng", result));

        if ("REJECTED".equalsIgnoreCase(order.getLatestReturnStatus())) {
            section.add(infoLine("Lí do từ chối",
                    blankAsDash(order.getLatestReturnRejectionReason())));
        }

        if (order.getLatestReturnValue() != null) {
            section.add(infoLine("Giá trị hàng trả",
                    NumberUtil.formatThousands(order.getLatestReturnValue().longValue()) + " đ"));
        }

        return section;
    }

    private String blankAsDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private JPanel buildDetailLine(OrderDetail d) {
        JPanel line = new JPanel(new BorderLayout(10, 0));
        line.setOpaque(false);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel thumb = new JLabel(loadRoundedThumb(d.getProductImageUrl(), 36));
        line.add(thumb, BorderLayout.WEST);

        JLabel name = new JLabel(d.getProductName() + " × " + d.getQuantity());
        name.setFont(AppFont.BODY);
        name.setForeground(AppColor.TEXT_PRIMARY);
        line.add(name, BorderLayout.CENTER);

        JLabel lineTotal = new JLabel(NumberUtil.formatThousands(d.getLineTotal() != null
                ? d.getLineTotal().longValue() : 0L) + " đ");
        lineTotal.setFont(AppFont.SMALL_BOLD);
        lineTotal.setForeground(AppColor.TEXT_SECONDARY);
        line.add(lineTotal, BorderLayout.EAST);

        return line;
    }

    private JPanel infoLine(String label, String value) {
        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        row.setBorder(new EmptyBorder(2, 0, 2, 0));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setPreferredSize(new Dimension(150, 18));
        row.add(labelComp, BorderLayout.WEST);

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueComp.setFont(AppFont.SMALL_BOLD);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);
        row.add(valueComp, BorderLayout.CENTER);

        return row;
    }

    private JComponent divider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private String returnResultText(Order order) {
        String type = "EXCHANGE".equalsIgnoreCase(order.getLatestReturnType()) ? "Đổi hàng" : "Trả hàng";
        return type + ": " + returnResultShortLabel(order.getLatestReturnStatus());
    }

    private String returnResultShortLabel(String status) {
        if (status == null) return "-";
        switch (status.toUpperCase()) {
            case "PENDING": return "Đang xử lý";
            case "APPROVED": return "Đã duyệt";
            case "REJECTED": return "Từ chối";
            default: return status;
        }
    }

    private Color returnResultColor(String status) {
        if (status == null) return AppColor.TEXT_MUTED;
        switch (status.toUpperCase()) {
            case "APPROVED": return AppColor.SUCCESS;
            case "REJECTED": return AppColor.ERROR;
            case "PENDING": return AppColor.WARNING;
            default: return AppColor.TEXT_MUTED;
        }
    }

    // ==================== Badge trạng thái (đồng bộ nhãn/màu với OrderPanel bên admin) ====================

    private JLabel statusBadge(Order order) {
        String status = order != null ? order.getOrderStatus() : null;

        // Khi yêu cầu trả hàng đã được nhân viên DUYỆT, trạng thái hiển thị
        // trên đơn của khách phải chuyển thành "Trả hàng", thay vì tiếp tục
        // hiển thị trạng thái đơn cũ như "Hoàn thành".
        boolean approvedReturn = order != null
                && "APPROVED".equalsIgnoreCase(order.getLatestReturnStatus());

        String label = approvedReturn ? "Trả hàng" : orderStatusLabel(status);
        Color color = approvedReturn ? AppColor.ERROR : orderStatusColor(status);

        JLabel badge = new JLabel(label) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(color);
        badge.setBorder(new EmptyBorder(3, 10, 3, 10));
        badge.setOpaque(false);
        return badge;
    }

    private static String orderStatusLabel(String status) {
        if (status == null) return "-";
        switch (status) {
            case "NEW": return "Chờ xác nhận";
            case "CONFIRMED": return "Đã xác nhận";
            case "SHIPPING": return "Đang giao";
            case "COMPLETED": return "Hoàn thành";
            case "CANCELLED": return "Đã hủy";
            default: return status;
        }
    }

    private static Color orderStatusColor(String status) {
        if (status == null) return AppColor.WARNING;
        switch (status) {
            case "COMPLETED": return AppColor.SUCCESS;
            case "CONFIRMED": return AppColor.INFO;
            case "SHIPPING": return AppColor.ACCENT;
            case "CANCELLED": return AppColor.ERROR;
            default: return AppColor.WARNING; // NEW
        }
    }

    private static String paymentMethodLabel(String method) {
        if (method == null) return "-";
        switch (method) {
            case "COD": return "COD";
            case "PAYPAL": return "PayPal";
            default: return method;
        }
    }

    private static String paymentStatusLabel(String status) {
        if (status == null) return "-";
        switch (status) {
            case "PENDING": return "Chờ thanh toán";
            case "PAID": return "Đã thanh toán";
            case "FAILED": return "Thất bại";
            default: return status;
        }
    }

    // ==================== Ảnh thumbnail (giống style CartPanel/ClientHeader) ====================

    /** Anh sản phẩm đầu tiên trong đơn (đại diện cho cả đơn ở dòng danh sách). */
    private ImageIcon loadOrderThumb(Order order) {
        List<OrderDetail> details = orderDAO.getDetailsByOrderId(order.getOrderId());
        String imageUrl = details.isEmpty() ? null : details.get(0).getProductImageUrl();
        return loadRoundedThumb(imageUrl, THUMB_SIZE);
    }

    private ImageIcon loadRoundedThumb(String imageUrl, int size) {
        BufferedImage raw = (imageUrl == null || imageUrl.isBlank()) ? null : ImageUtil.readSafe(imageUrl);
        BufferedImage square = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = square.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setClip(new RoundRectangle2D.Float(0, 0, size, size, 14, 14));

        if (raw != null) {
            int side = Math.min(raw.getWidth(), raw.getHeight());
            BufferedImage cropped = raw.getSubimage((raw.getWidth() - side) / 2, (raw.getHeight() - side) / 2, side, side);
            g2.drawImage(cropped, 0, 0, size, size, null);
        } else {
            g2.setColor(AppColor.ACCENT_BG_SOFT);
            g2.fillRect(0, 0, size, size);
            FontIcon icon = FontIcon.of(FontAwesomeSolid.BOX, (int) (size * 0.45));
            icon.setIconColor(AppColor.ACCENT_HOVER);
            int ix = (size - icon.getIconWidth()) / 2;
            int iy = (size - icon.getIconHeight()) / 2;
            icon.paintIcon(null, g2, ix, iy);
        }
        g2.dispose();
        return new ImageIcon(square);
    }
}