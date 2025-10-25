package com.semasem.service;

import com.semasem.repository.entity.ChatMessage;
import com.semasem.repository.ChatMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatMessage saveMessage(ChatMessage message) {
        return chatMessageRepository.save(message);
    }

    public Optional<ChatMessage> getMessage(UUID messageId) {
        return chatMessageRepository.findById(messageId);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRoomMessages(UUID roomId, int limit) {
        return chatMessageRepository.findByRoomUuidWithUserAndRoom(roomId, limit);
    }

    public void deleteMessage(UUID messageId) {
        chatMessageRepository.deleteById(messageId);
    }

    public void markMessageAsRead(UUID messageId, String userId) {
        // TODO: Реализовать логику отметки прочтения
    }
}