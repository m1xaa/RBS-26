package com.tim8.oblak.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class MinioInitConfig {

    private final MinioClient minioClient;

    public MinioInitConfig(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket("projects").build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket("projects").build()
                );
            }

            System.out.println("MinIO projects bucket ready");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}