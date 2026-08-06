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
 * Trang "Lịch sử mua hàng" phía khách — search autocomplete + hủy đơn NEW/CONFIRMED.
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
    }

    public void refresh() {
        loadOrders();
    }

    private void loadOrders() {
        int customerId = AuthService.getInstance().getCurrentUser().getUserId();
        allOrders = orderDAO.getByCustomerId(customerId);

        List<String> suggestions = allOrders.stream()
                .map(Order::getOrderCode)
                .filter(code -> code != null && !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream().collect(Collectors.toList());
        searchBar.setSuggestions(suggestions);

        applyFilter();
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
        line1.add(statusBadge(order.getOrderStatus()));

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

        JButton detailButton = smallButton("Chi tiết", AppColor.BG_LIGHTER, AppColor.TEXT_PRIMARY);
        detailButton.addActionListener(e -> showDetailDialog(order));
        buttons.add(detailButton);

        if (isCancellable(order)) {
            JButton cancelButton = smallButton("Hủy đơn", AppColor.ERROR_BG, AppColor.ERROR);
            cancelButton.addActionListener(e -> handleCancel(order));
            buttons.add(cancelButton);
        }

        east.add(total);
        east.add(Box.createVerticalStrut(8));
        east.add(buttons);
        return east;
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

    private boolean isCancellable(Order order) {
        String status = order.getOrderStatus();
        return "NEW".equalsIgnoreCase(status) || "CONFIRMED".equalsIgnoreCase(status);
    }

    private void handleCancel(Order order) {
        boolean confirmed = BaseDialog.confirm(this, "Hủy đơn hàng",
                "Bạn có chắc muốn hủy đơn hàng " + order.getOrderCode() + "? Hành động này không thể hoàn tác.",
                "Hủy đơn", AppColor.ERROR, AppColor.ERROR_HOVER, FontAwesomeSolid.TIMES_CIRCLE);
        if (!confirmed) return;

        int userId = AuthService.getInstance().getCurrentUser().getUserId();
        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(order.getOrderId(), "CANCELLED", userId);
        if (!result.success) {
            BaseDialog.error(this, "Không thể hủy đơn", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công", "Đã hủy đơn hàng " + order.getOrderCode() + ".");
        loadOrders();
    }

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
        header.add(statusBadge(order.getOrderStatus()), BorderLayout.EAST);
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
        body.add(Box.createVerticalStrut(10));
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

        if (isCancellable(order)) {
            JButton cancelButton = smallButton("Hủy đơn hàng", AppColor.ERROR_BG, AppColor.ERROR);
            cancelButton.addActionListener(e -> {
                dialog.dispose();
                handleCancel(order);
            });
            footer.add(cancelButton);
        }

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

    private JLabel statusBadge(String status) {
        String label = orderStatusLabel(status);
        Color color = orderStatusColor(status);

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
            default: return AppColor.WARNING;
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