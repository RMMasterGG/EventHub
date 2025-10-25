package com.semasem.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.*;
import com.semasem.service.ChatMessageService;
import com.semasem.service.RoomSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketChatController extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final ChatMessageService chatMessageService;
    private final RoomSessionService roomSessionService;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    // Хранилище сессий: roomId -> (userId -> WebSocketSession)
    private final Map<String, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();
    private final Map<String, String> userRooms = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Map<String, String> params = extractParameters(session);
        String token = params.get("token");
        String roomId = params.get("roomId");
        String userId = params.get("userId");

        User user = userRepository.findByUuid(UUID.fromString(userId))
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

        log.info("💬 CHAT WebSocket connection attempt - Room: {}, User: {}", roomId, userId);

        if (token == null || roomId == null || userId == null) {
            log.warn("❌ CHAT Invalid connection parameters");
            sendErrorSafe(session, "Missing required parameters: token, roomId, userId");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            UUID roomUuid = UUID.fromString(roomId);

            Room room = roomRepository.findByUuid(roomUuid)
                    .orElseThrow(() -> new CustomException(ErrorCode.VALIDATION_ERROR));

            // Валидация доступа к комнате
            if (!validateRoomAccess(roomUuid, userId)) {
                sendErrorSafe(session, "Access denied to room");
                session.close(CloseStatus.NOT_ACCEPTABLE);
                return;
            }

            // Регистрируем сессию в комнате
            registerSession(roomId, userId, session);
            userRooms.put(userId, roomId);

            // Отправляем историю чата
            sendChatHistory(roomUuid, userId);

            // Уведомляем участников о новом подключении к чату
            broadcastToRoom(roomId, createSystemMessage(user.getName() + " joined the chat", "USER_JOINED"));

            log.info("✅ CHAT User {} successfully connected to room {}", userId, roomId);

        } catch (Exception e) {
            log.error("❌ CHAT Failed to establish connection", e);
            sendErrorSafe(session, "Failed to join chat: " + e.getMessage());
            session.close(CloseStatus.NOT_ACCEPTABLE);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String userId = getUserIdFromSession(session);
        String roomId = userRooms.get(userId);

        if (roomId == null) {
            log.warn("❌ CHAT User {} sent message without active room", userId);
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
                log.warn("❌ CHAT Message without type from user {}", userId);
                sendErrorSafe(session, "Message type is required");
                return;
            }

            log.debug("💬 CHAT Received message type: {} from user: {} in room: {}", type, userId, roomId);

            User user = userRepository.findByUuid(UUID.fromString(userId))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

            switch (type) {
                case "chat_message":
                    handleChatMessage(roomId, user, payload);
                    break;
                case "edit_message":
                    handleEditMessage(roomId, userId, payload);
                    break;
                case "delete_message":
                    handleDeleteMessage(roomId, userId, payload);
                    break;
                case "typing_start":
                    handleTypingEvent(roomId, userId, true);
                    break;
                case "typing_stop":
                    handleTypingEvent(roomId, userId, false);
                    break;
                case "read_receipt":
                    handleReadReceipt(roomId, userId, payload);
                    break;
                default:
                    log.warn("❌ CHAT Unknown message type: {} from user {}", type, userId);
                    sendErrorSafe(session, "Unknown message type: " + type);
            }

        } catch (Exception e) {
            log.error("❌ CHAT Error handling message from user {}", userId, e);
            sendErrorSafe(session, "Error processing message");
        }
    }

    private void handleChatMessage(String roomId, User user, Map<String, Object> payload) {
        try {
            String content = (String) payload.get("content");
            String tempId = (String) payload.get("tempId");
            String replyTo = (String) payload.get("replyTo"); // Get reply target

            // Валидация сообщения
            if (content == null || content.trim().isEmpty()) {
                sendErrorSafe(getUserSession(roomId, user.getUuid().toString()), "Message content cannot be empty");
                return;
            }

            content = content.trim();
            if (content.length() > 1000) {
                sendErrorSafe(getUserSession(roomId, user.getUuid().toString()), "Message too long (max 1000 characters)");
                return;
            }

            content = filterContent(content);

            Room room = roomRepository.findByUuid(UUID.fromString(roomId))
                    .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

            // Создание и сохранение сообщения
            ChatMessage chatMessage = ChatMessage.builder()
                    .room(room)
                    .user(user)
                    .content(content)
                    .type(MessageType.TEXT)
                    .timestamp(Instant.now())
                    .replyTo(replyTo) // Сохраняем ID сообщения, на которое отвечаем
                    .edited(false)
                    .build();

            ChatMessage savedMessage = chatMessageService.saveMessage(chatMessage);

            // Подготовка данных для рассылки
            Map<String, Object> messageData = new HashMap<>();
            messageData.put("messageId", savedMessage.getUuid().toString());
            messageData.put("tempId", tempId);
            messageData.put("content", savedMessage.getContent());
            messageData.put("senderId", user.getUuid().toString()); // Отправляем ID вместо имени
            messageData.put("timestamp", savedMessage.getTimestamp().toEpochMilli());
            messageData.put("type", "chat_message");
            messageData.put("edited", savedMessage.isEdited());
            messageData.put("replyTo", savedMessage.getReplyTo());

            // Если это ответ на сообщение, добавляем информацию о нем
            if (replyTo != null) {
                Optional<ChatMessage> repliedMessageOpt = chatMessageService.getMessage(UUID.fromString(replyTo));
                if (repliedMessageOpt.isPresent()) {
                    ChatMessage repliedMessage = repliedMessageOpt.get();
                    messageData.put("replyToSender", repliedMessage.getUser().getUuid().toString());
                    messageData.put("replyContent", repliedMessage.getContent());
                }
            }

            // Рассылка сообщения всем участникам комнаты
            broadcastToRoom(roomId, messageData);

            log.debug("💬 CHAT Message saved and broadcasted from user {} in room {}", user.getUuid(), roomId);

        } catch (Exception e) {
            log.error("❌ CHAT Error handling chat message", e);
            sendErrorSafe(getUserSession(roomId, user.getUuid().toString()), "Failed to send message");
        }
    }

    private void handleEditMessage(String roomId, String userId, Map<String, Object> payload) {
        try {
            String messageId = (String) payload.get("messageId");
            String newContent = (String) payload.get("content");

            if (messageId == null || newContent == null || newContent.trim().isEmpty()) {
                sendErrorSafe(getUserSession(roomId, userId), "Invalid edit parameters");
                return;
            }

            newContent = filterContent(newContent.trim());

            // Поиск и обновление сообщения
            Optional<ChatMessage> messageOpt = chatMessageService.getMessage(UUID.fromString(messageId));
            if (messageOpt.isEmpty()) {
                sendErrorSafe(getUserSession(roomId, userId), "Message not found");
                return;
            }

            ChatMessage message = messageOpt.get();
            User currentUser = userRepository.findByUuid(UUID.fromString(userId))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

            // 🔐 ПРОВЕРКА ПРАВ НА РЕДАКТИРОВАНИЕ
            if (!hasEditPermission(message, currentUser, roomId)) {
                sendErrorSafe(getUserSession(roomId, userId), "Not authorized to edit this message");
                return;
            }

            // Обновление сообщения
            message.setContent(newContent);
            message.setEdited(true);
            message.setEditedAt(Instant.now());

            ChatMessage updatedMessage = chatMessageService.saveMessage(message);

            // Рассылка обновления
            Map<String, Object> updateData = new HashMap<>();
            updateData.put("messageId", updatedMessage.getUuid().toString());
            updateData.put("content", updatedMessage.getContent());
            updateData.put("edited", true);
            updateData.put("editedAt", updatedMessage.getEditedAt().toEpochMilli());
            updateData.put("editedBy", userId);
            updateData.put("editedByName", currentUser.getName());

            broadcastToRoom(roomId, createMessage("message_edited", updateData));

            log.info("✏️ CHAT Message {} edited by user {}", messageId, userId);

        } catch (Exception e) {
            log.error("❌ CHAT Error editing message", e);
            sendErrorSafe(getUserSession(roomId, userId), "Failed to edit message");
        }
    }

    private void handleDeleteMessage(String roomId, String userId, Map<String, Object> payload) {
        try {
            String messageId = (String) payload.get("messageId");

            if (messageId == null) {
                sendErrorSafe(getUserSession(roomId, userId), "Message ID is required");
                return;
            }

            Optional<ChatMessage> messageOpt = chatMessageService.getMessage(UUID.fromString(messageId));
            if (messageOpt.isEmpty()) {
                sendErrorSafe(getUserSession(roomId, userId), "Message not found");
                return;
            }

            ChatMessage message = messageOpt.get();
            User currentUser = userRepository.findByUuid(UUID.fromString(userId))
                    .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

            // 🔐 ПРОВЕРКА ПРАВ НА УДАЛЕНИЕ
            if (!hasDeletePermission(message, currentUser, roomId)) {
                sendErrorSafe(getUserSession(roomId, userId), "Not authorized to delete this message");
                return;
            }

            // Удаление сообщения
            chatMessageService.deleteMessage(UUID.fromString(messageId));

            // Рассылка уведомления об удалении
            Map<String, Object> deleteData = new HashMap<>();
            deleteData.put("messageId", messageId);
            deleteData.put("deletedBy", userId);
            deleteData.put("deletedByName", currentUser.getName());

            broadcastToRoom(roomId, createMessage("message_deleted", deleteData));

            log.info("🗑️ CHAT Message {} deleted by user {}", messageId, userId);

        } catch (Exception e) {
            log.error("❌ CHAT Error deleting message", e);
            sendErrorSafe(getUserSession(roomId, userId), "Failed to delete message");
        }
    }

    // ========== 🔐 МЕТОДЫ ПРОВЕРКИ ПРАВ ==========

    /**
     * Проверяет права на редактирование сообщения
     * Могут редактировать:
     * 1. Автор сообщения
     * 2. Владелец комнаты
     * 3. Co-host комнаты
     * 4. Администраторы
     */
    private boolean hasEditPermission(ChatMessage message, User currentUser, String roomId) {
        // 1. Автор сообщения
        if (message.getUser().getUuid().equals(currentUser.getUuid())) {
            return true;
        }

        // 2. Владелец комнаты, Co-host или Админ
        return hasModeratorPermission(currentUser, roomId);
    }

    /**
     * Проверяет права на удаление сообщения
     * Могут удалять:
     * 1. Автор сообщения
     * 2. Владелец комнаты
     * 3. Co-host комнаты
     * 4. Администраторы
     */
    private boolean hasDeletePermission(ChatMessage message, User currentUser, String roomId) {
        // 1. Автор сообщения
        if (message.getUser().getUuid().equals(currentUser.getUuid())) {
            return true;
        }

        // 2. Владелец комнаты, Co-host или Админ
        return hasModeratorPermission(currentUser, roomId);
    }

    /**
     * Проверяет права модератора (владелец, co-host, админ)
     */
    private boolean hasModeratorPermission(User user, String roomId) {
        try {
            UUID roomUuid = UUID.fromString(roomId);

            // Проверяем роль пользователя в комнате
            Optional<RoomParticipant> participantOpt = roomSessionService.getParticipant(roomUuid, user.getUuid());

            if (participantOpt.isPresent()) {
                RoomParticipant participant = participantOpt.get();
                ParticipantRole role = participant.getRole();

                // Владелец комнаты, Co-host или Админ могут модераить
                return role == ParticipantRole.HOST ||
                        role == ParticipantRole.CO_HOST ||
                        user.getRole() == UserRole.ROLE_ADMIN;
            }

            // Если пользователь не участник комнаты, проверяем только админские права
            return user.getRole() == UserRole.ROLE_ADMIN;

        } catch (Exception e) {
            log.error("❌ CHAT Error checking moderator permissions for user {} in room {}", user.getUuid(), roomId, e);
            return false;
        }
    }

    // ========== ОСТАЛЬНЫЕ МЕТОДЫ (без изменений) ==========

    private void handleTypingEvent(String roomId, String userId, boolean isTyping) {
        Map<String, Object> typingData = new HashMap<>();
        typingData.put("userId", userId);
        typingData.put("isTyping", isTyping);

        // Рассылка уведомления о наборе текста (всем кроме отправителя)
        broadcastToRoomExceptUser(roomId, userId, createMessage("user_typing", typingData));
    }

    private void handleReadReceipt(String roomId, String userId, Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");

        if (messageId != null) {
            // Обновление статуса прочтения сообщения
            chatMessageService.markMessageAsRead(UUID.fromString(messageId), userId);

            Map<String, Object> receiptData = new HashMap<>();
            receiptData.put("messageId", messageId);
            receiptData.put("readBy", userId);
            receiptData.put("readAt", System.currentTimeMillis());

            // Рассылка уведомления о прочтении
            broadcastToRoom(roomId, createMessage("message_read", receiptData));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = getUserIdFromSession(session);
        String roomId = userRooms.get(userId);

        if (roomId != null && userId != null) {
            log.info("💬 CHAT User {} disconnected from room {}, status: {}", userId, roomId, status);

            try {
                User user = userRepository.findByUuid(UUID.fromString(userId))
                        .orElseThrow(() -> new CustomException(ErrorCode.INVALID_CREDENTIALS));

                // Уведомляем об уходе из чата
                broadcastToRoomExceptUserSafe(roomId, userId,
                        createSystemMessage(user.getName() + " left the chat", "USER_LEFT"));
            } catch (Exception e) {
                log.warn("💬 CHAT Could not send leave notification for user {}", userId);
            }

            // Удаляем сессию
            unregisterSession(roomId, userId);
            userRooms.remove(userId);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        String userId = getUserIdFromSession(session);
        log.error("❌ CHAT Transport error for user {}", userId, exception);

        String roomId = userRooms.get(userId);
        if (roomId != null) {
            unregisterSession(roomId, userId);
            userRooms.remove(userId);
        }
    }

    // ========== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ==========

    private void registerSession(String roomId, String userId, WebSocketSession session) {
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(userId, session);
    }

    private void unregisterSession(String roomId, String userId) {
        Map<String, WebSocketSession> roomSessionsMap = roomSessions.get(roomId);
        if (roomSessionsMap != null) {
            roomSessionsMap.remove(userId);
            if (roomSessionsMap.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
    }

    private WebSocketSession getUserSession(String roomId, String userId) {
        Map<String, WebSocketSession> roomSessionsMap = roomSessions.get(roomId);
        return roomSessionsMap != null ? roomSessionsMap.get(userId) : null;
    }

    private void sendChatHistory(UUID roomId, String userId) {
        try {
            List<ChatMessage> messages = chatMessageService.getRoomMessages(roomId, 100);

            List<Map<String, Object>> messageList = new ArrayList<>();
            for (ChatMessage message : messages) {
                // ✅ УБЕДИТЕСЬ, что user загружен (не LAZY)
                User messageUser = message.getUser();
                if (messageUser == null) {
                    log.warn("Message {} has null user", message.getUuid());
                    continue;
                }

                Map<String, Object> messageData = new HashMap<>();
                messageData.put("messageId", message.getUuid().toString());
                messageData.put("content", message.getContent());
                messageData.put("senderId", messageUser.getUuid().toString());
                messageData.put("senderName", messageUser.getName());
                messageData.put("type", message.getType().toString());
                messageData.put("timestamp", message.getTimestamp().toEpochMilli());
                messageData.put("replyTo", message.getReplyTo());
                messageData.put("edited", message.isEdited());
                messageData.put("editedAt", message.getEditedAt() != null ?
                        message.getEditedAt().toEpochMilli() : null);

                messageList.add(messageData);
            }

            Map<String, Object> historyData = new HashMap<>();
            historyData.put("messages", messageList);
            historyData.put("roomId", roomId.toString());

            sendToUserSafe(userId, createMessage("chat_history", historyData));

        } catch (Exception e) {
            log.error("❌ CHAT Error sending chat history to user {}", userId, e);
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message) {
        broadcastToRoomExceptUserSafe(roomId, null, message);
    }

    private void broadcastToRoomExceptUser(String roomId, String excludeUserId, Map<String, Object> message) {
        broadcastToRoomExceptUserSafe(roomId, excludeUserId, message);
    }

    private void broadcastToRoomExceptUserSafe(String roomId, String excludeUserId, Map<String, Object> message) {
        Map<String, WebSocketSession> roomSessionsMap = roomSessions.get(roomId);
        if (roomSessionsMap == null) return;

        int sentCount = 0;
        List<String> brokenSessions = new ArrayList<>();

        for (Map.Entry<String, WebSocketSession> entry : roomSessionsMap.entrySet()) {
            String userId = entry.getKey();
            WebSocketSession session = entry.getValue();

            if (!userId.equals(excludeUserId)) {
                try {
                    if (session != null && session.isOpen()) {
                        String jsonMessage = objectMapper.writeValueAsString(message);
                        session.sendMessage(new TextMessage(jsonMessage));
                        sentCount++;
                    } else {
                        brokenSessions.add(userId);
                    }
                } catch (IOException e) {
                    log.warn("💬 CHAT Error broadcasting to user {}, marking as broken", userId);
                    brokenSessions.add(userId);
                }
            }
        }

        // Очищаем битые сессии
        for (String brokenUserId : brokenSessions) {
            unregisterSession(roomId, brokenUserId);
            userRooms.remove(brokenUserId);
        }

        log.debug("💬 CHAT Safe broadcast to {} users in room {}", sentCount, roomId);
    }

    private void sendToUserSafe(String userId, Map<String, Object> message) {
        String roomId = userRooms.get(userId);
        if (roomId != null) {
            WebSocketSession session = getUserSession(roomId, userId);
            if (session != null && session.isOpen()) {
                try {
                    String jsonMessage = objectMapper.writeValueAsString(message);
                    session.sendMessage(new TextMessage(jsonMessage));
                } catch (IOException e) {
                    log.error("❌ CHAT Error sending message to user {}", userId, e);
                    unregisterSession(roomId, userId);
                    userRooms.remove(userId);
                }
            }
        }
    }

    private void sendErrorSafe(WebSocketSession session, String error) {
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> errorMessage = createMessage("error",
                        Map.of("message", error, "timestamp", System.currentTimeMillis()));
                String jsonError = objectMapper.writeValueAsString(errorMessage);
                session.sendMessage(new TextMessage(jsonError));
            } catch (Exception e) {
                log.debug("💬 CHAT Could not send error message", e);
            }
        }
    }

    private Map<String, Object> createMessage(String type, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>(data);
        message.put("type", type);
        message.put("timestamp", System.currentTimeMillis());
        return message;
    }

    private Map<String, Object> createSystemMessage(String content, String systemType) {
        Map<String, Object> messageData = new HashMap<>();
        messageData.put("content", content);
        messageData.put("systemType", systemType);
        messageData.put("timestamp", System.currentTimeMillis());
        return createMessage("system_message", messageData);
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

    // ========== ЗАГЛУШКИ ДЛЯ СЕРВИСОВ ==========

    private boolean validateRoomAccess(UUID roomId, String userId) {
        // TODO: Реализовать проверку доступа к комнате
        return true;
    }

    private String filterContent(String content) {
        // TODO: Реализовать фильтрацию запрещенного контента
        String[] forbiddenWords = {"spam", "badword"};
        for (String word : forbiddenWords) {
            content = content.replaceAll("(?i)" + word, "***");
        }
        return content;
    }

    private Room createRoomProxy(String roomId) {
        Room room = new Room();
        room.setUuid(UUID.fromString(roomId));
        return room;
    }

    private User createUserProxy(String userId) {
        User user = new User();
        user.setUuid(UUID.fromString(userId));
        return user;
    }
}