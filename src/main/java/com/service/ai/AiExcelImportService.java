package com.service.ai;

import com.dao.CategoryDAO;
import com.dao.EmployeeDAO;
import com.dao.ProductDAO;
import com.dao.UserDAO;
import com.event.AppEventBus;
import com.event.DataChangedEvent;
import com.importer.SpreadsheetImportReader;
import com.model.Category;
import com.model.Employee;
import com.model.Product;
import com.model.Role;
import com.model.User;
import com.model.permission.AppPermission;
import com.permission.PermissionManager;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Đọc file Excel (.xlsx) và import vào DB theo cấu trúc cột nhận diện được.
 * Hỗ trợ: danh mục, sản phẩm, nhân viên, khách hàng.
 */
public final class AiExcelImportService {

    public enum EntityType { AUTO, CATEGORY, PRODUCT, EMPLOYEE, CUSTOMER }

    private final CategoryDAO categoryDAO = new CategoryDAO();
    private final ProductDAO productDAO = new ProductDAO();
    private final EmployeeDAO employeeDAO = new EmployeeDAO();
    private final UserDAO userDAO = new UserDAO();

    public String importFile(File file, EntityType preferred) {
        if (file == null || !file.isFile()) {
            return "Không tìm thấy file Excel.";
        }
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".docx")) {
            return "Chỉ hỗ trợ .xlsx (Excel) hoặc .docx (Word có bảng). File: " + file.getName();
        }

        List<String[]> rows;
        try {
            rows = SpreadsheetImportReader.read(file);
        } catch (Exception e) {
            return "Không đọc được file: " + e.getMessage();
        }
        if (rows == null || rows.size() < 2) {
            return "File không có dữ liệu (cần ít nhất 1 dòng tiêu đề + 1 dòng dữ liệu).";
        }

        String[] header = rows.get(0);
        Map<String, Integer> col = mapHeaders(header);
        EntityType type = preferred != null && preferred != EntityType.AUTO
                ? preferred
                : detectType(col);

        if (type == null || type == EntityType.AUTO) {
            return "Không nhận diện được cấu trúc file. "
                    + "Cần tiêu đề cột phù hợp, ví dụ:\n"
                    + "- Danh mục: Tên danh mục | Trạng thái\n"
                    + "- Sản phẩm: Tên sản phẩm | Danh mục | Giá nhập | Giá bán | Tồn kho\n"
                    + "- Nhân viên: Họ tên | Email | Vai trò | SĐT | Lương\n"
                    + "- Khách hàng: Họ tên | Email | SĐT | Username";
        }

        String permError = checkPermission(type);
        if (permError != null) return permError;

        return switch (type) {
            case CATEGORY -> importCategories(rows, col);
            case PRODUCT -> importProducts(rows, col);
            case EMPLOYEE -> importEmployees(rows, col);
            case CUSTOMER -> importCustomers(rows, col);
            default -> "Loại dữ liệu không hỗ trợ.";
        };
    }

    public String importBytes(byte[] bytes, String fileName, EntityType preferred) {
        if (bytes == null || bytes.length == 0) return "File trống.";
        String suffix = ".xlsx";
        if (fileName != null) {
            String n = fileName.toLowerCase(Locale.ROOT);
            if (n.endsWith(".docx")) suffix = ".docx";
            else if (n.endsWith(".xlsx")) suffix = ".xlsx";
        }
        try {
            File tmp = Files.createTempFile("ai_import_", suffix).toFile();
            Files.write(tmp.toPath(), bytes);
            try {
                return importFile(tmp, preferred);
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        } catch (Exception e) {
            return "Lỗi lưu file tạm: " + e.getMessage();
        }
    }

    /** Import nhiều file; gộp kết quả từng file. */
    public String importMany(List<File> files, EntityType preferred) {
        if (files == null || files.isEmpty()) return "Không có file để import.";
        StringBuilder all = new StringBuilder();
        int i = 0;
        for (File f : files) {
            i++;
            all.append("—— File ").append(i).append(": ").append(f.getName()).append(" ——\n");
            all.append(importFile(f, preferred)).append("\n");
        }
        return all.toString().trim();
    }

    // ------------------------------------------------------------------
    // Permission
    // ------------------------------------------------------------------

    private String checkPermission(EntityType type) {
        PermissionManager pm = PermissionManager.getInstance();
        return switch (type) {
            case CATEGORY -> pm.can(AppPermission.CATEGORY_MANAGE) ? null
                    : "KHÔNG ĐỦ THẨM QUYỀN: Cần CATEGORY_MANAGE để import danh mục.";
            case PRODUCT -> pm.can(AppPermission.PRODUCT_MANAGE) ? null
                    : "KHÔNG ĐỦ THẨM QUYỀN: Cần PRODUCT_MANAGE để import sản phẩm.";
            case EMPLOYEE -> pm.can(AppPermission.USER_MANAGE) ? null
                    : "KHÔNG ĐỦ THẨM QUYỀN: Cần USER_MANAGE để import nhân viên.";
            case CUSTOMER -> pm.can(AppPermission.USER_MANAGE) || pm.can(AppPermission.PRODUCT_VIEW) ? null
                    : "KHÔNG ĐỦ THẨM QUYỀN: Không được import khách hàng.";
            default -> "Loại import không hợp lệ.";
        };
    }

    // ------------------------------------------------------------------
    // Header mapping / detect
    // ------------------------------------------------------------------

    private static Map<String, Integer> mapHeaders(String[] header) {
        Map<String, Integer> map = new HashMap<>();
        if (header == null) return map;
        for (int i = 0; i < header.length; i++) {
            String key = normalize(header[i]);
            if (!key.isEmpty() && !map.containsKey(key)) {
                map.put(key, i);
            }
        }
        return map;
    }

    private static String normalize(String s) {
        if (s == null) return "";
        String t = s.trim().toLowerCase(Locale.ROOT);
        t = t.replace('\u00a0', ' ');
        // bỏ dấu tiếng Việt đơn giản
        t = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        t = t.replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
        return t;
    }

    private static Integer col(Map<String, Integer> map, String... aliases) {
        for (String a : aliases) {
            Integer idx = map.get(normalize(a));
            if (idx != null) return idx;
        }
        // partial contains
        for (String a : aliases) {
            String na = normalize(a);
            for (Map.Entry<String, Integer> e : map.entrySet()) {
                if (e.getKey().contains(na) || na.contains(e.getKey())) return e.getValue();
            }
        }
        return null;
    }

    private static String cell(String[] row, Integer idx) {
        if (idx == null || row == null || idx < 0 || idx >= row.length) return "";
        return row[idx] == null ? "" : row[idx].trim();
    }

    private EntityType detectType(Map<String, Integer> col) {
        boolean hasProductName = col(col, "ten san pham", "product name", "product_name", "tensanpham") != null;
        boolean hasCategory = col(col, "danh muc", "category", "category name", "ten danh muc") != null;
        boolean hasPrice = col(col, "gia ban", "sell price", "gia nhap", "import price") != null;
        boolean hasEmpName = col(col, "ho ten", "full name", "hoten", "ho va ten") != null;
        boolean hasEmail = col(col, "email") != null;
        boolean hasRole = col(col, "vai tro", "role", "chuc vu") != null;
        boolean hasCatOnly = col(col, "ten danh muc", "category name", "ten dm") != null
                && !hasProductName && !hasPrice;

        if (hasProductName && (hasCategory || hasPrice)) return EntityType.PRODUCT;
        if (hasCatOnly || (col(col, "ten danh muc") != null && !hasProductName && !hasEmpName))
            return EntityType.CATEGORY;
        if (hasEmpName && hasEmail && hasRole) return EntityType.EMPLOYEE;
        if (hasEmpName && hasEmail && !hasRole) return EntityType.CUSTOMER;
        if (hasCategory && !hasProductName) return EntityType.CATEGORY;
        return null;
    }

    // ------------------------------------------------------------------
    // Import implementations
    // ------------------------------------------------------------------

    private String importCategories(List<String[]> rows, Map<String, Integer> col) {
        Integer nameIdx = col(col, "ten danh muc", "category name", "ten dm", "danh muc", "name");
        Integer statusIdx = col(col, "trang thai", "status");
        if (nameIdx == null) {
            return "Cấu trúc danh mục không đúng. Cần cột: Tên danh mục (và tùy chọn Trạng thái).";
        }
        int ok = 0, fail = 0;
        StringBuilder errors = new StringBuilder();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String name = cell(row, nameIdx);
            if (name.isBlank()) continue;
            name = name.replaceAll("\\s+", " ").trim();
            if (categoryDAO.nameExistsExcluding(name, -1)) {
                fail++;
                appendErr(errors, i + 1, "Trùng tên \"" + name + "\"");
                continue;
            }
            String status = cell(row, statusIdx);
            if (status.isBlank()) status = "ACTIVE";
            status = status.toUpperCase(Locale.ROOT);
            if (!status.equals("ACTIVE") && !status.equals("DISABLED")) status = "ACTIVE";

            Category c = new Category();
            c.setCategoryName(name);
            c.setStatus(status);
            if (categoryDAO.insertCategory(c)) {
                ok++;
            } else {
                fail++;
                appendErr(errors, i + 1, "Lỗi DB khi tạo \"" + name + "\"");
            }
        }
        if (ok > 0) AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CATEGORY));
        return summary("danh mục", ok, fail, errors);
    }

    private String importProducts(List<String[]> rows, Map<String, Integer> col) {
        Integer nameIdx = col(col, "ten san pham", "product name", "tensanpham", "ten sp");
        Integer catIdx = col(col, "danh muc", "category", "category name", "ten danh muc");
        Integer importIdx = col(col, "gia nhap", "import price", "gianhap");
        Integer sellIdx = col(col, "gia ban", "sell price", "giaban");
        Integer stockIdx = col(col, "ton kho", "stock", "ton");
        Integer brandIdx = col(col, "thuong hieu", "brand");
        Integer unitIdx = col(col, "don vi", "unit");
        Integer descIdx = col(col, "mo ta", "description");
        Integer statusIdx = col(col, "trang thai", "status");

        if (nameIdx == null || catIdx == null || sellIdx == null) {
            return "Cấu trúc sản phẩm không đúng. Cần tối thiểu: Tên sản phẩm | Danh mục | Giá bán "
                    + "(khuyến nghị thêm Giá nhập, Tồn kho).";
        }

        int ok = 0, fail = 0;
        StringBuilder errors = new StringBuilder();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String name = cell(row, nameIdx);
            String catName = cell(row, catIdx);
            if (name.isBlank()) continue;
            name = name.replaceAll("\\s+", " ").trim();
            if (catName.isBlank()) {
                fail++;
                appendErr(errors, i + 1, "Thiếu danh mục cho \"" + name + "\"");
                continue;
            }
            Category cat = findCategory(catName);
            if (cat == null) {
                // tự tạo danh mục nếu có quyền
                if (PermissionManager.getInstance().can(AppPermission.CATEGORY_MANAGE)) {
                    Category nc = new Category();
                    nc.setCategoryName(catName.trim());
                    nc.setStatus("ACTIVE");
                    if (categoryDAO.insertCategory(nc)) {
                        cat = nc;
                        AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.CATEGORY));
                    }
                }
                if (cat == null) {
                    fail++;
                    appendErr(errors, i + 1, "Không có danh mục \"" + catName + "\"");
                    continue;
                }
            }

            BigDecimal sell = parseMoney(cell(row, sellIdx));
            BigDecimal imp = parseMoney(cell(row, importIdx));
            if (sell == null || sell.compareTo(BigDecimal.ZERO) < 0) {
                fail++;
                appendErr(errors, i + 1, "Giá bán không hợp lệ: \"" + name + "\"");
                continue;
            }
            if (imp == null) imp = BigDecimal.ZERO;

            int stock = parseInt(cell(row, stockIdx), 0);
            if (stock < 0) stock = 0;

            Product p = new Product();
            p.setProductName(name);
            p.setCategoryId(cat.getCategoryId());
            p.setImportPrice(imp);
            p.setSellPrice(sell);
            p.setStock(stock);
            p.setMinStock(5);
            p.setBrand(blankToNull(cell(row, brandIdx)));
            p.setUnit(blankToNull(cell(row, unitIdx)));
            p.setDescription(blankToNull(cell(row, descIdx)));
            String st = cell(row, statusIdx);
            p.setStatus(st.isBlank() ? "ACTIVE" : st.toUpperCase(Locale.ROOT));
            if (!"ACTIVE".equals(p.getStatus()) && !"DISABLED".equals(p.getStatus())) {
                p.setStatus("ACTIVE");
            }
            p.setAutoPrice(false);

            if (productDAO.insert(p)) {
                ok++;
            } else {
                fail++;
                appendErr(errors, i + 1, "Lỗi DB khi tạo SP \"" + name + "\"");
            }
        }
        if (ok > 0) AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.PRODUCT));
        return summary("sản phẩm", ok, fail, errors);
    }

    private String importEmployees(List<String[]> rows, Map<String, Integer> col) {
        Integer nameIdx = col(col, "ho ten", "full name", "ho va ten", "hoten", "ten");
        Integer emailIdx = col(col, "email");
        Integer roleIdx = col(col, "vai tro", "role", "chuc vu");
        Integer phoneIdx = col(col, "sdt", "so dien thoai", "phone", "dien thoai");
        Integer salaryIdx = col(col, "luong", "salary");

        if (nameIdx == null || emailIdx == null || roleIdx == null) {
            return "Cấu trúc nhân viên không đúng. Cần: Họ tên | Email | Vai trò "
                    + "(SALES_STAFF / SALES_MANAGER / INVENTORY_MANAGER / ADMIN).";
        }

        int ok = 0, fail = 0;
        StringBuilder errors = new StringBuilder();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String fullName = cell(row, nameIdx);
            String email = cell(row, emailIdx);
            String roleStr = cell(row, roleIdx);
            if (fullName.isBlank() && email.isBlank()) continue;
            if (fullName.isBlank() || email.isBlank() || roleStr.isBlank()) {
                fail++;
                appendErr(errors, i + 1, "Thiếu họ tên/email/vai trò");
                continue;
            }
            Role role = parseRole(roleStr);
            if (role == null || role == Role.CUSTOMER) {
                fail++;
                appendErr(errors, i + 1, "Vai trò không hợp lệ: " + roleStr);
                continue;
            }
            Employee emp = new Employee();
            emp.setFullName(fullName.replaceAll("\\s+", " ").trim());
            emp.setEmail(email.trim());
            emp.setPhone(blankToNull(cell(row, phoneIdx)));
            emp.setRole(role);
            emp.setHireDate(LocalDate.now());
            BigDecimal salary = parseMoney(cell(row, salaryIdx));
            if (salary != null) emp.setSalary(salary);

            EmployeeDAO.EmployeeCreationResult result = employeeDAO.createEmployee(emp);
            if (result.success) {
                ok++;
            } else {
                fail++;
                appendErr(errors, i + 1, "Không tạo được NV " + email + " (email trùng hoặc lỗi DB)");
            }
        }
        if (ok > 0) AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.USER));
        return summary("nhân viên", ok, fail, errors);
    }

    private String importCustomers(List<String[]> rows, Map<String, Integer> col) {
        Integer nameIdx = col(col, "ho ten", "full name", "ho va ten", "hoten", "ten");
        Integer emailIdx = col(col, "email");
        Integer phoneIdx = col(col, "sdt", "so dien thoai", "phone", "dien thoai");
        Integer userIdx = col(col, "username", "ten dang nhap", "dang nhap");

        if (nameIdx == null || emailIdx == null) {
            return "Cấu trúc khách hàng không đúng. Cần: Họ tên | Email (tùy chọn: SĐT, Username).";
        }

        int ok = 0, fail = 0;
        StringBuilder errors = new StringBuilder();
        for (int i = 1; i < rows.size(); i++) {
            String[] row = rows.get(i);
            String fullName = cell(row, nameIdx);
            String email = cell(row, emailIdx);
            if (fullName.isBlank() && email.isBlank()) continue;
            if (fullName.isBlank() || email.isBlank()) {
                fail++;
                appendErr(errors, i + 1, "Thiếu họ tên hoặc email");
                continue;
            }
            String username = cell(row, userIdx);
            if (username.isBlank()) {
                username = email.contains("@") ? email.substring(0, email.indexOf('@')) : email;
            }
            username = username.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
            String password = "Khach@" + System.currentTimeMillis() % 100000;

            User u = new User();
            u.setUsername(username);
            u.setFullName(fullName.replaceAll("\\s+", " ").trim());
            u.setEmail(email.trim());
            u.setPhone(cell(row, phoneIdx));
            u.setRole(Role.CUSTOMER);

            boolean created = userDAO.register(u, password);
            if (created) {
                ok++;
            } else {
                fail++;
                appendErr(errors, i + 1, "Không tạo KH " + email + " (trùng username/email?)");
            }
        }
        if (ok > 0) AppEventBus.getInstance().publish(new DataChangedEvent(DataChangedEvent.USER));
        return summary("khách hàng", ok, fail, errors)
                + (ok > 0 ? "\n(Mật khẩu tạm được hệ thống tự sinh khi đăng ký khách.)" : "");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Category findCategory(String name) {
        List<Category> all = categoryDAO.findAll();
        if (all == null) return null;
        for (Category c : all) {
            if (c.getCategoryName() != null
                    && c.getCategoryName().trim().equalsIgnoreCase(name.trim())) {
                return c;
            }
        }
        String lower = name.toLowerCase(Locale.ROOT);
        List<Category> partial = new ArrayList<>();
        for (Category c : all) {
            if (c.getCategoryName() != null
                    && c.getCategoryName().toLowerCase(Locale.ROOT).contains(lower)) {
                partial.add(c);
            }
        }
        return partial.size() == 1 ? partial.get(0) : null;
    }

    private static Role parseRole(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        // map tiếng Việt
        String n = normalize(s);
        if (n.contains("admin") || n.contains("quan tri")) return Role.ADMIN;
        if (n.contains("quan ly ban") || n.contains("sales manager")) return Role.SALES_MANAGER;
        if (n.contains("quan ly kho") || n.contains("inventory")) return Role.INVENTORY_MANAGER;
        if (n.contains("nhan vien") || n.contains("sales staff") || n.contains("ban hang"))
            return Role.SALES_STAFF;
        try {
            return Role.valueOf(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static BigDecimal parseMoney(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.replaceAll("[^0-9,.-]", "").replace(",", "");
        if (t.isBlank()) return null;
        try {
            return new BigDecimal(t);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseInt(String s, int def) {
        if (s == null || s.isBlank()) return def;
        try {
            return Integer.parseInt(s.replaceAll("[^0-9-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static void appendErr(StringBuilder sb, int row, String msg) {
        if (sb.length() > 800) return;
        sb.append("\n- Dòng ").append(row).append(": ").append(msg);
    }

    private static String summary(String label, int ok, int fail, StringBuilder errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("Import ").append(label).append(": thành công ").append(ok)
                .append(", lỗi ").append(fail).append(".");
        if (fail > 0 && errors.length() > 0) {
            sb.append("\nChi tiết lỗi:").append(errors);
        }
        if (ok == 0 && fail == 0) {
            sb.append(" Không có dòng dữ liệu hợp lệ.");
        }
        return sb.toString();
    }
}
