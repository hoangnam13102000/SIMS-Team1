/* ============================================================
   SIMS - MySQL 8.0+ business functions, procedures and triggers
   Chay SAU 01_SIMS_Schema_MySQL.sql.
   Co the chay lai; script se drop/reregister cac object cua SIMS.
   ============================================================ */
USE SIMS_DB;

DROP TRIGGER IF EXISTS trg_InvoiceDetails_CheckStock_BI;
DROP TRIGGER IF EXISTS trg_InvoiceDetails_ApplyStock_AI;
DROP TRIGGER IF EXISTS trg_Invoices_BlockDelete;
DROP TRIGGER IF EXISTS trg_PurchaseReceipts_BlockDelete;
DROP TRIGGER IF EXISTS trg_Invoices_CancelValidate_BU;
DROP TRIGGER IF EXISTS trg_Invoices_CancelRestore_AU;
DROP TRIGGER IF EXISTS trg_Users_AutoLock;
DROP TRIGGER IF EXISTS trg_PurchaseReceiptDetails_Insert;
DROP TRIGGER IF EXISTS trg_ReturnExchange_ApprovedStock;
DROP TRIGGER IF EXISTS trg_StockReconciliation_Prepare;
DROP TRIGGER IF EXISTS trg_StockReconciliation_Apply;
DROP TRIGGER IF EXISTS trg_StockReconciliation_BlockDelete;
DROP TRIGGER IF EXISTS trg_Products_AutoStockAlert;
DROP TRIGGER IF EXISTS trg_Products_SyncSellPrice_BI;
DROP TRIGGER IF EXISTS trg_Products_SyncSellPrice_BU;

DROP PROCEDURE IF EXISTS sp_ApplyReturnExchange;
DROP PROCEDURE IF EXISTS sp_AssignProductCode;
DROP PROCEDURE IF EXISTS sp_AssignBatchCode;
DROP PROCEDURE IF EXISTS sp_AssignOrderCode;
DROP PROCEDURE IF EXISTS sp_AssignDisposalCode;
DROP PROCEDURE IF EXISTS sp_AssignSupplierReturnCode;
DROP PROCEDURE IF EXISTS sp_BackfillAllCodes;
DROP FUNCTION IF EXISTS fn_GetDefaultMargin;

DELIMITER //

CREATE FUNCTION fn_GetDefaultMargin()
RETURNS DECIMAL(18,0)
READS SQL DATA
BEGIN
    DECLARE v_raw VARCHAR(255);
    DECLARE v_margin DECIMAL(18,0) DEFAULT 5000;

    SET v_raw = (SELECT ConfigValue FROM StoreConfig
                 WHERE ConfigKey = 'DEFAULT_MARGIN' LIMIT 1);
    IF v_raw IS NOT NULL AND v_raw REGEXP '^[0-9]+([.][0-9]+)?$' THEN
        SET v_margin = CAST(v_raw AS DECIMAL(18,0));
    END IF;
    IF v_margin < 0 THEN SET v_margin = 5000; END IF;
    RETURN v_margin;
END //

CREATE PROCEDURE sp_AssignProductCode(IN p_id INT)
BEGIN
    UPDATE Products SET ProductCode = CONCAT('SP_', LPAD(p_id, 4, '0'))
    WHERE ProductID = p_id;
END //

CREATE PROCEDURE sp_AssignBatchCode(IN p_id INT)
BEGIN
    UPDATE InventoryBatch SET BatchCode = CONCAT('LOT_', LPAD(p_id, 6, '0'))
    WHERE BatchID = p_id;
END //

CREATE PROCEDURE sp_AssignOrderCode(IN p_id INT)
BEGIN
    UPDATE Orders SET OrderCode = CONCAT('DH', LPAD(p_id, 4, '0'))
    WHERE OrderID = p_id;
END //

CREATE PROCEDURE sp_AssignDisposalCode(IN p_id INT)
BEGIN
    UPDATE StockDisposals SET DisposalCode = CONCAT('TH_', LPAD(p_id, 6, '0'))
    WHERE DisposalID = p_id;
END //

CREATE PROCEDURE sp_AssignSupplierReturnCode(IN p_id INT)
BEGIN
    UPDATE SupplierReturns SET SupplierReturnCode = CONCAT('TRNC_', LPAD(p_id, 6, '0'))
    WHERE SupplierReturnID = p_id;
END //

CREATE PROCEDURE sp_BackfillAllCodes()
BEGIN
    UPDATE Products SET ProductCode = CONCAT('SP_', LPAD(ProductID, 4, '0'))
    WHERE ProductCode IS NULL OR ProductCode = '';
    UPDATE InventoryBatch SET BatchCode = CONCAT('LOT_', LPAD(BatchID, 6, '0'))
    WHERE BatchCode IS NULL OR BatchCode = '';
    UPDATE Orders SET OrderCode = CONCAT('DH', LPAD(OrderID, 4, '0'))
    WHERE OrderCode IS NULL OR OrderCode = '';
    UPDATE StockDisposals SET DisposalCode = CONCAT('TH_', LPAD(DisposalID, 6, '0'))
    WHERE DisposalCode IS NULL OR DisposalCode = '';
    UPDATE SupplierReturns SET SupplierReturnCode = CONCAT('TRNC_', LPAD(SupplierReturnID, 6, '0'))
    WHERE SupplierReturnCode IS NULL OR SupplierReturnCode = '';
END //

/* POS sale: validate stock before insert, then allocate exact FEFO batches. */
CREATE TRIGGER trg_InvoiceDetails_CheckStock_BI
BEFORE INSERT ON InvoiceDetails
FOR EACH ROW
BEGIN
    DECLARE v_online INT DEFAULT 0;
    DECLARE v_available INT DEFAULT 0;

    SELECT COUNT(*) INTO v_online FROM Orders WHERE InvoiceID = NEW.InvoiceID;
    IF v_online = 0 THEN
        SELECT COALESCE(SUM(RemainingQty), 0) INTO v_available
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID
          AND Status = 'ACTIVE' AND RemainingQty > 0
          AND (ExpiryDate IS NULL OR ExpiryDate >= CURRENT_DATE);
        IF v_available < NEW.Quantity THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'San pham khong du ton kho con han de tao hoa don.';
        END IF;
    END IF;
END //

CREATE TRIGGER trg_InvoiceDetails_ApplyStock_AI
AFTER INSERT ON InvoiceDetails
FOR EACH ROW
main: BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_online INT DEFAULT 0;
    DECLARE v_remaining INT;
    DECLARE v_batch_id INT;
    DECLARE v_batch_qty INT;
    DECLARE v_take INT;
    DECLARE v_stock_before INT;
    DECLARE v_created_by INT;
    DECLARE c_batches CURSOR FOR
        SELECT BatchID, RemainingQty
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID
          AND Status = 'ACTIVE' AND RemainingQty > 0
          AND (ExpiryDate IS NULL OR ExpiryDate >= CURRENT_DATE)
        ORDER BY COALESCE(ExpiryDate, '9999-12-31'), BatchID;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    SELECT COUNT(*) INTO v_online FROM Orders WHERE InvoiceID = NEW.InvoiceID;
    IF v_online > 0 THEN LEAVE main; END IF;

    SET v_remaining = NEW.Quantity;
    SET v_stock_before = (SELECT Stock FROM Products WHERE ProductID = NEW.ProductID);
    SET v_created_by = (SELECT CreatedBy FROM Invoices WHERE InvoiceID = NEW.InvoiceID);

    OPEN c_batches;
    batch_loop: LOOP
        FETCH c_batches INTO v_batch_id, v_batch_qty;
        IF v_done = 1 OR v_remaining <= 0 THEN LEAVE batch_loop; END IF;
        SET v_take = LEAST(v_batch_qty, v_remaining);
        UPDATE InventoryBatch
        SET RemainingQty = RemainingQty - v_take,
            Status = CASE WHEN RemainingQty - v_take = 0 THEN 'DEPLETED' ELSE Status END
        WHERE BatchID = v_batch_id;
        INSERT INTO InvoiceDetailBatches (InvoiceDetailID, BatchID, Quantity)
        VALUES (NEW.InvoiceDetailID, v_batch_id, v_take);
        SET v_remaining = v_remaining - v_take;
    END LOOP;
    CLOSE c_batches;

    UPDATE Products SET Stock = Stock - NEW.Quantity WHERE ProductID = NEW.ProductID;
    INSERT INTO InventoryTransactions
        (ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter,
         RefTable, RefID, CreatedBy)
    VALUES
        (NEW.ProductID, 'SALE', 'OUT', NEW.Quantity, v_stock_before,
         v_stock_before - NEW.Quantity, 'Invoices', NEW.InvoiceID, v_created_by);
END //

CREATE TRIGGER trg_Invoices_BlockDelete
BEFORE DELETE ON Invoices FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien hoa don; hay huy hoa don kem ly do.';
END //

CREATE TRIGGER trg_PurchaseReceipts_BlockDelete
BEFORE DELETE ON PurchaseReceipts FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien phieu nhap kho.';
END //

CREATE TRIGGER trg_Invoices_CancelValidate_BU
BEFORE UPDATE ON Invoices
FOR EACH ROW
BEGIN
    DECLARE v_online INT DEFAULT 0;
    DECLARE v_shift_status VARCHAR(20);
    IF NEW.Status = 'CANCELLED' AND OLD.Status <> 'CANCELLED' THEN
        SELECT COUNT(*) INTO v_online FROM Orders WHERE InvoiceID = OLD.InvoiceID;
        IF v_online = 0 THEN
            SET v_shift_status = (SELECT Status FROM Shifts WHERE ShiftID = OLD.ShiftID);
            IF DATE(OLD.CreatedAt) <> CURRENT_DATE OR COALESCE(v_shift_status, 'CLOSED') = 'CLOSED' THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT = 'Chi duoc huy hoa don trong cung ngay va ca dang mo.';
            END IF;
        END IF;
    END IF;
END //

CREATE TRIGGER trg_Invoices_CancelRestore_AU
AFTER UPDATE ON Invoices
FOR EACH ROW
main: BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_batch_id INT;
    DECLARE v_qty INT;
    DECLARE c_restore CURSOR FOR
        SELECT idb.BatchID, idb.Quantity
        FROM InvoiceDetailBatches idb
        JOIN InvoiceDetails d ON d.InvoiceDetailID = idb.InvoiceDetailID
        WHERE d.InvoiceID = NEW.InvoiceID;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    IF NOT (NEW.Status = 'CANCELLED' AND OLD.Status <> 'CANCELLED') THEN
        LEAVE main;
    END IF;

    OPEN c_restore;
    restore_loop: LOOP
        FETCH c_restore INTO v_batch_id, v_qty;
        IF v_done = 1 THEN LEAVE restore_loop; END IF;
        UPDATE InventoryBatch
        SET RemainingQty = RemainingQty + v_qty,
            Status = CASE WHEN Status = 'DEPLETED' THEN 'ACTIVE' ELSE Status END
        WHERE BatchID = v_batch_id;
    END LOOP;
    CLOSE c_restore;

    UPDATE Products p
    JOIN (
        SELECT DISTINCT b.ProductID
        FROM InvoiceDetailBatches idb
        JOIN InvoiceDetails d ON d.InvoiceDetailID = idb.InvoiceDetailID
        JOIN InventoryBatch b ON b.BatchID = idb.BatchID
        WHERE d.InvoiceID = NEW.InvoiceID
    ) a ON a.ProductID = p.ProductID
    SET p.Stock = (SELECT COALESCE(SUM(b2.RemainingQty), 0)
                   FROM InventoryBatch b2
                   WHERE b2.ProductID = p.ProductID AND b2.Status = 'ACTIVE');

    INSERT INTO InventoryTransactions
        (ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter,
         RefTable, RefID, CreatedBy)
    SELECT d.ProductID, 'SALE_CANCEL', 'IN', SUM(d.Quantity),
           p.Stock - SUM(d.Quantity), p.Stock,
           'Invoices', NEW.InvoiceID, NEW.CreatedBy
    FROM InvoiceDetails d
    JOIN Products p ON p.ProductID = d.ProductID
    WHERE d.InvoiceID = NEW.InvoiceID
      AND EXISTS (SELECT 1 FROM InvoiceDetailBatches idb
                  WHERE idb.InvoiceDetailID = d.InvoiceDetailID)
    GROUP BY d.ProductID, p.Stock;
END //

CREATE TRIGGER trg_Users_AutoLock
BEFORE UPDATE ON Users
FOR EACH ROW
BEGIN
    IF NEW.FailedLoginCount >= 5 THEN SET NEW.IsLocked = 1; END IF;
END //

/* Each completed receipt detail creates one batch, stock movement and ledger row. */
CREATE TRIGGER trg_PurchaseReceiptDetails_Insert
AFTER INSERT ON PurchaseReceiptDetails
FOR EACH ROW
BEGIN
    DECLARE v_supplier_id INT;
    DECLARE v_created_by INT;
    DECLARE v_stock_before INT;

    SET v_supplier_id = (SELECT SupplierID FROM PurchaseReceipts WHERE ReceiptID = NEW.ReceiptID);
    SET v_created_by = (SELECT CreatedBy FROM PurchaseReceipts WHERE ReceiptID = NEW.ReceiptID);
    SET v_stock_before = (SELECT Stock FROM Products WHERE ProductID = NEW.ProductID);

    INSERT INTO InventoryBatch
        (BatchCode, ProductID, SupplierID, ReceiptDetailID, LotNumber,
         ManufactureDate, ExpiryDate, ImportPrice, Quantity, RemainingQty, Status)
    VALUES
        (NULL, NEW.ProductID, v_supplier_id, NEW.ReceiptDetailID, NEW.LotNumber,
         NEW.ManufactureDate, NEW.ExpiryDate, NEW.ImportPrice, NEW.Quantity, NEW.Quantity, 'ACTIVE');

    UPDATE Products SET Stock = Stock + NEW.Quantity WHERE ProductID = NEW.ProductID;
    INSERT INTO InventoryTransactions
        (ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter,
         RefTable, RefID, CreatedBy)
    VALUES
        (NEW.ProductID, 'IMPORT', 'IN', NEW.Quantity, v_stock_before,
         v_stock_before + NEW.Quantity, 'PurchaseReceipts', NEW.ReceiptID, v_created_by);
END //

/* Apply an approved return/exchange per detail and preserve batch traceability. */
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

CREATE TRIGGER trg_StockReconciliation_Prepare
BEFORE INSERT ON StockReconciliation
FOR EACH ROW
BEGIN
    SET NEW.SystemStock = (SELECT Stock FROM Products WHERE ProductID = NEW.ProductID);
    IF NEW.SystemStock IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'San pham khong ton tai, khong the doi chieu kho.';
    END IF;
END //

CREATE TRIGGER trg_StockReconciliation_Apply
AFTER INSERT ON StockReconciliation
FOR EACH ROW
BEGIN
    DECLARE v_diff INT;
    SET v_diff = NEW.ActualStock - NEW.SystemStock;
    IF v_diff <> 0 THEN
        UPDATE Products SET Stock = NEW.ActualStock WHERE ProductID = NEW.ProductID;
        INSERT INTO InventoryTransactions
            (ProductID, TransactionType, Direction, Quantity, StockBefore, StockAfter,
             RefTable, RefID, CreatedBy, Note)
        VALUES
            (NEW.ProductID, 'RECONCILE_ADJUST',
             CASE WHEN v_diff > 0 THEN 'IN' ELSE 'OUT' END,
             ABS(v_diff), NEW.SystemStock, NEW.ActualStock,
             'StockReconciliation', NEW.ReconciliationID, NEW.CreatedBy, NEW.Note);
    END IF;
END //

CREATE TRIGGER trg_StockReconciliation_BlockDelete
BEFORE DELETE ON StockReconciliation FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien lich su doi chieu kho.';
END //

CREATE TRIGGER trg_Products_AutoStockAlert
AFTER UPDATE ON Products
FOR EACH ROW
BEGIN
    IF NEW.Stock <> OLD.Stock AND NEW.Stock <= NEW.MinStock
       AND NOT EXISTS (SELECT 1 FROM StockAlerts
                       WHERE ProductID = NEW.ProductID AND Status <> 'RESOLVED') THEN
        INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, ReportedBy)
        VALUES (NEW.ProductID,
                CASE WHEN NEW.Stock <= 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
                NEW.Stock, NULL);
    END IF;
END //

CREATE TRIGGER trg_Products_SyncSellPrice_BI
BEFORE INSERT ON Products
FOR EACH ROW
BEGIN
    IF NEW.AutoPrice = 1 THEN
        SET NEW.SellPrice = NEW.ImportPrice + COALESCE(NEW.Margin, fn_GetDefaultMargin());
    END IF;
END //

CREATE TRIGGER trg_Products_SyncSellPrice_BU
BEFORE UPDATE ON Products
FOR EACH ROW
BEGIN
    IF NEW.AutoPrice = 1
       AND (NOT (NEW.ImportPrice <=> OLD.ImportPrice)
            OR NOT (NEW.Margin <=> OLD.Margin)
            OR NOT (NEW.AutoPrice <=> OLD.AutoPrice)) THEN
        SET NEW.SellPrice = NEW.ImportPrice + COALESCE(NEW.Margin, fn_GetDefaultMargin());
    END IF;
END //

DELIMITER ;

CALL sp_BackfillAllCodes();


/* ============================================================
   STOCK RECONCILIATION - FINAL V006 LOGIC
   ============================================================ */

DROP TRIGGER IF EXISTS trg_StockReconciliation_Prepare;
DROP TRIGGER IF EXISTS trg_StockReconciliation_Apply;

DELIMITER $$

CREATE TRIGGER trg_StockReconciliation_Prepare
BEFORE INSERT ON StockReconciliation
FOR EACH ROW
BEGIN
    DECLARE v_product_stock INT DEFAULT 0;
    DECLARE v_batch_product_id INT;
    DECLARE v_batch_stock INT DEFAULT 0;
    DECLARE v_has_batch INT DEFAULT 0;

    SELECT Stock
    INTO v_product_stock
    FROM Products
    WHERE ProductID = NEW.ProductID;

    IF v_product_stock IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT =
                'San pham khong ton tai, khong the doi chieu kho.';
    END IF;

    IF NEW.BatchID IS NOT NULL THEN

        SELECT ProductID, RemainingQty
        INTO v_batch_product_id, v_batch_stock
        FROM InventoryBatch
        WHERE BatchID = NEW.BatchID;

        IF v_batch_product_id IS NULL THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Lo hang khong ton tai.';
        END IF;

        IF v_batch_product_id <> NEW.ProductID THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT =
                    'Lo hang khong thuoc san pham dang doi chieu.';
        END IF;

        SET NEW.SystemStock = COALESCE(v_batch_stock, 0);

    ELSE

        SELECT COUNT(*)
        INTO v_has_batch
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID;

        IF v_has_batch > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT =
                    'San pham da quan ly theo lo; phai doi chieu theo tung lo.';
        END IF;

        SET NEW.SystemStock = COALESCE(v_product_stock, 0);

    END IF;
END$$


CREATE TRIGGER trg_StockReconciliation_Apply
AFTER INSERT ON StockReconciliation
FOR EACH ROW
BEGIN
    DECLARE v_diff INT DEFAULT 0;
    DECLARE v_before INT DEFAULT 0;
    DECLARE v_after INT DEFAULT 0;
    DECLARE v_new_batch_qty INT DEFAULT 0;
    DECLARE v_batch_quantity INT DEFAULT 0;

    IF NEW.BatchID IS NOT NULL THEN

        SELECT RemainingQty, Quantity
        INTO v_new_batch_qty, v_batch_quantity
        FROM InventoryBatch
        WHERE BatchID = NEW.BatchID
          AND ProductID = NEW.ProductID;

        IF NEW.ActualStock < 0
           OR NEW.ActualStock > v_batch_quantity THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT =
                    'Ton thuc te cua lo vuot gioi han so luong cua lo.';
        END IF;

        SELECT COALESCE(SUM(RemainingQty), 0)
        INTO v_before
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID;

        IF NEW.ActualStock <> NEW.SystemStock THEN

            UPDATE InventoryBatch
            SET RemainingQty = NEW.ActualStock,
                Status = CASE
                    WHEN NEW.ActualStock <= 0 THEN 'DEPLETED'
                    ELSE 'ACTIVE'
                END
            WHERE BatchID = NEW.BatchID
              AND ProductID = NEW.ProductID;

            IF ROW_COUNT() <> 1 THEN
                SIGNAL SQLSTATE '45000'
                    SET MESSAGE_TEXT =
                        'Khong the cap nhat ton cua lo hang.';
            END IF;

        END IF;

        SELECT COALESCE(SUM(RemainingQty), 0)
        INTO v_after
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID;

        UPDATE Products
        SET Stock = v_after
        WHERE ProductID = NEW.ProductID;

        SET v_diff = v_after - v_before;

    ELSE

        SELECT Stock
        INTO v_before
        FROM Products
        WHERE ProductID = NEW.ProductID;

        UPDATE Products
        SET Stock = NEW.ActualStock
        WHERE ProductID = NEW.ProductID;

        SET v_after = NEW.ActualStock;
        SET v_diff = v_after - v_before;

    END IF;

    IF v_diff <> 0 THEN

        INSERT INTO InventoryTransactions
            (
                ProductID,
                TransactionType,
                Direction,
                Quantity,
                StockBefore,
                StockAfter,
                RefTable,
                RefID,
                CreatedBy,
                Note
            )
        VALUES
            (
                NEW.ProductID,
                'RECONCILE_ADJUST',
                CASE
                    WHEN v_diff > 0 THEN 'IN'
                    ELSE 'OUT'
                END,
                ABS(v_diff),
                v_before,
                v_after,
                'StockReconciliation',
                NEW.ReconciliationID,
                NEW.CreatedBy,
                CASE
                    WHEN NEW.BatchID IS NOT NULL
                    THEN 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'
                    ELSE 'Dieu chinh ton thuc te tren bang doi chieu'
                END
            );

    END IF;
END$$

DELIMITER ;