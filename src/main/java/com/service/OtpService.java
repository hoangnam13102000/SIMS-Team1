package com.service;

import jakarta.mail.MessagingException;

import com.service.mail.MailSender;

import java.security.SecureRandom;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinh ma OTP 6 so, gui qua Gmail SMTP va xac thuc lai khi nguoi dung nhap ma.
 * Ma OTP chi luu trong bo nho (khong luu DB), het han sau OTP_TTL_MS.
 *
 * Viec gui email (Session/Transport, App password lay qua AppConfig) da
 * duoc tach sang {@link MailSender} de dung chung voi cac service khac
 * (vd EmployeeMailService gui tai khoan cho nhan vien moi).
 */
public class OtpService {

    private static final int OTP_LENGTH = 6;
    private static final long OTP_TTL_MS = 5 * 60 * 1000L; // 5 phut
    private static final int MAX_ATTEMPTS = 5;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final MailSender mailSender = new MailSender();

    /** key = email, value = phien OTP dang cho xac nhan */
    private final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();

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
        mailSender.send(
            toEmail,
            "Mã xác nhận đăng ký tài khoản",
            "Mã xác nhận (OTP) của bạn là: " + otp + "\n\n" +
            "Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai."
        );
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