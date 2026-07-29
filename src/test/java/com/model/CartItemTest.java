package com.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CartItemTest {

    private Phone phoneGia(long price) {
        Phone p = new Phone();
        p.setPrice(price);
        return p;
    }

    @Test
    void getSubtotal_bangGiaNhanSoLuong() {
        CartItem item = new CartItem(phoneGia(15_000_000), 3);
        assertEquals(45_000_000L, item.getSubtotal());
    }

    @Test
    void setQuantity_capNhatDungGiaTri() {
        CartItem item = new CartItem(phoneGia(1_000_000), 1);
        item.setQuantity(10);
        assertEquals(10, item.getQuantity());
        assertEquals(10_000_000L, item.getSubtotal());
    }

    @Test
    void setPhone_thayDoiSanPhamThamChieu() {
        CartItem item = new CartItem(phoneGia(1_000_000), 2);
        item.setPhone(phoneGia(500_000));
        assertEquals(1_000_000L, item.getSubtotal());
    }
}