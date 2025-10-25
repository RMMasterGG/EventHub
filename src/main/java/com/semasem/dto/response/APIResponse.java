package com.semasem.dto.response;

import java.time.Instant;

public record APIResponse <T>(String status, String message, T data, Instant time) {

    public APIResponse(String message, T data) {
        this("success", message, data, Instant.now());
    }

    public APIResponse(String message) {
        this("success", message, null, Instant.now());
    }

    public static <T> APIResponse<T> success(String message, T data) {
        return new APIResponse<>(message, data);
    }

    public static <T> APIResponse<T> success(String message) {
        return new APIResponse<>(message);
    }
}
