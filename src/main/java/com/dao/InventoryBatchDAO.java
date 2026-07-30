package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;      
import com.event.DataChangedEvent; 
import com.model.InventoryBatch;
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

public class InventoryBatchDAO extends BaseDAO<InventoryBatch> {

    private static final String BASE_TABLE =
            "InventoryBatch b "
                    + "JOIN Products p ON b.ProductID = p.ProductID "
                    + "JOIN Suppliers s ON b.SupplierID = s.SupplierID";

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
        return "b.BatchID, b.BatchCode, b.LotNumber, b.ProductID, p.ProductName, p.ProductCode, "
                + "b.SupplierID, s.SupplierName, b.ManufactureDate, b.ExpiryDate, b.ImportDate, "
                + "b.ImportPrice, b.Quantity, b.RemainingQty, b.Status";
    }

    @Override
    protected String getOrderBy() {
        // Lo het han/sap het han len truoc (dung tinh than FEFO) - lo khong co HSD xep sau cung.
        return "CASE WHEN b.ExpiryDate IS NULL THEN 1 ELSE 0 END, b.ExpiryDate ASC, b.BatchID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "p.ProductCode", "b.LotNumber", "b.BatchCode", "s.SupplierName"};
    }

    @Override
    protected InventoryBatch mapResultSet(ResultSet rs) throws SQLException {
        InventoryBatch batch = new InventoryBatch();
        batch.setBatchId(rs.getInt("BatchID"));
        batch.setBatchCode(rs.getString("BatchCode"));
        batch.setLotNumber(rs.getString("LotNumber"));
        batch.setProductId(rs.getInt("ProductID"));
        batch.setProductName(rs.getString("ProductName"));
        batch.setProductCode(rs.getString("ProductCode"));
        batch.setSupplierId(rs.getInt("SupplierID"));
        batch.setSupplierName(rs.getString("SupplierName"));

        Date mfg = rs.getDate("ManufactureDate");
        batch.setManufactureDate(mfg != null ? mfg.toLocalDate() : null);
        Date exp = rs.getDate("ExpiryDate");
        batch.setExpiryDate(exp != null ? exp.toLocalDate() : null);
        Timestamp importDate = rs.getTimestamp("ImportDate");
        batch.setImportDate(importDate != null ? importDate.toLocalDateTime() : null);

        batch.setImportPrice(rs.getBigDecimal("ImportPrice"));
        batch.setQuantity(rs.getInt("Quantity"));
        batch.setRemainingQty(rs.getInt("RemainingQty"));
        batch.setStatus(rs.getString("Status"));
        return batch;
    }

    /**
     * Dong bo Status='EXPIRED' cho cac lo da qua HSD nhung con RemainingQty > 0
     * (chua tung co gi tu dong lam viec nay - trigger chi chay khi co INSERT/
     * UPDATE, khong tu chay theo thoi gian troi qua). Goi truoc moi lan doc
     * danh sach de man hinh luon phan anh dung trang thai hien tai.
     */
    public void syncExpiredStatus() {
        String sql = "UPDATE InventoryBatch SET Status = 'EXPIRED' "
                + "WHERE Status = 'ACTIVE' AND RemainingQty > 0 "
                + "AND ExpiryDate IS NOT NULL AND ExpiryDate < CAST(GETDATE() AS DATE)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.executeUpdate();
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "InventoryBatchDAO.syncExpiredStatus", e);
        }
    }

    /**
     * Override duy nhat 1 diem chung (getPaged 4-tham-so) ma moi overload
     * khac trong BaseDAO deu goi qua no - du chi override o day, ca getPaged(page,size),
     * getPaged(page,size,where) va search(...) deu tu dong duoc dong bo truoc khi doc.
     */
    @Override
    public PaginationHelper.PaginationResult<InventoryBatch> getPaged(
            int pageNumber, int pageSize, String whereClause, Object... params) {
        syncExpiredStatus();
        return super.getPaged(pageNumber, pageSize, whereClause, params);
    }

    public boolean receiveBatch(InventoryBatch batch, int createdByUserId) {
        String receiptSql = "INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount) "
                + "VALUES (?, ?, ?, ?)";
        String updateCodeSql = "UPDATE PurchaseReceipts SET ReceiptCode = ? WHERE ReceiptID = ?";
        String detailSql = "INSERT INTO PurchaseReceiptDetails "
                + "(ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                BigDecimal total = batch.getImportPrice().multiply(BigDecimal.valueOf(batch.getQuantity()));
                int receiptId;

                try (PreparedStatement ps = con.prepareStatement(receiptSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "PENDING");
                    ps.setInt(2, batch.getSupplierId());
                    ps.setInt(3, createdByUserId);
                    ps.setBigDecimal(4, total);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        if (!keys.next()) throw new SQLException("Khong lay duoc ReceiptID vua tao.");
                        receiptId = keys.getInt(1);
                    }
                }

                try (PreparedStatement ps = con.prepareStatement(updateCodeSql)) {
                    ps.setString(1, "PN_" + String.format("%06d", receiptId));
                    ps.setInt(2, receiptId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = con.prepareStatement(detailSql)) {
                    ps.setInt(1, receiptId);
                    ps.setInt(2, batch.getProductId());
                    ps.setInt(3, batch.getQuantity());
                    ps.setBigDecimal(4, batch.getImportPrice());
                    ps.setString(5, batch.getLotNumber());
                    if (batch.getManufactureDate() != null) {
                        ps.setDate(6, Date.valueOf(batch.getManufactureDate()));
                    } else {
                        ps.setNull(6, Types.DATE);
                    }
                    if (batch.getExpiryDate() != null) {
                        ps.setDate(7, Date.valueOf(batch.getExpiryDate()));
                    } else {
                        ps.setNull(7, Types.DATE);
                    }
                    ps.executeUpdate();
                }

                con.commit();
                // MỚI: bao cho cac panel dang mo (VD: Quan ly nhap kho) tu reload
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PURCHASE_RECEIPT));
                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "InventoryBatchDAO.receiveBatch - productId=" + batch.getProductId(), e);
            return false;
        }
    }

    /** Dem so lo sap het han trong vong {@code days} ngay toi (dung cho canh bao tren Dashboard). */
    public int countExpiringSoon(int days) {
        String sql = "SELECT COUNT(*) FROM InventoryBatch "
                + "WHERE Status = 'ACTIVE' AND RemainingQty > 0 AND ExpiryDate IS NOT NULL "
                + "AND ExpiryDate BETWEEN CAST(GETDATE() AS DATE) AND DATEADD(DAY, ?, CAST(GETDATE() AS DATE))";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, days);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InventoryBatchDAO.countExpiringSoon", e);
            return 0;
        }
    }
}