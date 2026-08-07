package com.incident;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class IncidentLogger {

    private static final IncidentLogger INSTANCE = new IncidentLogger();
    public static IncidentLogger getInstance() { return INSTANCE; }

    private final List<IncidentSink> sinks = new CopyOnWriteArrayList<>();
    private final List<IncidentListener> listeners = new CopyOnWriteArrayList<>();

    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "incident-logger");
        t.setDaemon(true);
        return t;
    });

    private IncidentLogger() {}

    public void addSink(IncidentSink sink) { sinks.add(sink); }
    public void addListener(IncidentListener listener) { listeners.add(listener); }

    public void log(IncidentType type, IncidentSeverity severity, String source, String message, Throwable cause) {
        Incident incident = new Incident(type, severity, source, message, cause);
        writer.submit(() -> {
            for (IncidentSink sink : sinks) {
                try { sink.write(incident); } catch (Exception e) { e.printStackTrace(); }
            }
            for (IncidentListener listener : listeners) {
                try { listener.onIncident(incident); } catch (Exception e) { e.printStackTrace(); }
            }
        });
    }

    public void low(IncidentType type, String source, String message) {
        log(type, IncidentSeverity.LOW, source, message, null);
    }
    public void medium(IncidentType type, String source, String message) {
        log(type, IncidentSeverity.MEDIUM, source, message, null);
    }
    public void high(IncidentType type, String source, String message, Throwable cause) {
        log(type, IncidentSeverity.HIGH, source, message, cause);
    }
    public void critical(IncidentType type, String source, String message, Throwable cause) {
        log(type, IncidentSeverity.CRITICAL, source, message, cause);
    }
}