package com.semasem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GuestJoinResponse {
    private String accessToken;
    private String refreshToken;
    private String guestName;
    private String guestEmail;
    private RoomResponse room;
}
