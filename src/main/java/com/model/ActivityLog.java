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
    public static final String ACTION_RESTORE = "RESTORE";
    public static final String ACTION_PERMANENT_DELETE = "PERMANENT_DELETE";
    /** Nhan vien mo ca ban hang. */
    public static final String ACTION_SHIFT_OPEN = "SHIFT_OPEN";

    /** Thu them tien mat vao quy. */
    public static final String ACTION_CASH_IN = "CASH_IN";

    /** Chi tien mat ra khoi quy. */
    public static final String ACTION_CASH_OUT = "CASH_OUT";

    /** Dong ca va doi soat quy. */
    public static final String ACTION_SHIFT_CLOSE = "SHIFT_CLOSE";

    public static final String ENTITY_PHONE = "PHONE";
    public static final String ENTITY_CATEGORY = "CATEGORY";
    public static final String ENTITY_ORDER = "ORDER";
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_CUSTOMER = "CUSTOMER";
    public static final String ENTITY_SUPPLIER = "SUPPLIER";
    public static final String ENTITY_EMPLOYEE = "EMPLOYEE";
    public static final String ENTITY_PRODUCT = "PRODUCT";
    public static final String ENTITY_INVENTORY_BATCH = "INVENTORY_BATCH";
    public static final String ENTITY_INVOICE = "INVOICE";
    public static final String ENTITY_PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    public static final String ENTITY_STOCK_ALERT = "STOCK_ALERT";
    /** Ca ban hang. */
    public static final String ENTITY_SHIFT = "SHIFT";

    /** Giao dich thu/chi tien mat trong ca. */
    public static final String ENTITY_SHIFT_CASH_TRANSACTION ="SHIFT_CASH_TRANSACTION";
    
    public static final String ACTION_LOGIN_2FA_SUCCESS = "LOGIN_2FA_SUCCESS";
    public static final String ACTION_LOGIN_2FA_FAILED = "LOGIN_2FA_FAILED";
    public static final String ACTION_2FA_ENABLED = "2FA_ENABLED";
    public static final String ACTION_2FA_DISABLED = "2FA_DISABLED";
    public static final String ACTION_2FA_BACKUP_CODE_USED = "2FA_BACKUP_CODE_USED";

    /** Nhan vien/QL phe duyet don doi/tra co gia tri lon (vuot nguong tu duyet). */
    public static final String ACTION_RETURN_APPROVE = "RETURN_APPROVE";
    /** Cap nhat gia ban san pham (vd tu Insert_SIMS.sql seed / thao tac gia thu cong). */
    public static final String ACTION_PRODUCT_PRICE_UPDATE = "PRODUCT_PRICE_UPDATE";
    /** Tao phieu tra hang cho nha cung cap. */
    public static final String ACTION_SUPPLIER_RETURN_CREATE = "SUPPLIER_RETURN_CREATE";

    /**
     * Cac gia tri TableName duoc trigger SQL (Trigger_SIMS.sql) / du lieu seed
     * (Insert_SIMS.sql) ghi TRUC TIEP xuong AuditLogs, dung dung ten bang SQL
     * (PascalCase, so nhieu) thay vi hang so ENTITY_* phia tren (SCREAMING_
     * SNAKE_CASE, so it) do lop ung dung Java tu dinh nghia. Khai bao lai o
     * day de actionLabel()/entityLabel() trong AuditLogPanel co the anh xa
     * ve nhan tieng Viet thay vi hien thi nguyen van ten bang.
     */
    public static final String ENTITY_RETURN_EXCHANGE_SQL = "ReturnExchanges";
    public static final String ENTITY_SUPPLIER_RETURN_SQL = "SupplierReturns";
    public static final String ENTITY_PRODUCT_SQL = "Products";
    public static final String ENTITY_USER_SQL = "Users";

    private int logId;
    private String username;
    private String action;
    private String entityType;
    /** ID cua ban ghi bi tac dong trong bang entityType - null neu khong xac dinh duoc (vd LOGIN/LOGOUT). */
    private Integer recordId;
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

    public Integer getRecordId() { return recordId; }
    public void setRecordId(Integer recordId) { this.recordId = recordId; }

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