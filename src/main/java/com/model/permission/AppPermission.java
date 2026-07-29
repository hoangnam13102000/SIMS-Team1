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
    SUPPLIER_MANAGE
    
    
}