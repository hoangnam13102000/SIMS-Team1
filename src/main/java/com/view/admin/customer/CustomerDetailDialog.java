package com.view.admin.customer;

import com.components.StatBadge;
import com.model.Customer;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.DateUtil;
import com.utils.ImageUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Dialog xem nhanh (chỉ đọc) thông tin 1 khách hàng, mở từ nút "Xem chi tiết"
 * (icon mắt) trong {@link CustomerPanel}. Khác {@link CustomerFormDialog}:
 * dialog này KHÔNG có field chỉnh sửa - chỉ trình bày lại dữ liệu đã có theo
 * bố cục thẻ thông tin (avatar + badge + các dòng icon/label/giá trị), dùng
 * khi người dùng chỉ muốn xem nhanh mà không cần mở form sửa.
 * <p>
 * "Tổng đơn hàng" / "Tổng chi tiêu": ứng dụng hiện CHƯA có module Đơn hàng
 * (không có bảng/DAO Orders), nên 2 chỉ số này tạm hiển thị "0" làm chỗ trống
 * placeholder - khi nào có OrderDAO thật, chỉ cần thay 2 dòng gọi infoRow()
 * bên dưới bằng số liệu thật thay vì sửa lại UI dialog.
 */
public class CustomerDetailDialog extends JDialog {

    private static final int AVATAR_SIZE = 56;
    private static final int ICON_BOX_SIZE = 40;

    public CustomerDetailDialog(Frame owner, Customer customer) {
        super(owner, "Chi tiết khách hàng", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        setResizable(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(customer), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(460, 620);
        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------
    // Header: tiêu đề + nút đóng (X)
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 20)));

        JLabel title = new JLabel("Chi tiết khách hàng");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        header.add(title, BorderLayout.CENTER);
        header.add(buildCloseButton(), BorderLayout.EAST);
        return header;
    }

    private JComponent buildCloseButton() {
        FontIcon icon = FontIcon.of(FontAwesomeSolid.TIMES, 16);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel close = new JLabel(icon);
        close.setCursor(new Cursor(Cursor.HAND_CURSOR));
        close.setBorder(new EmptyBorder(4, 4, 4, 4));
        close.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { dispose(); }
            @Override public void mouseEntered(MouseEvent e) { icon.setIconColor(AppColor.TEXT_PRIMARY); close.repaint(); }
            @Override public void mouseExited(MouseEvent e) { icon.setIconColor(AppColor.TEXT_MUTED); close.repaint(); }
        });
        return close;
    }

    // ---------------------------------------------------------------
    // Body: avatar + tên/username + badge + các dòng thông tin
    // ---------------------------------------------------------------

    private JComponent buildBody(Customer customer) {
        JPanel body = new JPanel();
        body.setBackground(AppColor.WHITE);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(new EmptyBorder(22, 24, 8, 24));

        body.add(buildIdentitySection(customer));
        body.add(Box.createVerticalStrut(18));
        body.add(buildDivider());
        body.add(Box.createVerticalStrut(18));

        body.add(infoRow(FontAwesomeSolid.ENVELOPE, "Email", emptyDash(customer.getEmail())));
        body.add(Box.createVerticalStrut(16));
        body.add(infoRow(FontAwesomeSolid.PHONE_ALT, "Số điện thoại", emptyDash(customer.getPhone())));
        body.add(Box.createVerticalStrut(16));
        body.add(infoRow(FontAwesomeSolid.SHOPPING_BAG, "Tổng đơn hàng", "0 đơn"));
        body.add(Box.createVerticalStrut(16));
        body.add(infoRow(FontAwesomeSolid.DOLLAR_SIGN, "Tổng chi tiêu", "0 đ"));
        body.add(Box.createVerticalStrut(16));
        body.add(infoRow(FontAwesomeSolid.CALENDAR_ALT, "Ngày tham gia",
                customer.getCreatedAt() != null ? DateUtil.formatDate(customer.getCreatedAt()) : "-"));

        JScrollPane scroll = new JScrollPane(body);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildIdentitySection(Customer customer) {
        JPanel section = new JPanel(new BorderLayout(16, 0));
        section.setOpaque(false);
        section.setAlignmentX(Component.LEFT_ALIGNMENT);
        // KHONG gioi han chieu cao = AVATAR_SIZE (56px): khoi text ben phai co
        // 3 dong (ten + username + hang badge) can nhieu hon 56px, neu ep max
        // height = 56 thi BoxLayout (Y_AXIS) se CAT phan duoi (StatBadge bi mat
        // 1 phan) vi BorderLayout khong tu cuon. De rong (Integer.MAX_VALUE) de
        // JPanel tu lay chieu cao theo preferred size that su cua noi dung.
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        String name = customer.getFullName() != null && !customer.getFullName().isBlank()
                ? customer.getFullName() : customer.getUsername();
        ImageIcon avatarIcon = ImageUtil.circularIcon(customer.getAvatarUrl(), AVATAR_SIZE, name);
        section.add(new JLabel(avatarIcon), BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(AppFont.HEADING_MD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(nameLabel);

        JLabel usernameLabel = new JLabel("@" + customer.getUsername());
        usernameLabel.setFont(AppFont.BODY);
        usernameLabel.setForeground(AppColor.TEXT_MUTED);
        usernameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(usernameLabel);

        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.setBorder(new EmptyBorder(6, 0, 0, 0));
        badgeRow.add(new StatBadge("Khách hàng", AppColor.ACCENT));
        badgeRow.add(customer.isDisabled()
                ? new StatBadge("Vô hiệu hóa", AppColor.ERROR)
                : new StatBadge("Hoạt động", AppColor.SUCCESS));
        textPanel.add(badgeRow);

        section.add(textPanel, BorderLayout.CENTER);
        return section;
    }

    /** 1 dòng thông tin: icon vuông bo góc bên trái, nhãn nhỏ + giá trị đậm bên phải (giống hình mẫu). */
    private JPanel infoRow(FontAwesomeSolid iconType, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ICON_BOX_SIZE));

        row.add(iconBox(iconType), BorderLayout.WEST);

        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(AppFont.SMALL);
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(labelLabel);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(AppFont.BODY_BOLD.deriveFont(14f));
        valueLabel.setForeground(AppColor.TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        textPanel.add(Box.createVerticalStrut(2));
        textPanel.add(valueLabel);

        row.add(textPanel, BorderLayout.CENTER);
        return row;
    }

    /** O vuong bo goc nen nhat, chua icon - dung chung cho tat ca dong thong tin. */
    private JComponent iconBox(FontAwesomeSolid iconType) {
        JPanel box = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        box.setOpaque(false);
        box.setPreferredSize(new Dimension(ICON_BOX_SIZE, ICON_BOX_SIZE));
        FontIcon icon = FontIcon.of(iconType, 16);
        icon.setIconColor(AppColor.TEXT_MUTED);
        box.add(new JLabel(icon));
        return box;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    // ---------------------------------------------------------------
    // Footer: nút Đóng
    // ---------------------------------------------------------------

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        footer.setBackground(AppColor.BG_LIGHT);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColor.BORDER),
                new EmptyBorder(12, 24, 12, 24)));

        JButton closeButton = new JButton("Đóng");
        closeButton.setFont(AppFont.BODY_BOLD);
        closeButton.setFocusPainted(false);
        closeButton.setBackground(AppColor.CANCEL_BG);
        closeButton.setForeground(AppColor.TEXT_PRIMARY);
        closeButton.setBorder(new EmptyBorder(9, 20, 9, 20));
        closeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        closeButton.addActionListener(e -> dispose());
        footer.add(closeButton);

        getRootPane().setDefaultButton(closeButton);
        return footer;
    }
}