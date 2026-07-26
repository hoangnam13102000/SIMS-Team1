package com.components;

import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;
import java.awt.event.ActionListener;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Trạng thái "chưa có dữ liệu" dùng chung toàn app - hiển thị icon tròn mờ,
 * tiêu đề, mô tả phụ, và (tuỳ chọn) 1 nút hành động.
 *
 * KHÔNG chứa logic hay chữ nghĩa riêng của bất kỳ domain nào (đơn hàng,
 * điện thoại...) - mọi nội dung đều truyền vào từ bên ngoài, nên copy y
 * nguyên sang app khác (HR, ngân hàng...) là dùng được ngay.
 */
public class EmptyState extends JPanel {

    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;
    private final JButton actionButton;

    private FontAwesomeSolid iconType;

    public EmptyState(FontAwesomeSolid icon, String title, String subtitle) {
        this.iconType = icon;

        setOpaque(false);
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL));

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        iconLabel = buildIconCircle(icon);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        titleLabel = new JLabel(title);
        titleLabel.setFont(AppFont.HEADING_MD);
        titleLabel.setForeground(AppColor.TEXT_PRIMARY);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        subtitleLabel = new JLabel(subtitle);
        subtitleLabel.setFont(AppFont.BODY);
        subtitleLabel.setForeground(AppColor.TEXT_MUTED);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        actionButton = new JButton();
        actionButton.setFont(AppFont.BUTTON);
        actionButton.setForeground(Color.WHITE);
        actionButton.setBackground(AppColor.ACCENT);
        actionButton.setFocusPainted(false);
        actionButton.setBorder(new EmptyBorder(9, 18, 9, 18));
        actionButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        actionButton.setOpaque(true);
        actionButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        actionButton.setVisible(false);
        actionButton.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { actionButton.setBackground(AppColor.ACCENT_HOVER); }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { actionButton.setBackground(AppColor.ACCENT); }
        });

        content.add(iconLabel);
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(titleLabel);
        content.add(Box.createVerticalStrut(AppSpacing.XS));
        content.add(subtitleLabel);
        content.add(Box.createVerticalStrut(AppSpacing.LG));
        content.add(actionButton);

        add(content);
    }

    private JLabel buildIconCircle(FontAwesomeSolid icon) {
        int size = 64;
        FontIcon fontIcon = FontIcon.of(icon, 26);
        fontIcon.setIconColor(AppColor.ACCENT);

        JLabel circle = new JLabel(fontIcon) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_SOFT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        circle.setPreferredSize(new Dimension(size, size));
        circle.setMaximumSize(new Dimension(size, size));
        circle.setHorizontalAlignment(SwingConstants.CENTER);
        circle.setVerticalAlignment(SwingConstants.CENTER);
        circle.setAlignmentX(Component.CENTER_ALIGNMENT);
        return circle;
    }

    public EmptyState setTitle(String title) {
        titleLabel.setText(title);
        return this;
    }

    public EmptyState setSubtitle(String subtitle) {
        subtitleLabel.setText(subtitle);
        return this;
    }

    /** Gắn nút hành động (vd: "Thêm điện thoại"). Gọi lại với label=null để ẩn nút. */
    public EmptyState setAction(String label, Runnable onClick) {
        if (label == null) {
            actionButton.setVisible(false);
            return this;
        }
        actionButton.setText(label);
        actionButton.setVisible(true);
        for (ActionListener l : actionButton.getActionListeners()) {
            actionButton.removeActionListener(l);
        }
        actionButton.addActionListener(e -> { if (onClick != null) onClick.run(); });
        return this;
    }

    public static EmptyState noData(String entityName) {
        return new EmptyState(FontAwesomeSolid.INBOX,
                "Chưa có " + entityName + " nào",
                "Dữ liệu sẽ xuất hiện tại đây khi có " + entityName + " mới");
    }

    public static EmptyState noSearchResult(String keyword) {
        return new EmptyState(FontAwesomeSolid.SEARCH,
                "Không tìm thấy kết quả",
                "Không có dữ liệu nào khớp với \"" + keyword + "\"");
    }

    public static EmptyState error(String message) {
        EmptyState state = new EmptyState(FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                "Không thể tải dữ liệu", message);
        state.iconLabel.setIcon(recolor(state.iconType, AppColor.ERROR));
        return state;
    }

    private static Icon recolor(FontAwesomeSolid icon, Color color) {
        FontIcon fontIcon = FontIcon.of(icon, 26);
        fontIcon.setIconColor(color);
        return fontIcon;
    }
}