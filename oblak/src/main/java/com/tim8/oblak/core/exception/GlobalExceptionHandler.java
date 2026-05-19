package com.tim8.oblak.core.exception;

import com.tim8.oblak.core.validation.ZipValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ZipValidationException.class)
    public ResponseEntity<ApiError> handleZipValidationException(ZipValidationException exception) {
        ApiError error = new ApiError(HttpStatus.BAD_REQUEST.value(), exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    private record ApiError(int status, String message) {
    }
}
