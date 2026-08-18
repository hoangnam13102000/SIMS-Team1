package com.model.permission;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.dao.RolePermissionDAO;
import com.permission.Permission;
import com.permission.PermissionSet;
import com.model.Role;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class RolePermissions {

    /** Ban do quyen MAC DINH (hardcode) - xem Javadoc class de biet vai tro cua bien nay. */
    private static final Map<Role, PermissionSet> DEFAULT_MAP = new EnumMap<>(Role.class);

    static {
        DEFAULT_MAP.put(Role.ADMIN, PermissionSet.of(AppPermission.values()));

        DEFAULT_MAP.put(Role.SALES_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.REVENUE_REPORT_VIEW,
                AppPermission.PROFIT_REPORT_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.INVOICE_CANCEL,
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE,
                AppPermission.RETURN_EXCHANGE_APPROVE,
                AppPermission.SHIFT_VIEW_ALL,
                AppPermission.SHIFT_MONITOR,
                AppPermission.EXCEPTION_REPORT_HANDLE,
                AppPermission.STOCK_DISPOSE_VIEW,
                AppPermission.PROMOTION_MANAGE
        ));

        DEFAULT_MAP.put(Role.INVENTORY_MANAGER, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.PRODUCT_VIEW,
                AppPermission.STOCK_VIEW,
                AppPermission.STOCK_IMPORT,
                AppPermission.STOCK_RECONCILE,
                AppPermission.STOCK_DISPOSE,
                AppPermission.STOCK_DISPOSE_VIEW,
                AppPermission.STOCK_ALERT_VIEW,
                AppPermission.SUPPLIER_RETURN_CREATE,
                AppPermission.SUPPLIER_RETURN_VIEW,
                AppPermission.STOCK_REPORT_VIEW
        ));

        DEFAULT_MAP.put(Role.SALES_STAFF, PermissionSet.of(
                AppPermission.DASHBOARD_VIEW,
                AppPermission.CUSTOMER_MANAGE,
                AppPermission.PRODUCT_VIEW,
                AppPermission.INVOICE_CREATE,
                AppPermission.SHIFT_OPERATE,
                AppPermission.INVOICE_CANCEL,
                AppPermission.RETURN_EXCHANGE_CREATE,
                AppPermission.EXCEPTION_REPORT_CREATE,
                AppPermission.ORDER_VIEW,
                AppPermission.ORDER_MANAGE
        ));

        DEFAULT_MAP.put(Role.CUSTOMER, PermissionSet.EMPTY);
    }

    /**
     * True neu da KIEM TRA seeding it nhat 1 lan trong tien trinh nay - chi
     * dung de tranh goi {@code isRolePermissionsEmpty()} (1 cau COUNT(*)) o
     * MOI lan dang nhap cho ton kem, KHONG lien quan gi toi cache quyen (xem
     * Javadoc class - quyen luon doc tuoi tu DB, khong cache).
     */
    private static volatile boolean seedChecked = false;

    private RolePermissions() {
    }

    /**
     * Quyen HIEU LUC hien tai cua 1 Role - LUON doc tuoi tu DB (tru Role.ADMIN
     * luon toan quyen), co fallback ve hardcode neu DB loi. Goi o MOI lan
     * dang nhap (xem AuthService.setCurrentUser) nen luon phan anh dung du
     * lieu moi nhat ma Admin da luu tren RolePermissionPanel, bat ke luu tu
     * may/tien trinh nao.
     */
    public static PermissionSet of(Role role) {
        if (role == Role.ADMIN) {
            // Xem Javadoc class: Admin LUON toan quyen, khong phu thuoc DB.
            return PermissionSet.of(AppPermission.values());
        }

        ensureSeeded();

        Set<AppPermission> fromDb = new RolePermissionDAO().getPermissionsByRole(role);
        if (fromDb == null) {
            // null = TRUY VAN THAT BAI (loi ket noi/CSDL) - khac voi tap
            // rong hop le (Admin da chu dong thu hoi het quyen cua Role do)
            // - fallback ve hardcode de app van dung duoc.
            return DEFAULT_MAP.getOrDefault(role, PermissionSet.EMPTY);
        }
        return PermissionSet.of(fromDb.toArray(new Permission[0]));
    }

    /**
     * Khong con tac dung thuc te (xem Javadoc class - {@link #of(Role)} luon
     * doc tuoi tu DB, khong con cache dai han de "lam moi" nua). Giu lai CHI
     * de tuong thich nguoc voi cac noi da goi {@code RolePermissions.reload()}
     * (vd RolePermissionPanel sau khi luu), tranh phai sua lai noi goi.
     */
    public static void reload() {
        // no-op: xem Javadoc class va Javadoc method nay.
    }

    /** Quyen MAC DINH (hardcode) cua 1 Role - dung cho nut "Khôi phục mặc định" tren RolePermissionPanel. */
    public static PermissionSet getDefault(Role role) {
        return DEFAULT_MAP.getOrDefault(role, PermissionSet.EMPTY);
    }

    /** Giong {@link #getDefault(Role)} nhung tra thang ve {@code Set<AppPermission>} - tien dung cho UI. */
    public static Set<AppPermission> getDefaultAppPermissions(Role role) {
        return toAppPermissionSet(getDefault(role));
    }

    /**
     * Lan dau tien (moi tien trinh) bang RolePermissions con rong (cai dat
     * moi / nang cap tu ban chua co RBAC dong) -> seed du lieu mac dinh tu
     * DEFAULT_MAP xuong DB, de tu day ve sau moi Role co du lieu THAT trong
     * DB va Admin co the chinh sua tren RolePermissionPanel. Chi kiem tra 1
     * LAN cho MOI tien trinh (bang {@link #seedChecked}) de tranh 1 cau
     * COUNT(*) thua o moi lan dang nhap - AN TOAN de goi lai nhieu lan vi
     * ban than seedDefaults() ben trong RolePermissionDAO cung tu kiem tra
     * rong truoc khi ghi.
     */
    private static void ensureSeeded() {
        if (seedChecked) {
            return;
        }
        synchronized (RolePermissions.class) {
            if (seedChecked) {
                return;
            }
            try {
                RolePermissionDAO dao = new RolePermissionDAO();
                if (dao.isRolePermissionsEmpty()) {
                    dao.seedDefaults(toAppPermissionMap(DEFAULT_MAP));
                }
            } catch (Exception ex) {
                AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "RolePermissions.ensureSeeded", ex);
                // Khong chan luong doc quyen - of(role) se tu fallback ve
                // DEFAULT_MAP neu doc DB tiep tuc that bai.
            } finally {
                seedChecked = true;
            }
        }
    }

    private static Map<Role, Set<AppPermission>> toAppPermissionMap(Map<Role, PermissionSet> source) {
        Map<Role, Set<AppPermission>> result = new HashMap<>();
        for (Map.Entry<Role, PermissionSet> entry : source.entrySet()) {
            result.put(entry.getKey(), toAppPermissionSet(entry.getValue()));
        }
        return result;
    }

    private static Set<AppPermission> toAppPermissionSet(PermissionSet set) {
        Set<AppPermission> result = EnumSet.noneOf(AppPermission.class);
        for (Permission permission : set.asSet()) {
            if (permission instanceof AppPermission) {
                result.add((AppPermission) permission);
            }
        }
        return result;
    }
}