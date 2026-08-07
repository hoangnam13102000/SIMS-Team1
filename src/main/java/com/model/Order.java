package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;


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

    private String orderStatus; // NEW | CONFIRMED | SHIPPING | COMPLETED | CANCELLED
    private boolean seenByAdmin;

    private LocalDateTime completedAt; // set khi chuyen sang COMPLETED - dung tinh han 1 ngay duoc bam "Tra hang"
    private Integer invoiceId;         // hoa don duoc tu dong lap khi COMPLETED (null neu chua/khong co)
    private boolean returnRequested;   // da co yeu cau doi/tra PENDING/APPROVED gan voi hoa don nay chua

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

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }

    public Integer getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Integer invoiceId) { this.invoiceId = invoiceId; }

    public boolean isReturnRequested() { return returnRequested; }
    public void setReturnRequested(boolean returnRequested) { this.returnRequested = returnRequested; }

    public boolean isCancelled() { return "CANCELLED".equalsIgnoreCase(orderStatus); }
    public boolean isConfirmed() { return "CONFIRMED".equalsIgnoreCase(orderStatus); }
    public boolean isShipping() { return "SHIPPING".equalsIgnoreCase(orderStatus); }
    public boolean isCompleted() { return "COMPLETED".equalsIgnoreCase(orderStatus); }
    public boolean isPaid() { return "PAID".equalsIgnoreCase(paymentStatus); }

    /**
     * Nut "Trả hàng" phía khách chỉ hiện khi: đơn đã COMPLETED, có hóa đơn đi
     * kèm (mọi đơn COMPLETED đều có - xem OrderDAO), còn trong vòng 1 ngày kể
     * từ lúc hoàn thành, và chưa có yêu cầu đổi/trả nào đang PENDING/APPROVED.
     */
    public boolean canRequestReturn() {
        return isCompleted() && invoiceId != null && !returnRequested && completedAt != null
                && completedAt.plusDays(1).isAfter(LocalDateTime.now());
    }
}