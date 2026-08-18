/* ============================================================
   SIMS - RBAC + Admin
   Chạy SAU 01_SIMS_Schema_MySQL.sql

   Chức năng:
   1. Seed Roles
   2. Seed Permissions
   3. Seed RolePermissions
   4. Tạo / reset tài khoản ADMIN

   Idempotent:
   - Có thể chạy lại nhiều lần.
   - Không tạo Role/Permission/RolePermission trùng.
   - Không tạo User admin trùng.
   - Khi chạy lại sẽ đảm bảo password/trạng thái admin đúng.

   ADMIN:
       Username: admin
       Password: 123456

   CUSTOMER:
       Không có quyền RBAC quản trị.
       Quyền của CUSTOMER được xử lý ở chức năng phía client/API.
   ============================================================ */

USE SIMS_DB;


/* ============================================================
   I. ROLES
   ============================================================ */

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT
    'ADMIN',
    'Quản trị viên',
    'Quản lý user, danh mục, sản phẩm, cấu hình hệ thống'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Roles
    WHERE RoleCode = 'ADMIN'
);


INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT
    'SALES_MANAGER',
    'Quản lý bán hàng',
    'Thống kê doanh thu, báo cáo ngoại lệ, duyệt đổi/trả'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Roles
    WHERE RoleCode = 'SALES_MANAGER'
);


INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT
    'INVENTORY_MANAGER',
    'Quản lý kho',
    'Nhập hàng/kho, đối chiếu kho, báo cáo tồn'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Roles
    WHERE RoleCode = 'INVENTORY_MANAGER'
);


INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT
    'SALES_STAFF',
    'Nhân viên bán hàng',
    'Tạo hóa đơn, tìm sản phẩm, hủy/đổi trả'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Roles
    WHERE RoleCode = 'SALES_STAFF'
);


INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT
    'CUSTOMER',
    'Khách hàng',
    'Tự đăng ký, xem sản phẩm và mua hàng ở phía client'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Roles
    WHERE RoleCode = 'CUSTOMER'
);


/* ============================================================
   II. PERMISSIONS
   ============================================================ */

INSERT IGNORE INTO Permissions
    (PermissionCode, Description)
VALUES
    ('DASHBOARD_VIEW',            'Xem trang tổng quan'),

    ('USER_MANAGE',               'Quản lý tài khoản & nhân viên'),

    ('CUSTOMER_MANAGE',           'Quản lý khách hàng'),

    ('CATEGORY_MANAGE',           'Quản lý danh mục'),

    ('PRODUCT_MANAGE',            'Quản lý sản phẩm'),

    ('PRODUCT_VIEW',              'Chỉ xem sản phẩm'),

    ('SUPPLIER_MANAGE',           'Quản lý nhà cung cấp'),

    ('STOCK_VIEW',                'Xem tồn kho'),

    ('STOCK_IMPORT',              'Nhập kho'),

    ('STOCK_RECONCILE',           'Đối chiếu kho cuối ngày'),

    ('INVOICE_CREATE',            'Tạo hoá đơn'),

    ('SHIFT_OPERATE',             'Vận hành ca bán hàng'),

    ('SHIFT_VIEW_ALL',            'Xem tất cả ca bán hàng'),

    ('INVOICE_CANCEL',             'Huỷ hoá đơn'),

    ('RETURN_EXCHANGE_CREATE',    'Tạo yêu cầu đổi/trả'),

    ('RETURN_EXCHANGE_APPROVE',   'Duyệt đổi/trả hàng'),

    ('ORDER_VIEW',                'Xem đơn hàng online'),

    ('ORDER_MANAGE',              'Xử lý đơn hàng online'),

    ('STOCK_ALERT_REPORT',        'Báo cáo hàng sắp hết'),

    ('STOCK_ALERT_VIEW',          'Xử lý cảnh báo tồn'),

    ('BACKUP_MANAGE',             'Sao lưu & khôi phục'),

    ('AUDIT_LOG_VIEW',            'Nhật ký audit'),

    ('REVENUE_REPORT_VIEW',       'Báo cáo doanh thu'),

    ('EXCEPTION_REPORT_CREATE',   'Gửi báo cáo ngoại lệ'),

    ('EXCEPTION_REPORT_HANDLE',   'Xử lý báo cáo ngoại lệ'),

    ('PROFIT_REPORT_VIEW',        'Báo cáo lợi nhuận'),

    ('SETTINGS_MANAGE',           'Cài đặt hệ thống'),

    ('STOCK_DISPOSE',             'Tiêu huỷ hàng'),

    ('STOCK_DISPOSE_VIEW',        'Xem lịch sử tiêu huỷ'),

    ('SUPPLIER_RETURN_CREATE',    'Trả hàng nhà cung cấp'),

    ('SUPPLIER_RETURN_VIEW',      'Xem trả hàng NCC'),

    ('PROMOTION_MANAGE',          'Quản lý khuyến mãi'),

    ('RBAC_MANAGE',               'Phân quyền vai trò');


/* ============================================================
   III. ROLE PERMISSIONS
   ============================================================ */

/*
   ADMIN
   -----
   Toàn quyền.
*/

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT
    r.RoleID,
    p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode = 'ADMIN';


/*
   SALES_MANAGER
   -------------
*/

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT
    r.RoleID,
    p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_MANAGER'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW',
      'PRODUCT_VIEW',
      'REVENUE_REPORT_VIEW',
      'PROFIT_REPORT_VIEW',
      'INVOICE_CREATE',
      'INVOICE_CANCEL',
      'ORDER_VIEW',
      'ORDER_MANAGE',
      'RETURN_EXCHANGE_APPROVE',
      'SHIFT_VIEW_ALL',
      'EXCEPTION_REPORT_HANDLE',
      'STOCK_DISPOSE_VIEW',
      'PROMOTION_MANAGE'
  );


/*
   INVENTORY_MANAGER
   -----------------
*/

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT
    r.RoleID,
    p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode = 'INVENTORY_MANAGER'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW',
      'PRODUCT_VIEW',
      'STOCK_VIEW',
      'STOCK_IMPORT',
      'STOCK_RECONCILE',
      'STOCK_DISPOSE',
      'STOCK_DISPOSE_VIEW',
      'STOCK_ALERT_VIEW',
      'SUPPLIER_RETURN_CREATE',
      'SUPPLIER_RETURN_VIEW'
  );


/*
   SALES_STAFF
   -----------
*/

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT
    r.RoleID,
    p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW',
      'CUSTOMER_MANAGE',
      'PRODUCT_VIEW',
      'INVOICE_CREATE',
      'SHIFT_OPERATE',
      'INVOICE_CANCEL',
      'RETURN_EXCHANGE_CREATE',
      'EXCEPTION_REPORT_CREATE',
      'ORDER_VIEW',
      'ORDER_MANAGE'
  );


/*
   CUSTOMER
   --------
   Không có quyền quản trị.

   Không insert RolePermissions cho CUSTOMER.
*/


/* ============================================================
   IV. ADMIN ACCOUNT
   ============================================================ */

INSERT INTO Users (
    Username,
    PasswordHash,
    FullName,
    Email,
    Phone,
    RoleID,
    IsLocked,
    FailedLoginCount,
    Status
)
SELECT
    'admin',
    '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    'Quản trị viên hệ thống',
    'admin@sims.local',
    NULL,
    (
        SELECT RoleID
        FROM Roles
        WHERE RoleCode = 'ADMIN'
        LIMIT 1
    ),
    0,
    0,
    'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1
    FROM Users
    WHERE Username = 'admin'
);


/* ============================================================
   V. ĐẢM BẢO ADMIN Ở TRẠNG THÁI HOẠT ĐỘNG
   ============================================================ */

UPDATE Users
SET
    PasswordHash = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    IsLocked = 0,
    FailedLoginCount = 0,
    Status = 'ACTIVE',
    RoleID = (
        SELECT RoleID
        FROM Roles
        WHERE RoleCode = 'ADMIN'
        LIMIT 1
    )
WHERE Username = 'admin';


/* ============================================================
   VI. KIỂM TRA RBAC
   ============================================================ */

SELECT
    r.RoleCode,
    r.RoleName,
    p.PermissionCode,
    p.Description
FROM Roles r
LEFT JOIN RolePermissions rp
    ON rp.RoleID = r.RoleID
LEFT JOIN Permissions p
    ON p.PermissionID = rp.PermissionID
ORDER BY
    r.RoleCode,
    p.PermissionCode;


/* ============================================================
   VII. KIỂM TRA ADMIN
   ============================================================ */

SELECT
    u.UserID,
    u.Username,
    u.FullName,
    u.Email,
    r.RoleCode,
    u.Status,
    u.IsLocked
FROM Users u
JOIN Roles r
    ON r.RoleID = u.RoleID
WHERE u.Username = 'admin';


USE SIMS_DB;

-- Them cac quyen bi thieu vao bang Permissions
INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('USER_VIEW', 'Chỉ xem tài khoản & nhân viên'),
('USER_EDIT', 'Chỉ sửa tài khoản & nhân viên'),
('CUSTOMER_VIEW', 'Chỉ xem khách hàng'),
('CUSTOMER_EDIT', 'Chỉ sửa khách hàng'),
('CATEGORY_VIEW', 'Chỉ xem danh mục'),
('CATEGORY_EDIT', 'Chỉ sửa danh mục'),
('PRODUCT_EDIT', 'Chỉ sửa sản phẩm'),
('SUPPLIER_VIEW', 'Chỉ xem nhà cung cấp'),
('SUPPLIER_EDIT', 'Chỉ sửa nhà cung cấp'),
('EXCEPTION_REPORT_VIEW', 'Chỉ xem báo cáo ngoại lệ'),
('STOCK_REPORT_VIEW', 'Báo cáo hàng tồn kho');

-- Cap STOCK_REPORT_VIEW cho INVENTORY_MANAGER
INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r, Permissions p
WHERE r.RoleCode = 'INVENTORY_MANAGER' AND p.PermissionCode = 'STOCK_REPORT_VIEW';



INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('SHIFT_MONITOR', 'Giám sát ca bán hàng');

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r, Permissions p
WHERE r.RoleCode IN ('ADMIN', 'SALES_MANAGER')
  AND p.PermissionCode = 'SHIFT_MONITOR';

INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('SHIFT_APPROVE', 'Duyệt đối soát ca bán hàng');

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode IN ('ADMIN', 'SALES_MANAGER')
  AND p.PermissionCode = 'SHIFT_APPROVE';