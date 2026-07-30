package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class InventoryBatch {

    private int batchId;
    private String batchCode;   // "LOT_" + BatchID dem 6 so - cot COMPUTED PERSISTED, CHI DOC
    private String lotNumber;   // so lo tren bao bi cua NCC (tuy chon, co the trung giua cac lan nhap)

    private int productId;
    private String productName;
    private String productCode;

    private int supplierId;
    private String supplierName;

    private LocalDate manufactureDate; // NSX (tuy chon)
    private LocalDate expiryDate;      // HSD (tuy chon - vd phu kien khong co han su dung)
    private LocalDateTime importDate;

    private BigDecimal importPrice;
    private int quantity;      // so luong nhap ban dau, khong doi sau khi tao
    private int remainingQty;  // so luong con lai, giam dan khi ban theo FEFO

    private String status; // ACTIVE | EXPIRED | DEPLETED

    public InventoryBatch() {
    }

    public int getBatchId() { return batchId; }
    public void setBatchId(int batchId) { this.batchId = batchId; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public LocalDateTime getImportDate() { return importDate; }
    public void setImportDate(LocalDateTime importDate) { this.importDate = importDate; }

    public BigDecimal getImportPrice() { return importPrice; }
    public void setImportPrice(BigDecimal importPrice) { this.importPrice = importPrice; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getRemainingQty() { return remainingQty; }
    public void setRemainingQty(int remainingQty) { this.remainingQty = remainingQty; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    /** So ngay con lai den han (am = da qua han). Null neu khong co HSD. */
    public Long daysUntilExpiry() {
        if (expiryDate == null) return null;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
    }
}