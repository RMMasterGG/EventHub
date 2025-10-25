package com.semasem.dto.mapper;

import com.semasem.dto.request.CreateRoomRequest;
import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomStatus;
import com.semasem.repository.entity.User;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class RoomMapper {

    public Room toEntity(CreateRoomRequest request, User owner) {
        Room room = new Room();
        room.setTitle(request.getTitle());
        room.setDescription(request.getDescription());
        room.setOwnerUuid(owner.getUuid());
        room.setPublic(request.isPublic());
        room.setAllowGuests(request.isAllowGuests());
        room.setInviteLink(generateInviteCode());
        room.setMaxParticipants(request.getMaxParticipants());
        room.setStatus(RoomStatus.ACTIVE);
        room.setCreatedAt(Instant.now());
        room.setUpdatedAt(Instant.now());

        return room;
    }

    private String generateInviteCode() {
        // Генерируем короткий код из 8 символов
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
