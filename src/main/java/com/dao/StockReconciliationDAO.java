package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.StockReconciliation;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
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

    /**
     * Tim kiem + loc doi chieu kho theo tu khoa (ten SP / ma SP / nguoi doi chieu)
     * va/hoac khoang ngay tao phien doi chieu.
     *
     * @param fromDate ngay bat dau (bao gom ca ngay nay), null = khong gioi han duoi
     * @param toDate   ngay ket thuc (bao gom ca ngay nay), null = khong gioi han tren
     */
    public PaginationHelper.PaginationResult<StockReconciliation> getPagedFiltered(
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

        // Loc theo [fromDate 00:00:00, toDate+1 00:00:00) de bao gom tron ca ngay toDate
        if (fromDate != null) {
            conditions.add("r.CreatedAt >= ?");
            params.add(Timestamp.valueOf(fromDate.atStartOfDay()));
        }
        if (toDate != null) {
            conditions.add("r.CreatedAt < ?");
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

    /**
     * Kiem tra da co it nhat 1 dong doi chieu trong ngay {@code date} chua.
     */
    public boolean hasSessionForDate(LocalDate date) {
        if (date == null) return false;
        String sql = "SELECT TOP 1 1 FROM StockReconciliation "
                + "WHERE CreatedAt >= ? AND CreatedAt < ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StockReconciliationDAO.hasSessionForDate date=" + date, e);
            return false;
        }
    }

    /**
     * Tao phien doi chieu cho ngay {@code date}: chen MOI san pham dang ACTIVE
     * (va category ACTIVE) chua co trong phien ngay do. ActualStock = Stock hien
     * tai → Discrepancy = 0, trigger khong doi Products.Stock.
     * Neu da du san pham thi khong chen them.
     *
     * @return so dong moi duoc tao (0 neu da du / loi)
     */
    public int ensureDailySession(LocalDate date, int createdByUserId) {
        if (date == null) return 0;

        // Lay cac ProductID ACTIVE chua co dong doi chieu trong ngay
        String selectMissing = ""
                + "SELECT p.ProductID, p.Stock "
                + "FROM Products p "
                + "JOIN Categories c ON p.CategoryID = c.CategoryID "
                + "WHERE p.Status = 'ACTIVE' AND c.Status = 'ACTIVE' "
                + "AND NOT EXISTS ("
                + "  SELECT 1 FROM StockReconciliation r "
                + "  WHERE r.ProductID = p.ProductID "
                + "    AND r.CreatedAt >= ? AND r.CreatedAt < ?"
                + ")";

        String insertSql = "INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy) "
                + "VALUES (?, 0, ?, NULL, ?)";

        int created = 0;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                List<int[]> missing = new ArrayList<>(); // [productId, stock]
                try (PreparedStatement ps = con.prepareStatement(selectMissing)) {
                    ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
                    ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            missing.add(new int[]{rs.getInt("ProductID"), rs.getInt("Stock")});
                        }
                    }
                }

                if (!missing.isEmpty()) {
                    try (PreparedStatement ps = con.prepareStatement(insertSql)) {
                        for (int[] row : missing) {
                            ps.setInt(1, row[0]);
                            ps.setInt(2, row[1]); // ActualStock = Stock hien tai
                            ps.setInt(3, createdByUserId);
                            ps.addBatch();
                        }
                        int[] results = ps.executeBatch();
                        for (int r : results) {
                            if (r >= 0) created += r;
                            else if (r == java.sql.Statement.SUCCESS_NO_INFO) created++;
                        }
                    }
                }

                con.commit();
                if (created > 0) {
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.STOCK_RECONCILIATION));
                }
                return created;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "StockReconciliationDAO.ensureDailySession date=" + date, e);
            return 0;
        }
    }

    /**
     * Cap nhat ton thuc te cua 1 dong doi chieu, dong thoi dieu chinh Products.Stock
     * ve dung so dem moi va ghi InventoryTransactions neu co chenh.
     * Chi cho phep sua dong cua NGAY HOM NAY (bao toan lich su doi soat).
     *
     * @return true neu cap nhat thanh cong
     */
    public boolean updateActualStock(int reconciliationId, int newActualStock, int userId) {
        if (newActualStock < 0) return false;

        String selectSql = "SELECT r.ProductID, r.ActualStock, r.SystemStock, p.Stock AS CurrentStock, r.CreatedAt "
                + "FROM StockReconciliation r "
                + "JOIN Products p ON r.ProductID = p.ProductID "
                + "WHERE r.ReconciliationID = ?";

        String updateReconSql = "UPDATE StockReconciliation SET ActualStock = ? WHERE ReconciliationID = ?";
        String updateProductSql = "UPDATE Products SET Stock = ? WHERE ProductID = ?";
        String insertTxSql = "INSERT INTO InventoryTransactions "
                + "(ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter, RefTable, RefID, CreatedBy, Note) "
                + "VALUES (?, 'RECONCILE_ADJUST', ?, ?, ?, ?, 'StockReconciliation', ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                int productId;
                int oldActual;
                int currentStock;
                java.sql.Timestamp createdAt;

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, reconciliationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            con.rollback();
                            return false;
                        }
                        productId = rs.getInt("ProductID");
                        oldActual = rs.getInt("ActualStock");
                        currentStock = rs.getInt("CurrentStock");
                        createdAt = rs.getTimestamp("CreatedAt");
                    }
                }

                // Chi cho sua dong cua ngay hom nay
                LocalDate rowDate = createdAt.toLocalDateTime().toLocalDate();
                if (!rowDate.equals(LocalDate.now())) {
                    con.rollback();
                    return false;
                }

                if (oldActual == newActualStock) {
                    con.commit();
                    return true;
                }

                try (PreparedStatement ps = con.prepareStatement(updateReconSql)) {
                    ps.setInt(1, newActualStock);
                    ps.setInt(2, reconciliationId);
                    ps.executeUpdate();
                }

                // Dieu chinh Products.Stock ve dung so dem thuc te
                int stockBefore = currentStock;
                int stockAfter = newActualStock;
                int diff = stockAfter - stockBefore;

                try (PreparedStatement ps = con.prepareStatement(updateProductSql)) {
                    ps.setInt(1, stockAfter);
                    ps.setInt(2, productId);
                    ps.executeUpdate();
                }

                if (diff != 0) {
                    try (PreparedStatement ps = con.prepareStatement(insertTxSql)) {
                        ps.setInt(1, productId);
                        ps.setString(2, diff > 0 ? "IN" : "OUT");
                        ps.setInt(3, Math.abs(diff));
                        ps.setInt(4, stockBefore);
                        ps.setInt(5, stockAfter);
                        ps.setInt(6, reconciliationId);
                        ps.setInt(7, userId);
                        ps.setString(8, "Dieu chinh ton thuc te tren bang doi chieu");
                        ps.executeUpdate();
                    }
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
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "StockReconciliationDAO.updateActualStock id=" + reconciliationId, e);
            return false;
        }
    }
}