package com.semasem.service;

import com.semasem.repository.entity.ParticipantInfo;
import com.semasem.repository.entity.ParticipantRole;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomSessionService {

    // Храним базовую информацию о подключенных участниках
    private final Map<UUID, Set<UUID>> activeRoomParticipants = new ConcurrentHashMap<>();

    // Храним расширенную информацию о медиа-статусе участников
    private final Map<UUID, ParticipantMediaInfo> participantMediaInfo = new ConcurrentHashMap<>();

    // Класс для хранения медиа-информации о участнике
    public static class ParticipantMediaInfo {
        private UUID userId;
        private String name;
        private String email;
        private ParticipantRole role;
        private boolean isGuest;
        private Instant joinedAt;
        private Instant lastActiveAt;
        private boolean isAudioEnabled;
        private boolean isVideoEnabled;
        private String sessionId;

        public ParticipantMediaInfo(UUID userId, String name, String email, ParticipantRole role,
                                    boolean isGuest, Instant joinedAt, Instant lastActiveAt,
                                    boolean isAudioEnabled, boolean isVideoEnabled, String sessionId) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.role = role;
            this.isGuest = isGuest;
            this.joinedAt = joinedAt;
            this.lastActiveAt = lastActiveAt;
            this.isAudioEnabled = isAudioEnabled;
            this.isVideoEnabled = isVideoEnabled;
            this.sessionId = sessionId;
        }

        // Геттеры
        public UUID getUserId() { return userId; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public ParticipantRole getRole() { return role; }
        public boolean isGuest() { return isGuest; }
        public Instant getJoinedAt() { return joinedAt; }
        public Instant getLastActiveAt() { return lastActiveAt; }
        public boolean isAudioEnabled() { return isAudioEnabled; }
        public boolean isVideoEnabled() { return isVideoEnabled; }
        public String getSessionId() { return sessionId; }

        // Сеттеры для медиа-статусов
        public void setAudioEnabled(boolean audioEnabled) { isAudioEnabled = audioEnabled; }
        public void setVideoEnabled(boolean videoEnabled) { isVideoEnabled = videoEnabled; }
        public void setLastActiveAt(Instant lastActiveAt) { this.lastActiveAt = lastActiveAt; }
    }

    // Добавляем участника в комнату
    public void addParticipant(UUID roomId, UUID userId, String name, String email,
                               ParticipantRole role, boolean isGuest, Instant joinedAt, String sessionId) {
        activeRoomParticipants
                .computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                .add(userId);

        // Сохраняем медиа-информацию участника
        participantMediaInfo.put(userId, new ParticipantMediaInfo(
                userId, name, email, role, isGuest, joinedAt, Instant.now(),
                true, true, sessionId // По умолчанию аудио и видео включены
        ));

        log.info("User {} joined room {}. Active participants: {}",
                userId, roomId, getActiveParticipantsCount(roomId));
    }

    // Обновляем медиа-статус участника
    public void updateParticipantMediaStatus(UUID userId, boolean isAudioEnabled, boolean isVideoEnabled) {
        ParticipantMediaInfo info = participantMediaInfo.get(userId);
        if (info != null) {
            info.setAudioEnabled(isAudioEnabled);
            info.setVideoEnabled(isVideoEnabled);
            info.setLastActiveAt(Instant.now());
            log.debug("Updated media status for user {}: audio={}, video={}",
                    userId, isAudioEnabled, isVideoEnabled);
        }
    }

    // Удаляем участника из комнаты
    public void removeParticipant(UUID roomId, UUID userId) {
        Set<UUID> participants = activeRoomParticipants.get(roomId);
        if (participants != null) {
            participants.remove(userId);
            if (participants.isEmpty()) {
                activeRoomParticipants.remove(roomId);
            }
        }

        // Удаляем медиа-информацию
        participantMediaInfo.remove(userId);

        log.info("User {} left room {}. Active participants: {}",
                userId, roomId, getActiveParticipantsCount(roomId));
    }

    // Получаем полную информацию об активных участниках комнаты
    public List<ParticipantMediaInfo> getActiveParticipants(UUID roomId) {
        Set<UUID> participantIds = activeRoomParticipants.getOrDefault(roomId, Collections.emptySet());

        return participantIds.stream()
                .map(participantMediaInfo::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    // Получаем активных участников с объединением данных из БД
    public List<ParticipantMediaInfo> getActiveParticipantsWithDetails(UUID roomId,
                                                                       List<ParticipantInfo> dbParticipants) {
        Set<UUID> activeParticipantIds = activeRoomParticipants.getOrDefault(roomId, Collections.emptySet());

        return dbParticipants.stream()
                .filter(participant -> activeParticipantIds.contains(participant.getUserId()))
                .map(participant -> {
                    ParticipantMediaInfo mediaInfo = participantMediaInfo.get(participant.getUserId());
                    return new ParticipantMediaInfo(
                            participant.getUserId(),
                            participant.getName(),
                            participant.getEmail(),
                            participant.getRole(),
                            participant.isGuest(),
                            participant.getJoinedAt(),
                            participant.getLastActiveAt(),
                            mediaInfo != null ? mediaInfo.isAudioEnabled() : true,
                            mediaInfo != null ? mediaInfo.isVideoEnabled() : true,
                            mediaInfo != null ? mediaInfo.getSessionId() : null
                    );
                })
                .collect(Collectors.toList());
    }

    // Получаем количество активных участников
    public int getActiveParticipantsCount(UUID roomId) {
        return activeRoomParticipants.getOrDefault(roomId, Collections.emptySet()).size();
    }

    // Проверяем, есть ли пользователь в комнате
    public boolean isUserInRoom(UUID roomId, UUID userId) {
        return activeRoomParticipants.getOrDefault(roomId, Collections.emptySet())
                .contains(userId);
    }

    // Получаем медиа-информацию конкретного участника
    public ParticipantMediaInfo getParticipantMediaInfo(UUID userId) {
        return participantMediaInfo.get(userId);
    }

    // Обновляем время последней активности
    public void updateLastActive(UUID userId) {
        ParticipantMediaInfo info = participantMediaInfo.get(userId);
        if (info != null) {
            info.setLastActiveAt(Instant.now());
        }
    }

    // Получаем всех участников комнаты (включая неактивных) с медиа-статусами
    public List<ParticipantMediaInfo> getAllRoomParticipantsWithMediaStatus(UUID roomId,
                                                                            List<ParticipantInfo> allDbParticipants) {
        Set<UUID> activeParticipantIds = activeRoomParticipants.getOrDefault(roomId, Collections.emptySet());

        return allDbParticipants.stream()
                .map(participant -> {
                    boolean isActive = activeParticipantIds.contains(participant.getUserId());
                    ParticipantMediaInfo mediaInfo = participantMediaInfo.get(participant.getUserId());

                    return new ParticipantMediaInfo(
                            participant.getUserId(),
                            participant.getName(),
                            participant.getEmail(),
                            participant.getRole(),
                            participant.isGuest(),
                            participant.getJoinedAt(),
                            participant.getLastActiveAt(),
                            isActive && mediaInfo != null ? mediaInfo.isAudioEnabled() : false,
                            isActive && mediaInfo != null ? mediaInfo.isVideoEnabled() : false,
                            isActive && mediaInfo != null ? mediaInfo.getSessionId() : null
                    );
                })
                .collect(Collectors.toList());
    }
}