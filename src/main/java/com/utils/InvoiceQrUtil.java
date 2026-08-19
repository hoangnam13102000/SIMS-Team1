package com.utils;

import com.model.Invoice;
import java.time.format.DateTimeFormatter;

/** Payload QR nội bộ, không chứa dữ liệu nhạy cảm của khách hàng. */
public final class InvoiceQrUtil {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private InvoiceQrUtil() {}

    public static String payload(Invoice invoice) {
        String code = invoice != null && invoice.getInvoiceCode() != null ? invoice.getInvoiceCode() : "";
        String total = invoice != null && invoice.getOriginalTotalAmount() != null
                ? invoice.getOriginalTotalAmount().toPlainString()
                : invoice != null && invoice.getTotalAmount() != null ? invoice.getTotalAmount().toPlainString() : "0";
        String created = invoice != null && invoice.getCreatedAt() != null ? invoice.getCreatedAt().format(FMT) : "";
        return "SIMS|INVOICE|" + code + "|" + total + "|" + created;
    }
    /**
     * Trích mã hóa đơn từ QR nội bộ. Chấp nhận cả mã HD-... thuần để hỗ trợ
     * máy quét/keyboard wedge. Trả về null nếu nội dung không phải QR/mã hóa đơn SIMS.
     */
    public static String extractInvoiceCode(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if (value.isEmpty()) return null;
        if (value.regionMatches(true, 0, "SIMS|INVOICE|", 0, "SIMS|INVOICE|".length())) {
            String[] parts = value.split("\\|", -1);
            if (parts.length >= 3 && !parts[2].isBlank()) return parts[2].trim();
            return null;
        }
        return value.toUpperCase().startsWith("HD-") ? value : null;
    }

    /** Nếu là payload QR hóa đơn thì đổi về mã HD-..., còn từ khóa thường giữ nguyên. */
    public static String normalizeSearchKeyword(String raw) {
        String code = extractInvoiceCode(raw);
        return code != null ? code : (raw != null ? raw.trim() : "");
    }

}
