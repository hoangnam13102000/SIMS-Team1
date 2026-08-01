package com.event;

public final class DataChangedEvent {

    // Hang so entity dung trong myShop - doi app khac chi can doi cac hang so nay.
    public static final String ORDER = "ORDER";
    public static final String PHONE = "PHONE";
    public static final String PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    public static final String INVOICE = "INVOICE";
    public static final String STOCK_ALERT = "STOCK_ALERT";
    
    /** Dùng khi restore / thao tác ghi đè toàn bộ DB — mọi panel đều reload. */
    public static final String ALL = "ALL";

    public final String entity;

    public DataChangedEvent(String entity) {
        this.entity = entity;
    }

    /**
     * Báo toàn app: dữ liệu đã thay đổi lớn (vd sau restore backup).
     * An toàn gọi từ bất kỳ thread nào — AppEventBus đưa callback về EDT.
     */
    public static void publishFullRefresh() {
        AppEventBus.getInstance().publish(new DataChangedEvent(ALL));
    }
}