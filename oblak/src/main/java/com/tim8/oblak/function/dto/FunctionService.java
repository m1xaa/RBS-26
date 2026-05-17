package com.tim8.oblak.function.dto;

import com.tim8.oblak.function.Function;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class FunctionService {

    private final FunctionRepository repository;

    public FunctionService(FunctionRepository repository) {
        this.repository = repository;
    }

    public Function save(String name, String code) {
        Function f = new Function();
        f.setName(name);
        f.setCode(code);
        f.setStatus("UPLOADED");
        return repository.save(f);
    }

    public String execute(Long id) {
        Function function = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Function not found"));

        try {
            Path file = Files.createTempFile("func-", ".py");
            Files.writeString(file, function.getCode());

            Process process = new ProcessBuilder("python3", file.toString())
                    .redirectErrorStream(true)
                    .start();

            boolean finished = process.waitFor(5, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                return "Execution timeout";
            }

            String output = new String(process.getInputStream().readAllBytes());

            return output;

        } catch (Exception e) {
            return "Execution error: " + e.getMessage();
        }
    }
}