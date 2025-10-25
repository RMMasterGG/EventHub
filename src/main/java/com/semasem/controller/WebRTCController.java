package com.semasem.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semasem.service.RoomSessionService;
import com.semasem.service.WebRTCService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebRTCController extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebRTCService webRTCService;
    private final RoomSessionService roomSessionService;

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = extractParameters(session);
        String token = params.get("token");
        String roomId = params.get("roomId");
        String userId = params.get("userId");

        log.info("WebSocket connection attempt - Token: {}, Room: {}, User: {}",
                token != null ? "present" : "missing", roomId, userId);

        if (token == null || roomId == null || userId == null) {
            log.warn("Invalid connection parameters - token: {}, roomId: {}, userId: {}",
                    token, roomId, userId);
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            UUID roomUuid = UUID.fromString(roomId);

            webRTCService.validateRoomAccess(roomUuid, () -> userId);

            sessions.put(userId, session);
            userRooms.put(userId, roomId);

            roomSessionService.addParticipant(roomUuid, userId);

            broadcastToRoom(roomId, createMessage("new_peer", Map.of("userId", userId)));

            sendParticipantsList(roomUuid, userId);

            log.info("User {} successfully connected to room {}", userId, roomId);

        } catch (Exception e) {
            log.error("Failed to establish WebSocket connection for user {} to room {}",
                    userId, roomId, e);

            sendErrorSafe(session, "Failed to join room: " + e.getMessage());

            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserIdFromSession(session);
        String roomId = userRooms.get(userId);

        if (roomId == null) {
            log.warn("User {} sent message without active room", userId);
            sendErrorSafe(session, "No active room");
            return;
        }

        try {
            Map<String, Object> payload = objectMapper.readValue(
                    message.getPayload(),
                    new TypeReference<Map<String, Object>>() {}
            );
            String type = (String) payload.get("type");

            if (type == null) {
                log.warn("Message without type from user {}", userId);
                return;
            }

            log.debug("Received message type: {} from user: {} in room: {}", type, userId, roomId);

            switch (type) {
                case "offer":
                    handleOffer(roomId, userId, payload);
                    break;
                case "answer":
                    handleAnswer(roomId, userId, payload);
                    break;
                case "ice_candidate":
                    handleIceCandidate(roomId, userId, payload);
                    break;
                case "get_participants":
                    handleGetParticipants(roomId, userId);
                    break;
                case "peer_left":
                    handlePeerLeft(roomId, userId);
                    break;
                case "new_peer": // Обрабатываем new_peer сообщения
                    log.debug("New peer message from {}", userId);
                    break;
                default:
                    log.warn("Unknown message type: {} from user {}", type, userId);
                    sendErrorSafe(session, "Unknown message type: " + type);
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket message from user {}", userId, e);
            sendErrorSafe(session, "Error processing message");
        }
    }

    private void handleOffer(String roomId, String fromUserId, Map<String, Object> payload) {
        String targetUserId = (String) payload.get("targetUserId");
        Object sdp = payload.get("sdp");

        if (targetUserId != null && sdp != null) {
            Map<String, Object> message = createMessage("offer", Map.of(
                    "fromUserId", fromUserId,
                    "sdp", sdp
            ));

            sendToUserSafe(targetUserId, message);
            log.debug("Offer sent from {} to {}", fromUserId, targetUserId);
        } else {
            log.warn("Invalid offer from {}: targetUserId={}, sdp={}",
                    fromUserId, targetUserId, sdp != null ? "present" : "null");
        }
    }

    private void handleAnswer(String roomId, String fromUserId, Map<String, Object> payload) {
        String targetUserId = (String) payload.get("targetUserId");
        Object sdp = payload.get("sdp");

        if (targetUserId != null && sdp != null) {
            Map<String, Object> message = createMessage("answer", Map.of(
                    "fromUserId", fromUserId,
                    "sdp", sdp
            ));

            sendToUserSafe(targetUserId, message);
            log.debug("Answer sent from {} to {}", fromUserId, targetUserId);
        }
    }

    private void handleIceCandidate(String roomId, String fromUserId, Map<String, Object> payload) {
        String targetUserId = (String) payload.get("targetUserId");
        Object candidate = payload.get("candidate");

        if (targetUserId != null && candidate != null) {
            Map<String, Object> message = createMessage("ice_candidate", Map.of(
                    "fromUserId", fromUserId,
                    "candidate", candidate
            ));

            sendToUserSafe(targetUserId, message);
            log.debug("ICE candidate sent from {} to {}", fromUserId, targetUserId);
        }
    }

    private void handleGetParticipants(String roomId, String userId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            sendParticipantsList(roomUuid, userId);
        } catch (Exception e) {
            log.error("Error getting participants for room {}", roomId, e);
        }
    }

    private void handlePeerLeft(String roomId, String userId) {
        log.info("User {} explicitly left room {}", userId, roomId);

        broadcastToRoomSafe(roomId, createMessage("peer_left", Map.of("userId", userId)));

        try {
            UUID roomUuid = UUID.fromString(roomId);
            roomSessionService.removeParticipant(roomUuid, userId);
        } catch (Exception e) {
            log.error("Error removing participant from room", e);
        }

        cleanupUserSession(userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        String roomId = userRooms.get(userId);

        if (roomId != null && userId != null) {
            log.info("User {} connection closed from room {}, status: {}",
                    userId, roomId, status);

            broadcastToRoomSafe(roomId, createMessage("peer_left", Map.of("userId", userId)));

            try {
                UUID roomUuid = UUID.fromString(roomId);
                roomSessionService.removeParticipant(roomUuid, userId);
            } catch (Exception e) {
                log.error("Error cleaning up user session", e);
            }
        }

        cleanupUserSession(userId);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserIdFromSession(session);
        log.error("Transport error for user {}", userId, exception);
        cleanupUserSession(userId);
    }

    private void cleanupUserSession(String userId) {
        WebSocketSession session = sessions.remove(userId);
        userRooms.remove(userId);
        if (session != null && session.isOpen()) {
            try {
                session.close(CloseStatus.NORMAL);
            } catch (IOException e) {
                log.debug("Error closing session for user {}", userId, e);
            }
        }
    }

    private void sendParticipantsList(UUID roomId, String userId) {
        try {
            var participants = roomSessionService.getActiveParticipants(roomId);
            int count = roomSessionService.getActiveParticipantsCount(roomId);

            Map<String, Object> message = createMessage("participants_list", Map.of(
                    "participants", participants,
                    "count", count,
                    "roomId", roomId.toString()
            ));

            sendToUserSafe(userId, message);
            log.debug("Sent participants list to user {}: {} participants", userId, count);

        } catch (Exception e) {
            log.error("Error sending participants list", e);
        }
    }

    private void sendToUserSafe(String userId, Map<String, Object> message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                session.sendMessage(new TextMessage(jsonMessage));
            } catch (IOException e) {
                log.error("Error sending message to user {}", userId, e);
                // Удаляем нерабочую сессию
                cleanupUserSession(userId);
            }
        } else {
            log.debug("User {} session not found or closed", userId);
            cleanupUserSession(userId);
        }
    }

    private void broadcastToRoomSafe(String roomId, Map<String, Object> message) {
        int sentCount = 0;
        for (Map.Entry<String, String> entry : userRooms.entrySet()) {
            if (roomId.equals(entry.getValue())) {
                String userId = entry.getKey();
                sendToUserSafe(userId, message);
                sentCount++;
            }
        }
        log.debug("Broadcast message to {} users in room {}", sentCount, roomId);
    }

    private void sendErrorSafe(WebSocketSession session, String error) {
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> errorMessage = createMessage("error", Map.of("message", error));
                String jsonError = objectMapper.writeValueAsString(errorMessage);
                session.sendMessage(new TextMessage(jsonError));
            } catch (Exception e) {
                log.debug("Could not send error message (session might be closed)", e);
            }
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message) {
        broadcastToRoomSafe(roomId, message);
    }

    private Map<String, Object> createMessage(String type, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>(data);
        message.put("type", type);
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }

    private Map<String, String> extractParameters(WebSocketSession session) {
        Map<String, String> params = new HashMap<>();
        if (session.getUri() != null && session.getUri().getQuery() != null) {
            String query = session.getUri().getQuery();
            for (String param : query.split("&")) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    params.put(keyValue[0], keyValue[1]);
                }
            }
        }
        return params;
    }

    private String getUserIdFromSession(WebSocketSession session) {
        Map<String, String> params = extractParameters(session);
        return params.get("userId");
    }
}