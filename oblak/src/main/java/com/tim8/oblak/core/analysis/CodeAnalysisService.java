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

        var av = antivirusService.scan(extractedDirectory);
        var deps = dependencyAnalysisService.analyze(extractedDirectory);

        List<AnalysisResult> results = new ArrayList<>();

        try {
            Files.walk(extractedDirectory)
                    .filter(Files::isRegularFile)
                    .filter(this::isPythonFile)
                    .forEach(path -> results.add(
                            analyzeFile(path, av, deps)
                    ));

            return results;

        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private AnalysisResult analyzeFile(
            Path path,
            ClamResult av,
            DependencyResult deps
    ) {

        try {
            String code = Files.readString(path);

            BanditResult bandit = banditService.scan(code);

            List<String> issues = staticAnalysis(code);

            int score = 0;

            // static rules
            if (isMaliciousByRules(code)) score += 4;

            // bandit
            if (bandit.high() > 0) score += 3;
            if (bandit.medium() > 0) score += 1;

            // clamAV
            if (av.infectedFiles() > 0) score += 5;

            // dependencies
            if (deps.vulnerabilities() > 0) score += 2;
            if (deps.failed()) score += 4;

            boolean malicious = score >= 4;
            System.out.println("===== FILE ===== " + path);
            System.out.println("BANDIT:\n" + bandit.raw());
            System.out.println("CLAMAV:\n" + av.raw());
            System.out.println("DEPS:\n" + deps.raw());
            System.out.println("ISSUES:\n" + issues);
            System.out.println("SCORE: " + score + " MALICIOUS: " + malicious);
            return new AnalysisResult(
                    malicious,
                    bandit.raw(),
                    av.raw(),
                    deps.raw(),
                    issues
            );

        } catch (IOException e) {
            throw new IllegalStateException(e);
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
            if (line.contains("eval(")) issues.add("Dangerous eval()");
            if (line.contains("exec(")) issues.add("Dangerous exec()");
            if (line.contains("os.system")) issues.add("Shell execution");
            if (line.contains("subprocess")) issues.add("Subprocess usage");
            if (line.contains("socket")) issues.add("Network access");
        }

        return issues;
    }
}