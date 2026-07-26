package com.view;


import com.theme.AppColor;
import com.service.OtpService;
import com.validation.FormValidator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;

public class OtpVerifyDialog extends JDialog {

    private final OtpService otpService;
    private final String email;

    private JTextField codeField;
    private JLabel messageLabel;
    private JButton resendButton;
    private Timer cooldownTimer;

    private boolean confirmed = false;

    public OtpVerifyDialog(Window owner, OtpService otpService, String email) {
        super(owner, "Xac nhan OTP", Dialog.ModalityType.APPLICATION_MODAL);
        this.otpService = otpService;
        this.email = email;
        setSize(360, 260);
        setLocationRelativeTo(owner);
        setResizable(false);
        getContentPane().setBackground(AppColor.WHITE);
        add(buildForm());
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private JPanel buildForm() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(24, 24, 24, 24));
        panel.setBackground(AppColor.WHITE);

        JLabel title = new JLabel("Nhap ma xac nhan");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(AppColor.TEXT_PRIMARY);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        JLabel subtitle = new JLabel("<html>Ma OTP da duoc gui toi " + email + "</html>");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subtitle.setForeground(AppColor.TEXT_MUTED);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        subtitle.setBorder(new EmptyBorder(6, 0, 12, 0));
        panel.add(subtitle);

        codeField = new JTextField();
        codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        codeField.setFont(new Font("Segoe UI", Font.BOLD, 18));
        codeField.setHorizontalAlignment(JTextField.CENTER);
        codeField.setBorder(BorderFactory.createCompoundBorder(
                new LineBorder(AppColor.BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
        panel.add(codeField);

        messageLabel = new JLabel(" ");
        messageLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
        panel.add(messageLabel);

        JButton verifyButton = new JButton("Xac nhan");
        verifyButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        verifyButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        verifyButton.setBackground(AppColor.ACCENT);
        verifyButton.setForeground(Color.WHITE);
        verifyButton.setFocusPainted(false);
        verifyButton.addActionListener(e -> doVerify());
        panel.add(verifyButton);

        resendButton = new JButton("Gui lai ma");
        resendButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        resendButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));
        resendButton.setBorderPainted(false);
        resendButton.setContentAreaFilled(false);
        resendButton.setForeground(AppColor.ACCENT);
        resendButton.setFocusPainted(false);
        resendButton.addActionListener(e -> doResend());
        panel.add(resendButton);

        return panel;
    }

    private void doVerify() {
        String input = codeField.getText().trim();

        FormValidator validator = new FormValidator();
        validator.field(input)
                .required("Vui long nhap ma OTP.")
                .exactLength(6, "Ma OTP phai gom dung 6 chu so.")
                .digitsOnly("Ma OTP chi duoc chua chu so.");

        String error = validator.validate();
        if (error != null) {
            showMessage(error, AppColor.ERROR);
            return;
        }

        OtpService.OtpResult result = otpService.verify(email, input);
        switch (result) {
            case SUCCESS:
                confirmed = true;
                dispose();
                break;
            case WRONG_CODE:
                showMessage("Ma OTP khong dung, vui long thu lai.", AppColor.ERROR);
                break;
            case EXPIRED:
                showMessage("Ma OTP da het han, vui long bam \"Gui lai ma\".", AppColor.ERROR);
                break;
            case TOO_MANY_ATTEMPTS:
                showMessage("Ban da nhap sai qua nhieu lan, vui long gui lai ma moi.", AppColor.ERROR);
                break;
            case NOT_FOUND:
            default:
                showMessage("Khong tim thay phien OTP, vui long bam \"Gui lai ma\".", AppColor.ERROR);
                break;
        }
    }

    private void doResend() {
        resendButton.setEnabled(false);
        try {
            otpService.sendOtp(email);
            showMessage("Da gui lai ma OTP moi toi email cua ban.", AppColor.GREEN);
        } catch (Exception ex) {
            showMessage("Gui email that bai: " + ex.getMessage(), AppColor.ERROR);
        }
        startCooldown(30);
    }

    /** Vo hieu hoa nut "Gui lai ma" trong vai giay de tranh spam gui OTP lien tuc. */
    private void startCooldown(int seconds) {
        if (cooldownTimer != null && cooldownTimer.isRunning()) {
            cooldownTimer.stop();
        }
        final int[] remaining = {seconds};
        cooldownTimer = new Timer(1000, null);
        cooldownTimer.addActionListener(e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                cooldownTimer.stop();
                resendButton.setEnabled(true);
                resendButton.setText("Gui lai ma");
            } else {
                resendButton.setText("Gui lai ma (" + remaining[0] + "s)");
            }
        });
        cooldownTimer.start();
    }

    private void showMessage(String text, Color color) {
        messageLabel.setForeground(color);
        messageLabel.setText("<html>" + text + "</html>");
    }
}