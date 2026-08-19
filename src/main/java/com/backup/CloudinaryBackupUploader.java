package com.backup;

import com.incident.IncidentLogger;
import com.incident.IncidentType;
import com.service.media.CloudinaryService;
import com.service.media.CloudinaryUploadException;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * BackupListener tự động tải file backup vừa tạo thành công lên Cloudinary,
 * để có thêm 1 bản sao NGOÀI local (phòng khi ổ đĩa / máy chủ local bị hỏng).
 *
 * Chạy BẤT ĐỒNG BỘ trên thread riêng (không chặn UI, không làm chậm luồng
 * backup local đang chạy trên SwingWorker). Nếu upload lỗi, backup local vẫn
 * được coi là THÀNH CÔNG — chỉ ghi Incident cảnh báo, KHÔNG throw ngược lại
 * BackupManager, vì mất mạng/Cloudinary không nên làm hỏng quy trình backup
 * đã chạy xong tại chỗ.
 *
 * Link secure_url trả về từ Cloudinary được lưu vào 1 file sidecar
 * "<tên file backup>.cloudinary.url" nằm cạnh file backup gốc, để UI
 * (BackupRecoveryPanel) có thể đọc lại và hiển thị trạng thái/đường dẫn.
 */
public class CloudinaryBackupUploader implements BackupListener {

    /** Callback để UI (VD BackupRecoveryPanel) biết khi nào 1 lần upload Cloudinary đã xong, để tự refresh bảng. */
    public interface UploadFinishedListener {
        void onCloudUploadFinished(File backupFile, boolean success);
    }

    private final List<UploadFinishedListener> uploadListeners = new CopyOnWriteArrayList<>();

    public void addUploadFinishedListener(UploadFinishedListener listener) {
        uploadListeners.add(listener);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "backup-cloud-uploader");
        t.setDaemon(true);
        return t;
    });

    @Override
    public void onBackupSucceeded(BackupResult result) {
        File file = result.getFile();
        if (file == null || !file.isFile()) return;

        executor.submit(() -> uploadInBackground(file));
    }

    private void uploadInBackground(File file) {
        boolean success = false;
        try {
            String secureUrl = CloudinaryService.getInstance().uploadBackupFile(file);
            writeSidecarUrl(file, secureUrl);
            IncidentLogger.getInstance().low(IncidentType.BACKUP_CLOUD_UPLOAD_SUCCEEDED,
                    "CloudinaryBackupUploader",
                    "Da tai file backup " + file.getName() + " len Cloudinary -> " + secureUrl);
            success = true;
        } catch (CloudinaryUploadException e) {
            IncidentLogger.getInstance().high(IncidentType.BACKUP_CLOUD_UPLOAD_FAILED,
                    "CloudinaryBackupUploader",
                    "Khong the tai file backup " + file.getName() + " len Cloudinary: " + e.getMessage(), e);
        } catch (Exception e) {
            IncidentLogger.getInstance().high(IncidentType.BACKUP_CLOUD_UPLOAD_FAILED,
                    "CloudinaryBackupUploader",
                    "Loi khong xac dinh khi tai file backup " + file.getName() + " len Cloudinary: "
                            + e.getMessage(), e);
        } finally {
            boolean finalSuccess = success;
            for (UploadFinishedListener listener : uploadListeners) {
                try {
                    listener.onCloudUploadFinished(file, finalSuccess);
                } catch (Exception ignored) {
                    // Khong de 1 listener loi lam hong cac listener khac.
                }
            }
        }
    }

    private void writeSidecarUrl(File backupFile, String secureUrl) {
        File sidecar = sidecarFileFor(backupFile);
        try (PrintWriter writer = new PrintWriter(sidecar, StandardCharsets.UTF_8)) {
            writer.print(secureUrl);
        } catch (IOException ignored) {
            // Không ghi được sidecar không ảnh hưởng tới việc backup đã upload thành công lên Cloudinary.
        }
    }

    /** File .cloudinary.url đi kèm 1 file backup, dùng để tra lại link đã upload. */
    public static File sidecarFileFor(File backupFile) {
        return new File(backupFile.getParentFile(), backupFile.getName() + ".cloudinary.url");
    }

    /** Đọc link Cloudinary đã lưu cho 1 file backup, null nếu chưa từng upload hoặc upload thất bại. */
    public static String readUploadedUrl(File backupFile) {
        File sidecar = sidecarFileFor(backupFile);
        if (!sidecar.isFile()) return null;
        try {
            byte[] bytes = java.nio.file.Files.readAllBytes(sidecar.toPath());
            String url = new String(bytes, StandardCharsets.UTF_8).trim();
            return url.isEmpty() ? null : url;
        } catch (IOException e) {
            return null;
        }
    }

    public void shutdown() {
        executor.shutdown();
    }
}