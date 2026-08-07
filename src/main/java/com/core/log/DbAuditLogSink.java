package com.core.log;

import com.dao.AuditLogDAO;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * LogSink chinh thuc cua ung dung - truoc day AppLogger.setSink() CHUA TUNG
 * duoc goi o dau ca (ke ca Main.java) nen moi log audit (CREATE/UPDATE/
 * DELETE/LOGIN...) bi am tham "roi mat", khong luu vao AuditLogs. Class nay
 * ket noi AppLogger voi bang AuditLogs that su (xem sql/SIMS.sql), va la
 * sink duy nhat can setSink() 1 lan trong Main.main().
 * <p>
 * Chi cac LogEntry muc {@link LogLevel#AUDIT} duoc luu vao AuditLogs (dung
 * cho man hinh "Nhat ky audit" - AuditLogPanel). Cac muc con lai (DEBUG/INFO/
 * WARN/ERROR) hien CHUA co bang luu rieng trong schema hien tai nen fallback
 * ra console/stderr - it nhat KHONG con bi mat hoan toan nhu truoc.
 */
public final class DbAuditLogSink implements LogSink {

    private final AuditLogDAO auditLogDAO = new AuditLogDAO();

    @Override
    public void write(LogEntry entry) {
        if (entry.getLevel() == LogLevel.AUDIT) {
            writeAudit(entry);
        } else {
            writeFallback(entry);
        }
    }

    private void writeAudit(LogEntry entry) {
        try {
            String snapshotForId = entry.getNewValue() != null ? entry.getNewValue() : entry.getOldValue();
            Integer recordId = extractRecordId(snapshotForId);
            auditLogDAO.insert(
                    entry.getUsername(),
                    entry.getAction(),
                    entry.getEntityType(),
                    recordId,
                    entry.getOldValue(),
                    entry.getNewValue(),
                    entry.getDescription());
            // Khong publish LogWrittenEvent o day - AppLogger.log() da tu publish
            // NGAY SAU khi goi sink.write(...) (xem AppLogger.java), publish them
            // o day se ban trung 2 lan cho cung 1 thao tac.
        } catch (Exception e) {
            // KHONG duoc goi AppLogger.getInstance().error(...) o day - chinh no la sink
            // dang duoc goi, goi lai co the tao vong lap ghi log vo han.
            System.err.println("[DbAuditLogSink] Loi khi ghi audit log: " + e.getMessage());
        }
    }

    private void writeFallback(LogEntry entry) {
        String prefix = "[" + entry.getLevel() + "] " + entry.getTimestamp() + " - " + entry.getUsername();
        String message = prefix + ": " + entry.getDescription();
        if (entry.getLevel() == LogLevel.ERROR) {
            System.err.println(message);
            if (entry.getThrowable() != null) {
                entry.getThrowable().printStackTrace();
            }
        } else {
            System.out.println(message);
        }
    }

    /**
     * Do tim ID cua ban ghi bi tac dong tu snapshot JSON (oldValue/newValue),
     * lay truong DAU TIEN co ten ket thuc bang "Id"/"ID" va gia tri la so -
     * cac model trong du an nay (Product.productId, Category.categoryId,
     * User.userId...) deu khai bao truong khoa chinh dau tien nen heuristic
     * nay du dung trong thuc te. Neu khong parse duoc (vd log khong kem
     * snapshot nhu LOGIN) tra ve null - AuditLogs.RecordID cho phep NULL.
     */
    private Integer extractRecordId(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) return null;
            JsonObject obj = parsed.getAsJsonObject();
            for (String key : obj.keySet()) {
                if (key.toLowerCase().endsWith("id")) {
                    JsonElement value = obj.get(key);
                    if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
                        return value.getAsInt();
                    }
                }
            }
        } catch (Exception ignore) {
            // Best-effort - khong lam vo hieu viec ghi log chinh.
        }
        return null;
    }
}