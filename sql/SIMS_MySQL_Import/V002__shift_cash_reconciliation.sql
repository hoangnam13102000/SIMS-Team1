/*
 * V002 - Ca ban hang va doi soat quy (MySQL/MariaDB)
 * Chay file nay MOT LAN tren database SIMS dang co du lieu.
 * File schema 01 da duoc cap nhat cho truong hop tao database moi.
 */

DELIMITER $$

DROP PROCEDURE IF EXISTS migrate_shift_cash_reconciliation$$
CREATE PROCEDURE migrate_shift_cash_reconciliation()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'OpeningCash') THEN
        ALTER TABLE Shifts ADD COLUMN OpeningCash DECIMAL(18,0) NOT NULL DEFAULT 0 AFTER Status;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'ExpectedCash') THEN
        ALTER TABLE Shifts ADD COLUMN ExpectedCash DECIMAL(18,0) NULL AFTER OpeningCash;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'CountedCash') THEN
        ALTER TABLE Shifts ADD COLUMN CountedCash DECIMAL(18,0) NULL AFTER ExpectedCash;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'CashDifference') THEN
        ALTER TABLE Shifts ADD COLUMN CashDifference DECIMAL(18,0) NULL AFTER CountedCash;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'OpeningNote') THEN
        ALTER TABLE Shifts ADD COLUMN OpeningNote VARCHAR(500) NULL AFTER CashDifference;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'ClosingNote') THEN
        ALTER TABLE Shifts ADD COLUMN ClosingNote VARCHAR(500) NULL AFTER OpeningNote;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'ClosedBy') THEN
        ALTER TABLE Shifts ADD COLUMN ClosedBy INT NULL AFTER ClosingNote;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'LastUpdatedAt') THEN
        ALTER TABLE Shifts ADD COLUMN LastUpdatedAt DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
            ON UPDATE CURRENT_TIMESTAMP AFTER ClosedBy;
    END IF;

    /* Du lieu cu co the co nhieu ca OPEN/cung user. Giu ca moi nhat, dong cac ca cu. */
    UPDATE Shifts s
    JOIN (
        SELECT UserID, MAX(ShiftID) AS KeepShiftID
        FROM Shifts
        WHERE Status = 'OPEN'
        GROUP BY UserID
        HAVING COUNT(*) > 1
    ) duplicated ON duplicated.UserID = s.UserID AND s.ShiftID <> duplicated.KeepShiftID
    SET s.Status = 'CLOSED',
        s.EndTime = COALESCE(s.EndTime, CURRENT_TIMESTAMP),
        s.ExpectedCash = COALESCE(s.ExpectedCash, s.OpeningCash),
        s.CountedCash = COALESCE(s.CountedCash, s.OpeningCash),
        s.CashDifference = COALESCE(s.CashDifference, 0),
        s.ClosingNote = COALESCE(s.ClosingNote, 'Tu dong dong khi migration V002: trung ca OPEN');

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts' AND COLUMN_NAME = 'OpenUserID') THEN
        ALTER TABLE Shifts ADD COLUMN OpenUserID INT
            GENERATED ALWAYS AS (CASE WHEN Status = 'OPEN' THEN UserID ELSE NULL END) STORED;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts'
                     AND INDEX_NAME = 'UQ_Shifts_OneOpenPerUser') THEN
        ALTER TABLE Shifts ADD UNIQUE INDEX UQ_Shifts_OneOpenPerUser (OpenUserID);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
                   WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'Shifts'
                     AND CONSTRAINT_NAME = 'FK_Shifts_ClosedBy') THEN
        ALTER TABLE Shifts ADD CONSTRAINT FK_Shifts_ClosedBy
            FOREIGN KEY (ClosedBy) REFERENCES Users(UserID);
    END IF;
END$$

CALL migrate_shift_cash_reconciliation()$$
DROP PROCEDURE migrate_shift_cash_reconciliation$$

DELIMITER ;

CREATE TABLE IF NOT EXISTS ShiftCashTransactions (
    CashTransactionID   BIGINT AUTO_INCREMENT PRIMARY KEY,
    TransactionCode     VARCHAR(40) NOT NULL UNIQUE,
    ShiftID             INT NOT NULL,
    TransactionType     VARCHAR(20) NOT NULL CHECK (TransactionType IN ('CASH_IN', 'CASH_OUT')),
    Amount              DECIMAL(18,0) NOT NULL CHECK (Amount > 0),
    Reason              VARCHAR(255) NOT NULL,
    CreatedBy           INT NOT NULL,
    CreatedAt           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Status              VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                            CHECK (Status IN ('ACTIVE', 'VOIDED')),
    CONSTRAINT FK_ShiftCashTransactions_Shifts
        FOREIGN KEY (ShiftID) REFERENCES Shifts(ShiftID),
    CONSTRAINT FK_ShiftCashTransactions_Users
        FOREIGN KEY (CreatedBy) REFERENCES Users(UserID),
    INDEX IX_ShiftCashTransactions_ShiftTime (ShiftID, CreatedAt)
) ENGINE=InnoDB;

INSERT INTO Permissions (PermissionCode, Description)
SELECT 'SHIFT_OPERATE', 'Mo ca, thu/chi va dong/doi soat ca cua chinh nhan vien'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'SHIFT_OPERATE');

INSERT INTO Permissions (PermissionCode, Description)
SELECT 'SHIFT_VIEW_ALL', 'Xem lich su ca va chenh lech quy cua tat ca nhan vien'
WHERE NOT EXISTS (SELECT 1 FROM Permissions WHERE PermissionCode = 'SHIFT_VIEW_ALL');

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r JOIN Permissions p ON p.PermissionCode = 'SHIFT_OPERATE'
WHERE r.RoleCode = 'SALES_STAFF'
  AND NOT EXISTS (
      SELECT 1 FROM RolePermissions rp
      WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
  );

INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r JOIN Permissions p ON p.PermissionCode = 'SHIFT_VIEW_ALL'
WHERE r.RoleCode = 'SALES_MANAGER'
  AND NOT EXISTS (
      SELECT 1 FROM RolePermissions rp
      WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
  );

/* Admin duoc gan moi permission theo dung cach cua sample data. */
INSERT INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r JOIN Permissions p ON p.PermissionCode IN ('SHIFT_OPERATE', 'SHIFT_VIEW_ALL')
WHERE r.RoleCode = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM RolePermissions rp
      WHERE rp.RoleID = r.RoleID AND rp.PermissionID = p.PermissionID
  );
