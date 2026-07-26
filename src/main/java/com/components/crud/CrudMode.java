package com.components.crud;

/**
 * Chế độ hoạt động của một form CRUD (BaseFormDialog).
 * Dùng chung cho mọi entity thay vì mỗi FormDialog tự định nghĩa boolean/flag riêng.
 */
public enum CrudMode {
    ADD,
    EDIT,
    VIEW;

    /** VIEW là chế độ chỉ đọc: ẩn nút Lưu, disable toàn bộ input. */
    public boolean isReadOnly() {
        return this == VIEW;
    }
}