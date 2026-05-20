package com.tim8.oblak.core.upload;

import com.tim8.oblak.core.validation.ZipValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class ZipExtractionService {

    @Value("${oblak.temp-dir-name}")
    private String tempDirName;

    public Path extractZipToTempDir(MultipartFile file) {
        Path tempDir = Path.of(tempDirName).normalize();

        try (ZipInputStream zipInputStream = new ZipInputStream(file.getInputStream())) {
            Files.createDirectories(tempDir);

            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                Path targetPath = tempDir.resolve(entry.getName()).normalize();

                if (!targetPath.startsWith(tempDir)) {
                    throw new ZipValidationException("ZIP contains unsafe file paths.");
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(targetPath);
                } else {
                    Path parent = targetPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }

                    Files.copy(zipInputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zipInputStream.closeEntry();
            }

            return tempDir;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not extract uploaded ZIP.", exception);
        }
    }

    public void cleanTempDirectory() {
        Path tempDir = Path.of(tempDirName).normalize();

        if (!Files.exists(tempDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(tempDir)) {
            paths
                .filter(path -> !path.equals(tempDir))
                .sorted(Comparator.reverseOrder())
                .forEach(this::deleteTempPath);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not clean temp directory.", exception);
        }
    }

    private void deleteTempPath(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not delete temp file: " + path, exception);
        }
    }
}
