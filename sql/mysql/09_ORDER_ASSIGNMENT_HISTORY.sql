/* ============================================================
   SIMS A4-A5 - ORDER ASSIGNMENT + STATUS HISTORY
   MySQL 8.x / MariaDB compatible style
   ============================================================ */
USE SIMS_DB;

/* 1) Permissions */
INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('ORDER_VIEW_ASSIGNED',    'Xem các đơn online được gán cho chính nhân viên'),
('ORDER_PROCESS_ASSIGNED', 'Xử lý trạng thái các đơn online được gán cho chính nhân viên'),
('ORDER_ASSIGN',           'Gán/đổi nhân viên phụ trách đơn online');

/* Manager/Admin có quyền gán. */
INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode IN ('ADMIN','SALES_MANAGER')
  AND p.PermissionCode = 'ORDER_ASSIGN';

/* SALES_STAFF: cấp quyền hẹp trước. */
INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode IN ('ORDER_VIEW_ASSIGNED','ORDER_PROCESS_ASSIGNED');

/* Sau khi đã có quyền thay thế, bỏ quyền rộng cũ khỏi SALES_STAFF. */
DELETE rp
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode IN ('ORDER_VIEW','ORDER_MANAGE');

/* 2) Orders.Assigned* - idempotent */
SET @db := DATABASE();

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=@db AND TABLE_NAME='Orders' AND COLUMN_NAME='AssignedTo') = 0,
    'ALTER TABLE Orders ADD COLUMN AssignedTo INT NULL AFTER InvoiceID',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=@db AND TABLE_NAME='Orders' AND COLUMN_NAME='AssignedAt') = 0,
    'ALTER TABLE Orders ADD COLUMN AssignedAt DATETIME NULL AFTER AssignedTo',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.COLUMNS
     WHERE TABLE_SCHEMA=@db AND TABLE_NAME='Orders' AND COLUMN_NAME='AssignedBy') = 0,
    'ALTER TABLE Orders ADD COLUMN AssignedBy INT NULL AFTER AssignedAt',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='Orders'
       AND CONSTRAINT_NAME='FK_Orders_AssignedTo') = 0,
    'ALTER TABLE Orders ADD CONSTRAINT FK_Orders_AssignedTo FOREIGN KEY (AssignedTo) REFERENCES Users(UserID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
     WHERE CONSTRAINT_SCHEMA=@db AND TABLE_NAME='Orders'
       AND CONSTRAINT_NAME='FK_Orders_AssignedBy') = 0,
    'ALTER TABLE Orders ADD CONSTRAINT FK_Orders_AssignedBy FOREIGN KEY (AssignedBy) REFERENCES Users(UserID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=@db AND TABLE_NAME='Orders'
       AND INDEX_NAME='IX_Orders_AssignedTo_Status') = 0,
    'CREATE INDEX IX_Orders_AssignedTo_Status ON Orders(AssignedTo, OrderStatus, CreatedAt)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

/* 3) History */
CREATE TABLE IF NOT EXISTS OrderStatusHistory (
    HistoryID       BIGINT AUTO_INCREMENT PRIMARY KEY,
    OrderID         INT NOT NULL,
    FromStatus      VARCHAR(20) NULL,
    ToStatus        VARCHAR(20) NOT NULL,
    ChangedBy       INT NULL,
    ChangedAt       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    Note            VARCHAR(500) NULL,
    ViaAssistant    TINYINT(1) NOT NULL DEFAULT 0,
    CONSTRAINT FK_OrderStatusHistory_Order
        FOREIGN KEY (OrderID) REFERENCES Orders(OrderID) ON DELETE CASCADE,
    CONSTRAINT FK_OrderStatusHistory_User
        FOREIGN KEY (ChangedBy) REFERENCES Users(UserID),
    CONSTRAINT CK_OrderStatusHistory_ViaAssistant CHECK (ViaAssistant IN (0,1))
) ENGINE=InnoDB;

SET @sql := IF(
    (SELECT COUNT(*) FROM information_schema.STATISTICS
     WHERE TABLE_SCHEMA=@db AND TABLE_NAME='OrderStatusHistory'
       AND INDEX_NAME='IX_OrderStatusHistory_Order_ChangedAt') = 0,
    'CREATE INDEX IX_OrderStatusHistory_Order_ChangedAt ON OrderStatusHistory(OrderID, ChangedAt, HistoryID)',
    'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

/* Seed 1 moc ban dau cho don cu de UI khong bi lich su trong. */
INSERT INTO OrderStatusHistory
    (OrderID, FromStatus, ToStatus, ChangedBy, ChangedAt, Note, ViaAssistant)
SELECT o.OrderID, NULL, o.OrderStatus, NULL, o.CreatedAt,
       'Khởi tạo lịch sử từ dữ liệu hiện có.', 0
FROM Orders o
WHERE NOT EXISTS (
    SELECT 1 FROM OrderStatusHistory h WHERE h.OrderID = o.OrderID
);

/* 4) Verification */
SELECT r.RoleCode, p.PermissionCode
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode IN ('SALES_STAFF','SALES_MANAGER')
  AND p.PermissionCode LIKE 'ORDER%'
ORDER BY r.RoleCode, p.PermissionCode;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'Orders'
  AND COLUMN_NAME IN ('AssignedTo','AssignedAt','AssignedBy')
ORDER BY ORDINAL_POSITION;
