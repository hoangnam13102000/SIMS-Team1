package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Phieu tra hang lo ve nha cung cap (hang loi/hong/sai quy cach/het han
 * som). Khac voi {@link StockDisposal} (tieu huy = mat trang), phieu nay
 * ghi nhan cong no NCC (Suppliers.DebtBalance) de theo doi hoan tien.
 * Tru ton kho NGAY khi luu phieu (khong co buoc duyet) - xem
 * {@link com.dao.SupplierReturnDAO#createSupplierReturn}.
 */
public class SupplierReturn {

    public static final String REASON_DAMAGED = "DAMAGED";
    public static final String REASON_EXPIRED = "EXPIRED";
    public static final String REASON_QUALITY = "QUALITY";
    public static final String REASON_WRONG_SPEC = "WRONG_SPEC";
    public static final String REASON_OTHER = "OTHER";

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private int supplierReturnId;
    private String supplierReturnCode;
    private int supplierId;
    private String supplierName;
    private String reason;
    private String status;
    private BigDecimal totalRefundAmount;
    private String note;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private int itemCount;

    public int getSupplierReturnId() { return supplierReturnId; }
    public void setSupplierReturnId(int supplierReturnId) { this.supplierReturnId = supplierReturnId; }

    public String getSupplierReturnCode() { return supplierReturnCode; }
    public void setSupplierReturnCode(String supplierReturnCode) { this.supplierReturnCode = supplierReturnCode; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalRefundAmount() { return totalRefundAmount; }
    public void setTotalRefundAmount(BigDecimal totalRefundAmount) { this.totalRefundAmount = totalRefundAmount; }

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

    public boolean isCancelled() { return STATUS_CANCELLED.equalsIgnoreCase(status); }

    public String getReasonLabel() {
        if (reason == null) return "-";
        switch (reason) {
            case REASON_DAMAGED: return "Hỏng / hư hỏng";
            case REASON_EXPIRED: return "Hết hạn sớm";
            case REASON_QUALITY: return "Chất lượng";
            case REASON_WRONG_SPEC: return "Sai quy cách";
            default: return "Khác";
        }
    }
}