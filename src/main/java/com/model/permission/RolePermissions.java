package com.model.permission;

import com.permission.PermissionSet;
import com.model.Role;

import java.util.EnumMap;
import java.util.Map;

public final class RolePermissions {

    private static final Map<Role, PermissionSet> MAP = new EnumMap<>(Role.class);

    static {
        MAP.put(Role.ADMIN, PermissionSet.of(AppPermission.values()));

        MAP.put(Role.SALES_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.REVENUE_REPORT_VIEW,
                AppPermission.PROFIT_REPORT_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.INVOICE_CANCEL,
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE,
                AppPermission.RETURN_EXCHANGE_APPROVE,
                AppPermission.EXCEPTION_REPORT_HANDLE,
                AppPermission.STOCK_DISPOSE_VIEW,
                AppPermission.PROMOTION_MANAGE
        ));

        MAP.put(Role.INVENTORY_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.STOCK_VIEW,
                AppPermission.STOCK_IMPORT,
                AppPermission.STOCK_RECONCILE,
                AppPermission.STOCK_DISPOSE,
                AppPermission.STOCK_DISPOSE_VIEW,
                AppPermission.STOCK_ALERT_VIEW
        ));

        MAP.put(Role.SALES_STAFF, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.CUSTOMER_MANAGE,
                AppPermission.PRODUCT_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.INVOICE_CANCEL,
                AppPermission.RETURN_EXCHANGE_CREATE,
                AppPermission.EXCEPTION_REPORT_CREATE,
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE
        ));

        MAP.put(Role.CUSTOMER, PermissionSet.EMPTY);
    }

    private RolePermissions() {
    }

    public static PermissionSet of(Role role) {
        return MAP.getOrDefault(role, PermissionSet.EMPTY);
    }
}
