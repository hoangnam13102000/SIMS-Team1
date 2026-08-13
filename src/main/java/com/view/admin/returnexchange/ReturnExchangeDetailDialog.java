package com.view.admin.returnexchange;

import com.components.BaseDialog;
import com.dao.ReturnExchangeDAO;
import com.i18n.Lang;
import com.model.ReturnExchange;
import com.model.ReturnExchangeDetail;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
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
import java.awt.Color;
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

public class ReturnExchangeDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReturnExchangeDAO returnExchangeDAO;
    private final ReturnExchange item;

    public ReturnExchangeDetailDialog(Frame owner, ReturnExchange item, ReturnExchangeDAO returnExchangeDAO) {
        super(owner, Lang.get("returnExchange.detail.title"), Dialog.ModalityType.APPLICATION_MODAL);
        this.item = item;
        this.returnExchangeDAO = returnExchangeDAO;
        List<ReturnExchangeDetail> details = returnExchangeDAO.getDetails(item.getReturnId());

        setSize(760, 660);
        setMinimumSize(new Dimension(620, 500));
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

        Color statusIconColor = statusColor();
        Color statusIconBg = statusBgColor();
        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCHANGE_ALT, 18);
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

        JLabel titleLabel = new JLabel(Lang.get(
                item.isExchange() ? "returnExchange.detail.titleExchange" : "returnExchange.detail.titleReturn",
                item.getInvoiceCode()));
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(statusLabel());
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(statusIconColor);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body: card thông tin 2 cột + bảng SP
    // ---------------------------------------------------------------

    private JScrollPane buildBody(List<ReturnExchangeDetail> details) {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        JPanel infoCard = new JPanel(new BorderLayout());
        infoCard.setOpaque(true);
        infoCard.setBackground(AppColor.BG_LIGHT);
        infoCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));
        infoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 360));

        JPanel cardInner = new JPanel();
        cardInner.setOpaque(false);
        cardInner.setLayout(new BoxLayout(cardInner, BoxLayout.Y_AXIS));

        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 28, 14));
        infoGrid.setOpaque(false);
        infoGrid.add(infoCell(Lang.get("returnExchange.detail.info.invoice"), item.getInvoiceCode()));
        infoGrid.add(infoCell(Lang.get("returnExchange.detail.info.type"),
                item.isExchange() ? Lang.get("returnExchange.type.exchange") : Lang.get("returnExchange.type.return")));
        infoGrid.add(infoCell(Lang.get("returnExchange.detail.info.createdBy"),
                item.getCreatedByName() != null ? item.getCreatedByName() : "-"));
        infoGrid.add(infoCell(Lang.get("returnExchange.detail.info.createdAt"),
                item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-"));
        infoGrid.add(infoCell(Lang.get("returnExchange.detail.info.requiresApproval"),
                item.isRequiresApproval() ? Lang.get("returnExchange.detail.info.requiresApprovalYes") : Lang.get("returnExchange.bool.no")));
        infoGrid.add(infoCellTotal(Lang.get("returnExchange.detail.info.value"),
                NumberUtil.formatThousands(item.getTotalValue() != null ? item.getTotalValue().longValue() : 0) + " đ"));
        cardInner.add(infoGrid);

        boolean hasDiscountShare = item.getDiscountShare() != null && item.getDiscountShare().signum() > 0;
        boolean hasPointsShare = item.getPointsShare() != null && item.getPointsShare().signum() > 0;
        if (hasDiscountShare || hasPointsShare) {
            JPanel shareRow = new JPanel(new GridLayout(0, 2, 28, 14));
            shareRow.setOpaque(false);
            shareRow.setBorder(new EmptyBorder(12, 0, 0, 0));
            if (hasDiscountShare) {
                shareRow.add(infoCell(Lang.get("returnExchange.detail.info.discountShare"),
                        "-" + NumberUtil.formatThousands(item.getDiscountShare().longValue()) + " đ"));
            }
            if (hasPointsShare) {
                shareRow.add(infoCell(Lang.get("returnExchange.detail.info.pointsShare"),
                        "-" + NumberUtil.formatThousands(item.getPointsShare().longValue()) + " đ"));
            }
            cardInner.add(shareRow);
        }

        JPanel reasonRow = new JPanel(new GridLayout(0, 1, 0, 4));
        reasonRow.setOpaque(false);
        reasonRow.setBorder(new EmptyBorder(12, 0, 0, 0));
        reasonRow.add(infoCell(Lang.get("returnExchange.detail.info.reason"), item.getReason()));
        cardInner.add(reasonRow);

        if (!item.isPending()) {
            JPanel approveRow = new JPanel(new GridLayout(0, 2, 28, 14));
            approveRow.setOpaque(false);
            approveRow.setBorder(new EmptyBorder(12, 0, 0, 0));
            approveRow.add(infoCell(item.isApproved()
                    ? Lang.get("returnExchange.detail.info.approvedBy") : Lang.get("returnExchange.detail.info.rejectedBy"),
                    item.getApprovedByName() != null ? item.getApprovedByName() : "-"));
            approveRow.add(infoCell(Lang.get("returnExchange.detail.info.processedAt"),
                    item.getApprovedAt() != null ? item.getApprovedAt().format(DATE_TIME_FORMAT) : "-"));
            cardInner.add(approveRow);

            if (item.isRejected()) {
                JPanel rejectionRow = new JPanel(new GridLayout(0, 1, 0, 4));
                rejectionRow.setOpaque(false);
                rejectionRow.setBorder(new EmptyBorder(12, 0, 0, 0));
                rejectionRow.add(infoCell("Lý do từ chối",
                        item.getRejectionReason() != null && !item.getRejectionReason().isBlank()
                                ? item.getRejectionReason() : "Chưa có lý do từ chối"));
                cardInner.add(rejectionRow);
            }
        }

        infoCard.add(cardInner, BorderLayout.CENTER);
        content.add(infoCard);
        content.add(Box.createVerticalStrut(20));

        JLabel sectionLabel = new JLabel(Lang.get("returnExchange.detail.products", details.size()));
        sectionLabel.setFont(AppFont.BODY_BOLD);
        sectionLabel.setForeground(AppColor.TEXT_PRIMARY);
        sectionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(sectionLabel);
        content.add(Box.createVerticalStrut(10));

        JTable table = buildDetailTable(details);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        int tableH = Math.max(100, Math.min(240, 44 + details.size() * 44));
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

    private JTable buildDetailTable(List<ReturnExchangeDetail> details) {
        String[] columns = {
                Lang.get("returnExchange.detail.col.direction"), Lang.get("returnExchange.detail.col.product"),
                Lang.get("returnExchange.detail.col.qty"), Lang.get("returnExchange.detail.col.unitPrice"),
                Lang.get("returnExchange.detail.col.lineTotal")
        };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };

        for (ReturnExchangeDetail d : details) {
            model.addRow(new Object[]{
                    d.isIn() ? Lang.get("returnExchange.detail.direction.in") : Lang.get("returnExchange.detail.direction.out"),
                    d.getProductName(),
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                    NumberUtil.formatThousands(d.getLineTotal().longValue())
            });
        }

        JTable table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(40);
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
        table.setRowSelectionAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setIntercellSpacing(new Dimension(0, 0));

        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(0).setMaxWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(240);
        table.getColumnModel().getColumn(2).setPreferredWidth(50);
        table.getColumnModel().getColumn(2).setMaxWidth(60);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);
        table.getColumnModel().getColumn(4).setPreferredWidth(120);

        DefaultTableCellRenderer directionRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                          boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(AppFont.SMALL_BOLD);
                setForeground(Lang.get("returnExchange.detail.direction.in").equals(value) ? AppColor.INFO : AppColor.ACCENT);
                setBackground(AppColor.WHITE);
                setBorder(new EmptyBorder(0, 8, 0, 4));
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(directionRenderer);

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

        boolean canApprove = item.isPending()
                && PermissionManager.getInstance().can(AppPermission.RETURN_EXCHANGE_APPROVE);

        if (canApprove) {
            JButton rejectButton = new JButton(Lang.get("returnExchange.detail.btn.reject"));
            rejectButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            rejectButton.setFocusPainted(false);
            rejectButton.setBackground(AppColor.ERROR_BG);
            rejectButton.setForeground(AppColor.ERROR);
            rejectButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            rejectButton.addActionListener(e -> handleReject());
            footer.add(rejectButton);

            JButton approveButton = new JButton(Lang.get("returnExchange.detail.btn.approve"));
            approveButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
            approveButton.setFocusPainted(false);
            approveButton.setBackground(AppColor.SUCCESS_BG);
            approveButton.setForeground(AppColor.SUCCESS);
            approveButton.setBorder(new EmptyBorder(8, 18, 8, 18));
            approveButton.addActionListener(e -> handleApprove());
            footer.add(approveButton);
        }

        JButton closeButton = new JButton(Lang.get("returnExchange.detail.btn.close"));
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

    private void handleApprove() {
        boolean confirmed = BaseDialog.confirm(this, Lang.get("returnExchange.detail.confirm.approve.title"),
                Lang.get("returnExchange.detail.confirm.approve.message", item.getInvoiceCode()),
                Lang.get("returnExchange.detail.confirm.approve.button"), AppColor.SUCCESS, AppColor.SUCCESS, FontAwesomeSolid.CHECK_CIRCLE);
        if (!confirmed) return;

        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        String error = returnExchangeDAO.approve(item.getReturnId(), currentUserId);
        if (error != null) {
            BaseDialog.error(this, Lang.get("returnExchange.detail.error.approveTitle"), error);
            return;
        }
        BaseDialog.success(this, Lang.get("returnExchange.detail.success.title"),
                Lang.get("returnExchange.detail.success.approved", item.getInvoiceCode()));
        dispose();
    }

    private void handleReject() {
        boolean confirmed = BaseDialog.confirm(this, Lang.get("returnExchange.detail.confirm.reject.title"),
                Lang.get("returnExchange.detail.confirm.reject.message", item.getInvoiceCode()),
                Lang.get("returnExchange.detail.confirm.reject.button"), AppColor.ERROR, AppColor.ERROR, FontAwesomeSolid.TIMES_CIRCLE);
        if (!confirmed) return;

        String rejectionReason = BaseDialog.inputText(this,
                "Lý do từ chối trả hàng",
                "Vui lòng nhập lý do từ chối để khách hàng có thể xem.",
                "",
                "Từ chối");
        if (rejectionReason == null) return;
        rejectionReason = rejectionReason.trim();
        if (rejectionReason.isEmpty()) {
            BaseDialog.error(this, "Thiếu lý do từ chối", "Bạn phải nhập lý do từ chối.");
            return;
        }

        int currentUserId = AuthService.getInstance().getCurrentUser().getUserId();
        String error = returnExchangeDAO.reject(item.getReturnId(), currentUserId, rejectionReason);
        if (error != null) {
            BaseDialog.error(this, Lang.get("returnExchange.detail.error.rejectTitle"), error);
            return;
        }
        BaseDialog.success(this, Lang.get("returnExchange.detail.success.title"),
                Lang.get("returnExchange.detail.success.rejected", item.getInvoiceCode()));
        dispose();
    }

    private String statusLabel() {
        if (item.isApproved()) return Lang.get("returnExchange.status.approved");
        if (item.isRejected()) return Lang.get("returnExchange.status.rejected");
        return Lang.get("returnExchange.status.pending");
    }

    private Color statusColor() {
        if (item.isApproved()) return AppColor.SUCCESS;
        if (item.isRejected()) return AppColor.ERROR;
        return AppColor.WARNING;
    }

    private Color statusBgColor() {
        if (item.isApproved()) return AppColor.SUCCESS_BG;
        if (item.isRejected()) return AppColor.ERROR_BG;
        return AppColor.WARNING_BG;
    }
}