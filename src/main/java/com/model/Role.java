package com.model;

/**
 * 5 role hệ thống (seed DB + logic cứng: dashboard, login client/admin…).
 * Role do Admin tạo thêm không nằm trong enum — dùng {@link AppRole} / RoleCode string.
 */
public enum Role {
    ADMIN,              // Quan tri vien: quan ly user, danh muc, cau hinh he thong
    SALES_MANAGER,      // Quan ly ban hang: thong ke doanh thu, duyet doi/tra, bao cao ngoai le
    INVENTORY_MANAGER,  // Quan ly kho: nhap hang, doi chieu kho, canh bao ton
    SALES_STAFF,        // Nhan vien ban hang: tao hoa don, huy/doi tra, tim san pham
    CUSTOMER;           // Khach hang: client

    /**
     * Parse RoleCode từ DB. Role tùy chỉnh (không thuộc enum) → null (không ném exception).
     */
    public static Role tryParse(String code) {
        if (code == null || code.isBlank()) return null;
        try {
            return Role.valueOf(code.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** true nếu code là một trong 5 role hệ thống. */
    public static boolean isSystemCode(String code) {
        return tryParse(code) != null;
    }
}
