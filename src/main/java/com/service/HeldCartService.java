package com.service;

import com.core.log.ActivityLogHelper;
import com.dao.CustomerDAO;
import com.dao.HeldCartDAO;
import com.dao.ProductDAO;
import com.model.ActivityLog;
import com.model.Customer;
import com.model.HeldCart;
import com.model.HeldCartItem;
import com.model.Product;
import com.model.Shift;
import com.model.User;
import com.model.permission.AppPermission;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Nghiep vu tam giu va khoi phuc nhieu gio hang tai POS. */
public class HeldCartService {
    private final HeldCartDAO heldCartDAO = new HeldCartDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final CustomerDAO customerDAO = new CustomerDAO();
    private final ShiftService shiftService = new ShiftService();
    private final AuthService authService = AuthService.getInstance();

    public Result<HeldCart> holdCurrentCart(PosCartService cart, String paymentMethod, String note) {
        User user = authService.getCurrentUser();
        if (user == null) return Result.failure("Phiên đăng nhập không hợp lệ.");
        if (!authService.can(AppPermission.POS_CART_HOLD)) {
            return Result.failure("Bạn không có quyền tạm giữ giỏ hàng.");
        }
        Shift shift = shiftService.getMyOpenShift();
        if (shift == null) return Result.failure("Bạn phải mở ca bán hàng trước khi tạm giữ giỏ.");
        if (cart == null || cart.isEmpty()) return Result.failure("Giỏ hàng đang trống.");
        if (note != null && note.trim().length() > 500) {
            return Result.failure("Ghi chú tối đa 500 ký tự.");
        }

        try {
            HeldCart held = heldCartDAO.create(shift.getShiftId(), user.getUserId(), cart, paymentMethod, note);
            ActivityLogHelper.record("giỏ tạm giữ", ActivityLog.ACTION_CREATE,
                    "Tạm giữ giỏ " + held.getHoldCode() + " trong ca #" + shift.getShiftId(), null, held);
            cart.clear();
            return Result.success("Đã tạm giữ giỏ với mã " + held.getHoldCode() + ".", held);
        } catch (SQLException e) {
            return Result.failure("Không thể tạm giữ giỏ hàng. Vui lòng thử lại.");
        }
    }

    public List<HeldCart> getMyHeldCarts(String keyword) {
        User user = authService.getCurrentUser();
        if (user == null || !authService.can(AppPermission.POS_CART_RESTORE)) return List.of();
        Shift shift = shiftService.getMyOpenShift();
        if (shift == null) return List.of();
        return heldCartDAO.findHeldForShift(user.getUserId(), shift.getShiftId(), keyword);
    }

    public Result<HeldCart> restoreToCurrentCart(long holdId, PosCartService cart) {
        User user = authService.getCurrentUser();
        if (user == null) return Result.failure("Phiên đăng nhập không hợp lệ.");
        if (!authService.can(AppPermission.POS_CART_RESTORE)) {
            return Result.failure("Bạn không có quyền khôi phục giỏ tạm giữ.");
        }
        Shift shift = shiftService.getMyOpenShift();
        if (shift == null) return Result.failure("Bạn phải mở đúng ca bán hàng đã tạm giữ giỏ.");
        if (cart == null) return Result.failure("Không tìm thấy giỏ POS hiện tại.");
        if (!cart.isEmpty()) {
            return Result.failure("Giỏ hiện tại đang có sản phẩm. Hãy thanh toán hoặc tạm giữ giỏ hiện tại trước.");
        }

        HeldCart held = heldCartDAO.findById(holdId, user.getUserId(), shift.getShiftId());
        if (held == null || !held.isHeld()) {
            return Result.failure("Phiếu tạm giữ không còn khả dụng hoặc không thuộc ca hiện tại.");
        }

        List<String> warnings = new ArrayList<>();
        List<ProductQty> available = new ArrayList<>();
        for (HeldCartItem item : held.getItems()) {
            Product p = productDAO.findActiveById(item.getProductId());
            if (p == null || p.getStock() <= 0) {
                warnings.add(item.getProductNameSnapshot() + ": hiện không còn bán/không còn tồn.");
                continue;
            }
            int qty = Math.min(item.getQuantity(), p.getStock());
            if (qty < item.getQuantity()) {
                warnings.add(item.getProductNameSnapshot() + ": chỉ còn " + qty + "/" + item.getQuantity() + ".");
            }
            BigDecimal oldPrice = item.getUnitPriceSnapshot();
            if (oldPrice != null && p.getSellPrice() != null && oldPrice.compareTo(p.getSellPrice()) != 0) {
                warnings.add(item.getProductNameSnapshot() + ": giá bán đã thay đổi, dùng giá hiện tại.");
            }
            if (qty > 0) available.add(new ProductQty(p, qty));
        }
        if (available.isEmpty()) {
            return Result.failure("Không còn sản phẩm hợp lệ để khôi phục từ phiếu này.");
        }

        if (!heldCartDAO.markRestored(holdId, user.getUserId(), shift.getShiftId())) {
            return Result.failure("Phiếu vừa được xử lý ở nơi khác. Vui lòng làm mới danh sách.");
        }

        cart.clear();
        for (ProductQty pq : available) cart.addToCart(pq.product, pq.quantity);

        if (held.getCustomerId() != null) {
            Customer customer = customerDAO.findById(held.getCustomerId());
            if (customer != null && !customer.isLocked() && "ACTIVE".equalsIgnoreCase(customer.getStatus())) {
                String phone = customer.getPhone() != null && !customer.getPhone().isBlank() ? customer.getPhone() : "";
                String label = customer.getFullName() + (phone.isEmpty() ? "" : " - " + phone)
                        + " - Điểm: " + customer.getMemberPoint();
                cart.setCustomer(customer.getCustomerId(), label, customer.getMemberPoint());
            } else {
                warnings.add("Khách hàng cũ không còn khả dụng; giỏ được khôi phục ở chế độ khách lẻ.");
            }
        }

        if (held.getPromotionCode() != null && !held.getPromotionCode().isBlank()) {
            PromotionService.ApplyResult promo = cart.applyPromotionCode(held.getPromotionCode());
            if (!promo.ok) warnings.add("Mã KM " + held.getPromotionCode() + " không còn hợp lệ: " + promo.message);
        }
        if (held.getPointsToUse() > 0) {
            cart.setPointsToUse(held.getPointsToUse());
            if (cart.getPointsToUse() < held.getPointsToUse()) {
                warnings.add("Số điểm sử dụng đã được giảm theo số dư/giá trị giỏ hiện tại.");
            }
        }

        ActivityLogHelper.record("giỏ tạm giữ", ActivityLog.ACTION_STATUS_CHANGE,
                "Khôi phục giỏ " + held.getHoldCode() + " trong ca #" + shift.getShiftId(), held, null);

        String message = "Đã khôi phục " + held.getHoldCode() + ".";
        if (!warnings.isEmpty()) message += "\nLưu ý:\n- " + String.join("\n- ", warnings);
        return Result.success(message, held);
    }

    public Result<HeldCart> cancel(long holdId) {
        User user = authService.getCurrentUser();
        if (user == null) return Result.failure("Phiên đăng nhập không hợp lệ.");
        if (!authService.can(AppPermission.POS_CART_RESTORE)) {
            return Result.failure("Bạn không có quyền hủy phiếu tạm giữ.");
        }
        Shift shift = shiftService.getMyOpenShift();
        if (shift == null) return Result.failure("Bạn chưa có ca bán hàng đang mở.");

        HeldCart held = heldCartDAO.findById(holdId, user.getUserId(), shift.getShiftId());
        if (held == null || !held.isHeld()) return Result.failure("Phiếu không còn khả dụng.");
        if (!heldCartDAO.cancel(holdId, user.getUserId(), shift.getShiftId())) {
            return Result.failure("Không thể hủy phiếu tạm giữ.");
        }
        ActivityLogHelper.record("giỏ tạm giữ", ActivityLog.ACTION_STATUS_CHANGE,
                "Hủy phiếu tạm giữ " + held.getHoldCode(), held, null);
        return Result.success("Đã hủy phiếu " + held.getHoldCode() + ".", held);
    }

    private static final class ProductQty {
        final Product product;
        final int quantity;
        ProductQty(Product product, int quantity) { this.product = product; this.quantity = quantity; }
    }

    public static final class Result<T> {
        private final boolean success;
        private final String message;
        private final T data;
        private Result(boolean success, String message, T data) {
            this.success = success; this.message = message; this.data = data;
        }
        public static <T> Result<T> success(String message, T data) { return new Result<>(true, message, data); }
        public static <T> Result<T> failure(String message) { return new Result<>(false, message, null); }
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public T getData() { return data; }
    }
}
