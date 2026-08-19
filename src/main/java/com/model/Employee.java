package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Employee {
    private int userId;
    private String employeeId;
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    
    // ⭐ Giữ enum cho tương thích ngược (chỉ dùng cho 5 vai trò hệ thống)
    private Role role;
    
    // ⭐ FIELD MỚI: Lưu mã vai trò dạng String — hỗ trợ cả vai trò tùy chỉnh
    private String roleCode;
    
    private boolean locked;
    private String status;
    private LocalDate dateOfBirth;
    private Gender gender;
    private BigDecimal salary;
    private LocalDate hireDate;
    private LocalDateTime createdAt;

    public enum Gender { MALE, FEMALE, OTHER }

    // ==================== GETTER / SETTER ====================
    
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }
    
    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) { this.employeeId = employeeId; }
    
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
    
    // ⭐ GETTER/SETTER MỚI: Ưu tiên roleCode, fallback về enum
    public String getRoleCode() {
        if (roleCode != null && !roleCode.isBlank()) return roleCode;
        return role != null ? role.name() : null;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
        // Đồng bộ với enum nếu là vai trò hệ thống
        this.role = Role.tryParse(roleCode);
    }

    // ⭐ Override setRole để đồng bộ cả hai
    public Role getRole() { return role; }
    
    public void setRole(Role role) {
        this.role = role;
        if (role != null) {
            this.roleCode = role.name();
        }
    }
    
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isDisabled() {
        return "DISABLED".equalsIgnoreCase(status);
    }
    
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    
    public BigDecimal getSalary() { return salary; }
    public void setSalary(BigDecimal salary) { this.salary = salary; }
    
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Employee{" +
                "userId=" + userId +
                ", employeeId='" + employeeId + '\'' +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", roleCode='" + getRoleCode() + '\'' +
                '}';
    }
}