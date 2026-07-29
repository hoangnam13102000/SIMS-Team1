package com.view.admin.employee;

import com.components.StatBadge;
import com.model.Employee;
import com.model.Role;
import com.theme.AppColor;
import com.theme.AppFont;
import com.utils.ImageUtil;
import com.utils.NumberUtil;

import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


public class EmployeeDetailDialog extends JDialog {

    private static final int AVATAR_SIZE = 140;
    private static final int ICON_BOX_SIZE = 40;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public EmployeeDetailDialog(Frame owner, Employee employee) {
        super(owner, "Chi tiết nhân viên", true);
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColor.WHITE);
        setResizable(false);

        add(buildHeader(), BorderLayout.NORTH);
        add(buildBody(employee), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        setSize(720, 480);
        setLocationRelativeTo(owner);
    }

    // ---------------------------------------------------------------
    // Header: chỉ tiêu đề (không nút X — footer đã có "Đóng")
    // ---------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColor.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppColor.BORDER),
                new EmptyBorder(18, 24, 18, 24)));

        JLabel title = new JLabel("Chi tiết nhân viên");
        title.setFont(AppFont.DIALOG_TITLE);
        title.setForeground(AppColor.TEXT_PRIMARY);
        header.add(title, BorderLayout.CENTER);
        return header;
    }

    // ---------------------------------------------------------------
    // Body (layout ngang): avatar trái | tên + badge + lưới info phải
    // ---------------------------------------------------------------

    private JComponent buildBody(Employee employee) {
        JPanel root = new JPanel(new BorderLayout(24, 0));
        root.setBackground(AppColor.WHITE);
        root.setBorder(new EmptyBorder(22, 24, 12, 24));

        root.add(buildAvatarColumn(employee), BorderLayout.WEST);
        root.add(buildInfoColumn(employee), BorderLayout.CENTER);

        JScrollPane scroll = new JScrollPane(root);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getViewport().setBackground(AppColor.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    /** Cột trái: avatar lớn + tên + username. */
    private JComponent buildAvatarColumn(Employee employee) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setPreferredSize(new Dimension(AVATAR_SIZE + 8, 0));

        String name = displayName(employee);
        ImageIcon avatarIcon = ImageUtil.circularIcon(employee.getAvatarUrl(), AVATAR_SIZE, name);

        JLabel avatarLabel = new JLabel(avatarIcon);
        avatarLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatarLabel);
        col.add(Box.createVerticalStrut(14));

        JLabel nameLabel = new JLabel(name);
        nameLabel.setFont(AppFont.HEADING_MD);
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(nameLabel);
        col.add(Box.createVerticalStrut(4));

        JLabel usernameLabel = new JLabel("@" + employee.getUsername());
        usernameLabel.setFont(AppFont.BODY);
        usernameLabel.setForeground(AppColor.TEXT_MUTED);
        usernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(usernameLabel);

        return col;
    }

    /** Cột phải: badge + lưới thông tin 2 cột. */
    private JComponent buildInfoColumn(Employee employee) {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setBorder(new EmptyBorder(0, 4, 0, 0));

        // Badges
        JPanel badgeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badgeRow.setOpaque(false);
        badgeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        badgeRow.add(new StatBadge(roleLabel(employee.getRole()), AppColor.ACCENT));
        badgeRow.add(employee.isDisabled()
                ? new StatBadge("Vô hiệu hóa", AppColor.ERROR)
                : new StatBadge("Đang hoạt động", AppColor.SUCCESS));
        if (employee.isLocked()) {
            badgeRow.add(new StatBadge("Đang khóa", AppColor.WARNING));
        }
        col.add(badgeRow);
        col.add(Box.createVerticalStrut(16));
        col.add(buildDivider());
        col.add(Box.createVerticalStrut(16));
        col.add(buildInfoGrid(employee));

        return col;
    }

    /** Lưới 2 cột: mã NV / email / SĐT / ngày sinh / giới tính / lương / ngày vào làm. */
    private JComponent buildInfoGrid(Employee employee) {
        JPanel grid = new JPanel(new GridLayout(0, 2, 16, 14));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 280));

        grid.add(infoRow(FontAwesomeSolid.ID_CARD, "Mã nhân viên", emptyDash(employee.getEmployeeId())));
        grid.add(infoRow(FontAwesomeSolid.ENVELOPE, "Email", emptyDash(employee.getEmail())));
        grid.add(infoRow(FontAwesomeSolid.PHONE_ALT, "Số điện thoại", emptyDash(employee.getPhone())));
        grid.add(infoRow(FontAwesomeSolid.CALENDAR_ALT, "Ngày sinh", formatDate(employee.getDateOfBirth())));
        grid.add(infoRow(FontAwesomeSolid.USERS, "Giới tính", genderLabel(employee.getGender())));
        grid.add(infoRow(FontAwesomeSolid.MONEY_BILL_WAVE, "Lương", salaryLabel(employee)));
        grid.add(infoRow(FontAwesomeSolid.CALENDAR_CHECK, "Ngày vào làm", formatDate(employee.getHireDate())));
        // Ô trống để lưới chẵn nếu cần
        grid.add(Box.createGlue());

        return grid;
    }

    private String displayName(Employee employee) {
        return employee.getFullName() != null && !employee.getFullName().isBlank()
                ? employee.getFullName() : employee.getUsername();
    }

    private JPanel infoRow(FontAwesomeSolid iconType, String label, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
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

    // ---------------------------------------------------------------
    // Helper hiển thị
    // ---------------------------------------------------------------

    private String emptyDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String formatDate(LocalDate date) {
        return date != null ? date.format(DATE_FMT) : "-";
    }

    private String genderLabel(Employee.Gender gender) {
        if (gender == null) return "-";
        switch (gender) {
            case MALE: return "Nam";
            case FEMALE: return "Nữ";
            default: return "Khác";
        }
    }

    private String salaryLabel(Employee employee) {
        return employee.getSalary() != null
                ? NumberUtil.formatThousands(employee.getSalary().longValue()) + " đ"
                : "-";
    }

    private static String roleLabel(Role role) {
        if (role == null) return "-";
        switch (role) {
            case ADMIN: return "Quản trị viên";
            case SALES_MANAGER: return "Quản lý bán hàng";
            case INVENTORY_MANAGER: return "Quản lý kho";
            case SALES_STAFF: return "Nhân viên bán hàng";
            case CUSTOMER: return "Khách hàng";
            default: return role.name();
        }
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
