package com.core.log;

/**
 * Mã lỗi chuẩn hoá dùng chung toàn app - để khi người dùng báo lỗi (vd
 * "ERR-DB-002"), lập trình viên tra thẳng ra nhóm nguyên nhân mà không cần
 * đọc lại toàn bộ stack trace hay hỏi lại người dùng đang làm gì.
 * <p>
 * Quy ước đặt mã: {@code ERR-<NHÓM>-<SỐ THỨ TỰ 3 CHỮ SỐ>}. Khi thêm mã mới,
 * thêm vào cuối nhóm tương ứng (không đổi số của mã đã có - mã cũ có thể đã
 * được lưu trong log/DB, đổi số sẽ làm sai lệch log cũ).
 */
public enum ErrorCode {
	

    // ==== Cơ sở dữ liệu (DAO) ====
    DB_CONNECTION_FAIL("ERR-DB-001", "Không kết nối được cơ sở dữ liệu"),
    DB_QUERY_FAIL("ERR-DB-002", "Truy vấn dữ liệu thất bại"),
    DB_INSERT_FAIL("ERR-DB-003", "Thêm dữ liệu thất bại"),
    DB_UPDATE_FAIL("ERR-DB-004", "Cập nhật dữ liệu thất bại"),
    DB_DELETE_FAIL("ERR-DB-005", "Xoá dữ liệu thất bại"),
    DB_PAGINATION_FAIL("ERR-DB-006", "Phân trang/đếm dữ liệu thất bại"),

    // ==== Xác thực & phiên đăng nhập ====
    AUTH_LOGIN_FAIL("ERR-AUTH-001", "Đăng nhập thất bại"),
    AUTH_REMEMBER_ME_FAIL("ERR-AUTH-002", "Xử lý Remember-Me thất bại"),
    AUTH_ACCOUNT_LOCKED("ERR-AUTH-003", "Tài khoản bị tạm khoá do đăng nhập sai quá số lần cho phép"),
    AUTH_PASSWORD_RESET_FAIL("ERR-AUTH-004", "Đặt lại mật khẩu thất bại"),

    // ==== Đơn hàng ====
    ORDER_CREATE_FAIL("ERR-ORD-001", "Tạo đơn hàng thất bại"),
    ORDER_STOCK_UPDATE_FAIL("ERR-ORD-002", "Cập nhật tồn kho thất bại"),
    ORDER_STATUS_UPDATE_FAIL("ERR-ORD-003", "Cập nhật trạng thái đơn hàng thất bại"),
    ORDER_CHECKOUT_FAIL("ERR-ORD-004", "Thanh toán/hoàn tất đơn hàng thất bại"),

 // ==== Hóa đơn bán hàng (POS) ====
    INVOICE_CREATE_FAIL("ERR-INV-001", "Lập hóa đơn bán hàng thất bại"),

    
 // ==== Trợ lý AI (Gemini) ====
    AI_CHAT_FAIL("ERR-AI-001", "Gọi trợ lý AI thất bại"),
    // ==== Đổi/trả hàng ====
    RETURN_CREATE_FAIL("ERR-RET-001", "Tạo yêu cầu đổi/trả hàng thất bại"),
    RETURN_STATUS_UPDATE_FAIL("ERR-RET-002", "Cập nhật trạng thái yêu cầu đổi/trả hàng thất bại"),
    
    // ==== WebSocket / thời gian thực ====
    WS_CONNECTION_FAIL("ERR-WS-001", "Kết nối WebSocket thất bại"),
    WS_MESSAGE_FAIL("ERR-WS-002", "Gửi/nhận thông điệp WebSocket thất bại"),
    WS_SERVER_START_FAIL("ERR-WS-003", "Khởi động WebSocket server thất bại"),

    // ==== Sao lưu & phục hồi ====
    BACKUP_FAIL("ERR-BAK-001", "Sao lưu dữ liệu thất bại"),

    // ==== Giao diện người dùng ====
    UI_ACTION_FAIL("ERR-UI-001", "Thao tác trên giao diện thất bại"),
    UI_DATA_LOAD_FAIL("ERR-UI-002", "Tải dữ liệu hiển thị lên giao diện thất bại"),
    
	// ==== Email ====
    EMAIL_SEND_FAIL("ERR-MAIL-001", "Gửi email thất bại"),

    // ==== Hệ thống / không phân loại được ====
    SYSTEM_UNCAUGHT("ERR-SYS-001", "Lỗi hệ thống không xác định (uncaught exception)");
	
	

    private final String code;
    private final String description;

    ErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }

    /** Dùng khi ghép vào message log: "[ERR-DB-002] Truy vấn dữ liệu thất bại". */
    @Override
    public String toString() {
        return "[" + code + "] " + description;
    }
}