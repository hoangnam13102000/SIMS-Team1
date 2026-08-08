package com.view.forgotpassword;

import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.i18n.Lang;
import com.service.PasswordResetService;
import com.theme.AppColor;
import com.theme.AppSpacing;
import com.validation.FormValidator;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;

/** Buoc 1: nhap username + email de xin cap OTP. */
public class IdentifyStepPanel extends JPanel {

    public interface Listener {
        void onOtpRequestAccepted(PasswordResetService.RequestResult result);
        void onCancel();
    }

    private final PasswordResetFlowController controller;
    private final Listener listener;

    private final RoundedField usernameField;
    private final RoundedField emailField;
    private final JLabel message;
    private final PrimaryButton submitButton;

    public IdentifyStepPanel(String initialUsername, PasswordResetFlowController controller,
                              Listener listener) {
        this.controller = controller;
        this.listener = listener;

        setBackground(AppColor.WHITE);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        WizardWidgets.addHeader(
                this, 1, Lang.get("forgot.identify.title"), Lang.get("forgot.identify.subtitle"));

        usernameField = WizardWidgets.createTextField(Lang.get("forgot.identify.username.placeholder"));
        usernameField.setText(initialUsername == null ? "" : initialUsername.trim());
        emailField = WizardWidgets.createTextField(Lang.get("forgot.identify.email.placeholder"));

        add(WizardWidgets.fieldGroup(Lang.get("forgot.identify.username"), usernameField));
        add(Box.createVerticalStrut(AppSpacing.LG));
        add(WizardWidgets.fieldGroup(Lang.get("forgot.identify.email"), emailField));
        add(Box.createVerticalStrut(AppSpacing.MD));

        message = WizardWidgets.createMessageLabel();
        add(message);
        add(Box.createVerticalStrut(AppSpacing.SM));

        submitButton = WizardWidgets.createPrimaryButton(Lang.get("forgot.identify.submit"));
        submitButton.addActionListener(e -> requestOtp());
        add(submitButton);
        add(Box.createVerticalStrut(AppSpacing.SM));

        JButton backButton = WizardWidgets.createLinkButton(Lang.get("forgot.backToLogin"));
        backButton.addActionListener(e -> listener.onCancel());
        add(WizardWidgets.centeredRow(backButton));

        usernameField.getTextField().addActionListener(e -> emailField.getTextField().requestFocusInWindow());
        emailField.getTextField().addActionListener(e -> requestOtp());
    }

    public PrimaryButton getSubmitButton() {
        return submitButton;
    }

    public String getUsername() {
        return usernameField.getText().trim();
    }

    public void requestInitialFocus() {
        SwingUtilities.invokeLater(() -> usernameField.getTextField().requestFocusInWindow());
    }

    public void showMessage(String text, Color color) {
        WizardWidgets.showMessage(message, text, color);
    }

    private void requestOtp() {
        if (controller.isBusy()) {
            return;
        }
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();

        FormValidator validator = new FormValidator();
        validator.field(username)
                .required(Lang.get("forgot.validation.username.required"))
                .minLength(3, Lang.get("forgot.validation.username.length"))
                .maxLength(50, Lang.get("forgot.validation.username.length"));
        validator.field(email)
                .required(Lang.get("forgot.validation.email.required"))
                .email(Lang.get("forgot.validation.email.invalid"));
        String error = validator.validate();
        if (error != null) {
            showMessage(error, AppColor.ERROR);
            return;
        }

        submitButton.setEnabled(false);
        submitButton.setText(Lang.get("forgot.identify.submitting"));
        showMessage(Lang.get("forgot.identify.genericNotice"), AppColor.TEXT_MUTED);

        controller.requestOtp(username, email, this::handleRequestResult, () -> {
            submitButton.setEnabled(true);
            submitButton.setText(Lang.get("forgot.identify.submit"));
            showMessage(Lang.get("forgot.request.systemError"), AppColor.ERROR);
        });
    }

    private void handleRequestResult(PasswordResetService.RequestResult result) {
        submitButton.setEnabled(true);
        submitButton.setText(Lang.get("forgot.identify.submit"));
        switch (result.getStatus()) {
            case ACCEPTED:
                listener.onOtpRequestAccepted(result);
                break;
            case RATE_LIMITED:
                showMessage(
                        Lang.get("forgot.request.rateLimited", result.getRetryAfterSeconds()),
                        AppColor.ERROR);
                break;
            case MAIL_FAILED:
                showMessage(Lang.get("forgot.request.mailFailed"), AppColor.ERROR);
                break;
            case INVALID_INPUT:
                showMessage(Lang.get("forgot.validation.email.invalid"), AppColor.ERROR);
                break;
            case SYSTEM_ERROR:
            default:
                showMessage(Lang.get("forgot.request.systemError"), AppColor.ERROR);
                break;
        }
    }
}