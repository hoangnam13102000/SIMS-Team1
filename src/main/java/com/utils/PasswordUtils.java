package com.utils;

import org.mindrot.jbcrypt.BCrypt;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Bam mat khau bang BCrypt (jbcrypt): tu sinh salt ngau nhien + cost factor,
 * thay the SHA-256 thuan (khong salt, khong cost factor) truoc day.
 *
 * Van giu lai legacySha256Hash()/isBCryptHash() de UserDAO nhan dien va tuong
 * thich nguoc voi cac hash SHA-256 da luu san trong DB tu truoc khi migrate.
 * Khi mot user dang nhap thanh cong bang mat khau kiem tra ra hash cu, UserDAO
 * se tu dong rehash lai sang BCrypt ngay luc do (khong the rehash hang loat vi
 * DB khong con luu mat khau goc, chi luu hash).
 */
public class PasswordUtils {

    /** Cost factor cho BCrypt (2^12 vong lap). 10-12 la muc pho bien, can bang
     *  giua bao mat va thoi gian xu ly khi dang nhap. */
    private static final int BCRYPT_COST = 12;

    /** Hash mat khau moi bang BCrypt. Dung cho dang ky, doi mat khau va rehash. */
    public static String hash(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /** Kiem tra rawPassword co khop voi storedHash dang BCrypt khong. */
    public static boolean verify(String rawPassword, String storedHash) {
        return BCrypt.checkpw(rawPassword, storedHash);
    }

    /**
     * Nhan dien storedHash co phai dinh dang BCrypt khong (bat dau bang
     * $2a$, $2b$ hoac $2y$). Dung de phan biet voi hash SHA-256 cu (hex 64 ky tu).
     */
    public static boolean isBCryptHash(String storedHash) {
        return storedHash != null
                && (storedHash.startsWith("$2a$") || storedHash.startsWith("$2b$") || storedHash.startsWith("$2y$"));
    }

    /**
     * Hash SHA-256 khong salt kieu cu. CHI dung de kiem tra tuong thich nguoc
     * voi cac tai khoan chua duoc rehash sang BCrypt - KHONG dung de tao hash moi.
     */
    @Deprecated
    public static String legacySha256Hash(String rawPassword) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(rawPassword.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            throw new RuntimeException("Khong the hash mat khau", e);
        }
    }
}