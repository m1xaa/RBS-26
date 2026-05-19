package com.tim8.oblak.core.upload;

import com.tim8.oblak.core.analysis.AnalysisResult;
import com.tim8.oblak.core.analysis.CodeAnalysisService;
import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.core.metadata.ProjectMetadataService;
import com.tim8.oblak.core.validation.ZipValidation;
import com.tim8.oblak.core.validation.ZipValidationException;
import com.tim8.oblak.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Service
@RequiredArgsConstructor
public class UploadService {

    private final ZipValidation zipValidation;
    private final CodeAnalysisService codeAnalysisService;
    private final ProjectMetadataService projectMetadataService;
    private final MinioService minioService;

    @Value("${oblak.temp-dir-name}")
    private String tempDirName;

    public void upload(MultipartFile file) {
        zipValidation.validateZipFile(file);
        Path extractedDirectory = extractZipToTempDir(file);
        //assume it passed
        analyzeExtractedFiles(extractedDirectory);

        ProjectMetadata metadata = projectMetadataService.createPending(file);
        try {
            String minioKey = minioService.uploadProject(file, metadata.getId());
            projectMetadataService.markCompleted(metadata, minioKey);
        } catch (RuntimeException exception) {
            projectMetadataService.markFailed(metadata);
            throw exception;
        }
    }

    private Path extractZipToTempDir(MultipartFile file) {
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

    private List<AnalysisResult> analyzeExtractedFiles(Path extractedDirectory) {
        try (Stream<Path> paths = Files.walk(extractedDirectory)) {
            return paths
                .filter(Files::isRegularFile)
                .map(this::analyzeFile)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read extracted files.", exception);
        }
    }

    private AnalysisResult analyzeFile(Path path) {
        try {
            String code = Files.readString(path);
            return codeAnalysisService.analyze(code);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read extracted file: " + path, exception);
        }
    }
}
