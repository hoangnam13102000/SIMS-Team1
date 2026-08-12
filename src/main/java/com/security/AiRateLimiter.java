package com.security;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Chan hanh vi do quet (enumeration) / spam khi mot danh tinh (khach hoac
 * nhan vien) goi lien tuc cac tool AI nhay cam trong thoi gian ngan - vi du
 * goi get_employee_salary hang chuc lan voi ten khac nhau de do het luong
 * toan cong ty, hoac spam create_employee de tao rac tai khoan.
 * <p>
 * Khong thay the cho kiem tra quyen (PermissionManager) - day la lop bo sung,
 * chan theo TAN SUAT chu khong chan theo QUYEN. Mot nguoi dung du quyen van
 * co the bi khoa tam neu goi qua nhieu lan trong thoi gian ngan.
 * <p>
 * Luu trong bo nho (khong ben vung qua lan restart app) - phu hop quy mo
 * 1 ung dung desktop Swing, khong can ha tang rieng.
 */
public final class AiRateLimiter {

    private static final AiRateLimiter INSTANCE = new AiRateLimiter();

    public static AiRateLimiter getInstance() {
        return INSTANCE;
    }

    private AiRateLimiter() {
    }

    /** Cua so thoi gian tinh tan suat. */
    private static final long WINDOW_MS = 5 * 60 * 1000L; // 5 phut

    /** So lan goi toi da trong 1 cua so, cho tool NHAY CAM (co requiredPermissions). */
    private static final int SENSITIVE_LIMIT = 15;

    /** So lan goi toi da trong 1 cua so, cho tool THUONG (khong yeu cau quyen, vd search_products). */
    private static final int NORMAL_LIMIT = 60;

    /** Thoi gian khoa tam sau khi vuot nguong (ap dung cho MOI tool cua danh tinh do, khong chi tool vua vi pham). */
    private static final long COOLDOWN_MS = 10 * 60 * 1000L; // 10 phut

    /** key = "identity::toolName" -> danh sach timestamp cac lan goi gan day. */
    private final Map<String, Deque<Long>> callHistory = new ConcurrentHashMap<>();

    /** key = identity -> thoi diem het khoa (nếu đang bị khoá). */
    private final Map<String, Long> blockedUntil = new ConcurrentHashMap<>();

    public Result check(String identity, String toolName, boolean sensitive) {
        if (identity == null || identity.isBlank()) identity = "UNKNOWN";
        long now = System.currentTimeMillis();

        Long until = blockedUntil.get(identity);
        if (until != null) {
            if (now < until) {
                return Result.blocked((until - now) / 1000);
            }
            blockedUntil.remove(identity);
        }

        String key = identity + "::" + toolName;
        Deque<Long> history = callHistory.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        int limit = sensitive ? SENSITIVE_LIMIT : NORMAL_LIMIT;

        synchronized (history) {
            while (!history.isEmpty() && now - history.peekFirst() > WINDOW_MS) {
                history.pollFirst();
            }
            if (history.size() >= limit) {
                blockedUntil.put(identity, now + COOLDOWN_MS);
                return Result.exceeded(COOLDOWN_MS / 1000);
            }
            history.addLast(now);
        }
        return Result.allowed();
    }

    public static final class Result {
        public final boolean allowed;
        /** true = vua cham nguong lan nay (moi bi khoa), false = da bi khoa tu truoc. */
        public final boolean justTriggered;
        public final long remainingSeconds;

        private Result(boolean allowed, boolean justTriggered, long remainingSeconds) {
            this.allowed = allowed;
            this.justTriggered = justTriggered;
            this.remainingSeconds = remainingSeconds;
        }

        static Result allowed() {
            return new Result(true, false, 0);
        }

        static Result exceeded(long remainingSeconds) {
            return new Result(false, true, remainingSeconds);
        }

        static Result blocked(long remainingSeconds) {
            return new Result(false, false, remainingSeconds);
        }
    }
}