package com.tim8.oblak.core.metadata;

import com.tim8.oblak.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ProjectMetadataService {

    private final ProjectMetadataRepository projectMetadataRepository;

    public ProjectMetadata createPending(MultipartFile file, User owner) {
        String originalFilename = file.getOriginalFilename();
        ProjectMetadata metadata = new ProjectMetadata();
        metadata.setName(originalFilename);
        metadata.setSize(file.getSize());
        metadata.setWorkingDirectory(resolveWorkingDirectory(originalFilename));
        metadata.setRootFile("main.py");
        metadata.setUploadStatus(ProjectUploadStatus.PENDING);
        metadata.setOwner(owner);

        return projectMetadataRepository.save(metadata);
    }

    public String resolveWorkingDirectory(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return ".";
        }

        if (originalFilename.toLowerCase().endsWith(".zip")) {
            return originalFilename.substring(0, originalFilename.length() - 4);
        }

        return originalFilename;
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