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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Xuat bao cao doanh thu & loi nhuan (danh cho quan ly) ra file PDF day du
 * so lieu, cac bang chi tiet va khu vuc chu ky nhu mot trang bao cao thuc te.
 * <p>
 * Ngoai cac bang so lieu, bao cao con co phan "Tom tat dieu hanh" va
 * "Nhan dinh & khuyen nghi" duoc tong hop tu du lieu thanh van ban de nguoi
 * doc (quan ly/giam doc) nam duoc buc tranh tong quan ma khong can tu doc
 * va doi chieu tung dong trong bang.
 * <p>
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
    private static final Color CALLOUT_BG = new Color(240, 245, 255);
    private static final Color WARN_BG = new Color(255, 247, 237);
    private static final Color WARN_BORDER = new Color(253, 186, 116);
    private static final Color WARN_TEXT = new Color(154, 52, 18);

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
    private static Font narrativeFont;
    private static Font narrativeBoldFont;
    private static Font calloutTitleFont;
    private static Font bulletFont;
    private static Font bulletBoldFont;
    private static Font tableNoteFont;

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
            narrativeFont = new Font(roboto, 9.5f, Font.NORMAL, TEXT_DARK);
            narrativeBoldFont = new Font(robotoBold, 9.5f, Font.NORMAL, TEXT_DARK);
            calloutTitleFont = new Font(robotoBold, 10.5f, Font.NORMAL, PRIMARY_COLOR);
            bulletFont = new Font(roboto, 9.5f, Font.NORMAL, TEXT_DARK);
            bulletBoldFont = new Font(robotoBold, 9.5f, Font.NORMAL, TEXT_DARK);
            tableNoteFont = new Font(roboto, 8.5f, Font.ITALIC, TEXT_MUTED);
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
        addExecutiveSummary(document, ctx);
        addSummarySection(document, ctx);
        addProfitSection(document, ctx);
        addInsightsSection(document, ctx);
        addDailyFinanceTable(document, ctx);
        addPaymentAndCategoryTables(document, ctx);
        addTopProductsTables(document, ctx);
        addConclusionAndSignature(document, ctx);
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
    // Tom tat dieu hanh (van ban tong hop, khong phai bang)
    // ---------------------------------------------------------------

    private static void addExecutiveSummary(Document document, ReportContext ctx) throws DocumentException {
        Summary s = ctx.summary != null ? ctx.summary : new Summary(BigDecimal.ZERO, 0, 0);
        ProfitSummary p = ctx.profitSummary != null ? ctx.profitSummary
                : new ProfitSummary(BigDecimal.ZERO, BigDecimal.ZERO);
        Double growth = s.growthPercent(ctx.previousSummary);
        Double margin = p.netMarginPercent();

        StringBuilder sb = new StringBuilder();
        sb.append("Trong kỳ báo cáo từ ").append(ctx.from.format(DATE_FORMAT))
                .append(" đến ").append(ctx.to.format(DATE_FORMAT))
                .append(", cửa hàng ghi nhận tổng doanh thu ")
                .append(formatVND(s.totalRevenue.longValue()))
                .append(" từ ").append(NumberUtil.formatThousands(s.invoiceCount))
                .append(" hóa đơn (trung bình ").append(formatVND(s.avgOrderValue().longValue()))
                .append("/hóa đơn), tương ứng ").append(NumberUtil.formatThousands(s.itemsSold))
                .append(" sản phẩm bán ra. ");

        if (growth == null) {
            sb.append("Chưa có dữ liệu kỳ trước để so sánh mức tăng trưởng. ");
        } else if (growth >= 0) {
            sb.append("Doanh thu tăng ").append(NumberUtil.formatDecimal(growth, 1))
                    .append("% so với kỳ trước. ");
        } else {
            sb.append("Doanh thu giảm ").append(NumberUtil.formatDecimal(Math.abs(growth), 1))
                    .append("% so với kỳ trước. ");
        }

        sb.append("Sau khi trừ giá vốn hàng bán")
                .append(p.totalLoss.signum() > 0 ? " và thiệt hại hủy hàng" : "")
                .append(", lợi nhuận ròng đạt ").append(formatVND(p.netProfit.longValue()))
                .append(p.netProfit.signum() < 0 ? " (lỗ ròng)" : "")
                .append(margin != null ? ", tương ứng biên lợi nhuận ròng khoảng "
                        + NumberUtil.formatDecimal(margin, 1) + "%. " : ". ");

        CategoryProfit topCategory = topByProfit(ctx.categories);
        if (topCategory != null) {
            sb.append("Danh mục đóng góp lợi nhuận cao nhất là \"").append(topCategory.categoryName)
                    .append("\" với ").append(formatVND(topCategory.profit.longValue())).append(". ");
        }

        PaymentSlice topPayment = topPaymentMethod(ctx.payments);
        if (topPayment != null && s.totalRevenue.signum() > 0) {
            double pct = NumberUtil.percentageOf(topPayment.revenue.doubleValue(), s.totalRevenue.doubleValue());
            sb.append("Phương thức thanh toán phổ biến nhất là ")
                    .append(paymentMethodLabel(topPayment.method))
                    .append(", chiếm khoảng ").append(NumberUtil.formatDecimal(pct, 0)).append("% doanh thu.");
        }

        Paragraph heading = new Paragraph("TÓM TẮT ĐIỀU HÀNH", calloutTitleFont);
        heading.setSpacingAfter(4);

        Paragraph body = new Paragraph(sb.toString(), narrativeFont);
        body.setAlignment(Element.ALIGN_JUSTIFIED);
        body.setLeading(14f);

        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(CALLOUT_BG);
        cell.setBorder(Rectangle.LEFT);
        cell.setBorderWidthLeft(3f);
        cell.setBorderColorLeft(PRIMARY_COLOR);
        cell.setPadding(10);
        cell.addElement(heading);
        cell.addElement(body);

        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);
        wrapper.setSpacingAfter(12);
        wrapper.addCell(cell);
        document.add(wrapper);
    }

    // ---------------------------------------------------------------
    // Tong quan doanh thu / loi nhuan
    // ---------------------------------------------------------------

    private static void addSummarySection(Document document, ReportContext ctx) throws DocumentException {
        addSectionTitle(document, "1. TỔNG QUAN DOANH THU");

        Summary s = ctx.summary != null ? ctx.summary : new Summary(BigDecimal.ZERO, 0, 0);
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
                : new ProfitSummary(BigDecimal.ZERO, BigDecimal.ZERO);
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
    // Nhan dinh & khuyen nghi (van ban dang gach dau dong)
    // ---------------------------------------------------------------

    private static void addInsightsSection(Document document, ReportContext ctx) throws DocumentException {
        List<String> bullets = buildInsightBullets(ctx);
        if (bullets.isEmpty()) return;

        addSectionTitle(document, "3. NHẬN ĐỊNH & KHUYẾN NGHỊ");

        com.lowagie.text.List list = new com.lowagie.text.List(false, 10);
        list.setListSymbol(new Chunk("•  ", bulletBoldFont));
        for (String b : bullets) {
            ListItem item = new ListItem(new Phrase(b, bulletFont));
            item.setSpacingAfter(4);
            item.setAlignment(Element.ALIGN_JUSTIFIED);
            list.add(item);
        }
        list.setIndentationLeft(4);
        document.add(list);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingAfter(4);
        document.add(spacer);
    }

    private static List<String> buildInsightBullets(ReportContext ctx) {
        List<String> bullets = new ArrayList<>();
        Summary s = ctx.summary != null ? ctx.summary : new Summary(BigDecimal.ZERO, 0, 0);
        ProfitSummary p = ctx.profitSummary != null ? ctx.profitSummary
                : new ProfitSummary(BigDecimal.ZERO, BigDecimal.ZERO);
        Double growth = s.growthPercent(ctx.previousSummary);
        Double margin = p.netMarginPercent();

        if (growth != null) {
            if (growth >= 10) {
                bullets.add("Doanh thu tăng trưởng mạnh (" + NumberUtil.formatDecimal(growth, 1)
                        + "% so với kỳ trước) — nên duy trì các yếu tố đang phát huy hiệu quả "
                        + "(khuyến mãi, danh mục chủ lực, kênh thanh toán) cho kỳ tiếp theo.");
            } else if (growth >= 0) {
                bullets.add("Doanh thu tăng nhẹ (" + NumberUtil.formatDecimal(growth, 1)
                        + "% so với kỳ trước) — mức tăng trưởng còn khiêm tốn, có thể cân nhắc thêm "
                        + "chương trình kích cầu vào các ngày bán chậm.");
            } else {
                bullets.add("Doanh thu giảm " + NumberUtil.formatDecimal(Math.abs(growth), 1)
                        + "% so với kỳ trước — cần rà soát nguyên nhân (nhu cầu thị trường, tồn kho, "
                        + "cạnh tranh, hiệu quả khuyến mãi) để có hướng điều chỉnh kịp thời.");
            }
        }

        if (margin != null) {
            if (margin < 0) {
                bullets.add("Biên lợi nhuận ròng đang âm (" + NumberUtil.formatDecimal(margin, 1)
                        + "%) — doanh thu chưa đủ bù đắp giá vốn và thiệt hại hủy hàng trong kỳ, "
                        + "cần xem xét lại giá bán, chi phí nhập hàng hoặc kiểm soát hao hụt.");
            } else if (margin < 10) {
                bullets.add("Biên lợi nhuận ròng ở mức thấp (" + NumberUtil.formatDecimal(margin, 1)
                        + "%) — nên rà soát giá vốn và chi phí hủy hàng để cải thiện hiệu quả kinh doanh.");
            } else {
                bullets.add("Biên lợi nhuận ròng ở mức khả quan (" + NumberUtil.formatDecimal(margin, 1)
                        + "%), cho thấy hoạt động kinh doanh trong kỳ đang có hiệu quả tốt.");
            }
        }

        if (s.totalRevenue.signum() > 0 && p.totalLoss.signum() > 0) {
            double lossPct = NumberUtil.percentageOf(p.totalLoss.doubleValue(), s.totalRevenue.doubleValue());
            if (lossPct >= 2) {
                bullets.add("Thiệt hại do hủy hàng chiếm khoảng " + NumberUtil.formatDecimal(lossPct, 1)
                        + "% doanh thu (" + formatVND(p.totalLoss.longValue())
                        + ") — nên kiểm tra lại quy trình nhập hàng, hạn sử dụng và bảo quản để giảm hao hụt.");
            }
        }

        CategoryProfit topCategory = topByProfit(ctx.categories);
        CategoryProfit weakCategory = weakestByProfit(ctx.categories);
        if (topCategory != null && weakCategory != null && !topCategory.categoryName.equals(weakCategory.categoryName)) {
            bullets.add("Danh mục \"" + topCategory.categoryName + "\" mang lại lợi nhuận cao nhất, trong khi \""
                    + weakCategory.categoryName + "\" đóng góp thấp nhất — có thể cân nhắc điều chỉnh cơ cấu "
                    + "hàng hóa hoặc chiến lược giá cho danh mục có lợi nhuận thấp.");
        }

        PaymentSlice topPayment = topPaymentMethod(ctx.payments);
        if (topPayment != null && s.totalRevenue.signum() > 0) {
            double pct = NumberUtil.percentageOf(topPayment.revenue.doubleValue(), s.totalRevenue.doubleValue());
            if (pct >= 70) {
                bullets.add("Doanh thu phụ thuộc khá lớn vào hình thức " + paymentMethodLabel(topPayment.method)
                        + " (khoảng " + NumberUtil.formatDecimal(pct, 0) + "% doanh thu) — nên đa dạng hóa "
                        + "phương thức thanh toán để giảm rủi ro vận hành.");
            }
        }

        DailyFinancePoint bestDay = bestDay(ctx.financeDaily);
        DailyFinancePoint worstDay = worstDay(ctx.financeDaily);
        if (bestDay != null && worstDay != null && !bestDay.date.equals(worstDay.date)) {
            bullets.add("Ngày kinh doanh tốt nhất trong kỳ là " + bestDay.date.format(DATE_FORMAT)
                    + " (lợi nhuận ròng " + formatVND(bestDay.netProfit().longValue())
                    + "); ngày thấp nhất là " + worstDay.date.format(DATE_FORMAT)
                    + " (" + formatVND(worstDay.netProfit().longValue())
                    + ") — có thể đối chiếu để tìm nguyên nhân chênh lệch (ngày lễ, thời tiết, tồn kho...).");
        }

        return bullets;
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

    private static void addTableNote(Document document, String text) throws DocumentException {
        Paragraph note = new Paragraph(text, tableNoteFont);
        note.setSpacingAfter(6);
        note.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(note);
    }

    private static void addDailyFinanceTable(Document document, ReportContext ctx) throws DocumentException {
        List<DailyFinancePoint> daily = ctx.financeDaily;
        if (daily == null || daily.isEmpty()) return;

        addSectionTitle(document, "4. CHI TIẾT THU - CHI - LỢI NHUẬN THEO NGÀY");

        DailyFinancePoint bestDay = bestDay(daily);
        DailyFinancePoint worstDay = worstDay(daily);
        if (bestDay != null && worstDay != null) {
            addTableNote(document, "Bảng dưới đây thể hiện diễn biến thu, chi và lợi nhuận ròng theo từng ngày "
                    + "trong kỳ. Ngày có lợi nhuận ròng cao nhất là " + bestDay.date.format(DATE_FORMAT)
                    + " (" + formatVND(bestDay.netProfit().longValue()) + "), thấp nhất là "
                    + worstDay.date.format(DATE_FORMAT) + " (" + formatVND(worstDay.netProfit().longValue()) + ").");
        }

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
            addSectionTitle(document, "5. DOANH THU THEO PHƯƠNG THỨC THANH TOÁN");
            PaymentSlice topPayment = topPaymentMethod(ctx.payments);
            if (topPayment != null && ctx.summary != null && ctx.summary.totalRevenue.signum() > 0) {
                double pct = NumberUtil.percentageOf(topPayment.revenue.doubleValue(),
                        ctx.summary.totalRevenue.doubleValue());
                addTableNote(document, paymentMethodLabel(topPayment.method) + " là hình thức thanh toán được "
                        + "khách hàng sử dụng nhiều nhất, chiếm khoảng " + NumberUtil.formatDecimal(pct, 0)
                        + "% tổng doanh thu trong kỳ.");
            }
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
            addSectionTitle(document, "6. LỢI NHUẬN THEO DANH MỤC");
            CategoryProfit topCategory = topByProfit(ctx.categories);
            CategoryProfit weakCategory = weakestByProfit(ctx.categories);
            if (topCategory != null && weakCategory != null) {
                addTableNote(document, "Danh mục \"" + topCategory.categoryName + "\" dẫn đầu về lợi nhuận với "
                        + formatVND(topCategory.profit.longValue()) + ", trong khi \"" + weakCategory.categoryName
                        + "\" ghi nhận mức thấp nhất với " + formatVND(weakCategory.profit.longValue()) + ".");
            }
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
            addSectionTitle(document, "7. TOP SẢN PHẨM BÁN CHẠY (THEO DOANH THU)");
            TopProduct best = ctx.topProducts.get(0);
            addTableNote(document, "Sản phẩm bán chạy nhất trong kỳ là \"" + best.productName + "\" với "
                    + NumberUtil.formatThousands(best.quantity) + " sản phẩm, mang lại "
                    + formatVND(best.revenue.longValue()) + " doanh thu.");
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
            addSectionTitle(document, "8. TOP SẢN PHẨM THEO LỢI NHUẬN");
            ProductProfit best = ctx.topProductsProfit.get(0);
            addTableNote(document, "Xét theo lợi nhuận, \"" + best.productName + "\" đóng góp nhiều nhất với "
                    + formatVND(best.profit.longValue()) + " lợi nhuận trong kỳ.");
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
    // Ket luan & Chu ky
    // ---------------------------------------------------------------

    private static void addConclusionAndSignature(Document document, ReportContext ctx) throws DocumentException {
        addSectionTitle(document, "9. KẾT LUẬN");

        String conclusion = buildConclusionText(ctx);
        Paragraph body = new Paragraph(conclusion, narrativeFont);
        body.setAlignment(Element.ALIGN_JUSTIFIED);
        body.setLeading(14f);
        body.setSpacingAfter(4);
        document.add(body);

        Paragraph note = new Paragraph(
                "Số liệu trong báo cáo này được tổng hợp tự động từ hệ thống và cần được đối chiếu trước khi sử dụng chính thức.",
                tableNoteFont
        );
        note.setSpacingAfter(10);
        document.add(note);

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
        table.addCell(signatureCell("KẾ TOÁN TRƯỞNG", "Hà Minh Tuấn"));
        table.addCell(signatureCell("GIÁM ĐỐC / QUẢN LÝ", "Hoàng Trung Nam"));

        document.add(table);
    }

    private static String buildConclusionText(ReportContext ctx) {
        Summary s = ctx.summary != null ? ctx.summary : new Summary(BigDecimal.ZERO, 0, 0);
        ProfitSummary p = ctx.profitSummary != null ? ctx.profitSummary
                : new ProfitSummary(BigDecimal.ZERO, BigDecimal.ZERO);
        Double growth = s.growthPercent(ctx.previousSummary);
        boolean netPositive = p.netProfit.signum() >= 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Nhìn chung, hoạt động kinh doanh trong kỳ ")
                .append(netPositive ? "mang lại lợi nhuận dương" : "đang ghi nhận lỗ ròng")
                .append(", với tổng doanh thu ").append(formatVND(s.totalRevenue.longValue()))
                .append(growth != null
                        ? (growth >= 0 ? ", tăng " : ", giảm ") + NumberUtil.formatDecimal(Math.abs(growth), 1)
                            + "% so với kỳ trước"
                        : "")
                .append(". ");

        if (netPositive) {
            sb.append("Đề nghị tiếp tục theo dõi sát các danh mục và sản phẩm chủ lực để duy trì đà tăng trưởng, "
                    + "đồng thời kiểm soát chi phí và tỷ lệ hủy hàng ở mức hợp lý.");
        } else {
            sb.append("Đề nghị bộ phận liên quan rà soát lại cơ cấu chi phí, giá vốn và các khoản hao hụt để có "
                    + "phương án cải thiện lợi nhuận trong kỳ tới.");
        }

        return sb.toString();
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

    // ---------------------------------------------------------------
    // Helpers dung de tong hop van ban tu du lieu
    // ---------------------------------------------------------------

    private static CategoryProfit topByProfit(List<CategoryProfit> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream().max(Comparator.comparing(c -> c.profit)).orElse(null);
    }

    private static CategoryProfit weakestByProfit(List<CategoryProfit> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream().min(Comparator.comparing(c -> c.profit)).orElse(null);
    }

    private static PaymentSlice topPaymentMethod(List<PaymentSlice> payments) {
        if (payments == null || payments.isEmpty()) return null;
        return payments.stream().max(Comparator.comparing(pm -> pm.revenue)).orElse(null);
    }

    private static DailyFinancePoint bestDay(List<DailyFinancePoint> daily) {
        if (daily == null || daily.isEmpty()) return null;
        return daily.stream().max(Comparator.comparing(DailyFinancePoint::netProfit)).orElse(null);
    }

    private static DailyFinancePoint worstDay(List<DailyFinancePoint> daily) {
        if (daily == null || daily.isEmpty()) return null;
        return daily.stream().min(Comparator.comparing(DailyFinancePoint::netProfit)).orElse(null);
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