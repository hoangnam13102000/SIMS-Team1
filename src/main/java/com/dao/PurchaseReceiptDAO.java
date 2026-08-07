package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.PurchaseReceipt;
import com.model.PurchaseReceiptDetail;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
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

    /**
     * Lập phiếu nhập kho nhiều dòng trong 1 transaction.
     * Mỗi dòng chi tiết → trigger sinh 1 lô + cộng tồn + ghi sổ cái.
     *
     * @param supplierId      nhà cung cấp của cả phiếu
     * @param createdByUserId UserID người lập
     * @param details         danh sách dòng (≥ 1), mỗi dòng: productId, quantity &gt; 0, importPrice ≥ 0
     * @return ReceiptID nếu thành công, -1 nếu thất bại
     */
    public int createReceipt(int supplierId, int createdByUserId, List<PurchaseReceiptDetail> details) {
        if (details == null || details.isEmpty()) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "PurchaseReceiptDAO.createReceipt - danh sach dong rong", null);
            return -1;
        }

        BigDecimal total = BigDecimal.ZERO;
        for (PurchaseReceiptDetail d : details) {
            if (d.getQuantity() <= 0 || d.getImportPrice() == null || d.getImportPrice().signum() < 0) {
                AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                        "PurchaseReceiptDAO.createReceipt - dong khong hop le productId=" + d.getProductId(), null);
                return -1;
            }
            total = total.add(d.getImportPrice().multiply(BigDecimal.valueOf(d.getQuantity())));
        }

        String receiptSql = "INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount, Status) "
                + "VALUES (?, ?, ?, ?, 'COMPLETED')";
        String updateCodeSql = "UPDATE PurchaseReceipts SET ReceiptCode = ? WHERE ReceiptID = ?";
        String detailSql = "INSERT INTO PurchaseReceiptDetails "
                + "(ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int receiptId;
                try (PreparedStatement ps = con.prepareStatement(receiptSql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, "PENDING");
                    ps.setInt(2, supplierId);
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
                    for (PurchaseReceiptDetail d : details) {
                        ps.setInt(1, receiptId);
                        ps.setInt(2, d.getProductId());
                        ps.setInt(3, d.getQuantity());
                        ps.setBigDecimal(4, d.getImportPrice());
                        if (d.getLotNumber() != null && !d.getLotNumber().isBlank()) {
                            ps.setString(5, d.getLotNumber().trim());
                        } else {
                            ps.setNull(5, Types.NVARCHAR);
                        }
                        if (d.getManufactureDate() != null) {
                            ps.setDate(6, Date.valueOf(d.getManufactureDate()));
                        } else {
                            ps.setNull(6, Types.DATE);
                        }
                        if (d.getExpiryDate() != null) {
                            ps.setDate(7, Date.valueOf(d.getExpiryDate()));
                        } else {
                            ps.setNull(7, Types.DATE);
                        }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                // Liên kết giá nhập lô/phiếu → Products.ImportPrice (giá lần nhập gần nhất).
                // Báo cáo lợi nhuận dùng Products.ImportPrice; không cập nhật sẽ lệch với giá thực tế trên lô.
                String updateProductPriceSql = "UPDATE Products SET ImportPrice = ? WHERE ProductID = ?";
                try (PreparedStatement ps = con.prepareStatement(updateProductPriceSql)) {
                    for (PurchaseReceiptDetail d : details) {
                        ps.setBigDecimal(1, d.getImportPrice());
                        ps.setInt(2, d.getProductId());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PURCHASE_RECEIPT));
                return receiptId;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "PurchaseReceiptDAO.createReceipt - supplierId=" + supplierId, e);
            return -1;
        }
    }

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