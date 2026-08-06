package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.Order;
import com.model.OrderDetail;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Tìm kiếm + lọc đơn hàng theo từ khóa (mã đơn/khách hàng/email/SĐT)
     * và/hoặc khoảng ngày đặt (Orders.CreatedAt) - cả 2 đầu có thể null nếu
     * không lọc. Cùng cách làm với {@code InvoiceDAO.getPagedFiltered}: dùng
     * chung 1 whereClause tham số hóa để vừa an toàn SQL injection vừa tránh
     * phải tự escape ký tự đặc biệt của LIKE.
     *
     * @param fromDate ngày bắt đầu (bao gồm cả ngày này), null = không giới hạn dưới
     * @param toDate   ngày kết thúc (bao gồm cả ngày này), null = không giới hạn trên
     */
    public PaginationHelper.PaginationResult<Order> getPagedFiltered(
            int page, int pageSize, String keyword, LocalDate fromDate, LocalDate toDate) {

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (!trimmedKeyword.isEmpty()) {
            String[] columns = getSearchableColumns();
            String likeParam = "%" + escapeLike(trimmedKeyword) + "%";
            StringBuilder keywordCondition = new StringBuilder("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) keywordCondition.append(" OR ");
                keywordCondition.append(columns[i]).append(" LIKE ? ESCAPE '\\'");
                params.add(likeParam);
            }
            keywordCondition.append(")");
            conditions.add(keywordCondition.toString());
        }
        // Loc theo [fromDate 00:00:00, toDate+1 00:00:00) - vua danh cho kieu
        // DATETIME co gio/phut/giay, vua bao gom tron ven ca ngay toDate.
        if (fromDate != null) {
            conditions.add("o.CreatedAt >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            conditions.add("o.CreatedAt < ?");
            params.add(Timestamp.valueOf(toDate.plusDays(1).atStartOfDay()));
        }

        String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);
        return getPaged(page, pageSize, whereClause, params.toArray());
    }

    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
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

    /** Kết quả chuyển trạng thái đơn - kèm lý do cụ thể khi thất bại (vd thiếu hàng, còn thiếu SP nào) để UI hiển thị đúng thay vì chỉ "thất bại, thử lại". */
    public static final class StatusUpdateResult {
        public final boolean success;
        public final String errorMessage;

        private StatusUpdateResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static StatusUpdateResult ok() { return new StatusUpdateResult(true, null); }
        public static StatusUpdateResult fail(String message) { return new StatusUpdateResult(false, message); }
    }

    /**
     * Xác nhận / chuyển trạng thái đơn: NEW -&gt; CONFIRMED -&gt; SHIPPING -&gt; COMPLETED,
     * hủy (-&gt; CANCELLED) chỉ được phép ở NEW hoặc CONFIRMED (đơn đã giao cho ĐVVC
     * thì không hủy được nữa). Chuyển trạng thái không nằm trong
     * {@link #isValidTransition} bị từ chối ngay, không đụng DB.
     * <p>
     * Kho CHỈ thực sự bị trừ tại thời điểm xác nhận (không trừ lúc khách đặt
     * hàng ở {@link #createOrder}, vì đơn NEW có thể bị hủy) - khi chuyển
     * NEW -> CONFIRMED, trừ theo FEFO từng lô (InventoryBatch) giống nguyên tắc
     * bán hàng tại quầy ({@code trg_InvoiceDetails_CheckStock}), ghi lại
     * OrderDetailBatches để hoàn ĐÚNG lô nếu đơn bị hủy sau đó, và ghi
     * InventoryTransactions để có dấu vết kiểm toán. Nếu không đủ hàng cho
     * bất kỳ dòng nào, KHÔNG trừ dòng nào cả (all-or-nothing) và trả về lý do
     * cụ thể. SHIPPING và COMPLETED không đụng tới kho (đã trừ xong từ bước
     * CONFIRMED). Khi chuyển sang COMPLETED, đơn COD/CASH còn PaymentStatus
     * = PENDING sẽ được đánh dấu PAID (đã thu tiền khi giao hàng xong).
     * Toàn bộ nằm trong 1 transaction để tránh lệch kho nếu có lỗi giữa chừng.
     *
     * @param actorUserId UserID của admin đang thao tác - dùng làm CreatedBy khi ghi InventoryTransactions.
     */
    public StatusUpdateResult updateOrderStatus(int orderId, String newStatus, int actorUserId) {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                String oldStatus = getOrderStatusForUpdate(con, orderId);
                if (oldStatus == null || !isValidTransition(oldStatus, newStatus)) {
                    con.rollback();
                    return StatusUpdateResult.fail("Đơn hàng không ở trạng thái cho phép chuyển đổi này.");
                }

                if ("NEW".equals(oldStatus) && "CONFIRMED".equals(newStatus)) {
                    String insufficient = deductStockFEFO(con, orderId, actorUserId);
                    if (insufficient != null) {
                        con.rollback();
                        return StatusUpdateResult.fail(
                                "Không đủ tồn kho để xác nhận đơn - " + insufficient + ".");
                    }
                } else if ("CONFIRMED".equals(oldStatus) && "CANCELLED".equals(newStatus)) {
                    restoreStockFEFO(con, orderId, actorUserId);
                }

                // Khi hoàn thành đơn COD (tiền mặt khi nhận hàng): coi như đã thu tiền,
                // đồng bộ PaymentStatus = PAID. PayPal đã PAID từ lúc capture, không đụng.
                if ("COMPLETED".equals(newStatus)) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE Orders SET OrderStatus = ?, "
                                    + "PaymentStatus = CASE "
                                    + "WHEN PaymentMethod IN ('COD', 'CASH') AND PaymentStatus = 'PENDING' "
                                    + "THEN 'PAID' ELSE PaymentStatus END "
                                    + "WHERE OrderID = ?")) {
                        ps.setString(1, newStatus);
                        ps.setInt(2, orderId);
                        ps.executeUpdate();
                    }
                } else {
                    try (PreparedStatement ps = con.prepareStatement(
                            "UPDATE Orders SET OrderStatus = ? WHERE OrderID = ?")) {
                        ps.setString(1, newStatus);
                        ps.setInt(2, orderId);
                        ps.executeUpdate();
                    }
                }

                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ORDER));
                return StatusUpdateResult.ok();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.ORDER_STATUS_UPDATE_FAIL, "OrderDAO.updateOrderStatus - " + orderId, e);
            return StatusUpdateResult.fail("Đã xảy ra lỗi hệ thống. Vui lòng thử lại.");
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

    /**
     * Trừ kho theo FEFO (lô hết hạn sớm nhất trước, loại lô đã hết hạn) cho
     * TẤT CẢ dòng OrderDetails của đơn - cùng nguyên tắc với
     * {@code trg_InvoiceDetails_CheckStock} bên bán tại quầy, chỉ khác là
     * viết ở tầng Java vì luồng đổi trạng thái đơn đã tự quản lý transaction
     * riêng (khác trigger INSTEAD OF INSERT của InvoiceDetails).
     * <p>
     * Kiểm tra ĐỦ hàng cho toàn bộ các dòng trước, chỉ trừ khi tất cả các
     * dòng đều đủ (all-or-nothing) - khác với bên POS (cho phép bán ít hơn
     * số yêu cầu nếu thiếu hàng), vì đơn online khách đã chốt đúng số lượng,
     * không thể tự ý giao thiếu.
     *
     * @return null nếu đã trừ thành công tất cả các dòng; ngược lại là chuỗi
     *         mô tả (các) sản phẩm không đủ hàng, kèm số lượng còn khả dụng.
     */
    private String deductStockFEFO(Connection con, int orderId, int actorUserId) throws SQLException {
        List<OrderDetail> lines = getDetailsForUpdate(con, orderId);

        StringBuilder insufficient = new StringBuilder();
        for (OrderDetail line : lines) {
            int available = getAvailableStock(con, line.getProductId());
            if (line.getQuantity() > available) {
                if (insufficient.length() > 0) insufficient.append("; ");
                insufficient.append(line.getProductName()).append(" (cần ")
                        .append(line.getQuantity()).append(", còn ").append(available).append(")");
            }
        }
        if (insufficient.length() > 0) return insufficient.toString();

        for (OrderDetail line : lines) {
            List<int[]> batches = getFefoBatches(con, line.getProductId()); // {BatchID, RemainingQty}
            int remaining = line.getQuantity();

            for (int[] batch : batches) {
                if (remaining <= 0) break;
                int batchId = batch[0];
                int take = Math.min(batch[1], remaining);

                try (PreparedStatement upd = con.prepareStatement(
                        "UPDATE InventoryBatch SET RemainingQty = RemainingQty - ?, "
                                + "Status = CASE WHEN RemainingQty - ? = 0 THEN 'DEPLETED' ELSE Status END "
                                + "WHERE BatchID = ?")) {
                    upd.setInt(1, take);
                    upd.setInt(2, take);
                    upd.setInt(3, batchId);
                    upd.executeUpdate();
                }

                try (PreparedStatement ins = con.prepareStatement(
                        "INSERT INTO OrderDetailBatches (OrderDetailID, BatchID, Quantity) VALUES (?, ?, ?)")) {
                    ins.setInt(1, line.getOrderDetailId());
                    ins.setInt(2, batchId);
                    ins.setInt(3, take);
                    ins.executeUpdate();
                }

                remaining -= take;
            }

            int stockBefore = getProductStock(con, line.getProductId());
            try (PreparedStatement upd = con.prepareStatement(
                    "UPDATE Products SET Stock = Stock - ? WHERE ProductID = ?")) {
                upd.setInt(1, line.getQuantity());
                upd.setInt(2, line.getProductId());
                upd.executeUpdate();
            }

            insertInventoryTransaction(con, line.getProductId(), "SALE", "OUT", line.getQuantity(),
                    stockBefore, stockBefore - line.getQuantity(), orderId, actorUserId);
        }
        return null;
    }

    /**
     * Hoàn kho khi đơn ĐÃ CONFIRMED bị hủy - hoàn ĐÚNG lô đã trừ lúc xác
     * nhận (đọc lại từ OrderDetailBatches, giống cách
     * {@code trg_Invoices_CancelSameDayOnly} hoàn lô khi hủy hóa đơn tại
     * quầy) thay vì cộng thẳng vào Products.Stock chung chung.
     */
    private void restoreStockFEFO(Connection con, int orderId, int actorUserId) throws SQLException {
        String sql = "SELECT odb.BatchID, odb.Quantity, od.ProductID, od.ProductName "
                + "FROM OrderDetailBatches odb "
                + "JOIN OrderDetails od ON od.OrderDetailID = odb.OrderDetailID "
                + "WHERE od.OrderID = ?";

        List<int[]> batchRestores = new ArrayList<>(); // {BatchID, Quantity}
        // productId -> tong so luong hoan (gop nhieu lo cung 1 SP)
        java.util.LinkedHashMap<Integer, Integer> perProduct = new java.util.LinkedHashMap<>();

        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int batchId = rs.getInt("BatchID");
                    int qty = rs.getInt("Quantity");
                    int productId = rs.getInt("ProductID");
                    batchRestores.add(new int[]{batchId, qty});
                    perProduct.merge(productId, qty, Integer::sum);
                }
            }
        }

        for (int[] restore : batchRestores) {
            try (PreparedStatement upd = con.prepareStatement(
                    "UPDATE InventoryBatch SET RemainingQty = RemainingQty + ?, "
                            + "Status = CASE WHEN Status = 'DEPLETED' THEN 'ACTIVE' ELSE Status END "
                            + "WHERE BatchID = ?")) {
                upd.setInt(1, restore[1]);
                upd.setInt(2, restore[0]);
                upd.executeUpdate();
            }
        }

        for (var entry : perProduct.entrySet()) {
            int productId = entry.getKey();
            int qty = entry.getValue();
            int stockBefore = getProductStock(con, productId);

            try (PreparedStatement upd = con.prepareStatement(
                    "UPDATE Products SET Stock = Stock + ? WHERE ProductID = ?")) {
                upd.setInt(1, qty);
                upd.setInt(2, productId);
                upd.executeUpdate();
            }

            insertInventoryTransaction(con, productId, "SALE_CANCEL", "IN", qty,
                    stockBefore, stockBefore + qty, orderId, actorUserId);
        }
    }

    /** Đọc OrderDetails của đơn, khóa dòng Products tương ứng (UPDLOCK) để tránh 2 giao dịch cùng trừ 1 sản phẩm chồng lấn. */
    private List<OrderDetail> getDetailsForUpdate(Connection con, int orderId) throws SQLException {
        List<OrderDetail> list = new ArrayList<>();
        String sql = "SELECT od.OrderDetailID, od.ProductID, od.ProductName, od.Quantity "
                + "FROM OrderDetails od "
                + "JOIN Products p WITH (UPDLOCK, ROWLOCK) ON p.ProductID = od.ProductID "
                + "WHERE od.OrderID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    OrderDetail d = new OrderDetail();
                    d.setOrderDetailId(rs.getInt("OrderDetailID"));
                    d.setProductId(rs.getInt("ProductID"));
                    d.setProductName(rs.getString("ProductName"));
                    d.setQuantity(rs.getInt("Quantity"));
                    list.add(d);
                }
            }
        }
        return list;
    }

    /** Tồn khả dụng = tổng RemainingQty các lô ACTIVE, chưa hết hạn (khớp điều kiện lọc của getFefoBatches). */
    private int getAvailableStock(Connection con, int productId) throws SQLException {
        String sql = "SELECT ISNULL(SUM(RemainingQty), 0) FROM InventoryBatch "
                + "WHERE ProductID = ? AND Status = 'ACTIVE' "
                + "AND (ExpiryDate IS NULL OR ExpiryDate >= CAST(GETDATE() AS DATE))";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Danh sách lô còn hàng theo thứ tự FEFO (hết hạn sớm nhất trước, lô không HSD xếp sau cùng), loại lô đã hết hạn. */
    private List<int[]> getFefoBatches(Connection con, int productId) throws SQLException {
        List<int[]> batches = new ArrayList<>();
        String sql = "SELECT BatchID, RemainingQty FROM InventoryBatch WITH (UPDLOCK, ROWLOCK) "
                + "WHERE ProductID = ? AND Status = 'ACTIVE' AND RemainingQty > 0 "
                + "AND (ExpiryDate IS NULL OR ExpiryDate >= CAST(GETDATE() AS DATE)) "
                + "ORDER BY ISNULL(ExpiryDate, '9999-12-31'), BatchID";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    batches.add(new int[]{rs.getInt("BatchID"), rs.getInt("RemainingQty")});
                }
            }
        }
        return batches;
    }

    private int getProductStock(Connection con, int productId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT Stock FROM Products WHERE ProductID = ?")) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** RefTable luôn là 'Orders' - dùng lại TransactionType SALE/SALE_CANCEL sẵn có (CHECK constraint InventoryTransactions) để báo cáo tồn kho không cần đổi gì thêm. */
    private void insertInventoryTransaction(Connection con, int productId, String type, String direction,
            int quantity, int stockBefore, int stockAfter, int orderId, int actorUserId) throws SQLException {
        String sql = "INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity, "
                + "StockBefore, StockAfter, RefTable, RefID, CreatedBy) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'Orders', ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            ps.setString(2, type);
            ps.setString(3, direction);
            ps.setInt(4, quantity);
            ps.setInt(5, stockBefore);
            ps.setInt(6, stockAfter);
            ps.setInt(7, orderId);
            ps.setInt(8, actorUserId);
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