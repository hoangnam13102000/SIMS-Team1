package com.permission;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test dung enum gia lap ngay trong file test - khong phu thuoc model
 * cua myShop, dung de minh hoa la package com.permission co the copy
 * nguyen sang du an khac va test lai y het the nay.
 */
class PermissionSetTest {

    private enum TestPermission implements Permission { READ, WRITE, DELETE }

    @Test
    void empty_khongCoQuyenNao() {
        assertFalse(PermissionSet.EMPTY.has(TestPermission.READ));
        assertTrue(PermissionSet.EMPTY.asSet().isEmpty());
    }

    @Test
    void of_taoTapQuyenTuVarargs() {
        PermissionSet set = PermissionSet.of(TestPermission.READ, TestPermission.WRITE);
        assertTrue(set.has(TestPermission.READ));
        assertTrue(set.has(TestPermission.WRITE));
        assertFalse(set.has(TestPermission.DELETE));
    }

    @Test
    void has_permissionNull_traVeFalse() {
        PermissionSet set = PermissionSet.of(TestPermission.READ);
        assertFalse(set.has(null));
    }

    @Test
    void hasAny_coItNhatMotQuyenTrongDanhSach() {
        PermissionSet set = PermissionSet.of(TestPermission.READ);
        assertTrue(set.hasAny(TestPermission.READ, TestPermission.DELETE));
        assertFalse(set.hasAny(TestPermission.WRITE, TestPermission.DELETE));
    }

    @Test
    void hasAny_danhSachNull_traVeFalse() {
        assertFalse(PermissionSet.of(TestPermission.READ).hasAny((Permission[]) null));
    }

    @Test
    void hasAll_phaiCoDuTatCaQuyenYeuCau() {
        PermissionSet set = PermissionSet.of(TestPermission.READ, TestPermission.WRITE);
        assertTrue(set.hasAll(TestPermission.READ, TestPermission.WRITE));
        assertFalse(set.hasAll(TestPermission.READ, TestPermission.DELETE));
    }

    @Test
    void hasAll_danhSachNull_traVeTrue_theoKieuVacuousTruth() {
        // Luu y hanh vi bat doi xung voi hasAny: hasAll(null) = true, hasAny(null) = false
        assertTrue(PermissionSet.of(TestPermission.READ).hasAll((Permission[]) null));
    }

    @Test
    void of_varargsNull_traVeTapRong() {
        assertTrue(PermissionSet.of((Permission[]) null).asSet().isEmpty());
    }

    @Test
    void asSet_khongTheSuaDoiTuBenNgoai() {
        PermissionSet set = PermissionSet.of(TestPermission.READ);
        assertThrows(UnsupportedOperationException.class, () -> set.asSet().add(TestPermission.WRITE));
    }
}