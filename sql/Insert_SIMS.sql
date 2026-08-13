/* ============================================================
   DU LIEU MAU (INSERT) - PHIEN BAN DAY DU DE DEMO/THUYET TRINH
   Muc tieu: MOI BANG trong SIMS.sql deu co du lieu, va bieu do
   doanh thu (RevenueChartPanel/RevenueReportDAO.getDailyRevenue)
   co du 7 cot lien tuc = 7 ngay gan nhat tinh tu luc chay script.

   CHAY THEO DUNG THU TU: SIMS.sql -> Trigger_SIMS.sql -> file nay.
   Tat ca moc thoi gian deu tinh tuong doi qua DATEADD(..., GETDATE())
   nen script luon "con han"/"gan day" bat ke chay vao ngay nao -
   dung lai duoc (re-run) tren 1 CSDL SIMS_DB moi tao.
   ============================================================ */
USE SIMS_DB;
GO

-- ---- 1. Roles ----
INSERT INTO Roles (RoleCode, RoleName, Description) VALUES
('ADMIN',             N'Quản trị viên',        N'Toàn quyền hệ thống'),
('SALES_MANAGER',     N'Quản lý bán hàng',     N'Giám sát hoạt động bán hàng'),
('INVENTORY_MANAGER', N'Quản lý kho',          N'Kiểm soát nhập - xuất - tồn kho'),
('SALES_STAFF',       N'Nhân viên bán hàng',   N'Trực tiếp giao dịch với khách'),
('CUSTOMER',          N'Khách hàng',           N'Tự đăng ký, xem sản phẩm và mua hàng ở phía client');
GO

-- ---- 2. Permissions ----
-- Gom du toan bo permission code duoc dung trong RolePermissions.java /
-- PermissionManager (enforcement source that su la Java, bang SQL nay
-- chi phuc vu hien thi/tra cuu, xem ghi chu trong AppPermission).
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
('REPORT_PROFIT',       N'Báo cáo lợi nhuận'),
('ORDER_VIEW',          N'Xem đơn hàng online từ khách'),
('ORDER_MANAGE',        N'Xác nhận / hủy đơn hàng online từ khách'),
('BACKUP_MANAGE',       N'Xem trang Sao lưu & Khôi phục, tự sao lưu / khôi phục DB từ file backup'),
('RETURN_EXCHANGE_CREATE',  N'Tạo yêu cầu đổi/trả hàng cho hóa đơn'),
('RETURN_EXCHANGE_APPROVE', N'Duyệt / từ chối yêu cầu đổi/trả hàng giá trị lớn'),
('EXCEPTION_REPORT_CREATE', N'Gửi báo cáo ngoại lệ cho Quản lý bán hàng'),
('SETTINGS_MANAGE',     N'Xem và sửa trang Cài đặt hệ thống (VAT, tên cửa hàng, chính sách đổi trả...)'),
('SUPPLIER_RETURN_MANAGE',  N'Lập phiếu trả hàng lỗi/hỏng về nhà cung cấp');
GO

-- ---- 3. RolePermissions ----
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'ADMIN'), PermissionID FROM Permissions;

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'SALES_STAFF'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('STOCK_VIEW','PRODUCT_SEARCH','INVOICE_CREATE','INVOICE_CANCEL',
                          'RETURN_EXCHANGE','RETURN_EXCHANGE_CREATE','EXCEPTION_REPORT_SEND',
                          'EXCEPTION_REPORT_CREATE','CUSTOMER_MANAGE','ORDER_VIEW','ORDER_MANAGE');

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'INVENTORY_MANAGER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('STOCK_VIEW','STOCK_IMPORT','STOCK_RECONCILE','REPORT_INVENTORY',
                          'SUPPLIER_MANAGE','SUPPLIER_RETURN_MANAGE','EXCEPTION_REPORT_HANDLE');

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'SALES_MANAGER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('REPORT_REVENUE','REPORT_PROFIT','EXCEPTION_REPORT_HANDLE',
                          'RETURN_APPROVE','RETURN_EXCHANGE_APPROVE','AUDIT_VIEW');

-- Khach hang (tu dang ky o client): chi duoc tim/xem san pham
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER'), PermissionID
FROM Permissions
WHERE PermissionCode IN ('PRODUCT_SEARCH');
GO

-- ---- 4. Users ----
-- Mat khau tat ca tai khoan mau (tru 'khach_le' - vo hieu hoa) la: 123456
-- (BCrypt cost 12, xem UPDATE Users o cuoi file - dat sau khi bang Users co du dong).
INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, RoleID) VALUES
('admin',    '$2a$10$examplehash.admin.0000000000000000000000000000',    N'Hoàng Trung Nam',  'nam@connectmart.vn',   '0900000001', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='ADMIN')),
('salesmgr', '$2a$10$examplehash.salesmgr.000000000000000000000000000', N'Hà Minh Tuấn',     'tuan.sm@connectmart.vn', '0900000002', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_MANAGER')),
('invmgr',   '$2a$10$examplehash.invmgr.0000000000000000000000000000',  N'Trần Tài Phương',        'phuongkho.im@connectmart.vn',  '0900000003', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='INVENTORY_MANAGER')),
('staff01',  '$2a$10$examplehash.staff01.000000000000000000000000000', N'Lê Hoa Trường Vũ',     'vu.staff@connectmart.vn', '0900000004', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
('staff02',  '$2a$10$examplehash.staff02.000000000000000000000000000', N'Hoàng Văn Sơn',     'son.staff@connectmart.vn', '0900000005', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
-- Tai khoan khach hang (Role = CUSTOMER) - can co truoc vi Customers gio ke thua Users
('lan.nguyen',  '$2a$10$examplehash.customer1.00000000000000000000000', N'Nguyễn Thị Lan',  'lan.nguyen@gmail.com', '0912345678', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('hung.tran',   '$2a$10$examplehash.customer2.00000000000000000000000', N'Trần Văn Hùng',   'hung.tran@gmail.com',  '0987654321', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('mai.pham',    '$2a$10$examplehash.customer3.00000000000000000000000', N'Phạm Thị Mai',    'mai.pham@gmail.com',   '0933112233', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('duc.le',      '$2a$10$examplehash.customer4.00000000000000000000000', N'Lê Anh Đức',      'duc.le@gmail.com',     '0977665544', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('khach_le',    '$2a$10$examplehash.guest.000000000000000000000000000', N'Khách lẻ',        NULL,                    NULL,          NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER'));
GO

-- Tai khoan 'khach_le' chi la ho so dai dien cho khach vang lai (khong tu dang nhap
-- duoc vi mat khau la placeholder) - vo hieu hoa de tranh bi dung de dang nhap that.
UPDATE Users SET Status = 'DISABLED' WHERE Username = 'khach_le';
GO

-- ---- 5. Categories ----
INSERT INTO Categories (CategoryName) VALUES
(N'Trái cây'), (N'Rau củ'), (N'Đồ uống'), (N'Thực phẩm khô'), (N'Sữa các loại'), (N'Bánh kẹo');
GO

-- ---- 6. Suppliers ----
-- Them 1 NCC da bi xoa mem (IsDeleted=1) de trang Quan ly NCC co du truong hop
-- loc "da xoa"/"dang hoat dong".
INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES
(N'Công ty TNHH Nông sản Miền Tây', N'123 Nguyễn Trãi, Cần Thơ', '0710123456', 'contact@mientaynongsan.vn', N'Trái cây, rau củ'),
(N'Công ty CP Thực phẩm An Bình',   N'45 Lê Lợi, TP.HCM',        '0281234567', 'sales@anbinhfood.vn',       N'Đồ uống, thực phẩm khô'),
(N'Công ty TNHH Rau sạch Đà Lạt',   N'88 Trần Phú, Đà Lạt',      '0263123456', 'contact@dalatveggie.vn',    N'Rau củ'),
(N'Công ty CP Sữa & Bánh kẹo Việt', N'12 Cách Mạng Tháng 8, TP.HCM', '0287654321', 'sales@vietdairy.vn',    N'Sữa, bánh kẹo');
GO

INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES
(N'Công ty TNHH Gia vị Miền Trung', N'56 Trần Hưng Đạo, Đà Nẵng', '0236123456', 'contact@giavimientrung.vn', N'Gia vị, thực phẩm khô');
UPDATE Suppliers SET IsDeleted = 1, DeletedAt = DATEADD(DAY, -30, GETDATE())
WHERE SupplierName = N'Công ty TNHH Gia vị Miền Trung';
GO

-- ---- 7. Products ----
-- QUAN TRONG: Stock LUON de = 0 khi insert - day KHONG phai cot nhap tay,
-- ma la cot duoc trigger trg_PurchaseReceiptDetails_Insert tu dong cong don
-- theo InventoryBatch (xem sql/Trigger_SIMS.sql). Margin duoc dat rieng cho
-- tung SP de trg_Products_SyncSellPrice (chay ngay sau INSERT nay) tinh ra
-- dung SellPrice mong muon = ImportPrice + Margin, khong bi roi ve muc
-- DEFAULT_MARGIN chung (5000, fn_GetDefaultMargin). Tat ca ton kho ban dau
-- PHAI di qua muc 11 (Phieu nhap kho) ben duoi.
INSERT INTO Products (ProductName, CategoryID, ImportPrice, SellPrice, Margin, Stock, MinStock) VALUES
(N'Táo Envy',                (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      35000, 45000, 10000, 0, 10),
(N'Chuối già',                (SELECT CategoryID FROM Categories WHERE CategoryName=N'Trái cây'),      15000, 20000,  5000, 0, 15),
(N'Cà chua',                  (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        17500, 24000,  6500, 0, 10),
(N'Cà rốt',                   (SELECT CategoryID FROM Categories WHERE CategoryName=N'Rau củ'),        12000, 17000,  5000, 0, 10),
(N'Nước suối 500ml',          (SELECT CategoryID FROM Categories WHERE CategoryName=N'Đồ uống'),        4000,  6000,  2000, 0, 30),
(N'Trà xanh Không Độ 500ml',  (SELECT CategoryID FROM Categories WHERE CategoryName=N'Đồ uống'),        6000,  8500,  2500, 0, 20),
(N'Cà phê bột 500g',          (SELECT CategoryID FROM Categories WHERE CategoryName=N'Thực phẩm khô'), 65000, 89000, 24000, 0,  5),
(N'Mì tôm Hảo Hảo (thùng)',   (SELECT CategoryID FROM Categories WHERE CategoryName=N'Thực phẩm khô'), 90000,105000, 15000, 0,  5),
(N'Sữa tươi Vinamilk 1L',     (SELECT CategoryID FROM Categories WHERE CategoryName=N'Sữa các loại'),  28000, 36000,  8000, 0, 20),
(N'Bánh quy bơ 200g',         (SELECT CategoryID FROM Categories WHERE CategoryName=N'Bánh kẹo'),      20000, 28000,  8000, 0, 10);
GO

-- Mi tom Hao Hao khong nhap kho (Stock=0) va bi vo hieu hoa - minh hoa
-- san pham DISABLED trong ProductPanel/StockOverview.
UPDATE Products SET Status = 'DISABLED' WHERE ProductName = N'Mì tôm Hảo Hảo (thùng)';
GO

-- ---- 8. SupplierProducts ----
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
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 6000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 65000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Mì tôm Hảo Hảo (thùng)'), 90000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), 17500, 1),   -- gia tot hon -> NCC uu tien
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'), 12000, 1),    -- gia tot hon -> NCC uu tien
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 28000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Bánh quy bơ 200g'), 20000, 1);
GO

-- ---- 9. Customers ----
INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 120
FROM Users WHERE Username = 'lan.nguyen'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 35
FROM Users WHERE Username = 'hung.tran'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 68
FROM Users WHERE Username = 'mai.pham'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 12
FROM Users WHERE Username = 'duc.le'
UNION ALL
SELECT UserID, 'CUS_' + RIGHT('0000' + CAST(UserID AS VARCHAR(10)), 4), 0   -- dai dien cho khach vang lai khong luu thong tin
FROM Users WHERE Username = 'khach_le';
GO

-- ---- 10. Ca ban hang (Shifts) - 1 ca/ngay trong 7 ngay gan nhat ----
-- Ca cua 6 ngay truoc da CLOSED, ca hom nay (ngay 0) con OPEN - dung lam
-- ShiftID cho toan bo hoa don mau o muc 12 ben duoi (FK bat buoc).
INSERT INTO Shifts (UserID, StartTime, EndTime, Status) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)), DATEADD(HOUR, 21, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATEADD(HOUR, 8, CAST(CAST(DATEADD(DAY, 0,GETDATE()) AS DATE) AS DATETIME)), NULL, 'OPEN');
GO

-- ---- 11. Phieu nhap kho mau (BAT BUOC chay TRUOC muc 12 - Hoa don mau) ----
-- Day la nguon DUY NHAT tao InventoryBatch, trigger tu cong Products.Stock
-- tuong ung - KHONG duoc UPDATE Products.Stock thu cong o bat ky dau khac.
-- So luong nhap du de "song sot" qua 7 ngay ban hang mau o muc 12.
INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount) VALUES
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-10,GETDATE()), 112) + '-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-9,GETDATE()), 112) + '-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-8,GETDATE()), 112) + '-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-7,GETDATE()), 112) + '-001',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
-- Phieu rieng chi de tao 1 lo CA PHE BOT DA HET HAN (dung cho demo Tra NCC + Huy huy kho)
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-30,GETDATE()), 112) + '-002',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
-- Phieu nhap bo sung giua tuan (Tao Envy + Chuoi gia) - minh hoa 1 SP co NHIEU lo (FEFO)
('PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-002',
 (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty TNHH Nông sản Miền Tây'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0);
GO

-- Nong san Mien Tay: Tao Envy + Chuoi gia (trai cay, mau nhanh het han hon)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-10,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 300, 35000,
 N'LOT-TAO-001', DATEADD(DAY, -13, GETDATE()), DATEADD(DAY, 30, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-10,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 400, 15000,
 N'LOT-CHUOI-001', DATEADD(DAY, -11, GETDATE()), DATEADD(DAY, 14, GETDATE()));
GO

-- Rau sach Da Lat (NCC uu tien - gia 17500/12000 theo SupplierProducts): Ca chua + Ca rot
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-9,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), 200, 17500,
 N'LOT-CACHUA-001', DATEADD(DAY, -11, GETDATE()), DATEADD(DAY, 20, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-9,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'), 250, 12000,
 N'LOT-CAROT-001', DATEADD(DAY, -11, GETDATE()), DATEADD(DAY, 35, GETDATE()));
GO

-- An Binh: Nuoc suoi (khong theo doi HSD) + Tra xanh + Ca phe bot (kho, HSD dai)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-8,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 500, 4000,
 N'LOT-NUOC-001', NULL, NULL),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-8,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 300, 6000,
 N'LOT-TRAXANH-001', DATEADD(DAY, -10, GETDATE()), DATEADD(DAY, 180, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-8,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 150, 65000,
 N'LOT-CAPHE-001', DATEADD(DAY, -8, GETDATE()), DATEADD(DAY, 365, GETDATE()));
GO

-- Sua & Banh keo Viet: Sua tuoi (nhieu) + Banh quy bo (nhap it -> tu dong bao LOW_STOCK
-- qua trg_Products_AutoStockAlert vi Stock=8 <= MinStock=10 ngay sau khi cong kho)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-7,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 200, 28000,
 N'LOT-SUA-001', DATEADD(DAY, -9, GETDATE()), DATEADD(DAY, 25, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-7,GETDATE()), 112) + '-001'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Bánh quy bơ 200g'), 8, 20000,
 N'LOT-BANHQUY-001', DATEADD(DAY, -9, GETDATE()), DATEADD(DAY, 60, GETDATE()));
GO

-- Lo Ca phe bot DA HET HAN (30 ngay truoc, HSD -5 ngay so voi hom nay) - khong tinh
-- vao ton kho ban duoc (trigger loai b.ExpiryDate < GETDATE()), dung cho muc 13/14
-- (Tra hang ve NCC + Huy huy kho) minh hoa xu ly hang qua han.
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-30,GETDATE()), 112) + '-002'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 20, 65000,
 N'LOT-CAPHE-EXP-001', DATEADD(DAY, -395, GETDATE()), DATEADD(DAY, -5, GETDATE()));
GO

-- Nhap bo sung giua tuan: them 1 lo moi cho Tao Envy + Chuoi gia (2 lo/SP -> FEFO ro rang)
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-002'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 80, 35000,
 N'LOT-TAO-002', DATEADD(DAY, -4, GETDATE()), DATEADD(DAY, 25, GETDATE())),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode='PN-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-002'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 60, 15000,
 N'LOT-CHUOI-002', DATEADD(DAY, -4, GETDATE()), DATEADD(DAY, 10, GETDATE()));
GO

UPDATE r
SET r.TotalAmount = t.Sum
FROM PurchaseReceipts r
JOIN (SELECT ReceiptID, SUM(Quantity * ImportPrice) AS Sum FROM PurchaseReceiptDetails GROUP BY ReceiptID) t
  ON t.ReceiptID = r.ReceiptID;
GO

-- ---- 12. Hoa don ban hang 7 ngay gan nhat (phuc vu bieu do doanh thu) ----
-- Invoice A (sang, staff01, khach quen Lan, CASH), B (trua, staff02, khach le, BANK_TRANSFER),
-- C (chieu toi, staff01, khach quen Hung, CARD/PAYPAL xen ke) - moi ngay 3 hoa don
-- => dam bao RevenueChartPanel (RevenueReportDAO.getDailyRevenue) co du 7 cot doanh thu lien tuc.

-- Ngay -6
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-6,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-6,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-6,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-6,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-6,GETDATE()) AS DATE) AS DATETIME)) END),
 'CARD', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 5, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Bánh quy bơ 200g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Bánh quy bơ 200g'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -5
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-5,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-5,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-5,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-5,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-5,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-5,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-5,GETDATE()) AS DATE) AS DATETIME)) END),
 'PAYPAL', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -4
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-4,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-4,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-4,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-4,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-4,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-4,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-4,GETDATE()) AS DATE) AS DATETIME)) END),
 'CARD', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -3
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-3,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-3,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-3,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-3,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-3,GETDATE()) AS DATE) AS DATETIME)) END),
 'PAYPAL', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 6, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Bánh quy bơ 200g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Bánh quy bơ 200g'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -2
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-2,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-2,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-2,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-2,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-2,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-2,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-2,GETDATE()) AS DATE) AS DATETIME)) END),
 'CARD', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 5, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -1
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-1,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-1,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-1,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-1,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-1,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-1,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-1,GETDATE()) AS DATE) AS DATETIME)) END),
 'PAYPAL', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 4, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO

-- Ngay -0
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-0,GETDATE()), 112) + '-A',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-0,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 9, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) END),
 'CASH', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-0,GETDATE()), 112) + '-B',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-0,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 13, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) END),
 'BANK_TRANSFER', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
('HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-0,GETDATE()), 112) + '-C',
 (SELECT TOP 1 ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND CAST(StartTime AS DATE)=CAST(DATEADD(DAY,-0,GETDATE()) AS DATE)),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) > GETDATE() THEN DATEADD(MINUTE,-1,GETDATE()) ELSE DATEADD(HOUR, 18, CAST(CAST(DATEADD(DAY,-0,GETDATE()) AS DATE) AS DATETIME)) END),
 'CARD', 8, 0);
GO
INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già')),
((SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'));
GO
UPDATE i SET i.SubTotal = t.Sum, i.TotalAmount = t.Sum + (t.Sum * i.VATRate / 100)
FROM Invoices i JOIN (SELECT InvoiceID, SUM(LineTotal) AS Sum FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
WHERE i.InvoiceID = (SELECT TOP 1 InvoiceID FROM Invoices ORDER BY InvoiceID DESC);
GO



-- ---- 13. Doi/tra hang mau (co Approval that su, dung thu tu PENDING -> APPROVED) ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT InvoiceID FROM Invoices WHERE InvoiceCode = 'HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-2,GETDATE()), 112) + '-C'),
 'RETURN', N'Khách phản ánh chuối bị dập, xin trả lại 1 nải', 20000, 0, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff01'));
GO

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), 1, 'IN',
 (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già'));
GO

-- Duyet return (gia tri nho) - trigger trg_ReturnExchange_ApprovedStock se tu cong kho +
-- ghi InventoryTransactions (RETURN_IN) + tu dieu chinh lai Invoices.SubTotal/TotalAmount
UPDATE ReturnExchanges
SET Status = 'APPROVED',
    ApprovedBy = (SELECT UserID FROM Users WHERE Username='salesmgr'),
    ApprovedAt = GETDATE()
WHERE ReturnID = (SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC);
GO

-- ---- 14. Doi/tra gia tri lon, can duyet (van PENDING de demo man hinh cho duyet) ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT InvoiceID FROM Invoices WHERE InvoiceCode = 'HD-' + CONVERT(VARCHAR(8), DATEADD(DAY,-4,GETDATE()), 112) + '-B'),
 'EXCHANGE', N'Khách đổi cà phê bột lấy sữa tươi do đặt nhầm', 89000, 1, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff02'));
GO

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), 1, 'IN',
 (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),
((SELECT TOP 1 ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), 2, 'OUT',
 (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'));
GO
-- Gia tri lon (RequiresApproval=1) -> giu PENDING, cho SALES_MANAGER duyet o man hinh,
-- minh hoa constraint CK_Return_ApprovalRequired

-- ---- 15. Tra hang loi ve NCC (SupplierReturns) ----
-- Bang nay KHONG co trigger rieng - logic tru kho/cong no NCC nam trong
-- SupplierReturnDAO.java, tai lap y het o day: tru InventoryBatch.RemainingQty
-- (lo Ca phe bot DA HET HAN), tru Products.Stock, ghi InventoryTransactions
-- (SUPPLIER_RETURN, OUT), cong don Suppliers.DebtBalance.
DECLARE @SR_SupplierID INT = (SELECT SupplierID FROM Suppliers WHERE SupplierName=N'Công ty CP Thực phẩm An Bình');
DECLARE @SR_ProductID  INT = (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g');
DECLARE @SR_BatchID    INT = (SELECT BatchID FROM InventoryBatch WHERE LotNumber = N'LOT-CAPHE-EXP-001');
DECLARE @SR_Qty        INT = 10;
DECLARE @SR_UnitPrice  DECIMAL(18,0) = 65000;

INSERT INTO SupplierReturns (SupplierID, Reason, Status, TotalRefundAmount, Note, CreatedBy)
VALUES (@SR_SupplierID, 'EXPIRED', 'COMPLETED', @SR_Qty * @SR_UnitPrice,
        N'Trả lại lô cà phê bột hết hạn sử dụng, yêu cầu NCC hoàn tiền',
        (SELECT UserID FROM Users WHERE Username='invmgr'));

DECLARE @SR_ID INT = SCOPE_IDENTITY();

INSERT INTO SupplierReturnDetails (SupplierReturnID, ProductID, BatchID, Quantity, UnitRefundPrice)
VALUES (@SR_ID, @SR_ProductID, @SR_BatchID, @SR_Qty, @SR_UnitPrice);

UPDATE InventoryBatch
SET RemainingQty = RemainingQty - @SR_Qty,
    Status = CASE WHEN RemainingQty - @SR_Qty = 0 THEN 'DEPLETED' ELSE Status END
WHERE BatchID = @SR_BatchID;

UPDATE Products SET Stock = Stock - @SR_Qty WHERE ProductID = @SR_ProductID;

INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                    StockBefore, StockAfter, RefTable, RefID, CreatedBy, Note)
SELECT @SR_ProductID, 'SUPPLIER_RETURN', 'OUT', @SR_Qty,
       Stock + @SR_Qty, Stock, 'SupplierReturns', @SR_ID,
       (SELECT UserID FROM Users WHERE Username='invmgr'), N'Trả hàng hết hạn về NCC An Bình'
FROM Products WHERE ProductID = @SR_ProductID;

UPDATE Suppliers SET DebtBalance = DebtBalance + (@SR_Qty * @SR_UnitPrice) WHERE SupplierID = @SR_SupplierID;
GO

-- ---- 16. Huy huy kho (StockDisposals) ----
-- Phan con lai (10 don vi) cua lo Ca phe bot het han -> huy bo (khong tra NCC duoc nua),
-- tai lap logic StockDisposalDAO.java (khong co trigger rieng).
DECLARE @DP_ProductID INT = (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g');
DECLARE @DP_BatchID   INT = (SELECT BatchID FROM InventoryBatch WHERE LotNumber = N'LOT-CAPHE-EXP-001');
DECLARE @DP_Qty       INT = 10;
DECLARE @DP_UnitCost  DECIMAL(18,0) = 65000;

INSERT INTO StockDisposals (Reason, Status, TotalLossAmount, Note, CreatedBy)
VALUES ('EXPIRED', 'COMPLETED', @DP_Qty * @DP_UnitCost,
        N'Hủy toàn bộ số cà phê bột còn lại trong lô đã hết hạn sử dụng',
        (SELECT UserID FROM Users WHERE Username='invmgr'));

DECLARE @DP_ID INT = SCOPE_IDENTITY();

INSERT INTO StockDisposalDetails (DisposalID, ProductID, BatchID, Quantity, UnitCost)
VALUES (@DP_ID, @DP_ProductID, @DP_BatchID, @DP_Qty, @DP_UnitCost);

UPDATE InventoryBatch
SET RemainingQty = RemainingQty - @DP_Qty,
    Status = CASE WHEN RemainingQty - @DP_Qty = 0 THEN 'DEPLETED' ELSE Status END
WHERE BatchID = @DP_BatchID;

UPDATE Products SET Stock = Stock - @DP_Qty WHERE ProductID = @DP_ProductID;

INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                    StockBefore, StockAfter, RefTable, RefID, CreatedBy, Note)
SELECT @DP_ProductID, 'DISPOSAL', 'OUT', @DP_Qty,
       Stock + @DP_Qty, Stock, 'StockDisposals', @DP_ID,
       (SELECT UserID FROM Users WHERE Username='invmgr'), N'Hủy lô hết hạn (phần còn lại sau khi đã trả NCC)'
FROM Products WHERE ProductID = @DP_ProductID;
GO

-- ---- 17. Doi chieu kho cuoi ngay mau ----
-- Gia su kiem ke thuc te phat hien Ca rot thieu 2 don vi so voi he thong
INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy) VALUES
((SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName=N'Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName=N'Cà rốt') - 2,
 N'Kiểm kê cuối ca phát hiện thiếu, nghi do hao hụt khi bày quầy',
 (SELECT UserID FROM Users WHERE Username='invmgr'));
GO
-- Trigger trg_StockReconciliation_Apply se tu cap nhat Products.Stock ve ActualStock
-- + ghi InventoryTransactions (TransactionType='RECONCILE_ADJUST')

-- ---- 18. Bao cao ngoai le mau (1 PENDING, 1 da HANDLED) ----
INSERT INTO ExceptionReports (CreatedBy, Content) VALUES
((SELECT UserID FROM Users WHERE Username='staff02'),
 N'Khách yêu cầu mua "Xoài cát Hòa Lộc" nhưng sản phẩm chưa có trong hệ thống.');
GO

INSERT INTO ExceptionReports (CreatedBy, Content, Status, HandledBy, HandledAt) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'),
 N'Máy quét mã vạch ở quầy 2 quét không lên sản phẩm Nước suối 500ml, nghi lỗi tem.',
 'HANDLED', (SELECT UserID FROM Users WHERE Username='salesmgr'), DATEADD(HOUR, -6, GETDATE()));
GO

-- ---- 19. Canh bao ton kho (StockAlerts) ----
-- 1 dong tu trigger trg_Products_AutoStockAlert da tu sinh khi Banh quy bo
-- nhap kho (Stock=8 <= MinStock=10) o muc 11. Them thu cong 1 dong o trang
-- thai PLANNED va 1 dong RESOLVED de demo day du vong doi xu ly.
INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, Note, ReportedBy, Status) VALUES
((SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'),
 'LOW_STOCK', (SELECT Stock FROM Products WHERE ProductName=N'Cà phê bột 500g'),
 N'Sắp hết trước dịp cuối tuần, đề nghị nhập thêm', (SELECT UserID FROM Users WHERE Username='staff02'), 'PLANNED');
GO

INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, Note, ReportedBy, Status, ResolvedBy, ResolvedAt) VALUES
((SELECT ProductID FROM Products WHERE ProductName=N'Cà rốt'),
 'LOW_STOCK', 8, N'Đã lên kế hoạch nhập bổ sung từ NCC Đà Lạt',
 (SELECT UserID FROM Users WHERE Username='staff01'), 'RESOLVED',
 (SELECT UserID FROM Users WHERE Username='invmgr'), DATEADD(DAY, -1, GETDATE()));
GO

-- ---- 20. AuditLogs mau (co OldValue/NewValue de truy vet day du) ----
INSERT INTO AuditLogs (UserID, Action, TableName, RecordID, OldValue, NewValue, Detail, IPAddress) VALUES
((SELECT UserID FROM Users WHERE Username='admin'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='admin'),
   NULL, NULL, N'Đăng nhập thành công', '192.168.1.10'),

((SELECT UserID FROM Users WHERE Username='staff01'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff01'),
   NULL, NULL, N'Đăng nhập thành công', '192.168.1.25'),

((SELECT UserID FROM Users WHERE Username='salesmgr'), 'RETURN_APPROVE', 'ReturnExchanges',
   (SELECT MIN(ReturnID) FROM ReturnExchanges),
   N'{"Status":"PENDING","ApprovedBy":null}',
   N'{"Status":"APPROVED","ApprovedBy":"salesmgr"}',
   N'Duyệt đổi/trả giá trị nhỏ', '192.168.1.40'),

((SELECT UserID FROM Users WHERE Username='admin'), 'PRODUCT_PRICE_UPDATE', 'Products',
   (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'),
   N'{"SellPrice":23000}',
   N'{"SellPrice":24000}',
   N'Điều chỉnh giá bán theo giá nhập mới từ NCC Đà Lạt', '192.168.1.10'),

((SELECT UserID FROM Users WHERE Username='admin'), 'USER_LOCK', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff02'),
   N'{"IsLocked":false,"FailedLoginCount":5}',
   N'{"IsLocked":true,"FailedLoginCount":5}',
   N'Tài khoản tự động khóa sau 5 lần đăng nhập sai liên tiếp', NULL),

((SELECT UserID FROM Users WHERE Username='invmgr'), 'SUPPLIER_RETURN_CREATE', 'SupplierReturns',
   (SELECT MAX(SupplierReturnID) FROM SupplierReturns),
   NULL,
   N'{"Reason":"EXPIRED","Status":"COMPLETED"}',
   N'Lập phiếu trả hàng hết hạn về NCC An Bình', '192.168.1.30');
GO

-- ---- 21. Cau hinh he thong mau ----
-- QUAN TRONG: tat ca cac dong PHAI nam chung 1 cau INSERT (1 danh sach VALUES) -
-- 1 comment chen giua danh sach VALUES se tach roi tuple ra khoi cau lenh INSERT,
-- gay loi cu phap T-SQL lam HONG CA BATCH nay.
INSERT INTO StoreConfig (ConfigKey, ConfigValue) VALUES
('VAT_RATE', '0'),
('STORE_NAME', N'Connect Mart'),
('RETURN_POLICY_DAYS', '7'),
('DEFAULT_UNIT', N'cái'),
('DEFAULT_MARGIN', '5000'),
('POINT_RATE', '100000');
GO

UPDATE Products SET ImageUrl = 'uploads/products/tao-envy.jpg'               WHERE ProductName = N'Táo Envy';
UPDATE Products SET ImageUrl = 'uploads/products/chuoi-gia.jpg'              WHERE ProductName = N'Chuối già';
UPDATE Products SET ImageUrl = 'uploads/products/ca-chua.jpg'                WHERE ProductName = N'Cà chua';
UPDATE Products SET ImageUrl = 'uploads/products/ca-rot.jpg'                 WHERE ProductName = N'Cà rốt';
UPDATE Products SET ImageUrl = 'uploads/products/nuoc-suoi.jpg'              WHERE ProductName = N'Nước suối 500ml';
UPDATE Products SET ImageUrl = 'uploads/products/tra-xanh.jpg'               WHERE ProductName = N'Trà xanh Không Độ 500ml';
UPDATE Products SET ImageUrl = 'uploads/products/ca-phe-bot.jpg'             WHERE ProductName = N'Cà phê bột 500g';
UPDATE Products SET ImageUrl = 'uploads/products/mi-tom-hao-hao.jpg'         WHERE ProductName = N'Mì tôm Hảo Hảo (thùng)';
UPDATE Products SET ImageUrl = 'uploads/products/sua-tuoi-vinamilk.jpg'      WHERE ProductName = N'Sữa tươi Vinamilk 1L';
UPDATE Products SET ImageUrl = 'uploads/products/banh-quy-bo.jpg'            WHERE ProductName = N'Bánh quy bơ 200g';
GO

/* ============================================================
   Migration: Backfill Employees.EmployeeID cho cac tai khoan
   nhan vien (idempotent, an toan chay lai nhieu lan).
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

-- Hash BCrypt cost 12 cho password: 123456 - ap dung cho toan bo tai khoan mau
-- (tru 'khach_le' da bi vo hieu hoa o muc 4) de dang nhap demo/thuyet trinh.
UPDATE Users
SET PasswordHash = '$2a$12$rULa7sQqQB78UAMj4a.8IOPHPuspkHU2zffYsu75HhmFDVGPl3csS'
WHERE Username IN ('salesmgr', 'invmgr', 'staff01', 'staff02', 'lan.nguyen', 'hung.tran', 'mai.pham', 'duc.le');
GO

/* ============================================================
   22. KHUYEN MAI / MA GIAM GIA
   ============================================================ */
DELETE FROM Promotions WHERE Code IN ('SUMMER10', 'GIAM50K', 'FREESHIP', 'WELCOME15', 'FLASH20');
GO

INSERT INTO Promotions (
    Code, Name, DiscountType, DiscountValue,
    MaxDiscountAmount, MinOrderAmount,
    StartDate, EndDate, UsageLimit, UsedCount,
    IsActive, IsDeleted, CreatedBy, CreatedAt
) VALUES
('SUMMER10', N'Khuyến mãi hè - Giảm 10%', 'PERCENT', 10, 30000, 100000,
 CAST(DATEADD(DAY,-30,GETDATE()) AS DATE), CAST(DATEADD(DAY,180,GETDATE()) AS DATE), 1000, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), GETDATE()),
('GIAM50K', N'Giảm ngay 50.000đ', 'AMOUNT', 50000, NULL, 300000,
 CAST(DATEADD(DAY,-30,GETDATE()) AS DATE), CAST(DATEADD(DAY,180,GETDATE()) AS DATE), 500, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), GETDATE()),
('WELCOME15', N'Chào thành viên mới - Giảm 15%', 'PERCENT', 15, 40000, 150000,
 CAST(DATEADD(DAY,-30,GETDATE()) AS DATE), CAST(DATEADD(DAY,180,GETDATE()) AS DATE), NULL, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), GETDATE()),
('FLASH20', N'Flash sale - Giảm 20%', 'PERCENT', 20, 100000, 200000,
 CAST(GETDATE() AS DATE), DATEADD(DAY, 30, CAST(GETDATE() AS DATE)), 200, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), GETDATE()),
('FREESHIP', N'Ưu đãi 20.000đ', 'AMOUNT', 20000, NULL, 99000,
 CAST(DATEADD(DAY,-30,GETDATE()) AS DATE), CAST(DATEADD(DAY,180,GETDATE()) AS DATE), 9999, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), GETDATE());
GO

/* ============================================================
   23. Don hang online (Orders/OrderDetails) - du cac trang thai
   de demo OrderPanel (Admin/NV) va OrderHistoryPanel (Client).
   ============================================================ */
INSERT INTO Orders (CustomerID, CustomerName, CustomerEmail, CustomerPhone, ShippingAddress,
                     CreatedAt, SubTotal, VATRate, TotalAmount, PaymentMethod, PaymentStatus, OrderStatus, SeenByAdmin) VALUES
((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 N'Nguyễn Thị Lan', 'lan.nguyen@gmail.com', '0912345678', N'12 Nguyễn Huệ, Q.1, TP.HCM',
 DATEADD(HOUR, -2, GETDATE()), 0, 8, 0, 'COD', 'PENDING', 'NEW', 0),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 N'Trần Văn Hùng', 'hung.tran@gmail.com', '0987654321', N'45 Lý Thường Kiệt, Q.10, TP.HCM',
 DATEADD(DAY, -1, GETDATE()), 0, 8, 0, 'PAYPAL', 'PAID', 'CONFIRMED', 1),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='mai.pham'),
 N'Phạm Thị Mai', 'mai.pham@gmail.com', '0933112233', N'78 Điện Biên Phủ, Bình Thạnh, TP.HCM',
 DATEADD(DAY, -2, GETDATE()), 0, 8, 0, 'COD', 'PENDING', 'SHIPPING', 1),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='duc.le'),
 N'Lê Anh Đức', 'duc.le@gmail.com', '0977665544', N'9 Hoàng Diệu, Hải Châu, Đà Nẵng',
 DATEADD(DAY, -4, GETDATE()), 0, 8, 0, 'PAYPAL', 'PAID', 'COMPLETED', 1),

(NULL, N'Khách vãng lai - Đỗ Văn Kiên', 'kien.do.guest@gmail.com', '0909998888', N'23 Phan Đăng Lưu, Phú Nhuận, TP.HCM',
 DATEADD(DAY, -3, GETDATE()), 0, 8, 0, 'COD', 'FAILED', 'CANCELLED', 1);
GO

UPDATE Orders SET CancelReason = N'Khách đổi ý, không còn nhu cầu mua nữa'
WHERE OrderStatus = 'CANCELLED' AND CustomerEmail = 'kien.do.guest@gmail.com';
GO
UPDATE Orders SET CompletedAt = DATEADD(DAY, -3, GETDATE())
WHERE OrderStatus = 'COMPLETED' AND CustomerEmail = 'duc.le@gmail.com';
GO

INSERT INTO OrderDetails (OrderID, ProductID, ProductName, Quantity, UnitPrice) VALUES
((SELECT OrderID FROM Orders WHERE CustomerEmail='lan.nguyen@gmail.com' AND OrderStatus='NEW'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Táo Envy'), N'Táo Envy', 3, (SELECT SellPrice FROM Products WHERE ProductName=N'Táo Envy')),
((SELECT OrderID FROM Orders WHERE CustomerEmail='lan.nguyen@gmail.com' AND OrderStatus='NEW'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L'), N'Sữa tươi Vinamilk 1L', 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Sữa tươi Vinamilk 1L')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='hung.tran@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà phê bột 500g'), N'Cà phê bột 500g', 1, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà phê bột 500g')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='mai.pham@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml'), N'Trà xanh Không Độ 500ml', 6, (SELECT SellPrice FROM Products WHERE ProductName=N'Trà xanh Không Độ 500ml')),
((SELECT OrderID FROM Orders WHERE CustomerEmail='mai.pham@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Nước suối 500ml'), N'Nước suối 500ml', 6, (SELECT SellPrice FROM Products WHERE ProductName=N'Nước suối 500ml')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='duc.le@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Cà chua'), N'Cà chua', 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Cà chua')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='kien.do.guest@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName=N'Chuối già'), N'Chuối già', 2, (SELECT SellPrice FROM Products WHERE ProductName=N'Chuối già'));
GO

UPDATE o
SET o.SubTotal = t.Sum, o.TotalAmount = t.Sum
FROM Orders o
JOIN (SELECT OrderID, SUM(LineTotal) AS Sum FROM OrderDetails GROUP BY OrderID) t ON t.OrderID = o.OrderID;
GO

/* ============================================================
   24. Chat real-time (ChatConversations/ChatMessages) - demo
   ho tro khach hang + tin nhan noi bo giua nhan vien.
   ============================================================ */
INSERT INTO ChatConversations (ConversationType, CustomerUserID, CreatedAt, LastMessageAt, IsClosed) VALUES
('CUSTOMER_SUPPORT', (SELECT UserID FROM Users WHERE Username='lan.nguyen'),
 DATEADD(HOUR, -3, GETDATE()), DATEADD(MINUTE, -20, GETDATE()), 0);
GO

INSERT INTO ChatMessages (ConversationID, SenderUserID, SenderName, FromStaff, BodyText, CreatedAt, IsReadByPeer) VALUES
((SELECT TOP 1 ConversationID FROM ChatConversations WHERE ConversationType='CUSTOMER_SUPPORT' ORDER BY ConversationID DESC),
 (SELECT UserID FROM Users WHERE Username='lan.nguyen'), N'Nguyễn Thị Lan', 0,
 N'Shop ơi, đơn hàng của mình khi nào giao vậy ạ?', DATEADD(HOUR, -3, GETDATE()), 1),
((SELECT TOP 1 ConversationID FROM ChatConversations WHERE ConversationType='CUSTOMER_SUPPORT' ORDER BY ConversationID DESC),
 (SELECT UserID FROM Users WHERE Username='staff01'), N'Lê Hoa Trường Vũ', 1,
 N'Chào chị Lan, đơn hàng đang được đóng gói và sẽ giao trong hôm nay ạ.', DATEADD(MINUTE, -20, GETDATE()), 0);
GO

DECLARE @ChatA INT = (SELECT UserID FROM Users WHERE Username='staff01');
DECLARE @ChatB INT = (SELECT UserID FROM Users WHERE Username='staff02');
INSERT INTO ChatConversations (ConversationType, StaffUserIdA, StaffUserIdB, CreatedAt, LastMessageAt, IsClosed)
VALUES ('STAFF_DM', CASE WHEN @ChatA < @ChatB THEN @ChatA ELSE @ChatB END,
                     CASE WHEN @ChatA < @ChatB THEN @ChatB ELSE @ChatA END,
        DATEADD(HOUR, -5, GETDATE()), DATEADD(HOUR, -1, GETDATE()), 0);
GO

INSERT INTO ChatMessages (ConversationID, SenderUserID, SenderName, FromStaff, BodyText, CreatedAt, IsReadByPeer) VALUES
((SELECT TOP 1 ConversationID FROM ChatConversations WHERE ConversationType='STAFF_DM' ORDER BY ConversationID DESC),
 (SELECT UserID FROM Users WHERE Username='staff02'), N'Hoàng Văn Sơn', 0,
 N'Ca chiều nay bên quầy 2 hết nước suối rồi, có ai nhập thêm chưa nhỉ?', DATEADD(HOUR, -1, GETDATE()), 1);
GO

/* ============================================================
   Tài khoản khách hàng mẫu bổ sung (Username: customer1 / Password: 123456)
   ============================================================ */
IF NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'CUSTOMER')
BEGIN
    INSERT INTO Roles (RoleCode, RoleName, Description)
    VALUES ('CUSTOMER', N'Khách hàng', N'Tự đăng ký, xem sản phẩm và mua hàng ở phía client');
END
GO

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
        0, 0, 'ACTIVE'
    );
END
ELSE
BEGIN
    UPDATE Users
    SET PasswordHash     = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
        IsLocked         = 0,
        FailedLoginCount = 0,
        Status           = 'ACTIVE',
        RoleID           = (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER')
    WHERE Username = 'customer1';
END
GO

IF NOT EXISTS (
    SELECT 1 FROM Customers c JOIN Users u ON u.UserID = c.CustomerID WHERE u.Username = 'customer1'
)
BEGIN
    INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
    SELECT u.UserID, 'CUS_' + RIGHT('0000' + CAST(u.UserID AS VARCHAR(10)), 4), 0
    FROM Users u WHERE u.Username = 'customer1';
END
GO

/* ============================================================
   KIEM TRA NHANH SAU KHI CHAY (khong bat buoc, chi de doi chieu)
   ============================================================ */
SELECT CAST(CreatedAt AS DATE) AS Ngay, COUNT(*) AS SoHoaDon, SUM(TotalAmount) AS DoanhThu
FROM Invoices WHERE Status = 'ACTIVE'
GROUP BY CAST(CreatedAt AS DATE) ORDER BY Ngay;
GO