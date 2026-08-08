package com.view.forgotpassword;

import com.components.common.PrimaryButton;
import com.components.common.RoundedPasswordField;
import com.i18n.Lang;
import com.service.PasswordResetService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.util.Arrays;

/** Buoc 3: nhap mat khau moi. */
public class PasswordStepPanel extends JPanel {

    public interface Listener {
        void onResetSuccess(String username);
        void onNeedsRestart(String message);
        void onCancel();
    }

    private final PasswordResetFlowController controller;
    private final Listener listener;

    private final RoundedPasswordField newPasswordField;
    private final RoundedPasswordField confirmPasswordField;
    private final JLabel passwordMatch;
    private final JLabel message;
    private final PrimaryButton resetButton;

    private String pendingUsername = "";

    public PasswordStepPanel(PasswordResetFlowController controller, Listener listener) {
        this.controller = controller;
        this.listener = listener;

        setBackground(AppColor.WHITE);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        WizardWidgets.addHeader(
                this, 3, Lang.get("forgot.password.title"), Lang.get("forgot.password.subtitle"));

        newPasswordField = WizardWidgets.createPasswordField();
        WizardWidgets.putPasswordPlaceholder(
                newPasswordField, Lang.get("forgot.password.new.placeholder"));
        confirmPasswordField = WizardWidgets.createPasswordField();
        WizardWidgets.putPasswordPlaceholder(
                confirmPasswordField, Lang.get("forgot.password.confirm.placeholder"));

        add(WizardWidgets.fieldGroup(Lang.get("forgot.password.new"), newPasswordField));
        add(Box.createVerticalStrut(AppSpacing.MD));
        add(WizardWidgets.fieldGroup(Lang.get("forgot.password.confirm"), confirmPasswordField));
        add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel requirements = WizardWidgets.createWrappedLabel(
                Lang.get("forgot.password.requirements"), AppFont.SMALL, AppColor.TEXT_MUTED);
        add(requirements);
        add(Box.createVerticalStrut(AppSpacing.XS));

        passwordMatch = WizardWidgets.createMessageLabel();
        add(passwordMatch);
        add(Box.createVerticalStrut(AppSpacing.XS));

        message = WizardWidgets.createMessageLabel();
        add(message);
        add(Box.createVerticalStrut(AppSpacing.SM));

        resetButton = WizardWidgets.createPrimaryButton(Lang.get("forgot.password.submit"));
        resetButton.addActionListener(e -> resetPassword());
        add(resetButton);
        add(Box.createVerticalStrut(AppSpacing.SM));

        JButton cancelButton = WizardWidgets.createLinkButton(Lang.get("forgot.backToLogin"));
        cancelButton.addActionListener(e -> listener.onCancel());
        add(WizardWidgets.centeredRow(cancelButton));

        DocumentListener matchListener = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                updatePasswordMatch();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                updatePasswordMatch();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                updatePasswordMatch();
            }
        };
        newPasswordField.getPasswordField().getDocument().addDocumentListener(matchListener);
        confirmPasswordField.getPasswordField().getDocument().addDocumentListener(matchListener);
        confirmPasswordField.getPasswordField().addActionListener(e -> resetPassword());
    }

    public PrimaryButton getPrimaryButton() {
        return resetButton;
    }

    public void setPendingUsername(String username) {
        this.pendingUsername = username == null ? "" : username;
    }

    public void prepareForNewAttempt() {
        newPasswordField.showPassword(false);
        confirmPasswordField.showPassword(false);
    }

    public void requestInitialFocus() {
        SwingUtilities.invokeLater(() -> newPasswordField.getPasswordField().requestFocusInWindow());
    }

    public void clearFields() {
        newPasswordField.setText("");
        newPasswordField.showPassword(false);
        confirmPasswordField.setText("");
        confirmPasswordField.showPassword(false);
    }

    private void updatePasswordMatch() {
        char[] password = newPasswordField.getPasswordField().getPassword();
        char[] confirm = confirmPasswordField.getPasswordField().getPassword();
        if (confirm.length == 0) {
            WizardWidgets.showMessage(passwordMatch, " ", AppColor.TEXT_MUTED);
        } else if (Arrays.equals(password, confirm)) {
            WizardWidgets.showMessage(passwordMatch, Lang.get("forgot.password.match"), AppColor.SUCCESS);
        } else {
            WizardWidgets.showMessage(passwordMatch, Lang.get("forgot.password.mismatch"), AppColor.ERROR);
        }
        Arrays.fill(password, '\0');
        Arrays.fill(confirm, '\0');
    }

    private void resetPassword() {
        if (controller.isBusy() || !controller.hasActiveChallenge()) {
            return;
        }
        char[] password = newPasswordField.getPasswordField().getPassword();
        char[] confirm = confirmPasswordField.getPasswordField().getPassword();

        PasswordResetService.PasswordValidationStatus validation =
                PasswordResetService.validatePassword(password);
        if (validation != PasswordResetService.PasswordValidationStatus.VALID) {
            WizardWidgets.showMessage(message, validationMessage(validation), AppColor.ERROR);
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        if (confirm.length == 0) {
            WizardWidgets.showMessage(
                    message, Lang.get("forgot.validation.confirm.required"), AppColor.ERROR);
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        if (!Arrays.equals(password, confirm)) {
            WizardWidgets.showMessage(
                    message, Lang.get("forgot.validation.confirm.mismatch"), AppColor.ERROR);
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        Arrays.fill(confirm, '\0');

        resetButton.setEnabled(false);
        resetButton.setText(Lang.get("forgot.password.submitting"));

        controller.resetPassword(password, this::handleResetResult, () -> {
            resetButton.setEnabled(true);
            resetButton.setText(Lang.get("forgot.password.submit"));
            WizardWidgets.showMessage(message, Lang.get("forgot.reset.failed"), AppColor.ERROR);
        });
    }

    private void handleResetResult(PasswordResetService.ResetResult result) {
        resetButton.setEnabled(true);
        resetButton.setText(Lang.get("forgot.password.submit"));
        switch (result.getStatus()) {
            case SUCCESS:
                String username = pendingUsername;
                clearFields();
                listener.onResetSuccess(username);
                break;
            case SAME_AS_OLD_PASSWORD:
                WizardWidgets.showMessage(
                        message, Lang.get("forgot.validation.samePassword"), AppColor.ERROR);
                break;
            case INVALID_PASSWORD:
                WizardWidgets.showMessage(
                        message, validationMessage(result.getValidationStatus()), AppColor.ERROR);
                break;
            case NOT_VERIFIED:
                WizardWidgets.showMessage(
                        message, Lang.get("forgot.reset.notVerified"), AppColor.ERROR);
                break;
            case SESSION_EXPIRED:
            case NOT_FOUND:
                listener.onNeedsRestart(Lang.get("forgot.reset.sessionExpired"));
                break;
            case ACCOUNT_UNAVAILABLE:
                listener.onNeedsRestart(Lang.get("forgot.reset.accountUnavailable"));
                break;
            case IN_PROGRESS:
                WizardWidgets.showMessage(
                        message, Lang.get("forgot.password.submitting"), AppColor.TEXT_MUTED);
                break;
            case UPDATE_FAILED:
            default:
                WizardWidgets.showMessage(message, Lang.get("forgot.reset.failed"), AppColor.ERROR);
                break;
        }
    }

    private String validationMessage(PasswordResetService.PasswordValidationStatus validation) {
        switch (validation) {
            case REQUIRED:
                return Lang.get("forgot.validation.password.required");
            case LENGTH:
                return Lang.get("forgot.validation.password.length");
            case LETTER:
                return Lang.get("forgot.validation.password.letter");
            case DIGIT:
                return Lang.get("forgot.validation.password.digit");
            case WHITESPACE:
                return Lang.get("forgot.validation.password.whitespace");
            case BYTE_LENGTH:
                return Lang.get("forgot.validation.password.byteLength");
            case VALID:
            default:
                return " ";
        }
    }
}