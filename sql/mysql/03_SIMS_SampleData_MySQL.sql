/* ============================================================
   DU LIEU MAU (INSERT) - PHIEN BAN DAY DU DE DEMO/THUYET TRINH
   Chuyen doi tu SQL Server sang MySQL
   Muc tieu: MOI BANG trong SIMS deu co du lieu, va bieu do
   doanh thu (RevenueChartPanel/RevenueReportDAO.getDailyRevenue)
   co du 7 cot lien tuc = 7 ngay gan nhat tinh tu luc chay script.

   CHAY THEO DUNG THU TU: schema SIMS MySQL -> file nay -> Trigger MySQL.
   Khong chay trigger truoc seed vi seed da tu tao lo/tong ton mau.
   Tat ca moc thoi gian deu tinh tuong doi qua DATE_ADD/DATE_SUB(..., NOW())
   nen script luon "con han"/"gan day" bat ke chay vao ngay nao -
   dung lai duoc (re-run) tren 1 CSDL SIMS_DB moi tao.
   ============================================================ */
USE SIMS_DB;

/* ============================================================
   CLEANUP - cho phep chay lai script tren DB da co du lieu
   (tranh loi #1062 Duplicate entry)
   ============================================================ */
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE ChatMessages;
TRUNCATE TABLE ChatConversations;
TRUNCATE TABLE OrderDetailBatches;
TRUNCATE TABLE OrderDetails;
TRUNCATE TABLE Orders;
TRUNCATE TABLE Promotions;
TRUNCATE TABLE AuditLogs;
TRUNCATE TABLE StockAlerts;
TRUNCATE TABLE ExceptionReports;
TRUNCATE TABLE StockReconciliation;
TRUNCATE TABLE StockDisposalDetails;
TRUNCATE TABLE StockDisposals;
TRUNCATE TABLE SupplierReturnDetails;
TRUNCATE TABLE SupplierReturns;
TRUNCATE TABLE ReturnExchangeDetailBatches;
TRUNCATE TABLE ReturnExchangeDetails;
TRUNCATE TABLE ReturnExchanges;
TRUNCATE TABLE InvoiceDetailBatches;
TRUNCATE TABLE InvoiceDetails;
TRUNCATE TABLE Invoices;
TRUNCATE TABLE InventoryTransactions;
TRUNCATE TABLE InventoryBatch;
TRUNCATE TABLE PurchaseReceiptDetails;
TRUNCATE TABLE PurchaseReceipts;
TRUNCATE TABLE Shifts;
TRUNCATE TABLE Employees;
TRUNCATE TABLE Customers;
TRUNCATE TABLE SupplierProducts;
TRUNCATE TABLE Products;
TRUNCATE TABLE Suppliers;
TRUNCATE TABLE Categories;
TRUNCATE TABLE RolePermissions;
TRUNCATE TABLE Users;
TRUNCATE TABLE Permissions;
TRUNCATE TABLE Roles;
TRUNCATE TABLE StoreConfig;

SET FOREIGN_KEY_CHECKS = 1;

-- ---- 1. Roles ----
INSERT INTO Roles (RoleCode, RoleName, Description) VALUES
('ADMIN',             'Quản trị viên',        'Toàn quyền hệ thống'),
('SALES_MANAGER',     'Quản lý bán hàng',     'Giám sát hoạt động bán hàng'),
('INVENTORY_MANAGER', 'Quản lý kho',          'Kiểm soát nhập - xuất - tồn kho'),
('SALES_STAFF',       'Nhân viên bán hàng',   'Trực tiếp giao dịch với khách'),
('CUSTOMER',          'Khách hàng',           'Tự đăng ký, xem sản phẩm và mua hàng ở phía client');

-- ---- 2. Permissions ----
INSERT INTO Permissions (PermissionCode, Description) VALUES
('USER_MANAGE',         'Quản lý người dùng (tạo/khóa/gán quyền)'),
('CATEGORY_MANAGE',     'Quản lý danh mục sản phẩm'),
('PRODUCT_MANAGE',      'Quản lý sản phẩm, giá bán, mức tồn tối thiểu'),
('SUPPLIER_MANAGE',     'Quản lý nhà cung cấp'),
('SYSTEM_CONFIG',       'Cấu hình hệ thống (VAT, chính sách...)'),
('STOCK_VIEW',          'Xem trạng thái tồn kho'),
('PRODUCT_SEARCH',      'Tìm kiếm sản phẩm'),
('INVOICE_CREATE',      'Tạo hóa đơn bán hàng'),
('INVOICE_CANCEL',      'Hủy hóa đơn'),
('RETURN_EXCHANGE',     'Xử lý đổi/trả hàng'),
('RETURN_APPROVE',      'Phê duyệt đổi/trả giá trị lớn'),
('EXCEPTION_REPORT_SEND',   'Gửi báo cáo ngoại lệ'),
('EXCEPTION_REPORT_HANDLE', 'Xử lý báo cáo ngoại lệ'),
('STOCK_IMPORT',        'Nhập hàng vào kho'),
('STOCK_RECONCILE',     'Đối chiếu kho cuối ngày'),
('CUSTOMER_MANAGE',     'Quản lý khách hàng'),
('AUDIT_VIEW',          'Xem nhật ký hệ thống'),
('REPORT_INVENTORY',    'Báo cáo tồn kho, biểu đồ xu hướng tồn'),
('REPORT_REVENUE',      'Thống kê doanh thu, biểu đồ xu hướng bán'),
('REPORT_PROFIT',       'Báo cáo lợi nhuận'),
('ORDER_VIEW',          'Xem đơn hàng online từ khách'),
('ORDER_MANAGE',        'Xác nhận / hủy đơn hàng online từ khách'),
('BACKUP_MANAGE',       'Xem trang Sao lưu & Khôi phục, tự sao lưu / khôi phục DB từ file backup'),
('RETURN_EXCHANGE_CREATE',  'Tạo yêu cầu đổi/trả hàng cho hóa đơn'),
('RETURN_EXCHANGE_APPROVE', 'Duyệt / từ chối yêu cầu đổi/trả hàng giá trị lớn'),
('EXCEPTION_REPORT_CREATE', 'Gửi báo cáo ngoại lệ cho Quản lý bán hàng'),
('SETTINGS_MANAGE',     'Xem và sửa trang Cài đặt hệ thống (VAT, tên cửa hàng, chính sách đổi trả...)'),
('SUPPLIER_RETURN_MANAGE',  'Lập phiếu trả hàng lỗi/hỏng về nhà cung cấp');

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

-- ---- 4. Users ----
-- Mat khau tat ca (tru khach_le): 123456
-- Hash BCrypt cost 12 that (tuong thich jBCrypt / PasswordUtils.verify)
SET @PWD_HASH = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi';

INSERT INTO Users (Username, PasswordHash, FullName, Email, Phone, AvatarUrl, RoleID) VALUES
('admin',      @PWD_HASH, 'Hoàng Trung Nam',  'nam@connectmart.vn',          '0900000001', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='ADMIN')),
('salesmgr',   @PWD_HASH, 'Hà Minh Tuấn',     'tuan.sm@connectmart.vn',      '0900000002', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_MANAGER')),
('invmgr',     @PWD_HASH, 'Trần Tài Phương',  'phuongkho.im@connectmart.vn', '0900000003', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='INVENTORY_MANAGER')),
('staff01',    @PWD_HASH, 'Lê Hoa Trường Vũ', 'vu.staff@connectmart.vn',     '0900000004', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
('staff02',    @PWD_HASH, 'Hoàng Văn Sơn',    'son.staff@connectmart.vn',    '0900000005', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='SALES_STAFF')),
('lan.nguyen', @PWD_HASH, 'Nguyễn Thị Lan',   'lan.nguyen@gmail.com',        '0912345678', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('hung.tran',  @PWD_HASH, 'Trần Văn Hùng',    'hung.tran@gmail.com',         '0987654321', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('mai.pham',   @PWD_HASH, 'Phạm Thị Mai',     'mai.pham@gmail.com',          '0933112233', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('duc.le',     @PWD_HASH, 'Lê Anh Đức',       'duc.le@gmail.com',            '0977665544', NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER')),
('khach_le',   @PWD_HASH, 'Khách lẻ',         NULL,                           NULL,          NULL, (SELECT RoleID FROM Roles WHERE RoleCode='CUSTOMER'));

-- Tai khoan 'khach_le' chi la ho so dai dien cho khach vang lai - vo hieu hoa
UPDATE Users SET Status = 'DISABLED' WHERE Username = 'khach_le';

-- ---- 5. Categories ----
INSERT INTO Categories (CategoryName) VALUES
('Trái cây'), ('Rau củ'), ('Đồ uống'), ('Thực phẩm khô'), ('Sữa các loại'), ('Bánh kẹo');

-- ---- 6. Suppliers ----
INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES
('Công ty TNHH Nông sản Miền Tây', '123 Nguyễn Trãi, Cần Thơ', '0710123456', 'contact@mientaynongsan.vn', 'Trái cây, rau củ'),
('Công ty CP Thực phẩm An Bình',   '45 Lê Lợi, TP.HCM',        '0281234567', 'sales@anbinhfood.vn',       'Đồ uống, thực phẩm khô'),
('Công ty TNHH Rau sạch Đà Lạt',   '88 Trần Phú, Đà Lạt',      '0263123456', 'contact@dalatveggie.vn',    'Rau củ'),
('Công ty CP Sữa & Bánh kẹo Việt', '12 Cách Mạng Tháng 8, TP.HCM', '0287654321', 'sales@vietdairy.vn',    'Sữa, bánh kẹo');

INSERT INTO Suppliers (SupplierName, Address, Phone, Email, SuppliedItems) VALUES
('Công ty TNHH Gia vị Miền Trung', '56 Trần Hưng Đạo, Đà Nẵng', '0236123456', 'contact@giavimientrung.vn', 'Gia vị, thực phẩm khô');
UPDATE Suppliers SET IsDeleted = 1, DeletedAt = DATE_SUB(NOW(), INTERVAL 30 DAY)
WHERE SupplierName = 'Công ty TNHH Gia vị Miền Trung';

-- ---- 7. Products ----
-- MySQL: trigger tren Products khong duoc UPDATE cung bang khi INSERT (#1442).
-- SellPrice da set = ImportPrice + Margin. DROP trigger thuong gap (an toan phpMyAdmin).
-- Neu ten khac: SHOW TRIGGERS WHERE `Table`='Products'; roi DROP thu cong.
DROP TRIGGER IF EXISTS trg_Products_SetCode;
DROP TRIGGER IF EXISTS trg_Products_SyncSellPrice;
DROP TRIGGER IF EXISTS trg_Products_AutoStockAlert;
DROP TRIGGER IF EXISTS trg_Products_AfterInsert;
DROP TRIGGER IF EXISTS trg_Products_BeforeInsert;
DROP TRIGGER IF EXISTS Products_SetCode;
DROP TRIGGER IF EXISTS Products_SyncSellPrice;
DROP TRIGGER IF EXISTS Products_AutoStockAlert;

INSERT INTO Products (ProductName, CategoryID, ImportPrice, SellPrice, Margin, Stock, MinStock) VALUES
('Táo Envy',                (SELECT CategoryID FROM Categories WHERE CategoryName='Trái cây'),      35000, 45000, 10000, 0, 10),
('Chuối già',                (SELECT CategoryID FROM Categories WHERE CategoryName='Trái cây'),      15000, 20000,  5000, 0, 15),
('Cà chua',                  (SELECT CategoryID FROM Categories WHERE CategoryName='Rau củ'),        17500, 24000,  6500, 0, 10),
('Cà rốt',                   (SELECT CategoryID FROM Categories WHERE CategoryName='Rau củ'),        12000, 17000,  5000, 0, 10),
('Nước suối 500ml',          (SELECT CategoryID FROM Categories WHERE CategoryName='Đồ uống'),        4000,  6000,  2000, 0, 30),
('Trà xanh Không Độ 500ml',  (SELECT CategoryID FROM Categories WHERE CategoryName='Đồ uống'),        6000,  8500,  2500, 0, 20),
('Cà phê bột 500g',          (SELECT CategoryID FROM Categories WHERE CategoryName='Thực phẩm khô'), 65000, 89000, 24000, 0,  5),
('Mì tôm Hảo Hảo (thùng)',   (SELECT CategoryID FROM Categories WHERE CategoryName='Thực phẩm khô'), 90000,105000, 15000, 0,  5),
('Sữa tươi Vinamilk 1L',     (SELECT CategoryID FROM Categories WHERE CategoryName='Sữa các loại'),  28000, 36000,  8000, 0, 20),
('Bánh quy bơ 200g',         (SELECT CategoryID FROM Categories WHERE CategoryName='Bánh kẹo'),      20000, 28000,  8000, 0, 10);

UPDATE Products SET Status = 'DISABLED' WHERE ProductName = 'Mì tôm Hảo Hảo (thùng)';

-- Neu co file Trigger MySQL, CHAY LAI file do SAU muc 7 de khoi phuc trigger tren Products.

-- ---- 8. SupplierProducts ----
INSERT INTO SupplierProducts (SupplierID, ProductID, SupplyPrice, IsPreferred) VALUES
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 35000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 15000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà chua'), 18500, 0),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà rốt'), 12500, 0),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 4000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 6000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 65000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT ProductID FROM Products WHERE ProductName='Mì tôm Hảo Hảo (thùng)'), 90000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà chua'), 17500, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà rốt'), 12000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 28000, 1),
((SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT ProductID FROM Products WHERE ProductName='Bánh quy bơ 200g'), 20000, 1);

-- ---- 9. Customers ----
INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
SELECT UserID, CONCAT('CUS_', LPAD(UserID, 4, '0')), 120
FROM Users WHERE Username = 'lan.nguyen'
UNION ALL
SELECT UserID, CONCAT('CUS_', LPAD(UserID, 4, '0')), 35
FROM Users WHERE Username = 'hung.tran'
UNION ALL
SELECT UserID, CONCAT('CUS_', LPAD(UserID, 4, '0')), 68
FROM Users WHERE Username = 'mai.pham'
UNION ALL
SELECT UserID, CONCAT('CUS_', LPAD(UserID, 4, '0')), 12
FROM Users WHERE Username = 'duc.le'
UNION ALL
SELECT UserID, CONCAT('CUS_', LPAD(UserID, 4, '0')), 0
FROM Users WHERE Username = 'khach_le';

-- ---- 10. Ca ban hang (Shifts) - 1 ca/ngay trong 7 ngay gan nhat ----
INSERT INTO Shifts (UserID, StartTime, EndTime, Status) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 8 HOUR), DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 21 HOUR), 'CLOSED'),
((SELECT UserID FROM Users WHERE Username='staff01'), DATE_ADD(CAST(NOW() AS DATE), INTERVAL 8 HOUR), NULL, 'OPEN');

-- ---- 11. Phieu nhap kho mau (BAT BUOC chay TRUOC muc 12 - Hoa don mau) ----
INSERT INTO PurchaseReceipts (ReceiptCode, SupplierID, CreatedBy, TotalAmount) VALUES
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 10 DAY), '%Y%m%d'), '-001'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 9 DAY), '%Y%m%d'), '-001'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Rau sạch Đà Lạt'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 8 DAY), '%Y%m%d'), '-001'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 7 DAY), '%Y%m%d'), '-001'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Sữa & Bánh kẹo Việt'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 30 DAY), '%Y%m%d'), '-002'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0),
(CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-002'),
 (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty TNHH Nông sản Miền Tây'),
 (SELECT UserID FROM Users WHERE Username='invmgr'), 0);

-- Nong san Mien Tay: Tao Envy + Chuoi gia
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 10 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 300, 35000,
 'LOT-TAO-001', DATE_SUB(NOW(), INTERVAL 13 DAY), DATE_ADD(NOW(), INTERVAL 30 DAY)),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 10 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 400, 15000,
 'LOT-CHUOI-001', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_ADD(NOW(), INTERVAL 14 DAY));

-- Rau sach Da Lat
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 9 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Cà chua'), 200, 17500,
 'LOT-CACHUA-001', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_ADD(NOW(), INTERVAL 20 DAY)),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 9 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Cà rốt'), 250, 12000,
 'LOT-CAROT-001', DATE_SUB(NOW(), INTERVAL 11 DAY), DATE_ADD(NOW(), INTERVAL 35 DAY));

-- An Binh: Nuoc suoi + Tra xanh + Ca phe bot
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 8 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 500, 4000,
 'LOT-NUOC-001', NULL, NULL),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 8 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 300, 6000,
 'LOT-TRAXANH-001', DATE_SUB(NOW(), INTERVAL 10 DAY), DATE_ADD(NOW(), INTERVAL 180 DAY)),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 8 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 150, 65000,
 'LOT-CAPHE-001', DATE_SUB(NOW(), INTERVAL 8 DAY), DATE_ADD(NOW(), INTERVAL 365 DAY));

-- Sua & Banh keo Viet
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 7 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 200, 28000,
 'LOT-SUA-001', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY)),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 7 DAY), '%Y%m%d'), '-001')),
 (SELECT ProductID FROM Products WHERE ProductName='Bánh quy bơ 200g'), 8, 20000,
 'LOT-BANHQUY-001', DATE_SUB(NOW(), INTERVAL 9 DAY), DATE_ADD(NOW(), INTERVAL 60 DAY));

-- Lo Ca phe bot DA HET HAN
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 30 DAY), '%Y%m%d'), '-002')),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 20, 65000,
 'LOT-CAPHE-EXP-001', DATE_SUB(NOW(), INTERVAL 395 DAY), DATE_SUB(NOW(), INTERVAL 5 DAY));

-- Nhap bo sung giua tuan
INSERT INTO PurchaseReceiptDetails (ReceiptID, ProductID, Quantity, ImportPrice, LotNumber, ManufactureDate, ExpiryDate) VALUES
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-002')),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 80, 35000,
 'LOT-TAO-002', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 25 DAY)),
((SELECT ReceiptID FROM PurchaseReceipts WHERE ReceiptCode=CONCAT('PN-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-002')),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 60, 15000,
 'LOT-CHUOI-002', DATE_SUB(NOW(), INTERVAL 4 DAY), DATE_ADD(NOW(), INTERVAL 10 DAY));

UPDATE PurchaseReceipts r
JOIN (
    SELECT ReceiptID, SUM(Quantity * ImportPrice) AS SumTotal
    FROM PurchaseReceiptDetails
    GROUP BY ReceiptID
) t ON t.ReceiptID = r.ReceiptID
SET r.TotalAmount = t.SumTotal;

-- ---- 11b. InventoryBatch + Stock (thay the trigger SQL Server) ----
-- Schema SIMS MySQL: SupplierID NOT NULL, ReceiptDetailID (khong phai ReceiptID).
INSERT INTO InventoryBatch (
    ProductID, SupplierID, ReceiptDetailID, LotNumber,
    Quantity, RemainingQty, ImportPrice,
    ManufactureDate, ExpiryDate, Status
)
SELECT
    d.ProductID,
    r.SupplierID,
    d.ReceiptDetailID,
    d.LotNumber,
    d.Quantity,
    d.Quantity,
    d.ImportPrice,
    d.ManufactureDate,
    d.ExpiryDate,
    CASE
        WHEN d.ExpiryDate IS NOT NULL AND d.ExpiryDate < CURDATE() THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END
FROM PurchaseReceiptDetails d
JOIN PurchaseReceipts r ON r.ReceiptID = d.ReceiptID;

-- Cong Stock san pham (chi lo ACTIVE + con han)
UPDATE Products p
JOIN (
    SELECT ProductID, SUM(RemainingQty) AS Qty
    FROM InventoryBatch
    WHERE Status = 'ACTIVE'
      AND (ExpiryDate IS NULL OR ExpiryDate >= CURDATE())
    GROUP BY ProductID
) b ON b.ProductID = p.ProductID
SET p.Stock = b.Qty;

-- ---- 12. Hoa don ban hang 7 ngay gan nhat (phuc vu bieu do doanh thu) ----
-- Helper: tao hoa don theo ngay offset (0 = hom nay, -1 = hom qua, ...)

-- ========== Ngay -6 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 6 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 6 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 6 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 6 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 6 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 6 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 6 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'CARD', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 5, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Bánh quy bơ 200g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Bánh quy bơ 200g'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay -5 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 5 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 5 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 5 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 5 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 5 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 5 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 5 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'PAYPAL', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay -4 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 4 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 4 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 4 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 4 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 4 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 4 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 4 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'CARD', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay -3 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 3 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 3 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 3 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 3 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 3 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'PAYPAL', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 6, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Bánh quy bơ 200g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Bánh quy bơ 200g'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay -2 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 2 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 2 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 2 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 2 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'CARD', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 5, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay -1 ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 1 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 1 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 1 DAY), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(DATE_SUB(NOW(), INTERVAL 1 DAY)) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(DATE_SUB(NOW(), INTERVAL 1 DAY) AS DATE), INTERVAL 18 HOUR) END),
 'PAYPAL', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 4, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ========== Ngay 0 (hom nay) ==========
INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(NOW(), '%Y%m%d'), '-A'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(NOW()) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 (CASE WHEN DATE_ADD(CAST(NOW() AS DATE), INTERVAL 9 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(NOW() AS DATE), INTERVAL 9 HOUR) END),
 'CASH', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(NOW(), '%Y%m%d'), '-B'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(NOW()) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'),
 NULL,
 (CASE WHEN DATE_ADD(CAST(NOW() AS DATE), INTERVAL 13 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(NOW() AS DATE), INTERVAL 13 HOUR) END),
 'BANK_TRANSFER', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 1, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

INSERT INTO Invoices (InvoiceCode, ShiftID, CreatedBy, CustomerID, CreatedAt, PaymentMethod, VATRate, TotalAmount) VALUES
(CONCAT('HD-', DATE_FORMAT(NOW(), '%Y%m%d'), '-C'),
 (SELECT ShiftID FROM Shifts WHERE UserID=(SELECT UserID FROM Users WHERE Username='staff01') AND DATE(StartTime)=DATE(NOW()) LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'),
 (SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 (CASE WHEN DATE_ADD(CAST(NOW() AS DATE), INTERVAL 18 HOUR) > NOW() THEN DATE_SUB(NOW(), INTERVAL 1 MINUTE) ELSE DATE_ADD(CAST(NOW() AS DATE), INTERVAL 18 HOUR) END),
 'CARD', 8, 0);

INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice) VALUES
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 3, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già')),
((SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 2, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'));

UPDATE Invoices i
JOIN (SELECT InvoiceID, SUM(LineTotal) AS SumTotal FROM InvoiceDetails GROUP BY InvoiceID) t ON t.InvoiceID = i.InvoiceID
SET i.SubTotal = t.SumTotal, i.TotalAmount = t.SumTotal + (t.SumTotal * i.VATRate / 100)
WHERE i.InvoiceID = (SELECT InvoiceID FROM (SELECT InvoiceID FROM Invoices ORDER BY InvoiceID DESC LIMIT 1) AS tmp);

-- ---- 13. Doi/tra hang mau (co Approval that su) ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT InvoiceID FROM Invoices WHERE InvoiceCode = CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 2 DAY), '%Y%m%d'), '-C')),
 'RETURN', 'Khách phản ánh chuối bị dập, xin trả lại 1 nải', 20000, 0, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff01'));

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 1, 'IN',
 (SELECT SellPrice FROM Products WHERE ProductName='Chuối già'));

UPDATE ReturnExchanges
SET Status = 'APPROVED',
    ApprovedBy = (SELECT UserID FROM Users WHERE Username='salesmgr'),
    ApprovedAt = NOW()
WHERE ReturnID = (SELECT ReturnID FROM (SELECT ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC LIMIT 1) AS tmp);

-- ---- 14. Doi/tra gia tri lon, can duyet (van PENDING) ----
INSERT INTO ReturnExchanges (InvoiceID, Type, Reason, TotalValue, RequiresApproval, Status, CreatedBy) VALUES
((SELECT InvoiceID FROM Invoices WHERE InvoiceCode = CONCAT('HD-', DATE_FORMAT(DATE_SUB(NOW(), INTERVAL 4 DAY), '%Y%m%d'), '-B')),
 'EXCHANGE', 'Khách đổi cà phê bột lấy sữa tươi do đặt nhầm', 89000, 1, 'PENDING',
 (SELECT UserID FROM Users WHERE Username='staff02'));

INSERT INTO ReturnExchangeDetails (ReturnID, ProductID, Quantity, Direction, UnitPrice) VALUES
((SELECT ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 1, 'IN',
 (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),
((SELECT ReturnID FROM ReturnExchanges ORDER BY ReturnID DESC LIMIT 1),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 2, 'OUT',
 (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'));

-- ---- 15. Tra hang loi ve NCC (SupplierReturns) ----
SET @SR_SupplierID = (SELECT SupplierID FROM Suppliers WHERE SupplierName='Công ty CP Thực phẩm An Bình');
SET @SR_ProductID  = (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g');
SET @SR_BatchID    = (SELECT BatchID FROM InventoryBatch WHERE LotNumber = 'LOT-CAPHE-EXP-001');
SET @SR_Qty        = 10;
SET @SR_UnitPrice  = 65000;

INSERT INTO SupplierReturns (SupplierID, Reason, Status, TotalRefundAmount, Note, CreatedBy)
VALUES (@SR_SupplierID, 'EXPIRED', 'COMPLETED', @SR_Qty * @SR_UnitPrice,
        'Trả lại lô cà phê bột hết hạn sử dụng, yêu cầu NCC hoàn tiền',
        (SELECT UserID FROM Users WHERE Username='invmgr'));

SET @SR_ID = LAST_INSERT_ID();

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
       (SELECT UserID FROM Users WHERE Username='invmgr'), 'Trả hàng hết hạn về NCC An Bình'
FROM Products WHERE ProductID = @SR_ProductID;

UPDATE Suppliers SET DebtBalance = DebtBalance + (@SR_Qty * @SR_UnitPrice) WHERE SupplierID = @SR_SupplierID;

-- ---- 16. Huy huy kho (StockDisposals) ----
SET @DP_ProductID = (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g');
SET @DP_BatchID   = (SELECT BatchID FROM InventoryBatch WHERE LotNumber = 'LOT-CAPHE-EXP-001');
SET @DP_Qty       = 10;
SET @DP_UnitCost  = 65000;

INSERT INTO StockDisposals (Reason, Status, TotalLossAmount, Note, CreatedBy)
VALUES ('EXPIRED', 'COMPLETED', @DP_Qty * @DP_UnitCost,
        'Hủy toàn bộ số cà phê bột còn lại trong lô đã hết hạn sử dụng',
        (SELECT UserID FROM Users WHERE Username='invmgr'));

SET @DP_ID = LAST_INSERT_ID();

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
       (SELECT UserID FROM Users WHERE Username='invmgr'), 'Hủy lô hết hạn (phần còn lại sau khi đã trả NCC)'
FROM Products WHERE ProductID = @DP_ProductID;

-- ---- 17. Doi chieu kho cuoi ngay mau ----
INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy) VALUES
((SELECT ProductID FROM Products WHERE ProductName='Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName='Cà rốt'),
 (SELECT Stock FROM Products WHERE ProductName='Cà rốt') - 2,
 'Kiểm kê cuối ca phát hiện thiếu, nghi do hao hụt khi bày quầy',
 (SELECT UserID FROM Users WHERE Username='invmgr'));

-- ---- 18. Bao cao ngoai le mau ----
INSERT INTO ExceptionReports (CreatedBy, Content) VALUES
((SELECT UserID FROM Users WHERE Username='staff02'),
 'Khách yêu cầu mua "Xoài cát Hòa Lộc" nhưng sản phẩm chưa có trong hệ thống.');

INSERT INTO ExceptionReports (CreatedBy, Content, Status, HandledBy, HandledAt) VALUES
((SELECT UserID FROM Users WHERE Username='staff01'),
 'Máy quét mã vạch ở quầy 2 quét không lên sản phẩm Nước suối 500ml, nghi lỗi tem.',
 'HANDLED', (SELECT UserID FROM Users WHERE Username='salesmgr'), DATE_SUB(NOW(), INTERVAL 6 HOUR));

-- ---- 19. Canh bao ton kho (StockAlerts) ----
INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, Note, ReportedBy, Status) VALUES
((SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'),
 'LOW_STOCK', (SELECT Stock FROM Products WHERE ProductName='Cà phê bột 500g'),
 'Sắp hết trước dịp cuối tuần, đề nghị nhập thêm', (SELECT UserID FROM Users WHERE Username='staff02'), 'PLANNED');

INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, Note, ReportedBy, Status, ResolvedBy, ResolvedAt) VALUES
((SELECT ProductID FROM Products WHERE ProductName='Cà rốt'),
 'LOW_STOCK', 8, 'Đã lên kế hoạch nhập bổ sung từ NCC Đà Lạt',
 (SELECT UserID FROM Users WHERE Username='staff01'), 'RESOLVED',
 (SELECT UserID FROM Users WHERE Username='invmgr'), DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---- 20. AuditLogs mau ----
INSERT INTO AuditLogs (UserID, Action, TableName, RecordID, OldValue, NewValue, Detail, IPAddress) VALUES
((SELECT UserID FROM Users WHERE Username='admin'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='admin'),
   NULL, NULL, 'Đăng nhập thành công', '192.168.1.10'),

((SELECT UserID FROM Users WHERE Username='staff01'), 'LOGIN', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff01'),
   NULL, NULL, 'Đăng nhập thành công', '192.168.1.25'),

((SELECT UserID FROM Users WHERE Username='salesmgr'), 'RETURN_APPROVE', 'ReturnExchanges',
   (SELECT MIN(ReturnID) FROM ReturnExchanges),
   '{"Status":"PENDING","ApprovedBy":null}',
   '{"Status":"APPROVED","ApprovedBy":"salesmgr"}',
   'Duyệt đổi/trả giá trị nhỏ', '192.168.1.40'),

((SELECT UserID FROM Users WHERE Username='admin'), 'PRODUCT_PRICE_UPDATE', 'Products',
   (SELECT ProductID FROM Products WHERE ProductName='Cà chua'),
   '{"SellPrice":23000}',
   '{"SellPrice":24000}',
   'Điều chỉnh giá bán theo giá nhập mới từ NCC Đà Lạt', '192.168.1.10'),

((SELECT UserID FROM Users WHERE Username='admin'), 'USER_LOCK', 'Users',
   (SELECT UserID FROM Users WHERE Username='staff02'),
   '{"IsLocked":false,"FailedLoginCount":5}',
   '{"IsLocked":true,"FailedLoginCount":5}',
   'Tài khoản tự động khóa sau 5 lần đăng nhập sai liên tiếp', NULL),

((SELECT UserID FROM Users WHERE Username='invmgr'), 'SUPPLIER_RETURN_CREATE', 'SupplierReturns',
   (SELECT MAX(SupplierReturnID) FROM SupplierReturns),
   NULL,
   '{"Reason":"EXPIRED","Status":"COMPLETED"}',
   'Lập phiếu trả hàng hết hạn về NCC An Bình', '192.168.1.30');

-- ---- 21. Cau hinh he thong mau ----
INSERT INTO StoreConfig (ConfigKey, ConfigValue) VALUES
('VAT_RATE', '8'),
('STORE_NAME', 'Connect Mart'),
('RETURN_POLICY_DAYS', '7'),
('RETURN_APPROVAL_THRESHOLD', '500000'),
('DEFAULT_UNIT', 'cái'),
('DEFAULT_MARGIN', '5000'),
('POINT_RATE', '100000');

UPDATE Products SET ImageUrl = 'uploads/products/tao-envy.jpg'               WHERE ProductName = 'Táo Envy';
UPDATE Products SET ImageUrl = 'uploads/products/chuoi-gia.jpg'              WHERE ProductName = 'Chuối già';
UPDATE Products SET ImageUrl = 'uploads/products/ca-chua.jpg'                WHERE ProductName = 'Cà chua';
UPDATE Products SET ImageUrl = 'uploads/products/ca-rot.jpg'                 WHERE ProductName = 'Cà rốt';
UPDATE Products SET ImageUrl = 'uploads/products/nuoc-suoi.jpg'              WHERE ProductName = 'Nước suối 500ml';
UPDATE Products SET ImageUrl = 'uploads/products/tra-xanh.jpg'               WHERE ProductName = 'Trà xanh Không Độ 500ml';
UPDATE Products SET ImageUrl = 'uploads/products/ca-phe-bot.jpg'             WHERE ProductName = 'Cà phê bột 500g';
UPDATE Products SET ImageUrl = 'uploads/products/mi-tom-hao-hao.jpg'         WHERE ProductName = 'Mì tôm Hảo Hảo (thùng)';
UPDATE Products SET ImageUrl = 'uploads/products/sua-tuoi-vinamilk.jpg'      WHERE ProductName = 'Sữa tươi Vinamilk 1L';
UPDATE Products SET ImageUrl = 'uploads/products/banh-quy-bo.jpg'            WHERE ProductName = 'Bánh quy bơ 200g';

/* ============================================================
   Migration: Backfill Employees.EmployeeID
   ============================================================ */
INSERT INTO Employees (UserID, EmployeeID)
SELECT u.UserID, CONCAT('EMP_', LPAD(u.UserID, 4, '0'))
FROM Users u
JOIN Roles r ON u.RoleID = r.RoleID
WHERE r.RoleCode <> 'CUSTOMER'
  AND NOT EXISTS (
        SELECT 1 FROM Employees e WHERE e.UserID = u.UserID
      );

-- Dam bao tat ca tai khoan mau (tru khach_le) dung BCrypt that cua "123456"
UPDATE Users
SET PasswordHash = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    IsLocked = 0,
    FailedLoginCount = 0,
    Status = 'ACTIVE'
WHERE Username IN ('admin', 'salesmgr', 'invmgr', 'staff01', 'staff02',
                   'lan.nguyen', 'hung.tran', 'mai.pham', 'duc.le', 'customer1');

/* ============================================================
   22. KHUYEN MAI / MA GIAM GIA
   ============================================================ */
DELETE FROM Promotions WHERE Code IN ('SUMMER10', 'GIAM50K', 'FREESHIP', 'WELCOME15', 'FLASH20');

INSERT INTO Promotions (
    Code, Name, DiscountType, DiscountValue,
    MaxDiscountAmount, MinOrderAmount,
    StartDate, EndDate, UsageLimit, UsedCount,
    IsActive, IsDeleted, CreatedBy, CreatedAt
) VALUES
('SUMMER10', 'Khuyến mãi hè - Giảm 10%', 'PERCENT', 10, 30000, 100000,
 DATE(DATE_SUB(NOW(), INTERVAL 30 DAY)), DATE(DATE_ADD(NOW(), INTERVAL 180 DAY)), 1000, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), NOW()),
('GIAM50K', 'Giảm ngay 50.000đ', 'AMOUNT', 50000, NULL, 300000,
 DATE(DATE_SUB(NOW(), INTERVAL 30 DAY)), DATE(DATE_ADD(NOW(), INTERVAL 180 DAY)), 500, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), NOW()),
('WELCOME15', 'Chào thành viên mới - Giảm 15%', 'PERCENT', 15, 40000, 150000,
 DATE(DATE_SUB(NOW(), INTERVAL 30 DAY)), DATE(DATE_ADD(NOW(), INTERVAL 180 DAY)), NULL, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), NOW()),
('FLASH20', 'Flash sale - Giảm 20%', 'PERCENT', 20, 100000, 200000,
 DATE(NOW()), DATE_ADD(DATE(NOW()), INTERVAL 30 DAY), 200, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), NOW()),
('FREESHIP', 'Ưu đãi 20.000đ', 'AMOUNT', 20000, NULL, 99000,
 DATE(DATE_SUB(NOW(), INTERVAL 30 DAY)), DATE(DATE_ADD(NOW(), INTERVAL 180 DAY)), 9999, 0, 1, 0,
 (SELECT UserID FROM Users WHERE Username='admin'), NOW());

/* ============================================================
   23. Don hang online (Orders/OrderDetails)
   ============================================================ */
INSERT INTO Orders (CustomerID, CustomerName, CustomerEmail, CustomerPhone, ShippingAddress,
                     CreatedAt, SubTotal, VATRate, TotalAmount, PaymentMethod, PaymentStatus, OrderStatus, SeenByAdmin) VALUES
((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='lan.nguyen'),
 'Nguyễn Thị Lan', 'lan.nguyen@gmail.com', '0912345678', '12 Nguyễn Huệ, Q.1, TP.HCM',
 DATE_SUB(NOW(), INTERVAL 2 HOUR), 0, 8, 0, 'COD', 'PENDING', 'NEW', 0),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='hung.tran'),
 'Trần Văn Hùng', 'hung.tran@gmail.com', '0987654321', '45 Lý Thường Kiệt, Q.10, TP.HCM',
 DATE_SUB(NOW(), INTERVAL 1 DAY), 0, 8, 0, 'PAYPAL', 'PAID', 'CONFIRMED', 1),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='mai.pham'),
 'Phạm Thị Mai', 'mai.pham@gmail.com', '0933112233', '78 Điện Biên Phủ, Bình Thạnh, TP.HCM',
 DATE_SUB(NOW(), INTERVAL 2 DAY), 0, 8, 0, 'COD', 'PENDING', 'SHIPPING', 1),

((SELECT c.CustomerID FROM Customers c JOIN Users u ON u.UserID=c.CustomerID WHERE u.Username='duc.le'),
 'Lê Anh Đức', 'duc.le@gmail.com', '0977665544', '9 Hoàng Diệu, Hải Châu, Đà Nẵng',
 DATE_SUB(NOW(), INTERVAL 4 DAY), 0, 8, 0, 'PAYPAL', 'PAID', 'COMPLETED', 1),

(NULL, 'Khách vãng lai - Đỗ Văn Kiên', 'kien.do.guest@gmail.com', '0909998888', '23 Phan Đăng Lưu, Phú Nhuận, TP.HCM',
 DATE_SUB(NOW(), INTERVAL 3 DAY), 0, 8, 0, 'COD', 'FAILED', 'CANCELLED', 1);

UPDATE Orders SET CancelReason = 'Khách đổi ý, không còn nhu cầu mua nữa'
WHERE OrderStatus = 'CANCELLED' AND CustomerEmail = 'kien.do.guest@gmail.com';

UPDATE Orders SET CompletedAt = DATE_SUB(NOW(), INTERVAL 3 DAY)
WHERE OrderStatus = 'COMPLETED' AND CustomerEmail = 'duc.le@gmail.com';

INSERT INTO OrderDetails (OrderID, ProductID, ProductName, Quantity, UnitPrice) VALUES
((SELECT OrderID FROM Orders WHERE CustomerEmail='lan.nguyen@gmail.com' AND OrderStatus='NEW'),
 (SELECT ProductID FROM Products WHERE ProductName='Táo Envy'), 'Táo Envy', 3, (SELECT SellPrice FROM Products WHERE ProductName='Táo Envy')),
((SELECT OrderID FROM Orders WHERE CustomerEmail='lan.nguyen@gmail.com' AND OrderStatus='NEW'),
 (SELECT ProductID FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L'), 'Sữa tươi Vinamilk 1L', 2, (SELECT SellPrice FROM Products WHERE ProductName='Sữa tươi Vinamilk 1L')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='hung.tran@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà phê bột 500g'), 'Cà phê bột 500g', 1, (SELECT SellPrice FROM Products WHERE ProductName='Cà phê bột 500g')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='mai.pham@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName='Trà xanh Không Độ 500ml'), 'Trà xanh Không Độ 500ml', 6, (SELECT SellPrice FROM Products WHERE ProductName='Trà xanh Không Độ 500ml')),
((SELECT OrderID FROM Orders WHERE CustomerEmail='mai.pham@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName='Nước suối 500ml'), 'Nước suối 500ml', 6, (SELECT SellPrice FROM Products WHERE ProductName='Nước suối 500ml')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='duc.le@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName='Cà chua'), 'Cà chua', 2, (SELECT SellPrice FROM Products WHERE ProductName='Cà chua')),

((SELECT OrderID FROM Orders WHERE CustomerEmail='kien.do.guest@gmail.com'),
 (SELECT ProductID FROM Products WHERE ProductName='Chuối già'), 'Chuối già', 2, (SELECT SellPrice FROM Products WHERE ProductName='Chuối già'));

UPDATE Orders o
JOIN (SELECT OrderID, SUM(LineTotal) AS SumTotal FROM OrderDetails GROUP BY OrderID) t ON t.OrderID = o.OrderID
SET o.SubTotal = t.SumTotal, o.TotalAmount = t.SumTotal;

/* ============================================================
   24. Chat real-time (ChatConversations/ChatMessages)
   ============================================================ */
INSERT INTO ChatConversations (ConversationType, CustomerUserID, CreatedAt, LastMessageAt, IsClosed) VALUES
('CUSTOMER_SUPPORT', (SELECT UserID FROM Users WHERE Username='lan.nguyen'),
 DATE_SUB(NOW(), INTERVAL 3 HOUR), DATE_SUB(NOW(), INTERVAL 20 MINUTE), 0);

INSERT INTO ChatMessages (ConversationID, SenderUserID, SenderName, FromStaff, BodyText, CreatedAt, IsReadByPeer) VALUES
((SELECT ConversationID FROM ChatConversations WHERE ConversationType='CUSTOMER_SUPPORT' ORDER BY ConversationID DESC LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='lan.nguyen'), 'Nguyễn Thị Lan', 0,
 'Shop ơi, đơn hàng của mình khi nào giao vậy ạ?', DATE_SUB(NOW(), INTERVAL 3 HOUR), 1),
((SELECT ConversationID FROM ChatConversations WHERE ConversationType='CUSTOMER_SUPPORT' ORDER BY ConversationID DESC LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff01'), 'Lê Hoa Trường Vũ', 1,
 'Chào chị Lan, đơn hàng đang được đóng gói và sẽ giao trong hôm nay ạ.', DATE_SUB(NOW(), INTERVAL 20 MINUTE), 0);

SET @ChatA = (SELECT UserID FROM Users WHERE Username='staff01');
SET @ChatB = (SELECT UserID FROM Users WHERE Username='staff02');
INSERT INTO ChatConversations (ConversationType, StaffUserIdA, StaffUserIdB, CreatedAt, LastMessageAt, IsClosed)
VALUES ('STAFF_DM', IF(@ChatA < @ChatB, @ChatA, @ChatB),
                     IF(@ChatA < @ChatB, @ChatB, @ChatA),
        DATE_SUB(NOW(), INTERVAL 5 HOUR), DATE_SUB(NOW(), INTERVAL 1 HOUR), 0);

INSERT INTO ChatMessages (ConversationID, SenderUserID, SenderName, FromStaff, BodyText, CreatedAt, IsReadByPeer) VALUES
((SELECT ConversationID FROM ChatConversations WHERE ConversationType='STAFF_DM' ORDER BY ConversationID DESC LIMIT 1),
 (SELECT UserID FROM Users WHERE Username='staff02'), 'Hoàng Văn Sơn', 0,
 'Ca chiều nay bên quầy 2 hết nước suối rồi, có ai nhập thêm chưa nhỉ?', DATE_SUB(NOW(), INTERVAL 1 HOUR), 1);

/* ============================================================
   Tai khoan khach hang mau bo sung (Username: customer1 / Password: 123456)
   ============================================================ */
INSERT INTO Roles (RoleCode, RoleName, Description)
SELECT 'CUSTOMER', 'Khách hàng', 'Tự đăng ký, xem sản phẩm và mua hàng ở phía client'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Roles WHERE RoleCode = 'CUSTOMER');

INSERT INTO Users (
    Username, PasswordHash, FullName, Email, Phone,
    RoleID, IsLocked, FailedLoginCount, Status
)
SELECT
    'customer1',
    '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    'Khách hàng Demo',
    'customer1@sims.local',
    '0901234567',
    (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER'),
    0, 0, 'ACTIVE'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM Users WHERE Username = 'customer1');

UPDATE Users
SET PasswordHash     = '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi',
    IsLocked         = 0,
    FailedLoginCount = 0,
    Status           = 'ACTIVE',
    RoleID           = (SELECT RoleID FROM Roles WHERE RoleCode = 'CUSTOMER')
WHERE Username = 'customer1';

INSERT INTO Customers (CustomerID, CustomerCode, MemberPoint)
SELECT u.UserID, CONCAT('CUS_', LPAD(u.UserID, 4, '0')), 0
FROM Users u
WHERE u.Username = 'customer1'
  AND NOT EXISTS (
        SELECT 1 FROM Customers c WHERE c.CustomerID = u.UserID
      );

/* ============================================================
   KIEM TRA NHANH SAU KHI CHAY (khong bat buoc)
   ============================================================ */
SELECT DATE(CreatedAt) AS Ngay, COUNT(*) AS SoHoaDon, SUM(TotalAmount) AS DoanhThu
FROM Invoices WHERE Status = 'ACTIVE'
GROUP BY DATE(CreatedAt) ORDER BY Ngay;
