package com.model;

import java.math.BigDecimal;

public class OrderDetail {

    private int orderDetailId;
    private int orderId;
    private int productId;
    private String productName;
    /** Duong dan anh san pham (tu Products.ImageUrl) - co the null neu SP da xoa. */
    private String productImageUrl;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;

    /** So luong da tra (Direction=IN, phieu APPROVED, qua Orders.InvoiceID) — khong luu DB, gan tu OrderDAO.getDetailsByOrderId. */
    private int returnedQuantity;
    /** quantity - returnedQuantity. */
    private int remainingQuantity;

    public OrderDetail() {
    }

    public OrderDetail(int productId, String productName, int quantity, BigDecimal unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public int getOrderDetailId() { return orderDetailId; }
    public void setOrderDetailId(int orderDetailId) { this.orderDetailId = orderDetailId; }

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

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