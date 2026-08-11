package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Promotion {

    public static final String TYPE_PERCENT = "PERCENT";
    public static final String TYPE_AMOUNT = "AMOUNT";

    private int promotionId;
    private String code;
    private String name;
    private String discountType = TYPE_PERCENT;
    private BigDecimal discountValue;
    private BigDecimal maxDiscountAmount;
    private BigDecimal minOrderAmount = BigDecimal.ZERO;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer usageLimit;
    private int usedCount;
    private boolean active = true;
    private int createdBy;
    private java.time.LocalDateTime createdAt;

    public int getPromotionId() { return promotionId; }
    public void setPromotionId(int promotionId) { this.promotionId = promotionId; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDiscountType() { return discountType; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }

    public boolean isPercent() { return TYPE_PERCENT.equals(discountType); }

    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }

    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }

    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

    public int getUsedCount() { return usedCount; }
    public void setUsedCount(int usedCount) { this.usedCount = usedCount; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    /** true neu con luot su dung (chua cham UsageLimit, hoac khong gioi han). */
    public boolean hasUsageLeft() {
        return usageLimit == null || usedCount < usageLimit;
    }

    /**
     * Trang thai hien thi (khong luu DB, tinh tu du lieu hien co):
     * TAT (IsActive=0) > HET_LUOT (het UsageLimit) > CHUA_BAT_DAU / DA_KET_THUC (ngoai khoang ngay) > DANG_DIEN_RA.
     */
    public String computeStatus() {
        if (!active) return "PAUSED";
        if (!hasUsageLeft()) return "EXHAUSTED";
        LocalDate today = LocalDate.now();
        if (startDate != null && today.isBefore(startDate)) return "UPCOMING";
        if (endDate != null && today.isAfter(endDate)) return "EXPIRED";
        return "RUNNING";
    }

    /**
     * Tinh so tien duoc giam cho 1 don hang co tong tien {@code orderAmount}.
     * Tra ve BigDecimal.ZERO neu khong du dieu kien (chua/da het hieu luc, bi
     * tat, het luot dung, hoac don hang chua dat MinOrderAmount) - KHONG nem
     * exception, de noi goi (POS...) chi can kiem tra kt qua > 0.
     */
    public BigDecimal calculateDiscount(BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.signum() <= 0) return BigDecimal.ZERO;
        if (!"RUNNING".equals(computeStatus())) return BigDecimal.ZERO;
        if (minOrderAmount != null && orderAmount.compareTo(minOrderAmount) < 0) return BigDecimal.ZERO;
        if (discountValue == null) return BigDecimal.ZERO;

        BigDecimal discount;
        if (isPercent()) {
            discount = orderAmount.multiply(discountValue)
                    .divide(BigDecimal.valueOf(100), java.math.RoundingMode.HALF_UP);
            if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                discount = maxDiscountAmount;
            }
        } else {
            discount = discountValue;
        }
        return discount.compareTo(orderAmount) > 0 ? orderAmount : discount;
    }
}