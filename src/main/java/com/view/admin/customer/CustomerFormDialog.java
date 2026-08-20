package com.view.admin.customer;

import com.components.crud.BaseFormDialog;
import com.components.crud.CrudMode;
import com.dao.CustomerDAO;
import com.dao.UserDAO;
import com.model.Customer;
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

/**
 * Dialog Cập nhật khách hàng — UX/UI đồng bộ Category / User / Supplier.
 * Không đổi Vai trò / Username / Mật khẩu.
 */
public class CustomerFormDialog extends BaseFormDialog<Customer> {

    private static final String[] STATUS_LABELS = {"Đang hoạt động", "Vô hiệu hóa"};

    private final CustomerDAO customerDAO;
    private final UserDAO userDAO = new UserDAO();

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
    protected int getDialogWidth() {
        return 520;
    }

    @Override
    protected int getDialogHeight() {
        return 560;
    }

    @Override
    protected void buildFields(JPanel panel) {
        panel.add(buildInfoBanner());
        panel.add(Box.createVerticalStrut(14));

        if (editingEntity != null) {
            panel.add(buildIdentityCard());
            panel.add(Box.createVerticalStrut(14));
        }

        usernameField = newTextField();
        usernameField.setEnabled(false);

        panel.add(fieldLabel("Họ và tên", true));
        fullNameField = createIconTextField(FontAwesomeSolid.USER);
        panel.add(wrapIconField(fullNameField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("Họ tên hiển thị trên hồ sơ và đơn hàng."));
        panel.add(Box.createVerticalStrut(14));

        panel.add(fieldLabel("Email", true));
        emailField = createIconTextField(FontAwesomeSolid.ENVELOPE);
        panel.add(wrapIconField(emailField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("Không được trùng email tài khoản khác."));
        panel.add(Box.createVerticalStrut(14));

        panel.add(fieldLabel("Số điện thoại"));
        phoneField = createIconTextField(FontAwesomeSolid.PHONE);
        panel.add(wrapIconField(phoneField));
        panel.add(Box.createVerticalStrut(4));
        panel.add(hintLabel("VD: 09xxxxxxxx (tùy chọn)."));
        panel.add(Box.createVerticalStrut(14));

        memberPointField = createIconTextField(FontAwesomeSolid.STAR);
        statusCombo = newStyledComboBox(STATUS_LABELS);

        fieldRow(panel,
                fieldGroup("Điểm thành viên", true, wrapIconField(memberPointField)),
                fieldGroup("Trạng thái", true, statusCombo));
        panel.add(hintLabel("Điểm dùng cho chương trình khách hàng thân thiết (số nguyên ≥ 0)."));
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
        FontIcon icon = FontIcon.of(FontAwesomeSolid.USER_EDIT, 16);
        icon.setIconColor(AppColor.ACCENT);
        iconWrap.add(new JLabel(icon));

        String html = "<html><b style='color:" + hex(AppColor.TEXT_PRIMARY) + "'>Cập nhật khách hàng</b><br/>"
                + "<span style='color:" + hex(AppColor.TEXT_SECONDARY) + "'>"
                + "Chỉnh thông tin liên hệ, điểm thành viên hoặc trạng thái tài khoản.</span></html>";
        JLabel text = new JLabel(html);
        text.setFont(AppFont.BODY);

        banner.add(iconWrap, BorderLayout.WEST);
        banner.add(text, BorderLayout.CENTER);
        return banner;
    }

    private JPanel buildIdentityCard() {
        JPanel card = new JPanel(new GridLayout(1, 2, 12, 0));
        card.setOpaque(true);
        card.setBackground(AppColor.BG_LIGHTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(10, 14, 10, 14)));

        String code = editingEntity.getCustomerCode() != null && !editingEntity.getCustomerCode().isBlank()
                ? editingEntity.getCustomerCode()
                : "CUS_" + String.format("%04d", editingEntity.getCustomerId());
        String username = editingEntity.getUsername() != null ? editingEntity.getUsername() : "—";

        card.add(metaChip(FontAwesomeSolid.ID_CARD, "Mã khách hàng", code));
        card.add(metaChip(FontAwesomeSolid.AT, "Tên đăng nhập", username));
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

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
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
                .rule(Rules.custom(v -> !userDAO.emailExistsExcluding(v, excludeId),
                        "Email này đã được dùng cho tài khoản khác."));

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
        customer.setPhone(phoneField.getText() != null ? phoneField.getText().trim() : "");
        customer.setMemberPoint(Integer.parseInt(memberPointField.getText().trim()));
        customer.setStatus(statusCombo.getSelectedIndex() == 1 ? "DISABLED" : "ACTIVE");
        return customer;
    }

    @Override
    protected boolean persist(Customer entity, CrudMode mode) {
        return customerDAO.updateByAdmin(entity);
    }
}