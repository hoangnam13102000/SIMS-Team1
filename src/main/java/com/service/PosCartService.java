package com.service;

import com.model.CartItem;
import com.model.Product;

import java.util.ArrayList;
import java.util.List;

/**
 * Giỏ hàng session (in-memory, singleton) DÀNH RIÊNG cho trang POS (bán hàng
 * tại quầy, phía admin/nhân viên) - KHÔNG dùng chung {@link CartService} vì
 * đó là giỏ hàng của khách khi mua online phía client; 2 luồng nghiệp vụ độc
 * lập, dùng chung sẽ làm lẫn lộn dữ liệu nếu cả hai cửa sổ (admin/client)
 * cùng chạy trên 1 máy.
 * <p>
 * Là singleton (thay vì field thường trong PosPanel) để giỏ hàng KHÔNG bị
 * mất khi {@code AdminMainFrame} xây lại toàn bộ nội dung (đổi theme/ngôn
 * ngữ sẽ tạo lại PosPanel mới - xem AdminMainFrame#rebuildContent).
 */
public class PosCartService {

    private static PosCartService instance;
    private final List<CartItem> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    /** Khách hàng đang gắn cho đơn tại quầy hiện tại - null = khách lẻ. */
    private Integer customerId;
    private String customerLabel; // "Ho ten - SDT", hien thi tren UI

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

    /** Them san pham vao gio (cong don so luong neu da co). Khong cho vuot ton kho hien tai. */
    public void addToCart(Product product, int quantity) {
        if (product == null || quantity <= 0 || product.getStock() <= 0) return;

        for (CartItem item : items) {
            if (item.getProduct().getProductId() == product.getProductId()) {
                int newQty = item.getQuantity() + quantity;
                item.setQuantity(Math.min(newQty, Math.max(1, product.getStock())));
                item.setProduct(product);
                notifyChanged();
                return;
            }
        }
        items.add(new CartItem(product, Math.min(quantity, product.getStock())));
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
                    item.setQuantity(Math.min(quantity, Math.max(1, item.getProduct().getStock())));
                }
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

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void setCustomer(Integer customerId, String customerLabel) {
        this.customerId = customerId;
        this.customerLabel = customerLabel;
        notifyChanged();
    }

    public void clearCustomer() {
        setCustomer(null, null);
    }

    public Integer getCustomerId() {
        return customerId;
    }

    public String getCustomerLabel() {
        return customerLabel;
    }

    /** Xoa sach gio hang + bo chon khach - goi sau khi lap hoa don thanh cong. */
    public void clear() {
        items.clear();
        customerId = null;
        customerLabel = null;
        notifyChanged();
    }
}