package com.backup.dialect;

import java.sql.Types;

/** MySQL 8.x dialect used by the JDBC backup/restore subsystem. */
public class MySqlDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String rawName) {
        return "`" + rawName.replace("`", "``") + "`";
    }

    @Override
    public String escapeStringLiteral(String value) {
        return value.replace("\\", "\\\\").replace("'", "''");
    }

    @Override
    public String mapJdbcTypeToColumnDefinition(int jdbcType, int columnSize, int decimalDigits) {
        switch (jdbcType) {
            case Types.BIT: case Types.BOOLEAN: return "TINYINT(1)";
            case Types.TINYINT: return "TINYINT";
            case Types.SMALLINT: return "SMALLINT";
            case Types.INTEGER: return "INT";
            case Types.BIGINT: return "BIGINT";
            case Types.DECIMAL: case Types.NUMERIC:
                return "DECIMAL(" + Math.max(columnSize, 1) + "," + Math.max(decimalDigits, 0) + ")";
            case Types.REAL: case Types.FLOAT: return "FLOAT";
            case Types.DOUBLE: return "DOUBLE";
            case Types.DATE: return "DATE";
            case Types.TIME: return "TIME";
            case Types.TIMESTAMP: case Types.TIMESTAMP_WITH_TIMEZONE: return "DATETIME";
            case Types.CHAR: case Types.NCHAR: return "CHAR(" + Math.max(columnSize, 1) + ")";
            case Types.VARCHAR: case Types.NVARCHAR:
                return columnSize > 0 && columnSize <= 16383 ? "VARCHAR(" + columnSize + ")" : "LONGTEXT";
            case Types.LONGVARCHAR: case Types.LONGNVARCHAR: case Types.CLOB: return "LONGTEXT";
            case Types.VARBINARY: case Types.BINARY: case Types.LONGVARBINARY: case Types.BLOB:
                return "LONGBLOB";
            default: return "LONGTEXT";
        }
    }

    @Override
    public String dropTableIfExistsStatement(String tableName) {
        return "DROP TABLE IF EXISTS " + quoteIdentifier(tableName) + ";";
    }

    @Override
    public String disableForeignKeyChecksStatement() {
        return "SET FOREIGN_KEY_CHECKS = 0";
    }

    @Override
    public String enableForeignKeyChecksStatement() {
        return "SET FOREIGN_KEY_CHECKS = 1";
    }
}
