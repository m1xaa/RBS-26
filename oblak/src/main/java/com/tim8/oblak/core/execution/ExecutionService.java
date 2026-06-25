package com.tim8.oblak.core.execution;

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

    public ExecutionResult execute(UUID projectId, String requesterUsername) {
        log.info("Execution requested: projectId='{}', requester='{}'", projectId, requesterUsername);

        ProjectMetadata metadata = projectMetadataRepository.findById(projectId)
                .orElseThrow(() -> {
                    log.warn("Execution rejected — project not found: projectId='{}', requester='{}'", projectId, requesterUsername);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId);
                });

        if (metadata.getOwner() == null || !metadata.getOwner().getUsername().equals(requesterUsername)) {
            log.warn("Execution rejected — requester '{}' is not the owner of projectId='{}'", requesterUsername, projectId);
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