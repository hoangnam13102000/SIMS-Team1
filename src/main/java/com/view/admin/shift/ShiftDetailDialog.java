package com.view.admin.shift;

import com.model.Shift;
import com.model.ShiftCashTransaction;
import com.service.ShiftService;
import com.theme.AppColor;
import com.theme.AppFont;

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
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

/**
 * Xem chi tiết đầy đủ 1 ca bán hàng: so sánh trực quan "Tiền hệ thống" /
 * "Tiền thực tế" (giống StockReconciliationDetailDialog), toàn bộ dòng tiền
 * trong ca, ghi chú mở/đóng ca, thông tin duyệt/từ chối, và danh sách giao
 * dịch thu/chi - tất cả trong 1 hộp thoại thay vì phải nhìn dòng bảng bị cắt
 * chữ hoặc đổi qua lại tab "Thu/chi của ca" bên dưới.
 * <p>
 * Đây là dialog modal thật sự (đứng yên tới khi người dùng bấm Đóng), khác
 * với {@code BaseDialog.info(...)} vốn chỉ là 1 toast tự biến mất - không
 * phù hợp để xem chi tiết chứng từ.
 */
final class ShiftDetailDialog extends JDialog {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm 'ngày' dd/MM/yyyy");

    private ShiftDetailDialog(Window owner, Shift shift, ShiftService shiftService) {
        super(owner, "Chi tiết ca bán hàng", ModalityType.APPLICATION_MODAL);

        List<ShiftCashTransaction> transactions = loadTransactions(shiftService, shift.getShiftId());

        setSize(680, 720);
        setMinimumSize(new Dimension(560, 480));
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(shift), BorderLayout.NORTH);
        add(buildScrollBody(shift, transactions), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setLocationRelativeTo(owner);
    }

    /** Điểm vào duy nhất - gọi từ ShiftManagementPanel khi bấm nút "Xem chi tiết" trên bảng Lịch sử ca. */
    static void show(Window owner, Shift shift, ShiftService shiftService) {
        if (shift == null) return;
        new ShiftDetailDialog(owner, shift, shiftService).setVisible(true);
    }

    private List<ShiftCashTransaction> loadTransactions(ShiftService shiftService, int shiftId) {
        try {
            List<ShiftCashTransaction> list = shiftService.getTransactions(shiftId);
            return list != null ? list : Collections.emptyList();
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    // ---------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------

    private JPanel buildHeader(Shift shift) {
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        JLabel iconBadge = circleIcon(FontAwesomeSolid.CLOCK, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT, 44, 18);

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Chi tiết ca #" + shift.getShiftId());
        titleLabel.setFont(AppFont.DIALOG_TITLE);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel subtitleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        subtitleRow.setOpaque(false);
        subtitleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitleRow.setBorder(null);

        JLabel employeeLabel = new JLabel(shift.getUserName() != null ? shift.getUserName() : "—");
        employeeLabel.setFont(AppFont.BODY_BOLD);
        employeeLabel.setForeground(AppColor.TEXT_SECONDARY);

        StatusInfo status = StatusInfo.of(shift.getStatus());
        JLabel statusChip = chip(status.icon, shift.getStatusLabel(), status.accent, status.bg);

        subtitleRow.add(employeeLabel);
        subtitleRow.add(statusChip);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(4));
        titleBox.add(subtitleRow);

        header.add(iconBadge, BorderLayout.WEST);
        header.add(titleBox, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body (scroll)
    // ---------------------------------------------------------------

    private JScrollPane buildScrollBody(Shift shift, List<ShiftCashTransaction> transactions) {
        JPanel content = buildBody(shift, transactions);
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildBody(Shift shift, List<ShiftCashTransaction> transactions) {
        JPanel content = new JPanel();
        content.setBackground(AppColor.WHITE);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(new EmptyBorder(20, 24, 16, 24));

        content.add(buildTimeRow(shift));
        content.add(Box.createVerticalStrut(16));

        content.add(buildComparisonRow(shift));
        content.add(Box.createVerticalStrut(16));

        content.add(buildBreakdownGrid(shift));
        content.add(Box.createVerticalStrut(16));

        if (hasNote(shift.getOpeningNote())) {
            content.add(buildNoteCallout("Ghi chú mở ca", shift.getOpeningNote(),
                    FontAwesomeSolid.PLAY_CIRCLE, AppColor.ACCENT, AppColor.ACCENT_BG_SOFT));
            content.add(Box.createVerticalStrut(12));
        }
        if (hasNote(shift.getClosingNote())) {
            content.add(buildNoteCallout("Ghi chú đóng ca", shift.getClosingNote(),
                    FontAwesomeSolid.LOCK, AppColor.TEXT_SECONDARY, AppColor.BG_LIGHT));
            content.add(Box.createVerticalStrut(12));
        }

        JPanel approval = buildApprovalSection(shift);
        if (approval != null) {
            content.add(approval);
            content.add(Box.createVerticalStrut(18));
        }

        content.add(buildTransactionsSection(transactions));

        return content;
    }

    private boolean hasNote(String note) {
        return note != null && !note.isBlank();
    }

    // ---------------------------------------------------------------
    // Thời gian ca
    // ---------------------------------------------------------------

    private JPanel buildTimeRow(Shift shift) {
        JPanel row = new JPanel(new GridLayout(1, 2, 10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        row.add(metaChip(FontAwesomeSolid.PLAY, "Bắt đầu", dateTime(shift.getStartTime())));
        row.add(metaChip(FontAwesomeSolid.FLAG_CHECKERED, "Kết thúc",
                shift.getEndTime() != null ? dateTime(shift.getEndTime()) : "Đang diễn ra"));
        return row;
    }

    // ---------------------------------------------------------------
    // So sánh Tiền hệ thống / Tiền thực tế
    // ---------------------------------------------------------------

    private JPanel buildComparisonRow(Shift shift) {
        BigDecimal expected = expectedOf(shift);
        BigDecimal counted = shift.getCountedCash();

        JPanel row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 140));

        if (counted == null) {
            // Ca chưa đối soát (còn đang mở) - chỉ có 1 con số, chưa có gì để so sánh.
            JPanel wrap = new JPanel(new BorderLayout());
            wrap.setOpaque(false);

            JPanel expectedCard = statCard("TIỀN HỆ THỐNG", money(expected), "tạm tính đến hiện tại",
                    AppColor.TEXT_PRIMARY, AppColor.BG_LIGHT, AppColor.BORDER);
            wrap.add(expectedCard, BorderLayout.CENTER);

            JLabel hint = chip(FontAwesomeSolid.INFO_CIRCLE, "Ca đang mở — chưa đối soát quỹ",
                    AppColor.INFO, AppColor.INFO_BG);
            JPanel hintWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            hintWrap.setOpaque(false);
            hintWrap.setBorder(new EmptyBorder(8, 0, 0, 0));
            hintWrap.add(hint);

            JPanel outer = new JPanel();
            outer.setOpaque(false);
            outer.setLayout(new BoxLayout(outer, BoxLayout.Y_AXIS));
            outer.add(wrap);
            outer.add(hintWrap);

            row.add(outer, BorderLayout.CENTER);
            return row;
        }

        BigDecimal difference = shift.getCashDifference() != null
                ? shift.getCashDifference()
                : counted.subtract(expected);
        Status status = Status.of(difference);

        JPanel systemCard = statCard("TIỀN HỆ THỐNG", money(expected), "theo tính toán",
                AppColor.TEXT_PRIMARY, AppColor.BG_LIGHT, AppColor.BORDER);

        JPanel actualCard = statCard("TIỀN THỰC TẾ", money(counted), "đếm thực tế",
                status.textColor, status.bg, status.accent);

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
        badge.setMaximumSize(new Dimension(130, 34));

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

        connector.add(Box.createVerticalGlue());
        connector.add(arrowLabel);
        connector.add(Box.createVerticalStrut(8));
        connector.add(badge);
        connector.add(captionLabel);
        connector.add(Box.createVerticalGlue());
        return connector;
    }

    // ---------------------------------------------------------------
    // Chi tiết dòng tiền
    // ---------------------------------------------------------------

    private JPanel buildBreakdownGrid(Shift shift) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel title = sectionTitle(FontAwesomeSolid.COINS, "Chi tiết dòng tiền");
        section.add(title);
        section.add(Box.createVerticalStrut(8));

        JPanel grid = new JPanel(new GridLayout(2, 3, 10, 10));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);

        grid.add(breakdownTile(FontAwesomeSolid.WALLET, "Tiền đầu ca", money(shift.getOpeningCash())));
        grid.add(breakdownTile(FontAwesomeSolid.MONEY_BILL_WAVE, "Doanh thu tiền mặt", money(shift.getCashSales())));
        grid.add(breakdownTile(FontAwesomeSolid.FILE_INVOICE, "Số hóa đơn", String.valueOf(shift.getInvoiceCount())));
        grid.add(breakdownTile(FontAwesomeSolid.ARROW_DOWN, "Thu tiền", money(shift.getCashIn())));
        grid.add(breakdownTile(FontAwesomeSolid.ARROW_UP, "Chi tiền", money(shift.getCashOut())));
        grid.add(breakdownTile(FontAwesomeSolid.UNDO, "Hoàn tiền (trả hàng)", money(shift.getCashRefunds())));

        /*
         * KHONG dat cung 1 con so chieu cao "doan mo" cho ca khung grid: voi
         * GridLayout, container se CHIA DEU chinh xac phan chieu cao duoc
         * BoxLayout cha cap phat cho no ra 2 hang, bat ke noi dung cac o con
         * can bao nhieu - neu con so do nho hon chieu cao that su can (vd do
         * font/DPI/L&F khac nhau lam text cao hon uoc tinh), dong "gia tri"
         * (dong thu 2 trong o) se bi cat mat, chi con thay le duoi cua chu so
         * (giong loi da gap: "· · · ₫"). Thay vi doan 1 con so co dinh, goi
         * getPreferredSize() SAU KHI da them du 6 o - luc nay GridLayout tu
         * tinh chieu cao chinh xac dua tren kich thuoc that (font metrics
         * thuc te luc runtime) cua tung o, roi dung dung con so do lam tran
         * (max) cho grid - vua khong bao gio bi cat chu, vua khong bi
         * BoxLayout keo gian qua muc can thiet.
         */
        int neededHeight = grid.getPreferredSize().height + 4;
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, neededHeight));

        section.add(grid);
        return section;
    }

    private JPanel breakdownTile(FontAwesomeSolid icon, String label, String value) {
        RoundedPanel tile = new RoundedPanel(10, AppColor.BG_LIGHT, AppColor.BORDER);
        tile.setLayout(new BorderLayout(10, 0));
        tile.setBorder(new EmptyBorder(12, 12, 12, 10));

        FontIcon fontIcon = FontIcon.of(icon, 15);
        fontIcon.setIconColor(AppColor.ACCENT);
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

        JLabel valueText = new JLabel(value);
        valueText.setFont(AppFont.BODY_BOLD);
        valueText.setForeground(AppColor.TEXT_PRIMARY);
        valueText.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueText.setBorder(new EmptyBorder(3, 0, 0, 0));

        textBox.add(labelText);
        textBox.add(valueText);

        tile.add(iconLabel, BorderLayout.WEST);
        tile.add(textBox, BorderLayout.CENTER);
        return tile;
    }

    // ---------------------------------------------------------------
    // Ghi chú (mở ca / đóng ca)
    // ---------------------------------------------------------------

    private JPanel buildNoteCallout(String title, String note, FontAwesomeSolid icon, Color accent, Color bg) {
        RoundedPanel callout = new RoundedPanel(10, bg, accent);
        callout.setLayout(new BorderLayout(10, 0));
        callout.setAlignmentX(Component.LEFT_ALIGNMENT);
        callout.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        callout.setBorder(new EmptyBorder(12, 14, 12, 14));

        FontIcon noteIcon = FontIcon.of(icon, 15);
        noteIcon.setIconColor(accent);
        JLabel iconLabel = new JLabel(noteIcon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JPanel textBox = new JPanel();
        textBox.setOpaque(false);
        textBox.setLayout(new BoxLayout(textBox, BoxLayout.Y_AXIS));

        JLabel noteTitle = new JLabel(title);
        noteTitle.setFont(AppFont.SMALL_BOLD);
        noteTitle.setForeground(accent);
        noteTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel noteBody = new JLabel("<html><div style='width:420px'>" + escapeHtml(note) + "</div></html>");
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

    // ---------------------------------------------------------------
    // Duyệt / Từ chối
    // ---------------------------------------------------------------

    private JPanel buildApprovalSection(Shift shift) {
        boolean hasClosedBy = shift.getClosedByName() != null && !shift.getClosedByName().isBlank();
        boolean hasApprovedBy = shift.getApprovedByName() != null && !shift.getApprovedByName().isBlank();
        boolean rejected = shift.isRejected();
        boolean hasApprovalNote = shift.getApprovalNote() != null && !shift.getApprovalNote().isBlank();

        if (!hasClosedBy && !hasApprovedBy && !hasApprovalNote) return null;

        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        section.add(sectionTitle(FontAwesomeSolid.USER_CHECK, "Đóng ca & duyệt"));
        section.add(Box.createVerticalStrut(8));

        JPanel meta = new JPanel(new GridLayout(1, 2, 10, 0));
        meta.setOpaque(false);
        meta.setAlignmentX(Component.LEFT_ALIGNMENT);
        meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        meta.add(metaChip(FontAwesomeSolid.USER, "Người đóng ca", hasClosedBy ? shift.getClosedByName() : "—"));
        meta.add(metaChip(FontAwesomeSolid.USER_TIE, rejected ? "Người từ chối" : "Người duyệt",
                hasApprovedBy ? shift.getApprovedByName() : "—"));
        section.add(meta);

        if (shift.getApprovedAt() != null) {
            section.add(Box.createVerticalStrut(8));
            JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            timeRow.setOpaque(false);
            timeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
            timeRow.add(metaChip(FontAwesomeSolid.CLOCK, rejected ? "Thời gian từ chối" : "Thời gian duyệt",
                    dateTime(shift.getApprovedAt())));
            section.add(timeRow);
        }

        if (hasApprovalNote) {
            section.add(Box.createVerticalStrut(10));
            Color accent = rejected ? AppColor.ERROR : AppColor.SUCCESS;
            Color bg = rejected ? AppColor.ERROR_BG : AppColor.SUCCESS_BG;
            String title = rejected ? "Lý do từ chối" : "Ghi chú duyệt";
            section.add(buildNoteCallout(title, shift.getApprovalNote(), FontAwesomeSolid.COMMENT_DOTS, accent, bg));
        }

        return section;
    }

    // ---------------------------------------------------------------
    // Lịch sử thu/chi trong ca
    // ---------------------------------------------------------------

    private JPanel buildTransactionsSection(List<ShiftCashTransaction> transactions) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleLeft.setOpaque(false);
        titleLeft.add(sectionTitle(FontAwesomeSolid.EXCHANGE_ALT, "Thu/chi trong ca"));

        BigDecimal net = BigDecimal.ZERO;
        for (ShiftCashTransaction t : transactions) {
            if (t.getAmount() == null) continue;
            net = t.isCashIn() ? net.add(t.getAmount()) : net.subtract(t.getAmount());
        }
        titleLeft.add(chip(FontAwesomeSolid.CUBE, transactions.size() + " giao dịch · ròng " + signedMoney(net),
                AppColor.TEXT_MUTED, AppColor.BG_LIGHT));

        titleRow.add(titleLeft, BorderLayout.WEST);
        section.add(titleRow);
        section.add(Box.createVerticalStrut(10));

        if (transactions.isEmpty()) {
            RoundedPanel empty = new RoundedPanel(10, AppColor.BG_LIGHT, AppColor.BORDER);
            empty.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
            empty.setAlignmentX(Component.LEFT_ALIGNMENT);
            empty.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));

            FontIcon emptyIcon = FontIcon.of(FontAwesomeSolid.INBOX, 14);
            emptyIcon.setIconColor(AppColor.TEXT_MUTED);
            JLabel emptyLabel = new JLabel("Ca này chưa có giao dịch thu/chi nào.", emptyIcon, SwingConstants.LEFT);
            emptyLabel.setIconTextGap(8);
            emptyLabel.setFont(AppFont.BODY);
            emptyLabel.setForeground(AppColor.TEXT_MUTED);
            empty.add(emptyLabel);
            section.add(empty);
            return section;
        }

        section.add(buildTransactionHeaderRow());
        section.add(Box.createVerticalStrut(4));

        for (ShiftCashTransaction t : transactions) {
            section.add(buildTransactionRow(t));
            section.add(Box.createVerticalStrut(6));
        }

        return section;
    }

    /** Tỷ lệ cột: Loại | Số tiền | Lý do | Người tạo · giờ */
    private static final double[] TX_COL_WEIGHTS = {0.16, 0.18, 0.36, 0.30};

    private JPanel buildTxColumnsRow(Component colType, Component colAmount, Component colReason, Component colBy) {
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new java.awt.Insets(0, 0, 0, 8);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0;
        gbc.weightx = TX_COL_WEIGHTS[0];
        row.add(colType, gbc);

        gbc.gridx = 1;
        gbc.weightx = TX_COL_WEIGHTS[1];
        row.add(colAmount, gbc);

        gbc.gridx = 2;
        gbc.weightx = TX_COL_WEIGHTS[2];
        row.add(colReason, gbc);

        gbc.gridx = 3;
        gbc.weightx = TX_COL_WEIGHTS[3];
        gbc.insets = new java.awt.Insets(0, 0, 0, 0);
        row.add(colBy, gbc);

        return row;
    }

    private JPanel buildTransactionHeaderRow() {
        JPanel row = buildTxColumnsRow(
                colHeader("Loại"), colHeader("Số tiền"), colHeader("Lý do"), colHeader("Người tạo · giờ"));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        return row;
    }

    private JLabel colHeader(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFont.SMALL_BOLD);
        l.setForeground(AppColor.TEXT_MUTED);
        return l;
    }

    private JPanel buildTransactionRow(ShiftCashTransaction t) {
        boolean cashIn = t.isCashIn();
        Color typeColor = cashIn ? AppColor.SUCCESS : AppColor.WARNING;
        Color typeBg = cashIn ? AppColor.SUCCESS_BG : AppColor.WARNING_BG;
        JLabel typeChip = chip(cashIn ? FontAwesomeSolid.ARROW_DOWN : FontAwesomeSolid.ARROW_UP,
                cashIn ? "Thu tiền" : "Chi tiền", typeColor, typeBg);
        JPanel typeWrap = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        typeWrap.setOpaque(false);
        typeWrap.add(typeChip);

        BigDecimal amount = t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO;
        JLabel amountLabel = new JLabel((cashIn ? "+" : "-") + money(amount));
        amountLabel.setFont(AppFont.BODY_BOLD);
        amountLabel.setForeground(typeColor);

        String reason = t.getReason() != null && !t.getReason().isBlank() ? t.getReason() : "—";
        JLabel reasonLabel = new JLabel("<html><div style='width:220px'>" + escapeHtml(reason) + "</div></html>");
        reasonLabel.setFont(AppFont.BODY);
        reasonLabel.setForeground(AppColor.TEXT_PRIMARY);
        reasonLabel.setToolTipText(reason);

        JPanel byBox = new JPanel();
        byBox.setOpaque(false);
        byBox.setLayout(new BoxLayout(byBox, BoxLayout.Y_AXIS));

        JLabel byLabel = new JLabel(t.getCreatedByName() != null ? t.getCreatedByName() : "—");
        byLabel.setFont(AppFont.BODY_BOLD);
        byLabel.setForeground(AppColor.TEXT_PRIMARY);
        byLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel timeLabel = new JLabel(t.getCreatedAt() != null ? dateTime(t.getCreatedAt()) : "—");
        timeLabel.setFont(AppFont.SMALL);
        timeLabel.setForeground(AppColor.TEXT_MUTED);
        timeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        byBox.add(byLabel);
        byBox.add(timeLabel);

        JPanel inner = buildTxColumnsRow(typeWrap, amountLabel, reasonLabel, byBox);

        RoundedPanel row = new RoundedPanel(8, AppColor.BG_LIGHT, AppColor.BORDER);
        row.setLayout(new BorderLayout());
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        row.setBorder(new EmptyBorder(8, 12, 8, 12));
        row.add(inner, BorderLayout.CENTER);
        return row;
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
    // Tiện ích UI dùng chung
    // ---------------------------------------------------------------

    private JLabel sectionTitle(FontAwesomeSolid icon, String text) {
        FontIcon fontIcon = FontIcon.of(icon, 14);
        fontIcon.setIconColor(AppColor.ACCENT);
        JLabel label = new JLabel(text, fontIcon, SwingConstants.LEFT);
        label.setIconTextGap(8);
        label.setFont(AppFont.BODY_BOLD);
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
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

    private static String dateTime(LocalDateTime value) {
        return value != null ? value.format(DATE_TIME_FORMAT) : "—";
    }

    private static String money(BigDecimal value) {
        if (value == null) return "0 đ";
        return com.utils.NumberUtil.formatThousands(value.longValue()) + " đ";
    }

    private static String signedMoney(BigDecimal value) {
        if (value == null) return "0 đ";
        String prefix = value.signum() > 0 ? "+" : "";
        return prefix + money(value);
    }

    /** Công thức tiền hệ thống dự kiến: đầu ca + doanh thu tiền mặt + thu - chi - hoàn tiền. */
    private static BigDecimal expectedOf(Shift shift) {
        return shift.getOpeningCash()
                .add(shift.getCashSales())
                .add(shift.getCashIn())
                .subtract(shift.getCashOut())
                .subtract(shift.getCashRefunds());
    }

    // ---------------------------------------------------------------
    // Panel nền bo góc, dùng cho thẻ so sánh / huy hiệu / khung ghi chú.
    // ---------------------------------------------------------------

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

    /** Trạng thái trực quan (màu/icon/nhãn) suy ra từ dấu chênh lệch tiền mặt. */
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

        static Status of(BigDecimal difference) {
            int signum = difference == null ? 0 : difference.signum();
            if (signum < 0) {
                return new Status(AppColor.ERROR, AppColor.ERROR_BG, FontAwesomeSolid.ARROW_DOWN,
                        "Thiếu hụt quỹ tiền mặt", signedMoney(difference));
            }
            if (signum > 0) {
                return new Status(AppColor.WARNING, AppColor.WARNING_BG, FontAwesomeSolid.ARROW_UP,
                        "Dư thừa so với hệ thống", signedMoney(difference));
            }
            return new Status(AppColor.SUCCESS, AppColor.SUCCESS_BG, FontAwesomeSolid.CHECK,
                    "Khớp với hệ thống", "Khớp");
        }
    }

    /** Trạng thái ca (màu/icon) cho chip ở header. */
    private static final class StatusInfo {
        final Color accent;
        final Color bg;
        final FontAwesomeSolid icon;

        private StatusInfo(Color accent, Color bg, FontAwesomeSolid icon) {
            this.accent = accent;
            this.bg = bg;
            this.icon = icon;
        }

        static StatusInfo of(String status) {
            if (Shift.STATUS_OPEN.equalsIgnoreCase(status)) {
                return new StatusInfo(AppColor.SUCCESS, AppColor.SUCCESS_BG, FontAwesomeSolid.PLAY_CIRCLE);
            }
            if (Shift.STATUS_PENDING_APPROVAL.equalsIgnoreCase(status)) {
                return new StatusInfo(AppColor.WARNING, AppColor.WARNING_BG, FontAwesomeSolid.HOURGLASS_HALF);
            }
            if (Shift.STATUS_REJECTED.equalsIgnoreCase(status)) {
                return new StatusInfo(AppColor.ERROR, AppColor.ERROR_BG, FontAwesomeSolid.TIMES_CIRCLE);
            }
            return new StatusInfo(AppColor.TEXT_MUTED, AppColor.BG_LIGHT, FontAwesomeSolid.CHECK_CIRCLE);
        }
    }
}