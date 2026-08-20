/*
 * SIMS - Dữ liệu bổ sung cho biểu đồ Thu/Chi
 * Kỳ: 2026-08-14 đến 2026-08-20
 *
 * Chạy sau 01_SIMS_Schema_MySQL.sql, 02_SIMS_Triggers_MySQL.sql
 * và dữ liệu nền (03_SIMS_SampleData_MySQL.sql hoặc SIMS_DB_1.sql).
 *
 * Dữ liệu bán hàng đi qua các bảng nghiệp vụ. Trigger sẽ:
 * - tạo lô và cập nhật tồn khi nhập hàng;
 * - phân bổ FEFO, giảm tồn và ghi InventoryTransactions khi bán;
 * - không chèn trực tiếp vào Products.Stock.
 *
 * Doanh thu báo cáo: Invoices.TotalAmount với Status = ACTIVE.
 * Chi báo cáo: giá vốn hàng bán + StockDisposals đã COMPLETED.
 */

USE SIMS_DB;

START TRANSACTION;

/* Bổ sung tồn hợp lệ để đủ hàng cho chuỗi bán hàng mô phỏng. */
INSERT INTO PurchaseReceipts
    (ReceiptID, ReceiptCode, SupplierID, CreatedBy, CreatedAt, TotalAmount, Status)
VALUES
    (25, 'PN-20260814-0025', 2, 3, '2026-08-14 06:30:00', 2400000, 'COMPLETED'),
    (26, 'PN-20260814-0026', 2, 3, '2026-08-14 06:45:00', 1800000, 'COMPLETED'),
    (27, 'PN-20260814-0027', 3, 3, '2026-08-14 07:00:00', 1750000, 'COMPLETED'),
    (28, 'PN-20260814-0028', 2, 3, '2026-08-14 07:15:00', 3250000, 'COMPLETED');

INSERT INTO PurchaseReceiptDetails
    (ReceiptDetailID, ReceiptID, ProductID, Quantity, ImportPrice,
     LotNumber, ManufactureDate, ExpiryDate)
VALUES
    (25, 25, 5, 600, 4000, 'LOT-NUOC-20260814', NULL, '2027-08-14'),
    (26, 26, 6, 300, 6000, 'LOT-TRAXANH-20260814', '2026-08-01', '2027-02-14'),
    (27, 27, 3, 100, 17500, 'LOT-CACHUA-20260814', '2026-08-10', '2026-09-10'),
    (28, 28, 7, 50, 65000, 'LOT-CAPHE-20260814', '2026-08-10', '2027-08-14');

/* Mỗi ngày có một ca đóng, hóa đơn thuộc ca và nhân viên hợp lệ. */
INSERT INTO Shifts
    (ShiftID, UserID, StartTime, EndTime, Status, OpeningCash,
     ExpectedCash, CountedCash, CashDifference, ClosedBy)
VALUES
    (37, 4, '2026-08-14 08:00:00', '2026-08-14 21:00:00', 'CLOSED', 500000, 1531400, 1531400, 0, 4),
    (38, 4, '2026-08-15 08:00:00', '2026-08-15 21:00:00', 'CLOSED', 500000, 1614560, 1614560, 0, 4),
    (39, 4, '2026-08-16 08:00:00', '2026-08-16 21:00:00', 'CLOSED', 500000, 2541200, 2541200, 0, 4),
    (40, 4, '2026-08-17 08:00:00', '2026-08-17 21:00:00', 'CLOSED', 500000, 2843600, 2843600, 0, 4),
    (41, 4, '2026-08-18 08:00:00', '2026-08-18 21:00:00', 'CLOSED', 500000, 2843600, 2843600, 0, 4),
    (42, 4, '2026-08-19 08:00:00', '2026-08-19 21:00:00', 'CLOSED', 500000, 2152400, 2152400, 0, 4),
    (43, 4, '2026-08-20 08:00:00', '2026-08-20 21:00:00', 'CLOSED', 500000, 1482800, 1482800, 0, 4);

/* Hóa đơn ACTIVE; VAT 8%, không giảm giá để số liệu dễ đối soát. */
INSERT INTO Invoices
    (InvoiceID, InvoiceCode, ShiftID, CreatedBy, CreatedAt, SubTotal,
     DiscountAmount, VATRate, TotalAmount, OriginalTotalAmount, PaymentMethod, Status)
VALUES
    (42, 'HD-20260814-0042', 37, 4, '2026-08-14 09:15:00', 955000, 0, 8, 1031400, 1031400, 'CASH', 'ACTIVE'),
    (43, 'HD-20260814-0043', 37, 4, '2026-08-14 13:20:00', 574000, 0, 8, 619920, 619920, 'BANK_TRANSFER', 'ACTIVE'),
    (44, 'HD-20260814-0044', 37, 4, '2026-08-14 18:10:00', 735000, 0, 8, 793800, 793800, 'CARD', 'ACTIVE'),

    (45, 'HD-20260815-0045', 38, 4, '2026-08-15 09:10:00', 1032000, 0, 8, 1114560, 1114560, 'CASH', 'ACTIVE'),
    (46, 'HD-20260815-0046', 38, 4, '2026-08-15 13:30:00', 598000, 0, 8, 645840, 645840, 'BANK_TRANSFER', 'ACTIVE'),
    (47, 'HD-20260815-0047', 38, 4, '2026-08-15 18:20:00', 412000, 0, 8, 444960, 444960, 'CARD', 'ACTIVE'),

    (48, 'HD-20260816-0048', 39, 4, '2026-08-16 09:00:00', 1890000, 0, 8, 2041200, 2041200, 'CASH', 'ACTIVE'),
    (49, 'HD-20260816-0049', 39, 4, '2026-08-16 13:00:00', 3160000, 0, 8, 3412800, 3412800, 'BANK_TRANSFER', 'ACTIVE'),
    (50, 'HD-20260816-0050', 39, 4, '2026-08-16 18:00:00', 2190000, 0, 8, 2365200, 2365200, 'PAYPAL', 'ACTIVE'),

    (51, 'HD-20260817-0051', 40, 4, '2026-08-17 09:00:00', 2170000, 0, 8, 2343600, 2343600, 'CASH', 'ACTIVE'),
    (52, 'HD-20260817-0052', 40, 4, '2026-08-17 13:00:00', 3160000, 0, 8, 3412800, 3412800, 'BANK_TRANSFER', 'ACTIVE'),
    (53, 'HD-20260817-0053', 40, 4, '2026-08-17 18:00:00', 2520000, 0, 8, 2721600, 2721600, 'CARD', 'ACTIVE'),

    (54, 'HD-20260818-0054', 41, 4, '2026-08-18 09:00:00', 2170000, 0, 8, 2343600, 2343600, 'CASH', 'ACTIVE'),
    (55, 'HD-20260818-0055', 41, 4, '2026-08-18 13:00:00', 3160000, 0, 8, 3412800, 3412800, 'BANK_TRANSFER', 'ACTIVE'),
    (56, 'HD-20260818-0056', 41, 4, '2026-08-18 18:00:00', 2520000, 0, 8, 2721600, 2721600, 'PAYPAL', 'ACTIVE'),

    (57, 'HD-20260819-0057', 42, 4, '2026-08-19 09:00:00', 1530000, 0, 8, 1652400, 1652400, 'CASH', 'ACTIVE'),
    (58, 'HD-20260819-0058', 42, 4, '2026-08-19 13:00:00', 1230000, 0, 8, 1328400, 1328400, 'BANK_TRANSFER', 'ACTIVE'),
    (59, 'HD-20260819-0059', 42, 4, '2026-08-19 18:00:00', 935000, 0, 8, 1009800, 1009800, 'CARD', 'ACTIVE'),

    (60, 'HD-20260820-0060', 43, 4, '2026-08-20 09:00:00', 910000, 0, 8, 982800, 982800, 'CASH', 'ACTIVE'),
    (61, 'HD-20260820-0061', 43, 4, '2026-08-20 13:00:00', 1580000, 0, 8, 1706400, 1706400, 'BANK_TRANSFER', 'ACTIVE'),
    (62, 'HD-20260820-0062', 43, 4, '2026-08-20 18:00:00', 845000, 0, 8, 912600, 912600, 'CARD', 'ACTIVE');

/* Chi tiết bán hàng; trigger tự kiểm tra tồn, FEFO và ghi sổ kho. */
INSERT INTO InvoiceDetails (InvoiceDetailID, InvoiceID, ProductID, Quantity, UnitPrice) VALUES
    (71, 42, 7, 10, 70000), (72, 42, 9, 5, 33000), (73, 42, 5, 10, 9000),
    (74, 43, 1, 8, 40000), (75, 43, 3, 6, 24000), (76, 43, 6, 10, 11000),
    (77, 44, 7, 8, 70000), (78, 44, 2, 5, 35000),

    (79, 45, 7, 12, 70000), (80, 45, 3, 8, 24000),
    (81, 46, 1, 10, 40000), (82, 46, 9, 6, 33000),
    (83, 47, 2, 8, 35000), (84, 47, 6, 12, 11000),

    (85, 48, 5, 120, 9000), (86, 48, 6, 30, 11000), (87, 48, 3, 20, 24000),
    (88, 49, 7, 30, 70000), (89, 49, 9, 20, 33000), (90, 49, 1, 10, 40000),
    (91, 50, 5, 100, 9000), (92, 50, 6, 20, 11000), (93, 50, 3, 30, 24000), (94, 50, 2, 10, 35000),

    (95, 51, 5, 100, 9000), (96, 51, 6, 50, 11000), (97, 51, 3, 30, 24000),
    (98, 52, 7, 30, 70000), (99, 52, 9, 20, 33000), (100, 52, 1, 10, 40000),
    (101, 53, 5, 100, 9000), (102, 53, 6, 50, 11000), (103, 53, 3, 30, 24000), (104, 53, 2, 10, 35000),

    (105, 54, 5, 100, 9000), (106, 54, 6, 50, 11000), (107, 54, 3, 30, 24000),
    (108, 55, 7, 30, 70000), (109, 55, 9, 20, 33000), (110, 55, 1, 10, 40000),
    (111, 56, 5, 100, 9000), (112, 56, 6, 50, 11000), (113, 56, 3, 30, 24000), (114, 56, 2, 10, 35000),

    (115, 57, 5, 80, 9000), (116, 57, 6, 30, 11000), (117, 57, 3, 20, 24000),
    (118, 58, 7, 10, 70000), (119, 58, 9, 10, 33000), (120, 58, 1, 5, 40000),
    (121, 59, 5, 60, 9000), (122, 59, 6, 20, 11000), (123, 59, 2, 5, 35000),

    (124, 60, 5, 50, 9000), (125, 60, 6, 20, 11000), (126, 60, 3, 10, 24000),
    (127, 61, 7, 15, 70000), (128, 61, 9, 10, 33000), (129, 61, 1, 5, 40000),
    (130, 62, 5, 50, 9000), (131, 62, 6, 20, 11000), (132, 62, 2, 5, 35000);

/* Thanh toán hoàn tất để cơ cấu phương thức thanh toán khớp doanh thu. */
INSERT INTO InvoicePayments
    (InvoiceID, PaymentMethod, Amount, TenderedAmount, ChangeAmount,
     PaymentStatus, CreatedBy, CreatedAt)
SELECT InvoiceID, PaymentMethod, TotalAmount, TotalAmount, 0,
       'COMPLETED', CreatedBy, CreatedAt
FROM Invoices
WHERE InvoiceID BETWEEN 42 AND 62;

COMMIT;

/* Đối soát nhanh sau khi chạy script. */
SELECT CAST(CreatedAt AS DATE) AS Ngay,
       SUM(TotalAmount) AS DoanhThu,
       COUNT(*) AS SoHoaDon
FROM Invoices
WHERE Status = 'ACTIVE'
  AND CAST(CreatedAt AS DATE) BETWEEN '2026-08-14' AND '2026-08-20'
GROUP BY CAST(CreatedAt AS DATE)
ORDER BY Ngay;

SELECT d.Ngay, d.DoanhThu,
       COALESCE(c.GiaVon, 0) + COALESCE(l.Loss, 0) AS Chi,
       d.DoanhThu - COALESCE(c.GiaVon, 0) - COALESCE(l.Loss, 0) AS Lai
FROM (
    SELECT CAST(CreatedAt AS DATE) AS Ngay, SUM(TotalAmount) AS DoanhThu
    FROM Invoices
    WHERE Status = 'ACTIVE'
      AND CAST(CreatedAt AS DATE) BETWEEN '2026-08-14' AND '2026-08-20'
    GROUP BY CAST(CreatedAt AS DATE)
) d
LEFT JOIN (
    SELECT CAST(i.CreatedAt AS DATE) AS Ngay,
           SUM(id.Quantity * p.ImportPrice) AS GiaVon
    FROM InvoiceDetails id
    JOIN Invoices i ON i.InvoiceID = id.InvoiceID
    JOIN Products p ON p.ProductID = id.ProductID
    WHERE i.Status = 'ACTIVE'
      AND CAST(i.CreatedAt AS DATE) BETWEEN '2026-08-14' AND '2026-08-20'
    GROUP BY CAST(i.CreatedAt AS DATE)
) c ON c.Ngay = d.Ngay
LEFT JOIN (
    SELECT CAST(CreatedAt AS DATE) AS Ngay, SUM(TotalLossAmount) AS Loss
    FROM StockDisposals
    WHERE Status = 'COMPLETED'
      AND CAST(CreatedAt AS DATE) BETWEEN '2026-08-14' AND '2026-08-20'
    GROUP BY CAST(CreatedAt AS DATE)
) l ON l.Ngay = d.Ngay
ORDER BY d.Ngay;
