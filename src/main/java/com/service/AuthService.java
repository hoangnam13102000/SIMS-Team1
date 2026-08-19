package com.service;

import com.core.log.AppLogger;
import com.model.ActivityLog;
import com.permission.Permission;
import com.permission.PermissionManager;
import com.model.Role;
import com.model.User;
import com.model.permission.RolePermissions;

public class AuthService {

    private static AuthService instance;
    private User currentUser;

    private AuthService() {
    }

    public static synchronized AuthService getInstance() {
        if (instance == null) {
            instance = new AuthService();
        }
        return instance;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public void setCurrentUser(User user) {
        this.currentUser = user;
        PermissionManager.getInstance().setCurrentPermissions(
                user != null ? RolePermissions.ofRoleCode(user.getRoleCode()) : null);
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == Role.ADMIN;
    }

    public boolean can(Permission permission) {
        return PermissionManager.getInstance().can(permission);
    }

    public void logout() {
        if (currentUser != null) {
            AppLogger.getInstance().log(currentUser.getUsername(), ActivityLog.ACTION_LOGOUT,
                    ActivityLog.ENTITY_USER, currentUser.getFullName() + " đã đăng xuất");
        }
        this.currentUser = null;
        PermissionManager.getInstance().clear();
    }
}