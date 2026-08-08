-- Them ly do tu choi rieng, khong ghi de ly do khach hang
IF COL_LENGTH('dbo.ReturnExchanges', 'RejectionReason') IS NULL
BEGIN
    ALTER TABLE dbo.ReturnExchanges
        ADD RejectionReason NVARCHAR(500) NULL;
END
GO
