package com.model;

public class User {

    private int userId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    /**
     * Role hệ thống (enum). null nếu user thuộc role do Admin tạo (chỉ có {@link #roleCode}).
     */
    private Role role;
    /** RoleCode trong DB (luôn có) — dùng phân quyền khi role tùy chỉnh. */
    private String roleCode;
    private boolean locked;
    private int failedLoginCount;
    private String status; // ACTIVE | DISABLED
    private java.time.LocalDateTime createdAt;
    private String employeeCode; // "EMP_0001" - null neu Role.CUSTOMER

    public User() {
    }

    public User(int userId, String username, String fullName, String email, String phone, Role role) {
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.phone = phone;
        setRole(role);
    }

    public java.time.LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.LocalDateTime createdAt) { this.createdAt = createdAt; }

    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public Role getRole() { return role; }

    public void setRole(Role role) {
        this.role = role;
        if (role != null) {
            this.roleCode = role.name();
        }
    }

    public String getRoleCode() {
        if (roleCode != null && !roleCode.isBlank()) return roleCode;
        return role != null ? role.name() : null;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
        this.role = Role.tryParse(roleCode);
    }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public int getFailedLoginCount() { return failedLoginCount; }
    public void setFailedLoginCount(int failedLoginCount) { this.failedLoginCount = failedLoginCount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getEmployeeCode() { return employeeCode; }
    public void setEmployeeCode(String employeeCode) { this.employeeCode = employeeCode; }

    public boolean isDisabled() {
        return "DISABLED".equalsIgnoreCase(status);
    }

    /** true nếu là khách hàng (client), kể cả khi chỉ có roleCode. */
    public boolean isCustomer() {
        return Role.CUSTOMER == role
                || Role.CUSTOMER.name().equalsIgnoreCase(getRoleCode());
    }

    @Override
    public String toString() {
        return "User{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", roleCode='" + getRoleCode() + '\'' +
                '}';
    }
}
