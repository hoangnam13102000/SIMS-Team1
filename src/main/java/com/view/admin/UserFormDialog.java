package com.view.admin;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.UserDAO;
import com.model.Role;
import com.model.User;
import com.theme.AppColor;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.BorderFactory;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;

/**
 * Dialog Thêm mới / Cập nhật 1 tài khoản người dùng, dùng trong
 * {@link UserAccountPanel} (khu vực quản trị - chỉ Admin truy cập được).
 * <p>
 * Khác với RegisterFrame (đăng ký công khai, luôn gán Role.SALES_STAFF),
 * dialog này cho phép Admin chọn vai trò bất kỳ và (khi thêm mới) đặt mật
 * khẩu ban đầu cho tài khoản.
 */
public class UserFormDialog extends BaseFormDialog<User> {

    private static final Role[] ROLES = Role.values();
    private static final String[] ROLE_LABELS = {
            "Quản trị viên", "Quản lý bán hàng", "Quản lý kho", "Nhân viên bán hàng", "Khách hàng"
    };
    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private final UserDAO userDAO;

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
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 560 : 500;
    }

    @Override
    protected void buildFields(JPanel panel) {
        usernameField = addTextField(panel, "Tên đăng nhập");
        if (mode == CrudMode.EDIT) {
            usernameField.setEnabled(false); // Khong cho doi username sau khi da tao
        }

        fullNameField = addTextField(panel, "Họ và tên");
        emailField = addTextField(panel, "Email");
        phoneField = addTextField(panel, "Số điện thoại");
        roleCombo = addComboBox(panel, "Vai trò", ROLE_LABELS);

        if (mode == CrudMode.EDIT) {
            statusCombo = addComboBox(panel, "Trạng thái", STATUS_LABELS);
        }

        if (mode == CrudMode.ADD) {
            passwordField = addPasswordField(panel, "Mật khẩu ban đầu");
        }
    }

    /** BaseFormDialog chỉ có addTextField/addTextArea/addComboBox - tự thêm 1 field mật khẩu theo cùng style. */
    private JPasswordField addPasswordField(JPanel panel, String label) {
        panel.add(fieldLabel(label));
        JPasswordField field = new JPasswordField();
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)));
        panel.add(field);
        panel.add(Box.createVerticalStrut(14));
        return field;
    }

    @Override
    protected void fillForm(User entity) {
        usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        roleCombo.setSelectedIndex(indexOfRole(entity.getRole()));
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
                    .rule(Rules.custom(v -> !userDAO.usernameExists(v), "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác."));
        }

        validator.field(fullNameField.getText())
                .required("Vui lòng nhập họ và tên.");

        validator.field(emailField.getText())
                .required("Vui lòng nhập email.")
                .email("Email không đúng định dạng.")
                .rule(Rules.custom(v -> !userDAO.emailExistsExcluding(v, excludeId), "Email này đã được dùng cho tài khoản khác."));

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        if (mode == CrudMode.ADD) {
            validator.field(new String(passwordField.getPassword()))
                    .required("Vui lòng nhập mật khẩu ban đầu.")
                    .minLength(6, "Mật khẩu phải có ít nhất 6 ký tự.");
        }

        return validator.validate();
    }

    @Override
    protected User collectFormData() {
        User user = editingEntity != null ? editingEntity : new User();
        user.setUsername(usernameField.getText().trim());
        user.setFullName(fullNameField.getText().trim());
        user.setEmail(emailField.getText().trim());
        user.setPhone(phoneField.getText().trim());
        user.setRole(ROLES[roleCombo.getSelectedIndex()]);
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

    private static int indexOfRole(Role role) {
        for (int i = 0; i < ROLES.length; i++) {
            if (ROLES[i] == role) return i;
        }
        return 0;
    }
}