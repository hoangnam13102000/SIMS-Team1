package com.model.permission;

import com.permission.Permission;

public enum AppPermission implements Permission {
    DASHBOARD_VIEW,
    USER_MANAGE,
    /** Chi xem tai khoan & nhan vien. */
    USER_VIEW,
    /** Chi sua tai khoan & nhan vien (khong them / khoa). */
    USER_EDIT,
    CUSTOMER_MANAGE,
    /** Chi xem khach hang. */
    CUSTOMER_VIEW,
    /** Chi sua khach hang (khong xoa). */
    CUSTOMER_EDIT,
    CATEGORY_MANAGE,
    /** Chi xem danh muc (khong them/sua/xoa). */
    CATEGORY_VIEW,
    /** Chi sua danh muc da co + doi trang thai (khong them moi). */
    CATEGORY_EDIT,
    PRODUCT_MANAGE,
    /** Chi xem / tim kiem san pham (khong them/sua/xoa). */
    PRODUCT_VIEW,
    /** Chi sua san pham da co + doi trang thai ban (khong them moi). */
    PRODUCT_EDIT,
    SUPPLIER_MANAGE,
    /** Chi xem nha cung cap (khong them/sua/xoa). */
    SUPPLIER_VIEW,
    /** Chi sua nha cung cap da co (khong them moi / xoa). */
    SUPPLIER_EDIT,
    /** Xem trang thai ton kho / danh sach lo hang. */
    STOCK_VIEW,
    /** Nhap hang vao kho - tao lo hang moi. */
    STOCK_IMPORT,
    /** Doi chieu / kiem ke kho cuoi ngay - so sanh ton he thong voi ton dem thuc te. */
    STOCK_RECONCILE,
    /** Tao hoa don ban hang. */
    INVOICE_CREATE,

    /** Xem hoa don do chinh nhan vien hien tai tao. */
    INVOICE_VIEW_OWN,

    /** Xem tat ca hoa don cua he thong. */
    INVOICE_VIEW_ALL,

    /** Mo ca, ghi thu/chi va dong/doi soat ca cua chinh nhan vien. */
    SHIFT_OPERATE,
    /** Xem lich su ca va chenh lech quy cua tat ca nhan vien. */
    SHIFT_VIEW_ALL,
    /** Duyet / tu choi doi soat ca (man hinh giam sat ca). */
    SHIFT_APPROVE,
    /** Gui yeu cau huy hoa don de Sales Manager/Admin duyet. */
    INVOICE_CANCEL_REQUEST,
    /** Huy hoa don (quyen quan ly/Admin; SALES_STAFF khong duoc huy truc tiep). */
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
    /** NV ban hang gui bao cao ngoai le (SP chua co trong he thong, tinh huong bat thuong) cho Quan ly ban hang. */
    EXCEPTION_REPORT_CREATE,
    /** Chi xem danh sach bao cao ngoai le (khong gui / xu ly). */
    EXCEPTION_REPORT_VIEW,
    /** Quan ly ban hang xem va xu ly cac bao cao ngoai le tu NV ban hang. */
    EXCEPTION_REPORT_HANDLE,
    /** Xem trang "Bao cao loi nhuan" - so sanh gia nhap/gia ban, loi nhuan gop
     *  theo san pham/danh muc/ky (de bai muc 3.3, chi Quan ly ban hang + Admin). */
    PROFIT_REPORT_VIEW,
    /** Xem/sua trang "Cai dat he thong" (VAT_RATE va cac cau hinh StoreConfig khac). */
    SETTINGS_MANAGE,
    /** Lap phieu tieu huy hang (tru lo + ghi ton that). */
    STOCK_DISPOSE,
    /** Xem lich su tieu huy va bao cao ton that tai chinh. */
    STOCK_DISPOSE_VIEW,
    /** Xem trang "Bao cao hang ton kho" - thong ke so luong/gia tri ton theo
     *  danh muc, khoang gia ban, xu huong ton kho theo thang (khac STOCK_VIEW:
     *  chi xem tinh trang ton kho hien tai / danh sach lo hang, khong bao gom
     *  bao cao thong ke nay). */
    STOCK_REPORT_VIEW,
    /** Lap phieu tra hang lo ve nha cung cap (tru lo + ghi cong no NCC). */
    SUPPLIER_RETURN_CREATE,
    /** Xem lich su tra hang NCC va bao cao cong no. */
    SUPPLIER_RETURN_VIEW,
    /** Quan ly khuyen mai / ma giam gia (tao, sua, bat-tat, xoa). */
    PROMOTION_MANAGE,
    /** Xem/chinh sua trang "Phan quyen vai tro" - bat/tat quyen cua tung Role. Mac dinh chi ADMIN co (xem RolePermissions). */
    RBAC_MANAGE,
}