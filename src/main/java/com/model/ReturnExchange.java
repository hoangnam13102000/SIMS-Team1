package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReturnExchange {

    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_EXCHANGE = "EXCHANGE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private int returnId;
    private int invoiceId;
    private String invoiceCode;

    private String type;          // RETURN | EXCHANGE
    private String reason;        // R4: ly do khach hang
    private String rejectionReason; // ly do nhan vien tu choi
    private BigDecimal totalValue; // gia tri hang khach tra (tong Direction=IN * UnitPrice)
    private BigDecimal discountShare;  // phan gia tri ma khuyen mai duoc phan bo cho lan tra nay
    private BigDecimal pointsShare;    // phan gia tri diem khach hang da dung duoc phan bo cho lan tra nay
    private boolean requiresApproval;
    private String status;        // PENDING | APPROVED | REJECTED

    private Integer approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;

    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    public ReturnExchange() {
    }

    public int getReturnId() { return returnId; }
    public void setReturnId(int returnId) { this.returnId = returnId; }

    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }

    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public BigDecimal getTotalValue() { return totalValue; }
    public void setTotalValue(BigDecimal totalValue) { this.totalValue = totalValue; }

    public BigDecimal getDiscountShare() { return discountShare; }
    public void setDiscountShare(BigDecimal discountShare) { this.discountShare = discountShare; }

    public BigDecimal getPointsShare() { return pointsShare; }
    public void setPointsShare(BigDecimal pointsShare) { this.pointsShare = pointsShare; }

    public boolean isRequiresApproval() { return requiresApproval; }
    public void setRequiresApproval(boolean requiresApproval) { this.requiresApproval = requiresApproval; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }

    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isExchange() { return TYPE_EXCHANGE.equalsIgnoreCase(type); }
    public boolean isPending() { return STATUS_PENDING.equalsIgnoreCase(status); }
    public boolean isApproved() { return STATUS_APPROVED.equalsIgnoreCase(status); }
    public boolean isRejected() { return STATUS_REJECTED.equalsIgnoreCase(status); }
}