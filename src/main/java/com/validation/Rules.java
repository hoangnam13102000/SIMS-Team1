package com.validation;

import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public final class Rules {

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");

    private static final Pattern PHONE_VN_PATTERN =
            Pattern.compile("^(0\\d{9}|\\+84\\d{9})$");

    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^\\d+$");

    private Rules() {}

    public static ValidationRule<String> required(String message) {
        return v -> (v == null || v.trim().isEmpty()) ? message : null;
    }

    public static ValidationRule<String> minLength(int min, String message) {
        return v -> (v != null && v.trim().length() < min) ? message : null;
    }

    public static ValidationRule<String> maxLength(int max, String message) {
        return v -> (v != null && v.trim().length() > max) ? message : null;
    }

    public static ValidationRule<String> matches(String regex, String message) {
        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        return v -> (v == null || !pattern.matcher(v.trim()).matches()) ? message : null;
    }

    public static ValidationRule<String> email(String message) {
        return v -> (v == null || !EMAIL_PATTERN.matcher(v.trim()).matches()) ? message : null;
    }

    /** So dien thoai VN: bat dau bang 0 + 9 chu so, hoac +84 + 9 chu so. */
    public static ValidationRule<String> phoneVn(String message) {
        return v -> (v == null || !PHONE_VN_PATTERN.matcher(v.trim()).matches()) ? message : null;
    }

    public static ValidationRule<String> digitsOnly(String message) {
        return v -> (v == null || !DIGITS_ONLY_PATTERN.matcher(v.trim()).matches()) ? message : null;
    }

    public static ValidationRule<String> exactLength(int length, String message) {
        return v -> (v != null && v.trim().length() == length) ? null : message;
    }

    public static ValidationRule<String> integer(String message) {
        return v -> { try { Integer.parseInt(v.trim()); return null; } catch (Exception e) { return message; } };
    }

    public static ValidationRule<String> longNumber(String message) {
        return v -> { try { Long.parseLong(v.trim()); return null; } catch (Exception e) { return message; } };
    }

    public static ValidationRule<String> positiveLong(String message) {
        return v -> { try { return Long.parseLong(v.trim()) > 0 ? null : message; } catch (Exception e) { return message; } };
    }

    public static ValidationRule<String> equalsTo(Supplier<String> other, String message) {
        return v -> (v == null || other.get() == null || !v.equals(other.get())) ? message : null;
    }

    public static <T> ValidationRule<T> custom(Predicate<T> isValid, String message) {
        return v -> isValid.test(v) ? null : message;
    }
}