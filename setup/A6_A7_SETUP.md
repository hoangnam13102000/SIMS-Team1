# SIMS A6-A7 - Tạm giữ nhiều giỏ hàng tại POS

## Mục tiêu
- A6: Nhân viên đang có ca OPEN có thể tạm giữ giỏ hiện tại, nhận mã `HG-yyyyMMdd-xxxxxx`, sau đó POS được làm trống để phục vụ khách khác.
- A7: Nhân viên tìm giỏ theo mã phiếu / khách hàng / ghi chú và khôi phục lại trong đúng ca của chính mình.
- Giỏ tạm giữ không giữ chỗ tồn kho. Khi khôi phục, hệ thống đọc lại trạng thái sản phẩm, tồn kho, giá, khách hàng, khuyến mãi và điểm hiện tại.
- Phương thức thanh toán đang chọn cũng được snapshot; sau khi tạm giữ POS trở về Tiền mặt, khi khôi phục sẽ chọn lại phương thức cũ.
- Khi đóng ca, các phiếu còn `HELD` tự chuyển `EXPIRED`.

## Chạy migration
Chạy `sql/mysql/10_HELD_CARTS.sql` trên `SIMS_DB` trước khi chạy app.

## Test A6
1. Login SALES_STAFF, mở ca.
2. Thêm 2 sản phẩm, chọn khách hàng (tuỳ chọn), áp mã KM/điểm (tuỳ chọn).
3. Bấm `Tạm giữ`, nhập ghi chú, xác nhận.
4. POS phải trống; DB có `HeldCarts.Status='HELD'` và các dòng `HeldCartItems`.

## Test A7
1. Tạo thêm một giỏ khác hoặc để POS trống.
2. Bấm `Giỏ đã giữ`, tìm theo mã/khách hàng.
3. Chọn phiếu -> `Khôi phục giỏ`.
4. Sản phẩm hợp lệ được khôi phục; phiếu chuyển `RESTORED`.
5. Nếu giá/tồn/khuyến mãi/điểm thay đổi, app cảnh báo và dùng dữ liệu hiện tại.
6. Test `Hủy phiếu`: phiếu chuyển `CANCELLED` và không còn trong danh sách.
7. Tạo một phiếu HELD rồi đóng ca: phiếu phải chuyển `EXPIRED`.

## SQL kiểm tra
```sql
SELECT HoldID, HoldCode, ShiftID, HeldBy, CustomerLabelSnapshot,
       Status, HeldAt, RestoredAt, CancelledAt, ExpiredAt, Note
FROM HeldCarts
ORDER BY HoldID DESC;

SELECT h.HoldCode, i.ProductCodeSnapshot, i.ProductNameSnapshot,
       i.Quantity, i.UnitPriceSnapshot
FROM HeldCartItems i
JOIN HeldCarts h ON h.HoldID=i.HoldID
ORDER BY i.HoldItemID;
```
