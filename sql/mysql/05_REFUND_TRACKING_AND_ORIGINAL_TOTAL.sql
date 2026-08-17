USE SIMS_DB;

/* ============================================================
   05 - REFUND TRACKING + ORIGINAL INVOICE TOTAL

   Mục đích:
   1. Giữ lại tổng tiền ban đầu của hóa đơn trước khi đổi/trả.
   2. Lưu phương thức hoàn tiền thật.
   3. Biết refund thuộc ca nào.
   4. Theo dõi trạng thái hoàn tiền.
   5. Không còn suy luận:
        hóa đơn CASH => refund cũng CASH.
   ============================================================ */


/* ============================================================
   I. INVOICES - GIỮ TỔNG TIỀN BAN ĐẦU
   ============================================================ */

ALTER TABLE Invoices
ADD COLUMN OriginalTotalAmount DECIMAL(18,0) NOT NULL DEFAULT 0
AFTER TotalAmount;


/*
 * Với dữ liệu chưa từng đổi/trả:
 *
 * OriginalTotalAmount = TotalAmount.
 */
UPDATE Invoices
SET OriginalTotalAmount = TotalAmount;


/*
 * Khôi phục tổng tiền ban đầu cho các hóa đơn cũ
 * chỉ có nghiệp vụ RETURN.
 *
 * Ví dụ:
 *
 * TotalAmount hiện tại = 89.000
 * Đã RETURN             = 623.000
 *
 * OriginalTotalAmount   = 712.000
 *
 * Không tự suy luận hóa đơn đã có EXCHANGE,
 * vì project cũ chưa lưu riêng dòng tiền chênh lệch
 * của nghiệp vụ đổi hàng.
 */
UPDATE Invoices inv
SET inv.OriginalTotalAmount =
    inv.TotalAmount
    +
    COALESCE(
        (
            SELECT SUM(r.TotalValue)
            FROM ReturnExchanges r
            WHERE r.InvoiceID = inv.InvoiceID
              AND r.Status = 'APPROVED'
              AND r.Type = 'RETURN'
        ),
        0
    )
WHERE NOT EXISTS (
    SELECT 1
    FROM ReturnExchanges ex
    WHERE ex.InvoiceID = inv.InvoiceID
      AND ex.Status = 'APPROVED'
      AND ex.Type = 'EXCHANGE'
);


/* ============================================================
   II. RETURN EXCHANGES - THÔNG TIN HOÀN TIỀN
   ============================================================ */

ALTER TABLE ReturnExchanges

ADD COLUMN RefundMethod VARCHAR(20) NULL
AFTER PointsShare,

ADD COLUMN RefundShiftID INT NULL
AFTER RefundMethod,

ADD COLUMN RefundTransactionID VARCHAR(100) NULL
AFTER RefundShiftID,

ADD COLUMN RefundStatus VARCHAR(20) NOT NULL DEFAULT 'NONE'
AFTER RefundTransactionID,

ADD COLUMN RefundedBy INT NULL
AFTER RefundStatus,

ADD COLUMN RefundedAt DATETIME NULL
AFTER RefundedBy;


/* ============================================================
   III. CHECK CONSTRAINT
   ============================================================ */

ALTER TABLE ReturnExchanges

ADD CONSTRAINT CK_ReturnExchanges_RefundMethod
CHECK (
    RefundMethod IS NULL
    OR RefundMethod IN (
        'CASH',
        'BANK_TRANSFER',
        'CARD',
        'PAYPAL'
    )
),

ADD CONSTRAINT CK_ReturnExchanges_RefundStatus
CHECK (
    RefundStatus IN (
        'NONE',
        'PENDING',
        'COMPLETED',
        'FAILED'
    )
);


/* ============================================================
   IV. FOREIGN KEY
   ============================================================ */

ALTER TABLE ReturnExchanges

ADD CONSTRAINT FK_ReturnExchanges_RefundShift
FOREIGN KEY (RefundShiftID)
REFERENCES Shifts(ShiftID),

ADD CONSTRAINT FK_ReturnExchanges_RefundedBy
FOREIGN KEY (RefundedBy)
REFERENCES Users(UserID);


/* ============================================================
   V. UNIQUE TRANSACTION ID
   ============================================================ */

CREATE UNIQUE INDEX UQ_ReturnExchanges_RefundTransactionID
ON ReturnExchanges(RefundTransactionID);


/* ============================================================
   VI. MIGRATE DỮ LIỆU RETURN CŨ

   Chú ý:
   Dữ liệu cũ không lưu RefundMethod.

   Vì vậy chỉ trong migration legacy này mới tạm suy luận:
       PaymentMethod hóa đơn gốc
       => RefundMethod

   Code mới sau migration KHÔNG được suy luận kiểu này nữa.
   ============================================================ */

UPDATE ReturnExchanges r
JOIN Invoices inv
    ON inv.InvoiceID = r.InvoiceID
SET
    r.RefundMethod =
        CASE
            WHEN r.Type = 'RETURN'
                THEN inv.PaymentMethod
            ELSE NULL
        END,

    r.RefundStatus =
        CASE
            WHEN r.Type <> 'RETURN'
                THEN 'NONE'

            WHEN r.Status = 'REJECTED'
                THEN 'NONE'

            ELSE 'PENDING'
        END;


/* ============================================================
   VII. BACKFILL REFUND CASH CŨ

   Nếu một RETURN:
   - đã APPROVED
   - phương thức CASH
   - xác định được ca đang hoạt động tại ApprovedAt

   => xem như khoản CASH refund đã hoàn thành.

   Điều này giúp dữ liệu hiện tại của project tiếp tục
   đối soát được sau migration.
   ============================================================ */

UPDATE ReturnExchanges r
JOIN Shifts s
    ON s.UserID = r.CreatedBy
   AND r.ApprovedAt >= s.StartTime
   AND r.ApprovedAt <= COALESCE(s.EndTime, CURRENT_TIMESTAMP)
SET
    r.RefundShiftID = s.ShiftID,

    r.RefundTransactionID =
        CONCAT(
            'LEGACY-CASH-RET-',
            r.ReturnID
        ),

    r.RefundStatus = 'COMPLETED',

    r.RefundedBy = r.CreatedBy,

    r.RefundedAt = r.ApprovedAt

WHERE r.Type = 'RETURN'
  AND r.Status = 'APPROVED'
  AND r.RefundMethod = 'CASH';


/* ============================================================
   VIII. KIỂM TRA SAU MIGRATION
   ============================================================ */

SELECT
    InvoiceID,
    InvoiceCode,
    TotalAmount,
    OriginalTotalAmount
FROM Invoices
ORDER BY InvoiceID DESC
LIMIT 30;


SELECT
    ReturnID,
    InvoiceID,
    Type,
    TotalValue,
    RefundMethod,
    RefundShiftID,
    RefundTransactionID,
    RefundStatus,
    RefundedBy,
    RefundedAt,
    Status
FROM ReturnExchanges
ORDER BY ReturnID DESC;