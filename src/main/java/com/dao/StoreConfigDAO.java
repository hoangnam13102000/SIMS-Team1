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

public class StoreConfigDAO {

    public static final String KEY_VAT_RATE = "VAT_RATE";
    /** So VND khach can chi de duoc cong 1 diem thanh vien (vd "10000" = 10.000d/diem). */
    public static final String KEY_POINT_RATE = "POINT_RATE";
    /** 1 diem = bao nhieu VND khi DOI diem tru tien (mac dinh 1000). */
    public static final String KEY_POINT_REDEEM_RATE = "POINT_REDEEM_RATE";
    public static final String KEY_DEFAULT_MARGIN = "DEFAULT_MARGIN";
    /**
     * Nguong gia tri (VND) cua tong hang IN (khach tra) trong 1 phieu doi/tra
     * ke tu do BAT BUOC Quan ly ban hang duyet truoc khi kho/hoa don goc duoc
     * dieu chinh (R4). Dung boi ReturnExchangeDAO#createReturnExchange -
     * xem StoreConfigDAO.getApprovalThreshold().
     */
    public static final String KEY_APPROVAL_THRESHOLD = "RETURN_APPROVAL_THRESHOLD";

    private static final BigDecimal DEFAULT_VAT_RATE = new BigDecimal("8");
    private static final BigDecimal DEFAULT_POINT_RATE = new BigDecimal("10000");
    private static final BigDecimal DEFAULT_POINT_REDEEM_RATE = new BigDecimal("1000");
    private static final BigDecimal DEFAULT_MARGIN = new BigDecimal("5000");
    private static final BigDecimal DEFAULT_APPROVAL_THRESHOLD = new BigDecimal("0");

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

    public BigDecimal getPointRate() {
        String raw = getValue(KEY_POINT_RATE, null);
        if (raw == null || raw.isBlank()) return DEFAULT_POINT_RATE;
        try {
            BigDecimal rate = new BigDecimal(raw.trim());
            return rate.signum() > 0 ? rate : DEFAULT_POINT_RATE;
        } catch (NumberFormatException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StoreConfigDAO.getPointRate - gia tri POINT_RATE khong hop le: " + raw, e);
            return DEFAULT_POINT_RATE;
        }
    }

    /**
     * So VND quy doi tu 1 diem thanh vien khi DOI (tru tien).
     * Vi du POINT_REDEEM_RATE=1000 → 1 diem = 1.000d. Khong bao gio null/am.
     */
    public BigDecimal getPointRedeemRate() {
        String raw = getValue(KEY_POINT_REDEEM_RATE, null);
        if (raw == null || raw.isBlank()) return DEFAULT_POINT_REDEEM_RATE;
        try {
            BigDecimal rate = new BigDecimal(raw.trim());
            return rate.signum() > 0 ? rate : DEFAULT_POINT_REDEEM_RATE;
        } catch (NumberFormatException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StoreConfigDAO.getPointRedeemRate - gia tri POINT_REDEEM_RATE khong hop le: " + raw, e);
            return DEFAULT_POINT_REDEEM_RATE;
        }
    }

    public BigDecimal getDefaultMargin() {
        String raw = getValue(KEY_DEFAULT_MARGIN, null);
        if (raw == null || raw.isBlank()) return DEFAULT_MARGIN;
        try {
            BigDecimal margin = new BigDecimal(raw.trim());
            return margin.signum() >= 0 ? margin : DEFAULT_MARGIN;
        } catch (NumberFormatException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StoreConfigDAO.getDefaultMargin - gia tri DEFAULT_MARGIN khong hop le: " + raw, e);
            return DEFAULT_MARGIN;
        }
    }

    public BigDecimal getApprovalThreshold() {
        String raw = getValue(KEY_APPROVAL_THRESHOLD, null);
        if (raw == null || raw.isBlank()) return DEFAULT_APPROVAL_THRESHOLD;
        try {
            BigDecimal threshold = new BigDecimal(raw.trim());
            return threshold.signum() >= 0 ? threshold : DEFAULT_APPROVAL_THRESHOLD;
        } catch (NumberFormatException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "StoreConfigDAO.getApprovalThreshold - gia tri RETURN_APPROVAL_THRESHOLD khong hop le: " + raw, e);
            return DEFAULT_APPROVAL_THRESHOLD;
        }
    }

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