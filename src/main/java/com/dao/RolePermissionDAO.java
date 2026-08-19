package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Role;
import com.model.permission.AppPermission;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RolePermissionDAO {

    public void ensurePermissionsSeeded() {
        String sql = "INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (AppPermission permission : AppPermission.values()) {
                ps.setString(1, permission.name());
                ps.setString(2, permission.name());
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "RolePermissionDAO.ensurePermissionsSeeded", e);
        }
    }

    public boolean isRolePermissionsEmpty() {
        String sql = "SELECT COUNT(*) FROM RolePermissions";
        try (Connection con = DBConnection.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return !(rs.next() && rs.getInt(1) > 0);
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "RolePermissionDAO.isRolePermissionsEmpty", e);
            return true;
        }
    }

    public void seedDefaults(Map<Role, Set<AppPermission>> defaults) {
        ensurePermissionsSeeded();
        for (Map.Entry<Role, Set<AppPermission>> entry : defaults.entrySet()) {
            savePermissionsForRole(entry.getKey().name(), entry.getValue());
        }
    }

    // ---------- Theo enum (tương thích cũ) ----------

    public Set<AppPermission> getPermissionsByRole(Role role) {
        if (role == null) return EnumSet.noneOf(AppPermission.class);
        return getPermissionsByRoleCode(role.name());
    }

    public boolean savePermissionsForRole(Role role, Set<AppPermission> permissions) {
        if (role == null) return false;
        return savePermissionsForRole(role.name(), permissions);
    }

    // ---------- Theo RoleCode string (role động) ----------

    /**
     * @return null nếu lỗi DB; Set (có thể rỗng) nếu OK
     */
    public Set<AppPermission> getPermissionsByRoleCode(String roleCode) {
        Set<AppPermission> result = EnumSet.noneOf(AppPermission.class);
        if (roleCode == null || roleCode.isBlank()) return result;

        String sql = "SELECT p.PermissionCode " +
                "FROM RolePermissions rp " +
                "JOIN Roles r ON rp.RoleID = r.RoleID " +
                "JOIN Permissions p ON rp.PermissionID = p.PermissionID " +
                "WHERE r.RoleCode = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roleCode.trim().toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.add(AppPermission.valueOf(rs.getString("PermissionCode")));
                    } catch (IllegalArgumentException ignore) {
                        // permission cũ đã xóa khỏi enum
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "RolePermissionDAO.getPermissionsByRoleCode - role=" + roleCode, e);
            return null;
        }
        return result;
    }

    public boolean savePermissionsForRole(String roleCode, Set<AppPermission> permissions) {
        if (roleCode == null || roleCode.isBlank()) return false;
        String code = roleCode.trim().toUpperCase(Locale.ROOT);

        String deleteSql = "DELETE rp FROM RolePermissions rp " +
                "JOIN Roles r ON rp.RoleID = r.RoleID WHERE r.RoleCode = ?";
        String insertSql = "INSERT INTO RolePermissions (RoleID, PermissionID) " +
                "SELECT r.RoleID, p.PermissionID FROM Roles r, Permissions p " +
                "WHERE r.RoleCode = ? AND p.PermissionCode = ?";

        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement del = con.prepareStatement(deleteSql)) {
                    del.setString(1, code);
                    del.executeUpdate();
                }
                if (permissions != null && !permissions.isEmpty()) {
                    try (PreparedStatement ins = con.prepareStatement(insertSql)) {
                        for (AppPermission permission : permissions) {
                            ins.setString(1, code);
                            ins.setString(2, permission.name());
                            ins.addBatch();
                        }
                        ins.executeBatch();
                    }
                }
                con.commit();
                return true;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "RolePermissionDAO.savePermissionsForRole - role=" + code, e);
            return false;
        }
    }
}
