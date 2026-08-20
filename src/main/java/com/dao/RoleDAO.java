package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.AppRole;
import com.model.Role;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * CRUD bảng {@code Roles}. Role hệ thống (IsSystem=1) không được xóa.
 * Role mới do Admin tạo: IsSystem=0.
 */
public class RoleDAO {

    /** RoleCode: chữ hoa, số, gạch dưới; 2–30 ký tự; bắt đầu bằng chữ. */
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]{1,29}$");

    private Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    private AppRole map(ResultSet rs) throws SQLException {
        AppRole r = new AppRole();
        r.setRoleId(rs.getInt("RoleID"));
        r.setRoleCode(rs.getString("RoleCode"));
        r.setRoleName(rs.getString("RoleName"));
        r.setDescription(rs.getString("Description"));
        try {
            r.setSystemRole(rs.getBoolean("IsSystem"));
        } catch (SQLException e) {
            // Cột chưa migrate → coi role trong enum là system
            r.setSystemRole(Role.isSystemCode(r.getRoleCode()));
        }
        return r;
    }

    /** Tất cả role (kể cả CUSTOMER) — dùng khi gán user. */
    public List<AppRole> findAll() {
        String sql = "SELECT RoleID, RoleCode, RoleName, Description, "
                + "COALESCE(IsSystem, 0) AS IsSystem FROM Roles ORDER BY IsSystem DESC, RoleName ASC";
        // Fallback nếu chưa có cột IsSystem
        String sqlLegacy = "SELECT RoleID, RoleCode, RoleName, Description FROM Roles ORDER BY RoleName ASC";
        List<AppRole> list = new ArrayList<>();
        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(map(rs));
            } catch (SQLException e) {
                list.clear();
                try (PreparedStatement ps = con.prepareStatement(sqlLegacy);
                     ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AppRole r = new AppRole();
                        r.setRoleId(rs.getInt("RoleID"));
                        r.setRoleCode(rs.getString("RoleCode"));
                        r.setRoleName(rs.getString("RoleName"));
                        r.setDescription(rs.getString("Description"));
                        r.setSystemRole(Role.isSystemCode(r.getRoleCode()));
                        list.add(r);
                    }
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "RoleDAO.findAll", e);
        }
        return list;
    }

    /**
     * Thứ tự hiển thị cố định trên trang Phân quyền / danh sách role hệ thống.
     * ADMIN luôn đứng đầu; role tùy chỉnh (không nằm trong map) xếp sau, theo tên.
     */
    private static final Map<String, Integer> MANAGED_ROLE_ORDER = Map.of(
            "ADMIN", 0,
            "INVENTORY_MANAGER", 1,
            "SALES_MANAGER", 2,
            "SALES_STAFF", 3
    );

    /**
     * Role quản lý trên trang RBAC (loại CUSTOMER — không dùng AdminMainFrame).
     * Sắp xếp: Quản trị viên → Quản lý kho → Quản lý bán hàng → Nhân viên bán hàng
     * → các role tùy chỉnh (theo tên).
     */
    public List<AppRole> findManagedRoles() {
        List<AppRole> all = findAll();
        List<AppRole> managed = new ArrayList<>();
        for (AppRole r : all) {
            if (!r.isCustomer()) managed.add(r);
        }
        managed.sort(Comparator
                .comparingInt((AppRole r) -> MANAGED_ROLE_ORDER.getOrDefault(
                        r.getRoleCode() != null ? r.getRoleCode().toUpperCase(Locale.ROOT) : "",
                        100))
                .thenComparing(r -> r.getRoleName() != null ? r.getRoleName() : "",
                        String.CASE_INSENSITIVE_ORDER));
        return managed;
    }

    public AppRole findByCode(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) return null;
        String sql = "SELECT RoleID, RoleCode, RoleName, Description, "
                + "COALESCE(IsSystem, 0) AS IsSystem FROM Roles WHERE RoleCode = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roleCode.trim().toUpperCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return map(rs);
            }
        } catch (SQLException e) {
            // legacy without IsSystem
            try (Connection con = getConnection();
                 PreparedStatement ps = con.prepareStatement(
                         "SELECT RoleID, RoleCode, RoleName, Description FROM Roles WHERE RoleCode = ?")) {
                ps.setString(1, roleCode.trim().toUpperCase(Locale.ROOT));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        AppRole r = new AppRole();
                        r.setRoleId(rs.getInt("RoleID"));
                        r.setRoleCode(rs.getString("RoleCode"));
                        r.setRoleName(rs.getString("RoleName"));
                        r.setDescription(rs.getString("Description"));
                        r.setSystemRole(Role.isSystemCode(r.getRoleCode()));
                        return r;
                    }
                }
            } catch (SQLException e2) {
                AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "RoleDAO.findByCode", e2);
            }
        }
        return null;
    }

    /**
     * Tạo role mới. Trả về null nếu thành công; ngược lại chuỗi lỗi hiển thị UI.
     */
    public String create(String roleCode, String roleName, String description) {
        if (roleCode == null || roleCode.isBlank()) return "Mã vai trò không được để trống.";
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        if (!CODE_PATTERN.matcher(code).matches()) {
            return "Mã vai trò chỉ gồm A-Z, 0-9, _ ; bắt đầu bằng chữ; dài 2–30 ký tự.";
        }
        if (roleName == null || roleName.isBlank()) return "Tên vai trò không được để trống.";
        if (findByCode(code) != null) return "Mã vai trò \"" + code + "\" đã tồn tại.";

        String sql = "INSERT INTO Roles (RoleCode, RoleName, Description, IsSystem) VALUES (?, ?, ?, 0)";
        String sqlLegacy = "INSERT INTO Roles (RoleCode, RoleName, Description) VALUES (?, ?, ?)";
        try (Connection con = getConnection()) {
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, code);
                ps.setString(2, roleName.trim());
                ps.setString(3, description != null ? description.trim() : null);
                ps.executeUpdate();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ROLE));
                return null;
            } catch (SQLException e) {
                try (PreparedStatement ps = con.prepareStatement(sqlLegacy)) {
                    ps.setString(1, code);
                    ps.setString(2, roleName.trim());
                    ps.setString(3, description != null ? description.trim() : null);
                    ps.executeUpdate();
                    AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ROLE));
                    return null;
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "RoleDAO.create - code=" + code, e);
            return "Không tạo được vai trò: " + e.getMessage();
        }
    }

    /**
     * Xóa role tùy chỉnh. Không xóa IsSystem / đang được user dùng.
     * @return null nếu OK, ngược lại message lỗi
     */
    public String deleteCustomRole(String roleCode) {
        AppRole role = findByCode(roleCode);
        if (role == null) return "Vai trò không tồn tại.";
        if (role.isSystemRole()) return "Không thể xóa vai trò hệ thống.";

        String countSql = "SELECT COUNT(*) FROM Users WHERE RoleID = ? AND IsDeleted = 0";
        String delPerm = "DELETE FROM RolePermissions WHERE RoleID = ?";
        String delRole = "DELETE FROM Roles WHERE RoleID = ? AND COALESCE(IsSystem, 0) = 0";

        try (Connection con = getConnection()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(countSql)) {
                    ps.setInt(1, role.getRoleId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) {
                            con.rollback();
                            return "Còn " + rs.getInt(1) + " tài khoản đang dùng vai trò này. Hãy đổi role user trước.";
                        }
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(delPerm)) {
                    ps.setInt(1, role.getRoleId());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(delRole)) {
                    ps.setInt(1, role.getRoleId());
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        con.rollback();
                        return "Không xóa được vai trò (có thể là role hệ thống).";
                    }
                }
                con.commit();
                AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ROLE));
                return null;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_DELETE_FAIL, "RoleDAO.deleteCustomRole - " + roleCode, e);
            return "Lỗi xóa vai trò: " + e.getMessage();
        }
    }

    /**
     * Cập nhật tên + mô tả. Không đổi RoleCode / IsSystem.
     * @return null nếu OK, ngược lại message lỗi
     */
    public String update(int roleId, String roleName, String description) {
        if (roleName == null || roleName.isBlank()) return "Tên vai trò không được để trống.";
        String sql = "UPDATE Roles SET RoleName = ?, Description = ? WHERE RoleID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roleName.trim());
            ps.setString(2, description != null ? description.trim() : null);
            ps.setInt(3, roleId);
            int n = ps.executeUpdate();
            if (n == 0) return "Không tìm thấy vai trò để cập nhật.";
            AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.ROLE));
            return null;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "RoleDAO.update - id=" + roleId, e);
            return "Lỗi cập nhật vai trò: " + e.getMessage();
        }
    }

    /** Số user (chưa xóa mềm) đang gán role này. */
    public int countUsers(int roleId) {
        String sql = "SELECT COUNT(*) FROM Users WHERE RoleID = ? AND IsDeleted = 0";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, roleId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "RoleDAO.countUsers - id=" + roleId, e);
        }
        return 0;
    }

    /** Map RoleID → số user đang dùng (batch). */
    public java.util.Map<Integer, Integer> countUsersGrouped(java.util.Collection<Integer> roleIds) {
        java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
        if (roleIds == null || roleIds.isEmpty()) return map;
        StringBuilder in = new StringBuilder();
        for (Integer id : roleIds) {
            if (id == null) continue;
            if (in.length() > 0) in.append(',');
            in.append(id);
            map.put(id, 0);
        }
        if (in.length() == 0) return map;
        String sql = "SELECT RoleID, COUNT(*) AS Cnt FROM Users WHERE IsDeleted = 0 AND RoleID IN ("
                + in + ") GROUP BY RoleID";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getInt("RoleID"), rs.getInt("Cnt"));
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "RoleDAO.countUsersGrouped", e);
        }
        return map;
    }
}