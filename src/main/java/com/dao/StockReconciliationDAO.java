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
    public StockReconciliationDAO() {
        ensureBatchIdColumn();
    }

    /** Them khoa BatchID vao lich su doi chieu de moi dong gan duy nhat 1 lo hang. */
    private void ensureBatchIdColumn() {
        String checkSql = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'StockReconciliation' AND COLUMN_NAME = 'BatchID'";
        String alterSql = "ALTER TABLE StockReconciliation ADD COLUMN BatchID INT NULL AFTER ProductID";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement check = con.prepareStatement(checkSql);
             ResultSet rs = check.executeQuery()) {
            if (rs.next() && rs.getInt(1) == 0) {
                try (PreparedStatement alter = con.prepareStatement(alterSql)) {
                    alter.executeUpdate();
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "StockReconciliationDAO.ensureBatchIdColumn", e);
        }
    }
    private static final String BASE_TABLE =
            "StockReconciliation r "
                    + "JOIN Products p ON r.ProductID = p.ProductID "
                    + "JOIN Users u ON r.CreatedBy = u.UserID "
                    + "LEFT JOIN Users cu ON r.CheckedBy = cu.UserID "
                    + "LEFT JOIN InventoryBatch b ON r.BatchID = b.BatchID";
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
                + "COALESCE(r.BatchID, 0) AS BatchID, b.BatchCode AS BatchCode, "
                + "r.SystemStock, r.ActualStock, r.Discrepancy, r.Note, "
                + "r.CreatedBy, u.FullName AS CreatedByName, r.CreatedAt, "
                + "r.Checked, r.CheckedBy, cu.FullName AS CheckedByName, r.CheckedAt";
    }
    @Override
    protected String getOrderBy() {
        return "r.CreatedAt DESC, r.ReconciliationID DESC";
    }
    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "p.ProductCode", "b.BatchCode", "b.LotNumber", "cu.FullName"};
    }
    @Override
    protected StockReconciliation mapResultSet(ResultSet rs) throws SQLException {
        StockReconciliation r = new StockReconciliation();
        r.setReconciliationId(rs.getInt("ReconciliationID"));
        r.setProductId(rs.getInt("ProductID"));
        r.setProductName(rs.getString("ProductName"));
        r.setProductCode(rs.getString("ProductCode"));
        r.setBatchId(rs.getInt("BatchID"));
        r.setBatchCode(rs.getString("BatchCode"));
        r.setSystemStock(rs.getInt("SystemStock"));
        r.setActualStock(rs.getInt("ActualStock"));
        r.setDiscrepancy(rs.getInt("Discrepancy"));
        r.setNote(rs.getString("Note"));
        r.setCreatedBy(rs.getInt("CreatedBy"));
        r.setCreatedByName(rs.getString("CreatedByName"));
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        r.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        r.setChecked(rs.getBoolean("Checked"));
        r.setCheckedBy(rs.getInt("CheckedBy"));
        r.setCheckedByName(rs.getString("CheckedByName"));
        Timestamp checkedAt = rs.getTimestamp("CheckedAt");
        r.setCheckedAt(checkedAt != null ? checkedAt.toLocalDateTime() : null);
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
        String sql = "INSERT INTO StockReconciliation (ProductID, BatchID, SystemStock, ActualStock, Note, CreatedBy) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(sql)) {
                    for (StockReconciliation row : rows) {
                        ps.setInt(1, row.getProductId());
                        if (row.getBatchId() > 0) ps.setInt(2, row.getBatchId()); else ps.setNull(2, Types.INTEGER);
                        ps.setInt(3, row.getSystemStock());
                        ps.setInt(4, row.getActualStock());
                        String note = row.getNote();
                        if (note == null || note.isBlank()) ps.setNull(5, Types.VARCHAR);
                        else ps.setString(5, note);
                        ps.setInt(6, createdByUserId);
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
        // Sản phẩm chưa từng có lô hàng (BatchID NULL) không thể bán qua POS/đơn
        // online (kho chỉ trừ theo InventoryBatch), nên ẩn hẳn khỏi bảng đối
        // chiếu — tránh nhân viên tưởng nhầm là "tồn thật" có thể tất toán được.
        conditions.add("r.BatchID IS NOT NULL");
        String trimmedKeyword = keyword == null ? "" : keyword.trim();
        if (!trimmedKeyword.isEmpty()) {
            String[] columns = getSearchableColumns();
            String likeParam = "%" + escapeLike(trimmedKeyword) + "%";
            StringBuilder keywordCondition = new StringBuilder("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) keywordCondition.append(" OR ");
                keywordCondition.append(columns[i]).append(" LIKE ? ESCAPE '!'");
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
        return raw.replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
    }
    /**
     * Kiem tra da co it nhat 1 dong doi chieu trong ngay {@code date} chua.
     */
    public boolean hasSessionForDate(LocalDate date) {
        if (date == null) return false;
        String sql = "SELECT 1 FROM StockReconciliation "
                + "WHERE CreatedAt >= ? AND CreatedAt < ? LIMIT 1";
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
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                migrateTodayProductRowsToBatches(con, date, createdByUserId);

                String selectMissing = "SELECT p.ProductID, b.BatchID, "
                        + "COALESCE(b.RemainingQty, p.Stock) AS Stock "
                        + "FROM Products p "
                        + "JOIN Categories c ON p.CategoryID = c.CategoryID "
                        + "LEFT JOIN InventoryBatch b ON b.ProductID = p.ProductID AND b.RemainingQty > 0 AND b.Status = 'ACTIVE' "
                        + "WHERE p.Status = 'ACTIVE' AND c.Status = 'ACTIVE' "
                        + "AND (b.BatchID IS NOT NULL OR NOT EXISTS (SELECT 1 FROM InventoryBatch bx WHERE bx.ProductID = p.ProductID)) "
                        + "AND NOT EXISTS ("
                        + "  SELECT 1 FROM StockReconciliation r "
                        + "  WHERE r.ProductID = p.ProductID "
                        + "    AND r.BatchID <=> b.BatchID "
                        + "    AND r.CreatedAt >= ? AND r.CreatedAt < ?"
                        + ") "
                        + "ORDER BY p.ProductID, b.BatchID";

                String insertSql = "INSERT INTO StockReconciliation (ProductID, BatchID, SystemStock, ActualStock, Note, CreatedBy) "
                        + "VALUES (?, ?, ?, ?, NULL, ?)";
                int created = 0;
                try (PreparedStatement ps = con.prepareStatement(selectMissing);
                     PreparedStatement ins = con.prepareStatement(insertSql)) {
                    ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
                    ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int productId = rs.getInt("ProductID");
                            int batchId = rs.getInt("BatchID");
                            if (rs.wasNull()) batchId = 0;
                            int stock = rs.getInt("Stock");
                            ins.setInt(1, productId);
                            if (batchId > 0) ins.setInt(2, batchId); else ins.setNull(2, Types.INTEGER);
                            ins.setInt(3, stock);
                            ins.setInt(4, stock);
                            ins.setInt(5, createdByUserId);
                            ins.addBatch();
                            created++;
                        }
                    }
                    if (created > 0) ins.executeBatch();
                }
                con.commit();
                if (created > 0) AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.STOCK_RECONCILIATION));
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
     * Chuyen phien hom nay dang co dang 1 dong/san pham sang 1 dong/lo.
     * Chi ap dung cho dong chua BatchID, de khong tao trung khi app da tao phien moi.
     */
    private void migrateTodayProductRowsToBatches(Connection con, LocalDate date, int createdByUserId) throws SQLException {
        String select = "SELECT r.ReconciliationID, r.ProductID, r.SystemStock, r.ActualStock, r.Note, r.CreatedBy "
                + "FROM StockReconciliation r "
                + "WHERE r.BatchID IS NULL AND r.CreatedAt >= ? AND r.CreatedAt < ? "
                + "AND EXISTS (SELECT 1 FROM InventoryBatch b WHERE b.ProductID = r.ProductID AND b.RemainingQty > 0 AND b.Status = 'ACTIVE')";
        String batches = "SELECT BatchID, RemainingQty FROM InventoryBatch "
                + "WHERE ProductID = ? AND RemainingQty > 0 AND Status = 'ACTIVE' ORDER BY BatchID";
        String update = "UPDATE StockReconciliation SET BatchID = ?, SystemStock = ?, ActualStock = ? WHERE ReconciliationID = ?";
        String insert = "INSERT INTO StockReconciliation (ProductID, BatchID, SystemStock, ActualStock, Note, CreatedBy) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement ps = con.prepareStatement(select);
             PreparedStatement pb = con.prepareStatement(batches);
             PreparedStatement pu = con.prepareStatement(update);
             PreparedStatement pi = con.prepareStatement(insert)) {
            ps.setTimestamp(1, Timestamp.valueOf(date.atStartOfDay()));
            ps.setTimestamp(2, Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int reconId = rs.getInt("ReconciliationID");
                    int productId = rs.getInt("ProductID");
                    int oldSystem = rs.getInt("SystemStock");
                    int oldActual = rs.getInt("ActualStock");
                    String note = rs.getString("Note");
                    int creator = rs.getInt("CreatedBy");
                    List<int[]> rows = new ArrayList<>();
                    pb.setInt(1, productId);
                    try (ResultSet br = pb.executeQuery()) {
                        while (br.next()) rows.add(new int[]{br.getInt("BatchID"), br.getInt("RemainingQty")});
                    }
                    if (rows.isEmpty()) continue;
                    int deltaActual = oldActual - oldSystem;
                    int firstActual = Math.max(0, rows.get(0)[1] + deltaActual);
                    pu.setInt(1, rows.get(0)[0]);
                    pu.setInt(2, rows.get(0)[1]);
                    pu.setInt(3, firstActual);
                    pu.setInt(4, reconId);
                    pu.addBatch();
                    for (int i = 1; i < rows.size(); i++) {
                        pi.setInt(1, productId);
                        pi.setInt(2, rows.get(i)[0]);
                        pi.setInt(3, rows.get(i)[1]);
                        pi.setInt(4, rows.get(i)[1]);
                        if (note == null || note.isBlank()) pi.setNull(5, Types.VARCHAR); else pi.setString(5, note);
                        pi.setInt(6, creator > 0 ? creator : createdByUserId);
                        pi.addBatch();
                    }
                }
            }
            pu.executeBatch();
            pi.executeBatch();
        }
    }

    /**
     * Cap nhat ton thuc te cua 1 dong doi chieu, dong thoi dieu chinh Products.Stock
     * ve dung so dem moi va ghi InventoryTransactions neu co chenh.
     * Chi cho phep sua dong cua NGAY HOM NAY (bao toan lich su doi soat).
     *
     * @return true neu cap nhat thanh cong
     */
    /**
     * Doc [RemainingQty, Quantity] cua lo cho Panel validation.
     */
    public int[] getBatchStockLimits(int reconciliationId) {
        String sql = "SELECT COALESCE(b.RemainingQty, 0) AS RemainingQty, "
                + "COALESCE(b.Quantity, 0) AS Quantity "
                + "FROM StockReconciliation r "
                + "JOIN InventoryBatch b ON r.BatchID = b.BatchID "
                + "WHERE r.ReconciliationID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, reconciliationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new int[]{rs.getInt("RemainingQty"), rs.getInt("Quantity")};
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StockReconciliationDAO.getBatchStockLimits reconciliationId=" + reconciliationId, e);
            return null;
        }
    }

    /**
     * Lưu Tồn thực tế của dòng đối chiếu.
     * QUAN TRỌNG: phương thức này KHÔNG cập nhật Products.Stock hoặc InventoryBatch.
     * Việc điều chỉnh tồn kho chỉ được thực hiện khi chốt phiên lúc 00:00.
     */
    public boolean updateActualStock(int reconciliationId, int newActualStock, int userId) {
        if (newActualStock < 0) return false;

        String selectSql = "SELECT CreatedAt FROM StockReconciliation WHERE ReconciliationID = ?";
        String updateSql = "UPDATE StockReconciliation "
                + "SET ActualStock = ?, Checked = 1, CheckedBy = ?, CheckedAt = CURRENT_TIMESTAMP "
                + "WHERE ReconciliationID = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement select = con.prepareStatement(selectSql);
             PreparedStatement update = con.prepareStatement(updateSql)) {

            select.setInt(1, reconciliationId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) return false;
                Timestamp createdAt = rs.getTimestamp("CreatedAt");
                if (createdAt == null
                        || !createdAt.toLocalDateTime().toLocalDate().equals(LocalDate.now())) {
                    return false;
                }
            }

            update.setInt(1, newActualStock);
            update.setInt(2, userId);
            update.setInt(3, reconciliationId);

            boolean ok = update.executeUpdate() == 1;
            if (ok) {
                AppEventBus.getInstance().publish(
                        new DataChangedEvent(DataChangedEvent.STOCK_RECONCILIATION));
            }
            return ok;
        } catch (Exception e) {
            AppLogger.getInstance().error(
                    ErrorCode.DB_UPDATE_FAIL,
                    "StockReconciliationDAO.updateActualStock id=" + reconciliationId, e);
            return false;
        }
    }

    /**
     * Chốt toàn bộ phiên của một ngày.
     * ActualStock chỉ được đưa vào Products/InventoryBatch tại bước này.
     *
     * @param sessionDate ngày của phiên cần chốt (ví dụ 2026-08-17 sẽ được chốt lúc 00:00 ngày 18)
     * @param closedByUserId người thực hiện chốt; 0 nếu chốt tự động
     */
    public boolean closeSessionAndApplyAdjustments(LocalDate sessionDate, int closedByUserId) {
        String selectSql = "SELECT r.ReconciliationID, r.ProductID, r.BatchID, "
                + "r.SystemStock, r.ActualStock, r.CreatedBy, r.Note "
                + "FROM StockReconciliation r "
                + "WHERE DATE(r.CreatedAt) = ? "
                + "FOR UPDATE";

        String updateBatchSql = "UPDATE InventoryBatch SET RemainingQty = ?, "
                + "Status = CASE WHEN ? <= 0 THEN 'DEPLETED' ELSE 'ACTIVE' END "
                + "WHERE BatchID = ? AND ProductID = ?";

        String updateProductSql = "UPDATE Products SET Stock = ? WHERE ProductID = ?";

        String insertTxSql = "INSERT INTO InventoryTransactions "
                + "(ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter, "
                + "RefTable, RefID, CreatedBy, Note) "
                + "VALUES (?, 'RECONCILE_ADJUST', ?, ?, ?, ?, 'StockReconciliation', ?, ?, ?)";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                java.util.List<int[]> productAdjustments = new java.util.ArrayList<>();

                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setDate(1, java.sql.Date.valueOf(sessionDate));

                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            int reconciliationId = rs.getInt("ReconciliationID");
                            int productId = rs.getInt("ProductID");
                            int batchId = rs.getInt("BatchID");
                            if (rs.wasNull()) batchId = 0;

                            int systemStock = rs.getInt("SystemStock");
                            int actualStock = rs.getInt("ActualStock");
                            int createdBy = rs.getInt("CreatedBy");
                            String note = rs.getString("Note");

                            if (actualStock < 0) {
                                con.rollback();
                                return false;
                            }

                            if (batchId > 0) {
                                try (PreparedStatement psBatch = con.prepareStatement(
                                        "SELECT Quantity, RemainingQty FROM InventoryBatch "
                                                + "WHERE BatchID = ? AND ProductID = ? FOR UPDATE")) {
                                    psBatch.setInt(1, batchId);
                                    psBatch.setInt(2, productId);
                                    try (ResultSet rb = psBatch.executeQuery()) {
                                        if (!rb.next()) {
                                            con.rollback();
                                            return false;
                                        }
                                        int batchQuantity = rb.getInt("Quantity");
                                        if (actualStock > batchQuantity) {
                                            con.rollback();
                                            return false;
                                        }
                                    }
                                }

                                try (PreparedStatement up = con.prepareStatement(updateBatchSql)) {
                                    up.setInt(1, actualStock);
                                    up.setInt(2, actualStock);
                                    up.setInt(3, batchId);
                                    up.setInt(4, productId);
                                    if (up.executeUpdate() != 1) {
                                        con.rollback();
                                        return false;
                                    }
                                }

                                int beforeProduct;
                                int afterProduct;
                                try (PreparedStatement q = con.prepareStatement(
                                        "SELECT Stock FROM Products WHERE ProductID = ? FOR UPDATE")) {
                                    q.setInt(1, productId);
                                    q.setInt(2, productId);
                                    try (ResultSet rp = q.executeQuery()) {
                                        if (!rp.next()) {
                                            con.rollback();
                                            return false;
                                        }
                                        beforeProduct = rp.getInt("Stock");
                                    }
                                }
                                try (PreparedStatement q = con.prepareStatement(
                                        "SELECT COALESCE(SUM(RemainingQty),0) "
                                                + "FROM InventoryBatch WHERE ProductID = ?")) {
                                    q.setInt(1, productId);
                                    try (ResultSet rp = q.executeQuery()) {
                                        if (!rp.next()) {
                                            con.rollback();
                                            return false;
                                        }
                                        afterProduct = rp.getInt(1);
                                    }
                                }

                                if (beforeProduct != afterProduct) {
                                    try (PreparedStatement up = con.prepareStatement(updateProductSql)) {
                                        up.setInt(1, afterProduct);
                                        up.setInt(2, productId);
                                        up.executeUpdate();
                                    }

                                    int diff = afterProduct - beforeProduct;
                                    try (PreparedStatement tx = con.prepareStatement(insertTxSql)) {
                                        tx.setInt(1, productId);
                                        tx.setString(2, diff > 0 ? "IN" : "OUT");
                                        tx.setInt(3, Math.abs(diff));
                                        tx.setInt(4, beforeProduct);
                                        tx.setInt(5, afterProduct);
                                        tx.setInt(6, reconciliationId);
                                        tx.setInt(7, closedByUserId > 0 ? closedByUserId : createdBy);
                                        tx.setString(8, "Chốt phiên đối chiếu ngày " + sessionDate
                                                + (note == null ? "" : " - " + note));
                                        tx.executeUpdate();
                                    }
                                }
                            } else {
                                int before = systemStock;
                                try (PreparedStatement q = con.prepareStatement(
                                        "SELECT Stock FROM Products WHERE ProductID = ? FOR UPDATE")) {
                                    q.setInt(1, productId);
                                    try (ResultSet rp = q.executeQuery()) {
                                        if (!rp.next()) {
                                            con.rollback();
                                            return false;
                                        }
                                        before = rp.getInt(1);
                                    }
                                }

                                if (before != actualStock) {
                                    try (PreparedStatement up = con.prepareStatement(updateProductSql)) {
                                        up.setInt(1, actualStock);
                                        up.setInt(2, productId);
                                        up.executeUpdate();
                                    }

                                    int diff = actualStock - before;
                                    try (PreparedStatement tx = con.prepareStatement(insertTxSql)) {
                                        tx.setInt(1, productId);
                                        tx.setString(2, diff > 0 ? "IN" : "OUT");
                                        tx.setInt(3, Math.abs(diff));
                                        tx.setInt(4, before);
                                        tx.setInt(5, actualStock);
                                        tx.setInt(6, reconciliationId);
                                        tx.setInt(7, closedByUserId > 0 ? closedByUserId : createdBy);
                                        tx.setString(8, "Chốt phiên đối chiếu ngày " + sessionDate);
                                        tx.executeUpdate();
                                    }
                                }
                            }
                        }
                    }
                }

                con.commit();
                AppEventBus.getInstance().publish(
                        new DataChangedEvent(DataChangedEvent.STOCK_RECONCILIATION));
                return true;
            } catch (Exception e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(
                    ErrorCode.DB_UPDATE_FAIL,
                    "StockReconciliationDAO.closeSessionAndApplyAdjustments date=" + sessionDate, e);
            return false;
        }
    }

    /**
     * Danh dau 1 san pham da duoc kiem ke / bo danh dau. Chi cho phep phien hom nay.
     * Khi danh dau, luu nguoi va thoi diem thao tac de cot Nguoi doi chieu/Thoi gian
     * phan anh dung nhan vien truc tiep xac nhan.
     */
    public boolean setChecked(int reconciliationId, boolean checked, int userId) {
        String selectSql = "SELECT CreatedAt FROM StockReconciliation WHERE ReconciliationID = ?";
        String updateSql = checked
                ? "UPDATE StockReconciliation SET Checked = 1, CheckedBy = ?, CheckedAt = CURRENT_TIMESTAMP WHERE ReconciliationID = ?"
                : "UPDATE StockReconciliation SET Checked = 0, CheckedBy = NULL, CheckedAt = NULL WHERE ReconciliationID = ?";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                    ps.setInt(1, reconciliationId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { con.rollback(); return false; }
                        Timestamp ts = rs.getTimestamp("CreatedAt");
                        if (ts == null || !ts.toLocalDateTime().toLocalDate().equals(LocalDate.now())) {
                            con.rollback(); return false;
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                    if (checked) {
                        ps.setInt(1, userId);
                        ps.setInt(2, reconciliationId);
                    } else {
                        ps.setInt(1, reconciliationId);
                    }
                    ps.executeUpdate();
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
                    "StockReconciliationDAO.setChecked id=" + reconciliationId, e);
            return false;
        }
    }

    // ===================== 2 METHOD MOI THEM =====================

    /**
     * Lay ngay cua phien doi chieu MOI NHAT trong CSDL (duoc dung de phat
     * hien da sang ngay moi chua tao phien). Tra ve null neu chua co dong nao.
     */
    public LocalDate getLatestSessionDate() {
        String sql = "SELECT CAST(CreatedAt AS DATE) AS SessionDate "
                + "FROM StockReconciliation ORDER BY CreatedAt DESC LIMIT 1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                java.sql.Date d = rs.getDate("SessionDate");
                return d != null ? d.toLocalDate() : null;
            }
            return null;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StockReconciliationDAO.getLatestSessionDate", e);
            return null;
        }
    }

    /**
     * Kiem tra xem ngay {@code date} co phai la "qua khu" so voi hom nay khong.
     * Dung de phong nguoi dung sua doi dong lich su (bao toan doi soat).
     */
    public boolean isDateLocked(LocalDate date) {
        if (date == null) return true;
        return date.isBefore(LocalDate.now());
    }
}