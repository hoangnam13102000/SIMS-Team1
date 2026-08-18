/* ============================================================
   07_VIETQR_PAYOS_POS.sql
   Bổ sung metadata VietQR/payOS cho hóa đơn bán tại quầy.

   Chạy file này MỘT LẦN nếu database MySQL hiện tại đã được tạo
   từ schema cũ. Với database tạo mới từ 01_SIMS_Schema_MySQL.sql
   sau bản cập nhật này thì không cần chạy.
   ============================================================ */

ALTER TABLE Invoices
    ADD COLUMN PayOsOrderCode BIGINT NULL AFTER PayPalCaptureID,
    ADD COLUMN PayOsPaymentLinkID VARCHAR(64) NULL AFTER PayOsOrderCode,
    ADD COLUMN BankTransferReference VARCHAR(100) NULL AFTER PayOsPaymentLinkID;

ALTER TABLE Invoices
    ADD CONSTRAINT UQ_Invoices_PayOsOrderCode UNIQUE (PayOsOrderCode),
    ADD CONSTRAINT UQ_Invoices_PayOsPaymentLinkID UNIQUE (PayOsPaymentLinkID);

-- Kiểm tra sau khi chạy:
SHOW COLUMNS FROM Invoices LIKE 'PayOs%';
SHOW COLUMNS FROM Invoices LIKE 'BankTransferReference';
