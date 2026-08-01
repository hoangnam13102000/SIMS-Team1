package com.model;

import java.math.BigDecimal;

/**
 * Đại diện cho một sản phẩm nằm trong đơn hàng online.
 */
public class OrderDetail {

	private int orderDetailId;
	private int orderId;
	private int productId;

	/**
	 * Lưu lại mã và tên sản phẩm tại thời điểm khách đặt hàng. Nếu sản phẩm đổi tên
	 * sau này thì lịch sử đơn vẫn giữ tên cũ.
	 */
	private String productCodeSnapshot;
	private String productNameSnapshot;

	private int quantity;
	private BigDecimal unitPrice;
	private BigDecimal lineTotal;

	/**
	 * Không phải cột trong OrderDetails. DAO có thể lấy ImageUrl từ bảng Products
	 * để hiển thị giao diện.
	 */
	private String productImageUrl;

	public OrderDetail() {
	}

	public OrderDetail(int productId, String productCodeSnapshot, String productNameSnapshot, int quantity,
			BigDecimal unitPrice) {

		this.productId = productId;
		this.productCodeSnapshot = productCodeSnapshot;
		this.productNameSnapshot = productNameSnapshot;
		this.quantity = quantity;
		this.unitPrice = unitPrice;
	}

	public int getOrderDetailId() {
		return orderDetailId;
	}

	public void setOrderDetailId(int orderDetailId) {
		this.orderDetailId = orderDetailId;
	}

	public int getOrderId() {
		return orderId;
	}

	public void setOrderId(int orderId) {
		this.orderId = orderId;
	}

	public int getProductId() {
		return productId;
	}

	public void setProductId(int productId) {
		this.productId = productId;
	}

	public String getProductCodeSnapshot() {
		return productCodeSnapshot;
	}

	public void setProductCodeSnapshot(String productCodeSnapshot) {
		this.productCodeSnapshot = productCodeSnapshot;
	}

	public String getProductNameSnapshot() {
		return productNameSnapshot;
	}

	public void setProductNameSnapshot(String productNameSnapshot) {
		this.productNameSnapshot = productNameSnapshot;
	}

	public int getQuantity() {
		return quantity;
	}

	public void setQuantity(int quantity) {
		this.quantity = quantity;
	}

	public BigDecimal getUnitPrice() {
		return unitPrice;
	}

	public void setUnitPrice(BigDecimal unitPrice) {
		this.unitPrice = unitPrice;
	}

	public BigDecimal getLineTotal() {
		return lineTotal;
	}

	public void setLineTotal(BigDecimal lineTotal) {
		this.lineTotal = lineTotal;
	}

	public String getProductImageUrl() {
		return productImageUrl;
	}

	public void setProductImageUrl(String productImageUrl) {
		this.productImageUrl = productImageUrl;
	}
}