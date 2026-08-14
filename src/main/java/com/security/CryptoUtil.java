package com.security;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Lop tien ich ma hoa/giai ma dung chung cho toan bo he thong "config bao mat".
 * Thuat toan: AES-256-GCM (vua ma hoa vua chong gia mao/sua doi noi dung).
 *
 * Dinh dang du lieu da ma hoa (1 chuoi Base64 duy nhat):
 *   Base64( IV(12 byte) + cipherText + authTag(16 byte) )
 *
 * Lop nay khong tu quan ly key - viec sinh/luu/doc master key do AppConfig
 * va ConfigTool dam nhiem (doc tu bien moi truong MYSHOP_CONFIG_KEY).
 */
public final class CryptoUtil {

    private static final String KEY_ALGO = "AES";
    private static final String CIPHER_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE_BITS = 256;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final String CHARSET = "UTF-8";

    private static final String PBKDF2_ALGO = "PBKDF2WithHmacSHA256";
    private static final int PBKDF2_ITERATIONS = 200_000;
    public static final int PASSPHRASE_SALT_LENGTH_BYTES = 16;

    private CryptoUtil() {
        // chi chua static method, khong cho khoi tao
    }

    /**
     * Sinh mot master key AES-256 hoan toan moi va ngau nhien.
     * Tra ve dang Base64 de tien luu vao bien moi truong MYSHOP_CONFIG_KEY.
     */
    public static String generateKeyBase64() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KEY_ALGO);
            keyGenerator.init(AES_KEY_SIZE_BITS, new SecureRandom());
            SecretKey secretKey = keyGenerator.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Khong the sinh master key: " + e.getMessage(), e);
        }
    }

    /** Giai ma chuoi Base64 (lay tu bien moi truong) thanh SecretKey de dung cho encrypt/decrypt. */
    public static SecretKey decodeKey(String base64Key) {
        if (base64Key == null || base64Key.trim().isEmpty()) {
            throw new IllegalArgumentException("Master key dang rong.");
        }
        byte[] rawKey;
        try {
            rawKey = Base64.getDecoder().decode(base64Key.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Master key khong phai chuoi Base64 hop le. Dung lenh 'genkey' cua ConfigTool de tao key moi.", e);
        }
        if (rawKey.length != 32) {
            throw new IllegalArgumentException(
                "Master key khong hop le: can dung 32 byte (AES-256) sau khi giai ma Base64, "
                + "nhung nhan duoc " + rawKey.length + " byte. Dung lenh 'genkey' cua ConfigTool de tao key moi.");
        }
        return new SecretKeySpec(rawKey, KEY_ALGO);
    }

    /** Ma hoa 1 chuoi plaintext bat ky, tra ve chuoi Base64 (IV + cipherText + tag). */
    public static String encrypt(String plaintext, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext.getBytes(CHARSET));

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Loi encoding khi ma hoa config: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Loi ma hoa config: " + e.getMessage(), e);
        }
    }

    /** Giai ma chuoi Base64 (IV + cipherText + tag) tra ve lai plaintext ban dau. */
    public static String decrypt(String base64Combined, SecretKey key) {
        try {
            byte[] combined = Base64.getDecoder().decode(base64Combined.trim());
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Du lieu ma hoa qua ngan, co the file config bi hong.");
            }

            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainBytes = cipher.doFinal(cipherBytes);

            return new String(plainBytes, CHARSET);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Khong giai ma duoc config: sai MYSHOP_CONFIG_KEY hoac file config da bi hong/sua doi. "
                + e.getMessage(), e);
        }
    }

    /**
     * Ma hoa 1 mang byte bat ky (KHONG qua String/UTF-8), tra ve mang byte tho
     * dang IV(12) + cipherText + authTag(16) - dung cho du lieu nhi phan nhu
     * file backup (.sql text cua MySQL) deu an toan,
     * khac voi encrypt(String,...) o tren se lam hong byte nhi phan khong
     * phai UTF-8 hop le).
     */
    public static byte[] encryptBytes(byte[] plaintext, SecretKey key) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plaintext);

            byte[] combined = new byte[iv.length + cipherBytes.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherBytes, 0, combined, iv.length, cipherBytes.length);
            return combined;
        } catch (Exception e) {
            throw new IllegalStateException("Loi ma hoa du lieu nhi phan: " + e.getMessage(), e);
        }
    }

    /** Giai ma mang byte tao boi {@link #encryptBytes}. */
    public static byte[] decryptBytes(byte[] combined, SecretKey key) {
        try {
            if (combined.length <= GCM_IV_LENGTH_BYTES) {
                throw new IllegalArgumentException("Du lieu ma hoa qua ngan.");
            }
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherBytes = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, GCM_IV_LENGTH_BYTES);
            System.arraycopy(combined, GCM_IV_LENGTH_BYTES, cipherBytes, 0, cipherBytes.length);

            Cipher cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return cipher.doFinal(cipherBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Khong giai ma duoc du lieu nhi phan: " + e.getMessage(), e);
        }
    }

    /**
     * Sinh 1 salt ngau nhien (16 byte) dung cho dan xuat khoa tu passphrase.
     * Moi lan ma hoa (vd moi file backup) nen dung 1 salt moi, va salt nay
     * KHONG can giu bi mat - thuong duoc luu chung voi du lieu da ma hoa.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[PASSPHRASE_SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Dan xuat 1 SecretKey AES-256 tu passphrase (chuoi do nguoi dung dat)
     * bang PBKDF2WithHmacSHA256 (200.000 vong lap) + salt truyen vao.
     * Dung cho cac truong hop muon ma hoa bang passphrase de nho thay vi
     * mot master key dang Base64 kho nho (vd ma hoa file backup).
     *
     * QUAN TRONG: cung 1 passphrase + salt luon cho ra cung 1 key - vi vay
     * salt phai duoc luu lai (vd trong chinh file da ma hoa) de giai ma sau
     * nay dung lai chinh xac salt do.
     */
    public static SecretKey deriveKeyFromPassphrase(String passphrase, byte[] salt) {
        if (passphrase == null || passphrase.isEmpty()) {
            throw new IllegalArgumentException("Passphrase dang rong.");
        }
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGO);
            PBEKeySpec spec = new PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, AES_KEY_SIZE_BITS);
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, KEY_ALGO);
        } catch (Exception e) {
            throw new IllegalStateException("Khong the dan xuat khoa AES tu passphrase: " + e.getMessage(), e);
        }
    }
}
