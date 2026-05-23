package com.tim8.oblak.core.execution;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/execute")
public class ExecutionController {

    private final ExecutionService executionService;

    public ExecutionController(ExecutionService executionService) {
        this.executionService = executionService;
    }

    @PostMapping("/{url}")
    public ResponseEntity<ExecutionResult> execute(@PathVariable String url) {
        ExecutionResult executionResult = executionService.execute(url);
        System.out.println("Execution result: " + executionResult);
        return ResponseEntity.ok(executionResult);
    }
}
