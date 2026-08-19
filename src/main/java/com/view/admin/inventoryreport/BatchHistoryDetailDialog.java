package com.view.admin.inventoryreport;

import com.dao.InventoryReportDAO.BatchHistory;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.format.DateTimeFormatter;

/**
 * Form xem chi tiết một dòng trong "Lịch sử phiếu / hóa đơn làm thay đổi lô".
 *
 * Các trường được đưa vào form thay cho một số cột ít quan trọng trên bảng,
 * giúp bảng lịch sử gọn hơn nhưng vẫn giữ đầy đủ thông tin khi người dùng
 * bấm mắt "Xem chi tiết".
 */
public class BatchHistoryDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final BatchHistory history;

    public BatchHistoryDetailDialog(Frame owner, BatchHistory history) {
        super(owner, "Chi tiết lịch sử lô", Dialog.ModalityType.APPLICATION_MODAL);
        this.history = history;

        setSize(760, 620);
        setMinimumSize(new Dimension(620, 500));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        Color directionColor = "IN".equalsIgnoreCase(history.direction)
                ? AppColor.SUCCESS : AppColor.ACCENT;
        Color directionBg = "IN".equalsIgnoreCase(history.direction)
                ? AppColor.SUCCESS_BG : AppColor.ACCENT_BG_SOFT;

        FontIcon icon = FontIcon.of(
                "IN".equalsIgnoreCase(history.direction)
                        ? FontAwesomeSolid.ARROW_DOWN
                        : FontAwesomeSolid.ARROW_UP,
                18);
        icon.setIconColor(directionColor);

        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(directionBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(text(history.documentCode));
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        String subtitle = text(history.documentType)
                + "  ·  " + formatDateTime(history.changedAt);
        JLabel subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        header.add(directionPill(directionColor, directionBg), BorderLayout.EAST);
        return header;
    }

    private JLabel directionPill(Color color, Color bg) {
        String label = "IN".equalsIgnoreCase(history.direction) ? "NHẬP" : "XUẤT";
        JLabel pill = new JLabel(label, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setFont(AppFont.SMALL_BOLD);
        pill.setForeground(color);
        pill.setBorder(new EmptyBorder(6, 16, 6, 16));
        return pill;
    }

    private JScrollPane buildBody() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        content.add(buildSection("Thông tin chứng từ", new String[][]{
                {"Loại chứng từ", history.documentType},
                {"Mã chứng từ", history.documentCode},
                {"Thời gian", formatDateTime(history.changedAt)},
                {"Người thực hiện", history.userName}
        }));
        content.add(Box.createVerticalStrut(14));

        content.add(buildSection("Sản phẩm & lô", new String[][]{
                {"Mã lô", history.batchCode},
                {"Số lô", history.lotNumber},
                {"Mã SP", history.productCode},
                {"Tên sản phẩm", history.productName}
        }));
        content.add(Box.createVerticalStrut(14));

        content.add(buildStockSection());
        content.add(Box.createVerticalStrut(14));

        content.add(buildSection("Ghi chú", new String[][]{
                {"Trạng thái / ghi chú", history.note}
        }));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(14);
        return scroll;
    }

    private JPanel buildSection(String title, String[][] fields) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.BODY_BOLD);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        wrapper.add(titleLabel, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout((fields.length + 1) / 2, 2, 12, 10));
        grid.setOpaque(false);

        for (String[] field : fields) {
            grid.add(infoCard(field[0], field[1]));
        }
        if (fields.length % 2 != 0) {
            grid.add(Box.createHorizontalGlue());
        }

        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildStockSection() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 10));
        wrapper.setOpaque(false);
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = new JLabel("Biến động tồn kho");
        title.setFont(AppFont.BODY_BOLD);
        title.setForeground(AppColor.TEXT_PRIMARY);
        wrapper.add(title, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridLayout(1, 3, 12, 0));
        grid.setOpaque(false);
        grid.add(infoCard("Tồn trước", NumberUtil.formatThousands(history.stockBefore)));
        grid.add(infoCard("SL thay đổi", NumberUtil.formatThousands(history.quantity)));
        grid.add(infoCard("Tồn sau", NumberUtil.formatThousands(history.stockAfter)));

        wrapper.add(grid, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel infoCard(String label, String value) {
        JPanel card = new JPanel(new BorderLayout(0, 5));
        card.setBackground(AppColor.PAGE_BG);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));

        JLabel key = new JLabel(label);
        key.setFont(AppFont.SMALL);
        key.setForeground(AppColor.TEXT_MUTED);

        JLabel val = new JLabel(text(value));
        val.setFont(AppFont.BODY_BOLD);
        val.setForeground(AppColor.TEXT_PRIMARY);

        card.add(key, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(10, 24, 10, 24)));

        JButton close = new JButton("Đóng");
        close.setFont(AppFont.BODY_BOLD);
        close.addActionListener(e -> dispose());
        footer.add(close);
        return footer;
    }

    private static String formatDateTime(java.time.LocalDateTime value) {
        return value == null ? "—" : value.format(DATE_TIME);
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}