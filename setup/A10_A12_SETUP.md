# A10-A12 - QR hóa đơn + bằng chứng đổi/trả + InvoicePayments

## BẮT BUỘC: chạy migration trước khi mở app
Chạy `sql/mysql/11_INVOICE_PAYMENTS_RETURN_EVIDENCE.sql` trên `SIMS_DB`.
Script không dùng `information_schema`, phù hợp tài khoản MySQL bị giới hạn quyền.

Sau migration phải có:
- `InvoicePayments`
- `ReturnExchangeEvidence`

> Java A10-A12 đọc `InvoicePayments` ngay ở màn hình hóa đơn/dashboard/quỹ. Vì vậy không chạy app bản patch trước khi migration 11 thành công.

## A10 - QR hóa đơn
- Dialog thanh toán thành công hiển thị QR hóa đơn.
- PDF hóa đơn in QR nội bộ dạng `SIMS|INVOICE|<InvoiceCode>|<OriginalTotal>|<CreatedAt>`.
- QR không chứa tên/email/số điện thoại khách.
- Trang `Quản lý hóa đơn` có nút `Quét QR HĐ`; camera đọc QR rồi tự tìm đúng mã hóa đơn.
- Nếu máy quét dạng keyboard wedge dán nguyên payload QR vào ô tìm kiếm, hệ thống cũng tự rút `HD-...` ra để tìm.

## A11 - Ảnh bằng chứng đổi/trả
- Khi tạo yêu cầu đổi/trả có thể chọn tối đa 3 ảnh JPG/JPEG/PNG/WEBP/GIF.
- Ảnh dùng cấu hình Cloudinary hiện có:
  - `CLOUDINARY_CLOUD_NAME`
  - `CLOUDINARY_UPLOAD_PRESET`
- URL ảnh lưu trong `ReturnExchangeEvidence`.
- Chi tiết phiếu đổi/trả hiển thị danh sách ảnh; bấm để mở ảnh bằng trình duyệt.
- Ảnh là tùy chọn. Nếu upload ảnh lỗi, phiếu đổi/trả vẫn được tạo và app báo rõ ảnh nào chưa được lưu.

## A12 - InvoicePayments / tiền khách đưa / tiền thừa / thanh toán kết hợp
- Mọi hóa đơn POS mới ghi `InvoicePayments` trong cùng transaction tạo hóa đơn.
- Migration backfill hóa đơn cũ thành 1 dòng payment tương ứng.
- CASH lưu:
  - `Amount`: số tiền thực dùng để thanh toán hóa đơn
  - `TenderedAmount`: tiền khách đưa
  - `ChangeAmount`: tiền thừa
- CARD yêu cầu và lưu mã giao dịch để đối soát.
- PAYPAL lưu capture/order id.
- VietQR/payOS lưu reference/paymentLink/orderCode.
- POS có lựa chọn `Kết hợp` an toàn `CASH + CARD`:
  - nhân viên nhập phần tiền mặt;
  - hệ thống tự tính phần còn lại qua thẻ;
  - tổng các dòng COMPLETED bắt buộc bằng tổng hóa đơn trước khi commit.
- Quỹ ca chỉ cộng phần `CASH Amount`, không cộng phần thẻ/chuyển khoản/PayPal.
- Báo cáo cơ cấu phương thức thanh toán đọc từ `InvoicePayments`.
- Chi tiết hóa đơn có tab `Thanh toán` để xem từng dòng payment.

### Phạm vi cố ý chưa mở rộng
`Kết hợp` hiện chỉ hỗ trợ **CASH + CARD**. PayPal và VietQR vẫn giữ luồng đơn phương thức đã được xác nhận/capture như trước để tránh tạo giao dịch điện tử một phần nhưng hóa đơn không thể hoàn tất.

## Test nhanh

### 1. Kiểm tra migration
```sql
SHOW COLUMNS FROM InvoicePayments;
SHOW COLUMNS FROM ReturnExchangeEvidence;
```

### 2. CASH
Bán hóa đơn 59.000đ, nhập khách đưa 100.000đ. Sau đó:
```sql
SELECT i.InvoiceCode, p.PaymentMethod, p.Amount,
       p.TenderedAmount, p.ChangeAmount, p.PaymentStatus
FROM InvoicePayments p
JOIN Invoices i ON i.InvoiceID=p.InvoiceID
ORDER BY p.PaymentID DESC
LIMIT 10;
```
Kỳ vọng dòng CASH: Amount=59000, TenderedAmount=100000, ChangeAmount=41000.

### 3. Kết hợp CASH + CARD
Ví dụ hóa đơn 100.000đ, phần CASH 40.000đ, phần CARD 60.000đ. Kỳ vọng có 2 dòng cùng InvoiceID và tổng Amount = 100.000.

Kiểm tra mọi hóa đơn có tổng payment khớp tổng gốc:
```sql
SELECT i.InvoiceID, i.InvoiceCode, i.OriginalTotalAmount,
       COALESCE(SUM(CASE WHEN p.PaymentStatus='COMPLETED' THEN p.Amount ELSE 0 END),0) AS Paid
FROM Invoices i
LEFT JOIN InvoicePayments p ON p.InvoiceID=i.InvoiceID
GROUP BY i.InvoiceID, i.InvoiceCode, i.OriginalTotalAmount
HAVING Paid <> i.OriginalTotalAmount;
```
Kỳ vọng: 0 dòng.

### 4. Ảnh đổi/trả
Tạo phiếu đổi/trả có 1 ảnh, sau đó:
```sql
SELECT e.EvidenceID, e.ReturnID, e.ImageUrl,
       e.OriginalFileName, u.FullName AS UploadedByName, e.UploadedAt
FROM ReturnExchangeEvidence e
JOIN Users u ON u.UserID=e.UploadedBy
ORDER BY e.EvidenceID DESC;
```

### 5. QR hóa đơn
- Thanh toán xong: phải thấy QR trong dialog thành công.
- Xuất PDF: phải thấy QR + bảng thanh toán.
- Vào `Quản lý hóa đơn` -> `Quét QR HĐ` -> quét QR trên PDF: phải lọc ra đúng hóa đơn.
