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

        try {
            zipValidation.validateZipFile(file);
            Path extractedDirectory = zipExtractionService.extractZipToTempDir(file);

            List<AnalysisResult> results = codeAnalysisService.analyzeExtractedFiles(extractedDirectory);
            boolean malicious =
                    results.stream()
                            .anyMatch(AnalysisResult::isMalicious);
            if (malicious) {
                throw new MaliciousCodeException("Malicious code detected.");
            }

            // Priprema zavisnosti pre nego sto pakujemo u ext4 image.
            // resolveWorkingDirectory daje isti workdir koji ce metadata imati.
            String workingDirName = projectMetadataService
                    .resolveWorkingDirectory(file.getOriginalFilename());
            Path workdir = extractedDirectory.resolve(workingDirName);
            if (!workdir.toFile().exists()) {
                workdir = extractedDirectory;
            }
            executionPreparationService.prepare(workdir);

            ProjectMetadata metadata = projectMetadataService.createPending(file, owner);
            try {
                projectImage = projectFilesystemImageService.createExt4Image(extractedDirectory, metadata.getId());
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
}