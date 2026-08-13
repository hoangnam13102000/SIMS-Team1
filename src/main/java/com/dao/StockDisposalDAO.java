package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.InventoryBatch;
import com.model.StockDisposal;
import com.model.StockDisposalDetail;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DAO phieu tieu huy hang.
 * createDisposal: 1 transaction - insert phieu + dong, tru lo, tru Stock, ghi InventoryTransactions DISPOSAL.
 */
public class StockDisposalDAO extends BaseDAO<StockDisposal> {

    private static final String BASE_TABLE =
            "StockDisposals d "
                    + "JOIN Users u ON d.CreatedBy = u.UserID";

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
        return "d.DisposalID, d.DisposalCode, d.Reason, d.Status, d.TotalLossAmount, d.Note, "
                + "d.CreatedBy, u.FullName AS CreatedByName, d.CreatedAt, "
                + "(SELECT COUNT(*) FROM StockDisposalDetails x WHERE x.DisposalID = d.DisposalID) AS ItemCount";
    }

    @Override
    protected String getOrderBy() {
        return "d.CreatedAt DESC, d.DisposalID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"d.DisposalCode", "d.Reason", "u.FullName", "d.Note"};
    }

    @Override
    protected StockDisposal mapResultSet(ResultSet rs) throws SQLException {
        StockDisposal d = new StockDisposal();
        d.setDisposalId(rs.getInt("DisposalID"));
        d.setDisposalCode(rs.getString("DisposalCode"));
        d.setReason(rs.getString("Reason"));
        d.setStatus(rs.getString("Status"));
        d.setTotalLossAmount(rs.getBigDecimal("TotalLossAmount"));
        d.setNote(rs.getString("Note"));
        d.setCreatedBy(rs.getInt("CreatedBy"));
        d.setCreatedByName(rs.getString("CreatedByName"));
        Timestamp ts = rs.getTimestamp("CreatedAt");
        d.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
        d.setItemCount(rs.getInt("ItemCount"));
        return d;
    }

    /**
     * Lap phieu tieu huy nhieu dong trong 1 transaction.
     * @return DisposalID neu OK, -1 neu that bai
     */
    public int createDisposal(String reason, String note, int createdByUserId,
                              List<StockDisposalDetail> details) {
        if (details == null || details.isEmpty()) return -1;
        if (reason == null || reason.isBlank()) return -1;

        BigDecimal totalLoss = BigDecimal.ZERO;
        for (StockDisposalDetail det : details) {
            if (det.getQuantity() <= 0 || det.getBatchId() <= 0) return -1;
            if (det.getUnitCost() == null || det.getUnitCost().signum() < 0) return -1;
            totalLoss = totalLoss.add(det.getUnitCost().multiply(BigDecimal.valueOf(det.getQuantity())));
        }

        String insertHeader = "INSERT INTO StockDisposals (Reason, Status, TotalLossAmount, Note, CreatedBy) "
                + "VALUES (?, 'COMPLETED', ?, ?, ?)";
        String insertDetail = "INSERT INTO StockDisposalDetails "
                + "(DisposalID, ProductID, BatchID, Quantity, UnitCost) VALUES (?, ?, ?, ?, ?)";
        String selectBatch = "SELECT ProductID, RemainingQty, ImportPrice, Status FROM InventoryBatch "
                + "WHERE BatchID = ?";
        String updateBatch = "UPDATE InventoryBatch SET RemainingQty = RemainingQty - ?, "
                + "Status = CASE WHEN RemainingQty - ? <= 0 THEN 'DEPLETED' ELSE Status END "
                + "WHERE BatchID = ? AND RemainingQty >= ?";
        String updateProduct = "UPDATE Products SET Stock = Stock - ? WHERE ProductID = ? AND Stock >= ?";
        String selectStock = "SELECT Stock FROM Products WHERE ProductID = ?";
        String insertTx = "INSERT INTO InventoryTransactions "
                + "(ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter, "
                + "RefTable, RefID, CreatedBy, Note) VALUES (?, 'DISPOSAL', 'OUT', ?, ?, ?, 'StockDisposals', ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int disposalId;
                try (PreparedStatement ps = con.prepareStatement(insertHeader, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, reason.toUpperCase());
                    ps.setBigDecimal(2, totalLoss);
                    if (note != null && !note.isBlank()) {
                        ps.setString(3, note.trim());
                    } else {
                        ps.setNull(3, Types.NVARCHAR);
                    }
                    ps.setInt(4, createdByUserId);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("No DisposalID");
                        disposalId = keys.getInt(1);
                    }
                }

                for (StockDisposalDetail det : details) {
                    int productId;
                    int remaining;
                    BigDecimal importPrice;
                    try (PreparedStatement ps = con.prepareStatement(selectBatch)) {
                        ps.setInt(1, det.getBatchId());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new SQLException("Batch not found: " + det.getBatchId());
                            productId = rs.getInt("ProductID");
                            remaining = rs.getInt("RemainingQty");
                            importPrice = rs.getBigDecimal("ImportPrice");
                        }
                    }
                    if (det.getQuantity() > remaining) {
                        throw new SQLException("SL huy vuot ton lo BatchID=" + det.getBatchId()
                                + " (con " + remaining + ")");
                    }
                    // Snapshot gia nhap lo neu UI khong truyen
                    if (det.getUnitCost() == null) {
                        det.setUnitCost(importPrice);
                    }
                    det.setProductId(productId);

                    try (PreparedStatement ps = con.prepareStatement(insertDetail)) {
                        ps.setInt(1, disposalId);
                        ps.setInt(2, productId);
                        ps.setInt(3, det.getBatchId());
                        ps.setInt(4, det.getQuantity());
                        ps.setBigDecimal(5, det.getUnitCost());
                        ps.executeUpdate();
                    }

                    try (PreparedStatement ps = con.prepareStatement(updateBatch)) {
                        ps.setInt(1, det.getQuantity());
                        ps.setInt(2, det.getQuantity());
                        ps.setInt(3, det.getBatchId());
                        ps.setInt(4, det.getQuantity());
                        int updated = ps.executeUpdate();
                        if (updated == 0) {
                            throw new SQLException("Khong tru duoc lo BatchID=" + det.getBatchId());
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
                        throw new SQLException("Ton SP khong du ProductID=" + productId);
                    }
                    try (PreparedStatement ps = con.prepareStatement(updateProduct)) {
                        ps.setInt(1, det.getQuantity());
                        ps.setInt(2, productId);
                        ps.setInt(3, det.getQuantity());
                        ps.executeUpdate();
                    }

                    String txNote = "Tieu huy " + reason + " TH_" + String.format("%06d", disposalId);
                    try (PreparedStatement ps = con.prepareStatement(insertTx)) {
                        ps.setInt(1, productId);
                        ps.setInt(2, det.getQuantity());
                        ps.setInt(3, stockBefore);
                        ps.setInt(4, stockBefore - det.getQuantity());
                        ps.setInt(5, disposalId);
                        ps.setInt(6, createdByUserId);
                        ps.setString(7, txNote);
                        ps.executeUpdate();
                    }
                }

                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.DISPOSAL));
                return disposalId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "StockDisposalDAO.createDisposal reason=" + reason, e);
            return -1;
        }
    }

    public List<StockDisposalDetail> getDetails(int disposalId) {
        String sql = "SELECT dd.DisposalDetailID, dd.DisposalID, dd.ProductID, p.ProductName, p.ProductCode, "
                + "dd.BatchID, b.BatchCode, dd.Quantity, dd.UnitCost, dd.LineLossAmount, b.ExpiryDate "
                + "FROM StockDisposalDetails dd "
                + "JOIN Products p ON p.ProductID = dd.ProductID "
                + "JOIN InventoryBatch b ON b.BatchID = dd.BatchID "
                + "WHERE dd.DisposalID = ? ORDER BY dd.DisposalDetailID";
        List<StockDisposalDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, disposalId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    StockDisposalDetail d = new StockDisposalDetail();
                    d.setDisposalDetailId(rs.getInt("DisposalDetailID"));
                    d.setDisposalId(rs.getInt("DisposalID"));
                    d.setProductId(rs.getInt("ProductID"));
                    d.setProductName(rs.getString("ProductName"));
                    d.setProductCode(rs.getString("ProductCode"));
                    d.setBatchId(rs.getInt("BatchID"));
                    d.setBatchCode(rs.getString("BatchCode"));
                    d.setQuantity(rs.getInt("Quantity"));
                    d.setUnitCost(rs.getBigDecimal("UnitCost"));
                    d.setLineLossAmount(rs.getBigDecimal("LineLossAmount"));
                    Date exp = rs.getDate("ExpiryDate");
                    d.setExpiryDate(exp != null ? exp.toLocalDate() : null);
                    list.add(d);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StockDisposalDAO.getDetails id=" + disposalId, e);
        }
        return list;
    }

    /** Lo con hang de chon khi lap phieu tieu huy (het han / sap het han uu tien). */
    public List<InventoryBatch> listDisposableBatches() {
        String sql = "SELECT b.BatchID, b.BatchCode, b.LotNumber, b.ProductID, p.ProductName, p.ProductCode, "
                + "b.SupplierID, s.SupplierName, b.ManufactureDate, b.ExpiryDate, b.ImportDate, "
                + "b.ImportPrice, b.Quantity, b.RemainingQty, b.Status "
                + "FROM InventoryBatch b "
                + "JOIN Products p ON p.ProductID = b.ProductID "
                + "JOIN Suppliers s ON s.SupplierID = b.SupplierID "
                + "WHERE b.RemainingQty > 0 "
                + "ORDER BY CASE WHEN b.ExpiryDate IS NULL THEN 1 ELSE 0 END, "
                + "b.ExpiryDate ASC, b.BatchID ASC";
        List<InventoryBatch> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                InventoryBatch b = new InventoryBatch();
                b.setBatchId(rs.getInt("BatchID"));
                b.setBatchCode(rs.getString("BatchCode"));
                b.setLotNumber(rs.getString("LotNumber"));
                b.setProductId(rs.getInt("ProductID"));
                b.setProductName(rs.getString("ProductName"));
                b.setProductCode(rs.getString("ProductCode"));
                b.setSupplierId(rs.getInt("SupplierID"));
                b.setSupplierName(rs.getString("SupplierName"));
                Date mfg = rs.getDate("ManufactureDate");
                b.setManufactureDate(mfg != null ? mfg.toLocalDate() : null);
                Date exp = rs.getDate("ExpiryDate");
                b.setExpiryDate(exp != null ? exp.toLocalDate() : null);
                Timestamp imp = rs.getTimestamp("ImportDate");
                b.setImportDate(imp != null ? imp.toLocalDateTime() : null);
                b.setImportPrice(rs.getBigDecimal("ImportPrice"));
                b.setQuantity(rs.getInt("Quantity"));
                b.setRemainingQty(rs.getInt("RemainingQty"));
                b.setStatus(rs.getString("Status"));
                list.add(b);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockDisposalDAO.listDisposableBatches", e);
        }
        return list;
    }

    public BigDecimal sumLossBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT ISNULL(SUM(TotalLossAmount), 0) FROM StockDisposals "
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
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockDisposalDAO.sumLossBetween", e);
            return BigDecimal.ZERO;
        }
    }

    /** Map reason -> tong ton that trong ky. */
    public Map<String, BigDecimal> sumLossByReason(LocalDate from, LocalDate to) {
        String sql = "SELECT Reason, ISNULL(SUM(TotalLossAmount), 0) AS Loss "
                + "FROM StockDisposals WHERE Status = 'COMPLETED' "
                + "AND CAST(CreatedAt AS DATE) BETWEEN ? AND ? "
                + "GROUP BY Reason";
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(from));
            ps.setDate(2, Date.valueOf(to));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("Reason"), rs.getBigDecimal("Loss"));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockDisposalDAO.sumLossByReason", e);
        }
        return map;
    }

    public BigDecimal sumLossThisMonth() {
        LocalDate now = LocalDate.now();
        return sumLossBetween(now.withDayOfMonth(1), now);
    }

    /**
     * Phan trang + tim kiem + loc theo khoang ngay lap phieu (CreatedAt).
     * Loc theo [fromDate 00:00:00, toDate+1 00:00:00) de bao gom tron ca ngay toDate.
     */
    public PaginationHelper.PaginationResult<StockDisposal> getPagedFiltered(
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
        if (fromDate != null) {
            conditions.add("d.CreatedAt >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            conditions.add("d.CreatedAt < ?");
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
}