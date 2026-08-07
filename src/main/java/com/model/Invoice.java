package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Invoice {

    private int invoiceId;
    private String invoiceCode;   // "ORD_" + InvoiceID dem 4 so (vd ORD_0001)

    private int shiftId;          // Ca ban hang dang mo luc lap hoa don (xem ShiftDAO)
    private int createdBy;
    private String createdByName;

    private Integer customerId;   // null = khach le (khong co tai khoan)
    private String customerName;

    private LocalDateTime createdAt;
    private BigDecimal subTotal;
    private BigDecimal vatRate;
    private BigDecimal vatAmount;
    private BigDecimal totalAmount;

    private String paymentMethod; // CASH | BANK_TRANSFER | PAYPAL | CARD
    private String status;        // ACTIVE | CANCELLED
    private String cancelReason;
    private LocalDateTime cancelledAt;

    // Chi duoc set khi paymentMethod = PAYPAL (xem PosPanel#payWithPayPalThenCreateInvoice) -
    // luu lai de doi soat/tra cuu giao dich tren PayPal Dashboard (sandbox) khi can.
    private String payPalOrderId;
    private String payPalCaptureId;

    private int itemCount; // so dong san pham khac nhau trong hoa don

    // Chi duoc gan (KHONG luu cot rieng trong Invoices) ngay sau khi tao hoa
    // don thanh cong cho khach co tai khoan (xem InvoiceDAO#createInvoice) -
    // dung de hien thong bao "+N diem" tren PosPanel.
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

    public int getPointsEarned() { return pointsEarned; }
    public void setPointsEarned(int pointsEarned) { this.pointsEarned = pointsEarned; }

    public boolean isCancelled() {
        return "CANCELLED".equalsIgnoreCase(status);
    }

    /** Chi hoa don ACTIVE + con trong ngay tao moi du dieu kien de HIEN nut huy tren UI
     * (nghiep vu that su van do trigger trg_Invoices_CancelSameDayOnly quyet dinh o DB). */
    public boolean isCancellableToday() {
        return !isCancelled() && createdAt != null
                && createdAt.toLocalDate().isEqual(java.time.LocalDate.now());
    }
}