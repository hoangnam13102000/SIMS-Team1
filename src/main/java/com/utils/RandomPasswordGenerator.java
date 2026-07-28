package com.utils;

import java.security.SecureRandom;

/**
 * Sinh mat khau ngau nhien (dung cho tai khoan nhan vien duoc Admin tao qua
 * trang Quan ly nhan vien - mat khau nay chi ton tai o dang plain-text trong
 * bo nho lien luc tao/gui email, KHONG bao gio duoc luu xuong DB hay log).
 * <p>
 * Loai bo cac ky tu de nham lan khi doc/go tay (0/O, 1/l/I) de nguoi nhan
 * email nhap lai it sai sot hon.
 */
public final class RandomPasswordGenerator {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*";
    private static final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

    private static final SecureRandom RANDOM = new SecureRandom();

    private RandomPasswordGenerator() {
    }

    /** Sinh mat khau co it nhat 1 chu hoa, 1 chu thuong, 1 so, 1 ky tu dac biet. */
    public static String generate(int length) {
        int len = Math.max(length, 8);
        char[] result = new char[len];

        result[0] = UPPER.charAt(RANDOM.nextInt(UPPER.length()));
        result[1] = LOWER.charAt(RANDOM.nextInt(LOWER.length()));
        result[2] = DIGITS.charAt(RANDOM.nextInt(DIGITS.length()));
        result[3] = SYMBOLS.charAt(RANDOM.nextInt(SYMBOLS.length()));

        for (int i = 4; i < len; i++) {
            result[i] = ALL.charAt(RANDOM.nextInt(ALL.length()));
        }

        // Xao tron de 4 ky tu dau khong luon co dang co dinh (hoa-thuong-so-ky tu dac biet).
        for (int i = result.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char tmp = result[i];
            result[i] = result[j];
            result[j] = tmp;
        }

        return new String(result);
    }

    public static String generate() {
        return generate(10);
    }
}