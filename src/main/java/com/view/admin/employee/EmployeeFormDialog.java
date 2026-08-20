package com.view.admin.employee;

import com.components.BaseDialog;
import com.components.DatePickerField;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.EmployeeDAO;
import com.dao.RoleDAO;
import com.model.AppRole;
import com.model.Employee;
import com.service.media.CloudinaryService;
import com.service.media.CloudinaryUploadException;
import com.theme.AppColor;
import com.theme.AppFont;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Random;

/**
 * Dialog Thêm mới / Cập nhật nhân viên — UX/UI đồng bộ với
 * CategoryFormDialog và SupplierFormDialog.
 */
public class EmployeeFormDialog extends BaseFormDialog<Employee> {

    private List<AppRole> availableRoles;

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};
    private static final Employee.Gender[] GENDERS = {
            Employee.Gender.MALE, Employee.Gender.FEMALE, Employee.Gender.OTHER
    };
    private static final String[] GENDER_LABELS = {"Nam", "Nữ", "Khác"};
    private static final int AVATAR_PREVIEW = 140;
    private static final Random RANDOM = new Random();

    private static final DemoTemplate[] DEMO_TEMPLATES = {
            new DemoTemplate("Nguyễn Văn An", "nguyenvanan", Employee.Gender.MALE),
            new DemoTemplate("Trần Thị Bích", "tranthibich", Employee.Gender.FEMALE),
            new DemoTemplate("Lê Hoàng Nam", "lehoangnam", Employee.Gender.MALE),
            new DemoTemplate("Phạm Thị Hồng", "phamthihong", Employee.Gender.FEMALE),
            new DemoTemplate("Hoàng Minh Tuấn", "hoangminhtuan", Employee.Gender.MALE),
            new DemoTemplate("Vũ Thị Lan", "vuthilan", Employee.Gender.FEMALE),
            new DemoTemplate("Đặng Quốc Huy", "dangquochuy", Employee.Gender.MALE),
            new DemoTemplate("Bùi Thị Ngọc", "buithingoc", Employee.Gender.FEMALE),
    };

    private static final String[] PHONE_PREFIXES = {
            "032", "033", "034", "035", "036", "037", "038", "039",
            "070", "076", "077", "078", "079",
            "081", "082", "083", "084", "085", "088",
            "090", "091", "092", "093", "094", "096", "097", "098", "099"
    };

    private static final long[] SALARY_MIN_BY_ROLE = {15_000_000L, 12_000_000L, 10_000_000L, 6_000_000L};
    private static final long[] SALARY_MAX_BY_ROLE = {25_000_000L, 18_000_000L, 15_000_000L, 10_000_000L};

    private static final class DemoTemplate {
        final String fullName;
        final String emailSlug;
        final Employee.Gender gender;

        DemoTemplate(String fullName, String emailSlug, Employee.Gender gender) {
            this.fullName = fullName;
            this.emailSlug = emailSlug;
            this.gender = gender;
        }
    }

    private final EmployeeDAO employeeDAO;
    private final RoleDAO roleDAO = new RoleDAO();

    private JTextField employeeIdField;
    private JTextField usernameField;
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
        return 880;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 680 : 700;
    }

    @Override
    protected void buildFields(JPanel panel) {
        availableRoles = roleDAO.findManagedRoles();

        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(14));

        if (mode == CrudMode.EDIT) {
            employeeIdField = newTextField();
            employeeIdField.setEnabled(false);
            usernameField = newTextField();
            usernameField.setEnabled(false);
            panel.add(buildIdentityCard());
            panel.add(Box.createVerticalStrut(14));
        }

        if (mode == CrudMode.ADD) {
            panel.add(buildDemoBar());
            panel.add(Box.createVerticalStrut(10));
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
            panel.add(buildCredentialHintBanner());
        }
    }

    private JPanel buildInfoBanner() {
        JPanel banner = new JPanel(new BorderLayout(12, 0));
        banner.setOpaque(true);
        banner.setBackground(AppColor.ACCENT_BG_SOFT);
        banner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.ACCENT, 1, true),
                new EmptyBorder(12, 14, 12, 14)));
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));

        JPanel iconWrap = new JPanel();
        iconWrap.setOpaque(false);
        iconWrap.setPreferredSize(new Dimension(32, 32));
        FontIcon icon = FontIcon.of(
                mode == CrudMode.EDIT ? FontAwesomeSolid.USER_EDIT : FontAwesomeSolid.USER_PLUS, 16);
        icon.setIconColor(AppColor.ACCENT);
        iconWrap.add(new JLabel(icon));

        String html = mode == CrudMode.ADD
                ? "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Thêm nhân viên mới</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Tài khoản đăng nhập sẽ được tạo tự động và gửi qua email sau khi lưu.</span></html>"
                : "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Cập nhật nhân viên</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Chỉnh thông tin cá nhân, vai trò, lương hoặc trạng thái tài khoản.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildCredentialHintBanner() {
        JPanel banner = new JPanel(new BorderLayout(10, 0));
        banner.setBackground(AppColor.INFO_BG);
        banner.setAlignmentX(Component.LEFT_ALIGNMENT);
        banner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));
        banner.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.INFO, 1, true),
                new EmptyBorder(10, 12, 10, 12)));

        FontIcon icon = FontIcon.of(FontAwesomeSolid.INFO_CIRCLE, 15);
        icon.setIconColor(AppColor.INFO);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setVerticalAlignment(SwingConstants.TOP);
        iconLabel.setBorder(new EmptyBorder(2, 0, 0, 0));

        JTextArea textArea = new JTextArea(
                "Mã nhân viên, tên đăng nhập và mật khẩu sẽ được hệ thống tự động tạo "
                        + "và gửi tới email nhân viên sau khi lưu thành công.", 3, 44);
        textArea.setEditable(false);
        textArea.setFocusable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setOpaque(false);
        textArea.setBorder(BorderFactory.createEmptyBorder());
        textArea.setFont(AppFont.SMALL);
        textArea.setForeground(AppColor.TEXT_PRIMARY);

        banner.add(iconLabel, BorderLayout.WEST);
        banner.add(textArea, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildDemoBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bar.setOpaque(false);
        bar.setAlignmentX(Component.LEFT_ALIGNMENT);
        bar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JButton demoButton = new JButton("Điền dữ liệu Demo",
                FontIcon.of(FontAwesomeSolid.BOLT, 13, Color.WHITE));
        demoButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        demoButton.setFocusPainted(false);
        demoButton.setBackground(AppColor.ACCENT);
        demoButton.setForeground(Color.WHITE);
        demoButton.setBorder(new EmptyBorder(7, 14, 7, 14));
        demoButton.setToolTipText("Tự động điền thông tin mẫu để giảm thời gian demo");
        demoButton.addActionListener(e -> fillDemoData());
        demoButton.getModel().addChangeListener(e -> {
            if (demoButton.isEnabled()) {
                demoButton.setBackground(demoButton.getModel().isRollover()
                        ? AppColor.ACCENT_HOVER : AppColor.ACCENT);
            }
        });
        bar.add(demoButton);
        return bar;
    }

    private void fillDemoData() {
        if (availableRoles == null || availableRoles.isEmpty()) return;
        DemoTemplate t = DEMO_TEMPLATES[RANDOM.nextInt(DEMO_TEMPLATES.length)];
        int suffix = 100 + RANDOM.nextInt(900);
        int roleIndex = Math.min(RANDOM.nextInt(availableRoles.size()), SALARY_MIN_BY_ROLE.length - 1);
        fullNameField.setText(t.fullName);
        emailField.setText(t.emailSlug + suffix + "@gmail.com");
        phoneField.setText(randomPhoneVn());
        dobPicker.setValue(randomDob());
        genderCombo.setSelectedIndex(indexOfGender(t.gender));
        roleCombo.setSelectedIndex(roleIndex);
        salaryField.setText(CurrencyDocumentFilter.format(BigDecimal.valueOf(randomSalary(roleIndex))));
        showMessage(null);
        fullNameField.requestFocusInWindow();
    }

    private static String randomPhoneVn() {
        String prefix = PHONE_PREFIXES[RANDOM.nextInt(PHONE_PREFIXES.length)];
        StringBuilder sb = new StringBuilder(prefix);
        for (int i = 0; i < 7; i++) sb.append(RANDOM.nextInt(10));
        return sb.toString();
    }

    private static LocalDate randomDob() {
        int age = 20 + RANDOM.nextInt(26);
        return LocalDate.now().minusYears(age).minusDays(RANDOM.nextInt(365));
    }

    private static long randomSalary(int roleIndex) {
        int safeIdx = Math.min(roleIndex, SALARY_MIN_BY_ROLE.length - 1);
        long min = SALARY_MIN_BY_ROLE[safeIdx];
        long max = SALARY_MAX_BY_ROLE[safeIdx];
        long step = 500_000L;
        int steps = (int) ((max - min) / step) + 1;
        return min + RANDOM.nextInt(steps) * step;
    }

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
        avatarPreviewLabel.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        avatarPreviewLabel.setIcon(ImageUtil.circularIcon(null, AVATAR_PREVIEW, "?"));
        avatarPreviewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        col.add(avatarPreviewLabel);
        col.add(Box.createVerticalStrut(12));

        JButton chooseButton = new JButton("Chọn ảnh",
                FontIcon.of(FontAwesomeSolid.IMAGE, 13, AppColor.ACCENT));
        chooseButton.setFont(AppFont.BODY_BOLD);
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
        avatarHintLabel.setFont(AppFont.SMALL);
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

    private JPanel buildPersonalColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.ID_CARD, "Thông tin cá nhân");

        fullNameField = createIconTextField(FontAwesomeSolid.USER);
        col.add(fieldGroupIcon(fullNameField, "Họ và tên", true));
        col.add(Box.createVerticalStrut(12));

        emailField = createIconTextField(FontAwesomeSolid.ENVELOPE);
        col.add(fieldGroupIcon(emailField, "Email", true));
        col.add(Box.createVerticalStrut(12));

        phoneField = createIconTextField(FontAwesomeSolid.PHONE_ALT);
        col.add(fieldGroupIcon(phoneField, "Số điện thoại", false));
        col.add(Box.createVerticalStrut(4));
        col.add(hintLabel("VD: 09xxxxxxxx (tùy chọn)"));
        col.add(Box.createVerticalStrut(10));

        dobPicker = new DatePickerField(null, true);
        dobPicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        dobPicker.setPreferredSize(new Dimension(160, 36));
        col.add(fieldGroupPlain("Ngày sinh", false, dobPicker));
        col.add(Box.createVerticalStrut(12));

        genderCombo = newStyledComboBox(GENDER_LABELS);
        col.add(fieldGroupPlain("Giới tính", false, genderCombo));
        return col;
    }

    private JPanel buildWorkColumn() {
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setAlignmentY(Component.TOP_ALIGNMENT);
        col.setAlignmentX(Component.LEFT_ALIGNMENT);

        addSectionHeader(col, FontAwesomeSolid.CLIPBOARD_LIST, "Thông tin công việc");

        String[] roleNames = availableRoles.stream()
                .map(AppRole::getRoleName)
                .toArray(String[]::new);
        roleCombo = newStyledComboBox(roleNames);
        col.add(fieldGroupPlain("Vai trò", true, roleCombo));
        col.add(Box.createVerticalStrut(12));

        hireDatePicker = new DatePickerField(LocalDate.now());
        hireDatePicker.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        hireDatePicker.setPreferredSize(new Dimension(160, 36));
        col.add(fieldGroupPlain("Ngày vào làm", true, hireDatePicker));
        col.add(Box.createVerticalStrut(12));

        salaryField = newTextField();
        CurrencyDocumentFilter.install(salaryField);
        col.add(fieldGroupPlain("Lương", false, wrapWithSuffix(salaryField, "VNĐ")));
        col.add(Box.createVerticalStrut(4));
        col.add(hintLabel("Để trống nếu chưa xác định."));
        col.add(Box.createVerticalStrut(10));

        if (mode == CrudMode.EDIT) {
            statusCombo = newStyledComboBox(STATUS_LABELS);
            col.add(fieldGroupPlain("Trạng thái", true, statusCombo));
        }
        return col;
    }

    private <E> JComboBox<E> newStyledComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(AppFont.FIELD);
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    private void addSectionHeader(JPanel panel, FontAwesomeSolid iconType, String text) {
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));

        FontIcon icon = FontIcon.of(iconType, 13);
        icon.setIconColor(AppColor.ACCENT);
        JLabel textLabel = new JLabel(text.toUpperCase());
        textLabel.setFont(AppFont.SMALL_BOLD);
        textLabel.setForeground(AppColor.ACCENT);
        textLabel.setBorder(new EmptyBorder(0, 8, 0, 10));

        JSeparator sep = new JSeparator(SwingConstants.HORIZONTAL);
        sep.setForeground(AppColor.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setAlignmentY(Component.CENTER_ALIGNMENT);

        row.add(new JLabel(icon));
        row.add(textLabel);
        row.add(sep);
        panel.add(row);
        panel.add(Box.createVerticalStrut(12));
    }

    private JLabel iconFieldLabel(FontAwesomeSolid iconType, String text, boolean required) {
        FontIcon icon = FontIcon.of(iconType, 12);
        icon.setIconColor(AppColor.TEXT_MUTED_ALT);
        String html = "<html>" + text
                + (required ? " <font color='" + hex(AppColor.ERROR) + "'>*</font>" : "")
                + "</html>";
        JLabel label = new JLabel(html, icon, SwingConstants.LEFT);
        label.setIconTextGap(6);
        label.setFont(AppFont.SMALL_BOLD);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 2, 4, 0));
        return label;
    }

    private JTextField createIconTextField(FontAwesomeSolid iconKey) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 0));
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        FontIcon icon = FontIcon.of(iconKey, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setBorder(new EmptyBorder(0, 8, 0, 6));
        wrapper.add(iconLabel, BorderLayout.WEST);

        JTextField field = new JTextField();
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);
        field.setBorder(new EmptyBorder(6, 2, 6, 8));
        wrapper.add(field, BorderLayout.CENTER);
        field.putClientProperty("iconWrapper", wrapper);
        return field;
    }

    private JPanel fieldGroupIcon(JTextField iconField, String label, boolean required) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(fieldLabel(label, required));
        JPanel wrapper = (JPanel) iconField.getClientProperty("iconWrapper");
        if (wrapper != null) {
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(wrapper);
        } else {
            iconField.setAlignmentX(Component.LEFT_ALIGNMENT);
            group.add(iconField);
        }
        return group;
    }

    private JPanel fieldGroupPlain(String label, boolean required, JComponent field) {
        JPanel group = new JPanel();
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setOpaque(false);
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(fieldLabel(label, required));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(field);
        return group;
    }

    private JPanel wrapWithSuffix(JTextField field, String suffix) {
        field.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 0));
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);

        JLabel suffixLabel = new JLabel(suffix);
        suffixLabel.setFont(AppFont.SMALL_BOLD);
        suffixLabel.setForeground(AppColor.TEXT_MUTED);
        suffixLabel.setBorder(new EmptyBorder(0, 6, 0, 10));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(true);
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(new LineBorder(AppColor.FIELD_BORDER, 1, true));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(suffixLabel, BorderLayout.EAST);
        return wrapper;
    }

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
        valueField.setFont(AppFont.HEADING_MD);
        valueField.setDisabledTextColor(AppColor.TEXT_PRIMARY);
        valueField.setAlignmentX(Component.LEFT_ALIGNMENT);
        valueField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));

        FontIcon icon = FontIcon.of(iconType, 11);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel labelLabel = new JLabel(label, icon, SwingConstants.LEFT);
        labelLabel.setIconTextGap(5);
        labelLabel.setFont(AppFont.SMALL);
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

    private static String hex(Color c) {
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
        roleCombo.setSelectedIndex(indexOfRoleCode(entity.getRoleCode()));
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
                .rule(Rules.custom(v -> !employeeDAO.emailExistsExcluding(v, excludeId),
                        "Email này đã được dùng cho tài khoản khác."));
        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }
        validator.field(salaryField.getText())
                .rule(v -> isBlankOrValidSalary(v) ? null : "Lương phải là số không âm.");
        if (roleCombo.getSelectedIndex() < 0) {
            return "Vui lòng chọn vai trò.";
        }
        return validator.validate();
    }

    @Override
    protected Employee collectFormData() {
        Employee employee = editingEntity != null ? editingEntity : new Employee();
        employee.setFullName(fullNameField.getText().trim());
        employee.setEmail(emailField.getText().trim());
        employee.setPhone(phoneField.getText() != null ? phoneField.getText().trim() : "");
        employee.setDateOfBirth(dobPicker.getValue());
        employee.setHireDate(hireDatePicker.getValue());
        employee.setSalary(parseSalaryOrNull(salaryField.getText()));
        employee.setGender(GENDERS[genderCombo.getSelectedIndex()]);

        int selectedIdx = roleCombo.getSelectedIndex();
        if (selectedIdx >= 0 && selectedIdx < availableRoles.size()) {
            employee.setRoleCode(availableRoles.get(selectedIdx).getRoleCode());
        }

        if (statusCombo != null) {
            employee.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        } else {
            employee.setStatus("ACTIVE");
        }
        employee.setAvatarUrl(currentAvatarUrl);
        return employee;
    }

    @Override
    protected boolean persist(Employee entity, CrudMode mode) {
        if (pendingAvatarFile != null) {
            try {
                String cloudUrl = CloudinaryService.getInstance().uploadAvatar(pendingAvatarFile);
                entity.setAvatarUrl(cloudUrl);
                currentAvatarUrl = cloudUrl;
                pendingAvatarFile = null;
            } catch (CloudinaryUploadException e) {
                setPersistFailureMessage(e.getMessage());
                return false;
            }
        }
        if (mode == CrudMode.ADD) {
            EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(entity);
            if (!result.success) return false;
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

    private static boolean isBlankOrValidSalary(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        BigDecimal parsed = CurrencyDocumentFilter.parse(value);
        return parsed != null && parsed.compareTo(BigDecimal.ZERO) >= 0;
    }

    private static BigDecimal parseSalaryOrNull(String value) {
        return CurrencyDocumentFilter.parse(value);
    }

    private int indexOfRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return 0;
        for (int i = 0; i < availableRoles.size(); i++) {
            if (roleCode.equalsIgnoreCase(availableRoles.get(i).getRoleCode())) return i;
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