package com.service;

import com.model.Phone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CartService la singleton toan JVM (gio hang dung chung 1 phien) nen
 * PHAI reset ve rong truoc/sau moi test.
 */
class CartServiceTest {

    private final CartService cart = CartService.getInstance();

    @BeforeEach
    void resetTruocMoiTest() { cart.clear(); }

    @AfterEach
    void resetSauMoiTest() { cart.clear(); }

    private Phone phone(int id, long price) {
        Phone p = new Phone();
        p.setPhoneId(id);
        p.setPhoneName("Phone " + id);
        p.setPrice(price);
        return p;
    }

    @Test
    void addToCart_sanPhamMoi_themVaoDanhSach() {
        cart.addToCart(phone(1, 10_000_000), 2);
        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getQuantity());
    }

    @Test
    void addToCart_sanPhamDaCoTrongGio_congDonSoLuong() {
        Phone p = phone(1, 10_000_000);
        cart.addToCart(p, 2);
        cart.addToCart(p, 3);

        assertEquals(1, cart.getItems().size(), "Khong duoc tao them dong moi cho cung 1 phone");
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void removeItem_xoaDungSanPhamTheoId() {
        cart.addToCart(phone(1, 1_000_000), 1);
        cart.addToCart(phone(2, 2_000_000), 1);

        cart.removeItem(1);

        assertEquals(1, cart.getItems().size());
        assertEquals(2, cart.getItems().get(0).getPhone().getPhoneId());
    }

    @Test
    void updateQuantity_soLuongDuong_capNhatBinhThuong() {
        cart.addToCart(phone(1, 1_000_000), 1);
        cart.updateQuantity(1, 5);
        assertEquals(5, cart.getItems().get(0).getQuantity());
    }

    @Test
    void updateQuantity_soLuongKhongDuong_xoaKhoiGioHang() {
        cart.addToCart(phone(1, 1_000_000), 1);
        cart.updateQuantity(1, 0);
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void getTotal_tinhTongTienDungTheoGiaVaSoLuong() {
        cart.addToCart(phone(1, 10_000_000), 2); // 20tr
        cart.addToCart(phone(2, 5_000_000), 3);  // 15tr

        assertEquals(35_000_000L, cart.getTotal());
    }

    @Test
    void getTotalQuantity_tinhTongSoLuongTatCaSanPham() {
        cart.addToCart(phone(1, 1_000_000), 2);
        cart.addToCart(phone(2, 2_000_000), 3);

        assertEquals(5, cart.getTotalQuantity());
    }

    @Test
    void clear_xoaSachGioHang() {
        cart.addToCart(phone(1, 1_000_000), 1);
        cart.clear();
        assertTrue(cart.getItems().isEmpty());
        assertEquals(0, cart.getTotal());
    }

    @Test
    void listener_duocGoiKhiGioHangThayDoi() {
        AtomicBoolean notified = new AtomicBoolean(false);
        cart.addListener(() -> notified.set(true));

        cart.addToCart(phone(1, 1_000_000), 1);

        assertTrue(notified.get());
    }
}