package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.model.StockAlert;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;

/**
 * DAO cho canh bao het/sap het hang gui Quan ly kho (xem sql/SIMS.sql +
 * sql/Trigger_SIMS.sql). Cac dong StockAlerts gio day duoc trigger
 * trg_Products_AutoStockAlert TU DONG sinh ra khi Products.Stock giam
 * xuong <= MinStock (khong con NV ban hang bam chuong bao thu cong nua) -
 * {@link #create(StockAlert)} van duoc giu lai o day cho truong hop can
 * tao thu cong/goi lai tu code, nhung UI khong con goi den. Poll "chua
 * xem" duoc {@code com.service.StockAlertNotifyPoller} dam nhiem (giong
 * OrderNotifyPoller voi Orders.SeenByAdmin), StockAlertPanel dung
 * getPaged/search de hien thi danh sach cho Quan ly kho xu ly.
 */
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

    /** Con canh bao nao dang NEW/PLANNED (chua RESOLVED) cho san pham nay khong - dung de chan bao cao trung lap. */
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

    /**
     * Tao 1 bao cao het/sap het hang moi. Tu choi (tra ve false, KHONG dung
     * den DB) neu san pham nay dang co canh bao NEW/PLANNED chua xu ly -
     * tranh Quan ly kho nhan trung lap 1 SP nhieu lan tu nhieu NV ban hang
     * hoac nhieu lan bam trong cung 1 ca.
     */
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

    /** Danh dau TAT CA canh bao hien tai la da xem (khi Quan ly kho mo trang "Canh bao ton kho"). */
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

    /** Quan ly kho danh dau "Da len ke hoach nhap bo sung" (NEW/PLANNED -> PLANNED). */
    public boolean markPlanned(int alertId) {
        return updateStatus(alertId, "PLANNED", null);
    }

    /** Quan ly kho danh dau da xu ly xong (nhap hang ve / het van de) - ghi nhan nguoi xu ly + thoi gian. */
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
            // QUAN TRONG: SQL Server KHONG nem loi khi WHERE khong khop dong nao -
            // phai tu kiem tra so dong bi anh huong (xem ghi chu tuong tu o OrderDAO).
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
}