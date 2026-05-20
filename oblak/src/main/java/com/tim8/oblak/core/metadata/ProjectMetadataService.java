package com.tim8.oblak.core.metadata;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectMetadataService {

    private final ProjectMetadataRepository projectMetadataRepository;

    public ProjectMetadata createPending(MultipartFile file) {
        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setName(file.getOriginalFilename());
        metadata.setSize(file.getSize());
        metadata.setWorkingDirectory(".");
        metadata.setRootFile("main.py");
        metadata.setUploadStatus(ProjectUploadStatus.PENDING);

        return projectMetadataRepository.save(metadata);
    }

    public ProjectMetadata markCompleted(ProjectMetadata metadata, String minioKey) {
        metadata.setMinioKey(minioKey);
        metadata.setUploadStatus(ProjectUploadStatus.COMPLETED);

        return projectMetadataRepository.save(metadata);
    }

    public ProjectMetadata markFailed(ProjectMetadata metadata) {
        metadata.setUploadStatus(ProjectUploadStatus.FAILED);

        return projectMetadataRepository.save(metadata);
    }
}
