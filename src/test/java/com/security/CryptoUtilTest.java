package com.security;

import org.junit.jupiter.api.Test;
import javax.crypto.SecretKey;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test cho CryptoUtil (AES-256-GCM). Class nay khong phu thuoc DB/UI/file he
 * thong nen chay duoc hoan toan doc lap, khong can moi truong that.
 */
class CryptoUtilTest {

    @Test
    void encrypt_thenDecrypt_traVeDungPlaintextBanDau() {
        SecretKey key = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        String plaintext = "db.password=SuperSecret123!";

        String encrypted = CryptoUtil.encrypt(plaintext, key);
        String decrypted = CryptoUtil.decrypt(encrypted, key);

        assertEquals(plaintext, decrypted);
        assertNotEquals(plaintext, encrypted, "Ban ma phai khac plaintext");
    }

    @Test
    void encrypt_hoTroChuoiRong() {
        SecretKey key = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        assertEquals("", CryptoUtil.decrypt(CryptoUtil.encrypt("", key), key));
    }

    @Test
    void decrypt_saiKey_nemLoi() {
        SecretKey keyA = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        SecretKey keyB = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        String encrypted = CryptoUtil.encrypt("secret", keyA);

        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(encrypted, keyB));
    }

    @Test
    void decrypt_duLieuBiSuaDoi_nemLoiDoGcmTagKhongKhop() {
        SecretKey key = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        String encrypted = CryptoUtil.encrypt("secret-value", key);

        // Doi 1 ky tu giua chuoi Base64 de gia lap du lieu bi hong/gia mao
        char[] chars = encrypted.toCharArray();
        int mid = chars.length / 2;
        chars[mid] = chars[mid] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(tampered, key));
    }

    @Test
    void decrypt_duLieuQuaNgan_nemLoi() {
        SecretKey key = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        String tooShort = java.util.Base64.getEncoder().encodeToString(new byte[]{1, 2, 3});
        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(tooShort, key));
    }

    @Test
    void generateKeyBase64_taoKeyAES256HopLe() {
        String base64Key = CryptoUtil.generateKeyBase64();
        SecretKey key = CryptoUtil.decodeKey(base64Key); // khong nem loi = key hop le
        assertEquals("AES", key.getAlgorithm());
        assertEquals(32, key.getEncoded().length); // 256 bit = 32 byte
    }

    @Test
    void decodeKey_rongHoacNull_nemLoi() {
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decodeKey(null));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decodeKey(""));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decodeKey("   "));
    }

    @Test
    void decodeKey_khongPhaiBase64HopLe_nemLoi() {
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decodeKey("khong-phai-base64!!!"));
    }

    @Test
    void decodeKey_saiDoDaiSau32Byte_nemLoi() {
        // Base64 hop le nhung giai ma ra chi 16 byte (AES-128), khong phai 32 byte yeu cau
        String base64Of16Bytes = java.util.Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.decodeKey(base64Of16Bytes));
    }

    @Test
    void encryptBytes_thenDecryptBytes_hoatDongVoiDuLieuNhiPhan() {
        SecretKey key = CryptoUtil.decodeKey(CryptoUtil.generateKeyBase64());
        byte[] binaryData = new byte[]{0, 1, 2, 127, -128, -1, 55, -55};

        byte[] encrypted = CryptoUtil.encryptBytes(binaryData, key);
        byte[] decrypted = CryptoUtil.decryptBytes(encrypted, key);

        assertArrayEquals(binaryData, decrypted);
    }

    @Test
    void deriveKeyFromPassphrase_cungPassphraseCungSalt_traVeCungKey() {
        byte[] salt = CryptoUtil.generateSalt();
        SecretKey key1 = CryptoUtil.deriveKeyFromPassphrase("MatKhauBiMat!", salt);
        SecretKey key2 = CryptoUtil.deriveKeyFromPassphrase("MatKhauBiMat!", salt);

        assertEquals(key1, key2);
        assertArrayEquals(key1.getEncoded(), key2.getEncoded());
    }

    @Test
    void deriveKeyFromPassphrase_saltKhacNhau_raKeyKhacNhau() {
        SecretKey key1 = CryptoUtil.deriveKeyFromPassphrase("MatKhauBiMat!", CryptoUtil.generateSalt());
        SecretKey key2 = CryptoUtil.deriveKeyFromPassphrase("MatKhauBiMat!", CryptoUtil.generateSalt());

        assertNotEquals(key1, key2);
    }

    @Test
    void deriveKeyFromPassphrase_passphraseRong_nemLoi() {
        byte[] salt = CryptoUtil.generateSalt();
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.deriveKeyFromPassphrase(null, salt));
        assertThrows(IllegalArgumentException.class, () -> CryptoUtil.deriveKeyFromPassphrase("", salt));
    }

    @Test
    void generateSalt_traVeDungDoDai() {
        assertEquals(CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES, CryptoUtil.generateSalt().length);
    }
}