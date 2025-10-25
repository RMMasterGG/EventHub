package com.semasem.service;

import com.semasem.dto.exception.CustomException;
import com.semasem.dto.exception.ErrorCode;
import com.semasem.dto.request.GuestJoinRequest;
import com.semasem.dto.response.GuestJoinResponse;
import com.semasem.dto.response.RoomResponse;
import com.semasem.repository.RoomRepository;
import com.semasem.repository.UserRepository;
import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.User;
import com.semasem.repository.entity.UserRole;
import com.semasem.service.security.JwtService;
import com.semasem.dto.entity.TokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final UserRepository userRepository;
    private final RoomRepository roomRepository;
    private final RoomService roomService;
    private final JwtService jwtService;

    @Transactional
    public GuestJoinResponse joinAsGuest(GuestJoinRequest request) {
        // Находим комнату по invite link
        Room room = roomRepository.findByInviteLink(request.getRoomInviteLink())
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND, "Invalid invite link"));

        // Проверяем, разрешен ли гостевой доступ
        if (!room.isPublic()) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, "Guest access not allowed for this room");
        }

        // Создаем временного гостевого пользователя
        User guestUser = createGuestUser(request.getGuestName());
        User savedGuest = userRepository.save(guestUser);

        // Генерируем токены
        String accessToken = jwtService.generateToken(savedGuest, TokenType.ACCESS_TOKEN);
        String refreshToken = jwtService.generateToken(savedGuest, TokenType.REFRESH_TOKEN);

        // Сохраняем refresh token
        savedGuest.setRefreshToken(refreshToken);
        userRepository.save(savedGuest);

        // Автоматически присоединяем гостя к комнате
        Principal guestPrincipal = savedGuest::getEmail;
        roomService.joinRoom(room.getUuid(), guestPrincipal);

        RoomResponse roomResponse = RoomResponse.fromEntity(room);

        return new GuestJoinResponse(accessToken, refreshToken, request.getGuestName(),
                savedGuest.getEmail(), roomResponse);
    }

    private User createGuestUser(String guestName) {
        User guest = new User();
        guest.setName(guestName);
        guest.setEmail(generateGuestEmail());
        guest.setPassword(UUID.randomUUID().toString()); // случайный пароль
        guest.setGuest(true);
        guest.setGuestExpiresAt(Instant.now().plusSeconds(60 * 60 * 24));
        guest.setRole(UserRole.ROLE_GUEST);
        guest.setEmailVerified(true); // гостям не нужна верификация email
        guest.setCreatedAt(java.time.LocalDate.now());

        return guest;
    }

    private String generateGuestEmail() {
        return "guest_" + UUID.randomUUID().toString().substring(0, 8) + "@temp.semasem.com";
    }

    @Transactional
    public void cleanupExpiredGuests() {
        userRepository.deleteExpiredGuests(Instant.now());
    }
}