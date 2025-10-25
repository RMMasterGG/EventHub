package com.semasem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuppressWarnings("unused")
public class ChatMessage {
    private String type; // "CHAT_MESSAGE"
    private String roomId;
    private String sender;
    private String text;
    private String timestamp;
}
