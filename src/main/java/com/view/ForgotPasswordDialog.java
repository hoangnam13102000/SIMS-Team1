package com.view;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.i18n.Lang;
import com.service.PasswordResetService;
import com.theme.AppColor;
import com.theme.AppSpacing;
import com.view.forgotpassword.IdentifyStepPanel;
import com.view.forgotpassword.OtpStepPanel;
import com.view.forgotpassword.PasswordResetFlowController;
import com.view.forgotpassword.PasswordStepPanel;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Wizard 3 buoc "Quen mat khau". Cac buoc (Identify/Otp/Password) va logic
 * goi PasswordResetService da chuyen sang goi com.view.forgotpassword;
 * class nay chi con nhiem vu lap rap CardLayout va vong doi cua dialog.
 */
public class ForgotPasswordDialog extends JDialog {

    private enum Step {
        IDENTIFY_ACCOUNT,
        VERIFY_OTP,
        RESET_PASSWORD
    }

    private static final int DIALOG_WIDTH = 540;
    private static final int DIALOG_HEIGHT = 640;

    private final LoginFrame loginFrame;
    private final PasswordResetFlowController controller;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private IdentifyStepPanel identifyPanel;
    private OtpStepPanel otpPanel;
    private PasswordStepPanel passwordPanel;

    private Step currentStep = Step.IDENTIFY_ACCOUNT;

    public ForgotPasswordDialog(LoginFrame owner) {
        this(owner, "");
    }

    public ForgotPasswordDialog(LoginFrame owner, String initialUsername) {
        super(owner, Lang.get("forgot.frame.title"), Dialog.ModalityType.APPLICATION_MODAL);
        this.loginFrame = owner;
        this.controller = new PasswordResetFlowController(this);

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setSize(DIALOG_WIDTH, DIALOG_HEIGHT);
        setMinimumSize(new Dimension(DIALOG_WIDTH, DIALOG_HEIGHT));
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());
        add(buildContent(initialUsername), BorderLayout.CENTER);

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
        showStep(Step.IDENTIFY_ACCOUNT);
    }

    private JComponent buildContent(String initialUsername) {
        cards.setBackground(AppColor.WHITE);
        cards.setBorder(new EmptyBorder(
                AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        identifyPanel = new IdentifyStepPanel(initialUsername, controller, new IdentifyStepPanel.Listener() {
            @Override
            public void onOtpRequestAccepted(PasswordResetService.RequestResult result) {
                passwordPanel.setPendingUsername(identifyPanel.getUsername());
                otpPanel.prepareForNewChallenge(result.getMaskedEmail(), result.getRetryAfterSeconds());
                showStep(Step.VERIFY_OTP);
            }

            @Override
            public void onCancel() {
                requestClose();
            }
        });

        otpPanel = new OtpStepPanel(controller, new OtpStepPanel.Listener() {
            @Override
            public void onVerified() {
                passwordPanel.prepareForNewAttempt();
                showStep(Step.RESET_PASSWORD);
            }

            @Override
            public void onBackToIdentify() {
                showStep(Step.IDENTIFY_ACCOUNT);
            }
        });

        passwordPanel = new PasswordStepPanel(controller, new PasswordStepPanel.Listener() {
            @Override
            public void onResetSuccess(String username) {
                loginFrame.prepareAfterPasswordReset(username);
                dispose();
                AppAlert.success(
                        loginFrame,
                        Lang.get("forgot.reset.success.title"),
                        Lang.get("forgot.reset.success.message")
                );
            }

            @Override
            public void onNeedsRestart(String message) {
                resetToIdentify(message);
            }

            @Override
            public void onCancel() {
                requestClose();
            }
        });

        cards.add(identifyPanel, Step.IDENTIFY_ACCOUNT.name());
        cards.add(otpPanel, Step.VERIFY_OTP.name());
        cards.add(passwordPanel, Step.RESET_PASSWORD.name());
        return cards;
    }

    private void showStep(Step step) {
        currentStep = step;
        cardLayout.show(cards, step.name());
        switch (step) {
            case IDENTIFY_ACCOUNT:
                getRootPane().setDefaultButton(identifyPanel.getSubmitButton());
                identifyPanel.requestInitialFocus();
                break;
            case VERIFY_OTP:
                getRootPane().setDefaultButton(otpPanel.getPrimaryButton());
                otpPanel.requestInitialFocus();
                break;
            case RESET_PASSWORD:
                getRootPane().setDefaultButton(passwordPanel.getPrimaryButton());
                passwordPanel.requestInitialFocus();
                break;
            default:
                break;
        }
    }

    private void resetToIdentify(String message) {
        controller.cancelCurrentChallenge();
        otpPanel.stopCountdown();
        otpPanel.clearOtp();
        passwordPanel.clearFields();
        showStep(Step.IDENTIFY_ACCOUNT);
        identifyPanel.showMessage(message, AppColor.ERROR);
    }

    private void requestClose() {
        if (controller.isBusy()) {
            return;
        }
        if (currentStep != Step.IDENTIFY_ACCOUNT && controller.hasActiveChallenge()
                && !BaseDialog.confirm(
                this,
                Lang.get("forgot.close.confirm.title"),
                Lang.get("forgot.close.confirm.message"),
                Lang.get("forgot.close.confirm.button"),
                AppColor.ERROR,
                AppColor.ERROR_HOVER,
                FontAwesomeSolid.TIMES_CIRCLE)) {
            return;
        }
        dispose();
    }

    @Override
    public void dispose() {
        otpPanel.stopCountdown();
        otpPanel.clearOtp();
        passwordPanel.clearFields();
        if (!controller.isCompleted()) {
            controller.cancelCurrentChallenge();
        }
        super.dispose();
    }
}