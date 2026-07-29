package com.view.admin.supplier;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.SupplierDAO;
import com.model.Supplier;
import com.validation.FormValidator;

import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.Frame;

public class SupplierFormDialog extends BaseFormDialog<Supplier> {

    private final SupplierDAO supplierDAO;

    private JTextField nameField;
    private JTextField phoneField;
    private JTextField emailField;
    private JTextField addressField;
    private JTextArea suppliedItemsArea;

    public SupplierFormDialog(Frame owner, CrudMode mode, Supplier editingEntity, SupplierDAO supplierDAO) {
        super(owner, "nhà cung cấp", mode, editingEntity);
        this.supplierDAO = supplierDAO;
        init();
    }

    @Override
    protected int getDialogWidth() { return 480; }

    @Override
    protected int getDialogHeight() { return 560; }

    @Override
    protected void buildFields(JPanel panel) {
        nameField = addTextField(panel, "Tên nhà cung cấp", true);

        phoneField = newTextField();
        emailField = newTextField();
        fieldRow(panel,
                fieldGroup("Số điện thoại", false, phoneField),
                fieldGroup("Email", false, emailField));

        addressField = addTextField(panel, "Địa chỉ", false);

        suppliedItemsArea = addTextArea(panel, "Mặt hàng cung cấp");
    }

    @Override
    protected void fillForm(Supplier entity) {
        nameField.setText(entity.getSupplierName());
        phoneField.setText(entity.getPhone());
        emailField.setText(entity.getEmail());
        addressField.setText(entity.getAddress());
        suppliedItemsArea.setText(entity.getSuppliedItems());
    }

    @Override
    protected String validateForm() {
        FormValidator validator = new FormValidator();

        validator.field(nameField.getText())
                .required("Vui lòng nhập tên nhà cung cấp.")
                .maxLength(150, "Tên nhà cung cấp tối đa 150 ký tự.");

        String phone = phoneField.getText();
        if (phone != null && !phone.trim().isEmpty()) {
            validator.field(phone).phoneVn("Số điện thoại không đúng định dạng (vd 09xxxxxxxx).");
        }

        String email = emailField.getText();
        if (email != null && !email.trim().isEmpty()) {
            validator.field(email).email("Email không đúng định dạng.");
        }

        return validator.validate();
    }

    @Override
    protected Supplier collectFormData() {
        Supplier supplier = editingEntity != null ? editingEntity : new Supplier();
        supplier.setSupplierName(nameField.getText().trim());
        supplier.setPhone(blankToNull(phoneField.getText()));
        supplier.setEmail(blankToNull(emailField.getText()));
        supplier.setAddress(blankToNull(addressField.getText()));
        supplier.setSuppliedItems(blankToNull(suppliedItemsArea.getText()));
        return supplier;
    }

    @Override
    protected boolean persist(Supplier entity, CrudMode mode) {
        return mode == CrudMode.ADD ? supplierDAO.insert(entity) : supplierDAO.update(entity);
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}