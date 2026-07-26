package com.view;

import com.components.auth.RegisterLeftPanel;
import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.components.common.RoundedPasswordField;
import com.validation.FormValidator;
import com.validation.Rules;
import com.dao.UserDAO;
import com.model.Role;
import com.model.User;
import com.service.OtpService;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class RegisterFrame extends JFrame {

    private static final String GMAIL_REGEX = "^[\\w.+-]+@gmail\\.com$";

    // ✅ Dùng RoundedField cho email (JTextField)
    private RoundedField emailField;
    // ✅ Dùng RoundedField cho username (JTextField)
    private RoundedField usernameField;
    // ✅ Dùng RoundedPasswordField cho password (JPasswordField)
    private RoundedPasswordField passwordField;
    // ✅ Dùng RoundedPasswordField cho confirm password (JPasswordField)
    private RoundedPasswordField confirmPasswordField;
    private JLabel messageLabel;
    private JLabel confirmMessageLabel;
    private PrimaryButton registerButton;
    private RegisterLeftPanel leftPanel;

    private final UserDAO userDAO = new UserDAO();
    private final OtpService otpService = new OtpService();

    public RegisterFrame() {
        setTitle("Đăng ký tài khoản - " + AppConstant.APP_NAME);
        setSize(1000, 620);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.42;
        leftPanel = new RegisterLeftPanel();
        add(leftPanel, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.58;
        add(buildRegisterForm(), gbc);

        setVisible(true);
    }

    private JPanel buildRegisterForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColor.WHITE);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // Title
        JLabel title = new JLabel("Tạo tài khoản mới");
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Đăng ký tài khoản khách hàng để bắt đầu mua sắm");
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 30, 0));

        // Email - Dùng RoundedField (JTextField)
        emailField = new RoundedField(null);

        // Username - Dùng RoundedField (JTextField)
        usernameField = new RoundedField(null);

        // Password - Dùng RoundedPasswordField (JPasswordField)
        passwordField = new RoundedPasswordField(createEyeToggleForPassword());
        passwordField.getPasswordField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                String password = passwordField.getText();
                leftPanel.updatePasswordStrength(password);
                checkPasswordMatch();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doRegister();
                }
            }
        });

        JLabel hint = new JLabel("Mật khẩu cần có ít nhất 6 ký tự");
        hint.setFont(AppFont.SMALL);
        hint.setForeground(AppColor.TEXT_MUTED);
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        hint.setBorder(new EmptyBorder(6, 0, 0, 0));

        // Confirm Password - Dùng RoundedPasswordField (JPasswordField)
        confirmPasswordField = new RoundedPasswordField(createEyeToggleForConfirm());
        confirmPasswordField.getPasswordField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                checkPasswordMatch();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doRegister();
                }
            }
        });

        JLabel confirmHint = new JLabel("Nhập lại mật khẩu để xác nhận");
        confirmHint.setFont(AppFont.SMALL);
        confirmHint.setForeground(AppColor.TEXT_MUTED);
        confirmHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmHint.setBorder(new EmptyBorder(6, 0, 0, 0));

        confirmMessageLabel = new JLabel(" ");
        confirmMessageLabel.setFont(AppFont.SMALL);
        confirmMessageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmMessageLabel.setBorder(new EmptyBorder(4, 0, 0, 0));

        messageLabel = new JLabel(" ");
        messageLabel.setFont(AppFont.SMALL);
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageLabel.setBorder(new EmptyBorder(12, 0, 10, 0));

        registerButton = new PrimaryButton("Đăng ký");
        registerButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        registerButton.addActionListener(e -> doRegister());

        JPanel loginRow = createLinkRow("Đã có tài khoản?", " Đăng nhập", () -> {
            dispose();
            new LoginFrame();
        });

        // Add all to form
        form.add(title);
        form.add(subtitle);

        form.add(createLabel("Gmail *"));
        form.add(emailField);
        form.add(Box.createVerticalStrut(14));

        form.add(createLabel("Tên đăng nhập *"));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(14));

        form.add(createLabel("Mật khẩu *"));
        form.add(passwordField);
        form.add(hint);
        form.add(Box.createVerticalStrut(8));

        form.add(createLabel("Xác nhận mật khẩu *"));
        form.add(confirmPasswordField);
        form.add(confirmHint);
        form.add(confirmMessageLabel);

        form.add(messageLabel);
        form.add(registerButton);
        form.add(Box.createVerticalStrut(12));
        form.add(loginRow);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(form, gbc);

        return panel;
    }

    private JLabel createLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(AppFont.LABEL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 6, 0));
        return label;
    }

    private JPanel createLinkRow(String prefix, String linkText, Runnable action) {
        JLabel prefixLabel = new JLabel(prefix);
        prefixLabel.setFont(AppFont.SMALL);
        prefixLabel.setForeground(AppColor.TEXT_MUTED);

        JLabel linkLabel = new JLabel(linkText);
        linkLabel.setFont(AppFont.SMALL_BOLD);
        linkLabel.setForeground(AppColor.ACCENT);
        linkLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        linkLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                action.run();
            }
        });

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(AppConstant.FIELD_WIDTH, 24));
        row.add(prefixLabel);
        row.add(linkLabel);
        return row;
    }

    /**
     * Tạo nút toggle hiện/ẩn mật khẩu cho password field
     */
    private JComponent createEyeToggleForPassword() {
        final Icon eyeOpenIcon = createEyeIcon(FontAwesomeSolid.EYE);
        final Icon eyeClosedIcon = createEyeIcon(FontAwesomeSolid.EYE_SLASH);

        JLabel toggle = new JLabel(eyeOpenIcon);
        toggle.setVerticalAlignment(SwingConstants.CENTER);
        toggle.setToolTipText("Hiện mật khẩu");
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.addMouseListener(new MouseAdapter() {
            private boolean isShowing = false;

            @Override
            public void mouseClicked(MouseEvent e) {
                // ✅ Lấy trực tiếp từ RoundedPasswordField
                JPasswordField pf = passwordField.getPasswordField();
                isShowing = !isShowing;

                if (isShowing) {
                    pf.setEchoChar((char) 0);
                    toggle.setIcon(eyeClosedIcon);
                    toggle.setToolTipText("Ẩn mật khẩu");
                } else {
                    pf.setEchoChar('●');
                    toggle.setIcon(eyeOpenIcon);
                    toggle.setToolTipText("Hiện mật khẩu");
                }
                pf.requestFocus();
            }
        });
        return toggle;
    }

    /**
     * Tạo nút toggle hiện/ẩn mật khẩu cho confirm password field
     */
    private JComponent createEyeToggleForConfirm() {
        final Icon eyeOpenIcon = createEyeIcon(FontAwesomeSolid.EYE);
        final Icon eyeClosedIcon = createEyeIcon(FontAwesomeSolid.EYE_SLASH);

        JLabel toggle = new JLabel(eyeOpenIcon);
        toggle.setVerticalAlignment(SwingConstants.CENTER);
        toggle.setToolTipText("Hiện mật khẩu");
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.addMouseListener(new MouseAdapter() {
            private boolean isShowing = false;

            @Override
            public void mouseClicked(MouseEvent e) {
                // ✅ Lấy trực tiếp từ RoundedPasswordField
                JPasswordField pf = confirmPasswordField.getPasswordField();
                isShowing = !isShowing;

                if (isShowing) {
                    pf.setEchoChar((char) 0);
                    toggle.setIcon(eyeClosedIcon);
                    toggle.setToolTipText("Ẩn mật khẩu");
                } else {
                    pf.setEchoChar('●');
                    toggle.setIcon(eyeOpenIcon);
                    toggle.setToolTipText("Hiện mật khẩu");
                }
                pf.requestFocus();
            }
        });
        return toggle;
    }

    /**
     * Tạo icon con mắt từ Ikonli (FontAwesome5 Solid), đồng bộ màu với theme
     */
    private Icon createEyeIcon(Ikon iconCode) {
        FontIcon icon = FontIcon.of(iconCode, 18);
        icon.setIconColor(AppColor.ACCENT);
        return icon;
    }

    private void checkPasswordMatch() {
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        if (confirm.isEmpty()) {
            confirmMessageLabel.setText(" ");
            return;
        }

        if (password.equals(confirm)) {
            confirmMessageLabel.setText("✓ Mật khẩu khớp");
            confirmMessageLabel.setForeground(AppColor.GREEN);
        } else {
            confirmMessageLabel.setText("✗ Mật khẩu không khớp");
            confirmMessageLabel.setForeground(AppColor.RED_ALT);
        }
    }

    private void doRegister() {
        String email = emailField.getText().trim();
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        FormValidator validator = new FormValidator();
        validator.field(email)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .matches(GMAIL_REGEX, "Vui lòng nhập địa chỉ Gmail hợp lệ (vd: ten@gmail.com).")
                .rule(Rules.custom(v -> !userDAO.emailExists(v), "Gmail này đã được dùng để đăng ký tài khoản khác."));
        validator.field(username)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .rule(Rules.custom(v -> !userDAO.usernameExists(v), "Tên đăng nhập đã tồn tại, vui lòng chọn tên khác."));
        validator.field(password)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .minLength(6, "Mật khẩu phải có ít nhất 6 ký tự.");
        validator.field(confirm)
                .required("Vui lòng nhập đầy đủ thông tin.")
                .rule(Rules.equalsTo(passwordField::getText, "Mật khẩu xác nhận không khớp."));

        String error = validator.validate();
        if (error != null) {
            showMessage(error, AppColor.ERROR);
            return;
        }

        registerButton.setEnabled(false);
        showMessage("Đang gửi mã OTP tới " + email + " ...", AppColor.TEXT_MUTED);

        new SwingWorker<Void, Void>() {
            private Exception error;

            @Override
            protected Void doInBackground() {
                try {
                    otpService.sendOtp(email);
                } catch (Exception e) {
                    error = e;
                }
                return null;
            }

            @Override
            protected void done() {
                registerButton.setEnabled(true);
                if (error != null) {
                    showMessage("Gửi OTP thất bại: " + error.getMessage(), AppColor.ERROR);
                    return;
                }
                showMessage(" ", AppColor.SUCCESS);
                openOtpDialogAndFinish(email, username, password);
            }
        }.execute();
    }

    private void openOtpDialogAndFinish(String email, String username, String password) {
        OtpVerifyDialog otpDialog = new OtpVerifyDialog(this, otpService, email);
        otpDialog.setVisible(true);

        if (!otpDialog.isConfirmed()) {
            showMessage("Bạn chưa xác nhận OTP, đăng ký bị hủy.", AppColor.ERROR);
            return;
        }

        User newUser = new User();
        newUser.setUsername(username);
        newUser.setFullName(username);
        newUser.setEmail(email);
        newUser.setPhone("");
        

        boolean ok = userDAO.register(newUser, password);
        if (ok) {
            showMessage("Đăng ký thành công! Bạn có thể đăng nhập ngay.", AppColor.SUCCESS);
        } else {
            showMessage("Đăng ký thất bại, vui lòng thử lại.", AppColor.ERROR);
        }
    }

    private void showMessage(String text, Color color) {
        messageLabel.setForeground(color);
        messageLabel.setText("<html>" + text + "</html>");
    }
}