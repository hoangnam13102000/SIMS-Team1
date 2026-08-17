/* ============================================================
   SIMS - Sales and Inventory Management System (Connect Mart)
   Schema hoàn chỉnh - MySQL 8.0+ / MariaDB 10.5+
   Đã tích hợp: DiscountShare + PointsShare vào ReturnExchanges
   Đã tích hợp: Shift cash reconciliation + ShiftCashTransactions.
   ============================================================ */
CREATE DATABASE IF NOT EXISTS SIMS_DB CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE SIMS_DB;

/* ============================================================
   I. RBAC
   ============================================================ */
CREATE TABLE Roles (
    RoleID          INT AUTO_INCREMENT PRIMARY KEY,
    RoleCode        VARCHAR(30)  NOT NULL UNIQUE,
    RoleName        VARCHAR(100) NOT NULL,
    Description     VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE Permissions (
    PermissionID    INT AUTO_INCREMENT PRIMARY KEY,
    PermissionCode  VARCHAR(50)  NOT NULL UNIQUE,
    Description     VARCHAR(255) NULL
) ENGINE=InnoDB;

CREATE TABLE RolePermissions (
    RoleID          INT NOT NULL,
    PermissionID    INT NOT NULL,
    PRIMARY KEY (RoleID, PermissionID),
    CONSTRAINT FK_RolePermissions_Roles
        FOREIGN KEY (RoleID) REFERENCES Roles(RoleID),
    CONSTRAINT FK_RolePermissions_Permissions
        FOREIGN KEY (PermissionID) REFERENCES Permissions(PermissionID)
) ENGINE=InnoDB;

CREATE TABLE Users (
    UserID              INT AUTO_INCREMENT PRIMARY KEY,
    Username            VARCHAR(50)  NOT NULL UNIQUE,
    PasswordHash        VARCHAR(255) NOT NULL,
    FullName            VARCHAR(100) NOT NULL,
    Email               VARCHAR(100) NULL,
    Phone               VARCHAR(20)  NULL,
    AvatarUrl           VARCHAR(500) NULL,
    RoleID              INT NOT NULL,
    IsLocked            TINYINT(1) NOT NULL DEFAULT 0,
    FailedLoginCount    INT NOT NULL DEFAULT 0,
    Status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (Status IN ('ACTIVE', 'DISABLED')),
    IsDeleted           TINYINT(1) NOT NULL DEFAULT 0,
    DeletedAt           DATETIME NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_Users_Roles FOREIGN KEY (RoleID) REFERENCES Roles(RoleID)
) ENGINE=InnoDB;

/* ============================================================
   II. DANH MỤC / NCC / KHÁCH / SẢN PHẨM
   ============================================================ */
CREATE TABLE Categories (
    CategoryID      INT AUTO_INCREMENT PRIMARY KEY,
    CategoryName    VARCHAR(100) NOT NULL UNIQUE,
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB;

CREATE TABLE Suppliers (
    SupplierID      INT AUTO_INCREMENT PRIMARY KEY,
    SupplierName    VARCHAR(150) NOT NULL,
    Address         VARCHAR(255) NULL,
    Phone           VARCHAR(20) NULL,
    Email           VARCHAR(100) NULL,
    SuppliedItems   VARCHAR(255) NULL,
    IsDeleted       TINYINT(1) NOT NULL DEFAULT 0,
    DeletedAt       DATETIME NULL,
    DebtBalance     DECIMAL(18,0) NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE Customers (
    CustomerID      INT NOT NULL PRIMARY KEY,
    CustomerCode    VARCHAR(20) NOT NULL UNIQUE,
    MemberPoint     INT NOT NULL DEFAULT 0,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_Customers_Users
        FOREIGN KEY (CustomerID) REFERENCES Users(UserID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Employees (
    UserID          INT NOT NULL PRIMARY KEY,
    EmployeeID      VARCHAR(20) NOT NULL UNIQUE,
    DateOfBirth     DATE NULL,
    Gender          VARCHAR(10) NULL CHECK (Gender IN ('MALE', 'FEMALE', 'OTHER')),
    Salary          DECIMAL(18,2) NULL,
    HireDate        DATE NOT NULL DEFAULT (CURRENT_DATE),
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_Employees_Users
        FOREIGN KEY (UserID) REFERENCES Users(UserID) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE Products (
    ProductID       INT AUTO_INCREMENT PRIMARY KEY,
    ProductCode     VARCHAR(20) NULL,
    ProductName     VARCHAR(150) NOT NULL,
    CategoryID      INT NOT NULL,
    Brand           VARCHAR(100) NULL,
    Unit            VARCHAR(30) NULL,
    WeightVolume    VARCHAR(50) NULL,
    Description     VARCHAR(1000) NULL,
    ImportPrice     DECIMAL(18,0) NOT NULL CHECK (ImportPrice >= 0),
    SellPrice       DECIMAL(18,0) NOT NULL,
    Margin          DECIMAL(18,0) NULL CHECK (Margin IS NULL OR Margin >= 0),
    AutoPrice       TINYINT(1) NOT NULL DEFAULT 1,
    ImageUrl        VARCHAR(500) NULL,
    Stock           INT NOT NULL DEFAULT 0 CHECK (Stock >= 0),
    MinStock        INT NOT NULL DEFAULT 5 CHECK (MinStock >= 0),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE', 'DISABLED')),
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UpdatedAt       DATETIME NULL,
    UNIQUE KEY UQ_Products_ProductCode (ProductCode),
    CONSTRAINT FK_Products_Categories
        FOREIGN KEY (CategoryID) REFERENCES Categories(CategoryID),
    CONSTRAINT CK_Product_SellPrice CHECK (SellPrice >= ImportPrice)
) ENGINE=InnoDB;

CREATE TABLE SupplierProducts (
    SupplierID      INT NOT NULL,
    ProductID       INT NOT NULL,
    SupplyPrice     DECIMAL(18,0) NULL,
    IsPreferred     TINYINT(1) NOT NULL DEFAULT 0,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (SupplierID, ProductID),
    CONSTRAINT FK_SupplierProducts_Suppliers
        FOREIGN KEY (SupplierID) REFERENCES Suppliers(SupplierID),
    CONSTRAINT FK_SupplierProducts_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
) ENGINE=InnoDB;

/* ============================================================
   III. SHIFT
   ============================================================ */
CREATE TABLE Shifts (
    ShiftID         INT AUTO_INCREMENT PRIMARY KEY,
    UserID          INT NOT NULL,
    StartTime       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    EndTime         DATETIME NULL,
    Status          VARCHAR(20) NOT NULL DEFAULT 'OPEN'
                        CHECK (Status IN ('OPEN', 'CLOSED')),

    OpeningCash     DECIMAL(18,0) NOT NULL DEFAULT 0,
    ExpectedCash    DECIMAL(18,0) NULL,
    CountedCash     DECIMAL(18,0) NULL,
    CashDifference  DECIMAL(18,0) NULL,
    OpeningNote     VARCHAR(500) NULL,
    ClosingNote     VARCHAR(500) NULL,
    ClosedBy        INT NULL,
    LastUpdatedAt   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                        ON UPDATE CURRENT_TIMESTAMP,

    OpenUserID INT
        GENERATED ALWAYS AS (
            CASE WHEN Status = 'OPEN' THEN UserID ELSE NULL END
        ) STORED,

    CONSTRAINT FK_Shifts_Users
        FOREIGN KEY (UserID) REFERENCES Users(UserID),

    CONSTRAINT FK_Shifts_ClosedBy
        FOREIGN KEY (ClosedBy) REFERENCES Users(UserID),

    UNIQUE KEY UQ_Shifts_OneOpenPerUser (OpenUserID)
) ENGINE=InnoDB;

/* ============================================================
   CASH TRANSACTIONS TRONG CA
   ============================================================ */
CREATE TABLE ShiftCashTransactions (
    CashTransactionID   BIGINT AUTO_INCREMENT PRIMARY KEY,
    TransactionCode     VARCHAR(40) NOT NULL UNIQUE,
    ShiftID             INT NOT NULL,
    TransactionType     VARCHAR(20) NOT NULL
                            CHECK (TransactionType IN ('CASH_IN', 'CASH_OUT')),
    Amount              DECIMAL(18,0) NOT NULL CHECK (Amount > 0),
    Reason              VARCHAR(255) NOT NULL,
    CreatedBy           INT NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (Status IN ('ACTIVE', 'VOIDED')),

    CONSTRAINT FK_ShiftCashTransactions_Shifts
        FOREIGN KEY (ShiftID) REFERENCES Shifts(ShiftID),

    CONSTRAINT FK_ShiftCashTransactions_Users
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),

    INDEX IX_ShiftCashTransactions_ShiftTime (ShiftID, CreatedAt)
) ENGINE=InnoDB;

/* ============================================================
   IV. HÓA ĐƠN
   ============================================================ */
CREATE TABLE Invoices (
    InvoiceID               INT AUTO_INCREMENT PRIMARY KEY,
    InvoiceCode             VARCHAR(30) NOT NULL UNIQUE,
    ShiftID                 INT NOT NULL,
    CreatedBy               INT NOT NULL,
    CustomerID              INT NULL,
    CreatedAt               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SubTotal                DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountAmount          DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (DiscountAmount >= 0),
    PromotionID             INT NULL,
    PromotionCode           VARCHAR(30) NULL,
    VATRate                 DECIMAL(5,2) NOT NULL DEFAULT 8,
    VATAmount               DECIMAL(18,4) AS (
                                CASE WHEN (SubTotal - DiscountAmount) < 0 THEN 0
                                     ELSE (SubTotal - DiscountAmount) * VATRate / 100
                                END
                            ) STORED,
    TotalAmount             DECIMAL(18,0) NOT NULL DEFAULT 0,
    PaymentMethod           VARCHAR(20) NOT NULL DEFAULT 'CASH'
                                CHECK (PaymentMethod IN ('CASH','BANK_TRANSFER','PAYPAL','CARD')),
    PayPalOrderID           VARCHAR(50) NULL,
    PayPalCaptureID         VARCHAR(50) NULL,
    Status                  VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                                CHECK (Status IN ('ACTIVE', 'CANCELLED')),
    CancelReason            VARCHAR(255) NULL,
    CancelledAt             DATETIME NULL,
    PointsUsed              INT NOT NULL DEFAULT 0 CHECK (PointsUsed >= 0),
    PointsDiscountAmount    DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (PointsDiscountAmount >= 0),
    CONSTRAINT FK_Invoices_Shifts
        FOREIGN KEY (ShiftID) REFERENCES Shifts(ShiftID),
    CONSTRAINT FK_Invoices_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_Invoices_Customers
        FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID),
    CONSTRAINT CK_Invoices_DiscountNotExceedSubTotal
        CHECK (DiscountAmount <= SubTotal)
) ENGINE=InnoDB;

CREATE TABLE InvoiceDetails (
    InvoiceDetailID INT AUTO_INCREMENT PRIMARY KEY,
    InvoiceID       INT NOT NULL,
    ProductID       INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    UnitPrice       DECIMAL(18,0) NOT NULL,
    LineTotal       DECIMAL(18,0) AS (Quantity * UnitPrice) STORED,
    CONSTRAINT FK_InvoiceDetails_Invoices
        FOREIGN KEY (InvoiceID) REFERENCES Invoices(InvoiceID),
    CONSTRAINT FK_InvoiceDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
) ENGINE=InnoDB;

/* ============================================================
   V. ĐỔI / TRẢ HÀNG  (đã có DiscountShare + PointsShare)
   ============================================================ */
CREATE TABLE ReturnExchanges (
    ReturnID            INT AUTO_INCREMENT PRIMARY KEY,
    InvoiceID           INT NOT NULL,
    Type                VARCHAR(20) NOT NULL CHECK (Type IN ('RETURN', 'EXCHANGE')),
    Reason              VARCHAR(255) NOT NULL,
    RejectionReason     VARCHAR(500) NULL,
    TotalValue          DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountShare       DECIMAL(18,0) NOT NULL DEFAULT 0,   -- phần giá trị KM phân bổ
    PointsShare         DECIMAL(18,0) NOT NULL DEFAULT 0,   -- phần giá trị điểm KH phân bổ
    RequiresApproval    TINYINT(1) NOT NULL DEFAULT 0,
    Status              VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (Status IN ('PENDING', 'APPROVED', 'REJECTED')),
    ApprovedBy          INT NULL,
    ApprovedAt          DATETIME NULL,
    CreatedBy           INT NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_ReturnExchanges_Invoices
        FOREIGN KEY (InvoiceID) REFERENCES Invoices(InvoiceID),
    CONSTRAINT FK_ReturnExchanges_ApprovedBy
        FOREIGN KEY (ApprovedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_ReturnExchanges_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    CONSTRAINT CK_Return_ApprovalRequired
        CHECK (Status <> 'APPROVED' OR (ApprovedBy IS NOT NULL AND ApprovedAt IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE ReturnExchangeDetails (
    ReturnDetailID  INT AUTO_INCREMENT PRIMARY KEY,
    ReturnID        INT NOT NULL,
    ProductID       INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    Direction       VARCHAR(10) NOT NULL CHECK (Direction IN ('IN', 'OUT')),
    UnitPrice       DECIMAL(18,0) NOT NULL,
    CONSTRAINT FK_ReturnExchangeDetails_Returns
        FOREIGN KEY (ReturnID) REFERENCES ReturnExchanges(ReturnID),
    CONSTRAINT FK_ReturnExchangeDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
) ENGINE=InnoDB;

/* ============================================================
   VI. NHẬP KHO
   ============================================================ */
CREATE TABLE PurchaseReceipts (
    ReceiptID       INT AUTO_INCREMENT PRIMARY KEY,
    ReceiptCode     VARCHAR(30) NOT NULL UNIQUE,
    SupplierID      INT NOT NULL,
    CreatedBy       INT NOT NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    TotalAmount     DECIMAL(18,0) NOT NULL DEFAULT 0,
    Status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (Status IN ('COMPLETED', 'CANCELLED')),
    CONSTRAINT FK_PurchaseReceipts_Suppliers
        FOREIGN KEY (SupplierID) REFERENCES Suppliers(SupplierID),
    CONSTRAINT FK_PurchaseReceipts_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE TABLE PurchaseReceiptDetails (
    ReceiptDetailID INT AUTO_INCREMENT PRIMARY KEY,
    ReceiptID       INT NOT NULL,
    ProductID       INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    ImportPrice     DECIMAL(18,0) NOT NULL,
    LotNumber       VARCHAR(50) NULL,
    ManufactureDate DATE NULL,
    ExpiryDate      DATE NULL,
    CONSTRAINT FK_PurchaseReceiptDetails_Receipts
        FOREIGN KEY (ReceiptID) REFERENCES PurchaseReceipts(ReceiptID),
    CONSTRAINT FK_PurchaseReceiptDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
) ENGINE=InnoDB;

/* ============================================================
   VII. NGOẠI LỆ + CẢNH BÁO TỒN
   ============================================================ */
CREATE TABLE ExceptionReports (
    ReportID        INT AUTO_INCREMENT PRIMARY KEY,
    CreatedBy       INT NOT NULL,
    Content         VARCHAR(500) NOT NULL,
    Status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (Status IN ('PENDING', 'HANDLED')),
    HandledBy       INT NULL,
    HandledAt       DATETIME NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_ExceptionReports_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_ExceptionReports_HandledBy
        FOREIGN KEY (HandledBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE TABLE StockAlerts (
    AlertID                 INT AUTO_INCREMENT PRIMARY KEY,
    ProductID               INT NOT NULL,
    AlertType               VARCHAR(20) NOT NULL
                                CHECK (AlertType IN ('LOW_STOCK', 'OUT_OF_STOCK')),
    StockAtReport           INT NOT NULL,
    Note                    VARCHAR(255) NULL,
    ReportedBy              INT NULL,
    CreatedAt               DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Status                  VARCHAR(20) NOT NULL DEFAULT 'NEW'
                                CHECK (Status IN ('NEW', 'PLANNED', 'RESOLVED')),
    SeenByInventoryManager  TINYINT(1) NOT NULL DEFAULT 0,
    ResolvedBy              INT NULL,
    ResolvedAt              DATETIME NULL,
    CONSTRAINT FK_StockAlerts_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_StockAlerts_ReportedBy
        FOREIGN KEY (ReportedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_StockAlerts_ResolvedBy
        FOREIGN KEY (ResolvedBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_StockAlerts_Seen ON StockAlerts(SeenByInventoryManager, CreatedAt);
CREATE INDEX IX_StockAlerts_Product_Status ON StockAlerts(ProductID, Status);

/* ============================================================
   VIII. SỔ CÁI TỒN KHO + BATCH
   ============================================================ */
CREATE TABLE InventoryTransactions (
    TransactionID   BIGINT AUTO_INCREMENT PRIMARY KEY,
    ProductID       INT NOT NULL,
    TransactionType VARCHAR(20) NOT NULL
                        CHECK (TransactionType IN (
                            'IMPORT','SALE','SALE_CANCEL',
                            'RETURN_IN','RETURN_OUT','RECONCILE_ADJUST',
                            'DISPOSAL','SUPPLIER_RETURN')),
    Direction       VARCHAR(3) NOT NULL CHECK (Direction IN ('IN','OUT')),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    StockBefore     INT NOT NULL,
    StockAfter      INT NOT NULL,
    RefTable        VARCHAR(30) NOT NULL,
    RefID           INT NOT NULL,
    CreatedBy       INT NOT NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Note            VARCHAR(255) NULL,
    CONSTRAINT FK_InventoryTransactions_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_InventoryTransactions_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_InvTrans_Product_Date ON InventoryTransactions(ProductID, CreatedAt);

CREATE TABLE InventoryBatch (
    BatchID         INT AUTO_INCREMENT PRIMARY KEY,
    BatchCode       VARCHAR(20) NULL,
    LotNumber       VARCHAR(50) NULL,
    ProductID       INT NOT NULL,
    SupplierID      INT NOT NULL,
    ReceiptDetailID INT NULL UNIQUE,
    ManufactureDate DATE NULL,
    ExpiryDate      DATE NULL,
    ImportDate      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ImportPrice     DECIMAL(18,0) NOT NULL CHECK (ImportPrice >= 0),
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    RemainingQty    INT NOT NULL CHECK (RemainingQty >= 0),
    Status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                        CHECK (Status IN ('ACTIVE','EXPIRED','DEPLETED')),
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY UQ_InventoryBatch_BatchCode (BatchCode),
    CONSTRAINT FK_InventoryBatch_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_InventoryBatch_Suppliers
        FOREIGN KEY (SupplierID) REFERENCES Suppliers(SupplierID),
    CONSTRAINT FK_InventoryBatch_ReceiptDetail
        FOREIGN KEY (ReceiptDetailID) REFERENCES PurchaseReceiptDetails(ReceiptDetailID),
    CONSTRAINT CK_Batch_RemainingLEQty CHECK (RemainingQty <= Quantity),
    CONSTRAINT CK_Batch_Dates
        CHECK (ManufactureDate IS NULL OR ExpiryDate IS NULL OR ExpiryDate > ManufactureDate)
) ENGINE=InnoDB;

CREATE INDEX IX_InventoryBatch_FEFO
    ON InventoryBatch(ProductID, ExpiryDate, RemainingQty, Status);

CREATE TABLE InvoiceDetailBatches (
    InvoiceDetailID INT NOT NULL,
    BatchID         INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (InvoiceDetailID, BatchID),
    CONSTRAINT FK_InvoiceDetailBatches_Details
        FOREIGN KEY (InvoiceDetailID) REFERENCES InvoiceDetails(InvoiceDetailID),
    CONSTRAINT FK_InvoiceDetailBatches_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID)
) ENGINE=InnoDB;

CREATE TABLE ReturnExchangeDetailBatches (
    ReturnDetailID  INT NOT NULL,
    BatchID         INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (ReturnDetailID, BatchID),
    CONSTRAINT FK_ReturnExchangeDetailBatches_Details
        FOREIGN KEY (ReturnDetailID) REFERENCES ReturnExchangeDetails(ReturnDetailID),
    CONSTRAINT FK_ReturnExchangeDetailBatches_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID)
) ENGINE=InnoDB;

/* ============================================================
   IX. AUDIT
   ============================================================ */
CREATE TABLE AuditLogs (
    LogID           BIGINT AUTO_INCREMENT PRIMARY KEY,
    UserID          INT NULL,
    Action          VARCHAR(50) NOT NULL,
    TableName       VARCHAR(50) NULL,
    RecordID        INT NULL,
    OldValue        LONGTEXT NULL,
    NewValue        LONGTEXT NULL,
    Detail          LONGTEXT NULL,
    IPAddress       VARCHAR(45) NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_AuditLogs_Users FOREIGN KEY (UserID) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_AuditLogs_User_Date ON AuditLogs(UserID, CreatedAt);

/* ============================================================
   X. ĐỐI CHIẾU KHO + ORDERS
   ============================================================ */
CREATE TABLE StockReconciliation (
    ReconciliationID    INT AUTO_INCREMENT PRIMARY KEY,
    ProductID           INT NOT NULL,
    BatchID        INT NULL,
    SystemStock         INT NOT NULL,
    ActualStock         INT NOT NULL,
    Discrepancy         INT AS (ActualStock - SystemStock) STORED,
    Note                VARCHAR(255) NULL,
    CreatedBy           INT NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Checked        TINYINT(1) NOT NULL DEFAULT 0,
    CheckedBy      INT NULL,
    CheckedAt      DATETIME NULL,
    CONSTRAINT FK_StockReconciliation_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_StockReconciliation_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_StockReconciliation_CheckedBy
        FOREIGN KEY (CheckedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_StockReconciliation_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID)
) ENGINE=InnoDB;

CREATE TABLE Orders (
    OrderID             INT AUTO_INCREMENT PRIMARY KEY,
    OrderCode           VARCHAR(20) NULL,
    CustomerID          INT NULL,
    CustomerName        VARCHAR(150) NOT NULL,
    CustomerEmail       VARCHAR(150) NOT NULL,
    CustomerPhone       VARCHAR(20) NULL,
    ShippingAddress     VARCHAR(255) NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SubTotal            DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountAmount      DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (DiscountAmount >= 0),
    PromotionID         INT NULL,
    PromotionCode       VARCHAR(30) NULL,
    VATRate             DECIMAL(5,2) NOT NULL DEFAULT 8,
    VATAmount           DECIMAL(18,4) AS (
                            CASE WHEN (SubTotal - DiscountAmount) < 0 THEN 0
                                 ELSE (SubTotal - DiscountAmount) * VATRate / 100
                            END
                        ) STORED,
    TotalAmount         DECIMAL(18,0) NOT NULL DEFAULT 0,
    PaymentMethod       VARCHAR(20) NOT NULL DEFAULT 'COD'
                            CHECK (PaymentMethod IN ('COD', 'PAYPAL')),
    PaymentStatus       VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                            CHECK (PaymentStatus IN ('PENDING', 'PAID', 'FAILED')),
    PayPalOrderID       VARCHAR(50) NULL,
    PayPalCaptureID     VARCHAR(50) NULL,
    OrderStatus         VARCHAR(20) NOT NULL DEFAULT 'NEW'
                            CHECK (OrderStatus IN ('NEW', 'CONFIRMED', 'SHIPPING', 'COMPLETED', 'CANCELLED')),
    SeenByAdmin         TINYINT(1) NOT NULL DEFAULT 0,
    CancelReason        VARCHAR(500) NULL,
    CompletedAt         DATETIME NULL,
    InvoiceID           INT NULL,
    CONSTRAINT FK_Orders_Customers
        FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID),
    CONSTRAINT FK_Orders_Invoices
        FOREIGN KEY (InvoiceID) REFERENCES Invoices(InvoiceID),
    CONSTRAINT CK_Orders_DiscountNotExceedSubTotal
        CHECK (DiscountAmount <= SubTotal),
    UNIQUE KEY UX_Orders_InvoiceID (InvoiceID)
) ENGINE=InnoDB;

CREATE TABLE OrderDetails (
    OrderDetailID   INT AUTO_INCREMENT PRIMARY KEY,
    OrderID         INT NOT NULL,
    ProductID       INT NOT NULL,
    ProductName     VARCHAR(150) NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    UnitPrice       DECIMAL(18,0) NOT NULL,
    LineTotal       DECIMAL(18,0) AS (Quantity * UnitPrice) STORED,
    CONSTRAINT FK_OrderDetails_Orders
        FOREIGN KEY (OrderID) REFERENCES Orders(OrderID),
    CONSTRAINT FK_OrderDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID)
) ENGINE=InnoDB;

CREATE INDEX IX_Orders_SeenByAdmin ON Orders(SeenByAdmin, CreatedAt);

CREATE TABLE StoreConfig (
    ConfigKey   VARCHAR(50) PRIMARY KEY,
    ConfigValue VARCHAR(255) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE OrderDetailBatches (
    OrderDetailID   INT NOT NULL,
    BatchID         INT NOT NULL,
    Quantity        INT NOT NULL CHECK (Quantity > 0),
    PRIMARY KEY (OrderDetailID, BatchID),
    CONSTRAINT FK_OrderDetailBatches_Details
        FOREIGN KEY (OrderDetailID) REFERENCES OrderDetails(OrderDetailID),
    CONSTRAINT FK_OrderDetailBatches_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID)
) ENGINE=InnoDB;

CREATE TABLE StockDisposals (
    DisposalID      INT AUTO_INCREMENT PRIMARY KEY,
    DisposalCode    VARCHAR(20) NULL,
    Reason          VARCHAR(20) NOT NULL
                        CHECK (Reason IN ('EXPIRED','DAMAGED','QUALITY','OTHER')),
    Status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                        CHECK (Status IN ('COMPLETED','CANCELLED')),
    TotalLossAmount DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (TotalLossAmount >= 0),
    Note            VARCHAR(500) NULL,
    CreatedBy       INT NOT NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY UQ_StockDisposals_DisposalCode (DisposalCode),
    CONSTRAINT FK_StockDisposals_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_StockDisposals_CreatedAt ON StockDisposals(CreatedAt DESC);

CREATE TABLE StockDisposalDetails (
    DisposalDetailID INT AUTO_INCREMENT PRIMARY KEY,
    DisposalID       INT NOT NULL,
    ProductID        INT NOT NULL,
    BatchID          INT NOT NULL,
    Quantity         INT NOT NULL CHECK (Quantity > 0),
    UnitCost         DECIMAL(18,0) NOT NULL CHECK (UnitCost >= 0),
    LineLossAmount   DECIMAL(18,0) AS (Quantity * UnitCost) STORED,
    CONSTRAINT FK_StockDisposalDetails_Disposals
        FOREIGN KEY (DisposalID) REFERENCES StockDisposals(DisposalID),
    CONSTRAINT FK_StockDisposalDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_StockDisposalDetails_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID),
    CONSTRAINT UQ_Disposal_Batch UNIQUE (DisposalID, BatchID)
) ENGINE=InnoDB;

CREATE INDEX IX_StockDisposalDetails_Product ON StockDisposalDetails(ProductID);

CREATE TABLE SupplierReturns (
    SupplierReturnID    INT AUTO_INCREMENT PRIMARY KEY,
    SupplierReturnCode  VARCHAR(20) NULL,
    SupplierID          INT NOT NULL,
    Reason              VARCHAR(20) NOT NULL
                            CHECK (Reason IN ('DAMAGED','EXPIRED','QUALITY','WRONG_SPEC','OTHER')),
    Status              VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                            CHECK (Status IN ('COMPLETED','CANCELLED')),
    TotalRefundAmount   DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (TotalRefundAmount >= 0),
    Note                VARCHAR(500) NULL,
    CreatedBy           INT NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY UQ_SupplierReturns_Code (SupplierReturnCode),
    CONSTRAINT FK_SupplierReturns_Suppliers
        FOREIGN KEY (SupplierID) REFERENCES Suppliers(SupplierID),
    CONSTRAINT FK_SupplierReturns_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_SupplierReturns_CreatedAt ON SupplierReturns(CreatedAt DESC);
CREATE INDEX IX_SupplierReturns_Supplier ON SupplierReturns(SupplierID);

CREATE TABLE SupplierReturnDetails (
    SupplierReturnDetailID INT AUTO_INCREMENT PRIMARY KEY,
    SupplierReturnID       INT NOT NULL,
    ProductID              INT NOT NULL,
    BatchID                INT NOT NULL,
    Quantity               INT NOT NULL CHECK (Quantity > 0),
    UnitRefundPrice        DECIMAL(18,0) NOT NULL CHECK (UnitRefundPrice >= 0),
    LineRefundAmount       DECIMAL(18,0) AS (Quantity * UnitRefundPrice) STORED,
    CONSTRAINT FK_SupplierReturnDetails_Returns
        FOREIGN KEY (SupplierReturnID) REFERENCES SupplierReturns(SupplierReturnID),
    CONSTRAINT FK_SupplierReturnDetails_Products
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    CONSTRAINT FK_SupplierReturnDetails_Batch
        FOREIGN KEY (BatchID) REFERENCES InventoryBatch(BatchID),
    CONSTRAINT UQ_SupplierReturn_Batch UNIQUE (SupplierReturnID, BatchID)
) ENGINE=InnoDB;

CREATE INDEX IX_SupplierReturnDetails_Product ON SupplierReturnDetails(ProductID);

/* ============================================================
   XI. CHAT
   ============================================================ */
CREATE TABLE ChatConversations (
    ConversationID      INT AUTO_INCREMENT PRIMARY KEY,
    ConversationType    VARCHAR(20) NOT NULL
                            CHECK (ConversationType IN ('CUSTOMER_SUPPORT', 'STAFF_DM')),
    CustomerUserID      INT NULL,
    StaffUserIdA        INT NULL,
    StaffUserIdB        INT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    LastMessageAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    IsClosed            TINYINT(1) NOT NULL DEFAULT 0,
    -- Generated columns để tạo unique index lọc
    OpenSupportKey      INT AS (
                            CASE WHEN ConversationType = 'CUSTOMER_SUPPORT'
                                      AND CustomerUserID IS NOT NULL
                                      AND IsClosed = 0
                                 THEN CustomerUserID ELSE NULL END
                        ) STORED,
    StaffDmKey          VARCHAR(50) AS (
                            CASE WHEN ConversationType = 'STAFF_DM'
                                      AND StaffUserIdA IS NOT NULL
                                      AND StaffUserIdB IS NOT NULL
                                 THEN CONCAT(StaffUserIdA, '-', StaffUserIdB)
                                 ELSE NULL END
                        ) STORED,
    CONSTRAINT FK_ChatConversations_Customer
        FOREIGN KEY (CustomerUserID) REFERENCES Users(UserID),
    CONSTRAINT FK_ChatConversations_StaffA
        FOREIGN KEY (StaffUserIdA) REFERENCES Users(UserID),
    CONSTRAINT FK_ChatConversations_StaffB
        FOREIGN KEY (StaffUserIdB) REFERENCES Users(UserID),
    UNIQUE KEY UX_ChatConv_Customer_Open (OpenSupportKey),
    UNIQUE KEY UX_ChatConv_StaffPair (StaffDmKey)
) ENGINE=InnoDB;

CREATE TABLE ChatMessages (
    MessageID       INT AUTO_INCREMENT PRIMARY KEY,
    ConversationID  INT NOT NULL,
    SenderUserID    INT NOT NULL,
    SenderName      VARCHAR(100) NOT NULL,
    FromStaff       TINYINT(1) NOT NULL DEFAULT 0,
    BodyText        LONGTEXT NULL,
    ImagePath       VARCHAR(500) NULL,
    ImageMime       VARCHAR(50) NULL,
    FilePath        VARCHAR(500) NULL,
    FileName        VARCHAR(255) NULL,
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    IsReadByPeer    TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT FK_ChatMessages_Conversations
        FOREIGN KEY (ConversationID) REFERENCES ChatConversations(ConversationID),
    CONSTRAINT FK_ChatMessages_Sender
        FOREIGN KEY (SenderUserID) REFERENCES Users(UserID)
) ENGINE=InnoDB;

CREATE INDEX IX_ChatMessages_Conversation_Created
    ON ChatMessages (ConversationID, CreatedAt);
CREATE INDEX IX_ChatMessages_Sender
    ON ChatMessages (SenderUserID, CreatedAt DESC);

/* ============================================================
   XII. PROMOTIONS
   ============================================================ */
CREATE TABLE Promotions (
    PromotionID         INT AUTO_INCREMENT PRIMARY KEY,
    Code                VARCHAR(30) NOT NULL UNIQUE,
    Name                VARCHAR(150) NOT NULL,
    DiscountType        VARCHAR(10) NOT NULL
                            CHECK (DiscountType IN ('PERCENT', 'AMOUNT')),
    DiscountValue       DECIMAL(18,0) NOT NULL CHECK (DiscountValue > 0),
    MaxDiscountAmount   DECIMAL(18,0) NULL
                            CHECK (MaxDiscountAmount IS NULL OR MaxDiscountAmount >= 0),
    MinOrderAmount      DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (MinOrderAmount >= 0),
    StartDate           DATE NOT NULL,
    EndDate             DATE NOT NULL,
    UsageLimit          INT NULL CHECK (UsageLimit IS NULL OR UsageLimit > 0),
    UsedCount           INT NOT NULL DEFAULT 0 CHECK (UsedCount >= 0),
    IsActive            TINYINT(1) NOT NULL DEFAULT 1,
    ShowOnBanner        TINYINT(1) NOT NULL DEFAULT 0,
    BannerSortOrder     INT NULL,
    IsDeleted           TINYINT(1) NOT NULL DEFAULT 0,
    DeletedAt           DATETIME NULL,
    CreatedBy           INT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT FK_Promotions_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    CONSTRAINT CK_Promotions_DateRange CHECK (EndDate >= StartDate)
) ENGINE=InnoDB;

CREATE INDEX IX_Promotions_Code ON Promotions(Code);
CREATE INDEX IX_Promotions_ActiveRange ON Promotions(IsActive, StartDate, EndDate);
CREATE INDEX IX_Promotions_Banner
    ON Promotions (ShowOnBanner, IsActive, StartDate, EndDate, BannerSortOrder);

/* FK PromotionID (thêm sau vì Invoices/Orders tạo trước) */
ALTER TABLE Invoices
    ADD CONSTRAINT FK_Invoices_Promotion
        FOREIGN KEY (PromotionID) REFERENCES Promotions(PromotionID);

ALTER TABLE Orders
    ADD CONSTRAINT FK_Orders_Promotion
        FOREIGN KEY (PromotionID) REFERENCES Promotions(PromotionID);

CREATE INDEX IX_Invoices_PromotionID ON Invoices(PromotionID);
CREATE INDEX IX_Orders_PromotionID ON Orders(PromotionID);

-- ============================================================
-- SIMS - Migration_2FA.sql
-- Database: MySQL
-- Chạy sau khi đã có schema SIMS.sql gốc
--
-- Chức năng:
--   1. Lưu cấu hình 2FA của User
--   2. Hỗ trợ NONE / EMAIL / TOTP
--   3. Lưu TOTP Secret đã mã hóa
--   4. Lưu Backup Codes dưới dạng hash
--
-- Có thể chạy lại nhiều lần.
-- ============================================================


-- ============================================================
-- 1. BẢNG UserTwoFactor
-- ============================================================

CREATE TABLE IF NOT EXISTS UserTwoFactor (
    UserID          INT PRIMARY KEY,

    -- Phương thức 2FA:
    -- NONE  = Chưa bật 2FA
    -- EMAIL = Email OTP
    -- TOTP  = TOTP Authenticator
    Method          VARCHAR(10) NOT NULL DEFAULT 'NONE',

    -- TOTP Secret phải được mã hóa trước khi lưu
    TotpSecretEnc   VARCHAR(255) NULL,

    -- 0 = chưa kích hoạt
    -- 1 = 2FA đang hoạt động
    Enabled         BOOLEAN NOT NULL DEFAULT FALSE,

    -- Thời điểm hoàn tất đăng ký 2FA
    EnrolledAt      DATETIME NULL,

    -- Thời điểm cập nhật gần nhất
    UpdatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
                    ON UPDATE CURRENT_TIMESTAMP,

    -- Foreign Key tới Users
    CONSTRAINT FK_UserTwoFactor_User
        FOREIGN KEY (UserID)
        REFERENCES Users(UserID),

    -- Chỉ cho phép 3 phương thức
    CONSTRAINT CK_UserTwoFactor_Method
        CHECK (Method IN ('NONE', 'EMAIL', 'TOTP'))
);


-- ============================================================
-- 2. BẢNG UserTwoFactorBackupCodes
-- ============================================================

CREATE TABLE IF NOT EXISTS UserTwoFactorBackupCodes (
    BackupCodeID    INT AUTO_INCREMENT PRIMARY KEY,

    -- User sở hữu backup code
    UserID          INT NOT NULL,

    -- Không lưu backup code dạng plaintext
    -- Chỉ lưu hash
    CodeHash        VARCHAR(255) NOT NULL,

    -- NULL = chưa sử dụng
    -- Có giá trị = đã sử dụng
    UsedAt          DATETIME NULL,

    -- Thời điểm tạo backup code
    CreatedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- Foreign Key tới Users
    CONSTRAINT FK_UserTwoFactorBackupCodes_User
        FOREIGN KEY (UserID)
        REFERENCES Users(UserID)
);


-- ============================================================
-- 3. KIỂM TRA INDEX
-- ============================================================
--
-- MySQL/InnoDB thường tự tạo index cho UserID khi tạo
-- FOREIGN KEY nếu chưa có index phù hợp.
--
-- Vì vậy KHÔNG dùng:
--
-- CREATE INDEX IF NOT EXISTS ...
--
-- vì MySQL của bạn không hỗ trợ cú pháp này.
--
-- Có thể kiểm tra index bằng:
--
-- SHOW INDEX FROM UserTwoFactorBackupCodes;
--
-- ============================================================


-- ============================================================
-- 4. KIỂM TRA BẢNG SAU KHI MIGRATION
-- ============================================================

SHOW CREATE TABLE UserTwoFactor;

SHOW CREATE TABLE UserTwoFactorBackupCodes;

SHOW INDEX FROM UserTwoFactorBackupCodes;