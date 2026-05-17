package com.tim8.oblak.CloudProject;

import com.tim8.oblak.CloudProject.dto.ProjectUploadRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects")
public class CloudProjectController {

    private final CloudProjectService service;

    public CloudProjectController(CloudProjectService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public CloudProject upload(@RequestBody ProjectUploadRequest request) {
        return service.save(request.getName(), request.getFiles());
    }

    @PostMapping("/{id}/execute")
    public String execute(@PathVariable Long id) {
        return service.execute(id);
    }
}