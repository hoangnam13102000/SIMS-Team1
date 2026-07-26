package com.event;

/**
 * Bao "du lieu 1 nhom entity da thay doi" (them/sua/xoa, doi trang thai...)
 * ma khong kem chi tiet - chi bao "hay load lai di". Dung entity de panel
 * chi lang nghe dung thu no can, tranh reload toan bo khi khong lien quan.
 */
public final class DataChangedEvent {

    // Hang so entity dung trong myShop - doi app khac chi can doi cac hang so nay.
    public static final String ORDER = "ORDER";
    public static final String PHONE = "PHONE";

    public final String entity;

    public DataChangedEvent(String entity) {
        this.entity = entity;
    }
}