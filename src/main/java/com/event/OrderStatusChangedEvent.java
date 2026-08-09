package com.event;

/**
 * Phát ra mỗi khi {@code OrderDAO.updateOrderStatus} chuyển trạng thái 1 đơn
 * hàng THÀNH CÔNG - dùng để đẩy thông báo "phù hợp với từng tài khoản" lên
 * chuông thông báo (Header) của MỌI phiên admin đang mở, không chỉ riêng
 * người vừa thao tác:
 * <ul>
 *   <li>{@code AdminMainFrame} lắng nghe sự kiện này và chỉ thêm thông báo
 *       vào chuông nếu tài khoản đang đăng nhập có quyền ORDER_VIEW/ORDER_MANAGE
 *       (mỗi tài khoản tự quyết định có thấy hay không dựa theo quyền của
 *       chính nó - "phù hợp với từng tài khoản").</li>
 *   <li>Khác với {@link DataChangedEvent}(ORDER) vốn chỉ để các panel tự
 *       reload dữ liệu, event này mang đủ ngữ cảnh (mã đơn, trạng thái cũ/mới,
 *       có phải do trợ lý AI thực hiện hay không) để dựng nội dung thông báo.</li>
 * </ul>
 * Được publish bởi chính {@code OrderDAO.updateOrderStatus} nên áp dụng
 * đồng nhất cho CẢ thao tác bấm nút thủ công (OrderDetailDialog) LẪN thao
 * tác qua chatbot (AiToolExecutor) - không cần trùng lặp logic ở 2 nơi.
 */
public final class OrderStatusChangedEvent {

    private final int orderId;
    private final String orderCode;
    private final String oldStatus;
    private final String newStatus;
    private final int actorUserId;
    private final boolean viaAssistant;

    public OrderStatusChangedEvent(int orderId, String orderCode, String oldStatus, String newStatus,
                                    int actorUserId, boolean viaAssistant) {
        this.orderId = orderId;
        this.orderCode = orderCode;
        this.oldStatus = oldStatus;
        this.newStatus = newStatus;
        this.actorUserId = actorUserId;
        this.viaAssistant = viaAssistant;
    }

    public int getOrderId() { return orderId; }
    public String getOrderCode() { return orderCode; }
    public String getOldStatus() { return oldStatus; }
    public String getNewStatus() { return newStatus; }
    public int getActorUserId() { return actorUserId; }
    public boolean isViaAssistant() { return viaAssistant; }
}