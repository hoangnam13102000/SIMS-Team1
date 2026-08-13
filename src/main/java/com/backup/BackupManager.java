package com.backup;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class BackupManager {
    private final BackupStorage storage;
    private final List<BackupStrategy> strategiesInOrder;
    private final List<BackupListener> listeners = new CopyOnWriteArrayList<>();
    private final BackupScheduler scheduler = new BackupScheduler();

    public BackupManager(BackupStorage storage, List<BackupStrategy> strategiesInOrder) {
        if (strategiesInOrder == null || strategiesInOrder.isEmpty()) {
            throw new IllegalArgumentException("Can it nhat 1 BackupStrategy.");
        }
        this.storage = storage;
        this.strategiesInOrder = new ArrayList<>(strategiesInOrder);
    }

    public void addListener(BackupListener listener) { listeners.add(listener); }
    public BackupStorage getStorage() { return storage; }

    // ================================================================
    // 1) NGƯỜI DÙNG BẤM NÚT "SAO LƯU NGAY"
    //    → Dùng newTimestampedBackupFile → MỖI LẦN = 1 FILE RIÊNG
    // ================================================================
    public BackupResult backupNow() throws BackupException {
        return doBackupInternal(BackupMode.MANUAL);
    }

    // ================================================================
    // 2) SCHEDULER TỰ ĐỘNG (mỗi 24h)
    //    → Dùng newDailyBackupFile → 1 NGÀY 1 FILE DUY NHẤT
    //    → Check hasDailyBackupToday() → hôm nay có rồi thì BỎ QUA
    // ================================================================
    public BackupResult backupIfNotDoneToday() throws BackupException {
        if (storage.hasDailyBackupToday()) {
            return null; // hôm nay đã có file daily → bỏ qua
        }
        return doBackupInternal(BackupMode.SCHEDULED);
    }

    // ================================================================
    // 3) EMERGENCY (DB yếu → cần lưu trạng thái cuối)
    //    → Dùng newTimestampedBackupFile → 1 file riêng mỗi lần khẩn cấp
    // ================================================================
    public BackupResult backupEmergency() throws BackupException {
        return doBackupInternal(BackupMode.EMERGENCY);
    }

    /** Giữ tương thích ngược với code cũ gọi backupNow(true). */
    public BackupResult backupNow(boolean forceIfExists) throws BackupException {
        return doBackupInternal(forceIfExists ? BackupMode.MANUAL : BackupMode.SCHEDULED);
    }

    // ================================================================
    // HÀM NỘI BỘ: logic backup thực sự, phân biệt theo Mode
    // ================================================================
    private enum BackupMode {
        /** Thủ công: timestamp, mỗi lần 1 file. */
        MANUAL,
        /** Tự động: chỉ ngày, 1 ngày 1 file. */
        SCHEDULED,
        /** Khẩn cấp: timestamp, mỗi lần 1 file. */
        EMERGENCY
    }

    private BackupResult doBackupInternal(BackupMode mode) throws BackupException {
        Exception lastError = null;
        for (BackupStrategy strategy : strategiesInOrder) {

            // ====== CHỌN CÁCH ĐẶT TÊN FILE THEO MODE ======
            File destination;
            if (mode == BackupMode.SCHEDULED) {
                // Tự động → tên chỉ có ngày → ghi đè nếu cùng ngày
                destination = storage.newDailyBackupFile(strategy.getName(), strategy.getFileExtension());
                // Nếu file đã tồn tại từ sáng nay → xóa đi để ghi lại bản mới
                if (destination.exists()) {
                    try { destination.delete(); } catch (Exception ignored) { }
                }
            } else {
                // MANUAL / EMERGENCY → tên có giờ → mỗi lần 1 file riêng, không ghi đè ai
                destination = storage.newTimestampedBackupFile(strategy.getName(), strategy.getFileExtension());
            }

            notifyStarted(strategy.getName() + (mode == BackupMode.MANUAL ? " (thủ công)"
                    : mode == BackupMode.EMERGENCY ? " (khẩn cấp)" : ""));
            Instant start = Instant.now();
            try {
                strategy.backupTo(destination);
                if (!destination.exists() || destination.length() == 0) {
                    if (destination.exists()) destination.delete();
                    throw new BackupException("Strategy " + strategy.getName()
                            + " ghi ra file rong (0 byte).");
                }
                BackupResult result = new BackupResult(destination, strategy.getName(), start, Instant.now());
                notifySucceeded(result);
                return result;
            } catch (Exception e) {
                lastError = e;
                notifyFailed(strategy.getName(), e);
                if (destination.exists()) destination.delete();
            }
        }
        throw new BackupException("Tat ca " + strategiesInOrder.size() + " backup strategy deu that bai. "
                + "Loi cuoi cung: " + (lastError == null ? "khong ro" : lastError.getMessage()), lastError);
    }

    public void restore(File backupFile) throws BackupException {
        String fileName = backupFile.getName();
        for (BackupStrategy strategy : strategiesInOrder) {
            if (!(strategy instanceof RestoreStrategy)) continue;
            // Khớp với cả 2 định dạng:
            //   - daily:    backup_20260813_<strategy>.ext
            //   - timestamp: backup_20260813-091522_<strategy>.ext
            if (fileName.contains("_" + strategy.getName() + ".")) {
                RestoreStrategy restoreStrategy = (RestoreStrategy) strategy;
                notifyRestoreStarted(strategy.getName(), backupFile);
                try {
                    restoreStrategy.restoreFrom(backupFile);
                    notifyRestoreSucceeded(strategy.getName(), backupFile);
                } catch (Exception e) {
                    notifyRestoreFailed(strategy.getName(), backupFile, e);
                    throw e instanceof BackupException ? (BackupException) e
                            : new BackupException("Restore that bai: " + e.getMessage(), e);
                }
                return;
            }
        }
        throw new BackupException("Khong xac dinh duoc strategy phu hop de restore file: " + fileName);
    }

    public void restoreLatest() throws BackupException {
        File latest = storage.getLatestBackup();
        if (latest == null) {
            throw new BackupException("Khong co ban backup nao trong " + storage.getDirectory().getAbsolutePath());
        }
        restore(latest);
    }

    // ================================================================
    // Scheduler gọi backupIfNotDoneToday()
    // ================================================================
    public void startScheduled(long initialDelayMinutes, long intervalMinutes) {
        scheduler.start(() -> {
            try {
                backupIfNotDoneToday();
            } catch (BackupException e) {
                /* đã báo qua listener */
            }
        }, initialDelayMinutes, intervalMinutes);
    }

    public void stopScheduled() { scheduler.stop(); }

    private void notifyStarted(String s) { for (BackupListener l : listeners) l.onBackupStarted(s); }
    private void notifySucceeded(BackupResult r) { for (BackupListener l : listeners) l.onBackupSucceeded(r); }
    private void notifyFailed(String s, Exception e) { for (BackupListener l : listeners) l.onBackupFailed(s, e); }
    private void notifyRestoreStarted(String s, File f) { for (BackupListener l : listeners) l.onRestoreStarted(s, f); }
    private void notifyRestoreSucceeded(String s, File f) { for (BackupListener l : listeners) l.onRestoreSucceeded(s, f); }
    private void notifyRestoreFailed(String s, File f, Exception e) { for (BackupListener l : listeners) l.onRestoreFailed(s, f, e); }
}