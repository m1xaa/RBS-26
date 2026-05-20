package com.tim8.oblak.minio;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private String buildObjectKey(UUID projectId) {
        return projectId.toString();
    }
}
