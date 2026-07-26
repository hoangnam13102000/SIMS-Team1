package com.model;

public enum Role {
    ADMIN,              // Quan tri vien: quan ly user, danh muc, cau hinh he thong
    SALES_MANAGER,      // Quan ly ban hang: thong ke doanh thu, duyet doi/tra, bao cao ngoai le
    INVENTORY_MANAGER,  // Quan ly kho: nhap hang, doi chieu kho, canh bao ton
    SALES_STAFF,        // Nhan vien ban hang: tao hoa don, huy/doi tra, tim san pham
    CUSTOMER            // Khach hang: tu dang ky qua RegisterFrame, xem san pham/mua hang o phia client
}