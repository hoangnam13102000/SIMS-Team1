USE SIMS_DB;
GO

/* ============================================================
   1. Cho phép hóa đơn online không thuộc ca bán hàng tại quầy
   ShiftID = NULL nghĩa là hóa đơn được tạo từ đơn online.
   ============================================================ */

ALTER TABLE Invoices
ALTER COLUMN ShiftID INT NULL;
GO


/* ============================================================
   2. Bảng đơn hàng online
   ============================================================ */

IF OBJECT_ID('dbo.Orders', 'U') IS NULL
BEGIN
    CREATE TABLE Orders (
        OrderID          INT IDENTITY(1,1) PRIMARY KEY,

        OrderCode AS (
            'DH_' + RIGHT(
                '00000000' + CAST(OrderID AS VARCHAR(20)),
                8
            )
        ) PERSISTED,

        CustomerID       INT NOT NULL,
        InvoiceID        INT NULL,

        ReceiverName     NVARCHAR(100) NOT NULL,
        ReceiverPhone    VARCHAR(20) NULL,
        ReceiverEmail    VARCHAR(100) NOT NULL,
        ShippingAddress  NVARCHAR(500) NOT NULL,

        PaymentMethod    VARCHAR(20) NOT NULL
            CONSTRAINT CK_Orders_PaymentMethod
            CHECK (PaymentMethod IN ('COD', 'PAYPAL')),

        PaymentStatus    VARCHAR(30) NOT NULL
            CONSTRAINT DF_Orders_PaymentStatus
            DEFAULT 'UNPAID',

        OrderStatus      VARCHAR(20) NOT NULL
            CONSTRAINT DF_Orders_OrderStatus
            DEFAULT 'PENDING',

        SubTotal         DECIMAL(18,0) NOT NULL
            CONSTRAINT DF_Orders_SubTotal
            DEFAULT 0,

        ShippingFee      DECIMAL(18,0) NOT NULL
            CONSTRAINT DF_Orders_ShippingFee
            DEFAULT 0,

        DiscountAmount   DECIMAL(18,0) NOT NULL
            CONSTRAINT DF_Orders_Discount
            DEFAULT 0,

        TotalAmount AS (
            SubTotal + ShippingFee - DiscountAmount
        ) PERSISTED,

        CancelReason     NVARCHAR(255) NULL,
        CancelledBy      INT NULL,
        CancelledAt      DATETIME NULL,

        CreatedAt        DATETIME NOT NULL
            CONSTRAINT DF_Orders_CreatedAt
            DEFAULT GETDATE(),

        UpdatedAt        DATETIME NULL,

        CONSTRAINT UQ_Orders_OrderCode
            UNIQUE (OrderCode),

        CONSTRAINT FK_Orders_Customer
            FOREIGN KEY (CustomerID)
            REFERENCES Customers(CustomerID),

        CONSTRAINT FK_Orders_Invoice
            FOREIGN KEY (InvoiceID)
            REFERENCES Invoices(InvoiceID),

        CONSTRAINT FK_Orders_CancelledBy
            FOREIGN KEY (CancelledBy)
            REFERENCES Users(UserID),

        CONSTRAINT CK_Orders_OrderStatus
            CHECK (
                OrderStatus IN (
                    'PENDING',
                    'CONFIRMED',
                    'SHIPPING',
                    'COMPLETED',
                    'CANCELLED'
                )
            ),

        CONSTRAINT CK_Orders_PaymentStatus
            CHECK (
                PaymentStatus IN (
                    'UNPAID',
                    'PENDING',
                    'PAID',
                    'FAILED',
                    'CANCELLED',
                    'REFUND_PENDING',
                    'REFUNDED'
                )
            ),

        CONSTRAINT CK_Orders_Amounts
            CHECK (
                SubTotal >= 0
                AND ShippingFee >= 0
                AND DiscountAmount >= 0
                AND DiscountAmount <= SubTotal + ShippingFee
            ),

        CONSTRAINT CK_Orders_CancelInformation
            CHECK (
                OrderStatus <> 'CANCELLED'
                OR (
                    CancelReason IS NOT NULL
                    AND CancelledAt IS NOT NULL
                    AND CancelledBy IS NOT NULL
                )
            )
    );
END;
GO


/* ============================================================
   3. Mỗi hóa đơn chỉ thuộc tối đa một đơn hàng
   Dùng filtered index vì InvoiceID có thể NULL.
   ============================================================ */

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'UX_Orders_InvoiceID'
      AND object_id = OBJECT_ID('dbo.Orders')
)
BEGIN
    CREATE UNIQUE INDEX UX_Orders_InvoiceID
    ON Orders(InvoiceID)
    WHERE InvoiceID IS NOT NULL;
END;
GO


/* ============================================================
   4. Bảng chi tiết đơn hàng
   Lưu snapshot tên, mã, đơn giá để dữ liệu đơn cũ không thay đổi
   khi sản phẩm được sửa tên hoặc giá.
   ============================================================ */

IF OBJECT_ID('dbo.OrderDetails', 'U') IS NULL
BEGIN
    CREATE TABLE OrderDetails (
        OrderDetailID       INT IDENTITY(1,1) PRIMARY KEY,
        OrderID             INT NOT NULL,
        ProductID           INT NOT NULL,

        ProductCodeSnapshot NVARCHAR(30) NOT NULL,
        ProductNameSnapshot NVARCHAR(150) NOT NULL,

        Quantity            INT NOT NULL,
        UnitPrice           DECIMAL(18,0) NOT NULL,

        LineTotal AS (
            Quantity * UnitPrice
        ) PERSISTED,

        CONSTRAINT FK_OrderDetails_Order
            FOREIGN KEY (OrderID)
            REFERENCES Orders(OrderID),

        CONSTRAINT FK_OrderDetails_Product
            FOREIGN KEY (ProductID)
            REFERENCES Products(ProductID),

        CONSTRAINT CK_OrderDetails_Quantity
            CHECK (Quantity > 0),

        CONSTRAINT CK_OrderDetails_UnitPrice
            CHECK (UnitPrice >= 0),

        CONSTRAINT UQ_OrderDetails_Order_Product
            UNIQUE (OrderID, ProductID)
    );
END;
GO


/* ============================================================
   5. Index hỗ trợ trang "Đơn hàng của tôi"
   ============================================================ */

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_Orders_Customer_CreatedAt'
      AND object_id = OBJECT_ID('dbo.Orders')
)
BEGIN
    CREATE INDEX IX_Orders_Customer_CreatedAt
    ON Orders(CustomerID, CreatedAt DESC);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_Orders_Customer_Status'
      AND object_id = OBJECT_ID('dbo.Orders')
)
BEGIN
    CREATE INDEX IX_Orders_Customer_Status
    ON Orders(CustomerID, OrderStatus);
END;
GO

IF NOT EXISTS (
    SELECT 1
    FROM sys.indexes
    WHERE name = 'IX_OrderDetails_OrderID'
      AND object_id = OBJECT_ID('dbo.OrderDetails')
)
BEGIN
    CREATE INDEX IX_OrderDetails_OrderID
    ON OrderDetails(OrderID);
END;
GO


/* ============================================================
   6. Không cho xóa vật lý đơn hàng
   Muốn hủy phải chuyển trạng thái sang CANCELLED.
   ============================================================ */

CREATE OR ALTER TRIGGER trg_Orders_BlockDelete
ON Orders
INSTEAD OF DELETE
AS
BEGIN
    SET NOCOUNT ON;

    RAISERROR(
        N'Không được xóa vĩnh viễn đơn hàng. Hãy sử dụng chức năng hủy đơn.',
        16,
        1
    );
END;
GO


/* ============================================================
   7. Kiểm tra kết quả
   ============================================================ */

SELECT
    TABLE_NAME
FROM INFORMATION_SCHEMA.TABLES
WHERE TABLE_NAME IN ('Orders', 'OrderDetails');
GO