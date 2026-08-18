package com.service.payment;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Chặn đóng ca trong lúc một giao dịch điện tử tại POS đang chờ kết quả.
 *
 * Đây là guard trong cùng tiến trình ứng dụng. Nó bảo vệ trường hợp nhân viên
 * mở màn hình Ca bán hàng ở cửa sổ/tab khác rồi đóng ca khi POS đang chờ
 * VietQR.
 */
public final class ElectronicPaymentGuard {

	private static final ConcurrentHashMap<Integer, AtomicInteger> PENDING_BY_SHIFT = new ConcurrentHashMap<>();

	private ElectronicPaymentGuard() {
	}

	public static void begin(int shiftId) {
		if (shiftId <= 0) {
			return;
		}
		PENDING_BY_SHIFT.computeIfAbsent(shiftId, ignored -> new AtomicInteger()).incrementAndGet();
	}

	public static void end(int shiftId) {
		if (shiftId <= 0) {
			return;
		}
		PENDING_BY_SHIFT.computeIfPresent(shiftId, (ignored, counter) -> {
			return counter.decrementAndGet() <= 0 ? null : counter;
		});
	}

	public static boolean hasPending(int shiftId) {
		AtomicInteger count = PENDING_BY_SHIFT.get(shiftId);
		return count != null && count.get() > 0;
	}
}