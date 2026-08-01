package com.model;

import java.util.ArrayList;

import com.dao.OrderDAO;

public class CreateOnlineOrderTest {

    public static void main(String[] args) {

        /*
         * CustomerID 9 là tài khoản client_test của bạn.
         */
        int customerId = 9;

        /*
         * Thay số 1 bằng ProductID có AvailableQuantity > 0
         * mà SSMS vừa trả về.
         */
        int productId = 6;

        Order order = new Order();

        order.setCustomerId(customerId);
        order.setReceiverName("Lê Hoa Trường Vũ");
        order.setReceiverPhone("0903010000");
        order.setReceiverEmail("client_test@sims.local");
        order.setShippingAddress("TP. Hồ Chí Minh");
        order.setPaymentMethod("COD");

        OrderDetail detail = new OrderDetail();
        detail.setProductId(productId);
        detail.setQuantity(1);

        order.setDetails(new ArrayList<>());
        order.getDetails().add(detail);

        OrderDAO dao = new OrderDAO();

        String error = dao.createOnlineOrder(order);

        if (error != null) {
            System.out.println(
                    "Tạo đơn thất bại: " + error
            );
            return;
        }

        System.out.println("Tạo đơn thành công.");
        System.out.println(
                "OrderID: " + order.getOrderId()
        );
        System.out.println(
                "OrderCode: " + order.getOrderCode()
        );
        System.out.println(
                "InvoiceID: " + order.getInvoiceId()
        );
        System.out.println(
                "Tổng tiền: " + order.getTotalAmount()
        );
        System.out.println(
                "Trạng thái đơn: "
                        + order.getOrderStatus()
        );
        System.out.println(
                "Trạng thái thanh toán: "
                        + order.getPaymentStatus()
        );
    }
}
