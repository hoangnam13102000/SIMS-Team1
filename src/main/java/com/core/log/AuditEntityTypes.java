package com.core.log;

import com.model.ActivityLog;

import java.util.HashMap;
import java.util.Map;

/**
 * Anh xa nhan hien thi (getEntityLabel()/entityLabel truyen cho BaseFormDialog,
 * vd "danh mục", "sản phẩm"...) sang hang so ActivityLog.ENTITY_* de dong bo
 * voi cac log LOGIN/LOGOUT/PASSWORD_RESET da co san (dung ENTITY_USER...).
 * Dung chung cho BaseFormDialog (log ADD/EDIT) va BaseCrudPanel (log
 * DELETE/RESTORE) de KHONG phai sua tung *Panel/*FormDialog rieng le.
 * <p>
 * Nhan nao chua co trong bang - vd them 1 *Panel/*FormDialog moi sau nay -
 * se fallback ve chinh no (in hoa, thay khoang trang bang "_") thay vi bi
 * bo qua, de log van co gia tri loc/tim kiem toi thieu.
 */
public final class AuditEntityTypes {

    private static final Map<String, String> LABEL_TO_ENTITY = new HashMap<>();
    static {
        LABEL_TO_ENTITY.put("khách hàng", ActivityLog.ENTITY_CUSTOMER);
        LABEL_TO_ENTITY.put("danh mục", ActivityLog.ENTITY_CATEGORY);
        LABEL_TO_ENTITY.put("nhà cung cấp", ActivityLog.ENTITY_SUPPLIER);
        LABEL_TO_ENTITY.put("tài khoản", ActivityLog.ENTITY_USER);
        LABEL_TO_ENTITY.put("nhân viên", ActivityLog.ENTITY_EMPLOYEE);
        LABEL_TO_ENTITY.put("lô hàng", ActivityLog.ENTITY_INVENTORY_BATCH);
        LABEL_TO_ENTITY.put("sản phẩm", ActivityLog.ENTITY_PRODUCT);
        LABEL_TO_ENTITY.put("hóa đơn", ActivityLog.ENTITY_INVOICE);
        LABEL_TO_ENTITY.put("yêu cầu hủy hóa đơn", ActivityLog.ENTITY_INVOICE_CANCEL_REQUEST);
        LABEL_TO_ENTITY.put("yêu cầu huỷ hóa đơn", ActivityLog.ENTITY_INVOICE_CANCEL_REQUEST);
        LABEL_TO_ENTITY.put("đơn hàng", ActivityLog.ENTITY_ORDER);
        LABEL_TO_ENTITY.put("cảnh báo", ActivityLog.ENTITY_STOCK_ALERT);
        LABEL_TO_ENTITY.put("phiếu nhập kho", ActivityLog.ENTITY_PURCHASE_RECEIPT);
        LABEL_TO_ENTITY.put("ca bán hàng", ActivityLog.ENTITY_SHIFT);
        LABEL_TO_ENTITY.put("giao dịch quỹ", ActivityLog.ENTITY_SHIFT_CASH_TRANSACTION);
    }

    private AuditEntityTypes() {}

    public static String resolve(String label) {
        if (label == null || label.isBlank()) return "UNKNOWN";
        String key = label.trim().toLowerCase();
        String mapped = LABEL_TO_ENTITY.get(key);
        if (mapped != null) return mapped;
        return key.toUpperCase().replace(' ', '_');
    }
}