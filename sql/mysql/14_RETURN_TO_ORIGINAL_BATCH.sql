/* ============================================================
   14_RETURN_TO_ORIGINAL_BATCH.sql
   Fix RETURN/EXCHANGE stock traceability on a running MySQL DB.

   Business rule:
   - Hang khach tra (Direction=IN) PHAI quay ve dung InventoryBatch
     da xuat cho hoa don/order goc.
   - Khong tao InventoryBatch / LotNumber TRA-HANG-* moi.
   - Neu hoa don cu thieu mapping batch, approval bi chan va rollback.
   - Chi tru ReturnExchangeDetailBatches Direction=IN khi tinh so luong
     da tra, tranh EXCHANGE OUT lam sai ReturnableQty.
   ============================================================ */
USE SIMS_DB;

DROP TRIGGER IF EXISTS trg_ReturnExchange_ApprovedStock;
DROP PROCEDURE IF EXISTS sp_ApplyReturnExchange;

DELIMITER //

CREATE PROCEDURE sp_ApplyReturnExchange(IN p_return_id INT)
main: BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_detail_id INT;
    DECLARE v_invoice_id INT;
    DECLARE v_product_id INT;
    DECLARE v_qty INT;
    DECLARE v_direction VARCHAR(10);
    DECLARE v_created_by INT;
    DECLARE v_remaining INT;
    DECLARE v_batch_id INT;
    DECLARE v_batch_qty INT;
    DECLARE v_take INT;
    DECLARE v_available INT;
    DECLARE v_stock_before INT;
    DECLARE v_stock_after INT;
    DECLARE v_net DECIMAL(18,0) DEFAULT 0;
    DECLARE v_sub DECIMAL(18,0);
    DECLARE v_discount DECIMAL(18,0);
    DECLARE v_points DECIMAL(18,0);
    DECLARE v_vat DECIMAL(5,2);
    DECLARE v_original DECIMAL(18,0);
    DECLARE v_new_sub DECIMAL(18,0);
    DECLARE v_new_discount DECIMAL(18,0);
    DECLARE v_new_points DECIMAL(18,0);
    DECLARE c_details CURSOR FOR
        SELECT ReturnDetailID, ProductID, Quantity, Direction
        FROM ReturnExchangeDetails
        WHERE ReturnID = p_return_id
        ORDER BY ReturnDetailID;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    SET v_invoice_id = (SELECT InvoiceID FROM ReturnExchanges WHERE ReturnID = p_return_id);
    SET v_created_by = (SELECT CreatedBy FROM ReturnExchanges WHERE ReturnID = p_return_id);
    IF v_invoice_id IS NULL THEN LEAVE main; END IF;

    SET v_done = 0;
    OPEN c_details;
    detail_loop: LOOP
        FETCH c_details INTO v_detail_id, v_product_id, v_qty, v_direction;
        IF v_done = 1 THEN LEAVE detail_loop; END IF;

        SET v_remaining = v_qty;
        SET v_stock_before = (SELECT Stock FROM Products WHERE ProductID = v_product_id);

        IF v_direction = 'IN' THEN
            origin_block: BEGIN
                DECLARE v_origin_done INT DEFAULT 0;
                DECLARE c_origin CURSOR FOR
                    SELECT ob.BatchID,
                           SUM(ob.OriginQty) - COALESCE(rb.ReturnedQty, 0) AS ReturnableQty
                    FROM (
                        /* POS invoice: batch da tru khi ban la nguon tra hang duy nhat. */
                        SELECT idb.BatchID, idb.Quantity AS OriginQty
                        FROM InvoiceDetailBatches idb
                        JOIN InvoiceDetails idt ON idt.InvoiceDetailID = idb.InvoiceDetailID
                        WHERE idt.InvoiceID = v_invoice_id AND idt.ProductID = v_product_id
                        UNION ALL
                        /* Online order: dung OrderDetailBatches neu invoice chua co mapping batch rieng. */
                        SELECT odb.BatchID, odb.Quantity AS OriginQty
                        FROM Orders o
                        JOIN OrderDetails od ON od.OrderID = o.OrderID
                        JOIN OrderDetailBatches odb ON odb.OrderDetailID = od.OrderDetailID
                        WHERE o.InvoiceID = v_invoice_id AND od.ProductID = v_product_id
                          AND NOT EXISTS (
                              SELECT 1 FROM InvoiceDetailBatches idb2
                              JOIN InvoiceDetails idt2 ON idt2.InvoiceDetailID = idb2.InvoiceDetailID
                              WHERE idt2.InvoiceID = v_invoice_id
                                AND idt2.ProductID = v_product_id)
                    ) ob
                    JOIN InventoryBatch ib ON ib.BatchID = ob.BatchID
                    LEFT JOIN (
                        SELECT reb.BatchID, SUM(reb.Quantity) AS ReturnedQty
                        FROM ReturnExchangeDetailBatches reb
                        JOIN ReturnExchangeDetails rd ON rd.ReturnDetailID = reb.ReturnDetailID
                        JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID
                        WHERE rd.ProductID = v_product_id
                          AND rd.Direction = 'IN'
                          AND r.InvoiceID = v_invoice_id AND r.Status = 'APPROVED'
                        GROUP BY reb.BatchID
                    ) rb ON rb.BatchID = ob.BatchID
                    GROUP BY ob.BatchID, rb.ReturnedQty, ib.ExpiryDate
                    HAVING SUM(ob.OriginQty) - COALESCE(rb.ReturnedQty, 0) > 0
                    /* Partial return uu tien dung thu tu FEFO giong luc ban. */
                    ORDER BY COALESCE(ib.ExpiryDate, '9999-12-31'), ob.BatchID;
                DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_origin_done = 1;

                OPEN c_origin;
                origin_loop: LOOP
                    FETCH c_origin INTO v_batch_id, v_batch_qty;
                    IF v_origin_done = 1 OR v_remaining <= 0 THEN LEAVE origin_loop; END IF;
                    SET v_take = LEAST(v_batch_qty, v_remaining);
                    UPDATE InventoryBatch
                    SET RemainingQty = RemainingQty + v_take,
                        Status = CASE
                            WHEN ExpiryDate IS NOT NULL AND ExpiryDate < CURRENT_DATE THEN 'EXPIRED'
                            WHEN Status = 'DEPLETED' THEN 'ACTIVE'
                            ELSE Status
                        END
                    WHERE BatchID = v_batch_id AND RemainingQty + v_take <= Quantity;
                    IF ROW_COUNT() = 1 THEN
                        INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                        VALUES (v_detail_id, v_batch_id, v_take);
                        SET v_remaining = v_remaining - v_take;
                    END IF;
                END LOOP;
                CLOSE c_origin;
            END origin_block;

            /*
             * Bat buoc tra dung lo goc da xuat cho hoa don.
             * Tuyet doi KHONG tao lo TRA-HANG-* moi neu thieu batch traceability.
             * SIGNAL lam rollback transaction duyet, giu request o PENDING de xu ly du lieu goc.
             */
            IF v_remaining > 0 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Khong du lo goc cua hoa don de nhap hang tra; he thong khong tao lo moi.';
            END IF;
        ELSE
            SELECT COALESCE(SUM(RemainingQty), 0) INTO v_available
            FROM InventoryBatch
            WHERE ProductID = v_product_id AND Status = 'ACTIVE' AND RemainingQty > 0
              AND (ExpiryDate IS NULL OR ExpiryDate >= CURRENT_DATE);
            IF v_available < v_qty THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Khong du ton kho con han de giao hang doi.';
            END IF;

            fefo_block: BEGIN
                DECLARE v_fefo_done INT DEFAULT 0;
                DECLARE c_fefo CURSOR FOR
                    SELECT BatchID, RemainingQty FROM InventoryBatch
                    WHERE ProductID = v_product_id AND Status = 'ACTIVE' AND RemainingQty > 0
                      AND (ExpiryDate IS NULL OR ExpiryDate >= CURRENT_DATE)
                    ORDER BY COALESCE(ExpiryDate, '9999-12-31'), BatchID;
                DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_fefo_done = 1;

                OPEN c_fefo;
                fefo_loop: LOOP
                    FETCH c_fefo INTO v_batch_id, v_batch_qty;
                    IF v_fefo_done = 1 OR v_remaining <= 0 THEN LEAVE fefo_loop; END IF;
                    SET v_take = LEAST(v_batch_qty, v_remaining);
                    UPDATE InventoryBatch
                    SET RemainingQty = RemainingQty - v_take,
                        Status = CASE WHEN RemainingQty - v_take = 0 THEN 'DEPLETED' ELSE Status END
                    WHERE BatchID = v_batch_id;
                    INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                    VALUES (v_detail_id, v_batch_id, v_take);
                    SET v_remaining = v_remaining - v_take;
                END LOOP;
                CLOSE c_fefo;
            END fefo_block;
        END IF;

        SET v_stock_after = (SELECT COALESCE(SUM(RemainingQty), 0)
                             FROM InventoryBatch
                             WHERE ProductID = v_product_id AND Status = 'ACTIVE');
        UPDATE Products SET Stock = v_stock_after WHERE ProductID = v_product_id;
        INSERT INTO InventoryTransactions
            (ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter,
             RefTable, RefID, CreatedBy)
        VALUES
            (v_product_id,
             CASE WHEN v_direction = 'IN' THEN 'RETURN_IN' ELSE 'RETURN_OUT' END,
             v_direction, v_qty, v_stock_before, v_stock_after,
             'ReturnExchanges', p_return_id, v_created_by);
    END LOOP;
    CLOSE c_details;

    SELECT COALESCE(SUM(CASE WHEN Direction = 'OUT' THEN Quantity * UnitPrice
                             ELSE -Quantity * UnitPrice END), 0)
    INTO v_net FROM ReturnExchangeDetails WHERE ReturnID = p_return_id;

    SELECT SubTotal, DiscountAmount, PointsDiscountAmount, VATRate
    INTO v_sub, v_discount, v_points, v_vat
    FROM Invoices WHERE InvoiceID = v_invoice_id;
    SELECT COALESCE(SUM(Quantity * UnitPrice), 0)
    INTO v_original FROM InvoiceDetails WHERE InvoiceID = v_invoice_id;

    SET v_new_sub = GREATEST(0, v_sub + v_net);
    SET v_new_discount = CASE WHEN v_original <= 0 OR v_discount <= 0 THEN 0
                              ELSE LEAST(v_discount, ROUND(v_discount * v_new_sub / v_original, 0)) END;
    SET v_new_points = CASE WHEN v_original <= 0 OR v_points <= 0 THEN 0
                            ELSE LEAST(v_points, ROUND(v_points * v_new_sub / v_original, 0)) END;

    UPDATE Invoices
    SET SubTotal = v_new_sub,
        DiscountAmount = LEAST(GREATEST(v_new_discount, 0), v_new_sub),
        PointsDiscountAmount = GREATEST(v_new_points, 0),
        TotalAmount = GREATEST(0, ROUND(
            (v_new_sub - LEAST(GREATEST(v_new_discount, 0), v_new_sub))
            * (1 + v_vat / 100) - GREATEST(v_new_points, 0), 0))
    WHERE InvoiceID = v_invoice_id;
END //

CREATE TRIGGER trg_ReturnExchange_ApprovedStock
AFTER UPDATE ON ReturnExchanges
FOR EACH ROW
BEGIN
    IF NEW.Status = 'APPROVED' AND OLD.Status <> 'APPROVED' THEN
        CALL sp_ApplyReturnExchange(NEW.ReturnID);
    END IF;
END //

DELIMITER ;

/* Verify object exists. */
SHOW TRIGGERS LIKE 'ReturnExchanges';

/*
 * Legacy rows created by old logic are NOT deleted automatically.
 * Review this result after migration. If it returns rows, those are old
 * TRA-HANG-* lots and can be repaired separately after checking references.
 */
SELECT
    b.BatchID, b.BatchCode, b.LotNumber, b.ProductID, p.ProductCode, p.ProductName,
    b.Quantity, b.RemainingQty, b.Status,
    CAST(SUBSTRING(b.LotNumber, 10) AS UNSIGNED) AS SuspectedReturnID
FROM InventoryBatch b
JOIN Products p ON p.ProductID = b.ProductID
WHERE b.LotNumber LIKE 'TRA-HANG-%'
ORDER BY b.BatchID;
