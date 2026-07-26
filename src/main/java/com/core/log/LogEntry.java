package com.core.log;

import java.util.Date;

public final class LogEntry {
    private final String username;
    private final LogLevel level;
    private final String action;
    private final String entityType;
    private final String description;
    private final Throwable throwable;
    private final Date timestamp;
    private final String oldValue;
    private final String newValue;

    public LogEntry(String username, LogLevel level, String action,
                    String entityType, String description, Throwable throwable) {
        this(username, level, action, entityType, description, throwable, null, null);
    }

    /**
     * @param oldValue snapshot JSON cua entity TRUOC thay doi (null neu khong ap dung,
     *                 vd voi CREATE hoac cac log khong lien quan CRUD nhu LOGIN)
     * @param newValue snapshot JSON cua entity SAU thay doi (null neu khong ap dung,
     *                 vd voi DELETE)
     */
    public LogEntry(String username, LogLevel level, String action,
                    String entityType, String description, Throwable throwable,
                    String oldValue, String newValue) {
        this.username = username;
        this.level = level != null ? level : LogLevel.INFO;
        this.action = action;
        this.entityType = entityType;
        this.description = description;
        this.throwable = throwable;
        this.timestamp = new Date();
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    // Getters
    public String getUsername() { return username; }
    public LogLevel getLevel() { return level; }
    public String getAction() { return action; }
    public String getEntityType() { return entityType; }
    public String getDescription() { return description; }
    public Throwable getThrowable() { return throwable; }
    public Date getTimestamp() { return timestamp; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
}