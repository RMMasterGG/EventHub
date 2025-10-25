package com.semasem.controller;

import com.semasem.dto.request.ChatMessageRequest;
import com.semasem.dto.response.ChatMessageResponse;
import com.semasem.service.ChatService;
import com.semasem.service.WebRTCService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Real-time chat WebSocket endpoints")
public class WebSocketChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final WebRTCService webRTCService;


    @Operation(summary = "Subscribe to chat", description = "WebSocket endpoint for chat messages")
    @SubscribeMapping("/room/{roomId}/chat")
    public void subscribeToChat(@DestinationVariable UUID roomId, Principal principal) {
        log.info("User {} subscribed to chat for room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);
    }

    @MessageMapping("/room/{roomId}/chat/send")
    public void sendMessage(@DestinationVariable UUID roomId,
                            @Payload ChatMessageRequest messageRequest,
                            Principal principal) {
        log.info("Chat message from user {} in room {}", principal.getName(), roomId);

        // Отправляем сообщение и получаем ответ
        ChatMessageResponse response = chatService.sendMessage(roomId, messageRequest, principal);

        // Рассылаем сообщение всем участникам комнаты
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/chat/messages",
                response
        );
    }

    @MessageMapping("/room/{roomId}/chat/typing")
    public void handleTyping(@DestinationVariable UUID roomId, Principal principal) {
        // Уведомляем других участников о наборе текста
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/chat/typing",
                Map.of(
                        "userEmail", principal.getName(),
                        "typing", true,
                        "timestamp", Instant.now()
                )
        );
    }

    @MessageMapping("/room/{roomId}/chat/stop-typing")
    public void handleStopTyping(@DestinationVariable UUID roomId, Principal principal) {
        // Уведомляем других участников о прекращении набора
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/chat/typing",
                Map.of(
                        "userEmail", principal.getName(),
                        "typing", false,
                        "timestamp", Instant.now()
                )
        );
    }
}
