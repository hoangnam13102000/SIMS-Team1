package com.model;

import java.math.BigDecimal;

/**
 * 1 dong san pham trong yeu cau doi/tra (xem bang ReturnExchangeDetails).
 * Direction = IN: hang khach tra lai (cong kho khi duoc duyet).
 * Direction = OUT: hang doi moi giao cho khach (tru kho khi duoc duyet) -
 * chi co khi ReturnExchange.type = EXCHANGE.
 */
public class ReturnExchangeDetail {

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    private int returnDetailId;
    private int returnId;
    private int productId;
    private String productName;
    private String productCode;
    private int quantity;
    private String direction; // IN | OUT
    private BigDecimal unitPrice;

    public ReturnExchangeDetail() {
    }

    public ReturnExchangeDetail(int productId, int quantity, String direction, BigDecimal unitPrice) {
        this.productId = productId;
        this.quantity = quantity;
        this.direction = direction;
        this.unitPrice = unitPrice;
    }

    public int getReturnDetailId() { return returnDetailId; }
    public void setReturnDetailId(int returnDetailId) { this.returnDetailId = returnDetailId; }

    public int getReturnId() { return returnId; }
    public void setReturnId(int returnId) { this.returnId = returnId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }

    public boolean isIn() { return DIRECTION_IN.equalsIgnoreCase(direction); }
    public boolean isOut() { return DIRECTION_OUT.equalsIgnoreCase(direction); }

    /** Khong co cot computed trong DB (khac InvoiceDetails.LineTotal) - tu tinh o tang Java. */
    public BigDecimal getLineTotal() {
        if (unitPrice == null) return BigDecimal.ZERO;
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}