package com.backup;

import java.io.File;

public interface BackupStrategy {
    String getName();

    /** Phan mo rong file ma strategy nay tao ra, vd "sql", "bak". */
    default String getFileExtension() { return "sql"; }

    void backupTo(File destinationFile) throws BackupException;
}