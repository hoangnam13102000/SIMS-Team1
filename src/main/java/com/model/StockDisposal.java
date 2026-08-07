package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Phieu tieu huy hang (StockDisposals). */
public class StockDisposal {

    private int disposalId;
    private String disposalCode;
    private String reason;   // EXPIRED | DAMAGED | QUALITY | OTHER
    private String status;   // COMPLETED | CANCELLED
    private BigDecimal totalLossAmount;
    private String note;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private int itemCount;

    public int getDisposalId() { return disposalId; }
    public void setDisposalId(int disposalId) { this.disposalId = disposalId; }

    public String getDisposalCode() { return disposalCode; }
    public void setDisposalCode(String disposalCode) { this.disposalCode = disposalCode; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalLossAmount() { return totalLossAmount; }
    public void setTotalLossAmount(BigDecimal totalLossAmount) { this.totalLossAmount = totalLossAmount; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }

    public String getReasonLabel() {
        if (reason == null) return "-";
        switch (reason.toUpperCase()) {
            case "EXPIRED": return "Hết hạn";
            case "DAMAGED": return "Hỏng / hư hỏng";
            case "QUALITY": return "Chất lượng";
            case "OTHER": return "Khác";
            default: return reason;
        }
    }
}