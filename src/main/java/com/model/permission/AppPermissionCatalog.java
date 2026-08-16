package com.model.permission;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metadata hien thi (nhom module + nhan + mo ta tieng Viet) cho tung
 * {@link AppPermission} - tach rieng khoi enum AppPermission (enum chi la
 * KHOA/logic, khong the doc Javadoc luc runtime bang reflection) de
 * RolePermissionPanel co the ve UI ro rang (nhom theo module, co mo ta ngan)
 * ma khong phai nhoi chuoi hien thi vao enum.
 * <p>
 * Dung {@link LinkedHashMap} de GIU THU TU khai bao ben duoi - thu tu nay
 * quyet dinh thu tu nhom/hang muc hien thi tren RolePermissionPanel.
 */
public final class AppPermissionCatalog {

    /** 1 dong metadata cho 1 quyen: nhom (module), nhan ngan, mo ta chi tiet. */
    public static final class Entry {
        public final String group;
        public final String label;
        public final String description;

        Entry(String group, String label, String description) {
            this.group = group;
            this.label = label;
            this.description = description;
        }
    }

    private static final Map<AppPermission, Entry> CATALOG = new LinkedHashMap<>();

    static {
        put(AppPermission.DASHBOARD_VIEW, "Tổng quan",
                "Xem trang tổng quan", "Xem số liệu tổng quan (doanh thu, đơn hàng, tồn kho...) trên Dashboard.");

        put(AppPermission.USER_MANAGE, "Người dùng",
                "Quản lý tài khoản & nhân viên", "Thêm/sửa/xoá tài khoản đăng nhập và hồ sơ nhân viên.");
        put(AppPermission.CUSTOMER_MANAGE, "Người dùng",
                "Quản lý khách hàng", "Thêm/sửa/xoá thông tin khách hàng, xem lịch sử mua hàng.");

        put(AppPermission.CATEGORY_MANAGE, "Hàng hoá",
                "Quản lý danh mục", "Thêm/sửa/xoá danh mục sản phẩm.");
        put(AppPermission.PRODUCT_MANAGE, "Hàng hoá",
                "Quản lý sản phẩm", "Thêm/sửa/xoá thông tin sản phẩm, giá bán.");
        put(AppPermission.PRODUCT_VIEW, "Hàng hoá",
                "Chỉ xem sản phẩm", "Chỉ xem/tìm kiếm sản phẩm, không được thêm/sửa/xoá.");
        put(AppPermission.SUPPLIER_MANAGE, "Hàng hoá",
                "Quản lý nhà cung cấp", "Thêm/sửa/xoá thông tin nhà cung cấp.");

        put(AppPermission.STOCK_VIEW, "Kho hàng",
                "Xem tồn kho", "Xem tình trạng tồn kho, danh sách lô hàng.");
        put(AppPermission.STOCK_IMPORT, "Kho hàng",
                "Nhập kho", "Lập phiếu nhập hàng, tạo lô hàng mới.");
        put(AppPermission.STOCK_RECONCILE, "Kho hàng",
                "Đối chiếu kho cuối ngày", "Đối chiếu / kiểm kê tồn kho, so sánh tồn hệ thống với tồn đếm thực tế.");
        put(AppPermission.STOCK_DISPOSE, "Kho hàng",
                "Tiêu huỷ hàng", "Lập phiếu tiêu huỷ hàng hỏng/hết hạn (trừ lô + ghi tổn thất).");
        put(AppPermission.STOCK_DISPOSE_VIEW, "Kho hàng",
                "Xem lịch sử tiêu huỷ", "Xem lịch sử tiêu huỷ và báo cáo tổn thất tài chính.");
        put(AppPermission.STOCK_ALERT_REPORT, "Kho hàng",
                "Báo cáo hàng sắp hết", "Báo cáo sản phẩm hết/sắp hết hàng cho Quản lý kho.");
        put(AppPermission.STOCK_ALERT_VIEW, "Kho hàng",
                "Xử lý cảnh báo tồn", "Xem và xử lý các báo cáo hết/sắp hết hàng, lên kế hoạch nhập bổ sung.");
        put(AppPermission.SUPPLIER_RETURN_CREATE, "Kho hàng",
                "Trả hàng nhà cung cấp", "Lập phiếu trả hàng lô về nhà cung cấp (trừ lô + ghi công nợ).");
        put(AppPermission.SUPPLIER_RETURN_VIEW, "Kho hàng",
                "Xem trả hàng NCC", "Xem lịch sử trả hàng nhà cung cấp và báo cáo công nợ.");

        put(AppPermission.INVOICE_CREATE, "Bán hàng",
                "Tạo hoá đơn", "Lập hoá đơn bán hàng tại quầy (POS).");
        put(AppPermission.INVOICE_CANCEL, "Bán hàng",
                "Huỷ hoá đơn", "Huỷ hoá đơn đã lập.");
        put(AppPermission.SHIFT_OPERATE, "Bán hàng",
                "Vận hành ca bán hàng", "Mở ca, ghi thu/chi và đóng/đối soát ca của chính nhân viên.");
        put(AppPermission.SHIFT_VIEW_ALL, "Bán hàng",
                "Xem tất cả ca bán hàng", "Xem lịch sử ca và chênh lệch quỹ của tất cả nhân viên.");
        put(AppPermission.RETURN_EXCHANGE_CREATE, "Bán hàng",
                "Tạo yêu cầu đổi/trả", "Tạo yêu cầu đổi/trả hàng cho 1 hoá đơn (bắt buộc ghi rõ lý do).");
        put(AppPermission.RETURN_EXCHANGE_APPROVE, "Bán hàng",
                "Duyệt đổi/trả hàng", "Duyệt/từ chối yêu cầu đổi/trả hàng giá trị lớn.");
        put(AppPermission.ORDER_VIEW, "Bán hàng",
                "Xem đơn hàng online", "Xem đơn hàng online từ khách.");
        put(AppPermission.ORDER_MANAGE, "Bán hàng",
                "Xử lý đơn hàng online", "Xác nhận / huỷ đơn hàng online từ khách.");
        put(AppPermission.PROMOTION_MANAGE, "Bán hàng",
                "Quản lý khuyến mãi", "Tạo, sửa, bật/tắt, xoá khuyến mãi / mã giảm giá.");

        put(AppPermission.EXCEPTION_REPORT_CREATE, "Báo cáo",
                "Gửi báo cáo ngoại lệ", "Báo cáo sản phẩm chưa có trong hệ thống / tình huống bất thường.");
        put(AppPermission.EXCEPTION_REPORT_HANDLE, "Báo cáo",
                "Xử lý báo cáo ngoại lệ", "Xem và xử lý các báo cáo ngoại lệ từ nhân viên bán hàng.");
        put(AppPermission.REVENUE_REPORT_VIEW, "Báo cáo",
                "Báo cáo doanh thu", "Thống kê doanh thu theo thời gian / sản phẩm / phương thức thanh toán.");
        put(AppPermission.PROFIT_REPORT_VIEW, "Báo cáo",
                "Báo cáo lợi nhuận", "So sánh giá nhập/giá bán, lợi nhuận gộp theo sản phẩm/danh mục/kỳ.");
        put(AppPermission.STOCK_REPORT_VIEW, "Báo cáo",
                "Báo cáo hàng tồn kho", "Thống kê số lượng/giá trị tồn kho theo danh mục, khoảng giá bán, xu hướng theo tháng.");

        put(AppPermission.AUDIT_LOG_VIEW, "Hệ thống",
                "Nhật ký audit", "Xem lịch sử thao tác (thêm/sửa/xoá/đăng nhập...) của người dùng.");
        put(AppPermission.BACKUP_MANAGE, "Hệ thống",
                "Sao lưu & khôi phục", "Tự sao lưu thủ công hoặc khôi phục CSDL từ file backup.");
        put(AppPermission.SETTINGS_MANAGE, "Hệ thống",
                "Cài đặt hệ thống", "Xem/sửa cấu hình chung (thuế VAT, ngưỡng duyệt đổi trả...).");
        put(AppPermission.RBAC_MANAGE, "Hệ thống",
                "Phân quyền vai trò", "Xem/chỉnh sửa quyền truy cập chức năng theo từng vai trò trong hệ thống.");
    }

    private static void put(AppPermission permission, String group, String label, String description) {
        CATALOG.put(permission, new Entry(group, label, description));
    }

    /** Metadata cua 1 quyen. Khong bao gio null - quyen chua khai bao se tra ve entry mac dinh du xau. */
    public static Entry get(AppPermission permission) {
        Entry entry = CATALOG.get(permission);
        if (entry != null) return entry;
        return new Entry("Khác", permission.name(), "");
    }

    /** Toan bo catalog, GIU THU TU khai bao (dung de nhom quyen tren UI). */
    public static Map<AppPermission, Entry> all() {
        return CATALOG;
    }

    private AppPermissionCatalog() {
    }
}