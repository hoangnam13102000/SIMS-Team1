package com.backup.dialect;

import java.sql.Types;

public class AnsiSqlDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String rawName) {
        return "\"" + rawName.replace("\"", "\"\"") + "\"";
    }

    @Override
    public String escapeStringLiteral(String value) {
        return value.replace("'", "''");
    }

    @Override
    public String mapJdbcTypeToColumnDefinition(int jdbcType, int columnSize, int decimalDigits) {
        switch (jdbcType) {
            case Types.BIT: case Types.BOOLEAN: return "BOOLEAN";
            case Types.TINYINT: case Types.SMALLINT: return "SMALLINT";
            case Types.INTEGER: return "INTEGER";
            case Types.BIGINT: return "BIGINT";
            case Types.DECIMAL: case Types.NUMERIC:
                return "DECIMAL(" + Math.max(columnSize, 1) + "," + Math.max(decimalDigits, 0) + ")";
            case Types.REAL: case Types.FLOAT: return "REAL";
            case Types.DOUBLE: return "DOUBLE PRECISION";
            case Types.DATE: return "DATE";
            case Types.TIME: return "TIME";
            case Types.TIMESTAMP: return "TIMESTAMP";
            case Types.CHAR: case Types.NCHAR: return "CHAR(" + Math.max(columnSize, 1) + ")";
            case Types.VARCHAR: case Types.NVARCHAR: case Types.LONGVARCHAR: case Types.LONGNVARCHAR:
                return (columnSize > 0 && columnSize <= 8000) ? "VARCHAR(" + columnSize + ")" : "TEXT";
            case Types.VARBINARY: case Types.BINARY: case Types.LONGVARBINARY: case Types.BLOB:
                return "BLOB";
            default: return "TEXT";
        }
    }

    @Override
    public String dropTableIfExistsStatement(String tableName) {
        return "DROP TABLE IF EXISTS " + quoteIdentifier(tableName);
    }
}