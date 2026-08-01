package com.model;

import java.time.LocalDateTime;

/**
 * Model cho bang StockAlerts (sql/StockAlerts_Migration.sql) - bao cao cua
 * NV ban hang gui cho Quan ly kho khi phat hien 1 san pham het hang
 * (AlertType = OUT_OF_STOCK) hoac sap het hang, duoi muc ton toi thieu
 * (AlertType = LOW_STOCK). Quan ly kho xem/xu ly o StockAlertPanel, chuyen
 * Status NEW -> PLANNED (da len ke hoach nhap bo sung) -> RESOLVED.
 */
public class StockAlert {

    private int alertId;
    private int productId;
    private String productCode;   // join tu Products, chi doc
    private String productName;   // join tu Products, chi doc
    private int minStock;         // join tu Products, chi doc - de doi chieu voi StockAtReport
    private String alertType;     // LOW_STOCK | OUT_OF_STOCK
    private int stockAtReport;
    private String note;
    private int reportedBy;
    private String reportedByName; // join tu Users, chi doc
    private LocalDateTime createdAt;
    private String status;        // NEW | PLANNED | RESOLVED
    private boolean seenByInventoryManager;
    private Integer resolvedBy;
    private String resolvedByName; // join tu Users, chi doc
    private LocalDateTime resolvedAt;

    public StockAlert() {
    }

    public int getAlertId() { return alertId; }
    public void setAlertId(int alertId) { this.alertId = alertId; }

    public int getProductId() { return productId; }
    public void setProductId(int productId) { this.productId = productId; }

    public String getProductCode() { return productCode; }
    public void setProductCode(String productCode) { this.productCode = productCode; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getMinStock() { return minStock; }
    public void setMinStock(int minStock) { this.minStock = minStock; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public int getStockAtReport() { return stockAtReport; }
    public void setStockAtReport(int stockAtReport) { this.stockAtReport = stockAtReport; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getReportedBy() { return reportedBy; }
    public void setReportedBy(int reportedBy) { this.reportedBy = reportedBy; }

    public String getReportedByName() { return reportedByName; }
    public void setReportedByName(String reportedByName) { this.reportedByName = reportedByName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isSeenByInventoryManager() { return seenByInventoryManager; }
    public void setSeenByInventoryManager(boolean seenByInventoryManager) { this.seenByInventoryManager = seenByInventoryManager; }

    public Integer getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(Integer resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolvedByName() { return resolvedByName; }
    public void setResolvedByName(String resolvedByName) { this.resolvedByName = resolvedByName; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }

    public boolean isOutOfStock() {
        return "OUT_OF_STOCK".equalsIgnoreCase(alertType);
    }

    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(status);
    }

    @Override
    public String toString() {
        return "StockAlert{" +
                "alertId=" + alertId +
                ", productName='" + productName + '\'' +
                ", alertType='" + alertType + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}