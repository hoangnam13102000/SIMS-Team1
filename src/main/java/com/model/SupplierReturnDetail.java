package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dong chi tiet phieu tra NCC - gan 1 lo hang (InventoryBatch) cu the. */
public class SupplierReturnDetail {

    private int supplierReturnDetailId;
    private int supplierReturnId;
    private int productId;
    private String productName;
    private String productCode;
    private int batchId;
    private String batchCode;
    private String lotNumber;
    /** Ma phieu nhap goc cua lo (PurchaseReceipts.ReceiptCode) - chi de hien thi. */
    private String receiptCode;
    private int quantity;
    private BigDecimal unitRefundPrice;
    private BigDecimal lineRefundAmount;
    private LocalDate expiryDate;
    private int remainingQty;

    public int getSupplierReturnDetailId() { return supplierReturnDetailId; }
    public void setSupplierReturnDetailId(int supplierReturnDetailId) { this.supplierReturnDetailId = supplierReturnDetailId; }

    public int getSupplierReturnId() { return supplierReturnId; }
    public void setSupplierReturnId(int supplierReturnId) { this.supplierReturnId = supplierReturnId; }

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

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public String getReceiptCode() { return receiptCode; }
    public void setReceiptCode(String receiptCode) { this.receiptCode = receiptCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitRefundPrice() { return unitRefundPrice; }
    public void setUnitRefundPrice(BigDecimal unitRefundPrice) { this.unitRefundPrice = unitRefundPrice; }

    public BigDecimal getLineRefundAmount() { return lineRefundAmount; }
    public void setLineRefundAmount(BigDecimal lineRefundAmount) { this.lineRefundAmount = lineRefundAmount; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public int getRemainingQty() { return remainingQty; }
    public void setRemainingQty(int remainingQty) { this.remainingQty = remainingQty; }
}