/* ============================================================
   A1-A3 - INVOICE CANCELLATION APPROVAL WORKFLOW
   SIMS / MySQL

   Muc tieu:
   - SALES_STAFF khong huy hoa don truc tiep.
   - SALES_STAFF gui INVOICE_CANCEL_REQUEST.
   - SALES_MANAGER / ADMIN duyet hoac tu choi.
   - Chi khi duyet thanh cong moi goi logic huy hoa don hien tai.

   Script idempotent: co the chay lai.
   ============================================================ */

USE SIMS_DB;

/* 1) Permission moi */
INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('INVOICE_VIEW_OWN', 'Xem hóa đơn của chính nhân viên'),
('INVOICE_VIEW_ALL', 'Xem tất cả hóa đơn'),
('INVOICE_CANCEL_REQUEST', 'Gửi yêu cầu hủy hóa đơn để quản lý duyệt');

/* SALES_STAFF: co quyen request, khong con quyen huy truc tiep */
INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r
JOIN Permissions p ON p.PermissionCode IN ('INVOICE_VIEW_OWN', 'INVOICE_CANCEL_REQUEST')
WHERE r.RoleCode = 'SALES_STAFF';

DELETE rp
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode = 'INVOICE_CANCEL';

/* Bao dam SALES_MANAGER co quyen xem toan bo + huy de duyet request */
INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r
JOIN Permissions p ON p.PermissionCode IN ('INVOICE_VIEW_ALL', 'INVOICE_CANCEL')
WHERE r.RoleCode = 'SALES_MANAGER';

/* 2) Bang workflow */
CREATE TABLE IF NOT EXISTS InvoiceCancelRequests (
    RequestID       INT AUTO_INCREMENT PRIMARY KEY,
    InvoiceID       INT NOT NULL,
    RequestedBy     INT NOT NULL,
    Reason          VARCHAR(500) NOT NULL,
    Status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    RequestedAt     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ReviewedBy      INT NULL,
    ReviewedAt      DATETIME NULL,
    ReviewNote      VARCHAR(500) NULL,

    /*
     * MySQL cho phep nhieu NULL trong UNIQUE index.
     * Vi vay chi PENDING/PROCESSING sinh ActiveInvoiceID = InvoiceID,
     * bao dam 1 hoa don khong co 2 request dang hoat dong do race-condition.
     */
    ActiveInvoiceID INT GENERATED ALWAYS AS (
        CASE
            WHEN Status IN ('PENDING', 'PROCESSING') THEN InvoiceID
            ELSE NULL
        END
    ) STORED,

    CONSTRAINT CK_InvoiceCancelRequests_Status
        CHECK (Status IN ('PENDING', 'PROCESSING', 'APPROVED', 'REJECTED')),

    CONSTRAINT FK_InvoiceCancelRequests_Invoice
        FOREIGN KEY (InvoiceID) REFERENCES Invoices(InvoiceID),

    CONSTRAINT FK_InvoiceCancelRequests_RequestedBy
        FOREIGN KEY (RequestedBy) REFERENCES Users(UserID),

    CONSTRAINT FK_InvoiceCancelRequests_ReviewedBy
        FOREIGN KEY (ReviewedBy) REFERENCES Users(UserID),

    UNIQUE KEY UQ_InvoiceCancelRequests_ActiveInvoice (ActiveInvoiceID),
    KEY IX_InvoiceCancelRequests_Invoice (InvoiceID),
    KEY IX_InvoiceCancelRequests_StatusRequestedAt (Status, RequestedAt),
    KEY IX_InvoiceCancelRequests_RequestedBy (RequestedBy)
) ENGINE=InnoDB;

/* 3) Kiem tra nhanh sau migration */
SELECT r.RoleCode, p.PermissionCode
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode IN ('SALES_STAFF', 'SALES_MANAGER')
  AND p.PermissionCode IN (
      'INVOICE_VIEW_OWN',
      'INVOICE_VIEW_ALL',
      'INVOICE_CANCEL_REQUEST',
      'INVOICE_CANCEL'
  )
ORDER BY r.RoleCode, p.PermissionCode;
