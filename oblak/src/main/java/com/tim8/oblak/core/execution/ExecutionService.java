package com.tim8.oblak.core.execution;

import com.tim8.oblak.audit.AuditAction;
import com.tim8.oblak.audit.AuditEvent;
import com.tim8.oblak.audit.AuditOutcome;
import com.tim8.oblak.audit.AuditService;
import com.tim8.oblak.audit.IpResolver;
import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.core.metadata.ProjectMetadataRepository;
import com.tim8.oblak.firecracker.assets.FirecrackerAssetService;
import com.tim8.oblak.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final FirecrackerAssetService firecrackerAssetService;
    private final MinioService minioService;
    private final ProjectMetadataRepository projectMetadataRepository;
    private final FirecrackerOrchestrator firecrackerOrchestrator;
    private final AuditService auditService;
    private final IpResolver ipResolver;

    public ExecutionResult execute(UUID projectId, String requesterUsername) {
        log.info("Execution requested: projectId='{}', requester='{}'", projectId, requesterUsername);

        ProjectMetadata metadata = projectMetadataRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.warn("Execution rejected — project not found: projectId='{}', requester='{}'",
                            projectId, requesterUsername);
                    auditService.record(AuditEvent.builder(AuditAction.PROJECT_EXECUTE, AuditOutcome.REJECTED)
                            .actor(requesterUsername)
                            .resource(projectId)
                            .detail("Project not found")
                            .ip(ipResolver.resolve())
                            .build());
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
                });

        if (metadata.getOwner() == null
                || !metadata.getOwner().getUsername().equals(requesterUsername)) {
            log.warn("Execution rejected — requester '{}' is not the owner of projectId='{}'",
                    requesterUsername, projectId);
            auditService.record(AuditEvent.builder(AuditAction.PROJECT_EXECUTE, AuditOutcome.REJECTED)
                    .actor(requesterUsername)
                    .resource(projectId)
                    .detail("Not the owner")
                    .ip(ipResolver.resolve())
                    .build());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
        }

        firecrackerAssetService.ensureRequiredAssetsExist();

        Path projectImage = minioService.downloadProjectImage(projectId);
        log.debug("Project image downloaded for projectId='{}': path='{}'", projectId, projectImage);

        try {
            log.info("Launching Firecracker VM for projectId='{}'", projectId);
            ExecutionResult result = firecrackerOrchestrator.execute(
                    firecrackerAssetService.getKernelPath(),
                    firecrackerAssetService.getRootfsPath(),
                    projectImage,
                    metadata
            );

            auditService.record(AuditEvent.builder(AuditAction.PROJECT_EXECUTE, AuditOutcome.SUCCESS)
                    .actor(requesterUsername)
                    .resource(projectId)
                    .ip(ipResolver.resolve())
                    .build());

            log.info("Execution finished for projectId='{}'", projectId);
            return result;

        } finally {
            try {
                Files.deleteIfExists(projectImage);
                log.debug("Temporary project image deleted: '{}'", projectImage);
            } catch (Exception exception) {
                log.error("Failed to delete temporary project image: '{}'", projectImage, exception);
                throw new IllegalStateException("Could not delete temporary project image: " + projectImage, exception);
            }
        }
    }
}