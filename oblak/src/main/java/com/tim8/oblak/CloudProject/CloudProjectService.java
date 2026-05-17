package com.tim8.oblak.CloudProject;

import com.tim8.oblak.analysis.AnalysisResult;
import com.tim8.oblak.analysis.CodeAnalysisService;
import com.tim8.oblak.minio.MinioService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class CloudProjectService {

    private final CloudProjectRepository repository;
    private final MinioService minioService;
    private final CodeAnalysisService analysisService;

    public CloudProjectService(CloudProjectRepository repository,
                               MinioService minioService,
                               CodeAnalysisService analysisService) {
        this.repository = repository;
        this.minioService = minioService;
        this.analysisService = analysisService;
    }

    public CloudProject save(String name, Map<String, String> files) {

        String mainCode = files.get("main.py");

        if (mainCode == null) {
            throw new RuntimeException("main.py is required");
        }

        AnalysisResult result = analysisService.analyze(mainCode);

        if (result.isMalicious()) {
            throw new RuntimeException("Malicious code detected by analysis pipeline");
        }

        CloudProject project = new CloudProject();
        project.setName(name);
        project.setStatus("ANALYZED");

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

            java.nio.file.Path file =
                    java.nio.file.Files.createTempFile("project-", ".py");

            java.nio.file.Files.writeString(file, code);

            Process process = new ProcessBuilder("python3", file.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);

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