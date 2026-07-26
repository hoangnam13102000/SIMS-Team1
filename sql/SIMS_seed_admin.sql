/* ============================================================
   SIMS - Script tao du lieu RBAC ban dau (Roles) + tai khoan Admin
   Chay SAU KHI da chay xong SIMS.sql (da co bang Roles, Users).
   ============================================================ */

USE SIMS_DB;
GO

/* ---- 1) Tao 4 vai tro theo dung RBAC trong de bai ---- */
IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'ADMIN')
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('ADMIN', N'Quản trị viên', N'Quản lý user, danh mục, sản phẩm, cấu hình hệ thống');
GO

IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'SALES_MANAGER')
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('SALES_MANAGER', N'Quản lý bán hàng', N'Thống kê doanh thu, báo cáo ngoại lệ, duyệt đổi/trả');
GO

IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'INVENTORY_MANAGER')
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('INVENTORY_MANAGER', N'Quản lý kho', N'Nhập hàng/kho, đối chiếu kho, báo cáo tồn');
GO

IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'SALES_STAFF')
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('SALES_STAFF', N'Nhân viên bán hàng', N'Tạo hóa đơn, tìm sản phẩm, hủy/đổi trả');
GO

/* ---- 2) Tao tai khoan Admin mac dinh ----
   Username: admin
   Password: 123456
   PasswordHash duoi day la BCrypt (cost 12, prefix $2a$) cua "123456",
   sinh boi thu vien Python bcrypt - tuong thich voi jBCrypt (org.mindrot)
   dang dung trong PasswordUtils.verify() cua ung dung.
   KHONG dung tren moi truong production - doi mat khau ngay sau lan
   dang nhap dau tien. */
IF NOT EXISTS (SELECT 1 FROM Users WHERE Username = 'admin')
BEGIN
    INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, RoleID, IsLocked, FailedLoginCount, Status)
    VALUES (
        'admin',
        '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
        N'Quản trị viên hệ thống',
        'admin@sims.local',
        NULL,
        (SELECT RoleID FROM Roles WHERE RoleCode = 'ADMIN'),
        0,
        0,
        'ACTIVE'
    );
END
GO

/* ---- 3) (Tuy chon) Vi du them 1 vai tro moi cho tung nhom quyen sau nay ----
   Khi ban gan quyen chi tiet (AppPermission) cho tung Role trong
   RolePermissions.java, co the them cac tai khoan mau khac o day, vd:

   INSERT INTO Users (Username, PasswordHash, FullName, Email, RoleID, Status)
   VALUES ('manager1', '<bcrypt-hash>', N'Nguyen Van A', 'manager1@sims.local',
           (SELECT RoleID FROM Roles WHERE RoleCode = 'SALES_MANAGER'), 'ACTIVE');
*/