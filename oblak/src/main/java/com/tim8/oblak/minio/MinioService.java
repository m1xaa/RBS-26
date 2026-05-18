package com.tim8.oblak.minio;

import io.minio.*;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
public class MinioService {

    private final MinioClient minioClient;

    public MinioService(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    public void uploadFile(Long projectId, String filename, String content) {
        try {
            ByteArrayInputStream stream =
                    new ByteArrayInputStream(content.getBytes());

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket("projects")
                            .object(projectId + "/" + filename)
                            .stream(stream, content.length(), -1)
                            .contentType("text/plain")
                            .build()
            );

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getFile(Long projectId, String filename) {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket("projects")
                        .object(projectId + "/" + filename)
                        .build()
        )) {
            return new String(stream.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}