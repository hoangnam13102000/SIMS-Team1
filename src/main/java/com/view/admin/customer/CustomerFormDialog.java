package com.view.admin.customer;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.CustomerDAO;
import com.dao.UserDAO;
import com.model.Customer;
import com.theme.AppColor;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;

/**
 * Dialog Cập nhật thông tin 1 khách hàng, dùng trong {@link CustomerPanel}
 * (khu vực quản trị). Khác {@code UserFormDialog}: không đổi được Vai trò/
 * Tên đăng nhập/Mật khẩu (khách hàng luôn là Role.CUSTOMER, tự đặt mật khẩu
 * lúc đăng ký ở RegisterFrame) - chỉ sửa Họ tên/Email/SĐT/Trạng thái và
 * Điểm thành viên (bảng Customers).
 */
public class CustomerFormDialog extends BaseFormDialog<Customer> {

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private final CustomerDAO customerDAO;
    private final UserDAO userDAO = new UserDAO(); // dung lai emailExistsExcluding (cung bang Users)

    private JTextField usernameField;
    private JTextField fullNameField;
    private JTextField emailField;
    private JTextField phoneField;
    private JTextField memberPointField;
    private JComboBox<String> statusCombo;

    public CustomerFormDialog(Frame owner, CrudMode mode, Customer editingEntity, CustomerDAO customerDAO) {
        super(owner, "khách hàng", mode, editingEntity);
        this.customerDAO = customerDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 520; }

    @Override
    protected int getDialogHeight() { return 480; }

    @Override
    protected void buildFields(JPanel panel) {
        fullNameField = newTextField();
        usernameField = newTextField();
        usernameField.setEnabled(false); // khong the doi ten dang nhap
        fieldRow(panel,
                fieldGroup("Họ và tên", true, fullNameField),
                fieldGroup("Tên đăng nhập", true, usernameField));

        emailField = addTextField(panel, "Email", true);
        phoneField = addTextField(panel, "Số điện thoại", false);

        memberPointField = newTextField();
        statusCombo = newComboBox(STATUS_LABELS);
        fieldRow(panel,
                fieldGroup("Điểm thành viên", true, memberPointField),
                fieldGroup("Trạng thái", true, statusCombo));
    }

    /** Tao JComboBox da style dong bo voi text field nhung chua add vao panel - dung khi ghep hang ngang qua fieldRow(). */
    private <E> JComboBox<E> newComboBox(E[] items) {
        JComboBox<E> combo = new JComboBox<>(items);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        combo.setAlignmentX(Component.LEFT_ALIGNMENT);
        combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        combo.setBackground(AppColor.WHITE);
        return combo;
    }

    @Override
    protected void fillForm(Customer entity) {
        usernameField.setText(entity.getUsername());
        fullNameField.setText(entity.getFullName());
        emailField.setText(entity.getEmail());
        phoneField.setText(entity.getPhone());
        memberPointField.setText(String.valueOf(entity.getMemberPoint()));
        statusCombo.setSelectedIndex(entity.isDisabled() ? 1 : 0);
    }

    @Override
    protected String validateForm() {
        int excludeId = editingEntity != null ? editingEntity.getCustomerId() : -1;

        FormValidator validator = new FormValidator();

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

        validator.field(memberPointField.getText())
                .required("Vui lòng nhập điểm thành viên.")
                .rule(Rules.custom(v -> {
                    try {
                        return Integer.parseInt(v.trim()) >= 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }, "Điểm thành viên phải là số nguyên không âm."));

        return validator.validate();
    }

    @Override
    protected Customer collectFormData() {
        Customer customer = editingEntity != null ? editingEntity : new Customer();
        customer.setFullName(fullNameField.getText().trim());
        customer.setEmail(emailField.getText().trim());
        customer.setPhone(phoneField.getText().trim());
        customer.setMemberPoint(Integer.parseInt(memberPointField.getText().trim()));
        customer.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        return customer;
    }

    @Override
    protected boolean persist(Customer entity, CrudMode mode) {
        return customerDAO.updateByAdmin(entity);
    }
}