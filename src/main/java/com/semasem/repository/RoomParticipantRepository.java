package com.semasem.repository;

import com.semasem.repository.entity.ParticipantStatus;
import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomParticipant;
import com.semasem.repository.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomParticipantRepository extends JpaRepository<RoomParticipant, Long> {

    // Найти всех активных участников комнаты
    List<RoomParticipant> findByRoomUuidAndStatus(UUID roomUuid, ParticipantStatus status);

    // Найти участника по комнате и пользователю
    Optional<RoomParticipant> findByRoomUuidAndUserUuid(UUID roomUuid, UUID userUuid);

    // Найти участника по комнате и сессии
    Optional<RoomParticipant> findByRoomUuidAndSessionId(UUID roomUuid, String sessionId);

    // Проверить, является ли пользователь хостом комнаты
    @Query("SELECT COUNT(rp) > 0 FROM RoomParticipant rp WHERE rp.room.uuid = :roomUuid AND rp.user.uuid = :userUuid AND rp.role = 'HOST'")
    boolean isUserHostOfRoom(@Param("roomUuid") UUID roomUuid, @Param("userUuid") UUID userUuid);

    // Получить количество активных участников в комнате
    @Query("SELECT COUNT(rp) FROM RoomParticipant rp WHERE rp.room.uuid = :roomUuid AND rp.status = 'JOINED'")
    int countActiveParticipantsInRoom(@Param("roomUuid") UUID roomUuid);

    // Отметить всех участников комнаты как вышедших
    @Modifying
    @Query("UPDATE RoomParticipant rp SET rp.status = 'LEFT', rp.leftAt = CURRENT_TIMESTAMP WHERE rp.room.uuid = :roomUuid AND rp.status = 'JOINED'")
    void markAllParticipantsAsLeft(@Param("roomUuid") UUID roomUuid);

    // Обновить статус медиа участника
    @Modifying
    @Query("UPDATE RoomParticipant rp SET rp.isAudioEnabled = :audioEnabled, rp.isVideoEnabled = :videoEnabled WHERE rp.id = :participantId")
    void updateMediaStatus(@Param("participantId") Long participantId,
                           @Param("audioEnabled") boolean audioEnabled,
                           @Param("videoEnabled") boolean videoEnabled);


    boolean existsByRoomUuidAndUserUuidAndStatus(UUID roomUuid, UUID userUuid, ParticipantStatus status);

    Optional<RoomParticipant> findByRoomAndUser(Room room, User user);
    boolean existsByRoomAndUserAndStatus(Room room, User user, ParticipantStatus status);

}