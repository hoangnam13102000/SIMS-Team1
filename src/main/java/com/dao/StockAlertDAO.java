package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.StockAlert;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class StockAlertDAO extends BaseDAO<StockAlert> {
    private static final String BASE_TABLE = "StockAlerts sa";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() { return BASE_TABLE; }

    @Override
    protected String getJoinClause() {
        return "JOIN Products p ON sa.ProductID = p.ProductID "
                + "LEFT JOIN Users ru ON sa.ReportedBy = ru.UserID "
                + "LEFT JOIN Users rv ON sa.ResolvedBy = rv.UserID";
    }

    @Override
    protected String getColumns() {
        return "sa.AlertID, sa.ProductID, p.ProductCode, p.ProductName, p.MinStock, "
                + "sa.AlertType, sa.StockAtReport, sa.Note, "
                + "sa.ReportedBy, ru.FullName AS ReportedByName, sa.CreatedAt, sa.Status, "
                + "sa.SeenByInventoryManager, sa.ResolvedBy, rv.FullName AS ResolvedByName, sa.ResolvedAt";
    }

    @Override
    protected String getOrderBy() { return "sa.CreatedAt DESC, sa.AlertID DESC"; }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"p.ProductName", "p.ProductCode"};
    }

    @Override
    protected StockAlert mapResultSet(ResultSet rs) throws SQLException {
        StockAlert alert = new StockAlert();
        alert.setAlertId(rs.getInt("AlertID"));
        alert.setProductId(rs.getInt("ProductID"));
        alert.setProductCode(rs.getString("ProductCode"));
        alert.setProductName(rs.getString("ProductName"));
        alert.setMinStock(rs.getInt("MinStock"));
        alert.setAlertType(rs.getString("AlertType"));
        alert.setStockAtReport(rs.getInt("StockAtReport"));
        alert.setNote(rs.getString("Note"));
        int reportedBy = rs.getInt("ReportedBy");
        alert.setReportedBy(rs.wasNull() ? null : reportedBy);
        alert.setReportedByName(rs.getString("ReportedByName"));
        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) alert.setCreatedAt(createdAt.toLocalDateTime());
        alert.setStatus(rs.getString("Status"));
        alert.setSeenByInventoryManager(rs.getBoolean("SeenByInventoryManager"));
        int resolvedBy = rs.getInt("ResolvedBy");
        alert.setResolvedBy(rs.wasNull() ? null : resolvedBy);
        alert.setResolvedByName(rs.getString("ResolvedByName"));
        Timestamp resolvedAt = rs.getTimestamp("ResolvedAt");
        if (resolvedAt != null) alert.setResolvedAt(resolvedAt.toLocalDateTime());
        return alert;
    }

    public boolean hasActiveAlert(int productId) {
        String sql = "SELECT COUNT(*) FROM StockAlerts WHERE ProductID = ? AND Status <> 'RESOLVED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockAlertDAO.hasActiveAlert", e);
            return false;
        }
    }

    public boolean create(StockAlert alert) {
        if (hasActiveAlert(alert.getProductId())) {
            return false;
        }
        String sql = "INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, Note, ReportedBy) "
                + "VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, alert.getProductId());
            ps.setString(2, alert.getAlertType());
            ps.setInt(3, alert.getStockAtReport());
            if (alert.getNote() == null || alert.getNote().isBlank()) {
                ps.setNull(4, java.sql.Types.NVARCHAR);
            } else {
                ps.setString(4, alert.getNote());
            }
            if (alert.getReportedBy() == null) {
                ps.setNull(5, java.sql.Types.INTEGER);
            } else {
                ps.setInt(5, alert.getReportedBy());
            }
            int rows = ps.executeUpdate();
            if (rows == 0) return false;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) alert.setAlertId(keys.getInt(1));
            }
            AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.STOCK_ALERT));
            return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "StockAlertDAO.create", e);
            return false;
        }
    }

    public List<StockAlert> getUnseenForInventoryManager() {
        return getByCondition("sa.SeenByInventoryManager = 0");
    }

    public int countActive() {
        String sql = "SELECT COUNT(*) FROM StockAlerts WHERE Status <> 'RESOLVED'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockAlertDAO.countActive", e);
            return 0;
        }
    }

    public int countUnseen() {
        String sql = "SELECT COUNT(*) FROM StockAlerts WHERE SeenByInventoryManager = 0";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StockAlertDAO.countUnseen", e);
            return 0;
        }
    }

    public boolean markAllSeen() {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE StockAlerts SET SeenByInventoryManager = 1 WHERE SeenByInventoryManager = 0")) {
            ps.executeUpdate();
            return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "StockAlertDAO.markAllSeen", e);
            return false;
        }
    }

    public boolean markPlanned(int alertId) {
        return updateStatus(alertId, "PLANNED", null);
    }

    public boolean resolve(int alertId, int resolvedByUserId) {
        return updateStatus(alertId, "RESOLVED", resolvedByUserId);
    }

    private boolean updateStatus(int alertId, String newStatus, Integer resolvedByUserId) {
        String sql = "RESOLVED".equals(newStatus)
                ? "UPDATE StockAlerts SET Status = ?, ResolvedBy = ?, ResolvedAt = GETDATE() WHERE AlertID = ?"
                : "UPDATE StockAlerts SET Status = ? WHERE AlertID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if ("RESOLVED".equals(newStatus)) {
                ps.setString(1, newStatus);
                ps.setInt(2, resolvedByUserId);
                ps.setInt(3, alertId);
            } else {
                ps.setString(1, newStatus);
                ps.setInt(2, alertId);
            }
            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.STOCK_ALERT));
            }
            return updated;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "StockAlertDAO.updateStatus", e);
            return false;
        }
    }

    // ================================================================
    // ====== THÊM MỚI: LỌC THEO LOẠI SP + KHOẢNG NGÀY ======
    // ================================================================

    /**
     * Phân trang có lọc: loại SP (categoryId null = tất cả) + khoảng ngày.
     */
    public PaginationHelper.PaginationResult<StockAlert> getPagedFiltered(
            int page, int pageSize, Integer categoryId, LocalDate from, LocalDate to) {
        FilterContext ctx = buildWhereAndParams(null, categoryId, from, to);
        return getPaged(page, pageSize, ctx.where, ctx.params);
    }

    /**
     * Tìm kiếm text + lọc loại SP + lọc ngày (AND với nhau).
     */
    public PaginationHelper.PaginationResult<StockAlert> searchFiltered(
            String keyword, int page, int pageSize, Integer categoryId, LocalDate from, LocalDate to) {
        String[] columns = getSearchableColumns();
        if (keyword == null || keyword.trim().isEmpty() || columns.length == 0) {
            return getPagedFiltered(page, pageSize, categoryId, from, to);
        }
        FilterContext ctx = buildWhereAndParams(keyword, categoryId, from, to);
        return getPaged(page, pageSize, ctx.where, ctx.params);
    }

    /**
     * Lấy tất cả (không phân trang) có lọc — dùng cho xuất CSV/Excel.
     */
    public List<StockAlert> getAllFiltered(Integer categoryId, LocalDate from, LocalDate to) {
        FilterContext ctx = buildWhereAndParams(null, categoryId, from, to);
        if (ctx.where == null || ctx.where.isEmpty()) return getAll();
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT ").append(getColumns()).append(" FROM ").append(getTableName());
        String jc = getJoinClause();
        if (jc != null && !jc.isEmpty()) sql.append(" ").append(jc);
        sql.append(" WHERE ").append(ctx.where).append(" ORDER BY ").append(getOrderBy());
        List<StockAlert> list = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            int i = 1;
            for (Object p : ctx.params) ps.setObject(i++, p);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StockAlertDAO.getAllFiltered", e);
        }
        return list;
    }

    /**
     * Build where + params cho BaseDAO varargs.
     * Kết hợp: (từ khóa OR trên ProductName/ProductCode)
     *        AND (p.CategoryID = ? nếu có)
     *        AND (sa.CreatedAt >= ? nếu có from)
     *        AND (sa.CreatedAt <= ? nếu có to)
     */
    private FilterContext buildWhereAndParams(String keyword, Integer categoryId, LocalDate from, LocalDate to) {
        List<Object> params = new ArrayList<>();
        List<String> conds = new ArrayList<>();

        // 1) Từ khóa tìm kiếm
        String[] columns = getSearchableColumns();
        if (keyword != null && !keyword.trim().isEmpty() && columns.length > 0) {
            String escaped = keyword.trim()
                    .replace("[", "[[]").replace("%", "[%]").replace("_", "[_]");
            String likeValue = "%" + escaped + "%";
            StringBuilder or = new StringBuilder("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) or.append(" OR ");
                or.append(columns[i]).append(" LIKE ?");
                params.add(likeValue);
            }
            or.append(")");
            conds.add(or.toString());
        }

        // 2) Lọc theo Loại sản phẩm (dùng Products.CategoryID, không cần JOIN Categories)
        if (categoryId != null) {
            conds.add("p.CategoryID = ?");
            params.add(categoryId);
        }

        // 3) Lọc theo Từ ngày (CreatedAt >= from 00:00:00)
        if (from != null) {
            conds.add("sa.CreatedAt >= ?");
            params.add(Timestamp.valueOf(LocalDateTime.of(from, LocalTime.MIN)));
        }

        // 4) Lọc theo Đến ngày (CreatedAt <= to 23:59:59.999)
        if (to != null) {
            conds.add("sa.CreatedAt <= ?");
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