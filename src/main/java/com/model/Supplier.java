package com.model;

import java.math.BigDecimal;

public class Supplier {

    private int supplierId;
    private String supplierName;
    private String address;
    private String phone;
    private String email;
    private String suppliedItems;
    private int productCount;
    /** So tien NCC dang no lai cua hang, phat sinh tu cac phieu tra hang lo (SupplierReturns). */
    private BigDecimal debtBalance;

    public int getSupplierId() { return supplierId; }
    public void setSupplierId(int supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getSuppliedItems() { return suppliedItems; }
    public void setSuppliedItems(String suppliedItems) { this.suppliedItems = suppliedItems; }

    public int getProductCount() { return productCount; }
    public void setProductCount(int productCount) { this.productCount = productCount; }

    public BigDecimal getDebtBalance() { return debtBalance; }
    public void setDebtBalance(BigDecimal debtBalance) { this.debtBalance = debtBalance; }
}