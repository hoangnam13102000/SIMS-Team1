package com.backup;

import java.io.File;

public interface RestoreStrategy {
    String getName();
    void restoreFrom(File backupFile) throws BackupException;
}