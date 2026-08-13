// src/main/java/com/dao/UserTwoFactorDAO.java
package com.dao;

import com.core.log.AppLogger;
import com.core.log.ErrorCode;
import com.model.TwoFactorMethod;
import com.model.UserTwoFactor;
import com.utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO cho 2 bang UserTwoFactor / UserTwoFactorBackupCodes (xem
 * sql/Migration_2FA.sql). KHONG bao gio SELECT TotpSecretEnc trong cac truy
 * van tra ve UserTwoFactor thong thuong - chi getEncryptedTotpSecret() rieng
 * moi lay, va chi TwoFactorAuthService duoc goi ham do.
 */
public class UserTwoFactorDAO {

    /** Ban ghi 1 backup code con dung duoc trong DB (khong lo plaintext ra ngoai). */
    public static final class BackupCodeRecord {
        public final int backupCodeId;
        public final String codeHash;

        public BackupCodeRecord(int backupCodeId, String codeHash) {
            this.backupCodeId = backupCodeId;
            this.codeHash = codeHash;
        }
    }

    public UserTwoFactor getStatus(int userId) {
        String sql = "SELECT UserID, Method, Enabled, EnrolledAt FROM UserTwoFactor WHERE UserID = ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new UserTwoFactor(userId, TwoFactorMethod.NONE, false, null);
                }
                Timestamp enrolledAt = rs.getTimestamp("EnrolledAt");
                return new UserTwoFactor(
                        userId,
                        TwoFactorMethod.valueOf(rs.getString("Method")),
                        rs.getBoolean("Enabled"),
                        enrolledAt == null ? null : enrolledAt.toLocalDateTime()
                );
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserTwoFactorDAO.getStatus - userId=" + userId, e);
            return new UserTwoFactor(userId, TwoFactorMethod.NONE, false, null);
        }
    }

    /** CHI dung noi bo trong TwoFactorAuthService de xac thuc ma TOTP - khong lo ra UI/DAO khac. */
    public String getEncryptedTotpSecret(int userId) {
        String sql = "SELECT TotpSecretEnc FROM UserTwoFactor WHERE UserID = ? AND Method = 'TOTP'";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("TotpSecretEnc") : null;
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserTwoFactorDAO.getEncryptedTotpSecret - userId=" + userId, e);
            return null;
        }
    }

    /** Upsert va kich hoat phuong thuc TOTP (goi sau khi da xac nhan dung ma lan dau). */
    public boolean enableTotp(int userId, String encryptedSecret) {
        return upsert(userId, TwoFactorMethod.TOTP, encryptedSecret, true);
    }

    /** Upsert va kich hoat phuong thuc EMAIL (khong can luu secret). */
    public boolean enableEmail(int userId) {
        return upsert(userId, TwoFactorMethod.EMAIL, null, true);
    }

    /** Tat 2FA hoan toan (Method ve NONE) - dung khi admin doi phuong thuc hoac tat han. */
    public boolean disable(int userId) {
        return upsert(userId, TwoFactorMethod.NONE, null, false);
    }

    private boolean upsert(int userId, TwoFactorMethod method, String encryptedSecret, boolean enabled) {
        String sql =
                "MERGE UserTwoFactor AS target " +
                "USING (SELECT ? AS UserID) AS src ON target.UserID = src.UserID " +
                "WHEN MATCHED THEN UPDATE SET Method = ?, TotpSecretEnc = ?, Enabled = ?, " +
                "    EnrolledAt = CASE WHEN ? = 1 THEN GETDATE() ELSE target.EnrolledAt END, UpdatedAt = GETDATE() " +
                "WHEN NOT MATCHED THEN INSERT (UserID, Method, TotpSecretEnc, Enabled, EnrolledAt, UpdatedAt) " +
                "    VALUES (?, ?, ?, ?, CASE WHEN ? = 1 THEN GETDATE() ELSE NULL END, GETDATE());";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.setString(2, method.name());
            ps.setString(3, encryptedSecret);
            ps.setBoolean(4, enabled);
            ps.setBoolean(5, enabled);
            ps.setInt(6, userId);
            ps.setString(7, method.name());
            ps.setString(8, encryptedSecret);
            ps.setBoolean(9, enabled);
            ps.setBoolean(10, enabled);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserTwoFactorDAO.upsert - userId=" + userId, e);
            return false;
        }
    }

    /** Xoa toan bo backup code cu va luu bo 10 ma moi (da hash). */
    public boolean replaceBackupCodes(int userId, List<String> hashedCodes) {
        String deleteSql = "DELETE FROM UserTwoFactorBackupCodes WHERE UserID = ?";
        String insertSql = "INSERT INTO UserTwoFactorBackupCodes (UserID, CodeHash) VALUES (?, ?)";
        try (Connection con = DBConnection.getConnection()) {
            boolean prevAutoCommit = con.getAutoCommit();
            con.setAutoCommit(false);
            try (PreparedStatement del = con.prepareStatement(deleteSql)) {
                del.setInt(1, userId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = con.prepareStatement(insertSql)) {
                for (String hash : hashedCodes) {
                    ins.setInt(1, userId);
                    ins.setString(2, hash);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            con.commit();
            con.setAutoCommit(prevAutoCommit);
            return true;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_INSERT_FAIL, "UserTwoFactorDAO.replaceBackupCodes - userId=" + userId, e);
            return false;
        }
    }

    public List<BackupCodeRecord> findUnusedBackupCodes(int userId) {
        String sql = "SELECT BackupCodeID, CodeHash FROM UserTwoFactorBackupCodes WHERE UserID = ? AND UsedAt IS NULL";
        List<BackupCodeRecord> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new BackupCodeRecord(rs.getInt("BackupCodeID"), rs.getString("CodeHash")));
                }
            }
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_QUERY_FAIL, "UserTwoFactorDAO.findUnusedBackupCodes - userId=" + userId, e);
        }
        return result;
    }

    public int countUnusedBackupCodes(int userId) {
        return findUnusedBackupCodes(userId).size();
    }

    public boolean markBackupCodeUsed(int backupCodeId) {
        String sql = "UPDATE UserTwoFactorBackupCodes SET UsedAt = GETDATE() WHERE BackupCodeID = ? AND UsedAt IS NULL";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, backupCodeId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            AppLogger.getInstance().error(ErrorCode.DB_UPDATE_FAIL, "UserTwoFactorDAO.markBackupCodeUsed - id=" + backupCodeId, e);
            return false;
        }
    }
}