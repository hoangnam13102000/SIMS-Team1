package com.view.admin.inventory;

import com.model.InventoryBatch;
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
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;

/**
 * Dialog chi tiết lô hàng — tối ưu UX/UI:
 * - Header rõ ràng với mã lô + trạng thái
 * - Card thống kê số lượng (đã bán / còn lại) kèm progress bar
 * - Thông tin sản phẩm, nhà cung cấp, giá nhập, NSX/HSD
 * - Cảnh báo hết hạn / sắp hết hạn nổi bật
 * - Copy nhanh mã lô & số lô NCC
 */
public class InventoryBatchDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int NEAR_EXPIRY_DAYS = 7;

    private final InventoryBatch batch;

    public InventoryBatchDetailDialog(Frame owner, InventoryBatch batch) {
        super(owner, "Chi tiết lô hàng", Dialog.ModalityType.APPLICATION_MODAL);
        this.batch = batch;

        setSize(760, 680);
        setMinimumSize(new Dimension(640, 520));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------
    // Header: icon + mã lô + trạng thái
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        StatusInfo status = resolveStatus();

        FontIcon icon = FontIcon.of(FontAwesomeSolid.BOXES, 18);
        icon.setIconColor(status.color);
        JLabel iconBadge = new JLabel(icon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(status.bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));
        iconBadge.setMinimumSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        // Mã lô + nút copy
        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        codeRow.setOpaque(false);
        codeRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel titleLabel = new JLabel(emptyDash(batch.getBatchCode()));
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        codeRow.add(titleLabel);
        codeRow.add(copyButton(batch.getBatchCode(), "mã lô"));

        String productLine = emptyDash(batch.getProductName());
        if (batch.getProductCode() != null && !batch.getProductCode().isBlank()) {
            productLine += "  ·  " + batch.getProductCode();
        }
        JLabel subtitleLabel = new JLabel(productLine);
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(codeRow);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        header.add(statusPill(status), BorderLayout.EAST);
        return header;
    }

    private JLabel statusPill(StatusInfo status) {
        JLabel pill = new JLabel(status.label, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(status.bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setFont(AppFont.SMALL_BOLD);
        pill.setForeground(status.color);
        pill.setBorder(new EmptyBorder(6, 16, 6, 16));
        pill.setVerticalAlignment(SwingConstants.CENTER);
        return pill;
    }

    // ---------------------------------------------------------------
    // Body
    // ---------------------------------------------------------------

    private JScrollPane buildBody() {
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(AppColor.WHITE);
        content.setBorder(new EmptyBorder(18, 24, 18, 24));

        // 1. Card thống kê số lượng
        content.add(buildQuantityCard());
        content.add(Box.createVerticalStrut(16));

        // 2. Cảnh báo hết hạn / sắp hết hạn (nếu có)
        Long days = batch.daysUntilExpiry();
        if (days != null && days <= NEAR_EXPIRY_DAYS) {
            content.add(buildExpiryAlert(days));
            content.add(Box.createVerticalStrut(16));
        }

        // 3. Card thông tin chính
        content.add(buildInfoCard());
        content.add(Box.createVerticalStrut(16));

        // 4. Card ngày tháng & giá
        content.add(buildDatesAndPriceCard());
        // Đệm cuối để không bị cắt khi scroll
        content.add(Box.createVerticalStrut(8));

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        return scroll;
    }

    /** Card số lượng: còn lại / đã bán / tổng nhập + progress bar */
    private JPanel buildQuantityCard() {
        int total = Math.max(batch.getQuantity(), 0);
        int remaining = Math.max(batch.getRemainingQty(), 0);
        int sold = Math.max(total - remaining, 0);
        double ratio = total > 0 ? (double) remaining / total : 0;

        JPanel card = roundedCard();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel sectionTitle = new JLabel("Tồn kho lô hàng");
        sectionTitle.setFont(AppFont.BODY_BOLD);
        sectionTitle.setForeground(AppColor.TEXT_PRIMARY);
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(sectionTitle);
        card.add(Box.createVerticalStrut(14));

        // 3 số liệu ngang
        JPanel stats = new JPanel(new GridBagLayout());
        stats.setOpaque(false);
        stats.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 0, 16);

        gbc.gridx = 0;
        stats.add(statBlock("Còn lại", String.valueOf(remaining), remainingColor(remaining, total)), gbc);
        gbc.gridx = 1;
        stats.add(statBlock("Đã bán", String.valueOf(sold), AppColor.TEXT_MUTED), gbc);
        gbc.gridx = 2;
        gbc.insets = new Insets(0, 0, 0, 0);
        stats.add(statBlock("Nhập ban đầu", String.valueOf(total), AppColor.TEXT_PRIMARY), gbc);
        card.add(stats);
        card.add(Box.createVerticalStrut(12));

        // Progress bar
        JPanel barTrack = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                g2.setColor(AppColor.BORDER);
                g2.fillRoundRect(0, 0, w, h, h, h);
                int fillW = (int) Math.round(w * Math.min(1.0, Math.max(0, ratio)));
                if (fillW > 0) {
                    g2.setColor(remainingBarColor(remaining, total));
                    g2.fillRoundRect(0, 0, Math.max(fillW, h), h, h, h);
                }
                g2.dispose();
            }
        };
        barTrack.setOpaque(false);
        barTrack.setPreferredSize(new Dimension(100, 8));
        barTrack.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        barTrack.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(barTrack);

        JLabel pctLabel = new JLabel(String.format("%.0f%% còn lại", ratio * 100));
        pctLabel.setFont(AppFont.SMALL);
        pctLabel.setForeground(AppColor.TEXT_MUTED);
        pctLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(6));
        card.add(pctLabel);

        return card;
    }

    private JPanel statBlock(String label, String value, Color valueColor) {
        JPanel p = new JPanel();
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel l = new JLabel(label);
        l.setFont(AppFont.SMALL);
        l.setForeground(AppColor.TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.BOLD, 20));
        v.setForeground(valueColor);
        v.setAlignmentX(Component.LEFT_ALIGNMENT);

        p.add(l);
        p.add(Box.createVerticalStrut(2));
        p.add(v);
        return p;
    }

    private Color remainingColor(int remaining, int total) {
        if (remaining <= 0) return AppColor.TEXT_MUTED;
        if (total > 0 && remaining * 100.0 / total <= 20) return AppColor.WARNING;
        return AppColor.SUCCESS;
    }

    private Color remainingBarColor(int remaining, int total) {
        if (remaining <= 0) return AppColor.TEXT_MUTED;
        if (total > 0 && remaining * 100.0 / total <= 20) return AppColor.WARNING;
        return AppColor.SUCCESS;
    }

    /** Cảnh báo hết hạn / sắp hết hạn */
    private JPanel buildExpiryAlert(long days) {
        boolean expired = days < 0;
        Color fg = expired ? AppColor.ERROR : AppColor.WARNING;
        Color bg = expired ? AppColor.ERROR_BG : AppColor.WARNING_BG;

        String text;
        if (expired) {
            text = "Lô hàng đã hết hạn " + Math.abs(days) + " ngày (HSD: "
                    + batch.getExpiryDate().format(DATE_FORMAT) + ")";
        } else if (days == 0) {
            text = "Lô hàng hết hạn hôm nay (HSD: " + batch.getExpiryDate().format(DATE_FORMAT) + ")";
        } else {
            text = "Lô hàng sắp hết hạn trong " + days + " ngày (HSD: "
                    + batch.getExpiryDate().format(DATE_FORMAT) + ")";
        }

        FontIcon icon = FontIcon.of(
                expired ? FontAwesomeSolid.EXCLAMATION_CIRCLE : FontAwesomeSolid.EXCLAMATION_TRIANGLE, 14);
        icon.setIconColor(fg);

        JPanel alert = new JPanel(new BorderLayout(10, 0));
        alert.setBackground(bg);
        alert.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(fg, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        alert.setAlignmentX(Component.LEFT_ALIGNMENT);
        alert.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        JLabel iconLabel = new JLabel(icon);
        JLabel textLabel = new JLabel(text);
        textLabel.setFont(AppFont.BODY_BOLD);
        textLabel.setForeground(fg);

        alert.add(iconLabel, BorderLayout.WEST);
        alert.add(textLabel, BorderLayout.CENTER);
        return alert;
    }

    /** Card thông tin sản phẩm + nhà cung cấp + số lô NCC */
    private JPanel buildInfoCard() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 14, 24);
        gbc.weightx = 0.5;

        // Hàng 1
        gbc.gridx = 0; gbc.gridy = 0;
        grid.add(infoCell("Sản phẩm", emptyDash(batch.getProductName())), gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 14, 0);
        grid.add(infoCell("Mã sản phẩm", emptyDash(batch.getProductCode())), gbc);

        // Hàng 2
        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(0, 0, 14, 24);
        grid.add(infoCell("Nhà cung cấp", emptyDash(batch.getSupplierName())), gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 14, 0);
        grid.add(infoCellWithCopy("Số lô (theo NCC)", emptyDash(batch.getLotNumber()), batch.getLotNumber()), gbc);

        // Hàng 3
        gbc.gridx = 0; gbc.gridy = 2; gbc.insets = new Insets(0, 0, 0, 24);
        grid.add(infoCell("Mã lô hệ thống", emptyDash(batch.getBatchCode())), gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 0, 0);
        grid.add(infoCell("Trạng thái hệ thống", emptyDash(batch.getStatus())), gbc);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    /** Card NSX / HSD / Ngày nhập / Giá nhập */
    private JPanel buildDatesAndPriceCard() {
        JPanel card = roundedCard();
        card.setLayout(new BorderLayout());
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 14, 24);
        gbc.weightx = 0.5;

        gbc.gridx = 0; gbc.gridy = 0;
        grid.add(infoCell("Ngày sản xuất (NSX)",
                batch.getManufactureDate() != null ? batch.getManufactureDate().format(DATE_FORMAT) : "—"), gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 14, 0);
        grid.add(infoCell("Hạn sử dụng (HSD)",
                batch.getExpiryDate() != null ? batch.getExpiryDate().format(DATE_FORMAT) : "—"), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.insets = new Insets(0, 0, 0, 24);
        grid.add(infoCell("Ngày nhập kho",
                batch.getImportDate() != null ? batch.getImportDate().format(DATE_TIME_FORMAT) : "—"), gbc);
        gbc.gridx = 1; gbc.insets = new Insets(0, 0, 0, 0);
        grid.add(infoCellHighlight("Giá nhập / đơn vị",
                batch.getImportPrice() != null
                        ? NumberUtil.formatThousands(batch.getImportPrice().longValue()) + " đ"
                        : "—"), gbc);

        card.add(grid, BorderLayout.CENTER);
        return card;
    }

    // ---------------------------------------------------------------
    // Helpers UI
    // ---------------------------------------------------------------

    private JPanel roundedCard() {
        JPanel card = new JPanel();
        card.setOpaque(true);
        card.setBackground(AppColor.BG_LIGHT);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(16, 16, 16, 16)));
        // Không giới hạn chiều cao — để nội dung tự giãn, tránh cắt chữ
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JPanel infoCell(String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Dùng HTML để text dài xuống dòng thay vì bị cắt
        String safeValue = value == null ? "—" : value;
        JLabel valueComp = new JLabel("<html><body style='width:240px'>" + escapeHtml(safeValue) + "</body></html>");
        valueComp.setFont(AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(3));
        cell.add(valueComp);
        return cell;
    }

    private JPanel infoCellHighlight(String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        String safeValue = value == null ? "—" : value;
        JLabel valueComp = new JLabel("<html><body style='width:240px'>" + escapeHtml(safeValue) + "</body></html>");
        valueComp.setFont(AppFont.BODY_BOLD);
        valueComp.setForeground(AppColor.ACCENT);
        valueComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(3));
        cell.add(valueComp);
        return cell;
    }

    private JPanel infoCellWithCopy(String label, String displayValue, String copyValue) {
        JPanel cell = new JPanel();
        cell.setOpaque(false);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));

        JLabel labelComp = new JLabel(label);
        labelComp.setFont(AppFont.SMALL_BOLD);
        labelComp.setForeground(AppColor.TEXT_MUTED);
        labelComp.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel valueRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        valueRow.setOpaque(false);
        valueRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        String safeValue = displayValue == null ? "—" : displayValue;
        JLabel valueComp = new JLabel(safeValue);
        valueComp.setFont(AppFont.BODY);
        valueComp.setForeground(AppColor.TEXT_PRIMARY);
        valueRow.add(valueComp);

        if (copyValue != null && !copyValue.isBlank() && !"—".equals(copyValue)) {
            valueRow.add(copyButton(copyValue, "số lô NCC"));
        }

        cell.add(labelComp);
        cell.add(Box.createVerticalStrut(3));
        cell.add(valueRow);
        return cell;
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private JLabel copyButton(String text, String what) {
        if (text == null || text.isBlank() || "—".equals(text)) {
            return new JLabel();
        }
        FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 12);
        copyIcon.setIconColor(AppColor.ACCENT);
        JLabel btn = new JLabel(copyIcon);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setToolTipText("Copy " + what + ": " + text);
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(text), null);
                btn.setToolTipText("Đã copy!");
            }
        });
        return btn;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(14, 24, 14, 24)));

        JButton closeBtn = new JButton("Đóng");
        closeBtn.setFont(AppFont.BUTTON);
        closeBtn.setForeground(AppColor.TEXT_PRIMARY);
        closeBtn.setBackground(AppColor.CANCEL_BG);
        closeBtn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 20, 8, 20)));
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        closeBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                closeBtn.setBackground(AppColor.CANCEL_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                closeBtn.setBackground(AppColor.CANCEL_BG);
            }
        });

        footer.add(closeBtn);
        return footer;
    }

    // ---------------------------------------------------------------
    // Status helpers
    // ---------------------------------------------------------------

    private static class StatusInfo {
        final String label;
        final Color color;
        final Color bg;

        StatusInfo(String label, Color color, Color bg) {
            this.label = label;
            this.color = color;
            this.bg = bg;
        }
    }

    private StatusInfo resolveStatus() {
        if (batch.getRemainingQty() <= 0) {
            return new StatusInfo("Đã bán hết", AppColor.TEXT_MUTED, AppColor.BG_LIGHTER);
        }
        Long days = batch.daysUntilExpiry();
        if (days != null && days < 0) {
            return new StatusInfo("Hết hạn", AppColor.ERROR, AppColor.ERROR_BG);
        }
        if (days != null && days <= NEAR_EXPIRY_DAYS) {
            return new StatusInfo("Sắp hết hạn", AppColor.WARNING, AppColor.WARNING_BG);
        }
        return new StatusInfo("Còn hàng", AppColor.SUCCESS, AppColor.SUCCESS_BG);
    }

    private static String emptyDash(String value) {
        return (value == null || value.isBlank()) ? "—" : value;
    }
}