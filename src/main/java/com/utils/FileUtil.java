package com.utils;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Tien ich thao tac file dung chung: copy, sinh ten tranh trung, format dung
 * luong, mo hop thoai chon file/luu file... Khong phu thuoc domain nao cua
 * du an nen dung lai duoc cho bat ky ung dung Java Desktop khac.
 */
public final class FileUtil {

    private FileUtil() {}

    // ---------- Ten file / duong dan ----------

    public static String getExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return (dot >= 0 && dot < fileName.length() - 1) ? fileName.substring(dot + 1) : "";
    }

    public static String getNameWithoutExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /** Sinh ten file duy nhat (UUID) nhung giu nguyen duoi file goc, dung khi luu file upload de tranh trung ten. */
    public static String generateUniqueFileName(String originalFileName) {
        String ext = getExtension(originalFileName);
        String uuid = UUID.randomUUID().toString();
        return ext.isEmpty() ? uuid : uuid + "." + ext;
    }

    // ---------- Thu muc / copy / xoa ----------

    /** Dam bao thu muc ton tai, tu tao (kem thu muc cha) neu chua co - vi du thu muc "uploads/". */
    public static boolean ensureDirectory(String dirPath) {
        try {
            Files.createDirectories(Paths.get(dirPath));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Copy file nguon vao thu muc dich voi ten duy nhat (UUID); tra ve File dich hoac null neu loi. */
    public static File copyToDirectory(File source, String targetDir) {
        if (source == null || !source.exists()) return null;
        ensureDirectory(targetDir);
        String uniqueName = generateUniqueFileName(source.getName());
        Path target = Paths.get(targetDir, uniqueName);
        try {
            Files.copy(source.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            return target.toFile();
        } catch (IOException e) {
            return null;
        }
    }

    /** Xoa file, khong nem exception neu that bai (vd file dang bi khoa boi tien trinh khac). */
    public static boolean deleteQuietly(File file) {
        if (file == null) return false;
        try {
            return Files.deleteIfExists(file.toPath());
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Tra ve 1 File trong {temp}/{subDir}/, ten dang "{baseName}_{timestamp}.{ext}"
     * (timestamp toi milli-giay) - luon la ten MOI, khong bao gio trung voi lan
     * goi truoc do (ke ca goi 2 lan lien tiep trong cung 1 giay).
     * <p>
     * Dung khi xuat file (PDF, Excel...) ra thu muc tam roi mo bang trinh doc
     * mac dinh cua he dieu hanh (Desktop.open): neu dung 1 ten CO DINH cho moi
     * lan xuat, va nguoi dung van con dang mo file cu do trong trinh xem ben
     * ngoai, Windows se khoa file lai -> lan ghi tiep theo bao loi "The process
     * cannot access the file because it is being used by another process".
     */
    public static File uniqueTempFile(String subDir, String baseName, String ext) {
        File dir = new File(System.getProperty("java.io.tmpdir"), subDir);
        ensureDirectory(dir.getAbsolutePath());
        String safeBase = (baseName == null || baseName.isBlank())
                ? "file" : baseName.replaceAll("[^a-zA-Z0-9]", "_");
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS"));
        return new File(dir, safeBase + "_" + timestamp + "." + ext);
    }

    // ---------- Kich thuoc / dinh dang ----------

    /** Vi du: 2500000 byte -> "2.4 MB". Dung o cho hien thi gioi han dung luong upload. */
    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        exp = Math.min(exp, 4); // toi da TB de tranh vuot chi so chuoi don vi
        String unit = "KMGT".charAt(exp - 1) + "B";
        return String.format("%.1f %s", bytes / Math.pow(1024, exp), unit);
    }

    /** Kiem tra dung luong file co vuot qua gioi han (MB) khong - dung truoc khi cho phep upload. */
    public static boolean isWithinSizeLimit(File file, double maxMegabytes) {
        return file != null && file.exists() && file.length() <= maxMegabytes * 1024 * 1024;
    }

    // ---------- JFileChooser tien ich ----------

    /**
     * Mo hop thoai chon 1 file anh (jpg/jpeg/png/gif), tra ve null neu nguoi
     * dung huy. Dung cho tinh nang "Chon anh dai dien" / "Chon anh san pham".
     */
    public static File chooseImageFile(Component parent) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Ảnh (JPG, PNG, GIF)", "jpg", "jpeg", "png", "gif"));
        chooser.setAcceptAllFileFilterUsed(false);
        int result = chooser.showOpenDialog(parent);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    /** Mo hop thoai chon 1 file bat ky voi filter tuy chinh (mo ta + danh sach duoi file). */
    public static File chooseFile(Component parent, String description, String... extensions) {
        JFileChooser chooser = new JFileChooser();
        if (extensions != null && extensions.length > 0) {
            chooser.setFileFilter(new FileNameExtensionFilter(description, extensions));
            chooser.setAcceptAllFileFilterUsed(false);
        }
        int result = chooser.showOpenDialog(parent);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }

    /** Mo hop thoai chon noi luu file (Save As), tra ve null neu nguoi dung huy. */
    public static File chooseSaveLocation(Component parent, String defaultFileName) {
        JFileChooser chooser = new JFileChooser();
        if (defaultFileName != null) chooser.setSelectedFile(new File(defaultFileName));
        int result = chooser.showSaveDialog(parent);
        return result == JFileChooser.APPROVE_OPTION ? chooser.getSelectedFile() : null;
    }
}