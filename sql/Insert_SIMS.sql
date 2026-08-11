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
-- QUAN TRONG: Stock LUON de = 0 khi insert - day KHONG phai cot nhap tay,
-- ma la cot duoc trigger trg_PurchaseReceiptDetails_Insert tu dong cong don
-- theo InventoryBatch (xem sql/Trigger_SIMS.sql). Neu insert thang mot con
-- so vao Stock ma khong tao InventoryBatch tuong ung, POS se bi "tam thay":
-- PosCartService doc Products.Stock nen van cho bo vao gio hang binh thuong
-- (hien "con hang"), nhung trigger trg_InvoiceDetails_CheckStock khi lap hoa
-- don lai tinh ton that theo SUM(InventoryBatch.RemainingQty) = 0 nen chan
-- luon voi loi "San pham da het hang" -> thanh toan tao bug lan lon giua 2
-- nguon du lieu. Tat ca ton kho ban dau vi vay PHAI di qua muc 11 (Phieu
-- nhap kho) ngay ben duoi, giong cach "Ca phe bot" da tung lam dung truoc day.
INSERT INTO Products (ProductName, CategoryID, ImportPrice, SellPrice, Stock, MinStock) VALUES
(N'Táo Envy',       (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      35000, 45000, 0, 10),
(N'Chuối già',       (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      15000, 20000, 0, 15),
(N'Cà chua',         (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        18000, 24000, 0, 10),
(N'Cà rốt',          (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        12000, 17000, 0, 10),
(N'Nước suối 500ml', (SELECT CategoryID FROM Categories WHERE CategoryName=N'Đồ uống'),        4000,  6000, 0, 30),
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
INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 120
FROM Users WHERE Username = 'lan.nguyen'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 35
FROM Users WHERE Username = 'hung.tran'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 0   -- dai dien cho khach vang lai khong luu thong tin
FROM Users WHERE Username = 'khach_le';
GO

-- ---- 10. Shift ----
-- Giu Status = 'OPEN' de dung voi trigger R4 moi (chi huy khi ca dang mo + cung ngay)
INSERT INTO Shifts (UserID, StartTime, Status) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, -3, GETDATE()), 'OPEN');
GO

-- ---- 11. Phieu nhap kho mau (BAT BUOC chay TRUOC muc 12 - Hoa don mau) ----
-- Day la nguon DUY NHAT tao InventoryBatch (tung dong PurchaseReceiptDetails
-- sinh dung 1 lo qua trigger trg_PurchaseReceiptDetails_Insert), trigger se
-- tu cong Products.Stock tuong ung - KHONG duoc UPDATE Products.Stock thu
-- cong o bat ky dau khac. Nhap theo dung NCC uu tien (IsPreferred=1) da khai
-- bao o muc 8 cho nhat quan gia. HSD dat tuong doi theo GETDATE() de luon
-- "con han" bat ke script duoc chay vao ngay nao (R1/FEFO: b.ExpiryDate >=
-- CAST(GETDATE() AS DATE) moi duoc tinh vao ton kho ban duoc); hang kho
-- khong theo doi HSD (nuoc dong chai) de ManufactureDate/ExpiryDate = NULL.
INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount) VALUES
('PN-20260715-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
('PN-20260716-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
('PN-20260718-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0);
GO

-- Nong san Mien Tay: Tao Envy + Chuoi gia (trai cay, mau nhanh het han hon)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260715-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 50, 35000,
 N'LOT-TAO-001', DATEADD(DAY, -3, GETDATE()), DATEADD(DAY, 20, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260715-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 80, 15000,
 N'LOT-CHUOI-001', DATEADD(DAY, -1, GETDATE()), DATEADD(DAY, 7, GETDATE()));
GO

-- Rau sach Da Lat (NCC uu tien - gia 17500/12000 theo SupplierProducts): Ca chua + Ca rot
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260716-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), 40, 17500,
 N'LOT-CACHUA-001', DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, 10, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260716-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'), 60, 12000,
 N'LOT-CAROT-001', DATEADD(DAY, -2, GETDATE()), DATEADD(DAY, 25, GETDATE()));
GO

-- An Binh: Nuoc suoi (khong theo doi HSD) + Ca phe bot (kho, HSD dai)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260718-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 200, 4000,
 N'LOT-NUOC-001', NULL, NULL),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-20260718-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 30, 65000,
 N'LOT-CAPHE-001', DATEADD(DAY, -5, GETDATE()), DATEADD(DAY, 365, GETDATE()));
GO

UPDATE r
SET r.TotalAmount = t.Sum
FROM PurchaseReceipts r
JOIN (SELECT ReceiptID, SUM(Quantity * ImportPrice) AS Sum FROM PurchaseReceiptDetails GROUP BY ReceiptID) t
  ON t.ReceiptID = r.ReceiptID;
GO

-- ---- 12. Hoa don mau (co CustomerID, PaymentMethod, VATRate) ----
-- Chay SAU muc 11 nen luc nay Tao Envy/Nuoc suoi da co InventoryBatch that ->
-- trg_InvoiceDetails_CheckStock moi cho phep insert (truoc day 2 dong nay
-- nam TRUOC phan nhap kho nen luon bi trigger tu choi vi ton kho theo batch = 0).
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-20260725-001',
 (SELECT TOP 1 ShiftID FROM Shifts ORDER BY ShiftID DESC),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID = c.CustomerID WHERE u.FullName=N'Nguyễn Thị Lan'),
 'CASH', 8, 0);
GO

-- Insert chi tiet hoa don -> trigger trg_InvoiceDetails_CheckStock se tu tru kho
-- (theo FEFO, tru dan tren InventoryBatch) + tu ghi InventoryTransactions (TransactionType='SALE')
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
-- QUAN TRONG: ca 5 dong PHAI nam chung 1 cau INSERT (1 danh sach VALUES) -
-- truoc day dong POINT_RATE bi tach roi ra sau dau ';' (do 1 comment chen
-- giua danh sach VALUES), thanh 1 tuple "mo coi" khong thuoc cau lenh INSERT
-- nao ca -> loi cu phap T-SQL lam HONG CA BATCH nay, khien StoreConfig
-- khong duoc nap (hoac chi nap duoc mot phan tuy client), POS/thanh toan
-- doc nham VAT_RATE/POINT_RATE mac dinh hoac rong -> tinh sai VAT va diem
-- thanh vien luc lap hoa don.
-- So VND khach can chi de duoc cong 1 diem thanh vien (xem StoreConfigDAO.getPointRate()
-- va InvoiceDAO.createInvoice - tich diem tu dong khi lap hoa don co gan khach hang).
INSERT INTO StoreConfig (ConfigKey, ConfigValue) VALUES
('VAT_RATE', '0'),
('STORE_NAME', N'Connect Mart'),
('RETURN_POLICY_DAYS', '7'),
('DEFAULT_UNIT', N'cái'),
('POINT_RATE', '100000');
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

-- ---- Quyen "Doi/tra hang" (R4) ----
INSERT INTO Permissions (PermissionCode, Description)
SELECT 'RETURN_EXCHANGE_CREATE', N'Tạo yêu cầu đổi/trả hàng cho hóa đơn'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'RETURN_EXCHANGE_CREATE');

INSERT INTO Permissions (PermissionCode, Description)
SELECT 'RETURN_EXCHANGE_APPROVE', N'Duyệt / từ chối yêu cầu đổi/trả hàng giá trị lớn'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'RETURN_EXCHANGE_APPROVE');
GO

-- ---- Quyen "Bao cao ngoai le" (NVBH gui -> QL Ban hang xu ly) ----
INSERT INTO Permissions (PermissionCode, Description)
SELECT 'EXCEPTION_REPORT_CREATE', N'Gửi báo cáo ngoại lệ cho Quản lý bán hàng'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'EXCEPTION_REPORT_CREATE');

INSERT INTO Permissions (PermissionCode, Description)
SELECT 'EXCEPTION_REPORT_HANDLE', N'Xem và xử lý báo cáo ngoại lệ từ nhân viên bán hàng'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'EXCEPTION_REPORT_HANDLE');
GO

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'ADMIN' AND p.PermissionCode IN ('EXCEPTION_REPORT_CREATE', 'EXCEPTION_REPORT_HANDLE')
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );

-- Nhan vien ban hang: gui bao cao ngoai le
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_STAFF' AND p.PermissionCode = 'EXCEPTION_REPORT_CREATE'
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );

-- Quan ly ban hang: xem va xu ly bao cao ngoai le
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_MANAGER' AND p.PermissionCode = 'EXCEPTION_REPORT_HANDLE'
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );
GO

-- ---- Quyen "Cai dat he thong" (sua VAT_RATE va cac cau hinh StoreConfig khac) - chi ADMIN ----
INSERT INTO Permissions (PermissionCode, Description)
SELECT 'SETTINGS_MANAGE', N'Xem và sửa trang Cài đặt hệ thống (VAT, tên cửa hàng, chính sách đổi trả...)'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'SETTINGS_MANAGE');
GO

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'ADMIN' AND p.PermissionCode = 'SETTINGS_MANAGE'
  AND NOT EXISTS (
        SELECT 1 FROM RolePermissions rp
        WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
      );
GO


/* ============================================================
   Migration: Backfill Employees.EmployeeID cho cac tai khoan
   nhan vien da ton tai TRUOC KHI co thay doi nay trong UserDAO
   (register()/createByAdmin() truoc day KHONG insert vao bang
   Employees cho bat ky role nao, chi Customers cho Role.CUSTOMER).

   Chay 1 LAN sau khi da cap nhat code UserDAO.java. An toan de
   chay lai nhieu lan (idempotent) nho dieu kien NOT EXISTS.
   ============================================================ */

INSERT INTO Employees (UserID, EmployeeID)
SELECT u.UserID, 'EMP_' + RIGHT('0000' + CAST(u.UserID AS VARCHAR(10)), 4)
FROM Users u
JOIN Roles r ON u.RoleID = r.RoleID
WHERE r.RoleCode <> 'CUSTOMER'
  AND NOT EXISTS (
        SELECT 1 FROM Employees e WHERE e.UserID = u.UserID
      );
GO

-- Hash BCrypt cost 12 cho password: 123456
UPDATE Users 
SET PasswordHash = '$2a$12$rULa7sQqQB78UAMj4a.8IOPHPuspkHU2zffYsu75HhmFDVGPl3csS'
WHERE Username IN ('salesmgr', 'invmgr');

-- Cập nhật staff01 và staff02 với password 123456 (BCrypt cost 12)
UPDATE Users 
SET PasswordHash = '$2a$12$rULa7sQqQB78UAMj4a.8IOPHPuspkHU2zffYsu75HhmFDVGPl3csS'
WHERE Username IN ('staff01', 'staff02');


-- Xóa mã mẫu cũ (nếu chạy lại)
DELETE FROM Promotions WHERE Code IN (
    'SUMMER10', 'GIAM50K', 'FREESHIP', 'WELCOME15', 'FLASH20'
);
GO

INSERT INTO Promotions (
    Code, Name, DiscountType, DiscountValue,
    MaxDiscountAmount, MinOrderAmount,
    StartDate, EndDate, UsageLimit, UsedCount,
    IsActive, IsDeleted, CreatedBy, CreatedAt
) VALUES
-- Giảm 10% tối đa 30.000đ, đơn từ 100.000đ
(
    'SUMMER10',
    N'Khuyến mãi hè - Giảm 10%',
    'PERCENT',
    10,
    30000,
    100000,
    '2026-01-01',
    '2026-12-31',
    1000,
    0,
    1,
    0,
    1,
    GETDATE()
),
-- Giảm cố định 50.000đ, đơn từ 300.000đ
(
    'GIAM50K',
    N'Giảm ngay 50.000đ',
    'AMOUNT',
    50000,
    NULL,
    300000,
    '2026-01-01',
    '2026-12-31',
    500,
    0,
    1,
    0,
    1,
    GETDATE()
),
-- Giảm 15% cho khách mới, tối đa 40.000đ, đơn từ 150.000đ
(
    'WELCOME15',
    N'Chào thành viên mới - Giảm 15%',
    'PERCENT',
    15,
    40000,
    150000,
    '2026-01-01',
    '2026-12-31',
    NULL,          -- không giới hạn lượt
    0,
    1,
    0,
    1,
    GETDATE()
),
-- Flash sale giảm 20%, tối đa 100.000đ, đơn từ 200.000đ
(
    'FLASH20',
    N'Flash sale - Giảm 20%',
    'PERCENT',
    20,
    100000,
    200000,
    CAST(GETDATE() AS DATE),
    DATEADD(DAY, 30, CAST(GETDATE() AS DATE)),
    200,
    0,
    1,
    0,
    1,
    GETDATE()
),
-- Giảm 20.000đ, đơn từ 99.000đ (dùng test nhanh)
(
    'FREESHIP',
    N'Ưu đãi 20.000đ',
    'AMOUNT',
    20000,
    NULL,
    99000,
    '2026-01-01',
    '2026-12-31',
    9999,
    0,
    1,
    0,
    1,
    GETDATE()
);
GO

-- Kiểm tra
SELECT PromotionID, Code, Name, DiscountType, DiscountValue,
       MaxDiscountAmount, MinOrderAmount, StartDate, EndDate,
       UsageLimit, UsedCount, IsActive
FROM Promotions
WHERE IsDeleted = 0
ORDER BY PromotionID;
GO


/* ============================================================
   Tài khoản khách hàng mẫu
   Username : customer1
   Password : 123456
   PasswordHash: BCrypt cost 12 (tương thích PasswordUtils / jBCrypt)
   ============================================================ */

-- 1) Đảm bảo có role CUSTOMER
IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'CUSTOMER')
BEGIN
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('CUSTOMER', N'Khách hàng', N'Tự đăng ký, xem sản phẩm và mua hàng ở phía client');
END
GO

-- 2) Tạo user (bỏ qua nếu đã tồn tại)
IF NOT EXISTS (SELECT 1 FROM Users WHERE Username = 'customer1')
BEGIN
    INSERT INTO Users (
        Username, PasswordHash, FullName, Email, Phone,
        RoleID, IsLocked, FailedLoginCount, Status
    )
    VALUES (
        'customer1',
        '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',  -- = BCrypt("123456")
        N'Khách hàng Demo',
        'customer1@sims.local',
        '0901234567',
        (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER'),
        0,
        0,
        'ACTIVE'
    );
END
ELSE
BEGIN
    -- Nếu đã có user: reset mật khẩu + mở khóa
    UPDATE Users
    SET PasswordHash     = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
        IsLocked         = 0,
        FailedLoginCount = 0,
        Status           = 'ACTIVE',
        RoleID           = (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER')
    WHERE Username = 'customer1';
END
GO

-- 3) Tạo hồ sơ Customers (CustomerID = UserID)
IF NOT EXISTS (
    SELECT 1
    FROM Customers c
    JOIN Users u ON u.UserID = c.CustomerID
    WHERE u.Username = 'customer1'
)
BEGIN
    INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
    SELECT
        u.UserID,
        'CUS_' + RIGHT('0000' + CAST(u.UserID AS VARCHAR(10)), 4),
        0
    FROM Users u
    WHERE u.Username = 'customer1';
END
GO