package com.semasem.service;

import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.mapper.RoomMapper;
import com.semasem.dto.request.CreateRoomRequest;
import com.semasem.dto.response.ParticipantResponse;
import com.semasem.dto.response.RoomResponse;
import com.semasem.repository.RoomParticipantRepository;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class RoomService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final RoomMapper roomMapper;

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

        if (!room.isPublic() && !room.getOwnerUuid().equals(user.getUuid())) {
            throw new CustomException(ErrorCode.ACCESS_DENIED);
        }

        return new RoomResponse(room.getUuid(), room.getTitle(), room.getDescription(), room.getInviteLink());
    }

    public List<RoomResponse> getUserRooms(Principal principal) {
        String userEmail = principal.getName();
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Room> ownedRooms = roomRepository.findByOwnerUuid(user.getUuid());
        List<RoomResponse> roomResponses = new ArrayList<>();

        for (Room room : ownedRooms) {
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

        // Проверяем, не присоединился ли уже пользователь
        Optional<RoomParticipant> existingParticipant = roomParticipantRepository
                .findByRoomUuidAndUserUuid(roomId, user.getUuid());

        if (existingParticipant.isPresent()) {
            RoomParticipant participant = existingParticipant.get();
            if (participant.isActive()) {
                throw new CustomException(ErrorCode.ALREADY_JOINED, "User already joined this room");
            }
            // Reactivate participant
            participant.setStatus(ParticipantStatus.JOINED);
            participant.setJoinedAt(Instant.now());
            participant.setLastActiveAt(Instant.now());
            participant.setSessionId(UUID.randomUUID().toString());
            roomParticipantRepository.save(participant);
        } else {
            // Create new participant
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

        // Проверяем не превышен ли лимит участников
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
        // Проверяем доступ пользователя к комнате
        String userEmail = principal.getName();
        User currentUser = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        Room room = roomRepository.findByUuid(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));

        // Проверяем, что пользователь имеет доступ к комнате
        if (!room.isPublic() && !room.getOwnerUuid().equals(currentUser.getUuid())) {
            // Для приватных комнат проверяем, является ли пользователь участником
            RoomParticipant participant = roomParticipantRepository.findByRoomUuidAndUserUuid(roomId, currentUser.getUuid())
                    .orElseThrow(() -> new CustomException(ErrorCode.ACCESS_DENIED, "No access to this room"));
        }

        // Получаем всех активных участников комнаты
        List<RoomParticipant> activeParticipants = roomParticipantRepository
                .findByRoomUuidAndStatus(roomId, ParticipantStatus.JOINED);

        // Преобразуем в DTO
        return activeParticipants.stream()
                .map(this::convertToParticipantResponse)
                .collect(Collectors.toList());
    }

    private ParticipantResponse convertToParticipantResponse(RoomParticipant participant) {
        ParticipantResponse response = new ParticipantResponse();
        response.setUserEmail(participant.getUser().getEmail());
        response.setUserName(participant.getUser().getName());
        response.setRole(participant.getRole());
        response.setStatus(participant.getStatus());
        response.setAudioEnabled(participant.isAudioEnabled());
        response.setVideoEnabled(participant.isVideoEnabled());
        response.setJoinedAt(participant.getJoinedAt());
        response.setSessionId(participant.getSessionId());
        return response;
    }
}
