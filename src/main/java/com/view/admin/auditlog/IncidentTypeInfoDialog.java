package com.view.admin.auditlog;

import com.incident.IncidentSeverity;
import com.theme.AppColor;
import com.theme.AppFont;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Popup hiển thị danh sách các loại sự cố mà hệ thống SIMS có thể tự động
 * phát hiện và ghi lại vào nhật ký sự cố (xem {@link IncidentTypeCatalog}).
 *
 * Mở từ nút "Loại sự cố hệ thống" trên trang Nhật ký sự cố (AuditLogPanel),
 * tab "Nhật ký sự cố".
 */
public final class IncidentTypeInfoDialog extends JDialog {

    private static final int WIDTH = 620;
    private static final int HEIGHT = 640;

    public IncidentTypeInfoDialog(Window owner) {
        super(owner, "Các loại sự cố hệ thống", ModalityType.APPLICATION_MODAL);

        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        setResizable(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildList(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(WIDTH, HEIGHT);
        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------------
    // HEADER
    // ---------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(20, 24, 16, 24)));

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        FontIcon icon = FontIcon.of(FontAwesomeSolid.EXCLAMATION_TRIANGLE, 24);
        icon.setIconColor(AppColor.ACCENT);
        titleRow.add(new JLabel(icon));

        JLabel title = new JLabel("Các loại sự cố hệ thống có thể ghi nhận");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        titleRow.add(title);

        header.add(titleRow);
        header.add(Box.createVerticalStrut(8));

        JLabel subtitle = new JLabel("<html><div style='width:540px'>"
                + "SIMS tự động theo dõi và ghi lại các sự cố dưới đây vào nhật ký sự cố, giúp "
                + "quản trị viên nắm được tình trạng hệ thống ngay cả khi cơ sở dữ liệu gặp trục trặc."
                + "</div></html>");
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(subtitle);

        return header;
    }

    // ---------------------------------------------------------------------
    // DANH SACH LOAI SU CO
    // ---------------------------------------------------------------------

    /**
     * Thu tu hien thi nhom muc do: tu nghiem trong nhat xuong thap nhat,
     * de nguoi xem/giao vien thay ngay nhung su co quan trong nhat truoc tien.
     * Luu y thu tu nay NGUOC voi thu tu khai bao trong enum IncidentSeverity
     * (LOW, MEDIUM, HIGH, CRITICAL) nen phai liet ke tuong minh o day.
     */
    private static final IncidentSeverity[] SEVERITY_DISPLAY_ORDER = {
            IncidentSeverity.CRITICAL,
            IncidentSeverity.HIGH,
            IncidentSeverity.MEDIUM,
            IncidentSeverity.LOW
    };

    private JScrollPane buildList() {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBackground(AppColor.BG_LIGHT);
        list.setBorder(new EmptyBorder(14, 16, 14, 16));

        boolean firstSection = true;
        for (IncidentSeverity severity : SEVERITY_DISPLAY_ORDER) {
            List<IncidentTypeCatalog.Entry> group = new ArrayList<>();
            for (IncidentTypeCatalog.Entry entry : IncidentTypeCatalog.all()) {
                if (entry.typicalSeverity == severity) {
                    group.add(entry);
                }
            }
            if (group.isEmpty()) {
                continue;
            }

            if (!firstSection) {
                list.add(Box.createVerticalStrut(20));
            }
            firstSection = false;

            list.add(buildSeverityHeader(severity, group.size()));
            list.add(Box.createVerticalStrut(8));

            boolean firstCard = true;
            for (IncidentTypeCatalog.Entry entry : group) {
                if (!firstCard) {
                    list.add(Box.createVerticalStrut(10));
                }
                firstCard = false;
                list.add(buildEntryCard(entry));
            }
        }

        JScrollPane scroll = new JScrollPane(list,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(AppColor.BG_LIGHT);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Tieu de moi nhom muc do, vi du: "● NGHIÊM TRỌNG (4)". */
    private JPanel buildSeverityHeader(IncidentSeverity severity, int count) {
        Color color = severityColor(severity);

        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel dot = new JLabel("\u25CF");
        dot.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        dot.setForeground(color);
        header.add(dot);

        JLabel label = new JLabel(severityLabel(severity).toUpperCase());
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(color);
        header.add(label);

        JLabel countLabel = new JLabel("(" + count + ")");
        countLabel.setFont(AppFont.SMALL);
        countLabel.setForeground(AppColor.TEXT_MUTED);
        header.add(countLabel);

        return header;
    }

    private JPanel buildEntryCard(IncidentTypeCatalog.Entry entry) {
        Color severityColor = severityColor(entry.typicalSeverity);

        JPanel card = new JPanel(new BorderLayout(14, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 4, 0, 0, severityColor),
                        BorderFactory.createLineBorder(AppColor.BORDER, 1, true)),
                new EmptyBorder(14, 12, 14, 16)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        // ====== Icon minh hoa ben trai ======
        JLabel iconLabel = new JLabel(FontIcon.of(entry.icon, 16));
        ((FontIcon) iconLabel.getIcon()).setIconColor(severityColor);
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setOpaque(true);
        iconLabel.setBackground(new Color(severityColor.getRed(), severityColor.getGreen(), severityColor.getBlue(), 24));
        Dimension iconBox = new Dimension(38, 38);
        iconLabel.setPreferredSize(iconBox);
        iconLabel.setMinimumSize(iconBox);
        iconLabel.setMaximumSize(iconBox);
        JPanel iconWrapper = new JPanel(new BorderLayout());
        iconWrapper.setOpaque(false);
        iconWrapper.setBorder(new EmptyBorder(2, 0, 0, 0));
        iconWrapper.add(iconLabel, BorderLayout.NORTH);
        card.add(iconWrapper, BorderLayout.WEST);

        // ====== Noi dung ======
        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        JPanel titleLine = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleLine.setOpaque(false);
        titleLine.setAlignmentX(Component.LEFT_ALIGNMENT);
        titleLine.setBorder(new EmptyBorder(0, 0, 0, 0));

        JLabel nameLabel = new JLabel(entry.label);
        nameLabel.setFont(AppFont.BODY_BOLD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLine.add(nameLabel);
        titleLine.add(new com.components.StatBadge(severityLabel(entry.typicalSeverity), severityColor));
        content.add(titleLine);

        JLabel codeLabel = new JLabel(entry.type.name());
        codeLabel.setFont(new Font("Consolas", Font.PLAIN, 11));
        codeLabel.setForeground(AppColor.TEXT_MUTED);
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeLabel.setBorder(new EmptyBorder(2, 2, 6, 0));
        content.add(codeLabel);

        JLabel desc = new JLabel("<html><div style='width:400px'>" + entry.description + "</div></html>");
        desc.setFont(AppFont.SMALL);
        desc.setForeground(AppColor.TEXT_SECONDARY);
        desc.setAlignmentX(Component.LEFT_ALIGNMENT);
        desc.setBorder(new EmptyBorder(0, 2, 4, 0));
        content.add(desc);

        JLabel raisedBy = new JLabel("Nguồn ghi nhận: " + entry.raisedBy);
        raisedBy.setFont(AppFont.SMALL);
        raisedBy.setForeground(AppColor.TEXT_MUTED);
        raisedBy.setAlignmentX(Component.LEFT_ALIGNMENT);
        raisedBy.setBorder(new EmptyBorder(0, 2, 0, 0));
        content.add(raisedBy);

        card.add(content, BorderLayout.CENTER);

        // Chi gioi han chieu cao SAU KHI da them du icon + noi dung vao card,
        // neu khong preferred size se duoc tinh luc card con rong (=0) va
        // BoxLayout se bop toan bo noi dung ben trong lai gan nhu vo hinh.
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height));

        return card;
    }

    // ---------------------------------------------------------------------
    // FOOTER
    // ---------------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AppColor.WHITE);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(8, 16, 12, 16)));

        JButton closeButton = new JButton("Đóng") {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        closeButton.setFont(AppFont.BODY_BOLD);
        closeButton.setForeground(Color.WHITE);
        closeButton.setBackground(AppColor.ACCENT);
        closeButton.setBorder(new EmptyBorder(10, 24, 10, 24));
        closeButton.setFocusPainted(false);
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.setOpaque(false);
        closeButton.setContentAreaFilled(false);
        closeButton.addActionListener(e -> dispose());
        closeButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                closeButton.setBackground(AppColor.ACCENT_HOVER);
                closeButton.repaint();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                closeButton.setBackground(AppColor.ACCENT);
                closeButton.repaint();
            }
        });

        footer.add(closeButton);
        getRootPane().setDefaultButton(closeButton);
        return footer;
    }

    // ---------------------------------------------------------------------
    // HELPERS
    // ---------------------------------------------------------------------

    private static String severityLabel(IncidentSeverity severity) {
        if (severity == null) return "";
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
}