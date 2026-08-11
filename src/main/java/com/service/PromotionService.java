package com.service;

import com.dao.PromotionDAO;
import com.model.Promotion;

import java.math.BigDecimal;

/**
 * Dich vu ap dung ma khuyen mai (dung chung POS + online).
 * <ul>
 *   <li>Tra cuu ma, kiem tra hieu luc, tinh so tien giam.</li>
 *   <li>Khong ghi DB (UsedCount do DAO goi sau khi don/hoa don thanh cong).</li>
 * </ul>
 */
public class PromotionService {

    private static PromotionService instance;
    private final PromotionDAO promotionDAO = new PromotionDAO();

    private PromotionService() {
    }

    public static synchronized PromotionService getInstance() {
        if (instance == null) instance = new PromotionService();
        return instance;
    }

    /** Ket qua ap dung ma: thanh cong + so tien giam, hoac that bai + message. */
    public static final class ApplyResult {
        public final boolean ok;
        public final Promotion promotion;
        public final BigDecimal discountAmount;
        public final String message;

        private ApplyResult(boolean ok, Promotion promotion, BigDecimal discountAmount, String message) {
            this.ok = ok;
            this.promotion = promotion;
            this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
            this.message = message;
        }

        public static ApplyResult success(Promotion p, BigDecimal amount) {
            return new ApplyResult(true, p, amount, null);
        }

        public static ApplyResult fail(String message) {
            return new ApplyResult(false, null, BigDecimal.ZERO, message);
        }
    }

    /**
     * Ap dung ma giam gia cho tong tien hang (subTotal, truoc VAT).
     * Neu ma hop le va du dieu kien → tra ve so tien giam & ban ghi Promotion.
     */
    public ApplyResult applyCode(String code, BigDecimal orderSubTotal) {
        if (code == null || code.isBlank()) {
            return ApplyResult.fail("Vui lòng nhập mã khuyến mãi.");
        }
        if (orderSubTotal == null || orderSubTotal.signum() <= 0) {
            return ApplyResult.fail("Giỏ hàng trống hoặc tổng tiền không hợp lệ.");
        }

        Promotion promo = promotionDAO.findByCode(code.trim());
        if (promo == null) {
            return ApplyResult.fail("Mã khuyến mãi không tồn tại.");
        }

        String status = promo.computeStatus();
        switch (status) {
            case "PAUSED" -> {
                return ApplyResult.fail("Chương trình khuyến mãi đang tạm dừng.");
            }
            case "EXHAUSTED" -> {
                return ApplyResult.fail("Mã khuyến mãi đã hết lượt sử dụng.");
            }
            case "UPCOMING" -> {
                return ApplyResult.fail("Chương trình khuyến mãi chưa bắt đầu.");
            }
            case "EXPIRED" -> {
                return ApplyResult.fail("Mã khuyến mãi đã hết hạn.");
            }
            default -> { /* RUNNING */ }
        }

        if (promo.getMinOrderAmount() != null
                && orderSubTotal.compareTo(promo.getMinOrderAmount()) < 0) {
            return ApplyResult.fail("Đơn hàng chưa đạt giá trị tối thiểu "
                    + formatMoney(promo.getMinOrderAmount()) + " đ để dùng mã này.");
        }

        BigDecimal discount = promo.calculateDiscount(orderSubTotal);
        if (discount == null || discount.signum() <= 0) {
            return ApplyResult.fail("Không thể tính giảm giá cho mã này.");
        }
        return ApplyResult.success(promo, discount);
    }

    /** Tang UsedCount sau khi don hang / hoa don da luu thanh cong. */
    public void markUsed(Integer promotionId) {
        if (promotionId != null && promotionId > 0) {
            promotionDAO.incrementUsedCount(promotionId);
        }
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,.0f", amount);
    }
}
