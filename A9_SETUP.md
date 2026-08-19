# SIMS A9 - Hoàn thiện Dashboard cá nhân theo ca cho SALES_STAFF

## Mục tiêu
Bổ sung dashboard nhân viên bán hàng theo đúng ca đang mở:

- Tiền mặt dự kiến của ca.
- Số đơn online đang được giao cho chính nhân viên.
- Số yêu cầu hủy hóa đơn đang chờ duyệt/xử lý.
- Toàn bộ KPI bán hàng dùng đúng `UserID + ShiftID` hiện tại.
- Hóa đơn gần đây và đổi/trả chờ duyệt cũng lọc theo `ShiftID` đang mở.

## File thay đổi

- `src/main/java/com/dao/DashboardDAO.java`
- `src/main/java/com/view/admin/SalesStaffDashboardPanel.java`

A9 **không cần migration SQL mới**. Nó sử dụng các bảng/cột đã có từ A1-A7.

## KPI sau A9

Khi SALES_STAFF có ca OPEN:

- `Doanh thu ca của tôi`: `Invoices.CreatedBy = currentUserId`, `Invoices.ShiftID = currentShiftId`, `Status='ACTIVE'`.
- `Hóa đơn ca này`: cùng `UserID + ShiftID`.
- `SP bán trong ca`: InvoiceDetails của hóa đơn thuộc đúng `UserID + ShiftID`.
- `Hóa đơn hủy trong ca`: hóa đơn CANCELLED của đúng `UserID + ShiftID`.
- `Đổi/trả chờ duyệt`: ReturnExchanges do user tạo, invoice thuộc đúng ShiftID.
- `Yêu cầu hủy chờ duyệt`: InvoiceCancelRequests do user gửi, invoice thuộc đúng ShiftID, trạng thái PENDING/PROCESSING.
- `Tiền mặt dự kiến`: tiền đầu ca + CASH sales + thu - chi - hoàn CASH.
- `Đơn online được giao`: các Orders có `AssignedTo=currentUserId` và trạng thái NEW/CONFIRMED/SHIPPING. Chỉ số này không lọc ShiftID vì bảng Orders không có ShiftID.

Khi không có ca OPEN, các KPI theo ca về 0 và hiển thị `Chưa có ca OPEN`; riêng đơn online được giao vẫn hiển thị vì đây là hàng việc độc lập với ca.

## Copy vào project

Copy thư mục `src` của patch vào project hiện tại, giữ nguyên cấu trúc và cho phép ghi đè.

Project đích thường là:

`C:\Users\Vu\git\SIMS-Team1`

## Sau khi copy

Trong Eclipse:

1. F5 / Refresh project.
2. Project -> Clean.
3. Maven -> Update Project -> Force Update.
4. Kiểm tra không có Java compile error.

## Test A9

### Test 1 - Không có ca OPEN
Đăng nhập SALES_STAFF khi chưa mở ca:

- Trạng thái ca = Chưa mở ca.
- Doanh thu / Hóa đơn / SP / Tiền mặt dự kiến / Hủy / Đổi trả / Yêu cầu hủy = 0.
- Đơn online được giao vẫn phản ánh các đơn AssignedTo của user.

### Test 2 - Mở ca mới
Mở một ca mới, ghi nhớ ShiftID (ví dụ #41). Dashboard phải đổi sang:

- Trạng thái ca = Đang mở.
- Các card ghi rõ `Ca #41` hoặc số liệu của ca hiện tại.
- Tiền mặt dự kiến ban đầu = tiền đầu ca.

### Test 3 - Bán 1 hóa đơn trong ca
Tạo 1 hóa đơn tại POS trong ca đang mở rồi quay lại Tổng quan:

- Doanh thu tăng đúng TotalAmount.
- Hóa đơn ca này +1.
- SP bán trong ca tăng đúng Quantity.
- AOV được tính lại.
- Hóa đơn gần đây chỉ hiện hóa đơn của ShiftID hiện tại.

### Test 4 - Tiền mặt dự kiến
Nếu hóa đơn CASH, tiền dự kiến phải tăng theo tiền CASH thực thu.
Nếu Thu tiền / Chi tiền trong ca, card phải thay đổi tương ứng.

Công thức:

`Expected = OpeningCash + CashSales + CashIn - CashOut - CashRefunds`

### Test 5 - Đơn online được giao
Quản lý gán một đơn NEW/CONFIRMED/SHIPPING cho nhân viên đang đăng nhập.
Dashboard phải tăng card `Đơn online được giao`.
COMPLETED/CANCELLED không được tính.

### Test 6 - Yêu cầu hủy chờ duyệt
Trong đúng ca hiện tại, nhân viên gửi yêu cầu hủy hóa đơn.
Dashboard phải tăng `Yêu cầu hủy chờ duyệt` khi request ở PENDING/PROCESSING.
Sau APPROVED/REJECTED, số phải giảm.

## SQL đối chiếu nhanh

Thay `<USER_ID>` và `<SHIFT_ID>` bằng giá trị thật.

```sql
SELECT COUNT(*) AS InvoiceCount,
       COALESCE(SUM(TotalAmount),0) AS Revenue
FROM Invoices
WHERE CreatedBy = <USER_ID>
  AND ShiftID = <SHIFT_ID>
  AND Status = 'ACTIVE';
```

```sql
SELECT COUNT(*) AS AssignedOrders
FROM Orders
WHERE AssignedTo = <USER_ID>
  AND OrderStatus IN ('NEW','CONFIRMED','SHIPPING');
```

```sql
SELECT COUNT(*) AS PendingCancelRequests
FROM InvoiceCancelRequests r
JOIN Invoices i ON i.InvoiceID = r.InvoiceID
WHERE r.RequestedBy = <USER_ID>
  AND i.ShiftID = <SHIFT_ID>
  AND r.Status IN ('PENDING','PROCESSING');
```

## Kiểm tra compile đã thực hiện

- `DashboardDAO.java`: compile OK.
- `SalesStaffDashboardPanel.java`: compile OK với classpath project và stub Ikonli để kiểm tra cú pháp/liên kết các class nội bộ.
- Full Maven/Eclipse build vẫn cần chạy trên máy Windows của project.
