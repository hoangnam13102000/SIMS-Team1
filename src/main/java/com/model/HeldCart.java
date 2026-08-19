package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Phieu tam giu gio hang tai POS. */
public class HeldCart {
    public static final String STATUS_HELD = "HELD";
    public static final String STATUS_RESTORED = "RESTORED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    private long holdId;
    private String holdCode;
    private int shiftId;
    private int heldBy;
    private String heldByName;
    private Integer customerId;
    private String customerLabelSnapshot;
    private Integer promotionId;
    private String promotionCode;
    private String paymentMethodSnapshot = "CASH";
    private int pointsToUse;
    private BigDecimal subTotalSnapshot = BigDecimal.ZERO;
    private BigDecimal discountSnapshot = BigDecimal.ZERO;
    private BigDecimal pointsDiscountSnapshot = BigDecimal.ZERO;
    private String status;
    private String note;
    private LocalDateTime heldAt;
    private LocalDateTime restoredAt;
    private LocalDateTime cancelledAt;
    private LocalDateTime expiredAt;
    private int itemCount;
    private List<HeldCartItem> items = new ArrayList<>();

    public long getHoldId() { return holdId; }
    public void setHoldId(long holdId) { this.holdId = holdId; }
    public String getHoldCode() { return holdCode; }
    public void setHoldCode(String holdCode) { this.holdCode = holdCode; }
    public int getShiftId() { return shiftId; }
    public void setShiftId(int shiftId) { this.shiftId = shiftId; }
    public int getHeldBy() { return heldBy; }
    public void setHeldBy(int heldBy) { this.heldBy = heldBy; }
    public String getHeldByName() { return heldByName; }
    public void setHeldByName(String heldByName) { this.heldByName = heldByName; }
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public String getCustomerLabelSnapshot() { return customerLabelSnapshot; }
    public void setCustomerLabelSnapshot(String customerLabelSnapshot) { this.customerLabelSnapshot = customerLabelSnapshot; }
    public Integer getPromotionId() { return promotionId; }
    public void setPromotionId(Integer promotionId) { this.promotionId = promotionId; }
    public String getPromotionCode() { return promotionCode; }
    public void setPromotionCode(String promotionCode) { this.promotionCode = promotionCode; }
    public String getPaymentMethodSnapshot() { return paymentMethodSnapshot; }
    public void setPaymentMethodSnapshot(String paymentMethodSnapshot) {
        this.paymentMethodSnapshot = paymentMethodSnapshot != null && !paymentMethodSnapshot.isBlank()
                ? paymentMethodSnapshot : "CASH";
    }
    public int getPointsToUse() { return pointsToUse; }
    public void setPointsToUse(int pointsToUse) { this.pointsToUse = pointsToUse; }
    public BigDecimal getSubTotalSnapshot() { return nvl(subTotalSnapshot); }
    public void setSubTotalSnapshot(BigDecimal v) { subTotalSnapshot = nvl(v); }
    public BigDecimal getDiscountSnapshot() { return nvl(discountSnapshot); }
    public void setDiscountSnapshot(BigDecimal v) { discountSnapshot = nvl(v); }
    public BigDecimal getPointsDiscountSnapshot() { return nvl(pointsDiscountSnapshot); }
    public void setPointsDiscountSnapshot(BigDecimal v) { pointsDiscountSnapshot = nvl(v); }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public LocalDateTime getHeldAt() { return heldAt; }
    public void setHeldAt(LocalDateTime heldAt) { this.heldAt = heldAt; }
    public LocalDateTime getRestoredAt() { return restoredAt; }
    public void setRestoredAt(LocalDateTime restoredAt) { this.restoredAt = restoredAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public LocalDateTime getExpiredAt() { return expiredAt; }
    public void setExpiredAt(LocalDateTime expiredAt) { this.expiredAt = expiredAt; }
    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
    public List<HeldCartItem> getItems() { return items; }
    public void setItems(List<HeldCartItem> items) { this.items = items != null ? items : new ArrayList<>(); }
    public boolean isHeld() { return STATUS_HELD.equalsIgnoreCase(status); }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }
}
