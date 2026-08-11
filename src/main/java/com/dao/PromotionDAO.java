package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Promotion;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PromotionDAO extends SoftDeleteDAO<Promotion> {

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Promotions";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "PromotionID, Code, Name, DiscountType, DiscountValue, MaxDiscountAmount, "
                + "MinOrderAmount, StartDate, EndDate, UsageLimit, UsedCount, IsActive, CreatedBy, CreatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "PromotionID DESC";
    }

    // ---------------------------------------------------------------
    // SoftDeleteDAO hooks
    // ---------------------------------------------------------------

    @Override
    protected String getBaseTableName() {
        return "Promotions";
    }

    @Override
    protected String getIdColumn() {
        return "PromotionID";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"Code", "Name"};
    }

    @Override
    protected Promotion mapResultSet(ResultSet rs) throws SQLException {
        Promotion p = new Promotion();
        p.setPromotionId(rs.getInt("PromotionID"));
        p.setCode(rs.getString("Code"));
        p.setName(rs.getString("Name"));
        p.setDiscountType(rs.getString("DiscountType"));
        p.setDiscountValue(rs.getBigDecimal("DiscountValue"));
        p.setMaxDiscountAmount(rs.getBigDecimal("MaxDiscountAmount"));
        p.setMinOrderAmount(rs.getBigDecimal("MinOrderAmount"));
        Date start = rs.getDate("StartDate");
        p.setStartDate(start != null ? start.toLocalDate() : null);
        Date end = rs.getDate("EndDate");
        p.setEndDate(end != null ? end.toLocalDate() : null);
        int usageLimit = rs.getInt("UsageLimit");
        p.setUsageLimit(rs.wasNull() ? null : usageLimit);
        p.setUsedCount(rs.getInt("UsedCount"));
        p.setActive(rs.getBoolean("IsActive"));
        p.setCreatedBy(rs.getInt("CreatedBy"));
        java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
        p.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return p;
    }

    public boolean insert(Promotion p) {
        String sql = "INSERT INTO Promotions (Code, Name, DiscountType, DiscountValue, MaxDiscountAmount, "
                + "MinOrderAmount, StartDate, EndDate, UsageLimit, IsActive, CreatedBy, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindPromotion(ps, p);
            ps.setObject(11, p.getCreatedBy() > 0 ? p.getCreatedBy() : null);
            ps.setTimestamp(12, java.sql.Timestamp.valueOf(LocalDateTime.now()));

            if (ps.executeUpdate() == 0) return false;

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) p.setPromotionId(keys.getInt(1));
            }
            return true;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "PromotionDAO.insert - " + p.getCode(), e);
            return false;
        }
    }

    public boolean update(Promotion p) {
        String sql = "UPDATE Promotions SET Code = ?, Name = ?, DiscountType = ?, DiscountValue = ?, "
                + "MaxDiscountAmount = ?, MinOrderAmount = ?, StartDate = ?, EndDate = ?, UsageLimit = ?, "
                + "IsActive = ? WHERE PromotionID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int nextIndex = bindPromotion(ps, p);
            ps.setInt(nextIndex, p.getPromotionId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "PromotionDAO.update - promotionId=" + p.getPromotionId(), e);
            return false;
        }
    }

    private int bindPromotion(PreparedStatement ps, Promotion p) throws SQLException {
        ps.setString(1, p.getCode());
        ps.setString(2, p.getName());
        ps.setString(3, p.getDiscountType());
        ps.setBigDecimal(4, p.getDiscountValue());
        ps.setBigDecimal(5, p.getMaxDiscountAmount());
        ps.setBigDecimal(6, p.getMinOrderAmount() != null ? p.getMinOrderAmount() : BigDecimal.ZERO);
        ps.setDate(7, p.getStartDate() != null ? Date.valueOf(p.getStartDate()) : null);
        ps.setDate(8, p.getEndDate() != null ? Date.valueOf(p.getEndDate()) : null);
        ps.setObject(9, p.getUsageLimit());
        ps.setBoolean(10, p.isActive());
        return 11;
    }

    /** true neu Code da ton tai o 1 khuyen mai KHAC (dung khi validate form, tranh trung ma). */
    public boolean codeExists(String code, Integer excludePromotionId) {
        String sql = "SELECT COUNT(*) FROM Promotions WHERE Code = ? AND IsDeleted = 0"
                + (excludePromotionId != null ? " AND PromotionID <> ?" : "");
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            if (excludePromotionId != null) ps.setInt(2, excludePromotionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "PromotionDAO.codeExists - " + code, e);
            return false;
        }
    }

    /**
     * Tra cuu 1 khuyen mai theo ma (khong phan biet hoa/thuong), dung cho noi
     * ap dung ma giam gia (vd POS) o cac buoc tich hop sau nay. Chi tra ve
     * ban ghi CHUA xoa mem; con dieu kien hieu luc/luot dung do
     * {@link Promotion#calculateDiscount} tu quyet dinh o tang goi.
     */
    public Promotion findByCode(String code) {
        if (code == null || code.isBlank()) return null;
        String sql = "SELECT " + getColumns() + " FROM Promotions WHERE IsDeleted = 0 AND UPPER(Code) = UPPER(?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "PromotionDAO.findByCode - " + code, e);
            return null;
        }
    }

    /** Tang UsedCount len 1 - goi sau khi 1 don hang da ap dung thanh cong ma nay. */
    public boolean incrementUsedCount(int promotionId) {
        String sql = "UPDATE Promotions SET UsedCount = UsedCount + 1 WHERE PromotionID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, promotionId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "PromotionDAO.incrementUsedCount - promotionId=" + promotionId, e);
            return false;
        }
    }

    /** Xoa vinh vien (chi goi tu man hinh Thung rac). Khong co bang con nao tham chieu Promotions nen xoa thang. */
    public boolean hardDeletePromotion(int promotionId) {
        return hardDelete(promotionId);
    }

    /** Danh sach khuyen mai dang hoat dong (IsActive=1, chua xoa mem), sap xep theo ten - dung cho combo chon nhanh. */
    public List<Promotion> findAllActiveOrderByName() {
        String sql = "SELECT " + getColumns() + " FROM Promotions WHERE IsDeleted = 0 AND IsActive = 1 ORDER BY Name";
        List<Promotion> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapResultSet(rs));
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "PromotionDAO.findAllActiveOrderByName", e);
        }
        return result;
    }
}