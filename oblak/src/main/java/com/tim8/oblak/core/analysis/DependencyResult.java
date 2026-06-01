package com.tim8.oblak.core.analysis;

public record DependencyResult(
        boolean failed,
        int vulnerabilities,
        String raw,
        boolean malicious
) {}