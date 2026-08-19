package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một dòng thanh toán thực tế của hóa đơn POS.
 * Một hóa đơn có thể có nhiều dòng (ví dụ tiền mặt + thẻ).
 */
public class InvoicePayment {
    public static final String METHOD_CASH = "CASH";
    public static final String METHOD_BANK_TRANSFER = "BANK_TRANSFER";
    public static final String METHOD_PAYPAL = "PAYPAL";
    public static final String METHOD_CARD = "CARD";

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_REFUNDED = "REFUNDED";

    private long paymentId;
    private int invoiceId;
    private String paymentMethod;
    private BigDecimal amount = BigDecimal.ZERO;
    private BigDecimal tenderedAmount = BigDecimal.ZERO;
    private BigDecimal changeAmount = BigDecimal.ZERO;
    private String provider;
    private String providerTransactionId;
    private String providerPaymentId;
    private String idempotencyKey;
    private String paymentStatus = STATUS_COMPLETED;
    private int createdBy;
    private LocalDateTime createdAt;

    public InvoicePayment() {}

    public InvoicePayment(String paymentMethod, BigDecimal amount) {
        this.paymentMethod = paymentMethod;
        this.amount = amount != null ? amount : BigDecimal.ZERO;
    }

    public static InvoicePayment cash(BigDecimal amount, BigDecimal tendered) {
        InvoicePayment p = new InvoicePayment(METHOD_CASH, amount);
        BigDecimal safeTendered = tendered != null ? tendered : amount;
        p.setTenderedAmount(safeTendered);
        p.setChangeAmount(safeTendered.subtract(amount != null ? amount : BigDecimal.ZERO).max(BigDecimal.ZERO));
        return p;
    }

    public long getPaymentId() { return paymentId; }
    public void setPaymentId(long paymentId) { this.paymentId = paymentId; }
    public int getInvoiceId() { return invoiceId; }
    public void setInvoiceId(int invoiceId) { this.invoiceId = invoiceId; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getAmount() { return amount != null ? amount : BigDecimal.ZERO; }
    public void setAmount(BigDecimal amount) { this.amount = amount != null ? amount : BigDecimal.ZERO; }
    public BigDecimal getTenderedAmount() { return tenderedAmount != null ? tenderedAmount : BigDecimal.ZERO; }
    public void setTenderedAmount(BigDecimal tenderedAmount) { this.tenderedAmount = tenderedAmount != null ? tenderedAmount : BigDecimal.ZERO; }
    public BigDecimal getChangeAmount() { return changeAmount != null ? changeAmount : BigDecimal.ZERO; }
    public void setChangeAmount(BigDecimal changeAmount) { this.changeAmount = changeAmount != null ? changeAmount : BigDecimal.ZERO; }
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getProviderTransactionId() { return providerTransactionId; }
    public void setProviderTransactionId(String providerTransactionId) { this.providerTransactionId = providerTransactionId; }
    public String getProviderPaymentId() { return providerPaymentId; }
    public void setProviderPaymentId(String providerPaymentId) { this.providerPaymentId = providerPaymentId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getPaymentStatus() { return paymentStatus != null ? paymentStatus : STATUS_COMPLETED; }
    public void setPaymentStatus(String paymentStatus) { this.paymentStatus = paymentStatus; }
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isCash() { return METHOD_CASH.equalsIgnoreCase(paymentMethod); }
    public boolean isCompleted() { return STATUS_COMPLETED.equalsIgnoreCase(getPaymentStatus()); }
}
