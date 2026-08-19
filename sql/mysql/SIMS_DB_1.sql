-- phpMyAdmin SQL Dump
-- version 5.1.1deb5ubuntu1
-- https://www.phpmyadmin.net/
--
-- Host: localhost:3306
-- Generation Time: Aug 19, 2026 at 01:50 PM
-- Server version: 8.0.46-0ubuntu0.22.04.3
-- PHP Version: 8.1.2-1ubuntu2.24

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `SIMS_DB`
--

DELIMITER $$
--
-- Procedures
--
CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_ApplyReturnExchange` (IN `p_return_id` INT)  main: BEGIN
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
    DECLARE v_supplier_id INT;
    DECLARE v_import_price DECIMAL(18,0);
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
                        SELECT idb.BatchID, idb.Quantity AS OriginQty
                        FROM InvoiceDetailBatches idb
                        JOIN InvoiceDetails idt ON idt.InvoiceDetailID = idb.InvoiceDetailID
                        WHERE idt.InvoiceID = v_invoice_id AND idt.ProductID = v_product_id
                        UNION ALL
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
                    LEFT JOIN (
                        SELECT reb.BatchID, SUM(reb.Quantity) AS ReturnedQty
                        FROM ReturnExchangeDetailBatches reb
                        JOIN ReturnExchangeDetails rd ON rd.ReturnDetailID = reb.ReturnDetailID
                        JOIN ReturnExchanges r ON r.ReturnID = rd.ReturnID
                        WHERE rd.ProductID = v_product_id
                          AND r.InvoiceID = v_invoice_id AND r.Status = 'APPROVED'
                        GROUP BY reb.BatchID
                    ) rb ON rb.BatchID = ob.BatchID
                    GROUP BY ob.BatchID, rb.ReturnedQty
                    HAVING SUM(ob.OriginQty) - COALESCE(rb.ReturnedQty, 0) > 0
                    ORDER BY ob.BatchID;
                DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_origin_done = 1;

                OPEN c_origin;
                origin_loop: LOOP
                    FETCH c_origin INTO v_batch_id, v_batch_qty;
                    IF v_origin_done = 1 OR v_remaining <= 0 THEN LEAVE origin_loop; END IF;
                    SET v_take = LEAST(v_batch_qty, v_remaining);
                    UPDATE InventoryBatch
                    SET RemainingQty = RemainingQty + v_take,
                        Status = CASE WHEN Status = 'DEPLETED' THEN 'ACTIVE' ELSE Status END
                    WHERE BatchID = v_batch_id AND RemainingQty + v_take <= Quantity;
                    IF ROW_COUNT() = 1 THEN
                        INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                        VALUES (v_detail_id, v_batch_id, v_take);
                        SET v_remaining = v_remaining - v_take;
                    END IF;
                END LOOP;
                CLOSE c_origin;
            END origin_block;

            IF v_remaining > 0 THEN
                SET v_supplier_id = (SELECT SupplierID FROM InventoryBatch
                                     WHERE ProductID = v_product_id ORDER BY ImportDate DESC LIMIT 1);
                SET v_import_price = (SELECT ImportPrice FROM InventoryBatch
                                      WHERE ProductID = v_product_id ORDER BY ImportDate DESC LIMIT 1);
                IF v_supplier_id IS NULL THEN
                    SET v_supplier_id = (SELECT SupplierID FROM Suppliers ORDER BY SupplierID LIMIT 1);
                    SET v_import_price = 0;
                END IF;
                IF v_supplier_id IS NULL THEN
                    SIGNAL SQLSTATE '45000'
                        SET MESSAGE_TEXT = 'Khong co nha cung cap de tao lo hang tra.';
                END IF;
                INSERT INTO InventoryBatch
                    (BatchCode, ProductID, SupplierID, ReceiptDetailID, LotNumber,
                     ManufactureDate, ExpiryDate, ImportPrice, Quantity, RemainingQty, Status)
                VALUES
                    (NULL, v_product_id, v_supplier_id, NULL, CONCAT('TRA-HANG-', p_return_id),
                     NULL, NULL, COALESCE(v_import_price, 0), v_remaining, v_remaining, 'ACTIVE');
                SET v_batch_id = LAST_INSERT_ID();
                UPDATE InventoryBatch SET BatchCode = CONCAT('LOT_', LPAD(BatchID, 6, '0'))
                WHERE BatchID = v_batch_id;
                INSERT INTO ReturnExchangeDetailBatches (ReturnDetailID, BatchID, Quantity)
                VALUES (v_detail_id, v_batch_id, v_remaining);
                SET v_remaining = 0;
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
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_AssignBatchCode` (IN `p_id` INT)  BEGIN
    UPDATE InventoryBatch SET BatchCode = CONCAT('LOT_', LPAD(p_id, 6, '0'))
    WHERE BatchID = p_id;
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_AssignDisposalCode` (IN `p_id` INT)  BEGIN
    UPDATE StockDisposals SET DisposalCode = CONCAT('TH_', LPAD(p_id, 6, '0'))
    WHERE DisposalID = p_id;
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_AssignOrderCode` (IN `p_id` INT)  BEGIN
    UPDATE Orders SET OrderCode = CONCAT('DH', LPAD(p_id, 4, '0'))
    WHERE OrderID = p_id;
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_AssignProductCode` (IN `p_id` INT)  BEGIN
    UPDATE Products SET ProductCode = CONCAT('SP_', LPAD(p_id, 4, '0'))
    WHERE ProductID = p_id;
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_AssignSupplierReturnCode` (IN `p_id` INT)  BEGIN
    UPDATE SupplierReturns SET SupplierReturnCode = CONCAT('TRNC_', LPAD(p_id, 6, '0'))
    WHERE SupplierReturnID = p_id;
END$$

CREATE DEFINER=`aptech`@`%` PROCEDURE `sp_BackfillAllCodes` ()  BEGIN
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
END$$

--
-- Functions
--
CREATE DEFINER=`aptech`@`%` FUNCTION `fn_GetDefaultMargin` () RETURNS DECIMAL(18,0) READS SQL DATA
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
END$$

DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `AuditLogs`
--

CREATE TABLE `AuditLogs` (
  `LogID` bigint NOT NULL,
  `UserID` int DEFAULT NULL,
  `Action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `TableName` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `RecordID` int DEFAULT NULL,
  `OldValue` longtext COLLATE utf8mb4_unicode_ci,
  `NewValue` longtext COLLATE utf8mb4_unicode_ci,
  `Detail` longtext COLLATE utf8mb4_unicode_ci,
  `IPAddress` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `AuditLogs`
--

INSERT INTO `AuditLogs` (`LogID`, `UserID`, `Action`, `TableName`, `RecordID`, `OldValue`, `NewValue`, `Detail`, `IPAddress`, `CreatedAt`) VALUES
(1, 1, 'LOGIN', 'Users', 1, NULL, NULL, 'Đăng nhập thành công', '192.168.1.10', '2026-08-12 10:53:32'),
(2, 4, 'LOGIN', 'Users', 4, NULL, NULL, 'Đăng nhập thành công', '192.168.1.25', '2026-08-12 10:53:32'),
(3, 2, 'RETURN_APPROVE', 'ReturnExchanges', 1, '{\"Status\":\"PENDING\",\"ApprovedBy\":null}', '{\"Status\":\"APPROVED\",\"ApprovedBy\":\"salesmgr\"}', 'Duyệt đổi/trả giá trị nhỏ', '192.168.1.40', '2026-08-12 10:53:32'),
(4, 1, 'PRODUCT_PRICE_UPDATE', 'Products', 3, '{\"SellPrice\":23000}', '{\"SellPrice\":24000}', 'Điều chỉnh giá bán theo giá nhập mới từ NCC Đà Lạt', '192.168.1.10', '2026-08-12 10:53:32'),
(5, 1, 'USER_LOCK', 'Users', 5, '{\"IsLocked\":false,\"FailedLoginCount\":5}', '{\"IsLocked\":true,\"FailedLoginCount\":5}', 'Tài khoản tự động khóa sau 5 lần đăng nhập sai liên tiếp', NULL, '2026-08-12 10:53:32'),
(6, 3, 'SUPPLIER_RETURN_CREATE', 'SupplierReturns', 1, NULL, '{\"Reason\":\"EXPIRED\",\"Status\":\"COMPLETED\"}', 'Lập phiếu trả hàng hết hạn về NCC An Bình', '192.168.1.30', '2026-08-12 10:53:32'),
(7, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 10:02:57'),
(8, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 10:29:36'),
(9, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 10:32:17'),
(10, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 10:56:08'),
(11, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 11:08:13'),
(12, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 11:40:43'),
(13, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 12:11:59'),
(14, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 12:37:46'),
(15, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 14:46:02'),
(16, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 14:48:28'),
(17, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 15:48:47'),
(18, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 15:48:58'),
(19, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 15:55:04'),
(20, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 15:55:56'),
(21, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 15:56:13'),
(22, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 15:58:44'),
(23, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-13 16:03:32'),
(24, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-13 16:12:42'),
(25, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 04:04:16'),
(26, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 04:12:32'),
(27, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 04:19:41'),
(28, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 04:47:51'),
(29, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 04:48:36'),
(30, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 04:49:25'),
(31, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 05:01:24'),
(32, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 05:02:56'),
(33, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 05:10:40'),
(34, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 05:27:10'),
(35, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 05:27:14'),
(36, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 05:39:55'),
(37, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 05:41:20'),
(38, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 05:41:54'),
(39, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"igvmnr\"', NULL, '2026-08-14 05:42:03'),
(40, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-14 05:43:21'),
(41, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-14 05:46:27'),
(42, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 10:47:25'),
(43, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 10:52:12'),
(44, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"ivgmnr\"', NULL, '2026-08-14 10:53:44'),
(45, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"ivnmnr\"', NULL, '2026-08-14 10:53:52'),
(46, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"ivnmgr\"', NULL, '2026-08-14 10:53:59'),
(47, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-14 10:54:30'),
(48, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-14 10:58:06'),
(49, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-14 10:58:16'),
(50, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-14 11:07:02'),
(51, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-14 11:11:26'),
(52, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-14 11:12:40'),
(53, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-14 11:18:26'),
(54, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-14 11:18:54'),
(55, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 11:19:00'),
(56, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 11:35:00'),
(57, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-14 14:53:54'),
(58, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 14:54:36'),
(59, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 15:15:57'),
(60, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 15:16:09'),
(61, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 15:23:47'),
(62, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 15:43:42'),
(63, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 15:43:42'),
(64, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 15:49:29'),
(65, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 15:50:38'),
(66, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 15:50:38'),
(67, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 15:55:11'),
(68, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 15:55:44'),
(69, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 15:55:45'),
(70, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 16:00:29'),
(71, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 16:01:18'),
(72, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 16:01:19'),
(73, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 17:33:28'),
(74, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 17:33:28'),
(75, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 17:40:55'),
(76, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 17:41:05'),
(77, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 17:42:58'),
(78, 4, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"staff01\"', NULL, '2026-08-14 17:51:13'),
(79, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 17:51:20'),
(80, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 17:51:37'),
(81, 1, '2FA_BACKUP_CODE_USED', 'USER', NULL, NULL, NULL, 'Đăng nhập bằng mã dự phòng 2FA (còn lại 9 mã)', NULL, '2026-08-14 17:59:00'),
(82, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 17:59:00'),
(83, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-14 17:59:34'),
(84, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-14 18:00:28'),
(85, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-14 18:01:45'),
(86, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 18:03:40'),
(87, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 18:03:48'),
(88, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 18:06:29'),
(89, 1, '2FA_BACKUP_CODE_USED', 'USER', NULL, NULL, NULL, 'Đăng nhập bằng mã dự phòng 2FA (còn lại 9 mã)', NULL, '2026-08-14 18:11:41'),
(90, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 18:11:41'),
(91, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-14 18:13:39'),
(92, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-14 18:14:14'),
(93, 1, 'STATUS_CHANGE', 'USER', NULL, NULL, NULL, 'Đã khóa tài khoản \"Lê Anh Đức (duc.le)\"', NULL, '2026-08-14 18:22:06'),
(94, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 18:25:32'),
(95, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-14 18:25:38'),
(96, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-14 18:30:38'),
(97, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-14 18:30:42'),
(98, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-14 18:31:03'),
(99, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-14 18:40:47'),
(100, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-14 18:40:47'),
(101, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-14 18:56:23'),
(102, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-14 18:58:38'),
(103, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-14 18:58:46'),
(104, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 19:12:51'),
(105, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 19:14:07'),
(106, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 19:17:39'),
(107, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 19:19:08'),
(108, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 22:52:14'),
(109, 4, 'SHIFT_CLOSE', 'SHIFT', 7, '{\"shiftId\":7,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-12T08:00\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":3,\"cashSales\":116640,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":7,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-12T08:00\",\"endTime\":\"2026-08-14T22:55:47\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":116640,\"countedCash\":116640,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":3,\"cashSales\":116640,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #7, chenh lech 0', NULL, '2026-08-14 22:55:47'),
(110, 4, 'SHIFT_OPEN', 'SHIFT', 9, NULL, '{\"shiftId\":9,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T22:56:27\",\"status\":\"OPEN\",\"openingCash\":1000000,\"openingNote\":\"Nhận tiền đầu ca từ quản lý\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #9 voi tien dau ca 1000000', NULL, '2026-08-14 22:56:27'),
(111, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 1, NULL, '{\"cashTransactionId\":1,\"transactionCode\":\"CT-1786748372061-009F55AE\",\"shiftId\":9,\"transactionType\":\"CASH_IN\",\"amount\":200000,\"reason\":\"Quản lý bổ sung tiền lẻ\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-14T22:59:32\"}', 'Thu tien trong ca #9: 200000 - Quản lý bổ sung tiền lẻ', NULL, '2026-08-14 22:59:32'),
(112, 4, 'CASH_OUT', 'SHIFT_CASH_TRANSACTION', 2, NULL, '{\"cashTransactionId\":2,\"transactionCode\":\"CT-1786748450103-33B4305E\",\"shiftId\":9,\"transactionType\":\"CASH_OUT\",\"amount\":150000,\"reason\":\"Mua vật tư đóng gói\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-14T23:00:50\"}', 'Chi tien trong ca #9: 150000 - Mua vật tư đóng gói', NULL, '2026-08-14 23:00:50'),
(113, 4, 'SHIFT_CLOSE', 'SHIFT', 9, '{\"shiftId\":9,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T22:56:27\",\"status\":\"OPEN\",\"openingCash\":1000000,\"openingNote\":\"Nhận tiền đầu ca từ quản lý\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":150000,\"cashRefunds\":0}', '{\"shiftId\":9,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T22:56:27\",\"endTime\":\"2026-08-14T23:02:57\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1050000,\"countedCash\":1050000,\"cashDifference\":0,\"openingNote\":\"Nhận tiền đầu ca từ quản lý\",\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":150000,\"cashRefunds\":0}', 'Dong ca #9, chenh lech 0', NULL, '2026-08-14 23:02:57'),
(114, 4, 'SHIFT_OPEN', 'SHIFT', 10, NULL, '{\"shiftId\":10,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T23:04:08\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #10 voi tien dau ca 1000000', NULL, '2026-08-14 23:04:08'),
(115, 4, 'SHIFT_CLOSE', 'SHIFT', 10, '{\"shiftId\":10,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T23:04:08\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":10,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-14T23:04:08\",\"endTime\":\"2026-08-14T23:04:28\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1000000,\"countedCash\":950000,\"cashDifference\":-50000,\"closingNote\":\"Thiếu 50.000, đang chờ quản lý xác minh\",\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #10, chenh lech -50000', NULL, '2026-08-14 23:04:28'),
(116, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 23:05:30'),
(117, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-14 23:06:55'),
(118, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-14 23:13:01'),
(119, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-14 23:16:47'),
(120, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-14 23:17:21'),
(121, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-14 23:17:39'),
(122, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-14 23:29:44'),
(123, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 06:30:19'),
(124, 4, 'SHIFT_OPEN', 'SHIFT', 11, NULL, '{\"shiftId\":11,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:30:33\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #11 voi tien dau ca 0', NULL, '2026-08-15 06:30:33'),
(125, 4, 'SHIFT_CLOSE', 'SHIFT', 11, '{\"shiftId\":11,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:30:33\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":11,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:30:33\",\"endTime\":\"2026-08-15T06:31:48\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #11, chenh lech 0', NULL, '2026-08-15 06:31:48'),
(126, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 06:35:58'),
(127, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 06:48:55'),
(128, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 06:49:40'),
(129, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 06:50:22'),
(130, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 06:51:57'),
(131, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 06:52:08'),
(132, 4, 'SHIFT_OPEN', 'SHIFT', 12, NULL, '{\"shiftId\":12,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:52:16\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #12 voi tien dau ca 0', NULL, '2026-08-15 06:52:16'),
(133, 4, 'SHIFT_CLOSE', 'SHIFT', 12, '{\"shiftId\":12,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:52:16\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":12,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T06:52:16\",\"endTime\":\"2026-08-15T06:52:20\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #12, chenh lech 0', NULL, '2026-08-15 06:52:21'),
(134, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 06:57:03'),
(135, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 07:46:48'),
(136, 4, 'SHIFT_OPEN', 'SHIFT', 13, NULL, '{\"shiftId\":13,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:48:03\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #13 voi tien dau ca 1000000', NULL, '2026-08-15 07:48:03'),
(137, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 07:52:24'),
(138, 4, 'SHIFT_CLOSE', 'SHIFT', 13, '{\"shiftId\":13,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:48:03\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":1,\"cashSales\":211680,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":13,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:48:03\",\"endTime\":\"2026-08-15T07:52:42\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1211680,\"countedCash\":1211680,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":1,\"cashSales\":211680,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #13, chenh lech 0', NULL, '2026-08-15 07:52:42'),
(139, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 07:52:48'),
(140, 4, 'SHIFT_OPEN', 'SHIFT', 14, NULL, '{\"shiftId\":14,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:58:28\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #14 voi tien dau ca 0', NULL, '2026-08-15 07:58:28'),
(141, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 08:00:46'),
(142, 4, 'SHIFT_CLOSE', 'SHIFT', 14, '{\"shiftId\":14,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:58:28\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":14,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T07:58:28\",\"endTime\":\"2026-08-15T08:01\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #14, chenh lech 0', NULL, '2026-08-15 08:01:00'),
(143, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 08:01:21'),
(144, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 08:21:00'),
(145, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 08:30:10'),
(146, 4, 'SHIFT_OPEN', 'SHIFT', 15, NULL, '{\"shiftId\":15,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T08:32:03\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #15 voi tien dau ca 0', NULL, '2026-08-15 08:32:03'),
(147, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 08:33:19'),
(148, 4, 'SHIFT_CLOSE', 'SHIFT', 15, '{\"shiftId\":15,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T08:32:03\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":15,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T08:32:03\",\"endTime\":\"2026-08-15T08:33:36\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #15, chenh lech 0', NULL, '2026-08-15 08:33:36'),
(149, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 08:38:15'),
(150, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 09:14:22'),
(151, 4, 'SHIFT_OPEN', 'SHIFT', 16, NULL, '{\"shiftId\":16,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T09:14:55\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #16 voi tien dau ca 0', NULL, '2026-08-15 09:14:56'),
(152, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 09:16:02'),
(153, 4, 'SHIFT_CLOSE', 'SHIFT', 16, '{\"shiftId\":16,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T09:14:55\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":16,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T09:14:55\",\"endTime\":\"2026-08-15T09:16:22\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #16, chenh lech 0', NULL, '2026-08-15 09:16:22'),
(154, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 09:16:26'),
(155, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 09:16:59'),
(156, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-15 02:24:26'),
(157, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-15 02:24:57'),
(158, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 02:38:25'),
(159, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 02:38:26'),
(160, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Tạo lại bộ mã dự phòng 2FA', NULL, '2026-08-15 03:26:38'),
(161, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 03:43:05'),
(162, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 04:57:42'),
(163, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 04:57:55'),
(164, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 05:11:40'),
(165, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 05:11:51'),
(166, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 05:16:55'),
(167, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 05:17:11'),
(168, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 05:19:57'),
(169, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 06:23:52'),
(170, 1, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"admin\"', NULL, '2026-08-15 08:04:47'),
(171, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 15:04:49'),
(172, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 08:05:55'),
(173, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 08:05:55'),
(174, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 08:10:15'),
(175, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 08:12:50'),
(176, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 08:12:51'),
(177, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-15 08:13:18'),
(178, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 08:22:35'),
(179, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 08:22:38'),
(180, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 15:27:25'),
(181, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 15:30:09'),
(182, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 08:34:08'),
(183, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 15:38:04'),
(184, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 15:42:10'),
(185, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 08:46:05'),
(186, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 08:48:19'),
(187, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 08:48:19'),
(188, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-15 08:48:58'),
(189, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố qua Email', NULL, '2026-08-15 08:50:40'),
(190, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-15 08:51:42'),
(191, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 08:59:44'),
(192, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 09:02:54'),
(193, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 09:02:58'),
(194, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 09:03:25'),
(195, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 09:03:44'),
(196, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 09:06:54'),
(197, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 09:07:07'),
(198, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 09:07:37'),
(199, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 09:08:07'),
(200, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 09:08:32'),
(201, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 09:08:34'),
(202, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 09:08:50'),
(203, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"salemgr\"', NULL, '2026-08-15 09:09:48'),
(204, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 09:09:53'),
(205, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 09:09:53'),
(206, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 09:11:22'),
(207, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 09:15:47'),
(208, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 09:17:52'),
(209, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 09:29:45'),
(210, 1, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"admin\"', NULL, '2026-08-15 09:34:37'),
(211, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 09:41:53'),
(212, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 09:41:53'),
(213, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Tạo lại bộ mã dự phòng 2FA', NULL, '2026-08-15 09:42:29'),
(214, 1, '2FA_BACKUP_CODE_USED', 'USER', NULL, NULL, NULL, 'Đăng nhập bằng mã dự phòng 2FA (còn lại 9 mã)', NULL, '2026-08-15 16:43:57'),
(215, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 16:43:57'),
(216, 1, 'UPDATE', 'PRODUCT', 10, '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":28000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/banh-quy-bo.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":25000,\"autoPrice\":true,\"imageUrl\":\"uploads\\\\products\\\\fb24f647-aace-4a59-a1ad-42fc44c96c10.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 16:45:02'),
(217, 1, '2FA_DISABLED', 'USER', NULL, NULL, NULL, 'Tắt xác thực 2 yếu tố', NULL, '2026-08-15 09:50:21'),
(218, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 09:50:30'),
(219, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 16:51:23'),
(220, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 10:24:28'),
(221, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 10:24:32'),
(222, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 10:24:45'),
(223, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 10:26:53'),
(224, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 10:26:55'),
(225, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 10:32:19'),
(226, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 17:36:06'),
(227, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 17:36:18'),
(228, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 17:39:24'),
(229, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 17:39:26'),
(230, 1, 'UPDATE', 'PRODUCT', 10, '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":25000,\"autoPrice\":true,\"imageUrl\":\"uploads\\\\products\\\\fb24f647-aace-4a59-a1ad-42fc44c96c10.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T16:45:02\"}', '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":25000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786790411/ezsg01v8uupmzfx9ppiv.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T16:45:02\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 17:40:13'),
(231, 1, 'SHIFT_CLOSE', 'SHIFT', 8, '{\"shiftId\":8,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-13T12:13:52\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":1,\"cashSales\":51840,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":8,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-13T12:13:52\",\"endTime\":\"2026-08-15T17:53:36\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":51840,\"countedCash\":51840,\"cashDifference\":0,\"closedBy\":1,\"closedByName\":\"Hoàng Trung Nam\",\"invoiceCount\":1,\"cashSales\":51840,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #8, chenh lech 0', NULL, '2026-08-15 17:53:36'),
(232, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 17:54:11'),
(233, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 17:54:23'),
(234, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 17:59:30'),
(235, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 18:04:08'),
(236, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 18:04:08'),
(237, 1, 'UPDATE', 'PRODUCT', 6, '{\"productId\":6,\"productCode\":\"SP_0006\",\"productName\":\"Trà xanh Không Độ 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"importPrice\":6000,\"sellPrice\":8500,\"autoPrice\":true,\"imageUrl\":\"uploads/products/tra-xanh.jpg\",\"stock\":300,\"minStock\":20,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":6,\"productCode\":\"SP_0006\",\"productName\":\"Trà xanh Không Độ 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"importPrice\":6000,\"sellPrice\":11000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786791955/ymamfyauyxidkkycujo8.jpg\",\"stock\":300,\"minStock\":20,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 18:05:56'),
(238, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 18:11:04'),
(239, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 18:24:54'),
(240, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 18:25:02'),
(241, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 11:57:15'),
(242, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 11:57:17'),
(243, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 11:59:26'),
(244, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 12:00:21'),
(245, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 12:00:45'),
(246, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 12:00:45'),
(247, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 12:01:14'),
(248, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 12:01:30'),
(249, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 12:01:30'),
(250, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 12:01:32'),
(251, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 12:03:44'),
(252, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 12:04:45'),
(253, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 12:04:49'),
(254, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 12:04:53'),
(255, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 12:04:55'),
(256, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 12:06:03'),
(257, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 12:06:03'),
(258, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 12:28:35'),
(259, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 12:28:45'),
(260, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 12:30:38'),
(261, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 12:34:38'),
(262, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 12:34:38'),
(263, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 12:35:20'),
(264, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 12:35:21'),
(265, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 12:40:23'),
(266, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 12:40:33'),
(267, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 12:40:56'),
(268, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (TOTP) thành công khi đăng nhập', NULL, '2026-08-15 12:42:57'),
(269, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 12:42:58'),
(270, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 12:43:20'),
(271, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 13:04:02'),
(272, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 13:04:13'),
(273, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 13:04:26'),
(274, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 13:04:47'),
(275, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 20:05:31'),
(276, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:06:14'),
(277, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 20:08:22'),
(278, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 13:09:07'),
(279, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 13:09:39'),
(280, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 20:11:07'),
(281, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố qua Email', NULL, '2026-08-15 20:11:35'),
(282, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 20:11:37'),
(283, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-15 13:12:56'),
(284, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 13:12:56'),
(285, 1, 'UPDATE', 'PRODUCT', 10, '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":25000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786790411/ezsg01v8uupmzfx9ppiv.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T17:40:13\"}', '{\"productId\":10,\"productCode\":\"SP_0010\",\"productName\":\"Bánh quy bơ 200g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"importPrice\":20000,\"sellPrice\":25000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786799625/sczmbnlbdtyop7oxlwav.jpg\",\"stock\":9,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T17:40:13\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 20:13:46'),
(286, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:13:51'),
(287, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-15 13:14:13'),
(288, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 13:14:13'),
(289, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-15 13:14:36'),
(290, 1, 'UPDATE', 'PRODUCT', 9, '{\"productId\":9,\"productCode\":\"SP_0009\",\"productName\":\"Sữa tươi Vinamilk 1L\",\"categoryId\":5,\"categoryName\":\"Sữa các loại\",\"importPrice\":28000,\"sellPrice\":36000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/sua-tuoi-vinamilk.jpg\",\"stock\":200,\"minStock\":20,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T08:40:06\"}', '{\"productId\":9,\"productCode\":\"SP_0009\",\"productName\":\"Sữa tươi Vinamilk 1L\",\"categoryId\":5,\"categoryName\":\"Sữa các loại\",\"importPrice\":28000,\"sellPrice\":33000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786799711/lab1kvrjppicd6gbggel.jpg\",\"stock\":200,\"minStock\":20,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-12T10:53:30\",\"updatedAt\":\"2026-08-15T08:40:06\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 20:15:12'),
(291, 1, 'UPDATE', 'PRODUCT', 8, '{\"productId\":8,\"productCode\":\"SP_0008\",\"productName\":\"Mì tôm Hảo Hảo (thùng)\",\"categoryId\":4,\"categoryName\":\"Thực phẩm khô\",\"importPrice\":90000,\"sellPrice\":105000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/mi-tom-hao-hao.jpg\",\"stock\":0,\"minStock\":5,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":8,\"productCode\":\"SP_0008\",\"productName\":\"Mì tôm Hảo Hảo (thùng)\",\"categoryId\":4,\"categoryName\":\"Thực phẩm khô\",\"importPrice\":90000,\"sellPrice\":95000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/jcgabkar/image/upload/v1786799749/k1mqe1gx8qivvdc4rttk.jpg\",\"stock\":0,\"minStock\":5,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-15 20:15:50'),
(292, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:16:02'),
(293, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-15 13:18:03'),
(294, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 13:18:03'),
(295, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:18:09'),
(296, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-15 13:23:48'),
(297, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 13:23:48'),
(298, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:23:59'),
(299, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-15 13:24:43'),
(300, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 13:24:43'),
(301, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 13:29:37'),
(302, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 20:29:37'),
(303, 1, 'CREATE', 'PRODUCT', 11, '{\"productId\":11,\"productCode\":\"SP_0011\",\"productName\":\"Bánh quy Cosy 300g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"brand\":\"Cosy\",\"description\":\"Bánh quy bơ giòn tan\",\"importPrice\":0,\"sellPrice\":29000,\"autoPrice\":false,\"stock\":0,\"minStock\":0,\"status\":\"ACTIVE\"}', NULL, 'Đã nhập sản phẩm \"Bánh quy Cosy 300g\" từ file', NULL, '2026-08-15 13:29:40'),
(304, 1, 'CREATE', 'PRODUCT', 12, '{\"productId\":12,\"productCode\":\"SP_0012\",\"productName\":\"Nước suối Lavie 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Lavie\",\"description\":\"Nước khoáng thiên nhiên\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":false,\"stock\":0,\"minStock\":0,\"status\":\"ACTIVE\"}', NULL, 'Đã nhập sản phẩm \"Nước suối Lavie 500ml\" từ file', NULL, '2026-08-15 13:29:41'),
(305, 1, 'CREATE', 'PRODUCT', 13, '{\"productId\":13,\"productCode\":\"SP_0013\",\"productName\":\"Cà phê bột Trung Nguyên 500g\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Trung Nguyên\",\"description\":\"Cà phê rang xay nguyên chất\",\"importPrice\":0,\"sellPrice\":58000,\"autoPrice\":false,\"stock\":0,\"minStock\":0,\"status\":\"ACTIVE\"}', NULL, 'Đã nhập sản phẩm \"Cà phê bột Trung Nguyên 500g\" từ file', NULL, '2026-08-15 13:29:42'),
(306, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 20:29:50'),
(307, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 13:30:11'),
(308, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 13:34:13'),
(309, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố bằng ứng dụng Authenticator (TOTP)', NULL, '2026-08-15 13:38:56'),
(310, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Tạo lại bộ mã dự phòng 2FA', NULL, '2026-08-15 13:40:13'),
(311, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 13:47:37'),
(312, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 13:47:56'),
(313, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-15 13:53:11'),
(314, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 13:57:41'),
(315, 4, 'SHIFT_OPEN', 'SHIFT', 17, NULL, '{\"shiftId\":17,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T21:00:36\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #17 voi tien dau ca 0', NULL, '2026-08-15 21:00:36'),
(316, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 3, NULL, '{\"cashTransactionId\":3,\"transactionCode\":\"CT-1786802572354-74D6159C\",\"shiftId\":17,\"transactionType\":\"CASH_IN\",\"amount\":1000000,\"reason\":\"ban giao\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-15T21:02:53\"}', 'Thu tien trong ca #17: 1000000 - ban giao', NULL, '2026-08-15 21:02:53'),
(317, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-15 14:14:14'),
(318, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-15 14:19:26'),
(319, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 21:23:33'),
(320, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 21:23:53'),
(321, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 21:24:32'),
(322, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 21:31:33');
INSERT INTO `AuditLogs` (`LogID`, `UserID`, `Action`, `TableName`, `RecordID`, `OldValue`, `NewValue`, `Detail`, `IPAddress`, `CreatedAt`) VALUES
(323, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-15 21:50:58'),
(324, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-15 21:51:09'),
(325, 1, '2FA_ENABLED', 'USER', NULL, NULL, NULL, 'Bật xác thực 2 yếu tố qua Email', NULL, '2026-08-15 22:58:41'),
(326, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-15 22:58:44'),
(327, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-15 23:22:28'),
(328, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 00:10:41'),
(329, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 00:10:41'),
(330, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 01:06:06'),
(331, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 01:07:28'),
(332, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 01:07:28'),
(333, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 01:22:59'),
(334, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 01:23:37'),
(335, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 01:23:37'),
(336, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 01:32:49'),
(337, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 01:33:04'),
(338, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 01:33:04'),
(339, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 01:52:10'),
(340, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 01:52:31'),
(341, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 01:52:31'),
(342, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 01:57:05'),
(343, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:07:56'),
(344, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:07:57'),
(345, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 02:16:49'),
(346, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:17:14'),
(347, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:17:14'),
(348, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 02:25:12'),
(349, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:25:26'),
(350, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:25:26'),
(351, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 02:26:02'),
(352, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:31:25'),
(353, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:31:25'),
(354, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 02:43:31'),
(355, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:43:41'),
(356, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:43:42'),
(357, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 02:48:59'),
(358, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 02:48:59'),
(359, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 02:50:04'),
(360, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 11:53:10'),
(361, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 12:29:34'),
(362, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 12:48:32'),
(363, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 12:49:00'),
(364, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 13:19:01'),
(365, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 13:19:51'),
(366, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 13:21:25'),
(367, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 13:31:23'),
(368, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 13:34:12'),
(369, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 14:00:41'),
(370, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 14:06:06'),
(371, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 14:07:03'),
(372, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 14:07:03'),
(373, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 14:08:12'),
(374, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 14:15:15'),
(375, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 14:15:15'),
(376, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 14:15:42'),
(377, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 14:16:56'),
(378, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 14:36:40'),
(379, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 14:37:42'),
(380, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 14:37:43'),
(381, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 14:54:34'),
(382, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 14:54:54'),
(383, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 14:54:55'),
(384, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 15:02:00'),
(385, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 15:02:12'),
(386, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 15:02:35'),
(387, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 15:02:36'),
(388, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 15:05:52'),
(389, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 15:06:13'),
(390, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 15:06:13'),
(391, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 15:07:55'),
(392, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 15:09:21'),
(393, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 15:09:55'),
(394, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 15:13:11'),
(395, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 15:17:13'),
(396, 1, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"admin\"', NULL, '2026-08-16 15:17:20'),
(397, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 15:17:39'),
(398, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 15:17:40'),
(399, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 15:33:15'),
(400, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 15:41:09'),
(401, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 16:39:47'),
(402, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 16:41:15'),
(403, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 16:43:28'),
(404, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 16:54:31'),
(405, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 16:54:31'),
(406, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 17:17:48'),
(407, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 17:20:16'),
(408, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 17:21:43'),
(409, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 18:07:44'),
(410, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 18:08:35'),
(411, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 18:08:35'),
(412, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 18:12:52'),
(413, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 18:14:33'),
(414, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 18:14:34'),
(415, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 18:15:17'),
(416, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 18:16:25'),
(417, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 18:16:25'),
(418, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 18:32:06'),
(419, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 18:33:27'),
(420, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 18:33:27'),
(421, 1, 'UPDATE', 'PRODUCT', 13, '{\"productId\":13,\"productCode\":\"SP_0013\",\"productName\":\"Cà phê bột Trung Nguyên 500g\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Trung Nguyên\",\"description\":\"Cà phê rang xay nguyên chất\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"stock\":0,\"minStock\":0,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-15T13:29:41\"}', '{\"productId\":13,\"productCode\":\"SP_0013\",\"productName\":\"Cà phê bột Trung Nguyên 500g\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Trung Nguyên\",\"description\":\"Cà phê rang xay nguyên chất\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1786880029/ca-phe-trung-nguyen_emvgr4.jpg\",\"stock\":0,\"minStock\":0,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-15T13:29:41\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-16 18:33:50'),
(422, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 19:04:40'),
(423, 11, 'LOGIN', 'USER', NULL, NULL, NULL, 'Khách hàng Demo đã đăng nhập', NULL, '2026-08-16 19:04:52'),
(424, 11, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Khách hàng Demo đã đăng xuất', NULL, '2026-08-16 19:07:44'),
(425, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-16 19:07:53'),
(426, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-16 19:11:58'),
(427, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"invmgr2\"', NULL, '2026-08-16 19:16:38'),
(428, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 19:19:47'),
(429, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 19:39:30'),
(430, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 19:39:43'),
(431, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 19:39:55'),
(432, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 19:40:09'),
(433, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 19:40:12'),
(434, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 19:40:23'),
(435, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 19:42:34'),
(436, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 20:04:22'),
(437, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 20:06:59'),
(438, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 20:18:09'),
(439, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 20:43:21'),
(440, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 20:44:08'),
(441, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 20:56:27'),
(442, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 20:56:29'),
(443, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 21:01:08'),
(444, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 21:01:31'),
(445, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 21:03:28'),
(446, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 21:03:51'),
(447, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 21:03:52'),
(448, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-16 21:04:19'),
(449, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 21:04:46'),
(450, 4, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"staff01\"', NULL, '2026-08-16 21:05:01'),
(451, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 21:05:09'),
(452, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 21:05:59'),
(453, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 21:06:01'),
(454, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 21:06:03'),
(455, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 21:06:12'),
(456, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 21:06:27'),
(457, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 21:18:17'),
(458, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 21:18:38'),
(459, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 21:25:08'),
(460, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 21:25:32'),
(461, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 21:26:27'),
(462, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 21:26:28'),
(463, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 21:27:27'),
(464, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 21:27:31'),
(465, 5, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng nhập', NULL, '2026-08-16 21:27:38'),
(466, 5, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Văn Sơn đã đăng xuất', NULL, '2026-08-16 21:27:58'),
(467, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 21:28:21'),
(468, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 21:28:22'),
(469, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 21:28:49'),
(470, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:04:55'),
(471, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:14:13'),
(472, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:14:14'),
(473, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 22:14:38'),
(474, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:14:43'),
(475, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 22:14:51'),
(476, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 22:15:16'),
(477, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 22:15:32'),
(478, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 22:15:53'),
(479, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:16:11'),
(480, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:16:11'),
(481, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 22:16:49'),
(482, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:16:53'),
(483, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 22:17:01'),
(484, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 22:17:39'),
(485, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:23:28'),
(486, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:23:28'),
(487, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 22:24:39'),
(488, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:24:43'),
(489, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 22:24:50'),
(490, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 22:25:01'),
(491, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:25:23'),
(492, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:25:23'),
(493, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-16 22:26:57'),
(494, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:29:17'),
(495, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:29:33'),
(496, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:29:33'),
(497, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:38:13'),
(498, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:38:24'),
(499, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:38:24'),
(500, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 22:39:57'),
(501, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 22:40:03'),
(502, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 22:40:10'),
(503, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 22:42:35'),
(504, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 22:42:57'),
(505, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 22:42:58'),
(506, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 22:43:43'),
(507, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 23:04:26'),
(508, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-16 23:04:44'),
(509, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-16 23:04:44'),
(510, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-16 23:05:13'),
(511, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 23:05:21'),
(512, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-16 23:05:58'),
(513, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-16 23:06:03'),
(514, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-16 23:06:23'),
(515, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-16 23:06:33'),
(516, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-16 23:06:49'),
(517, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 23:10:13'),
(518, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-16 23:48:01'),
(519, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-16 23:48:05'),
(520, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 00:12:28'),
(521, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 00:16:05'),
(522, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 00:16:41'),
(523, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 00:16:49'),
(524, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 00:20:16'),
(525, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 00:20:50'),
(526, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 00:20:50'),
(527, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 00:21:17'),
(528, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 00:21:20'),
(529, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 00:22:20'),
(530, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 00:26:57'),
(531, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 00:31:39'),
(532, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 00:32:21'),
(533, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 01:29:28'),
(534, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 01:30:34'),
(535, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 01:31:25'),
(536, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 01:37:02'),
(537, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 01:37:02'),
(538, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 01:47:47'),
(539, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 02:32:14'),
(540, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 02:51:40'),
(541, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 09:16:40'),
(542, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 09:21:04'),
(543, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 09:22:03'),
(544, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 09:27:03'),
(545, 11, 'LOGIN', 'USER', NULL, NULL, NULL, 'Khách hàng Demo đã đăng nhập', NULL, '2026-08-17 09:32:20'),
(546, 11, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Khách hàng Demo đã đăng xuất', NULL, '2026-08-17 09:32:39'),
(547, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 09:35:59'),
(548, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 09:37:24'),
(549, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 09:40:25'),
(550, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 09:40:25'),
(551, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 09:42:30'),
(552, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 09:45:26'),
(553, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 09:45:26'),
(554, 1, 'UPDATE', 'PRODUCT', 12, '{\"productId\":12,\"productCode\":\"SP_0012\",\"productName\":\"Nước suối Lavie 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Lavie\",\"description\":\"Nước khoáng thiên nhiên\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"stock\":0,\"minStock\":0,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-15T13:29:41\",\"updatedAt\":\"2026-08-17T09:46:49\"}', '{\"productId\":12,\"productCode\":\"SP_0012\",\"productName\":\"Nước suối Lavie 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"brand\":\"Lavie\",\"description\":\"Nước khoáng thiên nhiên\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1786934937/dat-nuoc-lavie_aemtsn.jpg\",\"stock\":0,\"minStock\":0,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-15T13:29:41\",\"updatedAt\":\"2026-08-17T09:46:49\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-17 09:48:58'),
(555, 1, 'UPDATE', 'PRODUCT', 11, '{\"productId\":11,\"productCode\":\"SP_0011\",\"productName\":\"Bánh quy Cosy 300g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"brand\":\"Cosy\",\"description\":\"Bánh quy bơ giòn tan\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"stock\":0,\"minStock\":0,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-15T13:29:40\",\"updatedAt\":\"2026-08-17T09:46:51\"}', '{\"productId\":11,\"productCode\":\"SP_0011\",\"productName\":\"Bánh quy Cosy 300g\",\"categoryId\":6,\"categoryName\":\"Bánh kẹo\",\"brand\":\"Cosy\",\"description\":\"Bánh quy bơ giòn tan\",\"importPrice\":0,\"sellPrice\":5000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1786934962/banh-cosy-quy-bo_bmc4ip.jpg\",\"stock\":0,\"minStock\":0,\"status\":\"DISABLED\",\"createdAt\":\"2026-08-15T13:29:40\",\"updatedAt\":\"2026-08-17T09:46:51\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-17 09:49:23'),
(556, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 09:49:33'),
(557, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 09:50:08'),
(558, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 09:50:08'),
(559, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 10:03:20'),
(560, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 10:03:29'),
(561, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 10:03:29'),
(562, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 10:10:04'),
(563, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 10:10:38'),
(564, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 10:10:38'),
(565, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 10:11:00'),
(566, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 10:27:38'),
(567, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 10:28:01'),
(568, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 10:28:11'),
(569, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 11:01:11'),
(570, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 11:01:20'),
(571, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 11:02:13'),
(572, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 11:02:41'),
(573, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 11:02:41'),
(574, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 11:20:19'),
(575, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 11:20:46'),
(576, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 11:22:34'),
(577, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 11:37:38'),
(578, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 11:38:44'),
(579, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 11:43:31'),
(580, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 11:54:41'),
(581, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 11:55:21'),
(582, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:04:06'),
(583, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:04:35'),
(584, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:08:31'),
(585, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:09:07'),
(586, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:14:57'),
(587, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:15:30'),
(588, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:24:50'),
(589, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 12:30:43'),
(590, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 12:30:44'),
(591, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 12:31:41'),
(592, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:32:54'),
(593, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:33:09'),
(594, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:36:02'),
(595, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:36:06'),
(596, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 12:37:50'),
(597, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 12:41:51'),
(598, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 12:42:15'),
(599, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 12:42:16'),
(600, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 12:43:27'),
(601, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 12:43:50'),
(602, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 12:43:50'),
(603, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 12:44:38'),
(604, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 12:45:02'),
(605, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 12:45:02'),
(606, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 12:47:21'),
(607, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 12:47:37'),
(608, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 12:47:37'),
(609, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 12:47:55'),
(610, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 12:47:58'),
(611, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 13:13:15'),
(612, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 13:13:50'),
(613, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 13:18:38'),
(614, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 13:18:38'),
(615, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 13:18:45'),
(616, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 13:19:04'),
(617, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-17 13:19:32'),
(618, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 13:19:45'),
(619, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 13:20:02'),
(620, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 13:20:19'),
(621, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 13:25:46'),
(622, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 13:26:04'),
(623, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 13:26:05'),
(624, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 13:35:21'),
(625, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 13:36:32'),
(626, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 13:36:32'),
(627, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 13:37:09'),
(628, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 13:37:14'),
(629, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 13:54:07'),
(630, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 13:54:23'),
(631, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 13:54:23'),
(632, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 14:03:01'),
(633, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 14:03:32'),
(634, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 14:03:33'),
(635, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 14:04:05'),
(636, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 14:04:27'),
(637, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 14:08:58'),
(638, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 14:09:10'),
(639, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 14:09:32'),
(640, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 14:09:51'),
(641, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 14:09:51'),
(642, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-17 14:10:12'),
(643, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 14:10:38'),
(644, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 14:17:24'),
(645, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 14:17:24'),
(646, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 14:20:42'),
(647, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 15:09:13'),
(648, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 15:09:25'),
(649, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 15:09:36'),
(650, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 15:09:50'),
(651, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 16:40:11'),
(652, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 16:41:18'),
(653, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 16:44:03'),
(654, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 16:47:03'),
(655, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 16:47:10'),
(656, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 16:57:10'),
(657, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:09:05'),
(658, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:09:58'),
(659, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:31:27'),
(660, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 17:31:35'),
(661, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 17:32:26'),
(662, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 17:34:07'),
(663, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 17:35:02'),
(664, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:35:16'),
(665, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:44:34'),
(666, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:45:06'),
(667, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:46:23'),
(668, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:46:46'),
(669, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:48:16'),
(670, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:51:00'),
(671, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:55:17'),
(672, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 17:55:37'),
(673, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 17:59:54'),
(674, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:00:24'),
(675, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:07:11'),
(676, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:07:29'),
(677, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:08:42'),
(678, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:08:55'),
(679, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:14:23'),
(680, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:16:04'),
(681, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:19:45'),
(682, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:22:45'),
(683, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:26:28'),
(684, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:26:54'),
(685, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 18:30:39'),
(686, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:30:58'),
(687, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 18:38:39'),
(688, 4, 'SHIFT_CLOSE', 'SHIFT', 17, '{\"shiftId\":17,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T21:00:36\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":3,\"cashSales\":196000,\"cashIn\":1000000,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":17,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-15T21:00:36\",\"endTime\":\"2026-08-17T18:39:09\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":1196000,\"countedCash\":1196000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":3,\"cashSales\":196000,\"cashIn\":1000000,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #17, chenh lech 0', NULL, '2026-08-17 18:39:09'),
(689, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 18:51:40'),
(690, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:01:54'),
(691, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:02:20'),
(692, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:13:34'),
(693, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:18:34'),
(694, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:18:52'),
(695, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:20:16'),
(696, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 19:20:18'),
(697, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:20:23'),
(698, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:21:12'),
(699, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:22:15'),
(700, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 19:23:15'),
(701, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:23:29'),
(702, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:24:06'),
(703, 4, 'SHIFT_OPEN', 'SHIFT', 18, NULL, '{\"shiftId\":18,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:25:15\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #18 voi tien dau ca 1000000', NULL, '2026-08-17 19:25:15'),
(704, 4, 'SHIFT_CLOSE', 'SHIFT', 18, '{\"shiftId\":18,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:25:15\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":18,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:25:15\",\"endTime\":\"2026-08-17T19:26:07\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1000000,\"countedCash\":1000000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #18, chenh lech 0', NULL, '2026-08-17 19:26:07'),
(705, 4, 'SHIFT_OPEN', 'SHIFT', 19, NULL, '{\"shiftId\":19,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:26:23\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #19 voi tien dau ca 1000000', NULL, '2026-08-17 19:26:23'),
(706, 4, 'SHIFT_CLOSE', 'SHIFT', 19, '{\"shiftId\":19,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:26:23\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":19,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T19:26:23\",\"endTime\":\"2026-08-17T19:27\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1000000,\"countedCash\":1000000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #19, chenh lech 0', NULL, '2026-08-17 19:27:00'),
(707, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 19:27:57'),
(708, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:29:21'),
(709, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 19:29:22'),
(710, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:29:57'),
(711, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 19:33:01'),
(712, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 19:34:27'),
(713, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 19:34:27'),
(714, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:35:35'),
(715, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:36:28'),
(716, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:41:02'),
(717, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:41:21'),
(718, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 19:43:11'),
(719, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 19:43:35'),
(720, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 19:43:35'),
(721, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 19:44:21'),
(722, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:47:17'),
(723, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:47:34');
INSERT INTO `AuditLogs` (`LogID`, `UserID`, `Action`, `TableName`, `RecordID`, `OldValue`, `NewValue`, `Detail`, `IPAddress`, `CreatedAt`) VALUES
(724, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:48:03'),
(725, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:48:18'),
(726, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:49:05'),
(727, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:49:20'),
(728, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:49:53'),
(729, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:52:10'),
(730, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 19:53:50'),
(731, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 19:53:50'),
(732, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 19:55:30'),
(733, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 19:55:38'),
(734, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:56:25'),
(735, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 19:57:36'),
(736, 4, 'SHIFT_OPEN', 'SHIFT', 20, NULL, '{\"shiftId\":20,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T20:00:33\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #20 voi tien dau ca 1000000', NULL, '2026-08-17 20:00:33'),
(737, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 20:00:38'),
(738, 4, 'SHIFT_CLOSE', 'SHIFT', 20, '{\"shiftId\":20,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T20:00:33\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":20,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-17T20:00:33\",\"endTime\":\"2026-08-17T20:00:39\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1000000,\"countedCash\":1000000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #20, chenh lech 0', NULL, '2026-08-17 20:00:40'),
(739, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 20:06:49'),
(740, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 20:06:49'),
(741, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 20:08:13'),
(742, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 20:24:51'),
(743, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 20:26:36'),
(744, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 20:26:37'),
(745, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 20:30:03'),
(746, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 20:44:51'),
(747, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 20:46:24'),
(748, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 20:49:54'),
(749, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 21:19:56'),
(750, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 21:19:56'),
(751, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 21:20:01'),
(752, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 21:21:12'),
(753, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 21:26:06'),
(754, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 21:27:28'),
(755, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 21:32:50'),
(756, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 21:33:34'),
(757, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 21:35:58'),
(758, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 21:36:15'),
(759, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 21:45:13'),
(760, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-17 21:48:34'),
(761, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:03:03'),
(762, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:03:38'),
(763, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:04:44'),
(764, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:05:23'),
(765, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:15:28'),
(766, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:15:48'),
(767, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:20:59'),
(768, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 22:21:04'),
(769, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 22:21:04'),
(770, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:21:19'),
(771, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:22:43'),
(772, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"invmgr2\"', NULL, '2026-08-17 22:22:49'),
(773, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 22:23:09'),
(774, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:24:10'),
(775, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 22:24:51'),
(776, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 22:25:18'),
(777, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 22:25:18'),
(778, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 22:25:54'),
(779, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:26:15'),
(780, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:26:19'),
(781, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 22:26:53'),
(782, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 22:27:10'),
(783, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:01:35'),
(784, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:01:51'),
(785, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 23:01:52'),
(786, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 23:01:58'),
(787, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 23:02:03'),
(788, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:02:08'),
(789, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:02:32'),
(790, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:04:16'),
(791, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:04:38'),
(792, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:04:40'),
(793, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:04:40'),
(794, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:04:51'),
(795, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 23:04:59'),
(796, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:05:12'),
(797, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:05:12'),
(798, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:05:29'),
(799, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:05:29'),
(800, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:05:59'),
(801, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:06:14'),
(802, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 23:10:55'),
(803, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:11:33'),
(804, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:11:54'),
(805, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:11:54'),
(806, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:12:13'),
(807, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-17 23:21:49'),
(808, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:21:53'),
(809, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:22:40'),
(810, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-17 23:23:01'),
(811, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:29:48'),
(812, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:30:11'),
(813, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:30:12'),
(814, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:30:30'),
(815, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:30:36'),
(816, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:30:41'),
(817, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:30:48'),
(818, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:34:35'),
(819, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:34:35'),
(820, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:34:37'),
(821, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:34:49'),
(822, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:34:55'),
(823, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-17 23:35:04'),
(824, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:35:19'),
(825, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-17 23:35:33'),
(826, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:39:51'),
(827, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:40:10'),
(828, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:40:10'),
(829, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-17 23:56:24'),
(830, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-17 23:57:34'),
(831, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-17 23:57:50'),
(832, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-17 23:57:50'),
(833, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 00:13:01'),
(834, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 00:15:55'),
(835, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 00:25:04'),
(836, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 00:25:04'),
(837, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 00:26:53'),
(838, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 00:27:15'),
(839, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 00:27:16'),
(840, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 00:28:03'),
(841, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 00:29:14'),
(842, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 00:29:14'),
(843, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 00:29:56'),
(844, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 01:05:24'),
(845, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 01:05:25'),
(846, 1, 'SHIFT_OPEN', 'SHIFT', 21, NULL, '{\"shiftId\":21,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T01:13:52\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #21 voi tien dau ca 0', NULL, '2026-08-18 01:13:52'),
(847, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 01:53:45'),
(848, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 01:57:14'),
(849, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 03:03:32'),
(850, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 03:08:05'),
(851, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 03:08:15'),
(852, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 03:41:46'),
(853, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 04:10:43'),
(854, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 04:21:05'),
(855, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 06:49:48'),
(856, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 06:50:20'),
(857, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 08:27:56'),
(858, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 08:29:33'),
(859, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 08:29:42'),
(860, 4, 'SHIFT_OPEN', 'SHIFT', 22, NULL, '{\"shiftId\":22,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T08:30:07\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #22 voi tien dau ca 1000000', NULL, '2026-08-18 08:30:07'),
(861, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 08:32:12'),
(862, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 08:32:13'),
(863, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 08:37:40'),
(864, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 08:37:53'),
(865, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 08:47:52'),
(866, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 08:48:01'),
(867, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 08:49:59'),
(868, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 08:50:12'),
(869, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 08:50:14'),
(870, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 08:50:50'),
(871, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 08:50:59'),
(872, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 08:51:10'),
(873, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 09:00:52'),
(874, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 09:24:41'),
(875, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 09:24:49'),
(876, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 09:30:35'),
(877, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 09:30:45'),
(878, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 09:33:56'),
(879, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 09:34:57'),
(880, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 09:34:57'),
(881, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 09:48:55'),
(882, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 09:49:31'),
(883, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 09:49:31'),
(884, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 09:51:35'),
(885, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 09:54:44'),
(886, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 09:54:44'),
(887, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 09:55:52'),
(888, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 09:59:10'),
(889, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 10:01:37'),
(890, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 10:23:12'),
(891, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 10:29:47'),
(892, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 10:30:39'),
(893, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 10:31:43'),
(894, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 10:35:43'),
(895, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 10:41:08'),
(896, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 10:41:13'),
(897, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 10:41:26'),
(898, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 10:54:57'),
(899, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 10:55:13'),
(900, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 10:57:12'),
(901, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 10:58:02'),
(902, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:00:08'),
(903, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:00:20'),
(904, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:02:19'),
(905, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:02:31'),
(906, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 11:05:47'),
(907, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 11:05:47'),
(908, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:06:19'),
(909, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:10:47'),
(910, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:11:00'),
(911, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 11:15:27'),
(912, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:16:43'),
(913, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:16:58'),
(914, 1, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 4, NULL, '{\"cashTransactionId\":4,\"transactionCode\":\"CT-1787026727279-81FC15BA\",\"shiftId\":21,\"transactionType\":\"CASH_IN\",\"amount\":1068000,\"reason\":\"tiền khách\",\"createdBy\":1,\"createdByName\":\"Hoàng Trung Nam\",\"createdAt\":\"2026-08-18T11:18:48\"}', 'Thu tien trong ca #21: 1068000 - tiền khách', NULL, '2026-08-18 11:18:48'),
(915, 1, 'SHIFT_CLOSE', 'SHIFT', 21, '{\"shiftId\":21,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T01:13:52\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":3,\"cashSales\":202000,\"cashIn\":1068000,\"cashOut\":0,\"cashRefunds\":1270000}', '{\"shiftId\":21,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T01:13:52\",\"endTime\":\"2026-08-18T11:18:54\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":1,\"closedByName\":\"Hoàng Trung Nam\",\"invoiceCount\":3,\"cashSales\":202000,\"cashIn\":1068000,\"cashOut\":0,\"cashRefunds\":1270000}', 'Dong ca #21, chenh lech 0', NULL, '2026-08-18 11:18:54'),
(916, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:20:46'),
(917, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:20:58'),
(918, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:22:58'),
(919, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:23:13'),
(920, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:27:41'),
(921, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:27:58'),
(922, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:50:37'),
(923, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:50:56'),
(924, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:56:18'),
(925, 3, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"invmgr\"', NULL, '2026-08-18 11:56:33'),
(926, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:56:47'),
(927, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:57:00'),
(928, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 11:57:15'),
(929, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 11:57:29'),
(930, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:00:45'),
(931, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:01:00'),
(932, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:06:41'),
(933, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:12:20'),
(934, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:13:08'),
(935, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:14:56'),
(936, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:21:34'),
(937, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:21:50'),
(938, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:22:17'),
(939, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:22:34'),
(940, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:27:38'),
(941, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 12:28:12'),
(942, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 12:28:15'),
(943, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 12:30:01'),
(944, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 12:30:24'),
(945, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:32:10'),
(946, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 5, NULL, '{\"cashTransactionId\":5,\"transactionCode\":\"CT-1787031175364-43F3EC48\",\"shiftId\":22,\"transactionType\":\"CASH_IN\",\"amount\":7000,\"reason\":\"k\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T12:32:56\"}', 'Thu tien trong ca #22: 7000 - k', NULL, '2026-08-18 12:32:57'),
(947, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 12:37:58'),
(948, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 12:38:00'),
(949, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:39:02'),
(950, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:39:25'),
(951, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:39:42'),
(952, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 12:40:09'),
(953, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 12:40:25'),
(954, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:41:50'),
(955, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:41:52'),
(956, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:42:07'),
(957, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:42:49'),
(958, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:43:06'),
(959, 4, 'SHIFT_CLOSE', 'SHIFT', 22, '{\"shiftId\":22,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T08:30:07\",\"status\":\"OPEN\",\"openingCash\":1000000,\"invoiceCount\":4,\"cashSales\":48000,\"cashIn\":7000,\"cashOut\":0,\"cashRefunds\":41000}', '{\"shiftId\":22,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T08:30:07\",\"endTime\":\"2026-08-18T12:44:34\",\"status\":\"PENDING_APPROVAL\",\"openingCash\":1000000,\"expectedCash\":1014000,\"countedCash\":1014000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":4,\"cashSales\":48000,\"cashIn\":7000,\"cashOut\":0,\"cashRefunds\":41000}', 'Dong ca #22, chenh lech 0', NULL, '2026-08-18 12:44:35'),
(960, 2, 'SHIFT_APPROVE', 'SHIFT', 22, NULL, '{\"shiftId\":22,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T08:30:07\",\"endTime\":\"2026-08-18T12:44:34\",\"status\":\"CLOSED\",\"openingCash\":1000000,\"expectedCash\":1014000,\"countedCash\":1014000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"approvedBy\":2,\"approvedByName\":\"Hà Minh Tuấn\",\"approvedAt\":\"2026-08-18T12:44:55\",\"invoiceCount\":4,\"cashSales\":48000,\"cashIn\":7000,\"cashOut\":0,\"cashRefunds\":41000}', 'Duyet doi soat ca #22, chenh lech 0', NULL, '2026-08-18 12:44:56'),
(961, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:45:59'),
(962, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:46:15'),
(963, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:49:31'),
(964, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:49:51'),
(965, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 12:50:34'),
(966, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 12:50:54'),
(967, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 12:50:56'),
(968, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 12:51:04'),
(969, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 12:54:46'),
(970, 4, 'SHIFT_OPEN', 'SHIFT', 23, NULL, '{\"shiftId\":23,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T12:55:08\",\"status\":\"OPEN\",\"openingCash\":500000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #23 voi tien dau ca 500000', NULL, '2026-08-18 12:55:08'),
(971, 4, 'SHIFT_CLOSE', 'SHIFT', 23, '{\"shiftId\":23,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T12:55:08\",\"status\":\"OPEN\",\"openingCash\":500000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":23,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T12:55:08\",\"endTime\":\"2026-08-18T12:55:12\",\"status\":\"PENDING_APPROVAL\",\"openingCash\":500000,\"expectedCash\":500000,\"countedCash\":500000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #23, chenh lech 0', NULL, '2026-08-18 12:55:12'),
(972, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 12:55:53'),
(973, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 12:57:32'),
(974, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 12:57:41'),
(975, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 12:57:57'),
(976, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 13:01:38'),
(977, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 13:01:45'),
(978, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:03:20'),
(979, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 13:03:32'),
(980, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:03:39'),
(981, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 13:03:41'),
(982, 4, 'SHIFT_OPEN', 'SHIFT', 24, NULL, '{\"shiftId\":24,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:03:59\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #24 voi tien dau ca 0', NULL, '2026-08-18 13:03:59'),
(983, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:06:08'),
(984, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:06:24'),
(985, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 13:08:10'),
(986, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 13:08:18'),
(987, 4, 'SHIFT_CLOSE', 'SHIFT', 24, '{\"shiftId\":24,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:03:59\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":24,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:03:59\",\"endTime\":\"2026-08-18T13:08:56\",\"status\":\"PENDING_APPROVAL\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #24, chenh lech 0', NULL, '2026-08-18 13:08:56'),
(988, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 13:11:48'),
(989, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 13:11:56'),
(990, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 13:12:30'),
(991, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:12:35'),
(992, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:13:15'),
(993, 4, 'SHIFT_OPEN', 'SHIFT', 25, NULL, '{\"shiftId\":25,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:13:15\",\"status\":\"OPEN\",\"openingCash\":500000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #25 voi tien dau ca 500000', NULL, '2026-08-18 13:13:16'),
(994, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:15:57'),
(995, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:16:16'),
(996, 4, 'SHIFT_CLOSE', 'SHIFT', 25, '{\"shiftId\":25,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:13:15\",\"status\":\"OPEN\",\"openingCash\":500000,\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":25,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:13:15\",\"endTime\":\"2026-08-18T13:22:36\",\"status\":\"CLOSED\",\"openingCash\":500000,\"expectedCash\":500000,\"countedCash\":500000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #25, chenh lech 0', NULL, '2026-08-18 13:22:36'),
(997, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:24:25'),
(998, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:24:50'),
(999, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:27:12'),
(1000, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:27:35'),
(1001, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 13:27:55'),
(1002, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 13:28:05'),
(1003, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:33:42'),
(1004, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:34:08'),
(1005, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 13:35:53'),
(1006, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:38:31'),
(1007, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:40:04'),
(1008, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 13:43:25'),
(1009, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 13:43:34'),
(1010, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 13:47:47'),
(1011, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 13:48:19'),
(1012, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 13:50:14'),
(1013, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 13:50:22'),
(1014, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 13:51:10'),
(1015, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 13:52:01'),
(1016, 4, 'SHIFT_OPEN', 'SHIFT', 26, NULL, '{\"shiftId\":26,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:52:19\",\"status\":\"OPEN\",\"openingCash\":600000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #26 voi tien dau ca 600000', NULL, '2026-08-18 13:52:19'),
(1017, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 13:52:51'),
(1018, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 6, NULL, '{\"cashTransactionId\":6,\"transactionCode\":\"CT-1787036007573-E8CF2A67\",\"shiftId\":26,\"transactionType\":\"CASH_IN\",\"amount\":200000,\"reason\":\"tiền lẻ quản lí đưa thối\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T13:53:27\"}', 'Thu tien trong ca #26: 200000 - tiền lẻ quản lí đưa thối', NULL, '2026-08-18 13:53:28'),
(1019, 4, 'CASH_OUT', 'SHIFT_CASH_TRANSACTION', 7, NULL, '{\"cashTransactionId\":7,\"transactionCode\":\"CT-1787036028175-694B6BD8\",\"shiftId\":26,\"transactionType\":\"CASH_OUT\",\"amount\":100000,\"reason\":\"mua bọc đựng rác\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T13:53:48\"}', 'Chi tien trong ca #26: 100000 - mua bọc đựng rác', NULL, '2026-08-18 13:53:48'),
(1020, 4, 'SHIFT_CLOSE', 'SHIFT', 26, '{\"shiftId\":26,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:52:19\",\"status\":\"OPEN\",\"openingCash\":600000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":100000,\"cashRefunds\":0}', '{\"shiftId\":26,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:52:19\",\"endTime\":\"2026-08-18T13:57:40\",\"status\":\"CLOSED\",\"openingCash\":600000,\"expectedCash\":700000,\"countedCash\":700000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":100000,\"cashRefunds\":0}', 'Dong ca #26, chenh lech 0', NULL, '2026-08-18 13:57:40'),
(1021, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 13:59:23'),
(1022, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:00:15'),
(1023, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:00:47'),
(1024, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:01:46'),
(1025, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:03:32'),
(1026, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 14:06:25'),
(1027, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 14:06:32'),
(1028, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 14:08:06'),
(1029, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:09:31'),
(1030, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:09:55'),
(1031, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:11:52'),
(1032, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:12:05'),
(1033, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:15:15'),
(1034, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:18:52'),
(1035, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 14:22:11'),
(1036, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:23:50'),
(1037, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:24:13'),
(1038, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 14:24:34'),
(1039, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 14:24:45'),
(1040, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 14:27:08'),
(1041, 4, 'SHIFT_OPEN', 'SHIFT', 27, NULL, '{\"shiftId\":27,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:27:27\",\"status\":\"OPEN\",\"openingCash\":700000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #27 voi tien dau ca 700000', NULL, '2026-08-18 14:27:27'),
(1042, 4, 'SHIFT_CLOSE', 'SHIFT', 27, '{\"shiftId\":27,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:27:27\",\"status\":\"OPEN\",\"openingCash\":700000,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":27,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:27:27\",\"endTime\":\"2026-08-18T14:28:40\",\"status\":\"CLOSED\",\"openingCash\":700000,\"expectedCash\":700000,\"countedCash\":700000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #27, chenh lech 0', NULL, '2026-08-18 14:28:41'),
(1043, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:28:58'),
(1044, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:29:14'),
(1045, 4, 'SHIFT_OPEN', 'SHIFT', 28, NULL, '{\"shiftId\":28,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:31:03\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #28 voi tien dau ca 0', NULL, '2026-08-18 14:31:03'),
(1046, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 14:34:48'),
(1047, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 14:34:57'),
(1048, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 14:35:02'),
(1049, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:35:55'),
(1050, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:36:07'),
(1051, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 14:36:18'),
(1052, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:36:56'),
(1053, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:37:19'),
(1054, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:45:12'),
(1055, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:45:25'),
(1056, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:50:31'),
(1057, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 14:51:27'),
(1058, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:52:48'),
(1059, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 14:53:10'),
(1060, 4, 'SHIFT_CLOSE', 'SHIFT', 28, '{\"shiftId\":28,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:31:03\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":28,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T14:31:03\",\"endTime\":\"2026-08-18T14:53:23\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #28, chenh lech 0', NULL, '2026-08-18 14:53:24'),
(1061, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 14:53:27'),
(1062, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:54:32'),
(1063, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:54:56'),
(1064, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 14:56:57'),
(1065, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 14:57:38'),
(1066, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 14:57:49'),
(1067, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 14:58:41'),
(1068, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:00:07'),
(1069, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:00:23'),
(1070, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:01:09'),
(1071, 4, 'SHIFT_OPEN', 'SHIFT', 29, NULL, '{\"shiftId\":29,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T15:01:36\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #29 voi tien dau ca 0', NULL, '2026-08-18 15:01:36'),
(1072, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:03:08'),
(1073, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 15:04:01'),
(1074, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 15:04:09'),
(1075, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:04:19'),
(1076, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:04:34'),
(1077, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 15:04:41'),
(1078, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 15:04:58'),
(1079, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 15:04:58'),
(1080, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 15:05:35'),
(1081, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 15:05:47'),
(1082, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:07:09'),
(1083, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:07:32'),
(1084, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:10:16'),
(1085, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:10:31'),
(1086, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:12:06'),
(1087, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:18:01'),
(1088, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:20:31'),
(1089, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:21:32'),
(1090, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:25:29'),
(1091, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:31:28'),
(1092, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:31:42'),
(1093, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:32:41'),
(1094, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 15:33:10');
INSERT INTO `AuditLogs` (`LogID`, `UserID`, `Action`, `TableName`, `RecordID`, `OldValue`, `NewValue`, `Detail`, `IPAddress`, `CreatedAt`) VALUES
(1095, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:33:42'),
(1096, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 15:34:24'),
(1097, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 15:36:11'),
(1098, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:36:52'),
(1099, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 15:38:42'),
(1100, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 15:38:42'),
(1101, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:39:02'),
(1102, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 15:40:13'),
(1103, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:47:06'),
(1104, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:49:41'),
(1105, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 15:50:18'),
(1106, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 15:54:18'),
(1107, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 15:56:14'),
(1108, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 15:56:28'),
(1109, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 8, NULL, '{\"cashTransactionId\":8,\"transactionCode\":\"CT-1787043447990-CCDA6F94\",\"shiftId\":29,\"transactionType\":\"CASH_IN\",\"amount\":200000,\"reason\":\"tiền lẻ\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T15:57:28\"}', 'Thu tien trong ca #29: 200000 - tiền lẻ', NULL, '2026-08-18 15:57:28'),
(1110, 4, 'CASH_OUT', 'SHIFT_CASH_TRANSACTION', 9, NULL, '{\"cashTransactionId\":9,\"transactionCode\":\"CT-1787043466081-609BA5C7\",\"shiftId\":29,\"transactionType\":\"CASH_OUT\",\"amount\":100000,\"reason\":\"mua bao\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T15:57:46\"}', 'Chi tien trong ca #29: 100000 - mua bao', NULL, '2026-08-18 15:57:46'),
(1111, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 15:58:18'),
(1112, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 15:58:18'),
(1113, NULL, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"invmnr\"', NULL, '2026-08-18 16:02:35'),
(1114, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:02:45'),
(1115, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 16:03:45'),
(1116, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 16:04:53'),
(1117, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:04:55'),
(1118, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:07:27'),
(1119, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:12:32'),
(1120, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 16:12:37'),
(1121, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:13:13'),
(1122, 4, 'SHIFT_CLOSE', 'SHIFT', 29, '{\"shiftId\":29,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T15:01:36\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":100000,\"cashRefunds\":0}', '{\"shiftId\":29,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T15:01:36\",\"endTime\":\"2026-08-18T16:13:56\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":100000,\"countedCash\":100000,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":200000,\"cashOut\":100000,\"cashRefunds\":0}', 'Dong ca #29, chenh lech 0', NULL, '2026-08-18 16:13:57'),
(1123, 4, 'SHIFT_OPEN', 'SHIFT', 30, NULL, '{\"shiftId\":30,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:14:06\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #30 voi tien dau ca 0', NULL, '2026-08-18 16:14:07'),
(1124, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:16:08'),
(1125, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:16:36'),
(1126, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:22:28'),
(1127, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:22:40'),
(1128, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:23:09'),
(1129, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:26:18'),
(1130, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:27:15'),
(1131, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:28:16'),
(1132, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 16:32:34'),
(1133, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 16:34:33'),
(1134, 6, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"lan.nguyen\"', NULL, '2026-08-18 16:34:39'),
(1135, 6, 'LOGIN', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng nhập', NULL, '2026-08-18 16:35:30'),
(1136, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:36:44'),
(1137, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 16:39:08'),
(1138, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:39:12'),
(1139, 4, 'SHIFT_CLOSE', 'SHIFT', 30, '{\"shiftId\":30,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:14:06\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":30,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:14:06\",\"endTime\":\"2026-08-18T16:39:24\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #30, chenh lech 0', NULL, '2026-08-18 16:39:24'),
(1140, 4, 'SHIFT_OPEN', 'SHIFT', 31, NULL, '{\"shiftId\":31,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:39:46\",\"status\":\"OPEN\",\"openingCash\":100000,\"openingNote\":\"1\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #31 voi tien dau ca 100000', NULL, '2026-08-18 16:39:47'),
(1141, 4, 'CASH_IN', 'SHIFT_CASH_TRANSACTION', 10, NULL, '{\"cashTransactionId\":10,\"transactionCode\":\"CT-1787046002847-C6BD0632\",\"shiftId\":31,\"transactionType\":\"CASH_IN\",\"amount\":100000,\"reason\":\"1\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T16:40:03\"}', 'Thu tiền trong ca #31: 100000 - 1', NULL, '2026-08-18 16:40:03'),
(1142, 4, 'CASH_OUT', 'SHIFT_CASH_TRANSACTION', 11, NULL, '{\"cashTransactionId\":11,\"transactionCode\":\"CT-1787046017389-83C4CCED\",\"shiftId\":31,\"transactionType\":\"CASH_OUT\",\"amount\":100000,\"reason\":\"2\",\"createdBy\":4,\"createdByName\":\"Lê Hoa Trường Vũ\",\"createdAt\":\"2026-08-18T16:40:17\"}', 'Chi tiền trong ca #31: 100000 - 2', NULL, '2026-08-18 16:40:17'),
(1143, 4, 'SHIFT_CLOSE', 'SHIFT', 31, '{\"shiftId\":31,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:39:46\",\"status\":\"OPEN\",\"openingCash\":100000,\"openingNote\":\"1\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":100000,\"cashOut\":100000,\"cashRefunds\":0}', '{\"shiftId\":31,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T16:39:46\",\"endTime\":\"2026-08-18T16:40:31\",\"status\":\"CLOSED\",\"openingCash\":100000,\"expectedCash\":100000,\"countedCash\":100000,\"cashDifference\":0,\"openingNote\":\"1\",\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":100000,\"cashOut\":100000,\"cashRefunds\":0}', 'Dong ca #31, chenh lech 0', NULL, '2026-08-18 16:40:32'),
(1144, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 16:40:55'),
(1145, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 16:44:24'),
(1146, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:45:46'),
(1147, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:45:58'),
(1148, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 16:49:17'),
(1149, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 16:51:02'),
(1150, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:51:24'),
(1151, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 16:53:59'),
(1152, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:02:30'),
(1153, 6, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng xuất', NULL, '2026-08-18 17:03:47'),
(1154, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 17:03:56'),
(1155, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:06:32'),
(1156, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 17:09:47'),
(1157, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:10:00'),
(1158, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 17:11:56'),
(1159, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:16:34'),
(1160, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:16:47'),
(1161, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:17:38'),
(1162, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:17:50'),
(1163, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:18:51'),
(1164, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:19:09'),
(1165, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:20:43'),
(1166, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:21:07'),
(1167, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:23:55'),
(1168, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 17:24:02'),
(1169, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:24:06'),
(1170, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:24:22'),
(1171, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:25:16'),
(1172, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:25:29'),
(1173, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:28:04'),
(1174, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:28:19'),
(1175, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 17:30:03'),
(1176, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:31:28'),
(1177, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:31:37'),
(1178, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:37:14'),
(1179, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 17:39:39'),
(1180, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:39:48'),
(1181, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:40:03'),
(1182, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 17:41:27'),
(1183, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 17:42:42'),
(1184, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 17:44:13'),
(1185, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 17:45:35'),
(1186, 2, 'SHIFT_APPROVE', 'SHIFT', 24, NULL, '{\"shiftId\":24,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T13:03:59\",\"endTime\":\"2026-08-18T13:08:56\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"approvedBy\":2,\"approvedByName\":\"Hà Minh Tuấn\",\"approvedAt\":\"2026-08-18T17:48:41\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Duyệt đối soát ca #24, chênh lệch 0', NULL, '2026-08-18 17:48:42'),
(1187, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 18:17:43'),
(1188, 6, 'LOGIN', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng nhập', NULL, '2026-08-18 18:17:51'),
(1189, 6, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng xuất', NULL, '2026-08-18 18:19:08'),
(1190, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 18:20:50'),
(1191, 4, 'SHIFT_OPEN', 'SHIFT', 32, NULL, '{\"shiftId\":32,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T18:21:06\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #32 voi tien dau ca 0', NULL, '2026-08-18 18:21:06'),
(1192, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 18:28:01'),
(1193, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 18:29:09'),
(1194, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 18:29:09'),
(1195, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 18:30:04'),
(1196, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 18:31:29'),
(1197, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-18 18:31:45'),
(1198, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-18 18:31:59'),
(1199, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 18:38:32'),
(1200, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 18:40:15'),
(1201, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 18:40:21'),
(1202, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 18:43:03'),
(1203, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 18:43:04'),
(1204, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Nhân viên bán hàng\" (SALES_STAFF)', NULL, '2026-08-18 18:44:08'),
(1205, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 18:44:18'),
(1206, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 19:03:29'),
(1207, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 19:26:37'),
(1208, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 19:26:37'),
(1209, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 19:30:23'),
(1210, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 19:32:29'),
(1211, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 19:32:29'),
(1212, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 19:34:58'),
(1213, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 19:44:24'),
(1214, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 19:55:07'),
(1215, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 20:00:56'),
(1216, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 20:00:57'),
(1217, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:02:06'),
(1218, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:02:47'),
(1219, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:03:46'),
(1220, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:04:32'),
(1221, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:07:13'),
(1222, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 20:08:18'),
(1223, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 20:08:40'),
(1224, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 20:09:17'),
(1225, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 20:09:23'),
(1226, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 20:09:27'),
(1227, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 20:10:25'),
(1228, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 20:10:36'),
(1229, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 20:15:09'),
(1230, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 20:15:09'),
(1231, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 20:20:16'),
(1232, 4, 'SHIFT_CLOSE', 'SHIFT', 32, '{\"shiftId\":32,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T18:21:06\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":32,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T18:21:06\",\"endTime\":\"2026-08-18T20:20:55\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":1,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #32, chenh lech 0', NULL, '2026-08-18 20:20:56'),
(1233, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:21:42'),
(1234, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:23:25'),
(1235, 1, 'SHIFT_OPEN', 'SHIFT', 33, NULL, '{\"shiftId\":33,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T20:24:04\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #33 voi tien dau ca 0', NULL, '2026-08-18 20:24:04'),
(1236, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 20:28:26'),
(1237, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 20:33:10'),
(1238, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 20:33:10'),
(1239, 4, 'SHIFT_OPEN', 'SHIFT', 34, NULL, '{\"shiftId\":34,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T20:34:45\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #34 voi tien dau ca 0', NULL, '2026-08-18 20:34:45'),
(1240, 4, 'SHIFT_CLOSE', 'SHIFT', 34, '{\"shiftId\":34,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T20:34:45\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":34,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T20:34:45\",\"endTime\":\"2026-08-18T20:35:50\",\"status\":\"CLOSED\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":4,\"closedByName\":\"Lê Hoa Trường Vũ\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #34, chenh lech 0', NULL, '2026-08-18 20:35:51'),
(1241, 4, 'SHIFT_OPEN', 'SHIFT', 35, NULL, '{\"shiftId\":35,\"userId\":4,\"userName\":\"Lê Hoa Trường Vũ\",\"startTime\":\"2026-08-18T20:38:28\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #35 voi tien dau ca 0', NULL, '2026-08-18 20:38:28'),
(1242, 6, 'LOGIN', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng nhập', NULL, '2026-08-18 20:39:41'),
(1243, 6, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Nguyễn Thị Lan đã đăng xuất', NULL, '2026-08-18 20:42:57'),
(1244, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:43:09'),
(1245, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 20:44:35'),
(1246, 1, 'UPDATE', 'PRODUCT', 4, '{\"productId\":4,\"productCode\":\"SP_0004\",\"productName\":\"Cà rốt\",\"categoryId\":2,\"categoryName\":\"Rau củ\",\"importPrice\":12000,\"sellPrice\":17000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/ca-rot.jpg\",\"stock\":247,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":4,\"productCode\":\"SP_0004\",\"productName\":\"Cà rốt\",\"categoryId\":2,\"categoryName\":\"Rau củ\",\"importPrice\":12000,\"sellPrice\":17000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1787060763/ca-rot_twbcpb.jpg\",\"stock\":247,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-18 20:46:04'),
(1247, 1, 'UPDATE', 'PRODUCT', 5, '{\"productId\":5,\"productCode\":\"SP_0005\",\"productName\":\"Nước suối 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"importPrice\":4000,\"sellPrice\":6000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/nuoc-suoi.jpg\",\"stock\":500,\"minStock\":30,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":5,\"productCode\":\"SP_0005\",\"productName\":\"Nước suối 500ml\",\"categoryId\":3,\"categoryName\":\"Đồ uống\",\"importPrice\":4000,\"sellPrice\":9000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1787060803/nuoc-suoi_uhocmp.jpg\",\"stock\":500,\"minStock\":30,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-18 20:46:44'),
(1248, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:48:19'),
(1249, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:49:30'),
(1250, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:50:13'),
(1251, 1, 'SHIFT_CLOSE', 'SHIFT', 33, '{\"shiftId\":33,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T20:24:04\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', '{\"shiftId\":33,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T20:24:04\",\"endTime\":\"2026-08-18T20:54:57\",\"status\":\"PENDING_APPROVAL\",\"openingCash\":0,\"expectedCash\":0,\"countedCash\":0,\"cashDifference\":0,\"closedBy\":1,\"closedByName\":\"Hoàng Trung Nam\",\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Dong ca #33, chenh lech 0', NULL, '2026-08-18 20:54:57'),
(1252, 1, 'SHIFT_OPEN', 'SHIFT', 36, NULL, '{\"shiftId\":36,\"userId\":1,\"userName\":\"Hoàng Trung Nam\",\"startTime\":\"2026-08-18T20:55:22\",\"status\":\"OPEN\",\"openingCash\":0,\"invoiceCount\":0,\"cashSales\":0,\"cashIn\":0,\"cashOut\":0,\"cashRefunds\":0}', 'Mo ca #36 voi tien dau ca 0', NULL, '2026-08-18 20:55:22'),
(1253, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 20:56:10'),
(1254, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 20:59:55'),
(1255, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 21:00:16'),
(1256, 1, 'UPDATE', 'PRODUCT', 7, '{\"productId\":7,\"productCode\":\"SP_0007\",\"productName\":\"Cà phê bột 500g\",\"categoryId\":4,\"categoryName\":\"Thực phẩm khô\",\"importPrice\":65000,\"sellPrice\":89000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/ca-phe-bot.jpg\",\"stock\":147,\"minStock\":5,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":7,\"productCode\":\"SP_0007\",\"productName\":\"Cà phê bột 500g\",\"categoryId\":4,\"categoryName\":\"Thực phẩm khô\",\"importPrice\":65000,\"sellPrice\":70000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1787062040/ca-phe-bot_rvdd7o.jpg\",\"stock\":147,\"minStock\":5,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-18 21:07:21'),
(1257, 1, 'UPDATE', 'PRODUCT', 1, '{\"productId\":1,\"productCode\":\"SP_0001\",\"productName\":\"Táo Envy\",\"categoryId\":1,\"categoryName\":\"Trái cây\",\"importPrice\":35000,\"sellPrice\":45000,\"autoPrice\":true,\"imageUrl\":\"uploads/products/tao-envy.jpg\",\"stock\":175,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', '{\"productId\":1,\"productCode\":\"SP_0001\",\"productName\":\"Táo Envy\",\"categoryId\":1,\"categoryName\":\"Trái cây\",\"importPrice\":35000,\"sellPrice\":40000,\"autoPrice\":true,\"imageUrl\":\"https://res.cloudinary.com/dk4todoe8/image/upload/v1787062104/tao-envy_wtde2f.jpg\",\"stock\":175,\"minStock\":10,\"status\":\"ACTIVE\",\"createdAt\":\"2026-08-12T10:53:30\"}', 'Đã cập nhật sản phẩm', NULL, '2026-08-18 21:08:25'),
(1258, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 21:17:53'),
(1259, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 21:18:30'),
(1260, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 21:19:56'),
(1261, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 21:19:57'),
(1262, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-18 23:05:52'),
(1263, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-18 23:06:22'),
(1264, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:06:33'),
(1265, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:06:50'),
(1266, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 23:07:16'),
(1267, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 23:07:16'),
(1268, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý bán hàng\" (SALES_MANAGER)', NULL, '2026-08-18 23:07:39'),
(1269, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:08:00'),
(1270, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:13:21'),
(1271, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:13:28'),
(1272, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:14:08'),
(1273, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:14:22'),
(1274, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:15:51'),
(1275, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:17:26'),
(1276, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:17:35'),
(1277, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:19:37'),
(1278, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:19:52'),
(1279, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:20:02'),
(1280, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:20:17'),
(1281, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:20:34'),
(1282, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:27:34'),
(1283, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:27:49'),
(1284, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-18 23:36:09'),
(1285, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-18 23:37:35'),
(1286, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-18 23:41:09'),
(1287, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-18 23:41:10'),
(1288, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 23:41:27'),
(1289, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-18 23:42:13'),
(1290, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 23:45:31'),
(1291, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-18 23:46:02'),
(1292, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-18 23:48:29'),
(1293, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 09:59:31'),
(1294, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 10:01:23'),
(1295, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:11:35'),
(1296, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:11:35'),
(1297, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 10:19:31'),
(1298, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:19:48'),
(1299, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:19:49'),
(1300, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 10:26:02'),
(1301, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:30:10'),
(1302, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:30:10'),
(1303, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 10:46:27'),
(1304, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:46:48'),
(1305, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:46:49'),
(1306, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 10:51:48'),
(1307, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:52:08'),
(1308, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:52:08'),
(1309, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 10:52:29'),
(1310, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 10:58:57'),
(1311, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 10:58:58'),
(1312, 1, 'CREATE', 'PRODUCT', 14, NULL, '{\"productId\":14,\"productCode\":\"SP_0014\",\"productName\":\"Nước rửa chén Sunlight chanh 694\",\"categoryId\":4,\"categoryName\":\"Thực phẩm khô\",\"brand\":\"Sunlight\",\"unit\":\"Chai\",\"weightVolume\":\"750ml\",\"description\":\"Nước rửa chén hương chanh, đánh bay dầu mỡ hiệu quả.\",\"importPrice\":0,\"sellPrice\":25000,\"autoPrice\":false,\"stock\":0,\"minStock\":18,\"status\":\"ACTIVE\"}', 'Đã thêm mới sản phẩm', NULL, '2026-08-19 10:59:24'),
(1313, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:03:19'),
(1314, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:03:39'),
(1315, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:03:39'),
(1316, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:13:10'),
(1317, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:13:39'),
(1318, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:13:39'),
(1319, 1, 'CREATE', 'SUPPLIER', 6, NULL, '{\"supplierId\":6,\"supplierName\":\"Công ty TNHH Thực phẩm \\u0026 Đồ uống Miền Nam - CN988\",\"address\":\"123 Nguyễn Văn Linh, Quận 7, TP.HCM\",\"phone\":\"0360362297\",\"email\":\"thucphammiennam988@gmail.com\",\"suppliedItems\":\"Nước ngọt, nước suối, nước tăng lực\",\"productCount\":0}', 'Đã thêm mới nhà cung cấp', NULL, '2026-08-19 11:14:01'),
(1320, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:18:06'),
(1321, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:21:57'),
(1322, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:21:57'),
(1323, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:24:35'),
(1324, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:25:05'),
(1325, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:25:06'),
(1326, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:27:27'),
(1327, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:34:34'),
(1328, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:34:34'),
(1329, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:35:34'),
(1330, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:37:19'),
(1331, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:37:19'),
(1332, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 11:38:26'),
(1333, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 11:49:32'),
(1334, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 11:49:33'),
(1335, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 12:01:26'),
(1336, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 12:01:26'),
(1337, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 12:09:04'),
(1338, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 12:10:02'),
(1339, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 12:11:27'),
(1340, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 12:14:35'),
(1341, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 12:18:47'),
(1342, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 12:18:55'),
(1343, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 12:20:53'),
(1344, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 12:21:05'),
(1345, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 12:21:43'),
(1346, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 13:31:28'),
(1347, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 13:50:35'),
(1348, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 13:52:27'),
(1349, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 13:52:36'),
(1350, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 14:01:36'),
(1351, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:02:19'),
(1352, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:05:20'),
(1353, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 14:05:36'),
(1354, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 14:05:42'),
(1355, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:08:07'),
(1356, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:09:32'),
(1357, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:16:44'),
(1358, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:18:50'),
(1359, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:26:16'),
(1360, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 07:26:17'),
(1361, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:40:42'),
(1362, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:50:57'),
(1363, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 14:52:40'),
(1364, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:52:54'),
(1365, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 14:55:57'),
(1366, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:03:17'),
(1367, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 15:04:27'),
(1368, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:05:44'),
(1369, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 08:12:37'),
(1370, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 15:25:17'),
(1371, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:25:35'),
(1372, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:29:39'),
(1373, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 15:29:56'),
(1374, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:30:20'),
(1375, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:32:08'),
(1376, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:34:09'),
(1377, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:39:58'),
(1378, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 15:41:08'),
(1379, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:19:18'),
(1380, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:20:26'),
(1381, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:21:08'),
(1382, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 16:27:26'),
(1383, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 16:27:26'),
(1384, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:28:27'),
(1385, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:30:12'),
(1386, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:30:24'),
(1387, 1, 'UPDATE', 'ROLE_PERMISSION', NULL, NULL, NULL, 'Cập nhật quyền cho vai trò \"Quản lý kho\" (INVENTORY_MANAGER)', NULL, '2026-08-19 16:30:29'),
(1388, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:30:38'),
(1389, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:30:41'),
(1390, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:31:02'),
(1391, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:31:53'),
(1392, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:38:36'),
(1393, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 16:38:42'),
(1394, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 09:39:06'),
(1395, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 09:42:37'),
(1396, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 16:44:37'),
(1397, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:54:05'),
(1398, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 16:54:32'),
(1399, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 16:57:13'),
(1400, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 17:02:56'),
(1401, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 17:03:03'),
(1402, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 17:09:13'),
(1403, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:09:24'),
(1404, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 17:13:11'),
(1405, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 17:15:36'),
(1406, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 17:15:48'),
(1407, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 17:20:09'),
(1408, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 17:20:18'),
(1409, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:21:02'),
(1410, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 17:21:13'),
(1411, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:21:22'),
(1412, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 17:21:51'),
(1413, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:28:18'),
(1414, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 17:28:29'),
(1415, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 17:28:35'),
(1416, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:29:54'),
(1417, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 17:34:18'),
(1418, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 17:34:26'),
(1419, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 17:36:37'),
(1420, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 17:36:47'),
(1421, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 17:39:48'),
(1422, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:43:37'),
(1423, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:49:15'),
(1424, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 17:50:36'),
(1425, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:06:41'),
(1426, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:12:57'),
(1427, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:19:06'),
(1428, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:39:54'),
(1429, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:48:38'),
(1430, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 18:57:37'),
(1431, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:00:49'),
(1432, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:06:08'),
(1433, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:07:40'),
(1434, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:09:36'),
(1435, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:13:27'),
(1436, 3, 'LOGIN_FAILED', 'USER', NULL, NULL, NULL, 'Đăng nhập thất bại với tên đăng nhập \"invmgr\"', NULL, '2026-08-19 19:18:13'),
(1437, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:18:15'),
(1438, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:28:00'),
(1439, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 19:29:38'),
(1440, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:33:39'),
(1441, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:41:26'),
(1442, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:46:23'),
(1443, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:50:02'),
(1444, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 19:59:28'),
(1445, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:01:21'),
(1446, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 20:01:27'),
(1447, 2, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng nhập', NULL, '2026-08-19 20:01:41'),
(1448, 2, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hà Minh Tuấn đã đăng xuất', NULL, '2026-08-19 20:07:28'),
(1449, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 20:08:13'),
(1450, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 20:08:13'),
(1451, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:15:47'),
(1452, 1, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng xuất', NULL, '2026-08-19 20:24:13'),
(1453, 1, 'LOGIN_2FA_SUCCESS', 'USER', NULL, NULL, NULL, 'Xác thực 2FA (Email OTP) thành công khi đăng nhập', NULL, '2026-08-19 20:24:21'),
(1454, 1, 'LOGIN', 'USER', NULL, NULL, NULL, 'Hoàng Trung Nam đã đăng nhập', NULL, '2026-08-19 20:24:21'),
(1455, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:24:28'),
(1456, 4, 'LOGIN', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng nhập', NULL, '2026-08-19 20:24:34'),
(1457, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 20:27:41');
INSERT INTO `AuditLogs` (`LogID`, `UserID`, `Action`, `TableName`, `RecordID`, `OldValue`, `NewValue`, `Detail`, `IPAddress`, `CreatedAt`) VALUES
(1458, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:29:02'),
(1459, 4, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Lê Hoa Trường Vũ đã đăng xuất', NULL, '2026-08-19 20:29:54'),
(1460, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:30:56'),
(1461, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 20:34:29'),
(1462, 11, 'LOGIN', 'USER', NULL, NULL, NULL, 'Khách hàng Demo đã đăng nhập', NULL, '2026-08-19 20:34:43'),
(1463, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:36:28'),
(1464, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 20:37:00'),
(1465, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:42:43'),
(1466, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 20:43:22'),
(1467, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:43:57'),
(1468, 3, 'LOGOUT', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng xuất', NULL, '2026-08-19 20:44:18'),
(1469, 3, 'LOGIN', 'USER', NULL, NULL, NULL, 'Trần Tài Phương đã đăng nhập', NULL, '2026-08-19 20:45:37');

-- --------------------------------------------------------

--
-- Table structure for table `Categories`
--

CREATE TABLE `Categories` (
  `CategoryID` int NOT NULL,
  `CategoryName` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE'
) ;

--
-- Dumping data for table `Categories`
--

INSERT INTO `Categories` (`CategoryID`, `CategoryName`, `Status`) VALUES
(1, 'Trái cây', 'ACTIVE'),
(2, 'Rau củ', 'ACTIVE'),
(3, 'Đồ uống', 'ACTIVE'),
(4, 'Thực phẩm khô', 'ACTIVE'),
(5, 'Sữa các loại', 'ACTIVE'),
(6, 'Bánh kẹo', 'ACTIVE');

-- --------------------------------------------------------

--
-- Table structure for table `ChatConversations`
--

CREATE TABLE `ChatConversations` (
  `ConversationID` int NOT NULL,
  `ConversationType` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CustomerUserID` int DEFAULT NULL,
  `StaffUserIdA` int DEFAULT NULL,
  `StaffUserIdB` int DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `LastMessageAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `IsClosed` tinyint(1) NOT NULL DEFAULT '0',
  `OpenSupportKey` int GENERATED ALWAYS AS ((case when ((`ConversationType` = _utf8mb4'CUSTOMER_SUPPORT') and (`CustomerUserID` is not null) and (`IsClosed` = 0)) then `CustomerUserID` else NULL end)) STORED,
  `StaffDmKey` varchar(50) COLLATE utf8mb4_unicode_ci GENERATED ALWAYS AS ((case when ((`ConversationType` = _utf8mb4'STAFF_DM') and (`StaffUserIdA` is not null) and (`StaffUserIdB` is not null)) then concat(`StaffUserIdA`,_utf8mb4'-',`StaffUserIdB`) else NULL end)) STORED
) ;

--
-- Dumping data for table `ChatConversations`
--

INSERT INTO `ChatConversations` (`ConversationID`, `ConversationType`, `CustomerUserID`, `StaffUserIdA`, `StaffUserIdB`, `CreatedAt`, `LastMessageAt`, `IsClosed`) VALUES
(1, 'CUSTOMER_SUPPORT', 6, NULL, NULL, '2026-08-12 07:53:32', '2026-08-18 20:40:53', 0),
(2, 'STAFF_DM', NULL, 4, 5, '2026-08-12 05:53:32', '2026-08-12 09:53:32', 0),
(3, 'STAFF_DM', NULL, 2, 4, '2026-08-14 19:18:02', '2026-08-14 19:18:03', 0),
(4, 'STAFF_DM', NULL, 1, 4, '2026-08-14 19:18:12', '2026-08-19 20:26:48', 0),
(5, 'STAFF_DM', NULL, 2, 3, '2026-08-15 05:23:08', '2026-08-15 13:15:19', 0),
(6, 'STAFF_DM', NULL, 1, 3, '2026-08-15 05:24:26', '2026-08-19 20:33:17', 0),
(7, 'STAFF_DM', NULL, 3, 4, '2026-08-15 09:09:50', '2026-08-15 09:10:13', 0),
(8, 'STAFF_DM', NULL, 1, 2, '2026-08-15 10:21:44', '2026-08-15 13:15:07', 0);

-- --------------------------------------------------------

--
-- Table structure for table `ChatMessages`
--

CREATE TABLE `ChatMessages` (
  `MessageID` int NOT NULL,
  `ConversationID` int NOT NULL,
  `SenderUserID` int NOT NULL,
  `SenderName` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `FromStaff` tinyint(1) NOT NULL DEFAULT '0',
  `BodyText` longtext COLLATE utf8mb4_unicode_ci,
  `ImagePath` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ImageMime` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `FilePath` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `FileName` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `IsReadByPeer` tinyint(1) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `ChatMessages`
--

INSERT INTO `ChatMessages` (`MessageID`, `ConversationID`, `SenderUserID`, `SenderName`, `FromStaff`, `BodyText`, `ImagePath`, `ImageMime`, `FilePath`, `FileName`, `CreatedAt`, `IsReadByPeer`) VALUES
(1, 1, 6, 'Nguyễn Thị Lan', 0, 'Shop ơi, đơn hàng của mình khi nào giao vậy ạ?', NULL, NULL, NULL, NULL, '2026-08-12 07:53:32', 1),
(2, 1, 4, 'Lê Hoa Trường Vũ', 1, 'Chào chị Lan, đơn hàng đang được đóng gói và sẽ giao trong hôm nay ạ.', NULL, NULL, NULL, NULL, '2026-08-12 10:33:32', 0),
(3, 2, 5, 'Hoàng Văn Sơn', 0, 'Ca chiều nay bên quầy 2 hết nước suối rồi, có ai nhập thêm chưa nhỉ?', NULL, NULL, NULL, NULL, '2026-08-12 09:53:32', 1),
(6, 3, 4, 'Lê Hoa Trường Vũ', 1, 'alo', NULL, NULL, NULL, NULL, '2026-08-14 19:18:02', 0),
(7, 4, 4, 'Lê Hoa Trường Vũ', 1, 'alo', NULL, NULL, NULL, NULL, '2026-08-14 19:18:12', 0),
(8, 1, 5, 'Hoàng Văn Sơn', 1, 'ji', NULL, NULL, NULL, NULL, '2026-08-15 02:24:38', 0),
(13, 4, 4, 'Lê Hoa Trường Vũ', 1, 'alo', NULL, NULL, NULL, NULL, '2026-08-15 09:09:06', 0),
(14, 7, 4, 'Lê Hoa Trường Vũ', 1, 'alo', NULL, NULL, NULL, NULL, '2026-08-15 09:09:50', 0),
(15, 7, 4, 'Lê Hoa Trường Vũ', 1, 'alo', NULL, NULL, NULL, NULL, '2026-08-15 09:10:12', 0),
(26, 8, 1, 'Hoàng Trung Nam', 1, 'hello', NULL, NULL, NULL, NULL, '2026-08-15 10:25:02', 0),
(27, 8, 1, 'Hoàng Trung Nam', 1, 'chào', NULL, NULL, NULL, NULL, '2026-08-15 10:25:09', 0),
(28, 8, 1, 'Hoàng Trung Nam', 1, 'bán đc chưa', NULL, NULL, NULL, NULL, '2026-08-15 10:26:02', 0),
(29, 8, 2, 'Hà Minh Tuấn', 1, 'dậ chưa', NULL, NULL, NULL, NULL, '2026-08-15 10:26:06', 0),
(30, 8, 2, 'Hà Minh Tuấn', 1, 'hả', NULL, NULL, NULL, NULL, '2026-08-15 10:26:16', 0),
(31, 8, 1, 'Hoàng Trung Nam', 1, 'lm', NULL, NULL, NULL, NULL, '2026-08-15 10:26:21', 0),
(33, 6, 1, 'Hoàng Trung Nam', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-15 12:01:00', 0),
(34, 6, 1, 'Hoàng Trung Nam', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-15 12:01:59', 0),
(35, 8, 2, 'Hà Minh Tuấn', 1, 'sldsadsad', NULL, NULL, NULL, NULL, '2026-08-15 12:03:58', 0),
(36, 5, 3, 'Trần Tài Phương', 1, 'rwegw', NULL, NULL, NULL, NULL, '2026-08-15 12:04:07', 0),
(37, 8, 1, 'Hoàng Trung Nam', 1, 's', NULL, NULL, NULL, NULL, '2026-08-15 12:04:10', 0),
(38, 5, 2, 'Hà Minh Tuấn', 1, 'sdada', NULL, NULL, NULL, NULL, '2026-08-15 12:04:20', 0),
(39, 5, 3, 'Trần Tài Phương', 1, 'áda', NULL, NULL, NULL, NULL, '2026-08-15 12:04:38', 0),
(40, 5, 3, 'Trần Tài Phương', 1, 'online', NULL, NULL, NULL, NULL, '2026-08-15 12:31:08', 0),
(41, 5, 2, 'Hà Minh Tuấn', 1, 'koko', NULL, NULL, NULL, NULL, '2026-08-15 12:31:22', 0),
(42, 8, 1, 'Hoàng Trung Nam', 1, 'kk', NULL, NULL, NULL, NULL, '2026-08-15 12:34:52', 0),
(43, 6, 1, 'Hoàng Trung Nam', 1, 'kk', NULL, NULL, NULL, NULL, '2026-08-15 12:34:58', 0),
(44, 6, 3, 'Trần Tài Phương', 1, 'đá', NULL, NULL, NULL, NULL, '2026-08-15 12:35:05', 0),
(45, 8, 1, 'Hoàng Trung Nam', 1, 'sss', NULL, NULL, NULL, NULL, '2026-08-15 13:14:58', 0),
(46, 6, 1, 'Hoàng Trung Nam', 1, 'sss', NULL, NULL, NULL, NULL, '2026-08-15 13:15:02', 0),
(47, 8, 2, 'Hà Minh Tuấn', 1, 'ìksndfkdnflkdssd', NULL, NULL, NULL, NULL, '2026-08-15 13:15:06', 0),
(48, 5, 2, 'Hà Minh Tuấn', 1, 'nfrenfer', NULL, NULL, NULL, NULL, '2026-08-15 13:15:19', 0),
(49, 1, 4, 'Lê Hoa Trường Vũ', 1, NULL, NULL, NULL, 'uploads/chat/files/f_1786969127587_Import_SanPham_Mau.xlsx', 'Import_SanPham_Mau.xlsx', '2026-08-17 19:18:49', 0),
(50, 6, 3, 'Trần Tài Phương', 1, 'eh', NULL, NULL, NULL, NULL, '2026-08-18 20:03:13', 0),
(51, 6, 1, 'Hoàng Trung Nam', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-18 20:03:24', 0),
(52, 1, 6, 'Nguyễn Thị Lan', 0, 'aloo', NULL, NULL, NULL, NULL, '2026-08-18 20:40:53', 0),
(53, 4, 4, 'Lê Hoa Trường Vũ', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-19 20:26:35', 0),
(54, 4, 1, 'Hoàng Trung Nam', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-19 20:26:47', 0),
(55, 6, 3, 'Trần Tài Phương', 1, 'hi', NULL, NULL, NULL, NULL, '2026-08-19 20:30:03', 0),
(56, 6, 3, 'Trần Tài Phương', 1, 'hiii', NULL, NULL, NULL, NULL, '2026-08-19 20:30:07', 0),
(57, 6, 3, 'Trần Tài Phương', 1, 'hiii', NULL, NULL, NULL, NULL, '2026-08-19 20:33:16', 0);

-- --------------------------------------------------------

--
-- Table structure for table `Customers`
--

CREATE TABLE `Customers` (
  `CustomerID` int NOT NULL,
  `CustomerCode` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `MemberPoint` int NOT NULL DEFAULT '0',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `Customers`
--

INSERT INTO `Customers` (`CustomerID`, `CustomerCode`, `MemberPoint`, `CreatedAt`) VALUES
(6, 'CUS_0006', 120, '2026-08-12 10:53:30'),
(7, 'CUS_0007', 35, '2026-08-12 10:53:30'),
(8, 'CUS_0008', 58, '2026-08-12 10:53:30'),
(9, 'CUS_0009', 12, '2026-08-12 10:53:30'),
(10, 'CUS_0010', 0, '2026-08-12 10:53:30'),
(11, 'CUS_0011', 0, '2026-08-12 10:53:32');

-- --------------------------------------------------------

--
-- Table structure for table `Employees`
--

CREATE TABLE `Employees` (
  `UserID` int NOT NULL,
  `EmployeeID` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DateOfBirth` date DEFAULT NULL,
  `Gender` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Salary` decimal(18,2) DEFAULT NULL,
  `HireDate` date NOT NULL DEFAULT (curdate()),
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `Employees`
--

INSERT INTO `Employees` (`UserID`, `EmployeeID`, `DateOfBirth`, `Gender`, `Salary`, `HireDate`, `CreatedAt`) VALUES
(1, 'EMP_0001', NULL, NULL, NULL, '2026-08-12', '2026-08-12 10:53:32'),
(2, 'EMP_0002', NULL, NULL, NULL, '2026-08-12', '2026-08-12 10:53:32'),
(3, 'EMP_0003', NULL, NULL, NULL, '2026-08-12', '2026-08-12 10:53:32'),
(4, 'EMP_0004', NULL, NULL, NULL, '2026-08-12', '2026-08-12 10:53:32'),
(5, 'EMP_0005', NULL, NULL, NULL, '2026-08-12', '2026-08-12 10:53:32');

-- --------------------------------------------------------

--
-- Table structure for table `ExceptionReports`
--

CREATE TABLE `ExceptionReports` (
  `ReportID` int NOT NULL,
  `CreatedBy` int NOT NULL,
  `Content` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `HandledBy` int DEFAULT NULL,
  `HandledAt` datetime DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `ExceptionReports`
--

INSERT INTO `ExceptionReports` (`ReportID`, `CreatedBy`, `Content`, `Status`, `HandledBy`, `HandledAt`, `CreatedAt`) VALUES
(1, 5, 'Khách yêu cầu mua \"Xoài cát Hòa Lộc\" nhưng sản phẩm chưa có trong hệ thống.', 'PENDING', NULL, NULL, '2026-08-12 10:53:31'),
(2, 4, 'Máy quét mã vạch ở quầy 2 quét không lên sản phẩm Nước suối 500ml, nghi lỗi tem.', 'HANDLED', 2, '2026-08-12 04:53:31', '2026-08-12 10:53:31');

-- --------------------------------------------------------

--
-- Table structure for table `InventoryBatch`
--

CREATE TABLE `InventoryBatch` (
  `BatchID` int NOT NULL,
  `BatchCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LotNumber` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ProductID` int NOT NULL,
  `SupplierID` int NOT NULL,
  `ReceiptDetailID` int DEFAULT NULL,
  `ManufactureDate` date DEFAULT NULL,
  `ExpiryDate` date DEFAULT NULL,
  `ImportDate` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ImportPrice` decimal(18,0) NOT NULL,
  `Quantity` int NOT NULL,
  `RemainingQty` int NOT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `InventoryBatch`
--

INSERT INTO `InventoryBatch` (`BatchID`, `BatchCode`, `LotNumber`, `ProductID`, `SupplierID`, `ReceiptDetailID`, `ManufactureDate`, `ExpiryDate`, `ImportDate`, `ImportPrice`, `Quantity`, `RemainingQty`, `Status`, `CreatedAt`) VALUES
(1, 'LOT_000001', 'LOT-TAO-001', 1, 1, 1, '2026-07-30', '2026-09-11', '2026-08-12 10:53:30', '35000', 300, 175, 'ACTIVE', '2026-08-12 10:53:30'),
(2, 'LOT_000002', 'LOT-CHUOI-001', 2, 1, 2, '2026-08-01', '2026-08-26', '2026-08-12 10:53:30', '15000', 400, 395, 'ACTIVE', '2026-08-12 10:53:30'),
(3, 'LOT_000003', 'LOT-TAO-002', 1, 1, 11, '2026-08-08', '2026-09-06', '2026-08-12 10:53:30', '35000', 80, 0, 'DEPLETED', '2026-08-12 10:53:30'),
(4, 'LOT_000004', 'LOT-CHUOI-002', 2, 1, 12, '2026-08-08', '2026-08-22', '2026-08-12 10:53:30', '15000', 60, 0, 'DEPLETED', '2026-08-12 10:53:30'),
(5, 'LOT_000005', 'LOT-NUOC-001', 5, 2, 5, NULL, NULL, '2026-08-12 10:53:30', '4000', 500, 500, 'ACTIVE', '2026-08-12 10:53:30'),
(6, 'LOT_000006', 'LOT-TRAXANH-001', 6, 2, 6, '2026-08-02', '2027-02-08', '2026-08-12 10:53:30', '6000', 300, 270, 'ACTIVE', '2026-08-12 10:53:30'),
(7, 'LOT_000007', 'LOT-CAPHE-001', 7, 2, 7, '2026-08-04', '2027-08-12', '2026-08-12 10:53:30', '65000', 150, 147, 'ACTIVE', '2026-08-12 10:53:30'),
(8, 'LOT_000008', 'LOT-CAPHE-EXP-001', 7, 2, 10, '2025-07-13', '2026-08-07', '2026-08-12 10:53:30', '65000', 20, 0, 'DEPLETED', '2026-08-12 10:53:30'),
(9, 'LOT_000009', 'LOT-CACHUA-001', 3, 3, 3, '2026-08-01', '2026-09-01', '2026-08-12 10:53:30', '17500', 200, 189, 'ACTIVE', '2026-08-12 10:53:30'),
(10, 'LOT_000010', 'LOT-CAROT-001', 4, 3, 4, '2026-08-01', '2026-09-16', '2026-08-12 10:53:30', '12000', 250, 247, 'ACTIVE', '2026-08-12 10:53:30'),
(11, 'LOT_000011', 'LOT-SUA-001', 9, 4, 8, '2026-08-03', '2026-09-06', '2026-08-12 10:53:30', '28000', 200, 198, 'ACTIVE', '2026-08-12 10:53:30'),
(12, 'LOT_000012', 'LOT-BANHQUY-001', 10, 4, 9, '2026-08-03', '2026-10-11', '2026-08-12 10:53:30', '20000', 8, 0, 'ACTIVE', '2026-08-12 10:53:30'),
(16, 'LOT_000016', 'LOT-BQ-022', 10, 4, 18, '2026-08-14', '2026-11-21', '2026-08-14 10:48:27', '20000', 8, 0, 'DEPLETED', '2026-08-14 10:48:27'),
(17, 'LOT_000017', 'LOT_BQ_001', 10, 2, 19, '2026-08-15', '2026-11-14', '2026-08-15 13:52:03', '20000', 100, 99, 'DEPLETED', '2026-08-15 13:52:03'),
(18, 'LOT_000018', 'LOT-BANHQUYBO-002', 10, 4, 20, '2026-08-18', '2026-11-14', '2026-08-18 10:34:11', '20000', 10, 10, 'ACTIVE', '2026-08-18 10:34:11'),
(19, 'LOT_000019', 'LOT-MITOMHAOHAO', 8, 4, 21, '2026-08-18', '2026-08-20', '2026-08-18 15:58:08', '90000', 2, 0, 'DEPLETED', '2026-08-18 15:58:08'),
(20, 'LOT_000020', 'LOT-HAOHAO-003', 8, 4, 22, '2026-08-18', '2026-08-20', '2026-08-18 16:08:42', '90000', 2, 0, 'DEPLETED', '2026-08-18 16:08:42'),
(21, 'LOT_000021', 'LOT-CHUOIGIA-012', 10, 4, 23, '2026-08-18', '2026-08-23', '2026-08-18 21:06:32', '30000', 10, 5, 'DEPLETED', '2026-08-18 21:06:32'),
(22, 'LOT_000022', 'LOT-CHUOIGIA-013', 2, 4, 24, '2026-08-18', '2026-08-23', '2026-08-18 21:07:37', '30000', 10, 9, 'ACTIVE', '2026-08-18 21:07:37');

-- --------------------------------------------------------

--
-- Table structure for table `InventoryTransactions`
--

CREATE TABLE `InventoryTransactions` (
  `TransactionID` bigint NOT NULL,
  `ProductID` int NOT NULL,
  `TransactionType` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Direction` varchar(3) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Quantity` int NOT NULL,
  `StockBefore` int NOT NULL,
  `StockAfter` int NOT NULL,
  `RefTable` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `RefID` int NOT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `Note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ;

--
-- Dumping data for table `InventoryTransactions`
--

INSERT INTO `InventoryTransactions` (`TransactionID`, `ProductID`, `TransactionType`, `Direction`, `Quantity`, `StockBefore`, `StockAfter`, `RefTable`, `RefID`, `CreatedBy`, `CreatedAt`, `Note`) VALUES
(1, 7, 'SUPPLIER_RETURN', 'OUT', 10, 150, 140, 'SupplierReturns', 1, 3, '2026-08-12 10:53:31', 'Trả hàng hết hạn về NCC An Bình'),
(2, 7, 'DISPOSAL', 'OUT', 10, 140, 130, 'StockDisposals', 1, 3, '2026-08-12 10:53:31', 'Hủy lô hết hạn (phần còn lại sau khi đã trả NCC)'),
(3, 10, 'IMPORT', 'IN', 8, 8, 16, 'PurchaseReceipts', 12, 1, '2026-08-14 10:48:27', NULL),
(4, 10, 'SALE', 'OUT', 7, 16, 9, 'Invoices', 23, 4, '2026-08-15 07:48:54', NULL),
(5, 3, 'SALE', 'OUT', 4, 200, 196, 'Invoices', 24, 4, '2026-08-15 07:58:49', NULL),
(6, 10, 'IMPORT', 'IN', 100, 9, 109, 'PurchaseReceipts', 13, 3, '2026-08-15 13:52:03', NULL),
(7, 2, 'DISPOSAL', 'OUT', 2, 460, 458, 'StockDisposals', 2, 3, '2026-08-15 13:56:11', 'Tieu huy EXPIRED TH_000002'),
(8, 10, 'SALE', 'OUT', 2, 109, 107, 'Invoices', 25, 4, '2026-08-15 21:32:43', NULL),
(9, 6, 'RECONCILE_ADJUST', 'OUT', 1, 300, 299, 'StockReconciliation', 172, 3, '2026-08-16 13:36:13', 'Dieu chinh ton thuc te tren bang doi chieu'),
(10, 6, 'RECONCILE_ADJUST', 'IN', 1, 299, 300, 'StockReconciliation', 172, 3, '2026-08-16 14:16:38', 'Dieu chinh ton thuc te tren bang doi chieu'),
(11, 1, 'RECONCILE_ADJUST', 'OUT', 10, 380, 370, 'StockReconciliation', 167, 3, '2026-08-16 14:17:04', 'Dieu chinh ton thuc te tren bang doi chieu'),
(12, 1, 'RECONCILE_ADJUST', 'OUT', 290, 370, 80, 'StockReconciliation', 178, 1, '2026-08-16 19:39:33', NULL),
(13, 2, 'RECONCILE_ADJUST', 'OUT', 400, 458, 58, 'StockReconciliation', 179, 1, '2026-08-16 19:39:33', NULL),
(14, 10, 'RECONCILE_ADJUST', 'OUT', 8, 107, 99, 'StockReconciliation', 180, 1, '2026-08-16 19:39:33', NULL),
(15, 1, 'RECONCILE_ADJUST', 'OUT', 5, 80, 75, 'StockReconciliation', 167, 3, '2026-08-16 20:21:37', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(16, 1, 'RECONCILE_ADJUST', 'IN', 5, 75, 80, 'StockReconciliation', 167, 3, '2026-08-16 20:21:44', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(17, 2, 'RECONCILE_ADJUST', 'OUT', 1, 58, 57, 'StockReconciliation', 168, 3, '2026-08-16 20:22:09', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(18, 2, 'RECONCILE_ADJUST', 'IN', 1, 57, 58, 'StockReconciliation', 168, 3, '2026-08-16 20:22:16', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(19, 1, 'RECONCILE_ADJUST', 'OUT', 10, 80, 70, 'StockReconciliation', 167, 3, '2026-08-16 20:51:59', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(20, 1, 'RECONCILE_ADJUST', 'IN', 10, 370, 380, 'StockReconciliation', 167, 3, '2026-08-16 21:02:01', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(21, 1, 'RECONCILE_ADJUST', 'OUT', 10, 380, 370, 'StockReconciliation', 167, 3, '2026-08-16 21:03:00', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(22, 1, 'RECONCILE_ADJUST', 'IN', 10, 370, 380, 'StockReconciliation', 167, 3, '2026-08-16 21:20:32', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(23, 1, 'DISPOSAL', 'OUT', 10, 380, 370, 'StockDisposals', 3, 3, '2026-08-16 21:24:03', 'Tieu huy DAMAGED TH_000003'),
(24, 1, 'DISPOSAL', 'OUT', 10, 370, 360, 'StockDisposals', 4, 3, '2026-08-16 21:25:24', 'Tieu huy EXPIRED TH_000004'),
(25, 1, 'DISPOSAL', 'OUT', 10, 360, 350, 'StockDisposals', 5, 3, '2026-08-16 21:28:40', 'Tieu huy EXPIRED TH_000005'),
(26, 1, 'DISPOSAL', 'OUT', 10, 350, 340, 'StockDisposals', 6, 3, '2026-08-16 23:26:10', 'Tieu huy EXPIRED TH_000006'),
(27, 1, 'DISPOSAL', 'OUT', 10, 340, 330, 'StockDisposals', 7, 3, '2026-08-16 23:54:02', 'Tieu huy EXPIRED TH_000007'),
(28, 3, 'RECONCILE_ADJUST', 'IN', 4, 196, 200, 'StockReconciliation', 185, 3, '2026-08-17 00:17:21', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(29, 1, 'RECONCILE_ADJUST', 'OUT', 30, 330, 300, 'StockReconciliation', 181, 3, '2026-08-17 00:50:09', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(30, 1, 'DISPOSAL', 'OUT', 50, 300, 250, 'StockDisposals', 8, 3, '2026-08-17 00:51:11', 'Tieu huy EXPIRED TH_000008'),
(31, 1, 'DISPOSAL', 'OUT', 25, 250, 225, 'StockDisposals', 9, 3, '2026-08-17 01:14:11', 'Tieu huy EXPIRED TH_000009'),
(32, 1, 'DISPOSAL', 'OUT', 25, 225, 200, 'StockDisposals', 9, 3, '2026-08-17 01:14:11', 'Tieu huy EXPIRED TH_000009'),
(33, 10, 'RECONCILE_ADJUST', 'IN', 1, 107, 108, 'StockReconciliation', 191, 1, '2026-08-17 01:47:06', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(34, 10, 'RECONCILE_ADJUST', 'OUT', 100, 108, 8, 'StockReconciliation', 191, 1, '2026-08-17 01:49:44', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(35, 10, 'RECONCILE_ADJUST', 'IN', 99, 8, 107, 'StockReconciliation', 191, 1, '2026-08-17 01:49:51', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(36, 10, 'RECONCILE_ADJUST', 'OUT', 1, 107, 106, 'StockReconciliation', 191, 1, '2026-08-17 01:50:14', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(37, 10, 'RECONCILE_ADJUST', 'IN', 2, 106, 108, 'StockReconciliation', 191, 3, '2026-08-17 11:45:08', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(38, 10, 'RECONCILE_ADJUST', 'OUT', 1, 108, 107, 'StockReconciliation', 191, 3, '2026-08-17 11:45:15', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(39, 3, 'RECONCILE_ADJUST', 'OUT', 4, 200, 196, 'StockReconciliation', 185, 3, '2026-08-17 11:56:06', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(40, 3, 'RECONCILE_ADJUST', 'IN', 1, 196, 197, 'StockReconciliation', 185, 3, '2026-08-17 11:56:17', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(41, 3, 'RECONCILE_ADJUST', 'OUT', 1, 197, 196, 'StockReconciliation', 185, 3, '2026-08-17 12:05:01', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(42, 13, 'RECONCILE_ADJUST', 'IN', 1, 0, 1, 'StockReconciliation', 194, 1, '2026-08-17 12:42:41', 'Dieu chinh ton thuc te tren bang doi chieu'),
(43, 13, 'RECONCILE_ADJUST', 'OUT', 1, 1, 0, 'StockReconciliation', 194, 1, '2026-08-17 12:42:56', 'Dieu chinh ton thuc te tren bang doi chieu'),
(44, 13, 'RECONCILE_ADJUST', 'IN', 1, 0, 1, 'StockReconciliation', 194, 1, '2026-08-17 12:44:17', 'Dieu chinh ton thuc te tren bang doi chieu'),
(45, 13, 'RECONCILE_ADJUST', 'OUT', 1, 1, 0, 'StockReconciliation', 194, 1, '2026-08-17 12:44:36', 'Dieu chinh ton thuc te tren bang doi chieu'),
(46, 13, 'RECONCILE_ADJUST', 'IN', 1, 0, 1, 'StockReconciliation', 194, 1, '2026-08-17 12:45:16', 'Dieu chinh ton thuc te tren bang doi chieu'),
(47, 13, 'RECONCILE_ADJUST', 'OUT', 1, 1, 0, 'StockReconciliation', 194, 1, '2026-08-17 12:47:55', 'Dieu chinh ton thuc te tren bang doi chieu'),
(48, 13, 'RECONCILE_ADJUST', 'IN', 1, 0, 1, 'StockReconciliation', 194, 3, '2026-08-17 12:48:23', 'Dieu chinh ton thuc te tren bang doi chieu'),
(49, 13, 'RECONCILE_ADJUST', 'OUT', 1, 1, 0, 'StockReconciliation', 194, 3, '2026-08-17 12:48:33', 'Dieu chinh ton thuc te tren bang doi chieu'),
(50, 2, 'RECONCILE_ADJUST', 'OUT', 3, 458, 455, 'StockReconciliation', 184, 3, '2026-08-17 14:52:53', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(51, 2, 'RECONCILE_ADJUST', 'IN', 3, 455, 458, 'StockReconciliation', 184, 3, '2026-08-17 15:10:17', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(52, 3, 'RECONCILE_ADJUST', 'OUT', 6, 196, 190, 'StockReconciliation', 185, 3, '2026-08-17 15:11:07', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(53, 3, 'RECONCILE_ADJUST', 'IN', 6, 190, 196, 'StockReconciliation', 185, 3, '2026-08-17 15:11:15', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(54, 2, 'RECONCILE_ADJUST', 'OUT', 58, 458, 400, 'StockReconciliation', 184, 3, '2026-08-17 15:11:46', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(55, 2, 'RECONCILE_ADJUST', 'IN', 58, 400, 458, 'StockReconciliation', 184, 3, '2026-08-17 15:11:50', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(56, 2, 'DISPOSAL', 'OUT', 8, 458, 450, 'StockDisposals', 10, 3, '2026-08-17 16:25:29', 'Tieu huy EXPIRED TH_000010'),
(57, 2, 'DISPOSAL', 'OUT', 5, 450, 445, 'StockDisposals', 11, 3, '2026-08-17 16:41:59', 'Tieu huy EXPIRED TH_000011'),
(58, 2, 'SALE', 'OUT', 5, 445, 440, 'Invoices', 26, 4, '2026-08-17 16:44:50', NULL),
(59, 2, 'SALE', 'OUT', 2, 440, 438, 'Invoices', 27, 4, '2026-08-17 17:34:42', NULL),
(60, 2, 'RECONCILE_ADJUST', 'OUT', 3, 438, 435, 'StockReconciliation', 183, 3, '2026-08-17 18:31:49', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(61, 2, 'RECONCILE_ADJUST', 'IN', 5, 435, 440, 'StockReconciliation', 183, 3, '2026-08-17 18:32:04', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(62, 6, 'RECONCILE_ADJUST', 'OUT', 20, 300, 280, 'StockReconciliation', 188, 3, '2026-08-17 18:33:01', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(63, 6, 'RECONCILE_ADJUST', 'IN', 20, 280, 300, 'StockReconciliation', 188, 3, '2026-08-17 18:33:12', 'Dieu chinh ton thuc te theo lo hang tren bang doi chieu'),
(64, 6, 'DISPOSAL', 'OUT', 10, 300, 290, 'StockDisposals', 12, 3, '2026-08-17 18:33:34', 'Tieu huy EXPIRED TH_000012'),
(65, 6, 'DISPOSAL', 'OUT', 10, 290, 280, 'StockDisposals', 13, 3, '2026-08-17 19:32:07', 'Tieu huy EXPIRED TH_000013'),
(66, 6, 'DISPOSAL', 'OUT', 10, 280, 270, 'StockDisposals', 14, 3, '2026-08-17 19:38:52', 'Tieu huy EXPIRED TH_000014'),
(67, 1, 'SUPPLIER_RETURN', 'OUT', 25, 200, 175, 'SupplierReturns', 2, 3, '2026-08-17 19:59:34', 'Trả NCC DAMAGED TRNC_000002'),
(68, 3, 'SALE', 'OUT', 2, 196, 194, 'Invoices', 28, 1, '2026-08-18 01:14:08', NULL),
(69, 3, 'RETURN_IN', 'IN', 1, 194, 195, 'ReturnExchanges', 4, 1, '2026-08-18 01:15:08', NULL),
(70, 7, 'SALE', 'OUT', 8, 150, 142, 'Invoices', 29, 1, '2026-08-18 01:16:05', NULL),
(71, 7, 'RETURN_IN', 'IN', 7, 142, 149, 'ReturnExchanges', 5, 1, '2026-08-18 01:16:51', NULL),
(72, 7, 'SALE', 'OUT', 8, 149, 141, 'Invoices', 30, 1, '2026-08-18 01:21:15', NULL),
(73, 7, 'RETURN_IN', 'IN', 7, 141, 148, 'ReturnExchanges', 6, 1, '2026-08-18 01:22:50', NULL),
(74, 3, 'SALE', 'OUT', 2, 195, 193, 'Invoices', 31, 4, '2026-08-18 08:30:50', NULL),
(75, 3, 'RETURN_IN', 'IN', 1, 193, 194, 'ReturnExchanges', 7, 4, '2026-08-18 08:32:01', NULL),
(76, 3, 'SALE', 'OUT', 1, 194, 193, 'Invoices', 32, 4, '2026-08-18 08:34:20', NULL),
(77, 10, 'SALE', 'OUT', 1, 107, 106, 'Invoices', 32, 4, '2026-08-18 08:34:20', NULL),
(78, 10, 'RETURN_IN', 'IN', 1, 106, 8, 'ReturnExchanges', 8, 4, '2026-08-18 08:34:59', NULL),
(79, 4, 'SALE', 'OUT', 1, 250, 249, 'Invoices', 33, 4, '2026-08-18 08:48:43', NULL),
(80, 3, 'SALE', 'OUT', 1, 193, 192, 'Invoices', 33, 4, '2026-08-18 08:48:43', NULL),
(81, 4, 'RETURN_IN', 'IN', 1, 249, 250, 'ReturnExchanges', 9, 4, '2026-08-18 08:49:24', NULL),
(82, 3, 'RETURN_IN', 'IN', 1, 192, 193, 'ReturnExchanges', 10, 4, '2026-08-18 08:52:11', NULL),
(83, 4, 'SALE', 'OUT', 1, 250, 249, 'Invoices', 34, 4, '2026-08-18 08:52:46', NULL),
(84, 3, 'SALE', 'OUT', 1, 193, 192, 'Invoices', 34, 4, '2026-08-18 08:52:46', NULL),
(85, 4, 'RETURN_IN', 'IN', 1, 249, 250, 'ReturnExchanges', 11, 4, '2026-08-18 08:53:43', NULL),
(86, 10, 'IMPORT', 'IN', 10, 8, 18, 'PurchaseReceipts', 14, 3, '2026-08-18 10:34:11', NULL),
(87, 2, 'DISPOSAL', 'OUT', 45, 440, 395, 'StockDisposals', 15, 3, '2026-08-18 11:32:59', 'Tieu huy EXPIRED TH_000015'),
(88, 3, 'SALE', 'OUT', 1, 192, 191, 'Invoices', 35, 4, '2026-08-18 13:17:25', NULL),
(89, 4, 'SALE', 'OUT', 3, 250, 247, 'Invoices', 36, 4, '2026-08-18 14:31:23', NULL),
(90, 8, 'IMPORT', 'IN', 2, 0, 2, 'PurchaseReceipts', 15, 3, '2026-08-18 15:58:08', NULL),
(91, 8, 'IMPORT', 'IN', 2, 2, 4, 'PurchaseReceipts', 16, 3, '2026-08-18 16:08:42', NULL),
(92, 3, 'SALE', 'OUT', 1, 191, 190, 'Invoices', 37, 4, '2026-08-18 18:23:37', NULL),
(93, 3, 'RETURN_IN', 'IN', 1, 190, 191, 'ReturnExchanges', 12, 4, '2026-08-18 18:26:47', NULL),
(94, 8, 'DISPOSAL', 'OUT', 2, 4, 2, 'StockDisposals', 16, 3, '2026-08-18 19:45:06', 'Tieu huy EXPIRED TH_000016'),
(95, 8, 'DISPOSAL', 'OUT', 1, 2, 1, 'StockDisposals', 17, 3, '2026-08-18 20:05:09', 'Tieu huy EXPIRED TH_000017'),
(96, 8, 'DISPOSAL', 'OUT', 1, 1, 0, 'StockDisposals', 18, 3, '2026-08-18 20:22:21', 'Tieu huy EXPIRED TH_000018'),
(97, 7, 'SALE', 'OUT', 1, 148, 147, 'Invoices', 38, 4, '2026-08-18 20:40:58', NULL),
(98, 3, 'SALE', 'OUT', 1, 191, 190, 'Invoices', 39, 1, '2026-08-18 20:55:58', NULL),
(99, 4, 'SALE', 'OUT', 1, 247, 246, 'Invoices', 39, 1, '2026-08-18 20:55:58', NULL),
(100, 9, 'SALE', 'OUT', 2, 200, 198, 'Invoices', 39, 1, '2026-08-18 20:55:59', NULL),
(101, 3, 'RETURN_IN', 'IN', 1, 190, 191, 'ReturnExchanges', 13, 1, '2026-08-18 20:56:35', NULL),
(102, 4, 'RETURN_IN', 'IN', 1, 246, 247, 'ReturnExchanges', 13, 1, '2026-08-18 20:56:35', NULL),
(103, 10, 'IMPORT', 'IN', 10, 117, 127, 'PurchaseReceipts', 17, 3, '2026-08-18 21:06:32', NULL),
(104, 2, 'IMPORT', 'IN', 10, 395, 405, 'PurchaseReceipts', 18, 3, '2026-08-18 21:07:37', NULL),
(105, 2, 'SALE', 'OUT', 1, 405, 404, 'Invoices', 40, 4, '2026-08-18 21:19:29', NULL),
(106, 10, 'SUPPLIER_RETURN', 'OUT', 8, 127, 119, 'SupplierReturns', 3, 3, '2026-08-19 14:20:09', 'Trả NCC QUALITY TRNC_000003'),
(107, 10, 'DISPOSAL', 'OUT', 5, 119, 114, 'StockDisposals', 19, 3, '2026-08-19 14:23:06', 'Tieu huy EXPIRED TH_000019'),
(108, 3, 'SALE', 'OUT', 3, 191, 188, 'Invoices', 41, 4, '2026-08-19 17:16:50', NULL),
(109, 10, 'SALE', 'OUT', 1, 114, 113, 'Invoices', 41, 4, '2026-08-19 17:16:50', NULL),
(110, 3, 'RETURN_IN', 'IN', 1, 188, 189, 'ReturnExchanges', 14, 4, '2026-08-19 17:37:03', NULL),
(111, 10, 'RETURN_IN', 'IN', 1, 113, 10, 'ReturnExchanges', 14, 4, '2026-08-19 17:37:03', NULL);

-- --------------------------------------------------------

--
-- Table structure for table `InvoiceDetailBatches`
--

CREATE TABLE `InvoiceDetailBatches` (
  `InvoiceDetailID` int NOT NULL,
  `BatchID` int NOT NULL,
  `Quantity` int NOT NULL
) ;

--
-- Dumping data for table `InvoiceDetailBatches`
--

INSERT INTO `InvoiceDetailBatches` (`InvoiceDetailID`, `BatchID`, `Quantity`) VALUES
(46, 12, 7),
(47, 9, 4),
(48, 12, 1),
(48, 17, 1),
(49, 2, 5),
(50, 2, 2),
(51, 9, 2),
(52, 7, 8),
(53, 7, 8),
(54, 9, 2),
(55, 9, 1),
(56, 16, 1),
(57, 10, 1),
(58, 9, 1),
(59, 10, 1),
(60, 9, 1),
(61, 9, 1),
(62, 10, 3),
(63, 9, 1),
(64, 7, 1),
(65, 9, 1),
(66, 10, 1),
(67, 11, 2),
(68, 22, 1),
(69, 9, 3),
(70, 18, 1);

-- --------------------------------------------------------

--
-- Table structure for table `InvoiceDetails`
--

CREATE TABLE `InvoiceDetails` (
  `InvoiceDetailID` int NOT NULL,
  `InvoiceID` int NOT NULL,
  `ProductID` int NOT NULL,
  `Quantity` int NOT NULL,
  `UnitPrice` decimal(18,0) NOT NULL,
  `LineTotal` decimal(18,0) GENERATED ALWAYS AS ((`Quantity` * `UnitPrice`)) STORED
) ;

--
-- Dumping data for table `InvoiceDetails`
--

INSERT INTO `InvoiceDetails` (`InvoiceDetailID`, `InvoiceID`, `ProductID`, `Quantity`, `UnitPrice`) VALUES
(1, 1, 1, 2, '45000'),
(2, 1, 5, 3, '6000'),
(3, 2, 7, 1, '89000'),
(4, 2, 9, 3, '36000'),
(5, 3, 2, 5, '20000'),
(6, 3, 6, 2, '8500'),
(7, 3, 10, 1, '28000'),
(8, 4, 1, 4, '45000'),
(9, 4, 5, 4, '6000'),
(10, 5, 7, 2, '89000'),
(11, 5, 9, 2, '36000'),
(12, 6, 2, 4, '20000'),
(13, 6, 6, 4, '8500'),
(14, 7, 1, 3, '45000'),
(15, 7, 5, 3, '6000'),
(16, 8, 7, 1, '89000'),
(17, 8, 9, 4, '36000'),
(18, 9, 2, 3, '20000'),
(19, 9, 6, 3, '8500'),
(20, 10, 1, 2, '45000'),
(21, 10, 5, 4, '6000'),
(22, 11, 7, 2, '89000'),
(23, 11, 9, 3, '36000'),
(24, 12, 2, 6, '20000'),
(25, 12, 6, 2, '8500'),
(26, 12, 10, 1, '28000'),
(27, 13, 1, 4, '45000'),
(28, 13, 5, 3, '6000'),
(29, 14, 7, 1, '89000'),
(30, 14, 9, 2, '36000'),
(31, 15, 2, 5, '20000'),
(32, 15, 6, 4, '8500'),
(33, 16, 1, 3, '45000'),
(34, 16, 5, 4, '6000'),
(35, 17, 7, 2, '89000'),
(36, 17, 9, 4, '36000'),
(37, 18, 2, 4, '20000'),
(38, 18, 6, 3, '8500'),
(39, 19, 1, 2, '45000'),
(40, 19, 5, 3, '6000'),
(41, 20, 7, 1, '89000'),
(42, 20, 9, 3, '36000'),
(43, 21, 2, 3, '20000'),
(44, 21, 6, 2, '8500'),
(45, 22, 3, 2, '24000'),
(46, 23, 10, 7, '28000'),
(47, 24, 3, 4, '24000'),
(48, 25, 10, 2, '28000'),
(49, 26, 2, 5, '20000'),
(50, 27, 2, 2, '20000'),
(51, 28, 3, 2, '24000'),
(52, 29, 7, 8, '89000'),
(53, 30, 7, 8, '89000'),
(54, 31, 3, 2, '24000'),
(55, 32, 3, 1, '24000'),
(56, 32, 10, 1, '28000'),
(57, 33, 4, 1, '17000'),
(58, 33, 3, 1, '24000'),
(59, 34, 4, 1, '17000'),
(60, 34, 3, 1, '24000'),
(61, 35, 3, 1, '24000'),
(62, 36, 4, 3, '17000'),
(63, 37, 3, 1, '24000'),
(64, 38, 7, 1, '89000'),
(65, 39, 3, 1, '24000'),
(66, 39, 4, 1, '17000'),
(67, 39, 9, 2, '33000'),
(68, 40, 2, 1, '35000'),
(69, 41, 3, 3, '24000'),
(70, 41, 10, 1, '38000');

--
-- Triggers `InvoiceDetails`
--
DELIMITER $$
CREATE TRIGGER `trg_InvoiceDetails_ApplyStock_AI` AFTER INSERT ON `InvoiceDetails` FOR EACH ROW main: BEGIN
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
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_InvoiceDetails_CheckStock_BI` BEFORE INSERT ON `InvoiceDetails` FOR EACH ROW BEGIN
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
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `Invoices`
--

CREATE TABLE `Invoices` (
  `InvoiceID` int NOT NULL,
  `InvoiceCode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ShiftID` int NOT NULL,
  `CreatedBy` int NOT NULL,
  `CustomerID` int DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `SubTotal` decimal(18,0) NOT NULL DEFAULT '0',
  `DiscountAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `PromotionID` int DEFAULT NULL,
  `PromotionCode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `VATRate` decimal(5,2) NOT NULL DEFAULT '8.00',
  `VATAmount` decimal(18,4) GENERATED ALWAYS AS ((case when ((`SubTotal` - `DiscountAmount`) < 0) then 0 else (((`SubTotal` - `DiscountAmount`) * `VATRate`) / 100) end)) STORED,
  `TotalAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `OriginalTotalAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `PaymentMethod` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CASH',
  `PayPalOrderID` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `PayPalCaptureID` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `PayOsOrderCode` bigint DEFAULT NULL,
  `PayOsPaymentLinkID` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `BankTransferReference` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `CancelReason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CancelledAt` datetime DEFAULT NULL,
  `PointsUsed` int NOT NULL DEFAULT '0',
  `PointsDiscountAmount` decimal(18,0) NOT NULL DEFAULT '0'
) ;

--
-- Dumping data for table `Invoices`
--

INSERT INTO `Invoices` (`InvoiceID`, `InvoiceCode`, `ShiftID`, `CreatedBy`, `CustomerID`, `CreatedAt`, `SubTotal`, `DiscountAmount`, `PromotionID`, `PromotionCode`, `VATRate`, `TotalAmount`, `OriginalTotalAmount`, `PaymentMethod`, `PayPalOrderID`, `PayPalCaptureID`, `PayOsOrderCode`, `PayOsPaymentLinkID`, `BankTransferReference`, `Status`, `CancelReason`, `CancelledAt`, `PointsUsed`, `PointsDiscountAmount`) VALUES
(1, 'HD-20260806-A', 1, 4, 6, '2026-08-06 09:00:00', '108000', '0', NULL, NULL, '8.00', '116640', '116640', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(2, 'HD-20260806-B', 1, 5, NULL, '2026-08-06 13:00:00', '197000', '0', NULL, NULL, '8.00', '212760', '212760', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(3, 'HD-20260806-C', 1, 4, 7, '2026-08-06 18:00:00', '145000', '0', NULL, NULL, '8.00', '156600', '156600', 'CARD', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(4, 'HD-20260807-A', 2, 4, 6, '2026-08-07 09:00:00', '204000', '0', NULL, NULL, '8.00', '220320', '220320', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(5, 'HD-20260807-B', 2, 5, NULL, '2026-08-07 13:00:00', '250000', '0', NULL, NULL, '8.00', '270000', '270000', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(6, 'HD-20260807-C', 2, 4, 7, '2026-08-07 18:00:00', '114000', '0', NULL, NULL, '8.00', '123120', '123120', 'PAYPAL', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(7, 'HD-20260808-A', 3, 4, 6, '2026-08-08 09:00:00', '153000', '0', NULL, NULL, '8.00', '165240', '165240', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(8, 'HD-20260808-B', 3, 5, NULL, '2026-08-08 13:00:00', '233000', '0', NULL, NULL, '8.00', '251640', '251640', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(9, 'HD-20260808-C', 3, 4, 7, '2026-08-08 18:00:00', '85500', '0', NULL, NULL, '8.00', '92340', '92340', 'CARD', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(10, 'HD-20260809-A', 4, 4, 6, '2026-08-09 09:00:00', '114000', '0', NULL, NULL, '8.00', '123120', '123120', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(11, 'HD-20260809-B', 4, 5, NULL, '2026-08-09 13:00:00', '286000', '0', NULL, NULL, '8.00', '308880', '308880', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(12, 'HD-20260809-C', 4, 4, 7, '2026-08-09 18:00:00', '165000', '0', NULL, NULL, '8.00', '178200', '178200', 'PAYPAL', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(13, 'HD-20260810-A', 5, 4, 6, '2026-08-10 09:00:00', '198000', '0', NULL, NULL, '8.00', '213840', '213840', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(14, 'HD-20260810-B', 5, 5, NULL, '2026-08-10 13:00:00', '161000', '0', NULL, NULL, '8.00', '173880', '173880', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(15, 'HD-20260810-C', 5, 4, 7, '2026-08-10 18:00:00', '134000', '0', NULL, NULL, '8.00', '144720', '164720', 'CARD', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(16, 'HD-20260811-A', 6, 4, 6, '2026-08-11 09:00:00', '159000', '0', NULL, NULL, '8.00', '171720', '171720', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(17, 'HD-20260811-B', 6, 5, NULL, '2026-08-11 13:00:00', '322000', '0', NULL, NULL, '8.00', '347760', '347760', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(18, 'HD-20260811-C', 6, 4, 7, '2026-08-11 18:00:00', '105500', '0', NULL, NULL, '8.00', '113940', '113940', 'PAYPAL', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(19, 'HD-20260812-A', 7, 4, 6, '2026-08-12 09:00:00', '108000', '0', NULL, NULL, '8.00', '116640', '116640', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(20, 'HD-20260812-B', 7, 5, NULL, '2026-08-12 10:52:31', '197000', '0', NULL, NULL, '8.00', '212760', '212760', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(21, 'HD-20260812-C', 7, 4, 7, '2026-08-12 10:52:31', '77000', '0', NULL, NULL, '8.00', '83160', '83160', 'CARD', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(22, 'HD-20260813-0022', 8, 1, NULL, '2026-08-13 12:13:52', '48000', '0', NULL, NULL, '8.00', '51840', '51840', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(23, 'HD-20260815-0023', 13, 4, NULL, '2026-08-15 07:48:54', '196000', '0', NULL, NULL, '8.00', '211680', '211680', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(24, 'HD-20260815-0024', 14, 4, NULL, '2026-08-15 07:58:49', '96000', '0', NULL, NULL, '8.00', '103680', '103680', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(25, 'HD-20260815-0025', 17, 4, NULL, '2026-08-15 21:32:43', '56000', '0', NULL, NULL, '0.00', '56000', '56000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(26, 'HD-20260817-0026', 17, 4, NULL, '2026-08-17 16:44:50', '100000', '0', NULL, NULL, '0.00', '100000', '100000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(27, 'HD-20260817-0027', 17, 4, NULL, '2026-08-17 17:34:42', '40000', '0', NULL, NULL, '0.00', '40000', '40000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(28, 'HD-20260818-0028', 21, 1, NULL, '2026-08-18 01:14:08', '24000', '0', NULL, NULL, '0.00', '24000', '48000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(29, 'HD-20260818-0029', 21, 1, NULL, '2026-08-18 01:16:05', '89000', '0', NULL, NULL, '0.00', '89000', '712000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(30, 'HD-20260818-0030', 21, 1, NULL, '2026-08-18 01:21:15', '89000', '0', NULL, NULL, '0.00', '89000', '712000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(31, 'HD-20260818-0031', 22, 4, NULL, '2026-08-18 08:30:50', '24000', '0', NULL, NULL, '0.00', '24000', '48000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(32, 'HD-20260818-0032', 22, 4, NULL, '2026-08-18 08:34:20', '24000', '0', NULL, NULL, '0.00', '24000', '52000', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(33, 'HD-20260818-0033', 22, 4, NULL, '2026-08-18 08:48:43', '0', '0', NULL, NULL, '0.00', '0', '41000', 'BANK_TRANSFER', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(34, 'HD-20260818-0034', 22, 4, NULL, '2026-08-18 08:52:46', '24000', '0', NULL, NULL, '0.00', '24000', '41000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(35, 'HD-20260818-0035', 25, 4, NULL, '2026-08-18 13:17:25', '24000', '0', NULL, NULL, '0.00', '24000', '24000', 'PAYPAL', '22826854T5091204Y', '402226695A1962337', NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(36, 'HD-20260818-0036', 28, 4, NULL, '2026-08-18 14:31:23', '51000', '0', NULL, NULL, '0.00', '51000', '51000', 'PAYPAL', '4V122800LR3247931', '91P80555WM688381C', NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(37, 'HD-20260818-0037', 32, 4, NULL, '2026-08-18 18:23:37', '0', '0', NULL, NULL, '0.00', '0', '24000', 'BANK_TRANSFER', NULL, NULL, 1787052177796, '54668f3e266a479cab59de74f8daf651', 'FT26230KM8KD', 'ACTIVE', NULL, NULL, 0, '0'),
(38, 'HD-20260818-0038', 35, 4, NULL, '2026-08-18 20:40:58', '89000', '0', NULL, NULL, '0.00', '89000', '89000', 'BANK_TRANSFER', NULL, NULL, 1787060360763, 'bad80d2e725143b4993f6c3d9841b919', 'FT26230L065F', 'ACTIVE', NULL, NULL, 0, '0'),
(39, 'HD-20260818-0039', 36, 1, NULL, '2026-08-18 20:55:58', '66000', '0', NULL, NULL, '0.00', '66000', '107000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(40, 'HD-20260818-0040', 35, 4, NULL, '2026-08-18 21:19:29', '35000', '0', NULL, NULL, '0.00', '35000', '35000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 0, '0'),
(41, 'HD-20260819-0041', 35, 4, 8, '2026-08-19 17:16:50', '48000', '8727', 5, 'FREESHIP', '0.00', '29673', '68000', 'CASH', NULL, NULL, NULL, NULL, NULL, 'ACTIVE', NULL, NULL, 22, '9600');

--
-- Triggers `Invoices`
--
DELIMITER $$
CREATE TRIGGER `trg_Invoices_BlockDelete` BEFORE DELETE ON `Invoices` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien hoa don; hay huy hoa don kem ly do.';
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_Invoices_CancelRestore_AU` AFTER UPDATE ON `Invoices` FOR EACH ROW main: BEGIN
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
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_Invoices_CancelValidate_BU` BEFORE UPDATE ON `Invoices` FOR EACH ROW BEGIN
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
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `OrderDetailBatches`
--

CREATE TABLE `OrderDetailBatches` (
  `OrderDetailID` int NOT NULL,
  `BatchID` int NOT NULL,
  `Quantity` int NOT NULL
) ;

-- --------------------------------------------------------

--
-- Table structure for table `OrderDetails`
--

CREATE TABLE `OrderDetails` (
  `OrderDetailID` int NOT NULL,
  `OrderID` int NOT NULL,
  `ProductID` int NOT NULL,
  `ProductName` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Quantity` int NOT NULL,
  `UnitPrice` decimal(18,0) NOT NULL,
  `LineTotal` decimal(18,0) GENERATED ALWAYS AS ((`Quantity` * `UnitPrice`)) STORED
) ;

--
-- Dumping data for table `OrderDetails`
--

INSERT INTO `OrderDetails` (`OrderDetailID`, `OrderID`, `ProductID`, `ProductName`, `Quantity`, `UnitPrice`) VALUES
(1, 1, 1, 'Táo Envy', 3, '45000'),
(2, 1, 9, 'Sữa tươi Vinamilk 1L', 2, '36000'),
(3, 2, 7, 'Cà phê bột 500g', 1, '89000'),
(4, 3, 6, 'Trà xanh Không Độ 500ml', 6, '8500'),
(5, 3, 5, 'Nước suối 500ml', 6, '6000'),
(6, 4, 3, 'Cà chua', 2, '24000'),
(7, 5, 2, 'Chuối già', 2, '20000'),
(8, 6, 4, 'Cà rốt', 2, '17000');

-- --------------------------------------------------------

--
-- Table structure for table `Orders`
--

CREATE TABLE `Orders` (
  `OrderID` int NOT NULL,
  `OrderCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CustomerID` int DEFAULT NULL,
  `CustomerName` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CustomerEmail` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CustomerPhone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ShippingAddress` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `SubTotal` decimal(18,0) NOT NULL DEFAULT '0',
  `DiscountAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `PromotionID` int DEFAULT NULL,
  `PromotionCode` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `VATRate` decimal(5,2) NOT NULL DEFAULT '8.00',
  `VATAmount` decimal(18,4) GENERATED ALWAYS AS ((case when ((`SubTotal` - `DiscountAmount`) < 0) then 0 else (((`SubTotal` - `DiscountAmount`) * `VATRate`) / 100) end)) STORED,
  `TotalAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `PaymentMethod` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COD',
  `PaymentStatus` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `PayPalOrderID` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `PayPalCaptureID` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `OrderStatus` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `SeenByAdmin` tinyint(1) NOT NULL DEFAULT '0',
  `CancelReason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CompletedAt` datetime DEFAULT NULL,
  `InvoiceID` int DEFAULT NULL
) ;

--
-- Dumping data for table `Orders`
--

INSERT INTO `Orders` (`OrderID`, `OrderCode`, `CustomerID`, `CustomerName`, `CustomerEmail`, `CustomerPhone`, `ShippingAddress`, `CreatedAt`, `SubTotal`, `DiscountAmount`, `PromotionID`, `PromotionCode`, `VATRate`, `TotalAmount`, `PaymentMethod`, `PaymentStatus`, `PayPalOrderID`, `PayPalCaptureID`, `OrderStatus`, `SeenByAdmin`, `CancelReason`, `CompletedAt`, `InvoiceID`) VALUES
(1, 'DH0001', 6, 'Nguyễn Thị Lan', 'lan.nguyen@gmail.com', '0912345678', '12 Nguyễn Huệ, Q.1, TP.HCM', '2026-08-12 08:53:32', '207000', '0', NULL, NULL, '8.00', '207000', 'COD', 'PENDING', NULL, NULL, 'NEW', 1, NULL, NULL, NULL),
(2, 'DH0002', 7, 'Trần Văn Hùng', 'hung.tran@gmail.com', '0987654321', '45 Lý Thường Kiệt, Q.10, TP.HCM', '2026-08-11 10:53:32', '89000', '0', NULL, NULL, '8.00', '89000', 'PAYPAL', 'PAID', NULL, NULL, 'CONFIRMED', 1, NULL, NULL, NULL),
(3, 'DH0003', 8, 'Phạm Thị Mai', 'mai.pham@gmail.com', '0933112233', '78 Điện Biên Phủ, Bình Thạnh, TP.HCM', '2026-08-10 10:53:32', '87000', '0', NULL, NULL, '8.00', '87000', 'COD', 'PENDING', NULL, NULL, 'SHIPPING', 1, NULL, NULL, NULL),
(4, 'DH0004', 9, 'Lê Anh Đức', 'duc.le@gmail.com', '0977665544', '9 Hoàng Diệu, Hải Châu, Đà Nẵng', '2026-08-08 10:53:32', '48000', '0', NULL, NULL, '8.00', '48000', 'PAYPAL', 'PAID', NULL, NULL, 'COMPLETED', 1, NULL, '2026-08-09 10:53:32', NULL),
(5, 'DH0005', NULL, 'Khách vãng lai - Đỗ Văn Kiên', 'kien.do.guest@gmail.com', '0909998888', '23 Phan Đăng Lưu, Phú Nhuận, TP.HCM', '2026-08-09 10:53:32', '40000', '0', NULL, NULL, '8.00', '40000', 'COD', 'FAILED', NULL, NULL, 'CANCELLED', 1, 'Khách đổi ý, không còn nhu cầu mua nữa', NULL, NULL),
(6, 'DH0006', 11, 'Khách hàng Demo', 'customer1@sims.local', '0901234567', 'sdf', '2026-08-19 20:35:05', '34000', '0', NULL, NULL, '8.00', '34000', 'COD', 'PENDING', NULL, NULL, 'NEW', 1, NULL, NULL, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `Permissions`
--

CREATE TABLE `Permissions` (
  `PermissionID` int NOT NULL,
  `PermissionCode` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `Permissions`
--

INSERT INTO `Permissions` (`PermissionID`, `PermissionCode`, `Description`) VALUES
(1, 'USER_MANAGE', 'Quản lý người dùng (tạo/khóa/gán quyền)'),
(2, 'CATEGORY_MANAGE', 'Quản lý danh mục sản phẩm'),
(3, 'PRODUCT_MANAGE', 'Quản lý sản phẩm, giá bán, mức tồn tối thiểu'),
(4, 'SUPPLIER_MANAGE', 'Quản lý nhà cung cấp'),
(5, 'SYSTEM_CONFIG', 'Cấu hình hệ thống (VAT, chính sách...)'),
(6, 'STOCK_VIEW', 'Xem trạng thái tồn kho'),
(7, 'PRODUCT_SEARCH', 'Tìm kiếm sản phẩm'),
(8, 'INVOICE_CREATE', 'Tạo hóa đơn bán hàng'),
(9, 'INVOICE_CANCEL', 'Hủy hóa đơn'),
(10, 'RETURN_EXCHANGE', 'Xử lý đổi/trả hàng'),
(11, 'RETURN_APPROVE', 'Phê duyệt đổi/trả giá trị lớn'),
(12, 'EXCEPTION_REPORT_SEND', 'Gửi báo cáo ngoại lệ'),
(13, 'EXCEPTION_REPORT_HANDLE', 'Xử lý báo cáo ngoại lệ'),
(14, 'STOCK_IMPORT', 'Nhập hàng vào kho'),
(15, 'STOCK_RECONCILE', 'Đối chiếu kho cuối ngày'),
(16, 'CUSTOMER_MANAGE', 'Quản lý khách hàng'),
(17, 'AUDIT_VIEW', 'Xem nhật ký hệ thống'),
(18, 'REPORT_INVENTORY', 'Báo cáo tồn kho, biểu đồ xu hướng tồn'),
(19, 'REPORT_REVENUE', 'Thống kê doanh thu, biểu đồ xu hướng bán'),
(20, 'REPORT_PROFIT', 'Báo cáo lợi nhuận'),
(21, 'ORDER_VIEW', 'Xem đơn hàng online từ khách'),
(22, 'ORDER_MANAGE', 'Xác nhận / hủy đơn hàng online từ khách'),
(23, 'BACKUP_MANAGE', 'Xem trang Sao lưu & Khôi phục, tự sao lưu / khôi phục DB từ file backup'),
(24, 'RETURN_EXCHANGE_CREATE', 'Tạo yêu cầu đổi/trả hàng cho hóa đơn'),
(25, 'RETURN_EXCHANGE_APPROVE', 'Duyệt / từ chối yêu cầu đổi/trả hàng giá trị lớn'),
(26, 'EXCEPTION_REPORT_CREATE', 'Gửi báo cáo ngoại lệ cho Quản lý bán hàng'),
(27, 'SETTINGS_MANAGE', 'Xem và sửa trang Cài đặt hệ thống (VAT, tên cửa hàng, chính sách đổi trả...)'),
(28, 'SUPPLIER_RETURN_MANAGE', 'Lập phiếu trả hàng lỗi/hỏng về nhà cung cấp'),
(29, 'SHIFT_OPERATE', 'Mo ca, thu/chi va dong/doi soat ca cua chinh nhan vien'),
(30, 'SHIFT_VIEW_ALL', 'Xem lich su ca va chenh lech quy cua tat ca nhan vien'),
(31, 'DASHBOARD_VIEW', 'Xem trang tổng quan'),
(32, 'PRODUCT_VIEW', 'Chỉ xem sản phẩm'),
(33, 'STOCK_ALERT_REPORT', 'Báo cáo hàng sắp hết'),
(34, 'STOCK_ALERT_VIEW', 'Xử lý cảnh báo tồn'),
(35, 'AUDIT_LOG_VIEW', 'Nhật ký audit'),
(36, 'REVENUE_REPORT_VIEW', 'Báo cáo doanh thu'),
(37, 'PROFIT_REPORT_VIEW', 'Báo cáo lợi nhuận'),
(38, 'STOCK_DISPOSE', 'Tiêu huỷ hàng'),
(39, 'STOCK_DISPOSE_VIEW', 'Xem lịch sử tiêu huỷ'),
(40, 'SUPPLIER_RETURN_CREATE', 'Trả hàng nhà cung cấp'),
(41, 'SUPPLIER_RETURN_VIEW', 'Xem trả hàng NCC'),
(42, 'PROMOTION_MANAGE', 'Quản lý khuyến mãi'),
(43, 'RBAC_MANAGE', 'Phân quyền vai trò'),
(97, 'USER_VIEW', 'Chỉ xem tài khoản & nhân viên'),
(98, 'USER_EDIT', 'Chỉ sửa tài khoản & nhân viên'),
(99, 'CUSTOMER_VIEW', 'Chỉ xem khách hàng'),
(100, 'CUSTOMER_EDIT', 'Chỉ sửa khách hàng'),
(101, 'CATEGORY_VIEW', 'Chỉ xem danh mục'),
(102, 'CATEGORY_EDIT', 'Chỉ sửa danh mục'),
(103, 'PRODUCT_EDIT', 'Chỉ sửa sản phẩm'),
(104, 'SUPPLIER_VIEW', 'Chỉ xem nhà cung cấp'),
(105, 'SUPPLIER_EDIT', 'Chỉ sửa nhà cung cấp'),
(106, 'EXCEPTION_REPORT_VIEW', 'Chỉ xem báo cáo ngoại lệ'),
(107, 'STOCK_REPORT_VIEW', 'Báo cáo hàng tồn kho'),
(108, 'INVOICE_VIEW_OWN', 'Xem hóa đơn của chính nhân viên'),
(109, 'INVOICE_VIEW_ALL', 'Xem tất cả hóa đơn'),
(112, 'SHIFT_MONITOR', 'Giám sát ca bán hàng'),
(113, 'SHIFT_APPROVE', 'Duyệt đối soát ca bán hàng');

-- --------------------------------------------------------

--
-- Table structure for table `Products`
--

CREATE TABLE `Products` (
  `ProductID` int NOT NULL,
  `ProductCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ProductName` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CategoryID` int NOT NULL,
  `Brand` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Unit` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `WeightVolume` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ImportPrice` decimal(18,0) NOT NULL,
  `SellPrice` decimal(18,0) NOT NULL,
  `Margin` decimal(18,0) DEFAULT NULL,
  `AutoPrice` tinyint(1) NOT NULL DEFAULT '1',
  `ImageUrl` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Stock` int NOT NULL DEFAULT '0',
  `MinStock` int NOT NULL DEFAULT '5',
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `UpdatedAt` datetime DEFAULT NULL
) ;

--
-- Dumping data for table `Products`
--

INSERT INTO `Products` (`ProductID`, `ProductCode`, `ProductName`, `CategoryID`, `Brand`, `Unit`, `WeightVolume`, `Description`, `ImportPrice`, `SellPrice`, `Margin`, `AutoPrice`, `ImageUrl`, `Stock`, `MinStock`, `Status`, `CreatedAt`, `UpdatedAt`) VALUES
(1, 'SP_0001', 'Táo Envy', 1, NULL, NULL, NULL, NULL, '35000', '40000', '10000', 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1787062104/tao-envy_wtde2f.jpg', 175, 10, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-18 21:08:25'),
(2, 'SP_0002', 'Chuối già', 1, NULL, NULL, NULL, NULL, '30000', '35000', '5000', 1, 'uploads/products/chuoi-gia.jpg', 404, 15, 'ACTIVE', '2026-08-12 10:53:30', NULL),
(3, 'SP_0003', 'Cà chua', 2, NULL, NULL, NULL, NULL, '17500', '24000', '6500', 1, 'uploads/products/ca-chua.jpg', 189, 10, 'ACTIVE', '2026-08-12 10:53:30', NULL),
(4, 'SP_0004', 'Cà rốt', 2, NULL, NULL, NULL, NULL, '12000', '17000', '5000', 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1787060763/ca-rot_twbcpb.jpg', 247, 10, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-18 20:46:04'),
(5, 'SP_0005', 'Nước suối 500ml', 3, NULL, NULL, NULL, NULL, '4000', '9000', '2000', 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1787060803/nuoc-suoi_uhocmp.jpg', 500, 30, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-18 20:46:44'),
(6, 'SP_0006', 'Trà xanh Không Độ 500ml', 3, NULL, NULL, NULL, NULL, '6000', '11000', '2500', 1, 'https://res.cloudinary.com/jcgabkar/image/upload/v1786791955/ymamfyauyxidkkycujo8.jpg', 270, 20, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-15 18:05:56'),
(7, 'SP_0007', 'Cà phê bột 500g', 4, NULL, NULL, NULL, NULL, '65000', '70000', '24000', 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1787062040/ca-phe-bot_rvdd7o.jpg', 147, 5, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-18 21:07:21'),
(8, 'SP_0008', 'Mì tôm Hảo Hảo (thùng)', 4, NULL, NULL, NULL, NULL, '90000', '105000', '15000', 1, 'https://res.cloudinary.com/jcgabkar/image/upload/v1786799749/k1mqe1gx8qivvdc4rttk.jpg', 0, 5, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-18 15:57:13'),
(9, 'SP_0009', 'Sữa tươi Vinamilk 1L', 5, NULL, NULL, NULL, NULL, '28000', '33000', '8000', 1, 'https://res.cloudinary.com/jcgabkar/image/upload/v1786799711/lab1kvrjppicd6gbggel.jpg', 198, 20, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-17 22:21:41'),
(10, 'SP_0010', 'Bánh quy bơ 200g', 6, NULL, NULL, NULL, NULL, '30000', '38000', '8000', 1, 'https://res.cloudinary.com/jcgabkar/image/upload/v1786799625/sczmbnlbdtyop7oxlwav.jpg', 10, 10, 'ACTIVE', '2026-08-12 10:53:30', '2026-08-15 20:13:46'),
(11, 'SP_0011', 'Bánh quy Cosy 300g', 6, 'Cosy', NULL, NULL, 'Bánh quy bơ giòn tan', '0', '5000', NULL, 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786934962/banh-cosy-quy-bo_bmc4ip.jpg', 0, 0, 'DISABLED', '2026-08-15 13:29:40', '2026-08-17 09:49:23'),
(12, 'SP_0012', 'Nước suối Lavie 500ml', 3, 'Lavie', NULL, NULL, 'Nước khoáng thiên nhiên', '0', '5000', NULL, 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786934937/dat-nuoc-lavie_aemtsn.jpg', 0, 0, 'DISABLED', '2026-08-15 13:29:41', '2026-08-17 09:48:58'),
(13, 'SP_0013', 'Cà phê bột Trung Nguyên 500g', 3, 'Trung Nguyên', NULL, NULL, 'Cà phê rang xay nguyên chất', '0', '5000', NULL, 1, 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786880029/ca-phe-trung-nguyen_emvgr4.jpg', 0, 0, 'DISABLED', '2026-08-15 13:29:41', '2026-08-17 09:46:47');

--
-- Triggers `Products`
--
DELIMITER $$
CREATE TRIGGER `trg_Products_AutoStockAlert` AFTER UPDATE ON `Products` FOR EACH ROW BEGIN
    IF NEW.Stock <> OLD.Stock AND NEW.Stock <= NEW.MinStock
       AND NOT EXISTS (SELECT 1 FROM StockAlerts
                       WHERE ProductID = NEW.ProductID AND Status <> 'RESOLVED') THEN
        INSERT INTO StockAlerts (ProductID, AlertType, StockAtReport, ReportedBy)
        VALUES (NEW.ProductID,
                CASE WHEN NEW.Stock <= 0 THEN 'OUT_OF_STOCK' ELSE 'LOW_STOCK' END,
                NEW.Stock, NULL);
    END IF;
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_Products_SyncSellPrice_BI` BEFORE INSERT ON `Products` FOR EACH ROW BEGIN
    IF NEW.AutoPrice = 1 THEN
        SET NEW.SellPrice = NEW.ImportPrice + COALESCE(NEW.Margin, fn_GetDefaultMargin());
    END IF;
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_Products_SyncSellPrice_BU` BEFORE UPDATE ON `Products` FOR EACH ROW BEGIN
    IF NEW.AutoPrice = 1
       AND (NOT (NEW.ImportPrice <=> OLD.ImportPrice)
            OR NOT (NEW.Margin <=> OLD.Margin)
            OR NOT (NEW.AutoPrice <=> OLD.AutoPrice)) THEN
        SET NEW.SellPrice = NEW.ImportPrice + COALESCE(NEW.Margin, fn_GetDefaultMargin());
    END IF;
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `Promotions`
--

CREATE TABLE `Promotions` (
  `PromotionID` int NOT NULL,
  `Code` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DiscountType` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `DiscountValue` decimal(18,0) NOT NULL,
  `MaxDiscountAmount` decimal(18,0) DEFAULT NULL,
  `MinOrderAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `StartDate` date NOT NULL,
  `EndDate` date NOT NULL,
  `UsageLimit` int DEFAULT NULL,
  `UsedCount` int NOT NULL DEFAULT '0',
  `IsActive` tinyint(1) NOT NULL DEFAULT '1',
  `IsDeleted` tinyint(1) NOT NULL DEFAULT '0',
  `DeletedAt` datetime DEFAULT NULL,
  `CreatedBy` int DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `ShowOnBanner` tinyint(1) NOT NULL DEFAULT '0',
  `BannerSortOrder` int DEFAULT NULL
) ;

--
-- Dumping data for table `Promotions`
--

INSERT INTO `Promotions` (`PromotionID`, `Code`, `Name`, `DiscountType`, `DiscountValue`, `MaxDiscountAmount`, `MinOrderAmount`, `StartDate`, `EndDate`, `UsageLimit`, `UsedCount`, `IsActive`, `IsDeleted`, `DeletedAt`, `CreatedBy`, `CreatedAt`, `ShowOnBanner`, `BannerSortOrder`) VALUES
(1, 'SUMMER10', 'Khuyến mãi hè - Giảm 10%', 'PERCENT', '10', '30000', '100000', '2026-07-13', '2027-02-08', 1000, 0, 1, 0, NULL, 1, '2026-08-12 10:53:32', 0, NULL),
(2, 'GIAM50K', 'Giảm ngay 50.000đ', 'AMOUNT', '50000', NULL, '300000', '2026-07-13', '2027-02-08', 500, 0, 1, 0, NULL, 1, '2026-08-12 10:53:32', 0, NULL),
(3, 'WELCOME15', 'Chào thành viên mới - Giảm 15%', 'PERCENT', '15', '40000', '150000', '2026-07-13', '2027-02-08', NULL, 0, 1, 0, NULL, 1, '2026-08-12 10:53:32', 0, NULL),
(4, 'FLASH20', 'Flash sale - Giảm 20%', 'PERCENT', '20', '100000', '200000', '2026-08-12', '2026-09-11', 200, 0, 1, 0, NULL, 1, '2026-08-12 10:53:32', 0, NULL),
(5, 'FREESHIP', 'Ưu đãi 20.000đ', 'AMOUNT', '20000', NULL, '99000', '2026-07-13', '2027-02-08', 9999, 1, 1, 0, NULL, 1, '2026-08-12 10:53:32', 0, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `PurchaseReceiptDetails`
--

CREATE TABLE `PurchaseReceiptDetails` (
  `ReceiptDetailID` int NOT NULL,
  `ReceiptID` int NOT NULL,
  `ProductID` int NOT NULL,
  `Quantity` int NOT NULL,
  `ImportPrice` decimal(18,0) NOT NULL,
  `LotNumber` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ManufactureDate` date DEFAULT NULL,
  `ExpiryDate` date DEFAULT NULL
) ;

--
-- Dumping data for table `PurchaseReceiptDetails`
--

INSERT INTO `PurchaseReceiptDetails` (`ReceiptDetailID`, `ReceiptID`, `ProductID`, `Quantity`, `ImportPrice`, `LotNumber`, `ManufactureDate`, `ExpiryDate`) VALUES
(1, 1, 1, 300, '35000', 'LOT-TAO-001', '2026-07-30', '2026-09-11'),
(2, 1, 2, 400, '15000', 'LOT-CHUOI-001', '2026-08-01', '2026-08-26'),
(3, 2, 3, 200, '17500', 'LOT-CACHUA-001', '2026-08-01', '2026-09-01'),
(4, 2, 4, 250, '12000', 'LOT-CAROT-001', '2026-08-01', '2026-09-16'),
(5, 3, 5, 500, '4000', 'LOT-NUOC-001', NULL, NULL),
(6, 3, 6, 300, '6000', 'LOT-TRAXANH-001', '2026-08-02', '2027-02-08'),
(7, 3, 7, 150, '65000', 'LOT-CAPHE-001', '2026-08-04', '2027-08-12'),
(8, 4, 9, 200, '28000', 'LOT-SUA-001', '2026-08-03', '2026-09-06'),
(9, 4, 10, 8, '20000', 'LOT-BANHQUY-001', '2026-08-03', '2026-10-11'),
(10, 5, 7, 20, '65000', 'LOT-CAPHE-EXP-001', '2025-07-13', '2026-08-07'),
(11, 6, 1, 80, '35000', 'LOT-TAO-002', '2026-08-08', '2026-09-06'),
(12, 6, 2, 60, '15000', 'LOT-CHUOI-002', '2026-08-08', '2026-08-22'),
(16, 10, 10, 10, '20000', 'LOT-BANHQUY-002', '2026-08-14', '2026-09-12'),
(17, 11, 10, 10, '25000', 'LOT-BANHQUY-002', '2026-08-14', '2026-10-16'),
(18, 12, 10, 8, '20000', 'LOT-BQ-022', '2026-08-14', '2026-11-21'),
(19, 13, 10, 100, '20000', 'LOT_BQ_001', '2026-08-15', '2026-11-14'),
(20, 14, 10, 10, '20000', 'LOT-BANHQUYBO-002', '2026-08-18', '2026-11-14'),
(21, 15, 8, 2, '90000', 'LOT-MITOMHAOHAO', '2026-08-18', '2026-08-20'),
(22, 16, 8, 2, '90000', 'LOT-HAOHAO-003', '2026-08-18', '2026-08-20'),
(23, 17, 10, 10, '30000', 'LOT-CHUOIGIA-012', '2026-08-18', '2026-08-23'),
(24, 18, 2, 10, '30000', 'LOT-CHUOIGIA-013', '2026-08-18', '2026-08-23');

--
-- Triggers `PurchaseReceiptDetails`
--
DELIMITER $$
CREATE TRIGGER `trg_PurchaseReceiptDetails_Insert` AFTER INSERT ON `PurchaseReceiptDetails` FOR EACH ROW BEGIN
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
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `PurchaseReceipts`
--

CREATE TABLE `PurchaseReceipts` (
  `ReceiptID` int NOT NULL,
  `ReceiptCode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `SupplierID` int NOT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `TotalAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED'
) ;

--
-- Dumping data for table `PurchaseReceipts`
--

INSERT INTO `PurchaseReceipts` (`ReceiptID`, `ReceiptCode`, `SupplierID`, `CreatedBy`, `CreatedAt`, `TotalAmount`, `Status`) VALUES
(1, 'PN-20260802-001', 1, 3, '2026-08-12 10:53:30', '16500000', 'COMPLETED'),
(2, 'PN-20260803-001', 3, 3, '2026-08-12 10:53:30', '6500000', 'COMPLETED'),
(3, 'PN-20260804-001', 2, 3, '2026-08-12 10:53:30', '13550000', 'COMPLETED'),
(4, 'PN-20260805-001', 4, 3, '2026-08-12 10:53:30', '5760000', 'COMPLETED'),
(5, 'PN-20260713-002', 2, 3, '2026-08-12 10:53:30', '1300000', 'COMPLETED'),
(6, 'PN-20260809-002', 1, 3, '2026-08-12 10:53:30', '3700000', 'COMPLETED'),
(10, 'PN_000010', 4, 1, '2026-08-14 05:22:27', '200000', 'COMPLETED'),
(11, 'PN_000011', 4, 3, '2026-08-14 05:45:16', '250000', 'COMPLETED'),
(12, 'PN_000012', 4, 1, '2026-08-14 10:48:26', '160000', 'COMPLETED'),
(13, 'PN_000013', 2, 3, '2026-08-15 13:52:03', '2000000', 'COMPLETED'),
(14, 'PN_000014', 4, 3, '2026-08-18 10:34:11', '200000', 'COMPLETED'),
(15, 'PN_000015', 4, 3, '2026-08-18 15:58:07', '180000', 'COMPLETED'),
(16, 'PN_000016', 4, 3, '2026-08-18 16:08:42', '180000', 'COMPLETED'),
(17, 'PN_000017', 4, 3, '2026-08-18 21:06:32', '300000', 'COMPLETED'),
(18, 'PN_000018', 4, 3, '2026-08-18 21:07:37', '300000', 'COMPLETED');

--
-- Triggers `PurchaseReceipts`
--
DELIMITER $$
CREATE TRIGGER `trg_PurchaseReceipts_BlockDelete` BEFORE DELETE ON `PurchaseReceipts` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien phieu nhap kho.';
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `ReturnExchangeDetailBatches`
--

CREATE TABLE `ReturnExchangeDetailBatches` (
  `ReturnDetailID` int NOT NULL,
  `BatchID` int NOT NULL,
  `Quantity` int NOT NULL
) ;

--
-- Dumping data for table `ReturnExchangeDetailBatches`
--

INSERT INTO `ReturnExchangeDetailBatches` (`ReturnDetailID`, `BatchID`, `Quantity`) VALUES
(6, 9, 1),
(7, 7, 7),
(8, 7, 7),
(9, 9, 1),
(10, 16, 1),
(11, 10, 1),
(12, 9, 1),
(13, 10, 1),
(14, 9, 1),
(15, 9, 1),
(16, 10, 1),
(17, 9, 1),
(18, 18, 1);

-- --------------------------------------------------------

--
-- Table structure for table `ReturnExchangeDetails`
--

CREATE TABLE `ReturnExchangeDetails` (
  `ReturnDetailID` int NOT NULL,
  `ReturnID` int NOT NULL,
  `ProductID` int NOT NULL,
  `Quantity` int NOT NULL,
  `Direction` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `UnitPrice` decimal(18,0) NOT NULL
) ;

--
-- Dumping data for table `ReturnExchangeDetails`
--

INSERT INTO `ReturnExchangeDetails` (`ReturnDetailID`, `ReturnID`, `ProductID`, `Quantity`, `Direction`, `UnitPrice`) VALUES
(1, 1, 2, 1, 'IN', '20000'),
(2, 2, 7, 1, 'IN', '89000'),
(3, 2, 9, 2, 'OUT', '36000'),
(4, 3, 3, 1, 'IN', '24000'),
(5, 3, 2, 1, 'OUT', '20000'),
(6, 4, 3, 1, 'IN', '24000'),
(7, 5, 7, 7, 'IN', '89000'),
(8, 6, 7, 7, 'IN', '89000'),
(9, 7, 3, 1, 'IN', '24000'),
(10, 8, 10, 1, 'IN', '28000'),
(11, 9, 4, 1, 'IN', '17000'),
(12, 10, 3, 1, 'IN', '24000'),
(13, 11, 4, 1, 'IN', '17000'),
(14, 12, 3, 1, 'IN', '24000'),
(15, 13, 3, 1, 'IN', '24000'),
(16, 13, 4, 1, 'IN', '17000'),
(17, 14, 3, 1, 'IN', '24000'),
(18, 14, 10, 1, 'IN', '38000');

-- --------------------------------------------------------

--
-- Table structure for table `ReturnExchanges`
--

CREATE TABLE `ReturnExchanges` (
  `ReturnID` int NOT NULL,
  `InvoiceID` int NOT NULL,
  `Type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `RejectionReason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `TotalValue` decimal(18,0) NOT NULL DEFAULT '0',
  `RequiresApproval` tinyint(1) NOT NULL DEFAULT '0',
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING',
  `ApprovedBy` int DEFAULT NULL,
  `ApprovedAt` datetime DEFAULT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `DiscountShare` decimal(18,0) NOT NULL DEFAULT '0',
  `PointsShare` decimal(18,0) NOT NULL DEFAULT '0',
  `RefundMethod` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `RefundShiftID` int DEFAULT NULL,
  `RefundTransactionID` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `RefundStatus` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `RefundedBy` int DEFAULT NULL,
  `RefundedAt` datetime DEFAULT NULL
) ;

--
-- Dumping data for table `ReturnExchanges`
--

INSERT INTO `ReturnExchanges` (`ReturnID`, `InvoiceID`, `Type`, `Reason`, `RejectionReason`, `TotalValue`, `RequiresApproval`, `Status`, `ApprovedBy`, `ApprovedAt`, `CreatedBy`, `CreatedAt`, `DiscountShare`, `PointsShare`, `RefundMethod`, `RefundShiftID`, `RefundTransactionID`, `RefundStatus`, `RefundedBy`, `RefundedAt`) VALUES
(1, 15, 'RETURN', 'Khách phản ánh chuối bị dập, xin trả lại 1 nải', NULL, '20000', 0, 'APPROVED', 2, '2026-08-12 10:53:31', 4, '2026-08-12 10:53:31', '0', '0', 'CARD', NULL, NULL, 'PENDING', NULL, NULL),
(2, 8, 'EXCHANGE', 'Khách đổi cà phê bột lấy sữa tươi do đặt nhầm', NULL, '89000', 1, 'PENDING', NULL, NULL, 5, '2026-08-12 10:53:31', '0', '0', NULL, NULL, NULL, 'NONE', NULL, NULL),
(3, 22, 'EXCHANGE', '[', NULL, '25920', 1, 'APPROVED', 1, '2026-08-13 14:47:56', 1, '2026-08-13 14:47:49', '0', '0', NULL, NULL, NULL, 'NONE', NULL, NULL),
(4, 28, 'RETURN', 'bị dập', NULL, '24000', 0, 'APPROVED', 1, '2026-08-18 01:15:08', 1, '2026-08-18 01:15:08', '0', '0', 'CASH', 21, 'LEGACY-CASH-RET-4', 'COMPLETED', 1, '2026-08-18 01:15:08'),
(5, 29, 'RETURN', 'đổi ý', NULL, '623000', 1, 'APPROVED', 1, '2026-08-18 01:16:51', 1, '2026-08-18 01:16:35', '0', '0', 'CASH', 21, 'LEGACY-CASH-RET-5', 'COMPLETED', 1, '2026-08-18 01:16:51'),
(6, 30, 'RETURN', 'đổi ý', NULL, '623000', 1, 'APPROVED', 1, '2026-08-18 01:22:50', 1, '2026-08-18 01:21:42', '0', '0', 'CASH', 21, 'LEGACY-CASH-RET-6', 'COMPLETED', 1, '2026-08-18 01:22:50'),
(7, 31, 'RETURN', 'bị hư', NULL, '24000', 0, 'APPROVED', 4, '2026-08-18 08:32:01', 4, '2026-08-18 08:32:00', '0', '0', 'CASH', 22, 'CASH-22-RET-7', 'COMPLETED', 4, '2026-08-18 08:32:01'),
(8, 32, 'RETURN', 'bánh bị vỡ', NULL, '28000', 0, 'APPROVED', 4, '2026-08-18 08:34:59', 4, '2026-08-18 08:34:59', '0', '0', 'BANK_TRANSFER', NULL, 'VCB-REF-20260818-001', 'COMPLETED', 2, '2026-08-18 08:39:53'),
(9, 33, 'RETURN', 'bị cắn', NULL, '17000', 0, 'APPROVED', 4, '2026-08-18 08:49:24', 4, '2026-08-18 08:49:24', '0', '0', 'BANK_TRANSFER', NULL, NULL, 'PENDING', NULL, NULL),
(10, 33, 'RETURN', 'bị dập', NULL, '24000', 0, 'APPROVED', 4, '2026-08-18 08:52:11', 4, '2026-08-18 08:52:11', '0', '0', 'CASH', 22, 'CASH-22-RET-10', 'COMPLETED', 4, '2026-08-18 08:52:11'),
(11, 34, 'RETURN', 'bị hư', NULL, '17000', 0, 'APPROVED', 4, '2026-08-18 08:53:43', 4, '2026-08-18 08:53:43', '0', '0', 'BANK_TRANSFER', NULL, NULL, 'PENDING', NULL, NULL),
(12, 37, 'RETURN', 'kkkkk', NULL, '24000', 0, 'APPROVED', 4, '2026-08-18 18:26:47', 4, '2026-08-18 18:26:47', '0', '0', 'BANK_TRANSFER', NULL, NULL, 'PENDING', NULL, NULL),
(13, 39, 'RETURN', 'a', NULL, '41000', 0, 'APPROVED', 1, '2026-08-18 20:56:35', 1, '2026-08-18 20:56:35', '0', '0', 'CASH', 36, 'CASH-36-RET-13', 'COMPLETED', 1, '2026-08-18 20:56:35'),
(14, 41, 'RETURN', 'hỏnh', NULL, '38327', 1, 'APPROVED', 2, '2026-08-19 17:37:03', 4, '2026-08-19 17:36:13', '11273', '12400', 'CASH', 35, 'CASH-35-RET-14', 'COMPLETED', 4, '2026-08-19 17:37:03');

--
-- Triggers `ReturnExchanges`
--
DELIMITER $$
CREATE TRIGGER `trg_ReturnExchange_ApprovedStock` AFTER UPDATE ON `ReturnExchanges` FOR EACH ROW BEGIN
    IF NEW.Status = 'APPROVED' AND OLD.Status <> 'APPROVED' THEN
        CALL sp_ApplyReturnExchange(NEW.ReturnID);
    END IF;
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `RolePermissions`
--

CREATE TABLE `RolePermissions` (
  `RoleID` int NOT NULL,
  `PermissionID` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `RolePermissions`
--

INSERT INTO `RolePermissions` (`RoleID`, `PermissionID`) VALUES
(1, 1),
(1, 2),
(1, 3),
(1, 4),
(1, 5),
(1, 6),
(3, 6),
(1, 7),
(5, 7),
(1, 8),
(4, 8),
(1, 9),
(2, 9),
(4, 9),
(1, 10),
(1, 11),
(1, 12),
(1, 13),
(2, 13),
(1, 14),
(3, 14),
(1, 15),
(3, 15),
(1, 16),
(1, 17),
(1, 18),
(1, 19),
(1, 20),
(1, 21),
(2, 21),
(4, 21),
(1, 22),
(2, 22),
(4, 22),
(1, 23),
(1, 24),
(2, 24),
(4, 24),
(1, 25),
(2, 25),
(1, 26),
(4, 26),
(1, 27),
(1, 28),
(1, 29),
(4, 29),
(1, 30),
(2, 30),
(2, 31),
(3, 31),
(4, 31),
(2, 32),
(3, 32),
(4, 32),
(3, 33),
(4, 33),
(3, 34),
(2, 36),
(2, 37),
(3, 38),
(3, 39),
(3, 40),
(3, 41),
(2, 42),
(2, 99),
(4, 99),
(3, 107),
(1, 108),
(4, 108),
(1, 109),
(1, 112),
(2, 112),
(1, 113);

-- --------------------------------------------------------

--
-- Table structure for table `Roles`
--

CREATE TABLE `Roles` (
  `RoleID` int NOT NULL,
  `RoleCode` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `RoleName` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `IsSystem` tinyint(1) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `Roles`
--

INSERT INTO `Roles` (`RoleID`, `RoleCode`, `RoleName`, `Description`, `IsSystem`) VALUES
(1, 'ADMIN', 'Quản trị viên', 'Toàn quyền hệ thống', 1),
(2, 'SALES_MANAGER', 'Quản lý bán hàng', 'Giám sát hoạt động bán hàng', 1),
(3, 'INVENTORY_MANAGER', 'Quản lý kho', 'Kiểm soát nhập - xuất - tồn kho', 1),
(4, 'SALES_STAFF', 'Nhân viên bán hàng', 'Trực tiếp giao dịch với khách', 1),
(5, 'CUSTOMER', 'Khách hàng', 'Tự đăng ký, xem sản phẩm và mua hàng ở phía client', 1);

-- --------------------------------------------------------

--
-- Table structure for table `ShiftCashTransactions`
--

CREATE TABLE `ShiftCashTransactions` (
  `CashTransactionID` bigint NOT NULL,
  `TransactionCode` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ShiftID` int NOT NULL,
  `TransactionType` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Amount` decimal(18,0) NOT NULL,
  `Reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE'
) ;

--
-- Dumping data for table `ShiftCashTransactions`
--

INSERT INTO `ShiftCashTransactions` (`CashTransactionID`, `TransactionCode`, `ShiftID`, `TransactionType`, `Amount`, `Reason`, `CreatedBy`, `CreatedAt`, `Status`) VALUES
(1, 'CT-1786748372061-009F55AE', 9, 'CASH_IN', '200000', 'Quản lý bổ sung tiền lẻ', 4, '2026-08-14 22:59:32', 'ACTIVE'),
(2, 'CT-1786748450103-33B4305E', 9, 'CASH_OUT', '150000', 'Mua vật tư đóng gói', 4, '2026-08-14 23:00:50', 'ACTIVE'),
(3, 'CT-1786802572354-74D6159C', 17, 'CASH_IN', '1000000', 'ban giao', 4, '2026-08-15 21:02:53', 'ACTIVE'),
(4, 'CT-1787026727279-81FC15BA', 21, 'CASH_IN', '1068000', 'tiền khách', 1, '2026-08-18 11:18:48', 'ACTIVE'),
(5, 'CT-1787031175364-43F3EC48', 22, 'CASH_IN', '7000', 'k', 4, '2026-08-18 12:32:56', 'ACTIVE'),
(6, 'CT-1787036007573-E8CF2A67', 26, 'CASH_IN', '200000', 'tiền lẻ quản lí đưa thối', 4, '2026-08-18 13:53:27', 'ACTIVE'),
(7, 'CT-1787036028175-694B6BD8', 26, 'CASH_OUT', '100000', 'mua bọc đựng rác', 4, '2026-08-18 13:53:48', 'ACTIVE'),
(8, 'CT-1787043447990-CCDA6F94', 29, 'CASH_IN', '200000', 'tiền lẻ', 4, '2026-08-18 15:57:28', 'ACTIVE'),
(9, 'CT-1787043466081-609BA5C7', 29, 'CASH_OUT', '100000', 'mua bao', 4, '2026-08-18 15:57:46', 'ACTIVE'),
(10, 'CT-1787046002847-C6BD0632', 31, 'CASH_IN', '100000', '1', 4, '2026-08-18 16:40:03', 'ACTIVE'),
(11, 'CT-1787046017389-83C4CCED', 31, 'CASH_OUT', '100000', '2', 4, '2026-08-18 16:40:17', 'ACTIVE');

-- --------------------------------------------------------

--
-- Table structure for table `Shifts`
--

CREATE TABLE `Shifts` (
  `ShiftID` int NOT NULL,
  `UserID` int NOT NULL,
  `StartTime` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `EndTime` datetime DEFAULT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN',
  `OpeningCash` decimal(18,0) NOT NULL DEFAULT '0',
  `ExpectedCash` decimal(18,0) DEFAULT NULL,
  `CountedCash` decimal(18,0) DEFAULT NULL,
  `CashDifference` decimal(18,0) DEFAULT NULL,
  `OpeningNote` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ClosingNote` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ClosedBy` int DEFAULT NULL,
  `ApprovedBy` int DEFAULT NULL,
  `ApprovedAt` datetime DEFAULT NULL,
  `ApprovalNote` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `LastUpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `OpenUserID` int GENERATED ALWAYS AS ((case when (`Status` = _utf8mb4'OPEN') then `UserID` else NULL end)) STORED
) ;

--
-- Dumping data for table `Shifts`
--

INSERT INTO `Shifts` (`ShiftID`, `UserID`, `StartTime`, `EndTime`, `Status`, `OpeningCash`, `ExpectedCash`, `CountedCash`, `CashDifference`, `OpeningNote`, `ClosingNote`, `ClosedBy`, `ApprovedBy`, `ApprovedAt`, `ApprovalNote`, `LastUpdatedAt`) VALUES
(1, 4, '2026-08-06 08:00:00', '2026-08-06 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(2, 4, '2026-08-07 08:00:00', '2026-08-07 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(3, 4, '2026-08-08 08:00:00', '2026-08-08 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(4, 4, '2026-08-09 08:00:00', '2026-08-09 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(5, 4, '2026-08-10 08:00:00', '2026-08-10 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(6, 4, '2026-08-11 08:00:00', '2026-08-11 21:00:00', 'CLOSED', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-14 19:21:13'),
(7, 4, '2026-08-12 08:00:00', '2026-08-14 22:55:47', 'CLOSED', '0', '116640', '116640', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-14 22:55:47'),
(8, 1, '2026-08-13 12:13:52', '2026-08-15 17:53:36', 'CLOSED', '0', '51840', '51840', '0', NULL, NULL, 1, NULL, NULL, NULL, '2026-08-15 17:53:36'),
(9, 4, '2026-08-14 22:56:27', '2026-08-14 23:02:57', 'CLOSED', '1000000', '1050000', '1050000', '0', 'Nhận tiền đầu ca từ quản lý', NULL, 4, NULL, NULL, NULL, '2026-08-14 23:02:57'),
(10, 4, '2026-08-14 23:04:08', '2026-08-14 23:04:28', 'CLOSED', '1000000', '1000000', '950000', '-50000', NULL, 'Thiếu 50.000, đang chờ quản lý xác minh', 4, NULL, NULL, NULL, '2026-08-14 23:04:28'),
(11, 4, '2026-08-15 06:30:33', '2026-08-15 06:31:48', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 06:31:48'),
(12, 4, '2026-08-15 06:52:16', '2026-08-15 06:52:20', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 06:52:20'),
(13, 4, '2026-08-15 07:48:03', '2026-08-15 07:52:42', 'CLOSED', '1000000', '1211680', '1211680', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 07:52:42'),
(14, 4, '2026-08-15 07:58:28', '2026-08-15 08:01:00', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 08:01:00'),
(15, 4, '2026-08-15 08:32:03', '2026-08-15 08:33:36', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 08:33:36'),
(16, 4, '2026-08-15 09:14:55', '2026-08-15 09:16:22', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-15 09:16:22'),
(17, 4, '2026-08-15 21:00:36', '2026-08-17 18:39:09', 'CLOSED', '0', '1196000', '1196000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-17 18:39:09'),
(18, 4, '2026-08-17 19:25:15', '2026-08-17 19:26:07', 'CLOSED', '1000000', '1000000', '1000000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-17 19:26:07'),
(19, 4, '2026-08-17 19:26:23', '2026-08-17 19:27:00', 'CLOSED', '1000000', '1000000', '1000000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-17 19:27:00'),
(20, 4, '2026-08-17 20:00:33', '2026-08-17 20:00:39', 'CLOSED', '1000000', '1000000', '1000000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-17 20:00:39'),
(21, 1, '2026-08-18 01:13:52', '2026-08-18 11:18:54', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 1, NULL, NULL, NULL, '2026-08-18 11:18:54'),
(22, 4, '2026-08-18 08:30:07', '2026-08-18 12:44:34', 'CLOSED', '1000000', '1014000', '1014000', '0', NULL, NULL, 4, 2, '2026-08-18 12:44:55', NULL, '2026-08-18 12:44:55'),
(23, 4, '2026-08-18 12:55:08', '2026-08-18 12:55:12', 'PENDING_APPROVAL', '500000', '500000', '500000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 12:55:12'),
(24, 4, '2026-08-18 13:03:59', '2026-08-18 13:08:56', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, 2, '2026-08-18 17:48:41', NULL, '2026-08-18 17:48:41'),
(25, 4, '2026-08-18 13:13:15', '2026-08-18 13:22:36', 'CLOSED', '500000', '500000', '500000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 13:22:36'),
(26, 4, '2026-08-18 13:52:19', '2026-08-18 13:57:40', 'CLOSED', '600000', '700000', '700000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 13:57:40'),
(27, 4, '2026-08-18 14:27:27', '2026-08-18 14:28:40', 'CLOSED', '700000', '700000', '700000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 14:28:40'),
(28, 4, '2026-08-18 14:31:03', '2026-08-18 14:53:23', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 14:53:23'),
(29, 4, '2026-08-18 15:01:36', '2026-08-18 16:13:56', 'CLOSED', '0', '100000', '100000', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 16:13:56'),
(30, 4, '2026-08-18 16:14:06', '2026-08-18 16:39:24', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 16:39:24'),
(31, 4, '2026-08-18 16:39:46', '2026-08-18 16:40:31', 'CLOSED', '100000', '100000', '100000', '0', '1', NULL, 4, NULL, NULL, NULL, '2026-08-18 16:40:31'),
(32, 4, '2026-08-18 18:21:06', '2026-08-18 20:20:55', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 20:20:55'),
(33, 1, '2026-08-18 20:24:04', '2026-08-18 20:54:57', 'PENDING_APPROVAL', '0', '0', '0', '0', NULL, NULL, 1, NULL, NULL, NULL, '2026-08-18 20:54:57'),
(34, 4, '2026-08-18 20:34:45', '2026-08-18 20:35:50', 'CLOSED', '0', '0', '0', '0', NULL, NULL, 4, NULL, NULL, NULL, '2026-08-18 20:35:50'),
(35, 4, '2026-08-18 20:38:28', NULL, 'OPEN', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-18 20:38:28'),
(36, 1, '2026-08-18 20:55:22', NULL, 'OPEN', '0', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, '2026-08-18 20:55:22');

-- --------------------------------------------------------

--
-- Table structure for table `StockAlerts`
--

CREATE TABLE `StockAlerts` (
  `AlertID` int NOT NULL,
  `ProductID` int NOT NULL,
  `AlertType` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `StockAtReport` int NOT NULL,
  `Note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ReportedBy` int DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW',
  `SeenByInventoryManager` tinyint(1) NOT NULL DEFAULT '0',
  `ResolvedBy` int DEFAULT NULL,
  `ResolvedAt` datetime DEFAULT NULL
) ;

--
-- Dumping data for table `StockAlerts`
--

INSERT INTO `StockAlerts` (`AlertID`, `ProductID`, `AlertType`, `StockAtReport`, `Note`, `ReportedBy`, `CreatedAt`, `Status`, `SeenByInventoryManager`, `ResolvedBy`, `ResolvedAt`) VALUES
(1, 7, 'LOW_STOCK', 130, 'Sắp hết trước dịp cuối tuần, đề nghị nhập thêm', 5, '2026-08-12 10:53:31', 'RESOLVED', 1, 3, '2026-08-18 12:18:16'),
(2, 4, 'LOW_STOCK', 8, 'Đã lên kế hoạch nhập bổ sung từ NCC Đà Lạt', 4, '2026-08-12 10:53:32', 'RESOLVED', 1, 3, '2026-08-11 10:53:32'),
(3, 10, 'LOW_STOCK', 9, NULL, NULL, '2026-08-15 07:48:54', 'RESOLVED', 1, 3, '2026-08-18 12:19:02'),
(4, 13, 'OUT_OF_STOCK', 0, NULL, NULL, '2026-08-17 12:42:56', 'PLANNED', 1, NULL, NULL),
(5, 8, 'LOW_STOCK', 2, NULL, NULL, '2026-08-18 15:58:08', 'RESOLVED', 1, 3, '2026-08-18 16:07:58'),
(6, 8, 'LOW_STOCK', 4, NULL, NULL, '2026-08-18 16:08:42', 'NEW', 1, NULL, NULL),
(7, 10, 'LOW_STOCK', 10, NULL, NULL, '2026-08-19 17:37:03', 'NEW', 1, NULL, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `StockDisposalDetails`
--

CREATE TABLE `StockDisposalDetails` (
  `DisposalDetailID` int NOT NULL,
  `DisposalID` int NOT NULL,
  `ProductID` int NOT NULL,
  `BatchID` int NOT NULL,
  `Quantity` int NOT NULL,
  `UnitCost` decimal(18,0) NOT NULL,
  `LineLossAmount` decimal(18,0) GENERATED ALWAYS AS ((`Quantity` * `UnitCost`)) STORED
) ;

--
-- Dumping data for table `StockDisposalDetails`
--

INSERT INTO `StockDisposalDetails` (`DisposalDetailID`, `DisposalID`, `ProductID`, `BatchID`, `Quantity`, `UnitCost`) VALUES
(1, 1, 7, 8, 10, '65000'),
(2, 2, 2, 4, 2, '15000'),
(3, 3, 1, 3, 10, '35000'),
(4, 4, 1, 1, 10, '35000'),
(5, 5, 1, 1, 10, '35000'),
(6, 6, 1, 3, 10, '35000'),
(7, 7, 1, 3, 10, '35000'),
(8, 8, 1, 1, 50, '35000'),
(9, 9, 1, 3, 25, '35000'),
(10, 9, 1, 1, 25, '35000'),
(11, 10, 2, 4, 8, '15000'),
(12, 11, 2, 4, 5, '15000'),
(13, 12, 6, 6, 10, '6000'),
(14, 13, 6, 6, 10, '6000'),
(15, 14, 6, 6, 10, '6000'),
(16, 15, 2, 4, 45, '15000'),
(17, 16, 8, 19, 2, '90000'),
(18, 17, 8, 20, 1, '90000'),
(19, 18, 8, 20, 1, '90000'),
(20, 19, 10, 21, 5, '30000');

-- --------------------------------------------------------

--
-- Table structure for table `StockDisposals`
--

CREATE TABLE `StockDisposals` (
  `DisposalID` int NOT NULL,
  `DisposalCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED',
  `TotalLossAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `Note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `StockDisposals`
--

INSERT INTO `StockDisposals` (`DisposalID`, `DisposalCode`, `Reason`, `Status`, `TotalLossAmount`, `Note`, `CreatedBy`, `CreatedAt`) VALUES
(1, 'TH_000001', 'EXPIRED', 'COMPLETED', '650000', 'Hủy toàn bộ số cà phê bột còn lại trong lô đã hết hạn sử dụng', 3, '2026-08-12 10:53:31'),
(2, 'TH_000002', 'EXPIRED', 'COMPLETED', '30000', NULL, 3, '2026-08-15 13:56:11'),
(3, 'TH_000003', 'DAMAGED', 'COMPLETED', '350000', NULL, 3, '2026-08-16 21:24:03'),
(4, 'TH_000004', 'EXPIRED', 'COMPLETED', '350000', NULL, 3, '2026-08-16 21:25:24'),
(5, 'TH_000005', 'EXPIRED', 'COMPLETED', '350000', NULL, 3, '2026-08-16 21:28:39'),
(6, 'TH_000006', 'EXPIRED', 'COMPLETED', '350000', NULL, 3, '2026-08-16 23:26:10'),
(7, 'TH_000007', 'EXPIRED', 'COMPLETED', '350000', NULL, 3, '2026-08-16 23:54:02'),
(8, 'TH_000008', 'EXPIRED', 'COMPLETED', '1750000', NULL, 3, '2026-08-17 00:51:10'),
(9, 'TH_000009', 'EXPIRED', 'COMPLETED', '1750000', NULL, 3, '2026-08-17 01:14:11'),
(10, 'TH_000010', 'EXPIRED', 'COMPLETED', '120000', NULL, 3, '2026-08-17 16:25:29'),
(11, 'TH_000011', 'EXPIRED', 'COMPLETED', '75000', NULL, 3, '2026-08-17 16:41:58'),
(12, 'TH_000012', 'EXPIRED', 'COMPLETED', '60000', NULL, 3, '2026-08-17 18:33:34'),
(13, 'TH_000013', 'EXPIRED', 'COMPLETED', '60000', NULL, 3, '2026-08-17 19:32:07'),
(14, 'TH_000014', 'EXPIRED', 'COMPLETED', '60000', NULL, 3, '2026-08-17 19:38:52'),
(15, 'TH_000015', 'EXPIRED', 'COMPLETED', '675000', NULL, 3, '2026-08-18 11:32:59'),
(16, 'TH_000016', 'EXPIRED', 'COMPLETED', '180000', NULL, 3, '2026-08-18 19:45:05'),
(17, 'TH_000017', 'EXPIRED', 'COMPLETED', '90000', NULL, 3, '2026-08-18 20:05:09'),
(18, 'TH_000018', 'EXPIRED', 'COMPLETED', '90000', NULL, 3, '2026-08-18 20:22:21'),
(19, 'TH_000019', 'EXPIRED', 'COMPLETED', '150000', NULL, 3, '2026-08-19 14:23:06');

-- --------------------------------------------------------

--
-- Table structure for table `StockReconciliation`
--

CREATE TABLE `StockReconciliation` (
  `ReconciliationID` int NOT NULL,
  `ProductID` int NOT NULL,
  `BatchID` int DEFAULT NULL,
  `SystemStock` int NOT NULL,
  `ActualStock` int NOT NULL,
  `Discrepancy` int GENERATED ALWAYS AS ((`ActualStock` - `SystemStock`)) STORED,
  `Note` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `Checked` tinyint(1) NOT NULL DEFAULT '0',
  `CheckedBy` int DEFAULT NULL,
  `CheckedAt` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `StockReconciliation`
--

INSERT INTO `StockReconciliation` (`ReconciliationID`, `ProductID`, `BatchID`, `SystemStock`, `ActualStock`, `Note`, `CreatedBy`, `CreatedAt`, `Checked`, `CheckedBy`, `CheckedAt`) VALUES
(1, 4, NULL, 250, 248, 'Kiểm kê cuối ca phát hiện thiếu, nghi do hao hụt khi bày quầy', 3, '2026-08-12 10:53:31', 0, NULL, NULL),
(2, 1, NULL, 0, 380, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(3, 2, NULL, 0, 460, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(4, 3, NULL, 0, 200, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(5, 4, NULL, 0, 0, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(6, 5, NULL, 0, 500, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(7, 6, NULL, 0, 300, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(8, 7, NULL, 0, 130, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(9, 9, NULL, 0, 200, NULL, 1, '2026-08-13 10:02:59', 0, NULL, NULL),
(10, 10, NULL, 0, 8, NULL, 1, '2026-08-13 10:03:00', 0, NULL, NULL),
(11, 1, NULL, 0, 380, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(12, 2, NULL, 0, 460, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(13, 3, NULL, 0, 200, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(14, 4, NULL, 0, 0, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(15, 5, NULL, 0, 500, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(16, 6, NULL, 0, 300, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(17, 7, NULL, 0, 130, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(18, 9, NULL, 0, 200, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(19, 10, NULL, 0, 8, NULL, 1, '2026-08-14 04:04:19', 0, NULL, NULL),
(20, 1, NULL, 380, 380, NULL, 1, '2026-08-14 17:33:28', 0, NULL, NULL),
(21, 2, NULL, 460, 460, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(22, 3, NULL, 200, 200, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(23, 4, NULL, 250, 250, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(24, 5, NULL, 500, 500, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(25, 6, NULL, 300, 300, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(26, 7, NULL, 130, 130, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(27, 10, NULL, 16, 16, NULL, 1, '2026-08-14 17:33:29', 0, NULL, NULL),
(28, 1, NULL, 380, 380, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(29, 2, NULL, 460, 460, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(30, 3, NULL, 200, 200, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(31, 4, NULL, 250, 250, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(32, 5, NULL, 500, 500, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(33, 6, NULL, 300, 300, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(34, 7, NULL, 130, 130, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(35, 10, NULL, 16, 16, NULL, 4, '2026-08-14 17:41:06', 0, NULL, NULL),
(36, 1, NULL, 380, 380, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(37, 2, NULL, 460, 460, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(38, 3, NULL, 200, 200, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(39, 4, NULL, 250, 250, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(40, 5, NULL, 500, 500, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(41, 6, NULL, 300, 300, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(42, 7, NULL, 130, 130, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(43, 10, NULL, 16, 16, NULL, 4, '2026-08-14 17:51:21', 0, NULL, NULL),
(44, 1, NULL, 380, 380, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(45, 2, NULL, 460, 460, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(46, 3, NULL, 200, 200, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(47, 4, NULL, 250, 250, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(48, 5, NULL, 500, 500, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(49, 6, NULL, 300, 300, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(50, 7, NULL, 130, 130, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(51, 10, NULL, 16, 16, NULL, 1, '2026-08-14 17:59:02', 0, NULL, NULL),
(52, 1, NULL, 380, 380, NULL, 1, '2026-08-14 18:03:41', 0, NULL, NULL),
(53, 2, NULL, 460, 460, NULL, 1, '2026-08-14 18:03:41', 0, NULL, NULL),
(54, 3, NULL, 200, 200, NULL, 1, '2026-08-14 18:03:41', 0, NULL, NULL),
(55, 4, NULL, 250, 250, NULL, 1, '2026-08-14 18:03:41', 0, NULL, NULL),
(56, 5, NULL, 500, 500, NULL, 1, '2026-08-14 18:03:41', 0, NULL, NULL),
(57, 6, NULL, 300, 300, NULL, 1, '2026-08-14 18:03:42', 0, NULL, NULL),
(58, 7, NULL, 130, 130, NULL, 1, '2026-08-14 18:03:42', 0, NULL, NULL),
(59, 10, NULL, 16, 16, NULL, 1, '2026-08-14 18:03:42', 0, NULL, NULL),
(60, 1, NULL, 380, 380, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(61, 2, NULL, 460, 460, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(62, 3, NULL, 200, 200, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(63, 4, NULL, 250, 250, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(64, 5, NULL, 500, 500, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(65, 6, NULL, 300, 300, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(66, 7, NULL, 130, 130, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(67, 10, NULL, 16, 16, NULL, 1, '2026-08-14 18:11:42', 0, NULL, NULL),
(68, 1, NULL, 380, 380, NULL, 5, '2026-08-14 18:25:38', 0, NULL, NULL),
(69, 2, NULL, 460, 460, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(70, 3, NULL, 200, 200, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(71, 4, NULL, 250, 250, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(72, 5, NULL, 500, 500, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(73, 6, NULL, 300, 300, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(74, 7, NULL, 130, 130, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(75, 10, NULL, 16, 16, NULL, 5, '2026-08-14 18:25:39', 0, NULL, NULL),
(76, 1, NULL, 380, 380, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(77, 2, NULL, 460, 460, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(78, 3, NULL, 200, 200, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(79, 4, NULL, 250, 250, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(80, 5, NULL, 500, 500, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(81, 6, NULL, 300, 300, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(82, 7, NULL, 130, 130, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(83, 10, NULL, 16, 16, NULL, 5, '2026-08-14 18:30:20', 0, NULL, NULL),
(84, 1, NULL, 380, 380, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(85, 2, NULL, 460, 460, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(86, 3, NULL, 200, 200, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(87, 4, NULL, 250, 250, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(88, 5, NULL, 500, 500, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(89, 6, NULL, 300, 300, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(90, 7, NULL, 130, 130, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(91, 10, NULL, 16, 16, NULL, 5, '2026-08-14 18:30:43', 0, NULL, NULL),
(92, 1, NULL, 380, 380, NULL, 1, '2026-08-14 18:40:48', 0, NULL, NULL),
(93, 2, NULL, 460, 460, NULL, 1, '2026-08-14 18:40:48', 0, NULL, NULL),
(94, 3, NULL, 200, 200, NULL, 1, '2026-08-14 18:40:48', 0, NULL, NULL),
(95, 4, NULL, 250, 250, NULL, 1, '2026-08-14 18:40:48', 0, NULL, NULL),
(96, 5, NULL, 500, 500, NULL, 1, '2026-08-14 18:40:49', 0, NULL, NULL),
(97, 6, NULL, 300, 300, NULL, 1, '2026-08-14 18:40:49', 0, NULL, NULL),
(98, 7, NULL, 130, 130, NULL, 1, '2026-08-14 18:40:49', 0, NULL, NULL),
(99, 10, NULL, 16, 16, NULL, 1, '2026-08-14 18:40:49', 0, NULL, NULL),
(100, 1, NULL, 380, 380, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(101, 2, NULL, 460, 460, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(102, 3, NULL, 200, 200, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(103, 4, NULL, 250, 250, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(104, 5, NULL, 500, 500, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(105, 6, NULL, 300, 300, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(106, 7, NULL, 130, 130, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(107, 10, NULL, 16, 16, NULL, 5, '2026-08-14 18:58:40', 0, NULL, NULL),
(108, 1, NULL, 380, 380, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(109, 2, NULL, 460, 460, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(110, 3, NULL, 200, 200, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(111, 4, NULL, 250, 250, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(112, 5, NULL, 500, 500, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(113, 6, NULL, 300, 300, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(114, 7, NULL, 130, 130, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(115, 10, NULL, 16, 16, NULL, 4, '2026-08-14 19:12:52', 0, NULL, NULL),
(116, 1, NULL, 380, 380, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(117, 2, NULL, 460, 460, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(118, 3, NULL, 200, 200, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(119, 4, NULL, 250, 250, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(120, 5, NULL, 500, 500, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(121, 6, NULL, 300, 300, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(122, 7, NULL, 130, 130, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(123, 10, NULL, 16, 16, NULL, 4, '2026-08-14 19:17:40', 0, NULL, NULL),
(124, 1, NULL, 380, 380, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(125, 2, NULL, 460, 460, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(126, 3, NULL, 200, 200, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(127, 4, NULL, 250, 250, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(128, 5, NULL, 500, 500, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(129, 6, NULL, 300, 300, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(130, 7, NULL, 130, 130, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(131, 10, NULL, 16, 16, NULL, 4, '2026-08-14 22:52:15', 0, NULL, NULL),
(132, 1, NULL, 380, 380, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(133, 2, NULL, 460, 460, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(134, 3, NULL, 200, 200, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(135, 4, NULL, 250, 250, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(136, 5, NULL, 500, 500, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(137, 6, NULL, 300, 300, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(138, 7, NULL, 130, 130, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(139, 10, NULL, 16, 16, NULL, 2, '2026-08-14 23:06:56', 0, NULL, NULL),
(140, 1, NULL, 380, 380, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(141, 2, NULL, 460, 460, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(142, 3, NULL, 200, 200, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(143, 4, NULL, 250, 250, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(144, 5, NULL, 500, 500, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(145, 6, NULL, 300, 300, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(146, 7, NULL, 130, 130, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(147, 10, NULL, 16, 16, NULL, 4, '2026-08-14 23:16:48', 0, NULL, NULL),
(148, 1, NULL, 380, 380, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(149, 2, NULL, 460, 460, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(150, 3, NULL, 200, 200, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(151, 4, NULL, 250, 250, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(152, 5, NULL, 500, 500, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(153, 6, NULL, 300, 300, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(154, 7, NULL, 130, 130, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(155, 10, NULL, 16, 16, NULL, 2, '2026-08-14 23:17:40', 0, NULL, NULL),
(156, 1, NULL, 380, 380, NULL, 4, '2026-08-15 06:30:20', 0, NULL, NULL),
(157, 2, NULL, 460, 460, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(158, 3, NULL, 200, 200, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(159, 4, NULL, 250, 250, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(160, 5, NULL, 500, 500, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(161, 6, NULL, 300, 300, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(162, 7, NULL, 130, 130, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(163, 10, NULL, 16, 16, NULL, 4, '2026-08-15 06:30:21', 0, NULL, NULL),
(164, 12, NULL, 0, 0, NULL, 4, '2026-08-15 20:29:50', 0, NULL, NULL),
(165, 13, NULL, 0, 0, NULL, 4, '2026-08-15 20:29:50', 0, NULL, NULL),
(166, 11, NULL, 0, 0, NULL, 4, '2026-08-15 20:29:50', 0, NULL, NULL),
(167, 1, 1, 290, 280, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 21:29:18'),
(168, 2, 2, 400, 400, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 20:22:16'),
(169, 3, 9, 196, 196, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:49'),
(170, 4, 10, 250, 250, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:45'),
(171, 5, 5, 500, 500, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:42'),
(172, 6, 6, 300, 300, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 23:48:36'),
(173, 12, NULL, 0, 0, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:31'),
(174, 13, NULL, 0, 0, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:27'),
(175, 7, 7, 150, 150, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 14:16:23'),
(176, 10, 16, 8, 8, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 15:33:41'),
(177, 11, NULL, 0, 0, NULL, 1, '2026-08-16 00:10:42', 1, 3, '2026-08-16 19:22:11'),
(178, 1, 3, 80, 60, NULL, 1, '2026-08-16 19:39:33', 1, 3, '2026-08-16 23:27:18'),
(179, 2, 4, 58, 58, NULL, 1, '2026-08-16 19:39:33', 1, 3, '2026-08-16 23:48:37'),
(180, 10, 17, 99, 99, NULL, 1, '2026-08-16 19:39:33', 1, 3, '2026-08-16 23:48:33'),
(181, 1, 1, 175, 175, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(182, 1, 3, 0, 0, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(183, 2, 2, 395, 395, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(184, 2, 4, 45, 45, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(185, 3, 9, 196, 196, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(186, 4, 10, 250, 250, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(187, 5, 5, 500, 500, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(188, 6, 6, 270, 270, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(189, 7, 7, 150, 150, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(190, 10, 16, 8, 8, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(191, 10, 17, 99, 99, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(192, 11, NULL, 0, 0, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(193, 12, NULL, 0, 0, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(194, 13, NULL, 0, 0, NULL, 3, '2026-08-17 00:00:08', 0, NULL, NULL),
(195, 9, 11, 200, 200, NULL, 3, '2026-08-17 22:24:12', 0, NULL, NULL),
(196, 1, 1, 175, 175, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(197, 2, 2, 395, 395, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(198, 3, 9, 191, 191, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(199, 4, 10, 247, 247, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(200, 5, 5, 500, 500, NULL, 4, '2026-08-18 00:00:06', 1, 3, '2026-08-18 20:56:29'),
(201, 6, 6, 270, 266, NULL, 4, '2026-08-18 00:00:06', 1, 3, '2026-08-18 20:47:11'),
(202, 7, 7, 147, 147, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(203, 9, 11, 198, 198, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(204, 10, 16, 8, 8, NULL, 4, '2026-08-18 00:00:06', 0, NULL, NULL),
(205, 10, 18, 10, 10, NULL, 3, '2026-08-18 10:35:46', 0, NULL, NULL),
(206, 8, 19, 0, 0, NULL, 1, '2026-08-18 15:58:20', 0, NULL, NULL),
(207, 8, 20, 0, 0, NULL, 4, '2026-08-18 16:12:39', 0, NULL, NULL),
(208, 2, 22, 9, 9, NULL, 4, '2026-08-18 21:18:33', 0, NULL, NULL),
(209, 10, 21, 10, 10, NULL, 4, '2026-08-18 21:18:33', 0, NULL, NULL),
(210, 1, 1, 175, 175, NULL, 4, '2026-08-19 09:59:34', 0, NULL, NULL),
(211, 2, 2, 395, 395, NULL, 4, '2026-08-19 09:59:34', 0, NULL, NULL),
(212, 2, 22, 9, 9, NULL, 4, '2026-08-19 09:59:34', 0, NULL, NULL),
(213, 3, 9, 189, 189, NULL, 4, '2026-08-19 09:59:34', 0, NULL, NULL),
(214, 4, 10, 247, 247, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:13'),
(215, 5, 5, 500, 500, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:11'),
(216, 6, 6, 270, 270, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:09'),
(217, 7, 7, 147, 147, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:07'),
(218, 9, 11, 198, 198, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:05'),
(219, 10, 16, 0, 0, NULL, 4, '2026-08-19 09:59:35', 0, NULL, NULL),
(220, 10, 18, 10, 10, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:41:02'),
(221, 10, 21, 5, 2, NULL, 4, '2026-08-19 09:59:35', 1, 3, '2026-08-19 14:27:11');

--
-- Triggers `StockReconciliation`
--
DELIMITER $$
CREATE TRIGGER `trg_StockReconciliation_Apply` AFTER INSERT ON `StockReconciliation` FOR EACH ROW BEGIN
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

        IF NEW.ActualStock < 0 OR NEW.ActualStock > v_batch_quantity THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'Ton thuc te cua lo vuot gioi han so luong cua lo.';
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
                    SET MESSAGE_TEXT = 'Khong the cap nhat ton cua lo hang.';
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
            (ProductID, TransactionType, Direction, Quantity,
             StockBefore, StockAfter, RefTable, RefID, CreatedBy, Note)
        VALUES
            (NEW.ProductID,
             'RECONCILE_ADJUST',
             CASE WHEN v_diff > 0 THEN 'IN' ELSE 'OUT' END,
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
             END);
    END IF;
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_StockReconciliation_BlockDelete` BEFORE DELETE ON `StockReconciliation` FOR EACH ROW BEGIN
    SIGNAL SQLSTATE '45000'
        SET MESSAGE_TEXT = 'Khong duoc xoa vinh vien lich su doi chieu kho.';
END
$$
DELIMITER ;
DELIMITER $$
CREATE TRIGGER `trg_StockReconciliation_Prepare` BEFORE INSERT ON `StockReconciliation` FOR EACH ROW BEGIN
    DECLARE v_product_stock INT DEFAULT 0;
    DECLARE v_batch_product_id INT;
    DECLARE v_batch_stock INT DEFAULT 0;
    DECLARE v_has_batch INT DEFAULT 0;

    SELECT Stock INTO v_product_stock
    FROM Products
    WHERE ProductID = NEW.ProductID;

    IF v_product_stock IS NULL THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'San pham khong ton tai, khong the doi chieu kho.';
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
                SET MESSAGE_TEXT = 'Lo hang khong thuoc san pham dang doi chieu.';
        END IF;

        SET NEW.SystemStock = COALESCE(v_batch_stock, 0);
    ELSE
        SELECT COUNT(*) INTO v_has_batch
        FROM InventoryBatch
        WHERE ProductID = NEW.ProductID;

        IF v_has_batch > 0 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'San pham da quan ly theo lo; phai doi chieu theo tung lo.';
        END IF;

        SET NEW.SystemStock = COALESCE(v_product_stock, 0);
    END IF;
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `StoreConfig`
--

CREATE TABLE `StoreConfig` (
  `ConfigKey` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ConfigValue` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `StoreConfig`
--

INSERT INTO `StoreConfig` (`ConfigKey`, `ConfigValue`) VALUES
('DEFAULT_MARGIN', '5000'),
('DEFAULT_UNIT', 'cái'),
('POINT_RATE', '100000'),
('RETURN_APPROVAL_THRESHOLD', '0'),
('RETURN_POLICY_DAYS', '1'),
('STORE_NAME', 'Connect Mart'),
('VAT_RATE', '0');

-- --------------------------------------------------------

--
-- Table structure for table `SupplierProducts`
--

CREATE TABLE `SupplierProducts` (
  `SupplierID` int NOT NULL,
  `ProductID` int NOT NULL,
  `SupplyPrice` decimal(18,0) DEFAULT NULL,
  `IsPreferred` tinyint(1) NOT NULL DEFAULT '0',
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `SupplierProducts`
--

INSERT INTO `SupplierProducts` (`SupplierID`, `ProductID`, `SupplyPrice`, `IsPreferred`, `CreatedAt`) VALUES
(1, 1, '35000', 1, '2026-08-12 10:53:30'),
(1, 2, '15000', 1, '2026-08-12 10:53:30'),
(1, 3, '18500', 0, '2026-08-12 10:53:30'),
(1, 4, '12500', 0, '2026-08-12 10:53:30'),
(2, 5, '4000', 1, '2026-08-12 10:53:30'),
(2, 6, '6000', 1, '2026-08-12 10:53:30'),
(2, 7, '65000', 1, '2026-08-12 10:53:30'),
(2, 8, '90000', 1, '2026-08-12 10:53:30'),
(3, 3, '17500', 1, '2026-08-12 10:53:30'),
(3, 4, '12000', 1, '2026-08-12 10:53:30'),
(4, 9, '28000', 1, '2026-08-12 10:53:30'),
(4, 10, '20000', 1, '2026-08-12 10:53:30');

-- --------------------------------------------------------

--
-- Table structure for table `SupplierReturnDetails`
--

CREATE TABLE `SupplierReturnDetails` (
  `SupplierReturnDetailID` int NOT NULL,
  `SupplierReturnID` int NOT NULL,
  `ProductID` int NOT NULL,
  `BatchID` int NOT NULL,
  `Quantity` int NOT NULL,
  `UnitRefundPrice` decimal(18,0) NOT NULL,
  `LineRefundAmount` decimal(18,0) GENERATED ALWAYS AS ((`Quantity` * `UnitRefundPrice`)) STORED
) ;

--
-- Dumping data for table `SupplierReturnDetails`
--

INSERT INTO `SupplierReturnDetails` (`SupplierReturnDetailID`, `SupplierReturnID`, `ProductID`, `BatchID`, `Quantity`, `UnitRefundPrice`) VALUES
(1, 1, 7, 8, 10, '65000'),
(2, 2, 1, 3, 25, '35000'),
(3, 3, 10, 16, 8, '20000');

-- --------------------------------------------------------

--
-- Table structure for table `SupplierReturns`
--

CREATE TABLE `SupplierReturns` (
  `SupplierReturnID` int NOT NULL,
  `SupplierReturnCode` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `SupplierID` int NOT NULL,
  `Reason` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COMPLETED',
  `TotalRefundAmount` decimal(18,0) NOT NULL DEFAULT '0',
  `Note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `CreatedBy` int NOT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `SupplierReturns`
--

INSERT INTO `SupplierReturns` (`SupplierReturnID`, `SupplierReturnCode`, `SupplierID`, `Reason`, `Status`, `TotalRefundAmount`, `Note`, `CreatedBy`, `CreatedAt`) VALUES
(1, 'TRNC_000001', 2, 'EXPIRED', 'COMPLETED', '650000', 'Trả lại lô cà phê bột hết hạn sử dụng, yêu cầu NCC hoàn tiền', 3, '2026-08-12 10:53:31'),
(2, 'TRNC_000002', 1, 'DAMAGED', 'COMPLETED', '875000', NULL, 3, '2026-08-17 19:59:34'),
(3, 'TRNC_000003', 4, 'QUALITY', 'COMPLETED', '160000', NULL, 3, '2026-08-19 14:20:09');

-- --------------------------------------------------------

--
-- Table structure for table `Suppliers`
--

CREATE TABLE `Suppliers` (
  `SupplierID` int NOT NULL,
  `SupplierName` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `SuppliedItems` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `IsDeleted` tinyint(1) NOT NULL DEFAULT '0',
  `DeletedAt` datetime DEFAULT NULL,
  `DebtBalance` decimal(18,0) NOT NULL DEFAULT '0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `Suppliers`
--

INSERT INTO `Suppliers` (`SupplierID`, `SupplierName`, `Address`, `Phone`, `Email`, `SuppliedItems`, `IsDeleted`, `DeletedAt`, `DebtBalance`) VALUES
(1, 'Công ty TNHH Nông sản Miền Tây', '123 Nguyễn Trãi, Cần Thơ', '0710123456', 'contact@mientaynongsan.vn', 'Trái cây, rau củ', 0, NULL, '875000'),
(2, 'Công ty CP Thực phẩm An Bình', '45 Lê Lợi, TP.HCM', '0281234567', 'sales@anbinhfood.vn', 'Đồ uống, thực phẩm khô', 0, NULL, '650000'),
(3, 'Công ty TNHH Rau sạch Đà Lạt', '88 Trần Phú, Đà Lạt', '0263123456', 'contact@dalatveggie.vn', 'Rau củ', 0, NULL, '0'),
(4, 'Công ty CP Sữa & Bánh kẹo Việt', '12 Cách Mạng Tháng 8, TP.HCM', '0287654321', 'sales@vietdairy.vn', 'Sữa, bánh kẹo', 0, NULL, '160000'),
(5, 'Công ty TNHH Gia vị Miền Trung', '56 Trần Hưng Đạo, Đà Nẵng', '0236123456', 'contact@giavimientrung.vn', 'Gia vị, thực phẩm khô', 1, '2026-07-13 10:53:30', '0');

-- --------------------------------------------------------

--
-- Table structure for table `Users`
--

CREATE TABLE `Users` (
  `UserID` int NOT NULL,
  `Username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `PasswordHash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `FullName` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `Email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `AvatarUrl` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `RoleID` int NOT NULL,
  `IsLocked` tinyint(1) NOT NULL DEFAULT '0',
  `FailedLoginCount` int NOT NULL DEFAULT '0',
  `Status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE',
  `IsDeleted` tinyint(1) NOT NULL DEFAULT '0',
  `DeletedAt` datetime DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `Users`
--

INSERT INTO `Users` (`UserID`, `Username`, `PasswordHash`, `FullName`, `Email`, `Phone`, `AvatarUrl`, `RoleID`, `IsLocked`, `FailedLoginCount`, `Status`, `IsDeleted`, `DeletedAt`, `CreatedAt`) VALUES
(1, 'admin', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Hoàng Trung Nam', 'hoangnam131020@gmail.com', '0969036498', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786881235/admin_oq9blc.jpg', 1, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(2, 'salesmgr', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Hà Minh Tuấn', 'tuan.sm@connectmart.vn', '0900000002', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1787022061/anh-avatar_rtrghh.jpg', 2, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(3, 'invmgr', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Trần Tài Phương', 'phuongkho.im@connectmart.vn', '0900000003', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786901301/541ae78d-9173-460b-ad66-9a66b282ccc3_gjmog2.jpg', 3, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(4, 'staff01', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Lê Hoa Trường Vũ', 'vu.staff@connectmart.vn', '0900000004', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786894937/staff01_zqpz8b.jpg', 4, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(5, 'staff02', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Hoàng Văn Sơn', 'son.staff@connectmart.vn', '0900000005', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786882273/staff02_tz4qsa.jpg', 4, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(6, 'lan.nguyen', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Nguyễn Thị Lan', 'lan.nguyen@gmail.com', '0912345678', NULL, 5, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(7, 'hung.tran', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Trần Văn Hùng', 'hung.tran@gmail.com', '0987654321', NULL, 5, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(8, 'mai.pham', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Phạm Thị Mai', 'mai.pham@gmail.com', '0933112233', NULL, 5, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(9, 'duc.le', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Lê Anh Đức', 'duc.le@gmail.com', '0977665544', NULL, 5, 1, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:30'),
(10, 'khach_le', '$2a$10$examplehash.guest.000000000000000000000000000', 'Khách lẻ', NULL, NULL, NULL, 5, 0, 0, 'DISABLED', 0, NULL, '2026-08-12 10:53:30'),
(11, 'customer1', '$2a$12$bu9a8NWQ5nLvEzmP9KmDbOmZxADF8e83Lrf/w60dhBTXaUyxRl4zi', 'Khách hàng Demo', 'customer1@sims.local', '0901234567', 'https://res.cloudinary.com/dk4todoe8/image/upload/v1786882047/customer1_z2hix7.jpg', 5, 0, 0, 'ACTIVE', 0, NULL, '2026-08-12 10:53:32');

--
-- Triggers `Users`
--
DELIMITER $$
CREATE TRIGGER `trg_Users_AutoLock` BEFORE UPDATE ON `Users` FOR EACH ROW BEGIN
    IF NEW.FailedLoginCount >= 5 THEN SET NEW.IsLocked = 1; END IF;
END
$$
DELIMITER ;

-- --------------------------------------------------------

--
-- Table structure for table `UserTwoFactor`
--

CREATE TABLE `UserTwoFactor` (
  `UserID` int NOT NULL,
  `Method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE',
  `TotpSecretEnc` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `Enabled` tinyint(1) NOT NULL DEFAULT '0',
  `EnrolledAt` datetime DEFAULT NULL,
  `UpdatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ;

--
-- Dumping data for table `UserTwoFactor`
--

INSERT INTO `UserTwoFactor` (`UserID`, `Method`, `TotpSecretEnc`, `Enabled`, `EnrolledAt`, `UpdatedAt`) VALUES
(1, 'EMAIL', NULL, 1, '2026-08-15 22:58:38', '2026-08-15 22:58:38');

-- --------------------------------------------------------

--
-- Table structure for table `UserTwoFactorBackupCodes`
--

CREATE TABLE `UserTwoFactorBackupCodes` (
  `BackupCodeID` int NOT NULL,
  `UserID` int NOT NULL,
  `CodeHash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `UsedAt` datetime DEFAULT NULL,
  `CreatedAt` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Dumping data for table `UserTwoFactorBackupCodes`
--

INSERT INTO `UserTwoFactorBackupCodes` (`BackupCodeID`, `UserID`, `CodeHash`, `UsedAt`, `CreatedAt`) VALUES
(151, 1, '$2a$12$/mMA2Q4dLv7xW5zBotgwH.Lsm4R5KgGNETaOP26PCtSI4YkqPr/kS', NULL, '2026-08-15 22:58:40'),
(152, 1, '$2a$12$qwllMgphV6yvBvnKkCc8qOZktcXQgE4BHNKlsvIzJj.rzUh6YlxZW', NULL, '2026-08-15 22:58:40'),
(153, 1, '$2a$12$QSJcRYPlidq4Rjrh9C8Tzud0gpjHBL3PTo3q9lK3FXczonktG0ype', NULL, '2026-08-15 22:58:40'),
(154, 1, '$2a$12$S0WWnxiF8EG.rE5D.K.ISulPGTbEv.wCwZhQDa5HuK.yl4JNkzX.q', NULL, '2026-08-15 22:58:40'),
(155, 1, '$2a$12$clu.XJLpL7d.boOXB9VLPOWb5UMTAoGJjlTiGJ6XxbMo7BoIcli3u', NULL, '2026-08-15 22:58:41'),
(156, 1, '$2a$12$EcQJGiDYbZdOMjfbwR/69.BmdumbTgnMH4iTNjFBte26fS990UpR2', NULL, '2026-08-15 22:58:41'),
(157, 1, '$2a$12$DavHYdUkfs3GAqtpHZf7u.UkV0g1PW3bn1qpj43GmfVnEcYw6ZX5y', NULL, '2026-08-15 22:58:41'),
(158, 1, '$2a$12$oT9PtncnQ3dvmJw/46GUD.DskjoJHQ2p.tL2EzYcgt58EzQYT97ou', NULL, '2026-08-15 22:58:41'),
(159, 1, '$2a$12$xVJ8kFoCMzPvahNsEVdIfeH0Razp.gnqj5RzI8Kb3WBNC0nXCqsOC', NULL, '2026-08-15 22:58:41'),
(160, 1, '$2a$12$mUT1bagaRO9vhqAGeqhW3.Ft4R7wned5uLZ2CvEI3qUeVnbhbebmu', NULL, '2026-08-15 22:58:41');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `AuditLogs`
--
ALTER TABLE `AuditLogs`
  ADD PRIMARY KEY (`LogID`),
  ADD KEY `IX_AuditLogs_User_Date` (`UserID`,`CreatedAt`);

--
-- Indexes for table `Categories`
--
ALTER TABLE `Categories`
  ADD PRIMARY KEY (`CategoryID`),
  ADD UNIQUE KEY `CategoryName` (`CategoryName`);

--
-- Indexes for table `ChatConversations`
--
ALTER TABLE `ChatConversations`
  ADD PRIMARY KEY (`ConversationID`),
  ADD UNIQUE KEY `UX_ChatConv_Customer_Open` (`OpenSupportKey`),
  ADD UNIQUE KEY `UX_ChatConv_StaffPair` (`StaffDmKey`),
  ADD KEY `FK_ChatConversations_Customer` (`CustomerUserID`),
  ADD KEY `FK_ChatConversations_StaffA` (`StaffUserIdA`),
  ADD KEY `FK_ChatConversations_StaffB` (`StaffUserIdB`);

--
-- Indexes for table `ChatMessages`
--
ALTER TABLE `ChatMessages`
  ADD PRIMARY KEY (`MessageID`),
  ADD KEY `IX_ChatMessages_Conversation_Created` (`ConversationID`,`CreatedAt`),
  ADD KEY `IX_ChatMessages_Sender` (`SenderUserID`);

--
-- Indexes for table `Customers`
--
ALTER TABLE `Customers`
  ADD PRIMARY KEY (`CustomerID`),
  ADD UNIQUE KEY `CustomerCode` (`CustomerCode`);

--
-- Indexes for table `Employees`
--
ALTER TABLE `Employees`
  ADD PRIMARY KEY (`UserID`),
  ADD UNIQUE KEY `EmployeeID` (`EmployeeID`);

--
-- Indexes for table `ExceptionReports`
--
ALTER TABLE `ExceptionReports`
  ADD PRIMARY KEY (`ReportID`),
  ADD KEY `FK_ExceptionReports_CreatedBy` (`CreatedBy`),
  ADD KEY `FK_ExceptionReports_HandledBy` (`HandledBy`);

--
-- Indexes for table `InventoryBatch`
--
ALTER TABLE `InventoryBatch`
  ADD PRIMARY KEY (`BatchID`),
  ADD UNIQUE KEY `ReceiptDetailID` (`ReceiptDetailID`),
  ADD UNIQUE KEY `UQ_InventoryBatch_BatchCode` (`BatchCode`),
  ADD KEY `FK_InventoryBatch_Suppliers` (`SupplierID`),
  ADD KEY `IX_InventoryBatch_FEFO` (`ProductID`,`ExpiryDate`,`RemainingQty`,`Status`);

--
-- Indexes for table `InventoryTransactions`
--
ALTER TABLE `InventoryTransactions`
  ADD PRIMARY KEY (`TransactionID`),
  ADD KEY `FK_InventoryTransactions_CreatedBy` (`CreatedBy`),
  ADD KEY `IX_InvTrans_Product_Date` (`ProductID`,`CreatedAt`);

--
-- Indexes for table `InvoiceDetailBatches`
--
ALTER TABLE `InvoiceDetailBatches`
  ADD PRIMARY KEY (`InvoiceDetailID`,`BatchID`),
  ADD KEY `FK_InvoiceDetailBatches_Batch` (`BatchID`);

--
-- Indexes for table `InvoiceDetails`
--
ALTER TABLE `InvoiceDetails`
  ADD PRIMARY KEY (`InvoiceDetailID`),
  ADD KEY `FK_InvoiceDetails_Invoices` (`InvoiceID`),
  ADD KEY `FK_InvoiceDetails_Products` (`ProductID`);

--
-- Indexes for table `Invoices`
--
ALTER TABLE `Invoices`
  ADD PRIMARY KEY (`InvoiceID`),
  ADD UNIQUE KEY `InvoiceCode` (`InvoiceCode`),
  ADD UNIQUE KEY `UQ_Invoices_PayPalOrderID` (`PayPalOrderID`),
  ADD UNIQUE KEY `UQ_Invoices_PayPalCaptureID` (`PayPalCaptureID`),
  ADD UNIQUE KEY `UQ_Invoices_PayOsOrderCode` (`PayOsOrderCode`),
  ADD UNIQUE KEY `UQ_Invoices_PayOsPaymentLinkID` (`PayOsPaymentLinkID`),
  ADD KEY `FK_Invoices_Shifts` (`ShiftID`),
  ADD KEY `FK_Invoices_CreatedBy` (`CreatedBy`),
  ADD KEY `FK_Invoices_Customers` (`CustomerID`),
  ADD KEY `IX_Invoices_PromotionID` (`PromotionID`);

--
-- Indexes for table `OrderDetailBatches`
--
ALTER TABLE `OrderDetailBatches`
  ADD PRIMARY KEY (`OrderDetailID`,`BatchID`),
  ADD KEY `FK_OrderDetailBatches_Batch` (`BatchID`);

--
-- Indexes for table `OrderDetails`
--
ALTER TABLE `OrderDetails`
  ADD PRIMARY KEY (`OrderDetailID`),
  ADD KEY `FK_OrderDetails_Orders` (`OrderID`),
  ADD KEY `FK_OrderDetails_Products` (`ProductID`);

--
-- Indexes for table `Orders`
--
ALTER TABLE `Orders`
  ADD PRIMARY KEY (`OrderID`),
  ADD UNIQUE KEY `UX_Orders_InvoiceID` (`InvoiceID`),
  ADD UNIQUE KEY `UQ_Orders_PayPalOrderID` (`PayPalOrderID`),
  ADD UNIQUE KEY `UQ_Orders_PayPalCaptureID` (`PayPalCaptureID`),
  ADD KEY `FK_Orders_Customers` (`CustomerID`),
  ADD KEY `IX_Orders_SeenByAdmin` (`SeenByAdmin`,`CreatedAt`),
  ADD KEY `IX_Orders_PromotionID` (`PromotionID`);

--
-- Indexes for table `Permissions`
--
ALTER TABLE `Permissions`
  ADD PRIMARY KEY (`PermissionID`),
  ADD UNIQUE KEY `PermissionCode` (`PermissionCode`);

--
-- Indexes for table `Products`
--
ALTER TABLE `Products`
  ADD PRIMARY KEY (`ProductID`),
  ADD UNIQUE KEY `UQ_Products_ProductCode` (`ProductCode`),
  ADD KEY `FK_Products_Categories` (`CategoryID`);

--
-- Indexes for table `Promotions`
--
ALTER TABLE `Promotions`
  ADD PRIMARY KEY (`PromotionID`),
  ADD UNIQUE KEY `Code` (`Code`),
  ADD KEY `FK_Promotions_CreatedBy` (`CreatedBy`),
  ADD KEY `IX_Promotions_Code` (`Code`),
  ADD KEY `IX_Promotions_ActiveRange` (`IsActive`,`StartDate`,`EndDate`),
  ADD KEY `IX_Promotions_Banner` (`ShowOnBanner`,`IsActive`,`StartDate`,`EndDate`,`BannerSortOrder`);

--
-- Indexes for table `PurchaseReceiptDetails`
--
ALTER TABLE `PurchaseReceiptDetails`
  ADD PRIMARY KEY (`ReceiptDetailID`),
  ADD KEY `FK_PurchaseReceiptDetails_Receipts` (`ReceiptID`),
  ADD KEY `FK_PurchaseReceiptDetails_Products` (`ProductID`);

--
-- Indexes for table `PurchaseReceipts`
--
ALTER TABLE `PurchaseReceipts`
  ADD PRIMARY KEY (`ReceiptID`),
  ADD UNIQUE KEY `ReceiptCode` (`ReceiptCode`),
  ADD KEY `FK_PurchaseReceipts_Suppliers` (`SupplierID`),
  ADD KEY `FK_PurchaseReceipts_CreatedBy` (`CreatedBy`);

--
-- Indexes for table `ReturnExchangeDetailBatches`
--
ALTER TABLE `ReturnExchangeDetailBatches`
  ADD PRIMARY KEY (`ReturnDetailID`,`BatchID`),
  ADD KEY `FK_ReturnExchangeDetailBatches_Batch` (`BatchID`);

--
-- Indexes for table `ReturnExchangeDetails`
--
ALTER TABLE `ReturnExchangeDetails`
  ADD PRIMARY KEY (`ReturnDetailID`),
  ADD KEY `FK_ReturnExchangeDetails_Returns` (`ReturnID`),
  ADD KEY `FK_ReturnExchangeDetails_Products` (`ProductID`);

--
-- Indexes for table `ReturnExchanges`
--
ALTER TABLE `ReturnExchanges`
  ADD PRIMARY KEY (`ReturnID`),
  ADD UNIQUE KEY `UQ_ReturnExchanges_RefundTransactionID` (`RefundTransactionID`),
  ADD KEY `FK_ReturnExchanges_Invoices` (`InvoiceID`),
  ADD KEY `FK_ReturnExchanges_ApprovedBy` (`ApprovedBy`),
  ADD KEY `FK_ReturnExchanges_CreatedBy` (`CreatedBy`),
  ADD KEY `FK_ReturnExchanges_RefundShift` (`RefundShiftID`),
  ADD KEY `FK_ReturnExchanges_RefundedBy` (`RefundedBy`);

--
-- Indexes for table `RolePermissions`
--
ALTER TABLE `RolePermissions`
  ADD PRIMARY KEY (`RoleID`,`PermissionID`),
  ADD KEY `FK_RolePermissions_Permissions` (`PermissionID`);

--
-- Indexes for table `Roles`
--
ALTER TABLE `Roles`
  ADD PRIMARY KEY (`RoleID`),
  ADD UNIQUE KEY `RoleCode` (`RoleCode`);

--
-- Indexes for table `ShiftCashTransactions`
--
ALTER TABLE `ShiftCashTransactions`
  ADD PRIMARY KEY (`CashTransactionID`),
  ADD UNIQUE KEY `TransactionCode` (`TransactionCode`),
  ADD KEY `FK_ShiftCashTransactions_Users` (`CreatedBy`),
  ADD KEY `IX_ShiftCashTransactions_ShiftTime` (`ShiftID`,`CreatedAt`);

--
-- Indexes for table `Shifts`
--
ALTER TABLE `Shifts`
  ADD PRIMARY KEY (`ShiftID`),
  ADD UNIQUE KEY `UQ_Shifts_OneOpenPerUser` (`OpenUserID`),
  ADD KEY `FK_Shifts_Users` (`UserID`),
  ADD KEY `FK_Shifts_ClosedBy` (`ClosedBy`);

--
-- Indexes for table `StockAlerts`
--
ALTER TABLE `StockAlerts`
  ADD PRIMARY KEY (`AlertID`),
  ADD KEY `FK_StockAlerts_ReportedBy` (`ReportedBy`),
  ADD KEY `FK_StockAlerts_ResolvedBy` (`ResolvedBy`),
  ADD KEY `IX_StockAlerts_Seen` (`SeenByInventoryManager`,`CreatedAt`),
  ADD KEY `IX_StockAlerts_Product_Status` (`ProductID`,`Status`);

--
-- Indexes for table `StockDisposalDetails`
--
ALTER TABLE `StockDisposalDetails`
  ADD PRIMARY KEY (`DisposalDetailID`),
  ADD UNIQUE KEY `UQ_Disposal_Batch` (`DisposalID`,`BatchID`),
  ADD KEY `FK_StockDisposalDetails_Batch` (`BatchID`),
  ADD KEY `IX_StockDisposalDetails_Product` (`ProductID`);

--
-- Indexes for table `StockDisposals`
--
ALTER TABLE `StockDisposals`
  ADD PRIMARY KEY (`DisposalID`),
  ADD UNIQUE KEY `UQ_StockDisposals_DisposalCode` (`DisposalCode`),
  ADD KEY `FK_StockDisposals_CreatedBy` (`CreatedBy`),
  ADD KEY `IX_StockDisposals_CreatedAt` (`CreatedAt`);

--
-- Indexes for table `StockReconciliation`
--
ALTER TABLE `StockReconciliation`
  ADD PRIMARY KEY (`ReconciliationID`),
  ADD KEY `FK_StockReconciliation_Products` (`ProductID`),
  ADD KEY `FK_StockReconciliation_CreatedBy` (`CreatedBy`),
  ADD KEY `FK_StockReconciliation_CheckedBy` (`CheckedBy`);

--
-- Indexes for table `StoreConfig`
--
ALTER TABLE `StoreConfig`
  ADD PRIMARY KEY (`ConfigKey`);

--
-- Indexes for table `SupplierProducts`
--
ALTER TABLE `SupplierProducts`
  ADD PRIMARY KEY (`SupplierID`,`ProductID`),
  ADD KEY `FK_SupplierProducts_Products` (`ProductID`);

--
-- Indexes for table `SupplierReturnDetails`
--
ALTER TABLE `SupplierReturnDetails`
  ADD PRIMARY KEY (`SupplierReturnDetailID`),
  ADD UNIQUE KEY `UQ_SupplierReturn_Batch` (`SupplierReturnID`,`BatchID`),
  ADD KEY `FK_SupplierReturnDetails_Batch` (`BatchID`),
  ADD KEY `IX_SupplierReturnDetails_Product` (`ProductID`);

--
-- Indexes for table `SupplierReturns`
--
ALTER TABLE `SupplierReturns`
  ADD PRIMARY KEY (`SupplierReturnID`),
  ADD UNIQUE KEY `UQ_SupplierReturns_Code` (`SupplierReturnCode`),
  ADD KEY `FK_SupplierReturns_CreatedBy` (`CreatedBy`),
  ADD KEY `IX_SupplierReturns_CreatedAt` (`CreatedAt`),
  ADD KEY `IX_SupplierReturns_Supplier` (`SupplierID`);

--
-- Indexes for table `Suppliers`
--
ALTER TABLE `Suppliers`
  ADD PRIMARY KEY (`SupplierID`);

--
-- Indexes for table `Users`
--
ALTER TABLE `Users`
  ADD PRIMARY KEY (`UserID`),
  ADD UNIQUE KEY `Username` (`Username`),
  ADD KEY `FK_Users_Roles` (`RoleID`);

--
-- Indexes for table `UserTwoFactor`
--
ALTER TABLE `UserTwoFactor`
  ADD PRIMARY KEY (`UserID`);

--
-- Indexes for table `UserTwoFactorBackupCodes`
--
ALTER TABLE `UserTwoFactorBackupCodes`
  ADD PRIMARY KEY (`BackupCodeID`),
  ADD KEY `FK_UserTwoFactorBackupCodes_User` (`UserID`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `AuditLogs`
--
ALTER TABLE `AuditLogs`
  MODIFY `LogID` bigint NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=1470;

--
-- AUTO_INCREMENT for table `Categories`
--
ALTER TABLE `Categories`
  MODIFY `CategoryID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `ChatConversations`
--
ALTER TABLE `ChatConversations`
  MODIFY `ConversationID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `ChatMessages`
--
ALTER TABLE `ChatMessages`
  MODIFY `MessageID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=58;

--
-- AUTO_INCREMENT for table `ExceptionReports`
--
ALTER TABLE `ExceptionReports`
  MODIFY `ReportID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `InventoryBatch`
--
ALTER TABLE `InventoryBatch`
  MODIFY `BatchID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `InventoryTransactions`
--
ALTER TABLE `InventoryTransactions`
  MODIFY `TransactionID` bigint NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `InvoiceDetails`
--
ALTER TABLE `InvoiceDetails`
  MODIFY `InvoiceDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Invoices`
--
ALTER TABLE `Invoices`
  MODIFY `InvoiceID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `OrderDetails`
--
ALTER TABLE `OrderDetails`
  MODIFY `OrderDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Orders`
--
ALTER TABLE `Orders`
  MODIFY `OrderID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Permissions`
--
ALTER TABLE `Permissions`
  MODIFY `PermissionID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=114;

--
-- AUTO_INCREMENT for table `Products`
--
ALTER TABLE `Products`
  MODIFY `ProductID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Promotions`
--
ALTER TABLE `Promotions`
  MODIFY `PromotionID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `PurchaseReceiptDetails`
--
ALTER TABLE `PurchaseReceiptDetails`
  MODIFY `ReceiptDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `PurchaseReceipts`
--
ALTER TABLE `PurchaseReceipts`
  MODIFY `ReceiptID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `ReturnExchangeDetails`
--
ALTER TABLE `ReturnExchangeDetails`
  MODIFY `ReturnDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `ReturnExchanges`
--
ALTER TABLE `ReturnExchanges`
  MODIFY `ReturnID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Roles`
--
ALTER TABLE `Roles`
  MODIFY `RoleID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT for table `ShiftCashTransactions`
--
ALTER TABLE `ShiftCashTransactions`
  MODIFY `CashTransactionID` bigint NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Shifts`
--
ALTER TABLE `Shifts`
  MODIFY `ShiftID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `StockAlerts`
--
ALTER TABLE `StockAlerts`
  MODIFY `AlertID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `StockDisposalDetails`
--
ALTER TABLE `StockDisposalDetails`
  MODIFY `DisposalDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `StockDisposals`
--
ALTER TABLE `StockDisposals`
  MODIFY `DisposalID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `StockReconciliation`
--
ALTER TABLE `StockReconciliation`
  MODIFY `ReconciliationID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=222;

--
-- AUTO_INCREMENT for table `SupplierReturnDetails`
--
ALTER TABLE `SupplierReturnDetails`
  MODIFY `SupplierReturnDetailID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `SupplierReturns`
--
ALTER TABLE `SupplierReturns`
  MODIFY `SupplierReturnID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `Suppliers`
--
ALTER TABLE `Suppliers`
  MODIFY `SupplierID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT for table `Users`
--
ALTER TABLE `Users`
  MODIFY `UserID` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `UserTwoFactorBackupCodes`
--
ALTER TABLE `UserTwoFactorBackupCodes`
  MODIFY `BackupCodeID` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=161;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `AuditLogs`
--
ALTER TABLE `AuditLogs`
  ADD CONSTRAINT `FK_AuditLogs_Users` FOREIGN KEY (`UserID`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `ChatConversations`
--
ALTER TABLE `ChatConversations`
  ADD CONSTRAINT `FK_ChatConversations_Customer` FOREIGN KEY (`CustomerUserID`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ChatConversations_StaffA` FOREIGN KEY (`StaffUserIdA`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ChatConversations_StaffB` FOREIGN KEY (`StaffUserIdB`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `ChatMessages`
--
ALTER TABLE `ChatMessages`
  ADD CONSTRAINT `FK_ChatMessages_Conversations` FOREIGN KEY (`ConversationID`) REFERENCES `ChatConversations` (`ConversationID`),
  ADD CONSTRAINT `FK_ChatMessages_Sender` FOREIGN KEY (`SenderUserID`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `Customers`
--
ALTER TABLE `Customers`
  ADD CONSTRAINT `FK_Customers_Users` FOREIGN KEY (`CustomerID`) REFERENCES `Users` (`UserID`) ON DELETE CASCADE;

--
-- Constraints for table `Employees`
--
ALTER TABLE `Employees`
  ADD CONSTRAINT `FK_Employees_Users` FOREIGN KEY (`UserID`) REFERENCES `Users` (`UserID`) ON DELETE CASCADE;

--
-- Constraints for table `ExceptionReports`
--
ALTER TABLE `ExceptionReports`
  ADD CONSTRAINT `FK_ExceptionReports_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ExceptionReports_HandledBy` FOREIGN KEY (`HandledBy`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `InventoryBatch`
--
ALTER TABLE `InventoryBatch`
  ADD CONSTRAINT `FK_InventoryBatch_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_InventoryBatch_ReceiptDetail` FOREIGN KEY (`ReceiptDetailID`) REFERENCES `PurchaseReceiptDetails` (`ReceiptDetailID`),
  ADD CONSTRAINT `FK_InventoryBatch_Suppliers` FOREIGN KEY (`SupplierID`) REFERENCES `Suppliers` (`SupplierID`);

--
-- Constraints for table `InventoryTransactions`
--
ALTER TABLE `InventoryTransactions`
  ADD CONSTRAINT `FK_InventoryTransactions_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_InventoryTransactions_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`);

--
-- Constraints for table `InvoiceDetailBatches`
--
ALTER TABLE `InvoiceDetailBatches`
  ADD CONSTRAINT `FK_InvoiceDetailBatches_Batch` FOREIGN KEY (`BatchID`) REFERENCES `InventoryBatch` (`BatchID`),
  ADD CONSTRAINT `FK_InvoiceDetailBatches_Details` FOREIGN KEY (`InvoiceDetailID`) REFERENCES `InvoiceDetails` (`InvoiceDetailID`);

--
-- Constraints for table `InvoiceDetails`
--
ALTER TABLE `InvoiceDetails`
  ADD CONSTRAINT `FK_InvoiceDetails_Invoices` FOREIGN KEY (`InvoiceID`) REFERENCES `Invoices` (`InvoiceID`),
  ADD CONSTRAINT `FK_InvoiceDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`);

--
-- Constraints for table `Invoices`
--
ALTER TABLE `Invoices`
  ADD CONSTRAINT `FK_Invoices_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_Invoices_Customers` FOREIGN KEY (`CustomerID`) REFERENCES `Customers` (`CustomerID`),
  ADD CONSTRAINT `FK_Invoices_Promotion` FOREIGN KEY (`PromotionID`) REFERENCES `Promotions` (`PromotionID`),
  ADD CONSTRAINT `FK_Invoices_Shifts` FOREIGN KEY (`ShiftID`) REFERENCES `Shifts` (`ShiftID`);

--
-- Constraints for table `OrderDetailBatches`
--
ALTER TABLE `OrderDetailBatches`
  ADD CONSTRAINT `FK_OrderDetailBatches_Batch` FOREIGN KEY (`BatchID`) REFERENCES `InventoryBatch` (`BatchID`),
  ADD CONSTRAINT `FK_OrderDetailBatches_Details` FOREIGN KEY (`OrderDetailID`) REFERENCES `OrderDetails` (`OrderDetailID`);

--
-- Constraints for table `OrderDetails`
--
ALTER TABLE `OrderDetails`
  ADD CONSTRAINT `FK_OrderDetails_Orders` FOREIGN KEY (`OrderID`) REFERENCES `Orders` (`OrderID`),
  ADD CONSTRAINT `FK_OrderDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`);

--
-- Constraints for table `Orders`
--
ALTER TABLE `Orders`
  ADD CONSTRAINT `FK_Orders_Customers` FOREIGN KEY (`CustomerID`) REFERENCES `Customers` (`CustomerID`),
  ADD CONSTRAINT `FK_Orders_Invoices` FOREIGN KEY (`InvoiceID`) REFERENCES `Invoices` (`InvoiceID`),
  ADD CONSTRAINT `FK_Orders_Promotion` FOREIGN KEY (`PromotionID`) REFERENCES `Promotions` (`PromotionID`);

--
-- Constraints for table `Products`
--
ALTER TABLE `Products`
  ADD CONSTRAINT `FK_Products_Categories` FOREIGN KEY (`CategoryID`) REFERENCES `Categories` (`CategoryID`);

--
-- Constraints for table `Promotions`
--
ALTER TABLE `Promotions`
  ADD CONSTRAINT `FK_Promotions_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `PurchaseReceiptDetails`
--
ALTER TABLE `PurchaseReceiptDetails`
  ADD CONSTRAINT `FK_PurchaseReceiptDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_PurchaseReceiptDetails_Receipts` FOREIGN KEY (`ReceiptID`) REFERENCES `PurchaseReceipts` (`ReceiptID`);

--
-- Constraints for table `PurchaseReceipts`
--
ALTER TABLE `PurchaseReceipts`
  ADD CONSTRAINT `FK_PurchaseReceipts_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_PurchaseReceipts_Suppliers` FOREIGN KEY (`SupplierID`) REFERENCES `Suppliers` (`SupplierID`);

--
-- Constraints for table `ReturnExchangeDetailBatches`
--
ALTER TABLE `ReturnExchangeDetailBatches`
  ADD CONSTRAINT `FK_ReturnExchangeDetailBatches_Batch` FOREIGN KEY (`BatchID`) REFERENCES `InventoryBatch` (`BatchID`),
  ADD CONSTRAINT `FK_ReturnExchangeDetailBatches_Details` FOREIGN KEY (`ReturnDetailID`) REFERENCES `ReturnExchangeDetails` (`ReturnDetailID`);

--
-- Constraints for table `ReturnExchangeDetails`
--
ALTER TABLE `ReturnExchangeDetails`
  ADD CONSTRAINT `FK_ReturnExchangeDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_ReturnExchangeDetails_Returns` FOREIGN KEY (`ReturnID`) REFERENCES `ReturnExchanges` (`ReturnID`);

--
-- Constraints for table `ReturnExchanges`
--
ALTER TABLE `ReturnExchanges`
  ADD CONSTRAINT `FK_ReturnExchanges_ApprovedBy` FOREIGN KEY (`ApprovedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ReturnExchanges_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ReturnExchanges_Invoices` FOREIGN KEY (`InvoiceID`) REFERENCES `Invoices` (`InvoiceID`),
  ADD CONSTRAINT `FK_ReturnExchanges_RefundedBy` FOREIGN KEY (`RefundedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_ReturnExchanges_RefundShift` FOREIGN KEY (`RefundShiftID`) REFERENCES `Shifts` (`ShiftID`);

--
-- Constraints for table `RolePermissions`
--
ALTER TABLE `RolePermissions`
  ADD CONSTRAINT `FK_RolePermissions_Permissions` FOREIGN KEY (`PermissionID`) REFERENCES `Permissions` (`PermissionID`),
  ADD CONSTRAINT `FK_RolePermissions_Roles` FOREIGN KEY (`RoleID`) REFERENCES `Roles` (`RoleID`);

--
-- Constraints for table `ShiftCashTransactions`
--
ALTER TABLE `ShiftCashTransactions`
  ADD CONSTRAINT `FK_ShiftCashTransactions_Shifts` FOREIGN KEY (`ShiftID`) REFERENCES `Shifts` (`ShiftID`),
  ADD CONSTRAINT `FK_ShiftCashTransactions_Users` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `Shifts`
--
ALTER TABLE `Shifts`
  ADD CONSTRAINT `FK_Shifts_ClosedBy` FOREIGN KEY (`ClosedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_Shifts_Users` FOREIGN KEY (`UserID`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `StockAlerts`
--
ALTER TABLE `StockAlerts`
  ADD CONSTRAINT `FK_StockAlerts_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_StockAlerts_ReportedBy` FOREIGN KEY (`ReportedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_StockAlerts_ResolvedBy` FOREIGN KEY (`ResolvedBy`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `StockDisposalDetails`
--
ALTER TABLE `StockDisposalDetails`
  ADD CONSTRAINT `FK_StockDisposalDetails_Batch` FOREIGN KEY (`BatchID`) REFERENCES `InventoryBatch` (`BatchID`),
  ADD CONSTRAINT `FK_StockDisposalDetails_Disposals` FOREIGN KEY (`DisposalID`) REFERENCES `StockDisposals` (`DisposalID`),
  ADD CONSTRAINT `FK_StockDisposalDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`);

--
-- Constraints for table `StockDisposals`
--
ALTER TABLE `StockDisposals`
  ADD CONSTRAINT `FK_StockDisposals_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `StockReconciliation`
--
ALTER TABLE `StockReconciliation`
  ADD CONSTRAINT `FK_StockReconciliation_CheckedBy` FOREIGN KEY (`CheckedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_StockReconciliation_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_StockReconciliation_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`);

--
-- Constraints for table `SupplierProducts`
--
ALTER TABLE `SupplierProducts`
  ADD CONSTRAINT `FK_SupplierProducts_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_SupplierProducts_Suppliers` FOREIGN KEY (`SupplierID`) REFERENCES `Suppliers` (`SupplierID`);

--
-- Constraints for table `SupplierReturnDetails`
--
ALTER TABLE `SupplierReturnDetails`
  ADD CONSTRAINT `FK_SupplierReturnDetails_Batch` FOREIGN KEY (`BatchID`) REFERENCES `InventoryBatch` (`BatchID`),
  ADD CONSTRAINT `FK_SupplierReturnDetails_Products` FOREIGN KEY (`ProductID`) REFERENCES `Products` (`ProductID`),
  ADD CONSTRAINT `FK_SupplierReturnDetails_Returns` FOREIGN KEY (`SupplierReturnID`) REFERENCES `SupplierReturns` (`SupplierReturnID`);

--
-- Constraints for table `SupplierReturns`
--
ALTER TABLE `SupplierReturns`
  ADD CONSTRAINT `FK_SupplierReturns_CreatedBy` FOREIGN KEY (`CreatedBy`) REFERENCES `Users` (`UserID`),
  ADD CONSTRAINT `FK_SupplierReturns_Suppliers` FOREIGN KEY (`SupplierID`) REFERENCES `Suppliers` (`SupplierID`);

--
-- Constraints for table `Users`
--
ALTER TABLE `Users`
  ADD CONSTRAINT `FK_Users_Roles` FOREIGN KEY (`RoleID`) REFERENCES `Roles` (`RoleID`);

--
-- Constraints for table `UserTwoFactor`
--
ALTER TABLE `UserTwoFactor`
  ADD CONSTRAINT `FK_UserTwoFactor_User` FOREIGN KEY (`UserID`) REFERENCES `Users` (`UserID`);

--
-- Constraints for table `UserTwoFactorBackupCodes`
--
ALTER TABLE `UserTwoFactorBackupCodes`
  ADD CONSTRAINT `FK_UserTwoFactorBackupCodes_User` FOREIGN KEY (`UserID`) REFERENCES `Users` (`UserID`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
