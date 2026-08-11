package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Order {
    private int orderId;
    private String orderCode;
    private Integer customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shippingAddress;
    private LocalDateTime createdAt;
    private BigDecimal subTotal;
    private BigDecimal discountAmount = BigDecimal.ZERO;
    private Integer promotionId;
    private String promotionCode;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String payPalOrderId;
    private String payPalCaptureId;
    private String orderStatus;
    private boolean seenByAdmin;
    private String cancelReason;
    private LocalDateTime completedAt;
    private Integer invoiceId;
    private int itemCount;

    private boolean returnRequested;
    private String latestReturnStatus;
    private String latestReturnType;
    private BigDecimal latestReturnValue;
    private String latestReturnRejectionReason;
    private String latestReturnReason;
    private LocalDateTime latestReturnCreatedAt;

    public int getOrderId() { return orderId; }
    public void setOrderId(int orderId) { this.orderId = orderId; }

    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }

    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }

    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }

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

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }

    public String getPayPalOrderId() { return payPalOrderId; }
    public void setPayPalOrderId(String payPalOrderId) { this.payPalOrderId = payPalOrderId; }

    public String getPayPalCaptureId() { return payPalCaptureId; }
    public void setPayPalCaptureId(String payPalCaptureId) { this.payPalCaptureId = payPalCaptureId; }

    public String getOrderStatus() { return orderStatus; }
    public void setOrderStatus(String orderStatus) { this.orderStatus = orderStatus; }

    public boolean isSeenByAdmin() { return seenByAdmin; }
    public void setSeenByAdmin(boolean seenByAdmin) { this.seenByAdmin = seenByAdmin; }

    public String getCancelReason() { return cancelReason; }
    public void setCancelReason(String cancelReason) { this.cancelReason = cancelReason; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Integer invoiceId) { this.invoiceId = invoiceId; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public boolean isReturnRequested() { return returnRequested; }
    public void setReturnRequested(boolean returnRequested) { this.returnRequested = returnRequested; }

    public String getLatestReturnStatus() { return latestReturnStatus; }
    public void setLatestReturnStatus(String latestReturnStatus) { this.latestReturnStatus = latestReturnStatus; }

    public String getLatestReturnType() { return latestReturnType; }
    public void setLatestReturnType(String latestReturnType) { this.latestReturnType = latestReturnType; }

    public BigDecimal getLatestReturnValue() { return latestReturnValue; }
    public void setLatestReturnValue(BigDecimal latestReturnValue) { this.latestReturnValue = latestReturnValue; }

    public String getLatestReturnRejectionReason() { return latestReturnRejectionReason; }
    public void setLatestReturnRejectionReason(String latestReturnRejectionReason) {
        this.latestReturnRejectionReason = latestReturnRejectionReason;
    }

    public String getLatestReturnReason() { return latestReturnReason; }
    public void setLatestReturnReason(String latestReturnReason) { this.latestReturnReason = latestReturnReason; }

    public LocalDateTime getLatestReturnCreatedAt() { return latestReturnCreatedAt; }
    public void setLatestReturnCreatedAt(LocalDateTime latestReturnCreatedAt) {
        this.latestReturnCreatedAt = latestReturnCreatedAt;
    }

    public boolean isCancelled() { return "CANCELLED".equalsIgnoreCase(orderStatus); }
    public boolean isConfirmed() { return "CONFIRMED".equalsIgnoreCase(orderStatus); }
    public boolean isShipping() { return "SHIPPING".equalsIgnoreCase(orderStatus); }
    public boolean isCompleted() { return "COMPLETED".equalsIgnoreCase(orderStatus); }
    public boolean isPaid() { return "PAID".equalsIgnoreCase(paymentStatus); }

    public boolean canRequestReturn() {
        return isCompleted() && invoiceId != null && !returnRequested && completedAt != null
                && completedAt.plusDays(1).isAfter(LocalDateTime.now());
    }
}
