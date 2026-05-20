package com.tim8.oblak.core.analysis;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
public class CodeAnalysisService {

    private final BanditService banditService;

    public CodeAnalysisService(BanditService banditService) {
        this.banditService = banditService;
    }

    public AnalysisResult analyze(String code) {

        String bandit = banditService.scan(code);
        List<String> issues = staticAnalysis(code);

        boolean malicious =
                isMaliciousByRules(code) ||
                        bandit.contains("HIGH") ||
                        bandit.contains("MEDIUM");

        return new AnalysisResult(malicious, bandit, issues);
    }

    public List<AnalysisResult> analyzeExtractedFiles(Path extractedDirectory) {
        try (Stream<Path> paths = Files.walk(extractedDirectory)) {
            return paths
                .filter(Files::isRegularFile)
                .map(this::analyzeFile)
                .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read extracted files.", exception);
        }
    }

    private AnalysisResult analyzeFile(Path path) {
        try {
            String code = Files.readString(path);
            return analyze(code);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read extracted file: " + path, exception);
        }
    }

    private boolean isMaliciousByRules(String code) {

        String lower = code.toLowerCase();

        return lower.contains("rm -rf")
                || lower.contains("os.system")
                || lower.contains("subprocess")
                || lower.contains("eval(")
                || lower.contains("exec(");
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
        }

        return issues;
    }
}
