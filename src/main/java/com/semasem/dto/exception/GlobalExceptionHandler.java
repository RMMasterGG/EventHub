package com.semasem.dto.exception;

import com.semasem.dto.response.ApiError;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

import static com.semasem.dto.exception.ErrorCode.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleIllegalState(IllegalStateException ex, WebRequest request) {

        log.warn("Error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(USER_ALREADY_EXISTS));
    }


    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });


        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(ApiError.of(VALIDATION_ERROR, errors));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiError> handleRunTime(RuntimeException ex, WebRequest request) {

        log.warn("Runtime error: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(ApiError.of(INTERNAL_ERROR));
    }

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ApiError> handleCustom(CustomException ex, WebRequest request) {

        log.warn("CustomError: {}", ex.getCustomMessage());
        return ResponseEntity.status(determineHttpStatus(ex.getErrorCode())).body(ApiError.of(ex.getErrorCode()));
    }

    private HttpStatus determineHttpStatus(ErrorCode errorCode) {
        return switch (errorCode) {
            case USER_ALREADY_EXISTS -> HttpStatus.CONFLICT;
            case USER_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case INVALID_CREDENTIALS, INVALID_VERIFICATION_CODE -> HttpStatus.UNAUTHORIZED;
            case EMAIL_NOT_VERIFIED -> HttpStatus.FORBIDDEN;
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
