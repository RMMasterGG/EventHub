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
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Для гостей проверяем срок действия
        if (user.isGuest() && user.getGuestExpiresAt().isBefore(Instant.now())) {
            throw new CustomException(ErrorCode.GUEST_EXPIRED, "Guest access has expired");
        }

        // Проверяем активность комнаты
        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new CustomException(ErrorCode.ROOM_NOT_ACTIVE, "Room is not active");
        }

        // Проверяем, является ли пользователь активным участником комнаты
        RoomParticipant participant = roomParticipantRepository.findByRoomUuidAndUserUuid(roomId, user.getUuid())
                .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED, "User is not a participant of this room"));

        if (!participant.isActive()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "Participant is not active in this room");
        }

        // Обновляем время последней активности
        participant.setLastActiveAt(Instant.now());
        roomParticipantRepository.save(participant);
    }

    public List<String> getRoomParticipantsEmails(UUID roomId) {
        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findByRoomUuidAndStatus(roomId, ParticipantStatus.JOINED);

        return activeParticipants.stream()
                .map(participant -> participant.getUser().getEmail())
                .collect(Collectors.toList());
    }
}