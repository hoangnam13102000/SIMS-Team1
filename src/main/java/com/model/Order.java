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
    private Integer assignedTo;
    private String assignedToName;
    private LocalDateTime assignedAt;
    private Integer assignedBy;
    private String assignedByName;
    private int itemCount;

    private boolean returnRequested;
    private String latestReturnStatus;
    private String latestReturnType;
    private BigDecimal latestReturnValue;
    private String latestReturnRejectionReason;
    private String latestReturnReason;
    private LocalDateTime latestReturnCreatedAt;

    // ---- Tom tat doi/tra tong hop (khong luu DB, gan tu OrderDAO.attachReturnSummary) ----
    // Ap dung cung cach tinh nhu Invoice: tong hop TAT CA phieu APPROVED (khac voi
    // latestReturn* o tren chi lay 1 phieu gan nhat, dung cho OrderHistoryPanel/khach hang).
    /** Tong tien da hoan thuc te (Sigma ReturnExchanges.TotalValue APPROVED, qua InvoiceID). */
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    /** So phieu doi/tra da duyet. */
    private int approvedReturnCount;
    /** NONE | PARTIAL | FULL — suy tu hang da tra vs hang da ban. */
    private String returnState = "NONE";

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

    public Integer getAssignedTo() { return assignedTo; }
    public void setAssignedTo(Integer assignedTo) { this.assignedTo = assignedTo; }

    public String getAssignedToName() { return assignedToName; }
    public void setAssignedToName(String assignedToName) { this.assignedToName = assignedToName; }

    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }

    public Integer getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Integer assignedBy) { this.assignedBy = assignedBy; }

    public String getAssignedByName() { return assignedByName; }
    public void setAssignedByName(String assignedByName) { this.assignedByName = assignedByName; }

    public boolean isAssignedTo(int userId) {
        return assignedTo != null && assignedTo == userId;
    }

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

    public BigDecimal getRefundedAmount() {
        return refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
    }
    public void setRefundedAmount(BigDecimal refundedAmount) {
        this.refundedAmount = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
    }

    public int getApprovedReturnCount() { return approvedReturnCount; }
    public void setApprovedReturnCount(int approvedReturnCount) {
        this.approvedReturnCount = Math.max(0, approvedReturnCount);
    }

    public String getReturnState() { return returnState != null ? returnState : "NONE"; }
    public void setReturnState(String returnState) {
        this.returnState = returnState != null ? returnState : "NONE";
    }

    public boolean hasReturns() {
        return approvedReturnCount > 0 || getRefundedAmount().signum() > 0;
    }

    /** Nhan hien thi trang thai doi/tra (giong Invoice.getReturnStateLabel). */
    public String getReturnStateLabel() {
        return switch (getReturnState()) {
            case "FULL" -> "Đã trả hết";
            case "PARTIAL" -> "Trả một phần";
            default -> "—";
        };
    }

    /** Ghi chu ngan cho chi tiet don / tooltip, vd "Đã hoàn 15.000đ · 1 phiếu trả". */
    public String getReturnNote() {
        if (!hasReturns()) return "";
        String money = String.format("%,.0fđ", getRefundedAmount());
        return "Đã hoàn " + money + " · " + approvedReturnCount + " phiếu trả";
    }
}