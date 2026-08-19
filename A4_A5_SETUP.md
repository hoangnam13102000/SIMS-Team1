# SIMS A4-A5 - Gán đơn online + lịch sử trạng thái

## 1. Copy patch vào project
Copy toàn bộ `src` và `sql` trong patch vào root project `SIMS-Team1`, cho phép ghi đè file trùng tên.

## 2. Chạy migration
Trong phpMyAdmin, chọn database `SIMS_DB`, chạy:

`sql/mysql/09_ORDER_ASSIGNMENT_HISTORY.sql`

Kết quả quyền mong đợi:
- SALES_MANAGER: ORDER_VIEW, ORDER_MANAGE, ORDER_ASSIGN
- SALES_STAFF: ORDER_VIEW_ASSIGNED, ORDER_PROCESS_ASSIGNED
- SALES_STAFF không còn ORDER_VIEW / ORDER_MANAGE

## 3. Eclipse
- Ctrl+S
- Project -> Clean...
- Maven -> Update Project... nếu cần
- Tắt app hoàn toàn và chạy lại để đăng nhập nạp quyền mới.

## 4. Test A4
1. Login SALES_MANAGER.
2. Vào Quản lý đơn hàng online.
3. Mở đơn NEW hoặc CONFIRMED.
4. Bấm `Gán nhân viên` / `Đổi nhân viên`.
5. Chọn SALES_STAFF.
6. Logout, login nhân viên vừa được gán.
7. Nhân viên chỉ được thấy các đơn AssignedTo = UserID của mình.
8. Nhân viên được xác nhận/chuyển trạng thái/hủy đơn được giao; không xử lý được đơn người khác.

## 5. Test A5
1. Với đơn đã gán, chuyển NEW -> CONFIRMED -> SHIPPING -> COMPLETED.
2. Mở chi tiết đơn, phần `Lịch sử trạng thái` phải có các mốc.
3. Kiểm tra SQL:

```sql
SELECT h.HistoryID, o.OrderCode, h.FromStatus, h.ToStatus,
       u.FullName AS ChangedByName, h.ChangedAt, h.Note, h.ViaAssistant
FROM OrderStatusHistory h
JOIN Orders o ON o.OrderID = h.OrderID
LEFT JOIN Users u ON u.UserID = h.ChangedBy
WHERE o.OrderCode = 'DH0001'
ORDER BY h.ChangedAt, h.HistoryID;
```

## 6. Kiểm tra scope
```sql
SELECT o.OrderCode, o.OrderStatus, o.AssignedTo, u.FullName AS AssignedToName,
       o.AssignedAt, o.AssignedBy
FROM Orders o
LEFT JOIN Users u ON u.UserID = o.AssignedTo
ORDER BY o.CreatedAt DESC;
```
