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
    /** Doi chieu / kiem ke kho cuoi ngay - so sanh ton he thong voi ton dem thuc te. */
    STOCK_RECONCILE,
    /** Tao hoa don ban hang. */
    
    INVOICE_CREATE,     
    /** Huy hoa don. */
    INVOICE_CANCEL,      
    /** Tao yeu cau doi/tra hang cho 1 hoa don (R4: bat buoc ghi ro ly do). */
    RETURN_EXCHANGE_CREATE,
    /** Duyet/tu choi yeu cau doi/tra hang gia tri lon (R4). */
    RETURN_EXCHANGE_APPROVE,
    /** Xem don hang online tu khach. */
    ORDER_VIEW,
    /** Xac nhan / huy don hang online tu khach. */
    ORDER_MANAGE,
    /** NV ban hang bao cao SP het/sap het hang cho Quan ly kho. */
    STOCK_ALERT_REPORT,
    /** Quan ly kho xem va xu ly cac bao cao het/sap het hang (len ke hoach nhap bo sung). */
    STOCK_ALERT_VIEW,
    /** Xem trang "Sao luu & Khoi phuc", tu sao luu thu cong hoac khoi phuc DB tu file backup. */
    BACKUP_MANAGE,
    /** Xem trang "Nhat ky audit" - lich su thao tac (them/sua/xoa/dang nhap...) cua nguoi dung. */
    AUDIT_LOG_VIEW,
    /** Xem trang "Bao cao doanh thu" - thong ke doanh thu theo thoi gian/san pham/PT thanh toan. */
    REVENUE_REPORT_VIEW,
    
    
}