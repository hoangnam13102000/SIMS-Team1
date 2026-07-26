/* ============================================================
   IX. TRIGGER - HIEN THUC HOA NGUYEN TAC NGHIEP VU (R1, R3, R4, R5)
   (R2 da lam bang CHECK CONSTRAINT o tren)
   ============================================================ */
USE SIMS_DB;
GO
-- R1: khong ban khi het hang; neu dat vuot ton thi chi ban toi da bang ton hien co, roi tru kho
CREATE TRIGGER trg_InvoiceDetails_CheckStock
ON InvoiceDetails
INSTEAD OF INSERT
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM inserted i JOIN Products p ON p.ProductID = i.ProductID WHERE p.Stock = 0)
    BEGIN
        RAISERROR(N'San pham da het hang, khong the tao hoa don.', 16, 1);
        RETURN;
    END

    INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice)
    SELECT
        i.InvoiceID,
        i.ProductID,
        CASE WHEN i.Quantity > p.Stock THEN p.Stock ELSE i.Quantity END,  -- cap toi da bang ton
        i.UnitPrice
    FROM inserted i
    JOIN Products p ON p.ProductID = i.ProductID;

    UPDATE p
    SET p.Stock = p.Stock - ins.QtyDeducted
    FROM Products p
    JOIN (
        SELECT i.ProductID, CASE WHEN i.Quantity > p2.Stock THEN p2.Stock ELSE i.Quantity END AS QtyDeducted
        FROM inserted i JOIN Products p2 ON p2.ProductID = i.ProductID
    ) ins ON ins.ProductID = p.ProductID;
END;
GO
-- Sửa trg_InvoiceDetails_CheckStock: thêm log sau đoạn UPDATE Products
ALTER TRIGGER trg_InvoiceDetails_CheckStock
ON InvoiceDetails
INSTEAD OF INSERT
AS
BEGIN
    SET NOCOUNT ON;

    IF EXISTS (SELECT 1 FROM inserted i JOIN Products p ON p.ProductID = i.ProductID WHERE p.Stock = 0)
    BEGIN
        RAISERROR(N'San pham da het hang, khong the tao hoa don.', 16, 1);
        RETURN;
    END

    DECLARE @Deducted TABLE (ProductID INT, InvoiceID INT, UnitPrice DECIMAL(18,0),
                              QtyDeducted INT, StockBefore INT, CreatedBy INT);

    INSERT INTO @Deducted
    SELECT i.ProductID, i.InvoiceID, i.UnitPrice,
           CASE WHEN i.Quantity > p.Stock THEN p.Stock ELSE i.Quantity END,
           p.Stock,
           inv.CreatedBy
    FROM inserted i
    JOIN Products p ON p.ProductID = i.ProductID
    JOIN Invoices inv ON inv.InvoiceID = i.InvoiceID;

    INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice)
    SELECT InvoiceID, ProductID, QtyDeducted, UnitPrice FROM @Deducted;

    UPDATE p
    SET p.Stock = p.Stock - d.QtyDeducted
    FROM Products p JOIN @Deducted d ON d.ProductID = p.ProductID;

    INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                        StockBefore, StockAfter, RefTable, RefID, CreatedBy)
    SELECT ProductID, 'SALE', 'OUT', QtyDeducted, StockBefore, StockBefore - QtyDeducted,
           'Invoices', InvoiceID, CreatedBy
    FROM @Deducted;
END;
GO


-- R3: khong cho DELETE vat ly hoa don / phieu nhap - bat buoc dung UPDATE Status = 'CANCELLED'
CREATE TRIGGER trg_Invoices_BlockDelete
ON Invoices
INSTEAD OF DELETE
AS
BEGIN
    RAISERROR(N'Khong duoc xoa vinh vien hoa don. Hay cap nhat Status = ''CANCELLED'' kem ly do.', 16, 1);
END;
GO

CREATE TRIGGER trg_PurchaseReceipts_BlockDelete
ON PurchaseReceipts
INSTEAD OF DELETE
AS
BEGIN
    RAISERROR(N'Khong duoc xoa vinh vien phieu nhap kho.', 16, 1);
END;
GO

-- R4: chi cho huy hoa don trong cung ngay tao; huy xong hoan lai ton kho
CREATE TRIGGER trg_Invoices_CancelSameDayOnly
ON Invoices
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(Status)
    BEGIN
        IF EXISTS (
            SELECT 1 FROM inserted i JOIN deleted d ON i.InvoiceID = d.InvoiceID
            WHERE i.Status = 'CANCELLED' AND d.Status <> 'CANCELLED'
              AND CAST(i.CreatedAt AS DATE) <> CAST(GETDATE() AS DATE)
        )
        BEGIN
            RAISERROR(N'Chi duoc huy hoa don trong cung ngay tao.', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN;
        END

        -- hoan lai ton kho cho cac hoa don vua chuyen sang CANCELLED
        UPDATE p
        SET p.Stock = p.Stock + d.Quantity
        FROM Products p
        JOIN InvoiceDetails d ON d.ProductID = p.ProductID
        JOIN inserted i ON i.InvoiceID = d.InvoiceID
        JOIN deleted del ON del.InvoiceID = d.InvoiceID
        WHERE i.Status = 'CANCELLED' AND del.Status <> 'CANCELLED';
    END
END;
GO

-- R5: tu dong khoa tai khoan sau 5 lan dang nhap sai lien tiep
CREATE TRIGGER trg_Users_AutoLock
ON Users
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(FailedLoginCount)
    BEGIN
        UPDATE u
        SET u.IsLocked = 1
        FROM Users u JOIN inserted i ON u.UserID = i.UserID
        WHERE i.FailedLoginCount >= 5 AND u.IsLocked = 0;
    END
END;
GO

-- Trigger mới cho PurchaseReceiptDetails (chưa có trigger nào cộng kho + log)
CREATE TRIGGER trg_PurchaseReceiptDetails_Insert
ON PurchaseReceiptDetails
AFTER INSERT
AS
BEGIN
    SET NOCOUNT ON;
    INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                        StockBefore, StockAfter, RefTable, RefID, CreatedBy)
    SELECT i.ProductID, 'IMPORT', 'IN', i.Quantity,
           p.Stock, p.Stock + i.Quantity,
           'PurchaseReceipts', i.ReceiptID, r.CreatedBy
    FROM inserted i
    JOIN Products p ON p.ProductID = i.ProductID
    JOIN PurchaseReceipts r ON r.ReceiptID = i.ReceiptID;

    UPDATE p SET p.Stock = p.Stock + i.Quantity
    FROM Products p JOIN inserted i ON i.ProductID = p.ProductID;
END;
GO


-- Trigger mới cho ReturnExchangeDetails: cộng/trừ kho + log khi return được APPROVED
CREATE TRIGGER trg_ReturnExchange_ApprovedStock
ON ReturnExchanges
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(Status)
    BEGIN
        -- cong kho cho hang khach tra (IN)
        INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                            StockBefore, StockAfter, RefTable, RefID, CreatedBy)
        SELECT d.ProductID, 'RETURN_IN', 'IN', d.Quantity,
               p.Stock, p.Stock + d.Quantity, 'ReturnExchanges', i.ReturnID, i.CreatedBy
        FROM inserted i
        JOIN deleted de ON de.ReturnID = i.ReturnID
        JOIN ReturnExchangeDetails d ON d.ReturnID = i.ReturnID AND d.Direction = 'IN'
        JOIN Products p ON p.ProductID = d.ProductID
        WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED';

        UPDATE p SET p.Stock = p.Stock + d.Quantity
        FROM Products p
        JOIN ReturnExchangeDetails d ON d.ProductID = p.ProductID AND d.Direction='IN'
        JOIN inserted i ON i.ReturnID = d.ReturnID
        JOIN deleted de ON de.ReturnID = i.ReturnID
        WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED';

        -- tru kho cho hang doi moi giao (OUT)
        INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                            StockBefore, StockAfter, RefTable, RefID, CreatedBy)
        SELECT d.ProductID, 'RETURN_OUT', 'OUT', d.Quantity,
               p.Stock, p.Stock - d.Quantity, 'ReturnExchanges', i.ReturnID, i.CreatedBy
        FROM inserted i
        JOIN deleted de ON de.ReturnID = i.ReturnID
        JOIN ReturnExchangeDetails d ON d.ReturnID = i.ReturnID AND d.Direction = 'OUT'
        JOIN Products p ON p.ProductID = d.ProductID
        WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED';

        UPDATE p SET p.Stock = p.Stock - d.Quantity
        FROM Products p
        JOIN ReturnExchangeDetails d ON d.ProductID = p.ProductID AND d.Direction='OUT'
        JOIN inserted i ON i.ReturnID = d.ReturnID
        JOIN deleted de ON de.ReturnID = i.ReturnID
        WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED';
    END
END;
GO

ALTER TRIGGER trg_Invoices_CancelSameDayOnly
ON Invoices
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(Status)
    BEGIN
        IF EXISTS (
            SELECT 1
            FROM inserted i
            JOIN deleted d ON i.InvoiceID = d.InvoiceID
            JOIN Shifts s ON s.ShiftID = i.ShiftID
            WHERE i.Status = 'CANCELLED' AND d.Status <> 'CANCELLED'
              AND (
                    CAST(i.CreatedAt AS DATE) <> CAST(GETDATE() AS DATE)  -- khac ngay
                    OR s.Status = 'CLOSED'                                 -- hoac ca da dong
                  )
        )
        BEGIN
            RAISERROR(N'Chỉ được hủy hóa đơn trong cùng ca bán hàng đang mở và trong ngày tạo.', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN;
        END

        -- hoan lai ton kho cho cac hoa don vua chuyen sang CANCELLED
        UPDATE p
        SET p.Stock = p.Stock + d.Quantity
        FROM Products p
        JOIN InvoiceDetails d ON d.ProductID = p.ProductID
        JOIN inserted i ON i.InvoiceID = d.InvoiceID
        JOIN deleted del ON del.InvoiceID = d.InvoiceID
        WHERE i.Status = 'CANCELLED' AND del.Status <> 'CANCELLED';
    END
END;
GO


ALTER TRIGGER trg_ReturnExchange_ApprovedStock
ON ReturnExchanges
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF UPDATE(Status)
    BEGIN
        -- (giữ nguyên các đoạn cộng/trừ kho IN/OUT như cũ) ...

        -- MOI: dieu chinh lai hoa don goc theo net thay doi (OUT - IN)
        ;WITH ApprovedNow AS (
            SELECT i.ReturnID, i.InvoiceID
            FROM inserted i
            JOIN deleted de ON de.ReturnID = i.ReturnID
            WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED'
        ),
        NetAdjust AS (
            SELECT a.InvoiceID,
                   SUM(CASE WHEN d.Direction = 'OUT' THEN d.Quantity * d.UnitPrice
                            WHEN d.Direction = 'IN'  THEN -d.Quantity * d.UnitPrice END) AS NetChange
            FROM ApprovedNow a
            JOIN ReturnExchangeDetails d ON d.ReturnID = a.ReturnID
            GROUP BY a.InvoiceID
        )
        UPDATE inv
        SET inv.SubTotal    = inv.SubTotal + n.NetChange,
            inv.TotalAmount = (inv.SubTotal + n.NetChange)
                              + ((inv.SubTotal + n.NetChange) * inv.VATRate / 100)
        FROM Invoices inv
        JOIN NetAdjust n ON n.InvoiceID = inv.InvoiceID;
    END
END;
GO