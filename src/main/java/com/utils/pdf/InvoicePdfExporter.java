package com.utils.pdf;

import com.lowagie.text.pdf.draw.LineSeparator;
import com.model.Invoice;
import com.model.InvoiceDetail;
import com.utils.NumberUtil;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Xuat hoa don ban hang ra file PDF voi thiet ke hien dai, chuyen nghiep.
 * Su dung thu vien OpenPDF (com.github.librepdf:openpdf).
 */
public class InvoicePdfExporter {

    private static final DateTimeFormatter DATE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Mau sac chu dao - xanh duong nhat hien dai
    private static final Color PRIMARY_COLOR = new Color(30, 64, 175);      // Blue-700
    private static final Color PRIMARY_LIGHT = new Color(219, 234, 254);    // Blue-100
    private static final Color HEADER_BG = new Color(30, 64, 175);           // Blue-700
    private static final Color ROW_ALT_BG = new Color(248, 250, 252);        // Slate-50
    private static final Color BORDER_COLOR = new Color(226, 232, 240);     // Slate-200
    private static final Color TEXT_DARK = new Color(15, 23, 42);            // Slate-900
    private static final Color TEXT_MUTED = new Color(100, 116, 139);        // Slate-500

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
    private static Font grandTotalFont;
    private static Font footerFont;

    static {
        try {
            BaseFont helvetica = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.EMBEDDED);
            BaseFont helveticaBold = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.EMBEDDED);

            storeNameFont = new Font(helveticaBold, 22, Font.BOLD, PRIMARY_COLOR);
            storeInfoFont = new Font(helvetica, 10, Font.NORMAL, TEXT_MUTED);
            titleFont = new Font(helveticaBold, 16, Font.BOLD, TEXT_DARK);
            sectionLabelFont = new Font(helvetica, 9, Font.NORMAL, TEXT_MUTED);
            sectionValueFont = new Font(helveticaBold, 11, Font.BOLD, TEXT_DARK);
            tableHeaderFont = new Font(helveticaBold, 9, Font.BOLD, Color.WHITE);
            tableBodyFont = new Font(helvetica, 10, Font.NORMAL, TEXT_DARK);
            totalLabelFont = new Font(helvetica, 10, Font.NORMAL, TEXT_MUTED);
            totalValueFont = new Font(helveticaBold, 10, Font.BOLD, TEXT_DARK);
            grandTotalFont = new Font(helveticaBold, 15, Font.BOLD, PRIMARY_COLOR);
            footerFont = new Font(helvetica, 9, Font.ITALIC, TEXT_MUTED);
        } catch (Exception e) {
            throw new RuntimeException("Khong the khoi tao font PDF", e);
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

        Paragraph title = new Paragraph("HOA DON BAN HANG", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(6);
        document.add(title);

        LineSeparator line = new LineSeparator(1.5f, 100, PRIMARY_LIGHT, Element.ALIGN_CENTER, 0);
        document.add(line);
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(6);
        spacer.setSpacingAfter(6);
        document.add(spacer);

        addInvoiceInfo(document, invoice);
        addProductTable(document, details);
        addTotalSection(document, invoice);
        addFooter(document);

        document.close();
        writer.close();
        return outputFile;
    }

    private static void addStoreHeader(Document document) throws DocumentException {
        Paragraph storeName = new Paragraph("SIMS STORE", storeNameFont);
        storeName.setAlignment(Element.ALIGN_CENTER);
        storeName.setSpacingAfter(4);

        Paragraph storeInfo = new Paragraph(
                "254 Nguyen Van Linh, Quan 7, TP.Ho Chi Minh  |  028 3876 5432  |  hello@simsstore.vn",
                storeInfoFont
        );
        storeInfo.setAlignment(Element.ALIGN_CENTER);
        storeInfo.setSpacingAfter(10);

        document.add(storeName);
        document.add(storeInfo);
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
        leftCell.addElement(new Paragraph("KHACH HANG", sectionLabelFont));
        String customerName = invoice.getCustomerName() != null && !invoice.getCustomerName().isBlank()
                ? invoice.getCustomerName() : "Khach le";
        leftCell.addElement(new Paragraph(customerName, sectionValueFont));
        leftCell.addElement(new Paragraph(" "));
        leftCell.addElement(new Paragraph("NHAN VIEN LAP", sectionLabelFont));
        leftCell.addElement(new Paragraph(invoice.getCreatedByName() != null ? invoice.getCreatedByName() : "-", sectionValueFont));

        // Cot phai
        PdfPCell rightCell = new PdfPCell();
        rightCell.setBorder(Rectangle.NO_BORDER);
        rightCell.setPadding(4);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        Paragraph p1 = new Paragraph("MA HOA DON", sectionLabelFont);
        p1.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p1);

        Paragraph p2 = new Paragraph(invoice.getInvoiceCode(), sectionValueFont);
        p2.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p2);
        rightCell.addElement(new Paragraph(" "));

        Paragraph p3 = new Paragraph("NGAY LAP", sectionLabelFont);
        p3.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p3);

        String dateStr = invoice.getCreatedAt() != null
                ? invoice.getCreatedAt().format(DATE_TIME_FORMAT) : "-";
        Paragraph p4 = new Paragraph(dateStr, sectionValueFont);
        p4.setAlignment(Element.ALIGN_RIGHT);
        rightCell.addElement(p4);
        rightCell.addElement(new Paragraph(" "));

        Paragraph p5 = new Paragraph("PHUONG THUC", sectionLabelFont);
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

    private static void addProductTable(Document document, List<InvoiceDetail> details) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{0.6f, 3.5f, 1.2f, 1f, 1.5f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);

        String[] headers = {"STT", "SAN PHAM", "DON GIA", "SL", "THANH TIEN"};
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

            PdfPCell sttCell = new PdfPCell(new Phrase(String.valueOf(stt), tableBodyFont));
            sttCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            sttCell.setPadding(8);
            sttCell.setBorder(Rectangle.NO_BORDER);
            sttCell.setBorderWidthBottom(0.5f);
            sttCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) sttCell.setBackgroundColor(ROW_ALT_BG);

            PdfPCell nameCell = new PdfPCell(new Phrase(d.getProductName(), tableBodyFont));
            nameCell.setPadding(8);
            nameCell.setBorder(Rectangle.NO_BORDER);
            nameCell.setBorderWidthBottom(0.5f);
            nameCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) nameCell.setBackgroundColor(ROW_ALT_BG);

            PdfPCell priceCell = new PdfPCell(new Phrase(
                    NumberUtil.formatThousands(d.getUnitPrice().longValue()), tableBodyFont));
            priceCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            priceCell.setPadding(8);
            priceCell.setBorder(Rectangle.NO_BORDER);
            priceCell.setBorderWidthBottom(0.5f);
            priceCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) priceCell.setBackgroundColor(ROW_ALT_BG);

            PdfPCell qtyCell = new PdfPCell(new Phrase(String.valueOf(d.getQuantity()), tableBodyFont));
            qtyCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            qtyCell.setPadding(8);
            qtyCell.setBorder(Rectangle.NO_BORDER);
            qtyCell.setBorderWidthBottom(0.5f);
            qtyCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) qtyCell.setBackgroundColor(ROW_ALT_BG);

            PdfPCell totalCell = new PdfPCell(new Phrase(
                    NumberUtil.formatThousands(d.getLineTotal().longValue()), tableBodyFont));
            totalCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalCell.setPadding(8);
            totalCell.setBorder(Rectangle.NO_BORDER);
            totalCell.setBorderWidthBottom(0.5f);
            totalCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) totalCell.setBackgroundColor(ROW_ALT_BG);

            table.addCell(sttCell);
            table.addCell(nameCell);
            table.addCell(priceCell);
            table.addCell(qtyCell);
            table.addCell(totalCell);
            stt++;
        }

        document.add(table);
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

        addTotalRow(totalTable, "Tam tinh:", formatVND(subTotal));

        if (discount > 0) {
            String promoLabel = "Giam gia";
            if (invoice.getPromotionCode() != null && !invoice.getPromotionCode().isBlank()) {
                promoLabel += " (" + invoice.getPromotionCode() + ")";
            }
            addTotalRow(totalTable, promoLabel + ":", "-" + formatVND(discount));
        }

        if (pointsDiscount > 0) {
            addTotalRow(totalTable, "Diem thanh vien (" + invoice.getPointsUsed() + " diem):",
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
        empty.setFixedHeight(1);
        totalTable.addCell(empty);

        PdfPCell divider = new PdfPCell();
        divider.setBorder(Rectangle.TOP);
        divider.setBorderColorTop(BORDER_COLOR);
        divider.setBorderWidthTop(1f);
        divider.setFixedHeight(1);
        totalTable.addCell(divider);

        // TONG CONG
        PdfPCell labelCell = new PdfPCell(new Phrase("TONG CONG:", grandTotalFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        labelCell.setPadding(8);
        labelCell.setPaddingRight(16);
        totalTable.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(formatVND(grandTotal) + " VND", grandTotalFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setBackgroundColor(PRIMARY_LIGHT);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        valueCell.setPadding(10);
        valueCell.setPaddingRight(16);
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

    private static void addFooter(Document document) throws DocumentException {
        Paragraph sp = new Paragraph(" ");
        sp.setSpacingBefore(20);
        document.add(sp);

        LineSeparator line = new LineSeparator(0.5f, 100, BORDER_COLOR, Element.ALIGN_CENTER, 0);
        document.add(line);

        Paragraph f1 = new Paragraph(
                "Cam on quy khach da mua sam tai SIMS Store! Hen gap lai quy khach.",
                footerFont
        );
        f1.setAlignment(Element.ALIGN_CENTER);
        f1.setSpacingBefore(8);

        Paragraph f2 = new Paragraph(
                "Ho tro: 028 3876 5432  |  hello@simsstore.vn  |  www.simsstore.vn",
                footerFont
        );
        f2.setAlignment(Element.ALIGN_CENTER);
        f2.setSpacingBefore(2);

        document.add(f1);
        document.add(f2);
    }

    private static String formatVND(long amount) {
        return NumberUtil.formatThousands(amount) + " d";
    }

    private static String paymentMethodLabel(String method) {
        if (method == null) return "-";
        switch (method) {
            case "CASH": return "Tien mat";
            case "BANK_TRANSFER": return "Chuyen khoan";
            case "PAYPAL": return "PayPal";
            case "CARD": return "The";
            default: return method;
        }
    }
}