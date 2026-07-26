package com.service;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import com.security.AppConfig;

import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinh ma OTP 6 so, gui qua Gmail SMTP va xac thuc lai khi nguoi dung nhap ma.
 * Ma OTP chi luu trong bo nho (khong luu DB), het han sau OTP_TTL_MS.
 *
 * App password Gmail (mail.sender.app.password) khong con doc truc tiep tu
 * mail.properties nua - duoc lay qua AppConfig (giai ma tu secure-config.enc).
 */
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MS = 5 * 60 * 1000L; // 5 phut
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Properties mailConfig;

    /** key = email, value = phien OTP dang cho xac nhan */
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

    public OtpService() {
        this.mailConfig = loadMailConfig();
    }

    private Properties loadMailConfig() {
        AppConfig appConfig = AppConfig.getInstance();
        Properties props = new Properties();
        props.setProperty("mail.sender.address", appConfig.get("MAIL_SENDER_ADDRESS"));
        props.setProperty("mail.sender.app.password", appConfig.get("MAIL_SENDER_APP_PASSWORD"));
        return props;
    }

    /** Sinh OTP moi, luu vao bo nho va gui email. Nem Exception neu gui that bai. */
    public void sendOtp(String toEmail) throws MessagingException {
        String otp = generateCode();
        otpStore.put(toEmail, new OtpEntry(otp, System.currentTimeMillis() + OTP_TTL_MS));
        sendEmail(toEmail, otp);
    }

    /**
     * Kiem tra ma nguoi dung nhap.
     * @return OtpResult mo ta ket qua (thanh cong / sai ma / het han / qua so lan thu).
     */
    public OtpResult verify(String email, String inputCode) {
        OtpEntry entry = otpStore.get(email);
        if (entry == null) {
            return OtpResult.NOT_FOUND;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            otpStore.remove(email);
            return OtpResult.EXPIRED;
        }
        if (entry.attempts >= MAX_ATTEMPTS) {
            otpStore.remove(email);
            return OtpResult.TOO_MANY_ATTEMPTS;
        }

        entry.attempts++;
        if (entry.code.equals(inputCode.trim())) {
            otpStore.remove(email);
            return OtpResult.SUCCESS;
        }
        return OtpResult.WRONG_CODE;
    }

    private String generateCode() {
        int code = RANDOM.nextInt(1_000_000); // 0 -> 999999
        return String.format("%0" + OTP_LENGTH + "d", code);
    }

    private void sendEmail(String toEmail, String otp) throws MessagingException {
        String senderAddress = mailConfig.getProperty("mail.sender.address");
        String senderPassword = mailConfig.getProperty("mail.sender.app.password");

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
        // Luon truyen ro charset UTF-8 cho ca subject va noi dung - neu khong,
        // MimeMessage se dung charset mac dinh cua JVM/OS (vd Windows co the
        // la Cp1252/Cp1258 thay vi UTF-8), lam dau tieng Viet bi hien thi loi
        // (mojibake) khi mo email tren Gmail, du code da go dung dau san.
        message.setSubject("Mã xác nhận đăng ký tài khoản", "UTF-8");
        message.setText(
            "Mã xác nhận (OTP) của bạn là: " + otp + "\n\n" +
            "Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.",
            "UTF-8"
        );

        // Loi thuong gap khi Transport.send that bai: sai App Password, chua tao App Password
        // (dung nham mat khau Gmail thuong), hoac may khong ket noi duoc smtp.gmail.com:587
        // (bi firewall/proxy chan) -> de nguyen MessagingException cho noi goi ben ngoai xu ly/hien thi.
        Transport.send(message);
    }

    private static class OtpEntry {
        final String code;
        final long expiresAt;
        int attempts = 0;

        OtpEntry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    public enum OtpResult {
        SUCCESS, WRONG_CODE, EXPIRED, NOT_FOUND, TOO_MANY_ATTEMPTS
    }
}