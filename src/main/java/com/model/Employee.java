package com.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class Employee {

    private int userId;         // = Users.UserID (khoa ke thua, KHONG hien thi cho nguoi dung)
    private String employeeId;  // Ma nhan vien (UUID) - hien thi cho nguoi dung
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private Role role;
    private boolean locked;
    private String status; // ACTIVE | DISABLED (o bang Users)
    private LocalDate dateOfBirth;
    private Gender gender;
    private BigDecimal salary;
    private LocalDate hireDate;
    private LocalDateTime createdAt;

    public enum Gender { MALE, FEMALE, OTHER }

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

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

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
                ", role=" + role +
                '}';
    }
}