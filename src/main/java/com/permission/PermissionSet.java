package com.permission;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tap hop cac quyen ma 1 user/role dang co. La lop bao boc quanh
 * Set<Permission>, khong phu thuoc domain nao ca - dung chung cho moi
 * du an Swing (hoac bat ky Java app nao khac).
 */
public final class PermissionSet {

    /** Tap rong, dung cho user chua dang nhap hoac role khong co quyen nao. */
    public static final PermissionSet EMPTY = new PermissionSet(Collections.emptySet());

    private final Set<Permission> permissions;

    private PermissionSet(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public static PermissionSet of(Permission... permissions) {
        Set<Permission> set = new HashSet<>();
        if (permissions != null) Collections.addAll(set, permissions);
        return new PermissionSet(set);
    }

    public static PermissionSet of(Set<Permission> permissions) {
        return new PermissionSet(permissions == null ? Collections.emptySet() : new HashSet<>(permissions));
    }

    public boolean has(Permission permission) {
        return permission != null && permissions.contains(permission);
    }

    public boolean hasAny(Permission... required) {
        if (required == null) return false;
        for (Permission p : required) {
            if (has(p)) return true;
        }
        return false;
    }

    public boolean hasAll(Permission... required) {
        if (required == null) return true;
        for (Permission p : required) {
            if (!has(p)) return false;
        }
        return true;
    }

    public Set<Permission> asSet() {
        return Collections.unmodifiableSet(permissions);
    }
}