/* ============================================================
   X. DU LIEU MAU (INSERT) - PHIEN BAN DAY DU, KHOP SCHEMA MOI NHAT
   (co SupplierProducts, AuditLogs voi OldValue/NewValue)
   ============================================================ */
USE SIMS_DB;
GO

-- ---- 1. Roles ----
-- Da bo sung 'CUSTOMER' (truoc day bi thieu -> khien moi lan tu dang ky
-- qua RegisterFrame deu that bai vi Users.RoleID NOT NULL khong tim thay
-- RoleCode = 'CUSTOMER').
INSERT INTO Roles (RoleCode, RoleName, Description) VALUES
('ADMIN',             N'Quản trị viên',        N'Toàn quyền hệ thống'),
('SALES_MANAGER',     N'Quản lý bán hàng',     N'Giám sát hoạt động bán hàng'),
('INVENTORY_MANAGER', N'Quản lý kho',          N'Kiểm soát nhập - xuất - tồn kho'),
('SALES_STAFF',       N'Nhân viên bán hàng',   N'Trực tiếp giao dịch với khách'),
('CUSTOMER',          N'Khách hàng',           N'Tự đăng ký, xem sản phẩm và mua hàng ở phía client');
GO

-- ---- 2. Permissions ----
INSERT INTO Permissions (PermissionCode, Description) VALUES
('USER_MANAGE',         N'Quản lý người dùng (tạo/khóa/gán quyền)'),
('CATEGORY_MANAGE',     N'Quản lý danh mục sản phẩm'),
('PRODUCT_MANAGE',      N'Quản lý sản phẩm, giá bán, mức tồn tối thiểu'),
('SUPPLIER_MANAGE',     N'Quản lý nhà cung cấp'),
('SYSTEM_CONFIG',       N'Cấu hình hệ thống (VAT, chính sách...)'),
('STOCK_VIEW',          N'Xem trạng thái tồn kho'),
('PRODUCT_SEARCH',      N'Tìm kiếm sản phẩm'),
('INVOICE_CREATE',      N'Tạo hóa đơn bán hàng'),
('INVOICE_CANCEL',      N'Hủy hóa đơn'),
('RETURN_EXCHANGE',     N'Xử lý đổi/trả hàng'),
('RETURN_APPROVE',      N'Phê duyệt đổi/trả giá trị lớn'),
('EXCEPTION_REPORT_SEND',   N'Gửi báo cáo ngoại lệ'),
('EXCEPTION_REPORT_HANDLE', N'Xử lý báo cáo ngoại lệ'),
('STOCK_IMPORT',        N'Nhập hàng vào kho'),
('STOCK_RECONCILE',     N'Đối chiếu kho cuối ngày'),
('CUSTOMER_MANAGE',     N'Quản lý khách hàng'),
('AUDIT_VIEW',          N'Xem nhật ký hệ thống'),
('REPORT_INVENTORY',    N'Báo cáo tồn kho, biểu đồ xu hướng tồn'),
('REPORT_REVENUE',      N'Thống kê doanh thu, biểu đồ xu hướng bán'),
('REPORT_PROFIT',       N'Báo cáo lợi nhuận');
GO

-- ---- 3. RolePermissions ----
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'ADMIN'), PermissionID FROM Permissions;

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'SALES_STAFF'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('STOCK_VIEW','PRODUCT_SEARCH','INVOICE_CREATE','INVOICE_CANCEL',
                          'RETURN_EXCHANGE','EXCEPTION_REPORT_SEND','CUSTOMER_MANAGE');

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'INVENTORY_MANAGER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('STOCK_VIEW','STOCK_IMPORT','STOCK_RECONCILE','REPORT_INVENTORY',
                          'SUPPLIER_MANAGE','EXCEPTION_REPORT_HANDLE');

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'SALES_MANAGER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('REPORT_REVENUE','REPORT_PROFIT','EXCEPTION_REPORT_HANDLE',
                          'RETURN_APPROVE','AUDIT_VIEW');

-- Khach hang (tu dang ky o client): chi duoc tim/xem san pham
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('PRODUCT_SEARCH');
GO

-- ---- 4. Users ----
-- Da them cot AvatarUrl (anh dai dien, co the NULL neu chua upload).
INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, RoleID) VALUES
('admin',    '$2a$10$examplehash.admin.0000000000000000000000000000',    N'Nguyễn Văn Admin',  'admin@connectmart.vn',   '0900000001', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='ADMIN')),
('salesmgr', '$2a$10$examplehash.salesmgr.000000000000000000000000000', N'Trần Thị Bích',     'bich.sm@connectmart.vn', '0900000002', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_MANAGER')),
('invmgr',   '$2a$10$examplehash.invmgr.0000000000000000000000000000',  N'Lê Văn Kho',        'kho.im@connectmart.vn',  '0900000003', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='INVENTORY_MANAGER')),
('staff01',  '$2a$10$examplehash.staff01.000000000000000000000000000', N'Phạm Thị Ngân',     'ngan.staff@connectmart.vn', '0900000004', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
('staff02',  '$2a$10$examplehash.staff02.000000000000000000000000000', N'Hoàng Văn Sơn',     'son.staff@connectmart.vn', '0900000005', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
-- Tai khoan khach hang (Role = CUSTOMER) - can co truoc vi Customers gio ke thua Users
('lan.nguyen',  '$2a$10$examplehash.customer1.00000000000000000000000', N'Nguyễn Thị Lan',  'lan.nguyen@gmail.com', '0912345678', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('hung.tran',   '$2a$10$examplehash.customer2.00000000000000000000000', N'Trần Văn Hùng',   'hung.tran@gmail.com',  '0987654321', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('khach_le',    '$2a$10$examplehash.guest.000000000000000000000000000', N'Khách lẻ',        NULL,                    NULL,          NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER'));
GO

-- Tai khoan 'khach_le' chi la ho so dai dien cho khach vang lai (khong tu dang nhap
-- duoc vi mat khau la placeholder) - vo hieu hoa de tranh bi dung de dang nhap that.
UPDATE Users SET Status = 'DISABLED' WHERE Username = 'khach_le';
GO

-- ---- 5. Categories ----
INSERT INTO Categories (CategoryName) VALUES
(N'Trái cây'), (N'Rau củ'), (N'Đồ uống'), (N'Thực phẩm khô');
GO

-- ---- 6. Suppliers ----
INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES
(N'Công ty TNHH Nông sản Miền Tây', N'123 Nguyễn Trãi, Cần Thơ', '0710123456', 'contact@mientaynongsan.vn', N'Trái cây, rau củ'),
(N'Công ty CP Thực phẩm An Bình',   N'45 Lê Lợi, TP.HCM',        '0281234567', 'sales@anbinhfood.vn',       N'Đồ uống, thực phẩm khô'),
(N'Công ty TNHH Rau sạch Đà Lạt',   N'88 Trần Phú, Đà Lạt',      '0263123456', 'contact@dalatveggie.vn',    N'Rau củ');
GO

-- ---- 7. Products ----
-- Luu y: Ca phe bot de Stock = 0 luc dau, se duoc nhap kho ngay sau qua PurchaseReceiptDetails
-- (trigger trg_PurchaseReceiptDetails_Insert se tu cong kho + ghi InventoryTransactions, KHONG can UPDATE thu cong)
INSERT INTO Products (ProductName, CategoryID, ImportPrice, SellPrice, Stock, MinStock) VALUES
(N'Táo Envy',       (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      35000, 45000, 50, 10),
(N'Chuối già',       (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      15000, 20000, 80, 15),
(N'Cà chua',         (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        18000, 24000, 40, 10),
(N'Cà rốt',          (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        12000, 17000, 60, 10),
(N'Nước suối 500ml', (SELECT CategoryID FROM Categories WHERE CategoryName=N'Đồ uống'),        4000,  6000, 200, 30),
(N'Cà phê bột 500g', (SELECT CategoryID FROM Categories WHERE CategoryName=N'Thực phẩm khô'), 65000, 89000, 0,  5);
GO

-- ---- 8. SupplierProducts ----
-- Minh hoa 1 SP co the lay tu nhieu NCC voi gia khac nhau (vd: Ca rot lay duoc ca 2 noi)
INSERT INTO SupplierProducts (SupplierID, ProductID, SupplyPrice, IsPreferred) VALUES
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 35000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 15000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), 18500, 0),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'), 12500, 0),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 4000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 65000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), 17500, 1),   -- gia tot hon -> NCC uu tien
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'), 12000, 1);    -- gia tot hon -> NCC uu tien
GO

-- ---- 9. Customers ----
-- Customers gio ke thua Users (CustomerID = UserID), nen chi con insert
-- CustomerID (tro toi tai khoan da tao o buoc 4) + MemberPoint rieng cua
-- ho so khach hang. FullName/Phone/Email lay thang tu Users, khong luu trung.
INSERT INTO Customers (CustomerID, MemberPoint) VALUES
((SELECT UserID FROM Users WHERE Username = 'lan.nguyen'), 120),
((SELECT UserID FROM Users WHERE Username = 'hung.tran'),   35),
((SELECT UserID FROM Users WHERE Username = 'khach_le'),     0);   -- dai dien cho khach vang lai khong luu thong tin
GO

-- ---- 10. Shift ----
-- Giu Status = 'OPEN' de dung voi trigger R4 moi (chi huy khi ca dang mo + cung ngay)
INSERT INTO Shifts (UserID, StartTime, Status) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, -3, GETDATE()), 'OPEN');
GO

-- ---- 11. Invoice mau (co CustomerID, PaymentMethod, VATRate) ----
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-20260725-001',
 (SELECT TOP 1 ShiftID FROM Shifts ORDER BY ShiftID DESC),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID = c.CustomerID WHERE u.FullName=N'Nguyễn Thị Lan'),
 'CASH', 8, 0);
GO

-- Insert chi tiet hoa don -> trigger trg_InvoiceDetails_CheckStock se tu tru kho
-- + tu ghi InventoryTransactions (TransactionType='SALE')
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 3, 45000),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 2, 6000);
GO

-- Cap nhat SubTotal + TotalAmount (VATAmount la computed column tu SubTotal*VATRate/100)
UPDATE i
SET i.SubTotal    = t.Sum,
    i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t
  ON t.InvoiceID = i.InvoiceID;
GO

-- ---- 12. Phieu nhap kho mau ----
INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount) VALUES
('PN-20260720-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0);
GO

-- Insert chi tiet nhap -> trigger trg_PurchaseReceiptDetails_Insert se tu cong kho
-- + tu ghi InventoryTransactions (TransactionType='IMPORT'), KHONG can UPDATE Products thu cong nua
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice) VALUES
((SELECT TOP 1 ReceiptID FROM PurchaseReceipts ORDER BY ReceiptID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 30, 65000);
GO

UPDATE r
SET r.TotalAmount = t.Sum
FROM PurchaseReceipts r
JOIN (SELECT ReceiptID, SUM(Quantity * ImportPrice) AS Sum FROM PurchaseReceiptDetails GROUP BY ReceiptID) t
  ON t.ReceiptID = r.ReceiptID;
GO

-- ---- 13. Doi/tra hang mau (co Approval that su, dung thu tu PENDING -> APPROVED) ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 'RETURN', N'Khách phản ánh táo bị dập, xin trả lại 1 quả', 45000, 0, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff01'));
GO

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 1, 'IN', 45000);
GO

-- Duyet return (gia tri nho, khong bat buoc RETURN_APPROVE nhung van co the ghi nhan nguoi xu ly)
-- Trigger trg_ReturnExchange_ApprovedStock se tu cong kho + ghi InventoryTransactions (RETURN_IN)
-- + tu dieu chinh lai Invoices.SubTotal/TotalAmount cua hoa don goc
UPDATE ReturnExchanges
SET Status = 'APPROVED',
    ApprovedBy = (SELECT UserID FROM Users WHERE Username='salesmgr'),
    ApprovedAt = GETDATE()
WHERE ReturnID = (SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC);
GO

-- ---- 14. Doi/tra gia tri lon, can duyet ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 'EXCHANGE', N'Khách đổi nước suối 500ml lấy cà phê bột 500g do đặt nhầm', 89000, 1, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff01'));
GO

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 2, 'IN',  6000),
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'),  1, 'OUT', 89000);
GO
-- Gia tri lon (RequiresApproval=1) -> co tinh de PENDING, cho SALES_MANAGER duyet o buoc sau,
-- minh hoa constraint CK_Return_ApprovalRequired: khong the set APPROVED neu chua co ApprovedBy

-- ---- 15. Doi chieu kho cuoi ngay mau ----
-- Gia su kiem ke thuc te phat hien Ca rot thieu 2 don vi so voi he thong
INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy) VALUES
((SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName=N'Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName=N'Cà rốt') - 2,
 N'Kiểm kê cuối ca phát hiện thiếu, nghi do hao hụt khi bày quầy',
 (SELECT UserID FROM Users WHERE Username='invmgr'));
GO
-- Trigger trg_StockReconciliation_Adjust se tu cap nhat Products.Stock ve ActualStock
-- + ghi InventoryTransactions (TransactionType='RECONCILE_ADJUST')

-- ---- 16. Bao cao ngoai le mau ----
INSERT INTO ExceptionReports (CreatedBy, Content) VALUES
((SELECT UserID FROM Users WHERE Username='staff02'),
 N'Khách yêu cầu mua "Xoài cát Hòa Lộc" nhưng sản phẩm chưa có trong hệ thống.');
GO

-- ---- 17. AuditLogs mau (co OldValue/NewValue de truy vet day du) ----
INSERT INTO AuditLogs (UserID, Action, TableName, RecordID, OldValue, NewValue, Detail, IPAddress) VALUES
-- Dang nhap: khong co OldValue/NewValue vi khong sua doi du lieu
((SELECT UserID FROM Users WHERE Username='admin'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='admin'),
   NULL, NULL, N'Đăng nhập thành công', '192.168.1.10'),

((SELECT UserID FROM Users WHERE Username='staff01'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff01'),
   NULL, NULL, N'Đăng nhập thành công', '192.168.1.25'),

-- Duyet doi/tra: co snapshot truoc/sau trang thai
((SELECT UserID FROM Users WHERE Username='salesmgr'), 'RETURN_APPROVE', 'ReturnExchanges',
   (SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID ASC),
   N'{"Status":"PENDING","ApprovedBy":null}',
   N'{"Status":"APPROVED","ApprovedBy":"salesmgr"}',
   N'Duyệt đổi/trả giá trị nhỏ', '192.168.1.40'),

-- Vi du Admin sua gia ban san pham: minh hoa truy vet OldValue/NewValue cho PRODUCT_MANAGE
((SELECT UserID FROM Users WHERE Username='admin'), 'PRODUCT_PRICE_UPDATE', 'Products',
   (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'),
   N'{"SellPrice":24000}',
   N'{"SellPrice":25000}',
   N'Điều chỉnh giá bán theo giá nhập mới từ NCC Đà Lạt', '192.168.1.10'),

-- Vi du khoa tai khoan sau 5 lan dang nhap sai: minh hoa cho R5
((SELECT UserID FROM Users WHERE Username='admin'), 'USER_LOCK', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff02'),
   N'{"IsLocked":false,"FailedLoginCount":5}',
   N'{"IsLocked":true,"FailedLoginCount":5}',
   N'Tài khoản tự động khóa sau 5 lần đăng nhập sai liên tiếp', NULL);
GO

-- ---- 18. Cau hinh he thong mau ----
INSERT INTO StoreConfig (ConfigKey, ConfigValue) VALUES
('VAT_RATE', '8'),
('STORE_NAME', N'Connect Mart'),
('RETURN_POLICY_DAYS', '7'),
('DEFAULT_UNIT', N'cái');
GO

UPDATE Products
SET ImageUrl = 'uploads/products/tao-envy.jpg'
WHERE ProductName = N'Táo Envy';

UPDATE Products
SET ImageUrl = 'uploads/products/chuoi-gia.jpg'
WHERE ProductName = N'Chuối già';

UPDATE Products
SET ImageUrl = 'uploads/products/ca-chua.jpg'
WHERE ProductName = N'Cà chua';

UPDATE Products
SET ImageUrl = 'uploads/products/ca-rot.jpg'
WHERE ProductName = N'Cà rốt';

UPDATE Products
SET ImageUrl = 'uploads/products/nuoc-suoi.jpg'
WHERE ProductName = N'Nước suối 500ml';

UPDATE Products
SET ImageUrl = 'uploads/products/ca-phe-bot.jpg'
WHERE ProductName = N'Cà phê bột 500g';

-- ---- Quyen han moi ----
INSERT INTO Permissions (PermissionCode, Description) VALUES
('ORDER_VIEW',   N'Xem đơn hàng online từ khách'),
('ORDER_MANAGE', N'Xác nhận / hủy đơn hàng online từ khách');
GO

-- ADMIN da co san TAT CA quyen qua cau insert blanket trong Insert_SIMS.sql
-- (SELECT ... FROM Permissions) - chi can cap them cho ADMIN neu bang da
-- duoc seed truoc do (script nay co the chay sau khi da co du lieu):
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'ADMIN' AND p.PermissionCode IN ('ORDER_VIEW', 'ORDER_MANAGE')
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );

-- Nhan vien ban hang cung duoc xem + xac nhan don online
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_STAFF' AND p.PermissionCode IN ('ORDER_VIEW', 'ORDER_MANAGE')
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );
GO

-- ---- Quyen "Sao luu & Khoi phuc" - chi ADMIN ----
INSERT INTO Permissions (PermissionCode, Description) VALUES
('BACKUP_MANAGE', N'Xem trang Sao lưu & Khôi phục, tự sao lưu / khôi phục DB từ file backup');
GO
 
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'ADMIN' AND p.PermissionCode = 'BACKUP_MANAGE'
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );
GO