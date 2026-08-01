package com.backup.dialect;

public interface SqlDialect {
    String quoteIdentifier(String rawName);
    String escapeStringLiteral(String value);
    String mapJdbcTypeToColumnDefinition(int jdbcType, int columnSize, int decimalDigits);
    String dropTableIfExistsStatement(String tableName);
    default String disableForeignKeyChecksStatement() { return null; }
    default String enableForeignKeyChecksStatement() { return null; }

    /** Xoa HAN cac rang buoc FOREIGN KEY trong schema (khong the khoi phuc lai vi
     *  strategy nay khong dump FK). Can chay truoc DROP/CREATE TABLE de tranh loi
     *  "referenced by a FOREIGN KEY constraint". Tra ve null neu dialect khong ho tro. */
    default String dropAllForeignKeysStatement() { return null; }

    /** Cau lenh bat/tat IDENTITY_INSERT cho 1 bang. Tra ve null neu dialect khong ho tro
     *  (vd Postgres/MySQL dung co che khac cho auto-increment insert). */
    default String setIdentityInsertStatement(String tableName, boolean on) { return null; }

    /** Cau truy van tra ve dung 1 dong neu bang co cot IDENTITY, khong co dong nao neu khong.
     *  Tra ve null neu dialect khong ho tro kiem tra nay. */
    default String identityColumnCheckQuery(String tableName) { return null; }
}