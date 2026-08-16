package com.security;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Tu dong "cai dat" MYSHOP_CONFIG_KEY vao bien moi truong cap nguoi dung
 * cua Windows (HKCU\Environment) khi chay sims.exe, de nguoi dung KHONG
 * can tu tay vao System Properties > Environment Variables de set bien
 * nay - huu ich cho cac cong cu chay rieng le sau nay tren cung may
 * (vd ConfigTool) van doc duoc key ma khong can biet co che -D system
 * property ma jpackage da "bake" san vao SIMS.cfg.
 *
 * Chi thuc su lam gi khi:
 *   - He dieu hanh la Windows (setx la lenh cua Windows).
 *   - Da co san system property myshop.config.key (tuc dang chay tu ban
 *     .exe dong goi qua jpackage - xem build.bat, KHONG phai chay tu
 *     IDE/dev; dev van set bien moi truong thu cong nhu truoc).
 *   - Bien moi truong OS hien tai (System.getenv) CHUA co hoac khac gia
 *     tri, de tranh goi setx (spawn 1 process con) moi lan mo app.
 *
 * Luu y quan trong (gioi han von co cua Windows): setx chi ghi vao
 * registry (HKCU\Environment) va bao he thong broadcast thay doi - cac
 * process DANG CHAY (ke ca chinh process hien tai) se KHONG thay gia
 * tri moi ngay lap tuc, chi cac process MOI mo SAU do (thuong la sau khi
 * dang xuat/dang nhap lai) moi doc duoc qua System.getenv. Vi vay day
 * chi la buoc "cai san" cho lan sau / cho tool khac - KHONG thay the co
 * che -D bake key ma AppConfig dang dung de chinh app chay duoc ngay tu
 * lan dau, ke ca truoc khi Windows cap nhat xong bien moi truong.
 */
public final class EnvKeySync {

    private EnvKeySync() {
    }

    /**
     * Goi 1 lan luc app khoi dong (sau khi AppConfig.getInstance() da
     * chay thanh cong, tuc key dang dung la key hop le). Khong bao gio
     * nem exception ra ngoai va khong chan luong khoi dong app.
     */
    public static void syncIfNeeded() {
        try {
            if (!isWindows()) {
                return;
            }
            String bakedValue = System.getProperty(AppConfig.SYS_PROP_KEY_NAME);
            if (bakedValue == null || bakedValue.trim().isEmpty()) {
                // Dang chay tu IDE/dev (khong qua jpackage) - khong co gia tri
                // nao duoc "bake" de dong bo nguoc lai vao OS ca.
                return;
            }
            String currentEnvValue = System.getenv(AppConfig.ENV_KEY_NAME);
            if (Objects.equals(bakedValue, currentEnvValue)) {
                // Da dong bo tu lan chay truoc roi - khong can goi setx lai.
                return;
            }
            runSetxAsync(AppConfig.ENV_KEY_NAME, bakedValue);
        } catch (Exception e) {
            // Chi la buoc tien ich - app van chay binh thuong nho -D system
            // property du buoc nay that bai, nen khong duoc phep lam sap app.
            System.err.println("EnvKeySync: khong dong bo duoc bien moi truong OS: " + e.getMessage());
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase().contains("win");
    }

    private static void runSetxAsync(String name, String value) {
        Thread t = new Thread(() -> {
            try {
                // Goi truc tiep setx.exe (khong qua "cmd /c") de tranh moi
                // rac roi ve escape ky tu dac biet khi truyen value qua shell.
                ProcessBuilder pb = new ProcessBuilder("setx", name, value);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                // Phai doc het output, neu khong process con co the bi treo
                // vi buffer day (setx in ra vai dong thong bao).
                byte[] output = p.getInputStream().readAllBytes();
                boolean finished = p.waitFor(10, TimeUnit.SECONDS);
                if (finished && p.exitValue() == 0) {
                    System.out.println("EnvKeySync: da cai " + name
                        + " vao bien moi truong nguoi dung Windows "
                        + "(co hieu luc tu lan dang nhap/mo app tiep theo).");
                } else {
                    System.err.println("EnvKeySync: lenh setx that bai hoac qua thoi gian cho. Output: "
                        + new String(output, java.nio.charset.StandardCharsets.UTF_8));
                }
            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                System.err.println("EnvKeySync: loi khi chay setx: " + e.getMessage());
            }
        }, "env-key-sync");
        t.setDaemon(true);
        t.start();
    }
}