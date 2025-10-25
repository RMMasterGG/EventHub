package com.semasem.dto.response;

import com.semasem.dto.exception.ErrorCode;

import java.time.Instant;

public record ApiError(String status, ErrorDetails error, Instant time) {

    public ApiError(ErrorDetails error) {
        this("error", error, Instant.now());
    }

    public static ApiError of(ErrorCode errorCode, Object details) {
        return new ApiError(new ErrorDetails(errorCode.getCode(), errorCode.getMessage(), details));
    }

    public static ApiError of(ErrorCode errorCode) {
        return new ApiError(new ErrorDetails(errorCode.getCode(), errorCode.getMessage(), null));
    }

    public record ErrorDetails(String code, String message, Object details) {}

}
