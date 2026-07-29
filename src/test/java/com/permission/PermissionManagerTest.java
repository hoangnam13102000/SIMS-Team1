package com.permission;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PermissionManager la singleton toan JVM, nen PHAI reset ve trang thai
 * rong truoc va sau moi test de tranh anh huong cheo giua cac test khac
 * (vd RolePermissionsTest cung dung chung instance nay giam tiep).
 */
class PermissionManagerTest {

    private enum TestPermission implements Permission { READ, WRITE }

    private final PermissionManager manager = PermissionManager.getInstance();

    @BeforeEach
    void resetTruocMoiTest() { manager.clear(); }

    @AfterEach
    void resetSauMoiTest() { manager.clear(); }

    @Test
    void macDinh_khongCoQuyenNao() {
        assertFalse(manager.can(TestPermission.READ));
    }

    @Test
    void setCurrentPermissions_capNhatQuyenHienTai() {
        manager.setCurrentPermissions(PermissionSet.of(TestPermission.READ));
        assertTrue(manager.can(TestPermission.READ));
        assertFalse(manager.can(TestPermission.WRITE));
    }

    @Test
    void setCurrentPermissions_null_duocCoiLaTapRong() {
        manager.setCurrentPermissions(null);
        assertEquals(PermissionSet.EMPTY, manager.getCurrentPermissions());
    }

    @Test
    void clear_xoaHetQuyenDaCap() {
        manager.setCurrentPermissions(PermissionSet.of(TestPermission.READ));
        manager.clear();
        assertFalse(manager.can(TestPermission.READ));
    }

    @Test
    void canAny_canAll_uyQuyenChoPermissionSetBenTrong() {
        manager.setCurrentPermissions(PermissionSet.of(TestPermission.READ));
        assertTrue(manager.canAny(TestPermission.READ, TestPermission.WRITE));
        assertFalse(manager.canAll(TestPermission.READ, TestPermission.WRITE));
    }

    @Test
    void getInstance_luonTraVeCungMotInstance() {
        assertSame(manager, PermissionManager.getInstance());
    }
}