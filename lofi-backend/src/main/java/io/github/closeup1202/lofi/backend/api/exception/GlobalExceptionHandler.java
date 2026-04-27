package io.github.closeup1202.lofi.backend.api.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CommitNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleCommitNotFound(CommitNotFoundException e) {
        return ErrorResponse.of(HttpStatus.NOT_FOUND.value(), e.getMessage());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleConstraintViolation(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse(e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleMethodArgumentNotValid(MethodArgumentNotValidException e) {
        // getAllErrors() includes both field- and object-level errors, the latter being raised
        // by @Valid cascade into nested collection elements (e.g. metrics[3].className).
        String message = e.getBindingResult().getAllErrors().stream()
                .map(err -> {
                    if (err instanceof org.springframework.validation.FieldError fe) {
                        return fe.getField() + ": " + fe.getDefaultMessage();
                    }
                    return err.getObjectName() + ": " + err.getDefaultMessage();
                })
                .reduce((a, b) -> a + ", " + b)
                .orElse(e.getMessage());
        return ErrorResponse.of(HttpStatus.BAD_REQUEST.value(), message);
    }
}
