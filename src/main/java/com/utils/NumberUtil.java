package com.utils;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Tien ich xu ly so hoc dung chung: format, parse an toan, ep gioi han...
 * KHONG gan voi don vi tien te cu the (xem CurrencyUtil cho da tien te, hoac
 * MoneyFormatUtil rieng cho VND), nen tai su dung duoc cho bat ky du an nao
 * can lam viec voi so (pagination, thong ke, validate input...).
 */
public final class NumberUtil {

    private NumberUtil() {}

    private static final Locale VI = new Locale("vi", "VN");

    // ---------- Format ----------

    /** Vi du: 1234567 -> "1.234.567" (dau cham phan cach nghin kieu Viet Nam). */
    public static String formatThousands(long value) {
        return NumberFormat.getInstance(VI).format(value);
    }

    /** Vi du: 1234.5 voi 2 chu so -> "1.234,50". */
    public static String formatDecimal(double value, int decimals) {
        NumberFormat nf = NumberFormat.getInstance(VI);
        nf.setMinimumFractionDigits(decimals);
        nf.setMaximumFractionDigits(decimals);
        return nf.format(value);
    }

    /**
     * Rut gon kieu quoc te: 1500 -> "1.5K", 2300000 -> "2.3M", 4000000000 -> "4B".
     * Dung cho dashboard/thong ke can tiet kiem khong gian hien thi (khac ban
     * tieng Viet "tr/tỷ" cua MoneyFormatUtil).
     */
    public static String formatCompact(long value) {
        double abs = Math.abs((double) value);
        if (abs >= 1_000_000_000d) return trimZero(value / 1_000_000_000d) + "B";
        if (abs >= 1_000_000d) return trimZero(value / 1_000_000d) + "M";
        if (abs >= 1_000d) return trimZero(value / 1_000d) + "K";
        return String.valueOf(value);
    }

    private static String trimZero(double d) {
        String s = String.format(Locale.US, "%.1f", d);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Vi du: 0.856 -> "85.6%" (input la ty le 0..1). */
    public static String formatPercent(double ratio, int decimals) {
        return formatDecimal(ratio * 100, decimals) + "%";
    }

    /** Tinh phan tram cua part/total (tra ve 0 neu total = 0, tranh chia cho 0). */
    public static double percentageOf(double part, double total) {
        return total == 0 ? 0 : (part / total) * 100;
    }

    // ---------- Parse an toan (khong bao gio nem exception ra UI) ----------

    public static int parseIntSafe(String text, int defaultValue) {
        try { return Integer.parseInt(text.trim()); }
        catch (Exception e) { return defaultValue; }
    }

    public static long parseLongSafe(String text, long defaultValue) {
        try { return Long.parseLong(text.trim()); }
        catch (Exception e) { return defaultValue; }
    }

    public static double parseDoubleSafe(String text, double defaultValue) {
        try { return Double.parseDouble(text.trim().replace(",", ".")); }
        catch (Exception e) { return defaultValue; }
    }

    public static boolean isNumeric(String text) {
        if (text == null || text.isBlank()) return false;
        try { Double.parseDouble(text.trim()); return true; }
        catch (NumberFormatException e) { return false; }
    }

    // ---------- Gioi han / lam tron ----------

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static double round(double value, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(value * factor) / factor;
    }
}