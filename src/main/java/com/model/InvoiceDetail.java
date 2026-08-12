package com.model;

import java.math.BigDecimal;

public class InvoiceDetail {

    private int invoiceDetailId;
    private int invoiceId;
    private int productId;
    private String productName;
    private String productCode;
    /** Duong dan anh san pham (tu Products.ImageUrl) - co the null. */
    private String productImageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal; // cot computed (Quantity * UnitPrice) tu DB

    /** So luong da tra (Direction=IN, phieu APPROVED) — khong luu DB, gan tu InvoiceDAO.getDetails. */
    private int returnedQuantity;
    /** quantity - returnedQuantity. */
    private int remainingQuantity;

    public InvoiceDetail() {
    }

    public int getInvoiceDetailId() { return invoiceDetailId; }
    public void setInvoiceDetailId(int invoiceDetailId) { this.invoiceDetailId = invoiceDetailId; }

    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductImageUrl() { return productImageUrl; }
    public void setProductImageUrl(String productImageUrl) { this.productImageUrl = productImageUrl; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) {
        this.quantity = quantity;
        syncRemaining();
    }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public BigDecimal getLineTotal() { return lineTotal; }
    public void setLineTotal(BigDecimal lineTotal) { this.lineTotal = lineTotal; }

    public int getReturnedQuantity() { return returnedQuantity; }
    public void setReturnedQuantity(int returnedQuantity) {
        this.returnedQuantity = Math.max(0, returnedQuantity);
        syncRemaining();
    }

    public int getRemainingQuantity() { return remainingQuantity; }

    private void syncRemaining() {
        this.remainingQuantity = Math.max(0, quantity - returnedQuantity);
    }

    public boolean isFullyReturned() {
        return quantity > 0 && returnedQuantity >= quantity;
    }

    public boolean isPartiallyReturned() {
        return returnedQuantity > 0 && returnedQuantity < quantity;
    }
}