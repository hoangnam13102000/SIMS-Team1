/* ============================================================
   SIMS A10-A12 - INVOICE QR / RETURN EVIDENCE / INVOICE PAYMENTS
   MySQL 8.x / MariaDB compatible style
   Không dùng information_schema.
   ============================================================ */

USE SIMS_DB;

/* 1) Mỗi hóa đơn có thể có nhiều dòng thanh toán thực tế. */
CREATE TABLE IF NOT EXISTS InvoicePayments (
    PaymentID              BIGINT AUTO_INCREMENT PRIMARY KEY,
    InvoiceID              INT NOT NULL,
    PaymentMethod          VARCHAR(20) NOT NULL
                               CHECK (PaymentMethod IN ('CASH','BANK_TRANSFER','PAYPAL','CARD')),
    Amount                 DECIMAL(18,0) NOT NULL CHECK (Amount >= 0),
    TenderedAmount         DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (TenderedAmount >= 0),
    ChangeAmount           DECIMAL(18,0) NOT NULL DEFAULT 0 CHECK (ChangeAmount >= 0),
    Provider               VARCHAR(30) NULL,
    ProviderTransactionID  VARCHAR(120) NULL,
    ProviderPaymentID      VARCHAR(120) NULL,
    IdempotencyKey         VARCHAR(150) NULL,
    PaymentStatus          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
                               CHECK (PaymentStatus IN ('PENDING','COMPLETED','FAILED','REFUNDED')),
    CreatedBy              INT NOT NULL,
    CreatedAt              DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT FK_InvoicePayments_Invoice
        FOREIGN KEY (InvoiceID) REFERENCES Invoices(InvoiceID) ON DELETE CASCADE,
    CONSTRAINT FK_InvoicePayments_CreatedBy
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),

    UNIQUE KEY UQ_InvoicePayments_ProviderTxn (Provider, ProviderTransactionID),
    UNIQUE KEY UQ_InvoicePayments_Idempotency (IdempotencyKey),
    KEY IX_InvoicePayments_Invoice_Status (InvoiceID, PaymentStatus),
    KEY IX_InvoicePayments_Method_CreatedAt (PaymentMethod, CreatedAt)
) ENGINE=InnoDB;

/* Backfill hóa đơn cũ: mỗi HĐ cũ thành một dòng payment tương ứng. */
INSERT INTO InvoicePayments (
    InvoiceID, PaymentMethod, Amount, TenderedAmount, ChangeAmount,
    Provider, ProviderTransactionID, ProviderPaymentID, IdempotencyKey,
    PaymentStatus, CreatedBy, CreatedAt
)
SELECT
    i.InvoiceID,
    i.PaymentMethod,
    i.OriginalTotalAmount,
    CASE WHEN i.PaymentMethod='CASH' THEN i.OriginalTotalAmount ELSE 0 END,
    0,
    CASE
        WHEN i.PaymentMethod='PAYPAL' THEN 'PAYPAL'
        WHEN i.PaymentMethod='BANK_TRANSFER' THEN 'PAYOS'
        WHEN i.PaymentMethod='CARD' THEN 'CARD'
        ELSE NULL
    END,
    CASE
        WHEN i.PaymentMethod='PAYPAL' THEN i.PayPalCaptureID
        WHEN i.PaymentMethod='BANK_TRANSFER' THEN COALESCE(i.BankTransferReference, i.PayOsPaymentLinkID)
        ELSE NULL
    END,
    CASE
        WHEN i.PaymentMethod='PAYPAL' THEN i.PayPalOrderID
        WHEN i.PaymentMethod='BANK_TRANSFER' THEN i.PayOsPaymentLinkID
        ELSE NULL
    END,
    CASE
        WHEN i.PaymentMethod='PAYPAL' AND i.PayPalCaptureID IS NOT NULL THEN CONCAT('PAYPAL:', i.PayPalCaptureID)
        WHEN i.PaymentMethod='BANK_TRANSFER' AND i.PayOsOrderCode IS NOT NULL THEN CONCAT('PAYOS:', i.PayOsOrderCode)
        ELSE CONCAT('LEGACY-INVOICE:', i.InvoiceID)
    END,
    'COMPLETED',
    i.CreatedBy,
    i.CreatedAt
FROM Invoices i
WHERE NOT EXISTS (
    SELECT 1 FROM InvoicePayments p WHERE p.InvoiceID=i.InvoiceID
);

/* 2) Ảnh bằng chứng đổi/trả. Một phiếu có thể có nhiều ảnh. */
CREATE TABLE IF NOT EXISTS ReturnExchangeEvidence (
    EvidenceID       BIGINT AUTO_INCREMENT PRIMARY KEY,
    ReturnID         INT NOT NULL,
    ImageUrl         VARCHAR(500) NOT NULL,
    OriginalFileName VARCHAR(255) NULL,
    UploadedBy       INT NOT NULL,
    UploadedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT FK_ReturnExchangeEvidence_Return
        FOREIGN KEY (ReturnID) REFERENCES ReturnExchanges(ReturnID) ON DELETE CASCADE,
    CONSTRAINT FK_ReturnExchangeEvidence_UploadedBy
        FOREIGN KEY (UploadedBy) REFERENCES Users(UserID),

    KEY IX_ReturnExchangeEvidence_Return (ReturnID, UploadedAt)
) ENGINE=InnoDB;

/* 3) Kiểm tra nhanh */
SHOW COLUMNS FROM InvoicePayments;
SHOW COLUMNS FROM ReturnExchangeEvidence;

SELECT PaymentMethod, COUNT(*) AS PaymentRows, SUM(Amount) AS Amount
FROM InvoicePayments
WHERE PaymentStatus='COMPLETED'
GROUP BY PaymentMethod
ORDER BY PaymentMethod;
