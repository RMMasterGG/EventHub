package com.semasem.dto.mapper;

import com.semasem.dto.request.CreateRoomRequest;
import com.semasem.repository.entity.Room;
import com.semasem.repository.entity.RoomStatus;
import com.semasem.repository.entity.User;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RoomMapper {

    public Room toEntity(CreateRoomRequest request, User user) {
        Room room = new Room();
        room.setTitle(request.getTitle());
        room.setDescription(request.getDescription());
        room.setOwnerUuid(user.getUuid());
        room.setPublic(request.isPublic());
        room.setInviteLink(generateInviteLink());
        room.setMaxParticipants(request.getMaxParticipants());
        room.setStatus(RoomStatus.ACTIVE);
        return room;
    }

    private String generateInviteLink() {
        return "/join/" + UUID.randomUUID().toString().substring(0, 8);
    }
}
