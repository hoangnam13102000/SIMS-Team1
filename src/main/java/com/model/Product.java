package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Model cho bang Products (SIMS.sql), co kem CategoryName lay tu JOIN
 * Categories de cac man hinh hien thi (vd HomePanel phia client) khong
 * phai truy van rieng ten danh muc cho tung san pham.
 */
public class Product {

    private int productId;
    private String productCode; // "SP_" + ProductID dem 4 so (vd SP_0001) - cot COMPUTED PERSISTED, CHI DOC
    private String productName;
    private int categoryId;
    private String categoryName;
    private String brand;         // Thuong hieu: Vinamilk, TH True Milk... (tuy chon)
    private String unit;          // Don vi tinh: Kg, Hop, Chai, Goi... (tuy chon)
    private String weightVolume;  // Khoi luong/dung tich: 180ml, 500g, 1kg... (tuy chon)
    private String description;   // Mo ta san pham (tuy chon)
    private BigDecimal importPrice;
    private BigDecimal sellPrice;
    private String imageUrl;
    private int stock;
    private int minStock;
    private String status; // ACTIVE | DISABLED
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt; // null neu chua tung sua sau khi tao

    public Product() {
    }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getWeightVolume() { return weightVolume; }
    public void setWeightVolume(String weightVolume) { this.weightVolume = weightVolume; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getImportPrice() { return importPrice; }
    public void setImportPrice(BigDecimal importPrice) { this.importPrice = importPrice; }

    public BigDecimal getSellPrice() { return sellPrice; }
    public void setSellPrice(BigDecimal sellPrice) { this.sellPrice = sellPrice; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public int getMinStock() { return minStock; }
    public void setMinStock(int minStock) { this.minStock = minStock; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    public boolean isOutOfStock() {
        return stock <= 0;
    }

    public boolean isLowStock() {
        return stock > 0 && stock <= minStock;
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId=" + productId +
                ", productCode='" + productCode + '\'' +
                ", productName='" + productName + '\'' +
                ", sellPrice=" + sellPrice +
                ", stock=" + stock +
                '}';
    }
}