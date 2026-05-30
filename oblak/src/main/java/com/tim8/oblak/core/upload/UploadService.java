package com.tim8.oblak.core.upload;

import com.tim8.oblak.core.analysis.CodeAnalysisService;
import com.tim8.oblak.core.execution.ExecutionPreparationService;
import com.tim8.oblak.exception.MaliciousCodeException;
import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.core.metadata.ProjectMetadataService;
import com.tim8.oblak.core.validation.ZipValidation;
import com.tim8.oblak.minio.MinioService;
import com.tim8.oblak.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.tim8.oblak.core.analysis.AnalysisResult;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private final ZipValidation zipValidation;
    private final ZipExtractionService zipExtractionService;
    private final CodeAnalysisService codeAnalysisService;
    private final ExecutionPreparationService executionPreparationService;
    private final ProjectMetadataService projectMetadataService;
    private final ProjectFilesystemImageService projectFilesystemImageService;
    private final MinioService minioService;

    public UUID upload(MultipartFile file, User owner) {
        Path projectImage = null;
        ProjectMetadata metadata = null;

        try {
            zipValidation.validateZipFile(file);
            Path extractedDirectory = zipExtractionService.extractZipToTempDir(file);

            // List<AnalysisResult> results = codeAnalysisService.analyzeExtractedFiles(extractedDirectory);
            // boolean malicious =
            //         results.stream()
            //                 .anyMatch(AnalysisResult::isMalicious);
            // if (malicious) {
            //     throw new MaliciousCodeException("Malicious code detected.");
            // }

            metadata = projectMetadataService.createPending(file, owner);
            try {
                Path workdir = resolveProjectWorkdir(extractedDirectory, metadata);
                boolean hasRequirements = workdir.resolve("requirements.txt").toFile().exists();

                projectImage = projectFilesystemImageService.createExt4Image(extractedDirectory, metadata.getId());
                if (hasRequirements) {
                    executionPreparationService.prepareProjectImage(projectImage, metadata);
                }
                String minioKey = minioService.uploadProjectImage(projectImage, metadata.getId());
                projectMetadataService.markCompleted(metadata, minioKey);
                return metadata.getId();
            } catch (RuntimeException exception) {
                projectMetadataService.markFailed(metadata);
                throw exception;
            }
        } finally {
            projectFilesystemImageService.deleteImage(projectImage);
            zipExtractionService.cleanTempDirectory();
        }
    }

    private Path resolveProjectWorkdir(Path extractedDirectory, ProjectMetadata metadata) {
        String workingDirectoryName = metadata.getWorkingDirectory();
        if (workingDirectoryName == null || workingDirectoryName.isBlank() || ".".equals(workingDirectoryName)) {
            return extractedDirectory;
        }

        Path workdir = extractedDirectory.resolve(workingDirectoryName);
        return workdir.toFile().exists() ? workdir : extractedDirectory;
    }
}
