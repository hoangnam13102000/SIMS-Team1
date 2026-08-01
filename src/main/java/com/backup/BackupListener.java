package com.backup;

public interface BackupListener {
    default void onBackupStarted(String strategyName) {}
    default void onBackupSucceeded(BackupResult result) {}
    default void onBackupFailed(String strategyName, Exception error) {}
    default void onRestoreStarted(String strategyName, java.io.File file) {}
    default void onRestoreSucceeded(String strategyName, java.io.File file) {}
    default void onRestoreFailed(String strategyName, java.io.File file, Exception error) {}
}