package com.mihajlo.backend.controller;

import com.mihajlo.backend.controller.api.RunnableYaml;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

@Controller
@RequestMapping("/api/weather")
@CrossOrigin(origins = "http://localhost:4200")
public class VulnerableYAMLController {

    @PostMapping
    public ResponseEntity<?> parseYaml(@RequestBody String yaml) {
        RunnableYaml yamlTask = new RunnableYaml(yaml);

        yamlTask.getCommand().run();

        return ResponseEntity.ok(Collections.singleton(yamlTask.getExecutionResult()));
    }

    @GetMapping
    public void successfulVulnerability() {
        System.out.println("CVE-2022-1471 success");
    }
}
