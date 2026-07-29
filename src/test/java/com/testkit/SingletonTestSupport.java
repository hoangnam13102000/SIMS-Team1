package com.testkit;

/**
 * Tien ich chung de reset trang thai cua cac singleton (CartService,
 * PermissionManager, AppConfig, v.v.) truoc/sau moi test, tranh anh huong
 * cheo giua cac test class khi chay chung 1 JVM.
 *
 * KHONG phu thuoc bat ky class nao cua myShop - copy nguyen file nay sang
 * project khac va dung ngay.
 *
 * Vi du dung:
 *   @BeforeEach
 *   void reset() {
 *       SingletonTestSupport.resetAll(
 *           CartService.getInstance()::clear,
 *           PermissionManager.getInstance()::clear
 *       );
 *   }
 */
public final class SingletonTestSupport {

    private SingletonTestSupport() {
    }

    public static void resetAll(Runnable... resetActions) {
        if (resetActions == null) return;
        for (Runnable action : resetActions) {
            action.run();
        }
    }
}