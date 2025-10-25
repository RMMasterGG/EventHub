package com.semasem.controller;

import com.semasem.dto.request.WebRTCOfferRequest;
import com.semasem.dto.request.WebRTCAnswerRequest;
import com.semasem.dto.request.WebRTCIceCandidateRequest;
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
import java.util.Map;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@Tag(name = "WebRTC", description = "WebRTC signaling and WebSocket endpoints")
public class WebRTCController {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebRTCService webRTCService;

    // Подписка на комнату
    @Operation(summary = "Connect to WebRTC WebSocket",
            description = "WebSocket endpoint for WebRTC signaling"
    )
    @SubscribeMapping("/room/{roomId}/webrtc")
    public void subscribeToRoom(@DestinationVariable UUID roomId, Principal principal) {
        log.info("User {} subscribed to WebRTC signals for room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);
    }

    // Отправка WebRTC offer
    @MessageMapping("/room/{roomId}/webrtc/offer")
    public void handleOffer(@DestinationVariable UUID roomId,
                            @Payload WebRTCOfferRequest offerRequest,
                            Principal principal) {
        log.info("WebRTC offer from user {} in room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);

        // Рассылаем offer всем участникам комнаты кроме отправителя
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/webrtc/offer",
                offerRequest
        );
    }

    // Отправка WebRTC answer
    @MessageMapping("/room/{roomId}/webrtc/answer")
    public void handleAnswer(@DestinationVariable UUID roomId,
                             @Payload WebRTCAnswerRequest answerRequest,
                             Principal principal) {
        log.info("WebRTC answer from user {} in room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);

        // Отправляем answer конкретному пользователю
        messagingTemplate.convertAndSendToUser(
                answerRequest.getTargetUserId(),
                "/queue/room/" + roomId + "/webrtc/answer",
                answerRequest
        );
    }

    // Обмен ICE candidates
    @MessageMapping("/room/{roomId}/webrtc/ice-candidate")
    public void handleIceCandidate(@DestinationVariable UUID roomId,
                                   @Payload WebRTCIceCandidateRequest iceCandidateRequest,
                                   Principal principal) {
        log.info("ICE candidate from user {} in room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);

        // Отправляем ICE candidate конкретному пользователю
        messagingTemplate.convertAndSendToUser(
                iceCandidateRequest.getTargetUserId(),
                "/queue/room/" + roomId + "/webrtc/ice-candidate",
                iceCandidateRequest
        );
    }

    // Уведомление о новом участнике
    @MessageMapping("/room/{roomId}/webrtc/new-peer")
    public void handleNewPeer(@DestinationVariable UUID roomId, Principal principal) {
        log.info("New peer notification from user {} in room {}", principal.getName(), roomId);
        webRTCService.validateRoomAccess(roomId, principal);

        // Уведомляем всех о новом участнике
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/webrtc/new-peer",
                Map.of("userId", principal.getName(), "action", "joined")
        );
    }

    // Уведомление о выходе участника
    @MessageMapping("/room/{roomId}/webrtc/peer-left")
    public void handlePeerLeft(@DestinationVariable UUID roomId, Principal principal) {
        log.info("Peer left notification from user {} in room {}", principal.getName(), roomId);

        // Уведомляем всех о выходе участника
        messagingTemplate.convertAndSend(
                "/topic/room/" + roomId + "/webrtc/peer-left",
                Map.of("userId", principal.getName(), "action", "left")
        );
    }
}