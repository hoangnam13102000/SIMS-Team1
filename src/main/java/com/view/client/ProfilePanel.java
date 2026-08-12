package com.view.client;


import com.dao.CustomerDAO;
import com.model.Customer;
import com.model.Role;
import com.theme.AppColor;
import com.dao.UserDAO;
import com.model.User;
import com.service.AuthService;
import com.utils.BarcodeUtil;
import com.utils.FileUtil;
import com.utils.ImageUtil;
import com.validation.FormValidator;
import com.validation.Rules;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.time.format.DateTimeFormatter;

public class ProfilePanel extends JPanel {

	private static final int AVATAR_SIZE = 120;
	private static final int SIDE_CARD_WIDTH = 300;
	private static final int BARCODE_WIDTH = 240;
	private static final int BARCODE_HEIGHT = 60;

    private final UserDAO userDAO = new UserDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private Runnable onSavedListener;

    private JLabel avatarLabel;
    private JLabel nameLabel;
    private JLabel rolePillLabel;
    private JLabel emailValueLabel;
    private JLabel phoneValueLabel;
    private JLabel joinedLabel;
    private JLabel avatarMessage;
    private JPanel barcodeCard;
    private JLabel barcodeValueLabel;

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

        JLabel title = new JLabel("Trang cá nhân");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setBorder(new EmptyBorder(0, 0, 16, 0));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0;
        gbc.weighty = 0;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 0, 20);
        content.add(buildSideCard(), gbc);

        JPanel rightColumn = new JPanel();
        rightColumn.setOpaque(false);
        rightColumn.setLayout(new BoxLayout(rightColumn, BoxLayout.Y_AXIS));
        rightColumn.add(buildInfoFormCard());
        rightColumn.add(Box.createVerticalStrut(20));
        rightColumn.add(buildPasswordCard());

        gbc.gridx = 1;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(0, 0, 0, 0);
        content.add(rightColumn, gbc);

        JPanel northWrapper = new JPanel();
        northWrapper.setOpaque(false);
        northWrapper.setLayout(new BoxLayout(northWrapper, BoxLayout.Y_AXIS));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        northWrapper.add(title);
        northWrapper.add(content);

        JScrollPane scrollPane = new JScrollPane(northWrapper);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        add(scrollPane, BorderLayout.CENTER);
        loadCurrentUser();
    }

    private JPanel buildSideCard() {
        JPanel card = sideCard();

        JPanel avatarWrapper = new JPanel(null);
        avatarWrapper.setOpaque(false);
        Dimension avatarWrapperSize = new Dimension(AVATAR_SIZE + 20, AVATAR_SIZE + 20);
        avatarWrapper.setPreferredSize(avatarWrapperSize);
        avatarWrapper.setMaximumSize(avatarWrapperSize);
        avatarWrapper.setMinimumSize(avatarWrapperSize);

        avatarLabel = new JLabel();
        avatarLabel.setBounds(2, 2, AVATAR_SIZE, AVATAR_SIZE);
        avatarWrapper.add(avatarLabel);

        JButton cameraButton = circularIconButton(FontAwesomeSolid.CAMERA);
        cameraButton.setBounds(AVATAR_SIZE - 22, AVATAR_SIZE - 22, 34, 34);
        cameraButton.setToolTipText("Đổi ảnh đại diện");
        cameraButton.addActionListener(e -> chooseAndUploadAvatar());
        avatarWrapper.add(cameraButton);

        JPanel avatarRow = new JPanel();
        avatarRow.setOpaque(false);
        avatarRow.setLayout(new BoxLayout(avatarRow, BoxLayout.X_AXIS));
        avatarRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarRow.add(Box.createHorizontalGlue());
        avatarRow.add(avatarWrapper);
        avatarRow.add(Box.createHorizontalGlue());
        card.add(avatarRow);

        card.add(Box.createVerticalStrut(14));

        nameLabel = new JLabel(" ");
        nameLabel.setFont(new Font("Segoe UI", Font.BOLD, 17));
        nameLabel.setForeground(AppColor.TEXT_PRIMARY);
        nameLabel.setHorizontalAlignment(SwingConstants.CENTER);
        nameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel nameRow = new JPanel();
        nameRow.setOpaque(false);
        nameRow.setLayout(new BoxLayout(nameRow, BoxLayout.X_AXIS));
        nameRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        nameRow.add(Box.createHorizontalGlue());
        nameRow.add(nameLabel);
        nameRow.add(Box.createHorizontalGlue());
        card.add(nameRow);

        card.add(Box.createVerticalStrut(8));

        rolePillLabel = new JLabel(" ");
        rolePillLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rolePillLabel.setForeground(AppColor.ACCENT);
        rolePillLabel.setOpaque(false);
        rolePillLabel.setBorder(new EmptyBorder(4, 12, 4, 12));
        JPanel pill = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.ACCENT_BG_SOFT);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);
        pill.setMaximumSize(new Dimension(200, 26));
        pill.add(rolePillLabel, BorderLayout.CENTER);

        JPanel pillRow = new JPanel();
        pillRow.setOpaque(false);
        pillRow.setLayout(new BoxLayout(pillRow, BoxLayout.X_AXIS));
        pillRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        pillRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
        pillRow.add(Box.createHorizontalGlue());
        pillRow.add(pill);
        pillRow.add(Box.createHorizontalGlue());
        card.add(pillRow);

        avatarMessage = new JLabel(" ");
        avatarMessage.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        avatarMessage.setAlignmentX(Component.CENTER_ALIGNMENT);
        avatarMessage.setBorder(new EmptyBorder(8, 0, 0, 0));
        card.add(avatarMessage);

        card.add(Box.createVerticalStrut(16));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(16));

        emailValueLabel = valueLabel();
        card.add(infoRow(FontAwesomeSolid.ENVELOPE, "Email", emailValueLabel));
        card.add(Box.createVerticalStrut(14));

        phoneValueLabel = valueLabel();
        card.add(infoRow(FontAwesomeSolid.PHONE, "Số điện thoại", phoneValueLabel));

        card.add(Box.createVerticalStrut(16));

        joinedLabel = new JLabel(" ");
        joinedLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        joinedLabel.setForeground(AppColor.TEXT_MUTED);
        joinedLabel.setHorizontalAlignment(SwingConstants.CENTER);
        joinedLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel joinedRow = new JPanel();
        joinedRow.setOpaque(false);
        joinedRow.setLayout(new BoxLayout(joinedRow, BoxLayout.X_AXIS));
        joinedRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        joinedRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        joinedRow.add(Box.createHorizontalGlue());
        joinedRow.add(joinedLabel);
        joinedRow.add(Box.createHorizontalGlue());
        card.add(joinedRow);

        // --- Mã vạch thành viên ---
        card.add(Box.createVerticalStrut(16));
        card.add(buildDivider());
        card.add(Box.createVerticalStrut(12));

        JLabel barcodeTitle = new JLabel("Mã vạch thành viên");
        barcodeTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        barcodeTitle.setForeground(AppColor.TEXT_MUTED);
        barcodeTitle.setHorizontalAlignment(SwingConstants.CENTER);
        barcodeTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel barcodeTitleRow = new JPanel();
        barcodeTitleRow.setOpaque(false);
        barcodeTitleRow.setLayout(new BoxLayout(barcodeTitleRow, BoxLayout.X_AXIS));
        barcodeTitleRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        barcodeTitleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        barcodeTitleRow.add(Box.createHorizontalGlue());
        barcodeTitleRow.add(barcodeTitle);
        barcodeTitleRow.add(Box.createHorizontalGlue());
        card.add(barcodeTitleRow);

        card.add(Box.createVerticalStrut(8));

        barcodeCard = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(AppColor.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
            }
        };
        barcodeCard.setOpaque(false);
        barcodeCard.setAlignmentX(Component.CENTER_ALIGNMENT);
        barcodeCard.setMaximumSize(new Dimension(SIDE_CARD_WIDTH - 40, BARCODE_HEIGHT + 24));
        barcodeCard.setBorder(new EmptyBorder(6, 8, 6, 8));

        barcodeValueLabel = new JLabel(" ");
        barcodeValueLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        barcodeValueLabel.setForeground(AppColor.TEXT_PRIMARY);
        barcodeValueLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JPanel barcodeCardRow = new JPanel();
        barcodeCardRow.setOpaque(false);
        barcodeCardRow.setLayout(new BoxLayout(barcodeCardRow, BoxLayout.X_AXIS));
        barcodeCardRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        barcodeCardRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, BARCODE_HEIGHT + 30));
        barcodeCardRow.add(Box.createHorizontalGlue());
        barcodeCardRow.add(barcodeCard);
        barcodeCardRow.add(Box.createHorizontalGlue());
        card.add(barcodeCardRow);

        return card;
    }

    private JPanel infoRow(FontAwesomeSolid iconType, String label, JLabel value) {
        final int contentW = SIDE_CARD_WIDTH - 40;

        JPanel row = new JPanel(new BorderLayout(10, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setPreferredSize(new Dimension(contentW, 44));
        row.setMaximumSize(new Dimension(contentW, 44));
        row.setMinimumSize(new Dimension(contentW, 44));

        JPanel iconBox = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(AppColor.BG_LIGHTER);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(36, 36));
        iconBox.setMinimumSize(new Dimension(36, 36));
        iconBox.setMaximumSize(new Dimension(36, 36));
        FontIcon icon = FontIcon.of(iconType, 14);
        icon.setIconColor(AppColor.TEXT_MUTED);
        iconBox.add(new JLabel(icon));
        row.add(iconBox, BorderLayout.WEST);

        JPanel textPanel = new JPanel(new BorderLayout());
        textPanel.setOpaque(false);

        JLabel labelText = new JLabel(label);
        labelText.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        labelText.setForeground(AppColor.TEXT_MUTED);

        value.setHorizontalAlignment(SwingConstants.LEFT);

        JPanel stacked = new JPanel();
        stacked.setOpaque(false);
        stacked.setLayout(new BoxLayout(stacked, BoxLayout.Y_AXIS));
        labelText.setAlignmentX(Component.LEFT_ALIGNMENT);
        value.setAlignmentX(Component.LEFT_ALIGNMENT);
        stacked.add(labelText);
        stacked.add(Box.createVerticalStrut(2));
        stacked.add(value);

        textPanel.add(stacked, BorderLayout.WEST);
        row.add(textPanel, BorderLayout.CENTER);

        return row;
    }

    private JLabel valueLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(new Font("Segoe UI", Font.BOLD, 13));
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void chooseAndUploadAvatar() {
        File selected = FileUtil.chooseImageFile(this);
        if (selected == null) return;

        if (!FileUtil.isWithinSizeLimit(selected, 5)) {
            showMessage(avatarMessage, "Ảnh vượt quá 5MB, vui lòng chọn ảnh khác.", AppColor.ERROR);
            return;
        }

        File saved = FileUtil.copyToDirectory(selected, "uploads/avatars");
        if (saved == null) {
            showMessage(avatarMessage, "Tải ảnh lên thất bại, vui lòng thử lại.", AppColor.ERROR);
            return;
        }

        User user = AuthService.getInstance().getCurrentUser();
        boolean ok = userDAO.updateAvatar(user.getUserId(), saved.getPath());
        if (ok) {
            user.setAvatarUrl(saved.getPath());
            avatarLabel.setIcon(ImageUtil.circularIcon(saved.getPath(), AVATAR_SIZE, user.getFullName()));
            showMessage(avatarMessage, "Đã cập nhật ảnh đại diện.", AppColor.SUCCESS);
            if (onSavedListener != null) onSavedListener.run();
        } else {
            showMessage(avatarMessage, "Lưu ảnh đại diện thất bại, vui lòng thử lại.", AppColor.ERROR);
        }
    }

    private JPanel buildInfoFormCard() {
        JPanel card = card();
        card.add(cardTitle("Thông tin cá nhân"));

        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        fullNameField = new JTextField();
        styleField(fullNameField);
        row.add(fieldGroup("Họ và tên", fullNameField));

        phoneField = new JTextField();
        styleField(phoneField);
        row.add(fieldGroup("Số điện thoại", phoneField));

        card.add(row);
        card.add(Box.createVerticalStrut(8));

        card.add(fieldLabel("Email"));
        JTextField emailField = new JTextField(AuthService.getInstance().getCurrentUser().getEmail());
        emailField.setEditable(false);
        styleField(emailField);
        card.add(emailField);

        JLabel emailHint = new JLabel("Email đăng nhập không thể thay đổi tại đây.");
        emailHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        emailHint.setForeground(AppColor.TEXT_MUTED);
        emailHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        emailHint.setBorder(new EmptyBorder(4, 0, 0, 0));
        card.add(emailHint);

        infoMessage = new JLabel(" ");
        infoMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoMessage.setBorder(new EmptyBorder(10, 0, 8, 0));
        card.add(infoMessage);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JButton saveButton = accentButton("Lưu thay đổi", FontAwesomeSolid.CHECK);
        saveButton.addActionListener(e -> saveProfile());
        buttonRow.add(saveButton);
        card.add(buttonRow);

        return card;
    }

    private void saveProfile() {
        String fullName = fullNameField.getText().trim();
        String phone = phoneField.getText().trim();

        FormValidator validator = new FormValidator();
        validator.field(fullName)
                .required("Họ tên không được để trống.")
                .maxLength(100, "Họ tên không được vượt quá 100 ký tự.");
        if (!phone.isEmpty()) {
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
            refreshSideCard(user);
            showMessage(infoMessage, "Đã lưu thay đổi.", AppColor.SUCCESS);
            if (onSavedListener != null) onSavedListener.run();
        } else {
            showMessage(infoMessage, "Lưu thất bại, vui lòng thử lại.", AppColor.ERROR);
        }
    }

    private JPanel buildPasswordCard() {
        JPanel card = card();
        card.add(cardTitle("Đổi mật khẩu"));

        card.add(fieldLabel("Mật khẩu hiện tại"));
        currentPasswordField = new JPasswordField();
        card.add(passwordFieldWithToggle(currentPasswordField));

        card.add(Box.createVerticalStrut(8));

        JPanel row = new JPanel(new GridLayout(1, 2, 16, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));

        newPasswordField = new JPasswordField();
        row.add(fieldGroup("Mật khẩu mới", passwordFieldWithToggle(newPasswordField)));

        confirmPasswordField = new JPasswordField();
        row.add(fieldGroup("Xác nhận mật khẩu mới", passwordFieldWithToggle(confirmPasswordField)));

        card.add(row);

        passwordMessage = new JLabel(" ");
        passwordMessage.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        passwordMessage.setAlignmentX(Component.LEFT_ALIGNMENT);
        passwordMessage.setBorder(new EmptyBorder(10, 0, 8, 0));
        card.add(passwordMessage);

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        JButton changeButton = accentButton("Đổi mật khẩu", FontAwesomeSolid.KEY);
        changeButton.addActionListener(e -> changePassword());
        buttonRow.add(changeButton);
        card.add(buttonRow);

        return card;
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

    private void loadCurrentUser() {
        User user = AuthService.getInstance().getCurrentUser();
        fullNameField.setText(user.getFullName());
        phoneField.setText(user.getPhone());
        refreshSideCard(user);
    }

    private void refreshSideCard(User user) {
        nameLabel.setText(user.getFullName());
        rolePillLabel.setText(roleLabel(user.getRole()));
        emailValueLabel.setText(user.getEmail() == null || user.getEmail().isBlank() ? "-" : user.getEmail());
        phoneValueLabel.setText(user.getPhone() == null || user.getPhone().isBlank() ? "-" : user.getPhone());
        if (user.getCreatedAt() != null) {
            joinedLabel.setText("Tham gia từ " + user.getCreatedAt().format(DateTimeFormatter.ofPattern("d/M/yyyy")));
        } else {
            joinedLabel.setText(" ");
        }
        avatarLabel.setIcon(ImageUtil.circularIcon(user.getAvatarUrl(), AVATAR_SIZE, user.getFullName()));

        // Cập nhật mã vạch
        barcodeCard.removeAll();
        if (Role.CUSTOMER.equals(user.getRole())) {
            Customer customer = customerDAO.findById(user.getUserId());
            if (customer != null && customer.getCustomerCode() != null && !customer.getCustomerCode().isBlank()) {
                try {
                    BufferedImage barcodeImage = BarcodeUtil.generateCode128(
                            customer.getCustomerCode(), BARCODE_WIDTH, BARCODE_HEIGHT);
                    JLabel barcodeLabel = new JLabel(new ImageIcon(barcodeImage));
                    barcodeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
                    barcodeCard.add(barcodeLabel, BorderLayout.CENTER);

                    barcodeValueLabel.setText(customer.getCustomerCode());
                    barcodeCard.add(barcodeValueLabel, BorderLayout.SOUTH);
                } catch (Exception e) {
                    JLabel errorLabel = new JLabel("Không thể hiển thị mã vạch");
                    errorLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                    errorLabel.setForeground(AppColor.ERROR);
                    errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
                    barcodeCard.add(errorLabel, BorderLayout.CENTER);
                }
            } else {
                JLabel noCodeLabel = new JLabel("Chưa có mã thành viên");
                noCodeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
                noCodeLabel.setForeground(AppColor.TEXT_MUTED);
                noCodeLabel.setHorizontalAlignment(SwingConstants.CENTER);
                barcodeCard.add(noCodeLabel, BorderLayout.CENTER);
            }
        } else {
            JLabel notCustomerLabel = new JLabel("Chỉ áp dụng cho tài khoản khách hàng");
            notCustomerLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            notCustomerLabel.setForeground(AppColor.TEXT_MUTED);
            notCustomerLabel.setHorizontalAlignment(SwingConstants.CENTER);
            barcodeCard.add(notCustomerLabel, BorderLayout.CENTER);
        }
        barcodeCard.revalidate();
        barcodeCard.repaint();
    }

    private static String roleLabel(Role role) {
        if (role == null) return "";
        switch (role) {
            case ADMIN: return "Quản trị viên";
            case SALES_MANAGER: return "Quản lý bán hàng";
            case INVENTORY_MANAGER: return "Quản lý kho";
            case SALES_STAFF: return "Nhân viên bán hàng";
            case CUSTOMER: return "Khách hàng";
            default: return role.name();
        }
    }

    public void onSaved(Runnable listener) {
        this.onSavedListener = listener;
    }

    private JPanel card() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(20, 20, 20, 20)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        return card;
    }

    private JPanel sideCard() {
        JPanel card = new JPanel() {
            @Override
            public Dimension getPreferredSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(SIDE_CARD_WIDTH, d.height);
            }

            @Override
            public Dimension getMaximumSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(SIDE_CARD_WIDTH, d.height);
            }

            @Override
            public Dimension getMinimumSize() {
                Dimension d = super.getPreferredSize();
                return new Dimension(SIDE_CARD_WIDTH, d.height);
            }
        };
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(24, 20, 20, 20)
        ));
        return card;
    }

    private JLabel cardTitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 15));
        label.setForeground(AppColor.TEXT_PRIMARY);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, 16, 0));
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

    private JPanel fieldGroup(String labelText, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.add(fieldLabel(labelText));
        group.add(field);
        return group;
    }

    private void styleField(JTextField field) {
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(field.getPreferredSize().width, 40));
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(6, 10, 6, 10)
        ));
    }

    private JPanel passwordFieldWithToggle(JPasswordField field) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        field.setBorder(new EmptyBorder(6, 10, 6, 0));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        wrapper.setPreferredSize(new Dimension(wrapper.getPreferredSize().width, 40));
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        wrapper.setBackground(AppColor.WHITE);
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(0, 0, 0, 4)
        ));
        wrapper.add(field, BorderLayout.CENTER);

        char defaultEcho = field.getEchoChar();
        JButton toggle = new JButton(FontIcon.of(FontAwesomeSolid.EYE_SLASH, 14, AppColor.TEXT_MUTED));
        toggle.setContentAreaFilled(false);
        toggle.setBorderPainted(false);
        toggle.setFocusPainted(false);
        toggle.setOpaque(false);
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.setBorder(new EmptyBorder(0, 6, 0, 6));
        toggle.addActionListener(e -> {
            boolean currentlyHidden = field.getEchoChar() != 0;
            field.setEchoChar(currentlyHidden ? (char) 0 : defaultEcho);
            toggle.setIcon(FontIcon.of(currentlyHidden ? FontAwesomeSolid.EYE : FontAwesomeSolid.EYE_SLASH, 14, AppColor.TEXT_MUTED));
        });
        wrapper.add(toggle, BorderLayout.EAST);

        return wrapper;
    }

    private JButton accentButton(String text, FontAwesomeSolid iconType) {
        JButton button = new JButton(text, FontIcon.of(iconType, 13, Color.WHITE));
        button.setIconTextGap(8);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setForeground(Color.WHITE);
        button.setBackground(AppColor.ACCENT);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(9, 18, 9, 18));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.getModel().addChangeListener(e ->
                button.setBackground(button.getModel().isRollover() ? AppColor.ACCENT_HOVER : AppColor.ACCENT));
        return button;
    }

    private JButton circularIconButton(FontAwesomeSolid iconType) {
        JButton button = new JButton(FontIcon.of(iconType, 13, Color.WHITE)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? AppColor.ACCENT_HOVER : AppColor.ACCENT);
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setOpaque(false);
        button.setBorder(new EmptyBorder(0, 0, 0, 0));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JComponent buildDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(AppColor.BORDER);
        sep.setAlignmentX(Component.LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    private void showMessage(JLabel label, String text, Color color) {
        label.setForeground(color);
        label.setText("<html>" + text + "</html>");
    }
}