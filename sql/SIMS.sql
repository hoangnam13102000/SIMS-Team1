/* ============================================================
   SIMS - Sales and Inventory Management System (Connect Mart)
   Schema hoan chinh, T-SQL (SQL Server)
   ============================================================ */
USE master;
GO

IF DB_ID('SIMS_DB') IS NOT NULL
BEGIN
    ALTER DATABASE SIMS_DB SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
    DROP DATABASE SIMS_DB;
END
GO
CREATE DATABASE SIMS_DB;
GO
USE SIMS_DB;
GO

/* ============================================================
   I. RBAC: Roles - Permissions - RolePermissions - Users
   ============================================================ */

CREATE TABLE Roles (
    RoleID          INT IDENTITY(1,1) PRIMARY KEY,
    RoleCode        VARCHAR(30)  NOT NULL UNIQUE,   -- ADMIN, SALES_MANAGER, INVENTORY_MANAGER, SALES_STAFF
    RoleName        NVARCHAR(100) NOT NULL,
    Description     NVARCHAR(255) NULL
);
GO

CREATE TABLE Permissions (
    PermissionID    INT IDENTITY(1,1) PRIMARY KEY,
    PermissionCode  VARCHAR(50)  NOT NULL UNIQUE,   -- vd: USER_MANAGE, INVOICE_CREATE...
    Description     NVARCHAR(255) NULL
);
GO

CREATE TABLE RolePermissions (
    RoleID          INT NOT NULL FOREIGN KEY REFERENCES Roles(RoleID),
    PermissionID    INT NOT NULL FOREIGN KEY REFERENCES Permissions(PermissionID),
    PRIMARY KEY (RoleID, PermissionID)
);
GO

CREATE TABLE Users (
    UserID          INT IDENTITY(1,1) PRIMARY KEY,
    Username        VARCHAR(50)   NOT NULL UNIQUE,
    PasswordHash    VARCHAR(255)  NOT NULL,          -- R5: BCrypt hash, KHONG luu plain-text
    FullName        NVARCHAR(100) NOT NULL,
    Email           VARCHAR(100)  NULL,
    Phone           VARCHAR(20)   NULL,
    AvatarUrl       NVARCHAR(500) NULL,              -- anh dai dien: URL hoac duong dan file cuc bo
    RoleID          INT NOT NULL FOREIGN KEY REFERENCES Roles(RoleID),
    IsLocked        BIT NOT NULL DEFAULT 0,          -- R5: tam khoa sau 5 lan sai
    FailedLoginCount INT NOT NULL DEFAULT 0,
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED')),
    IsDeleted       BIT NOT NULL DEFAULT 0,          -- xoa mem (vd tu trang Quan ly khach hang) - khong DELETE that
    DeletedAt       DATETIME NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE()
);
GO

/* ============================================================
   II. DANH MUC / NHA CUNG CAP / KHACH HANG / SAN PHAM
   ============================================================ */

CREATE TABLE Categories (
    CategoryID      INT IDENTITY(1,1) PRIMARY KEY,
    CategoryName    NVARCHAR(100) NOT NULL UNIQUE,
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED'))
);
GO

CREATE TABLE Suppliers (
    SupplierID      INT IDENTITY(1,1) PRIMARY KEY,
    SupplierName    NVARCHAR(150) NOT NULL,
    Address         NVARCHAR(255) NULL,
    Phone           VARCHAR(20)   NULL,
    Email           VARCHAR(100)  NULL,
    SuppliedItems   NVARCHAR(255) NULL,               -- mat hang cung cap (mo ta)
    IsDeleted       BIT NOT NULL DEFAULT 0,          -- xoa mem  - khong DELETE that
    DeletedAt       DATETIME NULL
);
GO

CREATE TABLE Customers (
    CustomerID      INT NOT NULL PRIMARY KEY,        -- = Users.UserID (1-1, ke thua)
    CustomerCode    VARCHAR(20) NOT NULL UNIQUE,    -- Ma khach hang: "CUS_" + UserID dem 4 so (vd CUS_0001) - dung lam ma vach the thanh vien
    MemberPoint     INT NOT NULL DEFAULT 0,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Customers_Users
        FOREIGN KEY (CustomerID)
        REFERENCES Users(UserID)
        ON DELETE CASCADE                              -- xoa Users thi xoa luon ho so Customers
);
GO
CREATE TABLE Employees (
    UserID          INT NOT NULL PRIMARY KEY,        -- = Users.UserID (1-1, ke thua)
    EmployeeID      VARCHAR(20) NOT NULL UNIQUE,     -- Ma nhan vien: "EMP_" + UserID dem 4 so (vd EMP_0001)
    DateOfBirth     DATE NULL,
    Gender          VARCHAR(10) NULL
                        CHECK (Gender IN ('MALE', 'FEMALE', 'OTHER')),
    Salary          DECIMAL(18,2) NULL,
    HireDate        DATE NOT NULL DEFAULT CAST(GETDATE() AS DATE),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Employees_Users
        FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
        ON DELETE CASCADE
);
GO
CREATE TABLE Products (

    ProductID       INT IDENTITY(1,1) PRIMARY KEY,
    ProductCode     AS ('SP_' + RIGHT('0000' + CAST(ProductID AS VARCHAR(10)), 4)) PERSISTED UNIQUE,
    ProductName     NVARCHAR(150) NOT NULL,
    CategoryID      INT NOT NULL FOREIGN KEY REFERENCES Categories(CategoryID),
    Brand           NVARCHAR(100) NULL,               -- Thuong hieu: Vinamilk, TH True Milk...
    Unit            NVARCHAR(30)  NULL,                -- Don vi tinh: Kg, Hop, Chai, Goi...
    WeightVolume    NVARCHAR(50)  NULL,                -- Khoi luong/dung tich: 180ml, 500g, 1kg...
    Description     NVARCHAR(1000) NULL,
    ImportPrice     DECIMAL(18,0) NOT NULL CHECK (ImportPrice >= 0),
    SellPrice       DECIMAL(18,0) NOT NULL,
    Margin          DECIMAL(18,0) NULL CHECK (Margin IS NULL OR Margin >= 0), -- chenh lech rieng cua SP; NULL = dung DEFAULT_MARGIN chung
    AutoPrice       BIT NOT NULL DEFAULT 1, -- 1 = tu dong tinh SellPrice theo cong thuc; 0 = ADMIN da khoa gia, nhap hang khong ghi de
    ImageUrl        NVARCHAR(500) NULL,
    Stock           INT NOT NULL DEFAULT 0 CHECK (Stock >= 0),
    MinStock        INT NOT NULL DEFAULT 5 CHECK (MinStock >= 0),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED')),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    UpdatedAt       DATETIME NULL,                     -- app tu set = GETDATE() moi lan UPDATE (xem ProductDAO.update())
    CONSTRAINT CK_Product_SellPrice CHECK (SellPrice >= ImportPrice)  -- R2
);
GO

-- Bang trung gian nhieu-nhieu: 1 san pham co the nhap tu nhieu NCC, 1 NCC cung cap nhieu SP
CREATE TABLE SupplierProducts (
    SupplierID   INT NOT NULL FOREIGN KEY REFERENCES Suppliers(SupplierID),
    ProductID    INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    SupplyPrice  DECIMAL(18,0) NULL,      -- gia nhap rieng cua NCC nay cho SP nay
    IsPreferred  BIT NOT NULL DEFAULT 0,  -- danh dau NCC uu tien/chinh cho SP nay
    CreatedAt    DATETIME NOT NULL DEFAULT GETDATE(),
    PRIMARY KEY (SupplierID, ProductID)
);
GO

/* ============================================================
   III. CA BAN HANG (SHIFT) - phuc vu R4 (huy trong cung ca/ngay)
   ============================================================ */

CREATE TABLE Shifts (
    ShiftID         INT IDENTITY(1,1) PRIMARY KEY,
    UserID          INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    StartTime       DATETIME NOT NULL DEFAULT GETDATE(),
    EndTime         DATETIME NULL,
    Status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (Status IN ('OPEN', 'CLOSED'))
);
GO

/* ============================================================
   IV. HOA DON BAN HANG
   ============================================================ */

CREATE TABLE Invoices (
    InvoiceID       INT IDENTITY(1,1) PRIMARY KEY,
    InvoiceCode     VARCHAR(30) NOT NULL UNIQUE,
    ShiftID         INT NOT NULL FOREIGN KEY REFERENCES Shifts(ShiftID),
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CustomerID      INT NULL FOREIGN KEY REFERENCES Customers(CustomerID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    SubTotal        DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountAmount  DECIMAL(18,0) NOT NULL DEFAULT 0
                        CHECK (DiscountAmount >= 0),              -- so tien giam tu ma KM (0 neu khong ap dung)
    PromotionID     INT NULL,                                     -- FK them sau khi co bang Promotions
    PromotionCode   VARCHAR(30) NULL,                             -- snapshot ma KM luc lap HD
    VATRate         DECIMAL(5,2)  NOT NULL DEFAULT 8,             -- lay tu StoreConfig VAT_RATE
    -- VAT tinh tren (SubTotal - DiscountAmount); TotalAmount = taxable + VAT (duy tri qua app)
    VATAmount       AS (
                        CASE WHEN (SubTotal - DiscountAmount) < 0 THEN 0
                             ELSE (SubTotal - DiscountAmount) * VATRate / 100
                        END
                    ) PERSISTED,
    TotalAmount     DECIMAL(18,0) NOT NULL DEFAULT 0,
    PaymentMethod   VARCHAR(20) NOT NULL DEFAULT 'CASH'
                        CHECK (PaymentMethod IN ('CASH','BANK_TRANSFER','PAYPAL','CARD')),
    PayPalOrderID   VARCHAR(50) NULL,     -- id don PayPal (Orders v2 API), chi co khi PaymentMethod = PAYPAL
    PayPalCaptureID VARCHAR(50) NULL,     -- id giao dich sau khi capture thanh cong
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'          -- R3: soft-delete
                        CHECK (Status IN ('ACTIVE', 'CANCELLED')),
    CancelReason    NVARCHAR(255) NULL,
    CancelledAt     DATETIME NULL,
    PointsUsed              INT NOT NULL DEFAULT 0
                                CHECK (PointsUsed >= 0),             -- so diem KH dung de tru tien
    PointsDiscountAmount    DECIMAL(18,0) NOT NULL DEFAULT 0
                                CHECK (PointsDiscountAmount >= 0),   -- so tien quy doi tu diem
    CONSTRAINT CK_Invoices_DiscountNotExceedSubTotal
        CHECK (DiscountAmount <= SubTotal)
);
GO

CREATE TABLE InvoiceDetails (
    InvoiceDetailID INT IDENTITY(1,1) PRIMARY KEY,
    InvoiceID       INT NOT NULL FOREIGN KEY REFERENCES Invoices(InvoiceID),
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    UnitPrice       DECIMAL(18,0) NOT NULL,
    LineTotal       AS (Quantity * UnitPrice) PERSISTED
);
GO

/* ============================================================
   V. DOI / TRA HANG
   ============================================================ */

CREATE TABLE ReturnExchanges (
    ReturnID        INT IDENTITY(1,1) PRIMARY KEY,
    InvoiceID       INT NOT NULL FOREIGN KEY REFERENCES Invoices(InvoiceID),
    Type            VARCHAR(20) NOT NULL CHECK (Type IN ('RETURN', 'EXCHANGE')),
    Reason          NVARCHAR(255) NOT NULL,               -- lý do khách
    RejectionReason NVARCHAR(500) NULL,                    -- lý do NV từ chối
    TotalValue      DECIMAL(18,0) NOT NULL DEFAULT 0,
    RequiresApproval BIT NOT NULL DEFAULT 0,
    Status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (Status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ApprovedBy      INT NULL FOREIGN KEY REFERENCES Users(UserID),
    ApprovedAt      DATETIME NULL,
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT CK_Return_ApprovalRequired
        CHECK (Status <> 'APPROVED' OR (ApprovedBy IS NOT NULL AND ApprovedAt IS NOT NULL))
);
GO

CREATE TABLE ReturnExchangeDetails (
    ReturnDetailID  INT IDENTITY(1,1) PRIMARY KEY,
    ReturnID        INT NOT NULL FOREIGN KEY REFERENCES ReturnExchanges(ReturnID),
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    Direction       VARCHAR(10) NOT NULL CHECK (Direction IN ('IN', 'OUT')), -- IN=khach tra (cong kho), OUT=doi hang moi (tru kho)
    UnitPrice       DECIMAL(18,0) NOT NULL
);
GO

/* ============================================================
   VI. NHAP KHO
   ============================================================ */

CREATE TABLE PurchaseReceipts (
    ReceiptID       INT IDENTITY(1,1) PRIMARY KEY,
    ReceiptCode     VARCHAR(30) NOT NULL UNIQUE,
    SupplierID      INT NOT NULL FOREIGN KEY REFERENCES Suppliers(SupplierID),
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    TotalAmount     DECIMAL(18,0) NOT NULL DEFAULT 0,
    Status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (Status IN ('COMPLETED', 'CANCELLED'))
);
GO

CREATE TABLE PurchaseReceiptDetails (
    ReceiptDetailID INT IDENTITY(1,1) PRIMARY KEY,
    ReceiptID       INT NOT NULL FOREIGN KEY REFERENCES PurchaseReceipts(ReceiptID),
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    ImportPrice     DECIMAL(18,0) NOT NULL,
    LotNumber       NVARCHAR(50) NULL,     -- so lo tren bao bi (co the trung giua cac lan nhap khac nhau)
    ManufactureDate DATE NULL,
    ExpiryDate      DATE NULL
);
GO

/* ============================================================
   VII. BAO CAO NGOAI LE (Staff -> Sales Manager) + THONG BAO HET HANG
   ============================================================ */

CREATE TABLE ExceptionReports (
    ReportID        INT IDENTITY(1,1) PRIMARY KEY,
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    Content         NVARCHAR(500) NOT NULL,
    Status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (Status IN ('PENDING', 'HANDLED')),
    HandledBy       INT NULL FOREIGN KEY REFERENCES Users(UserID),
    HandledAt       DATETIME NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE()
);
GO

-- NV ban hang bao cao cho Quan ly kho khi phat hien SP het hang (Stock = 0)
-- hoac sap het (Stock <= MinStock). Quan ly kho xem o trang "Canh bao ton kho"
-- (StockAlertPanel) de len ke hoach nhap hang bo sung.
CREATE TABLE StockAlerts (
    AlertID         INT IDENTITY(1,1) PRIMARY KEY,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    AlertType       VARCHAR(20) NOT NULL
                        CHECK (AlertType IN ('LOW_STOCK', 'OUT_OF_STOCK')),
    StockAtReport   INT NOT NULL,           -- ton kho tai thoi diem bao cao (luu lai de doi chieu sau)
    Note            NVARCHAR(255) NULL,     -- ghi chu tuy chon cua NV bao cao
    ReportedBy      INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    Status          VARCHAR(20) NOT NULL DEFAULT 'NEW'
                        CHECK (Status IN ('NEW', 'PLANNED', 'RESOLVED')),
                        -- NEW: moi bao cao, chua xu ly
                        -- PLANNED: quan ly kho da len ke hoach nhap hang bo sung
                        -- RESOLVED: da nhap hang xong / het van de
    SeenByInventoryManager BIT NOT NULL DEFAULT 0,  -- da xem/danh dau doc chuong hay chua
    ResolvedBy      INT NULL FOREIGN KEY REFERENCES Users(UserID),
    ResolvedAt      DATETIME NULL
);
GO
CREATE INDEX IX_StockAlerts_Seen ON StockAlerts(SeenByInventoryManager, CreatedAt);
GO
CREATE INDEX IX_StockAlerts_Product_Status ON StockAlerts(ProductID, Status);
GO

/* ============================================================
   VIII. SO CAI TON KHO
   ============================================================ */

CREATE TABLE InventoryTransactions (
    TransactionID   BIGINT IDENTITY(1,1) PRIMARY KEY,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    TransactionType VARCHAR(20) NOT NULL
                        CHECK (TransactionType IN ('IMPORT','SALE','SALE_CANCEL',
                                                    'RETURN_IN','RETURN_OUT','RECONCILE_ADJUST','DISPOSAL',
                                                    'SUPPLIER_RETURN')),
    Direction       VARCHAR(3) NOT NULL CHECK (Direction IN ('IN','OUT')),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    StockBefore     INT NOT NULL,
    StockAfter      INT NOT NULL,
    RefTable        VARCHAR(30) NOT NULL,      -- 'Invoices','PurchaseReceipts','ReturnExchanges','StockReconciliation'
    RefID           INT NOT NULL,
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    Note            NVARCHAR(255) NULL
);
GO
CREATE INDEX IX_InvTrans_Product_Date ON InventoryTransactions(ProductID, CreatedAt);
GO

CREATE TABLE InventoryBatch (
    BatchID         INT IDENTITY(1,1) PRIMARY KEY,
    BatchCode       AS ('LOT_' + RIGHT('000000' + CAST(BatchID AS VARCHAR(10)), 6)) PERSISTED UNIQUE,
    LotNumber       NVARCHAR(50) NULL,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    SupplierID      INT NOT NULL FOREIGN KEY REFERENCES Suppliers(SupplierID),
    ReceiptDetailID INT NULL UNIQUE FOREIGN KEY REFERENCES PurchaseReceiptDetails(ReceiptDetailID),
    ManufactureDate DATE NULL,
    ExpiryDate      DATE NULL,
    ImportDate      DATETIME NOT NULL DEFAULT GETDATE(),
    ImportPrice     DECIMAL(18,0) NOT NULL CHECK (ImportPrice >= 0),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    RemainingQty    INT NOT NULL CHECK (RemainingQty >= 0),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE','EXPIRED','DEPLETED')),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT CK_Batch_RemainingLEQty CHECK (RemainingQty <= Quantity),

    CONSTRAINT CK_Batch_Dates CHECK (ManufactureDate IS NULL OR ExpiryDate IS NULL OR ExpiryDate > ManufactureDate)
);
GO
CREATE INDEX IX_InventoryBatch_FEFO ON InventoryBatch(ProductID, ExpiryDate)
    INCLUDE (RemainingQty, Status);
GO


CREATE TABLE InvoiceDetailBatches (
    InvoiceDetailID INT NOT NULL FOREIGN KEY REFERENCES InvoiceDetails(InvoiceDetailID),
    BatchID         INT NOT NULL FOREIGN KEY REFERENCES InventoryBatch(BatchID),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (InvoiceDetailID, BatchID)
);
GO

-- Ghi vet lo hang lien quan toi tung dong Doi/Tra hang (giong vai
-- tro cua InvoiceDetailBatches ben tren):
--   - Voi dong Direction='IN' (khach tra hang): ghi lo nao da duoc
--     CONG lai (lo goc da ban, hoac lo "hang tra" moi tao neu khong
--     truy duoc lo goc).
--   - Voi dong Direction='OUT' (giao hang doi moi): ghi lo nao da bi
--     TRU theo FEFO.
-- Duoc trigger trg_ReturnExchange_ApprovedStock (Trigger_SIMS.sql)
-- ghi du lieu khi 1 yeu cau Doi/Tra chuyen sang APPROVED.
CREATE TABLE ReturnExchangeDetailBatches (
    ReturnDetailID  INT NOT NULL FOREIGN KEY REFERENCES ReturnExchangeDetails(ReturnDetailID),
    BatchID         INT NOT NULL FOREIGN KEY REFERENCES InventoryBatch(BatchID),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (ReturnDetailID, BatchID)
);
GO

/* ============================================================
   IX. NHAT KY HE THONG (AUDIT LOG)
   ============================================================ */

CREATE TABLE AuditLogs (
    LogID       BIGINT IDENTITY(1,1) PRIMARY KEY,
    UserID      INT NULL FOREIGN KEY REFERENCES Users(UserID),
    Action      VARCHAR(50) NOT NULL,      -- 'LOGIN','INVOICE_CANCEL','USER_LOCK',...
    TableName   VARCHAR(50) NULL,
    RecordID    INT NULL,
    OldValue    NVARCHAR(MAX) NULL,        -- snapshot truoc khi thay doi (JSON/text)
    NewValue    NVARCHAR(MAX) NULL,        -- snapshot sau khi thay doi (JSON/text)
    Detail      NVARCHAR(MAX) NULL,        -- ghi chu them, khong bat buoc
    IPAddress   VARCHAR(45) NULL,
    CreatedAt   DATETIME NOT NULL DEFAULT GETDATE()
);
GO
CREATE INDEX IX_AuditLogs_User_Date ON AuditLogs(UserID, CreatedAt);
GO



/* ============================================================
   X. DOI CHIEU KHO CUOI NGAY
   ============================================================ */

CREATE TABLE StockReconciliation (
    ReconciliationID INT IDENTITY(1,1) PRIMARY KEY,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    SystemStock     INT NOT NULL,       -- ton theo he thong tai thoi diem doi chieu
    ActualStock     INT NOT NULL,       -- ton dem thuc te
    Discrepancy     AS (ActualStock - SystemStock) PERSISTED,
    Note            NVARCHAR(255) NULL,
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE()
);
GO


CREATE TABLE Orders (
    OrderID         INT IDENTITY(1,1) PRIMARY KEY,
    OrderCode       AS ('DH' + RIGHT('0000' + CAST(OrderID AS VARCHAR(10)), 4)) PERSISTED,
    CustomerID      INT NULL FOREIGN KEY REFERENCES Customers(CustomerID),
    CustomerName    NVARCHAR(150) NOT NULL,
    CustomerEmail   VARCHAR(150) NOT NULL,
    CustomerPhone   VARCHAR(20)  NULL,
    ShippingAddress NVARCHAR(255) NOT NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    SubTotal        DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountAmount  DECIMAL(18,0) NOT NULL DEFAULT 0
                        CHECK (DiscountAmount >= 0),              -- so tien giam tu ma KM
    PromotionID     INT NULL,                                     -- FK them sau khi co bang Promotions
    PromotionCode   VARCHAR(30) NULL,                             -- snapshot ma KM luc dat hang
    VATRate         DECIMAL(5,2)  NOT NULL DEFAULT 8,
    VATAmount       AS (
                        CASE WHEN (SubTotal - DiscountAmount) < 0 THEN 0
                             ELSE (SubTotal - DiscountAmount) * VATRate / 100
                        END
                    ) PERSISTED,
    TotalAmount     DECIMAL(18,0) NOT NULL DEFAULT 0,              -- online: thuong = SubTotal - DiscountAmount
    PaymentMethod   VARCHAR(20) NOT NULL DEFAULT 'COD'
                        CHECK (PaymentMethod IN ('COD', 'PAYPAL')),
    PaymentStatus   VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (PaymentStatus IN ('PENDING', 'PAID', 'FAILED')),
    PayPalOrderID   VARCHAR(50) NULL,
    PayPalCaptureID VARCHAR(50) NULL,
    OrderStatus     VARCHAR(20) NOT NULL DEFAULT 'NEW'
                    CHECK (OrderStatus IN ('NEW', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED')),
    SeenByAdmin     BIT NOT NULL DEFAULT 0,
    CancelReason    NVARCHAR(500) NULL,   -- ly do huy don
    CompletedAt     DATETIME NULL,
    InvoiceID       INT NULL FOREIGN KEY REFERENCES Invoices(InvoiceID),
    CONSTRAINT CK_Orders_DiscountNotExceedSubTotal
        CHECK (DiscountAmount <= SubTotal)
);
GO

CREATE TABLE OrderDetails (
    OrderDetailID   INT IDENTITY(1,1) PRIMARY KEY,
    OrderID         INT NOT NULL FOREIGN KEY REFERENCES Orders(OrderID),
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    ProductName     NVARCHAR(150) NOT NULL,   -- luu lai ten tai thoi diem dat (phong khi san pham doi ten/xoa sau nay)
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    UnitPrice       DECIMAL(18,0) NOT NULL,
    LineTotal       AS (Quantity * UnitPrice) PERSISTED
);
GO


CREATE INDEX IX_Orders_SeenByAdmin ON Orders(SeenByAdmin, CreatedAt);
GO

-- 1 hoa don chi duoc sinh ra tu toi da 1 don hang (tranh link nham/link 2 lan do loi ung dung)
CREATE UNIQUE INDEX UX_Orders_InvoiceID ON Orders(InvoiceID) WHERE InvoiceID IS NOT NULL;
GO
/* ============================================================
   XI. CAU HINH HE THONG
   ============================================================ */

CREATE TABLE StoreConfig (
    ConfigKey       VARCHAR(50) PRIMARY KEY,
    ConfigValue     NVARCHAR(255) NOT NULL
);
GO

IF NOT EXISTS (SELECT 1 FROM sys.tables WHERE name = 'OrderDetailBatches')
BEGIN
    CREATE TABLE OrderDetailBatches (
        OrderDetailID   INT NOT NULL FOREIGN KEY REFERENCES OrderDetails(OrderDetailID),
        BatchID         INT NOT NULL FOREIGN KEY REFERENCES InventoryBatch(BatchID),
        Quantity        INT NOT NULL CHECK (Quantity > 0),
        PRIMARY KEY (OrderDetailID, BatchID)
    );
END
GO

IF OBJECT_ID('StockDisposalDetails', 'U') IS NOT NULL DROP TABLE StockDisposalDetails;
IF OBJECT_ID('StockDisposals', 'U') IS NOT NULL DROP TABLE StockDisposals;
GO

CREATE TABLE StockDisposals (
    DisposalID      INT IDENTITY(1,1) PRIMARY KEY,
    DisposalCode    AS ('TH_' + RIGHT('000000' + CAST(DisposalID AS VARCHAR(10)), 6)) PERSISTED UNIQUE,
    Reason          VARCHAR(20) NOT NULL
                        CHECK (Reason IN ('EXPIRED','DAMAGED','QUALITY','OTHER')),
    Status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (Status IN ('COMPLETED','CANCELLED')),
    TotalLossAmount DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (TotalLossAmount >= 0),
    Note            NVARCHAR(500) NULL,
    CreatedBy       INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE()
);
GO
CREATE INDEX IX_StockDisposals_CreatedAt ON StockDisposals(CreatedAt DESC);
GO

CREATE TABLE StockDisposalDetails (
    DisposalDetailID INT IDENTITY(1,1) PRIMARY KEY,
    DisposalID       INT NOT NULL FOREIGN KEY REFERENCES StockDisposals(DisposalID),
    ProductID        INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    BatchID          INT NOT NULL FOREIGN KEY REFERENCES InventoryBatch(BatchID),
    Quantity         INT NOT NULL CHECK (Quantity > 0),
    UnitCost         DECIMAL(18,0) NOT NULL CHECK (UnitCost >= 0),
    LineLossAmount   AS (Quantity * UnitCost) PERSISTED,
    CONSTRAINT UQ_Disposal_Batch UNIQUE (DisposalID, BatchID)
);
GO
CREATE INDEX IX_StockDisposalDetails_Product ON StockDisposalDetails(ProductID);
GO

/* ============================================================
   XII. TRA HANG LO VE NHA CUNG CAP (loi/hong/sai quy cach)
   Tru kho NGAY khi lap phieu (khong qua duyet), ghi nhan cong no
   NCC (DebtBalance) de theo doi hoan tien.
   ============================================================ */

IF COL_LENGTH('Suppliers', 'DebtBalance') IS NULL
BEGIN
    ALTER TABLE Suppliers ADD DebtBalance DECIMAL(18,0) NOT NULL DEFAULT 0;
END
GO

IF OBJECT_ID('SupplierReturnDetails', 'U') IS NOT NULL DROP TABLE SupplierReturnDetails;
IF OBJECT_ID('SupplierReturns', 'U') IS NOT NULL DROP TABLE SupplierReturns;
GO

CREATE TABLE SupplierReturns (
    SupplierReturnID   INT IDENTITY(1,1) PRIMARY KEY,
    SupplierReturnCode AS ('TRNC_' + RIGHT('000000' + CAST(SupplierReturnID AS VARCHAR(10)), 6)) PERSISTED UNIQUE,
    SupplierID          INT NOT NULL FOREIGN KEY REFERENCES Suppliers(SupplierID),
    Reason              VARCHAR(20) NOT NULL
                            CHECK (Reason IN ('DAMAGED','EXPIRED','QUALITY','WRONG_SPEC','OTHER')),
    Status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                            CHECK (Status IN ('COMPLETED','CANCELLED')),
    TotalRefundAmount   DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (TotalRefundAmount >= 0),
    Note                NVARCHAR(500) NULL,
    CreatedBy           INT NOT NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt           DATETIME NOT NULL DEFAULT GETDATE()
);
GO
CREATE INDEX IX_SupplierReturns_CreatedAt ON SupplierReturns(CreatedAt DESC);
GO
CREATE INDEX IX_SupplierReturns_Supplier ON SupplierReturns(SupplierID);
GO

CREATE TABLE SupplierReturnDetails (
    SupplierReturnDetailID INT IDENTITY(1,1) PRIMARY KEY,
    SupplierReturnID       INT NOT NULL FOREIGN KEY REFERENCES SupplierReturns(SupplierReturnID),
    ProductID               INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    BatchID                 INT NOT NULL FOREIGN KEY REFERENCES InventoryBatch(BatchID),
    Quantity                INT NOT NULL CHECK (Quantity > 0),
    UnitRefundPrice         DECIMAL(18,0) NOT NULL CHECK (UnitRefundPrice >= 0),
    LineRefundAmount        AS (Quantity * UnitRefundPrice) PERSISTED,
    CONSTRAINT UQ_SupplierReturn_Batch UNIQUE (SupplierReturnID, BatchID)
);
GO
CREATE INDEX IX_SupplierReturnDetails_Product ON SupplierReturnDetails(ProductID);
GO

/* ============================================================
   SIMS - chat real-time
   ============================================================ */

IF OBJECT_ID('dbo.ChatMessages', 'U') IS NOT NULL DROP TABLE dbo.ChatMessages;
IF OBJECT_ID('dbo.ChatConversations', 'U') IS NOT NULL DROP TABLE dbo.ChatConversations;
GO

CREATE TABLE ChatConversations (
    ConversationID      INT IDENTITY(1,1) PRIMARY KEY,
    -- CUSTOMER_SUPPORT | STAFF_DM
    ConversationType    VARCHAR(20)  NOT NULL
        CHECK (ConversationType IN ('CUSTOMER_SUPPORT', 'STAFF_DM')),
    -- Khách (Users.UserID role CUSTOMER). NULL nếu STAFF_DM
    CustomerUserID      INT NULL
        FOREIGN KEY REFERENCES Users(UserID),
    -- Cặp NV cho STAFF_DM: luôn lưu UserID nhỏ hơn vào A, lớn hơn vào B
    StaffUserIdA        INT NULL
        FOREIGN KEY REFERENCES Users(UserID),
    StaffUserIdB        INT NULL
        FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt           DATETIME NOT NULL DEFAULT GETDATE(),
    LastMessageAt       DATETIME NOT NULL DEFAULT GETDATE(),
    IsClosed            BIT NOT NULL DEFAULT 0
);
GO

-- Mỗi khách chỉ 1 hội thoại hỗ trợ đang mở
CREATE UNIQUE INDEX UX_ChatConv_Customer_Open
    ON ChatConversations (CustomerUserID)
    WHERE ConversationType = 'CUSTOMER_SUPPORT' AND CustomerUserID IS NOT NULL AND IsClosed = 0;
GO

-- Mỗi cặp NV chỉ 1 hội thoại DM
CREATE UNIQUE INDEX UX_ChatConv_StaffPair
    ON ChatConversations (StaffUserIdA, StaffUserIdB)
    WHERE ConversationType = 'STAFF_DM' AND StaffUserIdA IS NOT NULL AND StaffUserIdB IS NOT NULL;
GO

CREATE TABLE ChatMessages (
    MessageID           INT IDENTITY(1,1) PRIMARY KEY,
    ConversationID      INT NOT NULL
        FOREIGN KEY REFERENCES ChatConversations(ConversationID),
    SenderUserID        INT NOT NULL
        FOREIGN KEY REFERENCES Users(UserID),
    SenderName          NVARCHAR(100) NOT NULL,
    -- true = tin từ phía nhân viên (trong hội thoại khách)
    FromStaff           BIT NOT NULL DEFAULT 0,
    BodyText            NVARCHAR(MAX) NULL,
    -- Ảnh lưu file local (uploads/chat/...), KHÔNG lưu base64 trong DB
    ImagePath           NVARCHAR(500) NULL,
    ImageMime           VARCHAR(50) NULL,
    -- File đính kèm (pdf, doc, zip...) lưu local uploads/chat/files/
    FilePath            NVARCHAR(500) NULL,
    FileName            NVARCHAR(255) NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT GETDATE(),
    IsReadByPeer        BIT NOT NULL DEFAULT 0
);
GO

-- An toàn nếu DB cũ đã có ChatMessages nhưng chưa có 2 cột file
IF COL_LENGTH('dbo.ChatMessages', 'FilePath') IS NULL
    ALTER TABLE dbo.ChatMessages ADD FilePath NVARCHAR(500) NULL;
GO

IF COL_LENGTH('dbo.ChatMessages', 'FileName') IS NULL
    ALTER TABLE dbo.ChatMessages ADD FileName NVARCHAR(255) NULL;
GO

CREATE INDEX IX_ChatMessages_Conversation_Created
    ON ChatMessages (ConversationID, CreatedAt);
GO

CREATE INDEX IX_ChatMessages_Sender
    ON ChatMessages (SenderUserID, CreatedAt DESC);
GO

/* ============================================================
   XV. KHUYEN MAI / MA GIAM GIA
   Ap dung tai quay (POS) va online (Orders).
   DiscountAmount / PromotionID / PromotionCode nam trong Invoices & Orders.
   ============================================================ */

CREATE TABLE Promotions (
    PromotionID       INT IDENTITY(1,1) PRIMARY KEY,
    Code              VARCHAR(30)    NOT NULL UNIQUE,   -- ma khuyen mai, vd SUMMER10
    Name              NVARCHAR(150)  NOT NULL,          -- ten chuong trinh, vd "Khuyen mai he 2026"
    DiscountType      VARCHAR(10)    NOT NULL
                          CHECK (DiscountType IN ('PERCENT', 'AMOUNT')),
    DiscountValue     DECIMAL(18,0)  NOT NULL CHECK (DiscountValue > 0),
    MaxDiscountAmount DECIMAL(18,0)  NULL
                          CHECK (MaxDiscountAmount IS NULL OR MaxDiscountAmount >= 0),
    MinOrderAmount    DECIMAL(18,0)  NOT NULL DEFAULT 0 CHECK (MinOrderAmount >= 0),
    StartDate         DATE           NOT NULL,
    EndDate           DATE           NOT NULL,
    UsageLimit        INT            NULL CHECK (UsageLimit IS NULL OR UsageLimit > 0),
    UsedCount         INT            NOT NULL DEFAULT 0 CHECK (UsedCount >= 0),
    IsActive          BIT            NOT NULL DEFAULT 1,
    IsDeleted         BIT            NOT NULL DEFAULT 0,
    DeletedAt         DATETIME       NULL,
    CreatedBy         INT            NULL FOREIGN KEY REFERENCES Users(UserID),
    CreatedAt         DATETIME       NOT NULL DEFAULT GETDATE(),
    CONSTRAINT CK_Promotions_DateRange CHECK (EndDate >= StartDate)
);
GO

CREATE INDEX IX_Promotions_Code ON Promotions(Code);
GO
CREATE INDEX IX_Promotions_ActiveRange ON Promotions(IsActive, StartDate, EndDate);
GO

-- FK PromotionID (tao sau Promotions vi Invoices/Orders duoc tao truoc)
ALTER TABLE Invoices
    ADD CONSTRAINT FK_Invoices_Promotion
        FOREIGN KEY (PromotionID) REFERENCES Promotions(PromotionID);
GO
ALTER TABLE Orders
    ADD CONSTRAINT FK_Orders_Promotion
        FOREIGN KEY (PromotionID) REFERENCES Promotions(PromotionID);
GO

CREATE INDEX IX_Invoices_PromotionID ON Invoices(PromotionID) WHERE PromotionID IS NOT NULL;
GO
CREATE INDEX IX_Orders_PromotionID ON Orders(PromotionID) WHERE PromotionID IS NOT NULL;
GO
