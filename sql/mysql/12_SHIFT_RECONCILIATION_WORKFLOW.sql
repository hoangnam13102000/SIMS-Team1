-- ============================================================
-- SIMS - MIGRATION 12
-- Tach vong doi ca ban hang khoi trang thai doi soat quy.
-- P1-P4: SHIFT_APPROVE + ShiftReconciliations + resubmit history.
-- P5-P7 nam o Java/UI/test va dung chung schema nay.
--
-- Khong dung information_schema (phu hop tai khoan aptech bi han quyen).
-- Co the chay lai: CREATE IF NOT EXISTS + INSERT IGNORE.
-- ============================================================

CREATE TABLE IF NOT EXISTS ShiftReconciliations (
    ReconciliationID BIGINT AUTO_INCREMENT PRIMARY KEY,
    ShiftID          INT NOT NULL,
    RevisionNo       INT NOT NULL,
    ExpectedCash     DECIMAL(18,0) NOT NULL DEFAULT 0,
    CountedCash      DECIMAL(18,0) NOT NULL DEFAULT 0,
    DifferenceAmount DECIMAL(18,0) NOT NULL DEFAULT 0,
    ClosingNote      VARCHAR(500) NULL,
    Status           VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                         CHECK (Status IN ('PENDING','APPROVED','REJECTED')),
    SubmittedBy      INT NOT NULL,
    SubmittedAt      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ReviewedBy       INT NULL,
    ReviewedAt       DATETIME NULL,
    ReviewNote       VARCHAR(500) NULL,

    CONSTRAINT FK_ShiftReconciliations_Shift
        FOREIGN KEY (ShiftID) REFERENCES Shifts(ShiftID),
    CONSTRAINT FK_ShiftReconciliations_SubmittedBy
        FOREIGN KEY (SubmittedBy) REFERENCES Users(UserID),
    CONSTRAINT FK_ShiftReconciliations_ReviewedBy
        FOREIGN KEY (ReviewedBy) REFERENCES Users(UserID),

    UNIQUE KEY UQ_ShiftReconciliations_Revision (ShiftID, RevisionNo),
    KEY IX_ShiftReconciliations_Status (Status, SubmittedAt),
    KEY IX_ShiftReconciliations_ShiftLatest (ShiftID, RevisionNo)
) ENGINE=InnoDB;

-- Backfill 1 revision cho cac ca cu. Du lieu cu co the dung
-- PENDING_APPROVAL / APPROVED / REJECTED ngay trong Shifts.Status.
INSERT IGNORE INTO ShiftReconciliations (
    ShiftID, RevisionNo, ExpectedCash, CountedCash, DifferenceAmount,
    ClosingNote, Status, SubmittedBy, SubmittedAt,
    ReviewedBy, ReviewedAt, ReviewNote
)
SELECT
    s.ShiftID,
    1,
    COALESCE(s.ExpectedCash, s.OpeningCash, 0),
    COALESCE(s.CountedCash, s.ExpectedCash, s.OpeningCash, 0),
    COALESCE(
        s.CashDifference,
        COALESCE(s.CountedCash, s.ExpectedCash, s.OpeningCash, 0)
        - COALESCE(s.ExpectedCash, s.OpeningCash, 0)
    ),
    s.ClosingNote,
    CASE
        WHEN s.Status = 'PENDING_APPROVAL' THEN 'PENDING'
        WHEN s.Status = 'REJECTED' THEN 'REJECTED'
        ELSE 'APPROVED'
    END,
    COALESCE(s.ClosedBy, s.UserID),
    COALESCE(s.EndTime, s.LastUpdatedAt, s.StartTime),
    CASE WHEN s.Status IN ('APPROVED','REJECTED') THEN s.ApprovedBy ELSE NULL END,
    CASE WHEN s.Status IN ('APPROVED','REJECTED') THEN s.ApprovedAt ELSE NULL END,
    CASE WHEN s.Status IN ('APPROVED','REJECTED') THEN s.ApprovalNote ELSE NULL END
FROM Shifts s
WHERE s.Status <> 'OPEN';

-- Tu migration 12 tro di, vong doi ca chi con OPEN/CLOSED.
-- Doi soat nam trong ShiftReconciliations.
UPDATE Shifts
SET Status = 'CLOSED'
WHERE Status IN ('PENDING_APPROVAL','APPROVED','REJECTED');

-- P1: tach quyen xem tat ca ca va quyen duyet.
INSERT IGNORE INTO Permissions (PermissionCode, Description)
VALUES ('SHIFT_APPROVE', 'Duyệt / từ chối đối soát ca bán hàng');

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r
CROSS JOIN Permissions p
WHERE r.RoleCode IN ('ADMIN','SALES_MANAGER')
  AND p.PermissionCode = 'SHIFT_APPROVE';

-- ============================================================
-- QUICK CHECK
-- ============================================================
-- SELECT Status, COUNT(*) FROM Shifts GROUP BY Status;
-- SELECT Status, COUNT(*) FROM ShiftReconciliations GROUP BY Status;
-- SELECT ShiftID,RevisionNo,ExpectedCash,CountedCash,DifferenceAmount,Status,
--        SubmittedBy,SubmittedAt,ReviewedBy,ReviewedAt,ReviewNote
-- FROM ShiftReconciliations ORDER BY ShiftID DESC,RevisionNo DESC LIMIT 30;
