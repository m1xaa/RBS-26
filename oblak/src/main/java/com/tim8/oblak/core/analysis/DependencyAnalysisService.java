package com.tim8.oblak.core.analysis;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class DependencyAnalysisService {

    public String analyze(Path extractedDirectory) {

        try {

            Path requirements = extractedDirectory.resolve("requirements.txt");

            if (!Files.exists(requirements)) {
                return "No requirements.txt";
            }

            Process process = new ProcessBuilder(
                    "pip-audit",
                    "-r",
                    requirements.toString()
            )
                    .redirectErrorStream(true)
                    .start();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            );

            StringBuilder output = new StringBuilder();

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            process.waitFor();

            return output.toString();

        } catch (Exception exception) {
            return "Dependency analysis error: " + exception.getMessage();
        }
    }
}