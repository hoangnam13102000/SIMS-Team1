package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mot ca lam viec tai quay. Cac so lieu doi soat da dong duoc luu lai de
 * lich su khong bi thay doi khi du lieu giao dich phat sinh sau nay.
 */
public class Shift {

    private int shiftId;
    private int userId;
    private String userName;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String status;
    private BigDecimal openingCash = BigDecimal.ZERO;
    private BigDecimal expectedCash;
    private BigDecimal countedCash;
    private BigDecimal cashDifference;
    private String openingNote;
    private String closingNote;
    private Integer closedBy;
    private String closedByName;
    private int invoiceCount;
    private BigDecimal cashSales = BigDecimal.ZERO;
    private BigDecimal cashIn = BigDecimal.ZERO;
    private BigDecimal cashOut = BigDecimal.ZERO;
    private BigDecimal cashRefunds = BigDecimal.ZERO;

    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getOpeningCash() { return openingCash; }
    public void setOpeningCash(BigDecimal openingCash) {
        this.openingCash = valueOrZero(openingCash);
    }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = expectedCash; }
    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = countedCash; }
    public BigDecimal getCashDifference() { return cashDifference; }
    public void setCashDifference(BigDecimal cashDifference) { this.cashDifference = cashDifference; }
    public String getOpeningNote() { return openingNote; }
    public void setOpeningNote(String openingNote) { this.openingNote = openingNote; }
    public String getClosingNote() { return closingNote; }
    public void setClosingNote(String closingNote) { this.closingNote = closingNote; }
    public Integer getClosedBy() { return closedBy; }
    public void setClosedBy(Integer closedBy) { this.closedBy = closedBy; }
    public String getClosedByName() { return closedByName; }
    public void setClosedByName(String closedByName) { this.closedByName = closedByName; }
    public int getInvoiceCount() { return invoiceCount; }
    public void setInvoiceCount(int invoiceCount) { this.invoiceCount = invoiceCount; }
    public BigDecimal getCashSales() { return cashSales; }
    public void setCashSales(BigDecimal cashSales) { this.cashSales = valueOrZero(cashSales); }
    public BigDecimal getCashIn() { return cashIn; }
    public void setCashIn(BigDecimal cashIn) { this.cashIn = valueOrZero(cashIn); }
    public BigDecimal getCashOut() { return cashOut; }
    public void setCashOut(BigDecimal cashOut) { this.cashOut = valueOrZero(cashOut); }
    public BigDecimal getCashRefunds() { return cashRefunds; }
    public void setCashRefunds(BigDecimal cashRefunds) { this.cashRefunds = valueOrZero(cashRefunds); }

    public boolean isOpen() {
        return "OPEN".equalsIgnoreCase(status);
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}