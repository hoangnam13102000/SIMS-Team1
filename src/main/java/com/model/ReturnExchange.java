package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ReturnExchange {

	public static final String TYPE_RETURN = "RETURN";
	public static final String TYPE_EXCHANGE = "EXCHANGE";

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_APPROVED = "APPROVED";
	public static final String STATUS_REJECTED = "REJECTED";

	// ============================================================
	// REFUND METHOD
	// ============================================================

	public static final String REFUND_CASH = "CASH";
	public static final String REFUND_BANK_TRANSFER = "BANK_TRANSFER";
	public static final String REFUND_CARD = "CARD";
	public static final String REFUND_PAYPAL = "PAYPAL";

	// ============================================================
	// REFUND STATUS
	// ============================================================

	public static final String REFUND_STATUS_NONE = "NONE";
	public static final String REFUND_STATUS_PENDING = "PENDING";
	public static final String REFUND_STATUS_COMPLETED = "COMPLETED";
	public static final String REFUND_STATUS_FAILED = "FAILED";

	private int returnId;
	private int invoiceId;
	private String invoiceCode;

	private String type; // RETURN | EXCHANGE
	private String reason; // R4: ly do khach hang
	private String rejectionReason; // ly do nhan vien tu choi
	private BigDecimal totalValue; // gia tri hang khach tra (tong Direction=IN * UnitPrice)
	private BigDecimal discountShare;
	private BigDecimal pointsShare;

	/**
	 * Phương thức cửa hàng thực sự dùng để trả tiền cho khách.
	 *
	 * CASH | BANK_TRANSFER | CARD | PAYPAL
	 */
	private String refundMethod;

	/**
	 * Ca thực tế chi tiền refund. Chủ yếu dùng cho CASH.
	 */
	private Integer refundShiftId;

	/**
	 * Mã giao dịch refund.
	 */
	private String refundTransactionId;

	/**
	 * NONE | PENDING | COMPLETED | FAILED
	 */
	private String refundStatus = REFUND_STATUS_NONE;

	/**
	 * Người thực hiện/xác nhận refund.
	 */
	private Integer refundedBy;

	/**
	 * Thời điểm refund thực sự hoàn thành.
	 */
	private LocalDateTime refundedAt;

	private boolean requiresApproval;
	private String status; // PENDING | APPROVED | REJECTED

	private Integer approvedBy;
	private String approvedByName;
	private LocalDateTime approvedAt;

	private int createdBy;
	private String createdByName;
	private LocalDateTime createdAt;

	public ReturnExchange() {
	}

	public int getReturnId() {
		return returnId;
	}

	public void setReturnId(int returnId) {
		this.returnId = returnId;
	}

	public int getInvoiceId() {
		return invoiceId;
	}

	public void setInvoiceId(int invoiceId) {
		this.invoiceId = invoiceId;
	}

	public String getInvoiceCode() {
		return invoiceCode;
	}

	public void setInvoiceCode(String invoiceCode) {
		this.invoiceCode = invoiceCode;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String reason) {
		this.reason = reason;
	}

	public String getRejectionReason() {
		return rejectionReason;
	}

	public void setRejectionReason(String rejectionReason) {
		this.rejectionReason = rejectionReason;
	}

	public BigDecimal getTotalValue() {
		return totalValue;
	}

	public void setTotalValue(BigDecimal totalValue) {
		this.totalValue = totalValue;
	}

	public BigDecimal getDiscountShare() {
		return discountShare;
	}

	public void setDiscountShare(BigDecimal discountShare) {
		this.discountShare = discountShare;
	}

	public BigDecimal getPointsShare() {
		return pointsShare;
	}

	public void setPointsShare(BigDecimal pointsShare) {
		this.pointsShare = pointsShare;
	}

	public String getRefundMethod() {
		return refundMethod;
	}

	public void setRefundMethod(String refundMethod) {
		this.refundMethod = refundMethod;
	}

	public Integer getRefundShiftId() {
		return refundShiftId;
	}

	public void setRefundShiftId(Integer refundShiftId) {
		this.refundShiftId = refundShiftId;
	}

	public String getRefundTransactionId() {
		return refundTransactionId;
	}

	public void setRefundTransactionId(String refundTransactionId) {
		this.refundTransactionId = refundTransactionId;
	}

	public String getRefundStatus() {
		return refundStatus != null ? refundStatus : REFUND_STATUS_NONE;
	}

	public void setRefundStatus(String refundStatus) {
		this.refundStatus = refundStatus != null ? refundStatus : REFUND_STATUS_NONE;
	}

	public Integer getRefundedBy() {
		return refundedBy;
	}

	public void setRefundedBy(Integer refundedBy) {
		this.refundedBy = refundedBy;
	}

	public LocalDateTime getRefundedAt() {
		return refundedAt;
	}

	public void setRefundedAt(LocalDateTime refundedAt) {
		this.refundedAt = refundedAt;
	}

	public boolean isRequiresApproval() {
		return requiresApproval;
	}

	public void setRequiresApproval(boolean requiresApproval) {
		this.requiresApproval = requiresApproval;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Integer getApprovedBy() {
		return approvedBy;
	}

	public void setApprovedBy(Integer approvedBy) {
		this.approvedBy = approvedBy;
	}

	public String getApprovedByName() {
		return approvedByName;
	}

	public void setApprovedByName(String approvedByName) {
		this.approvedByName = approvedByName;
	}

	public LocalDateTime getApprovedAt() {
		return approvedAt;
	}

	public void setApprovedAt(LocalDateTime approvedAt) {
		this.approvedAt = approvedAt;
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

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public boolean isExchange() {
		return TYPE_EXCHANGE.equalsIgnoreCase(type);
	}

	public boolean isPending() {
		return STATUS_PENDING.equalsIgnoreCase(status);
	}

	public boolean isApproved() {
		return STATUS_APPROVED.equalsIgnoreCase(status);
	}

	public boolean isRejected() {
		return STATUS_REJECTED.equalsIgnoreCase(status);
	}

	public boolean isReturn() {
		return TYPE_RETURN.equalsIgnoreCase(type);
	}

	public boolean isCashRefund() {
		return REFUND_CASH.equalsIgnoreCase(refundMethod);
	}

	public boolean isRefundPending() {
		return REFUND_STATUS_PENDING.equalsIgnoreCase(getRefundStatus());
	}

	public boolean isRefundCompleted() {
		return REFUND_STATUS_COMPLETED.equalsIgnoreCase(getRefundStatus());
	}

	public boolean isRefundFailed() {
		return REFUND_STATUS_FAILED.equalsIgnoreCase(getRefundStatus());
	}
}