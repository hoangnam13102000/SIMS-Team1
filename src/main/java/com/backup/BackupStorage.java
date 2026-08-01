package com.backup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class BackupStorage {

    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File directory;

    public BackupStorage(File directory) {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Khong tao duoc thu muc backup: " + directory.getAbsolutePath());
        }
    }

    public File getDirectory() { return directory; }

    public File newBackupFile(String strategyName, String extension) {
        String name = "backup_" + LocalDateTime.now().format(FILE_TIMESTAMP) + "_" + strategyName + "." + extension;
        return new File(directory, name);
    }

    public List<File> listBackups() {
        List<File> result = new ArrayList<>();
        File[] files = directory.listFiles((dir, name) -> name.startsWith("backup_"));
        if (files != null) for (File f : files) result.add(f);
        result.sort(Comparator.comparingLong(File::lastModified).reversed());
        return result;
    }

    public File getLatestBackup() {
        List<File> all = listBackups();
        return all.isEmpty() ? null : all.get(0);
    }

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