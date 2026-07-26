package com.security;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Quet file bang Windows Defender (Microsoft Defender Antivirus) co san tren moi
 * may Windows 10/11 - KHONG can cai them phan mem thu 3 nao (khac voi ClamAV can
 * tu cai + tu chay daemon clamd). Goi qua dong lenh MpCmdRun.exe di kem san trong
 * Windows, khong can mo socket/port nao ca.
 *
 * Chi hoat dong tren Windows va khi tim thay MpCmdRun.exe o 1 trong cac duong dan
 * chuan. Neu khong phai Windows hoac khong tim thay file -> unavailable (giong
 * cach ClamAvVirusScanner xu ly khi khong ket noi duoc).
 */
public final class WindowsDefenderVirusScanner implements VirusScanner {

    private static final int TIMEOUT_SECONDS = 60;

    // Cac duong dan pho bien cua MpCmdRun.exe qua cac phien ban Windows/Defender.
    private static final String[] CANDIDATE_PATHS = {
        System.getenv("ProgramFiles") + "\\Windows Defender\\MpCmdRun.exe",
        "C:\\ProgramData\\Microsoft\\Windows Defender\\Platform",
    };

    private final String mpCmdRunPath;
    private final boolean windows;

    public WindowsDefenderVirusScanner() {
        this.windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        this.mpCmdRunPath = windows ? locateMpCmdRun() : null;
    }

    @Override
    public String getName() {
        return mpCmdRunPath != null ? "Windows Defender" : "Windows Defender (không khả dụng)";
    }

    @Override
    public ScanResult scan(File file) {
        if (!windows) {
            return ScanResult.unavailable("Windows Defender chỉ khả dụng trên Windows.");
        }
        if (mpCmdRunPath == null) {
            return ScanResult.unavailable(
                "Không tìm thấy MpCmdRun.exe. Kiểm tra Windows Defender có đang bật không "
                + "(Windows Security > Virus & threat protection).");
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(
                mpCmdRunPath, "-Scan", "-ScanType", "3",
                "-File", file.getAbsolutePath(), "-DisableRemediation");
            pb.redirectErrorStream(true);
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append('\n');
                }
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return ScanResult.unavailable("Windows Defender quét quá thời gian chờ (" + TIMEOUT_SECONDS + "s).");
            }

            return interpretOutput(process.exitValue(), output.toString());

        } catch (IOException e) {
            return ScanResult.unavailable("Lỗi khi chạy Windows Defender: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ScanResult.unavailable("Quét bị gián đoạn.");
        }
    }

    /**
     * MpCmdRun in ra dong "Threat(s) found: <ten>" khi phat hien moi de doa - day la
     * cach dang tin cay nhat de biet co threat hay khong (exit code cua MpCmdRun
     * KHONG duoc Microsoft tai lieu hoa day du va co truong hop tra ve exit code 2
     * ngay ca khi chi la loi chay lenh, khong phai phat hien virus - xem
     * https://learn.microsoft.com/answers/questions/1164115).
     */
    private ScanResult interpretOutput(int exitCode, String output) {
        String lower = output.toLowerCase();
        if (lower.contains("threat(s) found") || lower.contains("threats found")
                || lower.contains("found threat")) {
            String threat = extractThreatName(output);
            return ScanResult.infected(threat, "Windows Defender phát hiện: " + threat);
        }
        if (lower.contains("no threats detected") || exitCode == 0) {
            return ScanResult.clean("Windows Defender: File sạch.");
        }
        return ScanResult.unavailable(
            "Windows Defender trả về kết quả không rõ ràng (exit code " + exitCode + "). "
            + "Chỉ kiểm tra heuristic.");
    }

    private String extractThreatName(String output) {
        for (String line : output.split("\n")) {
            if (line.trim().toLowerCase().startsWith("threat")) {
                return line.trim();
            }
        }
        return "Không xác định";
    }

    private String locateMpCmdRun() {
        for (String candidate : CANDIDATE_PATHS) {
            if (candidate == null) continue;
            Path path = Paths.get(candidate);
            if (Files.isRegularFile(path)) {
                return path.toAbsolutePath().toString();
            }
            // C:\ProgramData\...\Platform\<version>\MpCmdRun.exe - version thay doi theo
            // update hang thang, can do thu muc con moi nhat.
            if (Files.isDirectory(path)) {
                Path found = findInVersionedSubdirs(path);
                if (found != null) return found.toString();
            }
        }
        return null;
    }

    private Path findInVersionedSubdirs(Path platformDir) {
        try (java.util.stream.Stream<Path> stream = Files.list(platformDir)) {
            List<Path> versions = stream.filter(Files::isDirectory)
                .sorted((a, b) -> b.getFileName().toString().compareTo(a.getFileName().toString()))
                .collect(java.util.stream.Collectors.toList());
            for (Path version : versions) {
                Path exe = version.resolve("MpCmdRun.exe");
                if (Files.isRegularFile(exe)) return exe;
            }
        } catch (IOException ignored) {
            // Thu muc khong ton tai hoac khong doc duoc -> bo qua, thu candidate tiep theo.
        }
        return null;
    }
}