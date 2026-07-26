package com.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Xuất dữ liệu bảng (header + rows) ra file CSV hoặc Excel (.xlsx) thật.
 * <p>
 * Không phụ thuộc Apache POI hay bất kỳ thư viện ngoài nào — file .xlsx được
 * ghi trực tiếp dưới dạng gói OOXML tối giản bằng {@link java.util.zip.ZipOutputStream}
 * sẵn có trong JDK, Excel mở bình thường không cảnh báo định dạng.
 * <p>
 * Dùng chung được cho mọi bảng dữ liệu trong ứng dụng (không phụ thuộc entity nào).
 */
public final class TableExportUtil {

    private static final char CSV_DELIMITER = ';'; // Excel locale vi-VN dùng ";" làm dấu phân tách CSV

    private TableExportUtil() {}

    // ---------------------------------------------------------------
    // CSV
    // ---------------------------------------------------------------

    public static void exportCsv(File file, String[] headers, List<Object[]> rows) throws IOException {
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            writer.write('\uFEFF'); // BOM để Excel nhận đúng font/dấu tiếng Việt
            writer.write(toCsvRow(headers));
            for (Object[] row : rows) {
                writer.write(toCsvRow(row));
            }
        }
    }

    private static String toCsvRow(Object[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(CSV_DELIMITER);
            sb.append(escapeCsv(values[i]));
        }
        sb.append("\r\n");
        return sb.toString();
    }

    private static String escapeCsv(Object value) {
        String s = value == null ? "" : String.valueOf(value);
        boolean mustQuote = s.indexOf(CSV_DELIMITER) >= 0 || s.contains("\"") || s.contains("\n") || s.contains("\r");
        if (mustQuote) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    // ---------------------------------------------------------------
    // Excel (.xlsx)
    // ---------------------------------------------------------------

    public static void exportExcel(File file, String sheetName, String[] headers, List<Object[]> rows) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            writeZipEntry(zos, "[Content_Types].xml", contentTypesXml());
            writeZipEntry(zos, "_rels/.rels", rootRelsXml());
            writeZipEntry(zos, "xl/workbook.xml", workbookXml(sheetName));
            writeZipEntry(zos, "xl/_rels/workbook.xml.rels", workbookRelsXml());
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", sheetXml(headers, rows));
        }
    }

    private static void writeZipEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static String sheetXml(String[] headers, List<Object[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
        appendRow(sb, 1, headers);
        int rowNumber = 2;
        for (Object[] row : rows) {
            appendRow(sb, rowNumber++, row);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    private static void appendRow(StringBuilder sb, int rowNumber, Object[] values) {
        sb.append("<row r=\"").append(rowNumber).append("\">");
        for (int col = 0; col < values.length; col++) {
            String ref = columnLetter(col) + rowNumber;
            Object value = values[col];
            if (value instanceof Number) {
                sb.append("<c r=\"").append(ref).append("\"><v>").append(value).append("</v></c>");
            } else {
                String text = value == null ? "" : String.valueOf(value);
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(escapeXml(text))
                        .append("</t></is></c>");
            }
        }
        sb.append("</row>");
    }

    private static String columnLetter(int zeroBasedIndex) {
        StringBuilder sb = new StringBuilder();
        int n = zeroBasedIndex;
        do {
            sb.insert(0, (char) ('A' + (n % 26)));
            n = n / 26 - 1;
        } while (n >= 0);
        return sb.toString();
    }

    private static String escapeXml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String contentTypesXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String rootRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String workbookXml(String sheetName) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + escapeXml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String workbookRelsXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }
}