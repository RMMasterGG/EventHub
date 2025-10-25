package com.semasem.repository;

import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    List<Room> findByOwnerUuid(UUID ownerUuid);
    Optional<Room> findByUuid(UUID roomUuid);
    boolean existsByUuidAndStatus(UUID roomUuid, RoomStatus status);

}
