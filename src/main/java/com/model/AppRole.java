package com.model;

/**
 * Bản ghi role trong bảng {@code Roles} — gồm role hệ thống (enum {@link Role})
 * và role do Admin tạo thêm (IsSystem = false).
 * <p>
 * Mã {@link #roleCode} là khóa nghiệp vụ (UNIQUE), dùng khi gán user và
 * tra cứu {@code RolePermissions}.
 */
public class AppRole {

    private int roleId;
    private String roleCode;
    private String roleName;
    private String description;
    /** true = ADMIN / SALES_* / INVENTORY_* / CUSTOMER — không xóa được trên UI. */
    private boolean systemRole;

    public AppRole() {
    }

    public AppRole(int roleId, String roleCode, String roleName, String description, boolean systemRole) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.description = description;
        this.systemRole = systemRole;
    }

    public int getRoleId() { return roleId; }
    public void setRoleId(int roleId) { this.roleId = roleId; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isSystemRole() { return systemRole; }
    public void setSystemRole(boolean systemRole) { this.systemRole = systemRole; }

    /** true nếu là ADMIN (luôn toàn quyền, UI chỉ đọc). */
    public boolean isAdmin() {
        return Role.ADMIN.name().equalsIgnoreCase(roleCode);
    }

    /** true nếu là CUSTOMER (không quản lý trên trang RBAC admin). */
    public boolean isCustomer() {
        return Role.CUSTOMER.name().equalsIgnoreCase(roleCode);
    }

    /**
     * Map sang {@link Role} enum nếu là 1 trong 5 role hệ thống;
     * role tùy chỉnh → null.
     */
    public Role toEnumOrNull() {
        return Role.tryParse(roleCode);
    }

    @Override
    public String toString() {
        return roleName != null ? roleName : roleCode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AppRole other)) return false;
        return roleCode != null && roleCode.equalsIgnoreCase(other.roleCode);
    }

    @Override
    public int hashCode() {
        return roleCode != null ? roleCode.toUpperCase().hashCode() : 0;
    }
}
