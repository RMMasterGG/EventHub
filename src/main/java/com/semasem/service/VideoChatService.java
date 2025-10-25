package com.semasem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoChatService {

    private final SimpMessagingTemplate messagingTemplate;

    private final Map<String, Set<String>> roomUsers = new ConcurrentHashMap<>();
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();

    public void processVideoChatMessage(String roomId, Map<String, Object> payload) {
        try {
            String messageType = (String) payload.get("type");

            switch (messageType) {
                case "JOIN_ROOM":
                    handleJoinRoom(roomId, payload);
                    break;
                case "LEAVE_ROOM":
                    handleLeaveRoom(roomId, payload);
                    break;
                case "CHAT_MESSAGE":
                    handleChatMessage(roomId, payload);
                    break;
                case "WEBRTC_OFFER":
                case "WEBRTC_ANSWER":
                case "ICE_CANDIDATE":
                    handleWebRTCSignal(roomId, payload);
                    break;
                default:
                    log.warn("Unknown message type: {} in room: {}", messageType, roomId);
            }
        } catch (Exception e) {
            log.error("Error processing video chat message in room {}: {}", roomId, payload, e);
            sendErrorMessage(roomId, "Ошибка обработки сообщения");
        }
    }

    private void handleJoinRoom(String roomId, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String sessionId = (String) payload.get("sessionId");

        if (username == null || sessionId == null) {
            log.warn("Invalid JOIN_ROOM payload: {}", payload);
            return;
        }

        // Сохраняем связь сессии и пользователя
        userSessions.put(sessionId, username);

        // Добавляем пользователя в комнату
        roomUsers.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(username);

        // Отправляем обновленный список пользователей
        broadcastRoomUsers(roomId);

        // Уведомляем о новом пользователе
        Map<String, Object> systemMessage = Map.of(
                "type", "USER_JOINED",
                "username", username,
                "timestamp", Instant.now().toString(),
                "message", username + " присоединился к комнате"
        );

        messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, systemMessage);
        log.info("User {} joined room {}", username, roomId);
    }

    private void handleLeaveRoom(String roomId, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String sessionId = (String) payload.get("sessionId");

        if (username != null && sessionId != null) {
            userSessions.remove(sessionId);

            Set<String> users = roomUsers.get(roomId);
            if (users != null) {
                users.remove(username);

                if (users.isEmpty()) {
                    roomUsers.remove(roomId);
                } else {
                    broadcastRoomUsers(roomId);

                    // Уведомляем о выходе пользователя
                    Map<String, Object> systemMessage = Map.of(
                            "type", "USER_LEFT",
                            "username", username,
                            "timestamp", Instant.now().toString(),
                            "message", username + " покинул комнату"
                    );
                    messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, systemMessage);
                }
            }
            log.info("User {} left room {}", username, roomId);
        }
    }

    private void handleChatMessage(String roomId, Map<String, Object> payload) {
        String username = (String) payload.get("username");
        String message = (String) payload.get("message");

        if (username == null || message == null) {
            log.warn("Invalid CHAT_MESSAGE payload: {}", payload);
            return;
        }

        // Добавляем метаданные к сообщению
        Map<String, Object> chatMessage = Map.of(
                "type", "CHAT_MESSAGE",
                "username", username,
                "message", message,
                "timestamp", Instant.now().toString()
        );

        messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, chatMessage);
        log.debug("Chat message from {} in room {}: {}", username, roomId, message);
    }

    private void handleWebRTCSignal(String roomId, Map<String, Object> payload) {
        String targetUser = (String) payload.get("target");
        String sender = (String) payload.get("username");

        if (sender == null) {
            log.warn("WebRTC signal without sender: {}", payload);
            return;
        }

        if ("all".equals(targetUser)) {
            // Отправляем всем в комнате кроме отправителя
            messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, payload);
        } else if (targetUser != null) {
            // Отправляем конкретному пользователю
            Map<String, Object> targetedSignal = new ConcurrentHashMap<>(payload);
            targetedSignal.put("intendedReceiver", targetUser);
            messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, targetedSignal);
        } else {
            log.warn("WebRTC signal without target: {}", payload);
        }

        log.debug("WebRTC signal from {} to {} in room {}", sender, targetUser, roomId);
    }

    private void broadcastRoomUsers(String roomId) {
        Set<String> users = roomUsers.getOrDefault(roomId, Collections.emptySet());

        Map<String, Object> message = Map.of(
                "type", "ROOM_USERS_UPDATE",
                "roomId", roomId,
                "users", users,
                "timestamp", Instant.now().toString()
        );

        messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, message);
    }

    private void sendErrorMessage(String roomId, String errorMessage) {
        Map<String, Object> error = Map.of(
                "type", "ERROR",
                "message", errorMessage,
                "timestamp", Instant.now().toString()
        );

        messagingTemplate.convertAndSend("/topic/video-chat/" + roomId, error);
    }

    // Методы для управления комнатами (можно использовать в REST контроллере)
    public Set<String> getRoomUsers(String roomId) {
        return roomUsers.getOrDefault(roomId, Collections.emptySet());
    }

    public boolean isUserInRoom(String roomId, String username) {
        Set<String> users = roomUsers.get(roomId);
        return users != null && users.contains(username);
    }

    public void cleanupRoom(String roomId) {
        roomUsers.remove(roomId);
        log.info("Room {} cleaned up", roomId);
    }
}