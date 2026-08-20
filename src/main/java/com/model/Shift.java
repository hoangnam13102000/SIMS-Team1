package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mot ca lam viec tai quay. Cac so lieu doi soat da dong duoc luu lai de
 * lich su khong bi thay doi khi du lieu giao dich phat sinh sau nay.
 *
 * Vong doi ca ban hang chi gom OPEN -> CLOSED.
 * Trang thai doi soat (PENDING/APPROVED/REJECTED) duoc tach rieng trong
 * ShiftReconciliations de giu lich su nhieu lan kiem dem.
 *
 * Cac hang du lieu cu PENDING_APPROVAL/APPROVED/REJECTED duoc migration 12
 * chuyen ve CLOSED va tao ban ghi doi soat tuong ung.
 */
public class Shift {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_CLOSED = "CLOSED";

    /** Legacy constants - chi de doc du lieu cu truoc migration 12. */
    @Deprecated public static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    @Deprecated public static final String STATUS_APPROVED = "APPROVED";
    @Deprecated public static final String STATUS_REJECTED = "REJECTED";

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

    // Trang thai doi soat MOI NHAT (nguon that: ShiftReconciliations).
    private Long reconciliationId;
    private Integer reconciliationRevisionNo;
    private String reconciliationStatus;
    private LocalDateTime reconciliationSubmittedAt;

    // Duyet / tu choi doi soat moi nhat (giu getter cu de UI tuong thich).
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

    public Long getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(Long reconciliationId) { this.reconciliationId = reconciliationId; }
    public Integer getReconciliationRevisionNo() { return reconciliationRevisionNo; }
    public void setReconciliationRevisionNo(Integer reconciliationRevisionNo) { this.reconciliationRevisionNo = reconciliationRevisionNo; }
    public String getReconciliationStatus() { return reconciliationStatus; }
    public void setReconciliationStatus(String reconciliationStatus) { this.reconciliationStatus = reconciliationStatus; }
    public LocalDateTime getReconciliationSubmittedAt() { return reconciliationSubmittedAt; }
    public void setReconciliationSubmittedAt(LocalDateTime reconciliationSubmittedAt) { this.reconciliationSubmittedAt = reconciliationSubmittedAt; }

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

    /** Ca ban hang da ket thuc; trang thai doi soat khong lam mo lai ca. */
    public boolean isClosed() {
        return STATUS_CLOSED.equalsIgnoreCase(status)
                || STATUS_PENDING_APPROVAL.equalsIgnoreCase(status)
                || STATUS_APPROVED.equalsIgnoreCase(status)
                || STATUS_REJECTED.equalsIgnoreCase(status);
    }

    public boolean isPendingApproval() {
        return ShiftReconciliation.STATUS_PENDING.equalsIgnoreCase(reconciliationStatus)
                || (reconciliationStatus == null && STATUS_PENDING_APPROVAL.equalsIgnoreCase(status));
    }

    public boolean isRejected() {
        return ShiftReconciliation.STATUS_REJECTED.equalsIgnoreCase(reconciliationStatus)
                || (reconciliationStatus == null && STATUS_REJECTED.equalsIgnoreCase(status));
    }

    public boolean isApproved() {
        if (ShiftReconciliation.STATUS_APPROVED.equalsIgnoreCase(reconciliationStatus)) return true;
        if (reconciliationStatus != null) return false;
        return STATUS_APPROVED.equalsIgnoreCase(status) || STATUS_CLOSED.equalsIgnoreCase(status);
    }

    /** Trang thai vat ly cua ca, khong tron voi ket qua doi soat. */
    public String getShiftStatusLabel() {
        if (isOpen()) return "Đang mở";
        if (isClosed()) return "Đã đóng";
        return status == null || status.isBlank() ? "—" : status;
    }

    public String getReconciliationStatusLabel() {
        if (isOpen()) return "Chưa đối soát";
        if (isPendingApproval()) return "Chờ duyệt";
        if (isRejected()) return "Cần kiểm lại";
        if (isApproved()) return "Đã duyệt";
        return "Chưa đối soát";
    }

    /**
     * Nhan workflow de cac bang cu van hien thi mot cot ngan gon.
     */
    public String getStatusLabel() {
        if (isOpen()) return "Đang mở";
        if (isPendingApproval()) return "Chờ duyệt";
        if (isRejected()) return "Cần kiểm lại";
        if (isApproved()) return "Đã duyệt";
        return "Đã đóng";
    }

    private BigDecimal valueOrZero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}