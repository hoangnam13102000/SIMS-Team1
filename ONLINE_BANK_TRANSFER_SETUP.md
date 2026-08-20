# Online BANK_TRANSFER (VietQR/payOS)

## Mục tiêu

Thêm phương thức **Chuyển khoản ngân hàng** cho checkout khách hàng online, dùng lại
`VietQrPayOsService` + `QrCodeUtil` đang dùng ở POS.

Luồng:

`Giỏ hàng -> Chuyển khoản -> tạo VietQR -> payOS xác nhận PAID -> tạo Orders + Invoice + InvoicePayments`

Nếu khách hủy / QR hết hạn thì **giỏ hàng vẫn giữ nguyên** và không tạo đơn.

## 1. Bắt buộc chạy migration trước khi mở app

Chạy:

`sql/mysql/13_ONLINE_BANK_TRANSFER_PAYOS.sql`

Migration thêm vào `Orders`:

- `PaymentMethod = BANK_TRANSFER`
- `PayOsOrderCode`
- `PayOsPaymentLinkID`
- `BankTransferReference`

và unique keys để chống ghi nhận trùng.

> Lưu ý: DB hiện tại được tạo từ `01_SIMS_Schema_MySQL.sql` thường có CHECK
> `orders_chk_2` cho `Orders.PaymentMethod`. Nếu phpMyAdmin báo không tìm thấy
> `orders_chk_2`, chạy:
>
> `SHOW CREATE TABLE Orders;`
>
> tìm tên CHECK chứa `PaymentMethod IN ('COD','PAYPAL')`, rồi thay tên
> `orders_chk_2` trong migration bằng đúng tên đó và chạy lại từ đoạn `DROP CHECK`.

## 2. Cấu hình payOS

Không có secret mới. Online dùng chung cấu hình với POS:

- `PAYOS_CLIENT_ID`
- `PAYOS_API_KEY`
- `PAYOS_CHECKSUM_KEY`
- `PAYOS_RETURN_URL`
- `PAYOS_CANCEL_URL`
- `PAYOS_QR_EXPIRE_SECONDS`
- `PAYOS_POLL_INTERVAL_MS`

Nếu POS VietQR đang chạy được thì phần online dùng cùng cấu hình.

## 3. Test giao diện

1. Đăng nhập khách hàng.
2. Thêm sản phẩm -> Giỏ hàng -> Thanh toán ngay.
3. Dialog phải có 3 phương thức:
   - Thanh toán tiền mặt (COD)
   - Chuyển khoản ngân hàng
   - PayPal
4. Chọn `Chuyển khoản ngân hàng`.
5. Xác nhận.
6. Phải hiện dialog VietQR:
   - QR
   - đúng tổng tiền
   - nội dung chuyển khoản
   - nút `Mở trang thanh toán`
7. Chỉ sau khi payOS báo `PAID` mới báo đặt hàng thành công.

## 4. Kiểm tra DB sau khi PAID

```sql
SELECT
    OrderID,
    OrderCode,
    PaymentMethod,
    PaymentStatus,
    PayOsOrderCode,
    PayOsPaymentLinkID,
    BankTransferReference,
    OrderStatus,
    InvoiceID
FROM Orders
ORDER BY OrderID DESC
LIMIT 5;
```

Kỳ vọng đơn mới:

- `PaymentMethod = BANK_TRANSFER`
- `PaymentStatus = PAID`
- có `PayOsOrderCode`
- có `PayOsPaymentLinkID`
- `InvoiceID` có giá trị

Kiểm tra hóa đơn:

```sql
SELECT
    i.InvoiceID,
    i.InvoiceCode,
    i.PaymentMethod,
    i.PayOsOrderCode,
    i.PayOsPaymentLinkID,
    i.BankTransferReference,
    i.Status
FROM Invoices i
WHERE i.InvoiceID = (
    SELECT InvoiceID
    FROM Orders
    WHERE PaymentMethod = 'BANK_TRANSFER'
    ORDER BY OrderID DESC
    LIMIT 1
);
```

Kỳ vọng `PaymentMethod = BANK_TRANSFER`.

Kiểm tra payment ledger:

```sql
SELECT
    p.InvoiceID,
    p.PaymentMethod,
    p.Amount,
    p.Provider,
    p.ProviderTransactionID,
    p.ProviderPaymentID,
    p.IdempotencyKey,
    p.PaymentStatus
FROM InvoicePayments p
WHERE p.InvoiceID = (
    SELECT InvoiceID
    FROM Orders
    WHERE PaymentMethod = 'BANK_TRANSFER'
    ORDER BY OrderID DESC
    LIMIT 1
);
```

Kỳ vọng:

- `PaymentMethod = BANK_TRANSFER`
- `Provider = PAYOS`
- `PaymentStatus = COMPLETED`
- `IdempotencyKey = PAYOS:<orderCode>`

## 5. Test các tình huống an toàn

- Bấm Hủy trước khi chuyển tiền -> không tạo đơn, giỏ vẫn còn.
- QR hết hạn -> không tạo đơn, giỏ vẫn còn.
- payOS đã PAID nhưng DB lỗi -> app báo rõ **không chuyển lại tiền** và hiển thị mã payOS để đối soát.
- Lịch sử đơn hàng và trang quản lý đơn phải hiển thị `Chuyển khoản`.
- Chi tiết đơn hiển thị mã giao dịch VietQR.

## 6. Eclipse

Sau khi copy patch + chạy migration:

`Project -> Clean...`

sau đó:

`Right click project -> Maven -> Update Project... -> Force Update -> OK`

Rồi chạy app và test.
