package com.service.ai;

import com.core.log.AppLogger;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.core.log.ErrorCode;
import com.dao.CategoryDAO;
import com.dao.EmployeeDAO;
import com.dao.InvoiceDAO;
import com.dao.OrderDAO;
import com.dao.ProductDAO;
import com.dao.RevenueReportDAO;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.model.Category;
import com.model.Employee;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.model.Order;
import com.model.OrderDetail;
import com.model.Product;
import com.model.Role;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;
import com.service.AuthService;
import com.utils.PaginationHelper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * Thực thi tool do Gemini yêu cầu.
 * Luôn kiểm tra quyền trước khi chạm DB. Không bao giờ tin model.
 */
public final class AiToolExecutor {

    private static final int MAX_PRODUCT_RESULTS = 8;
    private static final NumberFormat VND = NumberFormat.getInstance(new Locale("vi", "VN"));

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final RevenueReportDAO revenueReportDAO = new RevenueReportDAO();
    private final OrderDAO orderDAO = new OrderDAO();
    private final InvoiceDAO invoiceDAO = new InvoiceDAO();

    /**
     * @param toolName   tên tool Gemini trả về
     * @param argsJson   object args (chuỗi JSON)
     * @param isCustomer true nếu gọi từ phía client (khách)
     * @return chuỗi kết quả đưa lại cho model (tiếng Việt, dễ đọc)
     */
    public String execute(String toolName, String argsJson, boolean isCustomer) {
        AiTool tool = AiTool.fromName(toolName);
        if (tool == null) {
            return "Lỗi: tool không tồn tại: " + toolName;
        }

        if (!tool.isAllowedFor(isCustomer, p -> PermissionManager.getInstance().can(p))) {
            logDenied(toolName, isCustomer);
            return "KHÔNG ĐỦ THẨM QUYỀN: Bạn không được phép sử dụng chức năng \"" + tool.getName() + "\".";
        }

        try {
            JsonObject args = parseArgs(argsJson);
            return switch (tool) {
                case SEARCH_PRODUCTS -> searchProducts(args, isCustomer);
                case GET_PRODUCT_DETAIL -> getProductDetail(args, isCustomer);
                case GET_STOCK_STATUS -> getStockStatus(args, isCustomer);
                case GET_EMPLOYEE_SALARY -> getEmployeeSalary(args);
                case GET_REVENUE_SUMMARY -> getRevenueSummary(args);
                case SEARCH_ORDERS -> searchOrders(args);
                case GET_ORDER_DETAIL -> getOrderDetail(args);
                case SEARCH_INVOICES -> searchInvoices(args);
                case GET_INVOICE_DETAIL -> getInvoiceDetail(args);
                case LIST_CATEGORIES -> listCategories(args);
                case CREATE_CATEGORY -> createCategory(args);
                case CREATE_PRODUCT -> createProduct(args);
                case CREATE_EMPLOYEE -> createEmployee(args);
                case UPDATE_CATEGORY -> updateCategory(args);
                case DELETE_CATEGORY -> deleteCategory(args);
                case UPDATE_PRODUCT -> updateProduct(args);
                case DELETE_PRODUCT -> deleteProduct(args);
                case UPDATE_EMPLOYEE -> updateEmployee(args);
                case LOCK_EMPLOYEE -> lockEmployee(args);
            };
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL,
                    "AiToolExecutor.execute - tool=" + toolName, e);
            return "Lỗi khi thực thi tool: " + e.getMessage();
        }
    }

    private JsonObject parseArgs(String argsJson) {
        if (argsJson == null || argsJson.isBlank()) {
            return new JsonObject();
        }
        try {
            return JsonParser.parseString(argsJson).getAsJsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    // ==================== PRODUCTS ====================

    private String searchProducts(JsonObject args, boolean isCustomer) {
        String keyword = text(args, "keyword");
        if (keyword.isBlank()) {
            return "Vui lòng cung cấp từ khóa tìm kiếm.";
        }
        List<Product> list = productDAO.searchActive(keyword);
        if (list == null || list.isEmpty()) {
            return "Không tìm thấy sản phẩm nào khớp với \"" + keyword + "\".";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Tìm thấy ").append(Math.min(list.size(), MAX_PRODUCT_RESULTS))
                .append(" sản phẩm (tối đa ").append(MAX_PRODUCT_RESULTS).append("):\n");
        int i = 0;
        for (Product p : list) {
            if (i++ >= MAX_PRODUCT_RESULTS) break;
            sb.append("- ").append(p.getProductCode())
                    .append(" | ").append(p.getProductName())
                    .append(" | Giá: ").append(formatMoney(p.getSellPrice()));
            if (!isCustomer && canSeeNumericStock()) {
                sb.append(" | Tồn: ").append(p.getStock());
            } else {
                sb.append(" | ").append(stockLabel(p));
            }
            sb.append('\n');
            if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
                sb.append("[[IMG:").append(p.getImageUrl().trim()).append("]]\n");
            }
        }
        return sb.toString().trim();
    }

    private String getProductDetail(JsonObject args, boolean isCustomer) {
        String code = text(args, "product_code");
        if (code.isBlank()) return "Thiếu product_code.";
        Product p = productDAO.findActiveByCode(code);
        if (p == null) {
            return "Không tìm thấy sản phẩm mã \"" + code + "\" (hoặc sản phẩm không còn bán).";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Mã: ").append(p.getProductCode()).append('\n');
        sb.append("Tên: ").append(p.getProductName()).append('\n');
        if (p.getCategoryName() != null) sb.append("Danh mục: ").append(p.getCategoryName()).append('\n');
        if (p.getBrand() != null && !p.getBrand().isBlank()) sb.append("Thương hiệu: ").append(p.getBrand()).append('\n');
        if (p.getUnit() != null) sb.append("Đơn vị: ").append(p.getUnit()).append('\n');
        if (p.getWeightVolume() != null) sb.append("Quy cách: ").append(p.getWeightVolume()).append('\n');
        sb.append("Giá bán: ").append(formatMoney(p.getSellPrice())).append('\n');
        if (p.getDescription() != null && !p.getDescription().isBlank()) {
            sb.append("Mô tả: ").append(p.getDescription()).append('\n');
        }
        if (p.getImageUrl() != null && !p.getImageUrl().isBlank()) {
            sb.append("[[IMG:").append(p.getImageUrl().trim()).append("]]\n");
        }
        if (!isCustomer && PermissionManager.getInstance().can(AppPermission.PRODUCT_MANAGE)) {
            sb.append("Giá nhập: ").append(formatMoney(p.getImportPrice())).append('\n');
        }
        if (!isCustomer && canSeeNumericStock()) {
            sb.append("Tồn kho: ").append(p.getStock())
                    .append(" (min: ").append(p.getMinStock()).append(")\n");
        } else {
            sb.append("Tình trạng: ").append(stockLabel(p)).append('\n');
        }
        return sb.toString().trim();
    }

    private String getStockStatus(JsonObject args, boolean isCustomer) {
        String code = text(args, "product_code");
        if (code.isBlank()) return "Thiếu product_code.";
        Product p = productDAO.findActiveByCode(code);
        if (p == null) {
            return "Không tìm thấy sản phẩm mã \"" + code + "\".";
        }
        if (!isCustomer && canSeeNumericStock()) {
            return p.getProductCode() + " – " + p.getProductName()
                    + ": tồn " + p.getStock() + " (ngưỡng cảnh báo " + p.getMinStock() + "). "
                    + stockLabel(p);
        }
        return p.getProductCode() + " – " + p.getProductName() + ": " + stockLabel(p);
    }

    // ==================== EMPLOYEE / REVENUE ====================

    private String getEmployeeSalary(JsonObject args) {
        String keyword = text(args, "keyword");
        if (keyword.isBlank()) return "Vui lòng cung cấp tên hoặc mã nhân viên.";

        PaginationHelper.PaginationResult<Employee> page =
                employeeDAO.filterByRole(keyword, null, 1, 10);
        List<Employee> list = page != null ? page.getData() : null;
        if (list == null || list.isEmpty()) {
            return "Không tìm thấy nhân viên khớp \"" + keyword + "\".";
        }
        StringBuilder sb = new StringBuilder("Kết quả tra cứu lương:\n");
        for (Employee e : list) {
            if (e.getRole() == Role.CUSTOMER) continue;
            sb.append("- ").append(e.getFullName());
            if (e.getEmployeeId() != null) sb.append(" (").append(e.getEmployeeId()).append(")");
            sb.append(" | Role: ").append(e.getRole());
            sb.append(" | Lương: ").append(formatMoney(e.getSalary()));
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private String getRevenueSummary(JsonObject args) {
        LocalDate from = parseDate(text(args, "from_date"));
        LocalDate to = parseDate(text(args, "to_date"));
        if (from == null || to == null) {
            return "Ngày không hợp lệ. Dùng định dạng yyyy-MM-dd.";
        }
        if (to.isBefore(from)) {
            return "to_date phải >= from_date.";
        }
        try {
            RevenueReportDAO.Summary summary = revenueReportDAO.getSummary(from, to);
            if (summary == null) {
                return "Không có dữ liệu doanh thu trong khoảng " + from + " → " + to + ".";
            }
            return "Doanh thu từ " + from + " đến " + to + ":\n"
                    + "- Tổng doanh thu: " + formatMoney(summary.totalRevenue) + "\n"
                    + "- Số hóa đơn: " + summary.invoiceCount + "\n"
                    + "- Số mặt hàng bán: " + summary.itemsSold + "\n"
                    + "- Giá trị TB/đơn: " + formatMoney(summary.avgOrderValue());
        } catch (Exception e) {
            AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL,
                    "AiToolExecutor.getRevenueSummary", e);
            return "Không lấy được báo cáo doanh thu: " + e.getMessage()
                    + ". Vui lòng xem trang Báo cáo doanh thu trong hệ thống.";
        }
    }

    // ==================== ORDERS (online) ====================

    private String searchOrders(JsonObject args) {
        String keyword = text(args, "keyword");
        LocalDate from = parseDate(text(args, "from_date"));
        LocalDate to = parseDate(text(args, "to_date"));
        if (keyword.isBlank() && from == null && to == null) {
            return "Vui lòng cung cấp mã đơn / tên khách / SĐT hoặc khoảng ngày.";
        }
        PaginationHelper.PaginationResult<Order> page =
                orderDAO.getPagedFiltered(1, 8, keyword.isBlank() ? null : keyword, from, to);
        List<Order> list = page != null ? page.getData() : null;
        if (list == null || list.isEmpty()) {
            return "Không tìm thấy đơn hàng nào khớp.";
        }
        StringBuilder sb = new StringBuilder("Danh sách đơn hàng (" + list.size() + "):\n");
        for (Order o : list) {
            sb.append("- ").append(o.getOrderCode())
                    .append(" | KH: ").append(nullToDash(o.getCustomerName()))
                    .append(" | SĐT: ").append(nullToDash(o.getCustomerPhone()))
                    .append(" | Tổng: ").append(formatMoney(o.getTotalAmount()))
                    .append(" | Đơn: ").append(nullToDash(o.getOrderStatus()))
                    .append(" | TT: ").append(nullToDash(o.getPaymentStatus()))
                    .append(" | ").append(o.getCreatedAt() != null ? o.getCreatedAt().toLocalDate() : "—")
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String getOrderDetail(JsonObject args) {
        String code = text(args, "order_code");
        if (code.isBlank()) return "Thiếu order_code.";

        PaginationHelper.PaginationResult<Order> page =
                orderDAO.getPagedFiltered(1, 5, code, null, null);
        List<Order> list = page != null ? page.getData() : null;
        Order order = null;
        if (list != null) {
            for (Order o : list) {
                if (o.getOrderCode() != null
                        && o.getOrderCode().equalsIgnoreCase(code.trim())) {
                    order = o;
                    break;
                }
            }
            if (order == null && !list.isEmpty()) {
                order = list.get(0);
            }
        }
        if (order == null) {
            return "Không tìm thấy đơn hàng mã \"" + code + "\".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Mã đơn: ").append(order.getOrderCode()).append('\n');
        sb.append("Khách: ").append(nullToDash(order.getCustomerName())).append('\n');
        sb.append("SĐT: ").append(nullToDash(order.getCustomerPhone())).append('\n');
        sb.append("Email: ").append(nullToDash(order.getCustomerEmail())).append('\n');
        sb.append("Địa chỉ giao: ").append(nullToDash(order.getShippingAddress())).append('\n');
        sb.append("Ngày tạo: ").append(order.getCreatedAt() != null ? order.getCreatedAt() : "—").append('\n');
        sb.append("Trạng thái đơn: ").append(nullToDash(order.getOrderStatus())).append('\n');
        sb.append("Thanh toán: ").append(nullToDash(order.getPaymentMethod()))
                .append(" / ").append(nullToDash(order.getPaymentStatus())).append('\n');
        sb.append("Tổng tiền: ").append(formatMoney(order.getTotalAmount())).append('\n');
        if (order.getInvoiceId() != null) {
            sb.append("Hóa đơn liên kết (invoiceId): ").append(order.getInvoiceId()).append('\n');
        }

        List<OrderDetail> details = orderDAO.getDetailsByOrderId(order.getOrderId());
        if (details != null && !details.isEmpty()) {
            sb.append("Chi tiết hàng:\n");
            for (OrderDetail d : details) {
                sb.append("  • ").append(d.getProductName())
                        .append(" x").append(d.getQuantity())
                        .append(" @ ").append(formatMoney(d.getUnitPrice()))
                        .append(" = ").append(formatMoney(d.getLineTotal()))
                        .append('\n');
            }
        }
        return sb.toString().trim();
    }

    // ==================== INVOICES (POS) ====================

    private String searchInvoices(JsonObject args) {
        String keyword = text(args, "keyword");
        LocalDate from = parseDate(text(args, "from_date"));
        LocalDate to = parseDate(text(args, "to_date"));
        if (keyword.isBlank() && from == null && to == null) {
            return "Vui lòng cung cấp mã hóa đơn / tên khách hoặc khoảng ngày.";
        }
        PaginationHelper.PaginationResult<Invoice> page =
                invoiceDAO.getPagedFiltered(1, 8, keyword.isBlank() ? null : keyword, from, to);
        List<Invoice> list = page != null ? page.getData() : null;
        if (list == null || list.isEmpty()) {
            return "Không tìm thấy hóa đơn nào khớp.";
        }
        StringBuilder sb = new StringBuilder("Danh sách hóa đơn (" + list.size() + "):\n");
        for (Invoice inv : list) {
            sb.append("- ").append(inv.getInvoiceCode())
                    .append(" | KH: ").append(nullToDash(inv.getCustomerName()))
                    .append(" | Tổng: ").append(formatMoney(inv.getTotalAmount()))
                    .append(" | ").append(nullToDash(inv.getPaymentMethod()))
                    .append(" | ").append(nullToDash(inv.getStatus()))
                    .append(" | NV: ").append(nullToDash(inv.getCreatedByName()))
                    .append(" | ").append(inv.getCreatedAt() != null ? inv.getCreatedAt().toLocalDate() : "—")
                    .append('\n');
        }
        return sb.toString().trim();
    }

    private String getInvoiceDetail(JsonObject args) {
        String code = text(args, "invoice_code");
        if (code.isBlank()) return "Thiếu invoice_code.";

        PaginationHelper.PaginationResult<Invoice> page =
                invoiceDAO.getPagedFiltered(1, 5, code, null, null);
        List<Invoice> list = page != null ? page.getData() : null;
        Invoice inv = null;
        if (list != null) {
            for (Invoice i : list) {
                if (i.getInvoiceCode() != null
                        && i.getInvoiceCode().equalsIgnoreCase(code.trim())) {
                    inv = i;
                    break;
                }
            }
            if (inv == null && !list.isEmpty()) inv = list.get(0);
        }
        if (inv == null) {
            return "Không tìm thấy hóa đơn mã \"" + code + "\".";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Mã HĐ: ").append(inv.getInvoiceCode()).append('\n');
        sb.append("Khách: ").append(nullToDash(inv.getCustomerName())).append('\n');
        sb.append("Ngày: ").append(inv.getCreatedAt() != null ? inv.getCreatedAt() : "—").append('\n');
        sb.append("NV lập: ").append(nullToDash(inv.getCreatedByName())).append('\n');
        sb.append("SubTotal: ").append(formatMoney(inv.getSubTotal())).append('\n');
        sb.append("VAT: ").append(formatMoney(inv.getVatAmount()))
                .append(" (").append(inv.getVatRate() != null ? inv.getVatRate() + "%" : "—").append(")\n");
        sb.append("Tổng: ").append(formatMoney(inv.getTotalAmount())).append('\n');
        sb.append("Thanh toán: ").append(nullToDash(inv.getPaymentMethod())).append('\n');
        sb.append("Trạng thái: ").append(nullToDash(inv.getStatus())).append('\n');

        List<InvoiceDetail> details = invoiceDAO.getDetails(inv.getInvoiceId());
        if (details != null && !details.isEmpty()) {
            sb.append("Chi tiết hàng:\n");
            for (InvoiceDetail d : details) {
                String name = d.getProductName() != null ? d.getProductName() : ("SP#" + d.getProductId());
                sb.append("  • ").append(name)
                        .append(" x").append(d.getQuantity())
                        .append(" @ ").append(formatMoney(d.getUnitPrice()))
                        .append(" = ").append(formatMoney(d.getLineTotal()))
                        .append('\n');
            }
        }
        return sb.toString().trim();
    }


    // ==================== CATEGORIES ====================

    private String listCategories(JsonObject args) {
        String keyword = text(args, "keyword").toLowerCase();
        List<Category> list = categoryDAO.findAll();
        if (list == null || list.isEmpty()) {
            return "Chưa có danh mục nào trong hệ thống.";
        }
        StringBuilder sb = new StringBuilder("Danh sách danh mục:\n");
        int n = 0;
        for (Category c : list) {
            if (!keyword.isBlank() && (c.getCategoryName() == null
                    || !c.getCategoryName().toLowerCase().contains(keyword))) {
                continue;
            }
            n++;
            sb.append("- ID=").append(c.getCategoryId())
                    .append(" | ").append(c.getCategoryName())
                    .append(" | ").append(nullToDash(c.getStatus()))
                    .append(" | SP đang bán: ").append(c.getActiveProductCount())
                    .append('\n');
        }
        if (n == 0) {
            return "Không có danh mục khớp \"" + text(args, "keyword") + "\".";
        }
        return sb.toString().trim();
    }

    private String createCategory(JsonObject args) {
        String name = text(args, "category_name");
        if (name.isBlank()) {
            return "Thiếu category_name. Ví dụ: Chất tẩy rửa";
        }
        // Chuẩn hóa khoảng trắng
        name = name.trim().replaceAll("\\s+", " ");
        if (name.length() > 100) {
            return "Tên danh mục quá dài (tối đa 100 ký tự).";
        }

        // Trùng tên: nameExistsExcluding với excludeId = -1 (không loại trừ ai)
        if (categoryDAO.nameExistsExcluding(name, -1)) {
            return "Đã tồn tại danh mục tên \"" + name + "\". Không tạo trùng. "
                    + "Dùng list_categories để xem danh sách hiện có.";
        }

        String status = text(args, "status");
        if (status.isBlank()) status = "ACTIVE";
        status = status.trim().toUpperCase();
        if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
            return "status chỉ nhận ACTIVE hoặc DISABLED.";
        }

        Category c = new Category();
        c.setCategoryName(name);
        c.setStatus(status);

        boolean ok = categoryDAO.insertCategory(c);
        if (!ok) {
            return "Tạo danh mục thất bại (lỗi DB). Vui lòng thử lại hoặc tạo trên trang Quản lý danh mục.";
        }
        // Báo UI (CategoryPanel / BaseCrudPanel) reload qua AppEventBus
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CATEGORY));
        return "Đã tạo danh mục thành công.\n"
                + "- ID: " + c.getCategoryId() + "\n"
                + "- Tên: " + c.getCategoryName() + "\n"
                + "- Trạng thái: " + c.getStatus() + "\n"
                + "Bạn có thể xem lại trong trang Danh mục của hệ thống.";
    }

    // ==================== helpers ====================


    private String createProduct(JsonObject args) {
        String name = text(args, "product_name");
        String categoryName = text(args, "category_name");
        if (name.isBlank()) return "Thiếu product_name.";
        if (categoryName.isBlank()) return "Thiếu category_name. Dùng list_categories để xem danh mục.";

        name = name.trim().replaceAll("\\s+", " ");
        categoryName = categoryName.trim().replaceAll("\\s+", " ");

        Category cat = findCategoryByName(categoryName);
        if (cat == null) {
            return "Không tìm thấy danh mục \"" + categoryName + "\". "
                    + "Hãy tạo danh mục trước (create_category) hoặc chọn đúng tên từ list_categories.";
        }
        if (!cat.isActive()) {
            return "Danh mục \"" + categoryName + "\" đang DISABLED. Hãy kích hoạt trước khi thêm sản phẩm.";
        }

        BigDecimal importPrice = decimal(args, "import_price");
        BigDecimal sellPrice = decimal(args, "sell_price");
        if (importPrice == null || importPrice.compareTo(BigDecimal.ZERO) < 0) {
            return "import_price không hợp lệ (phải >= 0).";
        }
        if (sellPrice == null || sellPrice.compareTo(BigDecimal.ZERO) < 0) {
            return "sell_price không hợp lệ (phải >= 0).";
        }

        int stock = intOr(args, "stock", 0);
        int minStock = intOr(args, "min_stock", 5);
        if (stock < 0) return "stock không được âm.";
        if (minStock < 0) return "min_stock không được âm.";

        Product p = new Product();
        p.setProductName(name);
        p.setCategoryId(cat.getCategoryId());
        p.setBrand(blankToNull(text(args, "brand")));
        p.setUnit(blankToNull(text(args, "unit")));
        p.setWeightVolume(blankToNull(text(args, "weight_volume")));
        p.setDescription(blankToNull(text(args, "description")));
        p.setImportPrice(importPrice);
        p.setSellPrice(sellPrice);
        p.setImageUrl(blankToNull(text(args, "image_url")));
        p.setStock(stock);
        p.setMinStock(minStock);
        p.setStatus("ACTIVE");
        p.setAutoPrice(false); // khóa giá bán theo user nhập

        boolean ok = productDAO.insert(p);
        if (!ok) {
            return "Tạo sản phẩm thất bại (lỗi DB). Kiểm tra dữ liệu hoặc tạo trên trang Quản lý sản phẩm.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PRODUCT));
        return "Đã tạo sản phẩm thành công.\n"
                + "- Mã: " + p.getProductCode() + "\n"
                + "- Tên: " + p.getProductName() + "\n"
                + "- Danh mục: " + cat.getCategoryName() + "\n"
                + "- Giá nhập: " + formatMoney(p.getImportPrice()) + "\n"
                + "- Giá bán: " + formatMoney(p.getSellPrice()) + "\n"
                + "- Tồn: " + p.getStock();
    }

    private Category findCategoryByName(String name) {
        List<Category> all = categoryDAO.findAll();
        if (all == null) return null;
        for (Category c : all) {
            if (c.getCategoryName() != null
                    && c.getCategoryName().trim().equalsIgnoreCase(name.trim())) {
                return c;
            }
        }
        // partial match nếu đúng 1 kết quả
        List<Category> partial = new java.util.ArrayList<>();
        String lower = name.toLowerCase();
        for (Category c : all) {
            if (c.getCategoryName() != null
                    && c.getCategoryName().toLowerCase().contains(lower)) {
                partial.add(c);
            }
        }
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private String createEmployee(JsonObject args) {
        String fullName = text(args, "full_name");
        String email = text(args, "email");
        String roleStr = text(args, "role");
        if (fullName.isBlank()) return "Thiếu full_name.";
        if (email.isBlank()) return "Thiếu email.";
        if (roleStr.isBlank()) return "Thiếu role (SALES_STAFF, SALES_MANAGER, INVENTORY_MANAGER, ADMIN).";

        fullName = fullName.trim().replaceAll("\\s+", " ");
        email = email.trim();
        roleStr = roleStr.trim().toUpperCase().replace(' ', '_');

        Role role;
        try {
            role = Role.valueOf(roleStr);
        } catch (Exception e) {
            return "role không hợp lệ. Chỉ nhận: SALES_STAFF, SALES_MANAGER, INVENTORY_MANAGER, ADMIN.";
        }
        if (role == Role.CUSTOMER) {
            return "Không tạo nhân viên với role CUSTOMER. Dùng role nhân sự nội bộ.";
        }

        Employee emp = new Employee();
        emp.setFullName(fullName);
        emp.setEmail(email);
        emp.setPhone(blankToNull(text(args, "phone")));
        emp.setRole(role);

        BigDecimal salary = decimal(args, "salary");
        if (salary != null) {
            if (salary.compareTo(BigDecimal.ZERO) < 0) return "salary không được âm.";
            emp.setSalary(salary);
        }

        LocalDate hire = parseDate(text(args, "hire_date"));
        emp.setHireDate(hire != null ? hire : LocalDate.now());

        String genderStr = text(args, "gender");
        if (!genderStr.isBlank()) {
            try {
                emp.setGender(Employee.Gender.valueOf(genderStr.trim().toUpperCase()));
            } catch (Exception e) {
                return "gender chỉ nhận MALE, FEMALE hoặc OTHER.";
            }
        }

        EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(emp);
        if (!result.success) {
            return "Tạo nhân viên thất bại. Email có thể đã tồn tại hoặc lỗi DB. "
                    + "Kiểm tra email trùng / SMTP / trang Quản lý nhân viên.";
        }

        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.USER));

        StringBuilder sb = new StringBuilder();
        sb.append("Đã tạo nhân viên thành công.\n");
        sb.append("- Họ tên: ").append(emp.getFullName()).append('\n');
        sb.append("- Mã NV: ").append(nullToDash(emp.getEmployeeId())).append('\n');
        sb.append("- Username: ").append(nullToDash(emp.getUsername())).append('\n');
        sb.append("- Email: ").append(email).append('\n');
        sb.append("- Role: ").append(role).append('\n');
        if (emp.getSalary() != null) {
            sb.append("- Lương: ").append(formatMoney(emp.getSalary())).append('\n');
        }
        // Mật khẩu tạm: chỉ trả về cho Admin trong chat (đã có USER_MANAGE)
        if (result.rawPassword != null && !result.rawPassword.isBlank()) {
            sb.append("- Mật khẩu tạm: ").append(result.rawPassword).append('\n');
        }
        if (result.emailSent) {
            sb.append("- Đã gửi email thông tin đăng nhập.\n");
        } else {
            sb.append("- Chưa gửi được email");
            if (result.emailError != null) sb.append(" (").append(result.emailError).append(")");
            sb.append(". Hãy gửi mật khẩu tạm cho nhân viên thủ công.\n");
        }
        return sb.toString().trim();
    }


    private String updateCategory(JsonObject args) {
        Category c = resolveCategory(args);
        if (c == null) {
            return "Không tìm thấy danh mục. Cung cấp category_name hoặc category_id.";
        }
        String newName = text(args, "new_name");
        String status = text(args, "status");
        if (newName.isBlank() && status.isBlank()) {
            return "Cần ít nhất new_name hoặc status để sửa.";
        }
        if (!newName.isBlank()) {
            newName = newName.trim().replaceAll("\\s+", " ");
            if (categoryDAO.nameExistsExcluding(newName, c.getCategoryId())) {
                return "Tên \"" + newName + "\" đã được dùng bởi danh mục khác.";
            }
            c.setCategoryName(newName);
        }
        if (!status.isBlank()) {
            status = status.trim().toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("DISABLED")) {
                return "status chỉ nhận ACTIVE hoặc DISABLED.";
            }
            c.setStatus(status);
        }
        if (!categoryDAO.updateCategory(c)) {
            return "Cập nhật danh mục thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CATEGORY));
        return "Đã cập nhật danh mục ID=" + c.getCategoryId()
                + " | Tên: " + c.getCategoryName()
                + " | Status: " + c.getStatus();
    }

    private String deleteCategory(JsonObject args) {
        Category c = resolveCategory(args);
        if (c == null) {
            return "Không tìm thấy danh mục cần xóa.";
        }
        int count = categoryDAO.countProducts(c.getCategoryId());
        if (count > 0) {
            return "Không thể xóa cứng: danh mục \"" + c.getCategoryName()
                    + "\" còn " + count + " sản phẩm. "
                    + "Hãy dùng update_category với status=DISABLED để vô hiệu hóa.";
        }
        if (!categoryDAO.deleteCategory(c.getCategoryId())) {
            return "Xóa danh mục thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CATEGORY));
        return "Đã xóa danh mục \"" + c.getCategoryName() + "\" (ID=" + c.getCategoryId() + ").";
    }

    private Category resolveCategory(JsonObject args) {
        int id = intOr(args, "category_id", -1);
        if (id > 0) {
            Category c = categoryDAO.findById(id);
            if (c != null) return c;
        }
        String name = text(args, "category_name");
        if (!name.isBlank()) {
            return findCategoryByName(name);
        }
        return null;
    }

    private String updateProduct(JsonObject args) {
        String code = text(args, "product_code");
        if (code.isBlank()) return "Thiếu product_code.";
        Product p = productDAO.findActiveByCode(code);
        if (p == null) {
            // thử tìm trong paged không filter active — fallback search keyword
            var page = productDAO.getPagedFiltered(1, 5, code, null, null, null);
            if (page != null && page.getData() != null) {
                for (Product x : page.getData()) {
                    if (x.getProductCode() != null && x.getProductCode().equalsIgnoreCase(code.trim())) {
                        p = x;
                        break;
                    }
                }
            }
        }
        if (p == null) {
            return "Không tìm thấy sản phẩm mã \"" + code + "\".";
        }

        String pname = text(args, "product_name");
        if (!pname.isBlank()) p.setProductName(pname.trim());

        String catName = text(args, "category_name");
        if (!catName.isBlank()) {
            Category cat = findCategoryByName(catName);
            if (cat == null) return "Không tìm thấy danh mục \"" + catName + "\".";
            p.setCategoryId(cat.getCategoryId());
        }

        BigDecimal ip = decimal(args, "import_price");
        if (ip != null) {
            if (ip.compareTo(BigDecimal.ZERO) < 0) return "import_price không hợp lệ.";
            p.setImportPrice(ip);
        }
        BigDecimal sp = decimal(args, "sell_price");
        if (sp != null) {
            if (sp.compareTo(BigDecimal.ZERO) < 0) return "sell_price không hợp lệ.";
            p.setSellPrice(sp);
            p.setAutoPrice(false);
        }
        if (args.has("brand") && !args.get("brand").isJsonNull())
            p.setBrand(blankToNull(text(args, "brand")));
        if (args.has("unit") && !args.get("unit").isJsonNull())
            p.setUnit(blankToNull(text(args, "unit")));
        if (args.has("weight_volume") && !args.get("weight_volume").isJsonNull())
            p.setWeightVolume(blankToNull(text(args, "weight_volume")));
        if (args.has("description") && !args.get("description").isJsonNull())
            p.setDescription(blankToNull(text(args, "description")));
        if (args.has("image_url") && !args.get("image_url").isJsonNull())
            p.setImageUrl(blankToNull(text(args, "image_url")));
        if (args.has("stock") && !args.get("stock").isJsonNull()) {
            int s = intOr(args, "stock", p.getStock());
            if (s < 0) return "stock không được âm.";
            p.setStock(s);
        }
        if (args.has("min_stock") && !args.get("min_stock").isJsonNull()) {
            int m = intOr(args, "min_stock", p.getMinStock());
            if (m < 0) return "min_stock không được âm.";
            p.setMinStock(m);
        }
        String status = text(args, "status");
        if (!status.isBlank()) {
            status = status.toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("DISABLED"))
                return "status chỉ nhận ACTIVE hoặc DISABLED.";
            p.setStatus(status);
        }

        if (!productDAO.update(p)) {
            return "Cập nhật sản phẩm thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PRODUCT));
        return "Đã cập nhật sản phẩm " + p.getProductCode()
                + " | " + p.getProductName()
                + " | Giá bán: " + formatMoney(p.getSellPrice())
                + " | Tồn: " + p.getStock()
                + " | Status: " + p.getStatus();
    }

    private String deleteProduct(JsonObject args) {
        String code = text(args, "product_code");
        if (code.isBlank()) return "Thiếu product_code.";
        Product p = productDAO.findActiveByCode(code);
        if (p == null) {
            return "Không tìm thấy sản phẩm đang bán mã \"" + code + "\" (có thể đã ngừng bán).";
        }
        p.setStatus("DISABLED");
        if (!productDAO.update(p)) {
            return "Ngừng bán sản phẩm thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PRODUCT));
        return "Đã ngừng bán sản phẩm " + p.getProductCode() + " – " + p.getProductName()
                + " (Status=DISABLED). Có thể mở bán lại bằng update_product status=ACTIVE.";
    }

    private String updateEmployee(JsonObject args) {
        String keyword = text(args, "keyword");
        if (keyword.isBlank()) return "Thiếu keyword (email/tên/mã NV).";
        Employee emp = findOneEmployee(keyword);
        if (emp == null) {
            return "Không tìm thấy đúng 1 nhân viên khớp \"" + keyword + "\". Hãy ghi rõ email.";
        }

        String fullName = text(args, "full_name");
        if (!fullName.isBlank()) emp.setFullName(fullName.trim());
        String phone = text(args, "phone");
        if (args.has("phone")) emp.setPhone(blankToNull(phone));
        String email = text(args, "email");
        if (!email.isBlank()) {
            if (employeeDAO.emailExistsExcluding(email.trim(), emp.getUserId())) {
                return "Email \"" + email + "\" đã được dùng bởi tài khoản khác.";
            }
            emp.setEmail(email.trim());
        }
        String roleStr = text(args, "role");
        if (!roleStr.isBlank()) {
            try {
                Role r = Role.valueOf(roleStr.trim().toUpperCase().replace(' ', '_'));
                if (r == Role.CUSTOMER) return "Không gán role CUSTOMER cho nhân viên.";
                emp.setRole(r);
            } catch (Exception e) {
                return "role không hợp lệ.";
            }
        }
        BigDecimal salary = decimal(args, "salary");
        if (salary != null) {
            if (salary.compareTo(BigDecimal.ZERO) < 0) return "salary không hợp lệ.";
            emp.setSalary(salary);
        }
        String status = text(args, "status");
        if (!status.isBlank()) {
            status = status.toUpperCase();
            if (!status.equals("ACTIVE") && !status.equals("DISABLED"))
                return "status chỉ nhận ACTIVE hoặc DISABLED.";
            emp.setStatus(status);
        }
        String gender = text(args, "gender");
        if (!gender.isBlank()) {
            try {
                emp.setGender(Employee.Gender.valueOf(gender.toUpperCase()));
            } catch (Exception e) {
                return "gender không hợp lệ.";
            }
        }

        if (!employeeDAO.updateByAdmin(emp)) {
            return "Cập nhật nhân viên thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.USER));
        return "Đã cập nhật NV " + emp.getFullName()
                + " (" + nullToDash(emp.getEmployeeId()) + ")"
                + " | Role: " + emp.getRole()
                + " | Status: " + emp.getStatus()
                + (emp.getSalary() != null ? " | Lương: " + formatMoney(emp.getSalary()) : "");
    }

    private String lockEmployee(JsonObject args) {
        String keyword = text(args, "keyword");
        if (keyword.isBlank()) return "Thiếu keyword.";
        if (!args.has("lock") || args.get("lock").isJsonNull()) {
            return "Thiếu lock (true/false).";
        }
        boolean lock;
        try {
            lock = args.get("lock").getAsBoolean();
        } catch (Exception e) {
            return "lock phải là true hoặc false.";
        }
        Employee emp = findOneEmployee(keyword);
        if (emp == null) {
            return "Không tìm thấy đúng 1 nhân viên khớp \"" + keyword + "\".";
        }
        if (!employeeDAO.setLocked(emp.getUserId(), lock)) {
            return (lock ? "Khóa" : "Mở khóa") + " tài khoản thất bại.";
        }
        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.USER));
        return (lock ? "Đã KHÓA" : "Đã MỞ KHÓA") + " tài khoản: "
                + emp.getFullName() + " (" + nullToDash(emp.getUsername()) + ").";
    }

    private Employee findOneEmployee(String keyword) {
        var page = employeeDAO.filterByRole(keyword, null, 1, 10);
        List<Employee> list = page != null ? page.getData() : null;
        if (list == null || list.isEmpty()) return null;
        List<Employee> staff = new java.util.ArrayList<>();
        for (Employee e : list) {
            if (e.getRole() != Role.CUSTOMER) staff.add(e);
        }
        if (staff.size() == 1) return staff.get(0);
        // exact email match
        for (Employee e : staff) {
            if (e.getEmail() != null && e.getEmail().equalsIgnoreCase(keyword.trim())) return e;
        }
        for (Employee e : staff) {
            if (e.getEmployeeId() != null && e.getEmployeeId().equalsIgnoreCase(keyword.trim())) return e;
        }
        return staff.size() == 1 ? staff.get(0) : null;
    }

    private boolean canSeeNumericStock() {
        return PermissionManager.getInstance().can(AppPermission.STOCK_VIEW)
                || PermissionManager.getInstance().can(AppPermission.PRODUCT_VIEW)
                || PermissionManager.getInstance().can(AppPermission.PRODUCT_MANAGE)
                || AuthService.getInstance().isAdmin();
    }

    private static String stockLabel(Product p) {
        if (p.isOutOfStock()) return "Hết hàng";
        if (p.isLowStock()) return "Sắp hết hàng";
        return "Còn hàng";
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) return "—";
        return VND.format(amount.setScale(0, RoundingMode.HALF_UP)) + "₫";
    }

    private static String text(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return "";
        return o.get(key).getAsString().trim();
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }


    private static BigDecimal decimal(JsonObject o, String key) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return null;
        try {
            return o.get(key).getAsBigDecimal();
        } catch (Exception e) {
            try {
                return new BigDecimal(o.get(key).getAsString().trim().replace(",", ""));
            } catch (Exception e2) {
                return null;
            }
        }
    }

    private static int intOr(JsonObject o, String key, int def) {
        if (o == null || !o.has(key) || o.get(key).isJsonNull()) return def;
        try {
            return o.get(key).getAsInt();
        } catch (Exception e) {
            try {
                return Integer.parseInt(o.get(key).getAsString().trim());
            } catch (Exception e2) {
                return def;
            }
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String nullToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

    private void logDenied(String toolName, boolean isCustomer) {
        String who = isCustomer ? "CUSTOMER"
                : (AuthService.getInstance().getCurrentUser() != null
                ? AuthService.getInstance().getCurrentUser().getUsername()
                : "UNKNOWN");
        AppLogger.getInstance().error(ErrorCode.AI_CHAT_FAIL,
                "AiTool DENIED tool=" + toolName + " by=" + who, null);
    }
}
