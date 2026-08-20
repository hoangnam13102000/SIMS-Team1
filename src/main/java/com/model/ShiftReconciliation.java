package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mot lan doi soat quy cua mot ca da dong.
 *
 * Shift.Status chi phan anh vong doi ban hang (OPEN/CLOSED). Trang thai
 * phan xet doi soat duoc luu rieng tai day de ca da dong khong bi mo lai khi
 * quan ly yeu cau nhan vien kiem dem lai.
 */
public class ShiftReconciliation {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    private long reconciliationId;
    private int shiftId;
    private int revisionNo;
    private BigDecimal expectedCash = BigDecimal.ZERO;
    private BigDecimal countedCash = BigDecimal.ZERO;
    private BigDecimal difference = BigDecimal.ZERO;
    private String closingNote;
    private String status;
    private int submittedBy;
    private String submittedByName;
    private LocalDateTime submittedAt;
    private Integer reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewNote;

    public long getReconciliationId() { return reconciliationId; }
    public void setReconciliationId(long reconciliationId) { this.reconciliationId = reconciliationId; }
    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }
    public int getRevisionNo() { return revisionNo; }
    public void setRevisionNo(int revisionNo) { this.revisionNo = revisionNo; }
    public BigDecimal getExpectedCash() { return expectedCash; }
    public void setExpectedCash(BigDecimal expectedCash) { this.expectedCash = nz(expectedCash); }
    public BigDecimal getCountedCash() { return countedCash; }
    public void setCountedCash(BigDecimal countedCash) { this.countedCash = nz(countedCash); }
    public BigDecimal getDifference() { return difference; }
    public void setDifference(BigDecimal difference) { this.difference = nz(difference); }
    public String getClosingNote() { return closingNote; }
    public void setClosingNote(String closingNote) { this.closingNote = closingNote; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(int submittedBy) { this.submittedBy = submittedBy; }
    public String getSubmittedByName() { return submittedByName; }
    public void setSubmittedByName(String submittedByName) { this.submittedByName = submittedByName; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
    public Integer getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(Integer reviewedBy) { this.reviewedBy = reviewedBy; }
    public String getReviewedByName() { return reviewedByName; }
    public void setReviewedByName(String reviewedByName) { this.reviewedByName = reviewedByName; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }

    public boolean isPending() { return STATUS_PENDING.equalsIgnoreCase(status); }
    public boolean isApproved() { return STATUS_APPROVED.equalsIgnoreCase(status); }
    public boolean isRejected() { return STATUS_REJECTED.equalsIgnoreCase(status); }

    public String getStatusLabel() {
        if (isPending()) return "Chờ duyệt";
        if (isApproved()) return "Đã duyệt";
        if (isRejected()) return "Cần kiểm lại";
        return status == null || status.isBlank() ? "—" : status;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
