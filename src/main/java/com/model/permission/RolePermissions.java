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

        // 3 vai tro nghiep vu con lai: hien tai dang nhap vao ClientMainFrame
        // (chua co Sidebar/permission-gated page nao rieng), nen chua can
        // gan quyen gi. Khi ban tach rieng man hinh cho tung vai tro (vd
        // Sales Staff -> trang Tao hoa don, Inventory Manager -> trang Nhap
        // kho...), them dong MAP.put(Role.X, PermissionSet.of(...)) tuong ung.
        MAP.put(Role.SALES_MANAGER, PermissionSet.EMPTY);
        MAP.put(Role.INVENTORY_MANAGER, PermissionSet.EMPTY);
        MAP.put(Role.SALES_STAFF, PermissionSet.EMPTY);

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