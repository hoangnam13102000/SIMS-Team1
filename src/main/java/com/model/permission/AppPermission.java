package com.model.permission;

import com.permission.Permission;

public enum AppPermission implements Permission {
    DASHBOARD_VIEW,
    USER_MANAGE,
    CUSTOMER_MANAGE,
    CATEGORY_MANAGE,
    PRODUCT_MANAGE,
    /** Chi xem / tim kiem san pham (khong them/sua/xoa). */
    PRODUCT_VIEW,
    SUPPLIER_MANAGE,
    /** Xem trang thai ton kho / danh sach lo hang. */
    STOCK_VIEW,
    /** Nhap hang vao kho - tao lo hang moi. */
    STOCK_IMPORT,
    /** Tao hoa don ban hang. */
    INVOICE_CREATE,     
    /** Huy hoa don. */
    INVOICE_CANCEL      
    
    
}