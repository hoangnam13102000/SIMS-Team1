/* ============================================================
   IX. TRIGGER - HIEN THUC HOA NGUYEN TAC NGHIEP VU (R1, R3, R4, R5)
   (R2 da lam bang CHECK CONSTRAINT trong SIMS.sql)

   File nay la BAN CUOI CUNG, da gop toan bo cac lan sua (FEFO,
   InventoryBatch, huy hoa don hoan dung lo, dieu chinh hoa don khi
   doi/tra duoc duyet...) - KHONG con dang CREATE roi ALTER lai nhieu
   lan nhu file cu. Truoc khi chay file nay, phai da chay xong buoc
   "DROP TRIGGER IF EXISTS ..." (Buoc 1) va SIMS.sql (dung schema co
   InventoryBatch, InvoiceDetailBatches, PurchaseReceiptDetails da co
   LotNumber/ManufactureDate/ExpiryDate, Shifts...).
   ============================================================ */
USE SIMS_DB;
GO

-- ---------------------------------------------------------------
-- R1 + FEFO: khong ban khi het hang; ban theo nguyen tac FEFO (lo
-- het han som nhat duoc xuat truoc), tru trai nhieu lo neu can, loai
-- han lo da qua han su dung khoi dien ban.
-- ---------------------------------------------------------------
CREATE TRIGGER trg_InvoiceDetails_CheckStock
ON InvoiceDetails
INSTEAD OF INSERT
AS
BEGIN
    SET NOCOUNT ON;

    -- R1: chan neu san pham het hang (chi tinh cac lo con han, chua het han)
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE ISNULL((SELECT SUM(b.RemainingQty) FROM InventoryBatch b
                       WHERE b.ProductID = i.ProductID AND b.Status = 'ACTIVE'
                         AND (b.ExpiryDate IS NULL OR b.ExpiryDate >= CAST(GETDATE() AS DATE))), 0) = 0
    )
    BEGIN
        RAISERROR(N'San pham da het hang, khong the tao hoa don.', 16, 1);
        RETURN;
    END

    DECLARE @InvoiceID INT, @ProductID INT, @Quantity INT, @UnitPrice DECIMAL(18,0), @CreatedBy INT;
    DECLARE @Available INT, @QtyToSell INT, @StockBefore INT, @NewDetailID INT;
    DECLARE @Remaining INT, @BatchID INT, @BatchRemain INT, @Take INT;

    DECLARE line_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT i.InvoiceID, i.ProductID, i.Quantity, i.UnitPrice, inv.CreatedBy
        FROM inserted i
        JOIN Invoices inv ON inv.InvoiceID = i.InvoiceID;

    OPEN line_cursor;
    FETCH NEXT FROM line_cursor INTO @InvoiceID, @ProductID, @Quantity, @UnitPrice, @CreatedBy;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        -- Available phai khop dieu kien loc cua batch_cursor ben duoi (loai lo het han)
        SELECT @Available = ISNULL(SUM(RemainingQty), 0) FROM InventoryBatch
        WHERE ProductID = @ProductID AND Status = 'ACTIVE'
          AND (ExpiryDate IS NULL OR ExpiryDate >= CAST(GETDATE() AS DATE));

        SET @QtyToSell = CASE WHEN @Quantity > @Available THEN @Available ELSE @Quantity END;
        SELECT @StockBefore = Stock FROM Products WHERE ProductID = @ProductID;

        INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice)
        VALUES (@InvoiceID, @ProductID, @QtyToSell, @UnitPrice);
        SET @NewDetailID = SCOPE_IDENTITY();

        -- FEFO: uu tien HSD gan nhat, hang khong HSD (NULL) ban sau cung, lo het han bi loai
        SET @Remaining = @QtyToSell;

        DECLARE batch_cursor CURSOR LOCAL FAST_FORWARD FOR
            SELECT BatchID, RemainingQty FROM InventoryBatch
            WHERE ProductID = @ProductID AND Status = 'ACTIVE' AND RemainingQty > 0
              AND (ExpiryDate IS NULL OR ExpiryDate >= CAST(GETDATE() AS DATE))
            ORDER BY ISNULL(ExpiryDate, '9999-12-31'), BatchID;

        OPEN batch_cursor;
        FETCH NEXT FROM batch_cursor INTO @BatchID, @BatchRemain;

        WHILE @@FETCH_STATUS = 0 AND @Remaining > 0
        BEGIN
            SET @Take = CASE WHEN @BatchRemain > @Remaining THEN @Remaining ELSE @BatchRemain END;

            UPDATE InventoryBatch
            SET RemainingQty = RemainingQty - @Take,
                Status = CASE WHEN RemainingQty - @Take = 0 THEN 'DEPLETED' ELSE Status END
            WHERE BatchID = @BatchID;

            INSERT INTO InvoiceDetailBatches (InvoiceDetailID, BatchID, Quantity)
            VALUES (@NewDetailID, @BatchID, @Take);

            SET @Remaining -= @Take;
            FETCH NEXT FROM batch_cursor INTO @BatchID, @BatchRemain;
        END

        CLOSE batch_cursor;
        DEALLOCATE batch_cursor;

        UPDATE Products SET Stock = Stock - @QtyToSell WHERE ProductID = @ProductID;

        INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                            StockBefore, StockAfter, RefTable, RefID, CreatedBy)
        VALUES (@ProductID, 'SALE', 'OUT', @QtyToSell, @StockBefore, @StockBefore - @QtyToSell,
                'Invoices', @InvoiceID, @CreatedBy);

        FETCH NEXT FROM line_cursor INTO @InvoiceID, @ProductID, @Quantity, @UnitPrice, @CreatedBy;
    END

    CLOSE line_cursor;
    DEALLOCATE line_cursor;
END;
GO


-- ---------------------------------------------------------------
-- R3: khong cho DELETE vat ly hoa don / phieu nhap - bat buoc dung
-- UPDATE Status = 'CANCELLED' kem ly do.
-- ---------------------------------------------------------------
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


-- ---------------------------------------------------------------
-- R4: chi cho huy hoa don trong cung ca ban hang dang mo va trong
-- ngay tao; huy xong hoan lai DUNG TUNG LO da tru (qua
-- InvoiceDetailBatches) thay vi cong thang vao Products.Stock chung
-- chung, tranh sai lech du lieu lo hang.
-- ---------------------------------------------------------------
CREATE TRIGGER trg_Invoices_CancelSameDayOnly
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
            RAISERROR(N'Chi duoc huy hoa don trong cung ca ban hang dang mo va trong ngay tao.', 16, 1);
            ROLLBACK TRANSACTION;
            RETURN;
        END

        -- Cac hoa don vua chuyen sang CANCELLED trong lan UPDATE nay
        DECLARE @CancelledNow TABLE (InvoiceID INT PRIMARY KEY);
        INSERT INTO @CancelledNow (InvoiceID)
        SELECT i.InvoiceID
        FROM inserted i
        JOIN deleted d ON d.InvoiceID = i.InvoiceID
        WHERE i.Status = 'CANCELLED' AND d.Status <> 'CANCELLED';

        IF EXISTS (SELECT 1 FROM @CancelledNow)
        BEGIN
            -- Hoan lai dung tung lo da tru (theo InvoiceDetailBatches)
            DECLARE @BatchID INT, @Qty INT, @ProductID INT, @InvoiceID INT;

            DECLARE restore_cursor CURSOR LOCAL FAST_FORWARD FOR
                SELECT idb.BatchID, idb.Quantity, b.ProductID, d.InvoiceID
                FROM InvoiceDetailBatches idb
                JOIN InvoiceDetails d ON d.InvoiceDetailID = idb.InvoiceDetailID
                JOIN InventoryBatch b ON b.BatchID = idb.BatchID
                JOIN @CancelledNow c ON c.InvoiceID = d.InvoiceID;

            OPEN restore_cursor;
            FETCH NEXT FROM restore_cursor INTO @BatchID, @Qty, @ProductID, @InvoiceID;

            WHILE @@FETCH_STATUS = 0
            BEGIN
                -- cong lai vao dung lo, dua lo tu DEPLETED ve lai ACTIVE neu can
                UPDATE InventoryBatch
                SET RemainingQty = RemainingQty + @Qty,
                    Status = CASE WHEN Status = 'DEPLETED' THEN 'ACTIVE' ELSE Status END
                WHERE BatchID = @BatchID;

                FETCH NEXT FROM restore_cursor INTO @BatchID, @Qty, @ProductID, @InvoiceID;
            END

            CLOSE restore_cursor;
            DEALLOCATE restore_cursor;

            -- Dong bo lai Products.Stock (cache) theo tung san pham bi anh huong
            ;WITH Affected AS (
                SELECT DISTINCT b.ProductID
                FROM InvoiceDetailBatches idb
                JOIN InvoiceDetails d ON d.InvoiceDetailID = idb.InvoiceDetailID
                JOIN InventoryBatch b ON b.BatchID = idb.BatchID
                JOIN @CancelledNow c ON c.InvoiceID = d.InvoiceID
            )
            UPDATE p
            SET p.Stock = (SELECT ISNULL(SUM(RemainingQty), 0) FROM InventoryBatch
                           WHERE ProductID = p.ProductID AND Status = 'ACTIVE')
            FROM Products p
            JOIN Affected a ON a.ProductID = p.ProductID;

            -- Log SALE_CANCEL
            INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                                StockBefore, StockAfter, RefTable, RefID, CreatedBy)
            SELECT d.ProductID, 'SALE_CANCEL', 'IN', d.Quantity,
                   p.Stock - d.Quantity, p.Stock,
                   'Invoices', c.InvoiceID, i.CreatedBy
            FROM @CancelledNow c
            JOIN Invoices i ON i.InvoiceID = c.InvoiceID
            JOIN InvoiceDetails d ON d.InvoiceID = c.InvoiceID
            JOIN Products p ON p.ProductID = d.ProductID;
        END
    END
END;
GO


-- ---------------------------------------------------------------
-- R5: tu dong khoa tai khoan sau 5 lan dang nhap sai lien tiep
-- ---------------------------------------------------------------
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


-- ---------------------------------------------------------------
-- Nhap kho: moi dong PurchaseReceiptDetails sinh dung 1 lo
-- InventoryBatch moi + cong kho Products.Stock + log IMPORT.
-- Ngoai ra: neu day la LO DAU TIEN cua san pham (chua tung co dong
-- nao trong InventoryBatch truoc do), dong bo Products.ImportPrice
-- theo dung gia nhap cua lo nay (ADMIN khong can nhap tay luc tao SP
-- nua - form Them san pham de ImportPrice = 0 cho toi khi co lo dau
-- tien). Trigger trg_Products_SyncSellPrice se tu chay tiep de tinh
-- lai SellPrice ngay sau buoc UPDATE ImportPrice nay.
-- ---------------------------------------------------------------
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

    -- Xac dinh truoc (truoc khi INSERT InventoryBatch ben duoi) nhung
    -- ProductID nao dang nhap LO DAU TIEN trong doi - lay dung 1 dong
    -- (gia nhap dau tien) cho moi san pham neu 1 phieu nhap co nhieu
    -- dong cung 1 san pham.
    SELECT i.ProductID, i.ImportPrice
    INTO #FirstBatchImportPrice
    FROM (
        SELECT i.ProductID, i.ImportPrice,
               ROW_NUMBER() OVER (PARTITION BY i.ProductID ORDER BY i.ReceiptDetailID) AS rn
        FROM inserted i
        WHERE NOT EXISTS (SELECT 1 FROM InventoryBatch b WHERE b.ProductID = i.ProductID)
    ) i
    WHERE i.rn = 1;

    -- moi lan nhap = 1 lo moi
    INSERT INTO InventoryBatch (ProductID, SupplierID, ReceiptDetailID, LotNumber,
                                 ManufactureDate, ExpiryDate, ImportPrice, Quantity, RemainingQty)
    SELECT i.ProductID, r.SupplierID, i.ReceiptDetailID, i.LotNumber,
           i.ManufactureDate, i.ExpiryDate, i.ImportPrice, i.Quantity, i.Quantity
    FROM inserted i
    JOIN PurchaseReceipts r ON r.ReceiptID = i.ReceiptID;

    UPDATE p SET p.Stock = p.Stock + i.Quantity
    FROM Products p JOIN inserted i ON i.ProductID = p.ProductID;

    -- QUAN TRONG: phai cap nhat ImportPrice VA SellPrice CUNG 1 cau
    -- UPDATE, khong duoc de rieng 2 buoc - vi CHECK constraint
    -- CK_Product_SellPrice (SellPrice >= ImportPrice) duoc kiem tra
    -- ngay khi cau UPDATE nay chay xong, TRUOC khi trigger
    -- trg_Products_SyncSellPrice (AFTER UPDATE) kip tinh lai SellPrice.
    -- Neu AutoPrice = 1: tinh SellPrice = ImportPrice moi + chenh lech
    -- hieu luc luon trong cau nay. Neu AutoPrice = 0 (da khoa gia tay)
    -- ma ImportPrice lo dau tien lai cao hon SellPrice dang khoa thi
    -- BO QUA dong bo cho san pham do (giu ImportPrice cu) de khong lam
    -- hong phieu nhap kho - ADMIN tu dieu chinh gia thu cong sau.
    UPDATE p
    SET p.ImportPrice = fb.ImportPrice,
        p.SellPrice = CASE WHEN p.AutoPrice = 1
                            THEN fb.ImportPrice + ISNULL(p.Margin, dbo.fn_GetDefaultMargin())
                            ELSE p.SellPrice END
    FROM Products p
    JOIN #FirstBatchImportPrice fb ON fb.ProductID = p.ProductID
    WHERE p.AutoPrice = 1 OR fb.ImportPrice <= p.SellPrice;

    DROP TABLE #FirstBatchImportPrice;
END;
GO


-- ---------------------------------------------------------------
-- Doi/tra hang duoc duyet: cong/tru kho tuong ung + dieu chinh lai
-- SubTotal/TotalAmount cua hoa don goc theo phan chenh lech
-- (OUT - IN) khi hang duoc duyet.
-- ---------------------------------------------------------------
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

        -- dieu chinh lai hoa don goc theo net thay doi (OUT - IN)
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


/* ============================================================
   X. DOI CHIEU / KIEM KE KHO CUOI NGAY (StockReconciliation)
   ============================================================ */

-- ---------------------------------------------------------------
-- Ap dung 1 dong doi chieu kho: SystemStock LUON duoc tinh lai tu
-- Products.Stock TAI THOI DIEM GHI (bo qua gia tri app gui len, neu
-- co) de tranh sai lech do phien kiem ke keo dai (nhieu nguoi/nhieu
-- thoi diem doc cung 1 luc). Neu ActualStock khac SystemStock thi
-- cap nhat lai Products.Stock theo so dem thuc te VA ghi nhan 1 dong
-- InventoryTransactions (TransactionType='RECONCILE_ADJUST') de truy
-- vet - giong tinh than cac trigger IMPORT/SALE/RETURN o tren.
-- ---------------------------------------------------------------
CREATE TRIGGER trg_StockReconciliation_Apply
ON StockReconciliation
INSTEAD OF INSERT
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @ProductID INT, @ActualStock INT, @Note NVARCHAR(255), @CreatedBy INT;
    DECLARE @SystemStock INT, @NewReconciliationID INT, @Diff INT;

    DECLARE recon_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT ProductID, ActualStock, Note, CreatedBy FROM inserted;

    OPEN recon_cursor;
    FETCH NEXT FROM recon_cursor INTO @ProductID, @ActualStock, @Note, @CreatedBy;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        SELECT @SystemStock = Stock FROM Products WHERE ProductID = @ProductID;

        IF @SystemStock IS NULL
        BEGIN
            RAISERROR(N'San pham khong ton tai, khong the doi chieu kho.', 16, 1);
        END
        ELSE
        BEGIN
            INSERT INTO StockReconciliation (ProductID, SystemStock, ActualStock, Note, CreatedBy)
            VALUES (@ProductID, @SystemStock, @ActualStock, @Note, @CreatedBy);
            SET @NewReconciliationID = SCOPE_IDENTITY();

            SET @Diff = @ActualStock - @SystemStock;

            IF @Diff <> 0
            BEGIN
                UPDATE Products SET Stock = @ActualStock WHERE ProductID = @ProductID;

                INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                                    StockBefore, StockAfter, RefTable, RefID, CreatedBy, Note)
                VALUES (@ProductID, 'RECONCILE_ADJUST',
                        CASE WHEN @Diff > 0 THEN 'IN' ELSE 'OUT' END,
                        ABS(@Diff), @SystemStock, @ActualStock,
                        'StockReconciliation', @NewReconciliationID, @CreatedBy, @Note);
            END
        END

        FETCH NEXT FROM recon_cursor INTO @ProductID, @ActualStock, @Note, @CreatedBy;
    END

    CLOSE recon_cursor;
    DEALLOCATE recon_cursor;
END;
GO


-- ---------------------------------------------------------------
-- R3: lich su doi chieu kho la chung tu doi soat/kiem toan - khong
-- duoc xoa vinh vien, giong nguyen tac ap dung cho Invoices/PurchaseReceipts.
-- ---------------------------------------------------------------
CREATE TRIGGER trg_StockReconciliation_BlockDelete
ON StockReconciliation
INSTEAD OF DELETE
AS
BEGIN
    RAISERROR(N'Khong duoc xoa vinh vien lich su doi chieu kho.', 16, 1);
END;
GO

ALTER TABLE StockAlerts ALTER COLUMN ReportedBy INT NULL;
GO

DROP TRIGGER IF EXISTS trg_Products_AutoStockAlert;
GO

CREATE TRIGGER trg_Products_AutoStockAlert
ON Products
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    IF UPDATE(Stock)
    BEGIN
        INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, ReportedBy)
        SELECT i.ProductID,
               CASE WHEN i.Stock <= 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
               i.Stock,
               NULL
        FROM inserted i
        JOIN deleted d ON d.ProductID = i.ProductID
        WHERE i.Stock <> d.Stock          -- Stock thuc su vua thay doi
          AND i.Stock <= i.MinStock       -- va dang o muc thap/het hang
          AND NOT EXISTS (
                SELECT 1 FROM StockAlerts sa
                WHERE sa.ProductID = i.ProductID AND sa.Status <> 'RESOLVED'
          );
    END
END;
GO

-- ---------------------------------------------------------------
-- Dong bo Gia ban theo Gia nhap: Gia nhap doi theo thi truong nen
-- thuong xuyen phai sua tay (tren phieu nhap, hoac sua truc tiep
-- SP) - neu Gia ban van la truong doc lap thi de quen chinh, gay
-- ban lo/lai qua it. Ham + trigger duoi day dua cong thuc gia
-- xuong DATABASE de KHONG phu thuoc noi goi (PurchaseReceiptDAO
-- nhap hang, ProductDAO sua SP, hay bat ky code moi nao sau nay).
-- ---------------------------------------------------------------

-- Chenh lech (VND) mac dinh khi 1 SP khong dat Margin rieng.
-- Fallback 5000 neu StoreConfig thieu/loi/am (khong bao gio de
-- trigger loi hoac ra gia am).
CREATE OR ALTER FUNCTION dbo.fn_GetDefaultMargin()
RETURNS DECIMAL(18,0)
AS
BEGIN
    DECLARE @raw NVARCHAR(255);
    DECLARE @margin DECIMAL(18,0);

    SELECT @raw = ConfigValue FROM StoreConfig WHERE ConfigKey = 'DEFAULT_MARGIN';

    IF @raw IS NULL OR TRY_CAST(@raw AS DECIMAL(18,0)) IS NULL OR TRY_CAST(@raw AS DECIMAL(18,0)) < 0
        SET @margin = 5000;
    ELSE
        SET @margin = TRY_CAST(@raw AS DECIMAL(18,0));

    RETURN @margin;
END;
GO

-- Sau MOI INSERT/UPDATE tren Products, voi cac dong AutoPrice = 1,
-- tinh lai SellPrice = ImportPrice + chenh lech hieu luc (Margin
-- rieng cua SP, hoac fn_GetDefaultMargin() neu SP khong dat rieng).
-- Dong AutoPrice = 0 (ADMIN da khoa gia tay, vd dot khuyen mai) bi
-- bo qua - khong bao gio bi trigger ghi de.
CREATE TRIGGER trg_Products_SyncSellPrice
ON Products
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Voi INSERT, UPDATE(ImportPrice) luon TRUE nen luon vao nhanh
    -- nay. Voi UPDATE thuong, dieu kien nay con giup tranh trigger
    -- tu goi lai chinh no vo han lan (cau UPDATE ben duoi chi dung
    -- vao SellPrice, khong dung ImportPrice/Margin/AutoPrice).
    IF NOT (UPDATE(ImportPrice) OR UPDATE(Margin) OR UPDATE(AutoPrice))
        RETURN;

    UPDATE p
    SET p.SellPrice = p.ImportPrice + ISNULL(p.Margin, dbo.fn_GetDefaultMargin())
    FROM Products p
    JOIN inserted i ON i.ProductID = p.ProductID
    WHERE p.AutoPrice = 1;
END;
GO