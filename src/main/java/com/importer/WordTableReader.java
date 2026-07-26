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
 * Doc du lieu tu BANG (table) DAU TIEN trong file Word (.docx) - dung cho truong
 * hop nguoi dung chuan bi du lieu nhap dang bang trong Word thay vi Excel.
 * Khong phu thuoc thu vien ngoai, chi dung StAX co san trong JDK.
 * <p>
 * Gioi han: chi doc bang dau tien tren cung trong tai lieu; noi dung bang long
 * (nested table) ben trong 1 o se duoc gop chung vao van ban cua o do thay vi
 * tach rieng - du cho muc dich nhap du lieu dang bang don gian (header + rows).
 */
public final class WordTableReader {

    private WordTableReader() {}

    public static List<String[]> readRows(File docxFile) throws IOException {
        try (ZipFile zip = new ZipFile(docxFile)) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) {
                throw new IOException("File Word không hợp lệ (thiếu word/document.xml).");
            }
            try (InputStream is = zip.getInputStream(entry)) {
                return parseFirstTable(is);
            }
        } catch (XMLStreamException e) {
            throw new IOException("File Word không đúng định dạng hoặc bị hỏng: " + e.getMessage(), e);
        }
    }

    private static List<String[]> parseFirstTable(InputStream is) throws XMLStreamException {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        XMLStreamReader reader = factory.createXMLStreamReader(is);

        List<String[]> rows = new ArrayList<>();
        boolean foundTable = false; // dung lai sau khi doc xong bang dau tien
        boolean inTargetTable = false;
        int tableDepth = 0;

        List<String> currentRow = null;
        StringBuilder currentCell = null;
        boolean inCell = false;

        while (reader.hasNext()) {
            int event = reader.next();
            String local;
            switch (event) {
                case XMLStreamConstants.START_ELEMENT:
                    local = reader.getLocalName();
                    if ("tbl".equals(local)) {
                        tableDepth++;
                        if (!foundTable && tableDepth == 1) inTargetTable = true;
                    } else if (inTargetTable && tableDepth == 1 && "tr".equals(local)) {
                        currentRow = new ArrayList<>();
                    } else if (inTargetTable && tableDepth == 1 && "tc".equals(local)) {
                        inCell = true;
                        currentCell = new StringBuilder();
                    } else if (inTargetTable && "t".equals(local) && inCell) {
                        String text = reader.getElementText();
                        currentCell.append(text);
                    } else if (inTargetTable && inCell && "br".equals(local)) {
                        currentCell.append(' ');
                    }
                    break;

                case XMLStreamConstants.END_ELEMENT:
                    local = reader.getLocalName();
                    if ("tc".equals(local) && inTargetTable && tableDepth == 1) {
                        inCell = false;
                        if (currentRow != null) currentRow.add(currentCell == null ? "" : currentCell.toString().trim());
                    } else if ("tr".equals(local) && inTargetTable && tableDepth == 1) {
                        if (currentRow != null) rows.add(currentRow.toArray(new String[0]));
                        currentRow = null;
                    } else if ("tbl".equals(local)) {
                        tableDepth--;
                        if (tableDepth == 0 && inTargetTable) {
                            inTargetTable = false;
                            foundTable = true;
                        }
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
}