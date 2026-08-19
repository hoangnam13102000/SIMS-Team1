package com.view.admin.account;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.RoleDAO;
import com.dao.UserDAO;
import com.model.AppRole;
import com.model.Role;
import com.model.User;
import com.theme.AppColor;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class UserFormDialog extends BaseFormDialog<User> {
    
    // ❌ ĐÃ XÓA: ROLES và ROLE_LABELS hardcode
    
    // ⭐ DANH SÁCH VAI TRÒ TƯƠI TỪ DB
    private List<AppRole> availableRoles;
    
    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};
    
    private final UserDAO userDAO;
    private final RoleDAO roleDAO = new RoleDAO(); // ⭐ Thêm RoleDAO
    
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
        return mode == CrudMode.ADD ? 540 : 480;
    }

    @Override
    protected void buildFields(JPanel panel) {
        // ⭐ LOAD VAI TRÒ TỪ DB (bao gồm cả vai trò tùy chỉnh)
        availableRoles = roleDAO.findAll(); // findAll bao gồm cả CUSTOMER
        
        fullNameField = newTextField();
        usernameField = newTextField();
        if (mode == CrudMode.EDIT) {
            usernameField.setEnabled(false);
        }
        fieldRow(panel,
                fieldGroup("Họ và tên", true, fullNameField),
                fieldGroup("Tên đăng nhập", true, usernameField));
        
        emailField = addTextField(panel, "Email", true);
        phoneField = addTextField(panel, "Số điện thoại", false);
        
        // ⭐ Tạo roleCombo từ DB
        String[] roleNames = availableRoles.stream()
                .map(AppRole::getRoleName)
                .toArray(String[]::new);
        roleCombo = newComboBox(roleNames);
        
        if (mode == CrudMode.EDIT) {
            statusCombo = newComboBox(STATUS_LABELS);
            fieldRow(panel,
                    fieldGroup("Vai trò", true, roleCombo),
                    fieldGroup("Trạng thái", true, statusCombo));
        } else {
            panel.add(fieldLabel("Vai trò", true));
            roleCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(roleCombo);
            panel.add(Box.createVerticalStrut(14));
        }
        
        if (mode == CrudMode.ADD) {
            passwordField = new JPasswordField();
            styleField(passwordField);
            panel.add(fieldGroup("Mật khẩu ban đầu", true, passwordField));
            panel.add(hintLabel("Ít nhất 6 ký tự"));
            panel.add(Box.createVerticalStrut(14));
        }
    }

    private <E> JComboBox<E> newComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    @Override
    protected void fillForm(User entity) {
        usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        
        // ⭐ Tìm index theo roleCode
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
        return validator.validate();
    }

    @Override
    protected User collectFormData() {
        User user = editingEntity != null ? editingEntity : new User();
        user.setUsername(usernameField.getText().trim());
        user.setFullName(fullNameField.getText().trim());
        user.setEmail(emailField.getText().trim());
        user.setPhone(phoneField.getText().trim());
        
        // ⭐ Lấy roleCode từ AppRole được chọn
        int selectedIdx = roleCombo.getSelectedIndex();
        if (selectedIdx >= 0 && selectedIdx < availableRoles.size()) {
            AppRole selectedRole = availableRoles.get(selectedIdx);
            user.setRoleCode(selectedRole.getRoleCode());
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

    // ⭐ MỚI: Tìm index theo roleCode
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