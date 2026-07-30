package com.event;

public final class DataChangedEvent {

    // Hang so entity dung trong myShop - doi app khac chi can doi cac hang so nay.
    public static final String ORDER = "ORDER";
    public static final String PHONE = "PHONE";
    public static final String PURCHASE_RECEIPT = "PURCHASE_RECEIPT";
    public static final String INVOICE = "INVOICE";

    public final String entity;

    public DataChangedEvent(String entity) {
        this.entity = entity;
    }
}