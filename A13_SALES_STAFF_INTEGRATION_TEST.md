# A13 - Integration test toàn luồng SALES_STAFF

## Mục tiêu

Chạy một chuỗi JUnit 5 trên MySQL thật để kiểm tra các luồng đã hoàn thiện:

1. Mở ca + thu/chi.
2. POS split payment CASH + CARD + InvoicePayments.
3. Dashboard cá nhân lọc đúng ShiftID.
4. Tạm giữ -> khôi phục -> hủy giỏ.
5. Nhân viên gửi yêu cầu hủy hóa đơn -> quản lý duyệt.
6. Đổi/trả dưới 500.000đ tự APPROVED.
7. Quản lý gán đơn online -> SALES_STAFF xác nhận -> giao -> hoàn thành -> OrderStatusHistory.
8. Đóng ca -> quản lý duyệt đối soát.

Suite còn kiểm tra invariant A12: hóa đơn được tạo từ đơn online cũng phải có InvoicePayments. Nếu test này FAIL với thông báo `Hoa don tu don online chua co InvoicePayments`, đây là lỗi thật cần sửa ở `OrderDAO#createInvoiceForOrder`, không phải lỗi test.

## An toàn dữ liệu

Khuyến nghị chạy trên database clone như `SIMS_DB_TEST` hoặc `SIMS_IT`.

Suite bị khóa mặc định. Phải set:

```text
SIMS_IT_RUN=true
```

Nếu cố chạy trên database không có chữ `test` / `it`, suite sẽ từ chối. Chỉ khi bạn chủ động chấp nhận chạy test trên DB hiện tại mới set thêm:

```text
SIMS_IT_ALLOW_SHARED_DB=true
```

Suite tạo user/product/order/invoice có prefix `IT_` và dọn dữ liệu sau khi chạy. Tuy vậy DB test riêng vẫn là lựa chọn an toàn nhất.

## Chạy trong Eclipse

1. Refresh project (F5).
2. `Project -> Clean`.
3. Mở `src/test/java/com/integration/SalesStaffFullFlowIT.java`.
4. `Run As -> JUnit Test`.
5. Nếu test bị skip/khóa, vào `Run Configurations -> JUnit -> Environment` và thêm `SIMS_IT_RUN=true`.
6. Nếu đang dùng DB clone, override `DB_URL` trong Environment sang DB clone. Có thể giữ DB_USER/DB_PASSWORD từ secure-config hiện tại nếu tài khoản đó có quyền trên DB clone.

Nếu buộc test trên `SIMS_DB`, thêm `SIMS_IT_ALLOW_SHARED_DB=true` nhưng chỉ nên làm khi không có thành viên khác đang thao tác dữ liệu cùng lúc.

## Kết quả đạt

JUnit phải xanh toàn bộ. Tên các test thể hiện luồng tương ứng. Nếu chỉ test `assignedOnlineOrder...` đỏ vì thiếu InvoicePayments thì sửa OrderDAO rồi chạy lại đến khi xanh.

## Ngoài phạm vi tự động

PayPal và VietQR/payOS là tích hợp external gateway. Không nên gọi capture/charge thật trong JUnit. Hai luồng này vẫn cần test manual sandbox như trước: approve/cancel, idempotency, callback/polling, chống đóng ca khi pending và đối soát sau lỗi mạng.


## Lưu ý cleanup sau bản fix

SIMS có chính sách không xóa vĩnh viễn hóa đơn và các khóa ngoại bảo vệ lịch sử giao dịch.
Vì vậy integration test **không tắt trigger/FK và không DELETE cưỡng bức Invoices**. Sau khi test, suite chỉ khôi phục `RETURN_APPROVAL_THRESHOLD`; các fixture có prefix `IT_` được giữ lại. Nên chạy A13 trên database clone có tên `TEST`/`IT` và reset database test từ snapshot khi cần làm sạch.

Bản fix A13 đồng thời kiểm tra hai invariant production:

- Hóa đơn sinh từ đơn online phải có `InvoicePayments`.
- Duyệt đối soát ca dùng trạng thái lịch sử `CLOSED`, mà `Shift.isApproved()` coi là đã duyệt, để tương thích database đang chạy.
