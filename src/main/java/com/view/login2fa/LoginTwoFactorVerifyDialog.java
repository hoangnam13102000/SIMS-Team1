// src/main/java/com/view/login2fa/LoginTwoFactorVerifyDialog.java
package com.view.login2fa;

import com.i18n.Lang;
import com.model.TwoFactorMethod;
import com.model.User;
import com.service.TwoFactorAuthService;
import com.theme.AppColor;
import com.theme.AppSpacing;
import com.view.LoginFrame;
import com.view.forgotpassword.WizardWidgets;
import com.components.common.PrimaryButton;
import com.components.common.RoundedField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Dialog xac thuc buoc 2FA LUC DANG NHAP, dung khi admin DA bat 2FA tu truoc
 * (khac voi LoginTwoFactorEnrollDialog - dialog do dung khi CHUA bat, ep
 * thiet lap lan dau). Ho tro nhap ma OTP/TOTP hoac chuyen sang backup code.
 */
public class LoginTwoFactorVerifyDialog extends JDialog {

    public enum Outcome { SUCCESS, CANCELLED }

    private final TwoFactorAuthService service = TwoFactorAuthService.getInstance();
    private final User user;
    private final TwoFactorMethod method;

    private String challengeId;
    private Outcome outcome = Outcome.CANCELLED;
    private boolean usingBackupCode = false;

    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private RoundedField codeField;
    private JLabel messageLabel;
    private PrimaryButton verifyButton;
    private JButton resendButton;
    private JButton toggleBackupButton;
    private Timer countdownTimer;

    public LoginTwoFactorVerifyDialog(LoginFrame owner, User user, TwoFactorMethod method) {
        super(owner, Lang.get("twofa.verify.dialog.title"), ModalityType.APPLICATION_MODAL);
        this.user = user;
        this.method = method;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setSize(520, 460);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());
        add(buildContent(), BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });
        getRootPane().registerKeyboardAction(
                e -> requestClose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        startChallenge();
    }

    public Outcome getOutcome() {
        return outcome;
    }

    private JComponent buildContent() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        titleLabel = new JLabel(Lang.get("twofa.verify.title"));
        titleLabel.setFont(com.theme.AppFont.TITLE);
        titleLabel.setForeground(AppColor.TEXT_TITLE);
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        subtitleLabel = WizardWidgets.createWrappedLabel(" ", com.theme.AppFont.BODY, AppColor.TEXT_MUTED);
        panel.add(subtitleLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.XL));

        codeField = WizardWidgets.createTextField(Lang.get("twofa.verify.code.placeholder"));
        JTextField input = codeField.getTextField();
        input.setHorizontalAlignment(SwingConstants.CENTER);
        input.setFont(com.theme.AppFont.HEADING_MD);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new CodeDocumentFilter());
        panel.add(WizardWidgets.fieldGroup(Lang.get("twofa.verify.code.label"), codeField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        messageLabel = WizardWidgets.createMessageLabel();
        panel.add(messageLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        verifyButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.verify.button"));
        verifyButton.addActionListener(e -> doVerify());
        panel.add(verifyButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        resendButton = WizardWidgets.createLinkButton(Lang.get("twofa.verify.resend"));
        resendButton.addActionListener(e -> doResend());

        toggleBackupButton = WizardWidgets.createLinkButton(Lang.get("twofa.verify.useBackupCode"));
        toggleBackupButton.addActionListener(e -> toggleBackupMode());

        JButton cancelButton = WizardWidgets.createLinkButton(Lang.get("twofa.verify.cancel"));
        cancelButton.addActionListener(e -> requestClose());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, AppSpacing.MD, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(cancelButton);
        actions.add(toggleBackupButton);
        actions.add(resendButton);
        panel.add(actions);

        input.addActionListener(e -> doVerify());
        return panel;
    }

    private void startChallenge() {
        if (method == TwoFactorMethod.TOTP) {
            TwoFactorAuthService.LoginChallengeResult r = service.startLoginChallenge(user, TwoFactorMethod.TOTP);
            challengeId = r.challengeId;
            subtitleLabel.setText(WizardWidgets.toHtml(Lang.get("twofa.verify.subtitle.totp")));
            resendButton.setVisible(false);
        } else {
            resendButton.setVisible(true);
            requestEmailOtp();
        }
        SwingUtilities.invokeLater(() -> codeField.getTextField().requestFocusInWindow());
    }

    private void requestEmailOtp() {
        TwoFactorAuthService.LoginChallengeResult r = service.startLoginChallenge(user, TwoFactorMethod.EMAIL);
        if (r.status == TwoFactorAuthService.RequestStatus.ACCEPTED) {
            challengeId = r.challengeId;
            subtitleLabel.setText(WizardWidgets.toHtml(Lang.get("twofa.verify.subtitle.email", maskEmail(user.getEmail()))));
            startCountdown(r.retryAfterSeconds);
        } else if (r.status == TwoFactorAuthService.RequestStatus.NO_EMAIL) {
            WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.noEmail"), AppColor.ERROR);
            verifyButton.setEnabled(false);
        } else {
            WizardWidgets.showMessage(messageLabel, Lang.get("twofa.request.mailFailed"), AppColor.ERROR);
        }
    }

    private void toggleBackupMode() {
        usingBackupCode = !usingBackupCode;
        codeField.setText("");
        WizardWidgets.showMessage(messageLabel, " ", AppColor.TEXT_MUTED);
        if (usingBackupCode) {
            toggleBackupButton.setText(Lang.get("twofa.verify.useCodeInstead"));
            subtitleLabel.setText(WizardWidgets.toHtml(Lang.get("twofa.verify.subtitle.backup")));
            resendButton.setVisible(false);
        } else {
            toggleBackupButton.setText(Lang.get("twofa.verify.useBackupCode"));
            resendButton.setVisible(method == TwoFactorMethod.EMAIL);
            startChallenge();
        }
    }

    private void doVerify() {
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.required"), AppColor.ERROR);
            return;
        }
        verifyButton.setEnabled(false);

        TwoFactorAuthService.VerifyResult result = usingBackupCode
                ? service.verifyBackupCode(challengeId, code)
                : service.verifyLoginCode(challengeId, code);

        verifyButton.setEnabled(true);
        switch (result) {
            case SUCCESS:
                stopCountdown();
                outcome = Outcome.SUCCESS;
                dispose();
                break;
            case INVALID_CODE:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.invalid"), AppColor.ERROR);
                codeField.setText("");
                break;
            case EXPIRED:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.expired"), AppColor.ERROR);
                break;
            case TOO_MANY_ATTEMPTS:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.tooManyAttempts"), AppColor.ERROR);
                verifyButton.setEnabled(false);
                break;
            case NOT_FOUND:
            default:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.notFound"), AppColor.ERROR);
                break;
        }
    }

    private void doResend() {
        TwoFactorAuthService.ResendResult r = service.resendOtp(challengeId);
        switch (r) {
            case SUCCESS:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.resent"), AppColor.SUCCESS);
                startCountdown(60);
                break;
            case COOLDOWN:
                break;
            case MAIL_FAILED:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.request.mailFailed"), AppColor.ERROR);
                break;
            case EXPIRED:
            case NOT_FOUND:
            default:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.verify.error.notFound"), AppColor.ERROR);
                break;
        }
    }

    private void startCountdown(int seconds) {
        stopCountdown();
        final int[] remaining = {Math.max(1, seconds)};
        resendButton.setEnabled(false);
        resendButton.setText(Lang.get("twofa.verify.resendCountdown", remaining[0]));
        countdownTimer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                stopCountdown();
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("twofa.verify.resend"));
            } else {
                resendButton.setText(Lang.get("twofa.verify.resendCountdown", remaining[0]));
            }
        });
        countdownTimer.start();
    }

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void requestClose() {
        stopCountdown();
        service.cancelChallenge(challengeId);
        outcome = Outcome.CANCELLED;
        dispose();
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return (local.length() == 1 ? local : local.charAt(0) + "***") + email.substring(at);
    }

    private static final class CodeDocumentFilter extends DocumentFilter {
        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            String replacement = text == null ? "" : text.toUpperCase(java.util.Locale.ROOT);
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String candidate = current.substring(0, offset) + replacement + current.substring(offset + length);
            if (candidate.length() <= 12 && candidate.matches("[0-9A-Z-]*")) {
                super.replace(fb, offset, length, replacement, attrs);
            }
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }
    }
}