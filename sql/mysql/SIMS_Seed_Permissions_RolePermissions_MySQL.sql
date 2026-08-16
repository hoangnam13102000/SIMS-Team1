/* ============================================================
   SIMS - Seed Permissions + RolePermissions (RBAC dong)
   Chay SAU 01_SIMS_Schema_MySQL.sql (va sau 00_RBAC_Admin_MySQL.sql neu co).
   Idempotent - chay lai nhieu lan khong tao du lieu trung.
   Luu y: RolePermissionDAO/RolePermissions (Java) cung TU DONG seed y het
   noi dung file nay ngay lan dau app doc quyen ma thay bang RolePermissions
   dang rong, nen script nay CHI can thiet neu ban muon seed thu cong truoc
   khi chay app, hoac muon xem truoc du lieu se duoc tao.
   ============================================================ */

USE SIMS_DB;

-- ===== 1) Permissions: dam bao du toan bo ma quyen (khop enum AppPermission) =====
INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
    ('DASHBOARD_VIEW',            'Xem trang tổng quan'),
    ('USER_MANAGE',               'Quản lý tài khoản & nhân viên'),
    ('CUSTOMER_MANAGE',           'Quản lý khách hàng'),
    ('CATEGORY_MANAGE',           'Quản lý danh mục'),
    ('PRODUCT_MANAGE',            'Quản lý sản phẩm'),
    ('PRODUCT_VIEW',              'Chỉ xem sản phẩm'),
    ('SUPPLIER_MANAGE',            'Quản lý nhà cung cấp'),
    ('STOCK_VIEW',                'Xem tồn kho'),
    ('STOCK_IMPORT',               'Nhập kho'),
    ('STOCK_RECONCILE',            'Đối chiếu kho cuối ngày'),
    ('INVOICE_CREATE',            'Tạo hoá đơn'),
    ('SHIFT_OPERATE',              'Vận hành ca bán hàng'),
    ('SHIFT_VIEW_ALL',             'Xem tất cả ca bán hàng'),
    ('INVOICE_CANCEL',             'Huỷ hoá đơn'),
    ('RETURN_EXCHANGE_CREATE',     'Tạo yêu cầu đổi/trả'),
    ('RETURN_EXCHANGE_APPROVE',    'Duyệt đổi/trả hàng'),
    ('ORDER_VIEW',                 'Xem đơn hàng online'),
    ('ORDER_MANAGE',               'Xử lý đơn hàng online'),
    ('STOCK_ALERT_REPORT',         'Báo cáo hàng sắp hết'),
    ('STOCK_ALERT_VIEW',           'Xử lý cảnh báo tồn'),
    ('BACKUP_MANAGE',              'Sao lưu & khôi phục'),
    ('AUDIT_LOG_VIEW',             'Nhật ký audit'),
    ('REVENUE_REPORT_VIEW',        'Báo cáo doanh thu'),
    ('EXCEPTION_REPORT_CREATE',    'Gửi báo cáo ngoại lệ'),
    ('EXCEPTION_REPORT_HANDLE',    'Xử lý báo cáo ngoại lệ'),
    ('PROFIT_REPORT_VIEW',         'Báo cáo lợi nhuận'),
    ('SETTINGS_MANAGE',            'Cài đặt hệ thống'),
    ('STOCK_DISPOSE',              'Tiêu huỷ hàng'),
    ('STOCK_DISPOSE_VIEW',         'Xem lịch sử tiêu huỷ'),
    ('SUPPLIER_RETURN_CREATE',     'Trả hàng nhà cung cấp'),
    ('SUPPLIER_RETURN_VIEW',       'Xem trả hàng NCC'),
    ('PROMOTION_MANAGE',           'Quản lý khuyến mãi'),
    ('RBAC_MANAGE',                'Phân quyền vai trò');

-- ===== 2) RolePermissions: chi seed neu bang dang RONG luc BAT DAU script =====
SET @rp_was_empty = (SELECT COUNT(*) = 0 FROM RolePermissions);

-- ADMIN: toan quyen
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID FROM Roles r, Permissions p
WHERE r.RoleCode = 'ADMIN'
  AND @rp_was_empty
ON DUPLICATE KEY UPDATE RoleID = VALUES(RoleID);

-- SALES_MANAGER
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID FROM Roles r, Permissions p
WHERE r.RoleCode = 'SALES_MANAGER'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW','PRODUCT_VIEW','REVENUE_REPORT_VIEW','PROFIT_REPORT_VIEW',
      'INVOICE_CREATE','INVOICE_CANCEL','ORDER_VIEW','ORDER_MANAGE',
      'RETURN_EXCHANGE_APPROVE','SHIFT_VIEW_ALL','EXCEPTION_REPORT_HANDLE',
      'STOCK_DISPOSE_VIEW','PROMOTION_MANAGE')
  AND @rp_was_empty
ON DUPLICATE KEY UPDATE RoleID = VALUES(RoleID);

-- INVENTORY_MANAGER
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID FROM Roles r, Permissions p
WHERE r.RoleCode = 'INVENTORY_MANAGER'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW','PRODUCT_VIEW','STOCK_VIEW','STOCK_IMPORT','STOCK_RECONCILE',
      'STOCK_DISPOSE','STOCK_DISPOSE_VIEW','STOCK_ALERT_VIEW',
      'SUPPLIER_RETURN_CREATE','SUPPLIER_RETURN_VIEW')
  AND @rp_was_empty
ON DUPLICATE KEY UPDATE RoleID = VALUES(RoleID);

-- SALES_STAFF
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID FROM Roles r, Permissions p
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode IN (
      'DASHBOARD_VIEW','CUSTOMER_MANAGE','PRODUCT_VIEW','INVOICE_CREATE',
      'SHIFT_OPERATE','INVOICE_CANCEL','RETURN_EXCHANGE_CREATE',
      'EXCEPTION_REPORT_CREATE','ORDER_VIEW','ORDER_MANAGE')
  AND @rp_was_empty
ON DUPLICATE KEY UPDATE RoleID = VALUES(RoleID);

-- CUSTOMER: khong co quyen nao trong khu vuc quan tri (co tinh de trong)
