package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO nhẹ dùng hiển thị card mã giảm giá trên banner/carousel trang chủ.
 * Không map bảng riêng — lấy từ Promotion đang được đánh dấu ShowOnBanner.
 */
public class PromoBannerItem {
    private final String code;
    private final String name;
    private final String discountType;
    private final BigDecimal discountValue;
    private final BigDecimal maxDiscountAmount;
    private final BigDecimal minOrderAmount;
    private final LocalDate endDate;

    public PromoBannerItem(String code, String name, String discountType,
                           BigDecimal discountValue, BigDecimal maxDiscountAmount,
                           BigDecimal minOrderAmount, LocalDate endDate) {
        this.code = code;
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
        this.minOrderAmount = minOrderAmount;
        this.endDate = endDate;
    }

    public static PromoBannerItem from(Promotion p) {
        if (p == null) return null;
        return new PromoBannerItem(
                p.getCode(),
                p.getName(),
                p.getDiscountType(),
                p.getDiscountValue(),
                p.getMaxDiscountAmount(),
                p.getMinOrderAmount(),
                p.getEndDate()
        );
    }

    public String getCode() { return code; }
    public String getName() { return name; }

    public String getDiscountLabel() {
        if ("PERCENT".equalsIgnoreCase(discountType)) {
            String s = "Giảm " + discountValue.stripTrailingZeros().toPlainString() + "%";
            if (maxDiscountAmount != null && maxDiscountAmount.signum() > 0) {
                s += " · tối đa " + String.format("%,.0fđ", maxDiscountAmount);
            }
            return s;
        }
        return "Giảm " + String.format("%,.0fđ", discountValue);
    }

    public String getConditionLabel() {
        if (minOrderAmount == null || minOrderAmount.signum() <= 0) return "Mọi đơn hàng";
        return "Đơn từ " + String.format("%,.0fđ", minOrderAmount);
    }

    public String getExpiryLabel() {
        return endDate == null ? "" : "HSD " + endDate;
    }
}