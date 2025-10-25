package com.semasem.dto.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {
    USER_ALREADY_EXISTS("AUTH_001", "Пользователь уже существует"),
    USER_NOT_FOUND("AUTH_002", "Пользователь не найден"),
    INVALID_CREDENTIALS("AUTH_003", "Неверные учетные данные"),
    EMAIL_NOT_VERIFIED("AUTH_004", "Email не подтвержден"),
    INVALID_VERIFICATION_CODE("AUTH_005", "Неверный код подтверждения"),
    ALREADY_VERIFIED("AUTH_006", "Пользователь уже верифицирован!"),
    TOKEN_NOT_FOUND("AUTH_007", "Токен не найден"),
    INVALID_TOKEN("AUTH_007", "Невалидный access token"),
    TOKEN_COMPROMISED("AUTH_008", "Токен скомпрометирован"),
    UNAUTHORIZED("AUTH_009", "Пользователь не аутентифицирован"),

    ROOM_NOT_FOUND("ROOM_001", "Комната не найдена"),
    ACCESS_DENIED("ROOM_002", "Нет доступа к комнате"),
    ALREADY_JOINED("ROOM_003", "User already joined this room"),
    ROOM_FULL("ROOM_004", "Room has reached maximum participants"),
    NOT_JOINED("ROOM_005", "User not joined this room"),
    ROOM_NOT_ACTIVE("ROOM_006", "Room is not active"),
    INVALID_INVITE_LINK("ROOM_007", "Invalid invite link"),

    GUEST_ACCESS_DENIED("GUESS_001", "Guest access not allowed"),
    GUEST_EXPIRED("GUESS_002","Guest access has expired"),
    GUESTS_NOT_ALLOWED("GUESS_003","Guest access has not allowed"),

    INVALID_INPUT("CHAT_001", "Неверные данные"),
    MESSAGE_NOT_FOUND("CHAT_002", "Сообщение не найдено"),
    MESSAGE_TOO_OLD("CHAT_003", "Сообщение устарело"),

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
