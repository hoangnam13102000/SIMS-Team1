package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class PurchaseReceiptDetail {

    private int receiptDetailId;
    private int receiptId;

    private int productId;
    private String productName;
    private String productCode;

    private int quantity;
    private BigDecimal importPrice;

    private String lotNumber;
    private LocalDate manufactureDate;
    private LocalDate expiryDate;

    // Ma lo he thong (LOT_xxxxxx) duoc trigger trg_PurchaseReceiptDetails_Insert
    // sinh ra tu dong sau khi INSERT dong nay - chi doc, khong ghi khi tao phieu.
    // Dung de doi chieu voi lotNumber (so lo tren bao bi NCC) khi tra loi kho.
    private String batchCode;

    public PurchaseReceiptDetail() {
    }

    public int getReceiptDetailId() { return receiptDetailId; }
    public void setReceiptDetailId(int receiptDetailId) { this.receiptDetailId = receiptDetailId; }

    public int getReceiptId() { return receiptId; }
    public void setReceiptId(int receiptId) { this.receiptId = receiptId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public BigDecimal getImportPrice() { return importPrice; }
    public void setImportPrice(BigDecimal importPrice) { this.importPrice = importPrice; }

    public String getLotNumber() { return lotNumber; }
    public void setLotNumber(String lotNumber) { this.lotNumber = lotNumber; }

    public LocalDate getManufactureDate() { return manufactureDate; }
    public void setManufactureDate(LocalDate manufactureDate) { this.manufactureDate = manufactureDate; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public String getBatchCode() { return batchCode; }
    public void setBatchCode(String batchCode) { this.batchCode = batchCode; }

    public BigDecimal lineTotal() {
        if (importPrice == null) return BigDecimal.ZERO;
        return importPrice.multiply(BigDecimal.valueOf(quantity));
    }
}