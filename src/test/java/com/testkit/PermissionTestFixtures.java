package com.testkit;

import com.permission.Permission;

/**
 * Enum quyen mau, dung khi copy package com.permission sang du an moi de
 * viet test truoc khi domain that (vd AppPermission) duoc dinh nghia.
 * Khong phu thuoc gi vao myShop.
 */
public final class PermissionTestFixtures {

    private PermissionTestFixtures() {
    }

    public enum SamplePermission implements Permission {
        READ, WRITE, DELETE, ADMIN
    }
}