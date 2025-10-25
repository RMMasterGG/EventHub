package com.semasem.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.semasem.repository.RoomParticipantRepository;
import com.semasem.repository.entity.ParticipantInfo;
import com.semasem.repository.entity.RoomParticipant;
import com.semasem.service.MediaStateService;
import com.semasem.service.RoomSessionService;
import com.semasem.service.WebRTCService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebRTCController extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final WebRTCService webRTCService;
    private final RoomSessionService roomSessionService;
    private final MediaStateService mediaStateService;
    private final RoomParticipantRepository roomParticipantRepository;

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
            UUID userUuid = UUID.fromString(userId);

            // Валидируем доступ к комнате
            webRTCService.validateRoomAccess(roomUuid, () -> userId);

            // Получаем данные участника из БД
            ParticipantInfo participantInfo = getParticipantInfo(roomUuid, userUuid);
            if (participantInfo == null) {
                log.warn("Participant not found in room: user {}, room {}", userId, roomId);
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            // Сохраняем сессию
            sessions.put(userId, session);
            userRooms.put(userId, roomId);

            // 🔥 ИСПРАВЛЕНО: Добавляем в активные участники с правильными параметрами
            roomSessionService.addParticipant(
                    roomUuid,
                    userUuid,
                    participantInfo.getName(),
                    participantInfo.getEmail(),
                    participantInfo.getRole(),
                    participantInfo.isGuest(),
                    participantInfo.getJoinedAt(),
                    session.getId() // Используем ID сессии как sessionId
            );

            // Инициализируем состояние медиа для нового участника
            mediaStateService.updateMediaState(roomUuid, userUuid, true, true, false);

            // Уведомляем о новом участнике
            broadcastToRoomSafe(roomId, createMessage("new_peer", Map.of("userId", userId)));

            // Отправляем текущие состояния медиа новому участнику
            sendCurrentMediaStates(roomUuid, userId);

            // Отправляем текущий список участников новому пользователю
            sendParticipantsList(roomUuid, userId);

            log.info("User {} successfully connected to room {}", userId, roomId);

        } catch (Exception e) {
            log.error("Failed to establish WebSocket connection for user {} to room {}",
                    userId, roomId, e);

            sendErrorSafe(session, "Failed to join room: " + e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    // 🔥 ДОБАВЛЕНО: Метод для получения информации об участнике
    private ParticipantInfo getParticipantInfo(UUID roomId, UUID userId) {
        try {
            // 🔥 ИСПРАВЛЕНО: Используем метод с JOIN FETCH
            Optional<RoomParticipant> roomParticipant = roomParticipantRepository
                    .findByRoomAndUserWithDetails(roomId, userId);

            return roomParticipant.map(rp -> new ParticipantInfo(
                    rp.getUser().getUuid(),
                    rp.getUser().getName(),
                    rp.getUser().getEmail(),
                    rp.getRole(),
                    rp.getUser().isGuest(),
                    rp.getJoinedAt(),
                    rp.getLastActiveAt()
            )).orElse(null);

        } catch (Exception e) {
            log.error("Error getting participant info for user {} in room {}", userId, roomId, e);
            return null;
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
            Map<String, Object> payload = objectMapper.readValue(message.getPayload(), Map.class);
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
                case "new_peer":
                    log.debug("New peer message from {}", userId);
                    break;
                case "media_state_update":
                    handleMediaStateUpdate(roomId, userId, payload);
                    break;
                case "request_media_state":
                    handleRequestMediaState(roomId, userId);
                    break;
                case "request_all_media_states":
                    handleRequestAllMediaStates(roomId, userId);
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

        // Уведомляем об уходе участника
        broadcastToRoomSafe(roomId, createMessage("peer_left", Map.of("userId", userId)));

        // Удаляем из активных участников
        try {
            UUID roomUuid = UUID.fromString(roomId);
            UUID userUuid = UUID.fromString(userId);

            // 🔥 ИСПРАВЛЕНО: Правильный вызов removeParticipant
            roomSessionService.removeParticipant(roomUuid, userUuid);

            // Очищаем состояние медиа
            mediaStateService.removeMediaState(roomUuid, userUuid);

        } catch (Exception e) {
            log.error("Error removing participant from room", e);
        }

        // Закрываем сессию
        cleanupUserSession(userId);
    }

    private void handleMediaStateUpdate(String roomId, String userId, Map<String, Object> payload) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            UUID userUuid = UUID.fromString(userId);

            Boolean audioEnabled = (Boolean) payload.get("audioEnabled");
            Boolean videoEnabled = (Boolean) payload.get("videoEnabled");
            Boolean screenSharing = (Boolean) payload.get("screenSharing");

            // Обновляем состояние в сервисе
            mediaStateService.updateMediaState(roomUuid, userUuid,
                    audioEnabled != null ? audioEnabled : false,
                    videoEnabled != null ? videoEnabled : false,
                    screenSharing != null ? screenSharing : false
            );

            // 🔥 ДОБАВЛЕНО: Обновляем в RoomSessionService
            roomSessionService.updateParticipantMediaStatus(userUuid,
                    audioEnabled != null ? audioEnabled : false,
                    videoEnabled != null ? videoEnabled : false
            );

            // Рассылаем обновление всем участникам
            Map<String, Object> mediaUpdate = createMessage("media_state_update", Map.of(
                    "userId", userId,
                    "audioEnabled", audioEnabled != null ? audioEnabled : false,
                    "videoEnabled", videoEnabled != null ? videoEnabled : false,
                    "screenSharing", screenSharing != null ? screenSharing : false,
                    "timestamp", System.currentTimeMillis()
            ));

            broadcastToRoomSafe(roomId, mediaUpdate);

            log.debug("Media state broadcast for user {} in room {}: audio={}, video={}, screen={}",
                    userId, roomId, audioEnabled, videoEnabled, screenSharing);

        } catch (Exception e) {
            log.error("Error handling media state update from user {}: {}", userId, e.getMessage());
            sendErrorSafe(sessions.get(userId), "Error updating media state");
        }
    }

    private void handleRequestMediaState(String roomId, String userId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            UUID userUuid = UUID.fromString(userId);

            MediaStateService.MediaState state = mediaStateService.getMediaState(roomUuid, userUuid);

            Map<String, Object> response = createMessage("media_state_response", Map.of(
                    "userId", userId,
                    "audioEnabled", state.isAudioEnabled(),
                    "videoEnabled", state.isVideoEnabled(),
                    "screenSharing", state.isScreenSharing(),
                    "lastUpdate", state.getLastUpdate(),
                    "timestamp", System.currentTimeMillis()
            ));

            sendToUserSafe(userId, response);

        } catch (Exception e) {
            log.error("Error handling media state request from user {}: {}", userId, e.getMessage());
        }
    }

    private void handleRequestAllMediaStates(String roomId, String userId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);
            sendCurrentMediaStates(roomUuid, userId);
        } catch (Exception e) {
            log.error("Error handling all media states request from user {}: {}", userId, e.getMessage());
        }
    }

    private void sendCurrentMediaStates(UUID roomId, String targetUserId) {
        try {
            Map<UUID, MediaStateService.MediaState> allStates = mediaStateService.getAllMediaStates(roomId);

            if (!allStates.isEmpty()) {
                Map<String, Object> statesMap = new HashMap<>();
                allStates.forEach((userUuid, state) -> {
                    Map<String, Object> userState = Map.of(
                            "audioEnabled", state.isAudioEnabled(),
                            "videoEnabled", state.isVideoEnabled(),
                            "screenSharing", state.isScreenSharing(),
                            "lastUpdate", state.getLastUpdate()
                    );
                    statesMap.put(userUuid.toString(), userState);
                });

                Map<String, Object> response = createMessage("all_media_states", Map.of(
                        "roomId", roomId.toString(),
                        "states", statesMap,
                        "timestamp", System.currentTimeMillis()
                ));

                sendToUserSafe(targetUserId, response);
                log.debug("Sent all media states to user {} for room {}", targetUserId, roomId);
            }
        } catch (Exception e) {
            log.error("Error sending current media states to user {}: {}", targetUserId, e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        String roomId = userRooms.get(userId);

        if (roomId != null && userId != null) {
            log.info("User {} connection closed from room {}, status: {}",
                    userId, roomId, status);

            try {
                UUID roomUuid = UUID.fromString(roomId);
                UUID userUuid = UUID.fromString(userId);

                // Уведомляем об отключении медиа
                Map<String, Object> mediaOffline = createMessage("media_state_update", Map.of(
                        "userId", userId,
                        "audioEnabled", false,
                        "videoEnabled", false,
                        "screenSharing", false,
                        "isOffline", true,
                        "timestamp", System.currentTimeMillis()
                ));

                broadcastToRoomSafe(roomId, mediaOffline);

                // Удаляем из активных участников
                roomSessionService.removeParticipant(roomUuid, userUuid);

                // Очищаем состояние медиа
                mediaStateService.removeMediaState(roomUuid, userUuid);

                log.info("User {} disconnected from room {} and media state cleaned up", userId, roomId);

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

    @Override
    public boolean supportsPartialMessages() {
        return false;
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
            // 🔥 ИСПРАВЛЕНО: Используем метод с JOIN FETCH
            List<RoomParticipant> roomParticipants = roomParticipantRepository
                    .findByRoomUuidWithDetails(roomId);

            List<ParticipantInfo> dbParticipants = roomParticipants.stream()
                    .map(rp -> new ParticipantInfo(
                            rp.getUser().getUuid(),
                            rp.getUser().getName(),
                            rp.getUser().getEmail(),
                            rp.getRole(),
                            rp.getUser().isGuest(),
                            rp.getJoinedAt(),
                            rp.getLastActiveAt()
                    ))
                    .collect(Collectors.toList());

            var participants = roomSessionService.getActiveParticipantsWithDetails(roomId, dbParticipants);
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