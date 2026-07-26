package com.model;

/**
 * Vai tro nguoi dung SIMS - phai khop CHINH XAC voi cot RoleCode trong bang
 * Roles cua database (xem SIMS.sql), vi UserDAO doi RoleCode -> Role bang
 * Role.valueOf(rs.getString("RoleCode")).
 */
public enum Role {
    ADMIN,              // Quan tri vien: quan ly user, danh muc, cau hinh he thong
    SALES_MANAGER,      // Quan ly ban hang: thong ke doanh thu, duyet doi/tra, bao cao ngoai le
    INVENTORY_MANAGER,  // Quan ly kho: nhap hang, doi chieu kho, canh bao ton
    SALES_STAFF         // Nhan vien ban hang: tao hoa don, huy/doi tra, tim san pham
}