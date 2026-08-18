package com.view.admin.inventory;

import com.dao.SupplierReturnDAO;
import com.model.SupplierReturn;
import com.model.SupplierReturnDetail;
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
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
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
 * Dialog chi xem chi tiet 1 phieu tra hang NCC.
 */
public class SupplierReturnDetailDialog extends JDialog {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // Width tối thiểu cho từng cột (sàn dưới) — width thực tế sẽ do autoFitDetailColumns()
    // tính theo nội dung, nhưng không nhỏ hơn các giá trị này để tránh cột quá chật.
    private static final int[] DETAIL_MIN_COLUMN_WIDTHS = {80, 80, 140, 90, 90, 70, 90, 100};

    public SupplierReturnDetailDialog(Frame owner, SupplierReturn ret, SupplierReturnDAO dao) {
        super(owner, "Chi tiết phiếu trả hàng NCC", Dialog.ModalityType.APPLICATION_MODAL);
        List<SupplierReturnDetail> details = dao.getDetails(ret.getSupplierReturnId());

        setSize(820, 600);
        setMinimumSize(new Dimension(680, 460));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        Color pageBg = AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(248, 250, 252);
        getContentPane().setBackground(pageBg);

        add(buildHeader(ret), BorderLayout.NORTH);
        add(buildBody(ret, details), BorderLayout.CENTER);
        add(buildFooter(ret), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader(SupplierReturn r) {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                new EmptyBorder(18, 24, 16, 24)));

        Color accent = AppColor.ACCENT != null ? AppColor.ACCENT : new Color(37, 99, 235);
        Color accentBg = AppColor.ACCENT_BG_SOFT != null ? AppColor.ACCENT_BG_SOFT : new Color(219, 234, 254);
        FontIcon icon = FontIcon.of(FontAwesomeSolid.UNDO, 18, accent);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(accentBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(48, 48));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel code = new JLabel(nullSafe(r.getSupplierReturnCode()) + "  —  " + nullSafe(r.getSupplierName()));
        code.setFont(AppFont.DIALOG_TITLE != null ? AppFont.DIALOG_TITLE : new Font("Segoe UI", Font.BOLD, 20));
        code.setForeground(AppColor.TEXT_PRIMARY);
        code.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(chip(r.getReasonLabel(),
                AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(254, 243, 199),
                AppColor.WARNING != null ? AppColor.WARNING : new Color(180, 83, 9)));
        chips.add(chip(r.isCancelled() ? "Đã hủy" : "Hoàn tất",
                r.isCancelled()
                        ? (AppColor.ERROR_BG != null ? AppColor.ERROR_BG : new Color(254, 226, 226))
                        : (AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(220, 252, 231)),
                r.isCancelled() ? AppColor.ERROR : AppColor.SUCCESS));

        titles.add(code);
        titles.add(Box.createVerticalStrut(8));
        titles.add(chips);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody(SupplierReturn r, List<SupplierReturnDetail> details) {
        // BorderLayout thay vì BoxLayout trong JScrollPane: infoCard cố định chiều cao ở
        // NORTH, tableCard chiếm CENTER — CENTER luôn được cấp phát toàn bộ không gian còn
        // lại của dialog, nên khi resize dialog to/nhỏ, khu vực danh sách lô trả hàng sẽ tự
        // giãn/co theo, thay vì giữ chiều cao cố định như trước.
        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(16, 20, 12, 20));

        content.add(infoCard(r), BorderLayout.NORTH);
        content.add(tableCard(details), BorderLayout.CENTER);
        return content;
    }

    private JPanel infoCard(SupplierReturn r) {
        JPanel card = whiteCard();
        card.setLayout(new GridLayout(1, 3, 16, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        card.add(statCell(FontAwesomeSolid.USER, "Người lập", nullSafe(r.getCreatedByName())));
        card.add(statCell(FontAwesomeSolid.CALENDAR_ALT, "Ngày lập",
                r.getCreatedAt() != null ? r.getCreatedAt().format(DT) : "—"));
        String note = (r.getNote() != null && !r.getNote().isBlank()) ? r.getNote() : "—";
        card.add(statCell(FontAwesomeSolid.STICKY_NOTE, "Ghi chú", note));
        return card;
    }

    private JPanel statCell(FontAwesomeSolid fa, String label, String value) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel lb = new JLabel(label);
        lb.setFont(AppFont.SMALL_BOLD != null ? AppFont.SMALL_BOLD : new Font("Segoe UI", Font.BOLD, 11));
        lb.setForeground(AppColor.TEXT_MUTED);
        lb.setIcon(FontIcon.of(fa, 11, AppColor.TEXT_MUTED));
        lb.setIconTextGap(6);
        lb.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel("<html><body style='width:160px'>" + escape(value) + "</body></html>");
        val.setFont(AppFont.BODY != null ? AppFont.BODY : new Font("Segoe UI", Font.PLAIN, 13));
        val.setForeground(AppColor.TEXT_PRIMARY);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(lb);
        p.add(Box.createVerticalStrut(6));
        p.add(val);
        return p;
    }

    private JPanel tableCard(List<SupplierReturnDetail> details) {
        JPanel card = whiteCard();
        card.setLayout(new BorderLayout(0, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel section = new JLabel("Danh sách lô trả hàng");
        section.setFont(AppFont.BODY_BOLD != null ? AppFont.BODY_BOLD : new Font("Segoe UI", Font.BOLD, 13));
        section.setForeground(AppColor.TEXT_PRIMARY);
        JLabel count = new JLabel(details.size() + " dòng");
        count.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        count.setForeground(AppColor.TEXT_MUTED);
        top.add(section, BorderLayout.WEST);
        top.add(count, BorderLayout.EAST);

        JTable table = buildTable(details);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createLineBorder(softBorder(), 1));
        scroll.getViewport().setBackground(AppColor.WHITE);
        // ====== THANH CUỘN NGANG cho bảng chi tiết ======
        // AUTO_RESIZE_OFF (set trong buildTable) + width cột tự tính theo nội dung
        // (autoFitDetailColumns) — khi tổng width vượt khung nhìn dialog, JScrollPane tự
        // hiện scrollbar ngang; khi không vượt, scrollbar tự ẩn.
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        int totalColumnWidth = 0;
        for (int col = 0; col < table.getColumnCount(); col++) {
            totalColumnWidth += table.getColumnModel().getColumn(col).getPreferredWidth();
        }
        // Chiều cao không còn set cứng — tableCard giờ nằm ở CENTER của content (BorderLayout),
        // nên sẽ tự nhận toàn bộ chiều cao còn lại của dialog và co giãn khi resize.
        // preferredSize width vẫn giữ để layout cha tính preferred-size hợp lý (không dùng
        // cho việc giới hạn kích thước thực tế lúc runtime).
        scroll.setPreferredSize(new Dimension(totalColumnWidth, 240));
        // ==================================================

        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JTable buildTable(List<SupplierReturnDetail> details) {
        String[] cols = {"Mã lô", "Phiếu nhập", "Sản phẩm", "Mã sản phẩm", "HSD", "SL trả", "Đơn giá", "Hoàn tiền"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (SupplierReturnDetail d : details) {
            model.addRow(new Object[]{
                    d.getBatchCode(),
                    d.getReceiptCode() != null ? d.getReceiptCode() : "—",
                    d.getProductName(),
                    d.getProductCode() != null ? d.getProductCode() : "—",
                    d.getExpiryDate() != null ? d.getExpiryDate().format(D) : "—",
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitRefundPrice() != null ? d.getUnitRefundPrice().longValue() : 0),
                    NumberUtil.formatThousands(d.getLineRefundAmount() != null ? d.getLineRefundAmount().longValue() : 0)
            });
        }

        JTable table = new JTable(model) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                if (!isRowSelected(row)) {
                    c.setBackground(row % 2 == 0 ? AppColor.WHITE
                            : (AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252)));
                }
                return c;
            }

            // Quan trọng cho cuộn ngang: KHÔNG cho bảng tự co giãn để lấp đầy viewport theo
            // chiều ngang. Nếu để mặc định (true khi autoResize != OFF), JTable sẽ ép các
            // cột co lại vừa khung thay vì để JScrollPane hiện thanh cuộn ngang.
            @Override
            public boolean getScrollableTracksViewportWidth() {
                return getPreferredSize().width < getParent().getWidth();
            }
        };
        table.setFont(AppFont.BODY != null ? AppFont.BODY : new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(34);
        table.getTableHeader().setFont(AppFont.SMALL_BOLD != null ? AppFont.SMALL_BOLD : new Font("Segoe UI", Font.BOLD, 12));
        table.getTableHeader().setBackground(AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : new Color(241, 245, 249));
        table.getTableHeader().setForeground(AppColor.TEXT_MUTED);
        table.getTableHeader().setReorderingAllowed(false);
        table.setGridColor(softBorder());
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setFillsViewportHeight(true);
        table.setRowSelectionAllowed(false);
        table.setFocusable(false);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

        // Width khởi tạo tạm — sẽ được autoFitDetailColumns() tính lại chính xác theo nội
        // dung thật ngay bên dưới, sau khi renderer của từng cột đã được gán đầy đủ.
        int[] widths = DETAIL_MIN_COLUMN_WIDTHS;
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer center = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? AppColor.WHITE
                            : (AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252)));
                    setForeground(AppColor.TEXT_PRIMARY);
                }
                return c;
            }
        };

        for (int i = 0; i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        // Riêng cột Hoàn tiền: vẫn màu xanh + in đậm nhưng căn giữa.
        DefaultTableCellRenderer refundRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.CENTER);
                setForeground(AppColor.SUCCESS);
                setFont(getFont().deriveFont(Font.BOLD));
                setBorder(new EmptyBorder(0, 8, 0, 8));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? AppColor.WHITE
                            : (AppColor.BG_LIGHTER != null ? AppColor.BG_LIGHTER : new Color(248, 250, 252)));
                }
                return c;
            }
        };
        table.getColumnModel().getColumn(7).setCellRenderer(refundRenderer);

        // Header cũng căn giữa.
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(
                        t, value, isSelected, hasFocus, row, column);
                label.setHorizontalAlignment(SwingConstants.CENTER);
                label.setBorder(new EmptyBorder(0, 8, 0, 8));
                return label;
            }
        });

        // ====== TỰ GIÃN CỘT VỪA THEO NỘI DUNG (thay cho width cố định cứng trước đây) ======
        // Đo width thật của header + từng ô dữ liệu (dùng đúng renderer đã gán ở trên, kể cả
        // renderer refund/center), rồi set preferredWidth = max tìm được, có padding.
        autoFitDetailColumns(table);
        // =====================================================================================

        return table;
    }

    private void autoFitDetailColumns(JTable table) {
        int columnCount = table.getColumnCount();
        int rowCount = table.getRowCount();
        for (int col = 0; col < columnCount; col++) {
            javax.swing.table.TableColumn column = table.getColumnModel().getColumn(col);
            int maxWidth = col < DETAIL_MIN_COLUMN_WIDTHS.length ? DETAIL_MIN_COLUMN_WIDTHS[col] : 70;

            TableCellRenderer headerRenderer = table.getTableHeader().getDefaultRenderer();
            Component headerComp = headerRenderer.getTableCellRendererComponent(
                    table, column.getHeaderValue(), false, false, -1, col);
            maxWidth = Math.max(maxWidth, headerComp.getPreferredSize().width + 16);

            for (int row = 0; row < rowCount; row++) {
                TableCellRenderer cellRenderer = table.getCellRenderer(row, col);
                Component cellComp = table.prepareRenderer(cellRenderer, row, col);
                maxWidth = Math.max(maxWidth, cellComp.getPreferredSize().width + 16);
            }
            column.setPreferredWidth(maxWidth);
        }
    }

    private JPanel buildFooter(SupplierReturn r) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, softBorder()),
                new EmptyBorder(12, 24, 14, 24)));

        long refund = r.getTotalRefundAmount() != null ? r.getTotalRefundAmount().longValue() : 0;

        JPanel refundBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        refundBox.setOpaque(false);
        refundBox.add(new JLabel(FontIcon.of(FontAwesomeSolid.DOLLAR_SIGN, 16, AppColor.SUCCESS)));
        JLabel refundLabel = new JLabel("Tổng tiền hoàn (NCC nợ lại)");
        refundLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        refundLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel refundValue = new JLabel(NumberUtil.formatThousands(refund) + " đ");
        refundValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        refundValue.setForeground(AppColor.SUCCESS);
        refundBox.add(refundLabel);
        refundBox.add(refundValue);

        JButton close = new JButton("Đóng");
        close.setFont(new Font("Segoe UI", Font.BOLD, 13));
        close.setFocusPainted(false);
        close.setBackground(AppColor.WHITE);
        close.setForeground(AppColor.TEXT_PRIMARY);
        close.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(8, 18, 8, 18)));
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.addActionListener(e -> dispose());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        right.setOpaque(false);
        right.add(close);

        footer.add(refundBox, BorderLayout.WEST);
        footer.add(right, BorderLayout.EAST);
        getRootPane().setDefaultButton(close);
        return footer;
    }

    private JPanel whiteCard() {
        JPanel p = new JPanel();
        p.setBackground(AppColor.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(softBorder(), 1),
                new EmptyBorder(14, 16, 14, 16)));
        p.setAlignmentX(Component.LEFT_ALIGNMENT);
        return p;
    }

    private JLabel chip(String text, Color bg, Color fg) {
        JLabel chip = new JLabel(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        chip.setOpaque(false);
        chip.setFont(new Font("Segoe UI", Font.BOLD, 11));
        chip.setForeground(fg);
        chip.setBorder(new EmptyBorder(4, 10, 4, 10));
        return chip;
    }

    private static Color softBorder() {
        return AppColor.BORDER != null ? AppColor.BORDER : new Color(226, 232, 240);
    }

    private static String nullSafe(String s) {
        return s != null && !s.isBlank() ? s : "—";
    }

    private static String escape(String s) {
        if (s == null) return "—";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}