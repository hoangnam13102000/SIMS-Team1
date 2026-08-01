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
        // Quan ly ban hang: xem dashboard + xem/tim san pham.
        MAP.put(Role.SALES_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW
        ));

        // Quan ly kho: xem dashboard + xem/tim san pham.
        MAP.put(Role.INVENTORY_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.STOCK_VIEW,
                AppPermission.STOCK_IMPORT,
                AppPermission.STOCK_ALERT_VIEW
        ));
     // Nhan vien ban hang: xem dashboard, quan ly khach hang, xem/tim san pham.
        MAP.put(Role.SALES_STAFF, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.CUSTOMER_MANAGE,
                AppPermission.PRODUCT_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.STOCK_ALERT_REPORT
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