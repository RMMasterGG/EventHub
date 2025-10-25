package com.semasem.service;

import com.semasem.dto.entity.TokenType;
import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.mapper.RoomMapper;
import com.semasem.dto.request.CreateRoomRequest;
import com.semasem.dto.response.ParticipantResponse;
import com.semasem.dto.response.RoomJoinResponse;
import com.semasem.dto.response.RoomResponse;
import com.semasem.repository.RoomParticipantRepository;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.*;
import com.semasem.service.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class RoomService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomMapper roomMapper;
    private final JwtService jwtService;

    public RoomResponse createRoom(CreateRoomRequest request, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomMapper.toEntity(request, user);

        Room savedRoom = roomRepository.save(room);

        RoomParticipant hostParticipant = RoomParticipant.builder()
                .room(savedRoom)
                .user(user)
                .joinedAt(Instant.now())
                .role(ParticipantRole.HOST)
                .status(ParticipantStatus.JOINED)
                .isAudioEnabled(true)
                .isVideoEnabled(true)
                .lastActiveAt(Instant.now())
                .sessionId(UUID.randomUUID().toString())
                .build();

        roomParticipantRepository.save(hostParticipant);

        return new RoomResponse(savedRoom.getUuid(), savedRoom.getTitle(), savedRoom.getDescription(), savedRoom.getInviteLink());
    }

    public RoomResponse getRoom(UUID roomId, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Доступ имеют: владелец, участники комнаты, или если комната публичная
//        boolean hasAccess = room.isPublic() ||
//                room.getOwnerUuid().equals(user.getUuid()) ||
//                roomParticipantRepository.existsByRoomUuidAndUserUuidAndStatus(
//                        roomId, user.getUuid(), ParticipantStatus.JOINED);

//        if (!hasAccess) {
//            throw new CustomException(ErrorCode.ACCESS_DENIED);
//        }

        return new RoomResponse(room.getUuid(), room.getTitle(), room.getDescription(), room.getInviteLink());
    }

    public List<RoomResponse> getUserRooms(Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // Возвращаем комнаты где пользователь владелец ИЛИ участник
        List<Room> ownedRooms = roomRepository.findByOwnerUuid(user.getUuid());
        List<Room> participatedRooms = roomRepository.findRoomsWhereUserIsParticipant(user.getUuid());

        List<RoomResponse> roomResponses = new ArrayList<>();

        // Добавляем комнаты владельца
        for (Room room : ownedRooms) {
            roomResponses.add(RoomResponse.fromEntity(room));
        }

        // Добавляем комнаты где пользователь участник
        for (Room room : participatedRooms) {
            roomResponses.add(RoomResponse.fromEntity(room));
        }

        return roomResponses;
    }

    public RoomResponse deleteRoom(UUID roomUUID, Principal principal) {
        String userEmail = principal.getName();

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomUUID)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        if (!room.getOwnerUuid().equals(user.getUuid())) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "Только владелец может удалить комнату");
        }

        roomRepository.delete(room);

        return new RoomResponse(room.getUuid(), room.getTitle(), room.getDescription(), room.getInviteLink());
    }

    public RoomResponse joinRoom(UUID roomId, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Проверяем доступ к комнате
        boolean canJoin = room.isPublic() || room.getOwnerUuid().equals(user.getUuid());
        if (!canJoin) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "No access to join this room");
        }

        Optional<RoomParticipant> existingParticipant = roomParticipantRepository
                .findByRoomUuidAndUserUuid(roomId, user.getUuid());

        if (existingParticipant.isPresent()) {
            RoomParticipant participant = existingParticipant.get();
            if (participant.isActive()) {
                throw new CustomException(ErrorCode.ALREADY_JOINED, "User already joined this room");
            }
            participant.setStatus(ParticipantStatus.JOINED);
            participant.setJoinedAt(Instant.now());
            participant.setLastActiveAt(Instant.now());
            participant.setSessionId(UUID.randomUUID().toString());
            roomParticipantRepository.save(participant);
        } else {
            RoomParticipant participant = RoomParticipant.builder()
                    .room(room)
                    .user(user)
                    .joinedAt(Instant.now())
                    .role(ParticipantRole.PARTICIPANT)
                    .status(ParticipantStatus.JOINED)
                    .isAudioEnabled(true)
                    .isVideoEnabled(true)
                    .lastActiveAt(Instant.now())
                    .sessionId(UUID.randomUUID().toString())
                    .build();
            roomParticipantRepository.save(participant);
        }

        int activeParticipants = roomParticipantRepository.countActiveParticipantsInRoom(roomId);
        if (activeParticipants > room.getMaxParticipants()) {
            throw new CustomException(ErrorCode.ROOM_FULL, "Room has reached maximum participants");
        }

        return RoomResponse.fromEntity(room);
    }

    public void leaveRoom(UUID roomId, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        RoomParticipant participant = roomParticipantRepository
                .findByRoomUuidAndUserUuid(roomId, user.getUuid())
                .orElseThrow(() -> new CustomException(ErrorCode.NOT_JOINED, "User not joined this room"));

        participant.markAsLeft();
        roomParticipantRepository.save(participant);
    }

    public List<ParticipantResponse> getRoomParticipants(UUID roomId, Principal principal) {
        String userEmail = principal.getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Доступ к участникам имеют: владелец, участники комнаты, или если комната публичная
//        boolean hasAccess = room.isPublic() ||
//                room.getOwnerUuid().equals(currentUser.getUuid()) ||
//                roomParticipantRepository.existsByRoomUuidAndUserUuidAndStatus(
//                        roomId, currentUser.getUuid(), ParticipantStatus.JOINED);
//
//        if (!hasAccess) {
//            throw new CustomException(ErrorCode.ACCESS_DENIED, "No access to this room's participants");
//        }

        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findByRoomUuidAndStatus(roomId, ParticipantStatus.JOINED);

        return activeParticipants.stream()
                .map(this::convertToParticipantResponse)
                .collect(Collectors.toList());
    }

    public RoomJoinResponse joinByInviteLink(String inviteCode, HttpServletRequest request) {
        Room room = roomRepository.findByInviteLink(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_LINK, "Invalid invite link"));

        if (room.getStatus() != RoomStatus.ACTIVE) {
            throw new CustomException(ErrorCode.ROOM_NOT_ACTIVE, "Room is not active");
        }

        RoomJoinResponse response = new RoomJoinResponse();
        response.setRoomId(room.getUuid());
        response.setRoomTitle(room.getTitle());
        response.setRoomDescription(room.getDescription());
        response.setAllowGuests(room.isAllowGuests());

        String authHeader = request.getHeader("Authorization");
        boolean isAuthenticated = authHeader != null && authHeader.startsWith("Bearer ");

        if (isAuthenticated) {
            String token = authHeader.substring(7);
            try {
                String userEmail = jwtService.extractEmail(token);
                if (jwtService.isTokenValid(token, TokenType.ACCESS_TOKEN)) {
                    User user = userRepository.findByEmail(userEmail)
                            .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "User not found"));

                    // ✅ ГАРАНТИРУЕМ ДОБАВЛЕНИЕ ПОЛЬЗОВАТЕЛЯ В КОМНАТУ
                    RoomParticipant participant = roomParticipantRepository
                            .findByRoomAndUser(room, user)
                            .orElse(null);

                    if (participant == null) {
                        // Создаём новую запись участника
                        participant = RoomParticipant.builder()
                                .room(room)
                                .user(user)
                                .guestName(null)
                                .joinedAt(Instant.now())
                                .role(room.getOwnerUuid().equals(user.getUuid()) ?
                                        ParticipantRole.HOST : ParticipantRole.PARTICIPANT)
                                .status(ParticipantStatus.JOINED)
                                .isAudioEnabled(true)
                                .isVideoEnabled(false)
                                .lastActiveAt(Instant.now())
                                .build();
                    } else {
                        // Обновляем существующую запись
                        participant.setStatus(ParticipantStatus.JOINED);
                        participant.setJoinedAt(Instant.now());
                        participant.setLeftAt(null);
                        participant.setLastActiveAt(Instant.now());

                        // Если пользователь был забанен, снимаем бан
                        if (participant.getStatus() == ParticipantStatus.BANNED) {
                            participant.setStatus(ParticipantStatus.JOINED);
                        }
                    }

                    roomParticipantRepository.save(participant);

                    response.setRequiresAuth(false);
                    response.setCanJoinDirectly(true);
                    response.setDirectJoinToken(generateDirectJoinToken(room.getUuid(), userEmail));
                    return response;
                }
            } catch (Exception e) {
                log.warn("Invalid token in invite link request: {}", e.getMessage());
                // Продолжаем как неавторизованный пользователь
            }
        }

        // Логика для неавторизованных пользователей
        response.setRequiresAuth(true);
        response.setCanJoinDirectly(false);

        if (room.isAllowGuests()) {
            String guestToken = jwtService.generateGuestToken(
                    room.getUuid().toString(),
                    "guest-" + System.currentTimeMillis()
            );
            response.setVisitorToken(guestToken);
            response.setGuestJoinUrl("/api/rooms/guest-join?inviteCode=" + inviteCode);
        } else {
            response.setAuthUrl("/api/auth/login?redirect=/api/rooms/join/" + inviteCode);
        }

        return response;
    }

    public RoomResponse guestJoin(String inviteCode, String guestName) {
        Room room = roomRepository.findByInviteLink(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_LINK, "Invalid invite link"));

        if (!room.isAllowGuests()) {
            throw new CustomException(ErrorCode.GUESTS_NOT_ALLOWED, "Guest access is not allowed for this room");
        }

        int activeParticipants = roomParticipantRepository.countActiveParticipantsInRoom(room.getUuid());
        if (activeParticipants >= room.getMaxParticipants()) {
            throw new CustomException(ErrorCode.ROOM_FULL, "Room has reached maximum participants");
        }

        RoomParticipant guestParticipant = RoomParticipant.builder()
                .room(room)
                .user(null)
                .guestName(guestName)
                .joinedAt(Instant.now())
                .role(ParticipantRole.GUEST)
                .status(ParticipantStatus.JOINED)
                .isAudioEnabled(true)
                .isVideoEnabled(true)
                .lastActiveAt(Instant.now())
                .sessionId(UUID.randomUUID().toString())
                .build();

        roomParticipantRepository.save(guestParticipant);

        return RoomResponse.fromEntity(room);
    }

    public RoomResponse directJoin(String inviteCode, Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByInviteLink(inviteCode)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INVITE_LINK));

        return joinRoom(room.getUuid(), principal);
    }

    private ParticipantResponse convertToParticipantResponse(RoomParticipant participant) {
        ParticipantResponse response = new ParticipantResponse();
        if (participant.getUser() != null) {
            response.setUserEmail(participant.getUser().getEmail());
            response.setUserName(participant.getUser().getName());
        } else {
            response.setUserName(participant.getGuestName());
            response.setUserEmail("guest");
        }
        response.setRole(participant.getRole());
        response.setStatus(participant.getStatus());
        response.setAudioEnabled(participant.isAudioEnabled());
        response.setVideoEnabled(participant.isVideoEnabled());
        response.setJoinedAt(participant.getJoinedAt());
        response.setSessionId(participant.getSessionId());
        return response;
    }

    private String generateDirectJoinToken(UUID roomId, String userEmail) {
        return jwtService.generateDirectJoinToken(roomId.toString(), userEmail);
    }
}