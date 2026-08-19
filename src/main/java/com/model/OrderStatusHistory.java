package com.model;

import java.time.LocalDateTime;

/** Mot moc lich su chuyen trang thai cua don hang online. */
public class OrderStatusHistory {
    private long historyId;
    private int orderId;
    private String fromStatus;
    private String toStatus;
    private Integer changedBy;
    private String changedByName;
    private LocalDateTime changedAt;
    private String note;
    private boolean viaAssistant;

    public long getHistoryId() { return historyId; }
    public void setHistoryId(long historyId) { this.historyId = historyId; }
    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }
    public String getFromStatus() { return fromStatus; }
    public void setFromStatus(String fromStatus) { this.fromStatus = fromStatus; }
    public String getToStatus() { return toStatus; }
    public void setToStatus(String toStatus) { this.toStatus = toStatus; }
    public Integer getChangedBy() { return changedBy; }
    public void setChangedBy(Integer changedBy) { this.changedBy = changedBy; }
    public String getChangedByName() { return changedByName; }
    public void setChangedByName(String changedByName) { this.changedByName = changedByName; }
    public LocalDateTime getChangedAt() { return changedAt; }
    public void setChangedAt(LocalDateTime changedAt) { this.changedAt = changedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public boolean isViaAssistant() { return viaAssistant; }
    public void setViaAssistant(boolean viaAssistant) { this.viaAssistant = viaAssistant; }
}
