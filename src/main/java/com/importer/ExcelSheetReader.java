package com.importer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Doc du lieu dang bang tu file .xlsx (OOXML) khong phu thuoc Apache POI hay bat ky
 * thu vien ngoai nao - chi dung {@code javax.xml.stream} (StAX) co san trong JDK.
 * Dong bo voi trien khai {@link com.utils.TableExportUtil#exportExcel} (cung khong
 * dung POI) de xuat/nhap khong bi lech dinh dang.
 * <p>
 * Chi doc sheet DAU TIEN theo thu tu khai bao trong workbook.xml (du cho nhu cau
 * nhap du lieu CRUD dang 1-sheet-1-bang).
 */
public final class ExcelSheetReader {

    private ExcelSheetReader() {}

    /** Doc toan bo cac dong (ke ca dong header) cua sheet dau tien. Moi dong la mang chuoi, do dai bang so cot rong nhat trong file. */
    public static List<String[]> readRows(File xlsxFile) throws IOException {
        try (ZipFile zip = new ZipFile(xlsxFile)) {
            List<String> sharedStrings = readSharedStrings(zip);
            String sheetEntryName = resolveFirstSheetEntry(zip);
            ZipEntry sheetEntry = zip.getEntry(sheetEntryName);
            if (sheetEntry == null) {
                throw new IOException("Không tìm thấy dữ liệu sheet trong file Excel (" + sheetEntryName + ").");
            }
            try (InputStream is = zip.getInputStream(sheetEntry)) {
                return parseSheet(is, sharedStrings);
            }
        } catch (XMLStreamException e) {
            throw new IOException("File Excel không đúng định dạng hoặc bị hỏng: " + e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------
    // sharedStrings.xml
    // ---------------------------------------------------------------

    private static List<String> readSharedStrings(ZipFile zip) throws IOException, XMLStreamException {
        List<String> result = new ArrayList<>();
        ZipEntry entry = zip.getEntry("xl/sharedStrings.xml");
        if (entry == null) return result; // file khong dung shared string (moi cell ghi truc tiep) - hop le

        XMLInputFactory factory = safeFactory();
        try (InputStream is = zip.getInputStream(entry)) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            StringBuilder current = null;
            boolean insideItem = false;
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    if ("si".equals(reader.getLocalName())) {
                        current = new StringBuilder();
                        insideItem = true;
                    } else if ("t".equals(reader.getLocalName()) && insideItem) {
                        current.append(reader.getElementText());
                    }
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    if ("si".equals(reader.getLocalName())) {
                        result.add(current == null ? "" : current.toString());
                        insideItem = false;
                    }
                }
            }
            reader.close();
        }
        return result;
    }

    // ---------------------------------------------------------------
    // workbook.xml + rels -> tim entry cua sheet dau tien
    // ---------------------------------------------------------------

    private static String resolveFirstSheetEntry(ZipFile zip) throws IOException, XMLStreamException {
        ZipEntry workbookEntry = zip.getEntry("xl/workbook.xml");
        if (workbookEntry == null) return "xl/worksheets/sheet1.xml"; // fallback

        String firstSheetRid = null;
        XMLInputFactory factory = safeFactory();
        try (InputStream is = zip.getInputStream(workbookEntry)) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "sheet".equals(reader.getLocalName())) {
                    firstSheetRid = reader.getAttributeValue(
                            "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id");
                    break; // chi can sheet dau tien
                }
            }
            reader.close();
        }
        if (firstSheetRid == null) return "xl/worksheets/sheet1.xml";

        ZipEntry relsEntry = zip.getEntry("xl/_rels/workbook.xml.rels");
        if (relsEntry == null) return "xl/worksheets/sheet1.xml";

        try (InputStream is = zip.getInputStream(relsEntry)) {
            XMLStreamReader reader = factory.createXMLStreamReader(is);
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && "Relationship".equals(reader.getLocalName())) {
                    String id = reader.getAttributeValue(null, "Id");
                    if (firstSheetRid.equals(id)) {
                        String target = reader.getAttributeValue(null, "Target");
                        reader.close();
                        return target.startsWith("/") ? target.substring(1)
                                : "xl/" + target.replaceFirst("^\\./", "");
                    }
                }
            }
            reader.close();
        }
        return "xl/worksheets/sheet1.xml";
    }

    // ---------------------------------------------------------------
    // sheetN.xml
    // ---------------------------------------------------------------

    private static List<String[]> parseSheet(InputStream is, List<String> sharedStrings) throws XMLStreamException {
        List<String[]> rows = new ArrayList<>();
        XMLInputFactory factory = safeFactory();
        XMLStreamReader reader = factory.createXMLStreamReader(is);

        List<String> currentRowValues = null;
        int currentColIndex = -1;
        String currentCellType = null;
        StringBuilder currentValue = null;
        boolean insideValueTag = false;

        while (reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    switch (reader.getLocalName()) {
                        case "row":
                            currentRowValues = new ArrayList<>();
                            break;
                        case "c":
                            currentCellType = reader.getAttributeValue(null, "t");
                            String ref = reader.getAttributeValue(null, "r");
                            currentColIndex = ref != null ? columnIndexFromRef(ref) : currentColIndex + 1;
                            break;
                        case "v":
                        case "t": // inlineStr: <is><t>...</t></is>
                            insideValueTag = true;
                            currentValue = new StringBuilder();
                            break;
                        default:
                            break;
                    }
                    break;

                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                    if (insideValueTag && currentValue != null) {
                        currentValue.append(reader.getText());
                    }
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    if (("v".equals(reader.getLocalName()) || "t".equals(reader.getLocalName())) && insideValueTag) {
                        insideValueTag = false;
                        if (currentRowValues != null && currentColIndex >= 0) {
                            ensureSize(currentRowValues, currentColIndex + 1);
                            currentRowValues.set(currentColIndex, resolveCellValue(currentCellType, currentValue.toString(), sharedStrings));
                        }
                    } else if ("row".equals(reader.getLocalName())) {
                        rows.add(currentRowValues == null ? new String[0]
                                : currentRowValues.toArray(new String[0]));
                        currentRowValues = null;
                    }
                    break;

                default:
                    break;
            }
        }
        reader.close();

        int maxCols = 0;
        for (String[] row : rows) maxCols = Math.max(maxCols, row.length);
        List<String[]> normalized = new ArrayList<>(rows.size());
        for (String[] row : rows) {
            if (row.length == maxCols) {
                normalized.add(row);
            } else {
                String[] padded = new String[maxCols];
                System.arraycopy(row, 0, padded, 0, row.length);
                for (int i = row.length; i < maxCols; i++) padded[i] = "";
                normalized.add(padded);
            }
        }
        return normalized;
    }

    private static String resolveCellValue(String cellType, String raw, List<String> sharedStrings) {
        if (raw == null) return "";
        if ("s".equals(cellType)) {
            try {
                int idx = Integer.parseInt(raw.trim());
                return idx >= 0 && idx < sharedStrings.size() ? sharedStrings.get(idx) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        return raw;
    }

    private static void ensureSize(List<String> list, int size) {
        while (list.size() < size) list.add("");
    }

    /** "C7" -> 2 (chi so cot 0-based, bo qua phan so hang). */
    private static int columnIndexFromRef(String cellRef) {
        int index = 0;
        for (int i = 0; i < cellRef.length(); i++) {
            char c = cellRef.charAt(i);
            if (Character.isDigit(c)) break;
            index = index * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return index - 1;
    }

    private static XMLInputFactory safeFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Chan XXE (XML External Entity) - file nguoi dung upload khong duoc phep tham chieu entity/DTD ngoai.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return factory;
    }
}