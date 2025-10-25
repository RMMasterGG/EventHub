package com.semasem.service;

import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.repository.RoomParticipantRepository;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebRTCService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomParticipantRepository roomParticipantRepository;

    public void validateRoomAccess(UUID roomId, Principal principal) {
        if (principal == null || principal.getName() == null) {
            log.warn("Unauthorized access attempt to room {}: principal is null", roomId);
            throw new CustomException(ErrorCode.UNAUTHORIZED, "Unauthorized access");
        }

        String userIdOrEmail = principal.getName();

        if (userIdOrEmail.startsWith("anonymous-")) {
            log.warn("Anonymous user access attempt to room {}", roomId);
            throw new CustomException(ErrorCode.UNAUTHORIZED, "Anonymous users not allowed");
        }

        User user;

        try {
            UUID userUuid = UUID.fromString(userIdOrEmail);
            user = userRepository.findByUuid((userUuid))
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "User not found by UUID"));
        } catch (IllegalArgumentException e) {
            user = userRepository.findByEmail(userIdOrEmail)
                    .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "User not found by email"));
        }

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND, "Room not found"));

        // Для гостей проверяем срок действия
        if (user.isGuest() && user.getGuestExpiresAt().isBefore(Instant.now())) {
            throw new CustomException(ErrorCode.GUEST_EXPIRED, "Guest access has expired");
        }

        // Проверяем активность комнаты
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new CustomException(ErrorCode.ROOM_NOT_ACTIVE, "Room is not active");
        }

        boolean hasAccess = checkRoomAccess(room, user);

        if (!hasAccess) {
            log.warn("User {} has no access to room {}", user.getUuid(), roomId);
            throw new CustomException(ErrorCode.ACCESS_DENIED, "No access to room");
        }

        updateParticipantActivity(roomId, user.getUuid());

        log.debug("User {} validated for room {}", user.getUuid(), roomId);
    }

    private boolean checkRoomAccess(Room room, User user) {
        // Владелец комнаты всегда имеет доступ
        if (room.getOwnerUuid().equals(user.getUuid())) {
            return true;
        }

        // Публичная комната - доступ всем
        if (room.isPublic()) {
            return true;
        }

        // Проверяем, является ли пользователь активным участником
        return roomParticipantRepository.findByRoomUuidAndUserUuid(room.getUuid(), user.getUuid())
                .map(participant -> participant.isActive() &&
                        participant.getStatus() == ParticipantStatus.JOINED)
                .orElse(false);
    }

    // ✅ ВЫНЕС ОБНОВЛЕНИЕ АКТИВНОСТИ В ОТДЕЛЬНЫЙ МЕТОД
    private void updateParticipantActivity(UUID roomId, UUID userUuid) {
        roomParticipantRepository.findByRoomUuidAndUserUuid(roomId, userUuid)
                .ifPresent(participant -> {
                    participant.setLastActiveAt(Instant.now());
                    roomParticipantRepository.save(participant);
                });
    }

    public List<String> getRoomParticipantsEmails(UUID roomId) {
        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findByRoomUuidAndStatus(roomId, ParticipantStatus.JOINED);

        return activeParticipants.stream()
                .map(participant -> participant.getUser().getEmail())
                .collect(Collectors.toList());
    }

    public List<UUID> getRoomParticipantsUserIds(UUID roomId) {
        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findByRoomUuidAndStatus(roomId, ParticipantStatus.JOINED);

        return activeParticipants.stream()
                .map(participant -> participant.getUser().getUuid())
                .collect(Collectors.toList());
    }
}