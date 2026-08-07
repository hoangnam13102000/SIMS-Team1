package com.model;

import java.util.Date;

/**
 * Khach hang (Role.CUSTOMER) - ke thua Users (Class-Table Inheritance),
 * CustomerID = UserID (xem bang Customers trong SIMS.sql). Model rieng nay
 * (thay vi dung lai User) de trang quan ly khach hang o admin chi hien dung
 * cac truong lien quan (khong co Username/Vai tro nhu UserAccountPanel) va
 * co them MemberPoint/CreatedAt lay tu bang Customers.
 */
public class Customer {

    private int customerId; // = User.userId
    private String customerCode; // "CUS_" + customerId dem 4 so (vd CUS_0001) - dung lam ma vach the thanh vien
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String avatarUrl;
    private boolean locked;
    private String status; // ACTIVE | DISABLED (o bang Users)
    private int memberPoint;
    private Date createdAt;

    public int getCustomerId() { return customerId; }
    public void setCustomerId(int customerId) { this.customerId = customerId; }

    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }

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

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public boolean isDisabled() {
        return "DISABLED".equalsIgnoreCase(status);
    }

    public int getMemberPoint() { return memberPoint; }
    public void setMemberPoint(int memberPoint) { this.memberPoint = memberPoint; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return "Customer{" +
                "customerId=" + customerId +
                ", customerCode='" + customerCode + '\'' +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", memberPoint=" + memberPoint +
                '}';
    }
}