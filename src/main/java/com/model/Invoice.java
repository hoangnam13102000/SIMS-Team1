package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class Invoice {

	private int invoiceId;
	private String invoiceCode;

	private int shiftId;
	private int createdBy;
	private String createdByName;

	private Integer customerId;
	private String customerName;

	private LocalDateTime createdAt;
	private BigDecimal subTotal;
	private BigDecimal discountAmount = BigDecimal.ZERO;
	private Integer promotionId;
	private String promotionCode;
	private BigDecimal vatRate;
	private BigDecimal vatAmount;
	private BigDecimal totalAmount;

	/**
	 * Tổng tiền khách phải thanh toán tại thời điểm hóa đơn được tạo.
	 *
	 * Khác với totalAmount: - originalTotalAmount: giữ nguyên lịch sử bán ban đầu.
	 * - totalAmount: có thể giảm sau RETURN/EXCHANGE.
	 */
	private BigDecimal originalTotalAmount = BigDecimal.ZERO;

	private String paymentMethod;
	private String status;
	private String cancelReason;
	private LocalDateTime cancelledAt;

	/** Trang thai request huy gan nhat (PENDING/PROCESSING/APPROVED/REJECTED), khong luu truc tiep trong Invoices. */
	private String cancelRequestStatus;

	private String payPalOrderId;
	private String payPalCaptureId;

	// VietQR/payOS metadata for POS bank-transfer idempotency/recovery.
	private Long payOsOrderCode;
	private String payOsPaymentLinkId;
	private String bankTransferReference;

	private int itemCount;

	private int pointsUsed;
	private BigDecimal pointsDiscountAmount = BigDecimal.ZERO;

	private int pointsEarned;

	// ---- Tóm tắt đổi/trả (không lưu DB, gắn từ InvoiceDAO.attachReturnSummary)
	// ----
	/**
	 * Tổng tiền đã được APPROVED cho hoàn.
	 *
	 * Chưa chắc khách đã nhận được tiền, ví dụ chuyển khoản đang PENDING.
	 */
	private BigDecimal refundedAmount = BigDecimal.ZERO;

	/**
	 * Tổng tiền đã thực sự hoàn thành.
	 *
	 * Chỉ tính ReturnExchanges có: RefundStatus = COMPLETED.
	 */
	private BigDecimal completedRefundAmount = BigDecimal.ZERO;

	/** SubTotal gốc theo dòng InvoiceDetails (trước khi trigger thu hẹp). */
	private BigDecimal originalSubTotal = BigDecimal.ZERO;
	/** Số phiếu đổi/trả đã duyệt. */
	private int approvedReturnCount;
	/**
	 * NONE | PARTIAL | FULL — suy từ hàng đã trả vs hàng bán. Không thay Status DB
	 * (vẫn ACTIVE/CANCELLED).
	 */
	private String returnState = "NONE";

	public Invoice() {
	}

	public int getInvoiceId() {
		return invoiceId;
	}

	public void setInvoiceId(int invoiceId) {
		this.invoiceId = invoiceId;
	}

	public int getShiftId() {
		return shiftId;
	}

	public void setShiftId(int shiftId) {
		this.shiftId = shiftId;
	}

	public String getInvoiceCode() {
		return invoiceCode;
	}

	public void setInvoiceCode(String invoiceCode) {
		this.invoiceCode = invoiceCode;
	}

	public int getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(int createdBy) {
		this.createdBy = createdBy;
	}

	public String getCreatedByName() {
		return createdByName;
	}

	public void setCreatedByName(String createdByName) {
		this.createdByName = createdByName;
	}

	public Integer getCustomerId() {
		return customerId;
	}

	public void setCustomerId(Integer customerId) {
		this.customerId = customerId;
	}

	public String getCustomerName() {
		return customerName;
	}

	public void setCustomerName(String customerName) {
		this.customerName = customerName;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public BigDecimal getSubTotal() {
		return subTotal;
	}

	public void setSubTotal(BigDecimal subTotal) {
		this.subTotal = subTotal;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount != null ? discountAmount : BigDecimal.ZERO;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
	}

	public Integer getPromotionId() {
		return promotionId;
	}

	public void setPromotionId(Integer promotionId) {
		this.promotionId = promotionId;
	}

	public String getPromotionCode() {
		return promotionCode;
	}

	public void setPromotionCode(String promotionCode) {
		this.promotionCode = promotionCode;
	}

	public BigDecimal getVatRate() {
		return vatRate;
	}

	public void setVatRate(BigDecimal vatRate) {
		this.vatRate = vatRate;
	}

	public BigDecimal getVatAmount() {
		return vatAmount;
	}

	public void setVatAmount(BigDecimal vatAmount) {
		this.vatAmount = vatAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public BigDecimal getOriginalTotalAmount() {
		return originalTotalAmount != null ? originalTotalAmount : BigDecimal.ZERO;
	}

	public void setOriginalTotalAmount(BigDecimal originalTotalAmount) {
		this.originalTotalAmount = originalTotalAmount != null ? originalTotalAmount : BigDecimal.ZERO;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}

	public String getPayPalOrderId() {
		return payPalOrderId;
	}

	public void setPayPalOrderId(String payPalOrderId) {
		this.payPalOrderId = payPalOrderId;
	}

	public String getPayPalCaptureId() {
		return payPalCaptureId;
	}

	public void setPayPalCaptureId(String payPalCaptureId) {
		this.payPalCaptureId = payPalCaptureId;
	}

	public Long getPayOsOrderCode() {
		return payOsOrderCode;
	}

	public void setPayOsOrderCode(Long payOsOrderCode) {
		this.payOsOrderCode = payOsOrderCode;
	}

	public String getPayOsPaymentLinkId() {
		return payOsPaymentLinkId;
	}

	public void setPayOsPaymentLinkId(String payOsPaymentLinkId) {
		this.payOsPaymentLinkId = payOsPaymentLinkId;
	}

	public String getBankTransferReference() {
		return bankTransferReference;
	}

	public void setBankTransferReference(String bankTransferReference) {
		this.bankTransferReference = bankTransferReference;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		this.cancelReason = cancelReason;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(LocalDateTime cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

	public String getCancelRequestStatus() {
		return cancelRequestStatus;
	}

	public void setCancelRequestStatus(String cancelRequestStatus) {
		this.cancelRequestStatus = cancelRequestStatus;
	}

	public int getItemCount() {
		return itemCount;
	}

	public void setItemCount(int itemCount) {
		this.itemCount = itemCount;
	}

	public int getPointsUsed() {
		return pointsUsed;
	}

	public void setPointsUsed(int pointsUsed) {
		this.pointsUsed = Math.max(0, pointsUsed);
	}

	public BigDecimal getPointsDiscountAmount() {
		return pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO;
	}

	public void setPointsDiscountAmount(BigDecimal pointsDiscountAmount) {
		this.pointsDiscountAmount = pointsDiscountAmount != null ? pointsDiscountAmount : BigDecimal.ZERO;
	}

	public int getPointsEarned() {
		return pointsEarned;
	}

	public void setPointsEarned(int pointsEarned) {
		this.pointsEarned = pointsEarned;
	}

	public BigDecimal getRefundedAmount() {
		return refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
	}

	public void setRefundedAmount(BigDecimal refundedAmount) {
		this.refundedAmount = refundedAmount != null ? refundedAmount : BigDecimal.ZERO;
	}

	public BigDecimal getCompletedRefundAmount() {
		return completedRefundAmount != null ? completedRefundAmount : BigDecimal.ZERO;
	}

	public void setCompletedRefundAmount(BigDecimal completedRefundAmount) {
		this.completedRefundAmount = completedRefundAmount != null ? completedRefundAmount : BigDecimal.ZERO;
	}

	public BigDecimal getOriginalSubTotal() {
		return originalSubTotal != null ? originalSubTotal : BigDecimal.ZERO;
	}

	public void setOriginalSubTotal(BigDecimal originalSubTotal) {
		this.originalSubTotal = originalSubTotal != null ? originalSubTotal : BigDecimal.ZERO;
	}

	public int getApprovedReturnCount() {
		return approvedReturnCount;
	}

	public void setApprovedReturnCount(int approvedReturnCount) {
		this.approvedReturnCount = Math.max(0, approvedReturnCount);
	}

	public String getReturnState() {
		return returnState != null ? returnState : "NONE";
	}

	public void setReturnState(String returnState) {
		this.returnState = returnState != null ? returnState : "NONE";
	}

	public boolean isCancelled() {
		return "CANCELLED".equalsIgnoreCase(status);
	}

	public boolean isCancellableToday() {
		return !isCancelled() && createdAt != null && createdAt.toLocalDate().isEqual(java.time.LocalDate.now());
	}

	public boolean hasReturns() {
		return approvedReturnCount > 0 || getRefundedAmount().signum() > 0;
	}

	/** Nhãn hiển thị cột trạng thái đổi/trả trên bảng hóa đơn. */
	public String getReturnStateLabel() {
		if (isCancelled())
			return "Đã hủy";
		return switch (getReturnState()) {
		case "FULL" -> "Đã trả hết";
		case "PARTIAL" -> "Trả một phần";
		default -> "—";
		};
	}

	/**
	 * Ghi chú ngắn cho chi tiết HD / tooltip: vd "Đã hoàn 15.000đ · 1 phiếu trả".
	 */
	public String getReturnNote() {
		if (!hasReturns())
			return "";
		String money = String.format("%,.0fđ", getRefundedAmount());
		return "Đã hoàn " + money + " · " + approvedReturnCount + " phiếu trả";
	}
}