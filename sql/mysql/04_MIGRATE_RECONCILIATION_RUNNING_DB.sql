-- ============================================================
-- MIGRATION CHO DATABASE ĐANG CHẠY
-- KHÔNG tạo database mới / KHÔNG xóa dữ liệu
-- ============================================================
-- Mục tiêu:
-- 1) Khi nhập ActualStock: KHÔNG cập nhật Products.Stock/InventoryBatch.
-- 2) Chốt phiên cũ vào 00:00 bằng MySQL Event.
--
-- Chạy file này trên DATABASE HIỆN TẠI sau khi deploy code mới.
-- Không cần chạy lại 01_SIMS_Schema_MySQL.sql.
-- Không cần chạy lại toàn bộ 02_SIMS_Triggers_MySQL.sql.
--
-- LƯU Ý:
-- File này chỉ thay trigger đối chiếu và tạo event chốt phiên.
-- Các trigger nghiệp vụ khác đang chạy trong DB được giữ nguyên.
-- ============================================================

DROP TRIGGER IF EXISTS trg_StockReconciliation_Apply;

DELIMITER $$

CREATE TRIGGER trg_StockReconciliation_Apply
AFTER INSERT ON StockReconciliation
FOR EACH ROW
BEGIN
    -- Cố ý không cập nhật tồn kho ở đây.
    -- ActualStock chỉ được lưu vào StockReconciliation.
    -- Tồn kho sẽ được cập nhật khi phiên được chốt lúc 00:00.
END$$

DELIMITER ;

DROP EVENT IF EXISTS ev_StockReconciliation_CloseMidnight;

DELIMITER $$

CREATE EVENT ev_StockReconciliation_CloseMidnight
ON SCHEDULE EVERY 1 DAY
STARTS (CURRENT_DATE + INTERVAL 1 DAY)
DO
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE v_id INT;
    DECLARE v_product_id INT;
    DECLARE v_batch_id INT;
    DECLARE v_actual INT;
    DECLARE v_before INT;
    DECLARE v_after INT;

    DECLARE cur CURSOR FOR
        SELECT ReconciliationID, ProductID, COALESCE(BatchID,0), ActualStock
        FROM StockReconciliation
        WHERE DATE(CreatedAt) = CURRENT_DATE - INTERVAL 1 DAY;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    START TRANSACTION;

    OPEN cur;

    read_loop: LOOP
        FETCH cur INTO v_id, v_product_id, v_batch_id, v_actual;

        IF done = 1 THEN
            LEAVE read_loop;
        END IF;

        IF v_batch_id > 0 THEN
            SELECT RemainingQty
            INTO v_before
            FROM InventoryBatch
            WHERE BatchID = v_batch_id
              AND ProductID = v_product_id
            FOR UPDATE;

            UPDATE InventoryBatch
            SET RemainingQty = v_actual,
                Status = CASE
                    WHEN v_actual <= 0 THEN 'DEPLETED'
                    ELSE 'ACTIVE'
                END
            WHERE BatchID = v_batch_id
              AND ProductID = v_product_id;

            SELECT COALESCE(SUM(RemainingQty), 0)
            INTO v_after
            FROM InventoryBatch
            WHERE ProductID = v_product_id;

            UPDATE Products
            SET Stock = v_after
            WHERE ProductID = v_product_id;

        ELSE
            SELECT Stock
            INTO v_before
            FROM Products
            WHERE ProductID = v_product_id
            FOR UPDATE;

            UPDATE Products
            SET Stock = v_actual
            WHERE ProductID = v_product_id;
        END IF;

    END LOOP;

    CLOSE cur;
    COMMIT;
END$$

DELIMITER ;

-- Bật Event Scheduler nếu server cho phép.
-- Nếu tài khoản DB không có quyền SUPER/SYSTEM_VARIABLES_ADMIN,
-- hãy bật ở cấu hình MySQL server thay vì chạy dòng này.
SET GLOBAL event_scheduler = ON;
