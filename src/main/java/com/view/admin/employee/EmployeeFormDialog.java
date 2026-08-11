package com.view.admin.employee;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.EmployeeDAO;
import com.model.Employee;
import com.model.Role;
import com.theme.AppColor;
import com.utils.CurrencyDocumentFilter;
import com.utils.FileUtil;
import com.utils.ImageUtil;
import com.validation.FormValidator;
import com.validation.Rules;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Dialog Them/Sua nhan vien - da thiet ke lai: chia thanh 2 nhom "Thong tin
 * ca nhan" / "Thong tin cong viec" voi tieu de nhom + icon cho tung nhan,
 * dung DatePickerField cho Ngay vao lam (thay vi go tay dd/MM/yyyy), the
 * hien Ma NV/Ten dang nhap (khi Sua) nhu 1 the thong tin chi doc, va banner
 * huong dan noi bat hon o cuoi form khi Them moi.
 */
public class EmployeeFormDialog extends BaseFormDialog<Employee> {

    /** Chỉ 4 vai trò nghiệp vụ - KHÔNG bao gồm Role.CUSTOMER (khách hàng không phải nhân viên). */
    private static final Role[] EMP_ROLES = {
            Role.ADMIN, Role.SALES_MANAGER, Role.INVENTORY_MANAGER, Role.SALES_STAFF
    };
    private static final String[] EMP_ROLE_LABELS = {
            "Quản trị viên", "Quản lý bán hàng", "Quản lý kho", "Nhân viên bán hàng"
    };
    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private static final Employee.Gender[] GENDERS = {Employee.Gender.MALE, Employee.Gender.FEMALE, Employee.Gender.OTHER};
    private static final String[] GENDER_LABELS = {"Nam", "Nữ", "Khác"};
    private static final String UPLOAD_DIR = "uploads/avatars";
    private static final int AVATAR_PREVIEW = 140;

    private final EmployeeDAO employeeDAO;

    private JTextField employeeIdField; // chi doc, chi hien thi khi Sua
    private JTextField usernameField;   // chi doc, chi hien thi khi Sua
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private DatePickerField dobPicker;
    private JTextField salaryField;
    private DatePickerField hireDatePicker;
    private JComboBox<String> genderCombo;
    private JComboBox<String> roleCombo;
    private JComboBox<String> statusCombo;

    private JLabel avatarPreviewLabel;
    private JLabel avatarHintLabel;
    private File pendingAvatarFile;
    private String currentAvatarUrl;

    public EmployeeFormDialog(Frame owner, CrudMode mode, Employee editingEntity, EmployeeDAO employeeDAO) {
        super(owner, "nhân viên", mode, editingEntity);
        this.employeeDAO = employeeDAO;
        this.currentAvatarUrl = editingEntity != null ? editingEntity.getAvatarUrl() : null;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 860;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 640 : 660;
    }

    /**
     * Layout ngang:
     *  - (EDIT) thẻ Mã NV / Username full-width phía trên
     *  - 3 cột: Avatar | Thông tin cá nhân | Thông tin công việc
     *  - (ADD) banner hướng dẫn phía dưới
     */
    @Override
    protected void buildFields(JPanel panel) {
        if (mode == CrudMode.EDIT) {
            employeeIdField = newTextField();
            employeeIdField.setEnabled(false);
            usernameField = newTextField();
            usernameField.setEnabled(false);
            panel.add(buildIdentityCard());
            panel.add(Box.createVerticalStrut(16));
        }

        JPanel columns = new JPanel();
        columns.setOpaque(false);
        columns.setLayout(new BoxLayout(columns, BoxLayout.X_AXIS));
        columns.setAlignmentX(Component.LEFT_ALIGNMENT);
        columns.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));

        columns.add(buildAvatarColumn());
        columns.add(Box.createHorizontalStrut(20));
        columns.add(buildPersonalColumn());
        columns.add(Box.createHorizontalStrut(20));
        columns.add(buildWorkColumn());
        panel.add(columns);

        if (mode == CrudMode.ADD) {
            panel.add(Box.createVerticalStrut(14));
            panel.add(infoBanner(
                    "Mã nhân viên, tên đăng nhập và mật khẩu đăng nhập sẽ được hệ thống tự động "
                    + "tạo và gửi tới email nhân viên ở trên sau khi lưu."));
        }
    }

    /** Cột avatar: preview tròn + nút chọn ảnh. */
    private JPanel buildAvatarColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);

        JLabel caption = iconFieldLabel(FontAwesomeSolid.USER_CIRCLE, "Ảnh đại diện", false);
        caption.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(caption);
        col.add(Box.createVerticalStrut(8));

        avatarPreviewLabel = new JLabel();
        avatarPreviewLabel.setHorizontalAlignment(JLabel.CENTER);
        avatarPreviewLabel.setPreferredSize(new Dimension(AVATAR_PREVIEW, AVATAR_PREVIEW));
        avatarPreviewLabel.setMaximumSize(new Dimension(AVATAR_PREVIEW, AVATAR_PREVIEW));
        avatarPreviewLabel.setMinimumSize(new Dimension(AVATAR_PREVIEW, AVATAR_PREVIEW));
        avatarPreviewLabel.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        avatarPreviewLabel.setIcon(ImageUtil.circularIcon(null, AVATAR_PREVIEW, "?"));
        avatarPreviewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatarPreviewLabel);
        col.add(Box.createVerticalStrut(12));

        JButton chooseButton = new JButton("Chọn ảnh", FontIcon.of(FontAwesomeSolid.IMAGE, 13, AppColor.ACCENT));
        chooseButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        chooseButton.setFocusPainted(false);
        chooseButton.setBackground(AppColor.WHITE);
        chooseButton.setForeground(AppColor.ACCENT);
        chooseButton.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(8, 14, 8, 14)));
        chooseButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        chooseButton.addActionListener(e -> chooseAvatar());
        col.add(chooseButton);
        col.add(Box.createVerticalStrut(8));

        avatarHintLabel = new JLabel("Tùy chọn · tối đa 5MB");
        avatarHintLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        avatarHintLabel.setForeground(AppColor.TEXT_MUTED);
        avatarHintLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatarHintLabel);

        return col;
    }

    private void chooseAvatar() {
        File selected = FileUtil.chooseImageFile(this);
        if (selected == null) return;

        if (!FileUtil.isWithinSizeLimit(selected, 5)) {
            showMessage("Ảnh vượt quá 5MB, vui lòng chọn ảnh khác.");
            return;
        }
        if (!ImageUtil.isSupportedImage(selected)) {
            showMessage("Định dạng ảnh không được hỗ trợ.");
            return;
        }

        pendingAvatarFile = selected;
        String displayName = fullNameField != null && fullNameField.getText() != null
                && !fullNameField.getText().isBlank()
                ? fullNameField.getText().trim() : selected.getName();
        avatarPreviewLabel.setIcon(ImageUtil.circularIcon(selected.getPath(), AVATAR_PREVIEW, displayName));
        avatarHintLabel.setText(selected.getName());
        showMessage(null);
    }

    /** Cột trái — Thông tin cá nhân. */
    private JPanel buildPersonalColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.ID_CARD, "Thông tin cá nhân");

        fullNameField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.USER, "Họ và tên", true, fullNameField));
        col.add(Box.createVerticalStrut(12));

        emailField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.ENVELOPE, "Email", true, emailField));
        col.add(Box.createVerticalStrut(12));

        phoneField = newTextField();
        col.add(fieldGroupIcon(FontAwesomeSolid.PHONE_ALT, "Số điện thoại", false, phoneField));
        col.add(Box.createVerticalStrut(12));

        dobPicker = new DatePickerField(null, true);
        dobPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        dobPicker.setPreferredSize(new Dimension(160, 36));
        col.add(fieldGroupIcon(FontAwesomeSolid.CALENDAR_ALT, "Ngày sinh", false, dobPicker));
        col.add(Box.createVerticalStrut(12));

        genderCombo = newComboBox(GENDER_LABELS);
        col.add(fieldGroupIcon(FontAwesomeSolid.USERS, "Giới tính", false, genderCombo));

        return col;
    }

    /** Cột phải — Thông tin công việc. */
    private JPanel buildWorkColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.CLIPBOARD_LIST, "Thông tin công việc");

        roleCombo = newComboBox(EMP_ROLE_LABELS);
        col.add(fieldGroupIcon(FontAwesomeSolid.USER_TIE, "Vai trò", true, roleCombo));
        col.add(Box.createVerticalStrut(12));

        hireDatePicker = new DatePickerField(LocalDate.now());
        hireDatePicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        hireDatePicker.setPreferredSize(new Dimension(160, 36));
        col.add(fieldGroupIcon(FontAwesomeSolid.CALENDAR_ALT, "Ngày vào làm", true, hireDatePicker));
        col.add(Box.createVerticalStrut(12));

        salaryField = newTextField();
        CurrencyDocumentFilter.install(salaryField);
        col.add(fieldGroupIcon(FontAwesomeSolid.MONEY_BILL_WAVE, "Lương", false, wrapWithSuffix(salaryField, "VNĐ")));
        col.add(Box.createVerticalStrut(12));

        if (mode == CrudMode.EDIT) {
            statusCombo = newComboBox(STATUS_LABELS);
            col.add(fieldGroupIcon(FontAwesomeSolid.CHECK_CIRCLE, "Trạng thái", true, statusCombo));
        }

        return col;
    }

    private <E> JComboBox<E> newComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    // ---------------------------------------------------------------
    // Helper giao diện riêng cho dialog này (nhãn có icon, nhóm nhãn,
    // tiêu đề nhóm, thẻ thông tin chỉ đọc, banner hướng dẫn...)
    // ---------------------------------------------------------------

    /** Tiêu đề 1 nhóm field: icon + chữ in hoa màu nhấn + đường kẻ ngăn cách kéo dài hết hàng. */
    private void addSectionHeader(JPanel panel, FontAwesomeSolid iconType, String text) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        FontIcon icon = FontIcon.of(iconType, 13);
        icon.setIconColor(AppColor.ACCENT);
        JLabel iconLabel = new JLabel(icon);

        JLabel textLabel = new JLabel(text.toUpperCase());
        textLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        textLabel.setForeground(AppColor.ACCENT);
        textLabel.setBorder(new EmptyBorder(0, 8, 0, 10));

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(AppColor.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentY(Component.CENTER_ALIGNMENT);

        row.add(iconLabel);
        row.add(textLabel);
        row.add(sep);

        panel.add(row);
        panel.add(Box.createVerticalStrut(12));
    }

    /** Nhãn field có icon nhỏ phía trước (giữ đúng phong cách fieldLabel của BaseFormDialog, chỉ thêm icon). */
    private JLabel iconFieldLabel(FontAwesomeSolid iconType, String text, boolean required) {
        FontIcon icon = FontIcon.of(iconType, 12);
        icon.setIconColor(AppColor.TEXT_MUTED_ALT);

        String html = "<html>" + text
                + (required ? " <font color='" + toHex(AppColor.ERROR) + "'>*</font>" : "")
                + "</html>";
        JLabel label = new JLabel(html, icon, SwingConstants.LEFT);
        label.setIconTextGap(6);
        label.setFont(new Font("Segoe UI", Font.BOLD, 12));
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    /** Giống fieldGroup() của lớp cha nhưng dùng nhãn có icon. */
    private JPanel fieldGroupIcon(FontAwesomeSolid iconType, String label, boolean required, JComponent field) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(iconFieldLabel(iconType, label, required));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(field);
        return group;
    }

    /** Bọc 1 field trong khung có hậu tố đơn vị bên phải (vd "VNĐ") ngay trong khung field. */
    private JPanel wrapWithSuffix(JTextField field, String suffix) {
        field.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 0));

        JLabel suffixLabel = new JLabel(suffix);
        suffixLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        suffixLabel.setForeground(AppColor.TEXT_MUTED);
        suffixLabel.setBorder(new EmptyBorder(0, 6, 0, 10));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(suffixLabel, BorderLayout.EAST);
        return wrapper;
    }

    /** Thẻ nền xám nhạt hiển thị Mã nhân viên / Tên đăng nhập (chỉ đọc) - tách biệt trực quan khỏi field có thể sửa. */
    private JPanel buildIdentityCard() {
        JPanel card = new JPanel(new GridLayout(1, 2, 20, 0));
        card.setBackground(AppColor.BG_LIGHTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 16, 10, 16)));
        card.add(readonlyChip(FontAwesomeSolid.ID_CARD, "Mã nhân viên", employeeIdField));
        card.add(readonlyChip(FontAwesomeSolid.KEY, "Tên đăng nhập", usernameField));
        return card;
    }

    private JPanel readonlyChip(FontAwesomeSolid iconType, String label, JTextField valueField) {
        valueField.setBorder(BorderFactory.createEmptyBorder());
        valueField.setOpaque(false);
        valueField.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valueField.setDisabledTextColor(AppColor.TEXT_PRIMARY);
        valueField.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        FontIcon icon = FontIcon.of(iconType, 11);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel labelLabel = new JLabel(label, icon, SwingConstants.LEFT);
        labelLabel.setIconTextGap(5);
        labelLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(labelLabel);
        col.add(Box.createVerticalStrut(3));
        col.add(valueField);
        return col;
    }

    /** Banner hướng dẫn nổi bật (thay cho hintLabel đơn thuần) - nền xanh nhạt + icon info + viền màu accent info. */
    private JPanel infoBanner(String text) {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setBackground(AppColor.INFO_BG);
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        banner.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.INFO, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.INFO_CIRCLE, 15);
        icon.setIconColor(AppColor.INFO);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        // JTextArea word-wrap THẬT (khong dung chieu "<html><div style='width:Npx'>") -
        // cach cu doi luc khien HTML engine tinh preferred size RONG HON ca gia tri px
        // da khai bao (dac biet voi tieng Viet co dau), lam formPanel bi day rong ra va
        // JScrollPane phai hien thanh cuon ngang. JTextArea(rows, columns) tinh do rong
        // theo SO KY TU, luon nam trong khoang du kien, khong bao gio vuot qua.
        JTextArea textArea = new JTextArea(text, 3, 44);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder());
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        textArea.setForeground(AppColor.TEXT_PRIMARY);

        banner.add(iconLabel, BorderLayout.WEST);
        banner.add(textArea, BorderLayout.CENTER);
        return banner;
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    protected void fillForm(Employee entity) {
        if (employeeIdField != null) employeeIdField.setText(entity.getEmployeeId());
        if (usernameField != null) usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        dobPicker.setValue(entity.getDateOfBirth());
        salaryField.setText(entity.getSalary() != null ? CurrencyDocumentFilter.format(entity.getSalary()) : "");
        hireDatePicker.setValue(entity.getHireDate() != null ? entity.getHireDate() : LocalDate.now());
        genderCombo.setSelectedIndex(indexOfGender(entity.getGender()));
        roleCombo.setSelectedIndex(indexOfRole(entity.getRole()));
        if (statusCombo != null) {
            statusCombo.setSelectedIndex(entity.isDisabled() ? 1 : 0);
        }

        currentAvatarUrl = entity.getAvatarUrl();
        String name = entity.getFullName() != null ? entity.getFullName() : entity.getUsername();
        if (currentAvatarUrl != null && !currentAvatarUrl.isBlank()) {
            avatarPreviewLabel.setIcon(ImageUtil.circularIcon(currentAvatarUrl, AVATAR_PREVIEW, name));
            avatarHintLabel.setText("Ảnh hiện tại");
        } else {
            avatarPreviewLabel.setIcon(ImageUtil.circularIcon(null, AVATAR_PREVIEW, name));
            avatarHintLabel.setText("Tùy chọn · tối đa 5MB");
        }
    }

    @Override
    protected String validateForm() {
        int excludeId = editingEntity != null ? editingEntity.getUserId() : -1;

        FormValidator validator = new FormValidator();

        validator.field(fullNameField.getText())
                .required("Vui lòng nhập họ và tên.");

        validator.field(emailField.getText())
                .required("Vui lòng nhập email.")
                .email("Email không đúng định dạng.")
                .rule(Rules.custom(v -> !employeeDAO.emailExistsExcluding(v, excludeId), "Email này đã được dùng cho tài khoản khác."));

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        validator.field(salaryField.getText())
                .rule(v -> isBlankOrValidSalary(v) ? null : "Lương phải là số không âm.");

        return validator.validate();
    }

    @Override
    protected Employee collectFormData() {
        Employee employee = editingEntity != null ? editingEntity : new Employee();
        employee.setFullName(fullNameField.getText().trim());
        employee.setEmail(emailField.getText().trim());
        employee.setPhone(phoneField.getText().trim());
        employee.setDateOfBirth(dobPicker.getValue());
        employee.setHireDate(hireDatePicker.getValue());
        employee.setSalary(parseSalaryOrNull(salaryField.getText()));
        employee.setGender(GENDERS[genderCombo.getSelectedIndex()]);
        employee.setRole(EMP_ROLES[roleCombo.getSelectedIndex()]);
        if (statusCombo != null) {
            employee.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        } else {
            employee.setStatus("ACTIVE");
        }

        // Copy avatar để ở persist() (background) — tránh đơ UI trên EDT
        employee.setAvatarUrl(currentAvatarUrl);
        return employee;
    }

    @Override
    protected boolean persist(Employee entity, CrudMode mode) {
        // File I/O + DB + gửi email chạy trên SwingWorker — không block EDT
        if (pendingAvatarFile != null) {
            File saved = FileUtil.copyToDirectory(pendingAvatarFile, UPLOAD_DIR);
            entity.setAvatarUrl(saved != null ? saved.getPath() : currentAvatarUrl);
        }
        if (mode == CrudMode.ADD) {
            EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(entity);
            if (!result.success) {
                return false;
            }
            // Dialog Swing phải chạy trên EDT (persist có thể đang ở background thread)
            if (SwingUtilities.isEventDispatchThread()) {
                showCreationResult(entity, result);
            } else {
                try {
                    SwingUtilities.invokeAndWait(() -> showCreationResult(entity, result));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }
        return employeeDAO.updateByAdmin(entity);
    }

    /**
     * Bao cho Admin biet Ma NV/Username vua sinh, va (neu gui email that bai)
     * hien thi tam mat khau ngay tren man hinh de Admin cung cap thu cong -
     * vi mat khau nay KHONG duoc luu lai o bat ky dau khac.
     */
    private void showCreationResult(Employee entity, EmployeeDAO.EmployeeCreationResult result) {
        String info = "Mã nhân viên: " + entity.getEmployeeId() + "\n"
                + "Tên đăng nhập: " + entity.getUsername() + "\n\n";

        if (result.emailSent) {
            BaseDialog.success(this, "Tạo tài khoản thành công",
                    info + "Mật khẩu đăng nhập đã được gửi tới email " + entity.getEmail() + ".");
        } else {
            BaseDialog.error(this, "Đã tạo tài khoản nhưng gửi email thất bại",
                    info + "Mật khẩu tạm thời: " + result.rawPassword + "\n\n"
                    + "Vui lòng cung cấp mật khẩu này cho nhân viên thủ công (gửi email thất bại"
                    + (result.emailError != null ? ": " + result.emailError : "") + ").");
        }
    }

    // ---------------------------------------------------------------
    // Helper parse/validate ngày & lương
    // ---------------------------------------------------------------

    private static boolean isBlankOrValidSalary(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        BigDecimal parsed = CurrencyDocumentFilter.parse(value);
        return parsed != null && parsed.compareTo(BigDecimal.ZERO) >= 0;
    }

    private static BigDecimal parseSalaryOrNull(String value) {
        return CurrencyDocumentFilter.parse(value);
    }

    private static int indexOfRole(Role role) {
        for (int i = 0; i < EMP_ROLES.length; i++) {
            if (EMP_ROLES[i] == role) return i;
        }
        return 0;
    }

    private static int indexOfGender(Employee.Gender gender) {
        if (gender == null) return 0;
        for (int i = 0; i < GENDERS.length; i++) {
            if (GENDERS[i] == gender) return i;
        }
        return 0;
    }
}