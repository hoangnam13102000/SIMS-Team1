package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Invoice {

    private int invoiceId;
    private String invoiceCode;

    private int shiftId;
    private int createdBy;
    private String createdByName;

    private Integer customerId;
    private String customerName;

    private LocalDateTime createdAt;
    private BigDecimal subTotal;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private Integer promotionId;
    private String promotionCode;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;

    private String paymentMethod;
    private String status;
    private String cancelReason;
    private LocalDateTime cancelledAt;

    private String payPalOrderId;
    private String payPalCaptureId;

    private int itemCount;

    private int pointsUsed;
    private BigDecimal pointsDiscountAmount = BigDecimal.ZERO;

    private int pointsEarned;

    public Invoice() {
    }

    public int getInvoiceId() { return invoiceId; }
    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }

    public String getInvoiceCode() { return invoiceCode; }
    public void setInvoiceCode(String invoiceCode) { this.invoiceCode = invoiceCode; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public BigDecimal getSubTotal() { return subTotal; }
    public void setSubTotal(BigDecimal subTotal) { this.subTotal = subTotal; }

    public BigDecimal getDiscountAmount() {
        return discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }
    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public Integer getPromotionId() { return promotionId; }
    public void setPromotionId(Integer promotionId) { this.promotionId = promotionId; }

    public String getPromotionCode() { return promotionCode; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }

    public BigDecimal getVatRate() { return vatRate; }
    public void setVatRate(BigDecimal vatRate) { this.vatRate = vatRate; }

    public BigDecimal getVatAmount() { return vatAmount; }
    public void setVatAmount(BigDecimal vatAmount) { this.vatAmount = vatAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPayPalOrderId() { return payPalOrderId; }
    public void setPayPalOrderId(String payPalOrderId) { this.payPalOrderId = payPalOrderId; }

    public String getPayPalCaptureId() { return payPalCaptureId; }
    public void setPayPalCaptureId(String payPalCaptureId) { this.payPalCaptureId = payPalCaptureId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public int getPointsUsed() { return pointsUsed; }
    public void setPointsUsed(int pointsUsed) { this.pointsUsed = Math.max(0, pointsUsed); }

    public BigDecimal getPointsDiscountAmount() {
        return pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO;
    }
    public void setPointsDiscountAmount(BigDecimal pointsDiscountAmount) {
        this.pointsDiscountAmount = pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO;
    }

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }

    public boolean isCancellableToday() {
        return !isCancelled() && createdAt != null
                && createdAt.toLocalDate().isEqual(java.time.LocalDate.now());
    }
}