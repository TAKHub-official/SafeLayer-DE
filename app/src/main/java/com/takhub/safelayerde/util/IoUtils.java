package com.takhub.safelayerde.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

public final class IoUtils {

    private IoUtils() {
    }

    public static void ensureDir(File dir) throws IOException {
        if (dir == null) {
            throw new IOException("Directory reference is null.");
        }
        if (dir.isDirectory()) {
            return;
        }
        if (!dir.exists() && dir.mkdirs()) {
            return;
        }
        if (!dir.isDirectory()) {
            throw new IOException("Unable to create directory: " + dir.getAbsolutePath());
        }
    }

    public static void atomicWrite(File target, String content) throws IOException {
        if (target == null) {
            throw new IOException("Target file is null.");
        }

        File parent = target.getParentFile();
        if (parent != null) {
            ensureDir(parent);
        }

        File temporaryFile = new File(target.getAbsolutePath() + ".tmp");
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw new IOException("Unable to delete temporary file: " + temporaryFile.getAbsolutePath());
        }

        try (Writer writer = new OutputStreamWriter(
                new FileOutputStream(temporaryFile), StandardCharsets.UTF_8)) {
            writer.write(content == null ? "" : content);
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace target file: " + target.getAbsolutePath());
        }

        if (!temporaryFile.renameTo(target)) {
            throw new IOException("Unable to atomically rename cache file: " + target.getAbsolutePath());
        }
    }

    public static void atomicWriteBytes(File target, byte[] content) throws IOException {
        if (target == null) {
            throw new IOException("Target file is null.");
        }

        File parent = target.getParentFile();
        if (parent != null) {
            ensureDir(parent);
        }

        File temporaryFile = new File(target.getAbsolutePath() + ".tmp");
        if (temporaryFile.exists() && !temporaryFile.delete()) {
            throw new IOException("Unable to delete temporary file: " + temporaryFile.getAbsolutePath());
        }

        try (FileOutputStream outputStream = new FileOutputStream(temporaryFile)) {
            outputStream.write(content == null ? new byte[0] : content);
            outputStream.flush();
        }

        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace target file: " + target.getAbsolutePath());
        }

        if (!temporaryFile.renameTo(target)) {
            throw new IOException("Unable to atomically rename cache file: " + target.getAbsolutePath());
        }
    }

    public static byte[] readBytes(File source) throws IOException {
        return readBytes(source, -1);
    }

    public static byte[] readBytes(File source, int maxBytes) throws IOException {
        if (source == null) {
            throw new IOException("Source file is null.");
        }
        if (!source.isFile()) {
            throw new IOException("Source file is not readable: " + source.getAbsolutePath());
        }
        if (maxBytes == 0) {
            return new byte[0];
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int totalBytes = 0;
        try (FileInputStream inputStream = new FileInputStream(source)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (maxBytes > 0 && totalBytes + bytesRead > maxBytes) {
                    int writableBytes = maxBytes - totalBytes;
                    if (writableBytes > 0) {
                        outputStream.write(buffer, 0, writableBytes);
                    }
                    throw new IOException("Source file exceeds maximum readable size: " + source.getAbsolutePath());
                }
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
        }
        return outputStream.toByteArray();
    }

    public static byte[] readLeadingBytes(File source, int maxBytes) throws IOException {
        if (source == null) {
            throw new IOException("Source file is null.");
        }
        if (!source.isFile()) {
            throw new IOException("Source file is not readable: " + source.getAbsolutePath());
        }
        if (maxBytes <= 0) {
            return new byte[0];
        }

        byte[] buffer = new byte[maxBytes];
        int totalBytes = 0;
        try (FileInputStream inputStream = new FileInputStream(source)) {
            int bytesRead;
            while (totalBytes < maxBytes
                    && (bytesRead = inputStream.read(buffer, totalBytes, maxBytes - totalBytes)) != -1) {
                totalBytes += bytesRead;
            }
        }
        if (totalBytes == buffer.length) {
            return buffer;
        }

        byte[] truncated = new byte[totalBytes];
        System.arraycopy(buffer, 0, truncated, 0, totalBytes);
        return truncated;
    }

    public static String readUtf8(File source) throws IOException {
        return new String(readBytes(source), StandardCharsets.UTF_8);
    }

    public static String readUtf8(File source, int maxBytes) throws IOException {
        return new String(readBytes(source, maxBytes), StandardCharsets.UTF_8);
    }

    public static File createTempFile(String prefix, String suffix) throws IOException {
        return File.createTempFile(prefix, suffix);
    }
}
