package com.service.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
 * Dịch vụ tải ảnh lên Cloudinary bằng Unsigned Upload Preset.
 *
 * Ứng dụng chỉ sử dụng:
 * - CLOUDINARY_CLOUD_NAME
 * - CLOUDINARY_UPLOAD_PRESET
 *
 * Không sử dụng API Secret trong ứng dụng desktop.
 */
public final class CloudinaryService {

    private static final long MAX_IMAGE_SIZE =
            5L * 1024L * 1024L;

    private static final String CLOUD_NAME_ENV =
            "CLOUDINARY_CLOUD_NAME";

    private static final String UPLOAD_PRESET_ENV =
            "CLOUDINARY_UPLOAD_PRESET";

    private static final CloudinaryService INSTANCE =
            new CloudinaryService();

    private final HttpClient httpClient;

    private CloudinaryService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(
                        HttpClient.Redirect.NORMAL
                )
                .build();
    }

    public static CloudinaryService getInstance() {
        return INSTANCE;
    }

    /**
     * Tải ảnh sản phẩm lên Cloudinary.
     */
    public String uploadProductImage(File imageFile)
            throws CloudinaryUploadException {

        return uploadImage(imageFile);
    }

    /**
     * Tải avatar khách hàng/nhân viên lên Cloudinary.
     */
    public String uploadAvatar(File imageFile)
            throws CloudinaryUploadException {

        return uploadImage(imageFile);
    }

    private String uploadImage(File imageFile)
            throws CloudinaryUploadException {

        validateFile(imageFile);

        String cloudName = getRequiredEnvironment(
                CLOUD_NAME_ENV,
                "Thiếu CLOUDINARY_CLOUD_NAME. "
                        + "Hãy cấu hình biến môi trường rồi mở lại Eclipse."
        );

        String uploadPreset = getRequiredEnvironment(
                UPLOAD_PRESET_ENV,
                "Thiếu CLOUDINARY_UPLOAD_PRESET. "
                        + "Hãy tạo Unsigned Upload Preset trên Cloudinary."
        );

        if (!cloudName.matches("[A-Za-z0-9_-]+")) {
            throw new CloudinaryUploadException(
                    "CLOUDINARY_CLOUD_NAME không hợp lệ."
            );
        }

        String boundary =
                "----SIMSCloudinary"
                        + UUID.randomUUID()
                        .toString()
                        .replace("-", "");

        byte[] requestBody;

        try {
            requestBody = createMultipartBody(
                    imageFile,
                    uploadPreset,
                    boundary
            );
        } catch (IOException e) {
            throw new CloudinaryUploadException(
                    "Không đọc được file ảnh đã chọn.",
                    e
            );
        }

        URI uploadUri = URI.create(
                "https://api.cloudinary.com/v1_1/"
                        + cloudName
                        + "/image/upload"
        );

        HttpRequest request = HttpRequest
                .newBuilder(uploadUri)
                .timeout(Duration.ofSeconds(60))
                .header(
                        "Content-Type",
                        "multipart/form-data; boundary="
                                + boundary
                )
                .header("Accept", "application/json")
                .POST(
                        HttpRequest.BodyPublishers
                                .ofByteArray(requestBody)
                )
                .build();

        HttpResponse<String> response;

        try {
            response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(
                            StandardCharsets.UTF_8
                    )
            );
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();

            throw new CloudinaryUploadException(
                    "Quá trình tải ảnh đã bị hủy.",
                    e
            );
        } catch (IOException e) {
            throw new CloudinaryUploadException(
                    "Không kết nối được Cloudinary. "
                            + "Hãy kiểm tra Internet.",
                    e
            );
        }

        if (response.statusCode() < 200
                || response.statusCode() >= 300) {

            String cloudinaryError =
                    getCloudinaryError(response.body());

            if (cloudinaryError != null
                    && !cloudinaryError.isBlank()) {

                throw new CloudinaryUploadException(
                        "Cloudinary từ chối tải ảnh: "
                                + cloudinaryError
                );
            }

            throw new CloudinaryUploadException(
                    "Cloudinary từ chối tải ảnh. HTTP "
                            + response.statusCode()
            );
        }

        try {
            JsonObject result =
                    JsonParser.parseString(
                            response.body()
                    ).getAsJsonObject();

            if (!result.has("secure_url")
                    || result.get("secure_url")
                    .isJsonNull()) {

                throw new CloudinaryUploadException(
                        "Cloudinary không trả về URL ảnh."
                );
            }

            String secureUrl =
                    result.get("secure_url")
                            .getAsString();

            if (secureUrl == null
                    || !secureUrl.startsWith("https://")) {

                throw new CloudinaryUploadException(
                        "URL Cloudinary trả về không hợp lệ."
                );
            }

            return secureUrl;

        } catch (CloudinaryUploadException e) {
            throw e;

        } catch (Exception e) {
            throw new CloudinaryUploadException(
                    "Không đọc được phản hồi từ Cloudinary.",
                    e
            );
        }
    }

    private static byte[] createMultipartBody(
            File imageFile,
            String uploadPreset,
            String boundary
    ) throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream(
                        (int) imageFile.length() + 2048
                );

        writeTextPart(
                output,
                boundary,
                "upload_preset",
                uploadPreset
        );

        String fileName =
                imageFile.getName()
                        .replace("\"", "")
                        .replace("\r", "")
                        .replace("\n", "");

        String contentType =
                Files.probeContentType(
                        imageFile.toPath()
                );

        if (contentType == null
                || !contentType.startsWith("image/")) {

            contentType =
                    contentTypeFromFileName(fileName);
        }

        writeAscii(
                output,
                "--" + boundary + "\r\n"
        );

        writeAscii(
                output,
                "Content-Disposition: form-data; "
                        + "name=\"file\"; "
                        + "filename=\""
                        + fileName
                        + "\"\r\n"
        );

        writeAscii(
                output,
                "Content-Type: "
                        + contentType
                        + "\r\n\r\n"
        );

        output.write(
                Files.readAllBytes(
                        imageFile.toPath()
                )
        );

        writeAscii(
                output,
                "\r\n--"
                        + boundary
                        + "--\r\n"
        );

        return output.toByteArray();
    }

    private static void writeTextPart(
            ByteArrayOutputStream output,
            String boundary,
            String name,
            String value
    ) throws IOException {

        writeAscii(
                output,
                "--" + boundary + "\r\n"
        );

        writeAscii(
                output,
                "Content-Disposition: form-data; "
                        + "name=\""
                        + name
                        + "\"\r\n\r\n"
        );

        output.write(
                value.getBytes(
                        StandardCharsets.UTF_8
                )
        );

        writeAscii(output, "\r\n");
    }

    private static void writeAscii(
            ByteArrayOutputStream output,
            String value
    ) throws IOException {

        output.write(
                value.getBytes(
                        StandardCharsets.US_ASCII
                )
        );
    }

    private static void validateFile(File file)
            throws CloudinaryUploadException {

        if (file == null || !file.isFile()) {
            throw new CloudinaryUploadException(
                    "File ảnh không tồn tại."
            );
        }

        if (file.length() <= 0) {
            throw new CloudinaryUploadException(
                    "File ảnh đang trống."
            );
        }

        if (file.length() > MAX_IMAGE_SIZE) {
            throw new CloudinaryUploadException(
                    "Ảnh vượt quá giới hạn 5MB."
            );
        }

        String name =
                file.getName().toLowerCase();

        boolean supported =
                name.endsWith(".jpg")
                        || name.endsWith(".jpeg")
                        || name.endsWith(".png")
                        || name.endsWith(".gif")
                        || name.endsWith(".webp");

        if (!supported) {
            throw new CloudinaryUploadException(
                    "Chỉ hỗ trợ ảnh JPG, PNG, GIF hoặc WEBP."
            );
        }
    }

    private static String contentTypeFromFileName(
            String fileName
    ) {
        String lower =
                fileName.toLowerCase();

        if (lower.endsWith(".png")) {
            return "image/png";
        }

        if (lower.endsWith(".gif")) {
            return "image/gif";
        }

        if (lower.endsWith(".webp")) {
            return "image/webp";
        }

        return "image/jpeg";
    }

    private static String getRequiredEnvironment(
            String environmentName,
            String errorMessage
    ) throws CloudinaryUploadException {

        String value =
                System.getenv(environmentName);

        if (value == null
                || value.trim().isEmpty()) {

            throw new CloudinaryUploadException(
                    errorMessage
            );
        }

        return value.trim();
    }

    private static String getCloudinaryError(
            String responseBody
    ) {
        if (responseBody == null
                || responseBody.isBlank()) {

            return null;
        }

        try {
            JsonObject json =
                    JsonParser.parseString(
                            responseBody
                    ).getAsJsonObject();

            if (json.has("error")
                    && json.get("error")
                    .isJsonObject()) {

                JsonObject error =
                        json.getAsJsonObject("error");

                if (error.has("message")
                        && !error.get("message")
                        .isJsonNull()) {

                    return error.get("message")
                            .getAsString();
                }
            }

        } catch (Exception ignored) {
            // Cloudinary trả về nội dung không phải JSON.
        }

        return null;
    }
}
