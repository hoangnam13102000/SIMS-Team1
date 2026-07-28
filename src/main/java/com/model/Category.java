package com.model;

/**
 * Model cho bang Categories (SIMS.sql), co kem activeProductCount (so san
 * pham dang ban thuoc danh muc nay) de man hinh "Danh muc" phia client
 * (CategoriesPanel) hien thi ma khong phai truy van rieng cho tung the.
 */
public class Category {

    private int categoryId;
    private String categoryName;
    private String status; // ACTIVE | DISABLED
    private int activeProductCount;

    public Category() {
    }

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getActiveProductCount() { return activeProductCount; }
    public void setActiveProductCount(int activeProductCount) { this.activeProductCount = activeProductCount; }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "Category{" +
                "categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", activeProductCount=" + activeProductCount +
                '}';
    }
}