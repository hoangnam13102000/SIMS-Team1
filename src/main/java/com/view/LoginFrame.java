package com.view;

import com.components.auth.AuthLeftPanel;
import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.components.common.RoundedPasswordField;
import com.components.common.SquareCheckIcon;
import com.dao.UserDAO;
import com.model.Role;
import com.model.User;
import com.service.AuthService;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import com.utils.RememberMeUtil;
import com.view.admin.AdminMainFrame;
import com.view.client.ClientMainFrame;
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
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.i18n.Lang;
import com.model.ActivityLog;
public class LoginFrame extends JFrame {

   
    private RoundedField usernameField;
    private RoundedPasswordField passwordField;
    private JCheckBox rememberCheckBox;
    private JLabel errorLabel;
    private PrimaryButton loginButton;

    private final UserDAO userDAO = new UserDAO();

    public LoginFrame() {
        setTitle(AppConstant.APP_TITLE_LOGIN);
        setSize(1000, 620);
        setMinimumSize(new Dimension(860, 560));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new GridBagLayout());

        String[] features = {
            Lang.get("login.leftpanel.feature1"),
            Lang.get("login.leftpanel.feature2"),
            Lang.get("login.leftpanel.feature3")
        };

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.42;
        add(new AuthLeftPanel(
            Lang.get("login.leftpanel.brand"),
            Lang.get("login.leftpanel.tagline"),
            features
        ), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.58;
        add(buildLoginForm(), gbc);

        setVisible(true);
    }

    private JPanel buildLoginForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColor.WHITE);

        JPanel form = new JPanel();
        form.setOpaque(false);
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        // Title
        JLabel title = new JLabel(Lang.get("login.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel(Lang.get("login.subtitle"));
        subtitle.setFont(AppFont.BODY);
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 30, 0));

        // Username - Dùng RoundedField (JTextField)
        usernameField = new RoundedField(null);
        String rememberedUsername = RememberMeUtil.getRememberedUsername();
        if (rememberedUsername != null) {
            usernameField.setText(rememberedUsername);
        }

        // Password - Dùng RoundedPasswordField (JPasswordField) với Eye Toggle
        passwordField = new RoundedPasswordField(createEyeToggle());
        passwordField.getPasswordField().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doLogin();
                }
            }
        });

        // Remember me
        rememberCheckBox = new JCheckBox(Lang.get("login.rememberMe"));
        rememberCheckBox.setSelected(rememberedUsername != null);
        rememberCheckBox.setOpaque(false);
        rememberCheckBox.setFont(AppFont.SMALL);
        rememberCheckBox.setForeground(AppColor.TEXT_MUTED);
        rememberCheckBox.setFocusPainted(false);
        rememberCheckBox.setIconTextGap(8);
        rememberCheckBox.setIcon(new SquareCheckIcon());

        JLabel forgot = new JLabel(Lang.get("login.forgotPassword"));
        forgot.setFont(AppFont.SMALL);
        forgot.setForeground(AppColor.ACCENT);
        forgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        forgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                ForgotPasswordDialog dialog = new ForgotPasswordDialog(
                        LoginFrame.this,
                        usernameField.getText()
                );
                dialog.setVisible(true);
            }
        });

        JPanel optionsRow = new JPanel(new BorderLayout());
        optionsRow.setOpaque(false);
        optionsRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        optionsRow.setMaximumSize(new Dimension(AppConstant.FIELD_WIDTH, Integer.MAX_VALUE));
        optionsRow.setBorder(new EmptyBorder(10, 0, 6, 0));
        optionsRow.add(rememberCheckBox, BorderLayout.WEST);
        optionsRow.add(forgot, BorderLayout.EAST);

        // Error label
        errorLabel = new JLabel(" ");
        errorLabel.setFont(AppFont.SMALL);
        errorLabel.setForeground(AppColor.ERROR);
        errorLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        errorLabel.setBorder(new EmptyBorder(2, 0, 8, 0));

        // Login button
        loginButton = new PrimaryButton(Lang.get("login.button"));
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.addActionListener(e -> doLogin());

        // Register link
        JPanel registerRow = createLinkRow(Lang.get("login.noAccount"), Lang.get("login.registerNow"), () -> {
            dispose();
            new RegisterFrame();
        });

        // Add all to form
        form.add(title);
        form.add(subtitle);
        form.add(createLabel(Lang.get("login.label.username")));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(16));
        form.add(createLabel(Lang.get("login.label.password")));
        form.add(passwordField);
        form.add(optionsRow);
        form.add(errorLabel);
        form.add(loginButton);
        form.add(Box.createVerticalStrut(12));
        form.add(registerRow);

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
     * Tạo nút toggle hiện/ẩn mật khẩu
     * Lấy trực tiếp từ RoundedPasswordField
     */
    private JComponent createEyeToggle() {
        final Icon eyeOpenIcon = createEyeIcon(FontAwesomeSolid.EYE);
        final Icon eyeClosedIcon = createEyeIcon(FontAwesomeSolid.EYE_SLASH);
        
        JLabel toggle = new JLabel(eyeOpenIcon);
        toggle.setVerticalAlignment(SwingConstants.CENTER);
        toggle.setToolTipText(Lang.get("login.password.show"));
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.addMouseListener(new MouseAdapter() {
            private boolean isShowing = false;
            
            @Override
            public void mouseClicked(MouseEvent e) {
               
                JPasswordField pf = passwordField.getPasswordField();
                isShowing = !isShowing;
                
                if (isShowing) {
                    pf.setEchoChar((char) 0);
                    toggle.setIcon(eyeClosedIcon);
                    toggle.setToolTipText(Lang.get("login.password.hide"));
                } else {
                    pf.setEchoChar('●');
                    toggle.setIcon(eyeOpenIcon);
                    toggle.setToolTipText(Lang.get("login.password.show"));
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

    private void doLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            errorLabel.setText(Lang.get("login.error.emptyFields"));
            return;
        }

        loginButton.setEnabled(false);
        errorLabel.setText(" ");

        SwingWorker<User, Void> worker = new SwingWorker<User, Void>() {
            @Override
            protected User doInBackground() {
                return userDAO.login(username, password);
            }

            @Override
            protected void done() {
                loginButton.setEnabled(true);
                try {
                	User user = get();
                	if (user == null) {
                	    User existing = userDAO.findByUsername(username);
                	    if (existing != null && existing.isLocked()) {
                	        errorLabel.setText(Lang.get("login.error.locked"));
                	    } else if (existing != null && existing.isDisabled()) {
                	        errorLabel.setText(Lang.get("login.error.disabled"));
                	    } else {
                	        errorLabel.setText(Lang.get("login.error.wrongCredentials"));
                	    }
                	    AppLogger.getInstance().log(username, ActivityLog.ACTION_LOGIN_FAILED,
                	            ActivityLog.ENTITY_USER, "Đăng nhập thất bại với tên đăng nhập \"" + username + "\"");
                	    return;
                	}
                    AuthService.getInstance().setCurrentUser(user);
                    AppLogger.getInstance().log(user.getUsername(), ActivityLog.ACTION_LOGIN,
                            ActivityLog.ENTITY_USER, user.getFullName() + " đã đăng nhập");
                    if (rememberCheckBox.isSelected()) {
                        RememberMeUtil.remember(username);
                    } else {
                        RememberMeUtil.forget();
                    }

                    dispose();
                    if (user.getRole() != Role.CUSTOMER) {
                        new AdminMainFrame();
                    } else {
                        new ClientMainFrame();
                    }
                } catch (Exception ex) {
                    AppLogger.getInstance().error(ErrorCode.AUTH_LOGIN_FAIL, "LoginFrame - dang nhap " + username, ex);
                    // In ra console de de debug: AppLogger.error() chi ghi vao
                    // CSDL (ActivityLogs) qua LogSink, KHONG tu in ra console -
                    // neu khong co dong nay, exception xay ra luc khoi tao
                    // AdminMainFrame()/ClientMainFrame() (SAU khi LoginFrame da
                    // dispose()) se hoan toan "bien mat" khong ai nhin thay duoc.
                    ex.printStackTrace();
                    // LoginFrame da dispose() truoc do roi (xem tren) nen
                    // errorLabel.setText() luc nay vo ich (khong ai thay duoc
                    // nua) - phai dung dialog rieng (owner=null) de nguoi dung
                    // it nhat con thay duoc co loi xay ra, thay vi khong co
                    // cua so nao hien len ca.
                    JOptionPane.showMessageDialog(null,
                            Lang.get("login.error.startupFailed"),
                            Lang.get("login.error.startupFailed.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    void prepareAfterPasswordReset(String username) {
        usernameField.setText(username == null ? "" : username);
        passwordField.setText("");
        errorLabel.setText(" ");
        setVisible(true);
        toFront();
        SwingUtilities.invokeLater(
                () -> passwordField.getPasswordField().requestFocusInWindow());
    }
}