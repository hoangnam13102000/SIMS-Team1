package com.utils;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * DocumentFilter cho ô nhập số tiền / lương:
 * - Khi gõ: tự động thêm dấu ',' phân tách hàng nghìn (vd 2000 → 2,000).
 * - Chỉ cho phép nhập chữ số (không chữ, không ký tự lạ).
 * - Giá trị thực (khi đọc) vẫn là số nguyên thuần, không rối dấu → không gây lỗi parse.
 *
 * Cách dùng:
 * <pre>
 *   JTextField field = new JTextField();
 *   CurrencyDocumentFilter.install(field);
 *   // Khi load dữ liệu:
 *   field.setText(CurrencyDocumentFilter.format(amount));
 *   // Khi lấy giá trị:
 *   BigDecimal value = CurrencyDocumentFilter.parse(field.getText());
 * </pre>
 */
public final class CurrencyDocumentFilter extends DocumentFilter {

    private static final char GROUPING = ',';
    private static final DecimalFormat FORMATTER;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(GROUPING);
        FORMATTER = new DecimalFormat("#,###", symbols);
        FORMATTER.setGroupingUsed(true);
        FORMATTER.setMaximumFractionDigits(0);
        FORMATTER.setParseBigDecimal(true);
    }

    private final JTextField field;

    private CurrencyDocumentFilter(JTextField field) {
        this.field = field;
    }

    /**
     * Cài filter + căn phải cho ô nhập tiền.
     * Gọi 1 lần sau khi tạo JTextField.
     */
    public static void install(JTextField field) {
        if (field == null) return;
        AbstractDocument doc = (AbstractDocument) field.getDocument();
        // Tránh cài trùng
        if (doc.getDocumentFilter() instanceof CurrencyDocumentFilter) return;
        doc.setDocumentFilter(new CurrencyDocumentFilter(field));
        field.setHorizontalAlignment(JTextField.RIGHT);
    }

    /** Format số thành chuỗi có dấu ',' (null/âm → ""). */
    public static String format(BigDecimal amount) {
        if (amount == null) return "";
        try {
            return FORMATTER.format(amount.toBigInteger());
        } catch (Exception e) {
            return amount.toPlainString();
        }
    }

    public static String format(long amount) {
        return FORMATTER.format(amount);
    }

    /**
     * Parse chuỗi đã format (có hoặc không có ',') về BigDecimal.
     * Trả null nếu rỗng hoặc không hợp lệ.
     */
    public static BigDecimal parse(String text) {
        if (text == null) return null;
        String cleaned = text.trim()
                .replace(",", "")
                .replace(".", "")
                .replace(" ", "");
        if (cleaned.isEmpty()) return null;
        if (!cleaned.matches("\\d+")) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Kiểm tra chuỗi có phải số không âm hợp lệ (cho phép dấu ','). */
    public static boolean isValidNonNegative(String text) {
        return parse(text) != null;
    }

    // ------------------------------------------------------------------
    // DocumentFilter overrides
    // ------------------------------------------------------------------

    @Override
    public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
            throws BadLocationException {
        if (string == null || string.isEmpty()) return;
        // Chỉ giữ chữ số
        String digits = string.replaceAll("\\D", "");
        if (digits.isEmpty()) return;
        replace(fb, offset, 0, digits, attr);
    }

    @Override
    public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
            throws BadLocationException {
        String current = fb.getDocument().getText(0, fb.getDocument().getLength());
        StringBuilder sb = new StringBuilder(current);
        // Xóa phần bị thay
        sb.delete(offset, offset + length);
        // Chèn phần mới (chỉ chữ số)
        String digitsOnly = text == null ? "" : text.replaceAll("\\D", "");
        sb.insert(offset, digitsOnly);

        String rawDigits = sb.toString().replaceAll("\\D", "");
        // Cho phép xóa hết
        if (rawDigits.isEmpty()) {
            fb.replace(0, fb.getDocument().getLength(), "", attrs);
            return;
        }
        // Bỏ số 0 đứng đầu (trừ khi chỉ còn "0")
        rawDigits = stripLeadingZeros(rawDigits);

        String formatted = formatRaw(rawDigits);

        // Tính vị trí con trỏ mới: đếm số chữ số bên trái vị trí chèn
        int digitsBeforeCaret = countDigits(current.substring(0, Math.min(offset, current.length())))
                + digitsOnly.length();
        int newCaret = caretPosForDigitCount(formatted, digitsBeforeCaret);

        fb.replace(0, fb.getDocument().getLength(), formatted, attrs);

        // Đặt lại caret sau khi document đã cập nhật
        SwingUtilities.invokeLater(() -> {
            try {
                int pos = Math.min(newCaret, field.getText().length());
                field.setCaretPosition(pos);
            } catch (Exception ignored) {
            }
        });
    }

    @Override
    public void remove(FilterBypass fb, int offset, int length) throws BadLocationException {
        String current = fb.getDocument().getText(0, fb.getDocument().getLength());
        StringBuilder sb = new StringBuilder(current);
        sb.delete(offset, offset + length);

        String rawDigits = sb.toString().replaceAll("\\D", "");
        if (rawDigits.isEmpty()) {
            fb.replace(0, fb.getDocument().getLength(), "", null);
            return;
        }
        rawDigits = stripLeadingZeros(rawDigits);
        String formatted = formatRaw(rawDigits);

        // Caret: số chữ số còn lại bên trái offset
        int digitsBefore = countDigits(current.substring(0, Math.min(offset, current.length())));
        int newCaret = caretPosForDigitCount(formatted, digitsBefore);

        fb.replace(0, fb.getDocument().getLength(), formatted, null);

        SwingUtilities.invokeLater(() -> {
            try {
                int pos = Math.min(newCaret, field.getText().length());
                field.setCaretPosition(pos);
            } catch (Exception ignored) {
            }
        });
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String stripLeadingZeros(String digits) {
        if (digits == null || digits.isEmpty()) return "0";
        String stripped = digits.replaceFirst("^0+(?!$)", "");
        return stripped.isEmpty() ? "0" : stripped;
    }

    private static String formatRaw(String digits) {
        try {
            return FORMATTER.format(new BigDecimal(digits));
        } catch (Exception e) {
            return digits;
        }
    }

    private static int countDigits(String s) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isDigit(s.charAt(i))) n++;
        }
        return n;
    }

    /** Tìm vị trí trong chuỗi đã format sao cho có đúng {@code digitCount} chữ số bên trái. */
    private static int caretPosForDigitCount(String formatted, int digitCount) {
        if (digitCount <= 0) return 0;
        int seen = 0;
        for (int i = 0; i < formatted.length(); i++) {
            if (Character.isDigit(formatted.charAt(i))) {
                seen++;
                if (seen == digitCount) return i + 1;
            }
        }
        return formatted.length();
    }
}