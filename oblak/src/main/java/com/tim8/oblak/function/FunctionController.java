package com.tim8.oblak.function;

import com.tim8.oblak.function.dto.FunctionService;
import com.tim8.oblak.function.dto.FunctionUploadRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/functions")
public class FunctionController {

    private final FunctionService service;

    public FunctionController(FunctionService service) {
        this.service = service;
    }

    @PostMapping("/upload")
    public Function upload(@RequestBody FunctionUploadRequest request) {
        return service.save(request.getName(), request.getCode());
    }

    @PostMapping("/{id}/execute")
    public String execute(@PathVariable Long id) {
        return service.execute(id);
    }
}