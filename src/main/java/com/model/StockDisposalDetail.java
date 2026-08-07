package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Dong chi tiet phieu tieu huy - gan 1 lo hang. */
public class StockDisposalDetail {

    private int disposalDetailId;
    private int disposalId;
    private int productId;
    private String productName;
    private String productCode;
    private int batchId;
    private String batchCode;
    private int quantity;
    private BigDecimal unitCost;
    private BigDecimal lineLossAmount;
    private LocalDate expiryDate;
    private int remainingQty;

    public int getDisposalDetailId() { return disposalDetailId; }
    public void setDisposalDetailId(int disposalDetailId) { this.disposalDetailId = disposalDetailId; }

    public int getDisposalId() { return disposalId; }
    public void setDisposalId(int disposalId) { this.disposalId = disposalId; }

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

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitCost() { return unitCost; }
    public void setUnitCost(BigDecimal unitCost) { this.unitCost = unitCost; }

    public BigDecimal getLineLossAmount() { return lineLossAmount; }
    public void setLineLossAmount(BigDecimal lineLossAmount) { this.lineLossAmount = lineLossAmount; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public int getRemainingQty() { return remainingQty; }
    public void setRemainingQty(int remainingQty) { this.remainingQty = remainingQty; }
}