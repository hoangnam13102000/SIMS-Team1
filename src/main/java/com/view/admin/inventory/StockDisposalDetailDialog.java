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
 * Dialog chi xem chi tiet 1 phieu tieu huy — UI hien dai (card + chip + zebra table).
 */
public class StockDisposalDetailDialog extends JDialog {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter D = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public StockDisposalDetailDialog(Frame owner, StockDisposal disposal, StockDisposalDAO dao) {
        super(owner, "Chi tiết phiếu tiêu hủy", Dialog.ModalityType.APPLICATION_MODAL);
        List<StockDisposalDetail> details = dao.getDetails(disposal.getDisposalId());

        setSize(760, 580);
        setMinimumSize(new Dimension(640, 460));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        Color pageBg = AppColor.PAGE_BG != null ? AppColor.PAGE_BG : new Color(248, 250, 252);
        getContentPane().setBackground(pageBg);

        add(buildHeader(disposal), BorderLayout.NORTH);
        add(buildBody(disposal, details), BorderLayout.CENTER);
        add(buildFooter(disposal), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader(StockDisposal d) {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, softBorder()),
                new EmptyBorder(18, 24, 16, 24)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.TRASH, 18, AppColor.ERROR);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ERROR_BG != null ? AppColor.ERROR_BG : new Color(254, 226, 226));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(48, 48));

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel code = new JLabel(nullSafe(d.getDisposalCode()));
        code.setFont(AppFont.DIALOG_TITLE != null ? AppFont.DIALOG_TITLE : new Font("Segoe UI", Font.BOLD, 20));
        code.setForeground(AppColor.TEXT_PRIMARY);
        code.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        chips.setAlignmentX(Component.LEFT_ALIGNMENT);
        chips.add(chip(d.getReasonLabel(),
                AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(254, 243, 199),
                AppColor.WARNING != null ? AppColor.WARNING : new Color(180, 83, 9)));
        chips.add(chip(d.isCancelled() ? "Đã hủy" : "Hoàn tất",
                d.isCancelled()
                        ? (AppColor.ERROR_BG != null ? AppColor.ERROR_BG : new Color(254, 226, 226))
                        : (AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(220, 252, 231)),
                d.isCancelled() ? AppColor.ERROR : AppColor.SUCCESS));

        titles.add(code);
        titles.add(Box.createVerticalStrut(8));
        titles.add(chips);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titles, BorderLayout.CENTER);
        return header;
    }

    private JComponent buildBody(StockDisposal d, List<StockDisposalDetail> details) {
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(16, 20, 12, 20));

        content.add(infoCard(d));
        content.add(Box.createVerticalStrut(14));
        content.add(tableCard(details));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel infoCard(StockDisposal d) {
        JPanel card = whiteCard();
        card.setLayout(new GridLayout(1, 3, 16, 0));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));

        card.add(statCell(FontAwesomeSolid.USER, "Người lập", nullSafe(d.getCreatedByName())));
        card.add(statCell(FontAwesomeSolid.CALENDAR_ALT, "Ngày lập",
                d.getCreatedAt() != null ? d.getCreatedAt().format(DT) : "—"));
        String note = (d.getNote() != null && !d.getNote().isBlank()) ? d.getNote() : "—";
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

    private JPanel tableCard(List<StockDisposalDetail> details) {
        JPanel card = whiteCard();
        card.setLayout(new BorderLayout(0, 10));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel section = new JLabel("Danh sách lô tiêu hủy");
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
        scroll.setPreferredSize(new Dimension(0, Math.min(280, 40 + details.size() * 36)));

        card.add(top, BorderLayout.NORTH);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JTable buildTable(List<StockDisposalDetail> details) {
        String[] cols = {"Mã lô", "Sản phẩm", "HSD", "SL hủy", "Giá vốn", "Tổn thất"};
        DefaultTableModel model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        for (StockDisposalDetail d : details) {
            model.addRow(new Object[]{
                    d.getBatchCode(),
                    d.getProductName(),
                    d.getExpiryDate() != null ? d.getExpiryDate().format(D) : "—",
                    d.getQuantity(),
                    NumberUtil.formatThousands(d.getUnitCost() != null ? d.getUnitCost().longValue() : 0),
                    NumberUtil.formatThousands(d.getLineLossAmount() != null ? d.getLineLossAmount().longValue() : 0)
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

        int[] widths = {110, 180, 100, 70, 100, 110};
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        table.getColumnModel().getColumn(3).setCellRenderer(right);
        table.getColumnModel().getColumn(4).setCellRenderer(right);

        DefaultTableCellRenderer lossRenderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(SwingConstants.RIGHT);
                setForeground(AppColor.ERROR);
                setFont(getFont().deriveFont(Font.BOLD));
                return c;
            }
        };
        table.getColumnModel().getColumn(5).setCellRenderer(lossRenderer);
        return table;
    }

    private JPanel buildFooter(StockDisposal d) {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, softBorder()),
                new EmptyBorder(12, 24, 14, 24)));

        long loss = d.getTotalLossAmount() != null ? d.getTotalLossAmount().longValue() : 0;

        JPanel lossBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        lossBox.setOpaque(false);
        lossBox.add(new JLabel(FontIcon.of(FontAwesomeSolid.CHART_LINE, 16, AppColor.ERROR)));
        JLabel lossLabel = new JLabel("Tổng tổn thất");
        lossLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lossLabel.setForeground(AppColor.TEXT_MUTED);
        JLabel lossValue = new JLabel(NumberUtil.formatThousands(loss) + " đ");
        lossValue.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lossValue.setForeground(AppColor.ERROR);
        lossBox.add(lossLabel);
        lossBox.add(lossValue);

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

        footer.add(lossBox, BorderLayout.WEST);
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