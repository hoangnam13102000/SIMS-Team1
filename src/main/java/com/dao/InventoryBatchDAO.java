package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.InventoryBatch;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

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
        // Lo da ban het (RemainingQty <= 0) day xuong cuoi danh sach - khong con
        // tac dung gi voi FEFO/nhap hang nua nen khong can uu tien hien thi truoc.
        // Trong nhom con hang: lo het han/sap het han len truoc (dung tinh than FEFO)
        // - lo khong co HSD xep sau cung. Cung HSD -> BatchID ASC (lo nhap truoc len
        // truoc) de khop dung thu tu trigger trg_InvoiceDetails_CheckStock dung khi
        // tru kho FEFO luc ban hang.
        return "CASE WHEN b.RemainingQty <= 0 THEN 1 ELSE 0 END, "
                + "CASE WHEN b.ExpiryDate IS NULL THEN 1 ELSE 0 END, b.ExpiryDate ASC, b.BatchID ASC";
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

    /**
     * Nhập 1 lô đơn lẻ — ủy quyền sang {@link PurchaseReceiptDAO#createReceipt}
     * (1 phiếu / 1 dòng). Giữ API cũ cho form đơn lô; form nhiều dòng dùng
     * trực tiếp PurchaseReceiptDAO.
     */
    public boolean receiveBatch(InventoryBatch batch, int createdByUserId) {
        com.model.PurchaseReceiptDetail detail = new com.model.PurchaseReceiptDetail();
        detail.setProductId(batch.getProductId());
        detail.setQuantity(batch.getQuantity());
        detail.setImportPrice(batch.getImportPrice());
        detail.setLotNumber(batch.getLotNumber());
        detail.setManufactureDate(batch.getManufactureDate());
        detail.setExpiryDate(batch.getExpiryDate());
        int receiptId = new PurchaseReceiptDAO().createReceipt(
                batch.getSupplierId(), createdByUserId, java.util.List.of(detail));
        return receiptId > 0;
    }

    /**
     * Tim + loc lo hang theo tu khoa (ma lo, ten SP, ma SP, so lo, NCC) va/hoac
     * danh muc san pham. categoryId = null -> khong loc theo danh muc ("Tat ca
     * danh muc"). Ket qua van giu nguyen thu tu FEFO tu {@link #getOrderBy()}
     * (uu tien HSD gan nhat) nen loc theo danh muc se cho thay dung danh sach
     * "san pham nao trong danh muc do se duoc ban truoc" theo tung nganh hang.
     */
    public PaginationHelper.PaginationResult<InventoryBatch> getPagedFiltered(
            int page, int pageSize, String keyword, Integer categoryId) {

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
        if (categoryId != null) {
            conditions.add("p.CategoryID = ?");
            params.add(categoryId);
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

    /**
     * Cac lo CON TON (RemainingQty > 0, Status='ACTIVE') cua 1 nhom ProductID,
     * theo dung thu tu FEFO (getOrderBy()) - dung cho InventoryOverviewPanel
     * de hien cot "Lo hang" (nhan vien nhin ma lo la biet can tim lo nao
     * trong kho khi bo sung hang). 1 QUERY DUY NHAT cho ca trang (IN (...)),
     * KHONG goi rieng tung san pham (tranh N+1). Ket qua group san theo
     * ProductID de panel tra cuu O(1) khi mapRowToColumns().
     */
    public java.util.Map<Integer, java.util.List<InventoryBatch>> getActiveBatchesByProductIds(List<Integer> productIds) {
        java.util.Map<Integer, java.util.List<InventoryBatch>> result = new java.util.LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;

        syncExpiredStatus();

        String placeholders = String.join(",", java.util.Collections.nCopies(productIds.size(), "?"));
        String sql = "SELECT " + getColumns() + " FROM " + getTableName()
                + " WHERE b.ProductID IN (" + placeholders + ") AND b.RemainingQty > 0 AND b.Status = 'ACTIVE'"
                + " ORDER BY " + getOrderBy();

        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < productIds.size(); i++) {
                ps.setInt(i + 1, productIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    InventoryBatch batch = mapResultSet(rs);
                    result.computeIfAbsent(batch.getProductId(), k -> new ArrayList<>()).add(batch);
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "InventoryBatchDAO.getActiveBatchesByProductIds", e);
        }
        return result;
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