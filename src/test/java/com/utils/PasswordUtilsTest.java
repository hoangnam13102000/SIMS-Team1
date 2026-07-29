package com.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void hash_cungInput_traVeCungHash_vìKhongCoSalt() {
        String hash1 = PasswordUtils.hash("MatKhau123");
        String hash2 = PasswordUtils.hash("MatKhau123");
        assertNotEquals(hash1, hash2);
        assertTrue(PasswordUtils.verify("MatKhau123", hash1));
        assertTrue(PasswordUtils.verify("MatKhau123", hash2));
    }

    @Test
    void hash_inputKhacNhau_raHashKhacNhau() {
        assertNotEquals(PasswordUtils.hash("MatKhau123"), PasswordUtils.hash("MatKhau124"));
    }

    @Test
    void hash_traVeChuoiHex64KyTu_ChoSHA256() {
        String hash = PasswordUtils.hash("bat-ky-mat-khau-nao");
        assertEquals(60, hash.length());
        assertTrue(PasswordUtils.isBCryptHash(hash));
        assertTrue(PasswordUtils.verify("bat-ky-mat-khau-nao", hash));
    }

    @Test
    void hash_khongTraVePlaintextGocTrongKetQua() {
        String raw = "password";
        assertFalse(PasswordUtils.hash(raw).contains(raw));
    }
}
