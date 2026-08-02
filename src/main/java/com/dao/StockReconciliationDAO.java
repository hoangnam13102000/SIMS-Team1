package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.StockReconciliation;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;

/**
 * DAO cho StockReconciliation (Doi chieu / kiem ke kho cuoi ngay).
 *
 * Chi INSERT qua day - viec tinh SystemStock, cap nhat Products.Stock va ghi
 * InventoryTransactions deu do trigger trg_StockReconciliation_Apply
 * (INSTEAD OF INSERT tren bang StockReconciliation) dam nhiem, giong tinh
 * than cac DAO khac trong du an (vd InventoryBatchDAO.receiveBatch). Khong
 * ho tro sua/xoa - xem trg_StockReconciliation_BlockDelete (R3).
 */
public class StockReconciliationDAO extends BaseDAO<StockReconciliation> {

    private static final String BASE_TABLE =
            "StockReconciliation r "
                    + "JOIN Products p ON r.ProductID = p.ProductID "
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
        return "r.ReconciliationID, r.ProductID, p.ProductName, p.ProductCode, "
                + "r.SystemStock, r.ActualStock, r.Discrepancy, r.Note, "
                + "r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "r.CreatedAt DESC, r.ReconciliationID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "p.ProductCode", "u.FullName"};
    }

    @Override
    protected StockReconciliation mapResultSet(ResultSet rs) throws SQLException {
        StockReconciliation r = new StockReconciliation();
        r.setReconciliationId(rs.getInt("ReconciliationID"));
        r.setProductId(rs.getInt("ProductID"));
        r.setProductName(rs.getString("ProductName"));
        r.setProductCode(rs.getString("ProductCode"));
        r.setSystemStock(rs.getInt("SystemStock"));
        r.setActualStock(rs.getInt("ActualStock"));
        r.setDiscrepancy(rs.getInt("Discrepancy"));
        r.setNote(rs.getString("Note"));
        r.setCreatedBy(rs.getInt("CreatedBy"));
        r.setCreatedByName(rs.getString("CreatedByName"));
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        r.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return r;
    }

    /**
     * Luu ca 1 phien kiem ke (nhieu san pham cung luc) trong 1 transaction
     * duy nhat - hoac tat ca deu duoc ghi, hoac khong dong nao ca. SystemStock
     * truyen vao day CHI la placeholder (0), trigger se tu tinh lai tu
     * Products.Stock hien hanh tai thoi diem ghi - xem trg_StockReconciliation_Apply.
     */
    public boolean saveSession(List<StockReconciliation> rows, int createdByUserId) {
        if (rows == null || rows.isEmpty()) return true;

        String sql = "INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy) "
                + "VALUES (?, 0, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    for (StockReconciliation row : rows) {
                        ps.setInt(1, row.getProductId());
                        ps.setInt(2, row.getActualStock());
                        String note = row.getNote();
                        if (note == null || note.isBlank()) {
                            ps.setNull(3, Types.NVARCHAR);
                        } else {
                            ps.setString(3, note);
                        }
                        ps.setInt(4, createdByUserId);
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.STOCK_RECONCILIATION));
                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "StockReconciliationDAO.saveSession - rows=" + rows.size(), e);
            return false;
        }
    }

    /** Dem so dong lech (Discrepancy <> 0) trong 1 khoang thoi gian - dung cho thong ke/dashboard neu can. */
    public int countDiscrepanciesSince(java.time.LocalDateTime since) {
        String sql = "SELECT COUNT(*) FROM StockReconciliation WHERE Discrepancy <> 0 AND CreatedAt >= ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(since));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockReconciliationDAO.countDiscrepanciesSince", e);
            return 0;
        }
    }
}