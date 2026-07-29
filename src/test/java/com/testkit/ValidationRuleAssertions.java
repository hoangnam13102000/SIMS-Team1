package com.testkit;

import com.validation.ValidationRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Assertion helper generic cho bat ky ValidationRule<T> nao (khong phu thuoc
 * field/model cu the). Dung chung khi copy package com.validation sang
 * project khac (Django/Spring/Swing gi cung duoc, mien la Java).
 *
 * Vi du dung:
 *   ValidationRuleAssertions.assertValid(Rules.email("loi"), "a@b.com");
 *   ValidationRuleAssertions.assertInvalid(Rules.email("loi"), "sai", "loi");
 */
public final class ValidationRuleAssertions {

    private ValidationRuleAssertions() {
    }

    public static <T> void assertValid(ValidationRule<T> rule, T value) {
        assertNull(rule.validate(value), "Ky vong gia tri hop le nhung rule tra ve loi");
    }

    public static <T> void assertInvalid(ValidationRule<T> rule, T value, String expectedMessage) {
        assertEquals(expectedMessage, rule.validate(value));
    }
}