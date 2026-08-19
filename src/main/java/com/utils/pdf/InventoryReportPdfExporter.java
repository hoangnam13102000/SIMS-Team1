package com.utils.pdf;

import com.dao.InventoryReportDAO.CategoryStock;
import com.dao.InventoryReportDAO.OverallSummary;
import com.dao.InventoryReportDAO.PriceRangeStock;
import com.dao.InventoryReportDAO.ProductStock;
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
import java.util.stream.Collectors;

/**
 * Xuat bao cao ton kho (danh cho nhan vien kho / quan ly kho) ra file PDF
 * day du so lieu, cac bang chi tiet va khu vuc chu ky nhu mot trang bao cao
 * thuc te.
 * <p>
 * Ngoai cac bang so lieu, bao cao con co phan "Tom tat dieu hanh" va
 * "Nhan dinh & khuyen nghi" duoc tong hop tu du lieu thanh van ban de nguoi
 * doc (thu kho/quan ly) nam duoc buc tranh tong quan va cac diem can hanh
 * dong ngay (het hang, sap het hang, hang qua han) ma khong can tu doc va
 * doi chieu tung dong trong bang.
 * <p>
 * Cung phong cach voi {@link RevenueReportPdfExporter}: dung thu vien
 * OpenPDF (com.github.librepdf:openpdf), nhung nhung font Roboto (co san
 * trong resources/fonts) de hien thi day du dau tieng Viet.
 */
public class InventoryReportPdfExporter {

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
    private static final Color WARNING_COLOR = new Color(180, 83, 9);
    private static final Color CALLOUT_BG = new Color(240, 245, 255);

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
            calloutTitleFont = new Font(robotoBold, 10.5f, Font.NORMAL, PRIMARY_COLOR);
            bulletFont = new Font(roboto, 9.5f, Font.NORMAL, TEXT_DARK);
            bulletBoldFont = new Font(robotoBold, 9.5f, Font.NORMAL, TEXT_DARK);
            tableNoteFont = new Font(roboto, 8.5f, Font.ITALIC, TEXT_MUTED);
        } catch (Exception e) {
            throw new RuntimeException("Khong the khoi tao font PDF", e);
        }
    }

    private static byte[] loadFontBytes(String resourcePath) throws IOException {
        try (InputStream is = InventoryReportPdfExporter.class.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Khong tim thay font trong resources: " + resourcePath);
            }
            return is.readAllBytes();
        }
    }

    /** Gom toan bo du lieu can thiet de xuat bao cao. */
    public static class ReportContext {
        /** Ky xem xu huong ton kho (neu co) - ton kho hien tai luon la "tuc thoi", khong theo ky. */
        public LocalDate from;
        public LocalDate to;
        public OverallSummary summary;
        public List<CategoryStock> categories;
        public List<PriceRangeStock> priceRanges;
        public List<ProductStock> productStocks;
        /** Ten nguoi lap bao cao (nhan vien dang dang nhap). */
        public String preparedByName;
        /** Chuc danh nguoi lap, vi du "Nhan vien kho" / "Quan ly kho". */
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
        addInsightsSection(document, ctx);
        addCategoryTable(document, ctx);
        addPriceRangeTable(document, ctx);
        addProductStockTable(document, ctx);
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

        Paragraph title = new Paragraph("BÁO CÁO HÀNG TỒN KHO", titleFont);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingAfter(4);
        document.add(title);

        String periodText = "Thời điểm chốt số liệu: " + LocalDate.now().format(DATE_FORMAT)
                + (ctx.from != null && ctx.to != null
                        ? "   |   Xu hướng theo dõi: " + ctx.from.format(DATE_FORMAT) + " - " + ctx.to.format(DATE_FORMAT)
                        : "");
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
        OverallSummary s = ctx.summary != null ? ctx.summary
                : new OverallSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

        StringBuilder sb = new StringBuilder();
        sb.append("Tính đến thời điểm chốt số liệu ngày ").append(LocalDate.now().format(DATE_FORMAT))
                .append(", kho hàng đang quản lý ").append(NumberUtil.formatThousands(s.productCount))
                .append(" mặt hàng với tổng số lượng tồn là ").append(NumberUtil.formatThousands(s.totalQuantity))
                .append(" đơn vị. Giá trị tồn kho tính theo giá bán đạt ").append(formatVND(s.valueAtSellPrice.longValue()))
                .append(", tương ứng ").append(formatVND(s.valueAtImportPrice.longValue()))
                .append(" nếu tính theo giá nhập");

        BigDecimal potentialMargin = s.valueAtSellPrice.subtract(s.valueAtImportPrice);
        if (potentialMargin.signum() > 0) {
            sb.append(" — chênh lệch khoảng ").append(formatVND(potentialMargin.longValue()))
                    .append(" là biên lợi nhuận tiềm năng nếu bán hết số hàng tồn hiện tại");
        }
        sb.append(". ");

        if (s.outOfStockCount > 0 || s.lowStockCount > 0) {
            sb.append("Hiện có ").append(NumberUtil.formatThousands(s.outOfStockCount))
                    .append(" mặt hàng đã hết hàng và ").append(NumberUtil.formatThousands(s.lowStockCount))
                    .append(" mặt hàng tồn kho ở mức thấp (dưới hoặc bằng tồn tối thiểu), cần được ưu tiên xử lý. ");
        } else {
            sb.append("Không có mặt hàng nào hết hàng hoặc dưới mức tồn tối thiểu tại thời điểm chốt số liệu. ");
        }

        CategoryStock topCategory = topByValue(ctx.categories);
        if (topCategory != null) {
            sb.append("Danh mục chiếm giá trị tồn kho lớn nhất là \"").append(topCategory.categoryName)
                    .append("\" với ").append(formatVND(topCategory.valueAtSellPrice.longValue())).append(". ");
        }

        int expiredCount = countExpiredBatch(ctx.productStocks);
        if (expiredCount > 0) {
            sb.append("Đáng lưu ý, có ").append(NumberUtil.formatThousands(expiredCount))
                    .append(" mặt hàng đang có lô hàng đã quá hạn sử dụng cần được kiểm tra và xử lý.");
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
    // Tong quan ton kho
    // ---------------------------------------------------------------

    private static void addSummarySection(Document document, ReportContext ctx) throws DocumentException {
        addSectionTitle(document, "1. TỔNG QUAN TỒN KHO");

        OverallSummary s = ctx.summary != null ? ctx.summary
                : new OverallSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

        PdfPTable row1 = new PdfPTable(3);
        row1.setWidthPercentage(100);
        row1.setSpacingAfter(6);
        row1.setWidths(new float[]{1, 1, 1});
        addStatCell(row1, "SỐ MẶT HÀNG", NumberUtil.formatThousands(s.productCount), "Tổng số sản phẩm quản lý", true);
        addStatCell(row1, "TỔNG SỐ LƯỢNG TỒN", NumberUtil.formatThousands(s.totalQuantity), "Tổng đơn vị đang tồn kho", true);
        addStatCell(row1, "GIÁ TRỊ TỒN (GIÁ BÁN)", formatVND(s.valueAtSellPrice.longValue()), "Tính theo giá bán hiện tại", true);
        document.add(row1);

        PdfPTable row2 = new PdfPTable(3);
        row2.setWidthPercentage(100);
        row2.setSpacingAfter(14);
        row2.setWidths(new float[]{1, 1, 1});
        addStatCell(row2, "GIÁ TRỊ TỒN (GIÁ NHẬP)", formatVND(s.valueAtImportPrice.longValue()), "Tính theo giá nhập hiện tại", true);
        addStatCell(row2, "SẢN PHẨM SẮP HẾT HÀNG", NumberUtil.formatThousands(s.lowStockCount),
                "Tồn kho ≤ tồn tối thiểu", s.lowStockCount == 0);
        addStatCell(row2, "SẢN PHẨM HẾT HÀNG", NumberUtil.formatThousands(s.outOfStockCount),
                "Cần nhập hàng gấp", s.outOfStockCount == 0);
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

        addSectionTitle(document, "2. NHẬN ĐỊNH & KHUYẾN NGHỊ");

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
        OverallSummary s = ctx.summary != null ? ctx.summary
                : new OverallSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

        if (s.outOfStockCount > 0) {
            List<ProductStock> outOfStock = productsWithZeroStock(ctx.productStocks, 5);
            StringBuilder b = new StringBuilder();
            b.append("Có ").append(NumberUtil.formatThousands(s.outOfStockCount))
                    .append(" sản phẩm đang hết hàng, cần lên đơn nhập bổ sung sớm để tránh mất doanh thu");
            if (!outOfStock.isEmpty()) {
                b.append(", ví dụ: ").append(outOfStock.stream().map(p -> p.productName)
                        .collect(Collectors.joining(", ")));
                if (s.outOfStockCount > outOfStock.size()) b.append("...");
            }
            b.append(".");
            bullets.add(b.toString());
        }

        if (s.lowStockCount > 0) {
            bullets.add("Có " + NumberUtil.formatThousands(s.lowStockCount)
                    + " sản phẩm tồn kho ở mức thấp (dưới hoặc bằng tồn tối thiểu) — nên lên kế hoạch nhập hàng "
                    + "trước khi các mặt hàng này hết hàng hoàn toàn.");
        }

        int expiredCount = countExpiredBatch(ctx.productStocks);
        if (expiredCount > 0) {
            List<ProductStock> expired = productsWithExpiredBatch(ctx.productStocks, 5);
            StringBuilder b = new StringBuilder();
            b.append("Phát hiện ").append(NumberUtil.formatThousands(expiredCount))
                    .append(" sản phẩm có lô hàng đã quá hạn sử dụng nhưng chưa được xử lý");
            if (!expired.isEmpty()) {
                b.append(" (").append(expired.stream().map(p -> p.productName)
                        .collect(Collectors.joining(", "))).append(expiredCount > expired.size() ? "..." : "").append(")");
            }
            b.append(" — cần kiểm tra, tiêu hủy hoặc thanh lý kịp thời để tránh rủi ro an toàn và tồn kho ảo.");
            bullets.add(b.toString());
        }

        if (s.outOfStockCount == 0 && s.lowStockCount == 0 && expiredCount == 0) {
            bullets.add("Tình trạng tồn kho hiện tại ổn định: không có mặt hàng hết hàng, dưới mức tồn tối thiểu "
                    + "hoặc có lô hàng quá hạn cần xử lý.");
        }

        CategoryStock topCategory = topByValue(ctx.categories);
        if (topCategory != null && s.valueAtSellPrice.signum() > 0) {
            double pct = NumberUtil.percentageOf(topCategory.valueAtSellPrice.doubleValue(),
                    s.valueAtSellPrice.doubleValue());
            if (pct >= 40) {
                bullets.add("Giá trị tồn kho đang tập trung khá lớn vào danh mục \"" + topCategory.categoryName
                        + "\" (khoảng " + NumberUtil.formatDecimal(pct, 0) + "% tổng giá trị tồn) — nên cân đối "
                        + "lại cơ cấu hàng hóa để giảm rủi ro ứ đọng vốn ở một nhóm hàng.");
            }
        }

        PriceRangeStock topRange = topRangeByValue(ctx.priceRanges);
        if (topRange != null && s.valueAtSellPrice.signum() > 0) {
            double pct = NumberUtil.percentageOf(topRange.valueAtSellPrice.doubleValue(),
                    s.valueAtSellPrice.doubleValue());
            bullets.add("Phân khúc giá \"" + topRange.label + "\" chiếm giá trị tồn kho lớn nhất, khoảng "
                    + NumberUtil.formatDecimal(pct, 0) + "% tổng giá trị — phù hợp để ưu tiên khi lên kế hoạch "
                    + "khuyến mãi hoặc xoay vòng hàng tồn.");
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

    private static void addCategoryTable(Document document, ReportContext ctx) throws DocumentException {
        if (ctx.categories == null || ctx.categories.isEmpty()) return;

        addSectionTitle(document, "3. TỒN KHO THEO DANH MỤC");
        CategoryStock topCategory = topByValue(ctx.categories);
        if (topCategory != null) {
            addTableNote(document, "Danh mục \"" + topCategory.categoryName + "\" đang chiếm giá trị tồn kho lớn "
                    + "nhất với " + formatVND(topCategory.valueAtSellPrice.longValue()) + ".");
        }
        String[] headers = {"DANH MỤC", "SỐ MẶT HÀNG", "SỐ LƯỢNG TỒN", "GIÁ TRỊ TỒN (GIÁ BÁN)"};
        float[] widths = {1.6f, 1f, 1.2f, 1.4f};
        PdfPTable table = newTable(widths, headers);

        int i = 0;
        for (CategoryStock c : ctx.categories) {
            boolean alt = i % 2 == 0;
            table.addCell(bodyCell(c.categoryName, Element.ALIGN_LEFT, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(c.productCount), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(c.quantity), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(formatVND(c.valueAtSellPrice.longValue()), Element.ALIGN_RIGHT, alt, true));
            i++;
        }
        document.add(table);
    }

    private static void addPriceRangeTable(Document document, ReportContext ctx) throws DocumentException {
        if (ctx.priceRanges == null || ctx.priceRanges.isEmpty()) return;

        addSectionTitle(document, "4. TỒN KHO THEO KHOẢNG GIÁ BÁN");
        PriceRangeStock topRange = topRangeByValue(ctx.priceRanges);
        if (topRange != null) {
            addTableNote(document, "Phân khúc \"" + topRange.label + "\" chiếm giá trị tồn kho lớn nhất trong "
                    + "toàn bộ các khoảng giá bán, với " + formatVND(topRange.valueAtSellPrice.longValue()) + ".");
        }
        String[] headers = {"KHOẢNG GIÁ BÁN", "SỐ MẶT HÀNG", "SỐ LƯỢNG TỒN", "GIÁ TRỊ TỒN (GIÁ BÁN)"};
        float[] widths = {1.6f, 1f, 1.2f, 1.4f};
        PdfPTable table = newTable(widths, headers);

        int i = 0;
        for (PriceRangeStock p : ctx.priceRanges) {
            boolean alt = i % 2 == 0;
            table.addCell(bodyCell(p.label, Element.ALIGN_LEFT, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(p.productCount), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(p.quantity), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(formatVND(p.valueAtSellPrice.longValue()), Element.ALIGN_RIGHT, alt, true));
            i++;
        }
        document.add(table);
    }

    private static void addProductStockTable(Document document, ReportContext ctx) throws DocumentException {
        if (ctx.productStocks == null || ctx.productStocks.isEmpty()) return;

        addSectionTitle(document, "5. CHI TIẾT TỒN KHO THEO SẢN PHẨM (TOP " + ctx.productStocks.size() + ")");
        int outOfStock = (int) ctx.productStocks.stream().filter(p -> p.stock == 0).count();
        int lowStock = (int) ctx.productStocks.stream().filter(p -> p.stock > 0 && p.stock <= p.minStock).count();
        addTableNote(document, "Trong danh sách dưới đây, " + NumberUtil.formatThousands(outOfStock)
                + " sản phẩm đang hết hàng và " + NumberUtil.formatThousands(lowStock)
                + " sản phẩm ở mức sắp hết hàng — được đánh dấu theo cột \"TÌNH TRẠNG\" để tiện theo dõi và xử lý.");
        String[] headers = {"SẢN PHẨM", "TỒN KHO", "TỒN TỐI THIỂU", "HSD GẦN NHẤT", "TÌNH TRẠNG"};
        float[] widths = {2.2f, 0.9f, 1f, 1.1f, 1.3f};
        PdfPTable table = newTable(widths, headers);

        int i = 0;
        for (ProductStock p : ctx.productStocks) {
            boolean alt = i % 2 == 0;
            table.addCell(bodyCell(p.productName, Element.ALIGN_LEFT, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(p.stock), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(NumberUtil.formatThousands(p.minStock), Element.ALIGN_CENTER, alt, false));
            table.addCell(bodyCell(p.nearestExpiry != null ? p.nearestExpiry.format(DATE_FORMAT) : "-",
                    Element.ALIGN_CENTER, alt, false));

            String status;
            Color statusColor;
            if (p.stock == 0) {
                status = "Hết hàng";
                statusColor = NEGATIVE_COLOR;
            } else if (p.hasExpiredBatch) {
                status = "Có lô hết hạn";
                statusColor = NEGATIVE_COLOR;
            } else if (p.stock <= p.minStock) {
                status = "Sắp hết hàng";
                statusColor = WARNING_COLOR;
            } else {
                status = "Bình thường";
                statusColor = POSITIVE_COLOR;
            }
            Font statusFont = new Font(tableBodyBoldFont.getBaseFont(), tableBodyBoldFont.getSize(),
                    Font.NORMAL, statusColor);
            PdfPCell statusCell = new PdfPCell(new Phrase(status, statusFont));
            statusCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            statusCell.setPadding(6);
            statusCell.setBorder(Rectangle.NO_BORDER);
            statusCell.setBorderWidthBottom(0.5f);
            statusCell.setBorderColorBottom(BORDER_COLOR);
            if (alt) statusCell.setBackgroundColor(ROW_ALT_BG);
            table.addCell(statusCell);
            i++;
        }
        document.add(table);
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
        addSectionTitle(document, "6. KẾT LUẬN");

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
        table.addCell(signatureCell("THỦ KHO", "Trần Hoài Phương"));
        table.addCell(signatureCell("GIÁM ĐỐC / QUẢN LÝ", "Hoàng Trung Nam"));

        document.add(table);
    }

    private static String buildConclusionText(ReportContext ctx) {
        OverallSummary s = ctx.summary != null ? ctx.summary
                : new OverallSummary(0, 0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
        boolean hasIssue = s.outOfStockCount > 0 || s.lowStockCount > 0 || countExpiredBatch(ctx.productStocks) > 0;

        StringBuilder sb = new StringBuilder();
        sb.append("Tổng giá trị tồn kho hiện tại theo giá bán là ").append(formatVND(s.valueAtSellPrice.longValue()))
                .append(" trên ").append(NumberUtil.formatThousands(s.productCount)).append(" mặt hàng. ");

        if (hasIssue) {
            sb.append("Báo cáo ghi nhận một số điểm cần lưu ý (hàng hết/sắp hết, lô hàng quá hạn) đã được liệt kê "
                    + "tại phần \"Nhận định & khuyến nghị\" — đề nghị thủ kho và bộ phận thu mua phối hợp xử lý "
                    + "sớm để đảm bảo hoạt động kinh doanh không bị gián đoạn.");
        } else {
            sb.append("Tình trạng tồn kho hiện ổn định, không phát sinh vấn đề cần xử lý gấp. Đề nghị tiếp tục "
                    + "theo dõi định kỳ để duy trì mức tồn kho hợp lý.");
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

    private static CategoryStock topByValue(List<CategoryStock> categories) {
        if (categories == null || categories.isEmpty()) return null;
        return categories.stream().max(Comparator.comparing(c -> c.valueAtSellPrice)).orElse(null);
    }

    private static PriceRangeStock topRangeByValue(List<PriceRangeStock> ranges) {
        if (ranges == null || ranges.isEmpty()) return null;
        return ranges.stream().max(Comparator.comparing(r -> r.valueAtSellPrice)).orElse(null);
    }

    private static int countExpiredBatch(List<ProductStock> products) {
        if (products == null) return 0;
        return (int) products.stream().filter(p -> p.hasExpiredBatch).count();
    }

    private static List<ProductStock> productsWithZeroStock(List<ProductStock> products, int limit) {
        if (products == null) return new ArrayList<>();
        return products.stream().filter(p -> p.stock == 0).limit(limit).collect(Collectors.toList());
    }

    private static List<ProductStock> productsWithExpiredBatch(List<ProductStock> products, int limit) {
        if (products == null) return new ArrayList<>();
        return products.stream().filter(p -> p.hasExpiredBatch).limit(limit).collect(Collectors.toList());
    }

    private static String formatVND(long amount) {
        return NumberUtil.formatThousands(amount) + " đ";
    }
}