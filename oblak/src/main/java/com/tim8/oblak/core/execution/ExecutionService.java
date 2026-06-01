package com.tim8.oblak.core.execution;

import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.core.metadata.ProjectMetadataRepository;
import com.tim8.oblak.firecracker.assets.FirecrackerAssetService;
import com.tim8.oblak.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExecutionService {

    private final FirecrackerAssetService firecrackerAssetService;
    private final MinioService minioService;
    private final ProjectMetadataRepository projectMetadataRepository;
    private final FirecrackerOrchestrator firecrackerOrchestrator;

    public ExecutionResult execute(UUID projectId, String requesterUsername) {
        ProjectMetadata metadata = projectMetadataRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Project not found: " + projectId
                ));

        if (metadata.getOwner() == null
                || !metadata.getOwner().getUsername().equals(requesterUsername)) {
            // Vracamo 404, ne 403, da ne otkrivamo postojanje projekta drugim korisnicima.
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Project not found: " + projectId
            );
        }

        firecrackerAssetService.ensureRequiredAssetsExist();
        Path projectImage = minioService.downloadProjectImage(projectId);
        try {
            return firecrackerOrchestrator.execute(
                    firecrackerAssetService.getKernelPath(),
                    firecrackerAssetService.getRootfsPath(),
                    projectImage,
                    metadata
            );
        } finally {
            try {
                Files.deleteIfExists(projectImage);
            } catch (Exception exception) {
                throw new IllegalStateException("Could not delete temporary project image: " + projectImage, exception);
            }
        }
    }
}