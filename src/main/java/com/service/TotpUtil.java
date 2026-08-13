// src/main/java/com/service/TotpUtil.java
package com.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Trien khai TOTP thuan (RFC 6238) tren nen HMAC-SHA1 co san trong javax.crypto -
 * KHONG can them thu vien ngoai (Google Authenticator/Authy tuong thich chuan
 * nay). Ma hoa lien quan (secret luu DB) do CryptoUtil dam nhiem o tang goi.
 */
public final class TotpUtil {

    private static final String HMAC_ALGO = "HmacSHA1";
    private static final int SECRET_BYTES = 20;      // 160 bit, chuan pho bien nhat
    private static final int CODE_DIGITS = 6;
    private static final int STEP_SECONDS = 30;
    private static final int ALLOWED_DRIFT_STEPS = 1; // cho phep lech +-1 buoc (~30s) do dong ho thiet bi

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private TotpUtil() {
    }

    /** Sinh secret ngau nhien, tra ve dang Base32 (dinh dang chuan de nguoi dung nhap tay/QR neu can). */
    public static String generateSecretBase32() {
        byte[] raw = new byte[SECRET_BYTES];
        new SecureRandom().nextBytes(raw);
        return base32Encode(raw);
    }

    /** Ma hien tai theo dong ho he thong - dung khi can debug/hien thi (khong dung de xac thuc). */
    public static String currentCode(String secretBase32) {
        return generateCode(secretBase32, System.currentTimeMillis() / 1000L / STEP_SECONDS);
    }

    /** Kiem tra ma nguoi dung nhap co khop khong, cho phep lech +-1 buoc thoi gian. */
    public static boolean verifyCode(String secretBase32, String inputCode) {
        if (secretBase32 == null || inputCode == null || !inputCode.matches("\\d{6}")) {
            return false;
        }
        long currentStep = System.currentTimeMillis() / 1000L / STEP_SECONDS;
        for (int drift = -ALLOWED_DRIFT_STEPS; drift <= ALLOWED_DRIFT_STEPS; drift++) {
            if (generateCode(secretBase32, currentStep + drift).equals(inputCode)) {
                return true;
            }
        }
        return false;
    }

    /** URI chuan de sinh QR code cho Google Authenticator/Authy/Microsoft Authenticator. */
    public static String buildOtpAuthUri(String issuer, String accountName, String secretBase32) {
        String label = urlEncode(issuer) + ":" + urlEncode(accountName);
        return "otpauth://totp/" + label
                + "?secret=" + secretBase32
                + "&issuer=" + urlEncode(issuer)
                + "&digits=" + CODE_DIGITS
                + "&period=" + STEP_SECONDS
                + "&algorithm=SHA1";
    }

    private static String generateCode(String secretBase32, long timeStep) {
        try {
            byte[] key = base32Decode(secretBase32);
            byte[] msg = ByteBuffer.allocate(8).putLong(timeStep).array();

            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(key, HMAC_ALGO));
            byte[] hash = mac.doFinal(msg);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);

            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the sinh ma TOTP: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String value) {
        return value.replace(" ", "%20").replace(":", "%3A");
    }

    private static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int bits = 0, value = 0;
        for (byte b : data) {
            value = (value << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                sb.append(BASE32_ALPHABET[(value >>> (bits - 5)) & 0x1F]);
                bits -= 5;
            }
        }
        if (bits > 0) {
            sb.append(BASE32_ALPHABET[(value << (5 - bits)) & 0x1F]);
        }
        return sb.toString();
    }

    private static byte[] base32Decode(String base32) {
        String clean = base32.trim().toUpperCase(Locale.ROOT).replace("=", "");
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int bits = 0, value = 0;
        for (char c : clean.toCharArray()) {
            int idx = new String(BASE32_ALPHABET).indexOf(c);
            if (idx < 0) {
                continue;
            }
            value = (value << 5) | idx;
            bits += 5;
            if (bits >= 8) {
                out.write((value >>> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}