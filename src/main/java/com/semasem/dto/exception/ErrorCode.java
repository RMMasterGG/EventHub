package com.semasem.dto.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    USER_ALREADY_EXISTS("AUTH_001", "Пользователь уже существует"),
    USER_NOT_FOUND("AUTH_002", "Пользователь не найден"),
    INVALID_CREDENTIALS("AUTH_003", "Неверные учетные данные"),
    EMAIL_NOT_VERIFIED("AUTH_004", "Email не подтвержден"),
    INVALID_VERIFICATION_CODE("AUTH_005", "Неверный код подтверждения"),

    ROOM_NOT_FOUND("ROOM_001", "Комната не найдена"),
    ACCESS_DENIED("ROOM_002", "Нет доступа к комнате"),
    ALREADY_JOINED("ROOM_003", "User already joined this room"),
    ROOM_FULL("ROOM_004", "Room has reached maximum participants"),
    NOT_JOINED("ROOM_005", "User not joined this room"),
    ROOM_NOT_ACTIVE("ROOM_006", "Room is not active"),

    VALIDATION_ERROR("VALID_001", "Ошибка валидации"),

    OBJECT_NOT_FOUND("OBJECT_001", "Данный объект не найден"),

    INTERNAL_ERROR("SYS_001", "Внутренняя ошибка сервера"),
    NOT_IMPLEMENTED("SYS_002", "Функционал не реализован");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
