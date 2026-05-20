package com.tim8.oblak.CloudProject;

import com.tim8.oblak.CloudProject.dto.ProjectUploadRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class CloudProjectController {

    private final CloudProjectService service;

    public CloudProjectController(CloudProjectService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public CloudProject upload(@RequestBody ProjectUploadRequest request,
                               Authentication auth) {
        return service.save(request.getName(), request.getFiles(), auth.getName());
    }

    @PostMapping("/{id}/execute")
    public String execute(@PathVariable Long id, Authentication auth) {
        return service.execute(id, auth);
    }

    /**
     * Lista projekata: USER vidi samo svoje, ADMIN vidi sve.
     * Vraca i osnovni list endpoint koji je CLI-u koristan za 'cdk list'
     * (umesto da CLI cuva mapiranje lokalno).
     */
    @GetMapping
    public List<CloudProject> list(Authentication auth) {
        return service.list(auth);
    }
}
