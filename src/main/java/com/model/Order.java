package com.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Đại diện cho một đơn hàng online của khách hàng.
 */
public class Order {

	public static final String STATUS_PENDING = "PENDING";
	public static final String STATUS_CONFIRMED = "CONFIRMED";
	public static final String STATUS_SHIPPING = "SHIPPING";
	public static final String STATUS_COMPLETED = "COMPLETED";
	public static final String STATUS_CANCELLED = "CANCELLED";

	public static final String PAYMENT_UNPAID = "UNPAID";
	public static final String PAYMENT_PENDING = "PENDING";
	public static final String PAYMENT_PAID = "PAID";
	public static final String PAYMENT_FAILED = "FAILED";
	public static final String PAYMENT_CANCELLED = "CANCELLED";
	public static final String PAYMENT_REFUND_PENDING = "REFUND_PENDING";
	public static final String PAYMENT_REFUNDED = "REFUNDED";

	private int orderId;
	private String orderCode;

	private int customerId;

	/**
	 * InvoiceID có thể null khi đơn hàng mới được tạo và chưa chuyển thành hóa đơn.
	 */
	private Integer invoiceId;

	private String receiverName;
	private String receiverPhone;
	private String receiverEmail;
	private String shippingAddress;

	private String paymentMethod;
	private String paymentStatus;
	private String orderStatus;

	private BigDecimal subTotal;
	private BigDecimal shippingFee;
	private BigDecimal discountAmount;
	private BigDecimal totalAmount;

	private String cancelReason;
	private Integer cancelledBy;

	private LocalDateTime cancelledAt;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;

	/**
	 * Không phải cột trực tiếp trong Orders. DAO sẽ đếm số dòng sản phẩm và gán vào
	 * đây.
	 */
	private int itemCount;

	private List<OrderDetail> details = new ArrayList<>();

	public Order() {
	}

	public int getOrderId() {
		return orderId;
	}

	public void setOrderId(int orderId) {
		this.orderId = orderId;
	}

	public String getOrderCode() {
		return orderCode;
	}

	public void setOrderCode(String orderCode) {
		this.orderCode = orderCode;
	}

	public int getCustomerId() {
		return customerId;
	}

	public void setCustomerId(int customerId) {
		this.customerId = customerId;
	}

	public Integer getInvoiceId() {
		return invoiceId;
	}

	public void setInvoiceId(Integer invoiceId) {
		this.invoiceId = invoiceId;
	}

	public String getReceiverName() {
		return receiverName;
	}

	public void setReceiverName(String receiverName) {
		this.receiverName = receiverName;
	}

	public String getReceiverPhone() {
		return receiverPhone;
	}

	public void setReceiverPhone(String receiverPhone) {
		this.receiverPhone = receiverPhone;
	}

	public String getReceiverEmail() {
		return receiverEmail;
	}

	public void setReceiverEmail(String receiverEmail) {
		this.receiverEmail = receiverEmail;
	}

	public String getShippingAddress() {
		return shippingAddress;
	}

	public void setShippingAddress(String shippingAddress) {
		this.shippingAddress = shippingAddress;
	}

	public String getPaymentMethod() {
		return paymentMethod;
	}

	public void setPaymentMethod(String paymentMethod) {
		this.paymentMethod = paymentMethod;
	}

	public String getPaymentStatus() {
		return paymentStatus;
	}

	public void setPaymentStatus(String paymentStatus) {
		this.paymentStatus = paymentStatus;
	}

	public String getOrderStatus() {
		return orderStatus;
	}

	public void setOrderStatus(String orderStatus) {
		this.orderStatus = orderStatus;
	}

	public BigDecimal getSubTotal() {
		return subTotal;
	}

	public void setSubTotal(BigDecimal subTotal) {
		this.subTotal = subTotal;
	}

	public BigDecimal getShippingFee() {
		return shippingFee;
	}

	public void setShippingFee(BigDecimal shippingFee) {
		this.shippingFee = shippingFee;
	}

	public BigDecimal getDiscountAmount() {
		return discountAmount;
	}

	public void setDiscountAmount(BigDecimal discountAmount) {
		this.discountAmount = discountAmount;
	}

	public BigDecimal getTotalAmount() {
		return totalAmount;
	}

	public void setTotalAmount(BigDecimal totalAmount) {
		this.totalAmount = totalAmount;
	}

	public String getCancelReason() {
		return cancelReason;
	}

	public void setCancelReason(String cancelReason) {
		this.cancelReason = cancelReason;
	}

	public Integer getCancelledBy() {
		return cancelledBy;
	}

	public void setCancelledBy(Integer cancelledBy) {
		this.cancelledBy = cancelledBy;
	}

	public LocalDateTime getCancelledAt() {
		return cancelledAt;
	}

	public void setCancelledAt(LocalDateTime cancelledAt) {
		this.cancelledAt = cancelledAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(LocalDateTime createdAt) {
		this.createdAt = createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(LocalDateTime updatedAt) {
		this.updatedAt = updatedAt;
	}

	public int getItemCount() {
		return itemCount;
	}

	public void setItemCount(int itemCount) {
		this.itemCount = itemCount;
	}

	public List<OrderDetail> getDetails() {
		return details;
	}

	public void setDetails(List<OrderDetail> details) {
		if (details == null) {
			this.details = new ArrayList<>();
		} else {
			this.details = details;
		}
	}

	public boolean isPending() {
		return STATUS_PENDING.equalsIgnoreCase(orderStatus);
	}

	public boolean isConfirmed() {
		return STATUS_CONFIRMED.equalsIgnoreCase(orderStatus);
	}

	public boolean isShipping() {
		return STATUS_SHIPPING.equalsIgnoreCase(orderStatus);
	}

	public boolean isCompleted() {
		return STATUS_COMPLETED.equalsIgnoreCase(orderStatus);
	}

	public boolean isCancelled() {
		return STATUS_CANCELLED.equalsIgnoreCase(orderStatus);
	}

	public boolean isPaid() {
		return PAYMENT_PAID.equalsIgnoreCase(paymentStatus);
	}

	/**
	 * Khách chỉ được hủy đơn khi đơn đang chờ xác nhận hoặc đã xác nhận nhưng chưa
	 * giao.
	 */
	public boolean canCancelByCustomer() {
		return isPending() || isConfirmed();
	}
}
