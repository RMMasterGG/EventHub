package com.semasem.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RoomMessage {
    private String type; // "JOIN_ROOM", "LEAVE_ROOM"
    private String roomId;
    private String username;
}
