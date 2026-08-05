package com.model;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Mot dong thong bao hien trong dropdown chuong tren Header (xem
 * com.view.layouts.Header). Thiet ke chung cho nhieu loai nguon: don hang
 * online moi, hoa don, canh bao ton kho, tin nhan chat... - moi loai co
 * icon/mau rieng khi ve (xem Header.NotificationRow).
 * <p>
 * "refId" la ID cua ban ghi goc (vd OrderID) de xu ly khi bam vao thong bao
 * (dieu huong sang trang tuong ung) hoac khi xoa (danh dau da xem trong DB).
 */
public class NotificationItem {

    public enum Type { ORDER, INVOICE, STOCK, MESSAGE, SYSTEM }

    private final String id;
    private final Type type;
    private final String title;
    private final String message;
    private final LocalDateTime time;
    private final Integer refId;

    public NotificationItem(String id, Type type, String title, String message, LocalDateTime time, Integer refId) {
        this.id = id;
        this.type = type;
        this.title = title;
        this.message = message;
        this.time = time;
        this.refId = refId;
    }

    public String getId() { return id; }
    public Type getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public LocalDateTime getTime() { return time; }
    public Integer getRefId() { return refId; }

    /** Chuoi thoi gian tuong doi kieu "Vua xong", "5 phut truoc", "2 gio truoc"... */
    public String getRelativeTime() {
        if (time == null) return "";
        Duration d = Duration.between(time, LocalDateTime.now());
        long seconds = Math.max(0, d.getSeconds());
        if (seconds < 60) return "Vừa xong";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " phút trước";
        long hours = minutes / 60;
        if (hours < 24) return hours + " giờ trước";
        long days = hours / 24;
        if (days < 7) return days + " ngày trước";
        return time.toLocalDate().toString();
    }
}