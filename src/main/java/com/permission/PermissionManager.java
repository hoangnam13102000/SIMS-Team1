package com.permission;

/**
 * Giu tap quyen (PermissionSet) cua phien lam viec hien tai (singleton, chi
 * dung trong bo nho) - cung mo hinh voi AuthService nhung tach rieng khoi
 * model User/Role cu the, nen co the copy nguyen package com.core.permission
 * sang du an khac ma khong sua gi.
 */
public final class PermissionManager {

    private static final PermissionManager INSTANCE = new PermissionManager();

    public static PermissionManager getInstance() {
        return INSTANCE;
    }

    private PermissionSet current = PermissionSet.EMPTY;

    private PermissionManager() {
    }

    public void setCurrentPermissions(PermissionSet permissions) {
        this.current = permissions != null ? permissions : PermissionSet.EMPTY;
    }

    public boolean can(Permission permission) {
        return current.has(permission);
    }

    public boolean canAny(Permission... permissions) {
        return current.hasAny(permissions);
    }

    public boolean canAll(Permission... permissions) {
        return current.hasAll(permissions);
    }

    public PermissionSet getCurrentPermissions() {
        return current;
    }

    public void clear() {
        current = PermissionSet.EMPTY;
    }
}