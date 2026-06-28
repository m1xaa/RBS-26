package com.tim8.oblak.core.analysis;

public record BanditResult(
        int high,
        int medium,
        int low,
        String raw
) {}