package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.ExceptionReport;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;

/**
 * DAO cho "Báo cáo ngoại lệ" (xem bảng ExceptionReports trong sql/SIMS.sql) -
 * NV bán hàng gửi báo cáo tự do (SP khách cần mua nhưng chưa có trong hệ
 * thống, khách yêu cầu SP đặc biệt, tình huống bất thường khác), Quản lý
 * bán hàng xem và đánh dấu đã xử lý.
 * <p>
 * Đơn giản hơn StockAlertDAO/ReturnExchangeDAO: chỉ 2 trạng thái
 * PENDING -&gt; HANDLED, không có tác động đến kho/hóa đơn nên không cần
 * transaction hay trigger.
 */
public class ExceptionReportDAO extends BaseDAO<ExceptionReport> {

    private static final String BASE_TABLE = "ExceptionReports er";

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() { return BASE_TABLE; }

    @Override
    protected String getJoinClause() {
        return "JOIN Users cu ON er.CreatedBy = cu.UserID "
                + "LEFT JOIN Users hu ON er.HandledBy = hu.UserID";
    }

    @Override
    protected String getColumns() {
        return "er.ReportID, er.CreatedBy, cu.FullName AS CreatedByName, er.Content, er.Status, "
                + "er.HandledBy, hu.FullName AS HandledByName, er.HandledAt, er.CreatedAt";
    }

    @Override
    protected String getOrderBy() { return "er.CreatedAt DESC, er.ReportID DESC"; }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"er.Content", "cu.FullName"};
    }

    @Override
    protected ExceptionReport mapResultSet(ResultSet rs) throws SQLException {
        ExceptionReport report = new ExceptionReport();
        report.setReportId(rs.getInt("ReportID"));
        report.setCreatedBy(rs.getInt("CreatedBy"));
        report.setCreatedByName(rs.getString("CreatedByName"));
        report.setContent(rs.getString("Content"));
        report.setStatus(rs.getString("Status"));

        int handledBy = rs.getInt("HandledBy");
        report.setHandledBy(rs.wasNull() ? null : handledBy);
        report.setHandledByName(rs.getString("HandledByName"));

        Timestamp handledAt = rs.getTimestamp("HandledAt");
        report.setHandledAt(handledAt != null ? handledAt.toLocalDateTime() : null);

        Timestamp createdAt = rs.getTimestamp("CreatedAt");
        report.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return report;
    }

    /** NV ban hang gui 1 bao cao ngoai le moi (Status mac dinh PENDING). */
    public boolean create(String content, int createdBy) {
        if (content == null || content.isBlank()) return false;

        String sql = "INSERT INTO ExceptionReports (CreatedBy, Content) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, createdBy);
            ps.setString(2, content.trim());

            int rows = ps.executeUpdate();
            if (rows == 0) return false;

            AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.EXCEPTION_REPORT));
            return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "ExceptionReportDAO.create", e);
            return false;
        }
    }

    /** Quan ly ban hang danh dau 1 bao cao dang PENDING la da xu ly xong. */
    public boolean handle(int reportId, int handledByUserId) {
        String sql = "UPDATE ExceptionReports SET Status = 'HANDLED', HandledBy = ?, HandledAt = GETDATE() "
                + "WHERE ReportID = ? AND Status = 'PENDING'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, handledByUserId);
            ps.setInt(2, reportId);
            // QUAN TRONG: SQL Server KHONG nem loi khi WHERE khong khop dong nao -
            // phai tu kiem tra so dong bi anh huong (xem ghi chu tuong tu o OrderDAO/StockAlertDAO).
            boolean updated = ps.executeUpdate() > 0;
            if (updated) {
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.EXCEPTION_REPORT));
            }
            return updated;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "ExceptionReportDAO.handle", e);
            return false;
        }
    }

    public int countPending() {
        String sql = "SELECT COUNT(*) FROM ExceptionReports WHERE Status = 'PENDING'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "ExceptionReportDAO.countPending", e);
            return 0;
        }
    }
}