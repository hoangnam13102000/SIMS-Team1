/* ============================================================
   PREVIEW ONLY - old TRA-HANG-* batches created by legacy logic.
   KHONG XOA / KHONG SUA DU LIEU.
   Run after 14_RETURN_TO_ORIGINAL_BATCH.sql.
   ============================================================ */
USE SIMS_DB;

SELECT
    b.BatchID,
    b.BatchCode,
    b.LotNumber,
    b.ProductID,
    p.ProductCode,
    p.ProductName,
    b.Quantity,
    b.RemainingQty,
    b.Status,
    CAST(SUBSTRING(b.LotNumber, 10) AS UNSIGNED) AS SuspectedReturnID,
    (SELECT COUNT(*) FROM ReturnExchangeDetailBatches reb WHERE reb.BatchID=b.BatchID) AS ReturnRefs,
    (SELECT COUNT(*) FROM InvoiceDetailBatches idb WHERE idb.BatchID=b.BatchID) AS InvoiceSaleRefs,
    (SELECT COUNT(*) FROM OrderDetailBatches odb WHERE odb.BatchID=b.BatchID) AS OrderSaleRefs,
    (SELECT COUNT(*) FROM StockReconciliation sr WHERE sr.BatchID=b.BatchID) AS ReconciliationRefs,
    (SELECT COUNT(*) FROM StockDisposalDetails sd WHERE sd.BatchID=b.BatchID) AS DisposalRefs,
    (SELECT COUNT(*) FROM SupplierReturnDetails srd WHERE srd.BatchID=b.BatchID) AS SupplierReturnRefs
FROM InventoryBatch b
JOIN Products p ON p.ProductID=b.ProductID
WHERE b.LotNumber LIKE 'TRA-HANG-%'
ORDER BY b.BatchID;

/* Original POS invoice batch candidates for each legacy return lot. */
SELECT
    legacy.BatchID AS LegacyBatchID,
    legacy.BatchCode AS LegacyBatchCode,
    legacy.LotNumber AS LegacyLotNumber,
    r.ReturnID,
    r.InvoiceID,
    rd.ReturnDetailID,
    rd.ProductID,
    p.ProductCode,
    p.ProductName,
    idb.BatchID AS OriginalBatchID,
    orig.BatchCode AS OriginalBatchCode,
    orig.LotNumber AS OriginalLotNumber,
    idb.Quantity AS SoldFromOriginalBatch,
    orig.Quantity AS OriginalBatchQuantity,
    orig.RemainingQty AS OriginalBatchRemaining,
    orig.Status AS OriginalBatchStatus
FROM InventoryBatch legacy
JOIN ReturnExchangeDetailBatches reb ON reb.BatchID=legacy.BatchID
JOIN ReturnExchangeDetails rd ON rd.ReturnDetailID=reb.ReturnDetailID AND rd.Direction='IN'
JOIN ReturnExchanges r ON r.ReturnID=rd.ReturnID
JOIN Products p ON p.ProductID=rd.ProductID
JOIN InvoiceDetails idt ON idt.InvoiceID=r.InvoiceID AND idt.ProductID=rd.ProductID
JOIN InvoiceDetailBatches idb ON idb.InvoiceDetailID=idt.InvoiceDetailID
JOIN InventoryBatch orig ON orig.BatchID=idb.BatchID
WHERE legacy.LotNumber LIKE 'TRA-HANG-%'
ORDER BY legacy.BatchID, COALESCE(orig.ExpiryDate, '9999-12-31'), orig.BatchID;

/* Online-order batch candidates when invoice-level mapping is absent. */
SELECT
    legacy.BatchID AS LegacyBatchID,
    legacy.BatchCode AS LegacyBatchCode,
    legacy.LotNumber AS LegacyLotNumber,
    r.ReturnID,
    r.InvoiceID,
    rd.ReturnDetailID,
    rd.ProductID,
    p.ProductCode,
    p.ProductName,
    odb.BatchID AS OriginalBatchID,
    orig.BatchCode AS OriginalBatchCode,
    orig.LotNumber AS OriginalLotNumber,
    odb.Quantity AS SoldFromOriginalBatch,
    orig.Quantity AS OriginalBatchQuantity,
    orig.RemainingQty AS OriginalBatchRemaining,
    orig.Status AS OriginalBatchStatus
FROM InventoryBatch legacy
JOIN ReturnExchangeDetailBatches reb ON reb.BatchID=legacy.BatchID
JOIN ReturnExchangeDetails rd ON rd.ReturnDetailID=reb.ReturnDetailID AND rd.Direction='IN'
JOIN ReturnExchanges r ON r.ReturnID=rd.ReturnID
JOIN Products p ON p.ProductID=rd.ProductID
JOIN Orders o ON o.InvoiceID=r.InvoiceID
JOIN OrderDetails od ON od.OrderID=o.OrderID AND od.ProductID=rd.ProductID
JOIN OrderDetailBatches odb ON odb.OrderDetailID=od.OrderDetailID
JOIN InventoryBatch orig ON orig.BatchID=odb.BatchID
WHERE legacy.LotNumber LIKE 'TRA-HANG-%'
  AND NOT EXISTS (
      SELECT 1
      FROM InvoiceDetails idt2
      JOIN InvoiceDetailBatches idb2 ON idb2.InvoiceDetailID=idt2.InvoiceDetailID
      WHERE idt2.InvoiceID=r.InvoiceID AND idt2.ProductID=rd.ProductID
  )
ORDER BY legacy.BatchID, COALESCE(orig.ExpiryDate, '9999-12-31'), orig.BatchID;
