package com.view.client;


import com.theme.AppColor;
import com.dao.UserDAO;
import com.model.User;
import com.service.AuthService;
import com.validation.FormValidator;
import com.validation.Rules;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

/**
 * Trang ho so ca nhan: sua ho ten / so dien thoai, va doi mat khau.
 * Gmail khong cho sua o day vi da duoc xac thuc OTP luc dang ky.
 */
public class ProfilePanel extends JPanel {

    private final UserDAO userDAO = new UserDAO();
    private Runnable onSavedListener;

    private JTextField fullNameField;
    private JTextField phoneField;
    private JLabel infoMessage;

    private JPasswordField currentPasswordField;
    private JPasswordField newPasswordField;
    private JPasswordField confirmPasswordField;
    private JLabel passwordMessage;

    public ProfilePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(24, 32, 24, 32));

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setOpaque(false);

        JLabel title = new JLabel("Trang cá nhân");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        title.setBorder(new EmptyBorder(0, 0, 16, 0));

        wrapper.add(title);
        wrapper.add(buildInfoCard());
        wrapper.add(Box.createVerticalStrut(20));
        wrapper.add(buildPasswordCard());

        add(wrapper, BorderLayout.NORTH);
        loadCurrentUser();
    }

    private JPanel buildInfoCard() {
        JPanel card = card();

        User user = AuthService.getInstance().getCurrentUser();

        card.add(cardTitle("Thông tin tài khoản"));

        card.add(fieldLabel("Tên đăng nhập"));
        JTextField usernameField = new JTextField(user.getUsername());
        usernameField.setEditable(false);
        styleField(usernameField);
        card.add(usernameField);

        card.add(fieldLabel("Gmail"));
        JTextField emailField = new JTextField(user.getEmail());
        emailField.setEditable(false);
        styleField(emailField);
        card.add(emailField);

        card.add(fieldLabel("Họ và tên"));
        fullNameField = new JTextField();
        styleField(fullNameField);
        card.add(fullNameField);

        card.add(fieldLabel("Số điện thoại"));
        phoneField = new JTextField();
        styleField(phoneField);
        card.add(phoneField);

        infoMessage = new JLabel(" ");
        infoMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoMessage.setBorder(new EmptyBorder(8, 0, 8, 0));
        card.add(infoMessage);

        JButton saveButton = accentButton("Lưu thay đổi");
        saveButton.addActionListener(e -> saveProfile());
        card.add(saveButton);

        return card;
    }

    private JPanel buildPasswordCard() {
        JPanel card = card();
        card.add(cardTitle("Đổi mật khẩu"));

        card.add(fieldLabel("Mật khẩu hiện tại"));
        currentPasswordField = new JPasswordField();
        styleField(currentPasswordField);
        card.add(currentPasswordField);

        card.add(fieldLabel("Mật khẩu mới"));
        newPasswordField = new JPasswordField();
        styleField(newPasswordField);
        card.add(newPasswordField);

        card.add(fieldLabel("Xác nhận mật khẩu mới"));
        confirmPasswordField = new JPasswordField();
        styleField(confirmPasswordField);
        card.add(confirmPasswordField);

        passwordMessage = new JLabel(" ");
        passwordMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        passwordMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordMessage.setBorder(new EmptyBorder(8, 0, 8, 0));
        card.add(passwordMessage);

        JButton changeButton = accentButton("Đổi mật khẩu");
        changeButton.addActionListener(e -> changePassword());
        card.add(changeButton);

        return card;
    }

    private void loadCurrentUser() {
        User user = AuthService.getInstance().getCurrentUser();
        fullNameField.setText(user.getFullName());
        phoneField.setText(user.getPhone());
    }

    private void saveProfile() {
        String fullName = fullNameField.getText().trim();
        String phone = phoneField.getText().trim();

        FormValidator validator = new FormValidator();
        validator.field(fullName)
                .required("Họ tên không được để trống.")
                .maxLength(100, "Họ tên không được vượt quá 100 ký tự.");
        if (!phone.isEmpty()) {
            // So dien thoai la truong tuy chon, nhung neu da nhap thi phai dung dinh dang.
            validator.field(phone)
                    .maxLength(20, "Số điện thoại không được vượt quá 20 ký tự.")
                    .phoneVn("Số điện thoại không hợp lệ (vd: 0912345678 hoặc +84912345678).");
        }

        String error = validator.validate();
        if (error != null) {
            showMessage(infoMessage, error, AppColor.ERROR);
            return;
        }

        User user = AuthService.getInstance().getCurrentUser();
        boolean ok = userDAO.updateProfile(user.getUserId(), fullName, phone);
        if (ok) {
            user.setFullName(fullName);
            user.setPhone(phone);
            showMessage(infoMessage, "Đã lưu thay đổi.", AppColor.SUCCESS);
            if (onSavedListener != null) onSavedListener.run();
        } else {
            showMessage(infoMessage, "Lưu thất bại, vui lòng thử lại.", AppColor.ERROR);
        }
    }

    private void changePassword() {
        String current = new String(currentPasswordField.getPassword());
        String newPass = new String(newPasswordField.getPassword());
        String confirm = new String(confirmPasswordField.getPassword());

        FormValidator validator = new FormValidator();
        validator.field(current).required("Vui lòng nhập đầy đủ thông tin.");
        validator.field(newPass)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .minLength(6, "Mật khẩu mới phải có ít nhất 6 ký tự.")
                .maxLength(64, "Mật khẩu mới không được vượt quá 64 ký tự.")
                .rule(Rules.custom(v -> !v.equals(current), "Mật khẩu mới phải khác mật khẩu hiện tại."));
        validator.field(confirm)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .rule(Rules.equalsTo(newPasswordField::getText, "Xác nhận mật khẩu không khớp."));

        String error = validator.validate();
        if (error != null) {
            showMessage(passwordMessage, error, AppColor.ERROR);
            return;
        }

        User user = AuthService.getInstance().getCurrentUser();
        if (!userDAO.verifyPassword(user.getUserId(), current)) {
            showMessage(passwordMessage, "Mật khẩu hiện tại không đúng.", AppColor.ERROR);
            return;
        }

        boolean ok = userDAO.changePassword(user.getUserId(), newPass);
        if (ok) {
            currentPasswordField.setText("");
            newPasswordField.setText("");
            confirmPasswordField.setText("");
            showMessage(passwordMessage, "Đổi mật khẩu thành công.", AppColor.SUCCESS);
        } else {
            showMessage(passwordMessage, "Đổi mật khẩu thất bại, vui lòng thử lại.", AppColor.ERROR);
        }
    }

    public void onSaved(Runnable listener) {
        this.onSavedListener = listener;
    }

    // ---- UI helpers ----

    private JPanel card() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));
        card.setMaximumSize(new Dimension(440, Integer.MAX_VALUE));
        return card;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 12, 0));
        return label;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(8, 0, 4, 0));
        return label;
    }

    private void styleField(JTextField field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private JButton accentButton(String text) {
        JButton button = new JButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        button.setBackground(AppColor.ACCENT);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(0, 0, 0, 0));
        button.getModel().addChangeListener(e ->
                button.setBackground(button.getModel().isRollover() ? AppColor.ACCENT_HOVER : AppColor.ACCENT));
        return button;
    }

    private void showMessage(JLabel label, String text, Color color) {
        label.setForeground(color);
        label.setText("<html>" + text + "</html>");
    }
}