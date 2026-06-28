package com.tim8.oblak.core.upload;

import com.tim8.oblak.audit.AuditAction;
import com.tim8.oblak.audit.AuditEvent;
import com.tim8.oblak.audit.AuditOutcome;
import com.tim8.oblak.audit.AuditService;
import com.tim8.oblak.audit.IpResolver;
import com.tim8.oblak.core.analysis.AnalysisResult;
import com.tim8.oblak.core.analysis.CodeAnalysisService;
import com.tim8.oblak.core.execution.ExecutionPreparationService;
import com.tim8.oblak.exception.MaliciousCodeException;
import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.core.metadata.ProjectMetadataService;
import com.tim8.oblak.core.validation.ZipValidation;
import com.tim8.oblak.minio.MinioService;
import com.tim8.oblak.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
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
    private final AuditService auditService;
    private final IpResolver ipResolver;

    public UUID upload(MultipartFile file, User owner) {
        log.info("Upload started: file='{}', owner='{}'", file.getOriginalFilename(), owner.getUsername());

        Path projectImage = null;
        ProjectMetadata metadata = null;

        try {
            zipValidation.validateZipFile(file);
            log.debug("ZIP validation passed for file='{}'", file.getOriginalFilename());

            Path extractedDirectory = zipExtractionService.extractZipToTempDir(file);
            log.debug("ZIP extracted to '{}'", extractedDirectory);

            List<AnalysisResult> results = codeAnalysisService.analyzeExtractedFiles(extractedDirectory);
            boolean malicious = results.stream().anyMatch(AnalysisResult::isMalicious);

            if (malicious) {
                log.warn("Malicious code detected in upload by owner='{}', file='{}'", owner.getUsername(), file.getOriginalFilename());
                auditService.record(AuditEvent.builder(AuditAction.PROJECT_UPLOAD, AuditOutcome.BLOCKED)
                        .actor(owner.getUsername())
                        .detail("Malicious code detected in: " + file.getOriginalFilename())
                        .ip(ipResolver.resolve())
                        .build());
                throw new MaliciousCodeException("Malicious code detected.");
            }

            metadata = projectMetadataService.createPending(file, owner);
            log.info("Project metadata created: projectId='{}', status=PENDING", metadata.getId());

            try {
                Path workdir = resolveProjectWorkdir(extractedDirectory, metadata);
                boolean hasRequirements = workdir.resolve("requirements.txt").toFile().exists();
                log.debug("Resolved workdir='{}', hasRequirements={}", workdir, hasRequirements);

                projectImage = projectFilesystemImageService.createExt4Image(extractedDirectory, metadata.getId());

                if (hasRequirements) {
                    log.info("Installing dependencies for projectId='{}'", metadata.getId());
                    executionPreparationService.prepareProjectImage(projectImage, metadata);
                }

                String minioKey = minioService.uploadProjectImage(projectImage, metadata.getId());
                projectMetadataService.markCompleted(metadata, minioKey);

                auditService.record(AuditEvent.builder(AuditAction.PROJECT_UPLOAD, AuditOutcome.SUCCESS)
                        .actor(owner.getUsername())
                        .resource(metadata.getId())
                        .detail("file=" + file.getOriginalFilename())
                        .ip(ipResolver.resolve())
                        .build());

                log.info("Upload completed successfully: projectId='{}'", metadata.getId());
                return metadata.getId();

            } catch (RuntimeException exception) {
                log.error("Upload failed after metadata creation: projectId='{}', reason='{}'",
                        metadata.getId(), exception.getMessage(), exception);
                projectMetadataService.markFailed(metadata);

                auditService.record(AuditEvent.builder(AuditAction.PROJECT_UPLOAD, AuditOutcome.FAILURE)
                        .actor(owner.getUsername())
                        .resource(metadata.getId())
                        .detail(exception.getMessage())
                        .ip(ipResolver.resolve())
                        .build());

                throw exception;
            }
        } finally {
            log.debug("Cleaning up temporary resources");
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
        if (workdir.toFile().exists()) {
            return workdir;
        }

        log.warn("Specified working directory '{}' not found in archive, falling back to root", workingDirectoryName);
        return extractedDirectory;
    }
}