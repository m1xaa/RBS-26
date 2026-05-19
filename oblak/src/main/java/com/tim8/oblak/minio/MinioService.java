package com.tim8.oblak.minio;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioService {

    private static final String DEFAULT_CONTENT_TYPE = "application/zip";

    private final MinioClient minioClient;

    @Value("${oblak.minio.bucket}")
    private String bucket;

    public String uploadProject(MultipartFile file, UUID projectId) {
        String objectKey = buildObjectKey(projectId);

        try {
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectKey)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(resolveContentType(file))
                    .build()
            );

            return objectKey;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not upload project to MinIO.", exception);
        }
    }

    private String buildObjectKey(UUID projectId) {
        return projectId.toString();
    }

    private String resolveContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }

        return contentType;
    }
}
