package com.tim8.oblak.minio;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${oblak.minio.bucket}")
    private String bucket;

    public String uploadProjectImage(Path imagePath, UUID projectId) {
        String objectKey = buildObjectKey(projectId);

        try (InputStream inputStream = Files.newInputStream(imagePath)) {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(inputStream, Files.size(imagePath), -1)
                    .contentType("application/octet-stream")
                    .build()
            );

            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not upload project image to MinIO.", exception);
        }
    }

    public Path downloadProjectImage(UUID projectId) {
        String objectKey = buildObjectKey(projectId);

        try {
            Path tempFile = Files.createTempFile("project-image-" + projectId + "-", ".ext4");
            try (InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectKey)
                        .build()
            )) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return tempFile;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not download project image from MinIO.", exception);
        }
    }

    private String buildObjectKey(UUID projectId) {
        return projectId.toString();
    }
}
