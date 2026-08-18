package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.SupplierReturn;
import com.model.SupplierReturnDetail;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SupplierReturnDAO extends BaseDAO<SupplierReturn> {
    private static final String BASE_TABLE =
            "SupplierReturns r "
                    + "JOIN Suppliers s ON r.SupplierID = s.SupplierID "
                    + "JOIN Users u ON r.CreatedBy = u.UserID";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return BASE_TABLE;
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "r.SupplierReturnID, r.SupplierReturnCode, r.SupplierID, s.SupplierName, "
                + "r.Reason, r.Status, r.TotalRefundAmount, r.Note, "
                + "r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt, "
                + "(SELECT COUNT(*) FROM SupplierReturnDetails x WHERE x.SupplierReturnID = r.SupplierReturnID) AS ItemCount";
    }

    @Override
    protected String getOrderBy() {
        return "r.CreatedAt DESC, r.SupplierReturnID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{
                "r.SupplierReturnCode",
                "s.SupplierName",
                "r.Reason",
                "u.FullName",
                "r.Note",
                // Cho phép tìm phiếu trả NCC theo thông tin sản phẩm/lô trong chi tiết phiếu.
                "EXISTS (SELECT 1 FROM SupplierReturnDetails rd1 JOIN Products p1 ON p1.ProductID = rd1.ProductID " +
                        "WHERE rd1.SupplierReturnID = r.SupplierReturnID AND p1.ProductName LIKE ? ESCAPE '!')",
                "EXISTS (SELECT 1 FROM SupplierReturnDetails rd2 JOIN Products p2 ON p2.ProductID = rd2.ProductID " +
                        "WHERE rd2.SupplierReturnID = r.SupplierReturnID AND p2.ProductCode LIKE ? ESCAPE '!')",
                "EXISTS (SELECT 1 FROM SupplierReturnDetails rd3 JOIN InventoryBatch b3 ON b3.BatchID = rd3.BatchID " +
                        "WHERE rd3.SupplierReturnID = r.SupplierReturnID AND b3.BatchCode LIKE ? ESCAPE '!')",
                "EXISTS (SELECT 1 FROM SupplierReturnDetails rd4 JOIN InventoryBatch b4 ON b4.BatchID = rd4.BatchID " +
                        "WHERE rd4.SupplierReturnID = r.SupplierReturnID AND b4.LotNumber LIKE ? ESCAPE '!')"
        };
    }

    @Override
    protected SupplierReturn mapResultSet(ResultSet rs) throws SQLException {
        SupplierReturn r = new SupplierReturn();
        r.setSupplierReturnId(rs.getInt("SupplierReturnID"));
        r.setSupplierReturnCode(rs.getString("SupplierReturnCode"));
        r.setSupplierId(rs.getInt("SupplierID"));
        r.setSupplierName(rs.getString("SupplierName"));
        r.setReason(rs.getString("Reason"));
        r.setStatus(rs.getString("Status"));
        r.setTotalRefundAmount(rs.getBigDecimal("TotalRefundAmount"));
        r.setNote(rs.getString("Note"));
        r.setCreatedBy(rs.getInt("CreatedBy"));
        r.setCreatedByName(rs.getString("CreatedByName"));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        r.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        r.setItemCount(rs.getInt("ItemCount"));
        return r;
    }

    public int createSupplierReturn(int supplierId, String reason, String note, int createdByUserId,
                                     List<SupplierReturnDetail> details) {
        if (details == null || details.isEmpty()) return -1;
        if (reason == null || reason.isBlank()) return -1;
        if (supplierId <= 0) return -1;
        BigDecimal totalRefund = BigDecimal.ZERO;
        for (SupplierReturnDetail det : details) {
            if (det.getQuantity() <= 0 || det.getBatchId() <= 0) return -1;
            if (det.getUnitRefundPrice() == null || det.getUnitRefundPrice().signum() < 0) return -1;
            totalRefund = totalRefund.add(det.getUnitRefundPrice().multiply(BigDecimal.valueOf(det.getQuantity())));
        }
        String insertHeader = "INSERT INTO SupplierReturns (SupplierID, Reason, Status, TotalRefundAmount, Note, CreatedBy) "
                + "VALUES (?, ?, 'COMPLETED', ?, ?, ?)";
        String insertDetail = "INSERT INTO SupplierReturnDetails "
                + "(SupplierReturnID, ProductID, BatchID, Quantity, UnitRefundPrice) VALUES (?, ?, ?, ?, ?)";
        String selectBatch = "SELECT ProductID, SupplierID, RemainingQty, ImportPrice FROM InventoryBatch "
                + "WHERE BatchID = ?";
        String updateBatch = "UPDATE InventoryBatch SET RemainingQty = RemainingQty - ?, "
                + "Status = CASE WHEN RemainingQty - ? <= 0 THEN 'DEPLETED' ELSE Status END "
                + "WHERE BatchID = ? AND RemainingQty >= ?";
        String updateProduct = "UPDATE Products SET Stock = Stock - ? WHERE ProductID = ? AND Stock >= ?";
        String selectStock = "SELECT Stock FROM Products WHERE ProductID = ?";
        String insertTx = "INSERT INTO InventoryTransactions "
                + "(ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter, "
                + "RefTable, RefID, CreatedBy, Note) VALUES (?, 'SUPPLIER_RETURN', 'OUT', ?, ?, ?, 'SupplierReturns', ?, ?, ?)";
        String updateSupplierDebt = "UPDATE Suppliers SET DebtBalance = DebtBalance + ? WHERE SupplierID = ?";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int returnId;
                try (PreparedStatement ps = con.prepareStatement(insertHeader, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, supplierId);
                    ps.setString(2, reason.toUpperCase());
                    ps.setBigDecimal(3, totalRefund);
                    if (note != null && !note.isBlank()) {
                        ps.setString(4, note.trim());
                    } else {
                        ps.setNull(4, Types.VARCHAR);
                    }
                    ps.setInt(5, createdByUserId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No SupplierReturnID");
                        returnId = keys.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE SupplierReturns SET SupplierReturnCode = ? WHERE SupplierReturnID = ?")) {
                    ps.setString(1, "TRNC_" + String.format("%06d", returnId));
                    ps.setInt(2, returnId);
                    ps.executeUpdate();
                }
                for (SupplierReturnDetail det : details) {
                    int productId;
                    int batchSupplierId;
                    int remaining;
                    BigDecimal importPrice;
                    try (PreparedStatement ps = con.prepareStatement(selectBatch)) {
                        ps.setInt(1, det.getBatchId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Batch not found: " + det.getBatchId());
                            productId = rs.getInt("ProductID");
                            batchSupplierId = rs.getInt("SupplierID");
                            remaining = rs.getInt("RemainingQty");
                            importPrice = rs.getBigDecimal("ImportPrice");
                        }
                    }
                    if (batchSupplierId != supplierId) {
                        throw new SQLException("Lô BatchID=" + det.getBatchId() + " không thuộc NCC đã chọn");
                    }
                    if (det.getQuantity() > remaining) {
                        throw new SQLException("SL trả vượt tồn lô BatchID=" + det.getBatchId()
                                + " (còn " + remaining + ")");
                    }
                    if (det.getUnitRefundPrice() == null) {
                        det.setUnitRefundPrice(importPrice);
                    }
                    det.setProductId(productId);
                    try (PreparedStatement ps = con.prepareStatement(insertDetail)) {
                        ps.setInt(1, returnId);
                        ps.setInt(2, productId);
                        ps.setInt(3, det.getBatchId());
                        ps.setInt(4, det.getQuantity());
                        ps.setBigDecimal(5, det.getUnitRefundPrice());
                        ps.executeUpdate();
                    }
                    try (PreparedStatement ps = con.prepareStatement(updateBatch)) {
                        ps.setInt(1, det.getQuantity());
                        ps.setInt(2, det.getQuantity());
                        ps.setInt(3, det.getBatchId());
                        ps.setInt(4, det.getQuantity());
                        int updated = ps.executeUpdate();
                        if (updated == 0) {
                            throw new SQLException("Không trừ được lô BatchID=" + det.getBatchId());
                        }
                    }
                    int stockBefore;
                    try (PreparedStatement ps = con.prepareStatement(selectStock)) {
                        ps.setInt(1, productId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Product not found: " + productId);
                            stockBefore = rs.getInt(1);
                        }
                    }
                    if (stockBefore < det.getQuantity()) {
                        throw new SQLException("Tồn SP không đủ ProductID=" + productId);
                    }
                    try (PreparedStatement ps = con.prepareStatement(updateProduct)) {
                        ps.setInt(1, det.getQuantity());
                        ps.setInt(2, productId);
                        ps.setInt(3, det.getQuantity());
                        ps.executeUpdate();
                    }
                    String txNote = "Trả NCC " + reason + " TRNC_" + String.format("%06d", returnId);
                    try (PreparedStatement ps = con.prepareStatement(insertTx)) {
                        ps.setInt(1, productId);
                        ps.setInt(2, det.getQuantity());
                        ps.setInt(3, stockBefore);
                        ps.setInt(4, stockBefore - det.getQuantity());
                        ps.setInt(5, returnId);
                        ps.setInt(6, createdByUserId);
                        ps.setString(7, txNote);
                        ps.executeUpdate();
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(updateSupplierDebt)) {
                    ps.setBigDecimal(1, totalRefund);
                    ps.setInt(2, supplierId);
                    ps.executeUpdate();
                }
                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.SUPPLIER_RETURN));
                return returnId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "SupplierReturnDAO.createSupplierReturn supplierId=" + supplierId, e);
            return -1;
        }
    }

    public List<SupplierReturnDetail> getDetails(int supplierReturnId) {
        String sql = "SELECT d.SupplierReturnDetailID, d.SupplierReturnID, d.ProductID, p.ProductName, p.ProductCode, "
                + "d.BatchID, b.BatchCode, b.LotNumber, pr.ReceiptCode, "
                + "d.Quantity, d.UnitRefundPrice, d.LineRefundAmount, b.ExpiryDate "
                + "FROM SupplierReturnDetails d "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "JOIN InventoryBatch b ON b.BatchID = d.BatchID "
                + "LEFT JOIN PurchaseReceiptDetails prd ON prd.ReceiptDetailID = b.ReceiptDetailID "
                + "LEFT JOIN PurchaseReceipts pr ON pr.ReceiptID = prd.ReceiptID "
                + "WHERE d.SupplierReturnID = ? ORDER BY d.SupplierReturnDetailID";
        List<SupplierReturnDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierReturnId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SupplierReturnDetail d = new SupplierReturnDetail();
                    d.setSupplierReturnDetailId(rs.getInt("SupplierReturnDetailID"));
                    d.setSupplierReturnId(rs.getInt("SupplierReturnID"));
                    d.setProductId(rs.getInt("ProductID"));
                    d.setProductName(rs.getString("ProductName"));
                    d.setProductCode(rs.getString("ProductCode"));
                    d.setBatchId(rs.getInt("BatchID"));
                    d.setBatchCode(rs.getString("BatchCode"));
                    d.setLotNumber(rs.getString("LotNumber"));
                    d.setReceiptCode(rs.getString("ReceiptCode"));
                    d.setQuantity(rs.getInt("Quantity"));
                    d.setUnitRefundPrice(rs.getBigDecimal("UnitRefundPrice"));
                    d.setLineRefundAmount(rs.getBigDecimal("LineRefundAmount"));
                    Date exp = rs.getDate("ExpiryDate");
                    d.setExpiryDate(exp != null ? exp.toLocalDate() : null);
                    list.add(d);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "SupplierReturnDAO.getDetails id=" + supplierReturnId, e);
        }
        return list;
    }

    public List<ReturnableBatch> listReturnableBatches(Integer supplierId) {
        StringBuilder sql = new StringBuilder(
                "SELECT b.BatchID, b.BatchCode, b.LotNumber, b.ProductID, p.ProductName, p.ProductCode, "
                        + "b.SupplierID, s.SupplierName, pr.ReceiptCode, b.ExpiryDate, b.ImportPrice, "
                        + "b.Quantity, b.RemainingQty "
                        + "FROM InventoryBatch b "
                        + "JOIN Products p ON p.ProductID = b.ProductID "
                        + "JOIN Suppliers s ON s.SupplierID = b.SupplierID "
                        + "LEFT JOIN PurchaseReceiptDetails prd ON prd.ReceiptDetailID = b.ReceiptDetailID "
                        + "LEFT JOIN PurchaseReceipts pr ON pr.ReceiptID = prd.ReceiptID "
                        + "WHERE b.RemainingQty > 0 ");
        if (supplierId != null) sql.append("AND b.SupplierID = ? ");
        sql.append("ORDER BY CASE WHEN b.ExpiryDate IS NULL THEN 1 ELSE 0 END, b.ExpiryDate ASC, b.BatchID ASC");
        List<ReturnableBatch> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            if (supplierId != null) ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ReturnableBatch b = new ReturnableBatch();
                    b.batchId = rs.getInt("BatchID");
                    b.batchCode = rs.getString("BatchCode");
                    b.lotNumber = rs.getString("LotNumber");
                    b.productId = rs.getInt("ProductID");
                    b.productName = rs.getString("ProductName");
                    b.productCode = rs.getString("ProductCode");
                    b.supplierId = rs.getInt("SupplierID");
                    b.supplierName = rs.getString("SupplierName");
                    b.receiptCode = rs.getString("ReceiptCode");
                    Date exp = rs.getDate("ExpiryDate");
                    b.expiryDate = exp != null ? exp.toLocalDate() : null;
                    b.importPrice = rs.getBigDecimal("ImportPrice");
                    b.quantity = rs.getInt("Quantity");
                    b.remainingQty = rs.getInt("RemainingQty");
                    list.add(b);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierReturnDAO.listReturnableBatches", e);
        }
        return list;
    }

    public BigDecimal sumRefundBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(TotalRefundAmount), 0) FROM SupplierReturns "
                + "WHERE Status = 'COMPLETED' "
                + "AND CAST(CreatedAt AS DATE) BETWEEN ? AND ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierReturnDAO.sumRefundBetween", e);
            return BigDecimal.ZERO;
        }
    }

    public BigDecimal sumRefundThisMonth() {
        LocalDate now = LocalDate.now();
        return sumRefundBetween(now.withDayOfMonth(1), now);
    }

    public Map<String, BigDecimal> sumRefundByReason(LocalDate from, LocalDate to) {
        String sql = "SELECT Reason, COALESCE(SUM(TotalRefundAmount), 0) AS Refund "
                + "FROM SupplierReturns WHERE Status = 'COMPLETED' "
                + "AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY Reason";
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("Reason"), rs.getBigDecimal("Refund"));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierReturnDAO.sumRefundByReason", e);
        }
        return map;
    }

    public BigDecimal getSupplierDebt(int supplierId) {
        String sql = "SELECT DebtBalance FROM Suppliers WHERE SupplierID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierReturnDAO.getSupplierDebt", e);
            return BigDecimal.ZERO;
        }
    }

    public static final class ReturnableBatch {
        public int batchId;
        public String batchCode;
        public String lotNumber;
        public int productId;
        public String productName;
        public String productCode;
        public int supplierId;
        public String supplierName;
        public String receiptCode;
        public LocalDate expiryDate;
        public BigDecimal importPrice;
        public int quantity;
        public int remainingQty;
    }

    // ================================================================
    // ====== THÊM MỚI: 3 METHOD LỌC THEO NCC + NGÀY (KHÔNG ĐỤNG CŨ) ======
    // ================================================================

    /**
     * Lấy phân trang có lọc theo NCC + khoảng ngày.
     * supplierId = null → không lọc NCC (tất cả).
     * from/to = null → không lọc ngày tương ứng.
     */
    public PaginationHelper.PaginationResult<SupplierReturn> getPagedFiltered(
            int page, int pageSize, Integer supplierId, LocalDate from, LocalDate to) {
        FilterContext ctx = buildWhereAndParams(null, supplierId, from, to);
        return getPaged(page, pageSize, ctx.where, ctx.params);
    }

    /**
     * Tìm kiếm text + đồng thời lọc theo NCC + khoảng ngày.
     * (Từ khóa OR trên các cột searchable) AND (điều kiện lọc NCC) AND (điều kiện lọc ngày).
     */
    public PaginationHelper.PaginationResult<SupplierReturn> searchFiltered(
            String keyword, int page, int pageSize, Integer supplierId, LocalDate from, LocalDate to) {
        String[] columns = getSearchableColumns();
        if (keyword == null || keyword.trim().isEmpty() || columns.length == 0) {
            return getPagedFiltered(page, pageSize, supplierId, from, to);
        }
        FilterContext ctx = buildWhereAndParams(keyword, supplierId, from, to);
        return getPaged(page, pageSize, ctx.where, ctx.params);
    }

    /**
     * Lấy tất cả (không phân trang) có lọc NCC + ngày — dùng cho xuất CSV/Excel.
     */
    public List<SupplierReturn> getAllFiltered(Integer supplierId, LocalDate from, LocalDate to) {
        FilterContext ctx = buildWhereAndParams(null, supplierId, from, to);
        if (ctx.where == null || ctx.where.isEmpty()) {
            return getAll();
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(getColumns()).append(" FROM ").append(getTableName());
        String jc = getJoinClause();
        if (jc != null && !jc.isEmpty()) sql.append(" ").append(jc);
        sql.append(" WHERE ").append(ctx.where).append(" ORDER BY ").append(getOrderBy());
        List<SupplierReturn> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            for (Object p : ctx.params) ps.setObject(i++, p);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "SupplierReturnDAO.getAllFiltered", e);
        }
        return list;
    }

    /**
     * Build whereClause + mảng params cho BaseDAO.getPaged(page, size, where, Object...).
     * Kết hợp: (search OR..) AND (supplierId=?) AND (CreatedAt >= ?) AND (CreatedAt <= ?)
     * — dùng PreparedStatement params → an toàn SQL injection.
     */
    private FilterContext buildWhereAndParams(String keyword, Integer supplierId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();

        // 1) Từ khóa tìm kiếm (OR trên các cột searchable)
        String[] columns = getSearchableColumns();
        if (keyword != null && !keyword.trim().isEmpty() && columns.length > 0) {
            String escaped = keyword.trim()
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_");
            String likeValue = "%" + escaped + "%";
            StringBuilder or = new StringBuilder("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) or.append(" OR ");
                String column = columns[i];
                // Một số cột là predicate EXISTS hoàn chỉnh để tìm trong chi tiết phiếu.
                // Các cột thông thường mới cần nối thêm LIKE ở đây.
                if (column.trim().startsWith("EXISTS (")) {
                    or.append(column);
                    params.add(likeValue);
                } else {
                    or.append(column).append(" LIKE ? ESCAPE '!' ");
                    params.add(likeValue);
                }
            }
            or.append(")");
            conds.add(or.toString());
        }

        // 2) Lọc theo NCC
        if (supplierId != null) {
            conds.add("r.SupplierID = ?");
            params.add(supplierId);
        }

        // 3) Lọc theo Từ ngày (CreatedAt >= from 00:00:00)
        if (from != null) {
            conds.add("r.CreatedAt >= ?");
            params.add(Timestamp.valueOf(LocalDateTime.of(from, LocalTime.MIN)));
        }

        // 4) Lọc theo Đến ngày (CreatedAt <= to 23:59:59.999)
        if (to != null) {
            conds.add("r.CreatedAt <= ?");
            params.add(Timestamp.valueOf(LocalDateTime.of(to, LocalTime.MAX)));
        }

        FilterContext ctx = new FilterContext();
        if (conds.isEmpty()) {
            ctx.where = null;
            ctx.params = new Object[0];
        } else {
            ctx.where = String.join(" AND ", conds);
            ctx.params = params.toArray();
        }
        return ctx;
    }

    private static final class FilterContext {
        String where;
        Object[] params;
    }
}