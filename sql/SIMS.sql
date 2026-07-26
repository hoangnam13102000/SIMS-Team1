/* ============================================================
   SIMS - Sales and Inventory Management System (Connect Mart)
   Schema hoan chinh, T-SQL (SQL Server)
   ============================================================ */

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
    SuppliedItems   NVARCHAR(255) NULL               -- mat hang cung cap (mo ta)
);
GO

-- Customers KE THUA Users (Class-Table Inheritance): moi Customer BAT BUOC
-- la 1 Users (Role = CUSTOMER). CustomerID dung lai chinh UserID (shared PK),
-- KHONG con IDENTITY rieng va KHONG con luu trung FullName/Phone/Email nua -
-- 3 truong nay lay thang tu Users. Cach nay cung loai bo luon loi cu: truoc
-- day Customers.Phone la UNIQUE nhung NULL (khach khong nhap SDT), ma SQL
-- Server chi cho phep DUY NHAT 1 dong NULL trong 1 cot UNIQUE => tu khach
-- thu 2 tro di dang ky se bi loi vi pham UNIQUE. Gio Phone chi con o Users
-- (khong UNIQUE) nen khong con van de nay.
CREATE TABLE Customers (
    CustomerID      INT NOT NULL PRIMARY KEY,        -- = Users.UserID (1-1, ke thua)
    MemberPoint     INT NOT NULL DEFAULT 0,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE(),
    CONSTRAINT FK_Customers_Users
        FOREIGN KEY (CustomerID)
        REFERENCES Users(UserID)
        ON DELETE CASCADE                              -- xoa Users thi xoa luon ho so Customers
);
GO

CREATE TABLE Products (
    
    ProductID       INT IDENTITY(1,1) PRIMARY KEY,
    ProductName     NVARCHAR(150) NOT NULL,
    CategoryID      INT NOT NULL FOREIGN KEY REFERENCES Categories(CategoryID),
    ImportPrice     DECIMAL(18,0) NOT NULL CHECK (ImportPrice >= 0),
    SellPrice       DECIMAL(18,0) NOT NULL,
    ImageUrl        NVARCHAR(500) NULL,
    Stock           INT NOT NULL DEFAULT 0 CHECK (Stock >= 0),
    MinStock        INT NOT NULL DEFAULT 5 CHECK (MinStock >= 0),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED')),
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
    VATRate         DECIMAL(5,2)  NOT NULL DEFAULT 8,   -- lấy từ StoreConfig VAT_RATE
    VATAmount       AS (SubTotal * VATRate / 100) PERSISTED,
    TotalAmount     DECIMAL(18,0) NOT NULL DEFAULT 0,   -- SubTotal + VATAmount, duy tri qua trigger/app
    PaymentMethod   VARCHAR(20) NOT NULL DEFAULT 'CASH'
                        CHECK (PaymentMethod IN ('CASH','BANK_TRANSFER','MOMO','CARD')),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'          -- R3: soft-delete
                        CHECK (Status IN ('ACTIVE', 'CANCELLED')),
    CancelReason    NVARCHAR(255) NULL,
    CancelledAt     DATETIME NULL
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
    Reason          NVARCHAR(255) NOT NULL,               -- R4: bat buoc ghi ro ly do
    TotalValue      DECIMAL(18,0) NOT NULL DEFAULT 0,
    RequiresApproval BIT NOT NULL DEFAULT 0,               -- R4: gia tri lon can duyet
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
    ImportPrice     DECIMAL(18,0) NOT NULL
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

CREATE TABLE StockAlerts (
    AlertID         INT IDENTITY(1,1) PRIMARY KEY,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    Message         NVARCHAR(255) NOT NULL,
    RecipientRoleID INT NOT NULL FOREIGN KEY REFERENCES Roles(RoleID),
    IsRead          BIT NOT NULL DEFAULT 0,
    CreatedAt       DATETIME NOT NULL DEFAULT GETDATE()
);
GO

/* ============================================================
   VIII. SO CAI TON KHO
   ============================================================ */

CREATE TABLE InventoryTransactions (
    TransactionID   BIGINT IDENTITY(1,1) PRIMARY KEY,
    ProductID       INT NOT NULL FOREIGN KEY REFERENCES Products(ProductID),
    TransactionType VARCHAR(20) NOT NULL
                        CHECK (TransactionType IN ('IMPORT','SALE','SALE_CANCEL',
                                                    'RETURN_IN','RETURN_OUT','RECONCILE_ADJUST')),
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

/* ============================================================
   XI. CAU HINH HE THONG
   ============================================================ */

CREATE TABLE StoreConfig (
    ConfigKey       VARCHAR(50) PRIMARY KEY,
    ConfigValue     NVARCHAR(255) NOT NULL
);
GO