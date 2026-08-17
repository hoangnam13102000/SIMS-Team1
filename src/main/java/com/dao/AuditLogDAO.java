package com.dao;

import com.model.ActivityLog;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AuditLogDAO extends BaseDAO<ActivityLog> {

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "AuditLogs a";
    }

    @Override
    protected String getColumns() {
        return "a.LogID, u.Username, a.Action, a.TableName, a.RecordID, a.OldValue, a.NewValue, a.Detail, a.CreatedAt";
    }

    @Override
    protected String getJoinClause() {
        return "LEFT JOIN Users u ON a.UserID = u.UserID";
    }

    @Override
    protected String getOrderBy() {
        return "a.CreatedAt DESC, a.LogID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"u.Username", "a.Detail", "a.TableName", "a.Action"};
    }

    @Override
    protected ActivityLog mapResultSet(ResultSet rs) throws SQLException {
        ActivityLog log = new ActivityLog();
        log.setLogId((int) rs.getLong("LogID"));
        String username = rs.getString("Username");
        log.setUsername(username != null ? username : "SYSTEM");
        log.setAction(rs.getString("Action"));
        log.setEntityType(rs.getString("TableName"));
        int recordId = rs.getInt("RecordID");
        log.setRecordId(rs.wasNull() ? null : recordId);
        log.setOldValue(rs.getString("OldValue"));
        log.setNewValue(rs.getString("NewValue"));
        log.setDescription(rs.getString("Detail"));
        log.setCreatedAt(rs.getTimestamp("CreatedAt"));
        return log;
    }

    /**
     * Ghi 1 dong audit log moi. Username duoc tra ve UserID qua subquery
     * thay vi query rieng UserDAO.findByUsername() truoc - gom lai trong 1
     * cau lenh INSERT duy nhat de tranh round-trip DB thua (ham nay chay
     * tren thread rieng cua AppLogger nen khong chan UI, nhung van nen gon).
     *
     * @return true neu ghi thanh cong.
     */
    public boolean insert(String username, String action, String tableName, Integer recordId,
                           String oldValue, String newValue, String detail) {
        String sql = "INSERT INTO AuditLogs (UserID, Action, TableName, RecordID, OldValue, NewValue, Detail) " +
                "VALUES ((SELECT UserID FROM Users WHERE Username = ?), ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, action);
            ps.setString(3, tableName);
            if (recordId != null) ps.setInt(4, recordId); else ps.setNull(4, Types.INTEGER);
            ps.setString(5, oldValue);
            ps.setString(6, newValue);
            ps.setString(7, detail);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("[AuditLogDAO] Ghi audit log that bai: " + e.getMessage());
            return false;
        }
    }

    /**
     * Loc theo tu khoa (username/mo ta/doi tuong/hanh dong) + hanh dong +
     * loai doi tuong + khoang thoi gian - moi tham so co the null/rong de
     * bo qua dieu kien tuong ung. Dung chung cho ca tai trang dau (moi tham
     * so null) va tim kiem/loc tren AuditLogPanel.
     * <p>
     * {@code actions}/{@code entityTypes} nhan NHIEU gia tri (IN-list) thay
     * vi 1 gia tri don, vi cung 1 nhan hien thi tieng Viet (vd "Tài khoản")
     * co the tuong ung voi nhieu gia tri RAW khac nhau trong DB (vd "USER"
     * ghi tu Java, "Users" ghi truc tiep tu trigger SQL) - xem AuditLogPanel
     * #setupFilters(). Dung List rong hoac null = khong loc theo dieu kien do.
     */
    public PaginationHelper.PaginationResult<ActivityLog> filter(
            int page, int pageSize, String keyword, List<String> actions, List<String> entityTypes,
            Date fromDate, Date toDate) {

        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.trim()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            String likeValue = "%" + escaped + "%";
            conditions.add("(u.Username LIKE ? ESCAPE '!' OR a.Detail LIKE ? ESCAPE '!' "
                    + "OR a.TableName LIKE ? ESCAPE '!' OR a.Action LIKE ? ESCAPE '!')");
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
            params.add(likeValue);
        }
        if (actions != null && !actions.isEmpty()) {
            conditions.add("a.Action IN (" + placeholders(actions.size()) + ")");
            params.addAll(actions);
        }
        if (entityTypes != null && !entityTypes.isEmpty()) {
            conditions.add("a.TableName IN (" + placeholders(entityTypes.size()) + ")");
            params.addAll(entityTypes);
        }
        if (fromDate != null) {
            conditions.add("a.CreatedAt >= ?");
            params.add(new Timestamp(fromDate.getTime()));
        }
        if (toDate != null) {
            conditions.add("a.CreatedAt <= ?");
            params.add(new Timestamp(toDate.getTime()));
        }

        String whereClause = conditions.isEmpty() ? null : String.join(" AND ", conditions);
        return getPaged(page, pageSize, whereClause, params.toArray());
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('?');
        }
        return sb.toString();
    }

    /** Danh sach cac gia tri Action / TableName da tung xuat hien - dung do dropdown loc tren AuditLogPanel. */
    public List<String> getDistinctActions() {
        return getDistinctValues("Action");
    }

    public List<String> getDistinctEntityTypes() {
        return getDistinctValues("TableName");
    }

    private List<String> getDistinctValues(String column) {
        List<String> values = new ArrayList<>();
        String sql = "SELECT DISTINCT " + column + " FROM AuditLogs WHERE " + column + " IS NOT NULL ORDER BY " + column;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String v = rs.getString(1);
                if (v != null && !v.isBlank()) values.add(v);
            }
        } catch (SQLException e) {
            com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.DB_QUERY_FAIL,
                    "AuditLogDAO.getDistinctValues(" + column + ")", e);
        }
        return values;
    }
    
    public AuditLogStats getStatsSummary() {
        String sql = "SELECT " +
                "COUNT(*) AS TotalLogs, " +
                "SUM(CASE WHEN CAST(a.CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE) THEN 1 ELSE 0 END) AS TodayLogs, " +
                "SUM(CASE WHEN a.Action = ? AND CAST(a.CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE) THEN 1 ELSE 0 END) AS FailedLoginsToday, " +
                "COUNT(DISTINCT CASE WHEN CAST(a.CreatedAt AS DATE) = CAST(CURRENT_TIMESTAMP AS DATE) THEN a.UserID END) AS ActiveUsersToday " +
                "FROM AuditLogs a";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ActivityLog.ACTION_LOGIN_FAILED);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new AuditLogStats(
                            rs.getInt("TotalLogs"),
                            rs.getInt("TodayLogs"),
                            rs.getInt("FailedLoginsToday"),
                            rs.getInt("ActiveUsersToday"));
                }
            }
        } catch (SQLException e) {
            com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.DB_QUERY_FAIL,
                    "AuditLogDAO.getStatsSummary", e);
        }
        return new AuditLogStats(0, 0, 0, 0);
    }

    /** So lieu tong quan cho hang StatCard - xem {@link #getStatsSummary()}. */
    public static class AuditLogStats {
        public final int totalLogs;
        public final int todayLogs;
        public final int failedLoginsToday;
        public final int activeUsersToday;

        public AuditLogStats(int totalLogs, int todayLogs, int failedLoginsToday, int activeUsersToday) {
            this.totalLogs = totalLogs;
            this.todayLogs = todayLogs;
            this.failedLoginsToday = failedLoginsToday;
            this.activeUsersToday = activeUsersToday;
        }
    }

    /** Xuat CSV/Excel: gioi han 5000 dong gan nhat de tranh export "treo" khi bang qua lon theo thoi gian. */
    public List<ActivityLog> getRecentForExport() {
        List<ActivityLog> list = new ArrayList<>();
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " " + getJoinClause() +
                " ORDER BY " + getOrderBy() + " LIMIT 5000";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (SQLException e) {
            com.core.log.AppLogger.getInstance().error(com.core.log.ErrorCode.DB_QUERY_FAIL,
                    "AuditLogDAO.getRecentForExport", e);
        }
        return list;
    }
}