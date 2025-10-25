package com.semasem.dto.response;

import com.semasem.repository.entity.Room;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class RoomResponse {
    UUID RoomUUID;
    String title;
    String description;
    String inviteLink;

    public static RoomResponse fromEntity(Room room) {
        return new RoomResponse(
                room.getUuid(),
                room.getTitle(),
                room.getDescription(),
                room.getInviteLink()
        );
    }
}
