# A13 FIX - Online InvoicePayments + Shift Approval + Safe Cleanup

## Sua 3 diem integration test vua phat hien

1. `OrderDAO#createInvoiceForOrder()` tao them `InvoicePayments` trong cung transaction.
   - COD/online completed -> CASH, Amount=TotalAmount, TenderedAmount=TotalAmount, ChangeAmount=0.
   - PayPal paid -> PAYPAL, luu PayPalCaptureID/PayPalOrderID, idempotency key.
2. `ShiftDAO#approveShift()` ghi `Status='CLOSED'` de tuong thich DB/du lieu lich su hien tai.
   `Shift.isApproved()` da coi `CLOSED` la da duyet.
3. A13 cleanup khong con co gang DELETE vinh vien hoa don / tat FK. Chi khoi phuc StoreConfig.

## Khong co SQL migration moi

Copy patch, Eclipse Project -> Clean, Maven -> Update Project, sau do chay lai SalesStaffFullFlowIT voi:

- `SIMS_IT_RUN=true`
- Neu dung SIMS_DB hien tai: `SIMS_IT_ALLOW_SHARED_DB=true`

Muc tieu: Runs 8/8, Errors 0, Failures 0.
