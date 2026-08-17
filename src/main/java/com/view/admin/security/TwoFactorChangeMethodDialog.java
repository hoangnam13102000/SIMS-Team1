// src/main/java/com/view/admin/security/TwoFactorChangeMethodDialog.java
package com.view.admin.security;

import com.i18n.Lang;
import com.model.User;
import com.service.TwoFactorAuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppSpacing;
import com.utils.QrCodeUtil;
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
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Giong het luong cua LoginTwoFactorEnrollDialog nhung dung khi admin DA
 * dang nhap va CHU DONG doi phuong thuc (khong bat buoc, co the dong bat
 * ky luc nao ma khong anh huong toi 2FA dang bat).
 */
public class TwoFactorChangeMethodDialog extends JDialog {

    public interface OnChanged {
        void onChanged(List<String> newBackupCodes);
    }

    private enum Step { CHOOSE_METHOD, VERIFY_EMAIL, VERIFY_TOTP }

    private final TwoFactorAuthService service = TwoFactorAuthService.getInstance();
    private final User user;
    private final OnChanged callback;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel cards = new JPanel(cardLayout);

    private String challengeId;
    private RoundedField emailCodeField;
    private JLabel emailMessage;
    private JLabel qrLabel;
    private JLabel secretLabel;
    private JLabel copySecretIcon;
    private String currentTotpSecret = "";
    private RoundedField totpCodeField;
    private JLabel totpMessage;

    public TwoFactorChangeMethodDialog(Window owner, User user, OnChanged callback) {
        super(owner, Lang.get("twofa.settings.changeMethod"), ModalityType.APPLICATION_MODAL);
        this.user = user;
        this.callback = callback;

        setResizable(false);
        setSize(520, 620);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(AppColor.WHITE);
        setLayout(new BorderLayout());

        cards.setBackground(AppColor.WHITE);
        cards.add(buildChooseStep(), Step.CHOOSE_METHOD.name());
        cards.add(buildEmailStep(), Step.VERIFY_EMAIL.name());
        cards.add(buildTotpStep(), Step.VERIFY_TOTP.name());
        add(cards, BorderLayout.CENTER);

        getRootPane().registerKeyboardAction(
                e -> { service.cancelChallenge(challengeId); dispose(); },
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private JComponent buildChooseStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        JLabel title = new JLabel(Lang.get("twofa.enroll.choose.title"));
        title.setFont(AppFont.TITLE);
        title.setForeground(AppColor.TEXT_TITLE);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);
        panel.add(Box.createVerticalStrut(AppSpacing.XL));

        PrimaryButton emailBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.choose.email"));
        emailBtn.addActionListener(e -> startEmail());
        panel.add(emailBtn);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        PrimaryButton totpBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.enroll.choose.totp"));
        totpBtn.addActionListener(e -> startTotp());
        panel.add(totpBtn);
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        JButton cancelBtn = WizardWidgets.createLinkButton(Lang.get("twofa.verify.cancel"));
        cancelBtn.addActionListener(e -> dispose());
        panel.add(cancelBtn);

        return panel;
    }

    private JComponent buildEmailStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.XL, AppSpacing.XXL, AppSpacing.XL, AppSpacing.XXL));

        panel.add(WizardWidgets.createWrappedLabel(
                Lang.get("twofa.enroll.email.subtitle", maskEmail(user.getEmail())), AppFont.BODY, AppColor.TEXT_MUTED));
        panel.add(Box.createVerticalStrut(AppSpacing.LG));

        emailCodeField = WizardWidgets.createTextField(Lang.get("twofa.verify.code.placeholder"));
        panel.add(WizardWidgets.fieldGroup(Lang.get("twofa.verify.code.label"), emailCodeField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        emailMessage = WizardWidgets.createMessageLabel();
        panel.add(emailMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        PrimaryButton verifyBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.verify.button"));
        verifyBtn.addActionListener(e -> {
            TwoFactorAuthService.EnrollResult result = service.confirmEmailEnrollment(challengeId, emailCodeField.getText().trim());
            handleResult(result, emailMessage);
        });
        panel.add(verifyBtn);

        return panel;
    }

    private JComponent buildTotpStep() {
        JPanel panel = WizardWidgets.createStepPanel();
        panel.setBorder(new EmptyBorder(AppSpacing.LG, AppSpacing.XXL, AppSpacing.LG, AppSpacing.XXL));

        // ---- QR: panel rieng GIAN HET CHIEU RONG THAT (khong gioi han
        // CONTENT_WIDTH), sau do FlowLayout.CENTER canh QR vao giua. ----
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
        panel.add(WizardWidgets.fieldGroup(Lang.get("twofa.enroll.totp.codeLabel"), totpCodeField));
        panel.add(Box.createVerticalStrut(AppSpacing.MD));

        totpMessage = WizardWidgets.createMessageLabel();
        panel.add(totpMessage);
        panel.add(Box.createVerticalStrut(AppSpacing.SM));

        PrimaryButton verifyBtn = WizardWidgets.createPrimaryButton(Lang.get("twofa.verify.button"));
        verifyBtn.addActionListener(e -> {
            TwoFactorAuthService.EnrollResult result = service.confirmTotpEnrollment(challengeId, totpCodeField.getText().trim());
            handleResult(result, totpMessage);
        });
        panel.add(verifyBtn);

        return panel;
    }

    private void startEmail() {
        TwoFactorAuthService.RequestResult r = service.startEmailEnrollment(user);
        if (r.status != TwoFactorAuthService.RequestStatus.ACCEPTED) {
            JOptionPane.showMessageDialog(this, Lang.get("twofa.request.mailFailed"));
            return;
        }
        challengeId = r.challengeId;
        emailCodeField.setText("");
        cardLayout.show(cards, Step.VERIFY_EMAIL.name());
    }

    private void startTotp() {
        TwoFactorAuthService.TotpEnrollment enrollment = service.startTotpEnrollment(user);
        challengeId = enrollment.challengeId;
        currentTotpSecret = enrollment.secretBase32;
        BufferedImage qr = QrCodeUtil.generate(enrollment.otpAuthUri, 220);
        qrLabel.setIcon(new ImageIcon(qr));
        secretLabel.setText(enrollment.secretBase32);
        totpCodeField.setText("");
        cardLayout.show(cards, Step.VERIFY_TOTP.name());
    }

    /**
     * Copy chuoi ma bi mat (Base32) vao clipboard - copy dung chuoi ma
     * thoi, khong kem chu "Ma nhap thu cong:" phia truoc.
     * Sau khi copy, doi icon sang dau tick trong 1.2s de nguoi dung
     * biet la da copy thanh cong, roi tu dong doi lai icon copy.
     */
    private void copyTotpSecretToClipboard() {
        if (currentTotpSecret == null || currentTotpSecret.isBlank()) {
            return;
        }

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(currentTotpSecret), null);

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

    private void handleResult(TwoFactorAuthService.EnrollResult result, JLabel messageLabel) {
        if (result.status == TwoFactorAuthService.EnrollStatus.SUCCESS) {
            dispose();
            callback.onChanged(result.backupCodes);
            return;
        }
        String key = switch (result.status) {
            case INVALID_CODE -> "twofa.verify.error.invalid";
            case EXPIRED -> "twofa.verify.error.expired";
            case TOO_MANY_ATTEMPTS -> "twofa.verify.error.tooManyAttempts";
            default -> "twofa.request.systemError";
        };
        WizardWidgets.showMessage(messageLabel, Lang.get(key), AppColor.ERROR);
    }

    private static String maskEmail(String email) {
        if (email == null) return "";
        int at = email.indexOf('@');
        if (at <= 0) return "***";
        String local = email.substring(0, at);
        return (local.length() == 1 ? local : local.charAt(0) + "***") + email.substring(at);
    }
}