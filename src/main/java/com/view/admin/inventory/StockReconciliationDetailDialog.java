package com.view.admin.inventory;

import com.dao.InventoryBatchDAO;
import com.model.InventoryBatch;
import com.model.StockReconciliation;
import com.theme.AppColor;
import com.theme.AppFont;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Xem chi tiết 1 dòng đối chiếu kho dưới dạng so sánh trực quan (2 thẻ số
 * "Tồn hệ thống" / "Tồn thực tế" cạnh nhau + huy hiệu chênh lệch ở giữa),
 * kèm danh sách các lô còn hàng (tham chiếu) để hỗ trợ điều tra khi lệch tồn.
 *
 * Đây là dialog modal thật sự (đứng yên tới khi người dùng bấm Đóng), khác
 * với {@code BaseDialog.info(...)} vốn chỉ là 1 toast tự biến mất sau vài
 * giây - không phù hợp để xem chi tiết chứng từ.
 */
public class StockReconciliationDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InventoryBatchDAO batchDAO = new InventoryBatchDAO();

    public StockReconciliationDetailDialog(java.awt.Frame owner, StockReconciliation item) {
        super(owner, "Chi tiết đối chiếu kho", Dialog.ModalityType.APPLICATION_MODAL);

        int discrepancy = item.getDiscrepancy();
        Status status = Status.of(discrepancy);

        List<InventoryBatch> activeBatches = loadActiveBatches(item.getProductId());

        setSize(640, 640);
        setMinimumSize(new Dimension(580, 480));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(item), BorderLayout.NORTH);
        add(buildScrollBody(item, status, activeBatches), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    private List<InventoryBatch> loadActiveBatches(int productId) {
        Map<Integer, List<InventoryBatch>> map =
                batchDAO.getActiveBatchesByProductIds(Collections.singletonList(productId));
        List<InventoryBatch> list = map.get(productId);
        return list != null ? list : Collections.emptyList();
    }

    private boolean discrepancyHasNote(StockReconciliation item) {
        return item.getNote() != null && !item.getNote().isBlank();
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private JPanel buildHeader(StockReconciliation item) {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        JLabel iconBadge = circleIcon(FontAwesomeSolid.BALANCE_SCALE, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT, 44, 18);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Chi tiết đối chiếu kho");
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        subtitleRow.setOpaque(false);
        subtitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleRow.setBorder(null);

        JLabel productLabel = new JLabel(item.getProductName());
        productLabel.setFont(AppFont.BODY_BOLD);
        productLabel.setForeground(AppColor.TEXT_SECONDARY);

        JLabel codeChip = chip(FontAwesomeSolid.TAG, item.getProductCode(), AppColor.TEXT_MUTED, AppColor.BG_LIGHT);

        subtitleRow.add(productLabel);
        subtitleRow.add(codeChip);

        titleBox.add(titleLabel);
        titleBox.add(javax.swing.Box.createVerticalStrut(4));
        titleBox.add(subtitleRow);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body (scroll): so sanh + ghi chu + meta + lo con hang
    // ---------------------------------------------------------------

    private JScrollPane buildScrollBody(StockReconciliation item, Status status, List<InventoryBatch> batches) {
        JPanel content = buildBody(item, status, batches);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildBody(StockReconciliation item, Status status, List<InventoryBatch> batches) {
        JPanel content = new JPanel();
        content.setBackground(AppColor.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));

        content.add(buildComparisonRow(item, status));
        content.add(javax.swing.Box.createVerticalStrut(16));

        if (discrepancyHasNote(item)) {
            content.add(buildNoteCallout(item.getNote(), status));
            content.add(javax.swing.Box.createVerticalStrut(16));
        }

        content.add(buildMetaRow(item));
        content.add(javax.swing.Box.createVerticalStrut(18));
        content.add(buildBatchesSection(item, batches));

        return content;
    }

    private JPanel buildComparisonRow(StockReconciliation item, Status status) {
        JPanel row = new JPanel(new BorderLayout(0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        JPanel systemCard = statCard("TỒN HỆ THỐNG", String.valueOf(item.getSystemStock()),
                "trước khi đối chiếu", AppColor.TEXT_PRIMARY, AppColor.BG_LIGHT, AppColor.BORDER);

        JPanel actualCard = statCard("TỒN THỰC TẾ", String.valueOf(item.getActualStock()),
                "đếm thực tế", status.textColor, status.bg, status.accent);

        JPanel connector = buildConnector(status);

        JPanel wrap = new JPanel(new GridLayout(1, 3, 10, 0));
        wrap.setOpaque(false);
        wrap.add(systemCard);
        wrap.add(connector);
        wrap.add(actualCard);

        row.add(wrap, BorderLayout.CENTER);
        return row;
    }

    private JPanel statCard(String label, String value, String subLabel, Color valueColor, Color bg, Color border) {
        RoundedPanel card = new RoundedPanel(14, bg, border);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(16, 14, 14, 14));

        JLabel labelText = new JLabel(label);
        labelText.setFont(AppFont.SMALL_BOLD);
        labelText.setForeground(AppColor.TEXT_MUTED);
        labelText.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel valueText = new JLabel(value);
        valueText.setFont(AppFont.getXXL_Bold());
        valueText.setForeground(valueColor);
        valueText.setAlignmentX(Component.CENTER_ALIGNMENT);
        valueText.setBorder(new EmptyBorder(6, 0, 4, 0));

        JLabel subText = new JLabel(subLabel);
        subText.setFont(AppFont.SMALL);
        subText.setForeground(AppColor.TEXT_MUTED);
        subText.setAlignmentX(Component.CENTER_ALIGNMENT);

        card.add(labelText);
        card.add(valueText);
        card.add(subText);
        return card;
    }

    private JPanel buildConnector(Status status) {
        JPanel connector = new JPanel();
        connector.setOpaque(false);
        connector.setLayout(new BoxLayout(connector, BoxLayout.Y_AXIS));

        FontIcon arrowIcon = FontIcon.of(FontAwesomeSolid.CHEVRON_RIGHT, 16);
        arrowIcon.setIconColor(AppColor.TEXT_MUTED_ALT);
        JLabel arrowLabel = new JLabel(arrowIcon);
        arrowLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        RoundedPanel badge = new RoundedPanel(999, status.bg, status.accent);
        badge.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 6));
        badge.setAlignmentX(Component.CENTER_ALIGNMENT);
        badge.setMaximumSize(new Dimension(110, 34));

        FontIcon badgeIcon = FontIcon.of(status.icon, 12);
        badgeIcon.setIconColor(status.accent);
        JLabel badgeIconLabel = new JLabel(badgeIcon);

        JLabel badgeText = new JLabel(status.badgeText);
        badgeText.setFont(AppFont.SMALL_BOLD);
        badgeText.setForeground(status.accent);

        badge.add(badgeIconLabel);
        badge.add(badgeText);

        JLabel captionLabel = new JLabel(status.caption, SwingConstants.CENTER);
        captionLabel.setFont(AppFont.SMALL);
        captionLabel.setForeground(AppColor.TEXT_MUTED);
        captionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        captionLabel.setBorder(new EmptyBorder(6, 0, 0, 0));

        connector.add(javax.swing.Box.createVerticalGlue());
        connector.add(arrowLabel);
        connector.add(javax.swing.Box.createVerticalStrut(8));
        connector.add(badge);
        connector.add(captionLabel);
        connector.add(javax.swing.Box.createVerticalGlue());
        return connector;
    }

    private JPanel buildNoteCallout(String note, Status status) {
        RoundedPanel callout = new RoundedPanel(10, status.bg, status.accent);
        callout.setLayout(new BorderLayout(10, 0));
        callout.setAlignmentX(Component.LEFT_ALIGNMENT);
        callout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        callout.setBorder(new EmptyBorder(12, 14, 12, 14));

        FontIcon noteIcon = FontIcon.of(FontAwesomeSolid.COMMENT_DOTS, 15);
        noteIcon.setIconColor(status.accent);
        JLabel iconLabel = new JLabel(noteIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));

        JLabel noteTitle = new JLabel("Ghi chú");
        noteTitle.setFont(AppFont.SMALL_BOLD);
        noteTitle.setForeground(status.accent);
        noteTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noteBody = new JLabel("<html><div style='width:340px'>" + escapeHtml(note) + "</div></html>");
        noteBody.setFont(AppFont.BODY);
        noteBody.setForeground(AppColor.TEXT_PRIMARY);
        noteBody.setAlignmentX(Component.LEFT_ALIGNMENT);
        noteBody.setBorder(new EmptyBorder(3, 0, 0, 0));

        textBox.add(noteTitle);
        textBox.add(noteBody);

        callout.add(iconLabel, BorderLayout.WEST);
        callout.add(textBox, BorderLayout.CENTER);
        return callout;
    }

    private JPanel buildMetaRow(StockReconciliation item) {
        JPanel meta = new JPanel(new GridLayout(1, 2, 10, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        meta.add(metaChip(FontAwesomeSolid.USER, "Người đối chiếu", item.getCreatedByName()));
        String time = item.getCreatedAt() != null ? item.getCreatedAt().format(DATE_TIME_FORMAT) : "-";
        meta.add(metaChip(FontAwesomeSolid.CLOCK, "Thời gian", time));
        return meta;
    }

    private JPanel metaChip(FontAwesomeSolid icon, String label, String value) {
        JPanel chip = new JPanel(new BorderLayout(8, 0));
        chip.setOpaque(false);

        FontIcon fontIcon = FontIcon.of(icon, 13);
        fontIcon.setIconColor(AppColor.ICON_MUTED);
        JLabel iconLabel = new JLabel(fontIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));

        JLabel labelText = new JLabel(label);
        labelText.setFont(AppFont.SMALL);
        labelText.setForeground(AppColor.TEXT_MUTED);
        labelText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueText = new JLabel(value == null ? "-" : value);
        valueText.setFont(AppFont.BODY_BOLD);
        valueText.setForeground(AppColor.TEXT_PRIMARY);
        valueText.setAlignmentX(Component.LEFT_ALIGNMENT);

        textBox.add(labelText);
        textBox.add(valueText);

        chip.add(iconLabel, BorderLayout.WEST);
        chip.add(textBox, BorderLayout.CENTER);
        return chip;
    }

    // ---------------------------------------------------------------
    // Các lô còn hàng (tham chiếu)
    // ---------------------------------------------------------------

    private JPanel buildBatchesSection(StockReconciliation item, List<InventoryBatch> batches) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Tiêu đề + tổng
        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleLeft.setOpaque(false);

        FontIcon boxIcon = FontIcon.of(FontAwesomeSolid.BOXES, 14);
        boxIcon.setIconColor(AppColor.ACCENT);
        JLabel titleLabel = new JLabel("Các lô còn hàng", boxIcon, SwingConstants.LEFT);
        titleLabel.setIconTextGap(8);
        titleLabel.setFont(AppFont.BODY_BOLD);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLeft.add(titleLabel);

        int totalRemaining = 0;
        for (InventoryBatch b : batches) {
            totalRemaining += b.getRemainingQty();
        }

        JLabel totalChip = chip(FontAwesomeSolid.CUBE,
                batches.size() + " lô · Σ " + totalRemaining,
                AppColor.TEXT_MUTED, AppColor.BG_LIGHT);
        titleLeft.add(totalChip);

        titleRow.add(titleLeft, BorderLayout.WEST);

        // Cảnh báo nếu tổng lô ≠ tồn hệ thống trên phiếu
        if (!batches.isEmpty() && totalRemaining != item.getSystemStock()) {
            JLabel warn = chip(FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                    "Σ lô ≠ tồn hệ thống (" + item.getSystemStock() + ")",
                    AppColor.WARNING, AppColor.WARNING_BG);
            titleRow.add(warn, BorderLayout.EAST);
        }

        section.add(titleRow);
        section.add(javax.swing.Box.createVerticalStrut(4));

        JLabel hint = new JLabel("Tham chiếu tại thời điểm xem — không phải snapshot lúc kiểm kê.");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(hint);
        section.add(javax.swing.Box.createVerticalStrut(10));

        if (batches.isEmpty()) {
            RoundedPanel empty = new RoundedPanel(10, AppColor.BG_LIGHT, AppColor.BORDER);
            empty.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

            FontIcon emptyIcon = FontIcon.of(FontAwesomeSolid.INBOX, 14);
            emptyIcon.setIconColor(AppColor.TEXT_MUTED);
            JLabel emptyLabel = new JLabel("Không có lô ACTIVE còn tồn cho sản phẩm này.", emptyIcon, SwingConstants.LEFT);
            emptyLabel.setIconTextGap(8);
            emptyLabel.setFont(AppFont.BODY);
            emptyLabel.setForeground(AppColor.TEXT_MUTED);
            empty.add(emptyLabel);
            section.add(empty);
            return section;
        }

        // Header cột
        section.add(buildBatchHeaderRow());
        section.add(javax.swing.Box.createVerticalStrut(4));

        for (InventoryBatch batch : batches) {
            section.add(buildBatchRow(batch));
            section.add(javax.swing.Box.createVerticalStrut(6));
        }

        return section;
    }

    /** Tỷ lệ cột: Mã lô (rộng) | Còn lại | HSD | Ghi chú */
    private static final double[] BATCH_COL_WEIGHTS = {0.42, 0.12, 0.24, 0.22};

    private JPanel buildBatchColumnsRow(Component colCode, Component colQty, Component colExp, Component colNote) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new java.awt.Insets(0, 0, 0, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = BATCH_COL_WEIGHTS[0];
        row.add(colCode, gbc);

        gbc.gridx = 1;
        gbc.weightx = BATCH_COL_WEIGHTS[1];
        row.add(colQty, gbc);

        gbc.gridx = 2;
        gbc.weightx = BATCH_COL_WEIGHTS[2];
        row.add(colExp, gbc);

        gbc.gridx = 3;
        gbc.weightx = BATCH_COL_WEIGHTS[3];
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        row.add(colNote, gbc);

        return row;
    }

    private JPanel buildBatchHeaderRow() {
        JPanel row = buildBatchColumnsRow(
                colHeader("Mã lô / Số lô"),
                colHeader("Còn lại"),
                colHeader("HSD"),
                colHeader("Ghi chú"));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    private JLabel colHeader(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFont.SMALL_BOLD);
        l.setForeground(AppColor.TEXT_MUTED);
        return l;
    }

    private JPanel buildBatchRow(InventoryBatch batch) {
        // Mã lô + số lô NCC — không cắt chữ
        JPanel codeBox = new JPanel();
        codeBox.setOpaque(false);
        codeBox.setLayout(new BoxLayout(codeBox, BoxLayout.Y_AXIS));

        String batchCode = batch.getBatchCode() != null ? batch.getBatchCode() : ("#" + batch.getBatchId());
        JLabel codeLabel = new JLabel(batchCode);
        codeLabel.setFont(AppFont.BODY_BOLD);
        codeLabel.setForeground(AppColor.TEXT_PRIMARY);
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Tooltip full text nếu vẫn dài
        codeLabel.setToolTipText(batchCode);

        String lot = batch.getLotNumber();
        if (lot != null && !lot.isBlank()) {
            String lotText = "NCC: " + lot;
            JLabel lotLabel = new JLabel(lotText);
            lotLabel.setFont(AppFont.SMALL);
            lotLabel.setForeground(AppColor.TEXT_MUTED);
            lotLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            lotLabel.setToolTipText(lotText);
            codeBox.add(codeLabel);
            codeBox.add(lotLabel);
        } else {
            codeBox.add(codeLabel);
        }

        // Còn lại
        JLabel qtyLabel = new JLabel(String.valueOf(batch.getRemainingQty()));
        qtyLabel.setFont(AppFont.BODY_BOLD);
        qtyLabel.setForeground(AppColor.TEXT_PRIMARY);
        qtyLabel.setHorizontalAlignment(SwingConstants.LEFT);

        // HSD
        JPanel expBox = new JPanel();
        expBox.setOpaque(false);
        expBox.setLayout(new BoxLayout(expBox, BoxLayout.Y_AXIS));

        if (batch.getExpiryDate() != null) {
            JLabel expLabel = new JLabel(batch.getExpiryDate().format(DATE_FORMAT));
            expLabel.setFont(AppFont.BODY);
            expLabel.setForeground(AppColor.TEXT_PRIMARY);
            expLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            expBox.add(expLabel);

            Long days = batch.daysUntilExpiry();
            if (days != null) {
                String dayText;
                Color dayColor;
                if (days < 0) {
                    dayText = "Quá hạn " + Math.abs(days) + " ngày";
                    dayColor = AppColor.ERROR;
                } else if (days <= 30) {
                    dayText = "Còn " + days + " ngày";
                    dayColor = AppColor.WARNING;
                } else {
                    dayText = "Còn " + days + " ngày";
                    dayColor = AppColor.TEXT_MUTED;
                }
                JLabel dayLabel = new JLabel(dayText);
                dayLabel.setFont(AppFont.SMALL);
                dayLabel.setForeground(dayColor);
                dayLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
                expBox.add(dayLabel);
            }
        } else {
            JLabel noExp = new JLabel("Không HSD");
            noExp.setFont(AppFont.BODY);
            noExp.setForeground(AppColor.TEXT_MUTED);
            noExp.setAlignmentX(Component.LEFT_ALIGNMENT);
            expBox.add(noExp);
        }

        // Ghi chú FEFO / sắp hết hạn
        String noteText = batchHint(batch);
        Color noteColor = AppColor.TEXT_MUTED;
        Color noteBg = AppColor.WHITE;
        if (batch.getExpiryDate() != null) {
            Long days = batch.daysUntilExpiry();
            if (days != null && days < 0) {
                noteColor = AppColor.ERROR;
                noteBg = AppColor.ERROR_BG;
            } else if (days != null && days <= 30) {
                noteColor = AppColor.WARNING;
                noteBg = AppColor.WARNING_BG;
            }
        }
        JLabel noteChip = chip(FontAwesomeSolid.INFO_CIRCLE, noteText, noteColor, noteBg);
        JPanel noteWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        noteWrap.setOpaque(false);
        noteWrap.add(noteChip);

        JPanel inner = buildBatchColumnsRow(codeBox, qtyLabel, expBox, noteWrap);

        RoundedPanel row = new RoundedPanel(8, AppColor.BG_LIGHT, AppColor.BORDER);
        row.setLayout(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.add(inner, BorderLayout.CENTER);
        return row;
    }

    private String batchHint(InventoryBatch batch) {
        if (batch.getExpiryDate() == null) {
            return "Không hạn dùng";
        }
        Long days = batch.daysUntilExpiry();
        if (days == null) return "—";
        if (days < 0) return "Đã quá hạn";
        if (days == 0) return "Hết hạn hôm nay";
        if (days <= 7) return "Sắp hết hạn";
        if (days <= 30) return "Gần hạn";
        return "Còn hạn";
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

        JButton closeButton = new JButton("Đóng");
        closeButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeButton.setFocusPainted(false);
        closeButton.setBackground(AppColor.ACCENT);
        closeButton.setForeground(AppColor.WHITE);
        closeButton.setBorder(new EmptyBorder(8, 22, 8, 22));
        closeButton.addActionListener(e -> dispose());

        footer.add(closeButton);
        getRootPane().setDefaultButton(closeButton);
        return footer;
    }

    // ---------------------------------------------------------------
    // Tien ich UI dung chung
    // ---------------------------------------------------------------

    private JLabel circleIcon(FontAwesomeSolid icon, Color fg, Color bg, int diameter, int iconSize) {
        FontIcon fontIcon = FontIcon.of(icon, iconSize);
        fontIcon.setIconColor(fg);
        JLabel badge = new JLabel(fontIcon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        badge.setPreferredSize(new Dimension(diameter, diameter));
        return badge;
    }

    private JLabel chip(FontAwesomeSolid icon, String text, Color fg, Color bg) {
        FontIcon fontIcon = FontIcon.of(icon, 10);
        fontIcon.setIconColor(fg);
        JLabel label = new JLabel(text, fontIcon, SwingConstants.LEFT);
        label.setIconTextGap(5);
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(fg);
        label.setOpaque(true);
        label.setBackground(bg);
        label.setBorder(new EmptyBorder(3, 8, 3, 8));
        return label;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** Panel nen bo goc, dung cho the so sanh / huy hieu / khung ghi chu. */
    private static final class RoundedPanel extends JPanel {
        private final int radius;
        private final Color bg;
        private final Color border;

        RoundedPanel(int radius, Color bg, Color border) {
            this.radius = radius;
            this.bg = bg;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** Trang thai truc quan (mau/icon/nhan) suy ra tu dau chenh lech. */
    private static final class Status {
        final Color accent;
        final Color bg;
        final FontAwesomeSolid icon;
        final String caption;
        final String badgeText;
        final Color textColor;

        private Status(Color accent, Color bg, FontAwesomeSolid icon, String caption, String badgeText) {
            this.accent = accent;
            this.bg = bg;
            this.icon = icon;
            this.caption = caption;
            this.badgeText = badgeText;
            this.textColor = accent;
        }

        static Status of(int discrepancy) {
            if (discrepancy < 0) {
                return new Status(AppColor.ERROR, AppColor.ERROR_BG, FontAwesomeSolid.ARROW_DOWN,
                        "Thiếu hụt tồn kho", String.valueOf(discrepancy));
            }
            if (discrepancy > 0) {
                return new Status(AppColor.WARNING, AppColor.WARNING_BG, FontAwesomeSolid.ARROW_UP,
                        "Dư thừa so với hệ thống", "+" + discrepancy);
            }
            return new Status(AppColor.SUCCESS, AppColor.SUCCESS_BG, FontAwesomeSolid.CHECK,
                    "Khớp với hệ thống", "Khớp");
        }
    }
}