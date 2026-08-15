package com.model;

import java.math.BigDecimal;

/** Anh chup so lieu tien mat dung de xem truoc va dong ca. */
public class ShiftCashSummary {

    private final BigDecimal openingCash;
    private final BigDecimal cashSales;
    private final BigDecimal cashIn;
    private final BigDecimal cashOut;
    private final BigDecimal cashRefunds;
    private final int invoiceCount;

    public ShiftCashSummary(BigDecimal openingCash, BigDecimal cashSales,
                            BigDecimal cashIn, BigDecimal cashOut,
                            BigDecimal cashRefunds, int invoiceCount) {
        this.openingCash = zero(openingCash);
        this.cashSales = zero(cashSales);
        this.cashIn = zero(cashIn);
        this.cashOut = zero(cashOut);
        this.cashRefunds = zero(cashRefunds);
        this.invoiceCount = invoiceCount;
    }

    public BigDecimal getOpeningCash() { return openingCash; }
    public BigDecimal getCashSales() { return cashSales; }
    public BigDecimal getCashIn() { return cashIn; }
    public BigDecimal getCashOut() { return cashOut; }
    public BigDecimal getCashRefunds() { return cashRefunds; }
    public int getInvoiceCount() { return invoiceCount; }

    public BigDecimal getExpectedCash() {
        return openingCash.add(cashSales).add(cashIn).subtract(cashOut).subtract(cashRefunds);
    }

    public BigDecimal differenceFrom(BigDecimal countedCash) {
        return zero(countedCash).subtract(getExpectedCash());
    }

    private static BigDecimal zero(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
