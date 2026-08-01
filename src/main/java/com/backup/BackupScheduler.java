package com.backup;

import com.incident.IncidentLogger;
import com.incident.IncidentType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupScheduler {

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "backup-scheduler");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean started = false;

    public synchronized void start(Runnable backupTask, long initialDelayMinutes, long intervalMinutes) {
        if (started) return;
        started = true;
        executor.scheduleAtFixedRate(() -> {
            try {
                backupTask.run();
            } catch (Exception e) {
                IncidentLogger.getInstance().critical(IncidentType.BACKUP_FAILED,
                        "BackupScheduler", "Tac vu sao luu dinh ky that bai", e);
            }
        }, initialDelayMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        started = false;
        executor.shutdownNow();
    }

    public boolean isStarted() { return started; }
}