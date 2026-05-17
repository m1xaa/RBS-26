package com.tim8.oblak.CloudProject;

import com.tim8.oblak.minio.MinioService;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CloudProjectService {

    private final CloudProjectRepository repository;
    private final MinioService minioService;

    public CloudProjectService(CloudProjectRepository repository,
                               MinioService minioService) {
        this.repository = repository;
        this.minioService = minioService;
    }

    public CloudProject save(String name, Map<String, String> files) {

        CloudProject project = new CloudProject();
        project.setName(name);
        project.setStatus("UPLOADED");

        CloudProject saved = repository.save(project);

        for (Map.Entry<String, String> file : files.entrySet()) {
            minioService.uploadFile(
                    saved.getId(),
                    file.getKey(),
                    file.getValue()
            );
        }

        return saved;
    }

    public String execute(Long id) {

        try {
            CloudProject project = repository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Project not found"));

            String code = minioService.getFile(id, "main.py");

            Path file = Files.createTempFile("project-", ".py");
            Files.writeString(file, code);

            Process process = new ProcessBuilder("python3", file.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(10, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "Timeout";
            }

            return new String(process.getInputStream().readAllBytes());

        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}