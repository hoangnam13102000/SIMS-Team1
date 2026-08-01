package com.backup;

import com.security.CryptoUtil;

import javax.crypto.SecretKey;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kiem tra "canh bao lech schema" TRUOC khi thuc su restore mot file backup
 * dang jdbc-sql-dump (co the boc .enc). Vi JdbcSqlDumpBackupStrategy khi
 * restore se DROP TABLE + CREATE TABLE lai dung schema DA CHUP tai thoi
 * diem backup, restore mot file backup CU HON lan ALTER TABLE gan nhat se
 * AM THAM xoa mat cac cot moi ma KHONG bao loi gi ca (xem su co thuc te
 * ngay 18-19/07/2026: restore ve ban 17/07 lam mat 4 cot hash-chain cua
 * ActivityLogs, khien AuditLogIntegrityCheck bao UNKNOWN vi thieu cot
 * PrevHash/RowHash). Class nay phat hien truoc tinh huong do de canh bao
 * ro rang truoc khi nguoi dung bam xac nhan restore.
 * <p>
 * Chi hoat dong voi backup dang jdbc-sql-dump (dang text SQL, tu doc duoc
 * cac dong CREATE TABLE). Backup dang native (SqlServerNativeBackupStrategy,
 * file .bak nhi phan) khong "peek" truoc duoc nen bo qua, tra ve danh sach
 * rong (khong chan, khong canh bao - giu hanh vi cu).
 */
public final class BackupSchemaGuard {

    private static final Pattern CREATE_TABLE_LINE =
            Pattern.compile("^CREATE TABLE \\[?(\\w+)]?\\s*\\((.*)\\);\\s*$", Pattern.CASE_INSENSITIVE);

    private BackupSchemaGuard() {}

    public static final class TableWarning {
        public final String table;
        public final List<String> missingColumns;

        public TableWarning(String table, List<String> missingColumns) {
            this.table = table;
            this.missingColumns = missingColumns;
        }
    }

    /**
     * @param backupFile         file .sql hoac .sql.enc can kiem tra
     * @param passphraseSupplier dung de giai ma neu file dang .enc (co the tra ve null neu chua cau hinh)
     * @param liveConnection     ket noi toi DB DANG CHAY HIEN TAI de so sanh - CALLER tu quan ly dong ket noi nay
     * @return danh sach bang se bi MAT COT neu restore file nay - RONG neu an toan hoac khong the kiem tra
     *         (vd backup dang native, khong doc/giai ma duoc, hoac loi khi truy van schema hien tai).
     *         Khong bao gio nem exception - kiem tra nay chi mang tinh CANH BAO THEM,
     *         khong duoc phep chan luong restore that su neu ban than no gap loi.
     */
    public static List<TableWarning> checkMissingColumns(File backupFile,
                                                          Supplier<String> passphraseSupplier,
                                                          Connection liveConnection) {
        String sqlText;
        try {
            sqlText = readAsPlainText(backupFile, passphraseSupplier);
        } catch (Exception e) {
            return List.of();
        }
        if (sqlText == null || !sqlText.contains("Generic JDBC SQL dump")) {
            return List.of();
        }

        Map<String, Set<String>> backupSchema = parseCreateTableColumns(sqlText);
        List<TableWarning> warnings = new ArrayList<>();

        try {
            DatabaseMetaData meta = liveConnection.getMetaData();
            for (Map.Entry<String, Set<String>> entry : backupSchema.entrySet()) {
                String table = entry.getKey();
                Set<String> backupColumns = entry.getValue();
                Set<String> liveColumns = new LinkedHashSet<>();
                try (ResultSet rs = meta.getColumns(liveConnection.getCatalog(), null, table, "%")) {
                    while (rs.next()) liveColumns.add(rs.getString("COLUMN_NAME").toUpperCase());
                }
                if (liveColumns.isEmpty()) continue;

                List<String> missing = new ArrayList<>();
                for (String liveCol : liveColumns) {
                    if (!backupColumns.contains(liveCol)) missing.add(liveCol);
                }
                if (!missing.isEmpty()) warnings.add(new TableWarning(table, missing));
            }
        } catch (SQLException e) {
            return List.of();
        }
        return warnings;
    }

    private static String readAsPlainText(File backupFile, Supplier<String> passphraseSupplier) throws Exception {
        byte[] fileBytes = Files.readAllBytes(backupFile.toPath());
        if (!backupFile.getName().endsWith(".enc")) {
            return new String(fileBytes, StandardCharsets.UTF_8);
        }
        String passphrase = passphraseSupplier.get();
        if (passphrase == null || passphrase.isBlank()) return null;
        if (fileBytes.length <= CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES) return null;

        byte[] salt = new byte[CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES];
        byte[] cipherPayload = new byte[fileBytes.length - CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES];
        System.arraycopy(fileBytes, 0, salt, 0, salt.length);
        System.arraycopy(fileBytes, salt.length, cipherPayload, 0, cipherPayload.length);

        SecretKey key = CryptoUtil.deriveKeyFromPassphrase(passphrase, salt);
        byte[] plainBytes = CryptoUtil.decryptBytes(cipherPayload, key);
        return new String(plainBytes, StandardCharsets.UTF_8);
    }

    private static Map<String, Set<String>> parseCreateTableColumns(String sqlText) {
        Map<String, Set<String>> result = new LinkedHashMap<>();
        for (String line : sqlText.split("\r?\n")) {
            Matcher m = CREATE_TABLE_LINE.matcher(line.trim());
            if (!m.matches()) continue;
            String table = m.group(1);
            Set<String> columns = new LinkedHashSet<>();
            for (String part : splitTopLevel(m.group(2))) {
                String trimmed = part.trim();
                if (trimmed.toUpperCase().startsWith("PRIMARY KEY")) continue;
                String colName = trimmed.split("\\s+")[0].replace("[", "").replace("]", "");
                columns.add(colName.toUpperCase());
            }
            result.put(table, columns);
        }
        return result;
    }

    /** Tach chuoi theo dau phay o CAP NGOAI CUNG (bo qua dau phay nam trong ngoac, vd NVARCHAR(MAX)). */
    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '(') depth++;
            if (c == ')') depth--;
            if (c == ',' && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts;
    }
}