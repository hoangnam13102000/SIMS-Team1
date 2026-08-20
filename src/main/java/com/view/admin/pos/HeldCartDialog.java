package com.view.admin.pos;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.BaseSearch;
import com.components.EmptyState;
import com.model.HeldCart;
import com.service.HeldCartService;
import com.service.PosCartService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Danh sach gio POS dang tam giu trong ca hien tai cua nhan vien.
 * Thiet ke dang the (card) bo goc + avatar/icon + PillButton/CircleIconButton,
 * dong bo phong cach voi TrashDialog/BaseSearch thay vi JTable + JOptionPane
 * mac dinh nhu truoc, de nhat quan giao dien toan he thong.
 */
public class HeldCartDialog extends JDialog {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter TIME_ONLY = DateTimeFormatter.ofPattern("HH:mm");

    private final HeldCartService service;
    private final PosCartService cart;
    private final BaseSearch searchField = new BaseSearch("Tìm theo mã phiếu, khách hàng hoặc ghi chú...");
    private final JPanel listPanel = new JPanel();
    private final JLabel subtitleLabel = new JLabel(" ");
    private List<HeldCart> rows = List.of();
    private boolean restored;
    private HeldCart restoredCart;

    public HeldCartDialog(Frame owner, HeldCartService service, PosCartService cart) {
        super(owner, "Giỏ hàng tạm giữ", true);
        this.service = service;
        this.cart = cart;

        setSize(720, 560);
        setMinimumSize(new Dimension(560, 420));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        searchField.onSearch(q -> loadData());
        loadData();
    }

    public boolean isRestored() { return restored; }
    public HeldCart getRestoredCart() { return restoredCart; }

    // ---------------------------------------------------------------
    // Header: icon badge tron + tieu de + phu de dem so luong + o tim kiem
    // ---------------------------------------------------------------
    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)));

        JPanel titleRow = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon pauseIcon = FontIcon.of(FontAwesomeSolid.PAUSE, 18);
        pauseIcon.setIconColor(AppColor.ACCENT);
        JLabel iconBadge = new JLabel(pauseIcon, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBadge.setPreferredSize(new Dimension(44, 44));

        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel("Giỏ đang tạm giữ trong ca hiện tại");
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleBox.add(titleLabel);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(subtitleLabel);

        titleRow.add(iconBadge, BorderLayout.WEST);
        titleRow.add(titleBox, BorderLayout.CENTER);
        header.add(titleRow);
        header.add(Box.createVerticalStrut(AppSpacing.MD));

        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setPreferredWidth(Integer.MAX_VALUE);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        header.add(searchField);

        return header;
    }

    // ---------------------------------------------------------------
    // Vung danh sach (scrollable) - cung nen BG_LIGHT nhu TrashDialog
    // ---------------------------------------------------------------
    private JScrollPane buildCenter() {
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(AppColor.BG_LIGHT);
        listPanel.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.LG, AppSpacing.LG, AppSpacing.LG));

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.getViewport().setBackground(AppColor.BG_LIGHT);
        return scrollPane;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.MD, AppSpacing.LG, AppSpacing.MD, AppSpacing.LG)));

        JButton refresh = new PillButton("Làm mới", FontAwesomeSolid.SYNC_ALT,
                AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        refresh.addActionListener(e -> loadData());

        JButton close = new PillButton("Đóng", null,
                AppColor.CANCEL_BG, AppColor.CANCEL_HOVER, AppColor.TEXT_PRIMARY);
        close.addActionListener(e -> dispose());

        footer.add(refresh);
        footer.add(close);
        return footer;
    }

    // ---------------------------------------------------------------
    // Nap du lieu + render lai danh sach dang the
    // ---------------------------------------------------------------
    private void loadData() {
        rows = service.getMyHeldCarts(searchField.getText());

        subtitleLabel.setText(rows.isEmpty()
                ? "Không có giỏ nào đang tạm giữ"
                : rows.size() + " giỏ đang tạm giữ \u00b7 bấm Khôi phục để tiếp tục bán");

        listPanel.removeAll();

        if (rows.isEmpty()) {
            listPanel.setLayout(new BorderLayout());
            EmptyState empty = new EmptyState(FontAwesomeSolid.PAUSE_CIRCLE,
                    "Chưa có giỏ tạm giữ nào",
                    "Các giỏ hàng bạn tạm giữ để phục vụ khách khác sẽ xuất hiện tại đây.");
            listPanel.add(empty, BorderLayout.CENTER);
        } else {
            listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
            for (HeldCart h : rows) {
                JComponent card = buildCard(h);
                card.setAlignmentX(Component.LEFT_ALIGNMENT);
                listPanel.add(card);
                listPanel.add(Box.createVerticalStrut(AppSpacing.SM));
            }
        }

        listPanel.revalidate();
        listPanel.repaint();
    }

    // ---------------------------------------------------------------
    // 1 the (card) cho 1 phieu tam giu
    // ---------------------------------------------------------------
    private JComponent buildCard(HeldCart h) {
        boolean[] hover = {false};

        JPanel card = new JPanel(new BorderLayout(AppSpacing.MD, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                int w = getWidth() - 2;
                int h2 = getHeight() - 2;
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.translate(1, 1);

                g2.setColor(new Color(15, 23, 42, hover[0] ? 16 : 8));
                g2.fill(new RoundRectangle2D.Float(0, 2, w, h2, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.setColor(AppColor.WHITE);
                g2.fill(new RoundRectangle2D.Float(0, 0, w, h2, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.setColor(hover[0] ? AppColor.ACCENT_SOFT : AppColor.BORDER);
                g2.setStroke(new BasicStroke(hover[0] ? 1.4f : 1f));
                g2.draw(new RoundRectangle2D.Float(0, 0, w, h2, AppRadius.MEDIUM, AppRadius.MEDIUM));

                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));
        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover[0] = true; card.repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover[0] = false; card.repaint(); }
        });

        // Boc badge trong 1 panel GridBagLayout (khong fill) truoc khi dat vao
        // BorderLayout.WEST - vi BorderLayout luon keo gian component WEST/EAST
        // theo chieu cao CONTAINER cha bat ke setMaximumSize() cua chinh no, khien
        // icon tron bi keo thanh hinh bau duc. GridBagLayout mac dinh KHONG fill,
        // nen se giu nguyen kich thuoc that (40x40) va tu can giua theo chieu doc.
        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        badgeWrap.add(buildHoldBadge(h));

        card.add(badgeWrap, BorderLayout.WEST);
        card.add(buildInfo(h), BorderLayout.CENTER);
        card.add(buildActions(h), BorderLayout.EAST);

        // Gioi han chieu cao toi da cua the theo dung noi dung thuc te - neu
        // khong, BoxLayout(Y_AXIS) cua listPanel se keo gian the duy nhat (hoac
        // the cuoi cung) chiem het khoang trong con lai cua khung cuon, vi
        // JPanel dung BorderLayout mac dinh tra ve maximumSize = KHONG GIOI HAN.
        int preferredHeight = card.getPreferredSize().height;
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, preferredHeight));

        return card;
    }

    /** Icon tron ben trai the - mau sac bao hieu thoi gian da giu (moi/canh bao/lau). */
    private JLabel buildHoldBadge(HeldCart h) {
        Color bg;
        Color fg;
        switch (heldUrgency(h)) {
            case 2 -> { bg = AppColor.ERROR_BG; fg = AppColor.ERROR; }
            case 1 -> { bg = AppColor.WARNING_BG; fg = AppColor.WARNING; }
            default -> { bg = AppColor.ACCENT_BG_SOFT; fg = AppColor.ACCENT; }
        }

        FontIcon icon = FontIcon.of(FontAwesomeSolid.SHOPPING_BASKET, 16);
        icon.setIconColor(fg);
        JLabel badge = new JLabel(icon, SwingConstants.CENTER) {
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
        badge.setPreferredSize(new Dimension(40, 40));
        badge.setMinimumSize(new Dimension(40, 40));
        badge.setMaximumSize(new Dimension(40, 40));
        return badge;
    }

    private JPanel buildInfo(HeldCart h) {
        JPanel info = new JPanel();
        info.setOpaque(false);
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setBorder(new EmptyBorder(0, 0, 0, AppSpacing.MD));

        String customer = h.getCustomerLabelSnapshot() != null && !h.getCustomerLabelSnapshot().isBlank()
                ? h.getCustomerLabelSnapshot() : "Khách lẻ";

        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        topRow.setOpaque(false);
        topRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel codeLabel = new JLabel(h.getHoldCode());
        codeLabel.setFont(AppFont.BODY_BOLD);
        codeLabel.setForeground(AppColor.TEXT_PRIMARY);
        topRow.add(codeLabel);
        topRow.add(buildTimeChip(h));
        info.add(topRow);

        info.add(Box.createVerticalStrut(3));

        JLabel metaLabel = new JLabel(customer + "  \u00b7  " + h.getItemCount() + " mặt hàng  \u00b7  "
                + com.utils.NumberUtil.formatThousands(h.getSubTotalSnapshot().longValue()) + " đ");
        metaLabel.setFont(AppFont.SMALL);
        metaLabel.setForeground(AppColor.TEXT_MUTED);
        metaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        info.add(metaLabel);

        if (h.getNote() != null && !h.getNote().isBlank()) {
            info.add(Box.createVerticalStrut(3));
            String noteText = h.getNote().trim();
            String shown = noteText.length() > 70 ? noteText.substring(0, 70) + "…" : noteText;
            JLabel noteLabel = new JLabel("“" + shown + "”");
            noteLabel.setFont(AppFont.SMALL);
            noteLabel.setForeground(AppColor.TEXT_MUTED_ALT);
            noteLabel.setToolTipText(noteText);
            noteLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
            info.add(noteLabel);
        }

        return info;
    }

    /** Chip nho hien thoi gian tam giu (tuong doi), mau theo do khan cap. */
    private JLabel buildTimeChip(HeldCart h) {
        Color bg;
        Color fg;
        switch (heldUrgency(h)) {
            case 2 -> { bg = AppColor.ERROR_BG; fg = AppColor.ERROR; }
            case 1 -> { bg = AppColor.WARNING_BG; fg = AppColor.WARNING; }
            default -> { bg = AppColor.BG_LIGHTER; fg = AppColor.TEXT_MUTED; }
        }
        JLabel chip = new JLabel(formatRelativeTime(h.getHeldAt())) {
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
        chip.setFont(AppFont.SMALL);
        chip.setForeground(fg);
        chip.setBorder(new EmptyBorder(2, 8, 2, 8));
        chip.setToolTipText(h.getHeldAt() != null ? h.getHeldAt().format(DATE_TIME) : null);
        return chip;
    }

    private JPanel buildActions(HeldCart h) {
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.setOpaque(false);

        JButton restoreBtn = new PillButton("Khôi phục", FontAwesomeSolid.UNDO,
                AppColor.SUCCESS_BG, AppColor.SUCCESS, AppColor.SUCCESS);
        restoreBtn.addActionListener(e -> restore(h));
        actions.add(restoreBtn);

        JButton cancelBtn = new CircleIconButton(FontAwesomeSolid.TRASH, AppColor.ERROR, AppColor.ERROR_BG, "Hủy phiếu");
        cancelBtn.addActionListener(e -> cancel(h));
        actions.add(cancelBtn);

        return actions;
    }

    // ---------------------------------------------------------------
    // Hanh dong
    // ---------------------------------------------------------------
    private void restore(HeldCart h) {
        HeldCartService.Result<HeldCart> result = service.restoreToCurrentCart(h.getHoldId(), cart);
        if (!result.isSuccess()) {
            AppAlert.error(this, "Không thể khôi phục", result.getMessage());
            return;
        }
        restored = true;
        restoredCart = result.getData();
        AppAlert.success(this, "Đã khôi phục giỏ", result.getMessage());
        dispose();
    }

    private void cancel(HeldCart h) {
        boolean confirmed = BaseDialog.confirm(this, "Xác nhận hủy phiếu",
                "Hủy phiếu " + h.getHoldCode() + "? Giỏ này sẽ không thể khôi phục lại.",
                "Hủy phiếu", AppColor.ERROR, AppColor.ERROR_HOVER, FontAwesomeSolid.TRASH);
        if (!confirmed) return;

        HeldCartService.Result<HeldCart> result = service.cancel(h.getHoldId());
        if (!result.isSuccess()) {
            AppAlert.error(this, "Không thể hủy phiếu", result.getMessage());
            return;
        }
        AppAlert.success(this, "Đã hủy phiếu", result.getMessage());
        loadData();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    /** 0 = moi giu (<15p), 1 = canh bao (15-60p), 2 = da lau (>60p). */
    private int heldUrgency(HeldCart h) {
        if (h.getHeldAt() == null) return 0;
        long minutes = Duration.between(h.getHeldAt(), LocalDateTime.now()).toMinutes();
        if (minutes >= 60) return 2;
        if (minutes >= 15) return 1;
        return 0;
    }

    private String formatRelativeTime(LocalDateTime dt) {
        if (dt == null) return "—";
        long minutes = Duration.between(dt, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        LocalDateTime now = LocalDateTime.now();
        if (dt.toLocalDate().isEqual(now.toLocalDate())) return dt.format(TIME_ONLY);
        return dt.format(DATE_TIME);
    }

    // ---------------------------------------------------------------
    // Nut bo tron kieu "vien mem -> to dam khi hover" (giong TrashDialog)
    // ---------------------------------------------------------------
    private static final class PillButton extends JButton {

        private final Color softBg;
        private final Color solidBg;
        private final Color accentFg;
        private boolean hover = false;

        PillButton(String text, FontAwesomeSolid icon, Color softBg, Color solidBg, Color accentFg) {
            super(text);
            this.softBg = softBg;
            this.solidBg = solidBg;
            this.accentFg = accentFg;

            if (icon != null) {
                FontIcon normalIcon = FontIcon.of(icon, 12);
                normalIcon.setIconColor(accentFg);
                setIcon(normalIcon);
                setIconTextGap(6);
            }

            setFont(AppFont.SMALL_BOLD);
            setForeground(accentFg);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(7, 16, 7, 16));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    hover = true;
                    setForeground(Color.WHITE);
                    if (icon != null) {
                        FontIcon hoverIcon = FontIcon.of(icon, 12);
                        hoverIcon.setIconColor(Color.WHITE);
                        setIcon(hoverIcon);
                    }
                    repaint();
                }
                @Override public void mouseExited(MouseEvent e) {
                    hover = false;
                    setForeground(PillButton.this.accentFg);
                    if (icon != null) {
                        FontIcon normalIcon = FontIcon.of(icon, 12);
                        normalIcon.setIconColor(PillButton.this.accentFg);
                        setIcon(normalIcon);
                    }
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(hover ? solidBg : softBg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // ---------------------------------------------------------------
    // Nut icon tron (Huy phieu) - chi noi bat mau khi hover (giong TrashDialog)
    // ---------------------------------------------------------------
    private static final class CircleIconButton extends JButton {

        private final Color hoverBg;
        private boolean hover = false;

        CircleIconButton(FontAwesomeSolid icon, Color color, Color hoverBg, String tooltip) {
            this.hoverBg = hoverBg;

            FontIcon fontIcon = FontIcon.of(icon, 14);
            fontIcon.setIconColor(color);
            setIcon(fontIcon);
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(34, 34));
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setOpaque(false);
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (hover) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(hoverBg);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
            super.paintComponent(g);
        }
    }
}