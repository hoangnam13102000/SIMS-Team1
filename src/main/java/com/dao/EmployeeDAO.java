package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.Employee;
import com.model.Role;
import com.service.EmployeeMailService;
import com.utils.DBConnection;
import com.utils.PasswordUtils;
import com.utils.RandomPasswordGenerator;
import jakarta.mail.MessagingException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.util.UUID;

public class EmployeeDAO extends BaseDAO<Employee> {
    
    private final UserDAO userDAO = new UserDAO();

    @Override
    protected Connection getConnection() throws SQLException {
        return DBConnection.getConnection();
    }

    @Override
    protected String getTableName() {
        return "Users u JOIN Employees e ON u.UserID = e.UserID JOIN Roles r ON u.RoleID = r.RoleID";
    }

    @Override
    protected String getJoinClause() {
        return null;
    }

    @Override
    protected String getColumns() {
        return "u.UserID, u.Username, u.FullName, u.Email, u.Phone, u.AvatarUrl, "
                + "u.IsLocked, u.Status, r.RoleCode, r.RoleName, "
                + "e.EmployeeID, e.DateOfBirth, e.Gender, e.Salary, e.HireDate, e.CreatedAt";
    }

    @Override
    protected String getOrderBy() {
        return "u.UserID DESC";
    }

    @Override
    protected String[] getSearchableColumns() {
        return new String[]{"u.Username", "u.FullName", "u.Email", "u.Phone", "e.EmployeeID"};
    }

    // ⭐ CẬP NHẬT: Dùng setRoleCode thay vì setRole(Role.valueOf)
    @Override
    protected Employee mapResultSet(ResultSet rs) throws SQLException {
        Employee employee = new Employee();
        employee.setUserId(rs.getInt("UserID"));
        employee.setUsername(rs.getString("Username"));
        employee.setFullName(rs.getString("FullName"));
        employee.setEmail(rs.getString("Email"));
        employee.setPhone(rs.getString("Phone"));
        employee.setAvatarUrl(rs.getString("AvatarUrl"));
        employee.setLocked(rs.getBoolean("IsLocked"));
        employee.setStatus(rs.getString("Status"));
        
        // ⭐ Dùng setRoleCode — hỗ trợ cả vai trò hệ thống và tùy chỉnh
        String roleCode = rs.getString("RoleCode");
        employee.setRoleCode(roleCode);
        
        employee.setEmployeeId(rs.getString("EmployeeID"));
        Date dob = rs.getDate("DateOfBirth");
        if (dob != null) employee.setDateOfBirth(dob.toLocalDate());
        String genderCode = rs.getString("Gender");
        if (genderCode != null) employee.setGender(Employee.Gender.valueOf(genderCode));
        BigDecimal salary = rs.getBigDecimal("Salary");
        if (salary != null) employee.setSalary(salary);
        Date hireDate = rs.getDate("HireDate");
        if (hireDate != null) employee.setHireDate(hireDate.toLocalDate());
        java.sql.Timestamp createdAt = rs.getTimestamp("CreatedAt");
        if (createdAt != null) employee.setCreatedAt(createdAt.toLocalDateTime());
        return employee;
    }

    // ⭐ CẬP NHẬT: Dùng employee.getRoleCode() thay vì employee.getRole().name()
    public EmployeeCreationResult createEmployee(Employee employee) {
        EmployeeCreationResult result = new EmployeeCreationResult();
        if (employee.getEmail() != null && userDAO.emailExists(employee.getEmail())) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                    "EmployeeDAO.createEmployee - trung Email: " + employee.getEmail(), null);
            return result;
        }
        String username = generateUniqueUsername(employee.getFullName());
        String rawPassword = RandomPasswordGenerator.generate();
        String insertUserSql = "INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, RoleID, Status) "
                + "VALUES (?, ?, ?, ?, ?, ?, (SELECT RoleID FROM Roles WHERE RoleCode = ?), 'ACTIVE')";
        String insertEmployeeSql = "INSERT INTO Employees (UserID, EmployeeID, DateOfBirth, Gender, Salary, HireDate) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        Connection con = null;
        String employeeId = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            int userId;
            try (PreparedStatement ps = con.prepareStatement(insertUserSql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, username);
                ps.setString(2, PasswordUtils.hash(rawPassword));
                ps.setString(3, employee.getFullName());
                ps.setString(4, employee.getEmail());
                ps.setString(5, employee.getPhone());
                ps.setString(6, employee.getAvatarUrl());
                // ⭐ Dùng getRoleCode()
                ps.setString(7, employee.getRoleCode());
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    return result;
                }
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        con.rollback();
                        return result;
                    }
                    userId = keys.getInt(1);
                }
            }
            employeeId = generateEmployeeCode(userId);
            try (PreparedStatement ps = con.prepareStatement(insertEmployeeSql)) {
                ps.setInt(1, userId);
                ps.setString(2, employeeId);
                ps.setDate(3, employee.getDateOfBirth() != null ? Date.valueOf(employee.getDateOfBirth()) : null);
                ps.setString(4, employee.getGender() != null ? employee.getGender().name() : null);
                if (employee.getSalary() != null) {
                    ps.setBigDecimal(5, employee.getSalary());
                } else {
                    ps.setNull(5, java.sql.Types.DECIMAL);
                }
                ps.setDate(6, Date.valueOf(employee.getHireDate() != null ? employee.getHireDate() : LocalDate.now()));
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL,
                            "EmployeeDAO.createEmployee - khong the tao Employees cho userId=" + userId, null);
                    return result;
                }
            }
            con.commit();
            employee.setUserId(userId);
        } catch (Exception e) {
            if (con != null) {
                try {
                    con.rollback();
                } catch (SQLException rollbackEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "EmployeeDAO.createEmployee - rollback that bai", rollbackEx);
                }
            }
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "EmployeeDAO.createEmployee - " + employee.getFullName(), e);
            return result;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "EmployeeDAO.createEmployee - dong connection that bai", closeEx);
                }
            }
        }
        employee.setEmployeeId(employeeId);
        employee.setUsername(username);
        employee.setStatus("ACTIVE");
        result.success = true;
        result.rawPassword = rawPassword;
        try {
            new EmployeeMailService().sendCredentials(
                    employee.getEmail(), employee.getFullName(), employeeId, username, rawPassword);
            result.emailSent = true;
        } catch (MessagingException e) {
            AppLogger.getInstance().error(ErrorCode.EMAIL_SEND_FAIL,
                    "EmployeeDAO.createEmployee - gui email that bai cho " + employee.getEmail(), e);
            result.emailSent = false;
            result.emailError = e.getMessage();
        }
        return result;
    }

    // ⭐ CẬP NHẬT: Dùng employee.getRoleCode() thay vì employee.getRole().name()
    public boolean updateByAdmin(Employee employee) {
        String updateUserSql = "UPDATE Users SET FullName = ?, Email = ?, Phone = ?, AvatarUrl = ?, "
                + "RoleID = (SELECT RoleID FROM Roles WHERE RoleCode = ?), Status = ? WHERE UserID = ?";
        String updateEmployeeSql = "UPDATE Employees SET DateOfBirth = ?, Gender = ?, Salary = ?, HireDate = ? WHERE UserID = ?";
        Connection con = null;
        try {
            con = DBConnection.getConnection();
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(updateUserSql)) {
                ps.setString(1, employee.getFullName());
                ps.setString(2, employee.getEmail());
                ps.setString(3, employee.getPhone());
                ps.setString(4, employee.getAvatarUrl());
                // ⭐ Dùng getRoleCode()
                ps.setString(5, employee.getRoleCode());
                ps.setString(6, employee.getStatus());
                ps.setInt(7, employee.getUserId());
                if (ps.executeUpdate() == 0) {
                    con.rollback();
                    return false;
                }
            }
            try (PreparedStatement ps = con.prepareStatement(updateEmployeeSql)) {
                ps.setDate(1, employee.getDateOfBirth() != null ? Date.valueOf(employee.getDateOfBirth()) : null);
                ps.setString(2, employee.getGender() != null ? employee.getGender().name() : null);
                if (employee.getSalary() != null) {
                    ps.setBigDecimal(3, employee.getSalary());
                } else {
                    ps.setNull(3, java.sql.Types.DECIMAL);
                }
                ps.setDate(4, employee.getHireDate() != null ? Date.valueOf(employee.getHireDate()) : null);
                ps.setInt(5, employee.getUserId());
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
                            "EmployeeDAO.updateByAdmin - rollback that bai", rollbackEx);
                }
            }
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                    "EmployeeDAO.updateByAdmin - userId=" + employee.getUserId(), e);
            return false;
        } finally {
            if (con != null) {
                try {
                    con.setAutoCommit(true);
                    con.close();
                } catch (SQLException closeEx) {
                    AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL,
                            "EmployeeDAO.updateByAdmin - dong connection that bai", closeEx);
                }
            }
        }
    }

    public boolean setLocked(int userId, boolean locked) {
        String sql = locked
                ? "UPDATE Users SET IsLocked = 1 WHERE UserID = ?"
                : "UPDATE Users SET IsLocked = 0, FailedLoginCount = 0 WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "EmployeeDAO.setLocked - userId=" + userId, e);
            return false;
        }
    }

    public boolean emailExistsExcluding(String email, int excludeUserId) {
        return userDAO.emailExistsExcluding(email, excludeUserId);
    }

    private String generateEmployeeCode(int userId) {
        return "EMP_" + String.format("%04d", userId);
    }

    private static final int USERNAME_MIN_LEN = 5;
    private static final int USERNAME_MAX_LEN = 8;
    private static final int USERNAME_INITIAL_SUFFIX_LEN = 3;

    private String generateUniqueUsername(String fullName) {
        String fullSlug = slugify(fullName);
        int suffixLen = USERNAME_INITIAL_SUFFIX_LEN;
        String candidate = buildUsername(fullSlug, suffixLen);
        int attempt = 0;
        while (userDAO.usernameExists(candidate) && attempt < 5) {
            suffixLen = Math.min(suffixLen + 1, USERNAME_MAX_LEN - 1);
            candidate = buildUsername(fullSlug, suffixLen);
            attempt++;
        }
        return candidate;
    }

    private String buildUsername(String fullSlug, int suffixLen) {
        int maxBaseLen = Math.max(1, USERNAME_MAX_LEN - suffixLen);
        int baseLen = Math.min(maxBaseLen, fullSlug.length());
        String base = fullSlug.substring(0, baseLen);
        int effectiveSuffixLen = Math.max(suffixLen, USERNAME_MIN_LEN - baseLen);
        return base + randomHexSuffix(effectiveSuffixLen);
    }

    private String randomHexSuffix(int length) {
        String hex = UUID.randomUUID().toString().replace("-", "");
        return hex.substring(0, Math.min(length, hex.length())).toLowerCase();
    }

    private String slugify(String fullName) {
        if (fullName == null || fullName.trim().isEmpty()) {
            return "nv";
        }
        String noDMark = fullName.trim().replace('đ', 'd').replace('Đ', 'D');
        String normalized = Normalizer.normalize(noDMark, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        normalized = normalized.toLowerCase().replaceAll("[^a-z0-9]", "");
        if (normalized.isEmpty()) {
            normalized = "nv";
        }
        return normalized.length() > 20 ? normalized.substring(0, 20) : normalized;
    }

    public static class EmployeeCreationResult {
        public boolean success = false;
        public boolean emailSent = false;
        public String rawPassword;
        public String emailError;
    }

    // ⭐ CẬP NHẬT: filterByRole nhận String roleCode thay vì Role enum
    public com.utils.PaginationHelper.PaginationResult<Employee> filterByRole(
            String keyword, String roleCode, int pageNumber, int pageSize) {
        StringBuilder where = new StringBuilder();
        java.util.List<Object> params = new java.util.ArrayList<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            String escaped = keyword.trim()
                    .replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_");
            String like = "%" + escaped + "%";
            String[] columns = getSearchableColumns();
            where.append("(");
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) where.append(" OR ");
                where.append(columns[i]).append(" LIKE ? ESCAPE '!'");
                params.add(like);
            }
            where.append(")");
        }
        if (roleCode != null && !roleCode.isBlank()) {
            if (where.length() > 0) where.append(" AND ");
            where.append("r.RoleCode = ?");
            params.add(roleCode);
        }
        if (where.length() == 0) return getPaged(pageNumber, pageSize);
        return getPaged(pageNumber, pageSize, where.toString(), params.toArray());
    }
}