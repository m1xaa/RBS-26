package com.tim8.oblak.CloudProject;

import com.tim8.oblak.analysis.AnalysisResult;
import com.tim8.oblak.analysis.CodeAnalysisService;
import com.tim8.oblak.minio.MinioService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
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

    public CloudProject save(String name, Map<String, String> files, String ownerUsername) {

        String mainCode = files.get("main.py");

        if (mainCode == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "main.py is required");
        }

        AnalysisResult result = analysisService.analyze(mainCode);

        if (result.isMalicious()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Malicious code detected by analysis pipeline");
        }

        CloudProject project = new CloudProject();
        project.setName(name);
        project.setStatus("ANALYZED");
        project.setOwnerUsername(ownerUsername);

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

    public List<CloudProject> list(Authentication auth) {
        if (isAdmin(auth)) {
            return repository.findAll();
        }
        return repository.findByOwnerUsername(auth.getName());
    }

    public String execute(Long id, Authentication auth) {
        CloudProject project = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Project not found"));

        // Autorizacija: vlasnik moze sve, admin moze sve, ostali ne.
        if (!isAdmin(auth) && !project.getOwnerUsername().equals(auth.getName())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Nije dozvoljen pristup ovom projektu");
        }

        try {
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

    private boolean isAdmin(Authentication auth) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            if ("ROLE_ADMIN".equals(a.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
