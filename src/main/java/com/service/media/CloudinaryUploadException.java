package com.service.media;

/**
 * Lỗi xảy ra khi tải ảnh lên Cloudinary.
 */
public class CloudinaryUploadException extends Exception {

    private static final long serialVersionUID = 1L;

    public CloudinaryUploadException(String message) {
        super(message);
    }

    public CloudinaryUploadException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}