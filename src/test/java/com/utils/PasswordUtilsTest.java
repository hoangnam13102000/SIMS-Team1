package com.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PasswordUtilsTest {

    @Test
    void hash_cungInput_traVeCungHash_vìKhongCoSalt() {
        String hash1 = PasswordUtils.hash("MatKhau123");
        String hash2 = PasswordUtils.hash("MatKhau123");
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_inputKhacNhau_raHashKhacNhau() {
        assertNotEquals(PasswordUtils.hash("MatKhau123"), PasswordUtils.hash("MatKhau124"));
    }

    @Test
    void hash_traVeChuoiHex64KyTu_ChoSHA256() {
        String hash = PasswordUtils.hash("bat-ky-mat-khau-nao");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("^[0-9a-f]{64}$"), "Hash phai la hex thuong, 64 ky tu");
    }

    @Test
    void hash_khongTraVePlaintextGocTrongKetQua() {
        String raw = "password";
        assertFalse(PasswordUtils.hash(raw).contains(raw));
    }
}