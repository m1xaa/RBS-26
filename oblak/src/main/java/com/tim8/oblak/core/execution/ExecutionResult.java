package com.tim8.oblak.core.execution;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ExecutionResult {
    private final int exitCode;
    private final String stdout;
    private final String stderr;
    private final String logPath;
}
