package com.tim8.oblak.core.analysis;

import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class DependencyAnalysisService {

    public DependencyResult analyze(Path extractedDirectory) {

        try {
            Path requirements = resolveRequirementsFile(extractedDirectory);

            if (requirements == null) {
                return new DependencyResult(
                        false,
                        0,
                        "No requirements.txt",
                        false
                );
            }

            Process process = new ProcessBuilder(
                    "pip-audit",
                    "-r",
                    requirements.toString(),
                    "--format=json"
            )
                    .redirectErrorStream(true)
                    .start();

            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream())
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            String raw = output.toString();

            boolean failed =
                    exitCode != 0 ||
                            raw.contains("Could not find a version") ||
                            raw.contains("ERROR") ||
                            raw.contains("Failed");

            int vulnerabilities = 0;

            if (raw.contains("\"vulnerabilities\"")) {
                vulnerabilities = raw.split("vulnerability").length - 1;
            }

            return new DependencyResult(
                    failed,
                    vulnerabilities,
                    raw,
                    failed || vulnerabilities > 0
            );

        } catch (Exception e) {
            return new DependencyResult(
                    true,
                    0,
                    "Dependency analysis error: " + e.getMessage(),
                    true
            );
        }
    }

    private Path resolveRequirementsFile(Path extractedDirectory) throws Exception {
        Path rootRequirements = extractedDirectory.resolve("requirements.txt");
        if (Files.exists(rootRequirements)) {
            return rootRequirements;
        }

        try (Stream<Path> walk = Files.walk(extractedDirectory)) {
            List<Path> matches = walk
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equalsIgnoreCase("requirements.txt"))
                    .sorted(Comparator.comparingInt(path -> path.getNameCount()))
                    .toList();

            if (matches.isEmpty()) {
                return null;
            }

            return matches.get(0);
        }
    }
}
