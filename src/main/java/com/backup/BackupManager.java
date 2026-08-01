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

    public BackupResult backupNow() throws BackupException {
        Exception lastError = null;
        for (BackupStrategy strategy : strategiesInOrder) {
            File destination = storage.newBackupFile(strategy.getName(), strategy.getFileExtension());
            notifyStarted(strategy.getName());
            Instant start = Instant.now();
            try {
                strategy.backupTo(destination);
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
        throw new BackupException("Khong xac dinh duoc strategy phu hop de restore file: " + fileName
                + " (ten file khong khop quy uoc backup_<thoigian>_<strategy>.<phanmorong>)");
    }

    public void restoreLatest() throws BackupException {
        File latest = storage.getLatestBackup();
        if (latest == null) {
            throw new BackupException("Khong co ban backup nao trong " + storage.getDirectory().getAbsolutePath());
        }
        restore(latest);
    }

    public void startScheduled(long initialDelayMinutes, long intervalMinutes) {
        scheduler.start(() -> {
            try { backupNow(); } catch (BackupException e) { /* da bao qua listener */ }
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