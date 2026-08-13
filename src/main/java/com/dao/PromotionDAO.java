package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Promotion;
import com.utils.DBConnection;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
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
                + "MinOrderAmount, StartDate, EndDate, UsageLimit, UsedCount, IsActive, "
                + "ShowOnBanner, BannerSortOrder, CreatedBy, CreatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "PromotionID DESC";
    }

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
        try {
            p.setShowOnBanner(rs.getBoolean("ShowOnBanner"));
        } catch (SQLException ignored) {
            p.setShowOnBanner(false);
        }
        try {
            int order = rs.getInt("BannerSortOrder");
            p.setBannerSortOrder(rs.wasNull() ? null : order);
        } catch (SQLException ignored) {
            p.setBannerSortOrder(null);
        }
        p.setCreatedBy(rs.getInt("CreatedBy"));
        java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
        p.setCreatedAt(createdAt != null ? createdAt.toLocalDateTime() : null);
        return p;
    }

    public boolean insert(Promotion p) {
        String sql = "INSERT INTO Promotions (Code, Name, DiscountType, DiscountValue, MaxDiscountAmount, "
                + "MinOrderAmount, StartDate, EndDate, UsageLimit, IsActive, ShowOnBanner, BannerSortOrder, "
                + "CreatedBy, CreatedAt) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int next = bindPromotion(ps, p);
            ps.setObject(next++, p.getCreatedBy() > 0 ? p.getCreatedBy() : null);
            ps.setTimestamp(next, java.sql.Timestamp.valueOf(LocalDateTime.now()));

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
                + "IsActive = ?, ShowOnBanner = ?, BannerSortOrder = ? WHERE PromotionID = ?";
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

    /** Gán tham số 1..12: Code..BannerSortOrder. @return index tiếp theo (13) */
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
        ps.setBoolean(11, p.isShowOnBanner());
        ps.setObject(12, p.getBannerSortOrder());
        return 13;
    }

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
     * Tìm kiếm + lọc khuyến mãi theo từ khóa (mã/tên) và/hoặc khoảng thời gian
     * hiệu lực. Khoảng [fromDate, toDate] lấy các CTKM giao với khoảng đó:
     * {@code StartDate <= toDate AND EndDate >= fromDate}.
     * Cả hai đầu có thể null nếu không lọc ngày.
     */
    public PaginationHelper.PaginationResult<Promotion> getPagedFiltered(
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
        // Giao khoảng hiệu lực với [from, to]
        if (fromDate != null) {
            conditions.add("EndDate >= ?");
            params.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            conditions.add("StartDate <= ?");
            params.add(Date.valueOf(toDate));
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

    public boolean hardDeletePromotion(int promotionId) {
        return hardDelete(promotionId);
    }

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

    /**
     * Mã đang được quảng bá trên banner/carousel:
     * ShowOnBanner=1, IsActive=1, IsDeleted=0, còn trong khoảng ngày, còn lượt dùng.
     */
    public List<Promotion> findBannerPromotions() {
        String sql = "SELECT " + getColumns() + " FROM Promotions "
                + "WHERE IsDeleted = 0 AND IsActive = 1 AND ShowOnBanner = 1 "
                + "  AND StartDate <= CAST(GETDATE() AS DATE) "
                + "  AND EndDate   >= CAST(GETDATE() AS DATE) "
                + "  AND (UsageLimit IS NULL OR UsedCount < UsageLimit) "
                + "ORDER BY CASE WHEN BannerSortOrder IS NULL THEN 1 ELSE 0 END, "
                + "         BannerSortOrder ASC, CreatedAt DESC";
        List<Promotion> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) result.add(mapResultSet(rs));
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "PromotionDAO.findBannerPromotions", e);
        }
        return result;
    }
}