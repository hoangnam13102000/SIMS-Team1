package com.view.admin.auditlog;

import com.incident.IncidentSeverity;
import com.incident.IncidentType;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.geom.RoundRectangle2D;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

/**
 * Dialog CHI XEM chi tiết 1 dòng nhật ký sự cố (incident) — header badge theo
 * mức độ nghiêm trọng, meta info dạng card, mô tả đầy đủ và ngăn xếp lỗi
 * (stack trace) nếu có.
 */
final class IncidentDetailDialog extends JDialog {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private final String rawLine;

    private IncidentDetailDialog(Window owner, String rawLine) {
        super(owner, "Chi tiết sự cố", ModalityType.APPLICATION_MODAL);
        this.rawLine = rawLine;

        String timestamp = extractJsonField(rawLine, "timestamp");
        String severityRaw = extractJsonField(rawLine, "severity");
        String typeRaw = extractJsonField(rawLine, "type");
        String source = extractJsonField(rawLine, "source");
        String message = extractJsonField(rawLine, "message");
        String stackTrace = extractJsonField(rawLine, "stackTrace");

        IncidentSeverity severity = parseSeverity(severityRaw);
        IncidentTypeCatalog.Entry typeEntry = parseTypeEntry(typeRaw);

        setSize(720, 640);
        setMinimumSize(new Dimension(560, 480));
        setLocationRelativeTo(owner);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.PAGE_BG);

        add(buildHeader(severity, typeEntry, typeRaw), BorderLayout.NORTH);
        add(buildBody(timestamp, severity, severityRaw, typeEntry, typeRaw, source, message, stackTrace),
                BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    // Header: icon badge + title + severity pill
    // ------------------------------------------------------------------

    private JPanel buildHeader(IncidentSeverity severity, IncidentTypeCatalog.Entry typeEntry, String typeRaw) {
        Color accent = severityColor(severity);
        FontAwesomeSolid icon = typeEntry != null ? typeEntry.icon : FontAwesomeSolid.EXCLAMATION_TRIANGLE;

        JPanel header = new JPanel(new BorderLayout(AppSpacing.MD, 0));
        header.setOpaque(true);
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.LG, AppSpacing.XL)
        ));

        JLabel iconLabel = new JLabel(FontIcon.of(icon, 18, accent));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setPreferredSize(new Dimension(44, 44));
        iconLabel.setOpaque(true);
        iconLabel.setBackground(soft(accent, 28));
        iconLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        JLabel title = new JLabel("Chi tiết sự cố");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(typeEntry != null ? typeEntry.label : (typeRaw != null && !typeRaw.isBlank() ? typeRaw : "—"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        textCol.add(title);
        textCol.add(Box.createVerticalStrut(3));
        textCol.add(subtitle);

        JLabel badge = new JLabel(severityLabel(severity).toUpperCase());
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
    // Body: meta cards + description + stack trace
    // ------------------------------------------------------------------

    private JPanel buildBody(String timestamp, IncidentSeverity severity, String severityRaw,
                              IncidentTypeCatalog.Entry typeEntry, String typeRaw,
                              String source, String message, String stackTrace) {
        JPanel body = new JPanel(new BorderLayout(0, AppSpacing.MD));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XL, AppSpacing.SM, AppSpacing.XL));

        body.add(buildMetaSection(timestamp, severity, typeEntry, typeRaw, source, message), BorderLayout.NORTH);
        body.add(buildStackTraceSection(stackTrace), BorderLayout.CENTER);
        return body;
    }

    private JPanel buildMetaSection(String timestamp, IncidentSeverity severity,
                                     IncidentTypeCatalog.Entry typeEntry, String typeRaw,
                                     String source, String message) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

        JPanel grid = new JPanel(new GridLayout(2, 2, AppSpacing.MD, AppSpacing.MD));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 148));

        String friendlyTime = formatTimestamp(timestamp);
        String typeLabel = typeEntry != null ? typeEntry.label : (typeRaw != null && !typeRaw.isBlank() ? typeRaw : "—");

        grid.add(metaCard(FontAwesomeSolid.CLOCK, "Thời gian", friendlyTime, AppColor.ACCENT));
        grid.add(metaCard(FontAwesomeSolid.EXCLAMATION_CIRCLE, "Mức độ", severityLabel(severity), severityColor(severity)));
        grid.add(metaCard(FontAwesomeSolid.TAG, "Loại sự cố", typeLabel, AppColor.WARNING));
        grid.add(metaCard(FontAwesomeSolid.MICROCHIP, "Nguồn",
                source != null && !source.isBlank() ? source : "—", AppColor.TEXT_MUTED));

        section.add(grid);
        section.add(Box.createVerticalStrut(AppSpacing.MD));

        String desc = message != null && !message.isBlank() ? message : "Không có mô tả.";
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

    private JPanel buildStackTraceSection(String stackTrace) {
        JPanel section = new JPanel(new BorderLayout());
        section.setOpaque(false);

        JLabel sectionTitle = new JLabel("Ngăn xếp lỗi (stack trace)");
        sectionTitle.setFont(AppFont.BODY_BOLD);
        sectionTitle.setForeground(AppColor.TEXT_TITLE);
        sectionTitle.setBorder(new EmptyBorder(0, 0, AppSpacing.SM, 0));

        JPanel card = new RoundedPanel(AppRadius.MEDIUM);
        card.setLayout(new BorderLayout());
        card.setBackground(AppColor.WHITE);
        card.setBorder(new EmptyBorder(AppSpacing.SM, AppSpacing.SM, AppSpacing.SM, AppSpacing.SM));
        card.add(buildStackTracePane(stackTrace), BorderLayout.CENTER);

        section.add(sectionTitle, BorderLayout.NORTH);
        section.add(card, BorderLayout.CENTER);
        return section;
    }

    private JComponent buildStackTracePane(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            JPanel emptyPane = new JPanel(new GridBagLayout());
            emptyPane.setBackground(AppColor.PAGE_BG);
            emptyPane.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

            JPanel inner = new JPanel();
            inner.setOpaque(false);
            inner.setLayout(new BoxLayout(inner, BoxLayout.Y_AXIS));

            JLabel icon = new JLabel(FontIcon.of(FontAwesomeSolid.CHECK_CIRCLE, 28, AppColor.TEXT_MUTED));
            icon.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel msg = new JLabel("Sự cố này không kèm ngăn xếp lỗi");
            msg.setFont(AppFont.BODY);
            msg.setForeground(AppColor.TEXT_MUTED);
            msg.setAlignmentX(Component.CENTER_ALIGNMENT);

            JLabel hint = new JLabel("Thường gặp ở các sự kiện thông tin (sao lưu thành công, kết nối lại DB...).");
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

        JTextArea area = new JTextArea(stackTrace);
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
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(true);
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(AppSpacing.MD, AppSpacing.XL, AppSpacing.MD, AppSpacing.XL)
        ));

        JButton copy = new JButton("Sao chép");
        copy.setFont(AppFont.BUTTON);
        copy.setForeground(AppColor.TEXT_PRIMARY);
        copy.setBackground(AppColor.WHITE);
        copy.setFocusPainted(false);
        copy.setBorder(BorderFactory.createLineBorder(AppColor.BORDER, 1, true));
        copy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copy.setPreferredSize(new Dimension(110, 38));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(rawLine == null ? "" : rawLine), null);
        });

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

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, AppSpacing.SM, 0));
        right.setOpaque(false);
        right.add(copy);
        right.add(close);

        footer.add(right, BorderLayout.EAST);
        getRootPane().setDefaultButton(close);
        return footer;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String formatTimestamp(String rawTimestamp) {
        if (rawTimestamp == null || rawTimestamp.isBlank()) return "—";
        try {
            Instant instant = Instant.parse(rawTimestamp);
            return DATE_FORMAT.format(Date.from(instant));
        } catch (Exception e) {
            return rawTimestamp;
        }
    }

    private static IncidentSeverity parseSeverity(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return IncidentSeverity.valueOf(raw.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static IncidentTypeCatalog.Entry parseTypeEntry(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return IncidentTypeCatalog.get(IncidentType.valueOf(raw.trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String severityLabel(IncidentSeverity severity) {
        if (severity == null) return "Không xác định";
        switch (severity) {
            case CRITICAL: return "Nghiêm trọng";
            case HIGH: return "Cao";
            case MEDIUM: return "Trung bình";
            case LOW: return "Thấp";
            default: return severity.name();
        }
    }

    private static Color severityColor(IncidentSeverity severity) {
        if (severity == null) return AppColor.TEXT_MUTED;
        switch (severity) {
            case CRITICAL: return AppColor.ERROR;
            case HIGH: return AppColor.WARNING;
            case MEDIUM: return AppColor.INFO;
            default: return AppColor.TEXT_MUTED;
        }
    }

    private static Color soft(Color base, int alpha) {
        return new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha);
    }

    /**
     * Đọc 1 field dạng chuỗi từ dòng JSON phẳng do FileIncidentSink ghi ra,
     * có giải mã escape (\n, \t, \r, \", \\, \\uXXXX) để hiển thị đúng định
     * dạng nhiều dòng (đặc biệt cần cho stackTrace).
     */
    private static String extractJsonField(String jsonLine, String field) {
        if (jsonLine == null || field == null) return "";

        String key = "\"" + field + "\":\"";
        int start = jsonLine.indexOf(key);
        if (start < 0) return "";
        start += key.length();

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < jsonLine.length(); i++) {
            char c = jsonLine.charAt(i);

            if (c == '\\' && i + 1 < jsonLine.length()) {
                char next = jsonLine.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    case 'u':
                        if (i + 5 < jsonLine.length()) {
                            String hex = jsonLine.substring(i + 2, i + 6);
                            try {
                                sb.append((char) Integer.parseInt(hex, 16));
                                i += 4;
                            } catch (NumberFormatException ex) {
                                sb.append(next);
                            }
                        } else {
                            sb.append(next);
                        }
                        break;
                    default:
                        sb.append(next);
                }
                i++;
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
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

    static void show(Window owner, String rawJsonLine) {
        new IncidentDetailDialog(owner, rawJsonLine).setVisible(true);
    }
}