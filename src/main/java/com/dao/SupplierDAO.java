package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Supplier;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SupplierDAO extends SoftDeleteDAO<Supplier> {

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Suppliers";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "SupplierID, SupplierName, Address, Phone, Email, SuppliedItems";
    }

    @Override
    protected String getOrderBy() {
        return "SupplierID DESC";
    }

    // ---------------------------------------------------------------
    // SoftDeleteDAO hooks
    // ---------------------------------------------------------------

    @Override
    protected String getBaseTableName() {
        return "Suppliers";
    }

    @Override
    protected String getIdColumn() {
        return "SupplierID";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"SupplierName", "Phone", "Email", "SuppliedItems"};
    }

    @Override
    protected Supplier mapResultSet(ResultSet rs) throws SQLException {
        Supplier supplier = new Supplier();
        supplier.setSupplierId(rs.getInt("SupplierID"));
        supplier.setSupplierName(rs.getString("SupplierName"));
        supplier.setAddress(rs.getString("Address"));
        supplier.setPhone(rs.getString("Phone"));
        supplier.setEmail(rs.getString("Email"));
        supplier.setSuppliedItems(rs.getString("SuppliedItems"));
        return supplier;
    }

    public boolean insert(Supplier supplier) {
        String sql = "INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES (?, ?, ?, ?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            bindSupplier(ps, supplier);
            if (ps.executeUpdate() == 0) return false;

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    supplier.setSupplierId(keys.getInt(1));
                }
            }
            return true;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "SupplierDAO.insert - " + supplier.getSupplierName(), e);
            return false;
        }
    }

    public boolean update(Supplier supplier) {
        String sql = "UPDATE Suppliers SET SupplierName = ?, Address = ?, Phone = ?, Email = ?, SuppliedItems = ? WHERE SupplierID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            int nextIndex = bindSupplier(ps, supplier);
            ps.setInt(nextIndex, supplier.getSupplierId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "SupplierDAO.update - supplierId=" + supplier.getSupplierId(), e);
            return false;
        }
    }

    private int bindSupplier(PreparedStatement ps, Supplier supplier) throws SQLException {
        ps.setString(1, supplier.getSupplierName());
        ps.setString(2, supplier.getAddress());
        ps.setString(3, supplier.getPhone());
        ps.setString(4, supplier.getEmail());
        ps.setString(5, supplier.getSuppliedItems());
        return 6;
    }

    /**
     * Xoa mem nha cung cap (IsDeleted = 1). Khong con DELETE that o day.
     * Giữ tên deleteSupplier de khong phai doi goi o cac cho khac.
     */
    public boolean deleteSupplier(int supplierId) {
        return softDelete(supplierId);
    }

    /**
     * Xoa vinh vien: go lien ket SupplierProducts truoc roi DELETE nha cung cap.
     * Goi tu Thung rac khi nguoi dung xac nhan.
     */
    public boolean hardDeleteSupplier(int supplierId) {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM SupplierProducts WHERE SupplierID = ?")) {
                    ps.setInt(1, supplierId);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM Suppliers WHERE SupplierID = ?")) {
                    ps.setInt(1, supplierId);
                    int rows = ps.executeUpdate();
                    con.commit();
                    return rows > 0;
                }
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_DELETE_FAIL,
                    "SupplierDAO.hardDeleteSupplier - supplierId=" + supplierId, e);
            return false;
        }
    }

    public int countProducts(int supplierId) {
        String sql = "SELECT COUNT(*) FROM SupplierProducts WHERE SupplierID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, supplierId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "SupplierDAO.countProducts - supplierId=" + supplierId, e);
            return 0;
        }
    }

    public Map<Integer, Integer> countProductsGrouped(List<Integer> supplierIds) {
        Map<Integer, Integer> result = new HashMap<>();
        if (supplierIds == null || supplierIds.isEmpty()) return result;

        StringBuilder sql = new StringBuilder(
                "SELECT SupplierID, COUNT(*) AS Cnt FROM SupplierProducts WHERE SupplierID IN (");
        for (int i = 0; i < supplierIds.size(); i++) {
            sql.append(i == 0 ? "?" : ", ?");
        }
        sql.append(") GROUP BY SupplierID");

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {

            for (int i = 0; i < supplierIds.size(); i++) {
                ps.setInt(i + 1, supplierIds.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getInt("SupplierID"), rs.getInt("Cnt"));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierDAO.countProductsGrouped", e);
        }
        return result;
    }

    /** Danh sach nha cung cap dang hoat dong (chua xoa mem), sap xep theo ten. */
    public List<Supplier> findAllOrderByName() {
        String sql = "SELECT SupplierID, SupplierName, Address, Phone, Email, SuppliedItems "
                + "FROM Suppliers WHERE IsDeleted = 0 ORDER BY SupplierName";
        List<Supplier> result = new ArrayList<>();

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                result.add(mapResultSet(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "SupplierDAO.findAllOrderByName", e);
        }
        return result;
    }
}