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
import com.model.ActivityLog;
public class LoginFrame extends JFrame {

    // ✅ Dùng RoundedField cho username (JTextField)
    private RoundedField usernameField;
    // ✅ Dùng RoundedPasswordField cho password (JPasswordField)
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
            "Quản lý kho hàng theo thời gian thực",
            "Thanh toán đa kênh: MoMo, PayPal, tiền mặt",
            "Đồng bộ đơn hàng tức thì qua WebSocket"
        };

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridy = 0;
        gbc.weighty = 1;

        gbc.gridx = 0;
        gbc.weightx = 0.42;
        add(new AuthLeftPanel(
            "Phone Store",
            "Nền tảng quản lý bán hàng dành cho chuỗi cửa hàng điện thoại — vận hành gọn nhẹ, chính xác và cập nhật theo thời gian thực.",
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
        JLabel title = new JLabel("Đăng nhập");
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("Nhập thông tin để truy cập hệ thống quản lý cửa hàng");
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
        rememberCheckBox = new JCheckBox("Ghi nhớ tên đăng nhập");
        rememberCheckBox.setSelected(rememberedUsername != null);
        rememberCheckBox.setOpaque(false);
        rememberCheckBox.setFont(AppFont.SMALL);
        rememberCheckBox.setForeground(AppColor.TEXT_MUTED);
        rememberCheckBox.setFocusPainted(false);
        rememberCheckBox.setIconTextGap(8);
        rememberCheckBox.setIcon(new SquareCheckIcon());

        JLabel forgot = new JLabel("Quên mật khẩu?");
        forgot.setFont(AppFont.SMALL);
        forgot.setForeground(AppColor.ACCENT);
        forgot.setCursor(new Cursor(Cursor.HAND_CURSOR));
        forgot.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                JOptionPane.showMessageDialog(LoginFrame.this,
                        "Vui lòng liên hệ quản trị viên hệ thống để được hỗ trợ đặt lại mật khẩu.",
                        "Quên mật khẩu", JOptionPane.INFORMATION_MESSAGE);
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
        loginButton = new PrimaryButton("Đăng nhập");
        loginButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        loginButton.addActionListener(e -> doLogin());

        // Register link
        JPanel registerRow = createLinkRow("Chưa có tài khoản?", " Đăng ký ngay", () -> {
            dispose();
            new RegisterFrame();
        });

        // Add all to form
        form.add(title);
        form.add(subtitle);
        form.add(createLabel("Tên đăng nhập"));
        form.add(usernameField);
        form.add(Box.createVerticalStrut(16));
        form.add(createLabel("Mật khẩu"));
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
        toggle.setToolTipText("Hiện mật khẩu");
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.addMouseListener(new MouseAdapter() {
            private boolean isShowing = false;
            
            @Override
            public void mouseClicked(MouseEvent e) {
                // ✅ Lấy trực tiếp từ RoundedPasswordField, không cần cast
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
            errorLabel.setText("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu.");
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
                	        errorLabel.setText("Tài khoản đã bị tạm khoá do đăng nhập sai quá 5 lần. Liên hệ Admin.");
                	    } else if (existing != null && existing.isDisabled()) {
                	        errorLabel.setText("Tài khoản đã bị vô hiệu hoá.");
                	    } else {
                	        errorLabel.setText("Sai tên đăng nhập hoặc mật khẩu.");
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
                    if (user.getRole() == Role.ADMIN) {
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
                            "Đăng nhập thành công nhưng không thể mở màn hình chính.\n"
                                    + "Vui lòng kiểm tra console/log để biết chi tiết lỗi, hoặc thử đăng nhập lại.",
                            "Lỗi khởi động", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}