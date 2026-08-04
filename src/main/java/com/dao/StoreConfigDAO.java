package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.utils.DBConnection;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DAO rất mỏng cho bảng StoreConfig (key-value, xem sql/SIMS.sql), theo đúng
 * phong cách ShiftDAO - không cần cả bộ máy BaseDAO (search/paging)
 * cho một bảng chỉ vài dòng cấu hình.
 *
 * Khoá quan trọng nhất hiện tại là VAT_RATE (%, ví dụ "8" = 8%) -
 * dùng chung cho cả hoá đơn tại quầy (PosPanel/InvoiceDAO) và đơn hàng online
 * (CartPanel/OrderDAO) thay vì hardcode rải rác nhiều nơi.
 */
public class StoreConfigDAO {

    public static final String KEY_VAT_RATE = "VAT_RATE";

    /** Giá trị mặc định khi bảng chưa được seed hoặc đọc lỗi - khớp DEFAULT 8 của cột Invoices.VATRate. */
    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("8");

    /** Đọc tỉ lệ VAT hiện hành (%). Không bao giờ trả về null - fallback DEFAULT_VAT_RATE nếu thiếu/lỗi. */
    public BigDecimal getVatRate() {
        String raw = getValue(KEY_VAT_RATE, null);
        if (raw == null || raw.isBlank()) return DEFAULT_VAT_RATE;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StoreConfigDAO.getVatRate - gia tri VAT_RATE khong hop le: " + raw, e);
            return DEFAULT_VAT_RATE;
        }
    }

    /** Đọc 1 giá trị cấu hình theo khoá; trả về defaultValue nếu chưa có / lỗi DB. */
    public String getValue(String key, String defaultValue) {
        String sql = "SELECT ConfigValue FROM StoreConfig WHERE ConfigKey = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : defaultValue;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StoreConfigDAO.getValue - key=" + key, e);
            return defaultValue;
        }
    }

    /** Toàn bộ cấu hình hiện có, giữ nguyên thứ tự khoá trong DB - dùng cho trang Cài đặt. */
    public Map<String, String> getAll() {
        Map<String, String> result = new LinkedHashMap<>();
        String sql = "SELECT ConfigKey, ConfigValue FROM StoreConfig ORDER BY ConfigKey";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                result.put(rs.getString("ConfigKey"), rs.getString("ConfigValue"));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "StoreConfigDAO.getAll", e);
        }
        return result;
    }

    /**
     * Ghi 1 giá trị cấu hình - update nếu khoá đã tồn tại, insert nếu chưa
     * (StoreConfig luôn được seed sẵn 4 khoá trong Insert_SIMS.sql nên nhánh
     * insert hiếm khi chạy tới, nhưng vẫn xử lý cho chắc).
     */
    public boolean setValue(String key, String value) {
        String updateSql = "UPDATE StoreConfig SET ConfigValue = ? WHERE ConfigKey = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(updateSql)) {
            ps.setString(1, value);
            ps.setString(2, key);
            if (ps.executeUpdate() > 0) return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "StoreConfigDAO.setValue - key=" + key, e);
            return false;
        }

        String insertSql = "INSERT INTO StoreConfig (ConfigKey, ConfigValue) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, key);
            ps.setString(2, value);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "StoreConfigDAO.setValue(insert) - key=" + key, e);
            return false;
        }
    }

    /**
     * Ghi nhiều giá trị trong 1 transaction (dùng cho form Cài đặt lưu tất cả
     * field 1 lần). Dùng MERGE (upsert) thay vì UPDATE đơn thuần để vẫn đúng
     * ngay cả khi 1 khoá nào đó chưa tồn tại sẵn trong bảng (vd DB cũ chưa
     * được seed đủ 4 khoá mặc định trong Insert_SIMS.sql).
     */
    public boolean setValues(Map<String, String> values) {
        String mergeSql = "MERGE StoreConfig AS target "
                + "USING (SELECT ? AS ConfigKey, ? AS ConfigValue) AS src "
                + "ON target.ConfigKey = src.ConfigKey "
                + "WHEN MATCHED THEN UPDATE SET ConfigValue = src.ConfigValue "
                + "WHEN NOT MATCHED THEN INSERT (ConfigKey, ConfigValue) VALUES (src.ConfigKey, src.ConfigValue);";
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(mergeSql)) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    ps.setString(1, e.getKey());
                    ps.setString(2, e.getValue());
                    ps.addBatch();
                }
                ps.executeBatch();
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "StoreConfigDAO.setValues", e);
            return false;
        }
    }
}