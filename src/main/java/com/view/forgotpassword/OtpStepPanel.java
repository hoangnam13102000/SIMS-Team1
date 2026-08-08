package com.view.forgotpassword;

import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.i18n.Lang;
import com.service.PasswordResetService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.validation.FormValidator;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/** Buoc 2: nhap ma OTP, co resend + dem nguoc. */
public class OtpStepPanel extends JPanel {

    public interface Listener {
        void onVerified();
        void onBackToIdentify();
    }

    private final PasswordResetFlowController controller;
    private final Listener listener;

    private final JLabel sentTo;
    private final RoundedField otpField;
    private final JLabel message;
    private final PrimaryButton verifyButton;
    private final JButton resendButton;

    private Timer countdownTimer;

    public OtpStepPanel(PasswordResetFlowController controller, Listener listener) {
        this.controller = controller;
        this.listener = listener;

        setBackground(AppColor.WHITE);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        WizardWidgets.addHeader(this, 2, Lang.get("forgot.otp.title"), Lang.get("forgot.otp.subtitle"));

        sentTo = WizardWidgets.createWrappedLabel(" ", AppFont.BODY, AppColor.TEXT_MUTED);
        add(sentTo);
        add(Box.createVerticalStrut(AppSpacing.LG));

        otpField = WizardWidgets.createTextField(Lang.get("forgot.otp.code.placeholder"));
        JTextField input = otpField.getTextField();
        input.setHorizontalAlignment(SwingConstants.CENTER);
        input.setFont(AppFont.HEADING_MD);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new OtpDocumentFilter());
        add(WizardWidgets.fieldGroup(Lang.get("forgot.otp.code"), otpField));
        add(Box.createVerticalStrut(AppSpacing.MD));

        message = WizardWidgets.createMessageLabel();
        add(message);
        add(Box.createVerticalStrut(AppSpacing.SM));

        verifyButton = WizardWidgets.createPrimaryButton(Lang.get("forgot.otp.verify"));
        verifyButton.addActionListener(e -> verifyOtp());
        add(verifyButton);
        add(Box.createVerticalStrut(AppSpacing.SM));

        resendButton = WizardWidgets.createLinkButton(Lang.get("forgot.otp.resend"));
        resendButton.addActionListener(e -> resendOtp());
        JButton backButton = WizardWidgets.createLinkButton(Lang.get("forgot.otp.back"));
        backButton.addActionListener(e -> returnToIdentify());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, AppSpacing.LG, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(WizardWidgets.CONTENT_WIDTH, 34));
        actions.add(backButton);
        actions.add(resendButton);
        add(actions);

        input.addActionListener(e -> verifyOtp());
    }

    public PrimaryButton getPrimaryButton() {
        return verifyButton;
    }

    public void requestInitialFocus() {
        SwingUtilities.invokeLater(() -> otpField.getTextField().requestFocusInWindow());
    }

    /** Goi khi buoc 1 vua duoc chap nhan (OTP moi da gui). */
    public void prepareForNewChallenge(String maskedEmail, int retryAfterSeconds) {
        sentTo.setText(WizardWidgets.toHtml(Lang.get("forgot.otp.sentTo", maskedEmail)));
        otpField.setText("");
        showMessage(Lang.get("forgot.request.accepted"), AppColor.INFO);
        startCountdown(retryAfterSeconds);
    }

    public void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    public void clearOtp() {
        otpField.setText("");
    }

    public void showMessage(String text, Color color) {
        WizardWidgets.showMessage(message, text, color);
    }

    private void startCountdown(int seconds) {
        stopCountdown();
        final int[] remaining = {Math.max(1, seconds)};
        resendButton.setEnabled(false);
        updateCountdownText(remaining[0]);

        countdownTimer = new Timer(1000, e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                stopCountdown();
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("forgot.otp.resend"));
            } else {
                updateCountdownText(remaining[0]);
            }
        });
        countdownTimer.start();
    }

    private void updateCountdownText(int remaining) {
        resendButton.setText(Lang.get("forgot.otp.resendCountdown", remaining));
    }

    private void selectOtp() {
        JTextField input = otpField.getTextField();
        input.requestFocusInWindow();
        input.selectAll();
    }

    private void returnToIdentify() {
        if (controller.isBusy()) {
            return;
        }
        controller.cancelCurrentChallenge();
        stopCountdown();
        otpField.setText("");
        showMessage(" ", AppColor.TEXT_MUTED);
        listener.onBackToIdentify();
    }

    private void verifyOtp() {
        if (controller.isBusy() || !controller.hasActiveChallenge()) {
            return;
        }
        String code = otpField.getText().trim();
        FormValidator validator = new FormValidator();
        validator.field(code)
                .required(Lang.get("forgot.validation.otp.required"))
                .exactLength(6, Lang.get("forgot.validation.otp.length"))
                .digitsOnly(Lang.get("forgot.validation.otp.digits"));
        String error = validator.validate();
        if (error != null) {
            showMessage(error, AppColor.ERROR);
            return;
        }

        verifyButton.setEnabled(false);
        verifyButton.setText(Lang.get("forgot.otp.verifying"));

        controller.verifyOtp(code, this::handleVerifyResult, () -> {
            verifyButton.setEnabled(true);
            verifyButton.setText(Lang.get("forgot.otp.verify"));
            showMessage(Lang.get("forgot.request.systemError"), AppColor.ERROR);
        });
    }

    private void handleVerifyResult(PasswordResetService.VerifyResult result) {
        verifyButton.setEnabled(true);
        verifyButton.setText(Lang.get("forgot.otp.verify"));
        switch (result.getStatus()) {
            case SUCCESS:
                stopCountdown();
                otpField.setText("");
                listener.onVerified();
                break;
            case INVALID_CODE:
                showMessage(
                        Lang.get("forgot.verify.invalid", result.getRemainingAttempts()),
                        AppColor.ERROR);
                selectOtp();
                break;
            case TOO_MANY_ATTEMPTS:
                showMessage(Lang.get("forgot.verify.tooManyAttempts"), AppColor.ERROR);
                selectOtp();
                break;
            case EXPIRED:
                showMessage(Lang.get("forgot.verify.expired"), AppColor.ERROR);
                break;
            case ALREADY_VERIFIED:
                listener.onVerified();
                break;
            case NOT_FOUND:
            default:
                showMessage(Lang.get("forgot.verify.notFound"), AppColor.ERROR);
                break;
        }
    }

    private void resendOtp() {
        if (controller.isBusy() || !controller.hasActiveChallenge()) {
            return;
        }
        resendButton.setEnabled(false);
        resendButton.setText(Lang.get("forgot.otp.resending"));

        controller.resendOtp(this::handleResendResult, () -> {
            resendButton.setEnabled(true);
            resendButton.setText(Lang.get("forgot.otp.resend"));
            showMessage(Lang.get("forgot.request.systemError"), AppColor.ERROR);
        });
    }

    private void handleResendResult(PasswordResetService.ResendResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                otpField.setText("");
                showMessage(Lang.get("forgot.otp.resent"), AppColor.SUCCESS);
                startCountdown(result.getRetryAfterSeconds());
                requestInitialFocus();
                break;
            case COOLDOWN:
                startCountdown(result.getRetryAfterSeconds());
                break;
            case RATE_LIMITED:
                resendButton.setEnabled(false);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(
                        Lang.get("forgot.request.rateLimited", result.getRetryAfterSeconds()),
                        AppColor.ERROR);
                break;
            case MAIL_FAILED:
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(Lang.get("forgot.request.mailFailed"), AppColor.ERROR);
                break;
            case ALREADY_VERIFIED:
                listener.onVerified();
                break;
            case IN_PROGRESS:
                startCountdown(Math.max(1, result.getRetryAfterSeconds()));
                break;
            case EXPIRED:
            case NOT_FOUND:
            default:
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(Lang.get("forgot.verify.notFound"), AppColor.ERROR);
                break;
        }
    }

    private static final class OtpDocumentFilter extends DocumentFilter {
        @Override
        public void replace(FilterBypass fb, int offset, int length,
                            String text, AttributeSet attrs)
                throws BadLocationException {
            String replacement = text == null ? "" : text;
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String candidate = current.substring(0, offset)
                    + replacement
                    + current.substring(offset + length);
            if (candidate.length() <= 6 && candidate.matches("\\d*")) {
                super.replace(fb, offset, length, replacement, attrs);
            }
        }

        @Override
        public void insertString(FilterBypass fb, int offset, String string,
                                 AttributeSet attr) throws BadLocationException {
            replace(fb, offset, 0, string, attr);
        }
    }
}