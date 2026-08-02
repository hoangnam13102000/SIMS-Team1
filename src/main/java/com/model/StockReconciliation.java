package com.model;

import java.time.LocalDateTime;

/**
 * Model cho bang StockReconciliation (SIMS.sql, muc X. DOI CHIEU KHO CUOI
 * NGAY) - moi dong la ket qua doi chieu 1 san pham trong 1 phien kiem ke
 * (SystemStock = ton theo he thong TAI THOI DIEM GHI, ActualStock = ton dem
 * thuc te do nhan vien nhap). Discrepancy la cot COMPUTED PERSISTED
 * (ActualStock - SystemStock), CHI DOC.
 *
 * Viec ghi StockReconciliation va cap nhat Products.Stock/InventoryTransactions
 * tuong ung deu do trigger trg_StockReconciliation_Apply (INSTEAD OF INSERT)
 * dam nhiem o tang CSDL - xem sql/Trigger_SIMS.sql.
 */
public class StockReconciliation {

    private int reconciliationId;
    private int productId;
    private String productName;
    private String productCode;
    private int systemStock;
    private int actualStock;
    private int discrepancy;
    private String note;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    public StockReconciliation() {
    }

    public int getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(int reconciliationId) { this.reconciliationId = reconciliationId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getSystemStock() { return systemStock; }
    public void setSystemStock(int systemStock) { this.systemStock = systemStock; }

    public int getActualStock() { return actualStock; }
    public void setActualStock(int actualStock) { this.actualStock = actualStock; }

    public int getDiscrepancy() { return discrepancy; }
    public void setDiscrepancy(int discrepancy) { this.discrepancy = discrepancy; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean hasDiscrepancy() {
        return discrepancy != 0;
    }

    @Override
    public String toString() {
        return "StockReconciliation{" +
                "reconciliationId=" + reconciliationId +
                ", productName='" + productName + '\'' +
                ", systemStock=" + systemStock +
                ", actualStock=" + actualStock +
                ", discrepancy=" + discrepancy +
                '}';
    }
}