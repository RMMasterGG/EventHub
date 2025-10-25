package com.semasem.dto.request;

import com.semasem.repository.entity.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ChatMessageRequest {
    @NotBlank(message = "Content cannot be empty")
    private String content;

    @NotNull(message = "Message type is required")
    private MessageType type;

    private UUID replyTo;
}
