package com.security.tool;

import com.security.CryptoUtil;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cong cu dong lenh quan tri "config bao mat" cua he thong myShop.
 * Khong duoc goi tu ben trong app luc chay - day la tool danh cho
 * dev/admin dung thu cong luc setup may hoac khi can doi secret.
 *
 * Cac lenh:
 *   genkey                                  - sinh 1 master key AES-256 moi (Base64)
 *   encrypt <input.properties> <output.enc> - ma hoa 1 file properties (da gop tat ca config) thanh file .enc
 *   decrypt <input.enc>                     - giai ma 1 file .enc, in ra man hinh de kiem tra (KHONG luu lai file)
 *
 * Vi du chay (sau khi mvn compile, tu thu muc goc project):
 *   java -cp target/classes com.security.tool.ConfigTool genkey
 *   java -cp target/classes com.security.tool.ConfigTool encrypt config/merged.properties secure-config.enc
 *   java -cp target/classes com.security.tool.ConfigTool decrypt secure-config.enc
 *
 * Truoc khi 'encrypt' hoac 'decrypt', PHAI da set bien moi truong MYSHOP_CONFIG_KEY
 * (lay tu ket qua lenh 'genkey').
 */
public class ConfigTool {

    private static final String ENV_KEY_NAME = "MYSHOP_CONFIG_KEY";

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }

        String command = args[0];
        try {
            if ("genkey".equals(command)) {
                runGenKey();
            } else if ("encrypt".equals(command)) {
                if (args.length < 3) {
                    System.out.println("Thieu tham so. Vi du: encrypt merged.properties secure-config.enc");
                    return;
                }
                runEncrypt(args[1], args[2]);
            } else if ("decrypt".equals(command)) {
                if (args.length < 2) {
                    System.out.println("Thieu tham so. Vi du: decrypt secure-config.enc");
                    return;
                }
                runDecrypt(args[1]);
            } else {
                printUsage();
            }
        } catch (Exception e) {
            System.out.println("Loi: " + e.getMessage());
        }
    }

    private static void runGenKey() {
        String key = CryptoUtil.generateKeyBase64();
        System.out.println("Da sinh master key moi (AES-256, dang Base64):");
        System.out.println();
        System.out.println(key);
        System.out.println();
        System.out.println("=> Set bien moi truong " + ENV_KEY_NAME + " truoc khi:");
        System.out.println("   - chay app myShop that");
        System.out.println("   - chay lenh 'encrypt'/'decrypt' cua ConfigTool nay");
        System.out.println();
        System.out.println("  Windows (cmd)       :  set " + ENV_KEY_NAME + "=" + key);
        System.out.println("  Windows (PowerShell) :  $env:" + ENV_KEY_NAME + " = \"" + key + "\"");
        System.out.println("  Linux / macOS        :  export " + ENV_KEY_NAME + "=" + key);
        System.out.println();
        System.out.println("LUU Y QUAN TRONG: Luu key nay o noi rieng tu, an toan (vd password manager).");
        System.out.println("KHONG commit key nay len Git. Neu mat key, se khong the giai ma lai config cu.");
    }

    private static void runEncrypt(String inputPath, String outputPath) throws IOException {
        SecretKey key = requireMasterKeyFromEnv();

        Path input = Paths.get(inputPath);
        if (!Files.exists(input)) {
            System.out.println("Khong tim thay file input: " + inputPath);
            return;
        }

        String plaintext = new String(Files.readAllBytes(input), StandardCharsets.UTF_8);
        String encrypted = CryptoUtil.encrypt(plaintext, key);

        Path output = Paths.get(outputPath);
        Files.write(output, encrypted.getBytes(StandardCharsets.UTF_8));

        System.out.println("Ma hoa thanh cong -> " + outputPath);
        System.out.println("Buoc tiep theo:");
        System.out.println("  1) Copy '" + outputPath + "' vao src/main/resources/ (de bundle vao jar) ");
        System.out.println("     hoac dat canh file .jar khi trien khai thuc te.");
        System.out.println("  2) XOA file plaintext goc '" + inputPath + "' (khong commit len Git).");
    }

    private static void runDecrypt(String inputPath) throws IOException {
        SecretKey key = requireMasterKeyFromEnv();

        Path input = Paths.get(inputPath);
        if (!Files.exists(input)) {
            System.out.println("Khong tim thay file input: " + inputPath);
            return;
        }

        String encrypted = new String(Files.readAllBytes(input), StandardCharsets.UTF_8).trim();
        String plaintext = CryptoUtil.decrypt(encrypted, key);

        System.out.println("--- Noi dung sau khi giai ma (chi de kiem tra, KHONG luu lai ra file) ---");
        System.out.println(plaintext);
        System.out.println("--- Het noi dung ---");
    }

    private static SecretKey requireMasterKeyFromEnv() {
        String envValue = System.getenv(ENV_KEY_NAME);
        if (envValue == null || envValue.trim().isEmpty()) {
            throw new IllegalStateException(
                "Chua set bien moi truong " + ENV_KEY_NAME + ". Chay lenh 'genkey' truoc de tao key, "
                + "roi set bien moi truong nay truoc khi encrypt/decrypt.");
        }
        return CryptoUtil.decodeKey(envValue);
    }

    private static void printUsage() {
        System.out.println("ConfigTool - quan tri config ma hoa cho myShop");
        System.out.println();
        System.out.println("Cach dung:");
        System.out.println("  genkey                                   Sinh master key AES-256 moi");
        System.out.println("  encrypt <input.properties> <output.enc>  Ma hoa file properties (da gop chung)");
        System.out.println("  decrypt <input.enc>                      Giai ma de kiem tra (khong luu file)");
    }
}