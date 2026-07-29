package com.service;

import com.i18n.Lang;
import com.service.mail.MailSender;
import jakarta.mail.MessagingException;

/**
 * Noi dung email rieng cho password recovery. SMTP va credentials van do
 * MailSender quan ly qua AppConfig.
 */
public final class PasswordResetMailService {

    private final MailSender mailSender;

    public PasswordResetMailService() {
        this.mailSender = new MailSender();
    }

    public void sendResetOtp(String toEmail, String otp) throws MessagingException {
        mailSender.send(
                toEmail,
                Lang.get("forgot.mail.subject"),
                Lang.get("forgot.mail.body", otp)
        );
    }
}
