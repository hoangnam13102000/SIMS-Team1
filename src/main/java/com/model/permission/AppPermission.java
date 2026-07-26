package com.model.permission;

import com.permission.Permission;

/**
 * Danh sach quyen cua framework SIMS - moi rong dan theo tung trang ban them.
 * Hien tai chi co 2 trang mau (Dashboard + Trang ca nhan) nen chi can 2 quyen
 * co ban; them cac quyen nghiep vu that (INVOICE_CREATE, PRODUCT_MANAGE,
 * PURCHASE_RECEIPT_CREATE, RETURN_APPROVE, REPORT_VIEW...) khi ban ghep tinh
 * nang cua tung vai tro (Sales Staff / Inventory Manager / Sales Manager)
 * theo de bai, roi khai bao lai trong RolePermissions.
 */
public enum AppPermission implements Permission {
    DASHBOARD_VIEW,
    USER_MANAGE,
    CUSTOMER_MANAGE
}