package com.tim8.oblak.core.analysis;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CodeAnalysisService {

    private final BanditService banditService;
    private final AntivirusService antivirusService;
    private final DependencyAnalysisService dependencyAnalysisService;

    public List<AnalysisResult> analyzeExtractedFiles(Path extractedDirectory) {

        String antivirusOutput = antivirusService.scan(extractedDirectory);

        String dependencyOutput =
                dependencyAnalysisService.analyze(extractedDirectory);

        List<AnalysisResult> results = new ArrayList<>();

        try {

            Files.walk(extractedDirectory)
                    .filter(Files::isRegularFile)
                    .filter(this::isPythonFile)
                    .forEach(path -> {
                        results.add(
                                analyzeFile(
                                        path,
                                        antivirusOutput,
                                        dependencyOutput
                                )
                        );
                    });

            return results;

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not analyze extracted files.",
                    exception
            );
        }
    }

    private AnalysisResult analyzeFile(
            Path path,
            String antivirusOutput,
            String dependencyOutput
    ) {

        try {

            String code = Files.readString(path);

            String banditOutput = banditService.scan(code);

            List<String> issues = staticAnalysis(code);

            boolean malicious =
                    isMaliciousByRules(code)
                            || banditOutput.contains("HIGH")
                            || banditOutput.contains("MEDIUM")
                            || antivirusOutput.contains("FOUND")
                            || dependencyOutput.contains("VULNERABILITY");

            return new AnalysisResult(
                    malicious,
                    banditOutput,
                    antivirusOutput,
                    dependencyOutput,
                    issues
            );

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not read file: " + path,
                    exception
            );
        }
    }

    private boolean isPythonFile(Path path) {
        return path.toString().endsWith(".py");
    }

    private boolean isMaliciousByRules(String code) {

        String lower = code.toLowerCase();

        return lower.contains("rm -rf")
                || lower.contains("os.system")
                || lower.contains("subprocess")
                || lower.contains("eval(")
                || lower.contains("exec(")
                || lower.contains("socket")
                || lower.contains("fork")
                || lower.contains("pty")
                || lower.contains("ctypes");
    }

    private List<String> staticAnalysis(String code) {

        List<String> issues = new ArrayList<>();

        for (String line : code.lines().toList()) {

            if (line.contains("eval(")) {
                issues.add("Dangerous usage of eval()");
            }

            if (line.contains("exec(")) {
                issues.add("Dangerous usage of exec()");
            }

            if (line.contains("while True")) {
                issues.add("Possible infinite loop");
            }

            if (line.contains("os.system")) {
                issues.add("Shell execution detected");
            }

            if (line.contains("subprocess")) {
                issues.add("Subprocess execution detected");
            }

            if (line.contains("socket")) {
                issues.add("Network communication detected");
            }
        }

        return issues;
    }
}