package com.view.admin.account;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.RoleDAO;
import com.dao.UserDAO;
import com.model.AppRole;
import com.model.User;
import com.theme.AppColor;
import com.theme.AppFont;
import com.validation.FormValidator;
import com.validation.Rules;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.List;

/**
 * Dialog Thêm mới / Cập nhật tài khoản — UX/UI đồng bộ Category / Supplier / Employee.
 */
public class UserFormDialog extends BaseFormDialog<User> {

    private List<AppRole> availableRoles;

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private final UserDAO userDAO;
    private final RoleDAO roleDAO = new RoleDAO();

    private JTextField usernameField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JComboBox<String> roleCombo;
    private JComboBox<String> statusCombo;
    private JPasswordField passwordField;

    public UserFormDialog(Frame owner, CrudMode mode, User editingEntity, UserDAO userDAO) {
        super(owner, "tài khoản", mode, editingEntity);
        this.userDAO = userDAO;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 520;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 620 : 580;
    }

    @Override
    protected void buildFields(JPanel panel) {
        availableRoles = roleDAO.findAll();

        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(14));

        if (mode == CrudMode.EDIT && editingEntity != null) {
            panel.add(buildIdentityCard());
            panel.add(Box.createVerticalStrut(14));
        }

        panel.add(fieldLabel("Họ và tên", true));
        fullNameField = createIconTextField(FontAwesomeSolid.USER);
        panel.add(wrapIconField(fullNameField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("Họ tên hiển thị trên hệ thống."));
        panel.add(Box.createVerticalStrut(14));

        if (mode == CrudMode.ADD) {
            panel.add(fieldLabel("Tên đăng nhập", true));
            usernameField = createIconTextField(FontAwesomeSolid.AT);
            panel.add(wrapIconField(usernameField));
            panel.add(Box.createVerticalStrut(4));
            panel.add(hintLabel("Ít nhất 4 ký tự, không trùng tài khoản khác."));
            panel.add(Box.createVerticalStrut(14));
        } else {
            usernameField = newTextField();
            usernameField.setEnabled(false);
        }

        panel.add(fieldLabel("Email", true));
        emailField = createIconTextField(FontAwesomeSolid.ENVELOPE);
        panel.add(wrapIconField(emailField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("Dùng để nhận thông báo / khôi phục tài khoản."));
        panel.add(Box.createVerticalStrut(14));

        panel.add(fieldLabel("Số điện thoại"));
        phoneField = createIconTextField(FontAwesomeSolid.PHONE);
        panel.add(wrapIconField(phoneField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("VD: 09xxxxxxxx (tùy chọn)."));
        panel.add(Box.createVerticalStrut(14));

        String[] roleNames = availableRoles.stream()
                .map(AppRole::getRoleName)
                .toArray(String[]::new);
        roleCombo = newStyledComboBox(roleNames);

        if (mode == CrudMode.EDIT) {
            statusCombo = newStyledComboBox(STATUS_LABELS);
            fieldRow(panel,
                    fieldGroup("Vai trò", true, roleCombo),
                    fieldGroup("Trạng thái", true, statusCombo));
        } else {
            panel.add(fieldLabel("Vai trò", true));
            roleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(roleCombo);
            panel.add(Box.createVerticalStrut(4));
            panel.add(hintLabel("Quyền truy cập theo vai trò đã cấu hình trong RBAC."));
            panel.add(Box.createVerticalStrut(14));
        }

        if (mode == CrudMode.ADD) {
            panel.add(fieldLabel("Mật khẩu ban đầu", true));
            passwordField = new JPasswordField();
            stylePasswordField(passwordField);
            panel.add(passwordField);
            panel.add(Box.createVerticalStrut(4));
            panel.add(hintLabel("Ít nhất 6 ký tự. Người dùng có thể đổi sau khi đăng nhập."));
            panel.add(Box.createVerticalStrut(8));
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
                mode == CrudMode.EDIT ? FontAwesomeSolid.USER_COG : FontAwesomeSolid.USER_PLUS, 16);
        icon.setIconColor(AppColor.ACCENT);
        iconWrap.add(new JLabel(icon));

        String html = mode == CrudMode.ADD
                ? "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Thêm tài khoản mới</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Tạo tài khoản đăng nhập và gán vai trò trong hệ thống.</span></html>"
                : "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Cập nhật tài khoản</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Chỉnh thông tin liên hệ, vai trò hoặc trạng thái tài khoản.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildIdentityCard() {
        boolean hasEmp = editingEntity.getEmployeeCode() != null
                && !editingEntity.getEmployeeCode().isBlank();

        JPanel card = new JPanel(new GridLayout(1, hasEmp ? 3 : 2, 12, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.BG_LIGHTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        card.add(metaChip(FontAwesomeSolid.HASHTAG, "Mã tài khoản",
                String.valueOf(editingEntity.getUserId())));
        card.add(metaChip(FontAwesomeSolid.AT, "Tên đăng nhập",
                editingEntity.getUsername() != null ? editingEntity.getUsername() : "—"));
        if (hasEmp) {
            card.add(metaChip(FontAwesomeSolid.ID_BADGE, "Mã nhân viên",
                    editingEntity.getEmployeeCode()));
        }
        return card;
    }

    private JPanel metaChip(FontAwesomeSolid iconType, String label, String value) {
        FontIcon icon = FontIcon.of(iconType, 11);
        icon.setIconColor(AppColor.TEXT_MUTED);
        JLabel labelLabel = new JLabel(label, icon, SwingConstants.LEFT);
        labelLabel.setIconTextGap(5);
        labelLabel.setFont(AppFont.SMALL);
        labelLabel.setForeground(AppColor.TEXT_MUTED);
        labelLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(AppFont.SMALL_BOLD);
        valueLabel.setForeground(AppColor.TEXT_PRIMARY);
        valueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(labelLabel);
        col.add(Box.createVerticalStrut(3));
        col.add(valueLabel);
        return col;
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

    private JPanel wrapIconField(JTextField field) {
        JPanel wrapper = (JPanel) field.getClientProperty("iconWrapper");
        if (wrapper != null) {
            wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            return wrapper;
        }
        JPanel fallback = new JPanel(new BorderLayout());
        fallback.setOpaque(false);
        fallback.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        fallback.add(field, BorderLayout.CENTER);
        return fallback;
    }

    private <E> JComboBox<E> newStyledComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(AppFont.FIELD);
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    private void stylePasswordField(JPasswordField field) {
        field.setFont(AppFont.FIELD);
        field.setForeground(AppColor.TEXT_PRIMARY);
        field.setBackground(AppColor.WHITE);
        field.setCaretColor(AppColor.ACCENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.FIELD_BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    protected void fillForm(User entity) {
        usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        roleCombo.setSelectedIndex(indexOfRoleCode(entity.getRoleCode()));
        if (statusCombo != null) {
            statusCombo.setSelectedIndex(entity.isDisabled() ? 1 : 0);
        }
    }

    @Override
    protected String validateForm() {
        int excludeId = editingEntity != null ? editingEntity.getUserId() : -1;
        FormValidator validator = new FormValidator();

        if (mode == CrudMode.ADD) {
            validator.field(usernameField.getText())
                    .required("Vui lòng nhập tên đăng nhập.")
                    .minLength(4, "Tên đăng nhập phải có ít nhất 4 ký tự.")
                    .rule(Rules.custom(v -> !userDAO.usernameExists(v),
                            "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác."));
        }

        validator.field(fullNameField.getText())
                .required("Vui lòng nhập họ và tên.");

        validator.field(emailField.getText())
                .required("Vui lòng nhập email.")
                .email("Email không đúng định dạng.")
                .rule(Rules.custom(v -> !userDAO.emailExistsExcluding(v, excludeId),
                        "Email này đã được dùng cho tài khoản khác."));

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        if (mode == CrudMode.ADD) {
            validator.field(new String(passwordField.getPassword()))
                    .required("Vui lòng nhập mật khẩu ban đầu.")
                    .minLength(6, "Mật khẩu phải có ít nhất 6 ký tự.");
        }

        if (roleCombo.getSelectedIndex() < 0) {
            return "Vui lòng chọn vai trò.";
        }

        return validator.validate();
    }

    @Override
    protected User collectFormData() {
        User user = editingEntity != null ? editingEntity : new User();
        user.setUsername(usernameField.getText().trim());
        user.setFullName(fullNameField.getText().trim());
        user.setEmail(emailField.getText().trim());
        user.setPhone(phoneField.getText() != null ? phoneField.getText().trim() : "");

        int selectedIdx = roleCombo.getSelectedIndex();
        if (selectedIdx >= 0 && selectedIdx < availableRoles.size()) {
            user.setRoleCode(availableRoles.get(selectedIdx).getRoleCode());
        }

        if (statusCombo != null) {
            user.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        } else {
            user.setStatus("ACTIVE");
        }
        return user;
    }

    @Override
    protected boolean persist(User entity, CrudMode mode) {
        if (mode == CrudMode.ADD) {
            return userDAO.createByAdmin(entity, new String(passwordField.getPassword()));
        }
        return userDAO.updateByAdmin(entity);
    }

    private int indexOfRoleCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return 0;
        for (int i = 0; i < availableRoles.size(); i++) {
            if (roleCode.equalsIgnoreCase(availableRoles.get(i).getRoleCode())) {
                return i;
            }
        }
        return 0;
    }
}