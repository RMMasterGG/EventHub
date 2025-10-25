package com.semasem.dto.exception;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String customMessage;
    private final Object details;

    public CustomException(ErrorCode errorCode, String customMessage, Object details) {
        super(customMessage != null ? customMessage : errorCode.getMessage());
        this.errorCode = errorCode;
        this.customMessage = customMessage;
        this.details = details;
    }

    public CustomException(ErrorCode errorCode) {
        this(errorCode, null, null);
    }

    public CustomException(ErrorCode errorCode, String customMessage) {
        this(errorCode, customMessage, null);
    }

    public CustomException(ErrorCode errorCode, Object details) {
        this(errorCode, null, details);
    }
}