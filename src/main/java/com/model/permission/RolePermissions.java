package com.model.permission;

import com.permission.PermissionSet;
import com.model.Role;

import java.util.EnumMap;
import java.util.Map;

public final class RolePermissions {

    private static final Map<Role, PermissionSet> MAP = new EnumMap<>(Role.class);

    static {
        // ADMIN: toan quyen.
        MAP.put(Role.ADMIN, PermissionSet.of(AppPermission.values()));

        // Quan ly ban hang : xem dashboard, xem/tim san pham,
        // xem bao cao doanh thu, duyet/tu choi doi-tra gia tri lon (R4),
        // xem va xu ly bao cao ngoai le tu NV ban hang.
        MAP.put(Role.SALES_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.REVENUE_REPORT_VIEW,
                AppPermission.PROFIT_REPORT_VIEW,
                AppPermission.RETURN_EXCHANGE_APPROVE,
                AppPermission.EXCEPTION_REPORT_HANDLE
        ));

        // Quan ly kho: xem dashboard + xem/tim san pham,
        // xem ton kho, nhap hang, doi chieu kho cuoi ngay, xu ly bao cao
        // het/sap het hang tu NV ban hang. KHONG xu ly bao cao ngoai le -
        // viec do thuoc Quan ly ban hang (muc 3.3), da chuyen len tren.
        MAP.put(Role.INVENTORY_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.STOCK_VIEW,
                AppPermission.STOCK_IMPORT,
                AppPermission.STOCK_RECONCILE,
                AppPermission.STOCK_ALERT_VIEW
        ));

        // Nhan vien ban hang : xem dashboard, quan ly khach
        // hang, xem/tim san pham + trang thai ton kho, tao/huy hoa don,
        // tao yeu cau doi/tra hang cho hoa don, gui bao cao het hang va
        // bao cao ngoai le (khong phai nguoi XU LY 2 loai bao cao nay).
        MAP.put(Role.SALES_STAFF, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.CUSTOMER_MANAGE,
                AppPermission.PRODUCT_VIEW,
                AppPermission.STOCK_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.INVOICE_CANCEL,
                AppPermission.RETURN_EXCHANGE_CREATE,
                AppPermission.STOCK_ALERT_REPORT,
                AppPermission.EXCEPTION_REPORT_CREATE,
                // Xac nhan / huy don hang online tu khach - dung theo thiet ke goc
                // trong sql/Insert_SIMS.sql (RolePermissions cho SALES_STAFF), nhung
                // truoc day bi thieu o day nen trang "Don hang" (orders) chi Admin
                // moi vao duoc, du OrderDetailDialog da san co logic gate rieng theo
                // ORDER_MANAGE cho tung nut trong trang.
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE
        ));

        // CUSTOMER: khach hang tu dang ky o RegisterFrame, chi dung ClientMainFrame
        // (xem san pham, trang ca nhan) - khong co quyen nghiep vu/quan tri nao.
        MAP.put(Role.CUSTOMER, PermissionSet.EMPTY);
    }

    private RolePermissions() {
    }

    public static PermissionSet of(Role role) {
        return MAP.getOrDefault(role, PermissionSet.EMPTY);
    }
}