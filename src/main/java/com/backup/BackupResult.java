package com.backup;

import java.io.File;
import java.time.Duration;
import java.time.Instant;

public final class BackupResult {
    private final File file;
    private final String strategyName;
    private final Instant startedAt;
    private final Instant finishedAt;
    private final long sizeBytes;

    public BackupResult(File file, String strategyName, Instant startedAt, Instant finishedAt) {
        this.file = file;
        this.strategyName = strategyName;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.sizeBytes = (file != null && file.exists()) ? file.length() : 0L;
    }

    public File getFile() { return file; }
    public String getStrategyName() { return strategyName; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public long getSizeBytes() { return sizeBytes; }
    public Duration getDuration() { return Duration.between(startedAt, finishedAt); }

    @Override
    public String toString() {
        return "BackupResult{file=" + file + ", strategy=" + strategyName
                + ", sizeBytes=" + sizeBytes + ", duration=" + getDuration() + "}";
    }
}