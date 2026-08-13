// src/main/java/com/service/TwoFactorMailService.java
package com.service;

import com.i18n.Lang;
import com.service.mail.MailSender;
import jakarta.mail.MessagingException;

/** Noi dung email rieng cho 2FA (enrollment + login OTP qua Email). */
public final class TwoFactorMailService {

    private final MailSender mailSender = new MailSender();

    public void sendLoginOtp(String toEmail, String otp) throws MessagingException {
        mailSender.send(
                toEmail,
                Lang.get("twofa.mail.login.subject"),
                Lang.get("twofa.mail.login.body", otp)
        );
    }

    public void sendEnrollOtp(String toEmail, String otp) throws MessagingException {
        mailSender.send(
                toEmail,
                Lang.get("twofa.mail.enroll.subject"),
                Lang.get("twofa.mail.enroll.body", otp)
        );
    }
}