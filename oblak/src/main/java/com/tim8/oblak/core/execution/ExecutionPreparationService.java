package com.tim8.oblak.core.execution;

import com.tim8.oblak.core.metadata.ProjectMetadata;
import com.tim8.oblak.firecracker.assets.FirecrackerAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class ExecutionPreparationService {

    private final FirecrackerAssetService firecrackerAssetService;
    private final FirecrackerOrchestrator firecrackerOrchestrator;

    public void prepareProjectImage(Path projectImage, ProjectMetadata metadata) {
        firecrackerAssetService.ensureRequiredAssetsExist();
        firecrackerOrchestrator.prepareProjectImage(
                firecrackerAssetService.getKernelPath(),
                firecrackerAssetService.getRootfsPath(),
                projectImage,
                metadata
        );
    }
}
