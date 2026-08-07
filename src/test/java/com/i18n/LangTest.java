package com.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LangTest {

    @AfterEach
    void restoreVietnamese() {
        Lang.setLocale(Locale.forLanguageTag("vi"));
    }

    @Test
    void loadsVietnamesePropertiesAsUtf8() {
        Lang.setLocale(Locale.forLanguageTag("vi"));

        assertEquals("Quên mật khẩu?", Lang.get("login.forgotPassword"));
        assertEquals("Khôi phục mật khẩu", Lang.get("forgot.frame.title"));
        assertFalse(Lang.get("sidebar.customers").contains("\uFFFD"));
    }

    @Test
    void forgotPasswordKeysExistInEnglish() {
        Lang.setLocale(Locale.ENGLISH);

        assertEquals("Reset password", Lang.get("forgot.frame.title"));
        assertEquals("Resend in 59 seconds",
                Lang.get("forgot.otp.resendCountdown", 59));
    }
}