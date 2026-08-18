USE SIMS_DB;

/*
 * ============================================================
 * PAYPAL IDEMPOTENCY / DUPLICATE PROTECTION
 * ============================================================
 *
 * Mục tiêu:
 *
 * 1. Một PayPal Order không thể được ghi thành nhiều hóa đơn.
 * 2. Một PayPal Capture không thể được ghi thành nhiều hóa đơn.
 * 3. Một PayPal Order/Capture không thể thuộc nhiều Online Order.
 *
 * NULL vẫn được phép vì CASH/CARD/BANK_TRANSFER không có
 * PayPalOrderID / PayPalCaptureID.
 * ============================================================
 */


/* ============================================================
   1. KIỂM TRA DUPLICATE TRƯỚC MIGRATION
   ============================================================ */

SELECT
    PayPalOrderID,
    COUNT(*) AS DuplicateCount
FROM Invoices
WHERE PayPalOrderID IS NOT NULL
  AND PayPalOrderID <> ''
GROUP BY PayPalOrderID
HAVING COUNT(*) > 1;


SELECT
    PayPalCaptureID,
    COUNT(*) AS DuplicateCount
FROM Invoices
WHERE PayPalCaptureID IS NOT NULL
  AND PayPalCaptureID <> ''
GROUP BY PayPalCaptureID
HAVING COUNT(*) > 1;


SELECT
    PayPalOrderID,
    COUNT(*) AS DuplicateCount
FROM Orders
WHERE PayPalOrderID IS NOT NULL
  AND PayPalOrderID <> ''
GROUP BY PayPalOrderID
HAVING COUNT(*) > 1;


SELECT
    PayPalCaptureID,
    COUNT(*) AS DuplicateCount
FROM Orders
WHERE PayPalCaptureID IS NOT NULL
  AND PayPalCaptureID <> ''
GROUP BY PayPalCaptureID
HAVING COUNT(*) > 1;


/* ============================================================
   2. UNIQUE CHO POS INVOICES
   ============================================================ */

ALTER TABLE Invoices
    ADD CONSTRAINT UQ_Invoices_PayPalOrderID
        UNIQUE (PayPalOrderID);

ALTER TABLE Invoices
    ADD CONSTRAINT UQ_Invoices_PayPalCaptureID
        UNIQUE (PayPalCaptureID);


/* ============================================================
   3. UNIQUE CHO ONLINE ORDERS
   ============================================================ */

ALTER TABLE Orders
    ADD CONSTRAINT UQ_Orders_PayPalOrderID
        UNIQUE (PayPalOrderID);

ALTER TABLE Orders
    ADD CONSTRAINT UQ_Orders_PayPalCaptureID
        UNIQUE (PayPalCaptureID);