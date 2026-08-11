package com.service;

import com.model.CartItem;
import com.model.Product;
import com.model.Promotion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Giỏ hàng session (in-memory, singleton) DÀNH RIÊNG cho trang POS.
 * Hỗ trợ: mã khuyến mãi + dùng điểm thành viên (MemberPoint) trừ tiền.
 */
public class PosCartService {

    private static PosCartService instance;
    private final List<CartItem> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Integer customerId;
    private String customerLabel;
    /** Điểm hiện có của khách (snapshot lúc chọn KH). */
    private int customerMemberPoint;

    private Promotion appliedPromotion;
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private int pointsToUse;
    /** 1 điểm = bao nhiêu VND khi đổi (StoreConfig.POINT_REDEEM_RATE). */
    private BigDecimal pointRedeemRate = new BigDecimal("1000");

    private PosCartService() {
    }

    public static synchronized PosCartService getInstance() {
        if (instance == null) instance = new PosCartService();
        return instance;
    }

    public void addListener(Runnable listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Runnable listener : new ArrayList<>(listeners)) {
            try {
                listener.run();
            } catch (Exception ignored) {
            }
        }
    }

    public void addToCart(Product product, int quantity) {
        if (product == null || quantity <= 0 || product.getStock() <= 0) return;

        for (CartItem item : items) {
            if (item.getProduct().getProductId() == product.getProductId()) {
                int newQty = item.getQuantity() + quantity;
                item.setQuantity(Math.min(newQty, Math.max(1, product.getStock())));
                item.setProduct(product);
                revalidatePromotion();
                clampPointsToUse();
                notifyChanged();
                return;
            }
        }
        items.add(new CartItem(product, Math.min(quantity, product.getStock())));
        revalidatePromotion();
        clampPointsToUse();
        notifyChanged();
    }

    public void removeItem(int productId) {
        items.removeIf(item -> item.getProduct().getProductId() == productId);
        revalidatePromotion();
        clampPointsToUse();
        notifyChanged();
    }

    public void updateQuantity(int productId, int quantity) {
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            if (item.getProduct().getProductId() == productId) {
                if (quantity <= 0) {
                    items.remove(i);
                } else {
                    item.setQuantity(Math.min(quantity, Math.max(1, item.getProduct().getStock())));
                }
                revalidatePromotion();
                clampPointsToUse();
                notifyChanged();
                return;
            }
        }
    }

    public List<CartItem> getItems() {
        return items;
    }

    public long getSubTotal() {
        long total = 0;
        for (CartItem item : items) total += item.getSubtotal();
        return total;
    }

    public BigDecimal getSubTotalDecimal() {
        return BigDecimal.valueOf(getSubTotal());
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void setCustomer(Integer customerId, String customerLabel) {
        setCustomer(customerId, customerLabel, 0);
    }

    public void setCustomer(Integer customerId, String customerLabel, int memberPoint) {
        this.customerId = customerId;
        this.customerLabel = customerLabel;
        this.customerMemberPoint = Math.max(0, memberPoint);
        if (customerId == null) {
            this.pointsToUse = 0;
            this.customerMemberPoint = 0;
        } else {
            clampPointsToUse();
        }
        notifyChanged();
    }

    public void clearCustomer() {
        setCustomer(null, null, 0);
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public String getCustomerLabel() {
        return customerLabel;
    }

    public int getCustomerMemberPoint() {
        return customerMemberPoint;
    }

    // ---------------- Khuyen mai ----------------

    public Promotion getAppliedPromotion() {
        return appliedPromotion;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount != null ? discountAmount : BigDecimal.ZERO;
    }

    public long getDiscountAmountLong() {
        return getDiscountAmount().longValue();
    }

    public PromotionService.ApplyResult applyPromotionCode(String code) {
        PromotionService.ApplyResult result =
                PromotionService.getInstance().applyCode(code, getSubTotalDecimal());
        if (result.ok) {
            this.appliedPromotion = result.promotion;
            this.discountAmount = result.discountAmount;
            clampPointsToUse();
            notifyChanged();
        }
        return result;
    }

    public void clearPromotion() {
        this.appliedPromotion = null;
        this.discountAmount = BigDecimal.ZERO;
        clampPointsToUse();
        notifyChanged();
    }

    private void revalidatePromotion() {
        if (appliedPromotion == null) {
            discountAmount = BigDecimal.ZERO;
            return;
        }
        BigDecimal sub = getSubTotalDecimal();
        BigDecimal d = appliedPromotion.calculateDiscount(sub);
        if (d == null || d.signum() <= 0) {
            appliedPromotion = null;
            discountAmount = BigDecimal.ZERO;
        } else {
            discountAmount = d;
        }
    }

    // ---------------- Diem thanh vien ----------------

    public void setPointRedeemRate(BigDecimal rate) {
        if (rate != null && rate.signum() > 0) {
            this.pointRedeemRate = rate;
            clampPointsToUse();
            notifyChanged();
        }
    }

    public BigDecimal getPointRedeemRate() {
        return pointRedeemRate != null ? pointRedeemRate : BigDecimal.ONE;
    }

    public int getPointsToUse() {
        return pointsToUse;
    }

    public void setPointsToUse(int points) {
        this.pointsToUse = Math.max(0, points);
        clampPointsToUse();
        notifyChanged();
    }

    public BigDecimal getPointsDiscountAmount() {
        if (pointsToUse <= 0) return BigDecimal.ZERO;
        return getPointRedeemRate().multiply(BigDecimal.valueOf(pointsToUse))
                .setScale(0, RoundingMode.DOWN);
    }

    public BigDecimal getAmountAfterPromo() {
        BigDecimal sub = getSubTotalDecimal();
        BigDecimal d = getDiscountAmount();
        BigDecimal after = sub.subtract(d);
        return after.signum() < 0 ? BigDecimal.ZERO : after;
    }

    private void clampPointsToUse() {
        if (customerId == null || customerMemberPoint <= 0) {
            pointsToUse = 0;
            return;
        }
        int maxByBalance = customerMemberPoint;
        BigDecimal rate = getPointRedeemRate();
        if (rate.signum() <= 0) {
            pointsToUse = 0;
            return;
        }
        BigDecimal maxMoney = getAmountAfterPromo();
        int maxByMoney = maxMoney.divide(rate, 0, RoundingMode.DOWN).intValue();
        int max = Math.min(maxByBalance, Math.max(0, maxByMoney));
        if (pointsToUse > max) pointsToUse = max;
        if (pointsToUse < 0) pointsToUse = 0;
    }

    public void clear() {
        items.clear();
        customerId = null;
        customerLabel = null;
        customerMemberPoint = 0;
        appliedPromotion = null;
        discountAmount = BigDecimal.ZERO;
        pointsToUse = 0;
        notifyChanged();
    }
}