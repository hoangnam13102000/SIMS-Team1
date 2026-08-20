# P1-P7 - Ca bán hàng & đối soát quỹ sát nghiệp vụ thực tế

## Mục tiêu

Tách **vòng đời ca bán hàng** khỏi **vòng đời đối soát quỹ**:

- `Shifts.Status`: chỉ `OPEN` / `CLOSED`.
- `ShiftReconciliations.Status`: `PENDING` / `APPROVED` / `REJECTED`.
- Khi nhân viên đóng ca: ca đóng ngay (`CLOSED`) và tạo đối soát `PENDING`.
- Nếu quản lý yêu cầu kiểm lại: ca vẫn `CLOSED`; nhân viên đếm lại và gửi một revision mới.
- Mọi revision cũ được giữ lại để audit.

## P1-P7 đã triển khai

### P1 - Tách quyền xem và quyền duyệt

- `SHIFT_VIEW_ALL`: xem tất cả ca.
- `SHIFT_APPROVE`: duyệt / yêu cầu kiểm lại đối soát.
- `ShiftService.approveShift()` và `rejectShift()` bắt buộc `SHIFT_APPROVE`.
- `SALES_MANAGER` được cấp `SHIFT_APPROVE`.
- Trang "Ca của tôi & đối soát quỹ" chỉ dành cho role có `SHIFT_OPERATE`; quản lý dùng trang "Giám sát & duyệt ca".

### P2 - Tách trạng thái ca và trạng thái đối soát

Luồng mới:

```text
OPEN
  |
  | nhân viên đóng ca
  v
CLOSED + Reconciliation PENDING
```

Quản lý duyệt hoặc yêu cầu kiểm lại chỉ thay đổi `ShiftReconciliations.Status`, không mở lại ca.

### P3 - Lịch sử ShiftReconciliations

Bảng `ShiftReconciliations` lưu:

- RevisionNo
- ExpectedCash
- CountedCash
- DifferenceAmount
- ClosingNote
- Status
- SubmittedBy / SubmittedAt
- ReviewedBy / ReviewedAt
- ReviewNote

Chi tiết ca hiển thị toàn bộ lịch sử revision.

### P4 - REJECTED -> kiểm lại -> gửi lại

```text
CLOSED + PENDING revision 1
        |
        | quản lý yêu cầu kiểm lại
        v
CLOSED + REJECTED revision 1
        |
        | nhân viên đếm lại
        v
CLOSED + PENDING revision 2
        |
        | quản lý duyệt
        v
CLOSED + APPROVED revision 2
```

Revision 1 không bị sửa/xóa.

### P5 - Blind cash count

Khi nhân viên đóng ca:

1. Hệ thống **ẩn tiền quỹ dự kiến**.
2. Nhân viên phải nhập số tiền thực tế đã đếm.
3. Sau khi xác nhận số đếm, hệ thống mới hiện Expected / Counted / Difference.
4. Nếu có chênh lệch thì bắt buộc nhập giải trình.

### P6 - Quản lý ưu tiên ca rủi ro

Trang "Giám sát & duyệt ca":

- Có cột `Chênh lệch`.
- Ca `Cần kiểm lại` lên trên cùng.
- Tiếp theo là `Chờ duyệt`, ưu tiên ca có chênh lệch.
- Ca đang mở nằm sau nhóm cần xử lý.
- Ca đã duyệt không bị đẩy lên đầu chỉ vì lịch sử từng có chênh lệch.
- Stat card đầu tiên là `Cần xử lý`.

### P7 - Integration test workflow mới

`SalesStaffFullFlowIT` kiểm tra:

```text
OPEN
 -> CLOSED + PENDING revision 1
 -> REJECTED revision 1
 -> PENDING revision 2
 -> APPROVED revision 2
```

và xác nhận `Shifts.Status` vẫn là `CLOSED` trong toàn bộ giai đoạn sau khi nhân viên đóng ca.

---

## 1. Bắt buộc chạy migration 12 trước khi mở app

File:

```text
sql/mysql/12_SHIFT_RECONCILIATION_WORKFLOW.sql
```

Migration sẽ:

- tạo `ShiftReconciliations`;
- backfill các ca cũ;
- đổi trạng thái ca cũ `PENDING_APPROVAL/APPROVED/REJECTED` về `CLOSED`;
- cấp `SHIFT_APPROVE` cho `ADMIN` và `SALES_MANAGER`.

Migration không dùng `information_schema`.

### Kiểm tra sau migration

```sql
SHOW COLUMNS FROM ShiftReconciliations;

SELECT Status, COUNT(*) AS Total
FROM Shifts
GROUP BY Status;

SELECT Status, COUNT(*) AS Total
FROM ShiftReconciliations
GROUP BY Status;
```

Sau migration, `Shifts.Status` chỉ nên còn:

```text
OPEN
CLOSED
```

Kiểm tra quyền:

```sql
SELECT r.RoleCode, p.PermissionCode
FROM RolePermissions rp
JOIN Roles r ON r.RoleID = rp.RoleID
JOIN Permissions p ON p.PermissionID = rp.PermissionID
WHERE r.RoleCode IN ('ADMIN','SALES_MANAGER')
  AND p.PermissionCode IN ('SHIFT_VIEW_ALL','SHIFT_APPROVE')
ORDER BY r.RoleCode, p.PermissionCode;
```

---

## 2. Test thủ công P1-P6

### Test A - Blind close + PENDING

1. Login SALES_STAFF.
2. Mở ca với tiền đầu ca, ví dụ `100000`.
3. Bán/thu/chi tùy ý.
4. Vào `Ca của tôi & đối soát quỹ`.
5. Xác nhận khi ca OPEN, tiền hệ thống được ẩn với nhân viên.
6. Bấm `Đóng ca`.
7. Nhập tiền đếm thật, không được prefill ExpectedCash.
8. Xác nhận.

Kiểm tra DB:

```sql
SELECT ShiftID, UserID, Status, StartTime, EndTime,
       OpeningCash, ExpectedCash, CountedCash, CashDifference
FROM Shifts
ORDER BY ShiftID DESC
LIMIT 5;
```

Ca vừa đóng phải là `CLOSED`.

```sql
SELECT ReconciliationID, ShiftID, RevisionNo,
       ExpectedCash, CountedCash, DifferenceAmount,
       Status, SubmittedBy, SubmittedAt,
       ReviewedBy, ReviewedAt, ReviewNote
FROM ShiftReconciliations
WHERE ShiftID = <SHIFT_ID>
ORDER BY RevisionNo;
```

Phải có revision 1 `PENDING`.

### Test B - Quyền P1

- SALES_STAFF không được duyệt/yêu cầu kiểm lại ca.
- SALES_MANAGER thấy `Giám sát & duyệt ca` và có nút duyệt/yêu cầu kiểm lại.
- `SHIFT_VIEW_ALL` không thay thế cho `SHIFT_APPROVE`; service vẫn chặn nếu thiếu `SHIFT_APPROVE`.

### Test C - Manager yêu cầu kiểm lại

1. Login SALES_MANAGER.
2. Vào `Giám sát & duyệt ca`.
3. Chọn ca PENDING.
4. Bấm `Yêu cầu kiểm lại` và nhập lý do.

DB:

```sql
SELECT ShiftID, Status
FROM Shifts
WHERE ShiftID = <SHIFT_ID>;
```

Vẫn phải là `CLOSED`.

Latest reconciliation phải là `REJECTED`:

```sql
SELECT *
FROM ShiftReconciliations
WHERE ShiftID = <SHIFT_ID>
ORDER BY RevisionNo DESC
LIMIT 1;
```

### Test D - Nhân viên gửi lại revision 2

1. Login lại SALES_STAFF.
2. Lịch sử ca phải hiện `Cần kiểm lại`.
3. Bấm icon gửi lại/kiểm đếm lại.
4. Nhập tiền đếm mới + giải trình.
5. Gửi lại.

Kiểm tra:

```sql
SELECT RevisionNo, ExpectedCash, CountedCash,
       DifferenceAmount, Status, ClosingNote,
       ReviewNote, SubmittedAt, ReviewedAt
FROM ShiftReconciliations
WHERE ShiftID = <SHIFT_ID>
ORDER BY RevisionNo;
```

Kỳ vọng:

```text
Revision 1 = REJECTED
Revision 2 = PENDING
```

### Test E - Quản lý duyệt revision 2

Manager duyệt ca. Sau đó:

```sql
SELECT ShiftID, Status
FROM Shifts
WHERE ShiftID = <SHIFT_ID>;
```

vẫn `CLOSED`.

```sql
SELECT RevisionNo, Status, ReviewedBy, ReviewedAt, ReviewNote
FROM ShiftReconciliations
WHERE ShiftID = <SHIFT_ID>
ORDER BY RevisionNo DESC
LIMIT 1;
```

phải là `APPROVED`.

### Test F - Risk sorting P6

Tạo/giữ các ca có trạng thái khác nhau rồi mở `Giám sát & duyệt ca`:

1. `Cần kiểm lại` có chênh lệch.
2. `Chờ duyệt` có chênh lệch.
3. `Chờ duyệt` không chênh lệch.
4. `Đang mở`.
5. `Đã duyệt`.

Các ca cần xử lý phải được ưu tiên ở phía trên. `Cần xử lý` chỉ đếm PENDING/REJECTED.

---

## 3. Chạy P7 bằng JUnit

Dùng configuration A13 hiện tại:

```text
SIMS_IT_RUN=true
SIMS_IT_ALLOW_SHARED_DB=true
```

Tốt nhất vẫn dùng DB TEST/IT riêng.

Kết quả mục tiêu:

```text
Runs:     8/8
Errors:   0
Failures: 0
```

Test cuối phải xác nhận đầy đủ revision workflow mới.

---

## 4. Lưu ý tương thích

Các cột `ExpectedCash`, `CountedCash`, `CashDifference`, `ClosingNote`, `ApprovedBy`, `ApprovedAt`, `ApprovalNote` trong `Shifts` được giữ làm snapshot tương thích với code/report cũ. **Nguồn truth của workflow duyệt mới là `ShiftReconciliations`.**

Các constant legacy `PENDING_APPROVAL/APPROVED/REJECTED` trong `Shift` vẫn được giữ tạm để đọc dữ liệu cũ trước migration, nhưng code mới không ghi các trạng thái đó vào `Shifts.Status` nữa.
