package com.model;

import java.util.List;

import com.dao.OrderDAO;

public class OrderDAOTest {

    public static void main(String[] args) {

        /*
         * Thay số 1 bằng CustomerID mà SSMS vừa trả về.
         */
        int customerId = 9;

        OrderDAO dao = new OrderDAO();

        List<Order> orders =
                dao.getOrdersByCustomer(customerId);

        System.out.println(
                "Số đơn tìm thấy: " + orders.size()
        );

        if (orders.isEmpty()) {
            System.out.println(
                    "Không có đơn để kiểm thử. "
                  + "Hãy kiểm tra lại CustomerID."
            );
            return;
        }

        Order order = orders.get(0);

        System.out.println(
                "Mã đơn: " + order.getOrderCode()
        );

        System.out.println(
                "Trạng thái: " + order.getOrderStatus()
        );

        System.out.println(
                "Tổng tiền: " + order.getTotalAmount()
        );

        Order fullOrder =
                dao.getOrderByIdForCustomer(
                        order.getOrderId(),
                        customerId
                );

        if (fullOrder == null) {
            System.out.println(
                    "Không đọc được chi tiết đơn."
            );
            return;
        }

        System.out.println(
                "Số dòng sản phẩm: "
                        + fullOrder.getDetails().size()
        );

        for (OrderDetail detail : fullOrder.getDetails()) {
            System.out.println(
                    detail.getProductCodeSnapshot()
                  + " | "
                  + detail.getProductNameSnapshot()
                  + " | SL: "
                  + detail.getQuantity()
                  + " | Thành tiền: "
                  + detail.getLineTotal()
            );
        }

        String cancelResult =
                dao.cancelOrderByCustomer(
                        order.getOrderId(),
                        customerId,
                        "Khách thay đổi nhu cầu"
                );

        if (cancelResult == null) {
            System.out.println(
                    "Hủy đơn thành công."
            );
        } else {
            System.out.println(
                    "Hủy đơn thất bại: " + cancelResult
            );
        }
    }
}
