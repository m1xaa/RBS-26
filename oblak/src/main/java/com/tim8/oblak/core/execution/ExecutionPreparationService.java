package com.tim8.oblak.core.execution;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class ExecutionPreparationService {

    public void prepare(Path projectDirectory) {

        try {

            Path requirements =
                    projectDirectory.resolve("requirements.txt");

            if (!requirements.toFile().exists()) {
                return;
            }

            Process process = new ProcessBuilder(
                    "python3",
                    "-m",
                    "venv",
                    projectDirectory.resolve("venv").toString()
            )
                    .directory(projectDirectory.toFile())
                    .start();

            process.waitFor();

            Process installProcess = new ProcessBuilder(
                    projectDirectory.resolve("venv/bin/pip").toString(),
                    "install",
                    "-r",
                    "requirements.txt"
            )
                    .directory(projectDirectory.toFile())
                    .start();

            installProcess.waitFor();

        } catch (IOException | InterruptedException exception) {

            Thread.currentThread().interrupt();

            throw new IllegalStateException(
                    "Could not prepare execution environment.",
                    exception
            );
        }
    }
}