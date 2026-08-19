package com.model;

import java.math.BigDecimal;

/** Snapshot mot dong san pham trong gio POS tam giu. */
public class HeldCartItem {
    private long holdItemId;
    private long holdId;
    private int productId;
    private String productCodeSnapshot;
    private String productNameSnapshot;
    private int quantity;
    private BigDecimal unitPriceSnapshot = BigDecimal.ZERO;

    public long getHoldItemId() { return holdItemId; }
    public void setHoldItemId(long holdItemId) { this.holdItemId = holdItemId; }

    public long getHoldId() { return holdId; }
    public void setHoldId(long holdId) { this.holdId = holdId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCodeSnapshot() { return productCodeSnapshot; }
    public void setProductCodeSnapshot(String productCodeSnapshot) { this.productCodeSnapshot = productCodeSnapshot; }

    public String getProductNameSnapshot() { return productNameSnapshot; }
    public void setProductNameSnapshot(String productNameSnapshot) { this.productNameSnapshot = productNameSnapshot; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getUnitPriceSnapshot() { return unitPriceSnapshot != null ? unitPriceSnapshot : BigDecimal.ZERO; }
    public void setUnitPriceSnapshot(BigDecimal unitPriceSnapshot) {
        this.unitPriceSnapshot = unitPriceSnapshot != null ? unitPriceSnapshot : BigDecimal.ZERO;
    }
}
