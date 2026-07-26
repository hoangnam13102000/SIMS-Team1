package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Role;
import com.model.User;
import com.utils.DBConnection;
import com.utils.PasswordUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DAO cho bang Users (JOIN Roles de lay RoleCode) theo dung schema trong
 * SIMS.sql - KHONG dung stored procedure (schema goc khong dinh nghia SP nao
 * cho Users, chi co bang thuan).
 * <p>
 * R5 (bao mat): sau 5 lan dang nhap sai LIEN TIEP, tai khoan bi khoa
 * (IsLocked = 1). Dang nhap dung se reset FailedLoginCount ve 0.
 */
public class UserDAO {

    private static final int MAX_FAILED_LOGIN = 5;

    /**
     * Tra ve User neu dung tai khoan/mat khau va tai khoan chua bi khoa/vo
     * hieu hoa, nguoc lai tra ve null.
     */
    public User login(String username, String rawPassword) {
        String sql = "SELECT u.UserID, u.Username, u.PasswordHash, u.FullName, u.Email, u.Phone, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, r.RoleCode "
                + "FROM Users u JOIN Roles r ON u.RoleID = r.RoleID "
                + "WHERE u.Username = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // khong ton tai username
                }

                boolean isLocked = rs.getBoolean("IsLocked");
                String status = rs.getString("Status");
                int userId = rs.getInt("UserID");

                if (isLocked || "DISABLED".equalsIgnoreCase(status)) {
                    return null; // tai khoan bi khoa/vo hieu hoa
                }

                String storedHash = rs.getString("PasswordHash");
                if (PasswordUtils.verify(rawPassword, storedHash)) {
                    resetFailedLogin(con, userId);
                    return mapUser(rs);
                }

                registerFailedLogin(con, userId, rs.getInt("FailedLoginCount"));
            }

        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.AUTH_LOGIN_FAIL, "UserDAO.login - " + username, e);
            // AppLogger chi ghi vao DB qua LogSink - Main.java cua framework
            // nay CHUA gan sink nao, nen error() o tren khong in ra dau ca.
            // In thang ra console de thay duoc NGUYEN NHAN THAT (vd sai ten
            // bang/cot do dang tro nham DB, hoac chua chay SIMS_seed_admin.sql).
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Tra ve trang thai tai khoan (khong kiem tra mat khau) - dung de
     * LoginFrame phan biet: sai mat khau / bi khoa / khong ton tai.
     */
    public User findByUsername(String username) {
        String sql = "SELECT u.UserID, u.Username, u.PasswordHash, u.FullName, u.Email, u.Phone, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, r.RoleCode "
                + "FROM Users u JOIN Roles r ON u.RoleID = r.RoleID "
                + "WHERE u.Username = ?";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.findByUsername - " + username, e);
            return null;
        }
    }

    private void resetFailedLogin(Connection con, int userId) {
        String sql = "UPDATE Users SET FailedLoginCount = 0 WHERE UserID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.resetFailedLogin - userId=" + userId, e);
        }
    }

    /** Tang FailedLoginCount; neu vua cham nguong MAX_FAILED_LOGIN thi khoa luon tai khoan (R5). */
    private void registerFailedLogin(Connection con, int userId, int currentCount) {
        int newCount = currentCount + 1;
        boolean shouldLock = newCount >= MAX_FAILED_LOGIN;

        String sql = "UPDATE Users SET FailedLoginCount = ?, IsLocked = ? WHERE UserID = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, newCount);
            ps.setBoolean(2, shouldLock);
            ps.setInt(3, userId);
            ps.executeUpdate();

            if (shouldLock) {
                AppLogger.getInstance().error(ErrorCode.AUTH_ACCOUNT_LOCKED,
                        "UserDAO.registerFailedLogin - userId=" + userId + " bi khoa sau " + newCount + " lan sai", null);
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.registerFailedLogin - userId=" + userId, e);
        }
    }

    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM Users WHERE Username = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.usernameExists - " + username, e);
            return true;
        }
    }

    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM Users WHERE Email = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.emailExists - " + email, e);
            return true;
        }
    }

    /** Cap nhat ho ten va so dien thoai (khong cho doi Email vi Email da duoc xac thuc OTP). */
    public boolean updateProfile(int userId, String fullName, String phone) {
        String sql = "UPDATE Users SET FullName = ?, Phone = ? WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.updateProfile - userId=" + userId, e);
            return false;
        }
    }

    /** Kiem tra mat khau hien tai co dung khong (dung khi doi mat khau). */
    public boolean verifyPassword(int userId, String rawPassword) {
        String sql = "SELECT PasswordHash FROM Users WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return PasswordUtils.verify(rawPassword, rs.getString("PasswordHash"));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.verifyPassword - userId=" + userId, e);
        }
        return false;
    }

    public boolean changePassword(int userId, String newRawPassword) {
        String sql = "UPDATE Users SET PasswordHash = ? WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, PasswordUtils.hash(newRawPassword));
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.changePassword - userId=" + userId, e);
            return false;
        }
    }

    /**
     * Dang ky tai khoan moi. Dang ky cong khai luon gan Role.SALES_STAFF
     * (quyen thap nhat) - Admin co the doi vai tro sau. Kiem tra trung
     * Username/Email o tang ung dung (schema khong co stored procedure).
     */
    public boolean register(User user, String rawPassword) {
        if (usernameExists(user.getUsername())) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.register - trung Username: " + user.getUsername(), null);
            return false;
        }
        if (user.getEmail() != null && emailExists(user.getEmail())) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.register - trung Email: " + user.getEmail(), null);
            return false;
        }

        String sql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, RoleID) "
                + "VALUES (?, ?, ?, ?, ?, (SELECT RoleID FROM Roles WHERE RoleCode = ?))";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getUsername());
            ps.setString(2, PasswordUtils.hash(rawPassword));
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getRole().name());

            int affected = ps.executeUpdate();
            if (affected == 0) return false;

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    user.setUserId(keys.getInt(1));
                }
            }
            return true;

        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "UserDAO.register - " + user.getUsername(), e);
            return false;
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("UserID"));
        user.setUsername(rs.getString("Username"));
        user.setFullName(rs.getString("FullName"));
        user.setEmail(rs.getString("Email"));
        user.setPhone(rs.getString("Phone"));
        user.setRole(Role.valueOf(rs.getString("RoleCode")));
        user.setLocked(rs.getBoolean("IsLocked"));
        user.setFailedLoginCount(rs.getInt("FailedLoginCount"));
        user.setStatus(rs.getString("Status"));
        return user;
    }

    public Map<String, Role> findRolesByUsernames(Collection<String> usernames) {
        Map<String, Role> result = new HashMap<>();
        if (usernames == null || usernames.isEmpty()) return result;

        List<String> distinct = usernames.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (distinct.isEmpty()) return result;

        String placeholders = distinct.stream().map(u -> "?").collect(Collectors.joining(","));
        String sql = "SELECT u.Username, r.RoleCode FROM Users u JOIN Roles r ON u.RoleID = r.RoleID "
                + "WHERE u.Username IN (" + placeholders + ")";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            for (int i = 0; i < distinct.size(); i++) {
                ps.setString(i + 1, distinct.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        result.put(rs.getString("Username"), Role.valueOf(rs.getString("RoleCode")));
                    } catch (IllegalArgumentException ignore) {
                        // RoleCode trong DB khong khop enum - bo qua dong nay.
                    }
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.findRolesByUsernames", e);
        }
        return result;
    }
}