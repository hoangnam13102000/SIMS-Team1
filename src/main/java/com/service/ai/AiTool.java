package com.service.ai;

import com.model.permission.AppPermission;
import com.permission.Permission;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Danh sách tool mà Gemini được phép gọi. Mỗi tool gắn:
 * <ul>
 *   <li>{@code allowCustomer} – khách hàng (Role.CUSTOMER / chưa đăng nhập phía client) có được dùng không</li>
 *   <li>{@code requiredPermissions} – nhân viên phải có ít nhất 1 permission trong tập này
 *       (rỗng = mọi nhân viên đã login đều dùng được)</li>
 * </ul>
 * ToolExecutor luôn kiểm tra lại trước khi chạy SQL – không tin tưởng model.
 */
public enum AiTool {

    SEARCH_PRODUCTS(
            "search_products",
            "Tìm sản phẩm đang bán theo tên hoặc mã. Trả về tối đa 8 kết quả: mã, tên, giá bán, trạng thái tồn.",
            true,
            EnumSet.noneOf(AppPermission.class),
            """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Từ khóa tên hoặc mã sản phẩm (ví dụ: sữa, SP_0001, iPhone)"
                }
              },
              "required": ["keyword"]
            }
            """
    ),

    FIND_SIMILAR_PRODUCTS(
            "find_similar_products",
            "Tìm sản phẩm tương tự dựa trên mô tả từ ảnh người dùng gửi (hoặc mô tả chữ). "
                    + "Dùng khi khách gửi ảnh và muốn biết cửa hàng có bán gì giống vậy. "
                    + "Thứ tự: tìm theo keywords → nếu không có thì gợi ý sản phẩm cùng danh mục (category_hint). "
                    + "Trả về danh sách SP + marker [[IMG:...]] nếu có ảnh.",
            true,
            EnumSet.noneOf(AppPermission.class),
            """
            {
              "type": "object",
              "properties": {
                "keywords": {
                  "type": "string",
                  "description": "Mô tả ngắn sản phẩm nhìn thấy trên ảnh: tên, thương hiệu, loại (ví dụ: 'sữa tươi TH True Milk hộp 1L', 'iPhone 15 xanh')"
                },
                "category_hint": {
                  "type": "string",
                  "description": "Gợi ý tên danh mục nếu nhận ra được (ví dụ: Sữa, Điện thoại, Đồ uống). Có thể để trống."
                }
              },
              "required": ["keywords"]
            }
            """
    ),

    IMPORT_EXCEL(
            "import_excel",
            "Đọc một hoặc nhiều file Excel (.xlsx) / Word bảng (.docx) người dùng đính kèm và import vào hệ thống "
                    + "nếu đúng cấu trúc cột. Hỗ trợ: danh mục, sản phẩm, nhân viên, khách hàng. "
                    + "Có thể truyền nhiều file_path (phân tách ; hoặc ,) hoặc mảng file_paths. "
                    + "entity_type: AUTO, CATEGORY, PRODUCT, EMPLOYEE, CUSTOMER. "
                    + "Chỉ nhân viên có quyền quản lý tương ứng.",
            false,
            EnumSet.of(AppPermission.CATEGORY_MANAGE, AppPermission.PRODUCT_MANAGE, AppPermission.USER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "file_path": {
                  "type": "string",
                  "description": "Một path hoặc nhiều path phân tách bởi ; hoặc , (vd uploads/ai_import/a.xlsx;uploads/ai_import/b.docx)"
                },
                "file_paths": {
                  "type": "array",
                  "items": { "type": "string" },
                  "description": "Danh sách đường dẫn file .xlsx/.docx"
                },
                "file_base64": {
                  "type": "string",
                  "description": "Nội dung Base64 nếu không có file_path (1 file)"
                },
                "file_name": {
                  "type": "string",
                  "description": "Tên file khi dùng file_base64"
                },
                "entity_type": {
                  "type": "string",
                  "description": "AUTO, CATEGORY, PRODUCT, EMPLOYEE hoặc CUSTOMER"
                }
              },
              "required": []
            }
            """
    ),

    GET_PRODUCT_DETAIL(
            "get_product_detail",
            "Lấy chi tiết 1 sản phẩm theo mã (productCode). Khách chỉ thấy giá bán, mô tả, ảnh, tình trạng còn hàng. "
                    + "Nhân viên có PRODUCT_VIEW/PRODUCT_MANAGE có thể thấy thêm tồn kho số lượng.",
            true,
            EnumSet.of(AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "product_code": {
                  "type": "string",
                  "description": "Mã sản phẩm (ví dụ SP_0001)"
                }
              },
              "required": ["product_code"]
            }
            """
    ),

    GET_STOCK_STATUS(
            "get_stock_status",
            "Kiểm tra tình trạng tồn kho của sản phẩm theo mã. Khách chỉ nhận mô tả (Còn hàng / Sắp hết / Hết hàng). "
                    + "Nhân viên có STOCK_VIEW hoặc PRODUCT_VIEW nhận thêm số lượng thực tế.",
            true,
            EnumSet.of(AppPermission.STOCK_VIEW, AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "product_code": {
                  "type": "string",
                  "description": "Mã sản phẩm"
                }
              },
              "required": ["product_code"]
            }
            """
    ),

    GET_EMPLOYEE_SALARY(
            "get_employee_salary",
            "Tra cứu lương nhân viên theo tên hoặc mã. CHỈ dành cho Admin (USER_MANAGE). "
                    + "Không bao giờ dùng cho khách hàng.",
            false,
            EnumSet.of(AppPermission.USER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Tên hoặc mã nhân viên cần tra lương"
                }
              },
              "required": ["keyword"]
            }
            """
    ),

    GET_REVENUE_SUMMARY(
            "get_revenue_summary",
            "Tóm tắt doanh thu trong khoảng ngày (yyyy-MM-dd). Chỉ role có REVENUE_REPORT_VIEW.",
            false,
            EnumSet.of(AppPermission.REVENUE_REPORT_VIEW),
            """
            {
              "type": "object",
              "properties": {
                "from_date": {
                  "type": "string",
                  "description": "Ngày bắt đầu yyyy-MM-dd"
                },
                "to_date": {
                  "type": "string",
                  "description": "Ngày kết thúc yyyy-MM-dd"
                }
              },
              "required": ["from_date", "to_date"]
            }
            """
    ),

    SEARCH_ORDERS(
            "search_orders",
            "Tìm đơn hàng online theo mã đơn (DH....), tên/SĐT/email khách. "
                    + "Trả về danh sách tối đa 8 đơn: mã, khách, tổng tiền, trạng thái đơn, thanh toán, ngày tạo. "
                    + "Chỉ nhân viên có ORDER_VIEW hoặc ORDER_MANAGE. Không dùng cho khách.",
            false,
            EnumSet.of(AppPermission.ORDER_VIEW, AppPermission.ORDER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Mã đơn (DH0001), tên khách, SĐT hoặc email"
                },
                "from_date": {
                  "type": "string",
                  "description": "Lọc từ ngày yyyy-MM-dd (tùy chọn)"
                },
                "to_date": {
                  "type": "string",
                  "description": "Lọc đến ngày yyyy-MM-dd (tùy chọn)"
                }
              },
              "required": ["keyword"]
            }
            """
    ),

    GET_ORDER_DETAIL(
            "get_order_detail",
            "Lấy chi tiết 1 đơn hàng online theo mã đơn (order_code, ví dụ DH0001): "
                    + "thông tin khách, địa chỉ giao, trạng thái, thanh toán, danh sách sản phẩm trong đơn. "
                    + "Chỉ ORDER_VIEW / ORDER_MANAGE.",
            false,
            EnumSet.of(AppPermission.ORDER_VIEW, AppPermission.ORDER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "order_code": {
                  "type": "string",
                  "description": "Mã đơn hàng, ví dụ DH0001 hoặc DH_0001"
                }
              },
              "required": ["order_code"]
            }
            """
    ),

    UPDATE_ORDER_STATUS(
            "update_order_status",
            "Xác nhận hoặc chuyển trạng thái 1 đơn hàng online theo mã đơn (order_code, ví dụ DH0001). "
                    + "action nhận 1 trong 4 giá trị: "
                    + "CONFIRM (NEW→CONFIRMED, tự động trừ kho theo FEFO — từ chối nếu không đủ hàng), "
                    + "SHIP (CONFIRMED→SHIPPING), "
                    + "COMPLETE (SHIPPING→COMPLETED, tự động lập hóa đơn tương ứng), "
                    + "CANCEL (chỉ hủy được khi đơn đang NEW hoặc CONFIRMED, tự động hoàn kho nếu đã trừ). "
                    + "CHỈ nhân viên có ORDER_MANAGE mới được gọi tool này — ORDER_VIEW (chỉ xem) KHÔNG đủ quyền. "
                    + "Không bao giờ dùng cho khách hàng. Khi thành công, hệ thống tự gửi thông báo tới "
                    + "mọi tài khoản có quyền quản lý đơn hàng đang đăng nhập.",
            false,
            EnumSet.of(AppPermission.ORDER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "order_code": {
                  "type": "string",
                  "description": "Mã đơn hàng cần cập nhật, ví dụ DH0001"
                },
                "action": {
                  "type": "string",
                  "description": "CONFIRM, SHIP, COMPLETE hoặc CANCEL"
                }
              },
              "required": ["order_code", "action"]
            }
            """
    ),

    SEARCH_INVOICES(
            "search_invoices",
            "Tìm hóa đơn bán tại quầy (POS) theo mã hóa đơn, tên khách. "
                    + "Trả về tối đa 8 hóa đơn: mã, khách, tổng tiền, PT thanh toán, trạng thái, ngày, NV lập. "
                    + "Nhân viên có INVOICE_CREATE hoặc REVENUE_REPORT_VIEW (hoặc Admin).",
            false,
            EnumSet.of(AppPermission.INVOICE_CREATE, AppPermission.REVENUE_REPORT_VIEW, AppPermission.INVOICE_CANCEL),
            """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Mã hóa đơn (ORD_0001), tên khách..."
                },
                "from_date": {
                  "type": "string",
                  "description": "Từ ngày yyyy-MM-dd (tùy chọn)"
                },
                "to_date": {
                  "type": "string",
                  "description": "Đến ngày yyyy-MM-dd (tùy chọn)"
                }
              },
              "required": ["keyword"]
            }
            """
    ),

    GET_INVOICE_DETAIL(
            "get_invoice_detail",
            "Chi tiết 1 hóa đơn POS theo mã (invoice_code): dòng hàng, VAT, tổng, PT thanh toán, trạng thái. "
                    + "Cần INVOICE_CREATE / INVOICE_CANCEL / REVENUE_REPORT_VIEW.",
            false,
            EnumSet.of(AppPermission.INVOICE_CREATE, AppPermission.REVENUE_REPORT_VIEW, AppPermission.INVOICE_CANCEL),
            """
            {
              "type": "object",
              "properties": {
                "invoice_code": {
                  "type": "string",
                  "description": "Mã hóa đơn, ví dụ ORD_0001"
                }
              },
              "required": ["invoice_code"]
            }
            """
    ),

    LIST_CATEGORIES(
            "list_categories",
            "Liệt kê danh mục sản phẩm hiện có (tên, trạng thái). Khách và nhân viên đều dùng được (chỉ xem). "
                    + "Dùng khi cần đoán danh mục từ ảnh hoặc trước khi tạo danh mục mới.",
            true,
            EnumSet.of(AppPermission.CATEGORY_MANAGE, AppPermission.PRODUCT_VIEW, AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "keyword": {
                  "type": "string",
                  "description": "Lọc theo tên danh mục (tùy chọn, để trống = lấy tất cả)"
                }
              },
              "required": []
            }
            """
    ),

    CREATE_CATEGORY(
            "create_category",
            "Tạo danh mục sản phẩm mới trong hệ thống. Chỉ nhân viên có CATEGORY_MANAGE (thường là Admin). "
                    + "Bắt buộc có category_name. Mặc định status=ACTIVE. "
                    + "Gọi tool này khi người dùng yêu cầu tạo/thêm danh mục (ví dụ: 'tạo danh mục chất tẩy rửa').",
            false,
            EnumSet.of(AppPermission.CATEGORY_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "category_name": {
                  "type": "string",
                  "description": "Tên danh mục cần tạo, ví dụ: Chất tẩy rửa"
                },
                "status": {
                  "type": "string",
                  "description": "ACTIVE hoặc DISABLED. Mặc định ACTIVE nếu bỏ trống"
                }
              },
              "required": ["category_name"]
            }
            """

    ),

    CREATE_PRODUCT(
            "create_product",
            "Tạo sản phẩm mới. Chỉ PRODUCT_MANAGE. Bắt buộc: product_name, category_name (tên danh mục đã có), "
                    + "import_price, sell_price. Tùy chọn: brand, unit, weight_volume, description, stock, min_stock, image_url. "
                    + "Gọi khi user yêu cầu thêm/tạo sản phẩm.",
            false,
            EnumSet.of(AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "product_name": { "type": "string", "description": "Tên sản phẩm" },
                "category_name": { "type": "string", "description": "Tên danh mục đã tồn tại (vd: Chất tẩy rửa)" },
                "import_price": { "type": "number", "description": "Giá nhập (VND)" },
                "sell_price": { "type": "number", "description": "Giá bán (VND)" },
                "brand": { "type": "string", "description": "Thương hiệu (tùy chọn)" },
                "unit": { "type": "string", "description": "Đơn vị: Hộp, Chai, Kg... (tùy chọn)" },
                "weight_volume": { "type": "string", "description": "Quy cách: 500ml, 1kg... (tùy chọn)" },
                "description": { "type": "string", "description": "Mô tả (tùy chọn)" },
                "stock": { "type": "integer", "description": "Tồn kho ban đầu, mặc định 0" },
                "min_stock": { "type": "integer", "description": "Ngưỡng cảnh báo, mặc định 5" },
                "image_url": { "type": "string", "description": "URL/đường dẫn ảnh (tùy chọn)" }
              },
              "required": ["product_name", "category_name", "import_price", "sell_price"]
            }
            """
    ),

    CREATE_EMPLOYEE(
            "create_employee",
            "Tạo tài khoản nhân viên mới (Users + Employees). Chỉ USER_MANAGE (Admin). "
                    + "Bắt buộc: full_name, email, role (SALES_STAFF|SALES_MANAGER|INVENTORY_MANAGER|ADMIN). "
                    + "Tùy chọn: phone, salary, hire_date (yyyy-MM-dd), gender (MALE|FEMALE|OTHER). "
                    + "Hệ thống tự sinh username + mật khẩu và gửi email nếu cấu hình SMTP.",
            false,
            EnumSet.of(AppPermission.USER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "full_name": { "type": "string", "description": "Họ tên nhân viên" },
                "email": { "type": "string", "description": "Email (duy nhất, dùng nhận mật khẩu)" },
                "role": { "type": "string", "description": "SALES_STAFF, SALES_MANAGER, INVENTORY_MANAGER hoặc ADMIN" },
                "phone": { "type": "string", "description": "Số điện thoại (tùy chọn)" },
                "salary": { "type": "number", "description": "Lương VND (tùy chọn)" },
                "hire_date": { "type": "string", "description": "Ngày vào làm yyyy-MM-dd (tùy chọn, mặc định hôm nay)" },
                "gender": { "type": "string", "description": "MALE, FEMALE hoặc OTHER (tùy chọn)" }
              },
              "required": ["full_name", "email", "role"]
            }
            """
    ),

    UPDATE_CATEGORY(
            "update_category",
            "Sửa danh mục: đổi tên và/hoặc trạng thái (ACTIVE|DISABLED). Cần category_name hiện tại hoặc category_id. CATEGORY_MANAGE.",
            false,
            EnumSet.of(AppPermission.CATEGORY_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "category_name": { "type": "string", "description": "Tên danh mục hiện tại cần sửa" },
                "category_id": { "type": "integer", "description": "ID danh mục (nếu biết)" },
                "new_name": { "type": "string", "description": "Tên mới (tùy chọn)" },
                "status": { "type": "string", "description": "ACTIVE hoặc DISABLED (tùy chọn)" }
              },
              "required": []
            }
            """
    ),

    DELETE_CATEGORY(
            "delete_category",
            "Xóa cứng danh mục CHỈ khi chưa có sản phẩm nào. Nếu còn SP thì dùng update_category status=DISABLED. CATEGORY_MANAGE.",
            false,
            EnumSet.of(AppPermission.CATEGORY_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "category_name": { "type": "string", "description": "Tên danh mục cần xóa" },
                "category_id": { "type": "integer", "description": "ID danh mục (nếu biết)" }
              },
              "required": []
            }
            """
    ),

    UPDATE_PRODUCT(
            "update_product",
            "Sửa sản phẩm theo product_code (vd SP_0001): tên, giá, tồn, mô tả, brand, unit, status... PRODUCT_MANAGE.",
            false,
            EnumSet.of(AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "product_code": { "type": "string", "description": "Mã SP, ví dụ SP_0001" },
                "product_name": { "type": "string" },
                "category_name": { "type": "string", "description": "Đổi sang danh mục khác (tên)" },
                "import_price": { "type": "number" },
                "sell_price": { "type": "number" },
                "brand": { "type": "string" },
                "unit": { "type": "string" },
                "weight_volume": { "type": "string" },
                "description": { "type": "string" },
                "stock": { "type": "integer" },
                "min_stock": { "type": "integer" },
                "status": { "type": "string", "description": "ACTIVE hoặc DISABLED" },
                "image_url": { "type": "string" }
              },
              "required": ["product_code"]
            }
            """
    ),

    DELETE_PRODUCT(
            "delete_product",
            "Ngừng bán sản phẩm (đặt Status=DISABLED), không xóa cứng — đúng quy tắc UI quản lý SP. PRODUCT_MANAGE.",
            false,
            EnumSet.of(AppPermission.PRODUCT_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "product_code": { "type": "string", "description": "Mã SP cần ngừng bán" }
              },
              "required": ["product_code"]
            }
            """
    ),

    UPDATE_EMPLOYEE(
            "update_employee",
            "Sửa thông tin nhân viên theo email hoặc keyword (tên/mã). Có thể đổi full_name, phone, role, salary, status, gender. USER_MANAGE.",
            false,
            EnumSet.of(AppPermission.USER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "keyword": { "type": "string", "description": "Email, tên hoặc mã NV để tìm" },
                "full_name": { "type": "string" },
                "phone": { "type": "string" },
                "email": { "type": "string", "description": "Email mới (tùy chọn)" },
                "role": { "type": "string", "description": "SALES_STAFF, SALES_MANAGER, INVENTORY_MANAGER, ADMIN" },
                "salary": { "type": "number" },
                "status": { "type": "string", "description": "ACTIVE hoặc DISABLED" },
                "gender": { "type": "string", "description": "MALE, FEMALE, OTHER" }
              },
              "required": ["keyword"]
            }
            """
    ),

    LOCK_EMPLOYEE(
            "lock_employee",
            "Khóa hoặc mở khóa tài khoản nhân viên (IsLocked). USER_MANAGE. lock=true khóa, false mở.",
            false,
            EnumSet.of(AppPermission.USER_MANAGE),
            """
            {
              "type": "object",
              "properties": {
                "keyword": { "type": "string", "description": "Email/tên/mã NV" },
                "lock": { "type": "boolean", "description": "true = khóa, false = mở khóa" }
              },
              "required": ["keyword", "lock"]
            }
            """
    );


    private final String name;
    private final String description;
    private final boolean allowCustomer;
    private final Set<AppPermission> requiredPermissions;
    private final String parametersJson;

    AiTool(String name, String description, boolean allowCustomer,
           Set<AppPermission> requiredPermissions, String parametersJson) {
        this.name = name;
        this.description = description;
        this.allowCustomer = allowCustomer;
        this.requiredPermissions = Collections.unmodifiableSet(requiredPermissions);
        this.parametersJson = parametersJson;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isAllowCustomer() {
        return allowCustomer;
    }

    public Set<AppPermission> getRequiredPermissions() {
        return requiredPermissions;
    }

    public String getParametersJson() {
        return parametersJson;
    }

    public static AiTool fromName(String name) {
        if (name == null) return null;
        for (AiTool t : values()) {
            if (t.name.equalsIgnoreCase(name)) return t;
        }
        return null;
    }

    /**
     * Kiểm tra caller hiện tại có được phép gọi tool này không.
     * @param isCustomer true nếu đang chat từ phía client (khách)
     * @param canCheck   callback PermissionManager.can(...)
     */
    public boolean isAllowedFor(boolean isCustomer, java.util.function.Predicate<Permission> canCheck) {
        if (isCustomer) {
            return allowCustomer;
        }
        // Nhân viên: nếu không yêu cầu permission cụ thể → cho phép
        if (requiredPermissions.isEmpty()) {
            return true;
        }
        // Cần ít nhất 1 permission trong tập
        for (AppPermission p : requiredPermissions) {
            if (canCheck.test(p)) return true;
        }
        return false;
    }
}