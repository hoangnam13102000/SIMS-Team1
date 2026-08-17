// src/main/java/com/view/login2fa/LoginTwoFactorEnrollDialog.java
package com.view.login2fa;

import com.components.BaseDialog;
import com.i18n.Lang;
import com.model.User;
import com.service.TwoFactorAuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.QrCodeUtil;
import com.view.LoginFrame;
import com.view.forgotpassword.WizardWidgets;
import com.components.common.PrimaryButton;
import com.components.common.RoundedField;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Dialog EP admin thiet lap 2FA khi dang nhap lan dau ma chua bat (khong the
 * bo qua - dong X/ESC se hoi xac nhan dang xuat thay vi vao thang AdminMainFrame).
 * 3 buoc: chon phuong thuc -> xac nhan (OTP email / ma TOTP) -> luu backup codes.
 */
public class LoginTwoFactorEnrollDialog extends JDialog {

    public enum Outcome { SUCCESS, CANCELLED }

    private enum Step { CHOOSE_METHOD, VERIFY_EMAIL, VERIFY_TOTP, BACKUP_CODES }

    private final TwoFactorAuthService service = TwoFactorAuthService.getInstance();
    private final User user;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private String challengeId;
    private Outcome outcome = Outcome.CANCELLED;

    private RoundedField emailCodeField;
    private JLabel emailMessage;
    private PrimaryButton emailVerifyButton;

    private JLabel qrLabel;
    private JLabel secretLabel;
    private JLabel copySecretIcon;
    private RoundedField totpCodeField;
    private JLabel totpMessage;
    private PrimaryButton totpVerifyButton;
    private String pendingTotpSecret;

    private JPanel backupCodesGrid;
    private JCheckBox savedCheckBox;
    private PrimaryButton finishButton;

    public LoginTwoFactorEnrollDialog(LoginFrame owner, User user) {
        super(owner, Lang.get("twofa.enroll.dialog.title"), ModalityType.APPLICATION_MODAL);
        this.user = user;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setResizable(false);
        setSize(560, 620);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());

        cards.setBackground(AppColor.WHITE);
        cards.add(buildChooseMethodStep(), Step.CHOOSE_METHOD.name());
        cards.add(buildEmailStep(), Step.VERIFY_EMAIL.name());
        cards.add(buildTotpStep(), Step.VERIFY_TOTP.name());
        cards.add(buildBackupCodesStep(), Step.BACKUP_CODES.name());
        add(cards, BorderLayout.CENTER);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestExit();
            }
        });
        getRootPane().registerKeyboardAction(
                e -> requestExit(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        cardLayout.show(cards, Step.CHOOSE_METHOD.name());
    }

    public Outcome getOutcome() {
        return outcome;
    }

    // ---------- BUOC 1: CHON PHUONG THUC ----------

    private JComponent buildChooseMethodStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        JLabel title = new JLabel(Lang.get("twofa.enroll.choose.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        panel.add(WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.choose.subtitle"), AppFont.BODY, AppColor.TEXT_MUTED));
        panel.add(Box.createVerticalStrut(AppSpacing.XL));

        PrimaryButton emailBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.choose.email"));
        emailBtn.addActionListener(e -> startEmailEnrollment());
        panel.add(emailBtn);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        PrimaryButton totpBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.choose.totp"));
        totpBtn.addActionListener(e -> startTotpEnrollment());
        panel.add(totpBtn);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        JButton logoutBtn = WizardWidgets.createLinkButton(Lang.get("twofa.enroll.logout"));
        logoutBtn.addActionListener(e -> requestExit());
        panel.add(logoutBtn);

        return panel;
    }

    // ---------- BUOC 2A: XAC NHAN QUA EMAIL ----------

    private JComponent buildEmailStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        JLabel title = new JLabel(Lang.get("twofa.enroll.email.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        panel.add(WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.email.subtitle", maskEmail(user.getEmail())), AppFont.BODY, AppColor.TEXT_MUTED));
        panel.add(Box.createVerticalStrut(AppSpacing.XL));

        emailCodeField = WizardWidgets.createTextField(Lang.get("twofa.verify.code.placeholder"));
        emailCodeField.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(WizardWidgets.fieldGroup(Lang.get("twofa.verify.code.label"), emailCodeField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        emailMessage = WizardWidgets.createMessageLabel();
        panel.add(emailMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        emailVerifyButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.verify.button"));
        emailVerifyButton.addActionListener(e -> confirmEmailEnrollment());
        panel.add(emailVerifyButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JButton backBtn = WizardWidgets.createLinkButton(Lang.get("twofa.enroll.back"));
        backBtn.addActionListener(e -> {
            service.cancelChallenge(challengeId);
            cardLayout.show(cards, Step.CHOOSE_METHOD.name());
        });
        panel.add(WizardWidgets.centeredRow(backBtn));

        return panel;
    }

    private void startEmailEnrollment() {
        TwoFactorAuthService.RequestResult r = service.startEmailEnrollment(user);
        if (r.status == TwoFactorAuthService.RequestStatus.NO_EMAIL) {
            JOptionPane.showMessageDialog(this, Lang.get("twofa.verify.error.noEmail"),
                    Lang.get("twofa.enroll.dialog.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (r.status != TwoFactorAuthService.RequestStatus.ACCEPTED) {
            JOptionPane.showMessageDialog(this, Lang.get("twofa.request.mailFailed"),
                    Lang.get("twofa.enroll.dialog.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }
        challengeId = r.challengeId;
        emailCodeField.setText("");
        WizardWidgets.showMessage(emailMessage, " ", AppColor.TEXT_MUTED);
        cardLayout.show(cards, Step.VERIFY_EMAIL.name());
        SwingUtilities.invokeLater(() -> emailCodeField.getTextField().requestFocusInWindow());
    }

    private void confirmEmailEnrollment() {
        String code = emailCodeField.getText().trim();
        TwoFactorAuthService.EnrollResult result = service.confirmEmailEnrollment(challengeId, code);
        handleEnrollResult(result, emailMessage, emailCodeField);
    }

    // ---------- BUOC 2B: XAC NHAN QUA TOTP ----------

    private JComponent buildTotpStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XXL, AppSpacing.LG, AppSpacing.XXL));

        JLabel title = new JLabel(Lang.get("twofa.enroll.totp.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        panel.add(WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.totp.subtitle"), AppFont.BODY, AppColor.TEXT_MUTED));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        // ---- QR: panel rieng GIAN HET CHIEU RONG THAT (khong gioi han
        // CONTENT_WIDTH nhu cac field khac), roi moi dung FlowLayout.CENTER
        // de canh QR vao giua khoang do. Neu gioi han maximumSize.width =
        // CONTENT_WIDTH nhu truoc, khi panel bi cac dong khac (vd secretLabel
        // chua chuoi ma dai) day rong hon CONTENT_WIDTH thi QR se bi lech trai. ----
        qrLabel = new JLabel();
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        qrLabel.setVerticalAlignment(SwingConstants.CENTER);

        JPanel qrWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        qrWrapper.setOpaque(false);
        qrWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        qrWrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, 240));
        qrWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 240));
        qrWrapper.add(qrLabel);
        panel.add(qrWrapper);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        // ---- Khoi "Ma nhap thu cong": KHONG dua vao alignmentX cua BoxLayout
        // nua (khong dang tin cay voi cac JLabel/JPanel co kich thuoc nho, gay
        // lech trai nhu truoc). Thay vao do dung DUNG ky thuat wrapper full-
        // width + FlowLayout.CENTER giong het qrWrapper o tren (da kiem chung
        // hoat dong dung, QR luon can giua) -> dam bao ca khoi nay can giua
        // chinh xac ngay duoi QR. Ma bi mat duoc dat trong 1 "chip" bo vien,
        // nen nhe de nhin ro rang va chuyen nghiep hon la chu tran. ----
        JLabel secretPrefixLabel = new JLabel(Lang.get("twofa.enroll.totp.secretPrefix"));
        secretPrefixLabel.setFont(AppFont.SMALL);
        secretPrefixLabel.setForeground(AppColor.TEXT_MUTED);
        secretPrefixLabel.setHorizontalAlignment(SwingConstants.CENTER);
        secretPrefixLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        secretLabel = new JLabel(" ");
        secretLabel.setFont(new Font("Consolas", Font.BOLD, 14));
        secretLabel.setForeground(AppColor.TEXT_TITLE);

        FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 14);
        copyIcon.setIconColor(AppColor.ACCENT);
        copySecretIcon = new JLabel(copyIcon);
        copySecretIcon.setCursor(new Cursor(Cursor.HAND_CURSOR));
        copySecretIcon.setToolTipText(Lang.get("twofa.enroll.totp.copyTooltip"));
        copySecretIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                copyTotpSecretToClipboard();
            }
        });

        JPanel secretChip = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        secretChip.setOpaque(true);
        secretChip.setBackground(AppColor.BG_LIGHTER);
        secretChip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(8, 16, 8, 16)));
        secretChip.add(secretLabel);
        secretChip.add(copySecretIcon);
        secretChip.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel secretInner = new JPanel();
        secretInner.setOpaque(false);
        secretInner.setLayout(new BoxLayout(secretInner, BoxLayout.Y_AXIS));
        secretInner.add(secretPrefixLabel);
        secretInner.add(Box.createVerticalStrut(6));
        secretInner.add(secretChip);

        JPanel secretWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        secretWrapper.setOpaque(false);
        secretWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
        secretWrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, 74));
        secretWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 74));
        secretWrapper.add(secretInner);
        panel.add(secretWrapper);
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        totpCodeField = WizardWidgets.createTextField(Lang.get("twofa.verify.code.placeholder"));
        totpCodeField.getTextField().setHorizontalAlignment(SwingConstants.CENTER);
        panel.add(WizardWidgets.fieldGroup(Lang.get("twofa.enroll.totp.codeLabel"), totpCodeField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        totpMessage = WizardWidgets.createMessageLabel();
        panel.add(totpMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        totpVerifyButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.verify.button"));
        totpVerifyButton.addActionListener(e -> confirmTotpEnrollment());
        panel.add(totpVerifyButton);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        JButton backBtn = WizardWidgets.createLinkButton(Lang.get("twofa.enroll.back"));
        backBtn.addActionListener(e -> {
            service.cancelChallenge(challengeId);
            cardLayout.show(cards, Step.CHOOSE_METHOD.name());
        });
        panel.add(WizardWidgets.centeredRow(backBtn));

        return panel;
    }

    private void startTotpEnrollment() {
        TwoFactorAuthService.TotpEnrollment enrollment = service.startTotpEnrollment(user);
        challengeId = enrollment.challengeId;
        pendingTotpSecret = enrollment.secretBase32;

        BufferedImage qrImage = QrCodeUtil.generate(enrollment.otpAuthUri, 220);
        qrLabel.setIcon(new ImageIcon(qrImage));
        secretLabel.setText(enrollment.secretBase32);

        totpCodeField.setText("");
        WizardWidgets.showMessage(totpMessage, " ", AppColor.TEXT_MUTED);
        cardLayout.show(cards, Step.VERIFY_TOTP.name());
        SwingUtilities.invokeLater(() -> totpCodeField.getTextField().requestFocusInWindow());
    }

    /**
     * Copy chuoi ma bi mat (Base32) vao clipboard - copy dung chuoi ma
     * thoi, khong kem chu "Ma nhap thu cong:" phia truoc. Sau khi copy,
     * doi icon sang dau tick trong 1.2s roi tu dong doi lai icon copy.
     */
    private void copyTotpSecretToClipboard() {
        if (pendingTotpSecret == null || pendingTotpSecret.isBlank()) {
            return;
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(pendingTotpSecret), null);

        FontIcon checkIcon = FontIcon.of(FontAwesomeSolid.CHECK, 14);
        checkIcon.setIconColor(AppColor.SUCCESS);
        copySecretIcon.setIcon(checkIcon);
        copySecretIcon.setToolTipText(Lang.get("twofa.enroll.totp.copied"));

        Timer resetTimer = new Timer(1200, e -> {
            FontIcon copyIcon = FontIcon.of(FontAwesomeSolid.COPY, 14);
            copyIcon.setIconColor(AppColor.ACCENT);
            copySecretIcon.setIcon(copyIcon);
            copySecretIcon.setToolTipText(Lang.get("twofa.enroll.totp.copyTooltip"));
        });
        resetTimer.setRepeats(false);
        resetTimer.start();
    }

    private void confirmTotpEnrollment() {
        String code = totpCodeField.getText().trim();
        TwoFactorAuthService.EnrollResult result = service.confirmTotpEnrollment(challengeId, code);
        handleEnrollResult(result, totpMessage, totpCodeField);
    }

    // ---------- XU LY KET QUA CHUNG ----------

    private void handleEnrollResult(TwoFactorAuthService.EnrollResult result, JLabel messageLabel, RoundedField codeField) {
        switch (result.status) {
            case SUCCESS:
                showBackupCodes(result.backupCodes);
                cardLayout.show(cards, Step.BACKUP_CODES.name());
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
                break;
            case NOT_FOUND:
            case SYSTEM_ERROR:
            default:
                WizardWidgets.showMessage(messageLabel, Lang.get("twofa.request.systemError"), AppColor.ERROR);
                break;
        }
    }

    // ---------- BUOC 3: BACKUP CODES ----------

    private JComponent buildBackupCodesStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        JLabel title = new JLabel(Lang.get("twofa.enroll.backup.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        panel.add(WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.backup.subtitle"), AppFont.BODY, AppColor.TEXT_MUTED));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        backupCodesGrid = new JPanel(new GridLayout(5, 2, AppSpacing.MD, AppSpacing.SM));
        backupCodesGrid.setOpaque(false);
        backupCodesGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        backupCodesGrid.setMaximumSize(new Dimension(WizardWidgets.CONTENT_WIDTH, 160));
        panel.add(backupCodesGrid);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        savedCheckBox = new JCheckBox(Lang.get("twofa.enroll.backup.confirmSaved"));
        savedCheckBox.setOpaque(false);
        savedCheckBox.setAlignmentX(Component.LEFT_ALIGNMENT);
        savedCheckBox.addActionListener(e -> finishButton.setEnabled(savedCheckBox.isSelected()));
        panel.add(savedCheckBox);
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        finishButton = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.backup.finish"));
        finishButton.setEnabled(false);
        finishButton.addActionListener(e -> {
            outcome = Outcome.SUCCESS;
            dispose();
        });
        panel.add(finishButton);

        return panel;
    }

    private void showBackupCodes(List<String> codes) {
        backupCodesGrid.removeAll();
        for (String code : codes) {
            JLabel codeLabel = new JLabel(code, SwingConstants.CENTER);
            codeLabel.setFont(AppFont.SMALL_BOLD);
            codeLabel.setForeground(AppColor.TEXT_TITLE);
            codeLabel.setBorder(BorderFactory.createLineBorder(AppColor.BORDER));
            backupCodesGrid.add(codeLabel);
        }
        backupCodesGrid.revalidate();
        backupCodesGrid.repaint();
        savedCheckBox.setSelected(false);
        finishButton.setEnabled(false);
    }

    // ---------- THOAT (khong the bo qua ma khong dang xuat) ----------

    private void requestExit() {
        boolean confirmed = BaseDialog.confirm(
                this,
                Lang.get("twofa.enroll.exit.title"),
                Lang.get("twofa.enroll.exit.message"),
                Lang.get("twofa.enroll.exit.confirmButton"),
                AppColor.ERROR,
                AppColor.ERROR_HOVER,
                FontAwesomeSolid.SIGN_OUT_ALT
        );
        if (confirmed) {
            service.cancelChallenge(challengeId);
            outcome = Outcome.CANCELLED;
            dispose();
        }
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return (local.length() == 1 ? local : local.charAt(0) + "***") + email.substring(at);
    }
}