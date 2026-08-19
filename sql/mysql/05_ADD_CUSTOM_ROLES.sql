/* ============================================================
   SIMS - Hỗ trợ Admin tạo thêm Role (role động)
   Chạy trên DB đã có schema Roles / RolePermissions.

   - IsSystem = 1: role hệ thống (ADMIN, SALES_*, INVENTORY_*, CUSTOMER)
     → không cho xóa trên UI
   - IsSystem = 0: role do Admin tạo
   ============================================================ */

USE SIMS_DB;

-- Cột đánh dấu role hệ thống (idempotent)
SET @col_exists := (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'Roles'
      AND COLUMN_NAME = 'IsSystem'
);

SET @sql := IF(@col_exists = 0,
    'ALTER TABLE Roles ADD COLUMN IsSystem TINYINT(1) NOT NULL DEFAULT 0 AFTER Description',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Đánh dấu 5 role seed là hệ thống
UPDATE Roles
SET IsSystem = 1
WHERE RoleCode IN ('ADMIN', 'SALES_MANAGER', 'INVENTORY_MANAGER', 'SALES_STAFF', 'CUSTOMER');

-- Role mới tạo sau này mặc định IsSystem = 0 (DEFAULT đã set ở trên)
