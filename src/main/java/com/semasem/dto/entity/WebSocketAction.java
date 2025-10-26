package com.semasem.dto.entity;

public enum WebSocketAction {
    SYSTEM_MESSAGE,
    SEND_MESSAGE,
    EDIT_MESSAGE,
    MESSAGE_READ,
    DELETE_MESSAGE,
    MESSAGE_DELETED,
    MESSAGE_EDITED,
    TYPING_START,
    TYPING_STOP,
    KICK_USER,
    USER_TYPING,
    READ_RECEIPT,
    ERROR,
    CHAT_HISTORY
}
