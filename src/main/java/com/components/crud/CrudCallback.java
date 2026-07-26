package com.components.crud;

/**
 * Callback được BaseFormDialog gọi sau khi lưu (thêm/sửa) thành công.
 * BaseCrudPanel dùng callback này để tự reload dữ liệu + hiện thông báo,
 * thay vì mỗi *Panel phải tự viết lại logic "if (dialog.isSaved()) {...}".
 */
public interface CrudCallback<T> {
    void onSaved(T entity, CrudMode mode);
}