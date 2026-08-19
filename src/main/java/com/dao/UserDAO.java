package com.dao;
import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.AppRole;
import com.model.Role;
import com.model.User;
import com.utils.DBConnection;
import com.utils.PasswordUtils;
import com.utils.PaginationHelper;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class UserDAO extends BaseDAO<User> {

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Users u JOIN Roles r ON u.RoleID = r.RoleID";
    }

    @Override
    protected String getColumns() {
        return "u.UserID, u.Username, u.PasswordHash, u.FullName, u.Email, u.Phone, " +
               "u.AvatarUrl, u.IsLocked, u.Status, r.RoleCode, r.RoleName, u.CreatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "u.UserID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"u.Username", "u.FullName", "u.Email", "u.Phone"};
    }

    // ==================== MAP RESULTSET ====================
    @Override
    protected User mapResultSet(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getInt("UserID"));
        user.setUsername(rs.getString("Username"));
        
        // ✅ BỎ DÒNG SET PASSWORDHASH — không cần thiết khi đọc dữ liệu
        // (PasswordHash không bao giờ hiển thị/biến đổi trên giao diện)
        
        user.setFullName(rs.getString("FullName"));
        user.setEmail(rs.getString("Email"));
        user.setPhone(rs.getString("Phone"));
        user.setAvatarUrl(rs.getString("AvatarUrl"));
        user.setLocked(rs.getBoolean("IsLocked"));
        user.setStatus(rs.getString("Status"));
        String roleCode = rs.getString("RoleCode");
        user.setRoleCode(roleCode);
        Timestamp ts = rs.getTimestamp("CreatedAt");
        if (ts != null) user.setCreatedAt(ts.toLocalDateTime());
        return user;
    }

    // ==================== TẠO TÀI KHOẢN ====================
    public boolean createByAdmin(User user, String rawPassword) {
        String sql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, RoleID, Status) " +
                     "VALUES (?, ?, ?, ?, ?, ?, (SELECT RoleID FROM Roles WHERE RoleCode = ?), ?)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, PasswordUtils.hash(rawPassword));
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getAvatarUrl());
            ps.setString(7, user.getRoleCode());
            ps.setString(8, "ACTIVE");
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.createByAdmin - " + user.getUsername(), e);
            return false;
        }
    }

    // ==================== CẬP NHẬT TÀI KHOẢN ====================
    public boolean updateByAdmin(User user) {
        String sql = "UPDATE Users SET FullName = ?, Email = ?, Phone = ?, AvatarUrl = ?, " +
                     "RoleID = (SELECT RoleID FROM Roles WHERE RoleCode = ?), Status = ? WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getFullName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getAvatarUrl());
            ps.setString(5, user.getRoleCode());
            ps.setString(6, user.getStatus());
            ps.setInt(7, user.getUserId());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.updateByAdmin - userId=" + user.getUserId(), e);
            return false;
        }
    }

    // ==================== ĐỔI MẬT KHẨU / KHÓA ====================
    public boolean updatePassword(int userId, String newHashedPassword) {
        String sql = "UPDATE Users SET PasswordHash = ?, FailedLoginCount = 0, IsLocked = 0 WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, newHashedPassword);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.updatePassword - userId=" + userId, e);
            return false;
        }
    }

    /**
     * ✅ ĐÃ THÊM MỚI — Sửa lỗi trong ảnh: verifyPassword(int, String) is undefined
     * Kiem tra mat khau nhap vao co khop voi hash luu trong DB cua user khong.
     * Ho tro tuong thich nguoc: neu hash trong DB la SHA-256 cu thi kiem tra
     * bang legacySha256Hash, sau do tu dong rehash sang BCrypt.
     */
    public boolean verifyPassword(int userId, String rawPassword) {
        String sql = "SELECT PasswordHash FROM Users WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                String storedHash = rs.getString("PasswordHash");
                if (storedHash == null || storedHash.isBlank()) {
                    return false;
                }

                boolean matches;
                if (PasswordUtils.isBCryptHash(storedHash)) {
                    matches = PasswordUtils.verify(rawPassword, storedHash);
                } else {
                    // Tuong thich nguoc voi hash SHA-256 cu
                    String legacyHash = PasswordUtils.legacySha256Hash(rawPassword);
                    matches = legacyHash.equals(storedHash);
                    // Neu dung, tu dong rehash sang BCrypt de bao mat hon
                    if (matches) {
                        updatePassword(userId, PasswordUtils.hash(rawPassword));
                    }
                }
                return matches;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.verifyPassword - userId=" + userId, e);
            return false;
        }
    }

    /**
     * ✅ ĐÃ THÊM MỚI — Được gọi từ ProfilePanel.java (đổi mật khẩu)
     * Doi mat khau: nhan mat khau goc, tu dong hash bang BCrypt roi cap nhat DB.
     */
    public boolean changePassword(int userId, String rawNewPassword) {
        if (rawNewPassword == null || rawNewPassword.isBlank()) {
            return false;
        }
        String hashed = PasswordUtils.hash(rawNewPassword);
        return updatePassword(userId, hashed);
    }

    public boolean setLocked(int userId, boolean locked) {
        String sql = locked
                ? "UPDATE Users SET IsLocked = 1 WHERE UserID = ?"
                : "UPDATE Users SET IsLocked = 0, FailedLoginCount = 0 WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.setLocked - userId=" + userId, e);
            return false;
        }
    }

    // ==================== KIỂM TRA TRÙNG ====================
    public boolean usernameExists(String username) {
        String sql = "SELECT 1 FROM Users WHERE Username = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.usernameExists - " + username, e);
            return false;
        }
    }

    public boolean emailExists(String email) {
        String sql = "SELECT 1 FROM Users WHERE Email = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.emailExists", e);
            return false;
        }
    }

    public boolean emailExistsExcluding(String email, int excludeUserId) {
        if (email == null || email.isBlank()) return false;
        String sql = "SELECT 1 FROM Users WHERE Email = ? AND UserID <> ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setInt(2, excludeUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.emailExistsExcluding", e);
            return false;
        }
    }

    // ==================== TÌM THEO TÊN / EMAIL ====================
    public User getByUsername(String username) {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE u.Username = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.getByUsername - " + username, e);
            return null;
        }
    }

    public User getByEmail(String email) {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE u.Email = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.getByEmail", e);
            return null;
        }
    }

    // ==================== ⭐ LỌC THEO ROLECODE ====================
    public PaginationHelper.PaginationResult<User> filterByRole(
            String keyword, String roleCode, int pageNumber, int pageSize) {
        StringBuilder where = new StringBuilder("1=1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String escaped = keyword.trim()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            String like = "%" + escaped + "%";
            String[] columns = getSearchableColumns();
            where.append(" AND (");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) where.append(" OR ");
                where.append(columns[i]).append(" LIKE ? ESCAPE '!'");
                params.add(like);
            }
            where.append(")");
        }
        if (roleCode != null && !roleCode.isBlank()) {
            where.append(" AND r.RoleCode = ?");
            params.add(roleCode);
        }
        String whereClause = where.toString();
        if (params.isEmpty()) {
            return getPaged(pageNumber, pageSize, whereClause);
        }
        return getPaged(pageNumber, pageSize, whereClause, params.toArray());
    }

    @Deprecated
    public PaginationHelper.PaginationResult<User> filterByRole(
            String keyword, Role role, int pageNumber, int pageSize) {
        return filterByRole(keyword, role != null ? role.name() : null, pageNumber, pageSize);
    }

    // ==================== LẤY TẤT CẢ ====================
    public List<User> getAll() {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " ORDER BY " + getOrderBy();
        List<User> list = new ArrayList<>();
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserDAO.getAll", e);
        }
        return list;
    }

    // ==================== TÌM THEO ROLECODE ====================
    public List<User> findByRoleCode(String roleCode) {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE r.RoleCode = ? ORDER BY u.FullName";
        List<User> list = new ArrayList<>();
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, roleCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSet(rs));
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.findByRoleCode - " + roleCode, e);
        }
        return list;
    }

    public List<User> findByRole(AppRole role) {
        return findByRoleCode(role.getRoleCode());
    }

    // ==================== ✅ ĐÃ THÊM MỚI: TIM THEO ID ====================
    public User findById(int userId) {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() + " WHERE u.UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.findById - userId=" + userId, e);
            return null;
        }
    }

    // ==================== ✅ ĐÃ THÊM MỚI: ALIAS cho getByUsername ====================
    public User findByUsername(String username) {
        return getByUsername(username);
    }

    // ==================== ✅ ĐÃ THÊM MỚI: DANG NHAP ====================
    /**
     * Dang nhap: kiem tra username + mat khau.
     * - Tra ve User neu thanh cong (reset FailedLoginCount).
     * - Tang FailedLoginCount neu sai mat khau; khoa tai khoan neu >= 5 lan.
     * - Tra ve null neu sai thong tin / bi khoa / bi vo hieu hoa.
     */
    public User login(String username, String rawPassword) {
        if (username == null || username.isBlank() || rawPassword == null) {
            return null;
        }

        String selectSql = "SELECT u.UserID, u.PasswordHash, u.IsLocked, u.FailedLoginCount, u.Status " +
                           "FROM Users u WHERE u.Username = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(selectSql)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // username khong ton tai
                }

                int userId = rs.getInt("UserID");
                String storedHash = rs.getString("PasswordHash");
                boolean isLocked = rs.getBoolean("IsLocked");
                int failedCount = rs.getInt("FailedLoginCount");
                String status = rs.getString("Status");

                if (isLocked) {
                    return null;
                }
                if (!"ACTIVE".equalsIgnoreCase(status)) {
                    return null;
                }

                boolean matches;
                if (PasswordUtils.isBCryptHash(storedHash)) {
                    matches = PasswordUtils.verify(rawPassword, storedHash);
                } else {
                    String legacyHash = PasswordUtils.legacySha256Hash(rawPassword);
                    matches = legacyHash.equals(storedHash);
                    if (matches) {
                        updatePassword(userId, PasswordUtils.hash(rawPassword));
                    }
                }

                if (matches) {
                    // Dang nhap thanh cong: reset FailedLoginCount
                    try (PreparedStatement resetPs = con.prepareStatement(
                            "UPDATE Users SET FailedLoginCount = 0 WHERE UserID = ?")) {
                        resetPs.setInt(1, userId);
                        resetPs.executeUpdate();
                    }
                    return findById(userId);
                } else {
                    // Sai mat khau: tang dem, khoa neu >= 5
                    failedCount++;
                    String updateSql = failedCount >= 5
                            ? "UPDATE Users SET FailedLoginCount = ?, IsLocked = 1 WHERE UserID = ?"
                            : "UPDATE Users SET FailedLoginCount = ? WHERE UserID = ?";
                    try (PreparedStatement updatePs = con.prepareStatement(updateSql)) {
                        updatePs.setInt(1, failedCount);
                        updatePs.setInt(2, userId);
                        updatePs.executeUpdate();
                    }
                    return null;
                }
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.login - " + username, e);
            return null;
        }
    }

    // ==================== ✅ ĐÃ THÊM MỚI: DANG KY ====================
    /**
     * Dang ky tai khoan khach hang moi. Mac dinh Role = CUSTOMER, Status = ACTIVE.
     */
    public boolean register(User user, String rawPassword) {
        String sql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, " +
                     "RoleID, Status) VALUES (?, ?, ?, ?, ?, ?, " +
                     "(SELECT RoleID FROM Roles WHERE RoleCode = ?), ?)";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, user.getUsername());
            ps.setString(2, PasswordUtils.hash(rawPassword));
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getEmail());
            ps.setString(5, user.getPhone());
            ps.setString(6, user.getAvatarUrl());
            // Mac dinh la CUSTOMER neu khong co roleCode
            String roleCode = user.getRoleCode() != null && !user.getRoleCode().isBlank()
                    ? user.getRoleCode() : "CUSTOMER";
            ps.setString(7, roleCode);
            ps.setString(8, "ACTIVE");
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "UserDAO.register - " + user.getUsername(), e);
            return false;
        }
    }

    // ==================== ✅ ĐÃ THÊM MỚI: CAP NHAT HO SO ====================
    public boolean updateProfile(int userId, String fullName, String phone) {
        String sql = "UPDATE Users SET FullName = ?, Phone = ? WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, fullName);
            ps.setString(2, phone);
            ps.setInt(3, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.updateProfile - userId=" + userId, e);
            return false;
        }
    }

    // ==================== ✅ ĐÃ THÊM MỚI: CAP NHAT AVATAR ====================
    public boolean updateAvatar(int userId, String avatarUrl) {
        String sql = "UPDATE Users SET AvatarUrl = ? WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, avatarUrl);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.updateAvatar - userId=" + userId, e);
            return false;
        }
    }

    // ==================== ✅ ĐÃ THÊM MỚI: TIM NHAN VIEN HOAT DONG ====================
    /**
     * Lay danh sach nhan vien (khong phai CUSTOMER) dang hoat dong, cho man hinh chat Admin.
     */
    public List<User> findActiveStaff() {
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() +
                     " WHERE r.RoleCode <> 'CUSTOMER' AND u.Status = 'ACTIVE' AND u.IsLocked = 0" +
                     " ORDER BY u.FullName";
        List<User> list = new ArrayList<>();
        try (Connection con = getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapResultSet(rs));
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.findActiveStaff", e);
        }
        return list;
    }

    // ==================== ✅ ĐÃ THÊM MỚI: 2FA THAT BAI ====================
    /**
     * Tang dem lan xac minh 2FA that bai (co the dung de khoa tam neu can).
     * Hien chi ghi log, chua co logic khoa.
     */
    public void registerFailedTwoFactorAttempt(int userId) {
        // Placeholder: hien tai chi ghi log, co the mo rong them cot Failed2FACount sau.
        AppLogger.getInstance().info("SYSTEM", "UserDAO.registerFailedTwoFactorAttempt - userId=" + userId);
    }

    // ==================== ✅ ĐÃ THÊM MỚI: DAT LAI MAT KHAU ====================
    /**
     * Tim user de dat lai mat khau: phai trung ca username va email.
     */
    public User findForPasswordReset(String username, String email) {
        if (username == null || email == null) return null;
        String sql = "SELECT " + getColumns() + " FROM " + getTableName() +
                     " WHERE u.Username = ? AND u.Email = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, username.trim());
            ps.setString(2, email.trim());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapResultSet(rs) : null;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL,
                    "UserDAO.findForPasswordReset", e);
            return null;
        }
    }

    /**
     * ✅ ĐÃ THÊM MỚI: Enum ket qua dat lai mat khau
     */
    public enum PasswordResetUpdateResult {
        SUCCESS,
        UPDATE_FAILED,
        SAME_AS_OLD_PASSWORD,
        ACCOUNT_UNAVAILABLE
    }

    /**
     * ✅ ĐÃ THÊM MỚI: Dat lai mat khau tu flow khoi phuc
     * Dat lai mat khau tu flow khoi phuc: nhan mat khau goc, tu dong hash.
     * Kiem tra mat khau moi khong duong trung mat khau hien tai.
     */
    public PasswordResetUpdateResult resetPasswordFromRecovery(int userId, String newRawPassword) {
        if (newRawPassword == null || newRawPassword.isBlank()) {
            return PasswordResetUpdateResult.UPDATE_FAILED;
        }

        String selectSql = "SELECT PasswordHash, Status, IsLocked FROM Users WHERE UserID = ?";
        try (Connection con = getConnection();
             PreparedStatement ps = con.prepareStatement(selectSql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return PasswordResetUpdateResult.ACCOUNT_UNAVAILABLE;
                }

                String storedHash = rs.getString("PasswordHash");
                String status = rs.getString("Status");
                boolean isLocked = rs.getBoolean("IsLocked");

                if (!"ACTIVE".equalsIgnoreCase(status) || isLocked) {
                    return PasswordResetUpdateResult.ACCOUNT_UNAVAILABLE;
                }

                // Kiem tra mat khau moi co trung mat khau cu khong
                boolean sameAsOld;
                if (PasswordUtils.isBCryptHash(storedHash)) {
                    sameAsOld = PasswordUtils.verify(newRawPassword, storedHash);
                } else {
                    String legacyHash = PasswordUtils.legacySha256Hash(newRawPassword);
                    sameAsOld = legacyHash.equals(storedHash);
                }

                if (sameAsOld) {
                    return PasswordResetUpdateResult.SAME_AS_OLD_PASSWORD;
                }

                // Thuc hien doi mat khau
                boolean ok = changePassword(userId, newRawPassword);
                return ok ? PasswordResetUpdateResult.SUCCESS
                          : PasswordResetUpdateResult.UPDATE_FAILED;
            }
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "UserDAO.resetPasswordFromRecovery - userId=" + userId, e);
            return PasswordResetUpdateResult.UPDATE_FAILED;
        }
    }
}