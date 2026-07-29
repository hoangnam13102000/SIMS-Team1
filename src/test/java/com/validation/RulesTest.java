package com.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RulesTest {

    private static final String ERR = "loi";

    @Test
    void required_giaTriRongHoacNull_traVeLoi() {
        ValidationRule<String> rule = Rules.required(ERR);
        assertEquals(ERR, rule.validate(null));
        assertEquals(ERR, rule.validate(""));
        assertEquals(ERR, rule.validate("   "));
        assertNull(rule.validate("co gia tri"));
    }

    @Test
    void minLength_ngoaiLe_khongBatNullVaCoiLaHopLe() {
        // Ghi chu quan trong: minLength/maxLength KHONG tu bat null,
        // phai ket hop voi required() de bat truong hop bo trong.
        ValidationRule<String> rule = Rules.minLength(5, ERR);
        assertNull(rule.validate(null));
        assertEquals(ERR, rule.validate("abc"));
        assertNull(rule.validate("abcde"));
    }

    @Test
    void maxLength_vuotQuaGioiHan_traVeLoi() {
        ValidationRule<String> rule = Rules.maxLength(5, ERR);
        assertEquals(ERR, rule.validate("abcdef"));
        assertNull(rule.validate("abcde"));
        assertNull(rule.validate(null));
    }

    @Test
    void matches_khopRegex_hopLe() {
        ValidationRule<String> rule = Rules.matches("^0\\d{9}$", ERR);
        assertNull(rule.validate("0912345678"));
        assertEquals(ERR, rule.validate("12345"));
        assertEquals(ERR, rule.validate(null));
    }

    @Test
    void email_cacTruongHopHopLeVaKhongHopLe() {
        ValidationRule<String> rule = Rules.email(ERR);
        assertNull(rule.validate("test@example.com"));
        assertEquals(ERR, rule.validate("khong-phai-email"));
        assertEquals(ERR, rule.validate("thieu@ten"));
        assertEquals(ERR, rule.validate(null));
    }

    @Test
    void integer_soNguyenHopLe_khongLoi() {
        ValidationRule<String> rule = Rules.integer(ERR);
        assertNull(rule.validate("123"));
        assertNull(rule.validate("-45"));
        assertEquals(ERR, rule.validate("abc"));
        assertEquals(ERR, rule.validate(null));
    }

    @Test
    void longNumber_hopLeVaKhongHopLe() {
        ValidationRule<String> rule = Rules.longNumber(ERR);
        assertNull(rule.validate("9999999999"));
        assertEquals(ERR, rule.validate("khong-phai-so"));
    }

    @Test
    void positiveLong_chiChapNhanSoDuong() {
        ValidationRule<String> rule = Rules.positiveLong(ERR);
        assertNull(rule.validate("100"));
        assertEquals(ERR, rule.validate("0"));
        assertEquals(ERR, rule.validate("-5"));
        assertEquals(ERR, rule.validate("khong-phai-so"));
    }

    @Test
    void equalsTo_soSanhVoiGiaTriDongTuSupplier() {
        String[] otherValue = {"123456"};
        ValidationRule<String> rule = Rules.equalsTo(() -> otherValue[0], ERR);

        assertNull(rule.validate("123456"));
        assertEquals(ERR, rule.validate("khac"));

        otherValue[0] = "moi-thay-doi"; // gia lap password field thay doi sau
        assertEquals(ERR, rule.validate("123456"));
    }

    @Test
    void equalsTo_caHaiNull_vanCoiLaKhongHopLe() {
        ValidationRule<String> rule = Rules.equalsTo(() -> null, ERR);
        assertEquals(ERR, rule.validate(null));
    }

    @Test
    void custom_dungPredicateTuyChinh() {
        ValidationRule<Integer> rule = Rules.custom(v -> v != null && v % 2 == 0, "phai la so chan");
        assertNull(rule.validate(4));
        assertEquals("phai la so chan", rule.validate(3));
    }
}