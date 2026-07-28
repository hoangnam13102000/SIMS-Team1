package com.service;

import com.model.CartItem;
import com.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * Gio hang session (in-memory, singleton).
 * Giong myShop: dang ky listener de cap nhat badge / dropdown tren ClientHeader.
 */
public class CartService {

    private static CartService instance;
    private final List<CartItem> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    private CartService() {
    }

    public static synchronized CartService getInstance() {
        if (instance == null) instance = new CartService();
        return instance;
    }

    /** Dang ky lang nghe khi gio hang thay doi (them/xoa/sua so luong/clear). */
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

    /**
     * Them san pham vao gio. Neu da co thi cong don so luong.
     * Khong cho vuot ton kho (stock).
     */
    public void addToCart(Product product, int quantity) {
        if (product == null || quantity <= 0) return;

        for (CartItem item : items) {
            if (item.getProduct().getProductId() == product.getProductId()) {
                int newQty = item.getQuantity() + quantity;
                int max = Math.max(1, product.getStock());
                item.setQuantity(Math.min(newQty, max));
                // Cap nhat snapshot product (gia/ten co the doi)
                item.setProduct(product);
                notifyChanged();
                return;
            }
        }
        int qty = Math.min(quantity, Math.max(1, product.getStock()));
        if (product.getStock() <= 0) return; // het hang
        items.add(new CartItem(product, qty));
        notifyChanged();
    }

    public void removeItem(int productId) {
        items.removeIf(item -> item.getProduct().getProductId() == productId);
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

    /** Tong so luong san pham (badge tren icon gio hang). */
    public int getTotalQuantity() {
        int total = 0;
        for (CartItem item : items) total += item.getQuantity();
        return total;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void clear() {
        items.clear();
        notifyChanged();
    }
}
