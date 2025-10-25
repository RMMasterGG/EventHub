package com.semasem.service;

import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.request.ChatMessageRequest;
import com.semasem.dto.request.EditMessageRequest;
import com.semasem.dto.response.ChatMessageResponse;
import com.semasem.repository.ChatMessageRepository;
import com.semasem.repository.RoomParticipantRepository;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.ChatMessage;
import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomParticipant;
import com.semasem.repository.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    @Transactional
    public ChatMessageResponse sendMessage(UUID roomId, ChatMessageRequest request, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Проверяем, что пользователь является участником комнаты
        RoomParticipant participant = roomParticipantRepository
                .findByRoomUuidAndUserUuid(roomId, user.getUuid())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED, "User is not a participant of this room"));

        if (!participant.isActive()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "Participant is not active in this room");
        }

        // Валидация контента
        validateMessageContent(request.getContent());

        // Проверяем сообщение, на которое отвечаем (если есть)
        ChatMessage repliedMessage = null;
        if (request.getReplyTo() != null) {
            repliedMessage = chatMessageRepository.findByUuidAndRoomUuid(request.getReplyTo(), roomId)
                    .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND, "Replied message not found"));
        }

        // Создаем сообщение
        ChatMessage message = ChatMessage.builder()
                .room(room)
                .user(user)
                .content(request.getContent())
                .type(request.getType())
                .timestamp(Instant.now())
                .replyTo(request.getReplyTo() != null ? request.getReplyTo().toString() : null)
                .edited(false)
                .build();

        ChatMessage savedMessage = chatMessageRepository.save(message);
        log.info("Message sent by {} in room {}", userEmail, roomId);

        return convertToResponse(savedMessage, repliedMessage);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getRoomMessages(UUID roomId, Pageable pageable, Principal principal) {
        validateRoomAccess(roomId, principal);

        Page<ChatMessage> messages = chatMessageRepository.findByRoomUuidOrderByTimestampDesc(roomId, pageable);

        return messages.map(message -> {
            ChatMessage repliedMessage = null;
            if (message.getReplyTo() != null) {
                repliedMessage = chatMessageRepository.findById(UUID.fromString(message.getReplyTo())).orElse(null);
            }
            return convertToResponse(message, repliedMessage);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getRecentMessages(UUID roomId, Principal principal) {
        validateRoomAccess(roomId, principal);

        List<ChatMessage> messages = chatMessageRepository.findTop50ByRoomUuidOrderByTimestampAsc(roomId);

        return messages.stream()
                .map(message -> {
                    ChatMessage repliedMessage = null;
                    if (message.getReplyTo() != null) {
                        repliedMessage = chatMessageRepository.findById(UUID.fromString(message.getReplyTo())).orElse(null);
                    }
                    return convertToResponse(message, repliedMessage);
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageResponse editMessage(UUID roomId, UUID messageId, EditMessageRequest request, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatMessage message = chatMessageRepository.findByUuidAndRoomUuid(messageId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));

        // Проверяем, что пользователь является автором сообщения
        if (!message.getUser().getUuid().equals(user.getUuid())) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "You can only edit your own messages");
        }

        // Проверяем, что сообщение не старше 15 минут
        if (message.getTimestamp().isBefore(Instant.now().minusSeconds(15 * 60))) {
            throw new CustomException(ErrorCode.MESSAGE_TOO_OLD, "Messages can only be edited within 15 minutes");
        }

        // Валидация контента
        validateMessageContent(request.getContent());

        message.setContent(request.getContent());
        message.setEdited(true);
        message.setEditedAt(Instant.now());

        ChatMessage updatedMessage = chatMessageRepository.save(message);
        log.info("Message edited by {} in room {}", userEmail, roomId);

        ChatMessage repliedMessage = null;
        if (updatedMessage.getReplyTo() != null) {
            repliedMessage = chatMessageRepository.findById(UUID.fromString(updatedMessage.getReplyTo())).orElse(null);
        }

        return convertToResponse(updatedMessage, repliedMessage);
    }

    @Transactional
    public void deleteMessage(UUID roomId, UUID messageId, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        ChatMessage message = chatMessageRepository.findByUuidAndRoomUuid(messageId, roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.MESSAGE_NOT_FOUND));

        // Проверяем, что пользователь является автором сообщения или хостом комнаты
        boolean isHost = roomParticipantRepository.isUserHostOfRoom(roomId, user.getUuid());
        if (!message.getUser().getUuid().equals(user.getUuid()) && !isHost) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "You can only delete your own messages");
        }

        chatMessageRepository.delete(message);
        log.info("Message deleted by {} in room {}", userEmail, roomId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesAfter(UUID roomId, Instant afterTimestamp, Principal principal) {
        validateRoomAccess(roomId, principal);

        List<ChatMessage> messages = chatMessageRepository
                .findByRoomUuidAndTimestampAfterOrderByTimestampAsc(roomId, afterTimestamp);

        return messages.stream()
                .map(message -> {
                    ChatMessage repliedMessage = null;
                    if (message.getReplyTo() != null) {
                        repliedMessage = chatMessageRepository.findById(UUID.fromString(message.getReplyTo())).orElse(null);
                    }
                    return convertToResponse(message, repliedMessage);
                })
                .collect(Collectors.toList());
    }

    private void validateRoomAccess(UUID roomId, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Для приватных комнат проверяем участие
        if (!room.isPublic()) {
            RoomParticipant participant = roomParticipantRepository
                    .findByRoomUuidAndUserUuid(roomId, user.getUuid())
                    .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED, "No access to this room"));

            if (!participant.isActive()) {
                throw new CustomException(ErrorCode.ACCESS_DENIED, "Participant is not active in this room");
            }
        }
    }

    private void validateMessageContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "Message content cannot be empty");
        }

        if (content.length() > 2000) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "Message too long (max 2000 characters)");
        }

        // Базовая проверка на запрещенный контент
        String lowerContent = content.toLowerCase();
        if (lowerContent.contains("http://") || lowerContent.contains("https://")) {
            throw new CustomException(ErrorCode.INVALID_INPUT, "Links are not allowed in messages");
        }
    }

    private ChatMessageResponse convertToResponse(ChatMessage message, ChatMessage repliedMessage) {
        return ChatMessageResponse.builder()
                .uuid(message.getUuid())
                .content(message.getContent())
                .type(message.getType())
                .timestamp(message.getTimestamp())
                .edited(message.isEdited())
                .editedAt(message.getEditedAt())
                .replyTo(message.getReplyTo() != null ? UUID.fromString(message.getReplyTo()) : null)
                .senderName(message.getUser().getName())
                .senderEmail(message.getUser().getEmail())
                .senderAvatar(message.getUser().getAvatarLink())
                .repliedMessageContent(repliedMessage != null ? repliedMessage.getContent() : null)
                .repliedMessageSender(repliedMessage != null ? repliedMessage.getUser().getName() : null)
                .build();
    }
}
