package com.model;

import java.time.LocalDateTime;

public class ReturnExchangeEvidence {
    private long evidenceId;
    private int returnId;
    private String imageUrl;
    private String originalFileName;
    private int uploadedBy;
    private String uploadedByName;
    private LocalDateTime uploadedAt;

    public long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(long evidenceId) { this.evidenceId = evidenceId; }
    public int getReturnId() { return returnId; }
    public void setReturnId(int returnId) { this.returnId = returnId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public int getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(int uploadedBy) { this.uploadedBy = uploadedBy; }
    public String getUploadedByName() { return uploadedByName; }
    public void setUploadedByName(String uploadedByName) { this.uploadedByName = uploadedByName; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(LocalDateTime uploadedAt) { this.uploadedAt = uploadedAt; }
}
