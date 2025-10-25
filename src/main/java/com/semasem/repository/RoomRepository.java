package com.semasem.repository;

import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByOwnerUuid(UUID ownerUuid);
    Optional<Room> findByUuid(UUID roomUuid);
    boolean existsByUuidAndStatus(UUID roomUuid, RoomStatus status);
    Optional<Room> findByInviteLink(String inviteLink);
    boolean existsByInviteLink(String inviteLink);



    @Query("SELECT DISTINCT r FROM Room r JOIN RoomParticipant rp ON r.uuid = rp.room.uuid WHERE rp.user.uuid = :userUuid AND rp.status = 'JOINED'")
    List<Room> findRoomsWhereUserIsParticipant(@Param("userUuid") UUID userUuid);
}
