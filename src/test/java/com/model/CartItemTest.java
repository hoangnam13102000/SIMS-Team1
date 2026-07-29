package com.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CartItemTest {

    private Product productGia(long price) {
        Product product = new Product();
        product.setSellPrice(BigDecimal.valueOf(price));
        return product;
    }

    @Test
    void getSubtotal_bangGiaNhanSoLuong() {
        CartItem item = new CartItem(productGia(15_000_000), 3);
        assertEquals(45_000_000L, item.getSubtotal());
    }

    @Test
    void setQuantity_capNhatDungGiaTri() {
        CartItem item = new CartItem(productGia(1_000_000), 1);
        item.setQuantity(10);
        assertEquals(10, item.getQuantity());
        assertEquals(10_000_000L, item.getSubtotal());
    }

    @Test
    void setProduct_thayDoiSanPhamThamChieu() {
        CartItem item = new CartItem(productGia(1_000_000), 2);
        item.setProduct(productGia(500_000));
        assertEquals(1_000_000L, item.getSubtotal());
    }
}