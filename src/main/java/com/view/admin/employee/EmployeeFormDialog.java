package com.view.admin.employee;

import com.components.BaseDialog;
import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.EmployeeDAO;
import com.model.Employee;
import com.model.Role;
import com.theme.AppColor;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.Box;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EmployeeDAO employeeDAO;

    private JTextField employeeIdField; // chi doc, chi hien thi khi Sua
    private JTextField usernameField;   // chi doc, chi hien thi khi Sua
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField dobField;
    private JTextField salaryField;
    private JTextField hireDateField;
    private JComboBox<String> genderCombo;
    private JComboBox<String> roleCombo;
    private JComboBox<String> statusCombo;

    public EmployeeFormDialog(Frame owner, CrudMode mode, Employee editingEntity, EmployeeDAO employeeDAO) {
        super(owner, "nhân viên", mode, editingEntity);
        this.employeeDAO = employeeDAO;
        init();
    }

    @Override
    protected int getDialogWidth() {
        return 540;
    }

    @Override
    protected int getDialogHeight() {
        return mode == CrudMode.ADD ? 660 : 720;
    }

    @Override
    protected void buildFields(JPanel panel) {
        if (mode == CrudMode.EDIT) {
            employeeIdField = newTextField();
            employeeIdField.setEnabled(false);
            usernameField = newTextField();
            usernameField.setEnabled(false);
            fieldRow(panel,
                    fieldGroup("Mã nhân viên", false, employeeIdField),
                    fieldGroup("Tên đăng nhập", false, usernameField));
        }

        fullNameField = addTextField(panel, "Họ và tên", true);
        emailField = addTextField(panel, "Email", true);

        phoneField = newTextField();
        dobField = newTextField();
        dobField.setToolTipText("dd/MM/yyyy");
        fieldRow(panel,
                fieldGroup("Số điện thoại", false, phoneField),
                fieldGroup("Ngày sinh (dd/MM/yyyy)", false, dobField));

        genderCombo = newComboBox(GENDER_LABELS);
        salaryField = newTextField();
        fieldRow(panel,
                fieldGroup("Giới tính", false, genderCombo),
                fieldGroup("Lương (VNĐ)", false, salaryField));

        roleCombo = newComboBox(EMP_ROLE_LABELS);
        hireDateField = newTextField();
        hireDateField.setText(LocalDate.now().format(DATE_FMT));
        hireDateField.setToolTipText("dd/MM/yyyy");
        fieldRow(panel,
                fieldGroup("Vai trò", true, roleCombo),
                fieldGroup("Ngày vào làm (dd/MM/yyyy)", false, hireDateField));

        if (mode == CrudMode.EDIT) {
            statusCombo = newComboBox(STATUS_LABELS);
            panel.add(fieldLabel("Trạng thái", true));
            statusCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(statusCombo);
            panel.add(Box.createVerticalStrut(14));
        }

        if (mode == CrudMode.ADD) {
            panel.add(hintLabel(
                    "Mã nhân viên, tên đăng nhập và mật khẩu đăng nhập sẽ được hệ thống tự động " +
                    "tạo và gửi tới email nhân viên ở trên sau khi lưu."));
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
    protected void fillForm(Employee entity) {
        if (employeeIdField != null) employeeIdField.setText(entity.getEmployeeId());
        if (usernameField != null) usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        dobField.setText(entity.getDateOfBirth() != null ? entity.getDateOfBirth().format(DATE_FMT) : "");
        salaryField.setText(entity.getSalary() != null ? entity.getSalary().toPlainString() : "");
        hireDateField.setText(entity.getHireDate() != null ? entity.getHireDate().format(DATE_FMT) : "");
        genderCombo.setSelectedIndex(indexOfGender(entity.getGender()));
        roleCombo.setSelectedIndex(indexOfRole(entity.getRole()));
        if (statusCombo != null) {
            statusCombo.setSelectedIndex(entity.isDisabled() ? 1 : 0);
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

        validator.field(dobField.getText())
                .rule(v -> isBlankOrValidDate(v) ? null : "Ngày sinh không đúng định dạng (dd/MM/yyyy).");

        validator.field(hireDateField.getText())
                .rule(v -> isBlankOrValidDate(v) ? null : "Ngày vào làm không đúng định dạng (dd/MM/yyyy).");

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
        employee.setDateOfBirth(parseDateOrNull(dobField.getText()));
        employee.setHireDate(parseDateOrNull(hireDateField.getText()));
        employee.setSalary(parseSalaryOrNull(salaryField.getText()));
        employee.setGender(GENDERS[genderCombo.getSelectedIndex()]);
        employee.setRole(EMP_ROLES[roleCombo.getSelectedIndex()]);
        if (statusCombo != null) {
            employee.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        } else {
            employee.setStatus("ACTIVE");
        }
        return employee;
    }

    @Override
    protected boolean persist(Employee entity, CrudMode mode) {
        if (mode == CrudMode.ADD) {
            EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(entity);
            if (!result.success) {
                return false;
            }
            showCreationResult(entity, result);
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

    private static boolean isBlankOrValidDate(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        try {
            LocalDate.parse(value.trim(), DATE_FMT);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static boolean isBlankOrValidSalary(String value) {
        if (value == null || value.trim().isEmpty()) return true;
        try {
            return new BigDecimal(value.trim().replace(",", "")).compareTo(BigDecimal.ZERO) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static LocalDate parseDateOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(value.trim(), DATE_FMT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static BigDecimal parseSalaryOrNull(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
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