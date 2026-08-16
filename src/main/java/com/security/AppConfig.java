package com.security;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Quản lý cấu hình bảo mật của ứng dụng.
 * 
 * Ưu tiên đọc giá trị theo thứ tự:
 *   1. System property (-Dkey=value khi chạy JVM)
 *   2. Biến môi trường OS
 *   3. File secure-config.enc (đã mã hóa)
 */
public final class AppConfig {
    /**
     * Tên biến môi trường chứa master key và tên file config đã mã hóa.
     */
    public static final String ENV_KEY_NAME =
            System.getProperty("secureconfig.envKeyName", "MYSHOP_CONFIG_KEY");

    /**
     * Tên JVM system property (-D) chứa master key, dùng khi đóng gói .exe qua jpackage.
     */
    public static final String SYS_PROP_KEY_NAME =
            System.getProperty("secureconfig.sysPropKeyName", "myshop.config.key");

    public static final String DEFAULT_FILE_NAME =
            System.getProperty("secureconfig.fileName", "secure-config.enc");

    private static volatile AppConfig instance;
    private final Properties values = new Properties();

    private AppConfig() {
        SecretKey masterKey = getMasterKey();
        String encryptedContent = loadEncryptedContent();
        String plaintext = CryptoUtil.decrypt(encryptedContent, masterKey);
        try {
            values.load(new StringReader(plaintext));
        } catch (IOException e) {
            throw new IllegalStateException(
                "Config đã giải mã thành công nhưng không đọc được định dạng key=value: " 
                + e.getMessage(), e);
        }
    }

    public static AppConfig getInstance() {
        AppConfig result = instance;
        if (result == null) {
            synchronized (AppConfig.class) {
                result = instance;
                if (result == null) {
                    instance = result = new AppConfig();
                }
            }
        }
        return result;
    }

    /** Dùng cho dev/test khi muốn nạp lại config sau khi đổi file secure-config.enc. */
    public static synchronized void reload() {
        instance = null;
    }

    /** Kiểm tra nhanh master key đã sẵn sàng chưa. */
    public static boolean isEnvKeySet() {
        return resolveRawKey() != null;
    }

    /** Đọc giá trị key thô (chưa base64-decode), ưu tiên system property trước, fallback biến môi trường. */
    private static String resolveRawKey() {
        String value = System.getProperty(SYS_PROP_KEY_NAME);
        if (value == null || value.trim().isEmpty()) {
            value = System.getenv(ENV_KEY_NAME);
        }
        return (value != null && !value.trim().isEmpty()) ? value : null;
    }

    /** true nếu AppConfig đã nạp thành công ít nhất 1 lần. */
    public static boolean isLoaded() {
        return instance != null;
    }

    /**
     * Đọc giá trị cấu hình theo thứ tự ưu tiên:
     *   1. System property (-Dkey=value)
     *   2. Biến môi trường OS
     *   3. File secure-config.enc
     */
    public String get(String key) {
        // Ưu tiên 1: System property
        String value = System.getProperty(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        
        // Ưu tiên 2: Biến môi trường
        value = System.getenv(key);
        if (value != null && !value.trim().isEmpty()) {
            return value.trim();
        }
        
        // Ưu tiên 3: File secure-config.enc
        value = values.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(
                "Thiếu key '" + key + "'. Hãy kiểm tra trong secure-config.enc, "
                + "biến môi trường, hoặc system property (-D" + key + "=...).");
        }
        return value.trim();
    }

    /** Đọc giá trị cấu hình với giá trị mặc định nếu không tìm thấy. */
    public String get(String key, String defaultValue) {
        try {
            return get(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key).trim());
    }

    public int getInt(String key, int defaultValue) {
        try {
            return getInt(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public long getLong(String key) {
        return Long.parseLong(get(key).trim());
    }

    public long getLong(String key, long defaultValue) {
        try {
            return getLong(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public boolean getBoolean(String key) {
        String value = get(key).trim().toLowerCase();
        return "true".equals(value) || "1".equals(value) || "yes".equals(value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        try {
            return getBoolean(key);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Đọc và giải mã master key AES-256.
     * Ưu tiên system property SYS_PROP_KEY_NAME (jpackage bake),
     * fallback về biến môi trường ENV_KEY_NAME (IDE/dev).
     */
    public static SecretKey getMasterKey() {
        String rawKey = resolveRawKey();
        if (rawKey == null) {
            throw new IllegalStateException(
                "Thiếu master key. Ứng dụng KHÔNG THỂ khởi động nếu không có master key.\n"
                + "Cách khắc phục:\n"
                + "  1) Chạy ConfigTool để sinh key mới (nếu chưa có):\n"
                + "     java -cp target/classes com.security.tool.ConfigTool genkey\n"
                + "  2a) Chạy từ bản .exe đã đóng gói qua build.bat (key được bake sẵn), hoặc\n"
                + "  2b) Nếu chạy từ IDE/dev, set biến môi trường " + ENV_KEY_NAME 
                + " = key vừa sinh ra, rồi chạy lại app.");
        }
        return CryptoUtil.decodeKey(rawKey);
    }

    private String loadEncryptedContent() {
        String customPath = System.getProperty("myshop.config.file", 
                System.getProperty("secureconfig.file"));
        Path externalPath = Paths.get(customPath != null ? customPath : DEFAULT_FILE_NAME);
        try {
            if (Files.exists(externalPath)) {
                return new String(Files.readAllBytes(externalPath), StandardCharsets.UTF_8).trim();
            }
            return readFromClasspath();
        } catch (IOException e) {
            throw new IllegalStateException(
                "Lỗi đọc file config đã mã hóa (" + externalPath + "): " + e.getMessage(), e);
        }
    }

    private String readFromClasspath() throws IOException {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULT_FILE_NAME)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Không tìm thấy file config đã mã hóa (" + DEFAULT_FILE_NAME + ").\n"
                    + "Đặt file này cạnh file .jar khi chạy thực tế, "
                    + "hoặc trong src/main/resources/ lúc build/chạy từ IDE.\n"
                    + "Dùng ConfigTool (lệnh 'encrypt') để tạo file này.");
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int len;
            while ((len = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, len);
            }
            return new String(buffer.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }
}