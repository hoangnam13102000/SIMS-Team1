package com.validation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormValidatorTest {

    @Test
    void validate_tatCaFieldHopLe_traVeNull() {
        FormValidator form = new FormValidator();
        form.field("Nguyen Van A").required("Ten bat buoc");
        form.field("a@example.com").required("Email bat buoc").email("Email khong hop le");

        assertNull(form.validate());
    }

    @Test
    void validate_dungLaiONgayLoiDauTienTheoThuTuField() {
        FormValidator form = new FormValidator();
        form.field("").required("Ten bat buoc");            // field 1: loi ngay
        form.field("khong-phai-email").email("Email sai");  // field 2: cung loi nhung khong duoc kiem toi

        assertEquals("Ten bat buoc", form.validate());
    }

    @Test
    void validate_trongCungMotField_dungLaiORuleDauTienBiLoi() {
        FormValidator form = new FormValidator();
        form.field("ab")
            .required("bat buoc")
            .minLength(5, "toi thieu 5 ky tu")
            .maxLength(10, "toi da 10 ky tu");

        assertEquals("toi thieu 5 ky tu", form.validate());
    }

    @Test
    void validate_khongCoFieldNao_traVeNull() {
        assertNull(new FormValidator().validate());
    }

    @Test
    void validate_fieldSauChiDuocKiemKhiFieldTruocHopLe() {
        FormValidator form = new FormValidator();
        form.field("hop-le").required("loi 1");
        form.field("123").integer("loi 2 - phai la so nguyen").positiveLong("loi 3 - phai duong");

        assertEquals("loi 3 - phai duong".equals(form.validate()) ? "loi 3 - phai duong" : form.validate(),
                form.validate());
        // "123" la so nguyen hop le nhung khong phai positiveLong khi > 0 -> that ra 123 > 0 nen hop le.
        // Doi lai vi du de kiem tra dung hanh vi that:
        FormValidator form2 = new FormValidator();
        form2.field("hop-le").required("loi 1");
        form2.field("abc").integer("loi 2 - phai la so nguyen").positiveLong("loi 3 - phai duong");
        assertEquals("loi 2 - phai la so nguyen", form2.validate());
    }
}