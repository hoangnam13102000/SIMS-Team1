package com.view.admin.auditlog;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import com.model.ActivityLog;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;

/**
 * Dialog CHI XEM chi tiết 1 dòng audit log — header badge theo loại hành động,
 * meta info dạng card, snapshot JSON trước/sau thay đổi.
 */
final class AuditLogDetailDialog extends JDialog {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private AuditLogDetailDialog(Window owner, ActivityLog log, String actionLabel, String entityLabel) {
        super(owner, "Chi tiết nhật ký", ModalityType.APPLICATION_MODAL);
        setSize(720, 620);
        setMinimumSize(new Dimension(560, 480));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        add(buildHeader(log, actionLabel), BorderLayout.NORTH);
        add(buildBody(log, actionLabel, entityLabel), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Header: icon badge + title + action pill
    // ------------------------------------------------------------------

    private JPanel buildHeader(ActivityLog log, String actionLabel) {
        Color accent = actionColor(log.getAction());
        FontAwesomeSolid icon = actionIcon(log.getAction());

        JPanel header = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        header.setOpaque(true);
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)
        ));

        // Icon circle
        JLabel iconLabel = new JLabel(FontIcon.of(icon, 18, accent));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(44, 44));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(soft(accent, 28));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Chi tiết nhật ký");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(actionLabel != null ? actionLabel : "—");
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(title);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(subtitle);

        // Action badge (pill)
        JLabel badge = new JLabel(actionLabel != null ? actionLabel.toUpperCase() : "—");
        badge.setFont(AppFont.SMALL_BOLD);
        badge.setForeground(accent);
        badge.setOpaque(true);
        badge.setBackground(soft(accent, 24));
        badge.setBorder(new EmptyBorder(6, 12, 6, 12));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, AppSpacing.MD, 0));
        left.setOpaque(false);
        left.add(iconLabel);
        left.add(textCol);

        header.add(left, BorderLayout.WEST);
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    // ------------------------------------------------------------------
    // Body: meta cards + description + JSON tabs
    // ------------------------------------------------------------------

    private JPanel buildBody(ActivityLog log, String actionLabel, String entityLabel) {
        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.MD));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.SM, AppSpacing.XL));

        body.add(buildMetaSection(log, entityLabel), BorderLayout.NORTH);
        body.add(buildSnapshotSection(log), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildMetaSection(ActivityLog log, String entityLabel) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        // 2x2 meta grid
        JPanel grid = new JPanel(new GridLayout(2, 2, AppSpacing.MD, AppSpacing.MD));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));

        String time = log.getCreatedAt() != null ? DATE_FORMAT.format(log.getCreatedAt()) : "—";
        String user = log.getUsername() != null ? log.getUsername() : "SYSTEM";
        String target = entityLabel
                + (log.getRecordId() != null ? " #" + log.getRecordId() : "");

        grid.add(metaCard(FontAwesomeSolid.CLOCK, "Thời gian", time, AppColor.ACCENT));
        grid.add(metaCard(FontAwesomeSolid.USER, "Người dùng", user, AppColor.SUCCESS));
        grid.add(metaCard(FontAwesomeSolid.TAG, "Đối tượng", target, AppColor.WARNING));
        grid.add(metaCard(FontAwesomeSolid.HASHTAG, "Log ID",
                log.getLogId() > 0 ? String.valueOf(log.getLogId()) : "—", AppColor.TEXT_MUTED));

        section.add(grid);
        section.add(Box.createVerticalStrut(AppSpacing.MD));

        // Description card
        String desc = log.getDescription() != null && !log.getDescription().isBlank()
                ? log.getDescription() : "Không có mô tả.";
        section.add(descriptionCard(desc));

        return section;
    }

    private JPanel metaCard(FontAwesomeSolid icon, String label, String value, Color accent) {
        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout(AppSpacing.SM, 0));
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));

        JLabel iconLbl = new JLabel(FontIcon.of(icon, 14, accent));
        iconLbl.setVerticalAlignment(SwingConstants.TOP);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel(label.toUpperCase());
        lbl.setFont(AppFont.SMALL_BOLD);
        lbl.setForeground(AppColor.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(AppFont.BODY_BOLD);
        val.setForeground(AppColor.TEXT_TITLE);
        val.setAlignmentX(Component.LEFT_ALIGNMENT);
        val.setToolTipText(value);

        text.add(lbl);
        text.add(Box.createVerticalStrut(4));
        text.add(val);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel descriptionCard(String description) {
        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout(AppSpacing.SM, 0));
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));

        JLabel iconLbl = new JLabel(FontIcon.of(FontAwesomeSolid.ALIGN_LEFT, 14, AppColor.ACCENT));
        iconLbl.setVerticalAlignment(SwingConstants.TOP);

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));

        JLabel lbl = new JLabel("MÔ TẢ");
        lbl.setFont(AppFont.SMALL_BOLD);
        lbl.setForeground(AppColor.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JTextArea area = new JTextArea(description);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(AppFont.BODY);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setBorder(null);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);

        text.add(lbl);
        text.add(Box.createVerticalStrut(4));
        text.add(area);

        card.add(iconLbl, BorderLayout.WEST);
        card.add(text, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildSnapshotSection(ActivityLog log) {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);

        JLabel sectionTitle = new JLabel("Snapshot dữ liệu");
        sectionTitle.setFont(AppFont.BODY_BOLD);
        sectionTitle.setForeground(AppColor.TEXT_TITLE);
        sectionTitle.setBorder(new EmptyBorder(0, 0, AppSpacing.SM, 0));

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(AppFont.BODY);
        tabs.setBackground(AppColor.WHITE);
        tabs.setForeground(AppColor.TEXT_PRIMARY);

        boolean hasOld = log.getOldValue() != null && !log.getOldValue().isBlank();
        boolean hasNew = log.getNewValue() != null && !log.getNewValue().isBlank();

        tabs.addTab("  Trước thay đổi  ", buildJsonPane(log.getOldValue(), !hasOld));
        tabs.addTab("  Sau thay đổi  ", buildJsonPane(log.getNewValue(), !hasNew));

        // Ưu tiên tab có dữ liệu
        if (!hasOld && hasNew) tabs.setSelectedIndex(1);

        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.SM, AppSpacing.SM, AppSpacing.SM));
        card.add(tabs, BorderLayout.CENTER);

        section.add(sectionTitle, BorderLayout.NORTH);
        section.add(card, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildJsonPane(String json, boolean empty) {
        if (empty) {
            JPanel emptyPane = new JPanel(new GridBagLayout());
            emptyPane.setBackground(AppColor.PAGE_BG);
            emptyPane.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

            JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.FILE_CODE, 28, AppColor.TEXT_MUTED));
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel msg = new JLabel("Không có snapshot cho hành động này");
            msg.setFont(AppFont.BODY);
            msg.setForeground(AppColor.TEXT_MUTED);
            msg.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel hint = new JLabel("Một số hành động (đăng nhập, khóa tài khoản...) không lưu trạng thái trước/sau.");
            hint.setFont(AppFont.SMALL);
            hint.setForeground(AppColor.TEXT_MUTED);
            hint.setAlignmentX(Component.CENTER_ALIGNMENT);

            inner.add(icon);
            inner.add(Box.createVerticalStrut(AppSpacing.MD));
            inner.add(msg);
            inner.add(Box.createVerticalStrut(4));
            inner.add(hint);

            emptyPane.add(inner);
            return emptyPane;
        }

        String display;
        try {
            Object parsed = JsonParser.parseString(json);
            display = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(parsed);
        } catch (Exception e) {
            display = json;
        }

        JTextArea area = new JTextArea(display);
        area.setEditable(false);
        area.setFont(new Font("Consolas", Font.PLAIN, 13));
        area.setBackground(AppColor.PAGE_BG);
        area.setForeground(AppColor.TEXT_PRIMARY);
        area.setCaretPosition(0);
        area.setBorder(new EmptyBorder(AppSpacing.MD, AppSpacing.MD, AppSpacing.MD, AppSpacing.MD));
        area.setLineWrap(false);

        JScrollPane scroll = new JScrollPane(area);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setBackground(AppColor.PAGE_BG);
        scroll.getViewport().setBackground(AppColor.PAGE_BG);
        return scroll;
    }

    // ------------------------------------------------------------------
    // Footer
    // ------------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.setOpaque(true);
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.MD, AppSpacing.XL, AppSpacing.MD, AppSpacing.XL)
        ));

        JButton close = new JButton("Đóng");
        close.setFont(AppFont.BUTTON);
        close.setForeground(Color.WHITE);
        close.setBackground(AppColor.ACCENT);
        close.setFocusPainted(false);
        close.setBorderPainted(false);
        close.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        close.setPreferredSize(new Dimension(110, 38));
        close.addActionListener(e -> dispose());
        close.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                close.setBackground(AppColor.ACCENT_HOVER);
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                close.setBackground(AppColor.ACCENT);
            }
        });

        footer.add(close);
        getRootPane().setDefaultButton(close);
        return footer;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Color actionColor(String action) {
        if (action == null) return AppColor.ACCENT;
        switch (action) {
            case ActivityLog.ACTION_CREATE:
            case ActivityLog.ACTION_RESTORE:
                return AppColor.SUCCESS;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE:
            case ActivityLog.ACTION_LOGIN_FAILED:
            case "USER_LOCK":
                return AppColor.ERROR;
            case ActivityLog.ACTION_UPDATE:
            case ActivityLog.ACTION_STATUS_CHANGE:
            case ActivityLog.ACTION_PASSWORD_RESET:
            case "USER_UNLOCK":
                return AppColor.WARNING;
            case ActivityLog.ACTION_LOGIN:
            case ActivityLog.ACTION_LOGOUT:
                return AppColor.ACCENT;
            default:
                return AppColor.ACCENT;
        }
    }

    private static FontAwesomeSolid actionIcon(String action) {
        if (action == null) return FontAwesomeSolid.HISTORY;
        switch (action) {
            case ActivityLog.ACTION_CREATE: return FontAwesomeSolid.PLUS_CIRCLE;
            case ActivityLog.ACTION_UPDATE: return FontAwesomeSolid.EDIT;
            case ActivityLog.ACTION_DELETE:
            case ActivityLog.ACTION_PERMANENT_DELETE: return FontAwesomeSolid.TRASH;
            case ActivityLog.ACTION_RESTORE: return FontAwesomeSolid.UNDO;
            case ActivityLog.ACTION_LOGIN: return FontAwesomeSolid.SIGN_IN_ALT;
            case ActivityLog.ACTION_LOGOUT: return FontAwesomeSolid.SIGN_OUT_ALT;
            case ActivityLog.ACTION_LOGIN_FAILED: return FontAwesomeSolid.EXCLAMATION_TRIANGLE;
            case ActivityLog.ACTION_PASSWORD_RESET: return FontAwesomeSolid.KEY;
            case ActivityLog.ACTION_STATUS_CHANGE: return FontAwesomeSolid.TOGGLE_ON;
            case "USER_LOCK": return FontAwesomeSolid.LOCK;
            case "USER_UNLOCK": return FontAwesomeSolid.UNLOCK;
            default: return FontAwesomeSolid.HISTORY;
        }
    }

    private static Color soft(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /** Panel bo góc nhẹ, vẽ nền + viền. */
    private static class RoundedPanel extends JPanel {
        private final int radius;

        RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D.Float shape = new RoundRectangle2D.Float(0, 0, w - 1, h - 1, radius, radius);
            g2.setColor(getBackground());
            g2.fill(shape);
            g2.setColor(AppColor.BORDER);
            g2.draw(shape);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    static void show(Window owner, ActivityLog log, String actionLabel, String entityLabel) {
        new AuditLogDetailDialog(owner, log, actionLabel, entityLabel).setVisible(true);
    }
}