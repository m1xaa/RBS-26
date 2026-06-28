package com.tim8.oblak.core.analysis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeAnalysisService {

    private final BanditService banditService;
    private final AntivirusService antivirusService;
    private final DependencyAnalysisService dependencyAnalysisService;

    public List<AnalysisResult> analyzeExtractedFiles(Path extractedDirectory) {
        log.info("Starting code analysis for directory='{}'", extractedDirectory);

        var av = antivirusService.scan(extractedDirectory);
        log.debug("Antivirus scan complete: infectedFiles={}", av.infectedFiles());

        var deps = dependencyAnalysisService.analyze(extractedDirectory);
        log.debug("Dependency analysis complete: vulnerabilities={}, failed={}", deps.vulnerabilities(), deps.failed());

        List<AnalysisResult> results = new ArrayList<>();

        try {
            Files.walk(extractedDirectory)
                    .filter(Files::isRegularFile)
                    .filter(this::isPythonFile)
                    .forEach(path -> results.add(analyzeFile(path, av, deps)));

            long maliciousCount = results.stream().filter(AnalysisResult::isMalicious).count();
            log.info("Analysis complete: totalFiles={}, malicious={}", results.size(), maliciousCount);

            return results;

        } catch (IOException e) {
            log.error("Failed to walk extracted directory '{}'", extractedDirectory, e);
            throw new IllegalStateException(e);
        }
    }

    private AnalysisResult analyzeFile(Path path, ClamResult av, DependencyResult deps) {
        log.debug("Analyzing file='{}'", path);

        try {
            String code = Files.readString(path);
            BanditResult bandit = banditService.scan(code);
            List<String> issues = staticAnalysis(code);

            int score = 0;
            if (isMaliciousByRules(code)) score += 4;
            if (bandit.high() > 0)        score += 3;
            if (bandit.medium() > 0)      score += 1;
            if (av.infectedFiles() > 0)   score += 5;
            if (deps.vulnerabilities() > 0) score += 2;
            if (deps.failed())            score += 4;

            boolean malicious = score >= 4;

            if (malicious) {
                log.warn("Malicious file detected: path='{}', score={}, banditHigh={}, banditMedium={}, infected={}, depVulns={}, staticIssues={}",
                        path, score, bandit.high(), bandit.medium(), av.infectedFiles(), deps.vulnerabilities(), issues);
            } else {
                log.debug("File clean: path='{}', score={}", path, score);
            }

            log.trace("Analysis details for '{}' — bandit:\n{}\nclamav:\n{}\ndeps:\n{}\nissues:\n{}",
                    path, bandit.raw(), av.raw(), deps.raw(), issues);

            return new AnalysisResult(malicious, bandit.raw(), av.raw(), deps.raw(), issues);

        } catch (IOException e) {
            log.error("Failed to read file for analysis: '{}'", path, e);
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
            if (line.contains("eval("))      issues.add("Dangerous eval()");
            if (line.contains("exec("))      issues.add("Dangerous exec()");
            if (line.contains("os.system"))  issues.add("Shell execution");
            if (line.contains("subprocess")) issues.add("Subprocess usage");
            if (line.contains("socket"))     issues.add("Network access");
        }
        return issues;
    }
}