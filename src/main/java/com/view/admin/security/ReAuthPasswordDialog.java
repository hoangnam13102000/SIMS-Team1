package com.view.admin.security;

import com.dao.UserDAO;
import com.i18n.Lang;
import com.model.User;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.view.forgotpassword.WizardWidgets;
import com.components.common.PrimaryButton;
import com.components.common.RoundedPasswordField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * Yeu cau nhap lai mat khau hien tai truoc khi cho phep doi/tat 2FA - tranh
 * truong hop 1 phien dang nhap dang mo bi loi dung de vo hieu hoa 2FA.
 */
public class ReAuthPasswordDialog extends JDialog {

    private final UserDAO userDAO = new UserDAO();
    private final User user;
    private boolean confirmed = false;

    private RoundedPasswordField passwordField;
    private JLabel messageLabel;

    public ReAuthPasswordDialog(Window owner, User user) {
        super(owner, Lang.get("twofa.reauth.title"), ModalityType.APPLICATION_MODAL);
        this.user = user;

        setResizable(false);
        setSize(440, 300);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private JComponent buildContent() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL));

        JLabel title = new JLabel(Lang.get("twofa.reauth.subtitle"));
        title.setFont(AppFont.BODY);
        title.setForeground(AppColor.TEXT_MUTED);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        passwordField = WizardWidgets.createPasswordField();
        WizardWidgets.putPasswordPlaceholder(passwordField, Lang.get("twofa.reauth.placeholder"));
        panel.add(WizardWidgets.fieldGroup(Lang.get("login.label.password"), passwordField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        messageLabel = WizardWidgets.createMessageLabel();
        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        PrimaryButton confirmButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.reauth.confirm"));
        confirmButton.addActionListener(e -> doVerify());
        panel.add(confirmButton);
        getRootPane().setDefaultButton(confirmButton);

        panel.add(Box.createVerticalStrut(AppSpacing.SM));
        JButton cancelButton = WizardWidgets.createLinkButton(Lang.get("twofa.verify.cancel"));
        cancelButton.addActionListener(e -> dispose());
        panel.add(WizardWidgets.centeredRow(cancelButton));

        passwordField.getPasswordField().addActionListener(e -> doVerify());
        return panel;
    }

    private void doVerify() {
        String rawPassword = new String(passwordField.getPasswordField().getPassword());
        if (rawPassword.isEmpty()) {
            WizardWidgets.showMessage(messageLabel, Lang.get("login.error.emptyFields"), AppColor.ERROR);
            return;
        }
        if (userDAO.verifyPassword(user.getUserId(), rawPassword)) {
            confirmed = true;
            dispose();
        } else {
            WizardWidgets.showMessage(messageLabel, Lang.get("twofa.reauth.wrongPassword"), AppColor.ERROR);
        }
    }
}