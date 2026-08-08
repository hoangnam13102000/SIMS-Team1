/*
 * MIGRATION: Khắc phục trả hàng cộng lại đúng LOT đã xuất.
 *
 * Trường hợp bán tại quầy:
 *   InvoiceDetailBatches -> LOT gốc.
 *
 * Trường hợp bán online:
 *   OrderDetailBatches -> LOT gốc, sau đó mới sinh Invoice khi COMPLETED.
 *
 * Trigger mới trong Trigger_SIMS.sql đã xử lý cả hai nguồn.
 * Nếu DB hiện tại đã có trigger trg_ReturnExchange_ApprovedStock,
 * chạy phần ALTER TRIGGER dưới đây để cập nhật mà không cần tạo lại toàn bộ DB.
 */
ALTER TRIGGER trg_ReturnExchange_ApprovedStock
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
