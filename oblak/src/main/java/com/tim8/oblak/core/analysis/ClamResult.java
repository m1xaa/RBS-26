package com.tim8.oblak.core.analysis;

public record ClamResult(
        int infectedFiles,
        String raw
) {}