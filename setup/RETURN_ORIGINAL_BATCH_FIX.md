# RETURN -> original batch fix

## Muc tieu
Khi khach tra hang, so luong phai quay ve dung `InventoryBatch` da xuat luc ban. Khong tao `TRA-HANG-*` batch moi.

## File thay doi
- `sql/mysql/02_SIMS_Triggers_MySQL.sql`
- `sql/mysql/14_RETURN_TO_ORIGINAL_BATCH.sql`
- `sql/mysql/README.txt`

## Cap nhat DB dang chay
Chay `sql/mysql/14_RETURN_TO_ORIGINAL_BATCH.sql` trong phpMyAdmin. Khong chay lai file seed 03 tren DB dang dung.

## Test
1. Chon hoa don co `InvoiceDetailBatches`/`OrderDetailBatches`.
2. Ghi lai BatchID/BatchCode va RemainingQty truoc khi tra.
3. Tao RETURN 1 san pham va duyet.
4. Xac nhan `ReturnExchangeDetailBatches.BatchID` trung batch goc.
5. Xac nhan RemainingQty cua batch goc tang 1.
6. Xac nhan khong co `InventoryBatch.LotNumber LIKE 'TRA-HANG-%'` moi.

### SQL kiem tra batch goc cua mot ReturnID
```sql
SET @ReturnID = 1;
SELECT r.ReturnID, r.InvoiceID, rd.ReturnDetailID, rd.ProductID,
       reb.BatchID, b.BatchCode, b.LotNumber, reb.Quantity, b.RemainingQty
FROM ReturnExchanges r
JOIN ReturnExchangeDetails rd ON rd.ReturnID=r.ReturnID AND rd.Direction='IN'
JOIN ReturnExchangeDetailBatches reb ON reb.ReturnDetailID=rd.ReturnDetailID
JOIN InventoryBatch b ON b.BatchID=reb.BatchID
WHERE r.ReturnID=@ReturnID;
```

### SQL kiem tra khong sinh lo tra moi
```sql
SELECT BatchID, BatchCode, LotNumber, ProductID, Quantity, RemainingQty
FROM InventoryBatch
WHERE LotNumber LIKE 'TRA-HANG-%'
ORDER BY BatchID;
```

Luu y: cac `TRA-HANG-*` da ton tai tu truoc migration khong tu dong xoa vi co the da duoc tham chieu boi giao dich khac. Hay preview truoc khi sua du lieu lich su.
