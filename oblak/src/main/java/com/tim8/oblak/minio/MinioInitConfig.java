package com.tim8.oblak.minio;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioInitConfig {

    private final MinioClient minioClient;

    @Value("${oblak.minio.bucket}")
    private String bucket;

    public MinioInitConfig(MinioClient minioClient) {
        this.minioClient = minioClient;
    }

    @PostConstruct
    public void init() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucket).build()
            );

            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucket).build()
                );
            }

            System.out.println("MinIO " + bucket + " bucket ready");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
