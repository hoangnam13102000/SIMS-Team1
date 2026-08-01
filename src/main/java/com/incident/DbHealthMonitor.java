package com.incident;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Ping dinh ky 1 Supplier<Connection> bat ky; mat ket noi lien tiep qua nguong -> bao Incident CRITICAL + trigger callback. */
public class DbHealthMonitor {

    private final Supplier<Connection> connectionSupplier;
    private final int consecutiveFailureThreshold;
    private final Runnable onSustainedOutage;
    private final Runnable onRecovered;

    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger incidentAlreadyRaised = new AtomicInteger(0);

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "db-health-monitor");
        t.setDaemon(true);
        return t;
    });

    public DbHealthMonitor(Supplier<Connection> connectionSupplier, int consecutiveFailureThreshold,
                            Runnable onSustainedOutage, Runnable onRecovered) {
        this.connectionSupplier = connectionSupplier;
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
        this.onSustainedOutage = onSustainedOutage;
        this.onRecovered = onRecovered;
    }

    public void start(long intervalSeconds) {
        executor.scheduleAtFixedRate(this::checkOnce, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    public void stop() { executor.shutdownNow(); }

    public void checkOnce() {
        boolean healthy = pingOnce();
        if (healthy) {
            int previousFailures = consecutiveFailures.getAndSet(0);
            if (incidentAlreadyRaised.compareAndSet(1, 0) && previousFailures > 0) {
                IncidentLogger.getInstance().medium(IncidentType.OTHER, "DbHealthMonitor",
                        "Da ket noi lai duoc DB sau " + previousFailures + " lan kiem tra that bai lien tiep.");
                if (onRecovered != null) try { onRecovered.run(); } catch (Exception ignored) {}
            }
            return;
        }
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= consecutiveFailureThreshold && incidentAlreadyRaised.compareAndSet(0, 1)) {
            IncidentLogger.getInstance().critical(IncidentType.DB_CONNECTION_LOST, "DbHealthMonitor",
                    "Mat ket noi DB " + failures + " lan kiem tra lien tiep - nghi ngo bi tan cong/sap/mat du lieu.", null);
            if (onSustainedOutage != null) try { onSustainedOutage.run(); } catch (Exception ignored) {}
        }
    }

    private boolean pingOnce() {
        try {
            Connection conn = connectionSupplier.get();
            if (conn == null) return false;
            boolean valid = conn.isValid(5);
            conn.close();
            return valid;
        } catch (SQLException | RuntimeException e) {
            return false;
        }
    }

    public int getConsecutiveFailures() { return consecutiveFailures.get(); }
}