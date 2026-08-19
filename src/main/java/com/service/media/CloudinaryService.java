package com.service.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.security.AppConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.UUID;

/**
 * Dịch vụ tải lên Cloudinary bằng Unsigned Upload Preset.
 *
 * Hỗ trợ 2 loại upload:
 *   - uploadProductImage / uploadAvatar: dùng CLOUDINARY_UPLOAD_PRESET (ảnh)
 *   - uploadFile / uploadDocument: dùng CLOUDINARY_FILE_UPLOAD_PRESET (file tài liệu)
 *
 * Ưu tiên đọc cấu hình theo thứ tự:
 *   1. Biến môi trường OS (System.getenv)
 *   2. System property (-Dkey=value)
 *   3. AppConfig (secure-config.enc)
 */
public final class CloudinaryService {
    // Giới hạn kích thước
    private static final long MAX_IMAGE_SIZE = 5L * 1024L * 1024L;   // 5MB cho ảnh
    private static final long MAX_FILE_SIZE = 25L * 1024L * 1024L;    // 25MB cho file tài liệu
    private static final long MAX_BACKUP_SIZE = 100L * 1024L * 1024L; // 100MB cho file backup DB

    // Các key cấu hình
    private static final String CLOUD_NAME_ENV = "CLOUDINARY_CLOUD_NAME";
    private static final String IMAGE_UPLOAD_PRESET_ENV = "CLOUDINARY_UPLOAD_PRESET";
    private static final String FILE_UPLOAD_PRESET_ENV = "CLOUDINARY_FILE_UPLOAD_PRESET";
    private static final String BACKUP_UPLOAD_PRESET_ENV = "CLOUDINARY_BACKUP_UPLOAD_PRESET";

    // Định dạng ảnh được phép
    private static final String[] ALLOWED_IMAGE_EXTENSIONS = 
            {".jpg", ".jpeg", ".png", ".gif", ".webp"};

    // Định dạng file tài liệu được phép
    private static final String[] ALLOWED_FILE_EXTENSIONS = {
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".csv", ".zip", ".rar", ".7z", ".mp3", ".mp4"
    };

    // Định dạng file backup DB được phép (bao gồm file đã mã hóa .enc từ EncryptingBackupStrategy)
    private static final String[] ALLOWED_BACKUP_EXTENSIONS = {
            ".sql", ".bak", ".gz", ".zip", ".enc"
    };

    private static final CloudinaryService INSTANCE = new CloudinaryService();
    private final HttpClient httpClient;

    private CloudinaryService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public static CloudinaryService getInstance() {
        return INSTANCE;
    }

    // ================================================================
    // UPLOAD ẢNH (sản phẩm, avatar)
    // ================================================================

    /**
     * Tải ảnh sản phẩm lên Cloudinary.
     * Dùng preset CLOUDINARY_UPLOAD_PRESET (sims_unsigned).
     */
    public String uploadProductImage(File imageFile) throws CloudinaryUploadException {
        return uploadImage(imageFile);
    }

    /**
     * Tải avatar khách hàng/nhân viên lên Cloudinary.
     * Dùng preset CLOUDINARY_UPLOAD_PRESET (sims_unsigned).
     */
    public String uploadAvatar(File imageFile) throws CloudinaryUploadException {
        return uploadImage(imageFile);
    }

    private String uploadImage(File imageFile) throws CloudinaryUploadException {
        validateImageFile(imageFile);

        String cloudName = getRequiredConfig(CLOUD_NAME_ENV,
                "Thiếu CLOUDINARY_CLOUD_NAME. Hãy cấu hình trong secure-config.enc hoặc biến môi trường rồi mở lại Eclipse.");
        String uploadPreset = getRequiredConfig(IMAGE_UPLOAD_PRESET_ENV,
                "Thiếu CLOUDINARY_UPLOAD_PRESET. Hãy tạo Unsigned Upload Preset cho ảnh trên Cloudinary.");

        if (!cloudName.matches("[A-Za-z0-9_-]+")) {
            throw new CloudinaryUploadException("CLOUDINARY_CLOUD_NAME không hợp lệ.");
        }

        String boundary = "----SIMSCloudinary" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody;
        try {
            requestBody = createMultipartBody(imageFile, uploadPreset, boundary, true);
        } catch (IOException e) {
            throw new CloudinaryUploadException("Không đọc được file ảnh đã chọn.", e);
        }

        // Upload vào thư mục image
        URI uploadUri = URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/image/upload");
        return executeUpload(uploadUri, requestBody, boundary, 60);
    }

    // ================================================================
    // UPLOAD FILE TÀI LIỆU (PDF, DOC, XLS, ZIP...)
    // ================================================================

    /**
     * Tải file tài liệu lên Cloudinary.
     * Dùng preset CLOUDINARY_FILE_UPLOAD_PRESET (sims_files_unsigned).
     * Hỗ trợ: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, CSV, ZIP, RAR, 7Z, MP3, MP4.
     */
    public String uploadFile(File file) throws CloudinaryUploadException {
        return uploadFile(file, FILE_UPLOAD_PRESET_ENV);
    }

    /**
     * Tải file tài liệu lên Cloudinary với preset cấu hình tùy chỉnh.
     */
    public String uploadFile(File file, String presetConfigKey) throws CloudinaryUploadException {
        validateGenericFile(file);

        String cloudName = getRequiredConfig(CLOUD_NAME_ENV,
                "Thiếu CLOUDINARY_CLOUD_NAME. Hãy cấu hình trong secure-config.enc hoặc biến môi trường rồi mở lại Eclipse.");
        String uploadPreset = getRequiredConfig(presetConfigKey,
                "Thiếu " + presetConfigKey + ". Hãy tạo và cấu hình Upload Preset cho file trên Cloudinary.");

        if (!cloudName.matches("[A-Za-z0-9_-]+")) {
            throw new CloudinaryUploadException("CLOUDINARY_CLOUD_NAME không hợp lệ.");
        }

        String boundary = "----SIMSCloudinaryFile" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody;
        try {
            requestBody = createMultipartBody(file, uploadPreset, boundary, false);
        } catch (IOException e) {
            throw new CloudinaryUploadException("Không đọc được file đã chọn.", e);
        }

        // Dùng "auto" để Cloudinary tự động nhận diện resource type
        URI uploadUri = URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/auto/upload");
        return executeUpload(uploadUri, requestBody, boundary, 120);
    }

    // ================================================================
    // UPLOAD FILE BACKUP DATABASE (.sql, .bak, .gz, .enc...)
    // ================================================================

    /**
     * Tải file backup database lên Cloudinary để lưu bản sao ngoài local.
     * Dùng preset riêng CLOUDINARY_BACKUP_UPLOAD_PRESET (nên đặt ở chế độ
     * "Signed" hoặc ít nhất giới hạn resource type = raw, KHÔNG public tùy ý,
     * vì file backup có thể chứa dữ liệu nhạy cảm dù đã được mã hóa AES-256-GCM
     * bởi EncryptingBackupStrategy trước khi tới đây).
     *
     * Trả về secure_url do Cloudinary cấp cho file vừa tải lên.
     */
    public String uploadBackupFile(File backupFile) throws CloudinaryUploadException {
        validateBackupFile(backupFile);

        String cloudName = getRequiredConfig(CLOUD_NAME_ENV,
                "Thiếu CLOUDINARY_CLOUD_NAME. Hãy cấu hình trong secure-config.enc hoặc biến môi trường rồi mở lại Eclipse.");
        String uploadPreset = getRequiredConfig(BACKUP_UPLOAD_PRESET_ENV,
                "Thiếu CLOUDINARY_BACKUP_UPLOAD_PRESET. Hãy tạo Upload Preset riêng cho file backup trên Cloudinary.");

        if (!cloudName.matches("[A-Za-z0-9_-]+")) {
            throw new CloudinaryUploadException("CLOUDINARY_CLOUD_NAME không hợp lệ.");
        }

        String boundary = "----SIMSCloudinaryBackup" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody;
        try {
            requestBody = createMultipartBody(backupFile, uploadPreset, boundary, false);
        } catch (IOException e) {
            throw new CloudinaryUploadException("Không đọc được file backup.", e);
        }

        // resource_type = raw: Cloudinary lưu nguyên file nhị phân (.sql/.bak/.enc),
        // không cố xử lý như ảnh/video → tránh lỗi định dạng và không tối ưu/nén sai.
        URI uploadUri = URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/raw/upload");
        return executeUpload(uploadUri, requestBody, boundary, 180);
    }

    // ================================================================
    // XỬ LÝ UPLOAD CHUNG
    // ================================================================

    private String executeUpload(URI uploadUri, byte[] requestBody, String boundary, int timeoutSeconds)
            throws CloudinaryUploadException {

        HttpRequest request = HttpRequest.newBuilder(uploadUri)
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CloudinaryUploadException("Quá trình tải đã bị hủy.", e);
        } catch (IOException e) {
            throw new CloudinaryUploadException("Không kết nối được Cloudinary. Hãy kiểm tra Internet.", e);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String cloudinaryError = getCloudinaryError(response.body());
            if (cloudinaryError != null && !cloudinaryError.isBlank()) {
                throw new CloudinaryUploadException("Cloudinary từ chối tải: " + cloudinaryError);
            }
            throw new CloudinaryUploadException("Cloudinary từ chối tải. HTTP " + response.statusCode());
        }

        return parseSecureUrl(response.body());
    }

    private String parseSecureUrl(String responseBody) throws CloudinaryUploadException {
        try {
            JsonObject result = JsonParser.parseString(responseBody).getAsJsonObject();
            if (!result.has("secure_url") || result.get("secure_url").isJsonNull()) {
                throw new CloudinaryUploadException("Cloudinary không trả về URL.");
            }
            String secureUrl = result.get("secure_url").getAsString();
            if (secureUrl == null || !secureUrl.startsWith("https://")) {
                throw new CloudinaryUploadException("URL Cloudinary trả về không hợp lệ.");
            }
            return secureUrl;
        } catch (CloudinaryUploadException e) {
            throw e;
        } catch (Exception e) {
            throw new CloudinaryUploadException("Không đọc được phản hồi từ Cloudinary.", e);
        }
    }

    // ================================================================
    // ĐỌC CẤU HÌNH
    // ================================================================

    /**
     * Đọc cấu hình theo thứ tự ưu tiên:
     *   1. Biến môi trường OS (System.getenv)
     *   2. System property (-Dkey=value)
     *   3. AppConfig (secure-config.enc)
     */
    private static String getRequiredConfig(String configKey, String errorMessage)
            throws CloudinaryUploadException {

        // Bước 1: Thử đọc từ biến môi trường hệ điều hành
        String value = System.getenv(configKey);

        // Bước 2: Nếu không có, thử đọc từ system property (-D khi chạy JVM)
        if (value == null || value.trim().isEmpty()) {
            value = System.getProperty(configKey);
        }

        // Bước 3: Nếu vẫn không có, đọc từ AppConfig (secure-config.enc)
        if (value == null || value.trim().isEmpty()) {
            try {
                value = AppConfig.getInstance().get(configKey);
            } catch (Exception e) {
                value = null;
            }
        }

        if (value == null || value.trim().isEmpty()) {
            throw new CloudinaryUploadException(errorMessage);
        }
        return value.trim();
    }

    // ================================================================
    // VALIDATE FILE
    // ================================================================

    private static void validateImageFile(File file) throws CloudinaryUploadException {
        if (file == null || !file.isFile()) {
            throw new CloudinaryUploadException("File ảnh không tồn tại.");
        }
        if (file.length() <= 0) {
            throw new CloudinaryUploadException("File ảnh đang trống.");
        }
        if (file.length() > MAX_IMAGE_SIZE) {
            throw new CloudinaryUploadException("Ảnh vượt quá giới hạn 5MB.");
        }
        String name = file.getName().toLowerCase();
        boolean supported = false;
        for (String ext : ALLOWED_IMAGE_EXTENSIONS) {
            if (name.endsWith(ext)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            throw new CloudinaryUploadException("Chỉ hỗ trợ ảnh JPG, PNG, GIF hoặc WEBP.");
        }
    }

    private static void validateBackupFile(File file) throws CloudinaryUploadException {
        if (file == null || !file.isFile()) {
            throw new CloudinaryUploadException("File backup không tồn tại.");
        }
        if (file.length() <= 0) {
            throw new CloudinaryUploadException("File backup đang trống.");
        }
        if (file.length() > MAX_BACKUP_SIZE) {
            throw new CloudinaryUploadException("File backup vượt quá giới hạn 100MB.");
        }
        String name = file.getName().toLowerCase();
        boolean supported = false;
        for (String ext : ALLOWED_BACKUP_EXTENSIONS) {
            if (name.endsWith(ext)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            throw new CloudinaryUploadException(
                    "Định dạng file backup không được hỗ trợ. Cho phép: SQL, BAK, GZ, ZIP, ENC.");
        }
    }

    private static void validateGenericFile(File file) throws CloudinaryUploadException {
        if (file == null || !file.isFile()) {
            throw new CloudinaryUploadException("File không tồn tại.");
        }
        if (file.length() <= 0) {
            throw new CloudinaryUploadException("File đang trống.");
        }
        if (file.length() > MAX_FILE_SIZE) {
            throw new CloudinaryUploadException("File vượt quá giới hạn 25MB.");
        }
        String name = file.getName().toLowerCase();
        boolean supported = false;
        for (String ext : ALLOWED_FILE_EXTENSIONS) {
            if (name.endsWith(ext)) {
                supported = true;
                break;
            }
        }
        if (!supported) {
            throw new CloudinaryUploadException(
                    "Định dạng file không được hỗ trợ. Cho phép: PDF, DOC, DOCX, XLS, XLSX, PPT, PPTX, TXT, CSV, ZIP, RAR, 7Z, MP3, MP4.");
        }
    }

    // ================================================================
    // TẠO MULTIPART BODY
    // ================================================================

    private static byte[] createMultipartBody(File file, String uploadPreset, String boundary, boolean isImage)
            throws IOException {

        ByteArrayOutputStream output = new ByteArrayOutputStream((int) file.length() + 2048);
        writeTextPart(output, boundary, "upload_preset", uploadPreset);

        String fileName = file.getName().replace("\"", "").replace("\r", "").replace("\n", "");
        String contentType = Files.probeContentType(file.toPath());

        if (contentType == null || contentType.isEmpty()) {
            contentType = isImage ? contentTypeFromImageFileName(fileName) : "application/octet-stream";
        }

        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n");
        writeAscii(output, "Content-Type: " + contentType + "\r\n\r\n");
        output.write(Files.readAllBytes(file.toPath()));
        writeAscii(output, "\r\n--" + boundary + "--\r\n");
        return output.toByteArray();
    }

    private static void writeTextPart(ByteArrayOutputStream output, String boundary, String name, String value)
            throws IOException {
        writeAscii(output, "--" + boundary + "\r\n");
        writeAscii(output, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        writeAscii(output, "\r\n");
    }

    private static void writeAscii(ByteArrayOutputStream output, String value) throws IOException {
        output.write(value.getBytes(StandardCharsets.US_ASCII));
    }

    private static String contentTypeFromImageFileName(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private static String getCloudinaryError(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) return null;
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("error") && json.get("error").isJsonObject()) {
                JsonObject error = json.getAsJsonObject("error");
                if (error.has("message") && !error.get("message").isJsonNull()) {
                    return error.get("message").getAsString();
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}