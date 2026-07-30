package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.PurchaseReceipt;
import com.model.PurchaseReceiptDetail;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class PurchaseReceiptDAO extends BaseDAO<PurchaseReceipt> {

    private static final String BASE_TABLE =
            "PurchaseReceipts r "
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
        return "r.ReceiptID, r.ReceiptCode, r.SupplierID, s.SupplierName, "
                + "r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt, r.TotalAmount, r.Status, "
                + "(SELECT COUNT(*) FROM PurchaseReceiptDetails d WHERE d.ReceiptID = r.ReceiptID) AS ItemCount";
    }

    @Override
    protected String getOrderBy() {
        return "r.CreatedAt DESC, r.ReceiptID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"r.ReceiptCode", "s.SupplierName", "u.FullName"};
    }

    @Override
    protected PurchaseReceipt mapResultSet(ResultSet rs) throws SQLException {
        PurchaseReceipt receipt = new PurchaseReceipt();
        receipt.setReceiptId(rs.getInt("ReceiptID"));
        receipt.setReceiptCode(rs.getString("ReceiptCode"));
        receipt.setSupplierId(rs.getInt("SupplierID"));
        receipt.setSupplierName(rs.getString("SupplierName"));
        receipt.setCreatedBy(rs.getInt("CreatedBy"));
        receipt.setCreatedByName(rs.getString("CreatedByName"));

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        receipt.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);

        receipt.setTotalAmount(rs.getBigDecimal("TotalAmount"));
        receipt.setStatus(rs.getString("Status"));
        receipt.setItemCount(rs.getInt("ItemCount"));
        return receipt;
    }

    /** Danh sach dong san pham (PurchaseReceiptDetails) cua 1 phieu nhap, dung khi xem chi tiet. */
    public List<PurchaseReceiptDetail> getDetails(int receiptId) {
        String sql = "SELECT d.ReceiptDetailID, d.ReceiptID, d.ProductID, p.ProductName, p.ProductCode, "
                + "d.Quantity, d.ImportPrice, d.LotNumber, d.ManufactureDate, d.ExpiryDate "
                + "FROM PurchaseReceiptDetails d "
                + "JOIN Products p ON p.ProductID = d.ProductID "
                + "WHERE d.ReceiptID = ? "
                + "ORDER BY d.ReceiptDetailID ASC";

        List<PurchaseReceiptDetail> list = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, receiptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PurchaseReceiptDetail detail = new PurchaseReceiptDetail();
                    detail.setReceiptDetailId(rs.getInt("ReceiptDetailID"));
                    detail.setReceiptId(rs.getInt("ReceiptID"));
                    detail.setProductId(rs.getInt("ProductID"));
                    detail.setProductName(rs.getString("ProductName"));
                    detail.setProductCode(rs.getString("ProductCode"));
                    detail.setQuantity(rs.getInt("Quantity"));
                    detail.setImportPrice(rs.getBigDecimal("ImportPrice"));
                    detail.setLotNumber(rs.getString("LotNumber"));

                    Date mfg = rs.getDate("ManufactureDate");
                    detail.setManufactureDate(mfg != null ? mfg.toLocalDate() : null);
                    Date exp = rs.getDate("ExpiryDate");
                    detail.setExpiryDate(exp != null ? exp.toLocalDate() : null);

                    list.add(detail);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "PurchaseReceiptDAO.getDetails - receiptId=" + receiptId, e);
        }
        return list;
    }

    /** Tong tien nhap kho trong ngay hom nay (dung cho Dashboard neu can). */
    public BigDecimal sumTodayAmount() {
        String sql = "SELECT ISNULL(SUM(TotalAmount), 0) FROM PurchaseReceipts "
                + "WHERE Status = 'COMPLETED' AND CAST(CreatedAt AS DATE) = CAST(GETDATE() AS DATE)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getBigDecimal(1) : BigDecimal.ZERO;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "PurchaseReceiptDAO.sumTodayAmount", e);
            return BigDecimal.ZERO;
        }
    }
}