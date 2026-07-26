package com.importer;

/**
 * Duoc panel CRUD cu the (vd PhonePanel) trien khai de bien 1 dong du lieu tho
 * (mang chuoi tu file Excel/Word) thanh 1 ban ghi that trong CSDL.
 */
@FunctionalInterface
public interface RowImportHandler {

    /**
     * @param cells      gia tri cac cot cua dong (da bo qua dong header)
     * @param rowNumber  so thu tu dong hien thi cho nguoi dung (tinh ca header, bat dau tu 1)
     */
    ImportRowResult importRow(String[] cells, int rowNumber);
}