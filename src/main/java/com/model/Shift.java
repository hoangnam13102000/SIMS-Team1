package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mot ca lam viec tai quay. Cac so lieu doi soat da dong duoc luu lai de
 * lich su khong bi thay doi khi du lieu giao dich phat sinh sau nay.
 *
 * Vong doi trang thai:
 * OPEN → (dong ca) → PENDING_APPROVAL → (duyet) APPROVED
 *                                       → (tu choi) REJECTED
 *
 * Du lieu cu co the van con Status = CLOSED (coi nhu da duyet).
 */
public class Shift {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    /** Tương thích dữ liệu cũ (đóng ca trước khi có luồng duyệt). */
    public static final String STATUS_CLOSED = "CLOSED";

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

    // Duyệt / từ chối đối soát
    private Integer approvedBy;
    private String approvedByName;
    private LocalDateTime approvedAt;
    private String approvalNote;

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

    public Integer getApprovedBy() { return approvedBy; }
    public void setApprovedBy(Integer approvedBy) { this.approvedBy = approvedBy; }
    public String getApprovedByName() { return approvedByName; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getApprovalNote() { return approvalNote; }
    public void setApprovalNote(String approvalNote) { this.approvalNote = approvalNote; }

    public boolean isOpen() {
        return STATUS_OPEN.equalsIgnoreCase(status);
    }

    /** Ca đã đóng (không còn OPEN) — gồm chờ duyệt / đã duyệt / từ chối. */
    public boolean isClosed() {
        return status != null && !STATUS_OPEN.equalsIgnoreCase(status);
    }

    /** Ca đã đóng và đang chờ quản lý duyệt đối soát. */
    public boolean isPendingApproval() {
        return STATUS_PENDING_APPROVAL.equalsIgnoreCase(status);
    }

    public boolean isRejected() {
        return STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isApproved() {
        return STATUS_APPROVED.equalsIgnoreCase(status)
                || STATUS_CLOSED.equalsIgnoreCase(status);
    }

    /**
     * Nhãn hiển thị trên UI (bảng, chip, filter).
     */
    public String getStatusLabel() {
        if (status == null || status.isBlank()) {
            return "—";
        }
        if (STATUS_OPEN.equalsIgnoreCase(status)) {
            return "Đang mở";
        }
        if (STATUS_PENDING_APPROVAL.equalsIgnoreCase(status)) {
            return "Chờ duyệt";
        }
        if (STATUS_REJECTED.equalsIgnoreCase(status)) {
            return "Từ chối";
        }
        if (STATUS_APPROVED.equalsIgnoreCase(status) || STATUS_CLOSED.equalsIgnoreCase(status)) {
            return "Đã duyệt";
        }
        return status;
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}