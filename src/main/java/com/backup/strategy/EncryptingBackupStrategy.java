package com.backup.strategy;

import com.backup.BackupException;
import com.backup.BackupStrategy;
import com.backup.RestoreStrategy;
import com.security.CryptoUtil;

import javax.crypto.SecretKey;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.function.Supplier;

/**
 * Decorator boc quanh 1 BackupStrategy (dong thoi phai la RestoreStrategy)
 * bat ky, tu dong MA HOA file ngay sau khi backup xong va GIAI MA truoc khi
 * restore. Khong doi logic backup/restore goc cua strategy duoc boc.
 *
 * Thuat toan: AES-256-GCM, khoa dan xuat tu 1 passphrase bang PBKDF2
 * (200.000 vong lap) - xem CryptoUtil.deriveKeyFromPassphrase().
 *
 * Dinh dang file ket qua (.enc):
 *   salt (16 byte) + IV (12 byte) + cipherText + authTag (16 byte)
 * Salt duoc sinh ngau nhien MOI LAN backup va luu ngay trong file, nen
 * khong can luu rieng o dau khac - nhung passphrase thi KHONG duoc luu
 * trong file, chi ton tai qua Supplier truyen vao luc chay.
 *
 * CANH BAO: neu mat passphrase, KHONG THE giai ma lai duoc du lieu backup -
 * day la thiet ke co chu dich, giong moi he thong ma hoa dung passphrase.
 * Hay luu passphrase o noi an toan, tach biet voi may/nguoi quan ly file
 * backup.
 */
public class EncryptingBackupStrategy implements BackupStrategy, RestoreStrategy {

    private static final String ENCRYPTED_EXTENSION_SUFFIX = ".enc";

    private final BackupStrategy delegate;
    private final RestoreStrategy delegateRestore;
    private final Supplier<String> passphraseSupplier;

    public EncryptingBackupStrategy(BackupStrategy delegate, Supplier<String> passphraseSupplier) {
        if (!(delegate instanceof RestoreStrategy)) {
            throw new IllegalArgumentException(
                    "EncryptingBackupStrategy chi boc duoc strategy co ho tro restore (RestoreStrategy). "
                            + "Strategy '" + delegate.getName() + "' khong thoa dieu kien nay.");
        }
        this.delegate = delegate;
        this.delegateRestore = (RestoreStrategy) delegate;
        this.passphraseSupplier = passphraseSupplier;
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getFileExtension() {
        return delegate.getFileExtension() + ENCRYPTED_EXTENSION_SUFFIX;
    }

    @Override
    public void backupTo(File destinationFile) throws BackupException {
        String passphrase = requirePassphrase();
        File plainTemp = plainTempFileFor(destinationFile);
        try {
            delegate.backupTo(plainTemp);

            byte[] plainBytes = Files.readAllBytes(plainTemp.toPath());
            byte[] salt = CryptoUtil.generateSalt();
            SecretKey key = CryptoUtil.deriveKeyFromPassphrase(passphrase, salt);
            byte[] cipherPayload = CryptoUtil.encryptBytes(plainBytes, key);

            byte[] out = new byte[salt.length + cipherPayload.length];
            System.arraycopy(salt, 0, out, 0, salt.length);
            System.arraycopy(cipherPayload, 0, out, salt.length, cipherPayload.length);

            Files.write(destinationFile.toPath(), out);
        } catch (IOException e) {
            throw new BackupException("Loi doc/ghi file khi ma hoa backup: " + e.getMessage(), e);
        } finally {
            deleteQuietly(plainTemp);
        }
    }

    @Override
    public void restoreFrom(File backupFile) throws BackupException {
        String passphrase = requirePassphrase();
        File plainTemp = plainTempFileFor(backupFile);
        try {
            byte[] fileBytes = Files.readAllBytes(backupFile.toPath());
            if (fileBytes.length <= CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES) {
                throw new BackupException(
                        "File backup ma hoa qua ngan hoac bi hong: " + backupFile.getName());
            }
            byte[] salt = new byte[CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES];
            byte[] cipherPayload = new byte[fileBytes.length - CryptoUtil.PASSPHRASE_SALT_LENGTH_BYTES];
            System.arraycopy(fileBytes, 0, salt, 0, salt.length);
            System.arraycopy(fileBytes, salt.length, cipherPayload, 0, cipherPayload.length);

            SecretKey key = CryptoUtil.deriveKeyFromPassphrase(passphrase, salt);
            byte[] plainBytes;
            try {
                plainBytes = CryptoUtil.decryptBytes(cipherPayload, key);
            } catch (IllegalStateException e) {
                throw new BackupException(
                        "Khong giai ma duoc file backup '" + backupFile.getName()
                                + "': sai passphrase hoac file da bi hong/sua doi.", e);
            }

            Files.write(plainTemp.toPath(), plainBytes);
            delegateRestore.restoreFrom(plainTemp);
        } catch (IOException e) {
            throw new BackupException("Loi doc/ghi file khi giai ma backup: " + e.getMessage(), e);
        } finally {
            deleteQuietly(plainTemp);
        }
    }

    private String requirePassphrase() throws BackupException {
        String passphrase = passphraseSupplier.get();
        if (passphrase == null || passphrase.isBlank()) {
            throw new BackupException(
                    "Chua cau hinh passphrase ma hoa backup (BACKUP_ENCRYPTION_PASSPHRASE trong secure-config.enc).");
        }
        return passphrase;
    }

    private File plainTempFileFor(File reference) {
        return new File(reference.getParentFile(), reference.getName() + ".plain-tmp");
    }

    /**
     * Xoa file tam chua du lieu chua ma hoa. Luu y: File.delete() thong
     * thuong KHONG ghi de noi dung truoc khi xoa (khong phai secure wipe) -
     * du lieu vat ly co the con luu tren dia cho toi khi bi ghi de. Chap
     * nhan duoc voi rui ro con lai la nguoi co quyen truy cap thap (dia da
     * bi chiem) - phong thu chinh van la ma hoa file backup ton tai lau
     * dai.
     */
    private void deleteQuietly(File file) {
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}