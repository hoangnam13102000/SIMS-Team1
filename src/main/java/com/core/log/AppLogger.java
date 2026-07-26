package com.core.log;

import com.event.AppEventBus;
import com.event.LogWrittenEvent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Ghi log dùng chung toàn app - không biết gì về domain.
 * Đã nâng cấp hỗ trợ LogLevel, Throwable, và snapshot JSON trước/sau thay đổi
 * (oldValue/newValue) để phục vụ audit trail chi tiết theo từng field.
 */
public final class AppLogger {

    private static final AppLogger INSTANCE = new AppLogger();
    public static AppLogger getInstance() { return INSTANCE; }

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "app-logger");
        t.setDaemon(true);
        return t;
    });

    private volatile LogSink sink;

    private AppLogger() {}

    public void setSink(LogSink sink) {
        this.sink = sink;
    }

    // ==================== CÁC PHƯƠNG THỨC GHI LOG ====================

    /** Ghi log audit cơ bản (dùng nhiều nhất) */
    public void log(String username, String action, String entityType, String description) {
        log(username, LogLevel.AUDIT, action, entityType, description, null, null, null);
    }

    /** Ghi log audit kèm snapshot JSON truoc/sau thay doi (dung cho CREATE/UPDATE/DELETE). */
    public void log(String username, String action, String entityType, String description,
                     String oldValueJson, String newValueJson) {
        log(username, LogLevel.AUDIT, action, entityType, description, null, oldValueJson, newValueJson);
    }

    /** Ghi log đầy đủ với level (khong kem snapshot - tuong thich nguoc). */
    public void log(String username, LogLevel level, String action,
                    String entityType, String description, Throwable ex) {
        log(username, level, action, entityType, description, ex, null, null);
    }

    /** Ghi log đầy đủ với level kèm snapshot JSON truoc/sau thay doi. */
    public void log(String username, LogLevel level, String action, String entityType,
                     String description, Throwable ex, String oldValueJson, String newValueJson) {

        LogSink currentSink = this.sink;
        if (currentSink == null) return;

        LogEntry entry = new LogEntry(username, level, action, entityType, description, ex,
                oldValueJson, newValueJson);

        writer.submit(() -> {
            try {
                currentSink.write(entry);
                AppEventBus.getInstance().publish(new LogWrittenEvent());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // ==================== CÁC HÀM TIỆN ÍCH ====================
    // Dung entityType = "SYSTEM" (khong duoc null) vi cot EntityType trong
    // ActivityLogs la NOT NULL - truyen null se lam INSERT that bai
    // (xem SQLServerException "Cannot insert the value NULL into column 'EntityType'").

    public void info(String username, String message) {
        log(username, LogLevel.INFO, "INFO", "SYSTEM", message, null);
    }

    public void warn(String username, String message) {
        log(username, LogLevel.WARN, "WARN", "SYSTEM", message, null);
    }

    public void error(String username, String message, Throwable ex) {
        log(username, LogLevel.ERROR, "ERROR", "SYSTEM", message, ex);
    }

    public void debug(String username, String message) {
        log(username, LogLevel.DEBUG, "DEBUG", "SYSTEM", message, null);
    }

    /**
     * Ghi log lỗi kèm mã lỗi chuẩn hoá ({@link ErrorCode}) - dùng thay cho
     * {@code e.printStackTrace()} ở tầng DAO/service/UI. Message log sẽ có
     * dạng "[ERR-DB-002] Truy vấn dữ liệu thất bại - PhoneDAO.findById",
     * giúp tra cứu nhanh khi người dùng báo lại đúng mã lỗi hiển thị.
     * <p>
     * Username được tự động lấy từ phiên đăng nhập hiện tại
     * ({@code AuthService.getInstance().getCurrentUser()}); neu chua dang
     * nhap (vd loi xay ra o man hinh Login) se dung "SYSTEM".
     *
     * @param code    mã lỗi chuẩn hoá, xem {@link ErrorCode}
     * @param context mô tả ngắn gọn nơi xảy ra lỗi (vd tên lớp.hàm), có thể null
     * @param ex      exception gốc, có thể null
     */
    public void error(ErrorCode code, String context, Throwable ex) {
        String message = (context == null || context.isEmpty())
                ? code.toString()
                : code + " - " + context;
        log(resolveCurrentUsername(), LogLevel.ERROR, "ERROR", "SYSTEM", message, ex);
    }

    private String resolveCurrentUsername() {
        try {
            com.model.User u = com.service.AuthService.getInstance().getCurrentUser();
            return u != null ? u.getUsername() : "SYSTEM";
        } catch (Exception ignore) {
            // AppLogger khong duoc phep tu no ma them loi khi dang co gang ghi log loi khac.
            return "SYSTEM";
        }
    }
}