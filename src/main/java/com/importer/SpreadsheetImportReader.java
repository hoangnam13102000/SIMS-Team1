package com.importer;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** Diem vao chung: doc file .xlsx hoac .docx thanh danh sach cac dong (String[]). */
public final class SpreadsheetImportReader {

    private SpreadsheetImportReader() {}

    public static List<String[]> read(File file) throws IOException {
        String name = file.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx")) {
            return ExcelSheetReader.readRows(file);
        }
        if (name.endsWith(".docx")) {
            return WordTableReader.readRows(file);
        }
        throw new IOException("Định dạng file không được hỗ trợ. Chỉ chấp nhận .xlsx hoặc .docx.");
    }

    public static boolean isSupported(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".xlsx") || name.endsWith(".docx");
    }
}