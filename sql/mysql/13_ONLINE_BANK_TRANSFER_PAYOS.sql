-- ============================================================
-- 13_ONLINE_BANK_TRANSFER_PAYOS.sql
-- Online checkout: BANK_TRANSFER via VietQR/payOS
-- Run ONCE before launching the patched application.
-- ============================================================

-- 1) Extend Orders with payOS reconciliation metadata.
ALTER TABLE Orders
    ADD COLUMN PayOsOrderCode BIGINT NULL AFTER PayPalCaptureID,
    ADD COLUMN PayOsPaymentLinkID VARCHAR(64) NULL AFTER PayOsOrderCode,
    ADD COLUMN BankTransferReference VARCHAR(100) NULL AFTER PayOsPaymentLinkID;

-- 2) Current schema creates an unnamed CHECK for Orders.PaymentMethod.
--    MySQL auto-generates it as orders_chk_2 because:
--      orders_chk_1 = DiscountAmount >= 0
--      orders_chk_2 = PaymentMethod
--    Replace it with an explicit named constraint that also allows BANK_TRANSFER.
ALTER TABLE Orders
    DROP CHECK orders_chk_2;

ALTER TABLE Orders
    ADD CONSTRAINT CK_Orders_PaymentMethod
        CHECK (PaymentMethod IN ('COD', 'BANK_TRANSFER', 'PAYPAL'));

-- 3) Idempotency / reconciliation keys for payOS.
ALTER TABLE Orders
    ADD CONSTRAINT UQ_Orders_PayOsOrderCode UNIQUE (PayOsOrderCode),
    ADD CONSTRAINT UQ_Orders_PayOsPaymentLinkID UNIQUE (PayOsPaymentLinkID);

CREATE INDEX IX_Orders_PaymentMethod_Status
    ON Orders (PaymentMethod, PaymentStatus, CreatedAt);

-- ============================================================
-- QUICK CHECK
-- ============================================================
SHOW COLUMNS FROM Orders;

SELECT
    PaymentMethod,
    PaymentStatus,
    COUNT(*) AS Total
FROM Orders
GROUP BY PaymentMethod, PaymentStatus
ORDER BY PaymentMethod, PaymentStatus;
