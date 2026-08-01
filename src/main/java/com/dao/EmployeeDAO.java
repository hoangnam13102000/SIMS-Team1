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

    /** Dung lai cac ham kiem tra trung Username/Email da co san trong UserDAO thay vi viet lai. */
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
                + "u.IsLocked, u.Status, r.RoleCode, "
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
        employee.setRole(Role.valueOf(rs.getString("RoleCode")));

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

    // ---------------------------------------------------------------
    // Quản lý nhân viên (dành cho Admin)
    // ---------------------------------------------------------------

    /**
     * Admin thêm 1 nhân viên mới. Trong CÙNG 1 transaction:
     * <ol>
     *   <li>Sinh Username tu ho ten + hau to hex ngau nhien (KHONG phu thuoc EmployeeID).</li>
     *   <li>Sinh mật khẩu ngẫu nhiên (KHÔNG do Admin nhập).</li>
     *   <li>INSERT Users (Username/PasswordHash/RoleID/Status), lay UserID (IDENTITY) sinh ra.</li>
     *   <li>Sinh EmployeeID = "EMP_" + UserID dem 4 so (vd "EMP_0007") - luon
     *       duy nhat vi dua tren UserID.</li>
     *   <li>INSERT Employees (UserID, EmployeeID, DateOfBirth, Gender, Salary, HireDate).</li>
     * </ol>
     * Sau khi commit, gửi email chứa Username + mật khẩu cho nhân viên
     * (KHÔNG nằm trong transaction - gửi email thất bại không rollback dữ
     * liệu đã tạo, chỉ báo lại cho Admin qua {@link EmployeeCreationResult}
     * để cung cấp mật khẩu thủ công).
     */
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
                ps.setString(7, employee.getRole().name());

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

            // Ma nhan vien sinh SAU KHI co UserID (IDENTITY, chi biet duoc sau INSERT
            // Users o tren) - dung chinh UserID lam nen tang nen luon duy nhat tu
            // nhien, khong can vong lap kiem tra trung nhu truoc (xem generateEmployeeCode()).
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

    /**
     * Admin cập nhật thông tin 1 nhân viên (Users + Employees) trong cùng 1
     * transaction. Không đổi Username/EmployeeID/mật khẩu ở đây.
     */
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
                ps.setString(5, employee.getRole().name());
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

    /** Khoá / mở khoá 1 tài khoản nhân viên (cùng cơ chế với UserDAO.setLocked - cùng bảng Users). */
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

    /** Giống emailExistsExcluding() của UserDAO - dùng khi Admin sửa thông tin nhân viên. */
    public boolean emailExistsExcluding(String email, int excludeUserId) {
        return userDAO.emailExistsExcluding(email, excludeUserId);
    }

    /**
     * Sinh EmployeeID dang "EMP_" + UserID dem 4 so (vd UserID=7 -> "EMP_0007").
     * Ngan gon, de doc va de tim kiem (khac ban UUID/hex truoc day) - va vi
     * UserID la IDENTITY duy nhat cua Users nen EmployeeID sinh ra CHAC CHAN
     * duy nhat, khong can vong lap kiem tra trung nhu voi UUID. %04d chi la
     * DO RONG TOI THIEU - UserID > 9999 van in day du (vd "EMP_10023"), khong
     * bi cat bot.
     */
    private String generateEmployeeCode(int userId) {
        return "EMP_" + String.format("%04d", userId);
    }

    /**
     * Ghep username tu ho ten (bo dau, chu thuong, khong khoang trang) + 8 ky
     * tu hex ngau nhien - vua du duy nhat, vua ngan gon de nhan vien de dang
     * nhap. Kiem tra trung (xac suat cuc thap nhung van phong thu) va sinh
     * lai hau to moi neu trung, toi da vai lan thu.
     */
    private String generateUniqueUsername(String fullName) {
        String base = slugify(fullName);
        String suffix = randomHexSuffix();
        String candidate = base + suffix;

        int attempt = 0;
        while (userDAO.usernameExists(candidate) && attempt < 5) {
            suffix = randomHexSuffix();
            candidate = base + suffix;
            attempt++;
        }
        return candidate;
    }

    private String randomHexSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8).toLowerCase();
    }

    /** Bo dau tieng Viet, chuyen chu thuong, chi giu a-z0-9, gioi han 20 ky tu. */
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

    /** Kết quả tạo tài khoản nhân viên - dùng cho UI hiển thị mã NV/username, và mật khẩu tạm nếu gửi email thất bại. */
    public static class EmployeeCreationResult {
        public boolean success = false;
        public boolean emailSent = false;
        public String rawPassword;
        public String emailError;
    }

    /** Lọc theo vai trò, đồng thời hỗ trợ từ khóa tìm kiếm hiện tại. */
    public com.utils.PaginationHelper.PaginationResult<Employee> filterByRole(
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
}