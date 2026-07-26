package com.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Luu "Ghi nho dang nhap" o may local (khong lien quan DB).
 * CHI luu ten dang nhap, KHONG luu mat khau (tranh luu mat khau khong ma hoa tren dia).
 */
public class RememberMeUtil {

    private static final Path FILE_PATH =
            Paths.get(System.getProperty("user.home"), ".myshop_remember.properties");

    private static final String KEY_USERNAME = "username";

    /** Luu ten dang nhap de lan sau tu dien san. */
    public static void remember(String username) {
        Properties props = new Properties();
        props.setProperty(KEY_USERNAME, username);
        try (OutputStream out = Files.newOutputStream(FILE_PATH)) {
            props.store(out, "MyShop - remembered login (khong luu mat khau)");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Xoa thong tin da ghi nho (khi nguoi dung bo tick "Ghi nho dang nhap"). */
    public static void forget() {
        try {
            Files.deleteIfExists(FILE_PATH);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** Tra ve ten dang nhap da luu, hoac null neu chua tung ghi nho / da bi xoa. */
    public static String getRememberedUsername() {
        if (!Files.exists(FILE_PATH)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(FILE_PATH)) {
            props.load(in);
            return props.getProperty(KEY_USERNAME);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}