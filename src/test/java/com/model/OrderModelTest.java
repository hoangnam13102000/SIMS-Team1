package com.model;

import java.math.BigDecimal;

public class OrderModelTest {

    public static void main(String[] args) {

        Order order = new Order();
        order.setOrderId(1);
        order.setOrderCode("ONL_000001");
        order.setCustomerId(1);
        order.setReceiverName("Lê Hoa Trường Vũ");
        order.setReceiverPhone("0903010000");
        order.setReceiverEmail("client_test@sims.local");
        order.setShippingAddress("TP. Hồ Chí Minh");
        order.setPaymentMethod("COD");
        order.setPaymentStatus(Order.PAYMENT_UNPAID);
        order.setOrderStatus(Order.STATUS_PENDING);
        order.setSubTotal(new BigDecimal("24000"));
        order.setShippingFee(BigDecimal.ZERO);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(new BigDecimal("24000"));

        OrderDetail detail = new OrderDetail(
                1,
                "SP_0001",
                "Cà chua",
                1,
                new BigDecimal("24000")
        );

        detail.setOrderId(order.getOrderId());
        detail.setLineTotal(new BigDecimal("24000"));

        order.getDetails().add(detail);
        order.setItemCount(order.getDetails().size());

        System.out.println("Mã đơn: " + order.getOrderCode());
        System.out.println("Khách nhận: " + order.getReceiverName());
        System.out.println("Số sản phẩm: " + order.getItemCount());
        System.out.println("Tổng tiền: " + order.getTotalAmount());
        System.out.println("Có thể hủy: " + order.canCancelByCustomer());
        System.out.println("Tên sản phẩm: "
                + order.getDetails().get(0).getProductNameSnapshot());
    }
}