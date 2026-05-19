package com.tim8.oblak.core.validation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

@Service
public class ZipValidation {

    private static final int BUFFER_SIZE = 8192;
    private static final Pattern WINDOWS_ABSOLUTE_PATH = Pattern.compile("^[a-zA-Z]:[\\\\/].*");

    private final long maxTotalExtractedSize;
    private final int maxFiles;

    public ZipValidation(
        @Value("${oblak.zip.max-total-extracted-size-bytes}") long maxTotalExtractedSize,
        @Value("${oblak.zip.max-files}") int maxFiles
    ) {
        this.maxTotalExtractedSize = maxTotalExtractedSize;
        this.maxFiles = maxFiles;
    }

    public void validateZipFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ZipValidationException("Uploaded file is empty.");
        }

        int fileCount = 0;
        long totalExtractedSize = 0;
        boolean hasEntries = false;
        byte[] buffer = new byte[BUFFER_SIZE];

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;

            while ((entry = zipInputStream.getNextEntry()) != null) {
                hasEntries = true;
                String entryName = entry.getName();

                validateEntryPath(entryName);

                if (!entry.isDirectory()) {
                    fileCount++;
                    if (fileCount > maxFiles) {
                        throw new ZipValidationException("ZIP contains too many files.");
                    }

                    if (isNestedZip(entryName)) {
                        throw new ZipValidationException("Nested ZIP files are not allowed.");
                    }

                    int bytesRead;
                    while ((bytesRead = zipInputStream.read(buffer)) != -1) {
                        totalExtractedSize += bytesRead;
                        if (totalExtractedSize > maxTotalExtractedSize) {
                            throw new ZipValidationException("ZIP extracted size is too large.");
                        }
                    }
                }

                zipInputStream.closeEntry();
            }
        } catch (ZipValidationException exception) {
            throw exception;
        } catch (ZipException exception) {
            throw new ZipValidationException("Uploaded file is not a valid ZIP.");
        } catch (IOException exception) {
            throw new ZipValidationException("Could not read uploaded ZIP.");
        }

        if (!hasEntries) {
            throw new ZipValidationException("Uploaded file is not a valid ZIP.");
        }
    }

    private void validateEntryPath(String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new ZipValidationException("ZIP contains an invalid file path.");
        }

        if (entryName.indexOf('\0') >= 0) {
            throw new ZipValidationException("ZIP contains an invalid file path.");
        }

        String normalizedEntryName = entryName.replace('\\', '/');

        if (
            normalizedEntryName.startsWith("/")
                || normalizedEntryName.equals("..")
                || normalizedEntryName.startsWith("../")
                || normalizedEntryName.contains("/../")
                || normalizedEntryName.endsWith("/..")
                || WINDOWS_ABSOLUTE_PATH.matcher(normalizedEntryName).matches()
        ) {
            throw new ZipValidationException("ZIP contains unsafe file paths.");
        }
    }

    private boolean isNestedZip(String entryName) {
        return entryName.toLowerCase(Locale.ROOT).endsWith(".zip");
    }
}
