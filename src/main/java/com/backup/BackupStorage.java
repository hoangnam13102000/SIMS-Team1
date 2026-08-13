package com.backup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BackupStorage {
    /** Tên file cho TỰ ĐỘNG: chỉ có NGÀY → 1 ngày 1 file, ghi đè. */
    private static final DateTimeFormatter FILE_DATE_ONLY = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** Tên file cho THỦ CÔNG / EMERGENCY: có GIỜ:PHÚT:GIÂY → mỗi lần 1 file riêng. */
    private static final DateTimeFormatter FILE_WITH_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File directory;

    public BackupStorage(File directory) {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Khong tao duoc thu muc backup: " + directory.getAbsolutePath());
        }
    }

    public File getDirectory() { return directory; }

    /**
     * Tạo tên file CHO SCHEDULER TỰ ĐỘNG: chỉ có ngày.
     * → backup_20260813_sqlservernative.bak
     * → CÙNG NGÀY = CÙNG TÊN = GHI ĐÈ → đảm bảo 1 ngày 1 file.
     */
    public File newDailyBackupFile(String strategyName, String extension) {
        String datePart = LocalDate.now().format(FILE_DATE_ONLY);
        String name = "backup_" + datePart + "_" + strategyName + "." + extension;
        return new File(directory, name);
    }

    /**
     * Tạo tên file CHO THỦ CÔNG / EMERGENCY: có đầy đủ ngày-giờ-phút-giây.
     * → backup_20260813-091522_sqlservernative.bak
     * → MỖI LẦN GỌI = 1 FILE RIÊNG → bạn bấm 5 lần = 5 file khác nhau.
     */
    public File newTimestampedBackupFile(String strategyName, String extension) {
        String ts = LocalDateTime.now().format(FILE_WITH_TIME);
        String name = "backup_" + ts + "_" + strategyName + "." + extension;
        return new File(directory, name);
    }

    /**
     * @deprecated Dùng newDailyBackupFile() hoặc newTimestampedBackupFile() thay cho rõ ràng.
     * Giữ lại để không phá code cũ gọi trực tiếp phương thức này.
     */
    @Deprecated
    public File newBackupFile(String strategyName, String extension) {
        return newTimestampedBackupFile(strategyName, extension);
    }

    /**
     * Kiểm tra hôm nay đã có backup TỰ ĐỘNG nào thành công chưa.
     * Chỉ kiểm tra file ĐỊNH DẠNG NGÀY (yyyyMMdd), không tính các file thủ công có timestamp.
     */
    public boolean hasDailyBackupToday() {
        String todayPrefix = "backup_" + LocalDate.now().format(FILE_DATE_ONLY) + "_";
        File[] files = directory.listFiles((dir, name) -> name.startsWith(todayPrefix));
        if (files == null) return false;
        for (File f : files) {
            if (f.isFile() && f.length() > 0) return true;
        }
        return false;
    }

    /** File mới nhất (dùng lastModified → đúng với mọi định dạng tên file). */
    public File getLatestBackup() {
        List<File> all = listBackups();
        return all.isEmpty() ? null : all.get(0);
    }

    /** Liệt kê TẤT CẢ file backup (cả tự động lẫn thủ công), mới nhất lên đầu. */
    public List<File> listBackups() {
        List<File> result = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.startsWith("backup_"));
        if (files != null) for (File f : files) result.add(f);
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    /**
     * Xóa file cũ, giữ `keepCount` bản MỚI NHẤT (tính trên lastModified).
     * Với quy tắc mới:
     *   - 14 ngày tự động = 14 file
     *   + vài file thủ công trong ngày → vẫn giữ đúng 14 bản MỚI NHẤT tổng cộng
     */
    public int cleanupOldBackups(int keepCount) {
        List<File> all = listBackups();
        int deleted = 0;
        for (int i = keepCount; i < all.size(); i++) {
            if (all.get(i).delete()) deleted++;
        }
        return deleted;
    }

    public long totalSizeBytes() {
        long total = 0;
        for (File f : listBackups()) total += f.length();
        return total;
    }

    public boolean delete(File backupFile) throws IOException {
        if (!backupFile.getParentFile().equals(directory)) {
            throw new IOException("File khong thuoc thu muc backup quan ly boi storage nay: " + backupFile);
        }
        return Files.deleteIfExists(backupFile.toPath());
    }
}