package com.service;

import com.model.CartItem;
import com.model.Product;
import com.model.Promotion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Giỏ hàng session phía khách (online). Hỗ trợ mã khuyến mãi.
 */
public class CartService {

    private static CartService instance;
    private final List<CartItem> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private Promotion appliedPromotion;
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private CartService() {
    }

    public static synchronized CartService getInstance() {
        if (instance == null) instance = new CartService();
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
        if (product == null || quantity <= 0) return;

        for (CartItem item : items) {
            if (item.getProduct().getProductId() == product.getProductId()) {
                int newQty = item.getQuantity() + quantity;
                int max = Math.max(1, product.getStock());
                item.setQuantity(Math.min(newQty, max));
                item.setProduct(product);
                revalidatePromotion();
                notifyChanged();
                return;
            }
        }
        int qty = Math.min(quantity, Math.max(1, product.getStock()));
        if (product.getStock() <= 0) return;
        items.add(new CartItem(product, qty));
        revalidatePromotion();
        notifyChanged();
    }

    public void removeItem(int productId) {
        items.removeIf(item -> item.getProduct().getProductId() == productId);
        revalidatePromotion();
        notifyChanged();
    }

    public void updateQuantity(int productId, int quantity) {
        for (int i = 0; i < items.size(); i++) {
            CartItem item = items.get(i);
            if (item.getProduct().getProductId() == productId) {
                if (quantity <= 0) {
                    items.remove(i);
                } else {
                    int max = Math.max(1, item.getProduct().getStock());
                    item.setQuantity(Math.min(quantity, max));
                }
                revalidatePromotion();
                notifyChanged();
                return;
            }
        }
    }

    public List<CartItem> getItems() {
        return items;
    }

    public long getTotal() {
        long total = 0;
        for (CartItem item : items) total += item.getSubtotal();
        return total;
    }

    public BigDecimal getSubTotalDecimal() {
        return BigDecimal.valueOf(getTotal());
    }

    public int getTotalQuantity() {
        int total = 0;
        for (CartItem item : items) total += item.getQuantity();
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
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

    /** Tổng sau KM (online: thường = total thanh toán). */
    public long getPayableTotal() {
        return Math.max(0, getTotal() - getDiscountAmountLong());
    }

    public BigDecimal getPayableTotalDecimal() {
        return BigDecimal.valueOf(getPayableTotal());
    }

    public PromotionService.ApplyResult applyPromotionCode(String code) {
        PromotionService.ApplyResult result =
                PromotionService.getInstance().applyCode(code, getSubTotalDecimal());
        if (result.ok) {
            this.appliedPromotion = result.promotion;
            this.discountAmount = result.discountAmount;
            notifyChanged();
        }
        return result;
    }

    public void clearPromotion() {
        this.appliedPromotion = null;
        this.discountAmount = BigDecimal.ZERO;
        notifyChanged();
    }

    private void revalidatePromotion() {
        if (appliedPromotion == null) {
            discountAmount = BigDecimal.ZERO;
            return;
        }
        BigDecimal d = appliedPromotion.calculateDiscount(getSubTotalDecimal());
        if (d == null || d.signum() <= 0) {
            appliedPromotion = null;
            discountAmount = BigDecimal.ZERO;
        } else {
            discountAmount = d;
        }
    }

    public void clear() {
        items.clear();
        appliedPromotion = null;
        discountAmount = BigDecimal.ZERO;
        notifyChanged();
    }
}
