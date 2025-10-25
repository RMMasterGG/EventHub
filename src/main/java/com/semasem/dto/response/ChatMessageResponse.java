package com.semasem.dto.response;

import com.semasem.repository.entity.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private UUID uuid;
    private String content;
    private MessageType type;
    private Instant timestamp;
    private boolean edited;
    private Instant editedAt;
    private UUID replyTo;

    private String senderName;
    private String senderEmail;
    private String senderAvatar;

    private String repliedMessageContent;
    private String repliedMessageSender;
}
