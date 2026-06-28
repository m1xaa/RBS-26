package com.tim8.oblak.core.analysis;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AnalysisResult {

    private boolean malicious;

    private String banditOutput;

    private String antivirusOutput;

    private String dependencyOutput;

    private List<String> issues;
}