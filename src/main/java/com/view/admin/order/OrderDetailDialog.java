package com.view.admin.order;

import com.components.BaseDialog;
import com.dao.InvoiceDAO;
import com.dao.OrderDAO;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Order;
import com.model.OrderDetail;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.components.table.ImageColumn;
import com.components.table.RowColorProvider;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;
import com.utils.pdf.InvoicePdfExporter;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
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
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
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
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class OrderDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final OrderDAO orderDAO;
    private final InvoiceDAO invoiceDAO = new InvoiceDAO();
    private Order order;

    public OrderDetailDialog(Frame owner, Order order, OrderDAO orderDAO) {
        super(owner, "Chi tiết đơn hàng", Dialog.ModalityType.APPLICATION_MODAL);
        this.order = order;
        this.orderDAO = orderDAO;

        // Dam bao co tom tat doi/tra (giong InvoiceDetailDialog voi Invoice) -
        // khong dung toi logic chuyen trang thai don hang.
        orderDAO.attachReturnSummary(order);

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
        boolean completed = order.isCompleted();
        Color statusIconColor = cancelled ? AppColor.ERROR : completed ? AppColor.SUCCESS : AppColor.ACCENT;
        Color statusIconBg = cancelled ? AppColor.ERROR_BG : completed ? AppColor.SUCCESS_BG : AppColor.ACCENT_BG_SOFT;
        FontIcon icon = FontIcon.of(FontAwesomeSolid.SHOPPING_CART, 18);
        icon.setIconColor(statusIconColor);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(statusIconBg);
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

        // Ghi chu doi/tra (giong InvoiceDetailDialog.returnNoteLabel) - chi hien
        // khi don co it nhat 1 phieu doi/tra da duyet.
        if (order.hasReturns()) {
            JLabel returnNoteLabel = new JLabel(order.getReturnNote());
            returnNoteLabel.setFont(AppFont.SMALL);
            returnNoteLabel.setForeground(AppColor.ACCENT);
            returnNoteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            titleBox.add(Box.createVerticalStrut(4));
            titleBox.add(returnNoteLabel);
        }

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);

        if (order.hasReturns()) {
            JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            badges.setOpaque(false);
            badges.add(returnStateBadge());
            header.add(badges, BorderLayout.EAST);
        }

        return header;
    }

    private JLabel returnStateBadge() {
        String text = order.getReturnStateLabel();
        Color color;
        if ("FULL".equalsIgnoreCase(order.getReturnState())) {
            color = AppColor.WARNING;
        } else if ("PARTIAL".equalsIgnoreCase(order.getReturnState())) {
            color = AppColor.ACCENT;
        } else {
            color = AppColor.TEXT_MUTED;
        }
        JLabel lb = new JLabel(text);
        lb.setOpaque(true);
        lb.setBackground(color);
        lb.setForeground(Color.WHITE);
        lb.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lb.setBorder(new EmptyBorder(4, 10, 4, 10));
        return lb;
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
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 420));

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

        // Ma khuyen mai + so tien giam (don online)
        String promoCode = order.getPromotionCode();
        boolean hasPromo = promoCode != null && !promoCode.isBlank()
                && order.getDiscountAmount() != null
                && order.getDiscountAmount().signum() > 0;
        infoGrid.add(infoCell("Mã khuyến mãi",
                hasPromo ? promoCode : "— Không áp dụng"));
        infoGrid.add(infoCell("Giảm giá (KM)",
                hasPromo
                        ? ("−" + NumberUtil.formatThousands(order.getDiscountAmount().longValue()) + " đ")
                        : "0 đ"));

        infoGrid.add(infoCellTotal("Tổng tiền đơn hàng",
                NumberUtil.formatThousands(order.getTotalAmount().longValue()) + " đ"));

        // Da hoan (chi hien khi co phieu doi/tra da duyet) - ap dung cung cach
        // tinh nhu Invoice.getRefundedAmount, khong dung toi trang thai don.
        if (order.hasReturns()) {
            infoGrid.add(infoCell("Đã hoàn",
                    "−" + NumberUtil.formatThousands(order.getRefundedAmount().longValue()) + " đ"));
        }
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
        // Chi hien 2 cot "Da tra"/"Con lai" khi co it nhat 1 dong da bi tra -
        // giu bang gon cho don chua tra hang nao, ap dung cung cach hien thi
        // nhu InvoiceDetailDialog.buildProductTable.
        boolean hasAnyReturn = details.stream().anyMatch(d -> d.getReturnedQuantity() > 0);

        String[] columns = hasAnyReturn
                ? new String[]{"Hình", "Sản phẩm", "SL", "Đã trả", "Còn lại", "Đơn giá", "Thành tiền"}
                : new String[]{"Hình", "Sản phẩm", "SL", "Đơn giá", "Thành tiền"};
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
            if (hasAnyReturn) {
                model.addRow(new Object[]{
                        d.getProductImageUrl() != null ? d.getProductImageUrl() : "",
                        d.getProductName(),
                        d.getQuantity(),
                        d.getReturnedQuantity(),
                        d.getRemainingQuantity(),
                        NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                        NumberUtil.formatThousands(d.getLineTotal().longValue())
                });
            } else {
                model.addRow(new Object[]{
                        d.getProductImageUrl() != null ? d.getProductImageUrl() : "",
                        d.getProductName(),
                        d.getQuantity(),
                        NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                        NumberUtil.formatThousands(d.getLineTotal().longValue())
                });
            }
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

        // Cot cuoi ("Thanh tien") thay doi vi tri tuy co hien cot Da tra/Con lai hay khong.
        int lastCol = columns.length - 1;
        int priceCol = hasAnyReturn ? 5 : 3;

        ImageColumn imageColumn = new ImageColumn(44, 10);
        RowColorProvider colors = (row, selected) -> rowBg(row, details);
        table.getColumnModel().getColumn(0).setCellRenderer(imageColumn.renderer(colors));
        table.getColumnModel().getColumn(0).setPreferredWidth(64);
        table.getColumnModel().getColumn(0).setMinWidth(60);
        table.getColumnModel().getColumn(0).setMaxWidth(72);
        table.getColumnModel().getColumn(1).setPreferredWidth(hasAnyReturn ? 200 : 260);
        table.getColumnModel().getColumn(2).setPreferredWidth(56);
        table.getColumnModel().getColumn(2).setMaxWidth(72);
        if (hasAnyReturn) {
            table.getColumnModel().getColumn(3).setPreferredWidth(64);
            table.getColumnModel().getColumn(3).setMaxWidth(80);
            table.getColumnModel().getColumn(4).setPreferredWidth(64);
            table.getColumnModel().getColumn(4).setMaxWidth(80);
        }
        table.getColumnModel().getColumn(priceCol).setPreferredWidth(110);
        table.getColumnModel().getColumn(lastCol).setPreferredWidth(120);

        DefaultTableCellRenderer nameRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(AppFont.BODY_BOLD);
                setForeground(AppColor.TEXT_PRIMARY);
                setBackground(rowBg(row, details));
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
                setBackground(rowBg(row, details));
                setForeground(AppColor.TEXT_PRIMARY);
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(center);
        if (hasAnyReturn) {
            // "Da tra" nhan manh mau canh bao, "Con lai" mau binh thuong.
            DefaultTableCellRenderer returnedCenter = new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                              boolean hasFocus, int row, int column) {
                    Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                    setHorizontalAlignment(SwingConstants.CENTER);
                    setBackground(rowBg(row, details));
                    boolean returned = row < details.size() && details.get(row).getReturnedQuantity() > 0;
                    setForeground(returned ? AppColor.WARNING : AppColor.TEXT_MUTED);
                    setFont(returned ? AppFont.BODY_BOLD : AppFont.BODY);
                    return c;
                }
            };
            table.getColumnModel().getColumn(3).setCellRenderer(returnedCenter);
            table.getColumnModel().getColumn(4).setCellRenderer(center);
        }

        DefaultTableCellRenderer money = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBackground(rowBg(row, details));
                setForeground(column == lastCol ? AppColor.ACCENT : AppColor.TEXT_PRIMARY);
                setFont(column == lastCol ? AppFont.BODY_BOLD : AppFont.BODY);
                setBorder(new EmptyBorder(0, 4, 0, 12));
                return c;
            }
        };
        table.getColumnModel().getColumn(priceCol).setCellRenderer(money);
        table.getColumnModel().getColumn(lastCol).setCellRenderer(money);

        return table;
    }

    /**
     * Mau nen dong theo trang thai da tra - ap dung cung bang mau nhu
     * InvoiceDetailDialog.applyRowStyle (vang nhat = da tra het, xanh nhat =
     * tra mot phan), giu nguyen mau trang cho dong chua tra.
     */
    private Color rowBg(int row, List<OrderDetail> details) {
        if (row >= details.size()) return AppColor.WHITE;
        OrderDetail d = details.get(row);
        if (d.isFullyReturned()) return new Color(0xFEF3C7);
        if (d.isPartiallyReturned()) return new Color(0xEFF6FF);
        return AppColor.WHITE;
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

        // Cho xuat PDF khi da co HĐ, hoặc đơn đã hoàn thành / PayPal đã thanh toán
        // (có thể lập bù HĐ hoặc xuất từ dữ liệu đơn để tra cứu lịch sử).
        boolean canExportPdf = order.getInvoiceId() != null
                || "COMPLETED".equalsIgnoreCase(order.getOrderStatus())
                || ("PAYPAL".equalsIgnoreCase(order.getPaymentMethod())
                    && "PAID".equalsIgnoreCase(order.getPaymentStatus()));
        if (canExportPdf) {
            JButton exportPdfButton = new JButton("Xuất hóa đơn PDF");
            exportPdfButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            exportPdfButton.setFocusPainted(false);
            exportPdfButton.setBackground(AppColor.ACCENT_BG_SOFT);
            exportPdfButton.setForeground(AppColor.ACCENT);
            exportPdfButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            exportPdfButton.setIcon(FontIcon.of(FontAwesomeSolid.FILE_PDF, 14, AppColor.ACCENT));
            exportPdfButton.setIconTextGap(8);
            exportPdfButton.addActionListener(e -> exportAndOpenPdf());
            footer.add(exportPdfButton);
        }

        boolean canManage = PermissionManager.getInstance().can(AppPermission.ORDER_MANAGE);
        boolean isNew = "NEW".equalsIgnoreCase(order.getOrderStatus());
        boolean isConfirmed = order.isConfirmed();
        boolean isShipping = order.isShipping();

        // Huy don chi cho phep o NEW/CONFIRMED - da giao cho DVVC (SHIPPING) thi khong huy duoc nua.
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

        if (canManage && isConfirmed) {
            JButton shipButton = new JButton("Bắt đầu giao");
            shipButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            shipButton.setFocusPainted(false);
            shipButton.setBackground(AppColor.ACCENT_BG_SOFT);
            shipButton.setForeground(AppColor.ACCENT);
            shipButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            shipButton.addActionListener(e -> handleShip());
            footer.add(shipButton);
        }

        if (canManage && isShipping) {
            JButton completeButton = new JButton("Hoàn thành");
            completeButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            completeButton.setFocusPainted(false);
            completeButton.setBackground(AppColor.SUCCESS_BG);
            completeButton.setForeground(AppColor.SUCCESS);
            completeButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            completeButton.addActionListener(e -> handleComplete());
            footer.add(completeButton);
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

    // ---------------------------------------------------------------
    // Xuat hoa don PDF cua don hang (dung lai hoa don da lap khi hoan
    // thanh don, dung chung InvoicePdfExporter voi trang POS / Hoa don).
    // ---------------------------------------------------------------

    private void exportAndOpenPdf() {
        try {
            Integer invoiceId = order.getInvoiceId();

            if (invoiceId == null) {
                boolean eligible = "COMPLETED".equalsIgnoreCase(order.getOrderStatus())
                        || ("PAYPAL".equalsIgnoreCase(order.getPaymentMethod())
                            && "PAID".equalsIgnoreCase(order.getPaymentStatus()));
                if (eligible) {
                    int actorId = AuthService.getInstance().getCurrentUser().getUserId();
                    invoiceId = orderDAO.ensureInvoiceForOrder(order.getOrderId(), actorId);
                    if (invoiceId != null) {
                        order.setInvoiceId(invoiceId);
                    }
                }
            }

            Invoice invoice = null;
            List<InvoiceDetail> details = null;

            if (invoiceId != null) {
                List<Invoice> found = invoiceDAO.getByCondition("inv.InvoiceID = " + invoiceId);
                if (!found.isEmpty()) {
                    invoice = found.get(0);
                    details = invoiceDAO.getDetails(invoice.getInvoiceId());
                }
            }

            if (invoice == null) {
                // Fallback: dựng từ dữ liệu đơn hàng để vẫn in được khi tra cứu lịch sử
                invoice = new Invoice();
                invoice.setInvoiceCode(order.getOrderCode() != null
                        ? "HD-" + order.getOrderCode() : "HD-ONLINE");
                invoice.setCustomerId(order.getCustomerId());
                invoice.setCustomerName(order.getCustomerName());
                invoice.setCreatedAt(order.getCompletedAt() != null
                        ? order.getCompletedAt() : order.getCreatedAt());
                invoice.setCreatedByName("Online");
                invoice.setSubTotal(order.getSubTotal());
                invoice.setDiscountAmount(order.getDiscountAmount());
                invoice.setPromotionCode(order.getPromotionCode());
                invoice.setTotalAmount(order.getTotalAmount());
                String pm = order.getPaymentMethod();
                if ("PAYPAL".equalsIgnoreCase(pm)) invoice.setPaymentMethod("PAYPAL");
                else if ("COD".equalsIgnoreCase(pm)) invoice.setPaymentMethod("CASH");
                else invoice.setPaymentMethod(pm);
                invoice.setStatus("ACTIVE");

                details = new ArrayList<>();
                for (OrderDetail line : orderDAO.getDetailsByOrderId(order.getOrderId())) {
                    InvoiceDetail d = new InvoiceDetail();
                    d.setProductId(line.getProductId());
                    d.setProductName(line.getProductName());
                    d.setQuantity(line.getQuantity());
                    d.setUnitPrice(line.getUnitPrice());
                    if (line.getLineTotal() != null) {
                        d.setLineTotal(line.getLineTotal());
                    } else if (line.getUnitPrice() != null) {
                        d.setLineTotal(line.getUnitPrice().multiply(
                                java.math.BigDecimal.valueOf(line.getQuantity())));
                    }
                    details.add(d);
                }
                if (details.isEmpty()) {
                    JOptionPane.showMessageDialog(this,
                            "Đơn hàng không có sản phẩm để xuất hóa đơn.",
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }

            String code = invoice.getInvoiceCode() != null
                    ? invoice.getInvoiceCode() : order.getOrderCode();
            String fileName = "HoaDon_" + code.replaceAll("[^a-zA-Z0-9]", "_") + ".pdf";
            File tempDir = new File(System.getProperty("java.io.tmpdir"), "sims_invoices");
            if (!tempDir.exists()) tempDir.mkdirs();
            File pdfFile = new File(tempDir, fileName);

            InvoicePdfExporter.exportInvoice(invoice, details, pdfFile);

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(pdfFile);
            } else {
                JOptionPane.showMessageDialog(this,
                        "Đã tạo file PDF tại:\n" + pdfFile.getAbsolutePath(),
                        "Xuất PDF", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi tạo file PDF: " + ex.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void handleConfirm() {
        boolean confirmed = BaseDialog.confirm(this, "Xác nhận đơn hàng",
                "Xác nhận đơn hàng " + order.getOrderCode() + "?");
        if (!confirmed) return;

        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(
                order.getOrderId(), "CONFIRMED", AuthService.getInstance().getCurrentUser().getUserId());
        if (!result.success) {
            BaseDialog.error(this, "Không thể xác nhận", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đã xác nhận đơn hàng " + order.getOrderCode() + ".");
        dispose();
    }

    private void handleShip() {
        boolean confirmed = BaseDialog.confirm(this, "Bắt đầu giao hàng",
                "Chuyển đơn hàng " + order.getOrderCode() + " sang trạng thái đang giao?");
        if (!confirmed) return;

        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(
                order.getOrderId(), "SHIPPING", AuthService.getInstance().getCurrentUser().getUserId());
        if (!result.success) {
            BaseDialog.error(this, "Không thể cập nhật", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đơn hàng " + order.getOrderCode() + " đang được giao.");
        dispose();
    }

    private void handleComplete() {
        boolean confirmed = BaseDialog.confirm(this, "Hoàn thành đơn hàng",
                "Xác nhận đơn hàng " + order.getOrderCode() + " đã giao thành công?");
        if (!confirmed) return;

        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(
                order.getOrderId(), "COMPLETED", AuthService.getInstance().getCurrentUser().getUserId());
        if (!result.success) {
            BaseDialog.error(this, "Không thể cập nhật", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đơn hàng " + order.getOrderCode() + " đã hoàn thành.");
        dispose();
    }

    private void handleCancel() {
        boolean confirmed = BaseDialog.confirm(this, "Hủy đơn hàng",
                "Bạn có chắc muốn hủy đơn hàng " + order.getOrderCode() + "?");
        if (!confirmed) return;

        OrderDAO.StatusUpdateResult result = orderDAO.updateOrderStatus(
                order.getOrderId(), "CANCELLED", AuthService.getInstance().getCurrentUser().getUserId());
        if (!result.success) {
            BaseDialog.error(this, "Không thể hủy đơn", result.errorMessage);
            return;
        }

        BaseDialog.success(this, "Thành công",
                "Đã hủy đơn hàng " + order.getOrderCode() + ".");
        dispose();
    }
}