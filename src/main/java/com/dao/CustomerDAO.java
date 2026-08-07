package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Customer;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class CustomerDAO extends SoftDeleteDAO<Customer> {

    // ---------------------------------------------------------------
    // Hook bắt buộc của BaseDAO - tái dùng getPaged()/search()/getAll()
    // ---------------------------------------------------------------

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Users u JOIN Customers c ON u.UserID = c.CustomerID";
    }

    @Override
    protected String getColumns() {
        return "u.UserID, u.Username, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.Status, c.CustomerCode, c.MemberPoint, c.CreatedAt";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getOrderBy() {
        return "u.UserID DESC";
    }

    // ---------------------------------------------------------------
    // Hook bắt buộc của SoftDeleteDAO - Users la bang GOC chua IsDeleted,
    // UserID la khoa chinh dung cho UPDATE khi soft-delete/restore.
    // ---------------------------------------------------------------

    @Override
    protected String getBaseTableName() {
        return "Users";
    }

    @Override
    protected String getIdColumn() {
        return "UserID";
    }

    @Override
    protected Customer mapResultSet(ResultSet rs) throws SQLException {
        Customer customer = new Customer();
        customer.setCustomerId(rs.getInt("UserID"));
        customer.setCustomerCode(rs.getString("CustomerCode"));
        customer.setUsername(rs.getString("Username"));
        customer.setFullName(rs.getString("FullName"));
        customer.setEmail(rs.getString("Email"));
        customer.setPhone(rs.getString("Phone"));
        customer.setAvatarUrl(rs.getString("AvatarUrl"));
        customer.setLocked(rs.getBoolean("IsLocked"));
        customer.setStatus(rs.getString("Status"));
        customer.setMemberPoint(rs.getInt("MemberPoint"));
        customer.setCreatedAt(rs.getTimestamp("CreatedAt"));
        return customer;
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"u.Username", "u.FullName", "u.Email", "u.Phone", "c.CustomerCode"};
    }

    /**
     * Tim 1 khach hang khop CHINH XAC theo CustomerCode (vd "CUS_0007") - dung
     * cho POS khi quet ma vach/the thanh vien. CustomerCode la ma ON DINH,
     * KHONG doi ngay ca khi doi CustomerID noi bo (khong ap dung o day vi
     * CustomerID = UserID co dinh, nhung tach rieng CustomerCode van tot hon
     * vi khong lo ID noi bo tang dan ra ngoai the in). Giong het pattern
     * ProductDAO.findActiveByCode - so sanh khong phan biet hoa/thuong va tu
     * dong bo qua khach da xoa mem (IsDeleted = 1).
     */
    public Customer findByCode(String customerCode) {
        if (customerCode == null || customerCode.isBlank()) return null;
        String sql = "SELECT " + getColumns() + " FROM " + getTableName()
                + " WHERE UPPER(c.CustomerCode) = UPPER(?) AND u.IsDeleted = 0";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, customerCode.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "CustomerDAO.findByCode - customerCode=" + customerCode, e);
            return null;
        }
    }

    // ---------------------------------------------------------------
    // Quản lý khách hàng (dành cho Admin)
    // ---------------------------------------------------------------

    /**
     * Admin cap nhat ho ten/email/sdt/trang thai (bang Users) va diem thanh
     * vien (bang Customers) cua 1 khach hang - trong cung 1 transaction.
     * Khong doi Username/mat khau o day (giong UserDAO.updateByAdmin).
     */
    public boolean updateByAdmin(Customer customer) {
        String updateUserSql = "UPDATE Users SET FullName = ?, Email = ?, Phone = ?, Status = ? WHERE UserID = ?";
        String updateCustomerSql = "UPDATE Customers SET MemberPoint = ? WHERE CustomerID = ?";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(updateUserSql)) {
                ps.setString(1, customer.getFullName());
                ps.setString(2, customer.getEmail());
                ps.setString(3, customer.getPhone());
                ps.setString(4, customer.getStatus());
                ps.setInt(5, customer.getCustomerId());
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }
            }

            try (PreparedStatement ps = con.prepareStatement(updateCustomerSql)) {
                ps.setInt(1, customer.getMemberPoint());
                ps.setInt(2, customer.getCustomerId());
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }
            }

            con.commit();
            return true;
        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "CustomerDAO.updateByAdmin - rollback that bai", rollbackEx);
                }
            }
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "CustomerDAO.updateByAdmin - customerId=" + customer.getCustomerId(), e);
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "CustomerDAO.updateByAdmin - dong connection that bai", closeEx);
                }
            }
        }
    }

    /** Khoa / mo khoa 1 tai khoan khach hang (dung chung co che voi UserDAO.setLocked - cung bang Users). Khong con duoc goi tu CustomerPanel (da bo icon khoa) nhung giu lai phong khi can dung lai. */
    public boolean setLocked(int customerId, boolean locked) {
        String sql = locked
                ? "UPDATE Users SET IsLocked = 1 WHERE UserID = ?"
                : "UPDATE Users SET IsLocked = 0, FailedLoginCount = 0 WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, customerId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "CustomerDAO.setLocked - customerId=" + customerId, e);
            return false;
        }
    }
}