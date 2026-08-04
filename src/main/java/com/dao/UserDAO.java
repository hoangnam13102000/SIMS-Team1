package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Role;
import com.model.User;
import com.utils.DBConnection;
import com.utils.PaginationHelper;
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
public class UserDAO extends BaseDAO<User> {

    private static final int MAX_FAILED_LOGIN = 5;

    public enum PasswordResetUpdateResult {
        SUCCESS,
        SAME_AS_OLD_PASSWORD,
        ACCOUNT_UNAVAILABLE,
        UPDATE_FAILED
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Users u";
    }

    @Override
    protected String getColumns() {
        return "u.UserID, u.Username, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, u.CreatedAt, r.RoleCode";
    }

    @Override
    protected String getJoinClause() {
        return "JOIN Roles r ON u.RoleID = r.RoleID";
    }

    @Override
    protected String getOrderBy() {
        return "u.UserID DESC";
    }

    @Override
    protected User mapResultSet(ResultSet rs) throws SQLException {
        return mapUser(rs);
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"u.Username", "u.FullName", "u.Email", "u.Phone"};
    }

    /**
     * Tra ve User neu dung tai khoan/mat khau va tai khoan chua bi khoa/vo
     * hieu hoa, nguoc lai tra ve null.
     */
    public User login(String username, String rawPassword) {
        String sql = "SELECT u.UserID, u.Username, u.PasswordHash, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, u.CreatedAt, r.RoleCode "
                + "FROM Users u JOIN Roles r ON u.RoleID = r.RoleID "
                + "WHERE u.Username = ? AND u.IsDeleted = 0";

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
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Tra ve trang thai tai khoan (khong kiem tra mat khau) - dung de
     * LoginFrame phan biet: sai mat khau / bi khoa / khong ton tai.
     */
    public User findByUsername(String username) {
        String sql = "SELECT u.UserID, u.Username, u.PasswordHash, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, u.CreatedAt, r.RoleCode "
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

    /**
     * Tim tai khoan hop le cho luong khoi phuc mat khau. Username va email
     * phai cung khop mot dong; khong cho phep tai khoan disabled/soft-deleted.
     * Method nay khong select PasswordHash de tranh dua hash ra khoi DAO.
     */
    public User findForPasswordReset(String username, String email) throws SQLException {
        String sql = "SELECT u.UserID, u.Username, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.FailedLoginCount, u.Status, u.CreatedAt, r.RoleCode "
                + "FROM Users u JOIN Roles r ON u.RoleID = r.RoleID "
                + "WHERE u.Username = ? AND LOWER(u.Email) = LOWER(?) "
                + "AND u.Email IS NOT NULL AND u.IsDeleted = 0 AND u.Status = 'ACTIVE'";

        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username == null ? "" : username.trim());
            ps.setString(2, email == null ? "" : email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapUser(rs) : null;
            }
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

    /** Cap nhat duong dan anh dai dien (dung khi nguoi dung tu upload avatar o trang ho so ca nhan). */
    public boolean updateAvatar(int userId, String avatarUrl) {
        String sql = "UPDATE Users SET AvatarUrl = ? WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.updateAvatar - userId=" + userId, e);
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
     * Cap nhat mat khau sau khi PasswordResetService da xac minh challenge.
     * Transaction giu row lock trong luc so sanh mat khau cu va ghi BCrypt
     * hash moi, tranh hai reset dong thoi cung thanh cong.
     */
    public PasswordResetUpdateResult resetPasswordFromRecovery(int userId, String newRawPassword) {
        String selectSql = "SELECT PasswordHash, IsLocked, FailedLoginCount, Status, IsDeleted "
                + "FROM Users WITH (UPDLOCK, ROWLOCK) WHERE UserID = ?";
        String updateSql = "UPDATE Users SET PasswordHash = ?, "
                + "IsLocked = CASE WHEN IsLocked = 1 AND FailedLoginCount >= ? THEN 0 ELSE IsLocked END, "
                + "FailedLoginCount = 0 "
                + "WHERE UserID = ? AND IsDeleted = 0 AND Status = 'ACTIVE'";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            String storedHash;
            try (PreparedStatement ps = con.prepareStatement(selectSql)) {
                ps.setInt(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()
                            || rs.getBoolean("IsDeleted")
                            || !"ACTIVE".equalsIgnoreCase(rs.getString("Status"))) {
                        con.rollback();
                        return PasswordResetUpdateResult.ACCOUNT_UNAVAILABLE;
                    }
                    storedHash = rs.getString("PasswordHash");
                }
            }

            if (matchesStoredPassword(newRawPassword, storedHash)) {
                con.rollback();
                return PasswordResetUpdateResult.SAME_AS_OLD_PASSWORD;
            }

            try (PreparedStatement ps = con.prepareStatement(updateSql)) {
                ps.setString(1, PasswordUtils.hash(newRawPassword));
                ps.setInt(2, MAX_FAILED_LOGIN);
                ps.setInt(3, userId);
                if (ps.executeUpdate() != 1) {
                    con.rollback();
                    return PasswordResetUpdateResult.ACCOUNT_UNAVAILABLE;
                }
            }

            con.commit();
            return PasswordResetUpdateResult.SUCCESS;
        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException rollbackError) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "UserDAO.resetPasswordFromRecovery rollback", rollbackError);
                }
            }
            AppLogger.getInstance().error(ErrorCode.AUTH_PASSWORD_RESET_FAIL,
                    "UserDAO.resetPasswordFromRecovery", e);
            return PasswordResetUpdateResult.UPDATE_FAILED;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeError) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "UserDAO.resetPasswordFromRecovery close", closeError);
                }
            }
        }
    }

    private boolean matchesStoredPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (PasswordUtils.isBCryptHash(storedHash)) {
            try {
                return PasswordUtils.verify(rawPassword, storedHash);
            } catch (IllegalArgumentException invalidStoredHash) {
                return false;
            }
        }
        return storedHash.equalsIgnoreCase(PasswordUtils.legacySha256Hash(rawPassword));
    }

    /**
     * Dang ky tai khoan moi. Dang ky cong khai luon gan Role.CUSTOMER.
     * R.CUSTOMER: sau khi tao Users phai tao/lien ket luon ho so Customers
     * (diem thanh vien, lich su mua hang), trong CUNG 1 transaction de
     * khong bao gio co User ma thieu Customer hoac nguoc lai.
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

        String insertUserSql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, RoleID) "
                + "VALUES (?, ?, ?, ?, ?, (SELECT RoleID FROM Roles WHERE RoleCode = ?))";
        String insertCustomerSql = "INSERT INTO Customers (CustomerID, MemberPoint) VALUES (?, 0)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(insertUserSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, PasswordUtils.hash(rawPassword));
                ps.setString(3, user.getFullName());
                ps.setString(4, user.getEmail());
                ps.setString(5, user.getPhone());
                ps.setString(6, user.getRole().name());

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    con.rollback();
                    return false;
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setUserId(keys.getInt(1));
                    }
                }
            }

            if (user.getRole() == Role.CUSTOMER) {
            	try (PreparedStatement cps = con.prepareStatement(insertCustomerSql)) {
                    cps.setInt(1, user.getUserId());
                    cps.setString(2, generateCustomerCode(user.getUserId()));
                    int customerAffected = cps.executeUpdate();
                    if (customerAffected == 0) {
                        con.rollback();
                        AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                                "UserDAO.register - khong the tao Customers cho userId=" + user.getUserId(), null);
                        return false;
                    }
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
                            "UserDAO.register - rollback that bai", rollbackEx);
                }
            }
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "UserDAO.register - " + user.getUsername(), e);
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "UserDAO.register - dong connection that bai", closeEx);
                }
            }
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("UserID"));
        user.setUsername(rs.getString("Username"));
        user.setFullName(rs.getString("FullName"));
        user.setEmail(rs.getString("Email"));
        user.setPhone(rs.getString("Phone"));
        user.setAvatarUrl(rs.getString("AvatarUrl"));
        user.setRole(Role.valueOf(rs.getString("RoleCode")));
        user.setLocked(rs.getBoolean("IsLocked"));
        user.setFailedLoginCount(rs.getInt("FailedLoginCount"));
        user.setStatus(rs.getString("Status"));
        java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) user.setCreatedAt(createdAt.toLocalDateTime());
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

    // ---------------------------------------------------------------
    // Quản lý tài khoản (dành cho Admin) - khác register() công khai vì cho
    // phép chọn vai trò bất kỳ và không tự động đăng nhập sau khi tạo.
    // ---------------------------------------------------------------

    /** Giống usernameExists() nhưng loại trừ 1 UserID - dùng khi Admin sửa tài khoản (không tự đụng chính username của tài khoản đang sửa). */
    public boolean usernameExistsExcluding(String username, int excludeUserId) {
        String sql = "SELECT 1 FROM Users WHERE Username = ? AND UserID <> ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.usernameExistsExcluding - " + username, e);
            return true;
        }
    }

    /** Giống emailExists() nhưng loại trừ 1 UserID - dùng khi Admin sửa tài khoản. */
    public boolean emailExistsExcluding(String email, int excludeUserId) {
        String sql = "SELECT 1 FROM Users WHERE Email = ? AND UserID <> ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.emailExistsExcluding - " + email, e);
            return true;
        }
    }

    /**
     * Admin tạo tài khoản mới với vai trò bất kỳ (khác register() công khai
     * luôn gán cứng Role.SALES_STAFF). Không tự đăng nhập sau khi tạo.
     */
    public boolean createByAdmin(User user, String rawPassword) {
        if (usernameExists(user.getUsername())) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.createByAdmin - trung Username: " + user.getUsername(), null);
            return false;
        }
        if (user.getEmail() != null && emailExists(user.getEmail())) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.createByAdmin - trung Email: " + user.getEmail(), null);
            return false;
        }

        String sql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, RoleID, Status) "
                + "VALUES (?, ?, ?, ?, ?, (SELECT RoleID FROM Roles WHERE RoleCode = ?), 'ACTIVE')";
        String insertCustomerSql = "INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint) VALUES (?, ?, 0)";

        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);

            try (PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, user.getUsername());
                ps.setString(2, PasswordUtils.hash(rawPassword));
                ps.setString(3, user.getFullName());
                ps.setString(4, user.getEmail());
                ps.setString(5, user.getPhone());
                ps.setString(6, user.getRole().name());

                int affected = ps.executeUpdate();
                if (affected == 0) {
                    con.rollback();
                    return false;
                }

                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        user.setUserId(keys.getInt(1));
                    }
                }
            }

            if (user.getRole() == Role.CUSTOMER) {
                try (PreparedStatement cps = con.prepareStatement(insertCustomerSql)) {
                    cps.setInt(1, user.getUserId());
                    cps.setString(2, generateCustomerCode(user.getUserId()));
                    if (cps.executeUpdate() == 0) {
                        con.rollback();
                        AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                                "UserDAO.createByAdmin - khong the tao Customers cho userId=" + user.getUserId(), null);
                        return false;
                    }
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
                            "UserDAO.createByAdmin - rollback that bai", rollbackEx);
                }
            }
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "UserDAO.createByAdmin - " + user.getUsername(), e);
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "UserDAO.createByAdmin - dong connection that bai", closeEx);
                }
            }
        }
    }

    /** Admin cập nhật họ tên/email/sđt/vai trò/trạng thái của 1 tài khoản (không đổi mật khẩu ở đây - xem {@link #resetPassword}). */
    public boolean updateByAdmin(User user) {
        String sql = "UPDATE Users SET FullName = ?, Email = ?, Phone = ?, "
                + "RoleID = (SELECT RoleID FROM Roles WHERE RoleCode = ?), Status = ? WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getRole().name());
            ps.setString(5, user.getStatus());
            ps.setInt(6, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.updateByAdmin - userId=" + user.getUserId(), e);
            return false;
        }
    }

    /** Khoá / mở khoá 1 tài khoản. Mở khoá sẽ reset luôn FailedLoginCount về 0 (R5). */
    public boolean setLocked(int userId, boolean locked) {
        String sql = locked
                ? "UPDATE Users SET IsLocked = 1 WHERE UserID = ?"
                : "UPDATE Users SET IsLocked = 0, FailedLoginCount = 0 WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserDAO.setLocked - userId=" + userId, e);
            return false;
        }
    }

    /** Admin đặt lại mật khẩu cho 1 tài khoản mà không cần biết mật khẩu cũ. */
    public boolean resetPassword(int userId, String newRawPassword) {
        return changePassword(userId, newRawPassword);
    }

    /** Lọc theo vai trò, đồng thời hỗ trợ từ khóa tìm kiếm hiện tại. */
    public com.utils.PaginationHelper.PaginationResult<User> filterByRole(
            String keyword, Role role, int pageNumber, int pageSize) {
        StringBuilder where = new StringBuilder();
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            String escaped = keyword.trim()
                    .replace("[", "[[]")
                    .replace("%", "[%]")
                    .replace("_", "[_]");
            String like = "%" + escaped + "%";
            String[] columns = getSearchableColumns();
            where.append("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) where.append(" OR ");
                where.append(columns[i]).append(" LIKE ?");
                params.add(like);
            }
            where.append(")");
        }

        if (role != null) {
            if (where.length() > 0) where.append(" AND ");
            where.append("r.RoleCode = ?");
            params.add(role.name());
        }

        if (where.length() == 0) return getPaged(pageNumber, pageSize);
        return getPaged(pageNumber, pageSize, where.toString(), params.toArray());
    }
    private String generateCustomerCode(int userId) {
        return "CUS_" + String.format("%04d", userId);
    }
}