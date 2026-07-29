package com.model;

import java.util.Date;

public class ActivityLog {

    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGIN_FAILED = "LOGIN_FAILED";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_STATUS_CHANGE = "STATUS_CHANGE";
    public static final String ACTION_PASSWORD_RESET = "PASSWORD_RESET";

    public static final String ENTITY_PHONE = "PHONE";
    public static final String ENTITY_CATEGORY = "CATEGORY";
    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_USER = "USER";

    private int logId;
    private String username;
    private String action;
    private String entityType;
    private String description;
    private Date createdAt;
    /** Snapshot JSON cua entity TRUOC thay doi - null neu khong ap dung (vd CREATE, LOGIN...). */
    private String oldValue;
    /** Snapshot JSON cua entity SAU thay doi - null neu khong ap dung (vd DELETE, LOGIN...). */
    private String newValue;

    public int getLogId() { return logId; }
    public void setLogId(int logId) { this.logId = logId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getEntityType() { return entityType; }
    public void setEntityType(String entityType) { this.entityType = entityType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public String getOldValue() { return oldValue; }
    public void setOldValue(String oldValue) { this.oldValue = oldValue; }

    public String getNewValue() { return newValue; }
    public void setNewValue(String newValue) { this.newValue = newValue; }

    /** true neu dong nay co du lieu snapshot de xem chi tiet thay doi (dung cho nut "Xem thay doi" tren UI). */
    public boolean hasValueSnapshot() {
        return (oldValue != null && !oldValue.isBlank()) || (newValue != null && !newValue.isBlank());
    }
}
