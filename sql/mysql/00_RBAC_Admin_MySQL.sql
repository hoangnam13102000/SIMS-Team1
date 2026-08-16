/* ============================================================
   SIMS - RBAC + Admin (toi thieu)
   Chay SAU schema, TRUOC sample data (hoac bo qua neu chay 03_SampleData)
   Username: admin / Password: 123456
   ============================================================ */
USE SIMS_DB;

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'ADMIN', 'Quản trị viên', 'Quản lý user, danh mục, sản phẩm, cấu hình hệ thống'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'ADMIN');

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'SALES_MANAGER', 'Quản lý bán hàng', 'Thống kê doanh thu, báo cáo ngoại lệ, duyệt đổi/trả'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'SALES_MANAGER');

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'INVENTORY_MANAGER', 'Quản lý kho', 'Nhập hàng/kho, đối chiếu kho, báo cáo tồn'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'INVENTORY_MANAGER');

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'SALES_STAFF', 'Nhân viên bán hàng', 'Tạo hóa đơn, tìm sản phẩm, hủy/đổi trả'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'SALES_STAFF');

INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'CUSTOMER', 'Khách hàng', 'Tự đăng ký, xem sản phẩm và mua hàng ở phía client'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'CUSTOMER');

INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, RoleID, IsLocked, FailedLoginCount, Status)
SELECT
    'admin',
    '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    'Quản trị viên hệ thống',
    'admin@sims.local',
    NULL,
    (SELECT RoleID FROM Roles WHERE RoleCode = 'ADMIN'),
    0, 0, 'ACTIVE'
FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM Users WHERE Username = 'admin');

UPDATE Users
SET PasswordHash = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    IsLocked = 0,
    FailedLoginCount = 0,
    Status = 'ACTIVE'
WHERE Username = 'admin';
