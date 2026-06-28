package com.tim8.oblak.core.execution;

public class ExecutionTimeoutException extends RuntimeException {

    public ExecutionTimeoutException(String message) {
        super(message);
    }
}
