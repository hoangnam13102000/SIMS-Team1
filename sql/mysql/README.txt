SIMS MySQL - Goi import
========================

Thu tu chay (phpMyAdmin Import hoac mysql CLI):

  1. 01_SIMS_Schema_MySQL.sql     -> tao DB + bang
  2. 03_SIMS_SampleData_MySQL.sql -> du lieu mau day du (tuy chon)
  3. 02_SIMS_Triggers_MySQL.sql   -> ham/trigger nghiep vu + gan ma con thieu

Neu KHONG can du lieu mau:
  1. 01_SIMS_Schema_MySQL.sql
  2. 00_RBAC_Admin_MySQL.sql
  3. 02_SIMS_Triggers_MySQL.sql

CLI:
  mysql -u root -p < 01_SIMS_Schema_MySQL.sql
  mysql -u root -p < 03_SIMS_SampleData_MySQL.sql
  mysql -u root -p < 02_SIMS_Triggers_MySQL.sql

Tai khoan demo (password: 123456):
  admin, salesmgr, invmgr, staff01, staff02
  lan.nguyen, hung.tran, mai.pham, duc.le, customer1

Luu y:
  - File 03 se TRUNCATE toan bo bang roi insert lai: CHI dung cho demo/test.
  - Luon chay file 02 SAU file 03 de tranh trigger nhap kho xu ly trung
    voi du lieu lo hang ma file seed da tao san.
  - File 02 da gom FEFO, tru/hoan dung lo, nhap kho, huy hoa don,
    doi/tra, doi chieu kho, canh bao ton va dong bo gia tu dong.
  - phpMyAdmin la giao dien web, khong phai JDBC URL. App Java can
    MySQL host/port/user/password rieng (thuong port 3306).

Nang cap project dang chay (A1-A3 huy hoa don co phe duyet):
  - Chay 08_INVOICE_CANCEL_APPROVAL.sql SAU khi DB hien tai da ton tai.
  - Dang xuat/dang nhap lai de phien SALES_STAFF nap quyen moi.

09_ORDER_ASSIGNMENT_HISTORY.sql
- Gán đơn online cho SALES_STAFF + phân quyền theo AssignedTo.
- Lưu lịch sử chuyển trạng thái OrderStatusHistory.

10_HELD_CARTS.sql
- A6-A7 POS: tạm giữ nhiều giỏ hàng, tìm/khôi phục/hủy theo ca và nhân viên.
- Tạo HeldCarts + HeldCartItems; thêm quyền POS_CART_HOLD/POS_CART_RESTORE.
- Không dùng information_schema, phù hợp tài khoản MySQL bị giới hạn.


11_INVOICE_PAYMENTS_RETURN_EVIDENCE.sql
- A10-A12: QR hóa đơn, nhiều dòng InvoicePayments, tiền khách đưa/tiền thừa và thanh toán kết hợp CASH + CARD.
- Tạo ReturnExchangeEvidence để lưu URL ảnh bằng chứng đổi/trả trên Cloudinary.
- Backfill hóa đơn cũ sang InvoicePayments; không dùng information_schema.


13_ONLINE_BANK_TRANSFER_PAYOS.sql
- Online checkout: them phuong thuc BANK_TRANSFER bang VietQR/payOS giong POS.
- Orders luu PayOsOrderCode, PayOsPaymentLinkID, BankTransferReference.
- Chi danh dau PAID va tao don/hoa don sau khi payOS xac nhan thanh toan.

14_RETURN_TO_ORIGINAL_BATCH.sql
- Sua logic RETURN/EXCHANGE: hang tra bat buoc cong lai dung lo goc da xuat cho hoa don/order.
- Khong con tao lo moi LotNumber=TRA-HANG-* khi tra hang.
- Neu hoa don cu thieu batch traceability, phe duyet bi chan/rollback thay vi tao lo gia.
- Sua tinh ReturnableQty chi tru cac batch mapping cua dong Direction=IN.

15_PREVIEW_LEGACY_TRA_HANG_BATCHES.sql
- Chi preview cac lo TRA-HANG-* da sinh tu logic cu va cac FK dang tham chieu.
- Khong sua/xoa du lieu. Dung de quyet dinh cach gop lo cu ve dung lo goc an toan.
