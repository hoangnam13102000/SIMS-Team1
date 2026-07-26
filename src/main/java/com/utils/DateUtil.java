package com.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Tien ich xu ly ngay/gio dung chung cho toan bo ung dung: format, parse,
 * tinh "x phut truoc", so sanh/loc theo ngay... Khong gan voi domain nao
 * (Order, Phone...) nen co the copy nguyen sang du an khac.
 *
 * Luu y: SimpleDateFormat KHONG thread-safe nen moi lan format/parse deu tao
 * instance moi - khong dang ke voi UI Swing (single-thread, it goi).
 */
public final class DateUtil {

    private DateUtil() {}

    public static final String PATTERN_DATE = "dd/MM/yyyy";
    public static final String PATTERN_DATE_TIME = "dd/MM/yyyy HH:mm";
    public static final String PATTERN_DATE_TIME_FULL = "dd/MM/yyyy HH:mm:ss";
    public static final String PATTERN_TIME = "HH:mm";
    public static final String PATTERN_TIME_DATE = "HH:mm dd/MM";
    public static final String PATTERN_TIME_DATE_FULL = "HH:mm dd/MM/yyyy";

    private static SimpleDateFormat sdf(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern);
        f.setLenient(false);
        return f;
    }

    // ---------- Format ----------

    public static String format(Date date, String pattern) {
        if (date == null) return "";
        return sdf(pattern).format(date);
    }

    public static String format(long epochMillis, String pattern) {
        if (epochMillis <= 0) return "";
        return sdf(pattern).format(new Date(epochMillis));
    }

    public static String formatDate(Date date) { return format(date, PATTERN_DATE); }
    public static String formatDateTime(Date date) { return format(date, PATTERN_DATE_TIME); }
    public static String formatTime(Date date) { return format(date, PATTERN_TIME); }

    public static String formatDate(long epochMillis) { return format(epochMillis, PATTERN_DATE); }
    public static String formatDateTime(long epochMillis) { return format(epochMillis, PATTERN_DATE_TIME); }

    // ---------- Parse ----------

    public static Date parse(String text, String pattern) {
        if (text == null || text.isBlank()) return null;
        try {
            return sdf(pattern).parse(text.trim());
        } catch (ParseException e) {
            return null;
        }
    }

    public static Date parseDate(String text) { return parse(text, PATTERN_DATE); }
    public static Date parseDateTime(String text) { return parse(text, PATTERN_DATE_TIME); }

    // ---------- SQL helpers (tien cho DAO / SQL Server) ----------

    public static java.sql.Timestamp toSqlTimestamp(Date date) {
        return date == null ? null : new java.sql.Timestamp(date.getTime());
    }

    public static java.sql.Date toSqlDate(Date date) {
        return date == null ? null : new java.sql.Date(date.getTime());
    }

    // ---------- So sanh / tinh toan ----------

    public static boolean isSameDay(Date d1, Date d2) {
        if (d1 == null || d2 == null) return false;
        Calendar c1 = Calendar.getInstance(); c1.setTime(d1);
        Calendar c2 = Calendar.getInstance(); c2.setTime(d2);
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
    }

    public static boolean isToday(Date date) { return isSameDay(date, new Date()); }

    /** Dau ngay (00:00:00.000). Dung de loc "tu ngay X" (vi du Date range picker). */
    public static Date startOfDay(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date == null ? new Date() : date);
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0);
        return c.getTime();
    }

    /** Cuoi ngay (23:59:59.999). Dung de loc "den ngay Y" (bao gom ca ngay Y). */
    public static Date endOfDay(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date == null ? new Date() : date);
        c.set(Calendar.HOUR_OF_DAY, 23); c.set(Calendar.MINUTE, 59);
        c.set(Calendar.SECOND, 59); c.set(Calendar.MILLISECOND, 999);
        return c.getTime();
    }

    public static Date addDays(Date date, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(date == null ? new Date() : date);
        c.add(Calendar.DAY_OF_MONTH, days);
        return c.getTime();
    }

    public static long daysBetween(Date from, Date to) {
        if (from == null || to == null) return 0;
        return TimeUnit.MILLISECONDS.toDays(Math.abs(to.getTime() - from.getTime()));
    }

    /**
     * Hien thi kieu "Vua xong / 5 phut truoc / 3 gio truoc / 2 ngay truoc",
     * qua 7 ngay thi tra ve gio/ngay cu the. Dung cho thong bao, chat, activity log.
     */
    public static String timeAgo(Date date) {
        if (date == null) return "";
        long diffMs = System.currentTimeMillis() - date.getTime();
        if (diffMs < 0) diffMs = 0;

        long seconds = diffMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (seconds < 60) return "Vừa xong";
        if (minutes < 60) return minutes + " phút trước";
        if (hours < 24) return hours + " giờ trước";
        if (days < 7) return days + " ngày trước";
        return isSameDay(date, new Date()) ? formatTime(date) : formatDate(date);
    }

    public static String timeAgo(long epochMillis) {
        return epochMillis <= 0 ? "" : timeAgo(new Date(epochMillis));
    }
}