package com.view;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import com.components.common.RoundedPasswordField;
import com.i18n.Lang;
import com.service.PasswordResetService;
import com.theme.AppColor;
import com.theme.AppConstant;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.validation.FormValidator;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;

public class ForgotPasswordDialog extends JDialog {

    private enum Step {
        IDENTIFY_ACCOUNT,
        VERIFY_OTP,
        RESET_PASSWORD
    }

    private static final int DIALOG_WIDTH = 540;
    private static final int DIALOG_HEIGHT = 640;
    private static final int CONTENT_WIDTH = 440;

    private final LoginFrame loginFrame;
    private final PasswordResetService resetService;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private RoundedField usernameField;
    private RoundedField emailField;
    private RoundedField otpField;
    private RoundedPasswordField newPasswordField;
    private RoundedPasswordField confirmPasswordField;

    private JLabel identifyMessage;
    private JLabel otpMessage;
    private JLabel otpSentTo;
    private JLabel passwordMessage;
    private JLabel passwordMatch;

    private PrimaryButton identifyButton;
    private PrimaryButton verifyButton;
    private PrimaryButton resetButton;
    private JButton resendButton;

    private Timer countdownTimer;
    private String challengeId;
    private boolean completed;
    private boolean busy;
    private Step currentStep = Step.IDENTIFY_ACCOUNT;

    public ForgotPasswordDialog(LoginFrame owner) {
        this(owner, "");
    }

    public ForgotPasswordDialog(LoginFrame owner, String initialUsername) {
        super(owner, Lang.get("forgot.frame.title"), Dialog.ModalityType.APPLICATION_MODAL);
        this.loginFrame = owner;
        this.resetService = PasswordResetService.getInstance();

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
        cards.add(buildIdentifyPanel(initialUsername), Step.IDENTIFY_ACCOUNT.name());
        cards.add(buildOtpPanel(), Step.VERIFY_OTP.name());
        cards.add(buildPasswordPanel(), Step.RESET_PASSWORD.name());
        return cards;
    }

    private JPanel buildIdentifyPanel(String initialUsername) {
        JPanel panel = createStepPanel();
        addHeader(
                panel,
                1,
                Lang.get("forgot.identify.title"),
                Lang.get("forgot.identify.subtitle")
        );

        usernameField = createTextField(Lang.get("forgot.identify.username.placeholder"));
        usernameField.setText(initialUsername == null ? "" : initialUsername.trim());
        emailField = createTextField(Lang.get("forgot.identify.email.placeholder"));

        panel.add(fieldGroup(Lang.get("forgot.identify.username"), usernameField));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));
        panel.add(fieldGroup(Lang.get("forgot.identify.email"), emailField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        identifyMessage = createMessageLabel();
        panel.add(identifyMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        identifyButton = createPrimaryButton(Lang.get("forgot.identify.submit"));
        identifyButton.addActionListener(e -> requestOtp());
        panel.add(identifyButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JButton backButton = createLinkButton(Lang.get("forgot.backToLogin"));
        backButton.addActionListener(e -> requestClose());
        panel.add(centeredRow(backButton));

        usernameField.getTextField().addActionListener(e -> emailField.getTextField().requestFocusInWindow());
        emailField.getTextField().addActionListener(e -> requestOtp());
        return panel;
    }

    private JPanel buildOtpPanel() {
        JPanel panel = createStepPanel();
        addHeader(
                panel,
                2,
                Lang.get("forgot.otp.title"),
                Lang.get("forgot.otp.subtitle")
        );

        otpSentTo = createWrappedLabel(" ", AppFont.BODY, AppColor.TEXT_MUTED);
        panel.add(otpSentTo);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        otpField = createTextField(Lang.get("forgot.otp.code.placeholder"));
        JTextField input = otpField.getTextField();
        input.setHorizontalAlignment(SwingConstants.CENTER);
        input.setFont(AppFont.HEADING_MD);
        ((AbstractDocument) input.getDocument()).setDocumentFilter(new OtpDocumentFilter());
        panel.add(fieldGroup(Lang.get("forgot.otp.code"), otpField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        otpMessage = createMessageLabel();
        panel.add(otpMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        verifyButton = createPrimaryButton(Lang.get("forgot.otp.verify"));
        verifyButton.addActionListener(e -> verifyOtp());
        panel.add(verifyButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        resendButton = createLinkButton(Lang.get("forgot.otp.resend"));
        resendButton.addActionListener(e -> resendOtp());
        JButton backButton = createLinkButton(Lang.get("forgot.otp.back"));
        backButton.addActionListener(e -> returnToIdentify());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.CENTER, AppSpacing.LG, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(CONTENT_WIDTH, 34));
        actions.add(backButton);
        actions.add(resendButton);
        panel.add(actions);

        input.addActionListener(e -> verifyOtp());
        return panel;
    }

    private JPanel buildPasswordPanel() {
        JPanel panel = createStepPanel();
        addHeader(
                panel,
                3,
                Lang.get("forgot.password.title"),
                Lang.get("forgot.password.subtitle")
        );

        newPasswordField = createPasswordField();
        putPasswordPlaceholder(
                newPasswordField, Lang.get("forgot.password.new.placeholder"));
        confirmPasswordField = createPasswordField();
        putPasswordPlaceholder(
                confirmPasswordField, Lang.get("forgot.password.confirm.placeholder"));

        panel.add(fieldGroup(Lang.get("forgot.password.new"), newPasswordField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));
        panel.add(fieldGroup(Lang.get("forgot.password.confirm"), confirmPasswordField));
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel requirements = createWrappedLabel(
                Lang.get("forgot.password.requirements"),
                AppFont.SMALL,
                AppColor.TEXT_MUTED
        );
        panel.add(requirements);
        panel.add(Box.createVerticalStrut(AppSpacing.XS));

        passwordMatch = createMessageLabel();
        panel.add(passwordMatch);
        panel.add(Box.createVerticalStrut(AppSpacing.XS));

        passwordMessage = createMessageLabel();
        panel.add(passwordMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        resetButton = createPrimaryButton(Lang.get("forgot.password.submit"));
        resetButton.addActionListener(e -> resetPassword());
        panel.add(resetButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JButton cancelButton = createLinkButton(Lang.get("forgot.backToLogin"));
        cancelButton.addActionListener(e -> requestClose());
        panel.add(centeredRow(cancelButton));

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
        return panel;
    }

    private JPanel createStepPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(AppColor.WHITE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private void addHeader(JPanel panel, int step, String titleText, String subtitleText) {
        JLabel stepLabel = new JLabel(Lang.get("forgot.step.counter", step, 3));
        stepLabel.setFont(AppFont.SMALL_BOLD);
        stepLabel.setForeground(AppColor.ACCENT);
        stepLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(stepLabel);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel title = new JLabel(titleText);
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JLabel subtitle = createWrappedLabel(subtitleText, AppFont.BODY, AppColor.TEXT_MUTED);
        panel.add(subtitle);
        panel.add(Box.createVerticalStrut(AppSpacing.XL));
    }

    private JPanel fieldGroup(String labelText, JComponent field) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        group.setAlignmentX(Component.LEFT_ALIGNMENT);
        group.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT + 28));

        JLabel label = new JLabel(labelText);
        label.setFont(AppFont.LABEL);
        label.setForeground(AppColor.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(new EmptyBorder(0, 0, AppSpacing.XS, 0));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);

        group.add(label);
        group.add(field);
        return group;
    }

    private RoundedField createTextField(String placeholder) {
        RoundedField field = new RoundedField();
        field.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.getTextField().putClientProperty("JTextField.placeholderText", placeholder);
        return field;
    }

    private RoundedPasswordField createPasswordField() {
        final RoundedPasswordField[] holder = new RoundedPasswordField[1];
        FontIcon showIcon = FontIcon.of(FontAwesomeSolid.EYE, AppConstant.ICON_SIZE_SM);
        showIcon.setIconColor(AppColor.ACCENT);
        FontIcon hideIcon = FontIcon.of(FontAwesomeSolid.EYE_SLASH, AppConstant.ICON_SIZE_SM);
        hideIcon.setIconColor(AppColor.ACCENT);

        JLabel toggle = new JLabel(showIcon);
        toggle.setCursor(new Cursor(Cursor.HAND_CURSOR));
        toggle.setToolTipText(Lang.get("forgot.password.show"));
        toggle.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                RoundedPasswordField field = holder[0];
                boolean show = !field.isPasswordShowing();
                field.showPassword(show);
                toggle.setIcon(show ? hideIcon : showIcon);
                toggle.setToolTipText(Lang.get(
                        show ? "forgot.password.hide" : "forgot.password.show"));
                field.getPasswordField().requestFocusInWindow();
            }
        });

        RoundedPasswordField field = new RoundedPasswordField(toggle);
        holder[0] = field;
        field.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        field.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.FIELD_HEIGHT));
        return field;
    }

    private void putPasswordPlaceholder(RoundedPasswordField field, String placeholder) {
        field.getPasswordField().putClientProperty(
                "JTextField.placeholderText", placeholder);
    }

    private PrimaryButton createPrimaryButton(String text) {
        PrimaryButton button = new PrimaryButton(text);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setPreferredSize(new Dimension(CONTENT_WIDTH, AppConstant.BUTTON_HEIGHT));
        button.setMaximumSize(new Dimension(CONTENT_WIDTH, AppConstant.BUTTON_HEIGHT));
        return button;
    }

    private JButton createLinkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(AppFont.SMALL_BOLD);
        button.setForeground(AppColor.ACCENT);
        button.setBorder(BorderFactory.createEmptyBorder(
                AppSpacing.SM, AppSpacing.MD, AppSpacing.SM, AppSpacing.MD));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    private JPanel centeredRow(JComponent component) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH, 36));
        row.add(component);
        return row;
    }

    private JLabel createMessageLabel() {
        JLabel label = createWrappedLabel(" ", AppFont.SMALL, AppColor.TEXT_MUTED);
        label.setPreferredSize(new Dimension(CONTENT_WIDTH, 44));
        label.setMaximumSize(new Dimension(CONTENT_WIDTH, 44));
        return label;
    }

    private JLabel createWrappedLabel(String text, java.awt.Font font, Color color) {
        JLabel label = new JLabel(toHtml(text));
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(CONTENT_WIDTH, 70));
        return label;
    }

    private String toHtml(String text) {
        String safeText = text == null ? "" : text.replace("\n", "<br>");
        return "<html><div style='width:" + CONTENT_WIDTH + "px'>"
                + safeText + "</div></html>";
    }

    private void requestOtp() {
        if (busy) {
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
            showMessage(identifyMessage, error, AppColor.ERROR);
            return;
        }

        cancelCurrentChallenge();
        setBusy(true);
        identifyButton.setEnabled(false);
        identifyButton.setText(Lang.get("forgot.identify.submitting"));
        showMessage(
                identifyMessage,
                Lang.get("forgot.identify.genericNotice"),
                AppColor.TEXT_MUTED
        );

        new SwingWorker<PasswordResetService.RequestResult, Void>() {
            @Override
            protected PasswordResetService.RequestResult doInBackground() {
                return resetService.requestOtp(username, email);
            }

            @Override
            protected void done() {
                setBusy(false);
                identifyButton.setEnabled(true);
                identifyButton.setText(Lang.get("forgot.identify.submit"));
                try {
                    PasswordResetService.RequestResult result = get();
                    if (!isDisplayable()) {
                        resetService.cancelChallenge(result.getChallengeId());
                        return;
                    }
                    handleRequestResult(result);
                } catch (Exception e) {
                    showMessage(
                            identifyMessage,
                            Lang.get("forgot.request.systemError"),
                            AppColor.ERROR
                    );
                }
            }
        }.execute();
    }

    private void handleRequestResult(PasswordResetService.RequestResult result) {
        switch (result.getStatus()) {
            case ACCEPTED:
                challengeId = result.getChallengeId();
                otpSentTo.setText(toHtml(
                        Lang.get("forgot.otp.sentTo", result.getMaskedEmail())));
                showMessage(
                        otpMessage,
                        Lang.get("forgot.request.accepted"),
                        AppColor.INFO
                );
                showStep(Step.VERIFY_OTP);
                startCountdown(result.getRetryAfterSeconds());
                break;
            case RATE_LIMITED:
                showMessage(
                        identifyMessage,
                        Lang.get(
                                "forgot.request.rateLimited",
                                result.getRetryAfterSeconds()),
                        AppColor.ERROR
                );
                break;
            case MAIL_FAILED:
                showMessage(
                        identifyMessage,
                        Lang.get("forgot.request.mailFailed"),
                        AppColor.ERROR
                );
                break;
            case INVALID_INPUT:
                showMessage(
                        identifyMessage,
                        Lang.get("forgot.validation.email.invalid"),
                        AppColor.ERROR
                );
                break;
            case SYSTEM_ERROR:
            default:
                showMessage(
                        identifyMessage,
                        Lang.get("forgot.request.systemError"),
                        AppColor.ERROR
                );
                break;
        }
    }

    private void verifyOtp() {
        if (busy || challengeId == null) {
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
            showMessage(otpMessage, error, AppColor.ERROR);
            return;
        }

        setBusy(true);
        verifyButton.setEnabled(false);
        verifyButton.setText(Lang.get("forgot.otp.verifying"));

        new SwingWorker<PasswordResetService.VerifyResult, Void>() {
            @Override
            protected PasswordResetService.VerifyResult doInBackground() {
                return resetService.verifyOtp(challengeId, code);
            }

            @Override
            protected void done() {
                setBusy(false);
                verifyButton.setEnabled(true);
                verifyButton.setText(Lang.get("forgot.otp.verify"));
                if (!isDisplayable()) {
                    return;
                }
                try {
                    handleVerifyResult(get());
                } catch (Exception e) {
                    showMessage(
                            otpMessage,
                            Lang.get("forgot.request.systemError"),
                            AppColor.ERROR
                    );
                }
            }
        }.execute();
    }

    private void handleVerifyResult(PasswordResetService.VerifyResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                stopCountdown();
                otpField.setText("");
                showStep(Step.RESET_PASSWORD);
                break;
            case INVALID_CODE:
                showMessage(
                        otpMessage,
                        Lang.get("forgot.verify.invalid", result.getRemainingAttempts()),
                        AppColor.ERROR
                );
                selectOtp();
                break;
            case TOO_MANY_ATTEMPTS:
                showMessage(
                        otpMessage,
                        Lang.get("forgot.verify.tooManyAttempts"),
                        AppColor.ERROR
                );
                selectOtp();
                break;
            case EXPIRED:
                showMessage(
                        otpMessage,
                        Lang.get("forgot.verify.expired"),
                        AppColor.ERROR
                );
                break;
            case ALREADY_VERIFIED:
                showStep(Step.RESET_PASSWORD);
                break;
            case NOT_FOUND:
            default:
                showMessage(
                        otpMessage,
                        Lang.get("forgot.verify.notFound"),
                        AppColor.ERROR
                );
                break;
        }
    }

    private void resendOtp() {
        if (busy || challengeId == null) {
            return;
        }
        setBusy(true);
        resendButton.setEnabled(false);
        resendButton.setText(Lang.get("forgot.otp.resending"));

        new SwingWorker<PasswordResetService.ResendResult, Void>() {
            @Override
            protected PasswordResetService.ResendResult doInBackground() {
                return resetService.resendOtp(challengeId);
            }

            @Override
            protected void done() {
                setBusy(false);
                if (!isDisplayable()) {
                    return;
                }
                try {
                    handleResendResult(get());
                } catch (Exception e) {
                    resendButton.setEnabled(true);
                    resendButton.setText(Lang.get("forgot.otp.resend"));
                    showMessage(
                            otpMessage,
                            Lang.get("forgot.request.systemError"),
                            AppColor.ERROR
                    );
                }
            }
        }.execute();
    }

    private void handleResendResult(PasswordResetService.ResendResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                otpField.setText("");
                showMessage(
                        otpMessage,
                        Lang.get("forgot.otp.resent"),
                        AppColor.SUCCESS
                );
                startCountdown(result.getRetryAfterSeconds());
                SwingUtilities.invokeLater(
                        () -> otpField.getTextField().requestFocusInWindow());
                break;
            case COOLDOWN:
                startCountdown(result.getRetryAfterSeconds());
                break;
            case RATE_LIMITED:
                resendButton.setEnabled(false);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(
                        otpMessage,
                        Lang.get(
                                "forgot.request.rateLimited",
                                result.getRetryAfterSeconds()),
                        AppColor.ERROR
                );
                break;
            case MAIL_FAILED:
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(
                        otpMessage,
                        Lang.get("forgot.request.mailFailed"),
                        AppColor.ERROR
                );
                break;
            case ALREADY_VERIFIED:
                showStep(Step.RESET_PASSWORD);
                break;
            case IN_PROGRESS:
                startCountdown(Math.max(1, result.getRetryAfterSeconds()));
                break;
            case EXPIRED:
            case NOT_FOUND:
            default:
                resendButton.setEnabled(true);
                resendButton.setText(Lang.get("forgot.otp.resend"));
                showMessage(
                        otpMessage,
                        Lang.get("forgot.verify.notFound"),
                        AppColor.ERROR
                );
                break;
        }
    }

    private void resetPassword() {
        if (busy || challengeId == null) {
            return;
        }
        char[] password = newPasswordField.getPasswordField().getPassword();
        char[] confirm = confirmPasswordField.getPasswordField().getPassword();

        PasswordResetService.PasswordValidationStatus validation =
                PasswordResetService.validatePassword(password);
        if (validation != PasswordResetService.PasswordValidationStatus.VALID) {
            showMessage(
                    passwordMessage,
                    validationMessage(validation),
                    AppColor.ERROR
            );
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        if (confirm.length == 0) {
            showMessage(
                    passwordMessage,
                    Lang.get("forgot.validation.confirm.required"),
                    AppColor.ERROR
            );
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        if (!Arrays.equals(password, confirm)) {
            showMessage(
                    passwordMessage,
                    Lang.get("forgot.validation.confirm.mismatch"),
                    AppColor.ERROR
            );
            Arrays.fill(password, '\0');
            Arrays.fill(confirm, '\0');
            return;
        }
        Arrays.fill(confirm, '\0');

        setBusy(true);
        resetButton.setEnabled(false);
        resetButton.setText(Lang.get("forgot.password.submitting"));

        new SwingWorker<PasswordResetService.ResetResult, Void>() {
            @Override
            protected PasswordResetService.ResetResult doInBackground() {
                return resetService.resetPassword(challengeId, password);
            }

            @Override
            protected void done() {
                setBusy(false);
                resetButton.setEnabled(true);
                resetButton.setText(Lang.get("forgot.password.submit"));
                if (!isDisplayable()) {
                    return;
                }
                try {
                    handleResetResult(get());
                } catch (Exception e) {
                    showMessage(
                            passwordMessage,
                            Lang.get("forgot.reset.failed"),
                            AppColor.ERROR
                    );
                }
            }
        }.execute();
    }

    private void handleResetResult(PasswordResetService.ResetResult result) {
        switch (result.getStatus()) {
            case SUCCESS:
                completed = true;
                challengeId = null;
                String username = usernameField.getText().trim();
                clearSensitiveFields();
                loginFrame.prepareAfterPasswordReset(username);
                dispose();
                AppAlert.success(
                        loginFrame,
                        Lang.get("forgot.reset.success.title"),
                        Lang.get("forgot.reset.success.message")
                );
                break;
            case SAME_AS_OLD_PASSWORD:
                showMessage(
                        passwordMessage,
                        Lang.get("forgot.validation.samePassword"),
                        AppColor.ERROR
                );
                break;
            case INVALID_PASSWORD:
                showMessage(
                        passwordMessage,
                        validationMessage(result.getValidationStatus()),
                        AppColor.ERROR
                );
                break;
            case NOT_VERIFIED:
                showMessage(
                        passwordMessage,
                        Lang.get("forgot.reset.notVerified"),
                        AppColor.ERROR
                );
                break;
            case SESSION_EXPIRED:
            case NOT_FOUND:
                resetToIdentify(Lang.get("forgot.reset.sessionExpired"));
                break;
            case ACCOUNT_UNAVAILABLE:
                resetToIdentify(Lang.get("forgot.reset.accountUnavailable"));
                break;
            case IN_PROGRESS:
                showMessage(
                        passwordMessage,
                        Lang.get("forgot.password.submitting"),
                        AppColor.TEXT_MUTED
                );
                break;
            case UPDATE_FAILED:
            default:
                showMessage(
                        passwordMessage,
                        Lang.get("forgot.reset.failed"),
                        AppColor.ERROR
                );
                break;
        }
    }

    private String validationMessage(
            PasswordResetService.PasswordValidationStatus validation) {
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

    private void updatePasswordMatch() {
        char[] password = newPasswordField.getPasswordField().getPassword();
        char[] confirm = confirmPasswordField.getPasswordField().getPassword();
        if (confirm.length == 0) {
            showMessage(passwordMatch, " ", AppColor.TEXT_MUTED);
        } else if (Arrays.equals(password, confirm)) {
            showMessage(
                    passwordMatch,
                    Lang.get("forgot.password.match"),
                    AppColor.SUCCESS
            );
        } else {
            showMessage(
                    passwordMatch,
                    Lang.get("forgot.password.mismatch"),
                    AppColor.ERROR
            );
        }
        Arrays.fill(password, '\0');
        Arrays.fill(confirm, '\0');
    }

    private void showStep(Step step) {
        currentStep = step;
        cardLayout.show(cards, step.name());
        switch (step) {
            case IDENTIFY_ACCOUNT:
                getRootPane().setDefaultButton(identifyButton);
                SwingUtilities.invokeLater(
                        () -> usernameField.getTextField().requestFocusInWindow());
                break;
            case VERIFY_OTP:
                getRootPane().setDefaultButton(verifyButton);
                SwingUtilities.invokeLater(
                        () -> otpField.getTextField().requestFocusInWindow());
                break;
            case RESET_PASSWORD:
                getRootPane().setDefaultButton(resetButton);
                newPasswordField.showPassword(false);
                confirmPasswordField.showPassword(false);
                SwingUtilities.invokeLater(
                        () -> newPasswordField.getPasswordField().requestFocusInWindow());
                break;
            default:
                break;
        }
    }

    private void returnToIdentify() {
        if (busy) {
            return;
        }
        cancelCurrentChallenge();
        stopCountdown();
        otpField.setText("");
        showMessage(otpMessage, " ", AppColor.TEXT_MUTED);
        showStep(Step.IDENTIFY_ACCOUNT);
    }

    private void resetToIdentify(String message) {
        cancelCurrentChallenge();
        stopCountdown();
        clearSensitiveFields();
        showStep(Step.IDENTIFY_ACCOUNT);
        showMessage(identifyMessage, message, AppColor.ERROR);
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

    private void stopCountdown() {
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
    }

    private void requestClose() {
        if (busy) {
            return;
        }
        if (currentStep != Step.IDENTIFY_ACCOUNT && challengeId != null
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

    private void cancelCurrentChallenge() {
        if (challengeId != null) {
            resetService.cancelChallenge(challengeId);
            challengeId = null;
        }
    }

    private void clearSensitiveFields() {
        if (otpField != null) {
            otpField.setText("");
        }
        if (newPasswordField != null) {
            newPasswordField.setText("");
            newPasswordField.showPassword(false);
        }
        if (confirmPasswordField != null) {
            confirmPasswordField.setText("");
            confirmPasswordField.showPassword(false);
        }
    }

    private void selectOtp() {
        JTextField input = otpField.getTextField();
        input.requestFocusInWindow();
        input.selectAll();
    }

    private void setBusy(boolean busy) {
        this.busy = busy;
        setCursor(Cursor.getPredefinedCursor(
                busy ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    private void showMessage(JLabel label, String message, Color color) {
        label.setForeground(color);
        label.setText(toHtml(message));
    }

    @Override
    public void dispose() {
        stopCountdown();
        clearSensitiveFields();
        if (!completed) {
            cancelCurrentChallenge();
        }
        super.dispose();
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
