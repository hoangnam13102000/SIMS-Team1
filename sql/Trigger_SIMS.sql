/* ============================================================
   IX. TRIGGER - HIEN THUC HOA NGUYEN TAC NGHIEP VU (R1, R3, R4, R5)
   (R2 da lam bang CHECK CONSTRAINT trong SIMS.sql)
   ============================================================ */
USE SIMS_DB;
GO


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

    -- R1: chan neu san pham het hang (chi tinh cac lo con han, chua het han) -
    -- KHONG ap dung cho dong thuoc hoa don duoc TU DONG sinh tu don online da
    -- COMPLETED (Orders.InvoiceID da tro ve hoa don nay), vi kho da tru xong
    -- roi tu luc don duoc CONFIRMED (xem OrderDAO#deductStockFEFO) - dong nay
    -- chi la BAN SAO de tai su dung luong doi/tra, khong duoc tru kho lan nua.
    IF EXISTS (
        SELECT 1 FROM inserted i
        WHERE NOT EXISTS (SELECT 1 FROM Orders o WHERE o.InvoiceID = i.InvoiceID)
          AND ISNULL((SELECT SUM(b.RemainingQty) FROM InventoryBatch b
                       WHERE b.ProductID = i.ProductID AND b.Status = 'ACTIVE'
                         AND (b.ExpiryDate IS NULL OR b.ExpiryDate >= CAST(GETDATE() AS DATE))), 0) = 0
    )
    BEGIN
        RAISERROR(N'San pham da het hang, khong the tao hoa don.', 16, 1);
        RETURN;
    END

    -- Cac dong thuoc hoa don sinh tu don online: insert nguyen van, KHONG dung
    -- FEFO/tru kho/ghi InventoryTransactions (da lam roi o buoc CONFIRMED).
    INSERT INTO InvoiceDetails (InvoiceID, ProductID, Quantity, UnitPrice)
    SELECT i.InvoiceID, i.ProductID, i.Quantity, i.UnitPrice
    FROM inserted i
    WHERE EXISTS (SELECT 1 FROM Orders o WHERE o.InvoiceID = i.InvoiceID);

    DECLARE @InvoiceID INT, @ProductID INT, @Quantity INT, @UnitPrice DECIMAL(18,0), @CreatedBy INT;
    DECLARE @Available INT, @QtyToSell INT, @StockBefore INT, @NewDetailID INT;
    DECLARE @Remaining INT, @BatchID INT, @BatchRemain INT, @Take INT;

    DECLARE line_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT i.InvoiceID, i.ProductID, i.Quantity, i.UnitPrice, inv.CreatedBy
        FROM inserted i
        JOIN Invoices inv ON inv.InvoiceID = i.InvoiceID
        WHERE NOT EXISTS (SELECT 1 FROM Orders o WHERE o.InvoiceID = i.InvoiceID);

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
        -- Han che "cung ngay + ca dang mo" CHI ap dung cho hoa don ban tai
        -- quay (nhan vien tu sua sai ngay tai cho). Hoa don gan voi don
        -- online (Orders.InvoiceID tro toi) duoc huy qua luong huy DON HANG
        -- (OrderDAO#updateOrderStatus) - co the xay ra bat ky luc nao sau khi
        -- khach thanh toan PayPal, khong bi rang buoc boi ca ban hang cua
        -- nhan vien nao ca, nen duoc mien tru khoi dieu kien nay.
        IF EXISTS (
            SELECT 1
            FROM inserted i
            JOIN deleted d ON i.InvoiceID = d.InvoiceID
            JOIN Shifts s ON s.ShiftID = i.ShiftID
            WHERE i.Status = 'CANCELLED' AND d.Status <> 'CANCELLED'
              AND NOT EXISTS (SELECT 1 FROM Orders o WHERE o.InvoiceID = i.InvoiceID)
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
-- Doi/tra hang duoc duyet: cong/tru kho THEO DUNG LO HANG
-- (InventoryBatch) + dieu chinh lai SubTotal/TotalAmount cua hoa
-- don goc theo phan chenh lech (OUT - IN) khi hang duoc duyet.
--
-- IN (khach tra hang): hoan lai DUNG vao cac lo goc da xuat cho
-- dong hoa don goc (tra qua InvoiceDetailBatches theo InvoiceID +
-- ProductID), gioi han theo dung so luong tung lo da bi tru cho
-- hoa don do. Neu khong truy duoc (du) lo goc (vd du lieu cu),
-- tu dong tao 1 lo "hang tra" moi (LotNumber = 'TRA-HANG-<ReturnID>')
-- de khong lam mat dau vet ton kho.
--
-- OUT (giao hang doi moi cho khach): tru kho theo FEFO giong het
-- logic ban hang o trg_InvoiceDetails_CheckStock (uu tien HSD gan
-- nhat, loai lo da het han).
--
-- Ca hai chieu deu ghi vet lo vao ReturnExchangeDetailBatches
-- (SIMS.sql) - dong vai tro nhu InvoiceDetailBatches. Products.Stock
-- (cache) duoc DONG BO LAI tu SUM(RemainingQty) cua InventoryBatch
-- cho tung san pham bi anh huong, thay vi cong/tru truc tiep, de
-- luon khop du co nhieu thao tac xen ke (giong cach trg_Invoices_
-- CancelSameDayOnly da lam khi hoan lo luc huy hoa don).
-- ---------------------------------------------------------------
CREATE TRIGGER trg_ReturnExchange_ApprovedStock
ON ReturnExchanges
AFTER UPDATE
AS
BEGIN
    SET NOCOUNT ON;
    IF NOT UPDATE(Status) RETURN;

    -- Cac ReturnID vua chuyen sang APPROVED trong lan UPDATE nay
    DECLARE @ApprovedNow TABLE (ReturnID INT PRIMARY KEY, InvoiceID INT, CreatedBy INT);
    INSERT INTO @ApprovedNow (ReturnID, InvoiceID, CreatedBy)
    SELECT i.ReturnID, i.InvoiceID, i.CreatedBy
    FROM inserted i
    JOIN deleted de ON de.ReturnID = i.ReturnID
    WHERE i.Status = 'APPROVED' AND de.Status <> 'APPROVED';

    IF NOT EXISTS (SELECT 1 FROM @ApprovedNow) RETURN;

    DECLARE @ReturnDetailID INT, @ReturnID INT, @InvoiceID INT, @ProductID INT,
            @Quantity INT, @Direction VARCHAR(10), @CreatedBy INT;

    -- ------------------------------------------------------------
    -- 1) Duyet tung dong ReturnExchangeDetails cua cac Return vua duyet
    -- ------------------------------------------------------------
    DECLARE detail_cursor CURSOR LOCAL FAST_FORWARD FOR
        SELECT d.ReturnDetailID, d.ReturnID, a.InvoiceID, d.ProductID, d.Quantity, d.Direction, a.CreatedBy
        FROM ReturnExchangeDetails d
        JOIN @ApprovedNow a ON a.ReturnID = d.ReturnID;

    OPEN detail_cursor;
    FETCH NEXT FROM detail_cursor INTO @ReturnDetailID, @ReturnID, @InvoiceID, @ProductID, @Quantity, @Direction, @CreatedBy;

    WHILE @@FETCH_STATUS = 0
    BEGIN
        DECLARE @Remaining INT = @Quantity;
        DECLARE @BatchID INT, @BatchQty INT, @Take INT;

        IF @Direction = 'IN'
        BEGIN
            -- --------------------------------------------------
            -- IN: hoan lai dung vao cac lo da xuat cho DUNG dong
            -- hoa don goc (InvoiceID + ProductID), gioi han theo
            -- so luong tung lo da bi tru cho hoa don do.
            -- --------------------------------------------------
            /*
             * Nguon LOT goc co 2 truong hop:
             *  1) Hoa don ban tai quay: InvoiceDetailBatches luu LOT da lay.
             *  2) Hoa don tao tu don online: kho da tru tu OrderDetailBatches,
             *     sau do InvoiceDetails chi la ban sao nen khong co
             *     InvoiceDetailBatches.
             *
             * Neu co InvoiceDetailBatches cho hoa don thi uu tien nguon nay;
             * neu khong thi lay truc tiep OrderDetailBatches qua Orders.InvoiceID.
             * Nhung lan tra truoc (APPROVED) van duoc tru ra de khong cong qua
             * so luong ban dau cua tung LOT.
             */
            DECLARE origin_cursor CURSOR LOCAL FAST_FORWARD FOR
                WITH OriginBatches AS (
                    SELECT idb.BatchID, idb.Quantity AS OriginQty
                    FROM InvoiceDetailBatches idb
                    JOIN InvoiceDetails dt ON dt.InvoiceDetailID = idb.InvoiceDetailID
                    WHERE dt.InvoiceID = @InvoiceID
                      AND dt.ProductID = @ProductID

                    UNION ALL

                    SELECT odb.BatchID, odb.Quantity AS OriginQty
                    FROM Orders o
                    JOIN OrderDetails od ON od.OrderID = o.OrderID
                    JOIN OrderDetailBatches odb ON odb.OrderDetailID = od.OrderDetailID
                    WHERE o.InvoiceID = @InvoiceID
                      AND od.ProductID = @ProductID
                      AND NOT EXISTS (
                          SELECT 1
                          FROM InvoiceDetailBatches idb2
                          JOIN InvoiceDetails dt2 ON dt2.InvoiceDetailID = idb2.InvoiceDetailID
                          WHERE dt2.InvoiceID = @InvoiceID
                            AND dt2.ProductID = @ProductID
                      )
                ),
                RemainingByBatch AS (
                    SELECT reb.BatchID, SUM(reb.Quantity) AS ReturnedQty
                    FROM ReturnExchangeDetailBatches reb
                    JOIN ReturnExchangeDetails rd2
                      ON rd2.ReturnDetailID = reb.ReturnDetailID
                    JOIN ReturnExchanges r2
                      ON r2.ReturnID = rd2.ReturnID
                    WHERE rd2.ProductID = @ProductID
                      AND r2.InvoiceID = @InvoiceID
                      AND r2.Status = 'APPROVED'
                    GROUP BY reb.BatchID
                )
                SELECT ob.BatchID,
                       ob.OriginQty - ISNULL(rb.ReturnedQty, 0) AS ReturnableBatchQty
                FROM OriginBatches ob
                LEFT JOIN RemainingByBatch rb ON rb.BatchID = ob.BatchID
                WHERE ob.OriginQty - ISNULL(rb.ReturnedQty, 0) > 0
                ORDER BY ob.BatchID;

            OPEN origin_cursor;
            FETCH NEXT FROM origin_cursor INTO @BatchID, @BatchQty;

            WHILE @@FETCH_STATUS = 0 AND @Remaining > 0
            BEGIN
                SET @Take = CASE WHEN @BatchQty > @Remaining THEN @Remaining ELSE @BatchQty END;

                UPDATE InventoryBatch
                SET RemainingQty = RemainingQty + @Take,
                    Status = CASE
                        WHEN Status = 'DEPLETED' THEN 'ACTIVE'
                        ELSE Status
                    END
                WHERE BatchID = @BatchID
                  AND RemainingQty + @Take <= Quantity;

                IF @@ROWCOUNT = 1
                BEGIN
                    INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                    VALUES (@ReturnDetailID, @BatchID, @Take);

                    SET @Remaining -= @Take;
                END

                FETCH NEXT FROM origin_cursor INTO @BatchID, @BatchQty;
            END

            CLOSE origin_cursor;
            DEALLOCATE origin_cursor;

            -- Khong truy duoc (du) lo goc => tao 1 lo "hang tra" moi
            -- de giu ton kho khong bi mat dau vet.
            IF @Remaining > 0
            BEGIN
                DECLARE @SupplierID INT, @ImportPrice DECIMAL(18,0);

                SELECT TOP 1 @SupplierID = SupplierID, @ImportPrice = ImportPrice
                FROM InventoryBatch
                WHERE ProductID = @ProductID
                ORDER BY ImportDate DESC;

                IF @SupplierID IS NULL
                BEGIN
                    SELECT TOP 1 @SupplierID = SupplierID FROM Suppliers ORDER BY SupplierID;
                    SET @ImportPrice = 0;
                END

                INSERT INTO InventoryBatch (ProductID, SupplierID, ReceiptDetailID, LotNumber,
                                             ManufactureDate, ExpiryDate, ImportPrice, Quantity, RemainingQty, Status)
                VALUES (@ProductID, @SupplierID, NULL, N'TRA-HANG-' + CAST(@ReturnID AS NVARCHAR(10)),
                        NULL, NULL, ISNULL(@ImportPrice, 0), @Remaining, @Remaining, 'ACTIVE');

                SET @BatchID = SCOPE_IDENTITY();

                INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                VALUES (@ReturnDetailID, @BatchID, @Remaining);

                SET @Remaining = 0;
            END

            INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                                StockBefore, StockAfter, RefTable, RefID, CreatedBy)
            SELECT @ProductID, 'RETURN_IN', 'IN', @Quantity,
                   Stock, Stock + @Quantity, 'ReturnExchanges', @ReturnID, @CreatedBy
            FROM Products WHERE ProductID = @ProductID;
        END
        ELSE -- @Direction = 'OUT'
        BEGIN
            -- --------------------------------------------------
            -- OUT: giao hang doi moi cho khach - tru kho theo FEFO,
            -- gioi han theo ton kho thuc te (khong de am kho).
            -- --------------------------------------------------
            DECLARE fefo_cursor CURSOR LOCAL FAST_FORWARD FOR
                SELECT BatchID, RemainingQty FROM InventoryBatch
                WHERE ProductID = @ProductID AND Status = 'ACTIVE' AND RemainingQty > 0
                  AND (ExpiryDate IS NULL OR ExpiryDate >= CAST(GETDATE() AS DATE))
                ORDER BY ISNULL(ExpiryDate, '9999-12-31'), BatchID;

            OPEN fefo_cursor;
            FETCH NEXT FROM fefo_cursor INTO @BatchID, @BatchQty;

            WHILE @@FETCH_STATUS = 0 AND @Remaining > 0
            BEGIN
                SET @Take = CASE WHEN @BatchQty > @Remaining THEN @Remaining ELSE @BatchQty END;

                UPDATE InventoryBatch
                SET RemainingQty = RemainingQty - @Take,
                    Status = CASE WHEN RemainingQty - @Take = 0 THEN 'DEPLETED' ELSE Status END
                WHERE BatchID = @BatchID;

                INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                VALUES (@ReturnDetailID, @BatchID, @Take);

                SET @Remaining -= @Take;
                FETCH NEXT FROM fefo_cursor INTO @BatchID, @BatchQty;
            END

            CLOSE fefo_cursor;
            DEALLOCATE fefo_cursor;
            -- Neu @Remaining > 0 sau vong lap: khong con du lo con
            -- han de giao (het hang that su) - Products.Stock se
            -- duoc dong bo lai dung theo InventoryBatch o buoc 2,
            -- tuc la khong the am kho hon so ton thuc te.

            INSERT INTO InventoryTransactions (ProductID, TransactionType, Direction, Quantity,
                                                StockBefore, StockAfter, RefTable, RefID, CreatedBy)
            SELECT @ProductID, 'RETURN_OUT', 'OUT', @Quantity - @Remaining,
                   Stock, Stock - (@Quantity - @Remaining), 'ReturnExchanges', @ReturnID, @CreatedBy
            FROM Products WHERE ProductID = @ProductID;
        END

        FETCH NEXT FROM detail_cursor INTO @ReturnDetailID, @ReturnID, @InvoiceID, @ProductID, @Quantity, @Direction, @CreatedBy;
    END

    CLOSE detail_cursor;
    DEALLOCATE detail_cursor;

    -- ------------------------------------------------------------
    -- 2) Dong bo lai Products.Stock (cache) tu InventoryBatch cho
    --    tung san pham bi anh huong boi cac Return vua duyet.
    -- ------------------------------------------------------------
    ;WITH Affected AS (
        SELECT DISTINCT d.ProductID
        FROM ReturnExchangeDetails d
        JOIN @ApprovedNow a ON a.ReturnID = d.ReturnID
    )
    UPDATE p
    SET p.Stock = (SELECT ISNULL(SUM(RemainingQty), 0) FROM InventoryBatch
                   WHERE ProductID = p.ProductID AND Status = 'ACTIVE')
    FROM Products p
    JOIN Affected af ON af.ProductID = p.ProductID;

    -- ------------------------------------------------------------
    -- 3) Dieu chinh lai hoa don goc theo net thay doi (OUT - IN)
    --    (giu nguyen logic cu)
    -- ------------------------------------------------------------
    ;WITH NetAdjust AS (
        SELECT a.InvoiceID,
               SUM(CASE WHEN d.Direction = 'OUT' THEN d.Quantity * d.UnitPrice
                        WHEN d.Direction = 'IN'  THEN -d.Quantity * d.UnitPrice END) AS NetChange
        FROM @ApprovedNow a
        JOIN ReturnExchangeDetails d ON d.ReturnID = a.ReturnID
        GROUP BY a.InvoiceID
    )
    UPDATE inv
    SET inv.SubTotal    = inv.SubTotal + n.NetChange,
        inv.TotalAmount = (inv.SubTotal + n.NetChange)
                          + ((inv.SubTotal + n.NetChange) * inv.VATRate / 100)
    FROM Invoices inv
    JOIN NetAdjust n ON n.InvoiceID = inv.InvoiceID;
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
-- Sau MOI INSERT/UPDATE tren Products, voi cac dong AutoPrice = 1,
-- tinh lai SellPrice = ImportPrice + chenh lech hieu luc (Margin
-- rieng cua SP, hoac fn_GetDefaultMargin() neu SP khong dat rieng).
-- Dong AutoPrice = 0 (ADMIN da khoa gia tay, vd dot khuyen mai) bi
-- bo qua - khong bao gio bi trigger ghi de. (Ham fn_GetDefaultMargin
-- da duoc dinh nghia o dau file.)
-- ---------------------------------------------------------------
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