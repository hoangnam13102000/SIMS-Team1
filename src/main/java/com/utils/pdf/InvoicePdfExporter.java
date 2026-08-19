package com.utils.pdf;

import com.lowagie.text.pdf.draw.DottedLineSeparator;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.utils.NumberUtil;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Xuat hoa don ban hang ra file PDF voi thiet ke hien dai, phong cach sieu
 * thi (lay cam hung bo cuc tu Bach Hoa Xanh: khoi tieu de bang noi bat,
 * duong ke cham kieu "xe hoa don", tong tien duoc nhan manh trong khoi mau
 * dam), nhung dung dung mau thuong hieu that cua cua hang - xanh duong dam
 * / xanh duong sang cua logo Connect Mart (logo_icon.png).
 *
 * Su dung thu vien OpenPDF (com.github.librepdf:openpdf), nhung nhung font
 * Roboto (co san trong resources/fonts, ma hoa IDENTITY_H) de hien thi day
 * du tieng Viet co dau.
 */
public class InvoicePdfExporter {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // ---- Bang mau chu dao - xanh duong thuong hieu Connect Mart (lay tu logo_icon.png) ----
    private static final Color PRIMARY_COLOR = new Color(15, 45, 110);     // Xanh duong dam (logo) - tieu de, khoi tong tien
    private static final Color PRIMARY_LIGHT = new Color(222, 234, 250);   // Xanh duong nhat - nen highlight
    private static final Color HEADER_BG = new Color(15, 45, 110);         // Nen tieu de bang san pham
    private static final Color ACCENT_BLUE = new Color(0, 157, 230);       // Xanh duong sang (logo) - dai trang tri, nhan manh
    private static final Color ACCENT_BLUE_LIGHT = new Color(224, 244, 253); // Nen nhat cho ghi chu doi/tra
    private static final Color ROW_ALT_BG = new Color(240, 246, 252);      // Nen xen ke cac dong (xanh rat nhat)
    private static final Color BORDER_COLOR = new Color(214, 227, 242);    // Duong ke nhe
    private static final Color TEXT_DARK = new Color(23, 32, 46);          // Chu chinh
    private static final Color TEXT_MUTED = new Color(100, 116, 139);      // Chu phu, nhan muc

    // Font
    private static Font storeNameFont;
    private static Font storeInfoFont;
    private static Font titleFont;
    private static Font sectionLabelFont;
    private static Font sectionValueFont;
    private static Font tableHeaderFont;
    private static Font tableBodyFont;
    private static Font totalLabelFont;
    private static Font totalValueFont;
    private static Font grandTotalLabelFont;
    private static Font grandTotalValueFont;
    private static Font footerFont;
    private static Font footerCodeFont;

    private static byte[] logoBytes;

    static {
        try {
            // Dung font Roboto nhung san (ho tro day du dau tieng Viet) thay vi
            // Helvetica chuan (Helvetica/WinAnsi khong the hien thi dau tieng Viet).
            // QUAN TRONG: chi nhung phan BAT BUOC (font) moi duoc phep lam fail
            // ca static initializer. Neu 1 dong throw o day, JVM boc thanh
            // ExceptionInInitializerError/NoClassDefFoundError (deu la Error,
            // KHONG PHAI Exception) - cac noi goi InvoicePdfExporter.exportInvoice(...)
            // hien dang "catch (Exception ex)" se KHONG bat duoc loi nay, khien nut
            // "In hoa don" o POS lan man hinh Quan ly hoa don deu im lang khong phan
            // hoi (giong nhu khong lam gi ca). Vi vay logo (chi mang tinh trang tri)
            // duoc nap RIENG, khong bao gio lam fail ca class.
            byte[] regularBytes = loadResourceBytes("/fonts/Roboto-Regular.ttf");
            byte[] boldBytes = loadResourceBytes("/fonts/Roboto-Bold.ttf");

            BaseFont roboto = BaseFont.createFont("Roboto-Regular.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, regularBytes, null);
            BaseFont robotoBold = BaseFont.createFont("Roboto-Bold.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, boldBytes, null);

            storeNameFont = new Font(robotoBold, 21, Font.NORMAL, PRIMARY_COLOR);
            storeInfoFont = new Font(roboto, 9, Font.NORMAL, TEXT_MUTED);
            titleFont = new Font(robotoBold, 15, Font.NORMAL, TEXT_DARK);
            sectionLabelFont = new Font(roboto, 8.5f, Font.NORMAL, TEXT_MUTED);
            sectionValueFont = new Font(robotoBold, 11, Font.NORMAL, TEXT_DARK);
            tableHeaderFont = new Font(robotoBold, 9, Font.NORMAL, Color.WHITE);
            tableBodyFont = new Font(roboto, 10, Font.NORMAL, TEXT_DARK);
            totalLabelFont = new Font(roboto, 10, Font.NORMAL, TEXT_MUTED);
            totalValueFont = new Font(robotoBold, 10, Font.NORMAL, TEXT_DARK);
            grandTotalLabelFont = new Font(robotoBold, 13, Font.NORMAL, Color.WHITE);
            grandTotalValueFont = new Font(robotoBold, 16, Font.NORMAL, Color.WHITE);
            footerFont = new Font(roboto, 9, Font.ITALIC, TEXT_MUTED);
            footerCodeFont = new Font(roboto, 8, Font.NORMAL, TEXT_MUTED);
        } catch (Exception e) {
            throw new RuntimeException("Khong the khoi tao font cho PDF hoa don", e);
        }

        // Logo la thanh phan trang tri, khong bat buoc: dung lai file logo_icon.png
        // co san (da duoc dung o Header/AppIcon...) nen chac chan ton tai trong
        // resources. Neu vi ly do gi do van khong doc duoc (thieu file, build cu
        // chua copy resources...) thi bo qua anh, hoa don van xuat ra binh thuong
        // (khong co logo) thay vi lam sap ca tinh nang in hoa don.
        try {
            logoBytes = loadResourceBytes("/logo/logo_icon.png");
        } catch (Exception e) {
            logoBytes = null;
            System.err.println("[InvoicePdfExporter] Khong the nap anh logo (/logo/logo_icon.png), "
                    + "hoa don se duoc xuat khong co logo: " + e.getMessage());
        }
    }

    private static byte[] loadResourceBytes(String resourcePath) throws IOException {
        try (InputStream is = InvoicePdfExporter.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Khong tim thay tai nguyen trong resources: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    /**
     * Xuat hoa don ra file PDF.
     */
    public static File exportInvoice(Invoice invoice, List<InvoiceDetail> details, File outputFile)
            throws Exception {
        Document document = new Document(PageSize.A5, 24, 24, 20, 20);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();

        addStoreHeader(document);

        Paragraph title = new Paragraph("HÓA ĐƠN BÁN HÀNG", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(4);
        title.setSpacingAfter(6);
        document.add(title);

        DottedLineSeparator dotted = new DottedLineSeparator();
        dotted.setLineColor(BORDER_COLOR);
        dotted.setGap(3f);
        document.add(dotted);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(4);
        spacer.setSpacingAfter(4);
        document.add(spacer);

        addInvoiceInfo(document, invoice);
        addProductTable(document, invoice, details);
        addReturnNote(document, invoice);
        addTotalSection(document, invoice);
        addFooter(document, invoice);

        document.close();
        writer.close();
        return outputFile;
    }

    private static void addStoreHeader(Document document) throws DocumentException {
        if (logoBytes != null) {
            try {
                Image logo = Image.getInstance(logoBytes);
                logo.scaleToFit(44, 42);
                logo.setAlignment(Element.ALIGN_CENTER);
                logo.setSpacingAfter(2);
                document.add(logo);
            } catch (Exception e) {
                // Anh logo loi/hong khong duoc lam gian doan viec xuat hoa don.
            }
        }

        Paragraph storeName = new Paragraph("CONNECT MART", storeNameFont);
        storeName.setAlignment(Element.ALIGN_CENTER);
        storeName.setSpacingAfter(3);

        Paragraph storeInfo = new Paragraph(
                "254 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh  |  1900 636 522  |  cskh@connectmart.vn",
                storeInfoFont
        );
        storeInfo.setAlignment(Element.ALIGN_CENTER);
        storeInfo.setSpacingAfter(8);

        document.add(storeName);
        document.add(storeInfo);

        // Dai 2 tong mau xanh duong, dong bo voi 2 sac do cua logo Connect Mart.
        PdfPTable bandTable = new PdfPTable(2);
        bandTable.setWidthPercentage(100);
        bandTable.setWidths(new float[]{3f, 1f});

        PdfPCell darkBand = new PdfPCell();
        darkBand.setBackgroundColor(PRIMARY_COLOR);
        darkBand.setFixedHeight(3f);
        darkBand.setBorder(Rectangle.NO_BORDER);
        bandTable.addCell(darkBand);

        PdfPCell lightBand = new PdfPCell();
        lightBand.setBackgroundColor(ACCENT_BLUE);
        lightBand.setFixedHeight(3f);
        lightBand.setBorder(Rectangle.NO_BORDER);
        bandTable.addCell(lightBand);

        document.add(bandTable);
    }

    private static void addInvoiceInfo(Document document, Invoice invoice) throws DocumentException {
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});
        infoTable.getDefaultCell().setBorder(Rectangle.NO_BORDER);
        infoTable.getDefaultCell().setPadding(4);

        // Cot trai
        PdfPCell leftCell = new PdfPCell();
        leftCell.setBorder(Rectangle.NO_BORDER);
        leftCell.setPadding(4);
        leftCell.addElement(new Paragraph("KHÁCH HÀNG", sectionLabelFont));
        String customerName = invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()
                ? invoice.getCustomerName() : "Khách lẻ";
        leftCell.addElement(new Paragraph(customerName, sectionValueFont));
        leftCell.addElement(new Paragraph(" "));
        leftCell.addElement(new Paragraph("NHÂN VIÊN LẬP", sectionLabelFont));
        leftCell.addElement(new Paragraph(invoice.getCreatedByName() != null ? invoice.getCreatedByName() : "-", sectionValueFont));

        // Cot phai
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(4);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph p1 = new Paragraph("MÃ HÓA ĐƠN", sectionLabelFont);
        p1.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p1);

        Paragraph p2 = new Paragraph(invoice.getInvoiceCode(), sectionValueFont);
        p2.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p2);
        rightCell.addElement(new Paragraph(" "));

        Paragraph p3 = new Paragraph("NGÀY LẬP", sectionLabelFont);
        p3.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p3);

        String dateStr = invoice.getCreatedAt() != null
                ? invoice.getCreatedAt().format(DATE_TIME_FORMAT) : "-";
        Paragraph p4 = new Paragraph(dateStr, sectionValueFont);
        p4.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p4);
        rightCell.addElement(new Paragraph(" "));

        Paragraph p5 = new Paragraph("PHƯƠNG THỨC", sectionLabelFont);
        p5.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p5);

        Paragraph p6 = new Paragraph(paymentMethodLabel(invoice.getPaymentMethod()), sectionValueFont);
        p6.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p6);

        infoTable.addCell(leftCell);
        infoTable.addCell(rightCell);
        document.add(infoTable);

        Paragraph s = new Paragraph(" ");
        s.setSpacingBefore(8);
        document.add(s);
    }

    private static void addProductTable(Document document, Invoice invoice, List<InvoiceDetail> details)
            throws DocumentException {
        // Hoa don co doi/tra -> them cot DA TRA / CON LAI va tinh lai THANH TIEN
        // theo so luong CON LAI, de khop voi Invoice.getSubTotal()/getTotalAmount()
        // (da duoc trigger DB dieu chinh giam ngay khi phieu tra duoc duyet).
        // Khong lam vay se in ra SL/Thanh tien GOC (truoc khi tra) trong khi dong
        // TONG CONG lai la so MOI (sau khi tra) -> lech so, gay hieu lam.
        boolean showReturns = invoice.hasReturns();

        float[] widths = showReturns
                ? new float[]{0.5f, 2.6f, 1f, 0.65f, 0.7f, 0.7f, 1.35f}
                : new float[]{0.6f, 3.5f, 1.2f, 1f, 1.5f};
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);

        String[] headers = showReturns
                ? new String[]{"STT", "SẢN PHẨM", "ĐƠN GIÁ", "SL", "ĐÃ TRẢ", "CÒN LẠI", "THÀNH TIỀN"}
                : new String[]{"STT", "SẢN PHẨM", "ĐƠN GIÁ", "SL", "THÀNH TIỀN"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(8);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }

        int stt = 1;
        for (InvoiceDetail d : details) {
            boolean alt = stt % 2 == 0;

            table.addCell(bodyCell(String.valueOf(stt), Element.ALIGN_CENTER, alt));
            table.addCell(bodyCell(d.getProductName(), Element.ALIGN_LEFT, alt));
            table.addCell(bodyCell(NumberUtil.formatThousands(d.getUnitPrice().longValue()),
                    Element.ALIGN_RIGHT, alt));
            table.addCell(bodyCell(String.valueOf(d.getQuantity()), Element.ALIGN_CENTER, alt));

            if (showReturns) {
                table.addCell(bodyCell(String.valueOf(d.getReturnedQuantity()), Element.ALIGN_CENTER, alt));
                table.addCell(bodyCell(String.valueOf(d.getRemainingQuantity()), Element.ALIGN_CENTER, alt));
                long netLineTotal = d.getUnitPrice()
                        .multiply(BigDecimal.valueOf(d.getRemainingQuantity()))
                        .longValue();
                table.addCell(bodyCell(NumberUtil.formatThousands(netLineTotal), Element.ALIGN_RIGHT, alt));
            } else {
                table.addCell(bodyCell(NumberUtil.formatThousands(d.getLineTotal().longValue()),
                        Element.ALIGN_RIGHT, alt));
            }
            stt++;
        }

        document.add(table);
    }

    /** 1 o du lieu trong bang san pham - dung chung cho ca 2 layout (co/khong doi tra). */
    private static PdfPCell bodyCell(String text, int align, boolean alt) {
        PdfPCell cell = new PdfPCell(new Phrase(text, tableBodyFont));
        cell.setHorizontalAlignment(align);
        cell.setPadding(8);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(BORDER_COLOR);
        if (alt) cell.setBackgroundColor(ROW_ALT_BG);
        return cell;
    }

    /** Ghi chu "Da hoan ... / N phieu tra" ngay duoi bang san pham, chi hien khi hoa don co doi/tra. */
    private static void addReturnNote(Document document, Invoice invoice) throws DocumentException {
        if (!invoice.hasReturns()) return;

        String note = "Đã hoàn: " + formatVND(invoice.getRefundedAmount().longValue())
                + "  -  " + invoice.getApprovedReturnCount() + " phiếu đổi/trả đã duyệt"
                + " (SL/thành tiền trên đã trừ hàng trả, xem chi tiết phiếu đổi/trả tại quầy).";
        Font noteFont = new Font(totalLabelFont.getBaseFont(), 9, Font.NORMAL, PRIMARY_COLOR);

        PdfPTable noteTable = new PdfPTable(1);
        noteTable.setWidthPercentage(100);
        noteTable.setSpacingAfter(6);
        PdfPCell noteCell = new PdfPCell(new Phrase(note, noteFont));
        noteCell.setBackgroundColor(ACCENT_BLUE_LIGHT);
        noteCell.setBorder(Rectangle.NO_BORDER);
        noteCell.setPadding(7);
        noteTable.addCell(noteCell);
        document.add(noteTable);
    }

    private static void addTotalSection(Document document, Invoice invoice) throws DocumentException {
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(100);
        totalTable.setWidths(new float[]{1.5f, 1f});
        totalTable.setSpacingBefore(4);

        long subTotal = invoice.getSubTotal() != null ? invoice.getSubTotal().longValue() : 0;
        long discount = invoice.getDiscountAmount() != null ? invoice.getDiscountAmount().longValue() : 0;
        long pointsDiscount = invoice.getPointsDiscountAmount() != null
                ? invoice.getPointsDiscountAmount().longValue() : 0;
        long vat = invoice.getVatAmount() != null ? invoice.getVatAmount().longValue() : 0;
        long grandTotal = invoice.getTotalAmount() != null ? invoice.getTotalAmount().longValue() : 0;

        addTotalRow(totalTable, "Tạm tính:", formatVND(subTotal));

        if (discount > 0) {
            String promoLabel = "Giảm giá";
            if (invoice.getPromotionCode() != null && !invoice.getPromotionCode().isBlank()) {
                promoLabel += " (" + invoice.getPromotionCode() + ")";
            }
            addTotalRow(totalTable, promoLabel + ":", "-" + formatVND(discount));
        }

        if (pointsDiscount > 0) {
            addTotalRow(totalTable, "Điểm thành viên (" + invoice.getPointsUsed() + " điểm):",
                    "-" + formatVND(pointsDiscount));
        }

        String vatLabel = "VAT";
        if (invoice.getVatRate() != null) {
            vatLabel += " (" + invoice.getVatRate().stripTrailingZeros().toPlainString() + "%)";
        }
        addTotalRow(totalTable, vatLabel + ":", formatVND(vat));

        // Duong ke
        PdfPCell empty = new PdfPCell();
        empty.setBorder(Rectangle.NO_BORDER);
        empty.setFixedHeight(3);
        totalTable.addCell(empty);

        PdfPCell divider = new PdfPCell();
        divider.setBorder(Rectangle.TOP);
        divider.setBorderColorTop(BORDER_COLOR);
        divider.setBorderWidthTop(1f);
        divider.setFixedHeight(3);
        totalTable.addCell(divider);

        // TONG CONG - khoi mau xanh dam, chu trang, noi bat nhu bang gia BHX
        PdfPCell labelCell = new PdfPCell(new Phrase("TỔNG CỘNG", grandTotalLabelFont));
        labelCell.setBackgroundColor(PRIMARY_COLOR);
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        labelCell.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelCell.setPadding(10);
        labelCell.setPaddingLeft(14);
        totalTable.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatVND(grandTotal), grandTotalValueFont));
        valueCell.setBackgroundColor(PRIMARY_COLOR);
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(10);
        valueCell.setPaddingRight(14);
        totalTable.addCell(valueCell);

        document.add(totalTable);
    }

    private static void addTotalRow(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, totalLabelFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(5);
        labelCell.setPaddingRight(16);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, totalValueFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(5);
        valueCell.setPaddingRight(16);
        table.addCell(valueCell);
    }

    private static void addFooter(Document document, Invoice invoice) throws DocumentException {
        Paragraph sp = new Paragraph(" ");
        sp.setSpacingBefore(16);
        document.add(sp);

        DottedLineSeparator dotted = new DottedLineSeparator();
        dotted.setLineColor(BORDER_COLOR);
        dotted.setGap(3f);
        document.add(dotted);

        Paragraph f1 = new Paragraph(
                "Cảm ơn quý khách đã mua sắm tại Connect Mart! Hẹn gặp lại quý khách.",
                footerFont
        );
        f1.setAlignment(Element.ALIGN_CENTER);
        f1.setSpacingBefore(8);

        Paragraph f2 = new Paragraph(
                "Hỗ trợ: 1900 636 522  |  cskh@connectmart.vn  |  www.connectmart.vn",
                footerFont
        );
        f2.setAlignment(Element.ALIGN_CENTER);
        f2.setSpacingBefore(2);

        document.add(f1);
        document.add(f2);

        Paragraph f3 = new Paragraph(
                "Mã tra cứu: " + (invoice.getInvoiceCode() != null ? invoice.getInvoiceCode() : "-"),
                footerCodeFont
        );
        f3.setAlignment(Element.ALIGN_CENTER);
        f3.setSpacingBefore(6);
        document.add(f3);
    }

    private static String formatVND(long amount) {
        return NumberUtil.formatThousands(amount) + " đ";
    }

    private static String paymentMethodLabel(String method) {
        if (method == null) return "-";
        switch (method) {
            case "CASH": return "Tiền mặt";
            case "BANK_TRANSFER": return "Chuyển khoản";
            case "PAYPAL": return "PayPal";
            case "CARD": return "Thẻ";
            default: return method;
        }
    }
}