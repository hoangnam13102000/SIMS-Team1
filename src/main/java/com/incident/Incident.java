package com.incident;

import java.time.Instant;
import java.io.PrintWriter;
import java.io.StringWriter;

public final class Incident {
    private final Instant timestamp;
    private final IncidentType type;
    private final IncidentSeverity severity;
    private final String source;
    private final String message;
    private final String stackTrace;

    public Incident(IncidentType type, IncidentSeverity severity, String source, String message, Throwable cause) {
        this.timestamp = Instant.now();
        this.type = type;
        this.severity = severity;
        this.source = source;
        this.message = message;
        this.stackTrace = cause == null ? null : stackTraceToString(cause);
    }

    private static String stackTraceToString(Throwable t) {
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    public Instant getTimestamp() { return timestamp; }
    public IncidentType getType() { return type; }
    public IncidentSeverity getSeverity() { return severity; }
    public String getSource() { return source; }
    public String getMessage() { return message; }
    public String getStackTrace() { return stackTrace; }

    @Override
    public String toString() {
        return "[" + timestamp + "] " + severity + " " + type + " (" + source + "): " + message;
    }
}