package com.utils;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;

/**
 * Tien ich sinh ma vach 1D (CODE_128) tu chuoi ky tu.
 * CODE_128 duoc chon vi ho tro day du ky tu ASCII (chu cai, so, ky tu dac biet
 * nhu "_") phu hop voi dinh dang customerCode (vd "CUS_0001").
 */
public final class BarcodeUtil {

    private BarcodeUtil() {}

    /**
     * Sinh anh ma vach CODE_128.
     *
     * @param content  Noi dung ma vach (thuong la customerCode)
     * @param widthPx  Chieu rong mong muon (pixel)
     * @param heightPx Chieu cao ma vach (pixel)
     * @return Anh BufferedImage da ve ma vach (nen trang, vach den)
     */
    public static BufferedImage generateCode128(String content, int widthPx, int heightPx) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Noi dung ma vach khong duoc rong");
        }
        
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.MARGIN, 2); // Canh trang nho xung quanh de de quet

        BitMatrix matrix = new Code128Writer().encode(
                content, BarcodeFormat.CODE_128, widthPx, heightPx, hints);
        return MatrixToImageWriter.toBufferedImage(matrix);
    }
}