package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Order;
import com.model.OrderDetail;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho đơn hàng online từ khách (xem sql/Orders_SIMS.sql). Tạo đơn được
 * bọc trong 1 transaction (Orders + OrderDetails), phát {@link DataChangedEvent}
 * (entity ORDER) sau khi tạo/đổi trạng thái thành công để các listener trong
 * cùng JVM (dashboard, OrdersPanel) cập nhật ngay; đơn nhận thông báo real-time
 * bên admin (khi client/admin chạy 2 tiến trình khác nhau) do
 * {@code com.service.OrderNotifyPoller} đảm nhiệm bằng cách polling định kỳ.
 */
public class OrderDAO extends BaseDAO<Order> {

    private static final String BASE_TABLE = "Orders o";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() { return BASE_TABLE; }

    @Override
    protected String getJoinClause() { return null; }

    @Override
    protected String getColumns() {
        return "o.OrderID, o.OrderCode, o.CustomerID, o.CustomerName, o.CustomerEmail, o.CustomerPhone, "
                + "o.ShippingAddress, o.CreatedAt, o.SubTotal, o.TotalAmount, o.PaymentMethod, o.PaymentStatus, "
                + "o.PayPalOrderID, o.PayPalCaptureID, o.OrderStatus, o.SeenByAdmin, "
                + "(SELECT COUNT(*) FROM OrderDetails d WHERE d.OrderID = o.OrderID) AS ItemCount";
    }

    @Override
    protected String getOrderBy() { return "o.CreatedAt DESC, o.OrderID DESC"; }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"o.OrderCode", "o.CustomerName", "o.CustomerEmail", "o.CustomerPhone"};
    }

    @Override
    protected Order mapResultSet(ResultSet rs) throws SQLException {
        Order order = new Order();
        order.setOrderId(rs.getInt("OrderID"));
        order.setOrderCode(rs.getString("OrderCode"));

        int customerId = rs.getInt("CustomerID");
        order.setCustomerId(rs.wasNull() ? null : customerId);
        order.setCustomerName(rs.getString("CustomerName"));
        order.setCustomerEmail(rs.getString("CustomerEmail"));
        order.setCustomerPhone(rs.getString("CustomerPhone"));
        order.setShippingAddress(rs.getString("ShippingAddress"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        order.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        order.setSubTotal(rs.getBigDecimal("SubTotal"));
        order.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        order.setPaymentMethod(rs.getString("PaymentMethod"));
        order.setPaymentStatus(rs.getString("PaymentStatus"));
        order.setPayPalOrderId(rs.getString("PayPalOrderID"));
        order.setPayPalCaptureId(rs.getString("PayPalCaptureID"));
        order.setOrderStatus(rs.getString("OrderStatus"));
        order.setSeenByAdmin(rs.getBoolean("SeenByAdmin"));
        order.setItemCount(rs.getInt("ItemCount"));
        return order;
    }

    /**
     * Tạo đơn hàng mới (Orders + OrderDetails) trong 1 transaction. Đầu vào
     * order.paymentStatus/payPalOrderId/payPalCaptureId đã được set sẵn từ
     * bên gọi (COD -> PENDING ngay, PAYPAL -> PAID sau khi capture thành
     * công qua PayPalService). Trả về true + order.orderId/orderCode được
     * gán lại nếu thành công.
     */
    public boolean createOrder(Order order, List<OrderDetail> items) {
        String insertOrderSql = "INSERT INTO Orders (CustomerID, CustomerName, CustomerEmail, CustomerPhone, "
                + "ShippingAddress, SubTotal, TotalAmount, PaymentMethod, PaymentStatus, PayPalOrderID, PayPalCaptureID) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertDetailSql = "INSERT INTO OrderDetails (OrderID, ProductID, ProductName, Quantity, UnitPrice) "
                + "VALUES (?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int orderId;
                try (PreparedStatement ps = con.prepareStatement(insertOrderSql, Statement.RETURN_GENERATED_KEYS)) {
                    if (order.getCustomerId() != null) {
                        ps.setInt(1, order.getCustomerId());
                    } else {
                        ps.setNull(1, Types.INTEGER);
                    }
                    ps.setString(2, order.getCustomerName());
                    ps.setString(3, order.getCustomerEmail());
                    ps.setString(4, order.getCustomerPhone());
                    ps.setString(5, order.getShippingAddress());
                    ps.setBigDecimal(6, order.getSubTotal());
                    ps.setBigDecimal(7, order.getTotalAmount());
                    ps.setString(8, order.getPaymentMethod());
                    ps.setString(9, order.getPaymentStatus());
                    ps.setString(10, order.getPayPalOrderId());
                    ps.setString(11, order.getPayPalCaptureId());
                    ps.executeUpdate();

                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Khong lay duoc OrderID vua tao.");
                        orderId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(insertDetailSql)) {
                    for (OrderDetail item : items) {
                        ps.setInt(1, orderId);
                        ps.setInt(2, item.getProductId());
                        ps.setString(3, item.getProductName());
                        ps.setInt(4, item.getQuantity());
                        ps.setBigDecimal(5, item.getUnitPrice());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                order.setOrderId(orderId);
                order.setOrderCode("DH" + String.format("%04d", orderId));
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ORDER));
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.ORDER_CREATE_FAIL,
                    "OrderDAO.createOrder - " + order.getCustomerName(), e);
            return false;
        }
    }

    /** Danh sách đơn CHƯA được admin xem (dùng cho polling chuông thông báo). */
    public List<Order> getUnseenOrders() {
        return getByCondition("o.SeenByAdmin = 0");
    }

    public int countUnseen() {
        String sql = "SELECT COUNT(*) FROM Orders WHERE SeenByAdmin = 0";
        try (Connection con = DBConnection.getConnection();
             Statement stmt = con.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "OrderDAO.countUnseen", e);
            return 0;
        }
    }

    /** Đánh dấu 1 đơn đã xem (khi admin mở chi tiết) - KHÔNG phát DataChangedEvent để tránh vòng lặp refresh vô ích. */
    public boolean markSeen(int orderId) {
        return executeUpdate("UPDATE Orders SET SeenByAdmin = 1 WHERE OrderID = ?", orderId);
    }

    /** Đánh dấu TẤT CẢ đơn hiện tại là đã xem (khi admin bấm vào chuông). */
    public boolean markAllSeen() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE Orders SET SeenByAdmin = 1 WHERE SeenByAdmin = 0")) {
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "OrderDAO.markAllSeen", e);
            return false;
        }
    }

    /**
     * Xác nhận / chuyển trạng thái đơn: NEW -&gt; CONFIRMED -&gt; SHIPPING -&gt; COMPLETED,
     * hủy (-&gt; CANCELLED) chỉ được phép ở NEW hoặc CONFIRMED (đơn đã giao cho ĐVVC
     * thì không hủy được nữa). Chuyển trạng thái không nằm trong
     * {@link #isValidTransition} bị từ chối ngay, không đụng DB.
     * <p>
     * Kho CHỈ thực sự bị trừ tại thời điểm xác nhận (không trừ lúc khách đặt
     * hàng ở {@link #createOrder}, vì đơn NEW có thể bị hủy) - khi chuyển
     * NEW -> CONFIRMED, trừ Products.Stock theo từng dòng OrderDetails. Nếu
     * 1 đơn ĐÃ CONFIRMED sau đó bị hủy, hoàn lại đúng số lượng đã trừ. SHIPPING
     * và COMPLETED không đụng tới kho (đã trừ xong từ bước CONFIRMED). Toàn
     * bộ nằm trong 1 transaction để tránh lệch kho nếu update giữa chừng lỗi.
     */
    public boolean updateOrderStatus(int orderId, String newStatus) {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String oldStatus = getOrderStatusForUpdate(con, orderId);
                if (oldStatus == null || !isValidTransition(oldStatus, newStatus)) {
                    con.rollback();
                    return false;
                }

                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE Orders SET OrderStatus = ? WHERE OrderID = ?")) {
                    ps.setString(1, newStatus);
                    ps.setInt(2, orderId);
                    ps.executeUpdate();
                }

                if ("NEW".equals(oldStatus) && "CONFIRMED".equals(newStatus)) {
                    adjustProductStock(con, orderId, -1);
                } else if ("CONFIRMED".equals(oldStatus) && "CANCELLED".equals(newStatus)) {
                    adjustProductStock(con, orderId, +1);
                }

                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ORDER));
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.ORDER_STATUS_UPDATE_FAIL, "OrderDAO.updateOrderStatus - " + orderId, e);
            return false;
        }
    }

    /** Các bước chuyển trạng thái hợp lệ - hủy chỉ cho phép ở NEW/CONFIRMED, SHIPPING/COMPLETED là 1 chiều. */
    private boolean isValidTransition(String oldStatus, String newStatus) {
        switch (oldStatus) {
            case "NEW":       return "CONFIRMED".equals(newStatus) || "CANCELLED".equals(newStatus);
            case "CONFIRMED": return "SHIPPING".equals(newStatus) || "CANCELLED".equals(newStatus);
            case "SHIPPING":  return "COMPLETED".equals(newStatus);
            default:          return false; // COMPLETED, CANCELLED la trang thai cuoi
        }
    }

    /** Đọc OrderStatus hiện tại, khóa dòng (FOR UPDATE kiểu SQL Server: UPDLOCK, ROWLOCK) để tránh 2 admin cùng xác nhận 1 lúc gây trừ kho 2 lần. */
    private String getOrderStatusForUpdate(Connection con, int orderId) throws SQLException {
        String sql = "SELECT OrderStatus FROM Orders WITH (UPDLOCK, ROWLOCK) WHERE OrderID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** direction = -1 khi trừ kho (xác nhận đơn), +1 khi hoàn kho (hủy đơn đã xác nhận). */
    private void adjustProductStock(Connection con, int orderId, int direction) throws SQLException {
        String sql = "UPDATE p SET p.Stock = p.Stock + (? * od.Quantity) "
                + "FROM Products p INNER JOIN OrderDetails od ON od.ProductID = p.ProductID "
                + "WHERE od.OrderID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, direction);
            ps.setInt(2, orderId);
            ps.executeUpdate();
        }
    }

    public List<OrderDetail> getDetailsByOrderId(int orderId) {
        // LEFT JOIN Products de lay anh SP (ImageUrl); van hien ten snapshot o OrderDetails
        // neu san pham da bi xoa.
        String sql = "SELECT od.OrderDetailID, od.OrderID, od.ProductID, od.ProductName, "
                + "od.Quantity, od.UnitPrice, od.LineTotal, p.ImageUrl AS ProductImageUrl "
                + "FROM OrderDetails od "
                + "LEFT JOIN Products p ON p.ProductID = od.ProductID "
                + "WHERE od.OrderID = ? ORDER BY od.OrderDetailID";
        List<OrderDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderDetail d = new OrderDetail();
                    d.setOrderDetailId(rs.getInt("OrderDetailID"));
                    d.setOrderId(rs.getInt("OrderID"));
                    d.setProductId(rs.getInt("ProductID"));
                    d.setProductName(rs.getString("ProductName"));
                    d.setQuantity(rs.getInt("Quantity"));
                    d.setUnitPrice(rs.getBigDecimal("UnitPrice"));
                    d.setLineTotal(rs.getBigDecimal("LineTotal"));
                    d.setProductImageUrl(rs.getString("ProductImageUrl"));
                    list.add(d);
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "OrderDAO.getDetailsByOrderId", e);
        }
        return list;
    }

    private boolean executeUpdate(String sql, Object... params) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "OrderDAO.executeUpdate", e);
            return false;
        }
    }
}