package com.model;

import java.time.LocalDateTime;

/**
 * Model cho bang StockReconciliation (SIMS.sql, muc X. DOI CHIEU KHO CUOI
 * NGAY) - moi dong la ket qua doi chieu 1 lo hang trong 1 phien kiem ke
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
    private int batchId;
    private String batchCode;
    private int systemStock;
    private int actualStock;
    private int discrepancy;
    private String note;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;
    private boolean checked;
    private int checkedBy;
    private String checkedByName;
    private LocalDateTime checkedAt;

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

    public int getBatchId() { return batchId; }
    public void setBatchId(int batchId) { this.batchId = batchId; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

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

    public boolean isChecked() { return checked; }
    public void setChecked(boolean checked) { this.checked = checked; }

    public int getCheckedBy() { return checkedBy; }
    public void setCheckedBy(int checkedBy) { this.checkedBy = checkedBy; }

    public String getCheckedByName() { return checkedByName; }
    public void setCheckedByName(String checkedByName) { this.checkedByName = checkedByName; }

    public LocalDateTime getCheckedAt() { return checkedAt; }
    public void setCheckedAt(LocalDateTime checkedAt) { this.checkedAt = checkedAt; }

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