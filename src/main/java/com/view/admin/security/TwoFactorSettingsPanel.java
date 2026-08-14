// src/main/java/com/view/admin/security/TwoFactorSettingsPanel.java
package com.view.admin.security;

import com.components.AppAlert;
import com.components.BaseDialog;
import com.components.SectionHeader;
import com.i18n.Lang;
import com.model.TwoFactorMethod;
import com.model.User;
import com.model.UserTwoFactor;
import com.service.AuthService;
import com.service.TwoFactorAuthService;
import com.theme.AppColor;
import com.theme.AppFont;
import com.theme.AppRadius;
import com.theme.AppSpacing;
import org.kordamp.ikonli.fontawesome5.FontAwesomeSolid;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.time.format.DateTimeFormatter;

/**
 * Trang "Bảo mật đăng nhập" trong Settings - cho ADMIN đã đăng nhập tự quản
 * lý 2FA của chính mình: xem trạng thái, đổi phương thức, tắt, tạo lại backup
 * codes. Khác với LoginTwoFactorEnrollDialog (bắt buộc lúc đăng nhập lần đầu),
 * mọi hành động ở đây đều tùy chọn và yêu cầu nhập lại mật khẩu trước khi áp dụng.
 */
public class TwoFactorSettingsPanel extends JPanel {

    private final TwoFactorAuthService service = TwoFactorAuthService.getInstance();
    private final User currentUser = AuthService.getInstance().getCurrentUser();

    private JLabel statusIcon;
    private JLabel statusTitle;
    private JLabel statusDetail;
    private JLabel backupCountLabel;
    private JButton changeMethodButton;
    private JButton disableButton;
    private JButton regenerateButton;

    public TwoFactorSettingsPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColor.PAGE_BG);
        setBorder(new EmptyBorder(20, 24, 20, 24));

        SectionHeader header = new SectionHeader(FontAwesomeSolid.SHIELD_ALT, AppColor.ACCENT,
                Lang.get("twofa.settings.title"), Lang.get("twofa.settings.subtitle"));

        add(header, BorderLayout.NORTH);
        add(buildStatusCard(), BorderLayout.CENTER);

        refreshStatus();
    }

    private JComponent buildStatusCard() {
        JPanel wrapper = new JPanel();
        wrapper.setOpaque(false);
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBorder(new EmptyBorder(AppSpacing.LG, 0, 0, 0));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColor.WHITE);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(560, 320));
        card.setBorder(new CompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(AppSpacing.XL, AppSpacing.XL, AppSpacing.XL, AppSpacing.XL)));

        JPanel statusRow = new JPanel();
        statusRow.setOpaque(false);
        statusRow.setLayout(new BoxLayout(statusRow, BoxLayout.X_AXIS));
        statusRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        statusIcon = new JLabel();
        statusRow.add(statusIcon);
        statusRow.add(Box.createHorizontalStrut(AppSpacing.MD));

        JPanel textCol = new JPanel();
        textCol.setOpaque(false);
        textCol.setLayout(new BoxLayout(textCol, BoxLayout.Y_AXIS));

        statusTitle = new JLabel();
        statusTitle.setFont(AppFont.HEADING_MD);
        statusTitle.setForeground(AppColor.TEXT_TITLE);
        textCol.add(statusTitle);

        statusDetail = new JLabel();
        statusDetail.setFont(AppFont.BODY);
        statusDetail.setForeground(AppColor.TEXT_MUTED);
        textCol.add(statusDetail);

        statusRow.add(textCol);
        card.add(statusRow);
        card.add(Box.createVerticalStrut(AppSpacing.SM));

        backupCountLabel = new JLabel();
        backupCountLabel.setFont(AppFont.SMALL);
        backupCountLabel.setForeground(AppColor.TEXT_MUTED);
        backupCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(backupCountLabel);
        card.add(Box.createVerticalStrut(AppSpacing.LG));

        changeMethodButton = new JButton(Lang.get("twofa.settings.changeMethod"));
        styleActionButton(changeMethodButton, AppColor.ACCENT);
        changeMethodButton.addActionListener(e -> withReAuth(this::openChangeMethodDialog));
        card.add(changeMethodButton);
        card.add(Box.createVerticalStrut(AppSpacing.SM));

        regenerateButton = new JButton(Lang.get("twofa.settings.regenerateBackup"));
        styleActionButton(regenerateButton, AppColor.ACCENT);
        regenerateButton.addActionListener(e -> withReAuth(this::regenerateBackupCodes));
        card.add(regenerateButton);
        card.add(Box.createVerticalStrut(AppSpacing.SM));

        disableButton = new JButton(Lang.get("twofa.settings.disable"));
        styleActionButton(disableButton, AppColor.ERROR);
        disableButton.addActionListener(e -> withReAuth(this::disableTwoFactor));
        card.add(disableButton);

        wrapper.add(card);
        return wrapper;
    }

    private void styleActionButton(JButton button, Color color) {
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setFocusPainted(false);
        button.setForeground(color);
        button.setBackground(AppColor.WHITE);
        button.setBorder(new CompoundBorder(new LineBorder(color, 1, true), AppSpacing.custom(10, 16, 10, 16)));
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setMaximumSize(new Dimension(300, 40));
        button.setHorizontalAlignment(SwingConstants.LEFT);
    }

    private void refreshStatus() {
        UserTwoFactor status = service.getStatus(currentUser.getUserId());
        FontIcon icon = FontIcon.of(
                status.isEnabled() ? FontAwesomeSolid.CHECK_CIRCLE : FontAwesomeSolid.EXCLAMATION_TRIANGLE,
                28);
        icon.setIconColor(status.isEnabled() ? AppColor.SUCCESS : AppColor.WARNING);
        statusIcon.setIcon(icon);

        if (status.isEnabled()) {
            statusTitle.setText(Lang.get("twofa.settings.enabledTitle"));
            String methodLabel = status.getMethod() == TwoFactorMethod.TOTP
                    ? Lang.get("twofa.settings.methodTotp")
                    : Lang.get("twofa.settings.methodEmail");
            String enrolledAt = status.getEnrolledAt() != null
                    ? status.getEnrolledAt().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                    : "-";
            statusDetail.setText(Lang.get("twofa.settings.enabledDetail", methodLabel, enrolledAt));
            int remaining = service.countRemainingBackupCodes(currentUser.getUserId());
            backupCountLabel.setText(Lang.get("twofa.settings.backupRemaining", remaining));
            changeMethodButton.setVisible(true);
            regenerateButton.setVisible(true);
            disableButton.setVisible(true);
        } else {
            statusTitle.setText(Lang.get("twofa.settings.disabledTitle"));
            statusDetail.setText(Lang.get("twofa.settings.disabledDetail"));
            backupCountLabel.setText(" ");
            changeMethodButton.setVisible(true);
            regenerateButton.setVisible(false);
            disableButton.setVisible(false);
        }
    }

    private void withReAuth(Runnable action) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        ReAuthPasswordDialog dialog = new ReAuthPasswordDialog(owner, currentUser);
        dialog.setVisible(true);
        if (dialog.isConfirmed()) {
            action.run();
        }
    }

    private void openChangeMethodDialog() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        TwoFactorChangeMethodDialog dialog = new TwoFactorChangeMethodDialog(owner, currentUser, backupCodes -> {
            refreshStatus();
            new BackupCodesDisplayDialog(owner, backupCodes).setVisible(true);
            AppAlert.success(this, Lang.get("twofa.settings.changeMethod"), Lang.get("twofa.settings.changed"));
        });
        dialog.setVisible(true);
    }

    private void regenerateBackupCodes() {
        Window owner = SwingUtilities.getWindowAncestor(this);
        var codes = service.regenerateBackupCodes(currentUser.getUserId());
        new BackupCodesDisplayDialog(owner, codes).setVisible(true);
        refreshStatus();
    }

    private void disableTwoFactor() {
        boolean confirmed = BaseDialog.confirm(
                this,
                Lang.get("twofa.settings.disableConfirm.title"),
                Lang.get("twofa.settings.disableConfirm.message"),
                Lang.get("twofa.settings.disable"),
                AppColor.ERROR, AppColor.ERROR_HOVER,
                FontAwesomeSolid.SHIELD_ALT
        );
        if (!confirmed) {
            return;
        }
        boolean ok = service.disableTwoFactor(currentUser.getUserId(), currentUser.getUsername());
        if (ok) {
            AppAlert.warning(this, Lang.get("twofa.settings.disabled"));
            refreshStatus();
        } else {
            AppAlert.error(this, Lang.get("twofa.request.systemError"));
        }
    }
}