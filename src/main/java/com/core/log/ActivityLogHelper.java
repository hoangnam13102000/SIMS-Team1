package com.core.log;

import com.model.User;
import com.service.AuthService;

/**
 * Diem goi chung de cac man hinh (BaseFormDialog, BaseCrudPanel, va vai
 * *Panel co hanh dong dac thu nhu khoa/mo khoa tai khoan) ghi audit log ma
 * KHONG phai tu lap lai logic "lay username hien tai + resolve entityType +
 * serialize JSON" o moi noi. Tat ca deu di qua AppLogger.log(...) (Muc
 * AUDIT) - xem DbAuditLogSink de biet noi du lieu that su duoc luu.
 */
public final class ActivityLogHelper {

    private ActivityLogHelper() {}

    /** Username cua nguoi dang dang nhap, "SYSTEM" neu chua dang nhap (vd loi xay ra o LoginFrame). */
    public static String currentUsername() {
        try {
            User u = AuthService.getInstance().getCurrentUser();
            return u != null ? u.getUsername() : "SYSTEM";
        } catch (Exception ignore) {
            return "SYSTEM";
        }
    }

    /**
     * Ghi 1 dong audit log, tu dong resolve entityType tu entityLabel (xem
     * AuditEntityTypes) va serialize oldEntity/newEntity thanh JSON (xem
     * AuditSnapshot). Bat loi noi bo - audit log khong duoc phep lam vo hieu
     * luong nghiep vu chinh (vd them/sua/xoa) neu ban than no that bai.
     *
     * @param entityLabel nhan hien thi (vd "sản phẩm", "danh mục") - giong tham so entityLabel cua BaseFormDialog.
     * @param action      1 trong cac hang so ActivityLog.ACTION_*.
     * @param description mo ta ngan cho dong log (hien thi truc tiep tren AuditLogPanel).
     * @param oldEntity   doi tuong TRUOC thay doi - null neu khong ap dung (vd CREATE).
     * @param newEntity   doi tuong SAU thay doi - null neu khong ap dung (vd DELETE).
     */
    public static void record(String entityLabel, String action, String description,
                               Object oldEntity, Object newEntity) {
        try {
            String entityType = AuditEntityTypes.resolve(entityLabel);
            String oldJson = AuditSnapshot.toJson(oldEntity);
            String newJson = AuditSnapshot.toJson(newEntity);
            AppLogger.getInstance().log(currentUsername(), action, entityType, description, oldJson, newJson);
        } catch (Exception ignore) {
            // Best-effort - khong duoc phep nem loi nguoc len UI vi audit log that bai.
        }
    }

    /** Bien the khong kem snapshot JSON - dung cho cac hanh dong don gian (vd khoa/mo khoa tai khoan). */
    public static void record(String entityLabel, String action, String description) {
        record(entityLabel, action, description, null, null);
    }
}