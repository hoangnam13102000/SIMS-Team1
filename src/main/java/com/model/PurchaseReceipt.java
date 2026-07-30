package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PurchaseReceipt {

    private int receiptId;
    private String receiptCode;   // "PN_" + ReceiptID dem 6 so

    private int supplierId;
    private String supplierName;

    private int createdBy;
    private String createdByName;

    private LocalDateTime createdAt;
    private BigDecimal totalAmount;
    private String status; // COMPLETED | CANCELLED

    private int itemCount; // so dong chi tiet (san pham khac nhau) trong phieu

    public PurchaseReceipt() {
    }

    public int getReceiptId() { return receiptId; }
    public void setReceiptId(int receiptId) { this.receiptId = receiptId; }

    public String getReceiptCode() { return receiptCode; }
    public void setReceiptCode(String receiptCode) { this.receiptCode = receiptCode; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }
}