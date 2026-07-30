package com.view.admin.order;

import com.components.BaseDialog;
import com.dao.OrderDAO;
import com.model.Order;
import com.model.OrderDetail;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.components.table.ImageColumn;
import com.components.table.RowColorProvider;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialog xem chi tiết 1 đơn hàng online: thông tin khách (lưới 2 cột),
 * địa chỉ giao hàng, bảng sản phẩm có ảnh. Mở dialog sẽ đánh dấu đã xem
 * (OrderDAO.markSeen). Có nút Xác nhận / Hủy đơn theo quyền ORDER_MANAGE.
 */
public class OrderDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrderDAO orderDAO;
    private Order order;

    public OrderDetailDialog(Frame owner, Order order, OrderDAO orderDAO) {
        super(owner, "Chi tiết đơn hàng", Dialog.ModalityType.APPLICATION_MODAL);
        this.order = order;
        this.orderDAO = orderDAO;
        List<OrderDetail> details = orderDAO.getDetailsByOrderId(order.getOrderId());

        if (!order.isSeenByAdmin()) {
            orderDAO.markSeen(order.getOrderId());
            order.setSeenByAdmin(true);
        }

        setSize(780, 680);
        setMinimumSize(new Dimension(640, 520));
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
    // Header
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        boolean cancelled = order.isCancelled();
        FontIcon icon = FontIcon.of(FontAwesomeSolid.SHOPPING_CART, 18);
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

        JLabel titleLabel = new JLabel(order.getOrderCode());
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(
                (order.getCustomerName() != null ? order.getCustomerName() : "Khách lẻ")
                        + "  ·  " + OrderPanel.orderStatusLabel(order.getOrderStatus()));
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body: card thông tin 2 cột + bảng SP có hình
    // ---------------------------------------------------------------

    private JScrollPane buildBody(List<OrderDetail> details) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        // Card thông tin
        JPanel infoCard = new JPanel(new BorderLayout());
        infoCard.setOpaque(true);
        infoCard.setBackground(AppColor.BG_LIGHT);
        infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));
        infoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 340));

        JPanel cardInner = new JPanel();
        cardInner.setOpaque(false);
        cardInner.setLayout(new BoxLayout(cardInner, BoxLayout.Y_AXIS));

        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 28, 14));
        infoGrid.setOpaque(false);
        infoGrid.add(infoCell("Khách hàng",
                order.getCustomerName() != null ? order.getCustomerName() : "Khách lẻ"));
        infoGrid.add(infoCell("Email", order.getCustomerEmail()));
        infoGrid.add(infoCell("Số điện thoại", order.getCustomerPhone()));
        infoGrid.add(infoCell("Ngày đặt",
                order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_TIME_FORMAT) : "-"));
        infoGrid.add(infoCell("Phương thức thanh toán",
                OrderPanel.paymentMethodLabel(order.getPaymentMethod())));
        infoGrid.add(infoCell("Trạng thái thanh toán",
                OrderPanel.paymentStatusLabel(order.getPaymentStatus())));
        infoGrid.add(infoCell("Tạm tính",
                NumberUtil.formatThousands(order.getSubTotal().longValue()) + " đ"));
        infoGrid.add(infoCellTotal("Tổng tiền đơn hàng",
                NumberUtil.formatThousands(order.getTotalAmount().longValue()) + " đ"));
        cardInner.add(infoGrid);

        JPanel addressRow = new JPanel(new BorderLayout(8, 0));
        addressRow.setOpaque(false);
        addressRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        JLabel addrLabel = new JLabel("Địa chỉ giao hàng");
        addrLabel.setFont(AppFont.SMALL_BOLD);
        addrLabel.setForeground(AppColor.TEXT_MUTED);
        String addr = order.getShippingAddress();
        JLabel addrValue = new JLabel(addr == null || addr.isBlank() ? "-" : addr);
        addrValue.setFont(AppFont.BODY);
        addrValue.setForeground(AppColor.TEXT_PRIMARY);
        addressRow.add(addrLabel, BorderLayout.NORTH);
        addressRow.add(addrValue, BorderLayout.CENTER);
        cardInner.add(addressRow);

        infoCard.add(cardInner, BorderLayout.CENTER);
        content.add(infoCard);
        content.add(Box.createVerticalStrut(20));

        JLabel sectionLabel = new JLabel("Danh sách sản phẩm (" + details.size() + ")");
        sectionLabel.setFont(AppFont.BODY_BOLD);
        sectionLabel.setForeground(AppColor.TEXT_PRIMARY);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sectionLabel);
        content.add(Box.createVerticalStrut(10));

        JTable table = buildDetailTable(details);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        int tableH = Math.max(100, Math.min(240, 44 + details.size() * 56));
        tableScroll.setPreferredSize(new Dimension(700, tableH));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableH + 20));
        tableScroll.getViewport().setBackground(AppColor.WHITE);
        tableScroll.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        tableScroll.setOpaque(false);
        content.add(tableScroll);

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(AppColor.WHITE);
        return scroll;
    }

    private JPanel infoCell(String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueComp.setFont(AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(2));
        cell.add(valueComp);
        return cell;
    }

    private JPanel infoCellTotal(String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueComp = new JLabel(value == null || value.isBlank() ? "-" : value);
        valueComp.setFont(AppFont.BODY_BOLD);
        valueComp.setForeground(AppColor.ACCENT);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(2));
        cell.add(valueComp);
        return cell;
    }

    private JTable buildDetailTable(List<OrderDetail> details) {
        String[] columns = {"Hình", "Sản phẩm", "SL", "Đơn giá", "Thành tiền"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? String.class : Object.class;
            }
        };

        for (OrderDetail d : details) {
            model.addRow(new Object[]{
                    d.getProductImageUrl() != null ? d.getProductImageUrl() : "",
                    d.getProductName(),
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                    NumberUtil.formatThousands(d.getLineTotal().longValue())
            });
        }

        JTable table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(56);
        table.setBackground(AppColor.WHITE);
        table.setForeground(AppColor.TEXT_PRIMARY);
        table.setSelectionBackground(AppColor.ACCENT_BG_SOFT);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.getTableHeader().setBackground(AppColor.BG_LIGHT);
        table.getTableHeader().setForeground(AppColor.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(0, 38));
        table.getTableHeader().setReorderingAllowed(false);
        table.setGridColor(AppColor.BORDER);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(false);
        table.setRowSelectionAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setIntercellSpacing(new Dimension(0, 0));

        ImageColumn imageColumn = new ImageColumn(44, 10);
        RowColorProvider colors = (row, selected) -> AppColor.WHITE;
        table.getColumnModel().getColumn(0).setCellRenderer(imageColumn.renderer(colors));
        table.getColumnModel().getColumn(0).setPreferredWidth(64);
        table.getColumnModel().getColumn(0).setMinWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(72);
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        table.getColumnModel().getColumn(2).setPreferredWidth(56);
        table.getColumnModel().getColumn(2).setMaxWidth(72);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);

        DefaultTableCellRenderer nameRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(AppFont.BODY_BOLD);
                setForeground(AppColor.TEXT_PRIMARY);
                setBackground(AppColor.WHITE);
                setBorder(new EmptyBorder(0, 8, 0, 4));
                return c;
            }
        };
        table.getColumnModel().getColumn(1).setCellRenderer(nameRenderer);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBackground(AppColor.WHITE);
                setForeground(AppColor.TEXT_PRIMARY);
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(center);

        DefaultTableCellRenderer money = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBackground(AppColor.WHITE);
                setForeground(column == 4 ? AppColor.ACCENT : AppColor.TEXT_PRIMARY);
                setFont(column == 4 ? AppFont.BODY_BOLD : AppFont.BODY);
                setBorder(new EmptyBorder(0, 4, 0, 12));
                return c;
            }
        };
        table.getColumnModel().getColumn(3).setCellRenderer(money);
        table.getColumnModel().getColumn(4).setCellRenderer(money);

        return table;
    }

    // ---------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        boolean canManage = PermissionManager.getInstance().can(AppPermission.ORDER_MANAGE);
        boolean isNew = "NEW".equalsIgnoreCase(order.getOrderStatus());
        boolean isConfirmed = order.isConfirmed();

        if (canManage && (isNew || isConfirmed)) {
            JButton cancelButton = new JButton("Hủy đơn");
            cancelButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            cancelButton.setFocusPainted(false);
            cancelButton.setBackground(AppColor.ERROR_BG);
            cancelButton.setForeground(AppColor.ERROR);
            cancelButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            cancelButton.addActionListener(e -> handleCancel());
            footer.add(cancelButton);
        }

        if (canManage && isNew) {
            JButton confirmButton = new JButton("Xác nhận đơn");
            confirmButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            confirmButton.setFocusPainted(false);
            confirmButton.setBackground(AppColor.SUCCESS_BG);
            confirmButton.setForeground(AppColor.SUCCESS);
            confirmButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            confirmButton.addActionListener(e -> handleConfirm());
            footer.add(confirmButton);
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

    private void handleConfirm() {
        boolean confirmed = BaseDialog.confirm(this, "Xác nhận đơn hàng",
                "Xác nhận đơn hàng " + order.getOrderCode() + "?");
        if (!confirmed) return;

        boolean ok = orderDAO.updateOrderStatus(order.getOrderId(), "CONFIRMED");
        if (!ok) {
            BaseDialog.error(this, "Không thể xác nhận",
                    "Xác nhận đơn hàng thất bại. Vui lòng thử lại.");
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đã xác nhận đơn hàng " + order.getOrderCode() + ".");
        dispose();
    }

    private void handleCancel() {
        boolean confirmed = BaseDialog.confirm(this, "Hủy đơn hàng",
                "Bạn có chắc muốn hủy đơn hàng " + order.getOrderCode() + "?");
        if (!confirmed) return;

        boolean ok = orderDAO.updateOrderStatus(order.getOrderId(), "CANCELLED");
        if (!ok) {
            BaseDialog.error(this, "Không thể hủy đơn",
                    "Hủy đơn hàng thất bại. Vui lòng thử lại.");
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đã hủy đơn hàng " + order.getOrderCode() + ".");
        dispose();
    }
}