package com.service.mail;

import com.security.AppConfig;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Gui email dang text thuan qua Gmail SMTP. Logic nay truoc day nam rieng
 * trong OtpService - tach ra day de cac service khac (vd EmployeeMailService
 * gui tai khoan cho nhan vien moi) dung chung thay vi copy lai boilerplate
 * Session/Transport.
 * <p>
 * App password Gmail duoc lay qua AppConfig (giai ma tu secure-config.enc),
 * giong cach OtpService dang dung - KHONG doc truc tiep tu file properties.
 */
public class MailSender {

    private final String senderAddress;
    private final String senderPassword;

    public MailSender() {
        AppConfig appConfig = AppConfig.getInstance();
        this.senderAddress = appConfig.get("MAIL_SENDER_ADDRESS");
        this.senderPassword = appConfig.get("MAIL_SENDER_APP_PASSWORD");
    }
    public void send(String toEmail, String subject, String bodyText) throws MessagingException {
        Properties smtpProps = new Properties();
        smtpProps.put("mail.smtp.auth", "true");
        smtpProps.put("mail.smtp.starttls.enable", "true");
        smtpProps.put("mail.smtp.host", "smtp.gmail.com");
        smtpProps.put("mail.smtp.port", "587");

        Session session = Session.getInstance(smtpProps, new jakarta.mail.Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(senderAddress, senderPassword);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(senderAddress, false));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject(subject, "UTF-8");
        message.setText(bodyText, "UTF-8");

        // Loi thuong gap: sai App Password, chua tao App Password (dung nham
        // mat khau Gmail thuong), hoac may khong ket noi duoc smtp.gmail.com:587
        // (bi firewall/proxy chan) -> de nguyen MessagingException cho noi goi
        // ben ngoai xu ly/hien thi.
        Transport.send(message);
    }
}