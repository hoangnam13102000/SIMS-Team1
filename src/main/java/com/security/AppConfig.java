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


public final class AppConfig {

    /**
     * Ten bien moi truong chua master key va ten file config da ma hoa.
     * Mac dinh la gia tri rieng cua myShop de KHONG lam thay doi hanh vi hien
     * tai, nhung neu copy module nay sang mot project khac, chi can set 2
     * system property duoi day (vd -Dsecureconfig.envKeyName=... khi chay
     * java, hoac trong pom.xml) la doi duoc ma khong phai sua source:
     *   -Dsecureconfig.envKeyName=MYAPP_CONFIG_KEY
     *   -Dsecureconfig.fileName=myapp-secure-config.enc
     */
    public static final String ENV_KEY_NAME =
            System.getProperty("secureconfig.envKeyName", "MYSHOP_CONFIG_KEY");
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
                "Config da giai ma thanh cong nhung khong doc duoc dinh dang key=value: " + e.getMessage(), e);
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

    /** Dung cho dev/test khi muon nap lai config sau khi doi file secure-config.enc. */
    public static synchronized void reload() {
        instance = null;
    }

    /** Kiem tra nhanh bien moi truong master key da duoc set chua, KHONG giai ma config. */
    public static boolean isEnvKeySet() {
        String value = System.getenv(ENV_KEY_NAME);
        return value != null && !value.trim().isEmpty();
    }

    /** true neu AppConfig da nap thanh cong it nhat 1 lan (dung cho SecurityCheck, khong ep nap lai). */
    public static boolean isLoaded() {
        return instance != null;
    }

    public String get(String key) {
        String value = values.getProperty(key);
        if (value == null) {
            throw new IllegalStateException(
                "Thieu key '" + key + "' trong secure-config.enc. "
                + "Kiem tra lai file config goc (truoc khi ma hoa) da co du key nay chua.");
        }
        return value;
    }

    public String get(String key, String defaultValue) {
        return values.getProperty(key, defaultValue);
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key).trim());
    }

    public int getInt(String key, int defaultValue) {
        String value = values.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value.trim());
    }

    public long getLong(String key) {
        return Long.parseLong(get(key).trim());
    }

    /**
     * Doc va giai ma master key AES-256 tu bien moi truong MYSHOP_CONFIG_KEY.
     * Public + static de cac module khac (vd ma hoa file backup) dung CHUNG
     * 1 master key voi secure-config.enc, KHONG can secure-config.enc ton tai
     * (chi can bien moi truong).
     */
    public static SecretKey getMasterKey() {
        String envValue = System.getenv(ENV_KEY_NAME);
        if (envValue == null || envValue.trim().isEmpty()) {
            throw new IllegalStateException(
                "Thieu bien moi truong " + ENV_KEY_NAME + ". Ung dung KHONG THE khoi dong neu khong co master key.\n"
                + "Cach khac phuc:\n"
                + "  1) Chay ConfigTool de sinh key moi (neu chua co):\n"
                + "     java -cp target/classes com.security.tool.ConfigTool genkey\n"
                + "  2) Set bien moi truong " + ENV_KEY_NAME + " = key vua sinh ra, roi chay lai app.");
        }
        return CryptoUtil.decodeKey(envValue);
    }

    private String loadEncryptedContent() {
        String customPath = System.getProperty("myshop.config.file", System.getProperty("secureconfig.file"));
        Path externalPath = Paths.get(customPath != null ? customPath : DEFAULT_FILE_NAME);

        try {
            if (Files.exists(externalPath)) {
                return new String(Files.readAllBytes(externalPath), StandardCharsets.UTF_8).trim();
            }
            return readFromClasspath();
        } catch (IOException e) {
            throw new IllegalStateException("Loi doc file config da ma hoa (" + externalPath + "): " + e.getMessage(), e);
        }
    }

    private String readFromClasspath() throws IOException {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(DEFAULT_FILE_NAME)) {
            if (in == null) {
                throw new IllegalStateException(
                    "Khong tim thay file config da ma hoa (" + DEFAULT_FILE_NAME + ") o bat ky vi tri nao.\n"
                    + "Dat file nay canh file .jar khi chay thuc te, hoac trong src/main/resources/ luc build/chay tu IDE.\n"
                    + "Dung ConfigTool (lenh 'encrypt') de tao file nay tu 1 file properties gop chung.");
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