/* ============================================================
   SIMS A6-A7 - POS HELD CARTS
   Tạm giữ nhiều giỏ hàng + tìm/khôi phục/hủy trong cùng ca.
   Không dùng information_schema (phù hợp tài khoản DB bị giới hạn).
   ============================================================ */

USE SIMS_DB;

/* 1) Quyền */
INSERT IGNORE INTO Permissions (PermissionCode, Description) VALUES
('POS_CART_HOLD',    'Tạm giữ giỏ hàng tại POS'),
('POS_CART_RESTORE', 'Tìm, khôi phục hoặc hủy giỏ hàng tạm giữ tại POS');

INSERT IGNORE INTO RolePermissions (RoleID, PermissionID)
SELECT r.RoleID, p.PermissionID
FROM Roles r CROSS JOIN Permissions p
WHERE r.RoleCode IN ('ADMIN','SALES_STAFF')
  AND p.PermissionCode IN ('POS_CART_HOLD','POS_CART_RESTORE');

/* 2) Phiếu tạm giữ */
CREATE TABLE IF NOT EXISTS HeldCarts (
    HoldID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    HoldCode                VARCHAR(30) NULL UNIQUE,
    ShiftID                 INT NOT NULL,
    HeldBy                  INT NOT NULL,
    CustomerID              INT NULL,
    CustomerLabelSnapshot   VARCHAR(255) NULL,
    PromotionID             INT NULL,
    PromotionCode           VARCHAR(30) NULL,
    PaymentMethodSnapshot   VARCHAR(20) NOT NULL DEFAULT 'CASH'
                                CHECK (PaymentMethodSnapshot IN ('CASH','BANK_TRANSFER','PAYPAL','CARD')),
    PointsToUse             INT NOT NULL DEFAULT 0,
    SubTotalSnapshot        DECIMAL(18,0) NOT NULL DEFAULT 0,
    DiscountSnapshot        DECIMAL(18,0) NOT NULL DEFAULT 0,
    PointsDiscountSnapshot  DECIMAL(18,0) NOT NULL DEFAULT 0,
    Status                  VARCHAR(20) NOT NULL DEFAULT 'HELD'
                                CHECK (Status IN ('HELD','RESTORED','CANCELLED','EXPIRED')),
    Note                    VARCHAR(500) NULL,
    HeldAt                  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    RestoredAt              DATETIME NULL,
    CancelledAt             DATETIME NULL,
    ExpiredAt               DATETIME NULL,

    CONSTRAINT FK_HeldCarts_Shift
        FOREIGN KEY (ShiftID) REFERENCES Shifts(ShiftID),
    CONSTRAINT FK_HeldCarts_HeldBy
        FOREIGN KEY (HeldBy) REFERENCES Users(UserID),
    CONSTRAINT FK_HeldCarts_Customer
        FOREIGN KEY (CustomerID) REFERENCES Customers(CustomerID),
    CONSTRAINT FK_HeldCarts_Promotion
        FOREIGN KEY (PromotionID) REFERENCES Promotions(PromotionID),

    KEY IX_HeldCarts_User_Shift_Status (HeldBy, ShiftID, Status, HeldAt),
    KEY IX_HeldCarts_Code (HoldCode)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS HeldCartItems (
    HoldItemID           BIGINT AUTO_INCREMENT PRIMARY KEY,
    HoldID               BIGINT NOT NULL,
    ProductID            INT NOT NULL,
    ProductCodeSnapshot  VARCHAR(20) NULL,
    ProductNameSnapshot  VARCHAR(150) NOT NULL,
    Quantity             INT NOT NULL CHECK (Quantity > 0),
    UnitPriceSnapshot    DECIMAL(18,0) NOT NULL CHECK (UnitPriceSnapshot >= 0),

    CONSTRAINT FK_HeldCartItems_HeldCart
        FOREIGN KEY (HoldID) REFERENCES HeldCarts(HoldID) ON DELETE CASCADE,
    CONSTRAINT FK_HeldCartItems_Product
        FOREIGN KEY (ProductID) REFERENCES Products(ProductID),
    UNIQUE KEY UQ_HeldCartItems_Hold_Product (HoldID, ProductID),
    KEY IX_HeldCartItems_Product (ProductID)
) ENGINE=InnoDB;

/* 3) Kiểm tra nhanh */
SHOW COLUMNS FROM HeldCarts;
SHOW COLUMNS FROM HeldCartItems;

SELECT r.RoleCode, p.PermissionCode
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode = 'SALES_STAFF'
  AND p.PermissionCode IN ('POS_CART_HOLD','POS_CART_RESTORE')
ORDER BY p.PermissionCode;
