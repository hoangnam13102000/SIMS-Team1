package com.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Order;
import com.model.OrderDetail;
import com.utils.DBConnection;

/**
 * DAO dành cho đơn hàng online của khách hàng.
 *
 * Mọi phương thức đọc, xem chi tiết và hủy đơn đều yêu cầu CustomerID
 * để ngăn khách hàng xem hoặc hủy đơn của người khác.
 */
public class OrderDAO {

    private static final String ORDER_COLUMNS =
            "o.OrderID, o.OrderCode, o.CustomerID, o.InvoiceID, "
          + "o.ReceiverName, o.ReceiverPhone, o.ReceiverEmail, "
          + "o.ShippingAddress, o.PaymentMethod, o.PaymentStatus, "
          + "o.OrderStatus, o.SubTotal, o.ShippingFee, "
          + "o.DiscountAmount, o.TotalAmount, o.CancelReason, "
          + "o.CancelledBy, o.CancelledAt, o.CreatedAt, o.UpdatedAt, "
          + "(SELECT COUNT(*) FROM OrderDetails d "
          + " WHERE d.OrderID = o.OrderID) AS ItemCount ";

    /**
     * Lấy toàn bộ đơn hàng thuộc về một khách hàng.
     */
    public List<Order> getOrdersByCustomer(int customerId) {

        String sql =
                "SELECT " + ORDER_COLUMNS
              + "FROM Orders o "
              + "WHERE o.CustomerID = ? "
              + "ORDER BY o.CreatedAt DESC, o.OrderID DESC";

        List<Order> orders = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    orders.add(mapOrder(rs));
                }
            }

        } catch (SQLException e) {
            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "OrderDAO.getOrdersByCustomer - customerId="
                            + customerId,
                    e
            );
        }

        return orders;
    }

    /**
     * Lấy một đơn hàng nhưng phải đúng cả OrderID và CustomerID.
     *
     * Trả về null nếu:
     * - Đơn không tồn tại.
     * - Đơn thuộc về một khách hàng khác.
     */
    public Order getOrderByIdForCustomer(
            int orderId,
            int customerId) {

        String sql =
                "SELECT " + ORDER_COLUMNS
              + "FROM Orders o "
              + "WHERE o.OrderID = ? AND o.CustomerID = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ps.setInt(2, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order order = mapOrder(rs);
                    order.setDetails(
                            getDetailsForCustomer(orderId, customerId)
                    );
                    return order;
                }
            }

        } catch (SQLException e) {
            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "OrderDAO.getOrderByIdForCustomer - orderId="
                            + orderId,
                    e
            );
        }

        return null;
    }

    /**
     * Lấy danh sách sản phẩm của đơn hàng.
     *
     * Câu lệnh JOIN Orders và kiểm tra CustomerID bảo đảm khách
     * không thể xem sản phẩm trong đơn hàng của người khác.
     */
    public List<OrderDetail> getDetailsForCustomer(
            int orderId,
            int customerId) {

        String sql =
                "SELECT od.OrderDetailID, od.OrderID, od.ProductID, "
              + "od.ProductCodeSnapshot, od.ProductNameSnapshot, "
              + "od.Quantity, od.UnitPrice, od.LineTotal, "
              + "p.ImageUrl AS ProductImageUrl "
              + "FROM OrderDetails od "
              + "JOIN Orders o ON o.OrderID = od.OrderID "
              + "LEFT JOIN Products p ON p.ProductID = od.ProductID "
              + "WHERE od.OrderID = ? AND o.CustomerID = ? "
              + "ORDER BY od.OrderDetailID ASC";

        List<OrderDetail> details = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, orderId);
            ps.setInt(2, customerId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {

                    OrderDetail detail = new OrderDetail();

                    detail.setOrderDetailId(
                            rs.getInt("OrderDetailID")
                    );

                    detail.setOrderId(
                            rs.getInt("OrderID")
                    );

                    detail.setProductId(
                            rs.getInt("ProductID")
                    );

                    detail.setProductCodeSnapshot(
                            rs.getString("ProductCodeSnapshot")
                    );

                    detail.setProductNameSnapshot(
                            rs.getString("ProductNameSnapshot")
                    );

                    detail.setQuantity(
                            rs.getInt("Quantity")
                    );

                    detail.setUnitPrice(
                            rs.getBigDecimal("UnitPrice")
                    );

                    detail.setLineTotal(
                            rs.getBigDecimal("LineTotal")
                    );

                    detail.setProductImageUrl(
                            rs.getString("ProductImageUrl")
                    );

                    details.add(detail);
                }
            }

        } catch (SQLException e) {
            AppLogger.getInstance().error(
                    ErrorCode.DB_QUERY_FAIL,
                    "OrderDAO.getDetailsForCustomer - orderId="
                            + orderId,
                    e
            );
        }

        return details;
    }

    /**
     * Hủy đơn hàng của khách.
     *
     * @return null nếu thành công; ngược lại trả về thông báo lỗi.
     */
    public String cancelOrderByCustomer(
            int orderId,
            int customerId,
            String reason) {

        if (reason == null || reason.trim().isEmpty()) {
            return "Bạn phải nhập lý do hủy đơn hàng.";
        }

        Connection con = null;

        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String currentStatus;
            Integer invoiceId;

            /*
             * UPDLOCK khóa đơn trong lúc kiểm tra nhằm tránh hai yêu cầu
             * hủy hoặc cập nhật trạng thái xảy ra cùng lúc.
             */
            String checkSql =
                    "SELECT OrderStatus, InvoiceID "
                  + "FROM Orders WITH (UPDLOCK, ROWLOCK) "
                  + "WHERE OrderID = ? AND CustomerID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(checkSql)) {

                ps.setInt(1, orderId);
                ps.setInt(2, customerId);

                try (ResultSet rs = ps.executeQuery()) {

                    if (!rs.next()) {
                        con.rollback();
                        return "Không tìm thấy đơn hàng hoặc đơn hàng "
                             + "không thuộc tài khoản của bạn.";
                    }

                    currentStatus = rs.getString("OrderStatus");

                    int value = rs.getInt("InvoiceID");
                    invoiceId = rs.wasNull() ? null : value;
                }
            }

            boolean canCancel =
                    Order.STATUS_PENDING.equalsIgnoreCase(currentStatus)
                 || Order.STATUS_CONFIRMED.equalsIgnoreCase(currentStatus);

            if (!canCancel) {
                con.rollback();

                if (Order.STATUS_CANCELLED.equalsIgnoreCase(
                        currentStatus)) {
                    return "Đơn hàng này đã được hủy trước đó.";
                }

                if (Order.STATUS_SHIPPING.equalsIgnoreCase(
                        currentStatus)) {
                    return "Đơn hàng đang được giao nên không thể hủy.";
                }

                if (Order.STATUS_COMPLETED.equalsIgnoreCase(
                        currentStatus)) {
                    return "Đơn hàng đã hoàn thành nên không thể hủy.";
                }

                return "Trạng thái hiện tại không cho phép hủy đơn.";
            }

            /*
             * Nếu đã thanh toán thì chuyển sang chờ hoàn tiền.
             * Nếu chưa thanh toán thì chuyển thành CANCELLED.
             */
            String updateOrderSql =
                    "UPDATE Orders "
                  + "SET OrderStatus = 'CANCELLED', "
                  + "    CancelReason = ?, "
                  + "    CancelledBy = ?, "
                  + "    CancelledAt = GETDATE(), "
                  + "    UpdatedAt = GETDATE(), "
                  + "    PaymentStatus = CASE "
                  + "        WHEN PaymentStatus = 'PAID' "
                  + "            THEN 'REFUND_PENDING' "
                  + "        WHEN PaymentStatus = 'REFUNDED' "
                  + "            THEN 'REFUNDED' "
                  + "        ELSE 'CANCELLED' "
                  + "    END "
                  + "WHERE OrderID = ? "
                  + "  AND CustomerID = ? "
                  + "  AND OrderStatus IN ('PENDING', 'CONFIRMED')";

            try (PreparedStatement ps =
                         con.prepareStatement(updateOrderSql)) {

                ps.setNString(1, reason.trim());
                ps.setInt(2, customerId);
                ps.setInt(3, orderId);
                ps.setInt(4, customerId);

                int affected = ps.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Không thể cập nhật trạng thái đơn hàng."
                    );
                }
            }

            /*
             * Nếu đơn đã liên kết với Invoices thì hủy hóa đơn.
             * Trigger hóa đơn sẽ hoàn lại đúng các lô hàng đã trừ.
             */
            if (invoiceId != null) {

                String cancelInvoiceSql =
                        "UPDATE Invoices "
                      + "SET Status = 'CANCELLED', "
                      + "    CancelReason = ?, "
                      + "    CancelledAt = GETDATE() "
                      + "WHERE InvoiceID = ? "
                      + "  AND Status = 'ACTIVE'";

                try (PreparedStatement ps =
                             con.prepareStatement(cancelInvoiceSql)) {

                    ps.setNString(1, reason.trim());
                    ps.setInt(2, invoiceId);

                    int affected = ps.executeUpdate();

                    if (affected != 1) {
                        throw new SQLException(
                                "Không thể hủy hóa đơn liên kết "
                              + "với đơn hàng."
                        );
                    }
                }
            }

            con.commit();

            AppEventBus.getInstance().publish(
                    new DataChangedEvent(DataChangedEvent.ORDER)
            );

            return null;

        } catch (SQLException e) {

            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    AppLogger.getInstance().error(
                            ErrorCode.DB_UPDATE_FAIL,
                            "OrderDAO.cancelOrderByCustomer.rollback",
                            rollbackError
                    );
                }
            }

            AppLogger.getInstance().error(
                    ErrorCode.DB_UPDATE_FAIL,
                    "OrderDAO.cancelOrderByCustomer - orderId="
                            + orderId,
                    e
            );

            return e.getMessage();

        } finally {

            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    AppLogger.getInstance().error(
                            ErrorCode.DB_UPDATE_FAIL,
                            "OrderDAO.closeConnection",
                            e
                    );
                }
            }
        }
    }

    /**
     * Chuyển một dòng ResultSet thành đối tượng Order.
     */
    
    /**
     * Tạo đơn online và hóa đơn liên kết trong cùng một transaction.
     *
     * Nếu bất kỳ bước nào thất bại, toàn bộ Orders, OrderDetails,
     * Invoices, InvoiceDetails và thay đổi tồn kho sẽ được rollback.
     *
     * @return null nếu thành công; ngược lại trả về nội dung lỗi.
     */
    public String createOnlineOrder(Order order) {

        if (order == null) {
            return "Thông tin đơn hàng không hợp lệ.";
        }

        if (order.getCustomerId() <= 0) {
            return "Không xác định được khách hàng đang đặt hàng.";
        }

        if (order.getReceiverName() == null
                || order.getReceiverName().trim().isEmpty()) {
            return "Vui lòng nhập tên người nhận.";
        }

        if (order.getReceiverPhone() == null
                || order.getReceiverPhone().trim().isEmpty()) {
            return "Vui lòng nhập số điện thoại người nhận.";
        }

        if (order.getShippingAddress() == null
                || order.getShippingAddress().trim().isEmpty()) {
            return "Vui lòng nhập địa chỉ giao hàng.";
        }

        if (order.getDetails() == null
                || order.getDetails().isEmpty()) {
            return "Giỏ hàng đang trống.";
        }

        String paymentMethod = order.getPaymentMethod();

        if (!"COD".equalsIgnoreCase(paymentMethod)
                && !"PAYPAL".equalsIgnoreCase(paymentMethod)) {
            return "Phương thức thanh toán không hợp lệ.";
        }

        paymentMethod = paymentMethod.toUpperCase();

        Connection con = null;

        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            BigDecimal subTotal = BigDecimal.ZERO;

            /*
             * Đọc lại thông tin sản phẩm từ database.
             * Không sử dụng giá do giao diện truyền xuống vì có thể đã cũ.
             */
            String productSql =
                    "SELECT ProductCode, ProductName, SellPrice, Status "
                  + "FROM Products WITH (UPDLOCK, HOLDLOCK) "
                  + "WHERE ProductID = ?";

            String stockSql =
                    "SELECT ISNULL(SUM(RemainingQty), 0) AS AvailableQty "
                  + "FROM InventoryBatch WITH (UPDLOCK, HOLDLOCK) "
                  + "WHERE ProductID = ? "
                  + "  AND Status = 'ACTIVE' "
                  + "  AND RemainingQty > 0 "
                  + "  AND (ExpiryDate IS NULL "
                  + "       OR ExpiryDate >= CAST(GETDATE() AS DATE))";

            for (OrderDetail detail : order.getDetails()) {

                if (detail.getProductId() <= 0) {
                    throw new SQLException(
                            "Có sản phẩm không hợp lệ trong giỏ hàng."
                    );
                }

                if (detail.getQuantity() <= 0) {
                    throw new SQLException(
                            "Số lượng sản phẩm phải lớn hơn 0."
                    );
                }

                String productCode;
                String productName;
                String productStatus;
                BigDecimal sellPrice;

                try (PreparedStatement ps =
                             con.prepareStatement(productSql)) {

                    ps.setInt(1, detail.getProductId());

                    try (ResultSet rs = ps.executeQuery()) {

                        if (!rs.next()) {
                            throw new SQLException(
                                    "Sản phẩm có mã ID "
                                  + detail.getProductId()
                                  + " không còn tồn tại."
                            );
                        }

                        productCode =
                                rs.getString("ProductCode");

                        productName =
                                rs.getString("ProductName");

                        sellPrice =
                                rs.getBigDecimal("SellPrice");

                        productStatus =
                                rs.getString("Status");
                    }
                }

                if (!"ACTIVE".equalsIgnoreCase(productStatus)) {
                    throw new SQLException(
                            "Sản phẩm \"" + productName
                          + "\" hiện không còn được bán."
                    );
                }

                int availableQuantity;

                try (PreparedStatement ps =
                             con.prepareStatement(stockSql)) {

                    ps.setInt(1, detail.getProductId());

                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        availableQuantity =
                                rs.getInt("AvailableQty");
                    }
                }

                if (availableQuantity < detail.getQuantity()) {
                    throw new SQLException(
                            "Sản phẩm \"" + productName
                          + "\" chỉ còn " + availableQuantity
                          + " sản phẩm."
                    );
                }

                /*
                 * Cập nhật snapshot bằng dữ liệu thật vừa đọc từ DB.
                 */
                detail.setProductCodeSnapshot(productCode);
                detail.setProductNameSnapshot(productName);
                detail.setUnitPrice(sellPrice);

                BigDecimal lineTotal =
                        sellPrice.multiply(
                                BigDecimal.valueOf(
                                        detail.getQuantity()
                                )
                        );

                detail.setLineTotal(lineTotal);
                subTotal = subTotal.add(lineTotal);
            }

            BigDecimal shippingFee = BigDecimal.ZERO;
            BigDecimal discountAmount = BigDecimal.ZERO;

            /*
             * Hóa đơn online không thuộc ca bán hàng nên ShiftID = NULL.
             *
             * CreatedBy dùng CustomerID vì CustomerID cũng chính là UserID.
             * VATRate = 0 vì SellPrice của giao diện đang được xem là giá
             * cuối cùng khách phải thanh toán.
             */
            String randomPart =
                    UUID.randomUUID()
                            .toString()
                            .replace("-", "")
                            .substring(0, 26);

            String invoiceCode = "WEB_" + randomPart;

            String invoicePaymentMethod =
                    "PAYPAL".equals(paymentMethod)
                            ? "CARD"
                            : "CASH";

            String insertInvoiceSql =
                    "INSERT INTO Invoices ("
                  + "InvoiceCode, ShiftID, CreatedBy, CustomerID, "
                  + "SubTotal, VATRate, TotalAmount, "
                  + "PaymentMethod, Status"
                  + ") VALUES (?, NULL, ?, ?, ?, 0, ?, ?, 'ACTIVE')";

            int invoiceId;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 insertInvoiceSql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                ps.setString(1, invoiceCode);
                ps.setInt(2, order.getCustomerId());
                ps.setInt(3, order.getCustomerId());
                ps.setBigDecimal(4, subTotal);
                ps.setBigDecimal(5, subTotal);
                ps.setString(6, invoicePaymentMethod);

                int affected = ps.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Không thể tạo hóa đơn cho đơn hàng."
                    );
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException(
                                "Không lấy được InvoiceID vừa tạo."
                        );
                    }

                    invoiceId = keys.getInt(1);
                }
            }

            String paymentStatus =
                    "PAYPAL".equals(paymentMethod)
                            ? Order.PAYMENT_PENDING
                            : Order.PAYMENT_UNPAID;

            String insertOrderSql =
                    "INSERT INTO Orders ("
                  + "CustomerID, InvoiceID, ReceiverName, "
                  + "ReceiverPhone, ReceiverEmail, ShippingAddress, "
                  + "PaymentMethod, PaymentStatus, OrderStatus, "
                  + "SubTotal, ShippingFee, DiscountAmount"
                  + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', ?, ?, ?)";

            int orderId;

            try (PreparedStatement ps =
                         con.prepareStatement(
                                 insertOrderSql,
                                 Statement.RETURN_GENERATED_KEYS
                         )) {

                ps.setInt(1, order.getCustomerId());
                ps.setInt(2, invoiceId);
                ps.setNString(3, order.getReceiverName().trim());
                ps.setString(4, order.getReceiverPhone().trim());

                if (order.getReceiverEmail() == null
                        || order.getReceiverEmail().trim().isEmpty()) {
                    ps.setNull(5, java.sql.Types.VARCHAR);
                } else {
                    ps.setString(
                            5,
                            order.getReceiverEmail().trim()
                    );
                }

                ps.setNString(
                        6,
                        order.getShippingAddress().trim()
                );

                ps.setString(7, paymentMethod);
                ps.setString(8, paymentStatus);
                ps.setBigDecimal(9, subTotal);
                ps.setBigDecimal(10, shippingFee);
                ps.setBigDecimal(11, discountAmount);

                int affected = ps.executeUpdate();

                if (affected != 1) {
                    throw new SQLException(
                            "Không thể tạo đơn hàng."
                    );
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException(
                                "Không lấy được OrderID vừa tạo."
                        );
                    }

                    orderId = keys.getInt(1);
                }
            }

            /*
             * Thêm InvoiceDetails trước.
             * Trigger trg_InvoiceDetails_CheckStock sẽ trừ kho theo FEFO.
             */
            String insertInvoiceDetailSql =
                    "INSERT INTO InvoiceDetails ("
                  + "InvoiceID, ProductID, Quantity, UnitPrice"
                  + ") VALUES (?, ?, ?, ?)";

            String insertOrderDetailSql =
                    "INSERT INTO OrderDetails ("
                  + "OrderID, ProductID, ProductCodeSnapshot, "
                  + "ProductNameSnapshot, Quantity, UnitPrice"
                  + ") VALUES (?, ?, ?, ?, ?, ?)";

            for (OrderDetail detail : order.getDetails()) {

                try (PreparedStatement ps =
                             con.prepareStatement(
                                     insertInvoiceDetailSql
                             )) {

                    ps.setInt(1, invoiceId);
                    ps.setInt(2, detail.getProductId());
                    ps.setInt(3, detail.getQuantity());
                    ps.setBigDecimal(4, detail.getUnitPrice());

                    int affected = ps.executeUpdate();

                    if (affected <= 0) {
                        throw new SQLException(
                                "Không thể tạo chi tiết hóa đơn."
                        );
                    }
                }

                try (PreparedStatement ps =
                             con.prepareStatement(
                                     insertOrderDetailSql
                             )) {

                    ps.setInt(1, orderId);
                    ps.setInt(2, detail.getProductId());

                    ps.setString(
                            3,
                            detail.getProductCodeSnapshot()
                    );

                    ps.setNString(
                            4,
                            detail.getProductNameSnapshot()
                    );

                    ps.setInt(5, detail.getQuantity());
                    ps.setBigDecimal(6, detail.getUnitPrice());

                    int affected = ps.executeUpdate();

                    if (affected != 1) {
                        throw new SQLException(
                                "Không thể tạo chi tiết đơn hàng."
                        );
                    }
                }
            }

            /*
             * Lấy lại các cột SQL Server tự sinh.
             */
            String generatedDataSql =
                    "SELECT OrderCode, TotalAmount, "
                  + "CreatedAt, UpdatedAt "
                  + "FROM Orders WHERE OrderID = ?";

            try (PreparedStatement ps =
                         con.prepareStatement(generatedDataSql)) {

                ps.setInt(1, orderId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {

                        order.setOrderId(orderId);
                        order.setInvoiceId(invoiceId);
                        order.setOrderCode(
                                rs.getString("OrderCode")
                        );

                        order.setSubTotal(subTotal);
                        order.setShippingFee(shippingFee);
                        order.setDiscountAmount(discountAmount);

                        order.setTotalAmount(
                                rs.getBigDecimal("TotalAmount")
                        );

                        order.setPaymentMethod(paymentMethod);
                        order.setPaymentStatus(paymentStatus);
                        order.setOrderStatus(
                                Order.STATUS_PENDING
                        );

                        Timestamp createdAt =
                                rs.getTimestamp("CreatedAt");

                        order.setCreatedAt(
                                createdAt == null
                                        ? null
                                        : createdAt.toLocalDateTime()
                        );

                        Timestamp updatedAt =
                                rs.getTimestamp("UpdatedAt");

                        order.setUpdatedAt(
                                updatedAt == null
                                        ? null
                                        : updatedAt.toLocalDateTime()
                        );
                    }
                }
            }

            con.commit();

            AppEventBus.getInstance().publish(
                    new DataChangedEvent(DataChangedEvent.ORDER)
            );

            AppEventBus.getInstance().publish(
                    new DataChangedEvent(DataChangedEvent.INVOICE)
            );

            return null;

        } catch (SQLException e) {

            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    AppLogger.getInstance().error(
                            ErrorCode.ORDER_CREATE_FAIL,
                            "OrderDAO.createOnlineOrder.rollback",
                            rollbackError
                    );
                }
            }

            AppLogger.getInstance().error(
                    ErrorCode.ORDER_CREATE_FAIL,
                    "OrderDAO.createOnlineOrder",
                    e
            );

            return e.getMessage();

        } finally {

            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException e) {
                    AppLogger.getInstance().error(
                            ErrorCode.DB_CONNECTION_FAIL,
                            "OrderDAO.createOnlineOrder.close",
                            e
                    );
                }
            }
        }
    }
    private Order mapOrder(ResultSet rs) throws SQLException {

        Order order = new Order();

        order.setOrderId(rs.getInt("OrderID"));
        order.setOrderCode(rs.getString("OrderCode"));
        order.setCustomerId(rs.getInt("CustomerID"));

        int invoiceValue = rs.getInt("InvoiceID");
        order.setInvoiceId(
                rs.wasNull() ? null : invoiceValue
        );

        order.setReceiverName(
                rs.getString("ReceiverName")
        );

        order.setReceiverPhone(
                rs.getString("ReceiverPhone")
        );

        order.setReceiverEmail(
                rs.getString("ReceiverEmail")
        );

        order.setShippingAddress(
                rs.getString("ShippingAddress")
        );

        order.setPaymentMethod(
                rs.getString("PaymentMethod")
        );

        order.setPaymentStatus(
                rs.getString("PaymentStatus")
        );

        order.setOrderStatus(
                rs.getString("OrderStatus")
        );

        order.setSubTotal(
                rs.getBigDecimal("SubTotal")
        );

        order.setShippingFee(
                rs.getBigDecimal("ShippingFee")
        );

        order.setDiscountAmount(
                rs.getBigDecimal("DiscountAmount")
        );

        order.setTotalAmount(
                rs.getBigDecimal("TotalAmount")
        );

        order.setCancelReason(
                rs.getString("CancelReason")
        );

        int cancelledByValue = rs.getInt("CancelledBy");
        order.setCancelledBy(
                rs.wasNull() ? null : cancelledByValue
        );

        Timestamp cancelledAt =
                rs.getTimestamp("CancelledAt");

        order.setCancelledAt(
                cancelledAt == null
                        ? null
                        : cancelledAt.toLocalDateTime()
        );

        Timestamp createdAt =
                rs.getTimestamp("CreatedAt");

        order.setCreatedAt(
                createdAt == null
                        ? null
                        : createdAt.toLocalDateTime()
        );

        Timestamp updatedAt =
                rs.getTimestamp("UpdatedAt");

        order.setUpdatedAt(
                updatedAt == null
                        ? null
                        : updatedAt.toLocalDateTime()
        );

        order.setItemCount(
                rs.getInt("ItemCount")
        );

        return order;
    }
}