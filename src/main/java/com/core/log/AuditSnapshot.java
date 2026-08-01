package com.core.log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Gson dung chung de chup snapshot JSON (oldValue/newValue) cho audit trail.
 * Tach rieng khoi BaseFormDialog (noi dau tien dung ky thuat nay) de
 * BaseCrudPanel/cac *Panel cung dung chung 1 cau hinh, tranh lap lai loi da
 * gap: Gson mac dinh dung Reflection de doc field private cua java.time.LocalDate/
 * LocalDateTime se nem InaccessibleObjectException tren Java 9+ (module
 * java.base khong "opens java.time" cho unnamed module).
 */
public final class AuditSnapshot {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDate.class,
                    (JsonSerializer<LocalDate>) (src, typeOfSrc, context) ->
                            src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString()))
            .registerTypeAdapter(LocalDateTime.class,
                    (JsonSerializer<LocalDateTime>) (src, typeOfSrc, context) ->
                            src == null ? JsonNull.INSTANCE : new JsonPrimitive(src.toString()))
            .create();

    private AuditSnapshot() {}

    /** Tra ve null neu entity null - khong dung cho ADD (chua co ban ghi goc). */
    public static String toJson(Object entity) {
        if (entity == null) return null;
        try {
            return GSON.toJson(entity);
        } catch (Exception e) {
            // Audit log khong duoc phep lam vo hieu luong nghiep vu chinh neu serialize loi.
            return null;
        }
    }
}