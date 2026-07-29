package com.permission;

public interface Permission {

    /** Khoa duy nhat cua quyen. Mac dinh dung ten enum constant (toString()). */
    default String key() {
        return toString();
    }
}