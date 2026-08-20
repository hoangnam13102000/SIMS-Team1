package com.view.admin.shift;

import com.model.Shift;
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
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Dialog Duyệt / Yêu cầu kiểm lại đối soát ca — UX/UI đồng bộ form Category /
 * User / Customer (banner ngữ cảnh, meta card, field note, footer chuẩn).
 * <p>
 * Dùng từ {@link ShiftMonitorPanel} khi QL bấm Duyệt hoặc Yêu cầu kiểm lại.
 */
public final class ShiftReviewDialog extends JDialog {

    public enum Mode { APPROVE, REJECT }

    private static final NumberFormat MONEY = NumberFormat.getInstance(new Locale("vi", "VN"));
    private static final int MAX_NOTE = 500;

    private final Mode mode;
    private final JTextArea noteArea;
    private final JLabel errorLabel;
    private boolean confirmed;
    private String noteResult;

    private ShiftReviewDialog(Window owner, Mode mode, Shift shift) {
        super(owner, mode == Mode.APPROVE ? "Duyệt đối soát ca" : "Yêu cầu kiểm lại",
                ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.noteArea = createNoteArea();
        this.errorLabel = new JLabel(" ");
        errorLabel.setFont(AppFont.SMALL);
        errorLabel.setForeground(AppColor.ERROR);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);

        add(buildTitleBar(), BorderLayout.NORTH);
        add(buildBody(shift), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(mode == Mode.APPROVE ? 480 : 500, mode == Mode.APPROVE ? 520 : 560);
        setMinimumSize(new Dimension(420, 420));
        setLocationRelativeTo(owner);
    }

    /**
     * @return note (có thể rỗng khi APPROVE), hoặc {@code null} nếu người dùng Hủy.
     */
    public static String show(Component parent, Mode mode, Shift shift) {
        if (shift == null) return null;
        Window owner = parent instanceof Window
                ? (Window) parent
                : javax.swing.SwingUtilities.getWindowAncestor(parent);
        if (owner == null) {
            owner = new Frame();
        }
        ShiftReviewDialog dialog = new ShiftReviewDialog(owner, mode, shift);
        dialog.setVisible(true);
        return dialog.confirmed ? (dialog.noteResult != null ? dialog.noteResult : "") : null;
    }

    private JPanel buildTitleBar() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(16, 22, 14, 22)));

        String title = mode == Mode.APPROVE ? "Duyệt đối soát ca" : "Yêu cầu kiểm lại";
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        header.add(titleLabel, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildBody(Shift shift) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColor.WHITE);
        panel.setBorder(new EmptyBorder(16, 22, 12, 22));

        panel.add(buildInfoBanner(shift));
        panel.add(Box.createVerticalStrut(14));
        panel.add(buildMetaCard(shift));
        panel.add(Box.createVerticalStrut(14));
        panel.add(buildMoneyCard(shift));
        panel.add(Box.createVerticalStrut(14));

        String noteLabel = mode == Mode.APPROVE
                ? "Ghi chú duyệt (tùy chọn)"
                : "Lý do cần kiểm lại *";
        panel.add(sectionLabel(noteLabel));
        panel.add(Box.createVerticalStrut(6));

        JScrollPane scroll = new JScrollPane(noteArea);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        scroll.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        scroll.setOpaque(false);
        scroll.getViewport().setBackground(AppColor.WHITE);
        panel.add(scroll);
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel(mode == Mode.APPROVE
                ? "Có thể để trống. Ghi chú sẽ lưu cùng lần duyệt."
                : "Bắt buộc — nhân viên sẽ thấy lý do để kiểm đếm lại."));
        panel.add(Box.createVerticalStrut(8));
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(errorLabel);

        JScrollPane outer = new JScrollPane(panel);
        outer.setBorder(null);
        outer.getViewport().setBackground(AppColor.WHITE);
        outer.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(AppColor.WHITE);
        wrap.add(outer, BorderLayout.CENTER);
        return wrap;
    }

    private JPanel buildInfoBanner(Shift shift) {
        boolean approve = mode == Mode.APPROVE;
        Color accent = approve ? AppColor.SUCCESS : AppColor.WARNING;
        Color softBg = approve
                ? (AppColor.SUCCESS_BG != null ? AppColor.SUCCESS_BG : new Color(236, 253, 245))
                : (AppColor.WARNING_BG != null ? AppColor.WARNING_BG : new Color(255, 251, 235));

        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(softBg);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));

        FontIcon icon = FontIcon.of(
                approve ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.EXCLAMATION_TRIANGLE, 16);
        icon.setIconColor(accent);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        String staff = shift.getUserName() != null ? shift.getUserName() : "nhân viên";
        String html = approve
                ? "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Xác nhận duyệt đối soát</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Ca #" + shift.getShiftId() + " · " + staff
                + " — quỹ sẽ được ghi nhận đã duyệt.</span></html>"
                : "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Yêu cầu nhân viên kiểm lại</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Ca #" + shift.getShiftId() + " · " + staff
                + " — ca vẫn đóng, chỉ tạo vòng đối soát mới.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconLabel, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildMetaCard(Shift shift) {
        JPanel card = new JPanel(new GridLayout(1, 2, 12, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.BG_LIGHTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        String staff = shift.getUserName() != null ? shift.getUserName() : "—";
        card.add(metaChip(FontAwesomeSolid.HASHTAG, "Mã ca", "#" + shift.getShiftId()));
        card.add(metaChip(FontAwesomeSolid.USER, "Nhân viên", staff));
        return card;
    }

    private JPanel buildMoneyCard(Shift shift) {
        JPanel card = new JPanel(new GridLayout(1, 3, 10, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 78));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(12, 12, 12, 12)));

        BigDecimal expected = shift.getExpectedCash() != null ? shift.getExpectedCash() : BigDecimal.ZERO;
        BigDecimal counted = shift.getCountedCash() != null ? shift.getCountedCash() : BigDecimal.ZERO;
        BigDecimal diff = shift.getCashDifference() != null
                ? shift.getCashDifference()
                : counted.subtract(expected);

        card.add(moneyChip("Quỹ hệ thống", money(expected), AppColor.TEXT_PRIMARY));
        card.add(moneyChip("Tiền đếm", money(counted), AppColor.TEXT_PRIMARY));
        Color diffColor = diff.signum() == 0 ? AppColor.SUCCESS
                : (diff.signum() > 0 ? AppColor.INFO : AppColor.ERROR);
        card.add(moneyChip("Chênh lệch", signedMoney(diff), diffColor));
        return card;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(AppColor.BG_LIGHT != null ? AppColor.BG_LIGHT : AppColor.BG_LIGHTER);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 22, 12, 22)));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);

        JButton cancel = new JButton("Hủy");
        cancel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        cancel.setFocusPainted(false);
        cancel.setBackground(AppColor.BORDER);
        cancel.setForeground(AppColor.TEXT_PRIMARY);
        cancel.setBorder(new EmptyBorder(8, 18, 8, 18));
        cancel.addActionListener(e -> dispose());

        boolean approve = mode == Mode.APPROVE;
        JButton ok = new JButton(approve ? "Duyệt đối soát" : "Yêu cầu kiểm lại");
        ok.setFont(new Font("Segoe UI", Font.BOLD, 13));
        ok.setFocusPainted(false);
        Color okBg = approve ? AppColor.SUCCESS : AppColor.WARNING;
        Color okHover = approve
                ? (AppColor.SUCCESS != null ? AppColor.SUCCESS.darker() : okBg)
                : (AppColor.WARNING != null ? AppColor.WARNING.darker() : okBg);
        ok.setBackground(okBg);
        ok.setForeground(Color.WHITE);
        ok.setBorder(new EmptyBorder(8, 18, 8, 18));
        ok.getModel().addChangeListener(e -> {
            if (ok.isEnabled()) {
                ok.setBackground(ok.getModel().isRollover() ? okHover : okBg);
            }
        });
        ok.addActionListener(e -> onConfirm());

        buttons.add(cancel);
        buttons.add(ok);
        footer.add(buttons, BorderLayout.EAST);
        getRootPane().setDefaultButton(ok);
        return footer;
    }

    private void onConfirm() {
        String note = noteArea.getText() != null ? noteArea.getText().trim() : "";
        if (mode == Mode.REJECT && note.isEmpty()) {
            errorLabel.setText("Vui lòng nhập lý do yêu cầu kiểm lại.");
            noteArea.requestFocusInWindow();
            return;
        }
        if (note.length() > MAX_NOTE) {
            errorLabel.setText("Ghi chú tối đa " + MAX_NOTE + " ký tự.");
            return;
        }
        confirmed = true;
        noteResult = note;
        dispose();
    }

    private JTextArea createNoteArea() {
        JTextArea area = new JTextArea(4, 28);
        area.setFont(AppFont.FIELD);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBackground(AppColor.WHITE);
        area.setCaretColor(AppColor.ACCENT);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        return area;
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel hintLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.SMALL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel metaChip(FontAwesomeSolid iconType, String label, String value) {
        FontIcon icon = FontIcon.of(iconType, 11);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel lab = new JLabel(label, icon, SwingConstants.LEFT);
        lab.setIconTextGap(5);
        lab.setFont(AppFont.SMALL);
        lab.setForeground(AppColor.TEXT_MUTED);
        lab.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(AppFont.SMALL_BOLD);
        val.setForeground(AppColor.TEXT_PRIMARY);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(lab);
        col.add(Box.createVerticalStrut(3));
        col.add(val);
        return col;
    }

    private JPanel moneyChip(String label, String value, Color valueColor) {
        JLabel lab = new JLabel(label);
        lab.setFont(AppFont.SMALL);
        lab.setForeground(AppColor.TEXT_MUTED);
        lab.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(AppFont.HEADING_MD);
        val.setForeground(valueColor);
        val.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(lab);
        col.add(Box.createVerticalStrut(4));
        col.add(val);
        return col;
    }

    private static String money(BigDecimal v) {
        if (v == null) return "0 ₫";
        return MONEY.format(v) + " ₫";
    }

    private static String signedMoney(BigDecimal v) {
        if (v == null || v.signum() == 0) return "0 ₫";
        String prefix = v.signum() > 0 ? "+" : "";
        return prefix + MONEY.format(v) + " ₫";
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }
}