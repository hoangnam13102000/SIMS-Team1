package com.model;

import java.time.LocalDateTime;

/**
 * Bao cao ngoai le do NV ban hang gui cho Quan ly ban hang (xem bang
 * ExceptionReports trong sql/SIMS.sql) - vd: SP khach can mua nhung chua
 * co trong he thong, khach yeu cau SP dac biet, tinh huong bat thuong
 * khac khong thuoc cac luong nghiep vu san co (hoa don, doi/tra, canh bao
 * ton kho...).
 */
public class ExceptionReport {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_HANDLED = "HANDLED";

    private int reportId;
    private int createdBy;
    private String createdByName;
    private String content;
    private String status; // PENDING | HANDLED

    private Integer handledBy;
    private String handledByName;
    private LocalDateTime handledAt;

    private LocalDateTime createdAt;

    public ExceptionReport() {
    }

    public int getReportId() { return reportId; }
    public void setReportId(int reportId) { this.reportId = reportId; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getHandledBy() { return handledBy; }
    public void setHandledBy(Integer handledBy) { this.handledBy = handledBy; }

    public String getHandledByName() { return handledByName; }
    public void setHandledByName(String handledByName) { this.handledByName = handledByName; }

    public LocalDateTime getHandledAt() { return handledAt; }
    public void setHandledAt(LocalDateTime handledAt) { this.handledAt = handledAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public boolean isPending() { return STATUS_PENDING.equalsIgnoreCase(status); }
    public boolean isHandled() { return STATUS_HANDLED.equalsIgnoreCase(status); }
}