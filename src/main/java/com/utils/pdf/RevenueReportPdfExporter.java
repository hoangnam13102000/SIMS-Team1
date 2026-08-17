package com.utils.pdf;

import com.dao.RevenueReportDAO.CategoryProfit;
import com.dao.RevenueReportDAO.DailyFinancePoint;
import com.dao.RevenueReportDAO.PaymentSlice;
import com.dao.RevenueReportDAO.ProductProfit;
import com.dao.RevenueReportDAO.ProfitSummary;
import com.dao.RevenueReportDAO.Summary;
import com.dao.RevenueReportDAO.TopProduct;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.lowagie.text.pdf.draw.LineSeparator;
import com.utils.NumberUtil;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Xuat bao cao doanh thu & loi nhuan (danh cho quan ly) ra file PDF day du
 * so lieu, cac bang chi tiet va khu vuc chu ky nhu mot trang bao cao thuc te.
 * Su dung thu vien OpenPDF (com.github.librepdf:openpdf), nhung nhung font
 * Roboto (co san trong resources/fonts) de hien thi day du dau tieng Viet.
 */
public class RevenueReportPdfExporter {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color PRIMARY_COLOR = new Color(30, 64, 175);
    private static final Color PRIMARY_LIGHT = new Color(219, 234, 254);
    private static final Color HEADER_BG = new Color(30, 64, 175);
    private static final Color ROW_ALT_BG = new Color(248, 250, 252);
    private static final Color BORDER_COLOR = new Color(226, 232, 240);
    private static final Color TEXT_DARK = new Color(15, 23, 42);
    private static final Color TEXT_MUTED = new Color(100, 116, 139);
    private static final Color POSITIVE_COLOR = new Color(21, 128, 61);
    private static final Color NEGATIVE_COLOR = new Color(190, 18, 60);

    private static Font storeNameFont;
    private static Font storeInfoFont;
    private static Font titleFont;
    private static Font periodFont;
    private static Font metaFont;
    private static Font sectionTitleFont;
    private static Font statLabelFont;
    private static Font statValueFont;
    private static Font tableHeaderFont;
    private static Font tableBodyFont;
    private static Font tableBodyBoldFont;
    private static Font signTitleFont;
    private static Font signHintFont;
    private static Font signNameFont;
    private static Font footerFont;
    private static Font placeDateFont;

    static {
        try {
            // Dung font Roboto nhung san (ho tro day du dau tieng Viet) thay vi
            // Helvetica chuan (Helvetica/WinAnsi khong the hien thi dau tieng Viet).
            byte[] regularBytes = loadFontBytes("/fonts/Roboto-Regular.ttf");
            byte[] boldBytes = loadFontBytes("/fonts/Roboto-Bold.ttf");

            BaseFont roboto = BaseFont.createFont("Roboto-Regular.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, regularBytes, null);
            BaseFont robotoBold = BaseFont.createFont("Roboto-Bold.ttf", BaseFont.IDENTITY_H,
                    BaseFont.EMBEDDED, true, boldBytes, null);

            storeNameFont = new Font(robotoBold, 20, Font.NORMAL, PRIMARY_COLOR);
            storeInfoFont = new Font(roboto, 9, Font.NORMAL, TEXT_MUTED);
            titleFont = new Font(robotoBold, 17, Font.NORMAL, TEXT_DARK);
            periodFont = new Font(robotoBold, 11, Font.NORMAL, PRIMARY_COLOR);
            metaFont = new Font(roboto, 9, Font.NORMAL, TEXT_MUTED);
            sectionTitleFont = new Font(robotoBold, 12, Font.NORMAL, PRIMARY_COLOR);
            statLabelFont = new Font(roboto, 9, Font.NORMAL, TEXT_MUTED);
            statValueFont = new Font(robotoBold, 13, Font.NORMAL, TEXT_DARK);
            tableHeaderFont = new Font(robotoBold, 8.5f, Font.NORMAL, Color.WHITE);
            tableBodyFont = new Font(roboto, 9, Font.NORMAL, TEXT_DARK);
            tableBodyBoldFont = new Font(robotoBold, 9, Font.NORMAL, TEXT_DARK);
            signTitleFont = new Font(robotoBold, 10, Font.NORMAL, TEXT_DARK);
            signHintFont = new Font(roboto, 8.5f, Font.NORMAL, TEXT_MUTED);
            signNameFont = new Font(robotoBold, 10, Font.NORMAL, TEXT_DARK);
            footerFont = new Font(roboto, 8.5f, Font.NORMAL, TEXT_MUTED);
            placeDateFont = new Font(roboto, 9.5f, Font.NORMAL, TEXT_DARK);
        } catch (Exception e) {
            throw new RuntimeException("Khong the khoi tao font PDF", e);
        }
    }

    private static byte[] loadFontBytes(String resourcePath) throws IOException {
        try (InputStream is = RevenueReportPdfExporter.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Khong tim thay font trong resources: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    /** Gom toan bo du lieu can thiet de xuat bao cao. */
    public static class ReportContext {
        public LocalDate from;
        public LocalDate to;
        public Summary summary;
        public Summary previousSummary;
        public ProfitSummary profitSummary;
        public List<DailyFinancePoint> financeDaily;
        public List<PaymentSlice> payments;
        public List<CategoryProfit> categories;
        public List<TopProduct> topProducts;
        public List<ProductProfit> topProductsProfit;
        /** Ten nguoi lap bao cao (nhan vien dang dang nhap). */
        public String preparedByName;
        /** Chuc danh nguoi lap, vi du "Nhan vien" / "Quan ly". */
        public String preparedByRole;
    }

    public static File export(ReportContext ctx, File outputFile) throws Exception {
        Document document = new Document(PageSize.A4, 32, 32, 28, 28);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFile));
        document.open();

        addStoreHeader(document);
        addTitle(document, ctx);
        addSummarySection(document, ctx);
        addProfitSection(document, ctx);
        addDailyFinanceTable(document, ctx);
        addPaymentAndCategoryTables(document, ctx);
        addTopProductsTables(document, ctx);
        addSignatureSection(document, ctx);
        addFooter(document);

        document.close();
        writer.close();
        return outputFile;
    }

    // ---------------------------------------------------------------
    // Header / tieu de
    // ---------------------------------------------------------------

    private static void addStoreHeader(Document document) throws DocumentException {
        Paragraph storeName = new Paragraph("SIMS STORE", storeNameFont);
        storeName.setAlignment(Element.ALIGN_CENTER);
        storeName.setSpacingAfter(3);

        Paragraph storeInfo = new Paragraph(
                "254 Nguyễn Văn Linh, Quận 7, TP. Hồ Chí Minh  |  028 3876 5432  |  hello@simsstore.vn",
                storeInfoFont
        );
        storeInfo.setAlignment(Element.ALIGN_CENTER);
        storeInfo.setSpacingAfter(10);

        document.add(storeName);
        document.add(storeInfo);

        LineSeparator line = new LineSeparator(1.5f, 100, PRIMARY_LIGHT, Element.ALIGN_CENTER, 0);
        document.add(line);
    }

    private static void addTitle(Document document, ReportContext ctx) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(4);
        document.add(spacer);

        Paragraph title = new Paragraph("BÁO CÁO DOANH THU & LỢI NHUẬN", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        document.add(title);

        String periodText = "Kỳ báo cáo: " + ctx.from.format(DATE_FORMAT) + " - " + ctx.to.format(DATE_FORMAT);
        Paragraph period = new Paragraph(periodText, periodFont);
        period.setAlignment(Element.ALIGN_CENTER);
        period.setSpacingAfter(3);
        document.add(period);

        String metaText = "Ngày xuất báo cáo: " + LocalDateTime.now().format(DATE_TIME_FORMAT)
                + (ctx.preparedByName != null && !ctx.preparedByName.isBlank()
                        ? "   |   Người lập: " + ctx.preparedByName : "");
        Paragraph meta = new Paragraph(metaText, metaFont);
        meta.setAlignment(Element.ALIGN_CENTER);
        meta.setSpacingAfter(14);
        document.add(meta);
    }

    // ---------------------------------------------------------------
    // Tong quan doanh thu / loi nhuan
    // ---------------------------------------------------------------

    private static void addSummarySection(Document document, ReportContext ctx) throws DocumentException {
        addSectionTitle(document, "1. TỔNG QUAN DOANH THU");

        Summary s = ctx.summary != null ? ctx.summary : new Summary(java.math.BigDecimal.ZERO, 0, 0);
        Double growth = s.growthPercent(ctx.previousSummary);
        String growthText = growth == null ? "Không có dữ liệu kỳ trước"
                : (growth >= 0 ? "+" : "") + NumberUtil.formatDecimal(growth, 1) + "% so với kỳ trước";

        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setSpacingAfter(14);
        table.setWidths(new float[]{1, 1, 1, 1});

        addStatCell(table, "TỔNG DOANH THU", formatVND(s.totalRevenue.longValue()), growthText,
                growth == null || growth >= 0);
        addStatCell(table, "SỐ HÓA ĐƠN", NumberUtil.formatThousands(s.invoiceCount), "Hóa đơn hợp lệ", true);
        addStatCell(table, "GIÁ TRỊ TB / HÓA ĐƠN", formatVND(s.avgOrderValue().longValue()), "Doanh thu / số hóa đơn", true);
        addStatCell(table, "SỐ LƯỢNG BÁN", NumberUtil.formatThousands(s.itemsSold), "Tổng sản phẩm đã bán", true);

        document.add(table);
    }

    private static void addProfitSection(Document document, ReportContext ctx) throws DocumentException {
        addSectionTitle(document, "2. TỔNG QUAN LỢI NHUẬN");

        ProfitSummary p = ctx.profitSummary != null ? ctx.profitSummary
                : new ProfitSummary(java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);
        Double margin = p.netMarginPercent();
        String marginText = margin == null ? "0%" : NumberUtil.formatDecimal(margin, 1) + "%";

        PdfPTable row1 = new PdfPTable(3);
        row1.setWidthPercentage(100);
        row1.setSpacingAfter(6);
        row1.setWidths(new float[]{1, 1, 1});
        addStatCell(row1, "GIÁ VỐN HÀNG BÁN", formatVND(p.totalCost.longValue()), "Theo giá nhập hiện tại", true);
        addStatCell(row1, "THIỆT HẠI HỦY HÀNG", formatVND(p.totalLoss.longValue()), "Hàng hết hạn/hỏng đã tiêu hủy", true);
        addStatCell(row1, "HOÀN TRẢ NCC (tham khảo)", formatVND(p.totalSupplierRefund.longValue()),
                "Không trừ vào lợi nhuận ròng", true);
        document.add(row1);

        PdfPTable row2 = new PdfPTable(2);
        row2.setWidthPercentage(100);
        row2.setSpacingAfter(14);
        row2.setWidths(new float[]{1, 1});
        boolean netPositive = p.netProfit.signum() >= 0;
        addStatCell(row2, "LỢI NHUẬN RÒNG", formatVND(p.netProfit.longValue()),
                netPositive ? "Lãi ròng trong kỳ" : "Lỗ ròng trong kỳ", netPositive);
        addStatCell(row2, "BIÊN LỢI NHUẬN RÒNG", marginText, "Lợi nhuận ròng / doanh thu", netPositive);
        document.add(row2);
    }

    private static void addStatCell(PdfPTable table, String label, String value, String hint, boolean positive) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_COLOR);
        cell.setPadding(8);

        Paragraph labelP = new Paragraph(label, statLabelFont);
        labelP.setSpacingAfter(4);
        cell.addElement(labelP);

        Font valueFont = new Font(statValueFont.getBaseFont(), statValueFont.getSize(), Font.NORMAL,
                positive ? TEXT_DARK : NEGATIVE_COLOR);
        cell.addElement(new Paragraph(value, valueFont));

        if (hint != null) {
            Font hintFont = new Font(statLabelFont.getBaseFont(), 8, Font.NORMAL,
                    positive ? POSITIVE_COLOR : NEGATIVE_COLOR);
            Paragraph hintP = new Paragraph(hint, hintFont);
            hintP.setSpacingBefore(3);
            cell.addElement(hintP);
        }
        table.addCell(cell);
    }

    // ---------------------------------------------------------------
    // Bang chi tiet
    // ---------------------------------------------------------------

    private static void addSectionTitle(Document document, String text) throws DocumentException {
        Paragraph title = new Paragraph(text, sectionTitleFont);
        title.setSpacingBefore(6);
        title.setSpacingAfter(6);
        document.add(title);
    }

    private static void addDailyFinanceTable(Document document, ReportContext ctx) throws DocumentException {
        List<DailyFinancePoint> daily = ctx.financeDaily;
        if (daily == null || daily.isEmpty()) return;

        addSectionTitle(document, "3. CHI TIẾT THU - CHI - LỢI NHUẬN THEO NGÀY");

        String[] headers = {"NGÀY", "SỐ HĐ", "THU (DOANH THU)", "CHI (GIÁ VỐN + HỦY)", "LỢI NHUẬN RÒNG"};
        float[] widths = {1f, 0.7f, 1.4f, 1.4f, 1.4f};
        PdfPTable table = newTable(widths, headers);

        int i = 0;
        for (DailyFinancePoint pnt : daily) {
            boolean alt = i % 2 == 0;
            table.addCell(bodyCell(pnt.date.format(DATE_FORMAT), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(String.valueOf(pnt.invoiceCount), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(formatVND(pnt.revenue.longValue()), Element.ALIGN_RIGHT, alt, false));
            table.addCell(bodyCell(formatVND(pnt.totalExpense().longValue()), Element.ALIGN_RIGHT, alt, false));
            table.addCell(bodyCell(formatVND(pnt.netProfit().longValue()), Element.ALIGN_RIGHT, alt, true));
            i++;
        }
        document.add(table);
    }

    private static void addPaymentAndCategoryTables(Document document, ReportContext ctx) throws DocumentException {
        if (ctx.payments != null && !ctx.payments.isEmpty()) {
            addSectionTitle(document, "4. DOANH THU THEO PHƯƠNG THỨC THANH TOÁN");
            String[] headers = {"PHƯƠNG THỨC", "SỐ HÓA ĐƠN", "DOANH THU"};
            float[] widths = {1.6f, 1f, 1.4f};
            PdfPTable table = newTable(widths, headers);
            int i = 0;
            for (PaymentSlice p : ctx.payments) {
                boolean alt = i % 2 == 0;
                table.addCell(bodyCell(paymentMethodLabel(p.method), Element.ALIGN_LEFT, alt, false));
                table.addCell(bodyCell(String.valueOf(p.invoiceCount), Element.ALIGN_CENTER, alt, false));
                table.addCell(bodyCell(formatVND(p.revenue.longValue()), Element.ALIGN_RIGHT, alt, false));
                i++;
            }
            document.add(table);
        }

        if (ctx.categories != null && !ctx.categories.isEmpty()) {
            addSectionTitle(document, "5. LỢI NHUẬN THEO DANH MỤC");
            String[] headers = {"DANH MỤC", "DOANH THU", "GIÁ VỐN", "LỢI NHUẬN"};
            float[] widths = {1.6f, 1.2f, 1.2f, 1.2f};
            PdfPTable table = newTable(widths, headers);
            int i = 0;
            for (CategoryProfit c : ctx.categories) {
                boolean alt = i % 2 == 0;
                table.addCell(bodyCell(c.categoryName, Element.ALIGN_LEFT, alt, false));
                table.addCell(bodyCell(formatVND(c.revenue.longValue()), Element.ALIGN_RIGHT, alt, false));
                table.addCell(bodyCell(formatVND(c.cost.longValue()), Element.ALIGN_RIGHT, alt, false));
                table.addCell(bodyCell(formatVND(c.profit.longValue()), Element.ALIGN_RIGHT, alt, true));
                i++;
            }
            document.add(table);
        }
    }

    private static void addTopProductsTables(Document document, ReportContext ctx) throws DocumentException {
        if (ctx.topProducts != null && !ctx.topProducts.isEmpty()) {
            addSectionTitle(document, "6. TOP SẢN PHẨM BÁN CHẠY (THEO DOANH THU)");
            String[] headers = {"HẠNG", "SẢN PHẨM", "SỐ LƯỢNG", "DOANH THU"};
            float[] widths = {0.5f, 2.4f, 1f, 1.4f};
            PdfPTable table = newTable(widths, headers);
            int rank = 1;
            for (TopProduct t : ctx.topProducts) {
                boolean alt = rank % 2 == 0;
                table.addCell(bodyCell(String.valueOf(rank), Element.ALIGN_CENTER, alt, false));
                table.addCell(bodyCell(t.productName, Element.ALIGN_LEFT, alt, false));
                table.addCell(bodyCell(NumberUtil.formatThousands(t.quantity), Element.ALIGN_CENTER, alt, false));
                table.addCell(bodyCell(formatVND(t.revenue.longValue()), Element.ALIGN_RIGHT, alt, false));
                rank++;
            }
            document.add(table);
        }

        if (ctx.topProductsProfit != null && !ctx.topProductsProfit.isEmpty()) {
            addSectionTitle(document, "7. TOP SẢN PHẨM THEO LỢI NHUẬN");
            String[] headers = {"HẠNG", "SẢN PHẨM", "SỐ LƯỢNG", "DOANH THU", "LỢI NHUẬN"};
            float[] widths = {0.5f, 2f, 0.9f, 1.3f, 1.3f};
            PdfPTable table = newTable(widths, headers);
            int rank = 1;
            for (ProductProfit p : ctx.topProductsProfit) {
                boolean alt = rank % 2 == 0;
                table.addCell(bodyCell(String.valueOf(rank), Element.ALIGN_CENTER, alt, false));
                table.addCell(bodyCell(p.productName, Element.ALIGN_LEFT, alt, false));
                table.addCell(bodyCell(NumberUtil.formatThousands(p.quantity), Element.ALIGN_CENTER, alt, false));
                table.addCell(bodyCell(formatVND(p.revenue.longValue()), Element.ALIGN_RIGHT, alt, false));
                table.addCell(bodyCell(formatVND(p.profit.longValue()), Element.ALIGN_RIGHT, alt, true));
                rank++;
            }
            document.add(table);
        }
    }

    private static PdfPTable newTable(float[] widths, String[] headers) throws DocumentException {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(2);
        table.setSpacingAfter(14);
        table.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, tableHeaderFont));
            cell.setBackgroundColor(HEADER_BG);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cell.setPadding(6);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
        return table;
    }

    private static PdfPCell bodyCell(String text, int align, boolean alt, boolean bold) {
        PdfPCell cell = new PdfPCell(new Phrase(text, bold ? tableBodyBoldFont : tableBodyFont));
        cell.setHorizontalAlignment(align);
        cell.setPadding(6);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(BORDER_COLOR);
        if (alt) cell.setBackgroundColor(ROW_ALT_BG);
        return cell;
    }

    // ---------------------------------------------------------------
    // Chu ky
    // ---------------------------------------------------------------

    private static void addSignatureSection(Document document, ReportContext ctx) throws DocumentException {
        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(10);
        document.add(spacer);

        String placeDate = "TP. Hồ Chí Minh, ngày " + pad(LocalDate.now().getDayOfMonth())
                + " tháng " + pad(LocalDate.now().getMonthValue())
                + " năm " + LocalDate.now().getYear();
        Paragraph placeDateP = new Paragraph(placeDate, placeDateFont);
        placeDateP.setAlignment(Element.ALIGN_RIGHT);
        placeDateP.setSpacingAfter(16);
        document.add(placeDateP);

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 1, 1});

        table.addCell(signatureCell("NGƯỜI LẬP BÁO CÁO", ctx.preparedByName));
        table.addCell(signatureCell("KẾ TOÁN TRƯỞNG", null));
        table.addCell(signatureCell("GIÁM ĐỐC / QUẢN LÝ", null));

        document.add(table);
    }

    private static PdfPCell signatureCell(String title, String presetName) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);

        Paragraph titleP = new Paragraph(title, signTitleFont);
        titleP.setAlignment(Element.ALIGN_CENTER);
        titleP.setSpacingAfter(2);
        cell.addElement(titleP);

        Paragraph hintP = new Paragraph("(Ký, ghi rõ họ tên)", signHintFont);
        hintP.setAlignment(Element.ALIGN_CENTER);
        hintP.setSpacingAfter(50);
        cell.addElement(hintP);

        Paragraph nameP = new Paragraph(presetName != null && !presetName.isBlank() ? presetName : " ", signNameFont);
        nameP.setAlignment(Element.ALIGN_CENTER);
        cell.addElement(nameP);

        return cell;
    }

    private static String pad(int value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }

    // ---------------------------------------------------------------
    // Footer
    // ---------------------------------------------------------------

    private static void addFooter(Document document) throws DocumentException {
        Paragraph sp = new Paragraph(" ");
        sp.setSpacingBefore(16);
        document.add(sp);

        LineSeparator line = new LineSeparator(0.5f, 100, BORDER_COLOR, Element.ALIGN_CENTER, 0);
        document.add(line);

        Paragraph f1 = new Paragraph(
                "Báo cáo được tạo tự động bởi hệ thống SIMS - Số liệu cần được đối chiếu trước khi sử dụng chính thức.",
                footerFont
        );
        f1.setAlignment(Element.ALIGN_CENTER);
        f1.setSpacingBefore(8);

        Paragraph f2 = new Paragraph(
                "Hỗ trợ: 028 3876 5432  |  hello@simsstore.vn  |  www.simsstore.vn",
                footerFont
        );
        f2.setAlignment(Element.ALIGN_CENTER);
        f2.setSpacingBefore(2);

        document.add(f1);
        document.add(f2);
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