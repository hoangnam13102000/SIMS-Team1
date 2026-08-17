package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Ban ghi thu/chi tien mat trong ca. Khong sua/xoa de bao toan audit. */
public class ShiftCashTransaction {

    public static final String CASH_IN = "CASH_IN";
    public static final String CASH_OUT = "CASH_OUT";

    private long cashTransactionId;
    private String transactionCode;
    private int shiftId;
    private String transactionType;
    private BigDecimal amount;
    private String reason;
    private int createdBy;
    private String createdByName;
    private LocalDateTime createdAt;

    public long getCashTransactionId() { return cashTransactionId; }
    public void setCashTransactionId(long cashTransactionId) { this.cashTransactionId = cashTransactionId; }
    public String getTransactionCode() { return transactionCode; }
    public void setTransactionCode(String transactionCode) { this.transactionCode = transactionCode; }
    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }
    public String getTransactionType() { return transactionType; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isCashIn() {
        return CASH_IN.equals(transactionType);
    }
}