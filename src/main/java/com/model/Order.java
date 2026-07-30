package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Đơn hàng online do khách tự đặt ở ClientMainFrame (giỏ hàng -> thanh
 * toán). Tách riêng khỏi {@link Invoice} vì Invoices gắn với ca làm việc
 * (ShiftID) + nhân viên lập (CreatedBy) tại quầy - không có ở luồng đặt
 * hàng online. Xem sql/Orders_SIMS.sql.
 */
public class Order {

    private int orderId;
    private String orderCode; // "DH" + OrderID đệm 4 số (cột COMPUTED trong DB)

    private Integer customerId; // null = khách đặt không đăng nhập
    private String customerName;
    private String customerEmail;
    private String customerPhone;
    private String shippingAddress;

    private LocalDateTime createdAt;
    private BigDecimal subTotal;
    private BigDecimal totalAmount;

    private String paymentMethod; // COD | PAYPAL
    private String paymentStatus; // PENDING | PAID | FAILED
    private String payPalOrderId;
    private String payPalCaptureId;

    private String orderStatus; // NEW | CONFIRMED | CANCELLED
    private boolean seenByAdmin;

    private int itemCount;

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

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }

    public boolean isCancelled() { return "CANCELLED".equalsIgnoreCase(orderStatus); }
    public boolean isConfirmed() { return "CONFIRMED".equalsIgnoreCase(orderStatus); }
    public boolean isPaid() { return "PAID".equalsIgnoreCase(paymentStatus); }
}