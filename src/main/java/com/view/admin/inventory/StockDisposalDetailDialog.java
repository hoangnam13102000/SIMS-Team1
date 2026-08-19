package com.view.admin.inventory;

import com.dao.StockDisposalDAO;
import com.model.StockDisposal;
import com.model.StockDisposalDetail;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Dialog chi tiết phiếu tiêu hủy (hủy hàng) — UI đồng bộ với {@link PurchaseReceiptDetailDialog}.
 * <ul>
 *   <li>Header: icon badge + mã phiếu + subtitle + status pill</li>
 *   <li>Body: card thông tin lưới 2 cột + bảng dòng sản phẩm + tổng thiệt hại</li>
 *   <li>Footer: nút Đóng</li>
 * </ul>
 */
public class StockDisposalDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final long EXPIRY_WARNING_DAYS = 30;

    private final StockDisposal disposal;
    private final List<StockDisposalDetail> details;

    public StockDisposalDetailDialog(Frame owner, StockDisposal disposal, StockDisposalDAO dao) {
        super(owner, "Chi tiết phiếu tiêu hủy", Dialog.ModalityType.APPLICATION_MODAL);
        this.disposal = disposal;
        this.details = dao.getDetails(disposal.getDisposalId());

        setSize(880, 660);
        setMinimumSize(new Dimension(640, 500));
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
    // Header: icon + mã phiếu + trạng thái (giống phiếu nhập kho)
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        boolean cancelled = disposal.isCancelled();
        // Tiêu hủy: icon trash; trạng thái hủy phiếu = ERROR, hoàn tất = SUCCESS
        Color statusIconColor = cancelled ? AppColor.ERROR : AppColor.SUCCESS;
        Color statusIconBg = cancelled ? AppColor.ERROR_BG : AppColor.SUCCESS_BG;
        FontIcon icon = FontIcon.of(FontAwesomeSolid.TRASH, 18);
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

        JLabel titleLabel = new JLabel(emptyDash(disposal.getDisposalCode()));
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitleLabel = new JLabel(emptyDash(disposal.getReasonLabel())
                + "  \u00b7  " + (cancelled ? "Đã hủy" : "Hoàn tất"));
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(cancelled ? AppColor.ERROR : AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        header.add(statusPill(cancelled), BorderLayout.EAST);
        return header;
    }

    private JLabel statusPill(boolean cancelled) {
        JLabel pill = new JLabel(cancelled ? "Đã hủy" : "Hoàn tất", SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(cancelled ? AppColor.ERROR_BG : AppColor.SUCCESS_BG);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setFont(AppFont.SMALL_BOLD);
        pill.setForeground(cancelled ? AppColor.ERROR : AppColor.SUCCESS);
        pill.setBorder(new EmptyBorder(6, 16, 6, 16));
        pill.setVerticalAlignment(SwingConstants.CENTER);
        return pill;
    }

    // ---------------------------------------------------------------
    // Body: card thông tin lưới 2 cột + bảng dòng sản phẩm
    // ---------------------------------------------------------------

    private JScrollPane buildBody(List<StockDisposalDetail> details) {
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
        infoCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        JPanel infoGrid = new JPanel(new GridLayout(0, 2, 28, 14));
        infoGrid.setOpaque(false);
        infoGrid.add(infoCell("Lý do tiêu hủy", disposal.getReasonLabel()));
        infoGrid.add(infoCell("Người tạo phiếu", disposal.getCreatedByName()));
        infoGrid.add(infoCell("Ngày tạo",
                disposal.getCreatedAt() != null
                        ? disposal.getCreatedAt().format(DATE_TIME_FORMAT) : "-"));
        infoGrid.add(infoCell("Số dòng sản phẩm", String.valueOf(details.size())));
        infoGrid.add(infoCell("Trạng thái", disposal.isCancelled() ? "Đã hủy" : "Hoàn tất"));
        infoGrid.add(infoCell("Ghi chú",
                disposal.getNote() != null && !disposal.getNote().isBlank()
                        ? disposal.getNote() : "-"));
        long loss = disposal.getTotalLossAmount() != null
                ? disposal.getTotalLossAmount().longValue() : 0;
        infoGrid.add(infoCellTotal("Tổng thiệt hại",
                NumberUtil.formatThousands(loss) + " đ"));

        infoCard.add(infoGrid, BorderLayout.CENTER);
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
        int tableH = Math.max(80, Math.min(260, 38 + details.size() * 34));
        tableScroll.setPreferredSize(new Dimension(700, tableH));
        tableScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, tableH + 20));
        tableScroll.getViewport().setBackground(AppColor.WHITE);
        tableScroll.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        content.add(tableScroll);

        content.add(Box.createVerticalStrut(12));
        content.add(buildTotalSummary());

        if (details.stream().anyMatch(this::isNearOrPastExpiry)) {
            content.add(Box.createVerticalStrut(12));
            content.add(expiryNotice());
        }

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

        JLabel valueComp = new JLabel(emptyDash(value));
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

        JLabel valueComp = new JLabel(emptyDash(value));
        valueComp.setFont(AppFont.BODY_BOLD);
        valueComp.setForeground(AppColor.ERROR);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(2));
        cell.add(valueComp);
        return cell;
    }

    // ---------------------------------------------------------------
    // Bảng dòng sản phẩm
    // ---------------------------------------------------------------

    private JTable buildDetailTable(List<StockDisposalDetail> details) {
        String[] columns = {
                "Mã sản phẩm", "Sản phẩm", "Mã lô (hệ thống)",
                "HSD", "SL hủy", "Đơn giá vốn", "Thiệt hại"
        };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        for (StockDisposalDetail d : details) {
            model.addRow(new Object[]{
                    emptyDash(d.getProductCode()),
                    emptyDash(d.getProductName()),
                    emptyDash(d.getBatchCode()),
                    d.getExpiryDate() != null ? d.getExpiryDate().format(DATE_FORMAT) : "-",
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitCost() != null
                            ? d.getUnitCost().longValue() : 0),
                    NumberUtil.formatThousands(d.getLineLossAmount() != null
                            ? d.getLineLossAmount().longValue() : 0)
            });
        }

        JTable table = new JTable(model);
        table.setFont(AppFont.BODY);
        table.setRowHeight(34);
        table.setBackground(AppColor.WHITE);
        table.setForeground(AppColor.TEXT_PRIMARY);
        table.setSelectionBackground(AppColor.ACCENT_BG_SOFT);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD);
        table.getTableHeader().setBackground(AppColor.BG_LIGHT);
        table.getTableHeader().setForeground(AppColor.TEXT_PRIMARY);
        table.getTableHeader().setPreferredSize(new Dimension(0, 36));
        table.getTableHeader().setReorderingAllowed(false);
        table.setGridColor(AppColor.TABLE_GRID);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setIntercellSpacing(new Dimension(0, 0));

        table.getColumnModel().getColumn(0).setPreferredWidth(110);
        table.getColumnModel().getColumn(1).setPreferredWidth(180);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(70);
        table.getColumnModel().getColumn(4).setMaxWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(110);

        // Mã SP: đậm
        DefaultTableCellRenderer codeRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setFont(AppFont.BODY_BOLD);
                setForeground(AppColor.TEXT_PRIMARY);
                setBackground(AppColor.WHITE);
                setBorder(new EmptyBorder(0, 10, 0, 4));
                return c;
            }
        };
        table.getColumnModel().getColumn(0).setCellRenderer(codeRenderer);

        // Mã lô hệ thống: accent
        DefaultTableCellRenderer batchCodeRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setForeground(AppColor.ACCENT);
                setFont(AppFont.SMALL_BOLD);
                setBackground(AppColor.WHITE);
                return c;
            }
        };
        table.getColumnModel().getColumn(2).setCellRenderer(batchCodeRenderer);

        // HSD: cảnh báo sắp/đã hết hạn
        DefaultTableCellRenderer expiry = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setBackground(AppColor.WHITE);
                LocalDate exp = details.get(row).getExpiryDate();
                if (exp != null && exp.isBefore(LocalDate.now())) {
                    setForeground(AppColor.ERROR);
                    setFont(AppFont.BODY_BOLD);
                } else if (exp != null && !exp.isAfter(LocalDate.now().plusDays(EXPIRY_WARNING_DAYS))) {
                    setForeground(AppColor.WARNING);
                    setFont(AppFont.BODY_BOLD);
                } else {
                    setForeground(AppColor.TEXT_PRIMARY);
                    setFont(AppFont.BODY);
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(3).setCellRenderer(expiry);

        // SL hủy: căn giữa
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
        table.getColumnModel().getColumn(4).setCellRenderer(center);

        // Đơn giá vốn + thiệt hại: căn phải; thiệt hại màu ERROR
        DefaultTableCellRenderer money = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                             boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setBackground(AppColor.WHITE);
                setForeground(column == 6 ? AppColor.ERROR : AppColor.TEXT_PRIMARY);
                setFont(column == 6 ? AppFont.BODY_BOLD : AppFont.BODY);
                setBorder(new EmptyBorder(0, 4, 0, 12));
                return c;
            }
        };
        table.getColumnModel().getColumn(5).setCellRenderer(money);
        table.getColumnModel().getColumn(6).setCellRenderer(money);

        return table;
    }

    private boolean isNearOrPastExpiry(StockDisposalDetail d) {
        LocalDate exp = d.getExpiryDate();
        return exp != null && !exp.isAfter(LocalDate.now().plusDays(EXPIRY_WARNING_DAYS));
    }

    private JPanel expiryNotice() {
        JPanel notice = new JPanel(new BorderLayout(8, 0));
        notice.setOpaque(true);
        notice.setBackground(AppColor.WARNING_BG);
        notice.setBorder(new EmptyBorder(10, 14, 10, 14));
        notice.setAlignmentX(Component.LEFT_ALIGNMENT);
        notice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCLAMATION_TRIANGLE, 14);
        icon.setIconColor(AppColor.WARNING);
        JLabel iconLabel = new JLabel(icon);

        JLabel text = new JLabel("Phiếu có lô hàng sắp hết hạn hoặc đã hết hạn sử dụng.");
        text.setFont(AppFont.SMALL_BOLD);
        text.setForeground(AppColor.WARNING);

        notice.add(iconLabel, BorderLayout.WEST);
        notice.add(text, BorderLayout.CENTER);
        return notice;
    }

    private JPanel buildTotalSummary() {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel label = new JLabel("Tổng thiệt hại:  ");
        label.setFont(AppFont.BODY);
        label.setForeground(AppColor.TEXT_MUTED);

        long loss = disposal.getTotalLossAmount() != null
                ? disposal.getTotalLossAmount().longValue() : 0;
        JLabel value = new JLabel(NumberUtil.formatThousands(loss) + " đ");
        value.setFont(AppFont.HEADING_MD);
        value.setForeground(AppColor.ERROR);

        row.add(label);
        row.add(value);
        return row;
    }

    private static String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    // ---------------------------------------------------------------
    // Footer: nút Đóng (giống phiếu nhập kho)
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

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
}
