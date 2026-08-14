package com.backup.dialect;

import java.sql.Types;

public class SqlServerDialect implements SqlDialect {

    @Override
    public String quoteIdentifier(String rawName) {
        return "[" + rawName.replace("]", "]]") + "]";
    }

    @Override
    public String escapeStringLiteral(String value) {
        return value.replace("'", "''");
    }

    @Override
    public String mapJdbcTypeToColumnDefinition(int jdbcType, int columnSize, int decimalDigits) {
        switch (jdbcType) {
            case Types.BIT: case Types.BOOLEAN: return "BIT";
            case Types.TINYINT: return "TINYINT";
            case Types.SMALLINT: return "SMALLINT";
            case Types.INTEGER: return "INT";
            case Types.BIGINT: return "BIGINT";
            case Types.DECIMAL: case Types.NUMERIC:
                return "DECIMAL(" + Math.max(columnSize, 1) + "," + Math.max(decimalDigits, 0) + ")";
            case Types.REAL: return "REAL";
            case Types.FLOAT: case Types.DOUBLE: return "FLOAT";
            case Types.DATE: return "DATE";
            case Types.TIME: return "TIME";
            case Types.TIMESTAMP: return "DATETIME2";
            case Types.CHAR: return "CHAR(" + Math.max(columnSize, 1) + ")";
            case Types.NCHAR: return "NCHAR(" + Math.max(columnSize, 1) + ")";
            case Types.VARCHAR:
                return (columnSize > 0 && columnSize <= 8000) ? "VARCHAR(" + columnSize + ")" : "VARCHAR(MAX)";
            case Types.NVARCHAR: case Types.LONGNVARCHAR:
                return (columnSize > 0 && columnSize <= 4000) ? "NVARCHAR(" + columnSize + ")" : "NVARCHAR(MAX)";
            case Types.LONGVARCHAR: return "VARCHAR(MAX)";
            case Types.VARBINARY:
                return (columnSize > 0 && columnSize <= 8000) ? "VARBINARY(" + columnSize + ")" : "VARBINARY(MAX)";
            case Types.BINARY: return "BINARY(" + Math.max(columnSize, 1) + ")";
            case Types.LONGVARBINARY: case Types.BLOB: return "VARBINARY(MAX)";
            default: return "NVARCHAR(MAX)";
        }
    }

    @Override
    public String dropTableIfExistsStatement(String tableName) {
        return "IF OBJECT_ID(N'" + tableName.replace("'", "''") + "', N'U') IS NOT NULL DROP TABLE "
                + quoteIdentifier(tableName);
    }

    @Override
    public String disableForeignKeyChecksStatement() {
        return "EXEC sp_MSforeachtable \"ALTER TABLE ? NOCHECK CONSTRAINT ALL\"";
    }

    @Override
    public String enableForeignKeyChecksStatement() {
        return "EXEC sp_MSforeachtable \"ALTER TABLE ? WITH CHECK CHECK CONSTRAINT ALL\"";
    }

    @Override
    public String dropAllForeignKeysStatement() {
        return "DECLARE @sql NVARCHAR(MAX) = N''; "
                + "SELECT @sql += 'ALTER TABLE ' + QUOTENAME(SCHEMA_NAME(t.schema_id)) + '.' + QUOTENAME(t.name) "
                + "+ ' DROP CONSTRAINT ' + QUOTENAME(fk.name) + ';' "
                + "FROM sys.foreign_keys fk INNER JOIN sys.tables t ON fk.parent_object_id = t.object_id; "
                + "EXEC sp_executesql @sql;";
    }

    @Override
    public String setIdentityInsertStatement(String tableName, boolean on) {
        return "SET IDENTITY_INSERT " + quoteIdentifier(tableName) + (on ? " ON" : " OFF");
    }

    @Override
    public String identityColumnCheckQuery(String tableName) {
        return "SELECT 1 FROM sys.identity_columns WHERE object_id = OBJECT_ID('"
                + tableName.replace("'", "''") + "')";
    }
}
