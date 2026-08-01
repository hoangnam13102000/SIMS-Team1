package com.incident;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Ghi Incident ra file JSON-lines tren dia cuc bo - KHONG dung JDBC/DB, nen
 * van song ke ca khi chinh DB la nguyen nhan gay su co. Xoay file theo ngay.
 */
public class FileIncidentSink implements IncidentSink {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final File directory;

    public FileIncidentSink(File directory) {
        this.directory = directory;
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Khong tao duoc thu muc incident log: " + directory.getAbsolutePath());
        }
    }

    @Override
    public synchronized void write(Incident incident) {
        File file = currentFile();
        try (PrintWriter out = new PrintWriter(new FileWriter(file, true))) {
            out.println(toJsonLine(incident));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private File currentFile() {
        return new File(directory, "incidents-" + LocalDate.now().format(DAY_FORMAT) + ".jsonl");
    }

    private static String toJsonLine(Incident i) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        appendField(sb, "timestamp", i.getTimestamp().toString(), true);
        appendField(sb, "type", i.getType().name(), true);
        appendField(sb, "severity", i.getSeverity().name(), true);
        appendField(sb, "source", nullToEmpty(i.getSource()), true);
        appendField(sb, "message", nullToEmpty(i.getMessage()), true);
        appendField(sb, "stackTrace", nullToEmpty(i.getStackTrace()), false);
        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value, boolean comma) {
        sb.append('"').append(key).append("\":\"").append(escape(value)).append('"');
        if (comma) sb.append(',');
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public List<String> readRawLines(LocalDate day) {
        List<String> lines = new ArrayList<>();
        File file = new File(directory, "incidents-" + day.format(DAY_FORMAT) + ".jsonl");
        if (!file.exists()) return lines;
        try { lines.addAll(java.nio.file.Files.readAllLines(file.toPath())); }
        catch (IOException e) { e.printStackTrace(); }
        return lines;
    }

    public File getDirectory() { return directory; }
}