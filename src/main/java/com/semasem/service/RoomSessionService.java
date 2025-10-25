package com.semasem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomSessionService {

    private final Map<UUID, Set<String>> activeRoomParticipants = new ConcurrentHashMap<>();

    // Добавляем участника в комнату
    public void addParticipant(UUID roomId, String userId) {
        activeRoomParticipants
                .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);

        log.info("User {} joined room {}. Active participants: {}",
                userId, roomId, getActiveParticipantsCount(roomId));
    }

    // Удаляем участника из комнаты
    public void removeParticipant(UUID roomId, String userId) {
        Set<String> participants = activeRoomParticipants.get(roomId);
        if (participants != null) {
            participants.remove(userId);
            if (participants.isEmpty()) {
                activeRoomParticipants.remove(roomId);
            }
        }

        log.info("User {} left room {}. Active participants: {}",
                userId, roomId, getActiveParticipantsCount(roomId));
    }

    // Получаем список активных участников
    public Set<String> getActiveParticipants(UUID roomId) {
        return activeRoomParticipants.getOrDefault(roomId, Collections.emptySet());
    }

    // Получаем количество активных участников
    public int getActiveParticipantsCount(UUID roomId) {
        return activeRoomParticipants.getOrDefault(roomId, Collections.emptySet()).size();
    }

    // Проверяем, есть ли пользователь в комнате
    public boolean isUserInRoom(UUID roomId, String userId) {
        return activeRoomParticipants.getOrDefault(roomId, Collections.emptySet())
                .contains(userId);
    }
}